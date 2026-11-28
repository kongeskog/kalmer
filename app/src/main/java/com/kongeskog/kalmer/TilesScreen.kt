package com.kongeskog.kalmer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import kotlin.math.abs

// Icon size, columns and name spacing are those of Mudita's first launcher (com.mudita.launcher).
private val COLUMNS = listOf(99, 240, 381) // icon centres
private const val ICON = 108
private const val TOP = 60                 // top of the first row of icons
private const val LABEL_GAP = 7            // icon to the top of its name
private const val LABEL_HEIGHT = 34
private const val ROW_GAP = 21             // bottom of a name to the next row of icons
private const val NAV_HEIGHT = 64          // the page dots row along the bottom
private val TileLabel = text(28).copy(textAlign = TextAlign.Center)

/**
 * The page of tiles on show. It lives outside the screen so that going on to the inbox or the rename page
 * and coming back lands on the same page; opening the apps page afresh starts at the first.
 */
val tilesPage = mutableIntStateOf(0)

/**
 * The apps page as pages of icon tiles, which is not something Kalm has. Nothing scrolls: the apps are cut
 * into pages, three icons across, and a sideways swipe turns the page, with dots along the bottom to show which one is up. It shows the same apps
 * as the list, under the same names, and has no header, search or quick access area.
 */
@Composable
fun TilesScreen(push: (Page) -> Unit, pop: () -> Unit, open: (String) -> Unit) {
    val context = LocalContext.current
    val s = Store.settings
    val apps = Apps.tiles()
    /** The app whose long-press sheet is open. */
    var chosen by remember { mutableStateOf<String?>(null) }
    /** Arranging by hand: a tap picks a tile, and a tap on another swaps the two. */
    var reorder by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf<String?>(null) }
    BackHandler(chosen != null || reorder) {
        if (chosen != null) chosen = null else { reorder = false; picked = null }
    }
    var page by tilesPage

    val iconPx = with(LocalDensity.current) { ICON.px.roundToPx() }
    val block = ICON + if (s.tileNames) LABEL_GAP + LABEL_HEIGHT else 0
    val pitch = block + ROW_GAP
    // The bottom row is kept clear on every page, so the grid doesn't move when a second page appears.
    val rows = ((800 - NAV_HEIGHT - TOP + ROW_GAP) / pitch).coerceAtLeast(1)
    val perPage = rows * COLUMNS.size
    val pages = ((apps.size + perPage - 1) / perPage).coerceAtLeast(1)
    val current = page.coerceIn(0, pages - 1)

    /** Forward a page, or back; back from the first page leaves for the home screen. */
    val turn by rememberUpdatedState { forward: Boolean ->
        when {
            forward -> if (current < pages - 1) page = current + 1
            current > 0 -> page = current - 1
            else -> pop()
        }
    }

    /** The up and down swipes open whatever is chosen for them; the apps page itself would be nowhere to go. */
    val swipe by rememberUpdatedState { key: String ->
        val target = Store.settings.target(key)
        if (!reorder && target != OPEN_APPS) open(target)
    }

    Box(
        Modifier.fillMaxSize().background(Color.White).pointerInput(Unit) {
            var moved = Offset.Zero
            detectDragGestures(
                onDragStart = { moved = Offset.Zero },
                onDragEnd = {
                    if (abs(moved.x) >= 80f && abs(moved.x) >= abs(moved.y)) turn(moved.x < 0)
                    // By default down opens the inbox. There is no bell here: the owner had it removed.
                    else if (abs(moved.y) >= 80f) swipe(if (moved.y < 0) "tileUp" else "tileDown")
                },
            ) { _, amount -> moved += amount }
        }
    ) {
        if (apps.isEmpty()) Message("No apps available", "Unhide your apps or install some new ones")
        for (row in 0 until rows) for ((column, centre) in COLUMNS.withIndex()) {
            val app = apps.getOrNull(current * perPage + row * COLUMNS.size + column) ?: continue
            Box(
                Modifier.offset((centre - 76).px, (TOP + row * pitch).px).width(152.px).height(block.px)
                    .tap(onLongClick = { if (!reorder) { buzz(context); chosen = app.pkg } }) {
                        val from = picked
                        when {
                            !reorder -> Apps.launch(context, app.pkg)
                            from == null -> picked = app.pkg
                            from == app.pkg -> picked = null
                            else -> {
                                val order = apps.map { it.pkg }.toMutableList()
                                val a = order.indexOf(from)
                                val b = order.indexOf(app.pkg)
                                if (a >= 0 && b >= 0) {
                                    order[a] = app.pkg
                                    order[b] = from
                                    Store.update { it.copy(tileOrder = order) }
                                }
                                picked = null
                            }
                        }
                    }
            ) {
                Box(Modifier.align(Alignment.TopCenter).size(ICON.px)) {
                    if (reorder && picked == app.pkg) Canvas(Modifier.fillMaxSize()) {
                        drawRoundRect(
                            Color.Black, Offset(-7f, -7f), Size(size.width + 14f, size.height + 14f), CornerRadius(26f),
                            Stroke(3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(9f, 6f))),
                        )
                    }
                    AppIcon(app.pkg, iconPx, ICON / 2)
                    val count = Counts.of(app.pkg)
                    // On a white disc, so the count stays clear of whatever the icon has in its corner.
                    if (count > 0 && s.badges != 0) Box(
                        Modifier.align(Alignment.TopEnd).offset(12.px, (-12).px).background(Color.White, CircleShape).padding(3.px)
                    ) { Badge(count) }
                }
                if (s.tileNames) BasicText(
                    Apps.name(app.pkg), Modifier.align(Alignment.BottomCenter).width(152.px).height(LABEL_HEIGHT.px),
                    style = TileLabel, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (pages > 1 || reorder) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(NAV_HEIGHT.px)) {
            if (reorder) Box(Modifier.align(Alignment.Center)) { HeaderButton("Done") { reorder = false; picked = null } }
            else if (pages > 1) Canvas(Modifier.align(Alignment.Center).width((pages * 24).px).height(14.px)) {
                val step = size.width / pages
                for (i in 0 until pages) {
                    val centre = Offset(step * (i + 0.5f), size.height / 2)
                    if (i == current) drawCircle(Color.Black, 6f, centre) else drawCircle(Color.Black, 5f, centre, style = Stroke(2f))
                }
            }
        }

        chosen?.let { pkg ->
            Sheet({ chosen = null }) {
                SheetTitle(Apps.name(pkg))
                // No "Pin": the tiles don't show the pinned apps, and leave the list's pins as they are.
                SheetButton("Reorder", icon = Swap) {
                    // Start from the order on screen, whichever it was, and keep it as the custom one.
                    Store.update { it.copy(tileSort = SORT_CUSTOM, tileOrder = apps.map { app -> app.pkg }) }
                    picked = pkg
                    reorder = true
                    chosen = null
                }
                SheetButton("Rename", icon = Pencil) { chosen = null; push(Page.Rename(pkg)) }
                SheetButton("Hide", icon = Eye) {
                    Store.update { it.copy(hidden = it.hidden + pkg) }
                    chosen = null
                }
                SheetButton("Cancel", filled = true) { chosen = null }
            }
        }
    }
}
