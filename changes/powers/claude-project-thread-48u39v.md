# faints can be mashed out of
- **Mash out**: in a faint's black, mashing sneak brings you round sooner, like a knock-out: 3 presses, each taking an even share off what's left; the knock-out's "Out cold: mash sneak" bar shows how far you've come. Sneak already held as you go down counts only once let go.
- **Timing**: the swoon is 2 s (1 s for Overcome's shock; was 4 s / 2 s). The black is 15 s (was 13 s) so friends and villagers have time to wake you; the mash cuts it short. The 3 s wake with the bars opening is unchanged.
- Captors, guards who want you and spiders still get their look: the last press asks them first (`TakenOut.takeNow`), and if they have you, you wake where they took you as before.
- Others still shake you awake (sneak + use), companions first; a blow still wakes you.
- `Fainting` (SWOON 40, OUT 300, OVERCOME_SWOON 20, `mash`), `TakenOut.takeNow`. Built; not played.
