# Changelog

All notable changes to Air Mouse TV Remote. Versions match the git tags and GitHub releases.

## [Unreleased]

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

[Unreleased]: https://github.com/PrasoonDhaneshwar/AirMouseTVRemote/compare/1.3...HEAD
[1.3]: https://github.com/PrasoonDhaneshwar/AirMouseTVRemote/compare/1.2...1.3
[1.2]: https://github.com/PrasoonDhaneshwar/AirMouseTVRemote/compare/1.1...1.2
[1.1]: https://github.com/PrasoonDhaneshwar/AirMouseTVRemote/compare/1.0...1.1
[1.0]: https://github.com/PrasoonDhaneshwar/AirMouseTVRemote/releases/tag/1.0
