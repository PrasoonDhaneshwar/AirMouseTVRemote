<div align="center">

# 📺 Air Mouse TV Remote

**Turn your phone into a remote for any Android TV / Google TV: no pairing hardware, no cloud, just your Wi-Fi.**

![Platform](https://img.shields.io/badge/platform-Android%207.0%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white)
![Hilt](https://img.shields.io/badge/DI-Hilt-2C8EBB)

[**⬇️ Download the APK**](https://github.com/PrasoonDhaneshwar/AirMouseTVRemote/releases/download/1.4/AirMouseTVRemote-v1.4.apk)

</div>

---

## ✨ What it does

- **Finds your TV automatically.** mDNS discovery, a scan of the local network for networks that block it, and an HTTP name lookup for TVs that don't advertise one. You can also add a TV by its IP address, and the last TV is remembered so the app reconnects on launch.
- **Pairs like the official app.** Type the 6-character code shown on the TV once (digits and capital letters on one keyboard) and it connects by itself as soon as the last character is in. The app remembers the TV's certificate and refuses a different device answering at the same address.
- **Full remote on one screen.** A big D-pad or a touchpad, OK, Back, Home, volume, mute, power and media controls, with no scrolling. Keys answer your thumb with a tick, hold one for a long press, and the phone's own volume buttons change the TV's volume.
- **Type on the TV.** When a TV search box asks for text, a sheet opens above the phone keyboard and types straight into it. It follows what the TV really holds and shows whether your text has reached the TV.
- **YouTube search.** YouTube's own keyboard can't be typed into from a phone, so a button opens the results for your search on the TV.
- **Honest connection state.** The UI reflects whether the TV is really reachable, and reconnects when the network comes back.
- **Accessible and small.** The keys work with TalkBack, and the release APK is about 1.7 MB.

## 🎬 See it in action

| Find & pair | Navigate | Type on the TV | Switch TV |
|:---:|:---:|:---:|:---:|
| <img src="docs/gifs/pairing.gif" width="190" /> | <img src="docs/gifs/remote.gif" width="190" /> | <img src="docs/gifs/text-entry.gif" width="190" /> | <img src="docs/gifs/reconnect.gif" width="190" /> |

## 👇 Get started

1. Put your phone and TV on the **same Wi-Fi network**.
2. [Download the APK](https://github.com/PrasoonDhaneshwar/AirMouseTVRemote/releases/download/1.4/AirMouseTVRemote-v1.4.apk) and install it.
3. Open the app and pick your TV from the list.
4. Type the code shown on the TV screen. It connects as soon as all six characters are in.
5. Start navigating.

**Build from source:** open the project in Android Studio (Narwhal or newer) and run the `app` configuration, or:

```bash
./gradlew assembleDebug
```

Run the unit tests with `./gradlew :app:testDebugUnitTest`. The release build is shrunk with R8 (`./gradlew assembleRelease`); the project needs JDK 17.

## 🛠️ Built with

- [Kotlin](https://kotlinlang.org/) and [Coroutines / Flow](https://kotlinlang.org/docs/coroutines-overview.html)
- [Jetpack Compose](https://developer.android.com/jetpack/compose) with [Material 3](https://m3.material.io/) (custom "Deep Code" theme)
- [Hilt](https://dagger.dev/hilt/) for dependency injection
- [Protocol Buffers (lite)](https://protobuf.dev/) for the Polo and remote protocols
- [BouncyCastle](https://www.bouncycastle.org/) for client certificate generation
- Android [NSD](https://developer.android.com/develop/connectivity/wifi/use-nsd) for mDNS discovery
- MVVM with a repository layer and unidirectional state
- JUnit and kotlinx-coroutines-test for the protocol, text-editing and connection logic

## 🔒 Privacy

Everything stays on your local network. There's no account, no analytics, no cloud service, and the app only talks to the TV you choose. Release builds drop the debug and info logging.

## 📄 License

Add a license of your choice (e.g. MIT) as `LICENSE` and mention it here.

---

<div align="center">
Made with ❤️ by <a href="https://github.com/PrasoonDhaneshwar">Prasoon Dhaneshwar</a>
</div>
