# Air mouse: design document

| | |
|---|---|
| **Covers** | A pointer on the TV, moved by pointing the phone, like LG's Magic Remote |
| **Status** | Option B (Bluetooth HID) is built in the `:airmouse` module, with its pad, settings and setup screens. On a Hisense Android TV 9 the pointer moves, clicks, scrolls and recenters (section 7.9). Not yet tried by hand: pointing by moving the phone. The companion app (A) is not started |
| **Related** | [Remote design](remote.md) (built) · Stitch "Deep Code" design (Air Mouse screens) |

The remote today drives the TV the way a normal remote does: D-pad steps, OK, and keys. This
document plans the next step: a cursor on the TV that follows the phone as you point it, with
click and scroll, as an **optional mode** next to the existing remote.

---

## 1. Background

### 1.1 What the Magic Remote does

LG's Magic Remote has a gyroscope. The TV draws a cursor, and moving the remote moves the cursor,
like a laser pointer. You click on what's under the cursor and scroll with the wheel. It works
because webOS is built for a pointer: the TV itself draws the cursor and every app accepts pointer
input.

### 1.2 Why the current app can't do it

The app speaks the stock Android TV Remote protocol ([remote.md](remote.md), section 4). That
protocol carries keys, text, volume and app links. It has **no message for moving a pointer or
clicking at a position**. The current "touchpad" only turns swipes into D-pad steps.

So a pointer needs a second channel to the TV. Section 4 compares the ways to get one.

### 1.3 What the Stitch design shows

> **The air mouse UI is being redesigned** as part of the app's UI overhaul. The screens below
> are the original Stitch design and may change. This document covers how the air mouse works;
> the overhaul decides how it looks and where it sits in the app.

The Stitch "Deep Code" project has the air mouse screens this document started from:

- **Air Mouse Touchpad:** pointing area, left and right buttons (LMB / RMB), a live readout of the
  6-axis motion sensor and the pointer's X/Y, a "Smooth Air" option and a Recenter button.
- **App Settings:** sensitivity sliders and calibration.
- **Accessibility Setup Guide:** tells the user to turn on an "Air Mouse Control" accessibility
  service in the TV's settings. This assumes a companion app on the TV (option A below).
- **TV Companion Dashboard** (phone and TV versions): connection state, latency and Wi-Fi signal.
  Its "Source: iPhone 15 Pro" label is a design placeholder.
- Tab bars that differ from screen to screen (Remote / Touchpad / Keyboard / Apps and others).
  The app has no tab navigation today (see section 8.3).

## 2. Goals and non-goals

**Goals**

- Point the phone at the TV and a cursor follows, smoothly and with low lag.
- Click, long press and scroll at the cursor.
- A touchpad fallback (drag on the phone to move the cursor) for phones without a gyroscope, or
  for users who prefer it.
- **Optional.** The existing remote keeps working on every TV with nothing extra installed or
  paired. The air mouse appears only where it can work.
- Secure: nobody else on the Wi-Fi can move the cursor or click on the TV.

**Non-goals**

- Replacing the stock remote protocol for keys, text, volume or app links. The air mouse is
  added alongside it.
- TVs that aren't Android TV / Google TV.
- Games or drawing that need precise absolute positioning.

## 3. Requirements

| | Target | Why |
|---|---|---|
| Motion-to-cursor latency | under ~50 ms on a normal home Wi-Fi | Above that, pointing feels laggy |
| Update rate | 60–100 updates per second | Smooth cursor motion on a 60 Hz TV |
| Jitter | Cursor still when the hand is still | Hand tremor must not shake the cursor |
| Battery | Sensors only while the air mouse screen is showing | Gyroscopes drain battery |
| Setup | One-time, guided | Users only accept a short setup |

## 4. Approaches

There are three ways to get a pointer onto an Android TV.

### Option A: TV companion app with an accessibility service

A small app installed on the TV runs an `AccessibilityService`. It:

- draws the cursor itself, in an accessibility overlay window;
- clicks and scrolls by injecting gestures at the cursor (`dispatchGesture`) or by acting on the
  screen element under it;
- receives pointer data from the phone over its own encrypted connection.

This is what the Stitch design assumes.

### Option B: the phone acts as a Bluetooth mouse

Android 9+ phones can present themselves to another device as a Bluetooth keyboard and mouse
(`BluetoothHidDevice`). Android TV supports Bluetooth mice natively: it **draws its own mouse
pointer** and apps receive real mouse events. The phone pairs with the TV over Bluetooth once, then
sends mouse movement and clicks as standard HID reports.

The phone's gyroscope still does the pointing (section 5). Bluetooth only carries the result.

This was not considered when the air mouse was first planned. It is included here because it
avoids a second app and the accessibility policy risk.

### Option C: ADB over the network (rejected)

Injecting input through ADB works technically, but needs developer options and network debugging
switched on on the TV, which leaves the TV open to anyone on the network. Rejected.

### 4.1 Comparison

| | A: Companion + accessibility | B: Bluetooth HID mouse |
|---|---|---|
| Install on TV | Yes, a second app | **Nothing** |
| One-time setup | Install app, enable accessibility service, pair over Wi-Fi | Pair phone with TV in Bluetooth settings |
| Cursor | Drawn by our overlay | **Native system pointer** |
| How apps see clicks | Injected touch gestures, which TV apps built for the D-pad may ignore | **Real mouse events**, which Android handles as standard input |
| Play Store risk | **High**: the accessibility API policy needs a declaration, and a remote isn't an accessibility tool | Low: normal Bluetooth permissions |
| Phone support | Any phone | Android 9+, and some phone makers disable the HID profile |
| TV support | Any Android TV that allows third-party accessibility services | TVs with Bluetooth that accept HID mice (most) |
| Extra features possible | Custom cursor look, on-TV dashboard | Bluetooth keyboard too, which could also fix typing where the text session fails |
| Transport | Our own protocol over Wi-Fi (to build) | Bluetooth, standard reports (latency is low) |
| Works alongside the stock remote | Yes | Yes |

### 4.2 Decision: B first, A only if needed

**Build option B (Bluetooth HID) first. Build option A (companion app) only if the spike or real
use shows it's needed.** Decided November 2026.

Why:

- A is several times the work of B: a second app, its own protocol and pairing, an accessibility
  service, sideloading from GitHub without automatic updates, and the Android 13+ restricted
  settings on every TV.
- A carries a Play Store risk under the accessibility API policy. B has none.
- Most of the work is shared: the pointer model (section 5), the air mouse screen and its
  settings. With the pointer output behind one interface (`PointerSink`, section 8.2), A can be
  added later without changing the rest, so waiting on it costs little.

**A is built if any of these turns out true:**

1. A meaningful share of phones lack the Bluetooth HID device role, measured in the spike or seen
   in users' diagnostics (section 7.6).
2. The test TVs mishandle the Bluetooth pointer, or limit Bluetooth connections so much that B is
   impractical.
3. A feature only A can give becomes a goal, such as a custom cursor or an on-TV dashboard.

If none of these happen, B alone is the product, and A stays here as the documented fallback.

Sections 5 and 8 apply to both options. Sections 6 and 7 cover what's specific to each.

## 5. Pointer model (phone side, both options)

### 5.1 Reading motion

- **Sensor:** `TYPE_GYROSCOPE` for angular speed, plus `TYPE_GAME_ROTATION_VECTOR` for the phone's
  orientation. The game rotation vector fuses gyroscope and accelerometer *without* the
  magnetometer, so the cursor doesn't jump near metal or speakers.
- **Mapping:** turning left/right (yaw) moves the cursor on X, tilting up/down (pitch) moves it on
  Y. This is relative pointing, like the Magic Remote: the cursor moves by how far the phone turns,
  not to where it points.
- **Roll compensation:** angular speed is turned into the world frame using the rotation vector.
  That way a sideways sweep moves the cursor sideways even when the phone is held tilted.
- **Rate:** sensors run at game rate or faster while the screen is showing. Readings are combined
  into one movement per send (60–100 per second).

### 5.2 Making it feel right

- **Gain with acceleration:** slow movements are scaled down for precision and fast movements
  scaled up, so the whole screen is reachable without big arm movements. Sensitivity sets the
  curve.
- **Smoothing ("Smooth Air"):** a One Euro filter, which smooths jitter when moving slowly and
  adds little lag when moving fast. The setting adjusts its strength.
- **Dead zone:** angular speeds below a small threshold are ignored, so hand tremor doesn't move
  the cursor.
- **No clutch.** The first build moved the cursor only while a thumb rested on the pad. Replaced
  (November 2026, section 13): with "Air pointing" on, the cursor follows the phone with no finger
  on the screen.
- **Freeze:** a finger held on the pad stops the cursor following, so the phone can be brought
  back to a comfortable position, like lifting a mouse.
- **Put down:** a phone lying still for 1.5 s pauses, and wakes only when clearly picked up, a
  moment after the swing of picking it up.
- **Hold-still around a press:** a tap or a volume button jolts the phone just before it
  registers. The movement of the last 100 ms is undone on press, and the cursor stays still for
  100 ms after release.
- **Wake:** when the link comes up the phone sends one count each way, so the TV shows a cursor it
  had hidden.
- **Recenter:** the cursor is pushed into the top-left corner by more than any screen is wide, then
  walked back to the middle in 4-count steps every 10 ms. Approximate, since a mouse is relative
  (section 7.9).
- **Calibration:** measures the gyroscope's resting drift while the phone lies still for 3 s and
  subtracts it; a phone that moved meanwhile gives no calibration.

All of this math is pure functions in the `:airmouse` module's `api` package, free of Android like
the rest of the protocol code, and unit-tested.

### 5.3 Mouse pad

With "Air pointing" off, sliding a finger on the pad moves the cursor like a laptop trackpad,
with its own acceleration curve. It is the only mode on phones without a gyroscope, and can be
chosen on any phone.

### 5.4 Clicks and scroll

| Phone gesture | Action on TV |
|---|---|
| Tap the pad | Click at the cursor |
| Vol + (Air pointing on) | Click, held for as long as the button is |
| Vol − (Air pointing on) | Back, sent over Wi-Fi like the Back key |
| Two-finger drag, or a strip (up and down on the right edge, left and right along the bottom) | Scroll in any direction. Sent as D-pad keys by default, or as the mouse wheel and its sideways wheel (settings: "Scroll with") |

The volume buttons can be swapped, or left to the TV's volume, in the settings. There is no
right-click control: Back is the remote's Back key or Vol −.

The D-pad, Back, Home, volume and text entry stay on the existing remote protocol.

## 6. Option A in detail: TV companion app

### 6.1 Components

- **New Gradle module** for the TV app with its own package name, in this repository. It shares
  protocol code (framing, pairing math) with the phone app through a small shared module.
- **`AccessibilityService`:**
  - Draws the cursor in a `TYPE_ACCESSIBILITY_OVERLAY` window and hides it after a few seconds
    without movement.
  - Clicks with `dispatchGesture` (a short tap at the cursor).
  - Scrolls with a swipe gesture, or with the scroll action of the element under the cursor.
- **Fallback for D-pad-only apps:** if injected touches are ignored, click the clickable element
  under the cursor (`ACTION_CLICK`) instead. This needs permission to read window content, which
  widens the privacy footprint and the Play declaration, so it's only added if the spike shows it's
  needed.
- **Server:** listens on a fixed port, advertised over mDNS as its own service type, so the phone
  finds the companion on the same address as the TV.
- **TV screen:** a dashboard that shows the pairing code, connection state and setup steps.

### 6.2 Transport and protocol

- **TLS over TCP with `TCP_NODELAY`** for everything to start with. On a home LAN that is fast
  enough, and it keeps one encrypted channel. If measured latency is too high, motion can move to
  UDP later, with sequence numbers so lost or late packets are dropped, not replayed.
- **Messages:** protobuf-lite with varint length framing, the same as the stock protocol, so
  `FrameCodec` is reused. Types:
  - `Hello` (protocol version, the TV's screen size)
  - pairing messages
  - `Move(dx, dy, seq)` in normalised units the TV scales to its resolution
  - `Button(id, down/up)`
  - `Scroll(dx, dy)`
  - `Recenter`
  - `Ping` / `Pong` (also gives the latency for the dashboard)
- The protocol is versioned from the start, since phone and TV apps update separately.

### 6.3 Pairing and security

An accessibility service that can click anywhere is powerful, so the channel must be locked to
paired phones:

- **Mutual TLS with self-signed certificates on both ends.** The phone reuses its existing client
  certificate.
- **One-time code shown on the TV**, entered on the phone. The proof is computed like the Polo
  secret (a hash of both public keys and the code), so the existing `computePairingSecret`
  approach and its tests carry over.
- **Pinned on both sides afterwards.** The phone pins the companion's certificate (as
  `CertPinStore` does for the TV). The companion only accepts certificates it has paired with, and
  can list and remove paired phones.
- The service doesn't read screen content unless the D-pad fallback (6.1) turns out to be needed.

### 6.4 Store policy and setup

- **Google Play's accessibility API policy:** apps using `AccessibilityService` must declare
  their use and show a prominent disclosure. A remote control isn't an accessibility tool, so
  approval is not guaranteed. This is the biggest risk of option A, and should be checked against
  the current policy before building.
- Turning on an accessibility service with a TV remote takes several steps through Settings, so
  the guided setup from the Stitch design is needed. On Android 13+, sideloaded apps have
  "restricted settings" that block enabling accessibility until allowed in the app's info page.
  Installing from the Play Store avoids that.

## 7. Option B in detail: Bluetooth HID

### 7.1 How it works

The phone presents itself to the TV as a Bluetooth keyboard and mouse. The TV can't tell it apart
from a real one: as soon as it moves, Android TV draws its own pointer, and apps receive normal
mouse clicks and scroll-wheel events. This is the same model as the Magic Remote: a motion sensor
in the hand, movement sent over a radio link, and the TV drawing the cursor.

```
Phone gyroscope → pointer math (section 5) → mouse report "dx=12, dy=-3"
    → Bluetooth (classic HID) → TV input system → native pointer moves
```

The gyroscope pointing of section 5 drives this link directly. Bluetooth only replaces the
connection; the phone still does all the motion processing.

The Wi-Fi remote keeps handling keys, typing, volume and power. Bluetooth adds only the pointer
(and optionally a keyboard, 7.5).

### 7.2 One-time pairing

1. On the TV: **Settings → Remotes & Accessories → Pair accessory**. The TV starts searching.
2. In the phone app: **Set up air mouse**. The app asks Android to make the phone discoverable, and
   the system shows its own prompt.
3. The TV lists the phone (for example "AirMouse"). The user picks it and confirms the pairing
   code on both screens.
4. The phone remembers the bonded TV and reconnects by itself whenever the air mouse is opened.

The app links this bond to the TV it already knows over Wi-Fi, so the air mouse mode appears for
that TV only.

### 7.3 Phone-side implementation

`BluetoothHidDevice` has been a public API since Android 9 (API 28):

```kotlin
adapter.getProfileProxy(context, listener, BluetoothProfile.HID_DEVICE)
// in the listener, once the profile proxy is connected:
hid.registerApp(sdpSettings, null, null, executor, callback) // name, SUBCLASS1_COMBO, report descriptor
hid.connect(tvDevice)                                        // after the one-time bond
hid.sendReport(tvDevice, MOUSE_REPORT_ID, report)            // buttons, dx, dy, wheel
```

- **Report descriptor:** a fixed byte table declaring one report: a mouse with 3 buttons,
  relative X and Y (−127…127 each), and a wheel. The keyboard report planned at first was left
  out (section 13): Android hides its on-screen keyboard while a hardware keyboard is connected.
- **Relative movement.** All pointer math (smoothing, dead zone, acceleration, clutch, recenter)
  stays on the phone, in `data/api/` with JVM tests, and only the resulting movement is sent.
  Larger movements are split across several reports.
- **Rate and latency:** Bluetooth mice usually report about 100 times a second, and typical delay
  is in the 10–20 ms range. Both are well within the requirements in section 3.
- **Lifetime:** the HID app is registered while the air mouse screen is showing, and unregistered
  when the app goes to the background. Keeping it alive with the screen off would need a foreground
  service, which isn't planned. Only one app on a phone can be the HID device at a time, so another
  "phone as mouse" app can block registration. The app reports that case clearly.
- **Permissions (Android 12+):** `BLUETOOTH_CONNECT`, plus `BLUETOOTH_ADVERTISE` while making the
  phone discoverable for pairing. These are runtime permissions, asked for only when the user sets
  up the air mouse. On Android 9–11 the older install-time `BLUETOOTH` / `BLUETOOTH_ADMIN`
  permissions apply.

### 7.4 What the TV provides

- **A native pointer.** Its look, hiding after a few seconds idle, and hiding when the D-pad is
  used are all handled by the TV.
- **Real mouse events.** Most views treat a primary-button click like a tap, and the wheel scrolls
  lists in apps built for a mouse. Many TV apps scroll by focus instead and handle the wheel badly (7.9).
- **The right button.** On Android it commonly acts as **Back** when the app doesn't use it. If
  that holds on our TVs, it settles the right-button question in section 12.
- **Pointer speed.** The TV applies its own pointer speed setting on top of our curve, so the gain
  and acceleration have to be tuned with the TV's default speed in mind.

### 7.5 Keyboard bonus

The same HID connection can type as a real keyboard, into any text field, including ones that never
open the text session the Wi-Fi remote relies on ([remote.md](remote.md), section 9). YouTube's own
keyboard, which can't be typed into today, is the first thing to test. If it works, the Bluetooth
keyboard becomes a fallback for typing.

### 7.6 Phone support

**There is no official list of phones that support the HID device role.** It's part of AOSP since
Android 9, but each phone's Bluetooth configuration can turn it off. What the research found
(November 2026):

- **Generally works:** Samsung Galaxy and Google Pixel phones are reported to ship with it on. A
  Galaxy S21 is one confirmed working example.
- **Reported missing on some models** (by community sources, mostly from the Android 9–11 era,
  without exact models): OnePlus, Motorola, LG, Sony, Xiaomi and Huawei.
- **Recently disabled on purpose in some builds:** Fairphone 6 (stock and /e/OS), and a change
  proposed to LineageOS by a Qualcomm engineer, with no reason given. So support can also *go away*
  on newer builds, not only be missing on old ones.
- Root users can turn it back on (a Magisk module), which isn't something the app can rely on.

Conclusion: support is **per model and per build, not per brand**. Some phones will lack it, and
the share can't be predicted from public data. Therefore:

- **Detect at runtime.** The option is offered only if the HID device profile proxy connects and
  `registerApp` succeeds (`onAppStatusChanged` reports registered). Otherwise the air mouse screen
  says this phone doesn't support it and, if built, offers option A.
- **Measure on real phones in the spike.** Test as many of the common brands as possible (borrowed
  phones count), and record the results in a table here.
- The diagnostics can note whether the profile was available, so users' bug reports show it.

### 7.7 Risks specific to option B

- Phones without the HID device role (7.6).
- TVs or sticks that allow only a few Bluetooth connections (remote, headphones, game pad), or
  have weak Bluetooth.
- Some full-screen video apps may ignore pointer clicks (equally a risk for option A).
- Range is about 10 m, which is fine for a living room.

### 7.8 Spike checklist

1. Register as a HID device and pair with each test TV.
2. Move the native pointer; check smoothness and latency against section 3.
3. Click and scroll in the home screen, Settings, YouTube, Netflix and a browser.
4. Check the right button (Back or nothing).
5. Type with the HID keyboard into YouTube's search and a normal text field.
6. Check reconnecting after the app is reopened and after the TV restarts.
7. Try every phone available, and record which ones support the HID device role.

**Security:** Bluetooth pairing and its encryption are handled by the system, and only bonded
devices can send input. No extra protocol or certificates are needed.

### 7.9 Spike findings so far (November 2026)

One session, with a POCO F1 (Android 12, an AOSP build) and a Hisense Android TV 9. Checklist
item 1 is half done; items 2 to 7 are open.

| Finding | What it means for the app |
|---|---|
| The POCO F1 has the HID device role, and `registerApp` succeeds | One Xiaomi-family phone confirmed, on a custom ROM. Says nothing about stock MIUI |
| **Android takes the role away whenever anything covers the app**, including its own pairing dialog and the "make visible" prompt. The app is unregistered while they show | The role can't be held through pairing. The link asks for it again every 1.5 s until the app is back in front |
| A TV that pairs while the phone is not registered files it as a generic device (shown with a game pad icon) and never opens the HID connection | After pairing, the phone has to open the connection itself. During setup it offers it to every paired device that isn't a computer or a phone |
| When the phone opens the connection to a TV that has forgotten it, Android drops the stale pairing and starts a new one. The TV shows its own "Bluetooth pairing request" **without its accessory screen being open** | Pairing can start from the phone. This may remove step 1 of the setup, if the phone can find the TV's Bluetooth address (open question, section 12) |
| The phone showed that pairing request only as a notification, not a dialog, and it timed out after 30 s | The setup says to look in the notifications |
| On a second try the TV's "Add accessory" list did not show the phone, although the TV's log shows it received the phone's inquiry response | Not understood yet. The list may filter on device class, which would change with the role being held or not |
| The remote protocol's link message most likely can't open "Add accessory": that screen is started by an internal action, not a link, and this TV rejects `intent:` links | Not tested on the TV yet. The setup screens carry a D-pad, Back and Home instead, so the user walks there with the app |

**Built on these findings, not yet run on a TV:** on Android 12 and later the setup's first step
is the phone looking for the TV over Bluetooth (`BLUETOOTH_SCAN`, declared never to be used for
location). A device found under one of the TV's network names is paired with at once; other
devices that announce themselves as TVs are offered for the user to pick; computers, phones and
headphones are never offered. The TV then only has to confirm its pairing request, which the
setup's own OK key can do. If the TV isn't found, the screen says to open "Add accessory", which
makes a TV visible. The older way round (the TV finds the phone) stays as a fallback, and is the
only way before Android 12, where scanning would need the location permission. Still to be
learned on real TVs: whether they can be found without their accessory screen open, and how often
the Bluetooth name differs from the network name.

**Second session (15 and 16 November 2026), same phone and TV,** with the phone and TV already
paired. The link came up within about 3 s each time the pad opened. Measured on the TV (with
`getevent` on the phone's input device, and screenshots):

| Finding | What it means for the app |
|---|---|
| Moves, clicks and wheel notches all arrive. A click's press and release arrive about 1 ms apart, whatever gap the phone leaves between them | The click works anyway. No delay between press and release is needed |
| The launcher treats the first click on a tile as selecting it, the second as opening it | Two taps open an app from the home screen. Inside apps (SmartTube) one click acts |
| **The TV accelerates the pointer by its own rule, which varies from run to run.** Slow movement is about 1 pixel per count; the same 960-count walk sent in 12-count steps ended 1.5 to 1.75 times too far | Recenter walks back in 4-count steps every 10 ms, where the TV adds nothing. It lands within about 100 pixels of the middle |
| After the TV restarted, the app found it by its long network name (`HiSmartTV-A4-…`), and the link saved during setup under its address and friendly name was not found: the air mouse looked unset | The link is now saved and looked up under all of the TV's names (network name, friendly name, address) |
| SmartTube animates each turn of the wheel, and a turn arriving mid-animation replaces it: single notches a second apart scrolled 128 pixels each, but seven notches in a quick drag scrolled 256 in all. The TV's home screen counts every notch | Wheel turns are spaced at least 250 ms apart and the notches in between sent as one bigger turn. After the fix a quick four-notch drag scrolled SmartTube exactly four notches |
| Vol − on the phone reached the TV as Back; Vol + as a click; the phone's own volume stayed put | The volume buttons work as designed while pointing |
| Calibration on a phone lying on a table took about 3 s and measured a drift of about 0.0001 rad/s | This phone's gyroscope already corrects itself; the calibration is for phones that don't |

**Third session (16 November 2026).** After the sideways wheel was added, scrolling with the Bluetooth wheel felt worse in real use on the TV, while the touchpad's D-pad keys scrolled lists smoothly. TV apps are built around focus: a key press moves focus and the app scrolls its own list, whereas a wheel event is handled per app, and some ignore it or drop notches. So scrolling now sends D-pad keys by default and keeps the wheel as a setting. Pairing was not redone before that first test, so part of the difference may have come from the TV still holding the old descriptor; not separated yet.

Not yet tried: pointing by moving the phone, pausing when put down, sideways scrolling in a browser,
the reconnecting waves (they need the TV out of range), and a first-time setup with the new
phone-started pairing.

Phones tested for the HID device role:

| Phone | Android | Role available |
|---|---|---|
| POCO F1 (AOSP-based custom ROM) | 12 | Yes |

## 8. Fitting into the existing app

### 8.1 Mode detection

The air mouse mode appears on the remote screen only when it can work: the companion is found on
the TV's address and paired (A), or the TV is a bonded Bluetooth HID host (B). Otherwise the
remote looks exactly as it does today.

### 8.2 Code structure

- **`PointerSink` interface** for everything the pointer sends:

  ```kotlin
  interface PointerSink {
      val state: StateFlow<PointerLinkState>   // unavailable, needs pairing, connecting, ready
      suspend fun move(dx: Float, dy: Float)    // relative, already filtered and scaled
      suspend fun button(button: PointerButton, down: Boolean)
      suspend fun scroll(dx: Float, dy: Float)
  }
  ```

  The pointer model and the air mouse screen only talk to this interface. `BluetoothHidSink`
  (option B) is built first. `CompanionSink` (option A) can be added later with nothing above it
  changing. A fake sink tests the pointer pipeline on the JVM, the same way `TvTextPort` is used
  for text entry.
- **New `AirMouseSession` singleton** that owns the active `PointerSink`, alongside
  `RemoteSessionManager`. Neither depends on the other, so the stock remote is unaffected by air
  mouse failures.
- **Its own Gradle module, `:airmouse`.** Everything except the screens lives there: pointer
  math, the Bluetooth link, the sensor source and `AirMouseSession`. It has no Hilt and no UI, so
  it builds and runs its tests without the app's annotation processing, and it can be left out of
  a build, or gated, when the air mouse becomes a paid feature. The app provides the session
  through `di/AirMouseModule`. The pad and setup screens stay in `app`, next to the theme and key
  widgets they share with the remote.
- **Pointer math in the module's `api` package** (filters, gain curve, roll compensation): pure
  Kotlin, JVM tests.
- **A sensor source wrapper** in its `repository` package that registers sensors only while the
  air mouse is showing, and stops on pause.
- `RemoteViewModel` gains air mouse state in `RemoteUiState`, following the one-state pattern.

### 8.3 Navigation

> **Decided in the UI overhaul.** The notes below are the constraints the redesign should keep
> in mind, not a decision.

The app has three screens chosen from connection state, and no Navigation library
([remote.md](remote.md), section 3.3). The air mouse adds a mode *inside* the connected state,
with its own settings. That fits the current approach as a pad mode (D-pad / Touchpad / Air
mouse) next to the existing toggle, plus a settings sheet. Moving to the Stitch tab bar would mean
revisiting the no-Navigation decision, together with the UI overhaul.

## 9. Privacy

- Motion data goes only to the paired TV and is never stored.
- Option A: the companion reads no screen content unless the D-pad fallback is needed, and says
  so in its disclosure.
- Diagnostics record connection events and latency, not motion traces.

## 10. Testing

- **Unit tests:** filters, gain curve, roll compensation and dead zone against recorded sensor
  traces. Message framing and the pairing proof (reusing existing tests).
- **On real devices:** latency (move-to-cursor, filmed at high frame rate), drift over a minute
  held still, and clicks and scrolls in YouTube, Netflix, the home screen, Settings and a
  browser.
- **Test TVs:** the waipu tv stick and every other TV used for the remote, plus at least one
  Google TV.
- **Release builds:** the same R8 smoke test as the remote, plus pointer, click and scroll.

## 11. Phases

| Phase | Scope | Done when |
|---|---|---|
| 0. Spike | B: the checklist in section 7.8, on as many phones as possible and the test TVs | Results written into this document, and A's criteria (section 4.2) checked |
| 1. Pointer channel | The chosen option's connection, pairing and security. Touchpad-mode pointer (no sensors yet) | A drag on the phone moves the TV cursor and clicks work, on all test TVs |
| 2. Air pointing | Gyro pointing, roll compensation, smoothing, dead zone, clutch, recenter, calibration, settings | Pointing feels steady and meets the latency target |
| 3. Product | Mode detection, guided setup, dashboard (latency, signal), Play Store listing (and TV app listing for A) | Released |

## 12. Risks and open questions

**Risks**

- **(A) Play Store rejection** under the accessibility API policy.
- **(A) TV apps built for the D-pad may ignore injected touch.** The node-click fallback costs
  privacy and policy scope.
- **(B) Phone makers that disable the Bluetooth HID profile**, and TVs with limited Bluetooth
  connections (remotes and headphones already connected).
- Wi-Fi latency spikes on busy networks (A).
- Users have to do a one-time setup on the TV, either way.

**Open questions**

- Whether A is needed after all, judged against the criteria in section 4.2 once the spike and
  first users' diagnostics are in.
- Whether phone-started pairing (section 7.9) works on real TVs well enough to drop the "TV
  finds the phone" fallback and the `BLUETOOTH_ADVERTISE` permission with it.
- How the gyroscope gain feels by hand, given that the TV adds acceleration of its own (7.9).
- Pricing for Pro. The air mouse carries a PRO mark, but nothing is charged yet.
- Whether the live sensor and X/Y readouts from the Stitch screen are for users, or only a debug
  view (for the UI overhaul).
- How the dashboard's "signal" should be measured: the phone's Wi-Fi strength (A), or Bluetooth
  link quality (B).
- For A: same Play listing as the phone app or a separate TV listing, and the package name.

## 13. Decisions so far

| When | Decision | Why |
|---|---|---|
| Oct 2026 | Air mouse is an optional mode, built after the stock remote | The stock remote works everywhere with nothing installed; the air mouse needs more setup |
| Oct 2026 | Keys, text, volume and app links stay on the stock protocol | Proven and works without the air mouse |
| Nov 2026 | Build Bluetooth HID (B) first; build the companion app (A) only if the criteria in section 4.2 are met | B avoids a TV app and the accessibility policy risk; the shared work keeps A cheap to add later |
| Nov 2026 | Pointer output goes through a `PointerSink` interface | Lets A be added beside B without changing the pointer model or UI, and allows JVM tests with a fake |
| Nov 2026 | The phone is a Bluetooth mouse only, not a keyboard too | A keyboard in the descriptor makes Android TV treat the phone as a hardware keyboard and hide its on-screen keyboard. The cost: adding the keyboard later (7.5) means pairing again |
| Nov 2026 | The air mouse lives in its own module, `:airmouse`; its screens stay in the app | Fast to build and test by itself, and separable for a paid feature. Moving the screens too would need a shared UI module first |
| Nov 2026 | The setup screens carry remote keys | The setup must not need the TV's own remote |
| Nov 2026 | One pad with an "Air pointing" switch, no rest-your-thumb clutch, no Click or Right keys; tap to click, Vol + clicks and Vol − is Back while pointing; Recenter is the one key. Hold to freeze was dropped (the pointer still holds still while a finger is down, since landing jolts the phone) | Pointing shouldn't need a thumb parked on the screen, and the click belongs where the hand already is |
| Nov 2026 | The air mouse is shown to everyone as a Pro feature, but only on phones known to have the Bluetooth mouse role | Nobody should be offered it and then told the phone can't do it |
| Nov 2026 | No paywall yet: the PRO mark is shown, the price line is left out | Pricing is not decided |
| Nov 2026 | The Air pointing switch is an icon key beside Recenter, off the pad; a one-time line explains it | A tap on the pad can never flip it, and the pad keeps its room. It needs the hint and an accessibility label because it has no text |
| Nov 2026 | Scrolling works in any direction: two fingers, and strips on the right edge and along the bottom, in the same place whether Air pointing is on or off | Sideways scrolling needs the sideways wheel (AC Pan) in the HID descriptor, so every TV paired before it was added has to pair again |
| Nov 2026 | Scrolling sends D-pad keys by default; the mouse wheel is a setting ("Scroll with") | On the Hisense Android TV 9 the touchpad's keys scroll lists smoothly in every app, while the wheel is dropped or ignored by some (7.9). The wheel stays for browsers and maps |
| Nov 2026 | App icon L3b: a TV with the pointer, a tilted remote and two waves, on a flat #161A1F background; adaptive icon with a themed monochrome layer, PNGs for Android 7, a 512 px store icon in `docs/store/` | Chosen from the canvas. `docs/design/render-icon.ps1` redraws the PNGs from the same geometry as the vector |
