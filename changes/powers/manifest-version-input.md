# The friends' zip shows the pack's real version in CurseForge

- The zip's CurseForge manifest is remade whenever the pack version changes. Before, Gradle kept reusing an old one, so the 0.27.46 zip imported as "Reason 0.27.39" (the mods inside were right). `curseforgeManifest` now lists the version as an input.
- Build change only; nothing in game changes.
