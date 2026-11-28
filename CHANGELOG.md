# Changelog

All notable changes to Air Mouse TV Remote. Versions match the git tags and GitHub releases.

## [Unreleased]

## [3.0] - 2026-11-27

### Added
- Voice search: hold the new Ask key and speak, and the TV's Google Assistant shows the results,
  like the mic on the TV's own remote. Your voice goes only to the TV. Slide off the key to cancel;
  with TalkBack, tap to start and tap again to stop. The microphone permission is asked for behind
  a sheet that says where the voice goes.
- On TVs that don't offer voice to remotes, the Ask key uses the phone's speech to text and types
  what you say into the TV's search box (into YouTube search while YouTube is open).
- Apps: the new Apps key opens the apps you've used on the TV, newest first, plus popular ones such
  as Netflix, YouTube and Prime Video. Tap one to open it on the TV. Long press a recent app to
  rename or remove it. Apps the TV doesn't have open their Play Store page.

- Send diagnostics: the ⋮ menu's Copy diagnostics is now Send diagnostics. A sheet shows what the
  report holds (never anything you typed or said) and the report itself; Send opens your email app
  with an email to support already filled in, and Copy instead still copies it. The air mouse
  setup's "Send log" opens the same sheet.

### Changed
- A new key row under the D-pad: Keyboard, a Back · Home · Apps pill, and Ask.
- Diagnostics now list the buttons pressed, with repeats merged into one line, and start with the
  app, phone and TV versions.
- While YouTube is on screen, the Keyboard key turns into YouTube search, with a one-time tip.

## [2.0] - 2026-11-22

### Added
- Air mouse: point the phone at the TV and a pointer follows. The phone acts as a Bluetooth mouse,
  so nothing is installed on the TV. Tap the pad to click (the pointer holds still while a finger
  is on the pad), and scroll with two fingers or the strip at the edge, at the touchpad's
  pace and one direction at a time. Pointing pauses when the phone is put down. While pointing,
  Vol + clicks and Vol − is Back.
- The TV's pointer stays hidden until you open the air mouse pad, so it doesn't appear while you
  use the D-pad or touchpad.
- The pointer corrects its own drift after the phone has rested for about two seconds, so the first
  use is smooth without calibrating in the settings.
- An "Air pointing" switch on the pad turns it into a laptop-style mouse pad instead. Phones
  without a gyroscope only have the mouse pad.
- Recenter brings a pointer lost at an edge back to about the middle of the screen.
- Air mouse settings: sensitivity, scroll speed, Smooth Air, a calibration for a pointer that drifts by itself,
  what the volume buttons do, and Unlink.
- The air mouse appears in the mode switch, with a PRO mark, on phones that can be a Bluetooth
  mouse. Picking it before setup offers the setup in place of the pad, and the Paired screen offers
  it once after a first pairing. Setup can be done with the app's own keys, without the TV's remote.
- When a set-up TV is out of reach, the pad shows it reconnecting, and only offers to set it up
  again after 30 seconds.
- Needs Android 9 or later and a phone whose maker left the Bluetooth mouse role enabled.

## [1.4] - 2026-11-13

### Added
- The phone's volume buttons change the TV's volume while the remote is open.
- Keys answer the thumb: a tick and a slight shrink on touch, a firmer buzz and a ring when a hold
  starts. The touchpad shows a dot under the finger and lights the arrow for each step it sends.
- A one-time tip on the remote: "Hold a key to repeat it."
- Screens fade into each other instead of cutting, and follow Android's "Remove animations" setting.

### Changed
- New look for every screen. The D-pad is larger and sits in the middle of the remote with a filled
  OK key; media and volume moved below it.
- Pairing shows where the code appears on the TV and connects by itself on the sixth character. A
  wrong code clears itself for the next try, and a TV that doesn't answer can be tried again
  without going back to the list.
- Typing and YouTube search open as a sheet above the keyboard, which says whether the text has
  reached the TV.
- The TV list has one headline and one line of text, shows which TV is being checked, and lists
  what to check when no TV is found.

### Fixed
- A TV that is on but idle is still found. While no TV is listed, the app now checks the network
  again every 10 seconds instead of only once.

## [1.3] - 2026-11-10

### Added
- TVs are also found by scanning the local network, for networks that block the multicast
  discovery: when discovery finds nothing, and whenever the list is refreshed. A TV can also be
  added by typing its IP address.
- When a TV leaves the network as it switches off (usually one with an IR remote), the app says it
  can't turn it back on and points to the TV's own remote or its network standby setting. It
  remembers this and warns the next time Power is tapped for that TV.

### Changed
- Copied diagnostics also record volume keys and the TV's volume replies, the network scan, and
  when the TV's screen turns on or off.

### Fixed
- A TV switched off from the app no longer switches itself off again when it is next turned on.
  Key presses aren't sent to a TV that has left the network, presses still waiting to go out are
  dropped when the connection closes, and a second Power tap within a few seconds is ignored.
- A TV found both by discovery and by the network scan is listed only once.

### Internal
- Opening TLS connections and keeping the client certificate moved out of the session manager into
  its own class, with a test that the certificate survives an app restart.
- Pairing and reconnect state are reset the same way whenever the app returns to the TV list.
- The text dialogs share their focus and hint code, and the remote's rounded shapes come from the theme.

## [1.2] - 2026-10-17

### Security
- Pin each TV's certificate after pairing, and reject a different device answering at the same address.
- Bound incoming frame sizes and stop logging private data.
- Release builds strip debug and info logs, which contained TV names and addresses.

### Changed
- The release APK is shrunk and obfuscated with R8 (about 14 MB → 1.5 MB).
- The remote screen fits without scrolling, and the D-pad stays visible on short screens.
- The pairing keyboard shows digits and capital letters together.
- Discovery prefers an IPv4 address when a TV reports several, and its animations are cheaper.
- More symbols can be typed through key presses.
- The app starts on a dark window instead of a white one, and control borders are easier to see.

### Fixed
- Text entry stays reliable on TVs that don't apply edits as planned, and the text dialog stays
  in step with the TV's field.
- Discovery and pairing bugs found in review.
- A held key is released when the press is interrupted.
- The system Back button leaves the pairing screen.
- TV model names are upper-cased before filtering.

### Accessibility
- The remote keys work with TalkBack.

### Internal
- Unit tests for the protocol encoders, key mapper, text entry and connection logic.
- Text entry moved into its own class; dead code and unused dependencies removed.

## [1.1] - 2026-10-12

### Added
- YouTube search button, since YouTube's own keyboard can't be typed into from a phone.
- Typing hints and a "Copy diagnostics" option for TVs without text input.
- A play/pause glyph, and the D-pad / touchpad choice is remembered.

### Changed
- Phones are locked to portrait.
- The keyboard button is dimmed when the TV has no text box focused.
- A hint that some apps ignore the media buttons.
- A longer connection timeout.

## [1.0] - 2026-10-10

### Added
- TV discovery over mDNS, with an HTTP name lookup for TVs that don't advertise a name.
- Polo pairing with the code shown on the TV, then the Android TV remote session.
- Full remote: D-pad or touchpad, OK, Back, Home, volume, mute, power and media keys.
- Typing into TV text fields from the phone keyboard.
- Reconnect, and a connection state that shows whether the TV is really reachable.
- The "Deep Code" dark theme.

[Unreleased]: https://github.com/PrasoonDhaneshwar/AirMouseTVRemote/compare/3.0...HEAD
[3.0]: https://github.com/PrasoonDhaneshwar/AirMouseTVRemote/compare/2.0...3.0
[2.0]: https://github.com/PrasoonDhaneshwar/AirMouseTVRemote/compare/1.4...2.0
[1.4]: https://github.com/PrasoonDhaneshwar/AirMouseTVRemote/compare/1.3...1.4
[1.3]: https://github.com/PrasoonDhaneshwar/AirMouseTVRemote/compare/1.2...1.3
[1.2]: https://github.com/PrasoonDhaneshwar/AirMouseTVRemote/compare/1.1...1.2
[1.1]: https://github.com/PrasoonDhaneshwar/AirMouseTVRemote/compare/1.0...1.1
[1.0]: https://github.com/PrasoonDhaneshwar/AirMouseTVRemote/releases/tag/1.0
