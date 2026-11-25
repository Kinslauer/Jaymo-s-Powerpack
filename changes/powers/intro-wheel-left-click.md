# Shift-click pins advancements, sound browser left clicks and the Notices tips toggle work again
- Minecraft 26.3 numbers mouse buttons from 1 (left is 1, right is 3), and three screens still used the old numbers:
  - **Advancement pins**: shift-clicking an advancement never pinned it (the check waited for button 0). It pins again.
  - **Sound browser**: every left click on a sound set it to full volume (button 1 was meant as the right click). Left click mutes or unmutes and dragging the bar sets the level again; right click resets to full.
  - **Notices**: clicking the tips toggle did nothing (waited for button 0). It turns the tip cards on and off again.
- Classes: `AdvancementPins`, `SoundBrowserScreen`, `NoticesScreen`.
- Built; not played.
