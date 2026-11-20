# forest-fire pack - Peterwolf's Forest Fire & Firefighting in the pack
- **Peterwolf's Forest Fire & Firefighting** (MIT, by Peterwolf) now ships in the pack, ported to 26.3 by us
  (`ports/forestfire`, see its `PORT.md`): wildfires that spread with the wind and the slope, throw embers ahead, climb
  into the crowns and burn trees through, with fire incidents, smoke, and a firefighter's kit (pump, hoses, splitters,
  nozzles, tanks, sprinklers, breathing mask, air tank, Pulaski, rake). Its water bomber is left out (it needs the
  Planes mod, which isn't in the pack); its title-screen dedication stays as Peterwolf wrote it.
- **Its options are set once per install** (`ForestFirePreset`, preLaunch): its villager panic off (ours answer fires
  their own way), its heat damage off (Kingdoms counts its heat into Kitchen's body heat instead), and lower limits so
  the friends' server on the Steam Deck keeps up (3000 burning blocks, 200 spread checks and 8 embers a tick, 4 fires at
  once, 48 chunk tickets, 120 smoke particles). A change made after that is never touched; delete
  `config/jaymospowers-forestfire-preset-v1` to set them again.
- Pack wiring: built by `buildForestFire` and shipped in the modpack, the CurseForge zip, the server mods and packwiz.
- The port was built and run on a plain 26.3 server: a grass and oak fire became an incident and spread (33 to 197
  blocks). Not played in the full pack; the preset not run in game.
