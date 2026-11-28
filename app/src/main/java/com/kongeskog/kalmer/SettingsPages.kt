package com.kongeskog.kalmer

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings as SystemSettings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private fun listenerEnabled(context: Context): Boolean {
    val enabled = SystemSettings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: ""
    return ComponentName(context, NotifListener::class.java).flattenToString() in enabled.split(":")
}

private val HOME_ITEMS = SLOTS.mapIndexed { i, key -> "Item ${i + 1}" to key }
private val TILE_ORDERS = listOf("A-Z" to SORT_AZ, "Most recent" to SORT_RECENT, "Most used" to SORT_USED, "Custom" to SORT_CUSTOM)
private val GESTURES = listOf("Swipe Up" to "up", "Swipe Down" to "down", "Swipe Left" to "left", "Swipe Right" to "right", "Long Press" to "long")
// Left and right turn the pages there, so only these two are free.
private val TILE_GESTURES = listOf("Swipe Up" to "tileUp", "Swipe Down" to "tileDown")
private val TAPS = listOf("Time" to "clock", "Date" to "date")

@Composable
fun SettingsPage(page: Page, resumeCount: Long, push: (Page) -> Unit, pop: () -> Unit, askPhone: () -> Unit) {
    val context = LocalContext.current
    val s = Store.settings
    val system: (String) -> Unit = { context.startActivity(Intent(it).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    /** A page of rows that each choose what something opens. */
    @Composable
    fun targets(title: String, rows: List<Pair<String, String>>) = Screen(title, pop) {
        for ((label, key) in rows) NavRow(label, Apps.targetLabel(s.target(key))) { push(Page.Pick(label, key)) }
    }
    when (page) {
        Page.Settings -> Screen("Home Screen Settings", pop) {
            NavRow("Customize Layout") { push(Page.Layout) }
            NavRow("Appearance") { push(Page.Appearance) }
            NavRow("Interaction Settings") { push(Page.Interaction) }
            NavRow("Notification Settings") { push(Page.Notifications) }
            NavRow("Permissions") { push(Page.Permissions) }
        }

        Page.Layout -> Screen("Customize Layout", pop) {
            NavRow("Home Style") { push(Page.HomeStyle) }
            NavRow("Home Screen Items") { push(Page.HomeItems) }
            NavRow("Home Screen Widgets") { push(Page.Widgets) }
            NavRow("App List View") { push(Page.AppListView) }
        }
        Page.HomeItems -> targets("Home Screen Items", HOME_ITEMS)
        Page.Widgets -> Screen("Home Screen Widgets", pop) {
            SwitchRow("Weather", checked = s.weather) { on -> Store.update { it.copy(weather = on) } }
            SwitchRow("Music Player", "Appears when you play music", s.music) { on -> Store.update { it.copy(music = on) } }
            if (s.music) {
                SectionHeader("Player Style")
                RadioRow("Minimal", s.musicStyle == MUSIC_MINIMAL) { Store.update { it.copy(musicStyle = MUSIC_MINIMAL) } }
                RadioRow("Kalm", s.musicStyle == MUSIC_KALM) { Store.update { it.copy(musicStyle = MUSIC_KALM) } }
            }
        }
        Page.AppListView -> Screen("App List View", pop) {
            SectionHeader("Layout")
            RadioRow("List", s.appsLayout == LAYOUT_LIST) { Store.update { it.copy(appsLayout = LAYOUT_LIST) } }
            RadioRow("Tiles", s.appsLayout == LAYOUT_TILES) { Store.update { it.copy(appsLayout = LAYOUT_TILES) } }
            // Both stay reachable whichever layout is on: each keeps its own settings.
            NavRow("List Options") { push(Page.ListOptions) }
            NavRow("Tile Options") { push(Page.TileOptions) }
            // The tiles have no overflow menu, so hidden apps are managed from here too.
            NavRow("Manage app list") { push(Page.ManageApps) }
        }
        Page.TileOptions -> Screen("Tile Options", pop) {
            SwitchRow("App Names", checked = s.tileNames) { on -> Store.update { it.copy(tileNames = on) } }
            NavRow("Order", TILE_ORDERS.firstOrNull { it.second == s.tileSort }?.first) { push(Page.TileOrder) }
        }
        Page.TileOrder -> Screen("Order", pop) {
            for ((label, sort) in TILE_ORDERS) RadioRow(label, s.tileSort == sort) { Store.update { it.copy(tileSort = sort) } }
        }
        Page.ListOptions -> Screen("List Options", pop) {
            SwitchRow("Search Bar", checked = s.searchBar) { on -> Store.update { it.copy(searchBar = on) } }
            SwitchRow("Notification shortcut", checked = s.bell) { on -> Store.update { it.copy(bell = on) } }
            SwitchRow("Scroll Bar", checked = s.scrollBar) { on -> Store.update { it.copy(scrollBar = on) } }
        }

        Page.Appearance -> Screen("Appearance", pop) {
            NavRow("Wallpaper") { push(Page.Wallpaper) }
            SwitchRow("Offline+ Wallpaper", "Night version while Offline+ is on", s.offlineWallpaper) { on -> Store.update { it.copy(offlineWallpaper = on) } }
        }
        Page.Wallpaper -> WallpaperPage(pop)
        Page.HomeStyle -> HomeStylePage(pop)

        Page.Interaction -> Screen("Interaction Settings", pop) {
            NavRow("Home Screen Gestures") { push(Page.Gestures) }
            NavRow("App Tiles Gestures") { push(Page.TileGestures) }
            NavRow("Search Behavior") { push(Page.SearchBehavior) }
            NavRow("Tap Actions") { push(Page.TapActions) }
        }
        Page.SearchBehavior -> Screen("Search Behavior", pop) {
            SwitchRow("Auto-open keyboard", checked = s.autoKeyboard) { on -> Store.update { it.copy(autoKeyboard = on) } }
            SwitchRow("Auto-open app", "When it’s the only search result", s.autoOpenApp, bold = true) { on -> Store.update { it.copy(autoOpenApp = on) } }
        }
        Page.Gestures -> targets("Home Screen Gestures", GESTURES)
        Page.TileGestures -> targets("App Tiles Gestures", TILE_GESTURES)
        Page.TapActions -> targets("Tap Actions", TAPS)
        is Page.Pick -> Screen(page.title, pop) {
            val selected = s.target(page.key)
            ScrollColumn {
                item { RadioRow("None", selected.isEmpty()) { Store.setTarget(page.key, "") } }
                item { SectionHeader("Use to open") }
                items(listOf(OPEN_APPS, OPEN_INBOX, OPEN_SETTINGS)) { target ->
                    RadioRow(Apps.targetLabel(target), target == selected) { Store.setTarget(page.key, target) }
                }
                item { SectionHeader("Use to open app") }
                items(Apps.sorted(), key = { it.pkg }) { app ->
                    RadioRow(Apps.name(app.pkg), app.pkg == selected) { Store.setTarget(page.key, app.pkg) }
                }
            }
        }

        Page.Notifications -> Screen("Notification Settings", pop) {
            SwitchRow("Enable Inbox", checked = s.inbox) { on -> Store.update { it.copy(inbox = on) } }
            NavRow("Notification Badges") { push(Page.Badges) }
            NavRow("Manage Notifications") { push(Page.InboxApps) }
            NavRow("Notification Sounds") { push(Page.SoundApps) }
        }
        Page.Badges -> Screen("Notification Badges", pop) {
            RadioRow("None", s.badges == 0) { Store.update { it.copy(badges = 0) } }
            RadioRow("Dots", s.badges == 1, trailing = { Badge(1, 1) }) { Store.update { it.copy(badges = 1) } }
            RadioRow("Dots with numbers", s.badges == 2, trailing = { Badge(1, 2) }) { Store.update { it.copy(badges = 2) } }
        }
        Page.InboxApps -> Screen("Manage Notifications", pop) {
            ScrollColumn {
                items(Apps.sorted(), key = { it.pkg }) { app ->
                    SwitchRow(Apps.name(app.pkg), checked = app.pkg !in s.inboxOff) { on ->
                        Store.update { it.copy(inboxOff = if (on) it.inboxOff - app.pkg else it.inboxOff + app.pkg) }
                    }
                }
            }
        }
        Page.SoundApps -> Screen("Notification Sounds", pop) {
            ScrollColumn {
                items(Apps.sorted(), key = { it.pkg }) { app ->
                    SwitchRow(Apps.homeLabel(app.pkg), checked = app.pkg in s.loud) { on ->
                        Store.update { it.copy(loud = if (on) it.loud + app.pkg else it.loud - app.pkg) }
                    }
                }
            }
        }

        Page.ManageApps -> Screen("Manage Apps", pop) {
            ScrollColumn {
                items(Apps.listed(), key = { it.pkg }) { app ->
                    SwitchRow(Apps.name(app.pkg), checked = app.pkg !in s.hidden) { on ->
                        Store.update { it.copy(hidden = if (on) it.hidden - app.pkg else it.hidden + app.pkg) }
                    }
                }
            }
        }

        Page.Permissions -> Screen("Permissions", pop) {
            val listener = remember(resumeCount) { listenerEnabled(context) }
            val phone = remember(resumeCount) { Counts.hasPhoneAccess(context) }
            val home = remember(resumeCount) { isDefaultHome(context) }
            SwitchRow("Notification access", "Reads and controls notifications", listener) {
                system(SystemSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            }
            SwitchRow("Calls and SMS", "Counts missed calls and SMS", phone) {
                if (phone) system(SystemSettings.ACTION_MANAGE_APPLICATIONS_SETTINGS) else askPhone()
            }
            SwitchRow("Default launcher", "Sets this as your Home Screen", home) {
                system(SystemSettings.ACTION_HOME_SETTINGS)
            }
        }

        else -> {}
    }
}

/**
 * Kalm's picker page, shared by the wallpapers and the home styles: a framed miniature of the home screen with
 * previous and next buttons and "12 / 13" between them. "Set" appears once something other than [saved] is showing.
 */
@Composable
private fun PickerPage(title: String, count: Int, saved: Int, pop: () -> Unit, set: (Int) -> Unit, preview: @Composable (Int) -> Unit) {
    var index by remember { mutableIntStateOf(saved.coerceIn(0, count - 1)) }
    Screen(title, pop, action = if (index != saved) ({ HeaderButton("Set") { set(index) } }) else null) {
        Box(Modifier.fillMaxSize()) {
            val frame = RoundedCornerShape(12.px)
            // Positions are from the top of the screen in Kalm; this box starts under the 135px header.
            Box(Modifier.offset(96.px, 44.px).size(288.px, 481.px).border(3.px, Color.Black, frame).clip(frame)) { preview(index) }
            val button = RoundedCornerShape(16.px)
            Box(Modifier.offset(43.px, 547.px).size(75.px).border(3.px, Color.Black, button).tap {
                index = (index + count - 1) % count
            }, contentAlignment = Alignment.Center) { Icon(36, draw = BackArrow) }
            BasicText("${index + 1}", Modifier.offset(180.px, 570.px), style = Body)
            BasicText("/", Modifier.offset(236.px, 570.px), style = Body)
            BasicText("$count", Modifier.offset(276.px, 570.px), style = Body)
            Box(Modifier.offset(362.px, 547.px).size(75.px).border(3.px, Color.Black, button).tap {
                index = (index + 1) % count
            }, contentAlignment = Alignment.Center) { Icon(36, draw = ForwardArrow) }
        }
    }
}

/** The home screen at six tenths of its size, as in Kalm's previews: a wallpaper in a home style, with stand-in text. */
@Composable
private fun MiniHome(wallpaper: String, style: Int) = Box(Modifier.fillMaxSize()) {
    Wallpaper(wallpaper, Modifier.fillMaxSize(), offline = Store.settings.offlineWallpaper && OfflinePlus.on, style = style)
    val side = styleSide(style)
    val across = listOf(Alignment.TopStart, Alignment.TopCenter, Alignment.TopEnd)[side]
    @Composable
    fun line(label: String, y: Int, size: Int) = Box(Modifier.fillMaxWidth().offset(0.px, y.px).padding(horizontal = 28.px)) {
        BasicText(label, Modifier.align(across), style = text(size))
    }
    line("12:00", 60, 38)
    line("Mon, Jan 1", 108, 19)
    if (styleButtons(style)) {
        val shape = RoundedCornerShape(13.px)
        for (x in listOf(14, 147)) for (y in listOf(332, 403)) {
            Box(Modifier.offset(x.px, y.px).size(126.px, 64.px).background(Color.White, shape).border(2.px, Color.Black, shape))
        }
    } else listOf("Phone", "SMS", "Shortcut", "Apps").forEachIndexed { i, label -> line(label, 311 + 39 * i, 17) }
}

@Composable
private fun WallpaperPage(pop: () -> Unit) {
    val context = LocalContext.current
    PickerPage("Wallpaper", WALLPAPERS.size, WALLPAPERS.indexOf(Store.settings.wallpaper), pop, { index ->
        Store.update { it.copy(wallpaper = WALLPAPERS[index]) }
        Toast.makeText(context, "Wallpaper set", Toast.LENGTH_SHORT).show()
    }) { index -> MiniHome(WALLPAPERS[index], Store.settings.homeStyle) }
}

/** Kalm's six home styles, shown on the current wallpaper. */
@Composable
private fun HomeStylePage(pop: () -> Unit) {
    val context = LocalContext.current
    PickerPage("Home Style", HOME_STYLES, Store.settings.homeStyle, pop, { index ->
        Store.update { it.copy(homeStyle = index) }
        Toast.makeText(context, "Home style set", Toast.LENGTH_SHORT).show()
    }) { index -> MiniHome(Store.settings.wallpaper, index) }
}
