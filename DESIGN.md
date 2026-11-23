# Kalmer

A copy of Mudita's Kalm launcher (`com.mudita.launcher2`, MuditaOS K 2.0.0) with three things made configurable: the home screen app slots, what the time and date open, and which apps show an unread count on the home screen. Kalm's source isn't published, so every screen is rebuilt from measurements of Kalm running on the phone.

## How to measure Kalm

Screenshot the same screen in both launchers and compare the edges of each element with the scripts in a scratch folder: decode the PNGs, take the bounding box of dark pixels in a region, and print Kalm's box beside Kalmer's. Text that is the right height but the wrong width means the size is off by a fraction; adjust until the widths agree. Everything listed below agrees with Kalm to within 1px unless it says otherwise.

`uiautomator dump` gives exact pixel bounds for every text and tap target, in both Kalm and Kalmer. Match the bounds first, then compare screenshots. All sizes in the code are those pixel values, written as `N.px` (see `Ui.kt`).

Kalm's APK is at `/system/system_ext/priv-app/MuditaLauncher2/MuditaLauncher2.apk`; `aapt2 dump` on a pulled copy gives its strings and resource names. None of Mudita's artwork is in this repository: Kalmer reads the wallpapers at runtime from the Kalm launcher installed on the phone (`createPackageContext("com.mudita.launcher2").assets`, `wallpapers/<name>.svg`). The Lato fonts are bundled under the SIL Open Font License (`LICENSE-Lato.txt`).

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

**Music bar**: white box at (17, 268), 446x103, 4px border, 24px corners. Five 75px buttons at y=283, x=29, 117, 203, 289, 377: open player, previous, play/pause, next, dismiss.

**Weather**: at (48, 212), 64 high, 32px temperature then a 30px conditions icon. Data is `content://com.mudita.weather/widget_weather` (columns `temperature`, `is_error`, `weather_icon_info` as an OpenWeatherMap code), readable with the normal-level permission `com.mudita.weather.PERMISSION_READ_WEATHER_DATA`.

**Wallpaper picker**: framed preview at (96, 179), 288x481. Previous and next buttons 75px at (43, 682) and (362, 682), counter between them. Kalm's order is none, then A to M without G.

## Differences from Kalm

Added:
- All four home items are configurable: each opens nothing, the app list, the inbox, settings or any app (Customize Layout > Home Screen Items). Kalm has two fixed, one shortcut and "Apps". Keep it at four: a fifth row runs into several wallpapers.
- The weather line keeps its last good reading. Kalm's disappears whenever the Weather app reports an error or has no data.
- Interaction Settings > Tap Actions for the time and date.
- A home item shows its app's count dot, the same count the app list shows. One list, Notification Settings > Manage Notifications, switches an app off everywhere: inbox, app list and home screen. (A separate "Home Screen Notifications" list was tried and removed as confusing.)
- A short vibration on the long-press gesture.

Different:
- Pinned apps are listed once, first, with a dotted divider under the last one. Kalm marks them with a pin icon and can list them twice; Kalmer does neither.
- Icons are redrawn by eye; Kalm's are compiled into its code.

Not built yet: the other five home styles (and their wallpaper variants), font size and type, folders, "most used apps", and the status bar and date/time widget switches.

## Offline+ wallpaper

While the Kompakt's Offline+ hardware switch is on, the home screen shows the night version of the current wallpaper, if one has been supplied: a file with the original's name (`L_Text_Left.svg` or `.png`) in `Android/data/com.kongeskog.kalmer/files/night/` on the phone's storage. Otherwise the ordinary wallpaper stays. Appearance > Offline+ Wallpaper turns this off. Kalmer creates the folder itself on first start; if it is created over adb first, the app can't read it.

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
