# Shipping AutoAPS: Google Play car-app rules

_Written 2026-11-01. Google changes these rules often; re-check the [Android for Cars app quality guidelines](https://developer.android.com/docs/quality-guidelines/car-app-quality) and the [supported categories](https://developer.android.com/training/cars#supported-app-categories) before acting on this._

## The problem

Google Play only accepts Android Auto apps that fit a supported category: media, messaging and calling, navigation, points of interest (parking, EV charging, fuel), internet of things, weather, plus a few parked-only categories such as video and games. Each category has its own quality rules, and Play review checks that the app actually does what its category is for.

There is **no health or medical category**. A glucose display doesn't honestly fit any of the existing ones, and declaring a category the app doesn't serve (for example IoT, which is for controlling connected devices like garage doors) is the most likely way to get a Play submission rejected or the developer account flagged.

The manifest currently declares `androidx.car.app.category.POI` (IOT was tried first, but didn't appear in the Android Auto launcher in testing). That's only so the app shows up in the launcher for sideloaded installs; it is **not** a claim that Play would accept it.

## Options

| Option | Effort | Reach | Risk |
|---|---|---|---|
| **A. GitHub Releases (sideload)** | Low | Users must enable Android Auto's "Unknown sources" developer setting | No Play review. Android Auto can change how sideloaded apps work. |
| **B. Phone app on Play, car app on GitHub** | Medium | Play users get the phone setup screen; car support stays sideloaded | Two builds to maintain (a `play` flavor without the car service). Little benefit unless the phone app does more. |
| **C. Fit an honest Play category** | High | Full Play reach | Means changing what the app is, e.g. a messaging-style app that posts readings as car-friendly notifications. Still at Google's discretion; review outcome unknown. |
| **D. Ask Google** | Low | Full, if granted | Request a category or exception through the Android for Cars developer channels. Slow and uncertain, but it's the only approved route for a health display. |

**Recommendation:** ship with **A** now, and pursue **D** in parallel. Revisit **C** only if Google offers no route and Play reach matters more than keeping the app a simple display.

Some community apps present glucose as "now playing" media metadata or as messaging notifications to appear on Android Auto. These work on the head unit, but publishing them on Play under the media or messaging category carries the same category-misuse risk; they aren't a policy workaround.

## If it does go on Google Play

Separate from the car category, a Play listing needs:

- **Health apps declaration** in Play Console (App content), choosing the closest health feature; and the **Data safety** form (health data: collected? No, it's only read from the user's own server and not sent to the developer).
- A hosted **privacy policy** URL (use `PRIVACY.md`).
- The medical disclaimer in the store description, not just in the app.
- No claims of diagnosis or treatment in the listing text or screenshots.

## Regulatory note (not legal advice)

AutoAPS is designed to stay a low-risk "secondary display": read-only, no alarms, no dosing advice, prominent disclaimer, stale data clearly marked. Adding alarms that people rely on, predictions, or treatment suggestions moves it toward regulated medical device software in the US (FDA) and EU (MDR). Get proper regulatory advice before adding any of those.
