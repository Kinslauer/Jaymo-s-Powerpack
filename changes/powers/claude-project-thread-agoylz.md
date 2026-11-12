# Dynamic Waters rivers replace Streams Reflowing
- **Rivers**: the pack now ships our own 26.3 port of AdamNew's Dynamic Waters (MPL-2.0, source in `ports/dynamicwaters`,
  credits and every change in `ports/dynamicwaters/PORT.md`). Rivers run downhill from the mountains to the sea, carve
  valleys and gorges, push swimmers, boats and dropped items along with the current (faster in rain and storms), freeze
  over in the cold and break up into drifting ice floes. Its config screen is in Mod Menu.
- Streams Reflowing is gone from the pack build, and so is `core/StreamsPreset` (it only set Streams' world preset).
  `water/Currents` stays for running water outside the rivers and for splashes; Dynamic Waters pushes its own river
  channels.
- Needs a new world: old worlds keep their old rivers and get odd seams at new chunks.
- Built and boot-tested on a dedicated server; not played.
