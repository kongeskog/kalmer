#!/usr/bin/env python3
"""
Makes the night version of a wallpaper for every home style from its text-left night version.

A night wallpaper is drawn once, on the text-left cut. The other cuts of the same design hold the same
shapes moved, mirrored or scaled. This finds, for each shape the night version changes, the matching shape
in each other cut, works out how it was moved, and puts the night shape there under the same transform.
Shapes the night version adds (closed eyelids, a redrawn face) follow the shape they belong to.

    python3 tools/night_cuts.py            # writes app/src/main/assets/wallpapers_night/*.svg

Run it again after changing a text-left night file. It stops if a shape can't be matched closely.
"""
import os
import re
import sys

ASSETS = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "assets")
DAY = os.path.join(ASSETS, "wallpapers")
NIGHT = os.path.join(ASSETS, "wallpapers_night")
CUTS = ["Text_Center", "Text_Right", "Buttons_Left", "Buttons_Center", "Buttons_Right"]

# For each design: which element of the day file (counting tags from 0) becomes which night elements,
# and which added night elements go with it. Indexes are into the text-left day and night files.
DESIGNS = {
    # The mountain landscape: the sun becomes a crescent moon.
    "L": {"defs": None, "shapes": {4: {"night": [5], "extra": []}}},
    # The woodland animals fall asleep.
    "J": {
        "defs": (2, 12),
        "shapes": {
            5: {"night": [17], "extra": []},              # fox, fill
            6: {"night": [18], "extra": [(28, 28)]},      # fox, outline; its closed eye
            9: {"night": [21], "extra": [(24, 27)]},      # owl; its eyelids
            10: {"night": [22], "extra": [(29, 33)]},     # stag; its redrawn eye and ear
            11: {"night": [23], "extra": []},             # lying deer
        },
    },
}

# Moves by hand, in SVG units, where the fitted place doesn't work. In the centre buttons cut of the mountain
# landscape the gate stands closer to the sun than in the others, and the moon's lower tip ran into its roof.
NUDGE = {("L", "Buttons_Center", 4): (-10, -4)}

TOKEN = re.compile(r"[A-Za-z]|-?\d*\.?\d+(?:e-?\d+)?")


def tags(text):
    return [m.group(0) for m in re.finditer(r"<[^>]+>", text)]


def points(d):
    """A path as (commands, xs, ys). Absolute M L H V C Z only; H and V are written out as L."""
    commands, xs, ys, cur = [], [], [], [0.0, 0.0]
    command, numbers = None, []

    def flush():
        if command is None:
            return
        if command in "Zz":
            commands.append("Z")
        elif command == "H":
            for v in numbers:
                cur[0] = v
                commands.append("L"); xs.append(cur[0]); ys.append(cur[1])
        elif command == "V":
            for v in numbers:
                cur[1] = v
                commands.append("L"); xs.append(cur[0]); ys.append(cur[1])
        elif command in "MLC":
            n = 6 if command == "C" else 2
            if len(numbers) % n:
                raise ValueError("odd coordinates")
            for i in range(0, len(numbers), n):
                commands.append("L" if command == "M" and i else command)
                xs.extend(numbers[i:i + n:2]); ys.extend(numbers[i + 1:i + n:2])
            cur[0], cur[1] = numbers[-2], numbers[-1]
        else:
            raise ValueError("unsupported path command " + command)

    for token in TOKEN.findall(d):
        if token.isalpha():
            flush()
            command, numbers = token, []
        else:
            numbers.append(float(token))
    flush()
    return "".join(commands), xs, ys


def line(a, b):
    """Least squares b = scale * a + shift, with the worst miss."""
    n = len(a)
    ma, mb = sum(a) / n, sum(b) / n
    spread = sum((x - ma) ** 2 for x in a)
    scale = sum((x - ma) * (y - mb) for x, y in zip(a, b)) / spread
    shift = mb - scale * ma
    return scale, shift, max(abs(y - (scale * x + shift)) for x, y in zip(a, b))


def path_data(tag):
    m = re.search(r'\sd="([^"]*)"', tag)
    return m.group(1) if m else None


def tidy(v):
    """Round off fitting noise: -0.99999996 is a mirror and 355.9998 is 356. Anything else keeps six decimals."""
    return "%d" % round(v) if abs(v - round(v)) < 1e-3 else ("%.6f" % v).rstrip("0")


def build(design, spec, cut):
    day_left = tags(open(os.path.join(DAY, design + "_Text_Left.svg")).read())
    night_left = tags(open(os.path.join(NIGHT, design + "_Text_Left.svg")).read())
    cut_tags = tags(open(os.path.join(DAY, "%s_%s.svg" % (design, cut))).read())
    originals = {i: points(path_data(day_left[i])) for i in spec["shapes"]}

    out, added, used = [], [], set()
    for tag in cut_tags:
        if tag == "</svg>":
            break
        d = path_data(tag)
        hit = None
        if d:
            try:
                shape = points(d)
            except ValueError:
                shape = None
            for i, original in originals.items():
                if shape and shape[0] == original[0]:
                    sx, tx, rx = line(original[1], shape[1])
                    sy, ty, ry = line(original[2], shape[2])
                    if max(rx, ry) < 0.01:
                        hit = (i, sx, sy, tx, ty)
        if hit is None:
            out.append(tag)
            continue
        i, sx, sy, tx, ty = hit
        used.add(i)
        dx, dy = NUDGE.get((design, cut, i), (0, 0))
        tx, ty = tx + dx, ty + dy
        if abs(abs(sx) - abs(sy)) > 1e-3:
            raise SystemExit("%s %s: shape %d is stretched, not just moved" % (design, cut, i))
        group = '<g transform="matrix(%s 0 0 %s %s %s)">' % (tidy(sx), tidy(sy), tidy(tx), tidy(ty))
        out.append(group)
        out.extend(night_left[n] for n in spec["shapes"][i]["night"])
        out.append("</g>")
        # Added shapes stay on top of everything, as in the text-left night file.
        for first, last in spec["shapes"][i]["extra"]:
            added.append(group)
            added.extend(night_left[first:last + 1])
            added.append("</g>")

    if spec["defs"]:
        first, last = spec["defs"]
        out[1:1] = night_left[first:last + 1]
    out.extend(added)
    out.append("</svg>")
    return "\n".join(out) + "\n", used


def main():
    for design, spec in DESIGNS.items():
        for cut in CUTS:
            text, used = build(design, spec, cut)
            # The centre cuts of the woodland animals have no fox; every other shape must be found.
            missing = set(spec["shapes"]) - used
            if missing - ({5, 6} if design == "J" and "Center" in cut else set()):
                raise SystemExit("%s %s: no match for shapes %s" % (design, cut, sorted(missing)))
            name = "%s_%s.svg" % (design, cut)
            open(os.path.join(NIGHT, name), "w").write(text)
            print(name, "shapes replaced:", sorted(used), "bytes:", len(text))


if __name__ == "__main__":
    sys.exit(main())
