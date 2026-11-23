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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
    data object Appearance : Page
    data object Wallpaper : Page
    data object Interaction : Page
    data object Gestures : Page
    data object SearchBehavior : Page
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
                        OPEN_APPS -> push(Page.Apps)
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
                    Page.Apps -> AppsScreen(push, pop)
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
        // Give the clock back before another app is on screen.
        StatusClock.hide(this, false)
    }

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
fun Wallpaper(name: String, modifier: Modifier = Modifier, offline: Boolean = false) {
    val context = LocalContext.current
    val bitmap = remember(name, offline) {
        if (name.isEmpty()) null else {
            val file = "${name}_Text_Left"
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

        Row(Modifier.offset(48.px, 101.px).height(77.px).tap { open(s.target("clock")) }) {
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
        }
        BasicText(
            DateFormat.format("EEE, MMM d", Date(now)).toString(),
            Modifier.offset(48.px, 169.px).height(64.px).wrapContentHeight().tap { open(s.target("date")) },
            // Kalm spaces the date slightly wider than Lato's default.
            style = text(32).copy(letterSpacing = 0.24.sp),
        )

        val temperature = Weather.temperature
        if (s.weather && temperature != null) Row(
            Modifier.offset(48.px, 212.px).height(64.px).tap { open(WEATHER_APP) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText("$temperature°", style = text(32))
            Spacer(Modifier.width(6.px))
            Icon(30, draw = weatherIcon(Weather.icon))
        }

        if (s.music && Media.active && !Media.dismissed) MusicPlayer(open)

        Column(Modifier.align(Alignment.BottomStart).padding(start = 48.px, bottom = 36.px)) {
            val slots = SLOTS.map { s.target(it) }.filter { it.isNotEmpty() }
            for (target in slots) {
                val count = if (target == OPEN_INBOX) Inbox.shown().size else Counts.of(target)
                HomeItem(Apps.homeLabel(target), count) { open(target) }
            }
        }
    }
}

@Composable
private fun HomeItem(label: String, count: Int, onClick: () -> Unit) {
    Row(Modifier.height(65.px).tap(onClick), verticalAlignment = Alignment.CenterVertically) {
        BasicText(label, style = Item, maxLines = 1)
        if (count > 0 && Store.settings.badges != 0) {
            Spacer(Modifier.width(12.px))
            Badge(count)
        }
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
        button(377, Cross) { Media.dismissed = true }
    }
}
