# Repository Guide for Coding Agents

## Project Layout

- The Android project lives in [`VoiceAssistant/`](VoiceAssistant/); run Gradle commands from that directory.
- `app` is the only module. Production Java is under `app/src/main/java/com/voice/assistant/`, and Android resources are under `app/src/main/res/`.
- [`README.md`](README.md) currently contains only the repository name and is not a setup guide.
- Do not edit generated files under `**/build/`. Treat `VoiceAssistant/local.properties` as machine-local and do not commit credentials or SDK paths.

## Build and Validation

Use the checked-in Gradle wrapper:

```bash
cd VoiceAssistant
./gradlew assembleDebug
./gradlew lint
./gradlew testDebugUnitTest
```

There are currently no source files under `app/src/test/` or `app/src/androidTest/`. For behavior changes, add the narrowest practical test; otherwise run `assembleDebug` and `lint` and report any environment limitation.

## Technical Baseline

- Android application written in Java, with Groovy Gradle scripts.
- Android Gradle Plugin 8.2.0, Gradle 8.2.1, compile/target SDK 34, minimum SDK 24, and Java 8 source compatibility.
- Keep implementations compatible with API 24 unless the code uses an explicit version guard.
- Follow the existing package `com.voice.assistant` and resource naming style (`activity_*`, `item_*`, `ic_*`, `bg_*`).

## Architecture and Conventions

- Activities own screen state and navigation; RecyclerView adapters render chat, forum, message, conversation, reply, and model rows.
- `ApiClient` owns backend requests. It uses synchronous OkHttp calls and returns raw response bodies, so invoke it on a background thread and marshal UI work through `runOnUiThread` or the main `Handler`.
- API responses are parsed locally as JSON and generally use `code`, `message`, and `data`. Preserve that contract when changing request handling.
- Authenticated endpoints use `Authorization: Bearer <token>`. `TokenManager` persists token, username, and balance in the `voice_prefs` SharedPreferences file.
- `MainActivity` owns text/voice chat, audio capture, balance refresh, drawer navigation, and sponsor-only model access. Keep Android lifecycle and runtime microphone permission handling in mind when changing this flow.
- The backend base URL is currently a fixed cleartext HTTP endpoint, enabled by `android:usesCleartextTraffic="true"`. Do not silently change the endpoint or transport policy; make configuration/security changes explicit and coordinated with the backend.
- User-facing strings are primarily Chinese, while some existing labels are English. Match the language and terminology of the screen being edited.

## Change Discipline

- Keep changes within the owning Activity, adapter, client, or resource unless a shared abstraction clearly reduces duplication.
- Never perform network or audio work on the main thread, and never update Android views from a worker thread.
- Do not log tokens, passwords, full authentication responses, or private-message contents.
- When adding an Activity, register it in `app/src/main/AndroidManifest.xml`; when changing a layout ID, update all corresponding Java lookups and adapters.