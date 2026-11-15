# Explosions: flying debris, ground dust, cave-in grit, flash, ringing ears
- Andy's pick from Explosion Overhaul (custom licence, ideas only, our own code): everything a blast does besides break blocks, for every explosion from any mod. How blocks break is unchanged.
- **Debris**: chunks of the blocks a blast broke are flung up and out, tumble, bounce where they land and lie a few seconds before shrinking away (`client/blast/DebrisChunk`). Only blasts that broke something throw them, more the more they broke (6 plus one per 2 blocks, at most 110), and never more than 300 in a tick across all blasts.
- **Dust**: a ring of dust in the colour of the ground (its map colour, mixed toward grey-brown) rolls out along it, and a little dark smoke rises from the middle.
- **Cave-ins**: underground, the ceiling round a blast (twice its radius) sheds falling dust and loose grit for about 2.5 seconds.
- **Flash**: a short warm whiteout when a blast goes off in your sight, up to 14 radii away, stronger for bigger blasts, nearer ones and when you look at it. Blocked by walls.
- **Ringing ears**: within 1.8 radii of a blast your ears ring (new sound `blast.ring`, made by `tools/gen_blast_sounds.py ring`), other sounds start muffled (down to 25%) and come back over 2-7 seconds, and the view sways slowly (`ScreenShake#daze`, turned down with `/shake` like the rest).
- **Screen shake** from an explosion is 1.4x harder when neither you nor the blast can see the sky.
- Less of all the particles on Decreased/Minimal particles and in low-end mode. Client only, nothing sent. Tuning at the top of `client/blast/BlastFx`.
- Built; not played.
