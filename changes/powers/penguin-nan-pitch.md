# Penguins no longer flood the server log with NaN rotation errors

- Ambient Creatures' penguin works out its swimming pitch from its speed with a typo in the sum, and gets NaN (not a number) for about half of its swimming directions. Minecraft threw each one away with an "Invalid entity rotation: NaN, discarding" error, hundreds a minute near penguins (found by the NaN rotation diagnostic near Colette's village). A non-finite pitch is now swapped for the one it has (`PenguinSwimPitchMixin`, only when Ambient Creatures is loaded).
- Built by CI; not played.
