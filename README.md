# Formation Drill: Android app

The **Android app** version of Formation Drill, a voice-controlled Drill & Ceremony practice game.
The browser version is in a separate repo: **https://github.com/imp-brando/formation-drill-html**
(play it at https://imp-brando.github.io/formation-drill-html/).

## How it works (hybrid)

- **Game:** a thin Kotlin shell (full-screen WebView) loads the game from the HTML repo's Pages site,
  https://imp-brando.github.io/formation-drill-html/, so game edits go live without an app update.
  The site's service worker keeps an offline copy, and a copy of the game is bundled in the APK for a
  first launch with no signal.
- **Voice (native):** one `AudioRecord` mic stream (16 kHz mono) feeds both
  - an **on-device Vosk recogniser** (small English model, Apache-2.0) limited to the drill-word grammar
    the game sends, which works offline, and
  - a **loudness + pitch meter** (every 25 ms) for inflection and voice-burst execution timing.
  There's no mic handoff and no Chrome speech engine. All command logic (sound-alikes, chatter discard,
  PICAA, Cover/Recover, `!` errors) stays in the game; it detects the app through `window.FGAndroid`.

## Install a test build (sideload)

1. On your Android phone (Android 8+), open the latest APK from
   [Releases](https://github.com/imp-brando/formation-drill-android/releases) **in the Chrome app** and download it.
2. Open the downloaded file. If asked, allow Chrome to "Install unknown apps", then tap Install
   (Play Protect may warn about an unknown developer: "Install anyway").
3. Open Formation Drill with signal the first time, tap Snake or Drill, and allow the microphone
   when you call FALL IN.

## Build

Requires JDK 17 and the Android SDK (platform 35).

    ./gradlew assembleDebug testDebugUnitTest      # phones: arm64-v8a + armeabi-v7a
    ./gradlew assembleDebug -Pemulator              # + x86_64 for an emulator
    ./gradlew assembleDebug -PgameDir=../formation-drill-html   # bundle a local game build instead of the live site

At build time the speech model (~40 MB) is downloaded into `app/src/main/assets/model-en-us/`, and the
current game (`index.html`, `sw.js`) is fetched from the Pages site into `app/src/main/assets/www/`.
Output: `app/build/outputs/apk/debug/app-debug.apk`.

## Bridge

- JS → native (`window.FGAndroid`): `start()`, `stop()`, `setGrammar(jsonArray)`, `version()`.
- Native → JS (`window.FGN`, defined in the HTML repo's `src/part6.js`): `frame(db, hz, ageMs)`,
  `speech(text, isFinal, alts)`, `state(s, detail)`.
- The bridge only answers the game's own pages (the Pages URL above or the bundled copy).
