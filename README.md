# AutoAPS

**Your Nightscout data on Android Auto and your phone's home screen.** AutoAPS shows current glucose with trend arrow and delta, insulin on board (IOB), carbs on board (COB), and the active temp basal, read straight from your own Nightscout site.

> [!WARNING]
> **AutoAPS is not a medical device.** It shows a copy of your Nightscout data, which can be delayed, missing or wrong. Never make treatment decisions from it. It has no alarms, so keep your CGM and loop alerts on. Don't look at it or set it up while driving. Read the full [safety notice](DISCLAIMER.md).

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

## Features

- **Android Auto screen** with glucose, trend, delta, IOB, COB and basal, updated every minute.
- **Home screen widget** in three sizes, updated about every 5 minutes. It's built to Google's guidelines for car widgets, so it's ready for the Android Auto dashboard once Google opens that to other apps.
- **Stale data is obvious.** After 15 minutes without a new reading, it's marked STALE and the trend arrow and delta are hidden.
- **LOW and HIGH labels** below 70 mg/dL (3.9 mmol/L) and above 180 mg/dL (10.0 mmol/L).
- **mg/dL or mmol/L**, taken from your Nightscout settings or chosen in the app.
- **Read-only.** AutoAPS never writes to Nightscout and can't dose insulin or control any device.
- **Private.** No accounts, analytics or ads. Your URL and token stay on your phone. See the [privacy policy](PRIVACY.md).

## What you need

- An Android phone running Android 8.0 or newer, with Android Auto.
- A Nightscout site, version 14 or newer recommended.
- For IOB, COB and temp basal: a looping app that uploads to Nightscout, such as AndroidAPS (AAPS), Loop or OpenAPS. With only a CGM uploader (for example xDrip+), you still get glucose, trend and delta, and the other fields show `--`.

## Install

AutoAPS isn't on Google Play. Android Auto only allows certain kinds of apps on Play, and health apps aren't one of them yet. You can read more in [docs/distribution.md](docs/distribution.md).

1. Download the latest `AutoAPS-x.y.z.apk` from [Releases](../../releases) and install it on your phone. You may need to allow installs from your browser or file manager.
2. Turn on Android Auto's developer settings. Open the Android Auto settings on your phone, scroll to **Version**, and tap it 10 times.
3. Allow unknown sources. Open the three-dot menu, choose **Developer settings**, and turn on **Unknown sources**.
4. Set up AutoAPS on your phone, as described below.
5. Connect to your car. AutoAPS appears in the Android Auto app launcher. If you don't see it, open **Customize launcher** in Android Auto settings and enable it there.

## Set up

### 1. Create a read-only token in Nightscout

Use a token, not your API secret. A token with the `readable` role can only read data.

1. Open your Nightscout site, then go to the menu and choose **Admin tools**.
2. Under **Subjects**, choose **Add new subject**. Name it `autoaps` and give it the `readable` role.
3. Copy the token shown next to it. It looks like `autoaps-1a2b3c4d5e6f7a8b`.

### 2. Enter it in the app

1. Open AutoAPS on your phone. Read the safety notice and tick **I understand and accept**.
2. Enter your Nightscout address, for example `https://my-cgm.example.com`. You can also paste a full link like `https://my-cgm.example.com/?token=…`, and the token is filled in for you.
3. Paste the token, pick your units, then tap **Test connection**. You should see your current reading.

### 3. Add the widget (optional)

Long-press your home screen, choose **Widgets**, find **AutoAPS glucose**, and drag it to your home screen. Resize it to show more or less detail. Tap the widget to open the app.

## Troubleshooting

| What you see | What to try |
|---|---|
| "Nightscout refused the token" | Check that the token's subject has the `readable` role, and that you copied the whole token. |
| "Can't reach Nightscout" | Open the address in your phone's browser. Your Nightscout site must use `https://`. |
| IOB, COB or basal show `--` | Nightscout needs the `iob`, `cob` and `basal` plugins enabled (in `ENABLE`), and your loop must be uploading device status. Basal also needs a profile in Nightscout. |
| AutoAPS missing from Android Auto | Check that **Unknown sources** is on in Android Auto's developer settings, then reconnect to the car. |
| Widget updates late | Android delays background work in battery saver and Doze. The widget always shows how old the reading is. |

## How it works

AutoAPS makes read-only `GET` requests to your Nightscout site:

- `/api/v2/properties/bgnow,delta,direction,iob,cob,basal` for current values.
- `/api/v1/entries/sgv.json?count=2` as a fallback on older sites, for glucose only.
- `/api/v1/status.json` to read your units.

The token is sent as the standard `?token=` parameter. The car screen uses a single Android for Cars `PaneTemplate` whose layout never changes, so the car treats each update as a refresh and never blocks it.

## Build from source

You need Android Studio (Ladybug or newer), or the Android SDK with platform 35 and JDK 17.

```sh
git clone https://github.com/wafflestompoz/AutoAPS.git
cd AutoAPS
./gradlew :app:assembleDebug      # APK in app/build/outputs/apk/debug/
cd core && ../gradlew test        # unit tests for the Nightscout logic
```

To try it without a car, run Google's [Desktop Head Unit](https://developer.android.com/training/cars/testing/dhu). Turn on **Start head unit server** in Android Auto's developer settings first.

| Folder | What's in it |
|---|---|
| `core/` | Nightscout client, parsing and display formatting in plain Kotlin, with unit tests. It builds without the Android SDK. |
| `app/` | The Android app: phone setup screen, Android Auto screen and home screen widget. |
| `demo/` | A browser preview of the car screen and widget using sample data. |
| `docs/` | Notes on distribution and Google Play. |

## Contributing

Issues and pull requests are welcome. Some rules keep AutoAPS safe:

- It stays **read-only**. No features that write treatments or send commands to a pump or loop.
- **No alarms, predictions or dosing suggestions.** Those would make AutoAPS regulated medical software (see [docs/distribution.md](docs/distribution.md)).
- Anything shown in the car must be readable at a glance, without scrolling.

If you change the safety notice, update `disclaimer_body` in `app/src/main/res/values/strings.xml` and increase `Settings.DISCLAIMER_VERSION` so existing users see the new text.

## Acknowledgements

AutoAPS is built on the work of the [Nightscout](https://nightscout.github.io/) community and depends on uploaders like [AndroidAPS](https://github.com/nightscout/AndroidAPS). It is an independent project, not affiliated with or endorsed by the Nightscout Foundation, AndroidAPS, Google or any device maker.

## License

[MIT](LICENSE). Provided "as is", without warranty of any kind. See the [safety notice](DISCLAIMER.md).
