# timed key prompts on finishers, first on kneeling bosses
- **Prompted finishers** (the QTE Finishers mod's idea only; our own code): a finisher can now ask for keys. Before each
  blow one of your four ability keys (R, G, V, B by default) shows under the crosshair with a bar running out. Press it
  in time and the blow lands. Press the wrong key, or none, and you are thrown off: flung back, hurt for 2 hearts, and
  they live. The ability keys do nothing else while it plays.
- **Great foes**: a boss brought to a knee with a fifth of its health or less (Kingdoms' boss posture) can be finished
  where it kneels with the usual sneak + hold (or sneak + double click and hold), with whatever is in hand. That
  finisher is prompted. Finishers on downed foes are unchanged and ask for nothing.
- Classes: `core/finisher/Prompts` (attachments `finish_open`, `finish_prompted`), `client/finisher/PromptClient`.
  Tuning at the top of `Prompts` (`WINDOW`, `LEAD`, `GAP`, `MISS_HURT`).
- Compiled; not played. The finisher poses were made for a body lying down, so on a kneeling boss the blows aim low.
