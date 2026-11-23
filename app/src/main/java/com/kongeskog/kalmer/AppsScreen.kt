package com.kongeskog.kalmer

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow

@Composable
fun AppsScreen(push: (Page) -> Unit, pop: () -> Unit) {
    val context = LocalContext.current
    val s = Store.settings
    var query by remember { mutableStateOf("") }
    /** The app whose long-press sheet is open. */
    var chosen by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }
    var quickSheet by remember { mutableStateOf(false) }
    BackHandler(chosen != null || menu || quickSheet) { chosen = null; menu = false; quickSheet = false }
    val searchFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (s.autoKeyboard && s.searchBar) searchFocus.requestFocus()
    }

    val searching = query.isNotBlank()
    val apps = if (searching) Apps.search(query) else Apps.visible()
    val quickCount = if (searching) 0 else Apps.quick().size
    // "Auto-open app": a single search result opens by itself.
    LaunchedEffect(query, apps.size) {
        if (s.autoOpenApp && searching && apps.size == 1) {
            val only = apps[0].pkg
            query = ""
            Apps.launch(context, only)
        }
    }
    Box(Modifier.fillMaxSize().background(Color.White).imePadding()) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().height(133.px)) {
                if (s.searchBar) Row(
                    Modifier.offset(21.px, 58.px).width(321.px).height(64.px)
                        .border(3.px, Color.Black, RoundedCornerShape(32.px)).padding(horizontal = 24.px),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(30) {
                        drawCircle(Color.Black, 10f, Offset(12f, 12f), style = Stroke(3f))
                        line(19f, 19f, 27f, 27f)
                    }
                    Spacer(Modifier.width(15.px))
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) BasicText("Search", style = text(30))
                        BasicTextField(query, { query = it }, Modifier.fillMaxWidth().focusRequester(searchFocus), textStyle = text(30), singleLine = true)
                    }
                    // Kalm's clear button: a filled circle with a white cross, shown once something is typed.
                    if (query.isNotEmpty()) Canvas(Modifier.offset(x = 9.px).size(37.px).tap { query = "" }) {
                        drawCircle(Color.Black)
                        val a = size.width * 0.33f
                        val z = size.width * 0.67f
                        drawLine(Color.White, Offset(a, a), Offset(z, z), 3f, androidx.compose.ui.graphics.StrokeCap.Round)
                        drawLine(Color.White, Offset(z, a), Offset(a, z), 3f, androidx.compose.ui.graphics.StrokeCap.Round)
                    }
                } else {
                    IconButton(Modifier.offset(6.px, 59.px), pop, BackArrow)
                    BasicText("App List", Modifier.offset(74.px, 70.px), style = Title)
                }
                if (s.bell && s.inbox) Box(Modifier.offset(348.px, 59.px)) {
                    IconButton(onClick = { push(Page.Inbox) }, draw = if (Inbox.shown().isNotEmpty()) BellWithDot else Bell)
                    // Kalm marks the bell with a dot while the inbox has something in it.
                    
                }
                IconButton(Modifier.offset(412.px, 59.px), { menu = true }, Dots)
            }
            // The app list's header rule is 3px; the settings pages' is 2px.
            Box(Modifier.fillMaxWidth().height(3.px).background(Color.Black))
            if (apps.isEmpty()) Message(
                if (query.isBlank()) "No apps available" else "No search results",
                if (query.isBlank()) "Unhide your apps or install some new ones" else "Try searching for the first few letters of the app name",
            )
            else ScrollColumn(bar = s.scrollBar) {
                // No item keys: with keys the list would follow the top row when a pin reorders it.
                // Pinned apps come first and end with Kalm's dotted divider, drawn inside the last pinned row
                // so every row stays 85px and the lines land in the same places while scrolling.
                val lastPinned = quickCount - 1
                if (!searching && s.quickMostUsed && quickCount == 0) item {
                    BasicText(
                        "Your device will learn up to 5 most used apps and list them here",
                        Modifier.padding(horizontal = 21.px, vertical = 16.px), style = Small,
                    )
                }
                itemsIndexed(apps) { index, app ->
                    Box(Modifier.fillMaxWidth().height(85.px).tap(onLongClick = { buzz(context); chosen = app.pkg }) { Apps.launch(context, app.pkg) }) {
                        Row(Modifier.fillMaxSize().padding(horizontal = 21.px), verticalAlignment = Alignment.CenterVertically) {
                            BasicText(Apps.name(app.pkg), style = Item, maxLines = 1)
                            if (Counts.of(app.pkg) > 0) {
                                Spacer(Modifier.width(11.px))
                                Badge(Counts.of(app.pkg))
                            }
                        }
                        if (index == lastPinned && index < apps.lastIndex) Box(
                            Modifier.align(Alignment.BottomStart).padding(start = 21.px, end = 29.px).fillMaxWidth().height(1.px).background(Color.Black)
                        )
                    }
                }
            }
        }

        chosen?.let { pkg ->
            Sheet({ chosen = null }) {
                SheetTitle(Apps.name(pkg))
                val pinned = pkg in s.pinned
                SheetButton(if (pinned) "Unpin" else "Pin", icon = Pin) {
                    Store.update { it.copy(pinned = if (pinned) it.pinned - pkg else it.pinned + pkg) }
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
        if (quickSheet) Sheet({ quickSheet = false }) {
            // Kalm's "Quick access area" sheet: title with a close button, then two radio rows.
            Box(Modifier.fillMaxWidth().height(85.px)) {
                BasicText("Quick access area", Modifier.offset(16.px, 29.px), style = Title)
                IconButton(Modifier.offset(414.px, 18.px), { quickSheet = false }, Cross)
            }
            for ((label, most) in listOf("Pinned apps" to false, "Most used apps" to true)) Row(
                Modifier.fillMaxWidth().height(64.px).padding(horizontal = 16.px).tap {
                    Store.update { it.copy(quickMostUsed = most) }
                    quickSheet = false
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Radio(s.quickMostUsed == most)
                Spacer(Modifier.width(9.px))
                BasicText(label, style = Body)
            }
        }
        if (menu) Sheet({ menu = false }) {
            Spacer(Modifier.height(33.px))
            SheetButton("Quick access area") { menu = false; quickSheet = true }
            SheetButton("Manage app list") { menu = false; push(Page.ManageApps) }
            SheetButton("Home Screen Settings") { menu = false; push(Page.Settings) }
            SheetButton("Cancel", filled = true) { menu = false }
        }
    }
}

/** Kalm's empty state: a bold line and a lighter one, centred. */
@Composable
fun Message(title: String, body: String) = Column(
    Modifier.fillMaxSize().padding(horizontal = 21.px),
    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally,
) {
    BasicText(title, style = Title.copy(textAlign = TextAlign.Center))
    Spacer(Modifier.height(11.px))
    BasicText(body, style = Body.copy(textAlign = TextAlign.Center))
}

private fun ago(time: Long): String {
    val minutes = (System.currentTimeMillis() - time) / 60_000
    return when {
        minutes < 1 -> "Now"
        minutes < 60 -> "$minutes min. ago"
        minutes < 24 * 60 -> "${minutes / 60} h ago"
        else -> "${minutes / (24 * 60)} d ago"
    }
}

/** The small icon an app uses for its notifications, in black. */
@Composable
private fun NotificationIcon(pkg: String, resId: Int) {
    val context = LocalContext.current
    val bitmap = remember(pkg, resId) {
        try {
            val drawable = context.createPackageContext(pkg, 0).getDrawable(resId)!!.mutate()
            drawable.setTint(android.graphics.Color.BLACK)
            val size = 32
            android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888).also {
                drawable.setBounds(0, 0, size, size)
                drawable.draw(android.graphics.Canvas(it))
            }.asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }
    if (bitmap != null) androidx.compose.foundation.Image(bitmap, null, Modifier.size(32.px))
}

/** Kalm's inbox: one row per app with its count, expanding to that app's notifications; "Clear all" pinned to the bottom. */
@Composable
fun InboxScreen(push: (Page) -> Unit, pop: () -> Unit) {
    val context = LocalContext.current
    val groups = Inbox.shown().groupBy { it.pkg }.toList()
    var open by remember { mutableStateOf(emptySet<String>()) }
    val time = text(20)
    Screen("Notification Inbox", pop, action = { IconButton(onClick = { push(Page.Notifications) }, draw = Gear) }) {
        when {
            !Store.settings.inbox -> Message("No new notifications", "Enable the Notification Inbox to capture app notifications and view them here")
            groups.isEmpty() -> Message("No new notifications", "When you receive notifications, we’ll list them here")
            else -> Box(Modifier.fillMaxSize()) {
                ScrollColumn(Modifier.padding(bottom = 96.px)) {
                    for ((pkg, entries) in groups) {
                        item {
                            Box(Modifier.fillMaxWidth().height(107.px).tap { Apps.launch(context, pkg) }) {
                                Box(Modifier.offset(24.px, 37.px)) { NotificationIcon(pkg, entries.first().icon) }
                                Row(Modifier.offset(79.px, 0.px).height(107.px).width(270.px), verticalAlignment = Alignment.CenterVertically) {
                                    BasicText(Apps.name(pkg), Modifier.weight(1f, fill = false), style = text(28, FontWeight.Black), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Spacer(Modifier.width(11.px))
                                    Badge(entries.size, 2)
                                }
                                BasicText(ago(entries.first().time), Modifier.align(Alignment.TopEnd).padding(top = 20.px, end = 21.px), style = time)
                                IconButton(Modifier.offset(409.px, 38.px), { open = if (pkg in open) open - pkg else open + pkg }) {
                                    val m = size.height / 2
                                    val d = if (pkg in open) -5f else 5f
                                    line(8f, m - d, 18f, m + d); line(28f, m - d, 18f, m + d)
                                }
                            }
                        }
                        if (pkg in open) items(entries) { entry ->
                            Box(Modifier.fillMaxWidth().height(107.px).tap { Apps.launch(context, pkg) }) {
                                BasicText(entry.title, Modifier.offset(43.px, 20.px).width(300.px), style = text(28, FontWeight.Black), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                BasicText(entry.text, Modifier.offset(43.px, 57.px).width(400.px), style = Small, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                BasicText(ago(entry.time), Modifier.align(Alignment.TopEnd).padding(top = 20.px, end = 21.px), style = time)
                            }
                        }
                    }
                }
                Box(
                    Modifier.align(Alignment.BottomCenter).padding(start = 16.px, end = 16.px, bottom = 16.px).fillMaxWidth().height(64.px)
                        .border(3.px, Color.Black, RoundedCornerShape(10.px)).tap { Inbox.clearAll() },
                    contentAlignment = Alignment.Center,
                ) { BasicText("Clear all", style = text(28, FontWeight.Black)) }
            }
        }
    }
}

@Composable
fun RenameScreen(pkg: String, pop: () -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(Apps.name(pkg)) }
    val save = {
        val trimmed = name.trim()
        val original = Apps.all.firstOrNull { it.pkg == pkg }?.systemLabel
        Store.update { it.copy(names = if (trimmed.isEmpty() || trimmed == original) it.names - pkg else it.names + (pkg to trimmed)) }
        Toast.makeText(context, "App renamed", Toast.LENGTH_SHORT).show()
        pop()
    }
    Screen("Rename ${Apps.name(pkg)}", pop, action = { HeaderButton("Save", save) }) {
        Column(Modifier.padding(21.px)) {
            BasicText("App name", style = Small)
            Spacer(Modifier.height(8.px))
            Box(
                Modifier.fillMaxWidth().height(75.px).border(3.px, Color.Black, RoundedCornerShape(10.px)).padding(horizontal = 16.px),
                contentAlignment = Alignment.CenterStart,
            ) {
                BasicTextField(name, { name = it }, Modifier.fillMaxWidth(), textStyle = Body, singleLine = true)
            }
        }
    }
}
