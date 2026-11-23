package com.kongeskog.kalmer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Every size in this app is a pixel measurement taken from Kalm on the Kompakt (480x800 at 213 dpi).
private const val SCALE = 213f / 160f
val Int.px: Dp get() = (this / SCALE).dp
val Int.pxSp: TextUnit get() = (this / SCALE).sp

val Lato = FontFamily(
    Font(R.font.lato_medium, FontWeight.Medium),
    Font(R.font.lato_black, FontWeight.Black),
)

fun text(sizePx: Int, weight: FontWeight = FontWeight.Medium, color: Color = Color.Black) =
    TextStyle(fontFamily = Lato, fontWeight = weight, fontSize = sizePx.pxSp, color = color)

/** Settings rows. */
val Body = text(27)
/** Home screen items and app list rows, which Kalm sets slightly smaller than settings rows. */
val Item = text(27).copy(fontSize = 20.sp)
val Small = text(23)
val Title = text(33, FontWeight.Black)
val ButtonText = text(30, FontWeight.Black)

/** A tap target with no ripple or other feedback: e-ink redraws are too costly for it. */
@Composable
fun Modifier.tap(onClick: () -> Unit): Modifier =
    clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.tap(onLongClick: () -> Unit, onClick: () -> Unit): Modifier =
    combinedClickable(remember { MutableInteractionSource() }, indication = null, onLongClick = onLongClick, onClick = onClick)

@Composable
fun Icon(size: Int, modifier: Modifier = Modifier, draw: DrawScope.() -> Unit) =
    Canvas(modifier.size(size.px), draw)

fun DrawScope.line(x1: Float, y1: Float, x2: Float, y2: Float, width: Float = 3f) =
    drawLine(Color.Black, Offset(x1, y1), Offset(x2, y2), width, StrokeCap.Round)

private fun DrawScope.outline(width: Float = 3f, build: Path.() -> Unit) =
    drawPath(Path().apply(build), Color.Black, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))

// Icons are drawn on a 36px box, by eye from Kalm's.
val BackArrow: DrawScope.() -> Unit = {
    val m = size.height / 2
    line(2f, m, size.width - 2f, m); line(2f, m, 16f, m - 14f); line(2f, m, 16f, m + 14f)
}
val ForwardArrow: DrawScope.() -> Unit = {
    val m = size.height / 2
    line(2f, m, size.width - 2f, m); line(size.width - 2f, m, size.width - 16f, m - 14f); line(size.width - 2f, m, size.width - 16f, m + 14f)
}
val Cross: DrawScope.() -> Unit = { line(4f, 4f, size.width - 4f, size.height - 4f); line(size.width - 4f, 4f, 4f, size.height - 4f) }
val Dots: DrawScope.() -> Unit = {
    for (y in listOf(5f, 18f, 31f)) drawCircle(Color.Black, 4f, Offset(size.width / 2, y), style = Stroke(3f))
}
val Bell: DrawScope.() -> Unit = {
    outline { moveTo(4f, 28f); lineTo(7f, 23f); lineTo(7f, 14f); cubicTo(7f, 0f, 29f, 0f, 29f, 14f); lineTo(29f, 23f); lineTo(32f, 28f); close() }
    outline { moveTo(14f, 32f); cubicTo(15f, 36f, 21f, 36f, 22f, 32f) }
    line(18f, 1.5f, 18f, 3f)
}
/** The bell while the inbox has something in it: Kalm cuts the outline away around a filled dot at the top right. */
val BellWithDot: DrawScope.() -> Unit = {
    Bell()
    drawCircle(Color.White, 10f, Offset(28f, 8f))
    drawCircle(Color.Black, 7f, Offset(28f, 8f))
}
val Gear: DrawScope.() -> Unit = {
    val c = Offset(size.width / 2, size.height / 2)
    drawCircle(Color.Black, 11f, c, style = Stroke(3f))
    drawCircle(Color.Black, 4f, c, style = Stroke(3f))
    for (i in 0 until 8) {
        val a = Math.toRadians(i * 45.0)
        val cos = Math.cos(a).toFloat()
        val sin = Math.sin(a).toFloat()
        line(c.x + 11f * cos, c.y + 11f * sin, c.x + 16f * cos, c.y + 16f * sin)
    }
}
val Pin: DrawScope.() -> Unit = {
    outline { moveTo(11f, 3f); lineTo(25f, 3f); lineTo(23f, 8f); lineTo(23f, 16f); lineTo(29f, 24f); lineTo(7f, 24f); lineTo(13f, 16f); lineTo(13f, 8f); close() }
    line(18f, 24f, 18f, 34f)
}
val Pencil: DrawScope.() -> Unit = {
    outline { moveTo(4f, 32f); lineTo(6f, 23f); lineTo(25f, 4f); lineTo(32f, 11f); lineTo(13f, 30f); close() }
    line(21f, 8f, 28f, 15f)
}
val Eye: DrawScope.() -> Unit = {
    outline { moveTo(2f, 18f); cubicTo(10f, 5f, 26f, 5f, 34f, 18f); cubicTo(26f, 31f, 10f, 31f, 2f, 18f); close() }
    drawCircle(Color.Black, 5f, style = Stroke(3f))
}
// The player bar's icons are filled, on a 48px box.
private fun DrawScope.filled(build: Path.() -> Unit) = Path().apply(build).let {
    drawPath(it, Color.Black)
    drawPath(it, Color.Black, style = Stroke(4f, join = StrokeJoin.Round))
}
val Previous: DrawScope.() -> Unit = {
    line(7f, 8f, 7f, 40f, 5f); filled { moveTo(40f, 8f); lineTo(16f, 24f); lineTo(40f, 40f); close() }
}
val Next: DrawScope.() -> Unit = {
    line(41f, 8f, 41f, 40f, 5f); filled { moveTo(8f, 8f); lineTo(32f, 24f); lineTo(8f, 40f); close() }
}
val Play: DrawScope.() -> Unit = { filled { moveTo(12f, 6f); lineTo(40f, 24f); lineTo(12f, 42f); close() } }
val Pause: DrawScope.() -> Unit = { line(15f, 11f, 15f, 37f, 11f); line(33f, 11f, 33f, 37f, 11f) }
val MusicNote: DrawScope.() -> Unit = {
    outline { moveTo(16f, 36f); lineTo(16f, 10f); lineTo(42f, 5f); lineTo(42f, 31f) }
    line(16f, 18f, 42f, 13f)
    drawCircle(Color.Black, 6f, Offset(10f, 37f), style = Stroke(3f))
    drawCircle(Color.Black, 6f, Offset(36f, 32f), style = Stroke(3f))
}

/** The filled alarm bell beside the time, on a 32px box. */
val AlarmBell: DrawScope.() -> Unit = {
    drawPath(Path().apply {
        moveTo(0f, 23f); cubicTo(4f, 20f, 4.5f, 16f, 4.5f, 12f); cubicTo(4.5f, -2f, 26.5f, -2f, 26.5f, 12f)
        cubicTo(26.5f, 16f, 27f, 20f, 31f, 23f); lineTo(31f, 24.5f); lineTo(0f, 24.5f); close()
    }, Color.Black)
    drawCircle(Color.Black, 2.2f, Offset(15.5f, 2.2f))
    drawPath(Path().apply { moveTo(10f, 26.5f); cubicTo(10f, 33.5f, 21f, 33.5f, 21f, 26.5f); close() }, Color.Black)
}

// Weather icons on a 30px box, thin lines as in Kalm.
private fun DrawScope.cloud(dy: Float = 0f) = drawPath(Path().apply {
    moveTo(8f, 25f + dy); cubicTo(-1f, 25f + dy, -1f, 14.5f + dy, 8f, 14.5f + dy); cubicTo(8f, 4f + dy, 23f, 4f + dy, 24f, 13f + dy)
    cubicTo(31.5f, 13f + dy, 31.5f, 25f + dy, 24.5f, 25f + dy); close()
}, Color.Black, style = Stroke(2f, join = StrokeJoin.Round))
private fun DrawScope.sun() {
    drawCircle(Color.Black, 6f, style = Stroke(2f))
    for (i in 0 until 8) {
        val a = Math.toRadians(i * 45.0)
        val cos = Math.cos(a).toFloat()
        val sin = Math.sin(a).toFloat()
        line(15f + 10f * cos, 15f + 10f * sin, 15f + 13f * cos, 15f + 13f * sin, 2f)
    }
}

/** Kalm's clear-night icon: a crescent open to the upper right with two small stars beside it. */
private fun DrawScope.moon() {
    val disc = Path().apply { addOval(androidx.compose.ui.geometry.Rect(Offset(11.5f, 17f), 11f)) }
    val bite = Path().apply { addOval(androidx.compose.ui.geometry.Rect(Offset(18f, 10.5f), 9.5f)) }
    drawPath(Path.combine(androidx.compose.ui.graphics.PathOperation.Difference, disc, bite), Color.Black, style = Stroke(2f, join = StrokeJoin.Round))
    for ((x, y) in listOf(19.5f to 3.5f, 25f to 12f)) { line(x - 2.5f, y, x + 2.5f, y, 1.5f); line(x, y - 2.5f, x, y + 2.5f, 1.5f) }
}

/**
 * Picks an icon from Mudita's icon name, which is an OpenWeatherMap code such as "04n":
 * 01 clear, 02 to 04 cloud, 09 and 10 rain, 11 thunder, 13 snow, 50 mist; "n" is night.
 */
fun weatherIcon(name: String): DrawScope.() -> Unit = when (name.take(2)) {
    "01" -> if (name.endsWith("n")) ({ moon() }) else ({ sun() })
    "09", "10" -> ({ cloud(-4f); for (x in listOf(9f, 15f, 21f)) line(x, 23f, x - 2f, 28f, 2f) })
    "11" -> ({ cloud(-4f); line(16f, 21f, 12f, 26f, 2f); line(12f, 26f, 17f, 26f, 2f); line(17f, 26f, 13f, 30f, 2f) })
    "13" -> ({ cloud(-4f); for (x in listOf(9f, 15f, 21f)) drawCircle(Color.Black, 1.5f, Offset(x, 26f)) })
    "50" -> ({ for (y in listOf(9f, 15f, 21f)) line(4f, y, 26f, y, 2f) })
    else -> ({ cloud() })
}

/** A 63px tap target holding a 36px icon, the size of Kalm's header buttons. */
@Composable
fun IconButton(modifier: Modifier = Modifier, onClick: () -> Unit, draw: DrawScope.() -> Unit) =
    Box(modifier.size(63.px).tap(onClick), contentAlignment = Alignment.Center) { Icon(36, draw = draw) }

@Composable
fun DottedDivider(insetPx: Int = 21) = Canvas(Modifier.fillMaxWidth().height(1.px).padding(start = insetPx.px)) {
    drawLine(Color.Black, Offset(0f, 0.5f), Offset(size.width, 0.5f), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 2f)))
}

@Composable
fun Rule() = Box(Modifier.fillMaxWidth().height(2.px).background(Color.Black))

/** Kalm's settings header: back arrow, bold title, an optional button on the right, solid rule at y=133. */
@Composable
fun Screen(title: String, onBack: () -> Unit, action: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().background(Color.White)) {
        Box(Modifier.fillMaxWidth().height(133.px)) {
            IconButton(Modifier.offset(6.px, 59.px), onBack, BackArrow)
            BasicText(
                title, Modifier.offset(74.px, 70.px).width(if (action != null) 310.px else 390.px),
                style = Title, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (action != null) Box(
                Modifier.align(Alignment.TopEnd).padding(top = 60.px, end = 12.px).height(63.px),
                contentAlignment = Alignment.Center,
            ) { action() }
        }
        Rule()
        content()
    }
}

/** A text button for the right side of a header, such as "Save". */
@Composable
fun HeaderButton(label: String, onClick: () -> Unit) =
    BasicText(label, Modifier.tap(onClick).padding(horizontal = 9.px, vertical = 12.px), style = text(28, FontWeight.Black))

/**
 * A scrolling list with Kalm's scroll bar: a 6px bar near the right edge, sized and placed by how much of the
 * list is on screen, kept clear of the header rule and the bottom edge. No overscroll stretch.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ScrollColumn(modifier: Modifier = Modifier, bar: Boolean = true, content: LazyListScope.() -> Unit) {
    val state = rememberLazyListState()
    val thumb by remember {
        derivedStateOf {
            val info = state.layoutInfo
            val visible = info.visibleItemsInfo
            if (visible.isEmpty()) return@derivedStateOf null
            val viewport = (info.viewportEndOffset - info.viewportStartOffset).toFloat()
            val average = visible.sumOf { it.size }.toFloat() / visible.size
            val total = average * info.totalItemsCount
            if (total <= viewport + 1) return@derivedStateOf null
            val scrolled = visible.first().index * average - visible.first().offset
            (scrolled / (total - viewport)).coerceIn(0f, 1f) to (viewport / total)
        }
    }
    Box(modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
            LazyColumn(Modifier.fillMaxSize(), state, content = content)
        }
        val t = thumb
        if (bar && t != null) {
            Canvas(Modifier.align(Alignment.CenterEnd).padding(end = 11.px, top = 12.px, bottom = 12.px).width(7.px).fillMaxHeight()) {
                val h = (size.height * t.second).coerceAtLeast(48f)
                drawRoundRect(Color.Black, Offset(0f, (size.height - h) * t.first), Size(size.width, h), CornerRadius(3f))
            }
        }
    }
}

@Composable
private fun Row107(onClick: (() -> Unit)?, dividerInset: Int = 21, content: @Composable RowScope.() -> Unit) {
    Column(if (onClick != null) Modifier.tap(onClick) else Modifier) {
        Row(Modifier.fillMaxWidth().height(107.px).padding(horizontal = 21.px), verticalAlignment = Alignment.CenterVertically) {
            content()
        }
        DottedDivider(dividerInset)
    }
}

@Composable
fun NavRow(label: String, value: String? = null, onClick: () -> Unit) = Row107(onClick) {
    Column(Modifier.weight(1f)) {
        BasicText(label, style = Body, maxLines = 1)
        if (value != null) BasicText(value, style = Small, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    Icon(30) {
        val m = size.height / 2 - 1f
        line(9.5f, m - 9.5f, 17f, m)
        line(9.5f, m + 9.5f, 17f, m)
    }
}

@Composable
fun SwitchRow(label: String, detail: String? = null, checked: Boolean, bold: Boolean = false, onChange: (Boolean) -> Unit) = Row107({ onChange(!checked) }) {
    Column(Modifier.weight(1f)) {
        BasicText(label, style = if (bold) Body.copy(fontWeight = FontWeight.Black) else Body, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (detail != null) BasicText(detail, style = Small, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    Canvas(Modifier.width(69.px).height(43.px)) {
        val r = size.height / 2
        if (checked) {
            drawRoundRect(Color.Black, cornerRadius = CornerRadius(r))
            drawCircle(Color.White, r - 9f, Offset(size.width - r, r))
        } else {
            drawRoundRect(Color.Black, Offset(1.5f, 1.5f), Size(size.width - 3f, size.height - 3f), CornerRadius(r), Stroke(3f))
            drawCircle(Color.Black, r - 9f, Offset(r, r))
        }
    }
}

@Composable
fun Radio(selected: Boolean) = Canvas(Modifier.size(35.px)) {
    val r = size.minDimension / 2
    drawCircle(Color.Black, r - 1.5f, style = Stroke(3f))
    if (selected) drawCircle(Color.Black, r - 9f)
}

@Composable
fun RadioRow(label: String, selected: Boolean, trailing: (@Composable () -> Unit)? = null, onClick: () -> Unit) = Row107(onClick, dividerInset = 80) {
    Radio(selected)
    Spacer(Modifier.width(19.px))
    BasicText(label, Modifier.weight(1f), style = Body, maxLines = 1, overflow = TextOverflow.Ellipsis)
    trailing?.invoke()
}

/** Kalm's small capitals heading between groups of radio rows, such as "USE TO OPEN". */
@Composable
fun SectionHeader(label: String) = Column {
    Box(Modifier.fillMaxWidth().height(92.px).padding(start = 21.px, bottom = 21.px), contentAlignment = Alignment.BottomStart) {
        BasicText(label.uppercase(), style = text(24, FontWeight.Black))
    }
    DottedDivider()
}

/** Kalm's count badge: a plain dot, or a larger dot with the number, depending on the badge style. */
@Composable
fun Badge(count: Int, style: Int = Store.settings.badges) {
    if (count <= 0 || style == 0) return
    if (style == 1) Box(Modifier.size(15.px).background(Color.Black, CircleShape))
    else Box(Modifier.size(37.px).background(Color.Black, CircleShape), contentAlignment = Alignment.Center) {
        BasicText(if (count > 99) "99" else count.toString(), style = text(22, FontWeight.Black, Color.White))
    }
}

/** Kalm's bottom sheet: the screen behind stays visible above a solid rule; tapping it dismisses. */
@Composable
fun Sheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().tap(onDismiss)) {
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.White).tap {}) {
            Box(Modifier.fillMaxWidth().height(3.px).background(Color.Black))
            content()
            Spacer(Modifier.height(16.px))
        }
    }
}

@Composable
fun SheetTitle(title: String) = Box(Modifier.fillMaxWidth().height(118.px).padding(start = 16.px, end = 16.px, top = 12.px), contentAlignment = Alignment.Center) {
    BasicText(title, style = Title, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** A full-width sheet button, 63px tall on an 85px pitch. Kalm fills the last one, "Cancel". */
@Composable
fun SheetButton(label: String, filled: Boolean = false, last: Boolean = filled, icon: (DrawScope.() -> Unit)? = null, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.px)
    Row(
        Modifier.padding(start = 16.px, end = 16.px, bottom = if (last) 0.px else 22.px).fillMaxWidth().height(63.px)
            .then(if (filled) Modifier.background(Color.Black, shape) else Modifier.border(3.px, Color.Black, shape))
            .tap(onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(36, draw = icon)
            Spacer(Modifier.width(18.px))
        }
        BasicText(label, style = if (filled) ButtonText.copy(color = Color.White) else ButtonText)
    }
}
