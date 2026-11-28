# Kalmer

A copy of Mudita's Kalm launcher (`com.mudita.launcher2`, MuditaOS K 2.0.0) with three things made configurable: the home screen app slots, what the time and date open, and which apps show an unread count on the home screen. Kalm's source isn't published, so every screen is rebuilt from measurements of Kalm running on the phone.

## How to measure Kalm

Screenshot the same screen in both launchers and compare the edges of each element with the scripts in a scratch folder: decode the PNGs, take the bounding box of dark pixels in a region, and print Kalm's box beside Kalmer's. Text that is the right height but the wrong width means the size is off by a fraction; adjust until the widths agree. Everything listed below agrees with Kalm to within 1px unless it says otherwise.

`uiautomator dump` gives exact pixel bounds for every text and tap target, in both Kalm and Kalmer. Match the bounds first, then compare screenshots. All sizes in the code are those pixel values, written as `N.px` (see `Ui.kt`).

Kalm's APK is at `/system/system_ext/priv-app/MuditaLauncher2/MuditaLauncher2.apk`; `aapt2 dump` on a pulled copy gives its strings and resource names. The wallpapers in `app/src/main/assets/wallpapers` are Kalm's own, twelve designs each cut for its six home styles, bundled at the owner's decision so that Kalmer doesn't depend on Kalm being installed. The Lato fonts are bundled under the SIL Open Font License (`LICENSE-Lato.txt`).

## Measurements (px on the 480x800 screen)

**Home, "text left" style**
- Time row at (48, 101), 77 high; digits 64px Lato Medium; AM/PM 18px, raised; alarm bell 40px to the left when an alarm is set.
- Date at (48, 169), 64 high, 32px.
- Items start at x=48, each 65 high, 20sp; the last one ("Apps") ends at y=764.
- Wallpaper SVG is 356x593 units, drawn across the full screen. Each wallpaper has a variant per home style (`L_Text_Left.svg`).

**Settings**
- Header 133 high: back arrow in a 63px box at (6, 59), title 33px Lato Black at (74, 70), then a 2px solid rule.
- Rows 107 high plus a 1px dotted divider starting at x=21 (x=80 on radio rows). Label 27px at x=21, detail line 23px under it.
- Chevron near x=443. Switch 69x42 at x=390. Radio 35px at x=21, label at x=75.

**App list**
- Search pill at (21, 58), 321x64, 3px border. Overflow button in a 63px box at (412, 59).
- Rows 85 high, 20sp at x=21. Search text 30px. The header rule here is 3px, not 2.
- Scroll bar 7px wide at x=462, starting 12px under the rule.
- Pinned apps come first, then a 1px solid line from x=21 to x=451 on the row boundary.
- The bell gains a filled dot at its top right while the inbox has entries.

**App list contents**: Kalm leaves Phone, SMS, Contacts, DAVx5 and the NXP test app out of its list and its manage-apps page; so does Kalmer (`Apps.UNLISTED`). They remain choosable for home items, taps and gestures.

**Search**: matches any app whose name contains the text, listed by name, with no quick access area. A filled 37px clear button appears in the pill once something is typed. Interaction Settings > Search Behavior has "Auto-open keyboard" and "Auto-open app" (when it's the only result), both off by default; the second row's label is Lato Black.

**Quick access area**: the overflow menu's "Quick access area" sheet chooses "Pinned apps" or "Most used apps". Most used shows up to five apps as plain rows above the same fine line, and repeats them in the list below as Kalm does. Kalm marks them with a star and pinned apps with a pin; both are jarring, so the quick access area has no icons and the line alone sets it apart. Kalm's ranking couldn't be reproduced from Android's usage statistics (neither launch counts nor screen time gave its order), so Kalmer counts the apps it opens itself.

**Count badge**: black 37px circle, white 22px Lato Black number, 12px after the label.

**Inbox**: a history, not a view of live notifications: entries stay after the notification is dismissed. One 107px row per app: the notification's small icon at x=24, name 28px Lato Black at x=79, count badge 11px after it, "37 min. ago" in 20px at the top right, expand chevron in a 63px box at (409, row+38). Expanded entries are 107px rows: title 28px Lato Black at (43, row+20), text 23px at (43, row+57), time top right. "Clear all" is an outlined button fixed at (16, 720), 448x64.

**Sheets** (long-press an app, overflow menu): solid 3px rule, title 33px Lato Black, buttons 448x63 at x=16 on an 85px pitch with a 3px border and 10px corners, last button filled, 16px below it.

**Radio pickers**: section headings ("USE TO OPEN") are 24px Lato Black capitals in a 92px band, then a dotted divider.

**Music bar**: white box at (17, 268), 446x103, 4px border, 24px corners. Five 75px buttons at y=283, x=29, 117, 203, 289, 377: open player, previous, play/pause, next, dismiss. Dismiss also pauses the music, as Kalm's does; the bar returns when music next plays.

**Weather**: at (48, 212), 64 high, 32px temperature then a 30px conditions icon. Data is `content://com.mudita.weather/widget_weather` (columns `temperature`, `is_error`, `weather_icon_info` as an OpenWeatherMap code), readable with the normal-level permission `com.mudita.weather.PERMISSION_READ_WEATHER_DATA`.

**Wallpaper picker**: framed preview at (96, 179), 288x481. Previous and next buttons 75px at (43, 682) and (362, 682), counter between them. Kalm's order is none, then A to M without G.

## Differences from Kalm

Added:
- All four home items are configurable: each opens nothing, the app list, the inbox, settings or any app (Customize Layout > Home Screen Items). Kalm has two fixed, one shortcut and "Apps". Keep it at four: a fifth row runs into several wallpapers.
- The weather line keeps its last good reading. Kalm's disappears whenever the Weather app reports an error or has no data.
- Interaction Settings > Tap Actions for the time and date.
- A home item shows its app's count dot, the same count the app list shows. One list, Notification Settings > Manage Notifications, switches an app off everywhere: inbox, app list and home screen. (A separate "Home Screen Notifications" list was tried and removed as confusing.)
- Inbox entries go once they've been seen, unlike Kalm's, which stay until cleared. An app's entries go when one of its notifications is tapped or the app is opened by any route. An entry also goes when its app takes the notification down or it is swiped away. Opening an app by another route is detected through usage access: `adb.exe shell appops set com.kongeskog.kalmer GET_USAGE_STATS allow`. Notifications Kalmer cancels to keep the phone quiet stay in the inbox.
- A short vibration on the long-press gesture.
- A minimal music player, the default (Home Screen Widgets > Player Style: Minimal / Kalm). It has no box. A 36px play/pause icon at (48, 274), its ink 20px below the weather, sits in a 52×64px tap cell. Next comes the track title (artist, or "Music", if there is none) in 24px Lato from x=100, 148px wide with an ellipsis. Last is a 28px ✕ at x=260–288. As on Kalm's bar, it pauses the music and hides the player until music next starts playing. Measured across all twelve wallpapers, the artwork stays right of x=283 above y=330 (wallpaper L's tree is the tightest) and spans the full width from y=336. So the player keeps inside x 48–296, y 268–316. Tapping the title opens the media app. There's no previous or next.

Different:
- Pinned apps are listed once, first, with a dotted divider under the last one. Kalm marks them with a pin icon and can list them twice; Kalmer does neither.
- Icons are redrawn by eye; Kalm's are compiled into its code.

Not built yet: font size and type (Appearance in Kalm: six sizes, five typefaces), folders, and the status bar and date/time widget switches (Home Screen Widgets in Kalm).

## Home styles

Customize Layout > Home Style, as in Kalm: six styles in Kalm's order, `Settings.homeStyle` 0 to 5. Text left, centre, right, then buttons with the clock left, centre, right. Measured from Kalm in each style.

- **Clock, date, weather**: against x=48, centred on 240, or ending at x=432, at the same heights in every style. The minimal music player follows the same side with a 40px margin; Kalm's boxed player is full width in all of them.
- **Text items**: the same column against the left margin, centred, or ending at x=432. A count goes after the name, or before it when the names are on the right; in the centre an equal blank goes before the name so the name stays centred.
- **Buttons**: four at (24, 554), (245, 554), (24, 672), (245, 672), each 210x107 with a 3px border and 22px corners, white inside. Items fill them in order; an empty item leaves no gap. Kalm draws its own Phone, SMS and Apps glyphs and shows the shortcut app's icon at about 37px. Kalmer's items can be anything, so: the app list, inbox and settings have drawn glyphs; `com.mudita.*` apps show the middle two thirds of their own icon, which is the line drawing without its rounded square, at Kalm's glyph size; other apps show their icon at 37px, as Kalm does. A count sits up and to the right of the icon; Kalm's placement of it was not seen.
- **Wallpapers**: every design has a cut per style, named `L_Text_Center.svg`, `L_Buttons_Right.svg` and so on. A to F have one `_Buttons.svg` for all three button styles. All 60 files from Kalm are in `app/src/main/assets/wallpapers`. Night versions exist only for J and L in text-left; in other styles the ordinary wallpaper stays.
- The wallpaper picker and the home style picker share `PickerPage` and the `MiniHome` preview, which shows the candidate in the current style or wallpaper.

## Offline+ wallpaper

While the Kompakt's Offline+ hardware switch is on, the home screen shows the night version of the current wallpaper where there is one. Bundled night versions are in `app/src/main/assets/wallpapers_night` (J and L so far), named like the original as `.svg` or `.png`. A file of the same name in `Android/data/com.kongeskog.kalmer/files/night/` on the phone takes its place. Otherwise the ordinary wallpaper stays. Appearance > Offline+ Wallpaper turns this off. Kalmer creates that folder itself on first start; if it is created over adb first, the app can't read it.

MuditaOS reports the switch with the broadcasts `android.intent.action.ACTION_HWSWITCH_LOCKED` and `..._UNLOCKED` (visible in logcat when the switch is flipped) and the global setting `HWSwitch_lock`. To test without touching the switch, send the broadcast to Kalmer only: `adb.exe shell am broadcast -a android.intent.action.ACTION_HWSWITCH_LOCKED -p com.kongeskog.kalmer`.

Night versions are black on white like the originals, same 356x593 canvas for SVG or 480x800 for PNG.

## Quiet notifications

Kalm keeps the phone quiet by cancelling other apps' notifications about 30ms after they arrive (seen in the event log as `notification_canceled` reason 10 from its `NotificationFilterService`); cancelling stops the sound, and Kalm keeps its own copy in the inbox. SMS and calls are left alone.

Kalmer does the same and makes it configurable: Notification Settings > Notification Sounds lists the apps allowed to sound, by default Phone and SMS. Everything else is cancelled on arrival after being recorded in the inbox. It only does this while Kalmer is the default home app, and never for call or alarm notifications or ones with a full-screen intent.

While Kalm still has notification access it goes on cancelling everything, whatever Kalmer allows. An app switched on in Notification Sounds will only actually sound once Kalm's access is off.

## Status bar clock

Kalm hides the status bar clock on its home screen through a system-only call. Kalmer gets the same result, pixel for pixel, by writing the secure setting `icon_blacklist` while the home screen is in front and clearing it on pause. That setting replaces SystemUI's built-in list (`config_statusBarIconsToExclude`, 30 slots on MuditaOS K 2.0.0), so `StatusClock` repeats that list and adds `clock`. If a system update changes the built-in list, stray icons will show on the home screen until the copy is updated. Needs `pm grant com.kongeskog.kalmer android.permission.WRITE_SECURE_SETTINGS`.

## Counts

Missed calls and unread SMS come from the call log and SMS database, as in Kalm, when the two permissions are granted. Every other app is counted from its live notifications, ignoring ongoing, group-summary and low-importance ones.

## Tiles layout

Not from Kalm. App List View > Layout swaps the app list for pages of icon tiles (`TilesScreen.kt`), three across, with no header, search or quick access area. Everything else on this page describes the list, which is unchanged.

- **Settings are separate.** The tiles have their own fields in `Settings` (`appsLayout`, `tileNames`, `tileSort`) and read none of the list's (`pinned`, `quickMostUsed`, `searchBar`, `bell`, `scrollBar`), so changing layout never alters the other one. Hidden apps and names are shared. `Store.update` writes every key, so a new field has to be added to `Settings`, `init()` and `update()`.
- **Few options, on purpose.** Only App Names and Order (A-Z, most recent, most used, custom; tiles only). Icon shape and size, swipe direction, wrapping and hiding the arrows were tried and dropped, as were page arrows and a copy of the first launcher's Previous and Next buttons.
- **Icons** (`Icons.kt`) are each app's own icon, drawn as supplied at 108px with no frame or recolouring. That size, the column centres (99, 240, 381) and the 28px names 7px below are measured from Mudita's first launcher, `com.mudita.launcher`. Framing every icon in a circle clashed with Mudita's icons, which carry their own rounded square.
- **Pages, not scrolling.** Rows start at y=60, 21px apart, and stop above a 64px bottom row: four rows with names, five without. The bottom row holds the page dots when there is more than one page. Swipes up and down on the tiles open whatever Interaction Settings > App Tiles Gestures says (targets `tileUp` and `tileDown`), by default nothing and the inbox. A bell in the bottom row was tried and removed at the owner's request, so the inbox has no visible control here. It had arrows too; the owner had them removed, so pages turn by swipe only.
- **Swipes** are sideways, 80px, as on the home screen. Back from the first page returns home. The home screen's defaults match: swipe left opens the apps page, down the inbox, up Mudita's camera (`com.mudita.camera`), and right nothing. Kalm opens its app list with a swipe up. All of these stay configurable under Interaction Settings > Home Screen Gestures; fixing them was tried and reverted. Saved choices are never overwritten by new defaults.
- **Reorder**, as Katapult does it (behaviour only, no code): long-press a tile and choose Reorder. That tile is ringed with a dashed line, and tapping another swaps the two. A tap on any tile picks it for the next swap, swipes still turn pages, and Done or Back finishes. Entering it copies the order on screen into `tileOrder` and sets Order to Custom; each swap is saved at once. Apps installed or unhidden later go at the end. Choosing another order keeps the custom one for later.
- **Counts** use the same `Counts.of` and `Badge` as the list, on a white disc at the icon's top right.
- Last-opened times for the "most recent" order are kept in `ink_home_recent`.
- Manage app list is also reachable from App List View, since the tiles have no overflow menu.
