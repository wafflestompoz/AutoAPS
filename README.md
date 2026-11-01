# AutoAPS

Shows your Nightscout data on Android Auto: current glucose with trend arrow and delta, IOB, COB, and the active temp basal.

AutoAPS is **read-only** and **not a medical device**. Read [DISCLAIMER.md](DISCLAIMER.md) before using it.

```
┌──────────────────────────────────────────────┐
│ ◉ AutoAPS                                    │
│                                              │
│  142 mg/dL  ↗  +4                            │
│  3 min ago                                   │
│                                              │
│  IOB 1.23 U  ·  COB 18 g                     │
│                                              │
│  Temp basal 0.50 U/h (62%) · 26 min left     │
│  Not for treatment decisions. Confirm with…  │
│                                   [Refresh]  │
└──────────────────────────────────────────────┘
```

## How it works

- **Phone app** (`MainActivity`): accept the safety notice, enter your Nightscout address and a read-only access token, pick units, and test the connection.
- **Car app** (`StatusScreen`): one `PaneTemplate` from the Android for Cars App Library, refreshed every 60 seconds. Its title and row count never change, so updates count as template refreshes and never hit the host's step limit.
- **Home screen widget** (`widget/`): a Jetpack Glance widget in three sizes, refreshed about every 5 minutes by WorkManager. It follows Google's [Adapt widgets for cars](https://developer.android.com/design/ui/cars/guides/flows/widgets) guidance (responsive, no scrolling, light/dark, dynamic color), so it's ready for the Android Auto dashboard once Google's car widget support reaches users. Google hasn't published which third-party widgets the car will accept, so treat that as unconfirmed.
- **Data**: `GET /api/v2/properties/bgnow,delta,direction,iob,cob,basal`, falling back to `/api/v1/entries/sgv.json` on older sites. Units come from `/api/v1/status.json` unless the user overrides them. The token is sent as the standard `?token=` query parameter.
- **Safety behavior**: readings 15 minutes or older are labelled STALE and their trend arrow and delta are hidden; LOW/HIGH labels appear below 70 or above 180 mg/dL; there are no alarms, by design.

IOB and COB need the Nightscout `iob`/`cob` plugins and an uploader that sends device status (AAPS, Loop, OpenAPS). Temp basal needs the `basal` plugin, a profile, and Temp Basal treatments uploaded to Nightscout (AAPS does this).

## Project layout

| Path | What it is |
|---|---|
| `core/` | Plain Kotlin/JVM: Nightscout client, parser, display formatting. Its own Gradle build so it can be tested without the Android SDK. |
| `app/` | Android app: phone settings screen and the Android Auto car app. |
| `docs/distribution.md` | Google Play car-app category rules and how to ship this. |

## Build

Requirements: Android Studio (Ladybug or newer) or the Android SDK with platform 35, JDK 17+.

```sh
./gradlew :app:assembleDebug          # debug APK
cd core && ../gradlew test            # unit tests for the Nightscout logic
```

## Try it in a car or on the desktop head unit

1. Install the debug APK on your phone and complete setup in the AutoAPS app.
2. In the Android Auto app settings, tap **Version** ten times to unlock developer settings, then enable **Unknown sources** (needed for any car app not installed from Play).
3. Connect to the car, or run Google's Desktop Head Unit (`$ANDROID_HOME/extras/google/auto/desktop-head-unit`) with **Start head unit server** enabled in Android Auto's developer menu.
4. AutoAPS appears in the Android Auto launcher.

## Distribution

Android Auto has no health category, so a glucose display can't be published to Google Play as a car app without a category Google would accept. See [docs/distribution.md](docs/distribution.md) for the options. Until that's settled, the plan is signed APKs on GitHub Releases.

## License

MIT, see [LICENSE](LICENSE).
