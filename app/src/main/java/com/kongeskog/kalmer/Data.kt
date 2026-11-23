package com.kongeskog.kalmer

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Process
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.CallLog
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Mudita's Kalm launcher. Kalmer doesn't use it; it is only left out of the app list, as Kalm leaves itself out. */
const val KALM = "com.mudita.launcher2"
const val DIAL = "com.mudita.dial"
const val SMS = "com.mudita.messages"

// Things a home item, tap or gesture can open that aren't apps.
const val OPEN_APPS = "@apps"
const val OPEN_INBOX = "@inbox"
const val OPEN_SETTINGS = "@settings"

/** Kalm's wallpapers in its order; position 1 of 13 is "none". */
val WALLPAPERS = listOf("", "A", "B", "C", "D", "E", "F", "H", "I", "J", "K", "L", "M")

// Four home items, as in Kalm: its wallpapers leave room for exactly four.
val SLOTS = listOf("slot0", "slot1", "slot2", "slot3")

/** What each home item, tap and gesture opens: "" for nothing, an OPEN_ constant, or a package name. */
private val DEFAULT_TARGETS = mapOf(
    "slot0" to DIAL, "slot1" to SMS, "slot2" to "", "slot3" to OPEN_APPS,
    "clock" to "com.mudita.alarm", "date" to "com.mudita.calendar",
    "up" to OPEN_APPS, "down" to "", "left" to "", "right" to "", "long" to OPEN_SETTINGS,
)

/** Everything the user can change. The defaults are Kalm's behaviour. */
data class Settings(
    val targets: Map<String, String> = DEFAULT_TARGETS,
    /** Apps switched off in Manage Notifications: no inbox entries and no count dots anywhere. */
    val inboxOff: Set<String> = emptySet(),
    /**
     * Apps allowed to make their notification sound. Everything else is silenced the way Kalm does it:
     * the notification is taken off the system the moment it arrives, which stops its sound, and lives on in the inbox.
     */
    val loud: Set<String> = setOf(DIAL, SMS),
    val inbox: Boolean = true,
    /** 0 none, 1 dots, 2 dots with numbers. */
    val badges: Int = 2,
    val wallpaper: String = "L",
    /** Show a wallpaper's night version while the Offline+ switch is on, where it has one. */
    val offlineWallpaper: Boolean = true,
    val pinned: Set<String> = emptySet(),
    val hidden: Set<String> = emptySet(),
    val names: Map<String, String> = emptyMap(),
    val searchBar: Boolean = true,
    val bell: Boolean = true,
    val scrollBar: Boolean = true,
    val music: Boolean = true,
    /** Search behaviour, as in Kalm: both off by default. */
    val autoKeyboard: Boolean = false,
    val autoOpenApp: Boolean = false,
    /** The quick access area at the top of the app list shows the most used apps instead of the pinned ones. */
    val quickMostUsed: Boolean = false,
    val weather: Boolean = true,
) {
    fun target(key: String) = targets[key] ?: ""
}

private fun Map<String, String>.encode() = entries.joinToString("\n") { "${it.key}\t${it.value}" }
private fun String.decode() = lines().filter { "\t" in it }.associate { it.substringBefore("\t") to it.substringAfter("\t") }

object Store {
    private lateinit var prefs: SharedPreferences
    var settings by mutableStateOf(Settings())
        private set

    fun init(context: Context) {
        prefs = context.getSharedPreferences("ink_home", Context.MODE_PRIVATE)
        val d = Settings()
        fun set(key: String, default: Set<String>) = prefs.getStringSet(key, default)!!.toSet()
        settings = Settings(
            targets = DEFAULT_TARGETS + (prefs.getString("targets", "")!!.decode()).let { saved ->
                // An earlier build had a fifth home item; move it into the fourth if that one is empty.
                val fifth = saved["slot4"]
                if (!fifth.isNullOrEmpty() && saved["slot3"].isNullOrEmpty()) saved - "slot4" + ("slot3" to fifth) else saved - "slot4"
            },
            inboxOff = set("inboxOff", d.inboxOff),
            loud = set("loud", d.loud),
            inbox = prefs.getBoolean("inbox", d.inbox),
            badges = prefs.getInt("badges", d.badges),
            wallpaper = prefs.getString("wallpaper", d.wallpaper)!!,
            offlineWallpaper = prefs.getBoolean("offlineWallpaper", d.offlineWallpaper),
            pinned = set("pinned", d.pinned),
            hidden = set("hidden", d.hidden),
            names = prefs.getString("names", "")!!.decode(),
            searchBar = prefs.getBoolean("searchBar", d.searchBar),
            bell = prefs.getBoolean("bell", d.bell),
            scrollBar = prefs.getBoolean("scrollBar", d.scrollBar),
            music = prefs.getBoolean("music", d.music),
            autoKeyboard = prefs.getBoolean("autoKeyboard", d.autoKeyboard),
            autoOpenApp = prefs.getBoolean("autoOpenApp", d.autoOpenApp),
            quickMostUsed = prefs.getBoolean("quickMostUsed", d.quickMostUsed),
            weather = prefs.getBoolean("weather", d.weather),
        )
    }

    fun update(change: (Settings) -> Settings) {
        val s = change(settings)
        settings = s
        prefs.edit()
            .putString("targets", s.targets.encode())
            .putStringSet("inboxOff", s.inboxOff)
            .putStringSet("loud", s.loud)
            .putBoolean("inbox", s.inbox)
            .putInt("badges", s.badges)
            .putString("wallpaper", s.wallpaper)
            .putBoolean("offlineWallpaper", s.offlineWallpaper)
            .putStringSet("pinned", s.pinned)
            .putStringSet("hidden", s.hidden)
            .putString("names", s.names.encode())
            .putBoolean("searchBar", s.searchBar)
            .putBoolean("bell", s.bell)
            .putBoolean("scrollBar", s.scrollBar)
            .putBoolean("music", s.music)
            .putBoolean("autoKeyboard", s.autoKeyboard)
            .putBoolean("autoOpenApp", s.autoOpenApp)
            .putBoolean("quickMostUsed", s.quickMostUsed)
            .putBoolean("weather", s.weather)
            .apply()
    }

    fun setTarget(key: String, value: String) = update { it.copy(targets = it.targets + (key to value)) }
}

data class AppEntry(val pkg: String, val systemLabel: String)

/** Installed apps that have a launcher activity. */
object Apps {
    var all by mutableStateOf(emptyList<AppEntry>())
        private set

    fun init(context: Context) {
        load(context)
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        context.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) = load(c)
        }, filter)
    }

    private fun load(context: Context) {
        val launcher = context.getSystemService(LauncherApps::class.java)
        all = launcher.getActivityList(null, Process.myUserHandle())
            .map { AppEntry(it.applicationInfo.packageName, it.label.toString()) }
            .filter { it.pkg != context.packageName && !hiddenBySystem(it.pkg) }
            .distinctBy { it.pkg }
    }

    /** Stock Android apps that Mudita keeps installed but leaves out of its app list. */
    private fun hiddenBySystem(pkg: String) =
        (pkg.startsWith("com.android.") && pkg != "com.android.settings") ||
            pkg.startsWith("com.mediatek.") || pkg.startsWith("org.chromium.") || pkg == KALM

    /** Apps Kalm keeps out of its app list and its manage-apps page. They can still be chosen for home items, taps and gestures. */
    private val UNLISTED = setOf(DIAL, SMS, "com.mudita.contacts", "at.bitfire.davdroid.mudita", "com.phdtaui.mainactivity")

    /** Every app that belongs in the app list, by name, including ones the user has hidden. */
    fun listed(): List<AppEntry> = sorted().filter { it.pkg !in UNLISTED }

    /** The user's name for an app if they renamed it, otherwise the app's own. */
    fun name(pkg: String): String =
        Store.settings.names[pkg] ?: all.firstOrNull { it.pkg == pkg }?.systemLabel ?: pkg

    /** Every app by name, for pickers and switch lists. */
    fun sorted(): List<AppEntry> = all.sortedBy { name(it.pkg).lowercase() }

    private fun shown(): List<AppEntry> = listed().filter { it.pkg !in Store.settings.hidden }

    /** The quick access area: the pinned apps by name, or up to five most used apps, most used first. */
    fun quick(): List<AppEntry> {
        val shown = shown()
        return if (Store.settings.quickMostUsed) Usage.top.mapNotNull { pkg -> shown.firstOrNull { it.pkg == pkg } }
        else shown.filter { it.pkg in Store.settings.pinned }
    }

    /**
     * The app list as shown: hidden apps left out, the quick access apps first. Pinned apps are not repeated
     * below, unlike in Kalm. Most used apps are, as in Kalm: they change from day to day,
     * and the alphabetical list underneath should stay put.
     */
    fun visible(): List<AppEntry> {
        val quick = quick()
        return quick + if (Store.settings.quickMostUsed) shown() else shown().filter { it !in quick }
    }

    /** Search results, as in Kalm: every app whose name contains what was typed, by name. */
    fun search(query: String): List<AppEntry> = shown().filter { name(it.pkg).contains(query.trim(), ignoreCase = true) }

    /** The label for a target on the home screen. Kalm calls its fixed items "Phone", "SMS" and "Apps". */
    fun homeLabel(target: String): String = when (target) {
        OPEN_APPS -> "Apps"
        OPEN_INBOX -> "Inbox"
        OPEN_SETTINGS -> "Settings"
        DIAL -> Store.settings.names[DIAL] ?: "Phone"
        SMS -> Store.settings.names[SMS] ?: "SMS"
        else -> name(target)
    }

    /** The label for a target in settings. */
    fun targetLabel(target: String): String = when (target) {
        "" -> "None"
        OPEN_APPS -> "App List"
        OPEN_INBOX -> "Notification Inbox"
        OPEN_SETTINGS -> "Home Screen Settings"
        else -> name(target)
    }

    fun launch(context: Context, pkg: String) {
        val intent = context.packageManager.getLaunchIntentForPackage(pkg) ?: return
        // Opening an app counts as having seen its notifications.
        Inbox.clear(pkg)
        Usage.opened(pkg)
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION))
    }
}

/**
 * Hides the clock in the status bar while the home screen is showing, as Kalm does, since the home screen has
 * its own. An ordinary app can't ask the status bar for this, so it is done through the system's list of hidden
 * status bar icons, which needs WRITE_SECURE_SETTINGS granted over adb. Without the permission nothing happens.
 */
object StatusClock {
    // Setting the list replaces Mudita's built-in one (MuditaOS K 2.0.0), so that is repeated here, plus the clock.
    private const val MUDITA_HIDDEN = "rotate,data_saver,managed_profile,ime,sync_failing,sync_active,cast,location,nfc,tty," +
        "speakerphone,zen,mute,volume,cdma_eri,secure,data_connection,phone_evdo_signal,phone_signal,alarm_clock,vpn," +
        "ethernet,microphone,camera,no_calling,sensors_off,screen_record,sim_3,sim_2,sim_1"
    private var hidden = false

    fun allowed(context: Context) =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    fun hide(context: Context, hide: Boolean) {
        if (hide == hidden || !allowed(context)) return
        try {
            // Null puts the system back on its built-in list, clock showing.
            android.provider.Settings.Secure.putString(context.contentResolver, "icon_blacklist", if (hide) "$MUDITA_HIDDEN,clock" else null)
            hidden = hide
        } catch (e: Exception) {
            // Leave the status bar as it is.
        }
    }
}

/**
 * The long-press vibration. Kalm plays the system's "heavy click" as touch feedback, for the home screen and
 * for apps in the list alike (read from `dumpsys vibrator_manager`).
 */
/**
 * The most used apps: Kalmer counts each app it opens and lists the five with the highest counts.
 * Counts are halved whenever one reaches 64, so the list follows current habits rather than all-time totals.
 */
object Usage {
    private lateinit var prefs: SharedPreferences
    private var counts = mapOf<String, Int>()
    var top by mutableStateOf(emptyList<String>())
        private set

    fun init(context: Context) {
        prefs = context.getSharedPreferences("ink_home_usage", Context.MODE_PRIVATE)
        counts = prefs.all.mapNotNull { (k, v) -> (v as? Int)?.let { k to it } }.toMap()
        rank()
    }

    fun opened(pkg: String) {
        var next = counts + (pkg to (counts[pkg] ?: 0) + 1)
        if (next.values.max() >= 64) next = next.mapValues { it.value / 2 }.filterValues { it > 0 }
        counts = next
        prefs.edit().clear().apply { next.forEach { (k, v) -> putInt(k, v) } }.apply()
        rank()
    }

    private fun rank() {
        top = counts.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }).take(5).map { it.key }
    }
}

fun isDefaultHome(context: Context): Boolean {
    val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    return context.packageManager.resolveActivity(home, 0)?.activityInfo?.packageName == context.packageName
}

fun buzz(context: Context) {
    val touch = android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).build()
    context.getSystemService(Vibrator::class.java)?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK), touch)
}

/** One notification kept in the inbox. [icon] is the resource id of the notification's small icon in [pkg]. */
data class Entry(val pkg: String, val title: String, val text: String, val time: Long, val icon: Int)

/**
 * The notification inbox. Like Kalm's, it is a history: an entry stays after its notification is dismissed,
 * until the inbox is cleared or the app is opened from the launcher.
 */
object Inbox {
    private const val SEP = "\u0001"
    private lateinit var prefs: SharedPreferences
    var entries by mutableStateOf(emptyList<Entry>())
        private set

    fun init(context: Context) {
        prefs = context.getSharedPreferences("ink_home_inbox", Context.MODE_PRIVATE)
        entries = prefs.getString("entries", "")!!.split("\u0002").mapNotNull { line ->
            val f = line.split(SEP)
            if (f.size != 5) null else Entry(f[0], f[1], f[2], f[3].toLongOrNull() ?: 0, f[4].toIntOrNull() ?: 0)
        }
    }

    private fun set(next: List<Entry>) {
        entries = next
        prefs.edit().putString("entries", next.joinToString("\u0002") {
            listOf(it.pkg, it.title, it.text, it.time, it.icon).joinToString(SEP)
        }).apply()
    }

    fun add(entry: Entry) {
        if (entries.any { it.pkg == entry.pkg && it.title == entry.title && it.text == entry.text }) return
        set((listOf(entry) + entries).take(200))
    }

    fun clear(pkg: String) { if (entries.any { it.pkg == pkg }) set(entries.filter { it.pkg != pkg }) }
    fun clearAll() = set(emptyList())

    /** Entries the user wants to see, newest first. */
    fun shown(): List<Entry> = if (!Store.settings.inbox) emptyList() else entries.filter { it.pkg !in Store.settings.inboxOff }
}

/** Unread counts per app. */
object Counts {
    var missedCalls by mutableStateOf<Int?>(null)
    var unreadSms by mutableStateOf<Int?>(null)

    /**
     * The count shown beside an app, on the home screen and in the app list alike. Calls and SMS come from the
     * system databases when allowed, as in Kalm; everything else from the inbox. Zero for apps switched off.
     */
    fun of(pkg: String): Int = if (pkg in Store.settings.inboxOff) 0 else when (pkg) {
        DIAL -> missedCalls
        SMS -> unreadSms
        else -> null
    } ?: Inbox.shown().count { it.pkg == pkg }

    fun hasPhoneAccess(context: Context) =
        context.checkSelfPermission(Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED &&
            context.checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    fun refreshPhone(context: Context) {
        missedCalls = if (context.checkSelfPermission(Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED)
            count(context, CallLog.Calls.CONTENT_URI, "${CallLog.Calls.TYPE}=${CallLog.Calls.MISSED_TYPE} AND ${CallLog.Calls.NEW}=1")
        else null
        unreadSms = if (context.checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED)
            count(context, Uri.parse("content://sms/inbox"), "read=0")
        else null
    }

    private fun count(context: Context, uri: Uri, where: String): Int? = try {
        context.contentResolver.query(uri, arrayOf("_id"), where, null, null)?.use { it.count }
    } catch (e: Exception) {
        null
    }
}

/**
 * The Kompakt's Offline+ hardware switch. MuditaOS announces each flip with a broadcast and keeps the current
 * position in the global setting HWSwitch_lock (1 while Offline+ is on).
 */
object OfflinePlus {
    private const val LOCKED = "android.intent.action.ACTION_HWSWITCH_LOCKED"
    private const val UNLOCKED = "android.intent.action.ACTION_HWSWITCH_UNLOCKED"
    var on by mutableStateOf(false)
        private set

    fun init(context: Context) {
        read(context)
        context.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) { on = i.action == LOCKED }
        }, IntentFilter().apply { addAction(LOCKED); addAction(UNLOCKED) })
    }

    /** Re-read the switch position, for when a flip happened while nothing was listening. */
    fun read(context: Context) {
        on = android.provider.Settings.Global.getInt(context.contentResolver, "HWSwitch_lock", 0) == 1
    }
}

const val WEATHER_APP = "com.mudita.weather"

/**
 * The temperature and conditions from Mudita's Weather app.
 *
 * Kalm's weather line vanishes whenever the Weather app reports an error or has nothing to give, for example
 * while it is offline. Here the last good reading is kept, across restarts too, until a newer one replaces it.
 */
object Weather {
    private val uri = Uri.parse("content://$WEATHER_APP/widget_weather")
    private lateinit var prefs: SharedPreferences
    var temperature by mutableStateOf<Int?>(null)
        private set
    /** Mudita's name for the conditions icon. */
    var icon by mutableStateOf("")
        private set

    fun init(context: Context) {
        prefs = context.getSharedPreferences("ink_home_weather", Context.MODE_PRIVATE)
        if (prefs.contains("temperature")) temperature = prefs.getInt("temperature", 0)
        icon = prefs.getString("icon", "")!!
    }

    fun observe(context: Context, observer: android.database.ContentObserver) = try {
        context.contentResolver.registerContentObserver(uri, true, observer)
    } catch (e: Exception) {
        // The Weather app isn't installed.
    }

    fun refresh(context: Context) {
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (!c.moveToFirst()) return
                fun column(name: String) = c.getColumnIndex(name).takeIf { it >= 0 && !c.isNull(it) }
                if (column("is_error")?.let { c.getInt(it) } ?: 0 != 0) return
                val t = column("temperature")?.let { c.getInt(it) } ?: return
                val info = column("weather_icon_info")?.let { c.getString(it) } ?: ""
                temperature = t
                icon = info
                prefs.edit().putInt("temperature", t).putString("icon", icon).apply()
            }
        } catch (e: Exception) {
            // No permission or no Weather app: keep whatever was last known.
        }
    }
}

/** Whatever is playing, for the home screen music player. */
object Media {
    var controller: MediaController? = null
        private set
    var title by mutableStateOf("")
    var artist by mutableStateOf("")
    var playing by mutableStateOf(false)
    /** True while a player has something loaded, playing or paused. */
    var active by mutableStateOf(false)
    /** Set by the bar's close button; cleared the next time playback starts. */
    var dismissed by mutableStateOf(false)

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = read()
        override fun onMetadataChanged(metadata: MediaMetadata?) = read()
        override fun onSessionDestroyed() = attach(null)
    }

    fun attach(next: MediaController?) {
        if (next?.sessionToken == controller?.sessionToken) return read()
        controller?.unregisterCallback(callback)
        controller = next
        next?.registerCallback(callback)
        read()
    }

    private fun read() {
        val c = controller
        val state = c?.playbackState?.state
        val nowPlaying = state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING
        if (nowPlaying && !playing) dismissed = false
        playing = nowPlaying
        active = playing || state == PlaybackState.STATE_PAUSED
        title = c?.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
        artist = c?.metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
    }
}

class NotifListener : NotificationListenerService() {
    private val sessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener { pickSession(it) }

    override fun onListenerConnected() {
        // Catch up on anything that arrived while the listener wasn't running.
        try { activeNotifications?.forEach(::record) } catch (e: Exception) {}
        try {
            val manager = getSystemService(MediaSessionManager::class.java)
            val me = ComponentName(this, NotifListener::class.java)
            manager.addOnActiveSessionsChangedListener(sessionsChanged, me)
            pickSession(manager.getActiveSessions(me))
        } catch (e: SecurityException) {
            // Notification access was just revoked.
        }
    }

    override fun onListenerDisconnected() {
        try { getSystemService(MediaSessionManager::class.java).removeOnActiveSessionsChangedListener(sessionsChanged) } catch (e: Exception) {}
        Media.attach(null)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        record(sbn)
        if (shouldSilence(sbn)) try { cancelNotification(sbn.key) } catch (e: Exception) {}
    }

    /** Whether this is the kind of notification the inbox keeps: something that would alert, not a status line. */
    private fun alerts(sbn: StatusBarNotification): Boolean {
        val n = sbn.notification
        if (sbn.packageName == packageName || sbn.isOngoing || n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return false
        // Skip quiet status notifications such as "background connection".
        val r = Ranking()
        return !(currentRanking.getRanking(sbn.key, r) && r.importance < NotificationManager.IMPORTANCE_DEFAULT)
    }

    /**
     * Kalm keeps the phone quiet by cancelling other apps' notifications as they arrive. Kalmer does the same,
     * but only while it is the home screen, never for calls, alarms or anything that takes over the screen,
     * and not for apps the user has allowed to make sound.
     */
    private fun shouldSilence(sbn: StatusBarNotification): Boolean {
        val n = sbn.notification
        if (!alerts(sbn) || sbn.packageName in Store.settings.loud) return false
        if (n.category == Notification.CATEGORY_CALL || n.category == Notification.CATEGORY_ALARM || n.fullScreenIntent != null) return false
        return isDefaultHome(this)
    }

    private fun pickSession(sessions: List<MediaController>?) {
        val list = sessions ?: emptyList()
        Media.attach(list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: list.firstOrNull())
    }

    private fun record(sbn: StatusBarNotification) {
        val n = sbn.notification
        if (!alerts(sbn)) return
        val title = n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = n.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        if (title.isBlank() && text.isBlank()) return
        val icon = n.smallIcon?.takeIf { it.type == android.graphics.drawable.Icon.TYPE_RESOURCE }?.resId ?: 0
        Inbox.add(Entry(sbn.packageName, title, text, sbn.postTime, icon))
    }
}

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        Apps.init(this)
        Weather.init(this)
        Inbox.init(this)
        OfflinePlus.init(this)
        // Create the folder for user-supplied night wallpapers, so it exists (and belongs to the app) before files are copied in.
        getExternalFilesDir(null)?.let { java.io.File(it, "night").mkdirs() }
        Usage.init(this)
    }
}
