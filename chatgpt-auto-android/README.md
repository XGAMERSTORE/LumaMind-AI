# ChatGPT Auto Continue (experimental Android prototype)

Native Java Android app, Android 8+. Enable the accessibility service, open ChatGPT, tap the floating START control, then initiate a response manually. The service only sends after observing the generation stop control and its disappearance. It never sends from an initial idle state, overwrites a nonempty composer, or operates outside the visible ChatGPT application. A STOP control, configurable delay, and configurable maximum (20 by default) are provided. No internet permission. No account credentials.

The official ChatGPT app's accessibility labels and UI can change; this prototype requires on-device validation. Locked screen and background ChatGPT are not supported. If expected controls are unavailable or ambiguous, automation pauses/stops; a partially inserted draft may remain for manual review.

Build with JDK 17, Android SDK 35, Gradle 8.9: from this folder run `gradle :app:assembleDebug`. GitHub Actions uploads the debug-signed APK as an artifact. No release signing credentials are included. This is an independent app, not affiliated with OpenAI. Respect usage limits; do not use it to bypass restrictions.
