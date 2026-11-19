# Youth's wisp really disappears while it thinks
- **Youth's wisp** (`YouthWispRenderer`): gone off to think, the wisp was made invisible, but its renderer always chose
  its own glowing render type, so it stayed drawn in the sky. An invisible wisp now uses GeckoLib's render type, which
  draws nothing for players, so it leaves view for everyone until it flies back with the answer.
- Built; not played on the server.
