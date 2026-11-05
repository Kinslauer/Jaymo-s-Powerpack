# Alien bodies: humanoid kept rare

Andy (2026-11-01): "Fuse and skitter should move uniquely, the aliens should not all be humanoid, in fact it should be
rare, skitter should move like a spider, and billow should be a real cloud, functioning similar to smoke".

Every alien was a `HumanoidModel` with parts hung on it. The way out, proven on patch 1: keep the humanoid parts in
the mesh (the first-person hand, the band, the chest tap and every pose mixin still find them) but hide them in
`setupAnim`, and build the real body from new root parts with its own gait. The hidden `body` is posed wherever the
insignia should sit (on Skitter it lies face-up on the abdomen), since `XenoBand.submitInsignia` draws from it. Ultimate
spikes (`UltimateSpikes.STYLES`) only fit humanoid bodies, so a reworked alien drops or trims its entry.

## Patch 1 (done, 0.19.34): Skitter, Fuse, Billow
- **Skitter:** a spider, low to the ground, 8 legs in a spider's alternating fours, body bob, abdomen sway, flat crouch,
  biting lunge. (0.19.62, Andy: "the same model it had before, only going into the crawl when you go prone") Standing it's
  the old upright shape again; only prone does it drop into the spider, which crawls up walls and along ceilings
  (tipped against the wall by `SkitterCeiling`, synced `XenoformState.CLIMB`) and turns back when it stands.
- **Fuse:** a creeper's build, no arms, 4 stubby legs shuffling in diagonal pairs with a waddle, a bounce and a late
  nod; swells on each punch.
- **Billow:** a cloud mass with a face puff and churning puffs that trail as it moves. Works like wholly-smoke: the
  swimming pose's low box, seeps through anything short of a full block (not what it rests on), shots and creatures
  pass through, sheds wisps as it drifts.

## Patch 2 (done, 0.19.62): beasts on four legs
Howl (wolf), Prowl (cat, stalking crouch), Tusk (boar, head-down charge), Gorger (armadillo, rolls into a ball for its
roll), Gyre (turtle, shell spin). Velox becomes a raptor: leaning forward on two legs with a long balancing tail.
Built with a synced `XenoformState.ACT` ("charge" for Tusk's Charge and Gorger's Horn Charge, "roll" for Gorger/Gyre
rolled up, "spin" for Gyre's Shell Spin, set by `XenoformPower.syncAct`) read as `XenoformClient.ACT`. None of the six
wears Ultimate spikes now (the spike model is a man's).

## Patch 3 (done, 0.19.66): small critters
Croaker (squat frog, hops instead of walking), Slipfin (axolotl, belly crawl on land, undulating swim), Locust
(six legs, folded hind legs that kick on a leap), Hive (hovering insect, buzzing wings, legs dangling), Gremlin (a
hunched imp on its knuckles, the big skull bobbing). Croaker takes the low box (`XenoformState.LOW_BOX`, with Billow).

## Patch 4 (done, 0.19.75): fliers
Talon (a bird: perches upright, wings spread in the air), Keen (a bat, hangs from ceilings), Wyrm (a small dragon on
four legs with the wings it has), Lumen (an orb of light with trailing motes). Wisp keeps its hooded legless ghost.
Keen's hang is synced as the "hang" act; Lumen is drawn a second time fullbright.

## Patch 5 (done, 0.19.82): floaters and blobs
Ooze and Magma Ooze (a cube that squashes and bounces as it moves; both take the low box), Prism (a crystal cluster that walks on shards), Cortex (a guardian-like eye fish, floats), Murk (a
squid, tentacles trailing), Gnasher (a shark, swims level, drags itself on land), Blight (three heads over a spine,
floating). Pyre stays humanoid (Andy: "pyre should still be fairly humanoid"). Prism's Reflect is synced
as the "root" act, Gnasher's Death Roll as "roll"; Cortex's Claw Snap is now Spine Snap.

## Patch 6 (done, 0.19.84): non-human bipeds
Bulwark (Andy: the four arms emphasised, a thinner body, the arms held out in an idle stance that keeps swapping which pair is raised), Knell (a hunched horned beast), Rime (a stooped ice brute), Shade (a thin
crawling shadow that unfolds tall), Thornwild (a treant with root feet), Lurk (on all fours when stalking, tall when
it stands), Rivet (a living-metal blob that stands up when it fights), Nadir and Dud (shapeless). Synced stances (XenoformState.ACT): Bulwark carry/leap, Knell
burrow/toll/sense, Shade hold/stare, Thornwild roots/bloom, Lurk fade, Rivet pour/fight, Dud dead/roll.

## Kept humanoid (7 of 41)
Marrow, Swathe, Mirage, Polestar, Towerback (the Giants' kin), Duplex (a golem's build already) and Pyre (Andy,
2026-11-03).

## Open questions
1. ~~Should small bodies get a small box too?~~ Andy (2026-11-01): yes. Ooze, Magma Ooze and Croaker get a low box
   (as Billow's) with their new bodies in patches 3 and 5, fitting through one-block gaps.
2. Is that the right six to keep humanoid?
