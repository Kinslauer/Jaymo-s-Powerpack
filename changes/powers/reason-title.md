# Reason: the pack's new name on the title screen
- **Title logo**: the title screen (and the credits) show our own "REASON" logo in place of MINECRAFT / JAVA EDITION:
  deep blocky stone letters with ivy grown over them, and behind them one warrior with a raised sword and shield facing
  a crowd of mob shadows with faint red eyes. `ReasonLogoMixin` draws `jaymospowers:textures/gui/title/reason.png`
  (drawn by `tools/reason_logo.py`), 256x80, rising 28 units above where vanilla's logo starts.
- **Caption**: the yellow line beside the logo always reads "what's yours" (`ReasonSplashMixin`), on holidays too. Our
  old random splash list is gone.
- **Window title**: "Reason" in place of "Minecraft* 26.3" (`ReasonTitleMixin`), keeping " - Singleplayer" and the like.
- **Pack name**: the CurseForge import and the testers' Prism/packwiz pack are named "Reason". Mod ids, jar and zip
  file names are unchanged.
- Compile-checked against the 26.3 client jar; not played.
