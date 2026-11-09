---
status: open
for: hermes
from: hermes (Andy, Discord, 2026-11-08)
asked: 2026-11-08
thread: 1569144710264791172
claimed_by:
pr:
suggested_owns: [kingdoms/src/client/java/com/jaymoskingdoms/client/spirits/SpiritArms.java, kingdoms/src/client/java/com/jaymoskingdoms/client/spirits/]
---

# Spirits: a faint warrior that manifests when it speaks

How the mana spirits look. Today they are a pair of aura arms behind the shoulder (`SpiritArms`); Andy wants a figure you can half-see, which comes fully into being when it speaks, in the way Johnny Silverhand sits in V's head.

## What's wanted

Andy, with four screenshots and the video "Cyberpunk 2077: Best of Johnny Silverhand":

- **"exactly what i want for the visuals on spirits. a faint but visible warrior."** A whole figure, not only arms — ghostly, translucent, readable against the world, but plainly not solid.
- **"They should also appear fully manifested as you level up when they speak."** Full manifestation is a performance: it happens on level-up and while the spirit is talking, and drops back to faint afterwards. Higher level → more solid, and it holds longer.
- **"commenting on your actions as usual"** — the existing voice (`SpiritVoice`: idle remarks from bond 40, advice from bond 60, act lines, level and bond lines) is unchanged in content. What changes is that a line now has a body to come from.
- **"kind of like Johnny from cyberpunk, but noone else other than the mana attuned can see them."** The attuned-only rule already exists in `SpiritArms` (`Mana.attuned` or `ManaAlchemy.readsMagic`); it must hold for the figure too, including the manifested state.
- **"the first person view of the arms should be more like this"** — the reference frames are first person with the other character in your own view. So the manifested spirit should be drawn in front of the player, where you can see it while it talks, not only behind the shoulder.
- **"only animating the large arms for the quiet one"** — the Quiet One never manifests a body: it animates only its oversized arms, which is all it ever shows anyone (it says nothing until max bond).

## References

In the vault, `Assets/`: `spirit-faint-look.png` (the translucent, wrongly-lit warrior that prompted this), `spirit-firstperson-johnny-1.png` and `-2.png` (first-person framing to aim at), `spirit-current-ingame.png` (where we are now).

## Notes

- **Build on `docs/Spirits-Plan.md`, don't replace it.** `SpiritArms`, the poses table, `SpiritState.show` and the kind colours are the base; this task changes the body that hangs off them.
- **Viewer-side only.** The figure is drawn from the synced `SpiritState` — no new server entity, nothing that has to be tracked for players who cannot see it anyway.
- **Cost discipline:** one figure per nearby out spirit, eased in and out, no per-tick rebuilds. We already have lag complaints; a manifestation that costs frames is worse than no manifestation.
- **What "faint" means:** something you notice at the edge of vision and stop noticing when you fight — never so faint it reads as a rendering bug (which is exactly what `spirit-faint-look.png` looks like now).
- **Design calls to settle in the PR, not in chat:** does the line still also print in chat while the figure speaks, or does the figure replace the chat line? (Recommend: keep the chat line — it survives distance and the player looking away — but say so in the PR.) Does the figure stand facing you, or at your shoulder?
- **Art:** the warrior's body comes from Prometheus once the shape is agreed; ask for it against the same "faint aura" material the arms already use.
- 2026-11-09 16:35 2026-11-09, Andy wants all models without animations animated ('I want tasks set for all the models with no animations'). The spirit figures (tools/blockbench/spirit-figures/*.bbmodel, SpiritFigure.java, posed in code) are among them: whoever builds this task should key the figures' moves (idle, speaking gestures, manifesting and fading) as Blockbench animations, by the method in docs/AI-HANDBOOK.md, or hand the art to Prometheus. No separate task, so the two don't collide.
