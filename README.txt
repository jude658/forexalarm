FOREX ALARM (Android) - how to build the app

1. Install Android Studio (free): https://developer.android.com/studio
2. Unzip this folder. In Android Studio choose File > Open and pick the ForexAlarm folder.
3. Wait for "Gradle sync" to finish (needs internet the first time).
4. Build the APK: Build > Build Bundle(s) / APK(s) > Build APK(s).
   Or plug in your phone (Developer options > USB debugging) and press the green Run button.
5. The APK is at app/build/outputs/apk/debug/app-debug.apk. Copy it to your phone and install it
   (allow "install unknown apps" when asked).

FIRST RUN ON THE PHONE
- Allow notifications.
- Tap "Allow background running" and choose Allow.
- Add an alert. Monitoring starts automatically and keeps running with the app closed.

KEEP IT ALIVE (important on Tecno, Infinix, Samsung, Xiaomi, Oppo, Huawei)
- Settings > Battery > this app > set to Unrestricted / No restrictions.
- Turn on Auto-start for the app if your phone has that option.
- Lock the app in the Recents screen so the phone does not clean it.
- Do not use "Force stop": that kills monitoring.
