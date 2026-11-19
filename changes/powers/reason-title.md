# Reason: the pack's new name on the title screen
- **Title logo**: the title screen shows our own blocky stone "REASON" logo in place of MINECRAFT / JAVA EDITION
  (the 1-in-10000 "Minceraft" logo shows it too). Drawn by `tools/reason_logo.py` over vanilla's `gui/title` textures.
- **Caption**: the yellow line beside the logo always reads "what's yours" (`ReasonSplashMixin`), on holidays too. Our
  old random splash list is gone.
- **Window title**: "Reason" in place of "Minecraft* 26.3" (`ReasonTitleMixin`), keeping " - Singleplayer" and the like.
- **Pack name**: the CurseForge import and the testers' Prism/packwiz pack are named "Reason". Mod ids, jar and zip
  file names are unchanged.
- Compile-checked against the 26.3 client jar; not played.
