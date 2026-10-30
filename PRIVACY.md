# Sky — Privacy Policy

Version 0.11.15. Last reviewed 27 October 2026.

## The short version

The developer collects nothing about you. There is no account, no sign-up, no analytics, no advertising and no crash reporting, and no server belonging to the developer that the app talks to. To show the weather, Sky has to say roughly where you are: your position, rounded to about a kilometre, or a town you chose, goes to Open-Meteo for the forecast and to RainViewer for the radar, and, for weather alerts in the United States, to the National Weather Service. If you have a weather station, it is read on your own network, or from Weather Underground if you choose that. Nothing else leaves the phone.

## Who is responsible

wander wildwood, the author of this version of the app. Contact: email@wanderthe.dev

Sky began as kRadar, an open-source radar app; the forecast, almanac, station and alert screens were added to it.

Because no personal data is transmitted to or held by the developer, there is no data controller relationship for the developer to administer. Where the app processes personal data, that processing happens on your own device, under your control.

## What the app stores, and where

Only what you set: a place you chose and, if you add one, a second place (each its name and its latitude and longitude, rounded to two decimal places), which of the two was shown last, which kind of weather station you have and its address on your network, the units you prefer, whether the weather goes to the lock screen, whether the notification for serious weather alerts is on, and, if you chose Weather Underground, your station ID and API key. The API key is sealed with AES-GCM under a key held in the phone's keystore, which cannot be taken off the phone; after a reinstall or on a new phone it has to be typed again.

Of the phone's own position, only the last one found is kept, already rounded to two decimal places (about a kilometre), so the lock screen can bring its weather up to date while the app is closed. Each new position replaces it; no history is kept. The forecast, air quality and radar are held in memory while the app is open and are not written to storage. The exception is a small cache for the lock screen and the apps listed below: the temperature, the condition, the day's high and low, and when rain or snow starts or stops, with the wind and gusts, the day's snow, whether heavy snow is falling or due, the time they were fetched, and the place the forecast was for (rounded to two decimals, with its time zone). For weather alerts, only the Weather Service's names for the alerts already seen are kept, each with when it runs out, so the same alert is not announced twice (each is forgotten a day after it ends), and, for the last alerts fetched, each one's kind, severity and times, and none of its text.

All of it lives in the app's own private storage on the phone. Uninstalling the app deletes it. There is no cloud copy and no backup held by the developer. The manifest sets `allowBackup="false"`, and its data-extraction rules leave the settings file (the places, the station and the Weather Underground key) out of both a cloud backup and a direct transfer to a new phone.

## Permissions, and what each is for

From `app/src/main/AndroidManifest.xml`:

- `INTERNET`: to fetch the forecast, air quality and place search from Open-Meteo, the radar from RainViewer and weather alerts from the National Weather Service, and to read your weather station if you set one, on your home network or from Weather Underground.
- `ACCESS_FINE_LOCATION` and `ACCESS_COARSE_LOCATION`: to know where to fetch the forecast for and where to centre the radar. Asked for only when you press the button to allow it. Read from Android's own location service, not Google's, and rounded to about a kilometre before anything else in the app sees it. While a place is set in settings, the phone's position is not read at all. Sky does not ask for background location.
- `CHANGE_WIFI_MULTICAST_STATE`: to hear a WeatherFlow Tempest, whose hub broadcasts its readings on the home network (UDP port 50222); Android discards those broadcasts unless an app asks to hear them. Held only while Sky is listening for a Tempest.
- `POST_NOTIFICATIONS`: to show a notification when a serious weather alert is issued for your place, only while Settings, Notifications for serious weather alerts, is on.

The libraries the app is built on add these, and the store lists them: `ACCESS_NETWORK_STATE`, `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED` and `FOREGROUND_SERVICE`. They are used by Android's WorkManager to run the half-hourly alert check when the phone has a network, and to keep it scheduled after a restart. They read nothing about you.

The store also lists `com.wanderwildwood.soramoyo.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` because it reads the APK. AndroidX declares it for its own internal receivers; it is signature-level and namespaced to this app, so nothing else can hold it and it grants no access to anything on the phone.

The app declares no cleartext traffic, so Android refuses plain http for all of it; everything on the internet is asked over https. The one plain-text request Sky ever makes is to your own Ecowitt gateway or Davis WeatherLink Live, which serve plain http and nothing else, at the address you typed. Android's network security config cannot say "any private address", so the app makes that request itself, over a plain socket, and only after checking that the address (and every address a name resolves to) is a private one: 10.x.x.x, 172.16-31.x.x, 192.168.x.x, 169.254.x.x, or the IPv6 equivalents (fc00::/7, fe80::/10). Any other address is refused and nothing is sent. Redirects are not followed. No other plain-text request is made.

Nothing a permission grants access to is sent anywhere, except as described in the next section.

## Who else receives anything

These services receive a request from the app, and see your IP address as any web request does. Nothing is routed through the developer.

Hosts contacted:

- **api.open-meteo.com** (Open-Meteo): the forecast, including its quarter-hour rain forecast for the next two hours, and its current conditions when your station cannot be reached. Sent: your position rounded to about a kilometre, or your chosen place, and the units.
- **air-quality-api.open-meteo.com** (Open-Meteo): air quality and pollen, with each forecast. Sent: the same rounded position.
- **geocoding-api.open-meteo.com** (Open-Meteo): only when you search for a place in settings. Sent: the words you typed and the phone's language.
- **api.rainviewer.com**, and the tile server its response names: the radar. Sent: the same rounded position, in the address of the map tile.
- **api.weather.gov** (US National Weather Service): weather alerts, with each forecast and, while the alert notification is on, about every half hour in the background when the phone has a network, at a time Android picks to suit the battery. Sent: the same rounded position, with the app's name and the address of its source repository, which the Weather Service asks callers to give. It answers only for places in the United States; elsewhere Sky shows no alerts.
- **api.weather.com** (Weather Underground): only if you choose Weather Underground as your station. Sent: your own station ID and API key.
- **Your own Ecowitt gateway or Davis WeatherLink Live**, at the address you enter, over your home network: only if you choose one. A Tempest is not contacted; the app listens for it.

The Almanac (the moon, moonrise and moonset, dawn, dusk and daylight) is worked out on the phone from the same rounded position or your chosen place, and contacts no host.

On the phone, to three other apps of the author's, each answered by name and no other:

- **Glance** (`com.wanderwildwood.hitome`), the lock-screen app: the weather lines above are handed to Glance, to show on the lock screen, where anyone holding the phone can read them. When the lock screen is looked at and the last forecast is more than half an hour old, Sky fetches a new one from the same hosts, for your chosen place or the phone's rounded position. Settings, The weather on the lock screen, turns it off.
- **Clock** (`com.wanderwildwood.tokei`), the alarm clock: with its weather line switched on in Clock's own settings, Clock can ask Sky for one short line of weather to show on its ringing screen. Sky fetches it the same way, for the same place, and hands it to Clock and nothing else.
- **Wander** (`com.wanderwildwood.sanpo`), the author's walking game: it can ask Sky what the sky is doing so the weather in its wood follows the real one. It is handed the sky (clear, fog, rain or snow, and whether rain or snow starts or stops in the next hours), the wind and gusts, the moon's phase and today's moonrise and moonset, the kind of the most significant US weather alert in force (none, heat, winter storm, smoke or other; never its text), whether heavy snow is falling or due, the snow over the next day, and the hemisphere (north, south or tropic; never where). It all comes from what Sky has already fetched, from the last forecast, and nothing is fetched for it alone. If the last forecast is older than half an hour, it is renewed for next time and Wander does without.

No analytics, advertising or crash-reporting service is contacted.

In About, "feed the llamas" opens a donation page (square.link), wanderthe.dev opens the author's website and the source link opens its public repository (github.com), in your browser and only when you press them. The app does not fetch either page and learns nothing about whether you went.

No data is sold, rented, or shared for advertising. There is no profiling, no personalization, and no automated decision-making about you.

## Legal basis (EU / EEA and UK)

Processing on the device is carried out for your own purposes and at your own direction. Where the app contacts an outside service, that request is made at your request and is necessary to provide the feature you asked for (Article 6(1)(b) GDPR); that operator is an independent controller for what it then does, under its own policy.

## Your rights

Under the GDPR and UK GDPR you have rights of access, rectification, erasure, restriction, portability and objection. Under the CCPA/CPRA and comparable North American laws you have rights to know, delete, correct and opt out of sale or sharing.

The developer holds no data about you, so there is nothing to request from them, and nothing has ever been sold or shared. You can exercise every one of these rights directly: the data is on your phone. Clear it in the app or in Android's app settings, or uninstall to remove all of it at once. For what Open-Meteo, RainViewer, the National Weather Service or Weather Underground keep of a request, ask them.

## Children

The app is suitable for all ages and does not knowingly collect anything from anyone, of any age, because the developer does not collect anything.

## Changes

If a later version changes what the app stores or which hosts it contacts, this policy is updated in the same release and the change is described in the release notes. The source is public, so any claim here can be checked against the code: <https://github.com/wanderwildwood/soramoyo>. GitHub reports its licence as GPL-3.0.
