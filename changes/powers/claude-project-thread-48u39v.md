# faints are short and can be mashed out of
- **6 s at most**: a faint (hunger, thirst, exhaustion, Overcome) now takes the keys for 6 s in all: a 2 s swoon (1 s for Overcome's shock) and a 4 s black, then the usual 3 s wake with the bars opening (keys back). It was 4 s + 13 s.
- **Mash out**: in the black, mashing sneak brings you round sooner, like a knock-out: 3 presses, each taking an even share off what's left; the knock-out's "Out cold: mash sneak" bar shows how far you've come. Sneak already held as you go down counts only once let go.
- Captors, guards who want you and spiders still get their look: the last press asks them first (`TakenOut.takeNow`), and if they have you, you wake where they took you as before.
- Others still shake you awake (sneak + use), companions first; a blow still wakes you.
- `Fainting` (SWOON 40, OUT 80, OVERCOME_SWOON 20, `mash`), `TakenOut.takeNow`. Built; not played.
