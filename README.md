# WebOS Remote

**An Android remote control for LG webOS smart TVs.** It puts a modern remote on your phone, with a touch navigation pad, a pointer trackpad, a phone keyboard for typing on the TV, and a web browser launcher. It talks to the TV directly over your home Wi-Fi, with no cloud service and no account.

---

## Features

### Remote control
- **Navigation pad.** A circular D-pad like the one on the original remote. Tap the ring for up, down, left and right (hold to repeat). Tap the centre for OK, or swipe across it to move through menus.
- **Pointer trackpad.** Works like the Magic Remote pointer: drag to move the cursor on the TV, tap to click, and drag with two fingers to scroll.
- **Keys.** Power, Back, Home, Play/Pause, a volume rocker, Mute (it shows whether the TV is muted) and a channel / page up-down rocker.
- **Haptic feedback** on key presses. You can turn it off in the settings menu.

### Typing and browsing
- **Phone keyboard.** Text you type on the phone appears live in the text field that's focused on the TV, including edits and deletions. Press Enter on the phone to submit it.
- **YouTube search.** When YouTube is on screen, your search opens directly in YouTube's results.
- **Web browser bar.** Open any address or search term in the TV's browser, use one-tap shortcuts to popular sites, and reopen recent pages.

### A connection that stays put
- **Automatic discovery.** The app finds LG TVs on your network (via SSDP), or you can enter the TV's IP address yourself.
- **Pair once.** You accept the pairing prompt on the TV the first time. After that the app keeps the key and reconnects on its own.
- **Reconnects when the TV comes on.** While the app is open it waits for your TV and connects as soon as it's switched on. It also reconnects after the phone wakes up or rejoins Wi-Fi.
- **Follows IP changes.** If your router gives the TV a new address, the app finds it again by its device ID.
- **Secure connection first.** It connects over encrypted WebSocket (port 3001) and falls back to port 3000 on older TVs.

---

## Requirements

- An Android phone running **Android 7.0 (API 24)** or newer
- An **LG webOS TV** on the **same Wi-Fi network**
- These TV settings, so the TV can be controlled and reached over the network:
  - **LG Connect Apps** turned on (*Settings → General → Devices → External Devices*; the location varies by model)
  - **Quick Start+** recommended, so the TV stays reachable right after it's switched on
  - Optional: a fixed IP address for the TV, set as a DHCP reservation in your router

## Getting started

1. Open the app and tap the device name at the top, or **Settings → TV connection**.
2. Tap **Scan** to find your TV, or enter its IP address.
3. Press **Allow** on the TV when the pairing prompt appears.

That's all. Later on, open the app and it connects by itself.

If pairing fails after a TV reset, use **Clear Key & Pair Fresh** in the connection dialog.

---

## Building from source

**Prerequisites:** [Android Studio](https://developer.android.com/studio) (recent version). Gradle downloads the Java toolchain it needs on the first build.

1. Clone the repository:
   ```bash
   git clone https://github.com/KirillFinn/-webos-remote.git
   ```
2. In Android Studio choose **File → Open** and select the project folder. Let Gradle sync.
3. Run the **app** configuration on an emulator or a physical device. For real use you need a physical device on the same network as the TV.

You can also build from the command line:

```bash
./gradlew assembleDebug        # build the debug APK  -> app/build/outputs/apk/debug/
./gradlew testDebugUnitTest    # run unit and Robolectric UI tests
```

## Tech stack

- **Kotlin** and **Jetpack Compose** (Material 3) for the UI
- **MVVM**: a single `RemoteViewModel` exposes UI state through `StateFlow`
- **OkHttp WebSockets** for LG's SSAP protocol, plus a separate pointer-input socket for the trackpad and keys
- **SSDP** (UDP multicast) for finding TVs on the network
- **JUnit**, **Robolectric** and **Compose UI tests**

## Project structure

```
app/src/main/java/com/example/
├── MainActivity.kt                 # entry point, starts/stops the auto-reconnect while visible
├── viewmodel/RemoteViewModel.kt    # UI state, remote actions, reconnect logic
├── network/
│   ├── WebOsClient.kt              # SSAP WebSocket client: pairing, commands, pointer socket
│   └── SsdpDiscovery.kt            # finds LG TVs on the local network
├── data/TvPreferencesRepository.kt # saved TVs, pairing keys, recent URLs, settings
├── model/TvModels.kt               # data classes and connection states
└── ui/
    ├── screens/RemoteMainScreen.kt # main remote layout
    ├── components/                 # nav pad, trackpad, keys, keyboard sheet, browser bar, dialogs
    └── theme/                      # silver & graphite colour palette
```

## Privacy

The app talks only to your TV on your local network. It collects no personal data, has no analytics and doesn't send anything to external servers. Pairing keys stay on your phone.

## Disclaimer

This is an independent project. It is not affiliated with or endorsed by LG Electronics. "LG" and "webOS" are trademarks of their respective owners.
