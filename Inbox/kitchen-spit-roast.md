---
status: in-review
for: hermes
from: coordinator
asked: 2026-11-07
thread: Claude project thread "Check open work against Hermes fixes" (asked alongside the fishing send-back, PR #1114)
pr: 1123
suggested_owns:
  - kitchen/src/main/java/com/jaymoskitchen/cooking/spit/ (new)
  - kitchen/src/client/java/com/jaymoskitchen/client/spit/ (new)
  - kitchen/src/main/resources/assets/jaymoskitchen/lang/parts/spit.json (new, or the Kitchen lang file if Kitchen has no parts)
claimed_by: hermes-fixer
---
# Spit roast over an open fire

**Andy's words (2026-11-07 17:33):** "You should also be able to cook it on an open fire, similar to other animals. Add
a spit crafted with sticks that animates the animal over the fire, and players can either rip chunks off, (animated)
or get the cooked product after"

**What's wanted:**
- A spit block crafted from sticks, placed over a campfire or other open fire (Kitchen's `jaymoskitchen:heat_sources`
  tag, lit).
- Put a fish (from the new fishing, #1114) or another animal carcass (Kitchen corpses) on it: the body shows on the
  spit and turns slowly over the flames, cooking over time (raw, cooked, burnt, with visible colour change).
- While it roasts or once cooked, a player can rip chunks off by hand (an animated tearing motion, gives a cooked
  piece each time), or take the whole cooked product when it's done (cooked fish/fillets, cooked meat in the amounts
  butchering would give).
- Tuning numbers (cook time, chunks per animal) as `static final` constants.

**Where to start:** Kitchen `cooking/Station.java` (campfire is a grill station), `cooking/Grill.java` and
`client/GrillView.java` (campfire cooking), `client/LarderGames.java` (there is already a spit-roasting minigame in
the plain larder: reuse ideas, not the minigame), `corpse/Corpses.java` + `CorpseKind` (FISH) and the butcher table
for meat yields. Ragdoll/corpse rendering is in Kitchen's corpse package. Coordinate with the fishing task
(fishing-real-tug) only through Kitchen's fish items; don't edit its files.

## Notes
- 2026-11-07 13:21 Review (head 0811178): changes needed. Good: stick recipe, needs a lit fire below (campfire or the heat_sources tag), raw/cooked/burnt with constants, whole carcass gives butchering's drops, Kitchen 0.5.87 plus a MODLOG entry, checks green. To do: 1) Client crash at launch: Payloads.SpitRip is never registered (Payloads.register has no PayloadTypeRegistry.clientboundPlay().register(SpitRip.TYPE, SpitRip.CODEC)), but JaymosKitchenClient.java:140 registers a receiver for it, which Fabric rejects for an unregistered type; sending it (SpitBlock.java:211) would fail too. Register it. 2) The colour never changes while it roasts: serverTick (SpitBlockEntity.java:463-465) only calls setChanged at the cooked/burnt thresholds, never sendBlockUpdated, so the client keeps the doneness it got when the food went on (raw) until someone clicks it. Call markUpdated() when it crosses COOKED and BURNT (and every second or so if you want a smooth fade). 3) Ripping a chunk off a carcass throws the rest away: SpitBlock.java:202-206 gives the first drop once and shrinks the stack, and a carcass is one item, so one chunk empties the spit and the rest of the meat is lost. Give each roast a number of chunks (CHUNKS_PER_ANIMAL and similar constants; SpitBlockEntity.CHUNK is defined but unused): each tear gives one cooked piece and lowers what's left, the last tear clears it, and the sneak-take gives what remains. 4) The tearing animation doesn't play: SpitBlockEntityRenderer.java:102 removes the RIPPING entry on the first frame and never counts it down, so the shake lasts one frame, and the player's own hand doesn't move. Count it down per tick (store the game time it started), and swing the player's arm (player.swing) when they tear. 5) It doesn't look like a spit roast: the renderer draws the item model spinning around the vertical axis (SpitBlockEntityRenderer.java:134, Axis.YP) like a turntable. Andy asked for the animal on a spit turning over the fire: lay it along the spit and rotate around the spit's horizontal axis, and for a carcass draw the body (Kitchen's corpse model for that animal) rather than the corpse item icon. Use the level's game time plus partial ticks for the angle; the 'turn' field (:83, :99) advances once per frame per renderer, so the speed changes with frame rate and the number of spits. 6) Formatting: Payloads.java re-indents Sip's closing brace and register() (:530-547) and en_us.json's new keys use mixed indentation; keep the file's tabs. Then build, pr_checks, push, set status=in-review.
- 2026-11-07 13:52 hermes-fixer took over the review fixes from hermes-b.
- 2026-11-07 15:21 Review (head f2637b9): changes needed (second review). Fixed: the colour updates every second while it roasts (markUpdated), chunks per roast with constants, the spit turns around its horizontal axis by game time, a carcass draws its body, the player's arm swings on a tear, en_us.json indentation. To do: 1) Still a client crash at launch (item 1 of the last review, unchanged): Payloads.register() still has no PayloadTypeRegistry.clientboundPlay().register(SpitRip.TYPE, SpitRip.CODEC); only the indentation around SpitRip changed. JaymosKitchenClient registers a receiver for SpitRip, which Fabric refuses for an unregistered type. Add that one register line. 2) A carcass gets one chunk, not four: SpitBlock.java:170 sets be.chunks = chunksFor(held) after held.shrink(1) at :168, so for a carcass (a stack of one) 'held' is already empty and chunksFor gives CHUNKS_PER_PIECE. Use chunksFor(be.items.get(0)) (or count before shrinking). 3) The cooked carcass comes off raw: cookedProduct returns corpse.butcheredDrops() unchanged (SpitBlock.java:127), which is the corpse's raw loot (raw beef, raw porkchop and the rest), so tearing or taking a roasted carcass gives raw meat. Map each drop through cookedOf (keep leather, bones and the like as they are). 4) The tear shake still lasts one frame: SpitBlockEntityRenderer extractRenderState does RIP_START.remove(pos) (around :81), so the countdown is read once and gone. Use get, and remove it only once 8 ticks have passed. 5) Small: SpitBlockEntity load/save/getUpdateTag lines for 'Chunks' and the SpitRip record body in Payloads are one tab short. Then build, pr_checks, push, set status=in-review. If item 1 is still open next time, this goes to Andy as blocked.
- 2026-11-07 15:22 hermes-fixer took over the review fixes from hermes-b.
