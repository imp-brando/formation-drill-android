# Formation Drill: Android app (hybrid)

A thin Kotlin shell around the web game.

- **Game layer:** the same HTML5/JavaScript game, loaded from https://imp-brando.github.io/formation-drill/,
  so game edits go live without an app update. The page's service worker (`sw.js`) keeps an offline copy,
  and a copy of `index.html` is bundled in the APK (`assets/www`) for a first launch with no signal.
- **Voice layer (native):** one `AudioRecord` stream (16 kHz mono, `VOICE_RECOGNITION` source) feeds both
  - the voice meter (loudness + pitch every 25 ms → `FGN.frame`) used for inflection and voice-burst timing, and
  - an on-device Vosk recogniser (small English model, Apache-2.0) restricted to the drill-word grammar the
    game sends (`FGAndroid.setGrammar`), → `FGN.speech(text, isFinal, alts)`.
  There's no mic handoff, no Chrome session restarts, and it works offline.
- The game keeps all command logic (sound-alikes, chatter discard, PICAA, voice burst, `!` errors).
  In a normal browser `window.FGAndroid` doesn't exist and the Chrome speech path is used unchanged.

## Build

Requires JDK 17 and the Android SDK (platform 35). The speech model (~40 MB) is downloaded at build time.

    ./gradlew assembleDebug testDebugUnitTest        # phones (arm64-v8a, armeabi-v7a)
    ./gradlew assembleDebug -Pemulator                # + x86_64 for the emulator

APK: `app/build/outputs/apk/debug/app-debug.apk`

## Bridge

JS → native (`window.FGAndroid`): `start()`, `stop()`, `setGrammar(jsonArray)`, `version()`.
Native → JS (`window.FGN`): `frame(db, hz, ageMs)`, `speech(text, isFinal, alts)`, `state(s, detail)`.
The bridge only answers pages from imp-brando.github.io or the bundled copy.
