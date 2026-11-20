# Z Fighter aura rework - flame shell and white core

- **Aura rework**: a Z Fighter charging up now shows a flame shell round his body with a bright white core at its heart
  (`client/power/zfighter/ZFighterAuraFlame.java`, drawn client-side beside the ki-aura wisps), and cracking ground
  rises under him while he charges (`power/zfighter/ZFighterPower.java`, via `core/GroundCracks`).
- New file: `client/power/zfighter/ZFighterAuraFlame.java`. Changed:
  `client/power/zfighter/ZFighterPowerClient.java` (registers the flame layer),
  `power/zfighter/ZFighterPower.java` (ground cracks while charging).
- Built; not played in game.
