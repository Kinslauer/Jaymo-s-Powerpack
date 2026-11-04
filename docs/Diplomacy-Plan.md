# Diplomacy and elections plan (Kingdoms 0.7.519)

Otectus pick 10 (`docs/Otectus-Plan.md` row 10, from MCA: Kingdoms; our own code from the idea, none of that mod's code
or art). Andy-approved plan text: "Treaties between realms (recognition, safe passage, trade charters, aid) carried by
envoys, which players' realms can sign too, and succession by election or regency in some realms instead of always the
heir."

Scope: Kingdoms only (`kingdoms/`). Powers is not touched, so the root `gradle.properties` stays at 0.20.12 and the root
`MODLOG.md` gets no entry. Every new comment is tagged `(0.7.519)`. All tuning numbers are `static final` constants at
the top of their class. Players get no new slash commands: everything they do is on a screen (the envoy's offer, the
ruler's desk). Players hear about it through Tidings letters (big news only), town boards (`Post.news`), MCA/Post
letters (`Post.report`), speech bubbles (`Speech.say`) and the MCA interact info lines (`Standing`). Nothing new goes to
chat.

Every hook below was checked against the current source in this worktree (line numbers are as of 0.7.512). Check each
Minecraft API named here against the 26.3 sources (`./gradlew genSources` in `kingdoms/`) before using it. The ones to
check are flagged **[26.3]**.

---

## 1. What the player sees

- **Realms treat with each other.** Once a day, NPC kingdoms near each other may send envoys. An envoy is a villager with
  the realm's banner who rides along the King's Roads (the courier machinery), and you can meet one on the road. At the
  far court the offer is weighed by the ruler's temper, the realm's creed, the tension between the two realms and old
  grievances. A treaty is signed or refused, and the envoy rides home. The town boards carry the small treaties; the
  Tidings carry only the big ones.
- **Four treaties**, each between two free realms (the heads of their sides; vassals share their overlord's):
  - **Recognition**: tension between the pair eases every day, and they can't slide into rivalry. Every other treaty
    needs it first.
  - **Safe Passage**: each realm's guards let the other's people by (no halting, no search, no gate toll).
  - **Trade Charter**: their caravans run more often, carry an extra cart and come home fuller. Each realm's people
    get 10% better prices from the other's traders and pay half the gate toll.
  - **Aid**: when either goes to war, the other joins its side as a pledged ally (its soldiers march in the war
    parties and it shares the spoils). If it can't march, it sends coin.
- **Breaking faith.** If you declare war on a treaty partner, or renounce a treaty, it is torn up. Tension jumps
  between the two realms, and a bit with everyone else who holds a treaty with the breaker. A player sovereign loses
  reputation with the wronged realm and with every other treaty partner.
- **Players' realms sign too.** When an envoy comes to a realm whose crown a player wears, it waits in the hall for up
  to two days, and the sovereign gets a letter about it. The sovereign presses "Hear the Envoy" on its Interact page
  (with MCA) or right-clicks it (without MCA), then picks Sign, Refuse or Not yet. To send envoys, the sovereign seeks
  an audience with their own steward. That opens the **Ruler's Desk**, which lists the realms in reach: their relation,
  creed, tension, the treaties in force (with a Renounce button), each kind's odds, and "Send an Envoy" (paid from the
  treasury).
- **Succession.** Coin realms elect their next ruler (the guilds choose). Realms of the Warriorborn or Frostborn who
  follow the Old Blood or Conquest acclaim one at a moot. Everywhere else, blood inherits, but when the only heir of
  the old ruler's family is a child, a councillor governs as **regent** until the child comes of age, then steps down.
  Notables are the realm's grown villagers (and its sworn players of Knight rank or higher), scored by wealth
  (profession level), standing (guard service, a sworn player's standing), kin, gifts and luck. A ruler's MCA interact
  info shows how the realm's succession works, how this ruler came to the throne, "Regent for X" if they're a regent,
  and the realm's treaties.

---

## 2. New files

All new main classes are in `kingdoms/src/main/java/com/jaymoskingdoms/` (package `com.jaymoskingdoms`). The client
screens are in `kingdoms/src/client/java/com/jaymoskingdoms/client/`.

### 2.1 `Treaties.java` (new, `public final class Treaties`)
The treaty records, their effects, signing, lapsing and breaking.

```java
public enum Kind { RECOGNITION, PASSAGE, CHARTER, AID;   // ordinal indexes the tables below
    String key()                 // "recognition" | "passage" | "charter" | "aid"
    Component named()            // "treaty.jaymoskingdoms.<key>"
    Component the()              // "treaty.jaymoskingdoms.<key>.the"
    static @Nullable Kind parse(String) }

record Treaty(String kind, int a, int b, long signed, long until, int proposer)   // a < b; times are game ticks
record Book(Map<String, Treaty> treaties, long lastDay)                           // key = WarData.pair(a,b) + "|" + kind
static final AttachmentType<Book> TREATIES =                                       // Overworld, persistent
    AttachmentRegistry.create(JaymosKingdoms.id("treaties"), b -> b.persistent(Book.CODEC));
```

Constants (top of class):

| Constant | Value | Meaning |
|---|---|---|
| `DAYS` | `{30, 10, 15, 20}` | Length of each kind, in days (RECOGNITION, PASSAGE, CHARTER, AID). |
| `DAY_TICKS` | `24000L` | |
| `RENEW_TICKS` | `24000L * 2` | This close to the end, a treaty comes up for renewal. |
| `RENEW_EASE` | `10` | NPC pairs renew if both score at least `Envoys.ACCEPT_AT - RENEW_EASE`. |
| `NEEDS_RECOGNITION` | `true` | Passage, charter and aid need recognition first. |
| `RECOGNITION_EASE` | `3.0F` | Daily tension eased (added to `GreatWars.stir`'s delta). |
| `PASSAGE_EASE`, `CHARTER_EASE` | `1.0F`, `1.0F` | Daily ease from each. |
| `AID_EASE` | `GreatWars.ALLIED_EASE` (5.0F) | An aid pair eases like allies. |
| `CHARTER_PRICE` | `0.10F` | Share off a villager's prices for the partner's people. |
| `CHARTER_TOLL_PERCENT` | `50` | The gate toll (`Approaches.TOLLS`) is this share for the partner's people. |
| `CHARTER_EVERY_PERCENT` | `60` | A charter caravan's next send comes after this share of `Sort.every`. |
| `CHARTER_CARTS` | `1` | Extra carts a charter caravan may take. |
| `CHARTER_RETURN` | `20` | Points added to `TradeRoutes.RETURN_SHARE` for the load home. |
| `CHARTER_REACH` | `1.5` | A charter partner counts as in reach up to this times `Sort.reach`. |
| `CHARTER_PULL` | `2.0` | And sorts as if this many times nearer. |
| `AID_LEAN` | `3.0` | Added to `GreatWars.lean` toward an aid partner. |
| `AID_COIN_SHARE` | `0.10` | An ally who can't march sends this share of its treasury once per war... |
| `AID_COIN_MOST`, `AID_COIN_LEAST` | `100L`, `5L` | ...at most this, and nothing under this. |
| `BREAK_TENSION` | `25.0F` | Tension added between breaker and wronged on a break. |
| `BREAK_TENSION_WITNESS` | `8.0F` | Added between breaker and each of its other treaty partners. |
| `REP_BREAK` | `-150` | A player sovereign's reputation with the wronged realm. |
| `REP_BREAK_WITNESS` | `-30` | And with each of the breaker's other treaty partners. |
| `BREAKERS` | `EnumSet.of(TYRANT, WARLORD)` | NPC tempers that may break a treaty for a war. |
| `BREAK_CHANCE` | `0.25F` | A breaker's chance a day once tension is at `GreatWars.WAR_AT`. |
| `CHECK_TICKS` | `1200` | How often the daily round is looked at. |

Methods (package-private unless noted):
- `register()`: `ServerTickEvents.END_SERVER_TICK` to `tick`, which runs `daily` once per `Production.day`.
- `book(server)` / `edit(server, UnaryOperator<Book>)` on `Factions.level(server)`, the `GreatWars.ledger` pattern.
- `head(server, Faction)`: `Wars.top(server, f)`. Every effect check compares heads, so vassals share their overlord's
  treaties.
- `has(server, a, b, Kind)`, `any(server, a, b)`, `of(server, f)` (a list of treaties), `partners(server, f, Kind)`.
- `daysLeft(server, Treaty)`.
- `sign(server, a, b, Kind, proposer)`:
  - Stores or renews the treaty (`until = now + DAYS*DAY_TICKS`).
  - Recognition between stored RIVALs sets them NEUTRAL (`Relations.set`).
  - Aid with a partner already at war joins at once (`joinWar`).
  - Announces through `KingdomEvents.announce`: `message.jaymoskingdoms.treaty.great.<kind>` if `big(...)`, else
    `message.jaymoskingdoms.treaty.signed.<kind>`.
- `big(server, a, b, kind)`: kind is AID, or either side has a sovereign (`Crowns.sovereign`), or kind is CHARTER and
  both are worth at least `GreatWars.GREAT_WORTH` (`Wealth.worth`).
- `daily(server, level)`:
  - Drops treaties whose side fell, became a vassal, or stopped being a realm. It announces
    `message.jaymoskingdoms.treaty.lapsed` (board only).
  - Renewals: an NPC–NPC pair renews quietly if both `Envoys.score` at least `ACCEPT_AT - RENEW_EASE`
    (`message.jaymoskingdoms.treaty.renewed`, board). With a player side, the NPC side sends a renewal envoy
    (`Envoys.dispatch(..., renew=true)`). A player–player treaty is renewed from the desk.
  - Treaties past `until` lapse (board line).
- `broken(server, breaker, wronged, @Nullable Kind kind)` (`kind == null` means every treaty between them, for a war):
  1. Removes the treaties.
  2. `GreatWars.setTension(server, breaker, wronged, tension + BREAK_TENSION)`.
  3. For every other partner W of the breaker: `setTension(breaker, W, +BREAK_TENSION_WITNESS)`.
  4. If the breaker has a sovereign who is online: `Reputation.change(sov, wronged, REP_BREAK)` and
     `Reputation.change(sov, W, REP_BREAK_WITNESS)`. A break by a player is always made online (desk or war command),
     so offline needs no queue.
  5. Announces `message.jaymoskingdoms.treaty.great_war` (war, Tidings), or `message.jaymoskingdoms.treaty.great_broken`
     (aid or a player side, Tidings), else `message.jaymoskingdoms.treaty.broken` (board).
- `warDeclared(server, a, d)` (from `Wars.declare`): if `any(a, d)`, then `broken(server, a, d, null)`.
- `mayBreakForWar(server, from, on, level)` (from `GreatWars.stir`): no treaty, or `from`'s temper is in `BREAKERS` and
  `random < BREAK_CHANCE`, or tension is at `TENSION_MAX`.
- `warBegun(server, war)` (from `Wars.begin`): for each principal P, `joinWar(server, war, P, partner)` for each AID
  partner of P.
- `joinWar(server, war, principal, ally)`:
  - Skips the ally if it is the foe or fallen. If it also holds any treaty with the foe, it stands aside
    (`message.jaymoskingdoms.treaty.aid_torn`, board).
  - If it is free (no war `Wars.warsOf(...)`, not pledged `GreatWars.pledgedTo == -1`, `overlord < 0`), it is pledged
    (`GreatWars.pledge(...)`, `message.jaymoskingdoms.treaty.great_aid`, Tidings). If it has a sovereign:
    `Post.report(sov, message.jaymoskingdoms.treaty.aid_called)`. Then `Wars.syncRelations(server)`.
  - Otherwise it sends coin: `min(AID_COIN_MOST, treasury*AID_COIN_SHARE)` if at least `AID_COIN_LEAST`, moved between
    treasuries with `Factions.update(...withTreasury)` (`message.jaymoskingdoms.treaty.aid_coin`, board).
- `passage(server, Faction guardRealm, ServerPlayer p)`:
  - False if `Identities.hidden(p, guardRealm.id())` (masked: nobody they know) or `Theft.wanted(p, guardRealm.id())`.
  - Otherwise the player's realm is `Factions.of(server, p.getUUID())`, and passage holds if its head is not the guard
    realm's head and `has(PASSAGE)`.
- `chartered(server, Faction x, Faction y)`: `has(head(x), head(y), CHARTER)`.
- `charterPlayer(server, Faction landRealm, ServerPlayer p)`: as `passage`, but for CHARTER and without the mask check.
- `prices(AbstractVillager villager, Player player)` (from `VillagerPriceMixin`):
  - The land's owner is `Banners.owner(server, ChunkPos.containing(villager.blockPosition()))`, as `Treasuries.traded`
    does. If `charterPlayer` holds, then for each offer
    `offer.addToSpecialPriceDiff(-max(1, floor(offer.getBaseCostA().getCount() * CHARTER_PRICE)))`, the `Faiths.prices`
    pattern.
- `lines(server, Faction f)`: the Standing treaty line and its hover (see 5.4).

### 2.2 `Envoys.java` (new, `final class Envoys`)
Envoys on the road, proposals, the NPC answer, and hearing an envoy. The movement is modelled on `Couriers` (Errand
record with a saved body, appear/leave, unseen travel along `Roads.ahead`, a courier's horse). It reuses
`Couriers.mount` (made package-private), `Couriers.HORSE_TAG` cleanup, `Couriers.WALK_SPEED/RIDE_SPEED/PACE/RIDE_PACE/
RIDE_FROM/APPEAR/LEAVE/WAIT`, `Roads.ahead` (`Roads.java:995`) and `Attention.carry` for teleports. It copies only the
small take-out/put-back helpers, which are private to `Couriers`.

```java
record Mission(int id, int from, int to, String kind, boolean renew, int leg, BlockPos home, BlockPos goal, BlockPos at,
    long since, long until, UUID envoy, boolean rides, CompoundTag body, Optional<UUID> sender, int answer, String reason)
// leg: TO=0, WAIT=1, BACK=2; answer: -1 pending, 0 refused, 1 signed
record Book(Map<String, Mission> missions, int nextId, long lastDay)
static final AttachmentType<Book> MISSIONS =                                  // Overworld, persistent
    AttachmentRegistry.create(JaymosKingdoms.id("envoy_missions"), b -> b.persistent(Book.CODEC));
public static final AttachmentType<Integer> ENVOY =                           // on the envoy villager, persistent
    AttachmentRegistry.create(JaymosKingdoms.id("envoy"), b -> b.persistent(Codec.INT));
```

Constants:

| Constant | Value | Meaning |
|---|---|---|
| `EVERY` | `10` | Ticks between looks at envoys (Couriers' pace). |
| `RANGE` | `GreatWars.TENSION_RANGE` (1600.0) | Realms this far apart can treat. |
| `ACCEPT_AT` | `50` | Score to sign. |
| `ACCEPT_LUCK` | `8` | ± random added to an NPC's score when it decides. |
| `ODDS_BAND` | `10` | Desk odds: likely at `ACCEPT_AT+10` or more, maybe within ±10, else unlikely. |
| `PROPOSE_SLACK` | `5` | A proposer must itself score at least `ACCEPT_AT-5`. |
| `PROPOSE_CHANCE` | `0.10F` | Per eligible NPC→NPC pair a day. |
| `PLAYER_PROPOSE_CHANCE` | `0.15F` | Per eligible NPC→player-realm pair a day. |
| `MISSIONS_MOST` | `6` | Envoys on the roads at once (one outgoing per realm, one waiting per player realm). |
| `THINK_TICKS` | `Wars.NPC_ANSWER_TICKS` (200L) | How long an NPC court hears an envoy. |
| `WAIT_TICKS` | `Couriers.WAIT` (48000L) | How long an envoy waits for a sovereign. |
| `HOME_GONE` | `16.0` | Home again, they go in once no player is this near (discarded). |
| `HEAR_RANGE` | `8.0` | A sovereign answering must be this near the envoy. |
| `ENVOY_COST` | `20L` | Treasury (emerald units, `Money.UNIT` each) a player realm pays to send one. |
| `REP_KILL_ENVOY` | `-200` | Killer's reputation with the sender realm. |
| `REP_KILL_GUEST` | `-60` | And with the realm whose land it died on (it was their guest). |
| `KILL_TENSION` | `15.0F` | Tension between sender and target when an envoy dies on the target's land. |
| `KIND_BASE` | `{60, 45, 50, 30}` | Score base per kind. |
| `FRICTION_WEIGHT` | `8` | Per point of `GreatWars.friction`. |
| `TENSION_WEIGHT` | `0.6F` | Per point of tension. |
| `KINDRED_BONUS` | `10` | Friction 0. |
| `RIVAL_PENALTY`, `TRUCE_PENALTY` | `20`, `10` | Stored RIVAL; a truce still running (`WarData.truce`). |
| `COMMON_FOE_BONUS`, `NO_FOE_PENALTY` | `20`, `10` | Aid: a third realm both are RIVAL/WAR with, or with tension ≥ `GreatWars.RIVAL_AT`. |
| `RICH_TRADE_BONUS` | `10` | Charter: both worth ≥ `GreatWars.TRADE_WORTH` (250). |
| `PURIST_KIN`, `PURIST_STRANGER` | `15`, `20` | A PURIST judge, same / other blood (`Purists.blood`). |
| `REP_PER_POINT`, `REP_MOST` | `10`, `30` | A player side: `clamp(Reputation.get(sov, judge)/10, -30, 30)`. |
| `TEMPER_BIAS` | 6×4, see below | By judge temper ordinal × kind. |
| `CREED_BIAS` | 6×4, see below | By judge creed ordinal × kind. |

```
TEMPER_BIAS  (REC, PASS, CHARTER, AID)     CREED_BIAS  (REC, PASS, CHARTER, AID)
TYRANT   { -5, -15,   0,   5}               CROWN    {  5,   0,   0,   5}
NOBLE    { 10,   5,   0,  10}               CHIVALRY { 10,   5,   0,  10}
WARLORD  { -5,   5,  -5,  15}               CONQUEST {-10,   0,  -5,  10}
PARANOID {  0, -25,  -5,   0}               FAITH    {  5,   5,   0,   5}
REVELER  { 10,  10,  15,  -5}               COIN     {  0,   5,  20,  -5}
PURIST   {-10, -15,  -5,   0}               BLOOD    { -5, -10,   0,   5}
```

Worked examples: a Noble Chivalry judge with friction 1 and tension 20, asked for recognition, scores
60-8-12+10+10 = 60 (signs). A Tyrant Conquest judge with friction 2 scores 60-16-12-5-10 = 17 (refuses). A Reveler Coin
judge asked for a charter scores 50+15+20-8-6 = 71 (signs). Aid without a common foe scores about 32; with one, about
62.

Methods:
- `register()`:
  - `END_SERVER_TICK` to `tick`.
  - `ENTITY_LOAD` / `ENTITY_UNLOAD` (Couriers' stale-copy handling).
  - `ServerLivingEntityEvents.AFTER_DEATH` to `slain`.
  - `UseEntityCallback.EVENT`: without MCA, right-clicking a villager with `ENVOY` calls `hear` and returns SUCCESS.
  - `SERVER_STOPPED` clears the transient sets.
- `score(server, Faction judge, Faction proposer, Kind kind)`: the sum above. `Tempers.of(judge)` is null for a
  player-ruled judge (temper term 0). It uses `GreatWars.creed`, `GreatWars.friction`, `GreatWars.tension`,
  `Relations.between`, `WarData.truce`, `Wealth.worth` and `Purists.blood`. `reason(...)` returns the lang key of the
  biggest negative term (tension / creed / temper / standing / unrecognised / none).
- `odds(server, judge, proposer, kind)`: 2 likely, 1 maybe, 0 unlikely; 3 if the judge has a sovereign (they decide);
  -1 if the kind isn't open (in force with more than `RENEW_TICKS` left, needs recognition, at war).
- `daily(server, level)` (once per `Production.day`): for each ordered pair of `Wars.heads` within `RANGE`, where the
  proposer is NPC (`Tempers.of != null`), there is no war between them (`Wars.between == null`), no mission between
  them, and the caps aren't reached:
  - Pick the first open kind in the proposer's creed order (COIN: CHARTER, REC, PASS, AID; CONQUEST: AID, REC, PASS,
    CHARTER; CHIVALRY: REC, PASS, AID, CHARTER; FAITH: REC, PASS, CHARTER, AID; CROWN: REC, AID, PASS, CHARTER; BLOOD:
    REC, AID, PASS, CHARTER). Recognition comes first while not held.
  - Send if `score(proposer, target, kind) >= ACCEPT_AT - PROPOSE_SLACK` and the day's chance passes
    (`PLAYER_PROPOSE_CHANCE` if the target has a sovereign).
- `dispatch(server, from, to, kind, renew, @Nullable UUID sender)`: a new Mission at `from`'s hall (`Factions.keep(..)
  .hall()`, else the seat), goal = `to`'s hall/seat, `rides = distance > Couriers.RIDE_FROM`, unbodied (no entity yet).
- `embody(level, Mission, BlockPos)`: when a mission has no entity and no saved body and a player is within `APPEAR`,
  a new villager is made:
  - With MCA: `McaCompat.envoy(level, from, at)` (new, `McaBridge.envoy`, modelled on `McaBridge.driver`: a newcomer,
    adult, persistent, not enlisted). Without MCA: `EntityType.VILLAGER.create(level, EntitySpawnReason.MOB_SUMMONED)`
    **[26.3]**.
  - Gets `ENVOY` = mission id, `Introductions.TITLE` = `title.jaymoskingdoms.envoy` (realm name), the realm's banner in
    the off hand (drop chance 0; `BannerBlock.byColor(f.color())` item **[26.3]**), and `Couriers.mount` if it rides.
    The mission's `envoy` becomes its UUID.
- `tick`: Couriers' loop. In the world: walk the leg (navigation to the `Roads.ahead` waypoint; stuck hop); taken out
  when nobody is within `LEAVE`. Out of the world: `travel` at `RIDE_SPEED` or `WALK_SPEED` per tick along
  `Roads.ahead`; put back when a player is within `APPEAR` and the spot is ticking.
- `arrived(level, Mission)` at `goal` on leg TO:
  - Target has a sovereign: leg WAIT, `until = now + WAIT_TICKS`, the envoy keeps to the hall (`setHomeTo(hall, 6)`),
    and `Post.report(sov, message.jaymoskingdoms.envoy.waiting)`. If embodied: `Speech.say(...,
    speech.jaymoskingdoms.envoy.arrive.<0..2>, Speech.GOLD, 16)`.
  - Target is NPC: leg WAIT, `until = now + THINK_TICKS`.
- `decide(server, Mission)` at an NPC court once `until` passes:
  - Signs if `score + random(±ACCEPT_LUCK) >= ACCEPT_AT` and the kind is still open (`Treaties.sign`), else refuses.
  - `answer` and `reason` are set and the mission goes to leg BACK. Bubble: `speech.jaymoskingdoms.envoy.signed` or
    `.refused`.
  - If a player sent it: `Post.report(sender, message.jaymoskingdoms.envoy.sent_signed | sent_refused (+ reason))`.
- WAIT at a player court past `until`: bubble `speech.jaymoskingdoms.envoy.leave`, leg BACK,
  `Post.report(sov, message.jaymoskingdoms.envoy.unanswered)`. No penalty.
- BACK reaches home: removed; the entity is discarded once no player is within `HOME_GONE`.
- `hearable(ServerPlayer p, LivingEntity v)`: `v` has `ENVOY`, its mission is on leg WAIT, and
  `Crowns.rules(server, to, p)`.
- `hear(p, v)`: sends `DiplomacyNet.EnvoyOffer` (realm name, envoy name, kind, days, the sender's creed and temper
  keys, relation key, tension) and remembers p→mission in `HEARING` (transient).
- `answered(p, EnvoyAnswer)`: checks again that the mission is in `HEARING`, still WAIT, that p is the sovereign and
  within `HEAR_RANGE` of the envoy. Then Sign → `Treaties.sign`, Refuse → refused (`answer = 0`), Not yet → nothing.
  A signed or refused mission goes to leg BACK with the matching bubble. If a player sent it, they get a
  `Post.report`.
- `slain(entity, source)`: the mission is dropped and `message.jaymoskingdoms.envoy.slain` goes on the board.
  - A player killer: `Reputation.change(killer, from, REP_KILL_ENVOY)` and, if it died on a realm's land
    (`Banners.owner`), `REP_KILL_GUEST` with that realm.
  - If it died on the target realm's land: tension between from and to `+KILL_TENSION`.
- `outgoing(server, f)` / `between(server, a, b)` for the desk and the caps.

### 2.3 `RulersDesk.java` (new, `final class RulersDesk`)
The sovereign's desk. It is server side: it builds the `Desk` payload and handles `DeskAct`.

| Constant | Value | Meaning |
|---|---|---|
| `DESK_TICKS` | `6000L` | A desk action is accepted this long after it was opened. |
| `DESK_RANGE` | `16.0` | ...and only this near where it was opened. |
| `ROWS_MOST` | `24` | Realms listed (nearest first). |

- `open(ServerPlayer p, Faction f)`: returns false if the client can't receive `Desk` (`ServerPlayNetworking.canSend`),
  so the old chat audience stays as a fallback. Otherwise it records `OPEN` (player → realm, position, time) and
  sends `Desk(...)`:
  - For a fiefdom only `status = gui.jaymoskingdoms.desk.vassal` (its overlord treats for it).
  - Otherwise rows for every other head in `Envoys.RANGE`, nearest first.
- `act(p, DeskAct)`: validates `OPEN`, `DESK_TICKS`, `DESK_RANGE`, `Crowns.rules(server, f, p)`, `f.overlord() < 0`,
  and that the target is a head in range. Then:
  - **SEND**: the kind is open (`Envoys.odds != -1`), `Envoys.between(f, t) == null`, no outgoing envoy. It pays
    `ENVOY_COST` from the treasury; whatever the treasury lacks comes out of the purse
    (`Money.charge(p, f, Money.worth(short))`), else `status = gui.jaymoskingdoms.desk.poor`. Then
    `Envoys.dispatch(server, f, t, kind, renewIfInForce, p.getUUID())`.
  - **RENOUNCE**: `Treaties.broken(server, f, t, kind)`. Renouncing recognition breaks all of them (`kind = null`).
  - Either way it re-sends `Desk` with the status key.

### 2.4 `DiplomacyNet.java` (new, `public final class DiplomacyNet`)
The four payloads, registered in `register()` with `PayloadTypeRegistry` and `ServerPlayNetworking.registerGlobalReceiver`.
They are hand-written `CustomPacketPayload.codec(writer, reader)` like `Lineage.Ask`.

| Record | Direction | Id | Fields |
|---|---|---|---|
| `EnvoyOffer` | server→client | `jaymoskingdoms:envoy_offer` | `int entityId, int mission, String realm, String envoy, String kind, int days, boolean renew, String creed, String temper, String relation, int tension, boolean playerRealm` |
| `EnvoyAnswer` | client→server | `jaymoskingdoms:envoy_answer` | `int mission, int choice` (0 not yet, 1 sign, 2 refuse) |
| `Desk` | server→client | `jaymoskingdoms:ruler_desk` | `String realm, long treasury, long cost, String status, List<Row> rows`; `Row(int id, String name, String relation, int tension, String creed, boolean playerRealm, boolean atWar, boolean envoyOut, List<String> kinds, List<Integer> daysLeft, List<Integer> odds /* 4: -1,0,1,2,3 */)` |
| `DeskAct` | client→server | `jaymoskingdoms:ruler_desk_act` | `int action /* 0 SEND, 1 RENOUNCE */, int realm, int kind` |

### 2.5 `Elections.java` (new, `final class Elections`)
Succession customs, the notables' scores, regencies, and the ruler-info lines. It is used only where
`Rulers.inPlace(f)` (MCA, a realm). Without MCA, folk rulers are spawned as before and nothing here applies.

```java
enum Custom { HEREDITARY, ELECTIVE, MOOT }                       // key(): "heir" | "elective" | "moot"
record Choice(String way, @Nullable UUID villager, @Nullable UUID player, @Nullable UUID ward, @Nullable UUID house)
    // way: "heir" | "elected" | "acclaimed" | "regent" | "of_age" | "" (leave it to McaBridge.heir as now)
record Seat(String way, Optional<UUID> ward, String wardName, Optional<UUID> house, long since)
record Book(Map<String, Seat> seats, long lastDay)
static final AttachmentType<Book> SEATS =                         // Overworld, persistent
    AttachmentRegistry.create(JaymosKingdoms.id("succession_seats"), b -> b.persistent(Book.CODEC));
```

| Constant | Value | Meaning |
|---|---|---|
| `MOOT_BLOODS` | `EnumSet.of(WARRIORBORN, FROSTBORN)` | Bloods whose Old Blood / Conquest realms hold a moot. |
| `PROFESSION_W` | `8` | Per MCA villager profession level 1-5 (wealth). ×2 in an ELECTIVE realm. |
| `GUARD_W` | `10` | A guard (standing in the realm's service). ×2 at a MOOT. |
| `KIN_W`, `PARTNER_W` | `12`, `8` | Kin / spouse of the late ruler (`McaCompat.kinOf`). Halved at a MOOT. |
| `GIFTED_W` | `10` | `PowersCompat.gift(v) != null`. |
| `MOOD_W` | `3` | × `McaCompat.temper(v)` (-2..1, personality). |
| `LUCK` | `10` | Random 0..LUCK added. |
| `ELECT_PLAYERS` | `true` | Sworn players stand too. |
| `PLAYER_LEAST` | `Rank.KNIGHT.standing` (300) | Least standing for a player to stand. |
| `PLAYER_STANDING_DIV` | `25` | Player score = standing/25 + clamp(rep/100, 0, 10) + luck. |
| `REGENCY_TEEN_DAYS` | `30` | After this many days a teen ward is crowned even if not yet grown. |
| `CHECK_TICKS` | `200` | How often regencies are looked at. |

Methods:
- `register()`: `END_SERVER_TICK` (every `CHECK_TICKS`: regencies), `AFTER_DEATH` (a ward died).
- `custom(server, Faction f)`:
  - ELECTIVE if `GreatWars.creed(server, f) == COIN`.
  - MOOT if the creed is BLOOD or CONQUEST and the ruling blood is in `MOOT_BLOODS`. The ruling blood is
    `Purists.blood(Wars.top(server, f))`, else the last reign's blood (`Rulers.lastBlood(server, f)`, new).
  - Else HEREDITARY.
- `choose(ServerLevel, Faction f)` (before crowning, from `KingdomEvents.tick`):
  1. A sovereign wears the crown (`Crowns.sovereign != null`): `Choice("")`. The steward is replaced as now.
  2. `house` = the stored `Seat.house` while a regent sits (so the old dynasty stays first), else `Rulers.house`.
  3. A ward already recorded who is loaded and grown (`McaCompat.adult`), or a teen once `REGENCY_TEEN_DAYS` have
     passed: `of_age`, villager = ward.
  4. `cands = McaCompat.notables(level, f, house)` (grown only).
  5. HEREDITARY:
     - Grown kin or partner present: `""` (`McaBridge.heir` picks them as now).
     - Else, with young kin of the house present (`McaCompat.youngKin`) or a recorded ward still alive: `regent`.
       The villager is the best-scored non-kin notable, the ward is the eldest young kin, and the house is kept.
     - Else `""`.
  6. ELECTIVE / MOOT: the best of the scored villagers and the eligible sworn players: `elected` or `acclaimed`.
     Eligible players are members with `standing >= PLAYER_LEAST`, `Crowns.crownOf == null`, not the old ruler's
     killer.
- `score(Villager v, Custom c, @Nullable UUID house, RandomSource)` and `score(ServerPlayer p, Faction f, ...)`.
- `crowned(ServerLevel, @Nullable Faction crowned, Choice c)` returns true if it announced:
  - Records the `Seat`.
  - A player won: `Crowns.crown(server, crowned, player)`; `Post.report(player, message.jaymoskingdoms.election.you)`;
    board `message.jaymoskingdoms.election.player`.
  - `elected`/`acclaimed`: `message.jaymoskingdoms.election.elected|acclaimed`.
  - `regent`: `message.jaymoskingdoms.regency.begun`.
  - `of_age`: `message.jaymoskingdoms.regency.of_age`.
  - All of these go on boards only. Not GREAT.
- Regency tick: a recorded ward who is loaded and grown (or a teen after `REGENCY_TEEN_DAYS`), with the regent loaded
  (`Rulers.of`):
  1. `Rulers.stepDown(level, f, regent)` (new: `McaCompat.discharge` + `Rulers.unfit`).
  2. `KingdomEvents.crown(level, f, ward)`.
  3. Seat way `of_age`.
  4. Announce.
  It waits while either one is unloaded.
- Ward's `AFTER_DEATH`: the seat's ward is cleared, way `heir` (the regent rules in their own right), and board
  `message.jaymoskingdoms.regency.ward_died`.
- `deathKey(server, f, key)`: `key + ".elect"` when `custom != HEREDITARY` (for the old-age / successor news).
- `lines(server, f)`: the Standing succession lines (see 5.4).

### 2.6 Client: `EnvoyScreen.java` (new, `final class EnvoyScreen extends Screen`)
The `LineageScreen` pattern: `W = 300`, `PAD = 10`, `BUTTON_H = 16`, `GAP = 4`, `ACCENT = 0xFF8FB0D9` (an envoy's
blue). `register()`: `ClientPlayNetworking.registerGlobalReceiver(DiplomacyNet.EnvoyOffer.TYPE, (p, c) ->
c.client().gui.setScreen(new EnvoyScreen(p)))`. It replaces MCA's interact screen if that is up.
- Title `gui.jaymoskingdoms.envoy.title` (realm).
- Wrapped lines:
  - `gui.jaymoskingdoms.envoy.offer`, or `.renew` (envoy, realm, the kind's name, days).
  - The kind's `treaty.jaymoskingdoms.<kind>.about`.
  - `gui.jaymoskingdoms.envoy.realm` or `.realm_player` (realm, creed named, temper `temper.jaymoskingdoms.<key>`).
  - `gui.jaymoskingdoms.envoy.relation` (relation key, tension).
- Buttons: Sign, Refuse (half width each), and Not yet (full width) below. Esc counts as Not yet. Each sends
  `EnvoyAnswer`. Drawn with `Ui.panel`, `Ui.line` and `Ui.rule`. No texture.

### 2.7 Client: `RulersDeskScreen.java` (new, `final class RulersDeskScreen extends Screen`)
`W = 360`, `H = 220`, `PAD = 8`, `LIST_W = 150`, `ROW_H = 12`, `ROWS = 12` visible (mouse-wheel scroll),
`BUTTON_H = 16`, `ACCENT = 0xFFB89A5E` (bronze). Registered like `EnvoyScreen` for `DiplomacyNet.Desk`. On a re-send it
keeps the picked row and the scroll (the `StableScreen` pattern, `JaymosKingdomsClient.java:215-217`).
- Header: `gui.jaymoskingdoms.desk.title` (realm), `gui.jaymoskingdoms.desk.treasury` (treasury).
- Left list: `gui.jaymoskingdoms.desk.row` (name, relation word, tension), using `Ui.ROW`/`ROW_ALT`/`ROW_HOVER`/
  `ROW_PICKED`. Empty list: `gui.jaymoskingdoms.desk.none`.
- Right panel for the picked realm:
  - Creed line (`creed.jaymoskingdoms.<key>`).
  - The treaties in force: `gui.jaymoskingdoms.desk.in_force` (kind, days left), each with a small Renounce button.
    The first click relabels it `gui.jaymoskingdoms.desk.renounce_sure`; a second click within 3 s sends RENOUNCE.
  - Four kind toggles (one picked), greyed when the kind is closed. Their tooltips are `.about` and
    `gui.jaymoskingdoms.desk.needs_recognition` / `.at_war` when it applies.
  - The odds line for the picked kind (`gui.jaymoskingdoms.desk.odds.likely|maybe|unlikely|player`).
  - `gui.jaymoskingdoms.desk.send` (cost) is disabled while `envoyOut` (`gui.jaymoskingdoms.desk.envoy_out`) or the kind
    is closed.
  - The status line from the payload. Close button.
  - Nothing is decided client side. The server checks everything again.

### 2.8 Lang and hint
All keys go in the single `kingdoms/src/main/resources/assets/jaymoskingdoms/lang/en_us.json`. The full list is in
section 6.

---

## 3. Changed files (exact hooks)

| File | Where | Change |
|---|---|---|
| `kingdoms/gradle.properties` | `version=0.7.512` | → `version=0.7.519` |
| `JaymosKingdoms.java` | after `Wars.register();` (:360) | `Treaties.register(); Envoys.register(); DiplomacyNet.register(); Elections.register();` |
| `GreatWars.java` | `stir` :345-396 | After the base delta: if `Treaties.has(RECOGNITION)`, `delta -= RECOGNITION_EASE`; PASSAGE `-= PASSAGE_EASE`; CHARTER: skip `TRADE_GAIN` and `-= CHARTER_EASE`; AID: `delta = Math.min(delta, -AID_EASE)`. The NEUTRAL→RIVAL step (:381) is skipped while recognised. Before `Wars.declare` (:390): `if (!Treaties.mayBreakForWar(server, from, on, level)) return;`. |
| `GreatWars.java` | `lean` :514 | `return 0.0` if `Treaties.any(h, foe)`; `lean += Treaties.AID_LEAN` if `Treaties.has(h, friend, AID)`. |
| `GreatWars.java` | new helper after `pledgedTo` :409 | `static void pledge(MinecraftServer server, int faction, int war, int principal)`: `edit(server, l -> withPledges(l, with(l.pledges(), Integer.toString(faction), new Pledge(war, principal))))`. `forgetEnded` already drops it when the war ends; `Wars.onSide` :162 already counts it. |
| `Wars.java` | `declare` :306, after the war is stored (:334) | `Treaties.warDeclared(server, a.id(), d.id());` (a player declaring through the existing war command breaks faith too). |
| `Wars.java` | `begin` :353, after `GreatWars.begun` | `Treaties.warBegun(server, war);` |
| `Wars.java` | `stirRivalries` :521 | Skip the pair if `Treaties.has(a, b, RECOGNITION)`. |
| `Wars.java` | `declareOnRival` :562 | Skip `other` if `Treaties.any(king, other)`. |
| `Approaches.java` | `pick` guard branch :634-656 | `boolean passage = !sworn && Treaties.passage(server, f, player);`. `w[HALT]` only if `!passage`. SEARCH/TOLL weights only if `!passage` (the condition at :651 gains `&& !passage`). Next to `Blessings.haltGap`/`searchEase` (:640-641), which stay as they are. |
| `Approaches.java` | toll price :869 | `a.price = ... TOLL ? (Treaties.charterPlayer(server, f, player) ? Math.max(1, TOLLS[a.variant] * CHARTER_TOLL_PERCENT / 100) : TOLLS[a.variant]) ...` |
| `Retinue.java` | `passage` :178 | `\|\| Treaties.passage(f's server, f, p)`. The ruler's guards don't dog or shove a partner's people (used at :238, :277, :309, :515). |
| `mixin/VillagerPriceMixin.java` | `jaymoskingdoms$purist` :17-27 | Before `Currencies.localize`: `com.jaymoskingdoms.Treaties.prices((Villager)(Object)this, player);` (`Treaties.prices` public). |
| `TradeRoutes.java` | `partner` :252-271 | Reach test: `Raids.horizontal(...) > (reach*(chartered ? CHARTER_REACH : 1))^2`. Sort key divides by `CHARTER_PULL` when chartered. A pair at war is still skipped. |
| `TradeRoutes.java` | `start` :378-418 | When `Treaties.chartered(f, to)`: the profile gets `carts + CHARTER_CARTS`, and `Caravans.launch(..., now + sort.every * CHARTER_EVERY_PERCENT / 100)`. |
| `TradeRoutes.java` | `arrive` :470-510 | `most` uses `RETURN_SHARE + (chartered ? CHARTER_RETURN : 0)`. |
| `KingdomEvents.java` | `tick` :124-145 | `Elections.Choice choice = Elections.choose(overworld, faction);` then `Faction crowned = crown(overworld, faction.withSuccessionAt(-1L), choice.villager());`. Sovereign path unchanged. `if (Elections.crowned(overworld, crowned, choice)) continue;` before the usual `succession` / `succession.villager` announcements. |
| `KingdomEvents.java` | `crown` :367 | New overload `crown(ServerLevel, Faction, @Nullable UUID chosen)` passes `chosen` to `Rulers.crown(level, faction, role, chosen)`; the old signature delegates with null. |
| `KingdomEvents.java` | `rulerDied` :604, :655 | Wrap the keys: `Elections.deathKey(server, faction, lord ? "...old_age.lord" : "...old_age.king")` and `Elections.deathKey(server, faction, "...fate.successor")` (inside `Rulers.her`). |
| `KingdomEvents.java` | `audience` :678 | First thing after the barbarian check: `if (Crowns.rules(server, faction, player.getUUID()) && RulersDesk.open(player, faction)) return;`. Covers MCA rulers (`Rulers.audience` → here) and folk rulers. |
| `Rulers.java` | `crown` :337 | New overload `crown(level, faction, role, @Nullable UUID chosen)` → `McaCompat.enlistRuler(level, faction, role, house(...), chosen)`; the old one delegates with null. New `stepDown(level, f, mob)` (discharge, `unfit`, leader cleared) and `lastBlood(server, f)` (the stored Reign's blood whoever rules). |
| `McaCompat.java` | :401 and new | `enlistRuler(..., @Nullable UUID chosen)` overload. New: `notables(level, f, house)`, `youngKin(level, f, house)`, `adult(Villager)`, `professionLevel(Villager)`, `envoy(level, f, at)`, `discharge(PathfinderMob)`. All guarded by `LOADED`. |
| `McaBridge.java` | `enlistRuler` :510, `heir` :536 | `chosen` is used when loaded, alive and grown; else `heir`. The filter moves into `candidates(level, f, house)`, which uses `v.getAgeState() == AgeState.ADULT` in place of `!v.isBaby()` (so a teen is never crowned outright: they get a regency). New `envoy` (like `driver`), `youngKin`, `adult`, `professionLevel` (`v.getVillagerData().level()` **[26.3]**), `discharge`. |
| `Standing.java` | `ruler` :73-101 | After the reign line: `lines.addAll(Elections.lines(...))` with hovers, then the `Treaties.lines(...)` line. |
| `Post.java` | `GREAT` :70-82 | Add `"message.jaymoskingdoms.treaty.great."`, `"message.jaymoskingdoms.treaty.great_broken"`, `"message.jaymoskingdoms.treaty.great_war"`, `"message.jaymoskingdoms.treaty.great_aid"`, `"message.jaymoskingdoms.old_age.king.elect"`. |
| `InteractActions.java` | `ALL` :37, `offers` :78, `act` :93 | New `ENVOY = "envoy"` appended to `ALL` (the button is added by `McaScreensMixin`, shown by `McaActionButtonsMixin`). `offers`: `Envoys.hearable(player, v)`. `act`: `Envoys.hear(player, v); return null;`. |
| `Couriers.java` | `mount` :423 | `private` → package-private (reused by Envoys). |
| `client/JaymosKingdomsClient.java` | near `LineageScreen.register()` :111 | `EnvoyScreen.register(); RulersDeskScreen.register();` |
| `client/StoryHoldScreen.java` | :35 | `HINTS = 88` → `HINTS = 93` (master is at 88; 89-92 are reserved for other open PRs). |
| `kingdoms/MODLOG.md` | end | Entry in section 8. |
| `docs/Otectus-Plan.md` | row 10 status | `built in Kingdoms 0.7.519 (docs/Diplomacy-Plan.md)` |

Not changed: `Faction` (its codec stays as it is; the treaty, mission and seat data are separate attachments),
`Relation` (no TREATY state; treaties never write ALLIED), `Treasuries` (the trade toll still credits the land's realm
in full), and `Money`/`Currencies` (foreign-coin cut unchanged).

---

## 4. Hook map: each effect and the existing method it runs through

There are no patron blessings in this patch. The existing `Blessings.haltGap` / `Blessings.searchEase` calls in
`Approaches.pick` stay; safe passage is checked right beside them and, when it holds, the halt/search/toll weights are
zero whatever the blessings say.

| Effect | Existing method hooked |
|---|---|
| Recognition eases tension, no rivalry | `GreatWars.stir` (:345), `Wars.stirRivalries` (:521) |
| Any treaty eases tension a little | `GreatWars.stir` |
| Safe passage: no halt/search/toll | `Approaches.pick` (:611, guard branch) |
| Safe passage: the ruler's retinue lets them by | `Retinue.passage` (:178) |
| Safe passage: war parties may pass | Already true: no code stops a third realm's war party crossing land (`WarParties.send`/`start` :199/:228 march seat to seat and only fight the enemy side), so there is nothing to change. |
| Trade charter: better prices | `VillagerPriceMixin` → `Treaties.prices` (the `Faiths.prices` pattern) |
| Trade charter: half gate toll | `Approaches` toll price (:869) |
| Trade charter: more caravans, more carts, fuller returns, preferred partner | `TradeRoutes.partner` (:252), `TradeRoutes.start` (:378), `TradeRoutes.arrive` (:470) |
| Aid: drawn into wars like a pledge | `Wars.begin` (:353) → `GreatWars.pledge` → `Wars.onSide` (:162) / `Wars.members` (:179) |
| Aid: soldiers | `WarParties.send` (:199) already raises parties from every member of a side, pledged realms included |
| Aid: coin | `Factions.update(...withTreasury)` |
| Aid: coalition lean | `GreatWars.lean` (:514) |
| Breaking: tension | `GreatWars.setTension` (:307) |
| Breaking: reputation | `Reputation.change` (:61) |
| Breaking by war | `Wars.declare` (:306) |
| NPC kings keep faith | `GreatWars.stir` (before declaring), `Wars.declareOnRival` (:562) |
| Big treaties to Tidings only | `Post.GREAT` (:70) via `KingdomEvents.announce` (:738) → `Post.news` (:180) |
| Envoy movement | `Couriers` pattern, `Couriers.mount`, `Roads.ahead` (:995) |
| Envoy letters | `Post.report` (:338) |
| Envoy talk button | `InteractActions` (`McaScreensMixin`/`McaActionButtonsMixin` pick up `ALL`) |
| Ruler's desk | `KingdomEvents.audience` (:678) |
| Elections/regency | `KingdomEvents.tick` (:125), `KingdomEvents.crown` (:367), `Rulers.crown` (:337), `McaBridge.enlistRuler`/`heir` (:510/:536) |
| Ruler info | `Standing.ruler` (:73) |
| Player elected | `Crowns.crown` (:135) |

---

## 5. Details

### 5.1 Who treats with whom
- Only `Wars.heads(server)` (standing kingdoms with no overlord and a ruler) sign treaties, NPC or player (a player
  realm is a kingdom with a sovereign). Fiefdoms follow their overlord: every effect check goes through `Wars.top`. A
  realm made a vassal or ruined has its treaties lapse at the next daily round.
- There are no treaties or envoys between sides at war (`Wars.between != null`). Peace stays with the existing war
  offers (`Wars.offer`); this patch doesn't touch it.
- Barbarian camps, villages and greenskin tribes are out (tribes keep `TribePacts`/`Tribes` truces).

### 5.2 Envoy life
`dispatch` → (unbodied record, or embodied when seen) TO → WAIT (NPC: `THINK_TICKS`; player court: up to `WAIT_TICKS`)
→ decide or hear → BACK → home, gone. A killed envoy ends the mission. Saved: `MISSIONS` (with body while out of the
world). An envoy left in a chunk is handled like a courier (a stale copy is discarded, and the record goes on).

### 5.3 Elections and regency, worked through
- A Coin kingdom's king dies. The board says "...A successor will be chosen by tomorrow". Next day `choose` scores the
  grown townsfolk: a master armorer (level 5: 5×8×2 = 80) beats a guard (10). The board says "The guilds of Aldmere
  have chosen Bren to rule." Bren's info shows "Succession: the guilds elect" and "Elected by the guilds".
- A Noble Crown kingdom's queen is slain, and her only kin is a child daughter. The best non-kin notable is crowned
  as regent: "Regent for Ilse" (hover: rules until she comes of age). When Ilse is grown (MCA ages her), the regent
  steps down (regalia and health go), Ilse is crowned, and the board says "Ilse has come of age and takes the throne
  of...". If Ilse dies first, the regent rules in their own right.
- A sworn player Lord (standing 700 → 28, rep 400 → 4, + luck) in a Coin realm can beat a journeyman but not a master
  smith. If they win, they wear the crown (`Crowns.crown`) with the old villager as their steward, and get a letter.

### 5.4 Ruler info lines (`Standing.ruler`)
- `standing.jaymoskingdoms.succession.<heir|elective|moot>`, hover `...<key>.hover`.
- If they came by election, acclamation or of age: `standing.jaymoskingdoms.came_by.<elected|acclaimed|of_age>`,
  no hover.
- If regent: `standing.jaymoskingdoms.regent` (ward name), hover `standing.jaymoskingdoms.regent.hover` (ward name).
- `standing.jaymoskingdoms.treaties` (count) or `standing.jaymoskingdoms.treaties.none`. The hover lists one
  `standing.jaymoskingdoms.treaties.line` per treaty (kind, realm, days), joined with `\n`.

### 5.5 Saves and ids
- New attachments (all `persistent`, all on the Overworld except the envoy mark): `jaymoskingdoms:treaties`,
  `jaymoskingdoms:envoy_missions`, `jaymoskingdoms:succession_seats`, and `jaymoskingdoms:envoy` (an Integer on the
  envoy entity).
- New payloads: `jaymoskingdoms:envoy_offer`, `envoy_answer`, `ruler_desk`, `ruler_desk_act`.
- New Interact action: `envoy`.
- No items, blocks, recipes, data components, entities, textures, mixins, sounds, or dialogue JSON.

---

## 6. Lang keys (`en_us.json`; add before the closing brace)

```json
"treaty.jaymoskingdoms.recognition": "Recognition",
"treaty.jaymoskingdoms.passage": "Safe Passage",
"treaty.jaymoskingdoms.charter": "Trade Charter",
"treaty.jaymoskingdoms.aid": "Treaty of Aid",
"treaty.jaymoskingdoms.recognition.the": "their recognition",
"treaty.jaymoskingdoms.passage.the": "the safe passage",
"treaty.jaymoskingdoms.charter.the": "the trade charter",
"treaty.jaymoskingdoms.aid.the": "the treaty of aid",
"treaty.jaymoskingdoms.recognition.about": "Each crown owns the other's right to rule. Tension between them eases every day and they won't fall into rivalry. Every other treaty needs it first.",
"treaty.jaymoskingdoms.passage.about": "Each realm's guards let the other's people by: no halting, no search, no toll at the gate.",
"treaty.jaymoskingdoms.charter.about": "Their caravans run more often and come home fuller. Each realm's people get better prices from the other's traders and pay half the gate toll.",
"treaty.jaymoskingdoms.aid.about": "When either goes to war, the other joins its side and sends soldiers, or coin if it can't march.",

"message.jaymoskingdoms.treaty.signed.recognition": "%s and %s have recognised each other's crowns.",
"message.jaymoskingdoms.treaty.signed.passage": "%s and %s have granted each other's people safe passage.",
"message.jaymoskingdoms.treaty.signed.charter": "%s and %s have signed a trade charter.",
"message.jaymoskingdoms.treaty.signed.aid": "%s and %s have sworn a treaty of aid: if either goes to war, the other goes with it.",
"message.jaymoskingdoms.treaty.great.recognition": "%s and %s have recognised each other's crowns.",
"message.jaymoskingdoms.treaty.great.passage": "%s and %s have granted each other's people safe passage.",
"message.jaymoskingdoms.treaty.great.charter": "%s and %s have signed a great trade charter.",
"message.jaymoskingdoms.treaty.great.aid": "%s and %s have sworn a treaty of aid: if either goes to war, the other goes with it.",
"message.jaymoskingdoms.treaty.renewed": "%s and %s renew %s.",
"message.jaymoskingdoms.treaty.lapsed": "%s and %s let %s lapse.",
"message.jaymoskingdoms.treaty.broken": "%s has broken %s with %s.",
"message.jaymoskingdoms.treaty.great_broken": "%1$s has broken faith with %3$s and torn up %2$s.",
"message.jaymoskingdoms.treaty.great_war": "%s marches on %s and tears up every treaty between them.",
"message.jaymoskingdoms.treaty.great_aid": "Bound by their treaty of aid, %s goes to war at %s's side against %s.",
"message.jaymoskingdoms.treaty.aid_coin": "%s cannot march with %s, but sends %s from its treasury for the war against %s.",
"message.jaymoskingdoms.treaty.aid_torn": "%s is bound by treaties to both %s and %s, and stands aside.",
"message.jaymoskingdoms.treaty.aid_called": "%s is at war with %s. By your treaty of aid, %s stands at its side.",

"message.jaymoskingdoms.envoy.waiting": "An envoy of %s waits in your hall at %s with an offer: %s. They will wait %s days.",
"message.jaymoskingdoms.envoy.unanswered": "The envoy of %s has gone home unanswered.",
"message.jaymoskingdoms.envoy.sent_signed": "Your envoy writes from %s: they have signed %s.",
"message.jaymoskingdoms.envoy.sent_refused": "Your envoy writes from %s: they will not sign %s. %s",
"message.jaymoskingdoms.envoy.reason.tension": "Too much bad blood lies between your realms.",
"message.jaymoskingdoms.envoy.reason.creed": "Their creed sits ill with yours.",
"message.jaymoskingdoms.envoy.reason.temper": "Their ruler trusts no one's word.",
"message.jaymoskingdoms.envoy.reason.standing": "They think little of your name.",
"message.jaymoskingdoms.envoy.reason.unrecognised": "They do not yet recognise your crown.",
"message.jaymoskingdoms.envoy.reason.none": "They gave no reason.",
"message.jaymoskingdoms.envoy.slain": "An envoy of %s was slain on the road.",
"speech.jaymoskingdoms.envoy.arrive.0": "I come from %s with words for your crown.",
"speech.jaymoskingdoms.envoy.arrive.1": "Word from %s, for whoever wears the crown here.",
"speech.jaymoskingdoms.envoy.arrive.2": "I bear an offer from %s. Where is your sovereign?",
"speech.jaymoskingdoms.envoy.signed": "My lord will be glad of it.",
"speech.jaymoskingdoms.envoy.refused": "So be it. I will tell %s.",
"speech.jaymoskingdoms.envoy.leave": "I can wait no longer. I go home to %s.",
"title.jaymoskingdoms.envoy": "Envoy of %s",
"gui.button.act.envoy": "Hear the Envoy",

"gui.jaymoskingdoms.envoy.title": "An Envoy of %s",
"gui.jaymoskingdoms.envoy.offer": "%s, envoy of %s, offers %s for %s days.",
"gui.jaymoskingdoms.envoy.renew": "%s, envoy of %s, asks to renew %s for %s days.",
"gui.jaymoskingdoms.envoy.realm": "%s holds to %s; its ruler is %s.",
"gui.jaymoskingdoms.envoy.realm_player": "%s holds to %s; a player wears its crown.",
"gui.jaymoskingdoms.envoy.relation": "Between you: %s, tension %s of 100.",
"gui.jaymoskingdoms.envoy.sign": "Sign",
"gui.jaymoskingdoms.envoy.refuse": "Refuse",
"gui.jaymoskingdoms.envoy.later": "Not yet",

"gui.jaymoskingdoms.desk.title": "Matters of State: %s",
"gui.jaymoskingdoms.desk.treasury": "Treasury: %s",
"gui.jaymoskingdoms.desk.none": "No realm lies near enough to treat with.",
"gui.jaymoskingdoms.desk.row": "%s (%s, tension %s)",
"gui.jaymoskingdoms.desk.creed": "Holds to %s",
"gui.jaymoskingdoms.desk.in_force": "%s: %s days left",
"gui.jaymoskingdoms.desk.envoy_out": "Your envoy is on the road to them.",
"gui.jaymoskingdoms.desk.send": "Send an Envoy (%s)",
"gui.jaymoskingdoms.desk.renounce": "Renounce",
"gui.jaymoskingdoms.desk.renounce_sure": "Renounce? Click again",
"gui.jaymoskingdoms.desk.odds.likely": "They would likely sign.",
"gui.jaymoskingdoms.desk.odds.maybe": "It could go either way.",
"gui.jaymoskingdoms.desk.odds.unlikely": "They would likely refuse.",
"gui.jaymoskingdoms.desk.odds.player": "Its sovereign will decide.",
"gui.jaymoskingdoms.desk.needs_recognition": "Needs recognition first.",
"gui.jaymoskingdoms.desk.at_war": "You are at war with them.",
"gui.jaymoskingdoms.desk.poor": "Neither the treasury nor your purse can pay for an envoy.",
"gui.jaymoskingdoms.desk.sent": "Your envoy sets out.",
"gui.jaymoskingdoms.desk.renounced": "The treaty is torn up.",
"gui.jaymoskingdoms.desk.vassal": "%s answers to its overlord, who treats for it.",
"gui.jaymoskingdoms.desk.close": "Close",

"message.jaymoskingdoms.election.elected": "The guilds of %2$s have chosen %1$s to rule.",
"message.jaymoskingdoms.election.acclaimed": "At the moot of %2$s, %1$s is acclaimed ruler.",
"message.jaymoskingdoms.election.player": "The notables of %2$s have chosen %1$s to wear its crown.",
"message.jaymoskingdoms.election.you": "The notables of %s have chosen you to wear its crown. %s, its steward, rules in your name while you're away.",
"message.jaymoskingdoms.regency.begun": "%1$s of %2$s is too young to rule; %3$s will govern as regent until they come of age.",
"message.jaymoskingdoms.regency.of_age": "%1$s has come of age and takes the throne of %2$s from the regent %3$s.",
"message.jaymoskingdoms.regency.ward_died": "%1$s, the young heir of %2$s, is dead; the regent %3$s now rules in their own right.",
"message.jaymoskingdoms.fate.successor.elect": "%s mourns its king. A successor will be chosen by tomorrow.",
"message.jaymoskingdoms.fate.successor.elect.her": "%s mourns its queen. A successor will be chosen by tomorrow.",
"message.jaymoskingdoms.old_age.king.elect": "The King of %s has died peacefully in his sleep, at a great age. A successor will be chosen by tomorrow.",
"message.jaymoskingdoms.old_age.king.elect.her": "The Queen of %s has died peacefully in her sleep, at a great age. A successor will be chosen by tomorrow.",
"message.jaymoskingdoms.old_age.lord.elect": "The Lord of %s has died peacefully in his sleep, at a great age. Another will be chosen for his seat by tomorrow.",
"message.jaymoskingdoms.old_age.lord.elect.her": "The Lady of %s has died peacefully in her sleep, at a great age. Another will be chosen for her seat by tomorrow.",

"standing.jaymoskingdoms.succession.heir": "Succession: by blood",
"standing.jaymoskingdoms.succession.elective": "Succession: the guilds elect",
"standing.jaymoskingdoms.succession.moot": "Succession: the moot acclaims",
"standing.jaymoskingdoms.succession.heir.hover": "Their family inherits; a child heir rules through a regent until of age.",
"standing.jaymoskingdoms.succession.elective.hover": "The richest and most respected of the realm choose the next ruler.",
"standing.jaymoskingdoms.succession.moot.hover": "The realm's fighters and elders acclaim the next ruler at a moot.",
"standing.jaymoskingdoms.came_by.elected": "Elected by the guilds",
"standing.jaymoskingdoms.came_by.acclaimed": "Acclaimed by the moot",
"standing.jaymoskingdoms.came_by.of_age": "Came of age to the throne",
"standing.jaymoskingdoms.regent": "Regent for %s",
"standing.jaymoskingdoms.regent.hover": "Rules until %s comes of age, then steps down.",
"standing.jaymoskingdoms.treaties": "Treaties: %s",
"standing.jaymoskingdoms.treaties.none": "No treaties",
"standing.jaymoskingdoms.treaties.line": "%s with %s (%s days)",

"gui.jaymoskingdoms.story.hint.93": "Realms send envoys to sign treaties: recognition, safe passage, trade charters and aid. Wear a crown and seek an audience with your steward to send your own."
```

(Format arguments stay positional where the order changes, as above. `Rulers.her` picks up the `.her` keys
automatically from the lang file.)

---

## 7. Textures, recipes, data
None. Both screens are drawn with `Ui.panel` / `Ui.rule` / `Ui.line` like `LineageScreen`. The envoy is a villager
with a vanilla banner and a courier's horse. There are no new items or recipes.

---

## 8. MODLOG (`kingdoms/MODLOG.md`, appended)

```markdown
## 0.7.519: diplomacy and elections
Otectus pick 10 (Andy chose MCA: Kingdoms' idea; our own version, none of its code or art). Plan: `docs/Diplomacy-Plan.md`.
- **Treaties:** free realms sign four kinds, each for a while and renewable (`Treaties`):
  - **Recognition** (30 days): tension eases 3 a day, and they don't fall into rivalry. Every other kind needs it.
  - **Safe passage** (10): the partner's people aren't halted, searched or tolled by the guards, and the ruler's
    retinue lets them by.
  - **Trade charter** (15): partner caravans go more often (60% of the wait), take an extra cart and come home 20
    points fuller, and are preferred. The partner's people get 10% off at traders and pay half the gate toll.
  - **Aid** (20): when either goes to war, the other is pledged to its side like a great war's ally (its soldiers join
    the war parties, it shares spoils and reparations). Already at war elsewhere, it sends up to 100 from its treasury.
  Vassals share their overlord's treaties.
- **Envoys** (`Envoys`): NPC kingdoms send envoys once a day by creed (Coin wants charters, Conquest aid...). Courts
  weigh offers by temper, creed, friction, tension, rivalry, a truce, a common foe, blood (purists) and, for a player
  realm, your reputation. Envoys ride the King's Roads like couriers, with the realm's banner; you can meet them. Kill
  one and the sender (-200) and the land's realm (-60) remember.
- **Players' realms:** an envoy waits up to two days in your hall and you get a letter. "Hear the Envoy" (MCA Interact
  page; right-click without MCA) offers Sign, Refuse or Not yet. Seek an audience with your own steward for the Ruler's
  Desk: the realms in reach, their creed, relation and tension, the treaties in force (Renounce), each kind's odds, and
  Send an Envoy (20 from the treasury, the rest from your purse).
- **Breaking faith:** declaring war on a partner, or renouncing, tears treaties up: +25 tension, +8 with the breaker's
  other partners. A player sovereign loses 150 reputation with the wronged realm and 30 with each other partner. NPC
  tyrants and warlords may break faith when tension boils over; others hold their hand.
- **News:** the town boards carry every treaty; the Tidings only aid, a player realm's treaties, great charters,
  broken faith and war.
- **Succession** (`Elections`, MCA realms): Coin realms elect, and Warriorborn/Frostborn realms of the Old Blood or
  Conquest acclaim at a moot. Notables are the grown townsfolk (profession level, guard service, kin to the late
  ruler, a gift, temper, luck) and sworn players of Knight rank or higher; a player who wins wears the crown with the
  old steward. In other realms blood inherits, but a child heir gets a regent (the best councillor) until they come of
  age; then the regent steps down. If the child dies, the regent rules on. A teen is never crowned outright now.
- **Ruler info** (MCA interact screen): how the realm chooses its rulers, how this one came to the throne, "Regent for
  ...", and its treaties (hover for the list).
- Loading hint 93. Not played; compile-checked with `kingdoms/gradlew build`.
```

---

## 9. Build and checks
- `cd kingdoms && ./gradlew build` (JDK 25). No dev client or server runs.
- **[26.3]** checks before writing:
  - `VillagerData.level()` accessor.
  - `BannerBlock.byColor(DyeColor)` / its item.
  - `EntityType.VILLAGER.create(Level, EntitySpawnReason)`.
  - `MerchantOffer.getBaseCostA` / `addToSpecialPriceDiff` (already used by `Faiths.prices`).
  - `ServerPlayNetworking.canSend`.
- MCA: `VillagerEntityMCA.getAgeState()` / `AgeState.ADULT|TEEN` (already used in `McaBridge`).
- Verifier focus:
  - `Wars.declare` callers: NPC `stir`, `declareOnRival`, the player command, `Crowns`.
  - That `GreatWars.pledge` doesn't block a pledged ally's own defence: `Wars.declare` refuses a pledged realm as
    attacker or target (:325). So a realm pledged by aid can't be newly declared on during that war. This matches
    great-war pledges and is accepted.
  - That the regency hand-over leaves no second villager in a ruler role.

## 10. Risks and notes
- Hints 89-92 will show raw keys on the loading screen until the PRs that own them merge, because `HINTS = 93` deals
  every number and `StoryHoldScreen` doesn't skip missing keys. This was asked for; merge order fixes it.
- `McaBridge.heir`'s grown filter changes from `!isBaby()` to `AgeState.ADULT`. If MCA's `isBaby()` already excluded
  teens, nothing changes. If not, teens stop being crowned (intended).
- With MCA ageing turned off, a ward never grows; the regency then runs until the ward is a teen and
  `REGENCY_TEEN_DAYS` have passed, or for good if the ward stays a child.
- Elections, regencies and the ruler-info lines need MCA (`Rulers.inPlace`). Treaties, envoys and the desk work
  without it (vanilla villager envoys, right-click to hear, folk ruler audience to open the desk).
- `Envoys.daily` is O(heads²) a day, the same as `GreatWars.daily`.

## 11. Open questions for Andy (defaults are built)
1. Moot bloods: Warriorborn and Frostborn (default). Add others, or let greenskin havens under an orc king acclaim too?
2. Should sworn players stand in elections and be crowned if they win (default yes, `ELECT_PLAYERS`), or only be told?
3. Envoy cost 20 from the treasury (default). Free, or dearer by distance?
4. Should recognition be required before the other three (default yes, `NEEDS_RECOGNITION`)?
