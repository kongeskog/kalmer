package com.kongeskog.kalmer

import android.Manifest
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import com.caverock.androidsvg.SVG
import com.mudita.mmd.ThemeMMD
import kotlinx.coroutines.delay
import java.util.Date
import kotlin.math.abs

sealed interface Page {
    data object Apps : Page
    data object Inbox : Page
    data object ManageApps : Page
    data class Rename(val pkg: String) : Page
    data object Settings : Page
    data object Layout : Page
    data object HomeItems : Page
    data object Widgets : Page
    data object AppListView : Page
    data object ListOptions : Page
    data object TileOptions : Page
    data object TileOrder : Page
    data object Appearance : Page
    data object Wallpaper : Page
    data object HomeStyle : Page
    data object Interaction : Page
    data object SearchBehavior : Page
    data object Gestures : Page
    data object TileGestures : Page
    data object TapActions : Page
    /** Choose what one home item, tap or gesture opens. */
    data class Pick(val title: String, val key: String) : Page
    data object Notifications : Page
    data object Badges : Page
    data object InboxApps : Page
    data object SoundApps : Page
    data object Permissions : Page
}

class MainActivity : ComponentActivity() {
    private val stack = mutableStateListOf<Page>()
    private var resumed by mutableStateOf(false)
    /** Bumped on every resume so permission and default-launcher switches re-read their state. */
    private var resumeCount by mutableLongStateOf(0)

    private val phoneObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = Counts.refreshPhone(this@MainActivity)
    }
    private val weatherObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = Weather.refresh(this@MainActivity)
    }
    private val askPhone = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        watchPhone()
        resumeCount++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setDecorFitsSystemWindows(false)
        watchPhone()
        Weather.observe(this, weatherObserver)
        setContent {
            ThemeMMD {
                val push: (Page) -> Unit = { stack.add(it) }
                val pop: () -> Unit = { stack.removeLastOrNull() }
                val open: (String) -> Unit = { target ->
                    when (target) {
                        "" -> {}
                        OPEN_APPS -> { tilesPage.intValue = 0; push(Page.Apps) }
                        OPEN_INBOX -> push(Page.Inbox)
                        OPEN_SETTINGS -> push(Page.Settings)
                        else -> Apps.launch(this, target)
                    }
                }
                val onHome = resumed && stack.isEmpty()
                LaunchedEffect(onHome) { StatusClock.hide(this@MainActivity, onHome) }
                // On the home screen Back does nothing.
                BackHandler { pop() }
                when (val page = stack.lastOrNull()) {
                    null -> HomeScreen(resumed, open)
                    Page.Apps -> if (Store.settings.appsLayout == LAYOUT_TILES) TilesScreen(push, pop, open) else AppsScreen(push, pop)
                    Page.Inbox -> InboxScreen(push, pop)
                    is Page.Rename -> RenameScreen(page.pkg, pop)
                    else -> SettingsPage(page, resumeCount, push, pop) {
                        askPhone.launch(arrayOf(Manifest.permission.READ_CALL_LOG, Manifest.permission.READ_SMS))
                    }
                }
            }
        }
    }

    private fun watchPhone() {
        contentResolver.unregisterContentObserver(phoneObserver)
        if (Counts.hasPhoneAccess(this)) {
            contentResolver.registerContentObserver(CallLog.Calls.CONTENT_URI, true, phoneObserver)
            contentResolver.registerContentObserver(Uri.parse("content://sms"), true, phoneObserver)
        }
        Counts.refreshPhone(this)
    }

    /** The Home button, or opening the launcher again, always lands on the home screen. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        stack.clear()
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        resumeCount++
        window.decorView.removeCallbacks(showClock)
        Inbox.clearSeen(this)
        Counts.refreshPhone(this)
        Weather.refresh(this)
        OfflinePlus.read(this)
        // Set here as well as from the composition: a quick pause and resume doesn't recompose,
        // and onPause has already given the clock back.
        StatusClock.hide(this, stack.isEmpty())
    }

    override fun onPause() {
        super.onPause()
        resumed = false
        // Give the clock back once another app is on screen. Pressing Home on the home screen also pauses and
        // resumes the launcher at once, so wait a moment: onResume cancels it, and the clock doesn't flash.
        window.decorView.postDelayed(showClock, 200)
    }

    private val showClock = Runnable { StatusClock.hide(this, false) }

    override fun onDestroy() {
        contentResolver.unregisterContentObserver(phoneObserver)
        contentResolver.unregisterContentObserver(weatherObserver)
        super.onDestroy()
    }
}

/** The current time, updated on each minute boundary and only while the launcher is in front. */
@Composable
private fun rememberMinute(resumed: Boolean) = produceState(System.currentTimeMillis(), resumed) {
    value = System.currentTimeMillis()
    while (resumed) {
        delay(60_000 - System.currentTimeMillis() % 60_000 + 50)
        value = System.currentTimeMillis()
    }
}

/**
 * One of the wallpapers, drawn at screen size. An empty name is the plain white "none".
 *
 * The wallpapers are bundled in assets/wallpapers. With [offline] set, a night version is used instead where
 * there is one: a file the user has supplied (see [nightFile]) or, failing that, one bundled in
 * assets/wallpapers_night.
 */
@Composable
fun Wallpaper(name: String, modifier: Modifier = Modifier, offline: Boolean = false, style: Int = Store.settings.homeStyle) {
    val context = LocalContext.current
    val bitmap = remember(name, offline, style) {
        if (name.isEmpty()) null else {
            // Each wallpaper is cut once per home style. The older ones (A to F) have a single cut for all three button styles.
            val side = listOf("Left", "Center", "Right")[styleSide(style)]
            val file = if (!styleButtons(style)) "${name}_Text_$side" else {
                val cuts = context.assets.list("wallpapers").orEmpty()
                if ("${name}_Buttons_$side.svg" in cuts) "${name}_Buttons_$side" else "${name}_Buttons"
            }
            val metrics = context.resources.displayMetrics
            fun svg(open: () -> java.io.InputStream) = try {
                val svg = open().use { SVG.getFromInputStream(it) }
                svg.setDocumentWidth(metrics.widthPixels.toFloat())
                svg.setDocumentHeight(metrics.heightPixels.toFloat())
                Bitmap.createBitmap(metrics.widthPixels, metrics.heightPixels, Bitmap.Config.ARGB_8888).also {
                    svg.renderToCanvas(Canvas(it))
                }
            } catch (e: Exception) {
                null
            }
            fun png(open: () -> java.io.InputStream) = try {
                open().use { android.graphics.BitmapFactory.decodeStream(it) }
            } catch (e: Exception) {
                null
            }
            val night = if (!offline) null else {
                val own = nightFile(context, file)
                when {
                    own == null -> svg { context.assets.open("wallpapers_night/$file.svg") }
                        ?: png { context.assets.open("wallpapers_night/$file.png") }
                    own.extension == "svg" -> svg { own.inputStream() }
                    else -> png { own.inputStream() }
                }
            }
            night ?: svg { context.assets.open("wallpapers/$file.svg") }
        }
    }
    if (bitmap != null) Image(bitmap.asImageBitmap(), null, modifier, contentScale = ContentScale.FillBounds)
}

/**
 * A user-supplied night version of a wallpaper, which takes the place of any bundled one: an SVG or PNG named
 * like the original, in the "night" folder of Kalmer's files on shared storage
 * (Android/data/com.kongeskog.kalmer/files/night).
 */
fun nightFile(context: Context, file: String): java.io.File? {
    val dir = java.io.File(context.getExternalFilesDir(null) ?: return null, "night")
    return listOf("svg", "png").map { java.io.File(dir, "$file.$it") }.firstOrNull { it.isFile }
}

@Composable
private fun HomeScreen(resumed: Boolean, open: (String) -> Unit) {
    val context = LocalContext.current
    val s = Store.settings
    val now by rememberMinute(resumed)
    val alarmSet = remember(now) {
        context.getSystemService(android.app.AlarmManager::class.java).nextAlarmClock != null
    }
    Box(
        Modifier.fillMaxSize().background(Color.White)
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = {
                    val target = Store.settings.target("long")
                    if (target.isNotEmpty()) {
                        buzz(context)
                        open(target)
                    }
                })
            }
            .pointerInput(Unit) {
                var moved = Offset.Zero
                detectDragGestures(
                    onDragStart = { moved = Offset.Zero },
                    onDragEnd = {
                        val key = when {
                            maxOf(abs(moved.x), abs(moved.y)) < 80f -> null
                            abs(moved.y) >= abs(moved.x) -> if (moved.y < 0) "up" else "down"
                            else -> if (moved.x < 0) "left" else "right"
                        }
                        if (key != null) open(Store.settings.target(key))
                    },
                ) { _, amount -> moved += amount }
            }
    ) {
        Wallpaper(s.wallpaper, Modifier.fillMaxSize(), offline = s.offlineWallpaper && OfflinePlus.on)

        // Kalm's styles put the clock block and the items against the left margin, in the middle, or against the right.
        val side = styleSide(s.homeStyle)
        val across = listOf(Alignment.TopStart, Alignment.TopCenter, Alignment.TopEnd)[side]
        @Composable
        fun line(y: Int, margin: Int = 48, content: @Composable (Modifier) -> Unit) =
            Box(Modifier.fillMaxWidth().offset(0.px, y.px).padding(horizontal = margin.px)) { content(Modifier.align(across)) }

        line(101) { place -> Row(place.height(77.px).tap { open(s.target("clock")) }) {
            if (alarmSet) {
                // Kalm's alarm bell fills (48, 127) to (79, 159); the digits start 43px in.
                Box(Modifier.width(43.px)) { Icon(32, Modifier.offset(0.px, 26.px), AlarmBell) }
            }
            val is24 = DateFormat.is24HourFormat(context)
            BasicText(DateFormat.format(if (is24) "H:mm" else "h:mm", Date(now)).toString(), style = text(64))
            if (!is24) BasicText(
                DateFormat.format("a", Date(now)).toString().uppercase(),
                Modifier.padding(start = 11.px, top = 13.px), style = text(20),
            )
        } }
        line(169) { place -> BasicText(
            DateFormat.format("EEE, MMM d", Date(now)).toString(),
            place.height(64.px).wrapContentHeight().tap { open(s.target("date")) },
            // Kalm spaces the date slightly wider than Lato's default.
            style = text(32).copy(letterSpacing = 0.24.sp),
        ) }

        val temperature = Weather.temperature
        if (s.weather && temperature != null) line(212) { place -> Row(
            place.height(64.px).tap { open(WEATHER_APP) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText("$temperature°", style = text(32))
            Spacer(Modifier.width(6.px))
            Icon(30, draw = weatherIcon(Weather.icon))
        } }

        if (s.music && Media.active && !Media.dismissed) {
            if (s.musicStyle == MUSIC_KALM) MusicPlayer(open) else line(260, margin = 40) { place -> MinimalPlayer(place, side, open) }
        }

        val slots = SLOTS.map { s.target(it) }.filter { it.isNotEmpty() }
        fun count(target: String) = if (target == OPEN_INBOX) Inbox.shown().size else Counts.of(target)
        if (styleButtons(s.homeStyle)) slots.forEachIndexed { i, target ->
            // Kalm's four buttons, measured: 210x107, in two rows at y=554 and y=672.
            HomeButton(target, count(target), Modifier.offset((if (i % 2 == 0) 24 else 245).px, (if (i < 2) 554 else 672).px)) { open(target) }
        }
        else Column(
            Modifier.align(listOf(Alignment.BottomStart, Alignment.BottomCenter, Alignment.BottomEnd)[side]).padding(start = 48.px, end = 48.px, bottom = 36.px),
            horizontalAlignment = listOf(Alignment.Start, Alignment.CenterHorizontally, Alignment.End)[side],
        ) {
            for (target in slots) HomeItem(Apps.homeLabel(target), count(target), side) { open(target) }
        }
    }
}

/** A home item as text. Its count sits after the name, or before it when the names are against the right margin. */
@Composable
private fun HomeItem(label: String, count: Int, side: Int, onClick: () -> Unit) {
    val style = Store.settings.badges
    val badge = count > 0 && style != 0
    Row(Modifier.height(65.px).tap(onClick), verticalAlignment = Alignment.CenterVertically) {
        if (badge && side == 2) {
            Badge(count)
            Spacer(Modifier.width(12.px))
        }
        // In the centre, as much blank space as the count takes goes before the name, so the name itself stays centred.
        if (badge && side == 1) Spacer(Modifier.width((12 + if (style == 1) 15 else 37).px))
        BasicText(label, style = Item, maxLines = 1)
        if (badge && side != 2) {
            Spacer(Modifier.width(12.px))
            Badge(count)
        }
    }
}

/**
 * A home item as one of Kalm's buttons. Kalm draws Phone, SMS and Apps itself and shows the shortcut app's icon;
 * here every item can be anything, so Mudita's apps show the drawing from their own icon, without its square,
 * and other apps their icon as it is.
 */
@Composable
private fun HomeButton(target: String, count: Int, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.px)
    Box(
        modifier.size(210.px, 107.px).background(Color.White, shape).border(3.px, Color.Black, shape).tap(onClick),
        contentAlignment = Alignment.Center,
    ) {
        when (target) {
            OPEN_APPS -> Icon(36, draw = AppsGrid)
            OPEN_INBOX -> Icon(36, draw = Bell)
            OPEN_SETTINGS -> Icon(36, draw = Gear)
            else -> {
                val mudita = target.startsWith("com.mudita.")
                val size = if (mudita) 56 else 37
                val sizePx = with(androidx.compose.ui.platform.LocalDensity.current) { size.px.roundToPx() }
                Box(Modifier.size(size.px)) { AppIcon(target, sizePx, 28, glyph = mudita) }
            }
        }
        if (count > 0 && Store.settings.badges != 0) Box(Modifier.offset(38.px, (-24).px)) { Badge(count) }
    }
}

/**
 * The minimal player: a play/pause toggle and the track title on the bare wallpaper, under the weather.
 * Measured on all twelve wallpapers, the art stays right of x=283 above y=330 (L's tree is the tightest)
 * and spans the width from y=336, so everything drawn here keeps to x 48–296, y 268–316. Its icon sits 20px
 * below the weather: matching the 14px between the date and the weather looks cramped, as the icon is taller.
 */
@Composable
private fun MinimalPlayer(place: Modifier, side: Int, open: (String) -> Unit) {
    // On the right the ✕ is what meets the margin. Its ink stops 14px short of the row's end, so the row goes
    // 4px further right to bring it to x=430, where the "AM" and the date end. In the centre the row is as wide
    // as its title, so the whole group is centred, and 2px right because the ✕ has more blank beside it than the toggle.
    Row(place.offset(listOf(0, 2, 4)[side].px, 0.px).height(64.px), verticalAlignment = Alignment.CenterVertically) {
        // The 52x64px cell is the tap target.
        // Play and Pause are drawn on a 48px box, so they're scaled down to fit.
        val toggle = if (Media.playing) Pause else Play
        Box(Modifier.size(52.px, 64.px).tap {
            val controls = Media.controller?.transportControls
            if (Media.playing) controls?.pause() else controls?.play()
        }, contentAlignment = Alignment.Center) {
            // Both icons leave 9px of blank space on their left. Shift 4px so the ink starts at x=52: a solid shape lined up
            // exactly with the text (x=49) looks too far left.
            Box(Modifier.offset((-4).px, 0.px)) { Icon(36) { scale(36f / 48f, Offset.Zero) { toggle() } } }
        }
        Spacer(Modifier.width(8.px))
        BasicText(
            Media.title.ifBlank { Media.artist }.ifBlank { "Music" },
            (if (side == 1) Modifier.widthIn(max = 148.px) else Modifier.width(148.px))
                .height(64.px).wrapContentHeight().tap { Media.controller?.packageName?.let(open) },
            style = text(24), maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        // Like the ✕ on Kalm's bar: pauses and hides the player until music next starts playing. The icon ends at x=288.
        Box(Modifier.size(52.px, 64.px).tap { Media.close() }, contentAlignment = Alignment.Center) { Icon(28, draw = Cross) }
    }
}

/** Kalm's player bar: open the player, previous, play or pause, next, and dismiss. */
@Composable
private fun MusicPlayer(open: (String) -> Unit) {
    val shape = RoundedCornerShape(24.px)
    Box(Modifier.offset(17.px, 268.px).size(446.px, 103.px).background(Color.White, shape).border(4.px, Color.Black, shape)) {
        val controls = Media.controller?.transportControls
        @Composable
        fun button(x: Int, draw: DrawScope.() -> Unit, onClick: () -> Unit) =
            Box(Modifier.offset((x - 17).px, 15.px).size(75.px).tap(onClick), contentAlignment = Alignment.Center) { Icon(48, draw = draw) }
        button(29, MusicNote) { Media.controller?.packageName?.let(open) }
        button(117, Previous) { controls?.skipToPrevious() }
        button(203, if (Media.playing) Pause else Play) { if (Media.playing) controls?.pause() else controls?.play() }
        button(289, Next) { controls?.skipToNext() }
        button(377, Cross) { Media.close() }
    }
}
