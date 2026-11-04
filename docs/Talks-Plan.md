# Talks that matter: Kingdoms 0.7.523 (pack 0.20.25)

Patch 8. Andy picked the idea of the MCA: Conversations add-on; this is our own version and uses none of its code.

Approved plan text: "Branching talks with two or three replies that change things, hidden trust, respect and warmth on
each villager's memory of you, and promises (bring this by that day, come back, look after my child) that they hold
you to."

Worktree `/home/user/talks` (HEAD 58f9c3561). All code is in `kingdoms/` (package `com.jaymoskingdoms`). Paths below are
relative to `kingdoms/src/main/java/com/jaymoskingdoms/` unless marked. Tag every new code comment `(0.7.523)`.

## 0. What the player sees

- A villager's talk menu (MCA) sometimes shows **Have a word**. Now and then **Chat** turns into a talk too. A small
  screen opens (the existing `TalkScreen`): an italic line on how they look at you (the only hint of how they feel),
  what they say, and two or three replies. Each reply has its own answer (a speech bubble) and quietly shifts how much they
  **trust** you, **respect** you and feel **warmth** for you. Some replies make a **promise**, earn a **favour**, get you a
  **rumour**, or start a **quarrel**.
- The three values are never shown as numbers. They show only in how villagers act: the opening line, what they charge,
  whether they gossip or tell secrets, whether a guard comes to your side in a fight, whether a witness looks away from
  your crime, and whether they'll talk to you at all.
- **Promises** (bring this by that day, come back, look after my child, walk my child home, speak for me to the ruler,
  keep the peace, pay back a loan) go in the Traveller's Journal's Quests page. Keep one and they, and their family,
  warm to you. Break one and the villager remembers, brings it up, and their kin and friends hear of it.
- The Interact page gets **Keep Your Word** when you carry what you promised to bring them.
- No player commands. No chat text from us beyond the villager's own speech (MCA chat lines and speech bubbles).

## 1. Files

New:
| File | What it does |
|---|---|
| `Bearings.java` | Trust, respect and warmth: reading them (with drift and the hearts and reputation inputs), moving them, the deed table, temper scaling, and every behaviour check (prices, gossip, secrets, standing by you in a fight, looking away, refusing). Also the "manner" hint line and the chat AI's prompt line. Registers the fight listener. |
| `Talks.java` | The branching-talk flow: entry points, choosing a talk, sending `TalkNet.Ask`, the open-talk state, `answer(...)` in the TalkNet chain, outcomes (promise, favour, rumour, secret, loan, quarrel, gift, bribe). |
| `TalkBook.java` | Data only: the 52 authored talks (gates, args, replies, deltas, outcomes) and the job goods table. |
| `Promises.java` | Promises: the record and its player attachment, making, checking (tick, deaths, harm, audiences, wrongs), keeping, breaking, releasing, settling on the villager, the journal entries, and a neutral API for patch 9's quests. |

Changed:
| File | Change |
|---|---|
| `Recollections.java` | `Person` gains a `Bearing` field; `bear(...)` writes it; `remember(...)` feeds the deed table; three new `Kind`s; `gossip` split so talks can use it; `refuses` also checks bearing; `rememberHeard(...)`; the ops `memories` command shows the three values. |
| `JaymosKingdoms.java` | Class-load `Promises.PROMISES`; call `Bearings.register()`, `Talks.register()`, `Promises.register()`. |
| `Approaches.java` | `Talks.answer` first in the TalkNet answer chain; BARGAIN friend discount also for a fond villager; `war(...)` made package-private. |
| `InteractActions.java` | New action `PROMISE` ("Keep Your Word"). |
| `mixin/mca/McaDialogueMixin.java` | Branch for `Talks.ANSWER`; Chat sometimes opens a talk. |
| `mixin/mca/McaAnswersMixin.java` | Hide `Talks.ANSWER` unless a talk is ready. |
| `mixin/VillagerPriceMixin.java` | `Bearings.prices(...)` before `Currencies.localize`. |
| `Theft.java` | `witnesses(...)`: a villager who would shield you looks away. |
| `KingdomEvents.java` | `audience(player, folk, factionId)`: tell `Promises.audience`. |
| `ComingOver.java` | New `Reason.PROMISE`: a villager you promised comes over on the due day. |
| `ChatPrompts.java` | Add the bearing line to the AI prompt. |
| `SharedHistory.java` | `WRONGS` adds `BROKE_WORD`, so Apologise can be about it. |
| `Journal.java` | `entries.addAll(Promises.journal(player))`. |
| `kingdoms/src/client/java/com/jaymoskingdoms/client/StoryHoldScreen.java` | `HINTS = 95`. |
| `kingdoms/src/main/resources/data/jaymoskingdoms/dialogues/main.json` | Answer `jaymoskingdoms_word`. |
| `kingdoms/src/main/resources/assets/jaymoskingdoms/lang/en_us.json` | All keys below. |
| `kingdoms/gradle.properties` | `version=0.7.523`. |
| `gradle.properties` (root) | `version=0.20.25`. |
| `kingdoms/MODLOG.md`, `MODLOG.md` | Entries in section 11. |

No new textures, models, recipes, items, blocks, key bindings, packets or client screens. `TalkScreen`, `TalkNet.Ask` /
`Answer`, `Speech`, the journal and the Interact page are all reused as they are.

## 2. Bearings: trust, respect, warmth

### 2.1 Storage (on the villager's memory of you)

In `Recollections.java`:

```java
/** (0.7.523) How they bear toward the player: trust, respect, warmth (-100..100, never shown), the day drift was last
 *  applied, the talks they've had with them (id -> day; "#day" the last talk day, "#favour" the last favour day), and the
 *  day a quarrel lasts until (-1: none). */
public record Bearing(int trust, int respect, int warmth, long settled, Map<String, Long> talks, long quarrel) {
    public static final Bearing NONE = new Bearing(0, 0, 0, 0L, Map.of(), -1L);
    public static final Codec<Bearing> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.INT.optionalFieldOf("trust", 0).forGetter(Bearing::trust),
        Codec.INT.optionalFieldOf("respect", 0).forGetter(Bearing::respect),
        Codec.INT.optionalFieldOf("warmth", 0).forGetter(Bearing::warmth),
        Codec.LONG.optionalFieldOf("settled", 0L).forGetter(Bearing::settled),
        Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("talks", Map.of()).forGetter(Bearing::talks),
        Codec.LONG.optionalFieldOf("quarrel", -1L).forGetter(Bearing::quarrel)).apply(i, Bearing::new));
}
public record Person(String name, long last, List<Recollection> memories, Bearing bearing) { ...
    Bearing.CODEC.optionalFieldOf("bearing", Bearing.NONE).forGetter(Person::bearing) }
```

- Every `new Person(...)` in `Recollections` (in `remember`, `forget`, `markTold`; grep shows no other callers) carries
  the old bearing along (`had == null ? Bearing.NONE : had.bearing()`).
- Old saves load fine: the field is optional.
- It stays in `MEMORIES` (persistent, **not synced**). Nothing about it goes into any packet. Do not use `syncWith`.
- It is keyed like memories, by `Recollections.key(player, who)`, so a masked stranger builds a bearing under the costume's
  stand-in, not under the real player.
- The `PLAYERS = 24` cap applies (the player unseen longest is forgotten, bearing and all). Keep that: a villager who
  hasn't seen you for ages may forget you.
- New package-private writer: `static void bear(LivingEntity who, ServerPlayer player, UnaryOperator<Bearing> change)`
  does get, change and put under the same key and name rules as `remember`. It creates the `Person` if needed and applies
  the same `PLAYERS` cap.
- `TALKS_KEPT = 24`: `talks` map entries beyond this drop the oldest day first.

### 2.2 Reading (`Bearings`)

`static int trust(LivingEntity who, ServerPlayer p)`, `respect(...)`, `warmth(...)` return the **effective** value:

- **Drift** (applied lazily on read and before each write): each axis moves 1 point toward 0 every `DRIFT_DAYS_TRUST = 4`,
  `DRIFT_DAYS_RESPECT = 4`, `DRIFT_DAYS_WARMTH = 2` days since `settled` (day from `Deeds.today`).
- **Respect** adds `clamp(Reputation.get(player, f.id()) / REP_PER_RESPECT, -REP_RESPECT_MOST, REP_RESPECT_MOST)` with
  `REP_PER_RESPECT = 40`, `REP_RESPECT_MOST = 20`, where `f` is the villager's settlement (`Quests.settlement`, for
  children their parents' town by position via `Theft.lawAt`; none: no bonus).
- **Warmth** adds `clamp(McaCompat.heartsOf(v, player) / HEARTS_PER_WARMTH, -HEARTS_WARMTH_MOST, HEARTS_WARMTH_MOST)`
  with `HEARTS_PER_WARMTH = 5`, `HEARTS_WARMTH_MOST = 20` (0 for plain villagers and masked strangers). Hearts stay
  MCA's own. We only read them.
- The result is clamped to `LEAST = -100`, `MOST = 100`.

### 2.3 Moving

`static void move(LivingEntity who, ServerPlayer p, int trust, int respect, int warmth)`: applies drift, scales by
temper (2.5), adds the changes, clamps the stored values to -100..100, sets `settled = today`.

From deeds: in `Recollections.remember(LivingEntity who, UUID id, String name, Kind kind, Component detail, String from)`
(the private overload every memory goes through), after the list is merged, call
`Bearings.deed(who, id, kind, !from.isEmpty())`, but not when the merge pushed the count past `COUNT_CAP`. Hearsay
(`from` not empty: `share`, kin told of a kept or broken promise) applies the row divided by `HEARSAY_DIV = 2`
(rounded toward 0). Every existing deed hook (Deeds, Rescues, Crooks, Quests, SharedHistory, Approaches.replied,
Restoration, ValorFolk, Trespass, Couriers, Tag, Pickpocket, Retaliation, FightPit, Questioning, Audiences, MCA
gifts, trades and social acts) moves bearings this way **without being edited**.

`Bearings.deed` works by id (not `ServerPlayer`), since `share` has no player. So there is a private
`move(LivingEntity who, UUID id, ...)` too, and the public one calls it with `Recollections.key(player, who)`.

### 2.4 The deed table (`Bearings.DEEDS`, `EnumMap<Recollections.Kind, int[]>` of {trust, respect, warmth})

| Kind | T | R | W |  | Kind | T | R | W |
|---|---|---|---|---|---|---|---|---|
| GIFT | 0 | 0 | 3 | | SAVED | 15 | 8 | 15 |
| TRADE | 1 | 0 | 1 | | MENDED | 4 | 2 | 6 |
| FED | 2 | 0 | 5 | | FREED | 10 | 5 | 10 |
| KIND | 0 | 0 | 2 | | HOME | 15 | 6 | 15 |
| STOOD | 3 | 4 | 2 | | RETURNED_KIN | 12 | 5 | 12 |
| PLAYED | 1 | 0 | 3 | | SAW_DEED | 2 | 4 | 1 |
| CHATTED | 0 | 0 | 1 | | QUEST_DONE | 6 | 3 | 3 |
| HUGGED | 0 | 0 | 3 | | QUEST_FAILED | -5 | -2 | -1 |
| KISSED | 1 | 0 | 5 | | LETTER | 2 | 0 | 1 |
| REBUFFED | 0 | -1 | -2 | | REVIVED_YOU | 0 | 0 | 1 |
| RUDE | -1 | -2 | -4 | | SHOVED_YOU | -1 | 0 | -2 |
| LIED | -8 | -3 | -3 | | SEARCHED_YOU | 0 | 0 | -1 |
| THREATENED | -6 | 0 | -8 | | THANKED | 1 | 1 | 2 |
| CONFESSED | 3 | 1 | 0 | | APOLOGISED | 3 | 1 | 2 |
| BRIBED | -2 | -3 | 1 | | SPURNED | -1 | -1 | -1 |
| BEAT_YOU (you beat them) | 0 | 5 | 0 | | AIRED | 1 | 1 | 0 |
| LOST_TO_YOU (they beat you) | 0 | -2 | 1 | | ASKED_AFTER | 0 | 0 | 2 |
| STRUCK | -5 | -1 | -6 | | CARRIED | 8 | 4 | 8 |
| PAID_OFF | 1 | 0 | 1 | | GOT_UP | 8 | 4 | 8 |
| CRIME | -6 | -3 | -3 | | TENDED | 3 | 1 | 4 |
| ROBBED | -10 | -2 | -6 | | SHIELDED | 6 | 5 | 5 |
| KILLED_KIN | -40 | 0 | -40 | | THREW | -4 | -2 | -5 |
| SPARED | 4 | 4 | 2 | | QUESTIONED | -12 | 3 | -12 |
| SWORE | 2 | 0 | 0 | | ACCUSED | -6 | -2 | -6 |
| KEPT_WORD (new) | 15 | 5 | 10 | | BROKE_WORD (new) | -25 | -5 | -12 |
| PROMISED (new) | 0 | 0 | 0 | | TALKED, LED_YOU, HALTED_YOU | 0 | 0 | 0 |

Any Kind missing from the map counts 0. The editor must cover every Kind constant (a unit check at class load is fine:
log a warning for unmapped kinds).

### 2.5 Temper (scales changes, villagers only, `Temperaments.of(v)`)

| Temperament | Effect | Constant |
|---|---|---|
| PROUD | respect changes x1.5 | `PROUD_RESPECT = 1.5F` |
| SUSPICIOUS | trust gains x0.5 | `SUSPICIOUS_TRUST_GAIN = 0.5F` |
| KINDHEARTED, CHEERFUL | warmth losses x0.5 | `GENTLE_WARMTH_LOSS = 0.5F` |
| SPITEFUL | every loss x1.5 | `SPITEFUL_LOSS = 1.5F` |
| HOTHEADED | warmth losses x1.5 | `HOTHEADED_WARMTH_LOSS = 1.5F` |

Round half away from zero. A non-zero change never rounds to 0.

### 2.6 What it changes (behaviour)

Thresholds are effective values (2.2), all `static final` at the top of `Bearings`:

| Behaviour | Rule | Hook (existing method edited) |
|---|---|---|
| **Prices** | `m = clamp((warmth + trust / 2) / 100F, -1, 1)`. If `abs(m) >= PRICE_LEAST_SWAY (0.15F)`: each offer `addToSpecialPriceDiff(-round(cost * PRICE_SHARE_MOST * m))`, `PRICE_SHARE_MOST = 0.2F`, at least 1 either way. (Vanilla keeps a price at 1 or more.) | `VillagerPriceMixin.jaymoskingdoms$purist`: `Bearings.prices(villager, player)` after `Treaties.prices`, before `Currencies.localize`. Same shape as `Prejudice.prices`. |
| **Haggle discount** | A fond villager (see manner "fond") also gives `FRIEND_DISCOUNT` | `Approaches.prepare` BARGAIN (L849): `friend = ... || Bearings.fond(a.mob, player)` |
| **Gossip** | `trust <= GUARDED_TRUST (-15)`: they don't gossip to you. `trust >= CONFIDE_TRUST (30)`: chance x`CONFIDE_GOSSIP = 2.0F` | `Recollections.gossip` (L457): gate and chance |
| **Rumours and secrets in talks** | RUMOUR needs `trust >= RUMOUR_TRUST (15)`; SECRET needs `SECRET_TRUST (35)` | `Talks` outcomes |
| **Loans** | offered only at `trust >= LEND_TRUST (35)` and `warmth >= LEND_WARMTH (20)` | `TalkBook` gate `LENDER` |
| **Favours** | given only at `warmth >= FAVOUR_WARMTH (10)` | `Talks` outcome FAVOUR |
| **Taking your word** | `trust <= DOUBT_TRUST (-25)`: promise replies are greyed, label "(they won't take your word)" | `Talks.options` |
| **Standing by you in a fight** | When a player is hurt by an `Enemy` (monsters, raiders), each guard (`Downed.isGuard`) within `STAND_RANGE = 16.0` with `trust >= STAND_TRUST (20)` and `respect >= STAND_RESPECT (30)`, not already fighting (`Rulers.target(mob) == null`), charges it (`Rulers.charge(guard, attacker)`, which already covers folk and MCA guards). Grown non-guard villagers who'd stand by you shout a warning. Once per player per `STAND_GAP = 40` ticks; lines at most every `STAND_SAY_GAP = 600` ticks per speaker. Never in a duel (`Duels.dueling`), never when the attacker is a villager, folk or player, never when `Reputation.get(player, f) <= Reputation.HOSTILE`. | New `ServerLivingEntityEvents.AFTER_DAMAGE` in `Bearings.register()` |
| **Hiding you** | A witness villager (not folk, not golem) with `trust >= SHIELD_TRUST (50)` and `warmth >= SHIELD_WARMTH (40)` looks away: dropped from the witness list. So they don't cry out, don't report it and don't remember it as a crime. | `Theft.witnesses` (L563): `out.removeIf(e -> e instanceof Villager v && Bearings.shields(v, player))` before sorting. (Pickpocket marks pass their own list, so a mark still knows.) |
| **Refusing to talk** | `warmth <= REFUSE_WARMTH (-50)` or `trust <= REFUSE_TRUST (-60)` | `Recollections.refuses` (L338): `leaning <= REFUSE \|\| Bearings.cold(who, player)`. That already blocks MCA chat, joke, story, hug, flirt and kiss (McaDialogueMixin) and so blocks Talks too. |
| **Talk hints** | The manner line (2.7) on every talk screen | `Talks.send` |
| **Chat AI** | One prompt line in words: e.g. "You trust them a great deal. You look up to them. You are fond of them." | `ChatPrompts` after the `lean` line: `sb.append(Bearings.prompt(villager, player))` |

Prompt words (Java strings, like the Kind prompts), joined with spaces, empty when all three sit in -10..10:
- trust: `>= 50` "You trust them a great deal."; `>= 20` "You trust them."; `<= -50` "You would not trust them with anything."; `<= -20` "You don't trust them."
- respect: `>= 50` "You look up to them."; `>= 20` "You respect them."; `<= -50` "You hold them in contempt."; `<= -20` "You think little of them."
- warmth: `>= 50` "You are fond of them."; `>= 20` "You like them."; `<= -50` "You can't stand them."; `<= -20` "You dislike them."

### 2.7 Manner (the hint line)

`static String manner(LivingEntity who, ServerPlayer p)`, checked in this order:
1. `warmth <= COLD_WARMTH (-20)`: `cold`
2. `trust <= WARY_TRUST (-20)`: `wary`
3. `respect <= SCORN_RESPECT (-30)`: `scornful`
4. `warmth >= FOND_WARMTH (50)` and `trust >= FOND_TRUST (30)`: `fond`
5. `respect >= ADMIRING_RESPECT (40)`: `admiring`
6. `warmth >= WARM_WARMTH (20)`: `warm`
7. `trust >= TRUSTING_TRUST (40)`: `trusting`
8. else `plain`

`Bearings.fond(...)` is rule 4. The line is `talk.jaymoskingdoms.word.manner.<state>.<0..MANNER_LINES-1>` (`MANNER_LINES = 3`) with `%1$s` the
villager's display name, italic grey on the screen.

### 2.8 Ops check (no player command)

`Recollections.show` (the existing ops-only `/kingdom memories`) sends one more line after the head:
`recall.jaymoskingdoms.command.bearing` with the stored and effective values. It is ops only, for testing.

## 3. Talks: entry points and flow

### 3.1 Entry points

1. **MCA talk menu, "Have a word"**: `Talks.ANSWER = "jaymoskingdoms_word"`, added to `dialogues/main.json` like the
   others (`{"name": "jaymoskingdoms_word", "results": [{"actions": {}}]}`), lang
   `dialogue.main.jaymoskingdoms_word`.
   - `McaAnswersMixin`: `if ("main".equals(name) && !com.jaymoskingdoms.Talks.canOffer(player, villager)) remove(Talks.ANSWER)`.
   - `McaDialogueMixin.jaymoskingdoms$homeTalk`, after the `Recollections.refuses` block:
     `if ("main".equals(question) && Talks.ANSWER.equals(answer)) { villager.getInteractions().stopInteracting(); if (!Talks.open(player, villager, false)) villager.sendChatMessage(Talks.nothing(player, villager), player); ci.cancel(); return; }`
     The language check (shrug) runs before this, as for every worded answer.
2. **MCA Chat**: in the same mixin, right after that, `if ("main".equals(question) && "chat".equals(answer) && Talks.onChat(player, villager)) { villager.getInteractions().stopInteracting(); ci.cancel(); return; }`.
   `onChat`: `random < TALK_ON_CHAT_CHANCE (0.3F)` and `canOffer`, then `open(player, v, true)`. When it doesn't fire,
   MCA's chat goes on as before (and so do `BEFORE` and `socialised`).
3. **Interact page "Keep Your Word"** (`InteractActions.PROMISE = "promise"`, added to `ALL` after `ENVOY`): see 4.6.
   This is a hand-over, not a talk, so it fits the Interact page rule (0.7.454: actions there, talk in the menu).

Talks are for MCA `Villager`s only (grown or child). Not for `Folk`, not for greenskins (`Dialects.of(v) != null`:
they speak their own dialect, which these lines have no variants for), not while `Approaches.talking(v)`, not while
`Questioning.active(player, v)`, and only within `Approaches.TALK_RANGE` (8.0).

### 3.2 Choosing a talk (`Talks.pick(ctx)`)

`Context` record: `(ServerLevel level, ServerPlayer player, Villager v, @Nullable Faction f, String job, @Nullable Heritage.Kind blood, @Nullable Heritage.Kind mine, @Nullable Patron faith, @Nullable Patron sworn, List<Recollections.Recollection> memories, long today, long clock)`.
Here `job` is `BuiltInRegistries.VILLAGER_PROFESSION.getKey(v.getVillagerData().profession().value()).getPath()`
(as in `Downed.isGuard`), with `"guard"` when `Downed.isGuard(v)`. `sworn` is `Patrons.of(player)` only when
`Patrons.state(player).sworn()`.

1. A **promise talk** first, always, when one applies (4.5): `promise_due` or `promise_broken`.
2. Otherwise, nothing when: `Bearing.quarrel >= today`; `talks["#day"] == today` (`TALKS_PER_DAY = 1` per villager per
   player per day); `Bearings.cold(...)` or `Recollections.refuses(...)`.
3. Otherwise, a weighted draw over the talks in `TalkBook.ALL` whose gate passes and that weren't had in the last
   `TALK_REPEAT_DAYS = 24` days (`once` talks: never again with this villager). Weights: `WEIGHT_RECENT = 3` for the
   talks marked recent, `WEIGHT_LENDER = 2` and `WEIGHT_SECRET = 2`, `WEIGHT_LIFE = 1` for the rest.

`canOffer(player, v)` is the same test without the draw (any candidate exists). It must stay cheap: gates only read
entity state, attachments and a small entity box query (no path finding).

### 3.3 The screen

`Talks.send(player, v, talk)`:
- `words = Component.translatable("language.jaymoskingdoms.gist", manner.withStyle(GRAY, ITALIC), Component.translatable(open key, args))`
  (that key is already `"%s\n\n%s"`).
- `options`: each reply's label, `Component.translatable(key, args)`. A reply that makes a promise, when
  `trust <= DOUBT_TRUST`, is greyed with `talk.jaymoskingdoms.word.doubt` around its label. When the player has
  `Promises.MAX_OPEN` open promises it's greyed with `talk.jaymoskingdoms.word.too_many`. A reply whose `need` fails
  (no item, not enough emeralds, no MCA for an escort) is greyed with no suffix.
- `TalkNet.Ask(v.getId(), v.getDisplayName(), words, labels, enabled)` via `ServerPlayNetworking.send` (check `canSend`).
- `OPEN.put(player UUID, new Open(v.getId(), talk.id(), clock + OPEN_TICKS))` with `OPEN_TICKS = 1200L`.

The talk counts as had (`talks[id] = today`, `talks["#day"] = today`) when the screen is sent, so closing it without
answering doesn't let you fish for another one.

### 3.4 Answers (`Talks.answer(ServerPlayer, TalkNet.Answer)`)

Goes **first** in `Approaches.answer` (before `Disguises.answer`): `if (Talks.answer(player, answer)) return;`.
- Returns false unless `OPEN` has an entry for this player with the same `entityId` and it hasn't expired. In that case
  it removes the entry and returns true from here on.
- `option < 0`: nothing more (they shrug; no change).
- The option must be in range and its `need` and enablement re-checked now (the server holds the truth, as in
  `Approaches` L1140-1146). The villager must be alive, loaded, and within `TALK_RANGE`.
- Then `apply(level, player, v, talk, reply)`:
  1. Their answer: `Speech.tell(level, v, player, Component.translatable(said key, args), outcome == QUARREL ? Speech.RED : Speech.WHITE)`.
  2. `Bearings.move(v, player, reply.trust, reply.respect, reply.warmth)`.
  3. MCA hearts in step: `reply.warmth >= TALK_HEARTS_AT (3)`: `McaCompat.hearts(v, player, TALK_WARM_HEARTS = 2)`;
     `reply.warmth <= -TALK_HEARTS_AT`: `-TALK_COLD_HEARTS = 2`.
  4. The outcome (3.5).
  5. A villager sound as Approaches does (`VILLAGER_AMBIENT` on a warm reply, `VILLAGER_NO` on a quarrel).

### 3.5 Outcomes

| Outcome | What happens |
|---|---|
| `NONE` | only the deltas |
| `BRING(item, count, days)` / `BRING_JOB(days)` | `Promises.make(player, v, Kind.BRING, terms)`; `BRING_JOB` takes the item and count from `TalkBook.GOODS[job].want` |
| `COME_BACK(days)` | promise COME_BACK |
| `WATCH_CHILD` | promise WATCH_CHILD on the nearest baby kin within `CHILD_RANGE` |
| `ESCORT_CHILD` | promise ESCORT_CHILD on the wandered child the gate found; `McaCompat.follow(child, player)` |
| `SPEAK_FOR(days)` | promise SPEAK_FOR (needs the villager's realm to have a ruler) |
| `KEEP_PEACE(days)` | promise KEEP_PEACE |
| `LOAN` | gives `LOAN_EMERALDS = 6` emeralds (`player.getInventory().add`, else drop, as `Approaches.give`) and makes promise REPAY (6, `LOAN_DAYS = 3`) |
| `FAVOUR` | if `warmth >= FAVOUR_WARMTH` and `talks["#favour"]` is older than `FAVOUR_GAP_DAYS = 3`: gives `GOODS[job].give` and says `talk.jaymoskingdoms.word.favour.given`; else says `talk.jaymoskingdoms.word.favour.none` |
| `RUMOUR` | `trust < RUMOUR_TRUST`: `rumour.clam.<0..1>`. Else, in order: a crook near (if `trust >= SECRET_TRUST`) `rumour.crook`; `Recollections.gossipLine(v, player, random)` (the "about" line, if any); the realm at war `rumour.war`; else `rumour.none.<0..1>`. Said as a second bubble. |
| `SECRET` | like RUMOUR but only the crook line; under `SECRET_TRUST` they clam up |
| `QUARREL` | `quarrel = today + QUARREL_DAYS (1)`; `McaCompat.hearts(v, player, -QUARREL_HEARTS = 5)`; `Recollections.remember(v, player, RUDE, empty)` |
| `GIVE(item, count)` | takes the items (`Purse.take`), `remember(GIFT, item name)` |
| `PAY(n)` | takes n emeralds (`Purse.take(player, Items.EMERALD, n)`, coin counts), `remember(GIFT, "n emeralds")` |
| `BRIBE(n)` | takes n emeralds, `remember(BRIBED)` |
| `LIE` | `remember(LIED)` |
| `KIND` | `remember(KIND)` |
| `KEEP(promise)` / `RELEASE(promise)` | promise talks (4.5) |

A memory an outcome records moves the bearing by its deed row too (2.4), on top of the reply's own deltas. The tables
below list only the reply's own deltas.

`Recollections.gossip` is split: `gossipLine(who, listener, random)` (no chance roll, package-private) holds today's
body; `gossip(...)` keeps the chance roll and the new bearing gate, then calls it.

Crook near: the nearest `Villager` other than `v` within `RUMOUR_RANGE = 64.0` with `Crooks.isCrook(e)`; its
display name is `%2$s`.

## 4. Promises

### 4.1 Kinds

| Kind (`Promises.Kind`, key) | Made by | Kept when | Broken when |
|---|---|---|---|
| `BRING` ("bring") | a talk reply | handed over (Keep Your Word, or the `promise_due` talk) by the due clock | due passes |
| `REPAY` ("repay") | a loan | the same, with emeralds (coin counts, `Purse`) | due passes |
| `COME_BACK` ("come_back") | a talk reply | you're within `COME_BACK_NEAR = 8.0` of them on a day after the day it was made, by the due clock | due passes |
| `WATCH_CHILD` ("watch_child") | a talk reply | for each of the `WATCH_DAYS = 2` days after it's made, you come within `WATCH_NEAR = 16.0` of the child at least once; the child is alive when the last day ends | a day ends without a visit, or the child dies |
| `ESCORT_CHILD` ("escort_child") | a talk reply | the child gets within `ESCORT_HOME_NEAR = 8.0` of the parent's home (`McaCompat.home(parent)`) or of the parent within `ESCORT_TICKS = 6000` ticks; then `McaCompat.stopFollowing(child)` | time runs out, the child dies, or you change dimension without them (then `stopFollowing`) |
| `SPEAK_FOR` ("speak_for") | a talk reply | you have an audience with the ruler of the villager's realm by the due clock (`Promises.audience`) | due passes |
| `KEEP_PEACE` ("keep_peace") | a talk reply | the due clock passes with no wrong | anyone within `PEACE_RANGE = 96.0` of the promise's place (same level) records `STRUCK`, `CRIME`, `ROBBED`, `THREATENED` or `KILLED_KIN` against you (the stand-in id counts too) |

A harmed child (`AFTER_DAMAGE` on the watched or escorted child, any source) doesn't break the promise. It sets `hurt`,
and on settling the parent says `promise.jaymoskingdoms.child_hurt` and adds `CHILD_HURT_WARMTH = -5` warmth.

### 4.2 The record and attachment

```java
/** (0.7.523) A promise: kind, to whom (uuid, name as known, realm id or -1, where they were), what (item id, count),
 *  the child it's about, when it was made (clock, day), when it's due (Overworld clock), days visited / last visit day
 *  (WATCH_CHILD), harmed (a child), status (open, kept, broken, released), and the day it was settled (-1 while open). */
public record Promise(String kind, UUID to, String name, int faction, BlockPos where, String item, int count,
    Optional<UUID> child, long made, long madeDay, long due, int visits, long lastVisit, boolean hurt, String status, long closed)
```

The `Codec` is built with `RecordCodecBuilder` (16 fields, the limit), with `optionalFieldOf` defaults for everything
past `due`. `where` is `BlockPos.CODEC`, `child` `UUIDUtil.STRING_CODEC.optionalFieldOf`.

`static final AttachmentType<List<Promise>> PROMISES = AttachmentRegistry.create(JaymosKingdoms.id("promises"), b -> b.persistent(Promise.CODEC.listOf()).copyOnDeath());`
It lives on the **player**, persistent, copies on death, and is **not synced**. Class-load it in `JaymosKingdoms`
next to the memory attachment: `LOGGER.debug("promise attachment: {}", Promises.PROMISES);`.

Times use the Overworld clock, as `BloodRequests` and `Deeds.today` do:
`clock = Factions.level(server).getOverworldClockTime()`, `day = Math.floorDiv(clock, 24000L)`. A promise "in N days"
falls due at the end of day `today + N`: `due = (today + N + 1) * 24000L - 1`. ESCORT_CHILD uses `clock + ESCORT_TICKS`.

Limits: `MAX_OPEN = 5` open per player; `PER_VILLAGER = 1` open per villager (a talk with a promise reply isn't drawn
for a villager you already owe).

### 4.3 Checks

- `Promises.tick` on `ServerTickEvents.END_SERVER_TICK`, every `TICK_EVERY = 20` ticks, for each online player with
  promises:
  - open COME_BACK: find the villager (`level.getEntity(uuid)` in the player's level). If they're alive, within
    `COME_BACK_NEAR`, and `today > madeDay`: kept.
  - open WATCH_CHILD: when the child is in range today, set `visits` and `lastVisit`. At a day boundary, if a whole day
    from `madeDay + 1` passed with `lastVisit` behind it: broken. When `today > due day` with every day visited: kept.
  - open ESCORT_CHILD: the child is near home or the parent: kept. Out of time or a different dimension: broken.
  - any open promise with `clock > due` and no other rule: BRING, REPAY, SPEAK_FOR are broken; KEEP_PEACE is kept.
  - status not open: try to **settle** (4.4).
  - drop a settled-but-never-met promise once `today - closed > SETTLE_LIMIT_DAYS (30)`.
- `ServerLivingEntityEvents.AFTER_DEATH`: a watched or escorted child (UUID match over online players' open promises):
  broken (detail "child_lost"). The villager promised dying: the promise is dropped quietly.
- `ServerLivingEntityEvents.AFTER_DAMAGE` on a baby `Villager`: matching promise `hurt = true`.
- `Promises.audience(ServerPlayer, int factionId)`: open SPEAK_FOR with that faction becomes kept. Hook:
  `KingdomEvents.audience(player, folk, factionId)` after the barbarian check, when `Rulers.is(folk)` or `folk` is a
  `Folk` with `role() == Folk.KING` (check that `Rulers.is` covers folk kings; if it does, `Rulers.is` alone).
- `Promises.wronged(ServerPlayer, LivingEntity who, Recollections.Kind kind)`: called at the end of the public
  `Recollections.remember(who, player, kind, detail)`. Open KEEP_PEACE in range: broken.
- `ServerPlayConnectionEvents.DISCONNECT`: an ESCORT_CHILD in progress is broken (`stopFollowing`). Other kinds keep
  running against the clock while you're away.

### 4.4 Keeping, breaking, settling

Status changes are immediate on the player's record. The villager's side (memory, bearing, hearts, kin) waits until
the villager is loaded: `settle(level, player, p, v)` runs as soon as `getEntity(p.to)` finds them alive in any server
level (tried each `TICK_EVERY`):

- **Kept**: `Recollections.remember(v, player, KEPT_WORD, what(p))` (so the deed row: +15/+5/+10);
  `McaCompat.hearts(v, player, KEPT_HEARTS = 10)`; `Reputation.change(player, f, KEPT_REP = 5)` (if `f`); kin within
  `Recollections.KIN_RANGE` (`McaCompat.kin != STRANGERS`) get `rememberHeard(kin, player, KEPT_WORD, what, v name)`
  (half row) and `McaCompat.hearts(kin, player, KIN_KEPT_HEARTS = 3)`;
  `Patrons.deed(player, Patron.TRUTH, Patrons.DEED_MID, "kept_word")`, and for WATCH_CHILD or ESCORT_CHILD also
  `Patrons.deed(player, Patron.HEARTHKEEPER, Patrons.DEED_MID, "kept_child")`. If the player is within
  `SETTLE_SAY_RANGE = 16.0`, the villager says `promise.jaymoskingdoms.kept.<kind>.<0..1>` (`Speech.tell`, GOLD).
- **Broken**: `remember(v, player, BROKE_WORD, what(p))` (-25/-5/-12); `McaCompat.hearts(v, player, -BROKEN_HEARTS = 15)`;
  `Reputation.change(player, f, -BROKEN_REP = 10)`; kin get `rememberHeard(... BROKE_WORD ...)` and
  `McaCompat.hearts(kin, player, -KIN_BROKEN_HEARTS = 5)`. If in range they say `promise.jaymoskingdoms.broken.<kind>.<0..1>`
  (`child_lost` in place of the kind when the child died), RED. `BROKE_WORD` is tellable, so `Recollections.share`
  carries it on to friends at the well (`Neighbours`) with no new code: that's how neighbours cool.
- **Released** (you told them honestly you can't, 4.5): `Bearings.move(v, player, RELEASE_TRUST = -8, 0, RELEASE_WARMTH = -2)`,
  `McaCompat.hearts(v, player, -RELEASED_HEARTS = 2)`, no memory and no kin. They say `promise.jaymoskingdoms.released.<0..1>`.
- Then the promise is removed from the list.

`Recollections.rememberHeard(LivingEntity who, ServerPlayer player, Kind kind, Component detail, String from)` is a
new package-private wrapper over the private overload (key and name as `remember` uses).

`what(p)` is the memory's detail, written from the villager's side (`promise.jaymoskingdoms.what.<kind>`), so
"You promised to %2$s" reads "You promised to bring me 8 Bone Meal".

### 4.5 Promise talks (picked before any other)

- `promise_due`: you hold an open BRING or REPAY to this villager.
  - a "Here it is." (needs the goods) → KEEP: `Purse.take`, kept, and their thanks is `promise.jaymoskingdoms.handed.<kind>.<0..1>`
  - b "Not yet. Soon." → "Don't forget, %1$s." · 0/0/0
  - c "I can't. I'm sorry." → RELEASE (the released line is their answer)
- `promise_broken`: they hold a BROKE_WORD memory of you from the last `BROKEN_TALK_DAYS = 7` days, not yet raised in a
  talk (`talks["#broken"]` older than that memory's day).
  - a "I'm sorry. I'll make it right." → "We'll see." · +2/0/+1
  - b "Things came up." → "They always do, with you." · 0/0/-1
  - c "I never promised you anything." → "...Get away from me." · -5/-2/-3 · LIE + QUARREL

### 4.6 Keep Your Word (Interact page)

- `InteractActions.offers` → `case PROMISE -> v instanceof Villager villager && Promises.handable(player, villager)`:
  an open BRING or REPAY to them and you carry enough (`Purse.count`).
- `act` → `case PROMISE -> { return Promises.handOver(player, (Villager) v); }`: takes the goods, kept, returns the
  `handed` line (MCA prints it as their chat line).
- Lang `gui.button.act.promise`: "Keep Your Word". No client change: `McaScreensMixin` builds the buttons from `ALL`, and
  `McaActionButtonsMixin` shows only what `Offered` lists.

### 4.7 They hold you to it (reminders)

- `ComingOver.Reason.PROMISE("promise", 9)`. In `reason(...)`: if `Promises.dueToday(player, v)` (an open promise to
  `v` whose due falls within the current day and isn't yet kept), add
  `new Pick(Reason.PROMISE, new Object[] {Promises.what(p)}, null)`. Lines `come.jaymoskingdoms.hail.promise.<0..1>`,
  `come.jaymoskingdoms.promise.<0..2>`, `come.jaymoskingdoms.why.promise.<0..1>`, `come.jaymoskingdoms.gist.promise`.
- Their memory of a broken promise is a remark (`BROKE_WORD.remark = true`), so MCA's opening and chat bring it up
  through the existing `Recollections.opening` / `remark` with the new `recall.jaymoskingdoms.broke_word.*` lines.
- SharedHistory: `BROKE_WORD` joins `WRONGS`. Apologise can then be about it (the existing forgiveness roll), using
  `history.jaymoskingdoms.for.broke_word`.

### 4.8 Journal

`Promises.journal(ServerPlayer)` returns `List<Journal.Entry>`, added in `Journal.open` after `Captains.journal`:
- `royal = false`; `ready` = BRING/REPAY carried enough, or WATCH_CHILD visited today, or kept-but-unsettled.
- `title = promise.jaymoskingdoms.journal.title (name)`; `giver = promise.jaymoskingdoms.journal.giver (name, realm)`,
  or `.giver.alone (name)` without a realm.
- `text = promise.jaymoskingdoms.journal.<kind>` (args below); `objective`: open `due.today` / `due.days (n)` /
  `due.soon` (escort); WATCH_CHILD adds `visited` or `not_visited` via `promise.jaymoskingdoms.journal.both`; unsettled
  `kept` / `broken`.
- `target = Optional.of(where)`; for WATCH/ESCORT, the child's position if it's loaded.

The Quests tab count (`journal.jaymoskingdoms.sub.quests`) counts them too, as it does for orders. That's fine.

### 4.9 API for patch 9 (quests)

Public and neutral, so Quests can make promises without knowing talks:
- `Promises.make(ServerPlayer player, LivingEntity to, Kind kind, Terms terms)` → `boolean` (false at limits). `Terms`
  record: `(String item, int count, @Nullable UUID child, long due, BlockPos where)`.
- `Promises.of(ServerPlayer)` (an immutable list), `Promises.open(ServerPlayer, UUID to)`.
- `Promises.keep(ServerPlayer, Promise)`, `Promises.breakOff(ServerPlayer, Promise, String why)`,
  `Promises.release(ServerPlayer, Promise)`.
- `Promises.what(Promise)` (Component), `Promises.dueToday(...)`, `Promises.handable(...)`, `Promises.handOver(...)`.
Quests stay in `Quests.LOG`. Promises never touch it.

## 5. TalkBook: the 52 talks

Every talk has its own keys:
- `talk.jaymoskingdoms.word.<id>.open`: what they say
- `talk.jaymoskingdoms.word.<id>.<a|b|c>`: the reply button
- `talk.jaymoskingdoms.word.<id>.<a|b|c>.said`: their answer

Args everywhere: `%1$s` = your name as they know you (`Recollections.nameTo`). `%2$s` and `%3$s` are given per talk.
Deltas are trust/respect/warmth. "Recent" talks get `WEIGHT_RECENT`. `once` talks never repeat with that villager.
The text below is the lang text, exactly.

Java shape: `record Talk(String id, Gate gate, boolean recent, boolean once, Args args, List<Reply> replies)`,
`record Reply(String key, int trust, int respect, int warmth, Outcome outcome, Need need)`. `Gate` and `Args` are
functional interfaces over `Context`. `Need` is `NONE`, `ITEM(item, count)`, `EMERALDS(n)`, `RULER`, `MCA`, `CHILD_IN_SIGHT`.

### Gates

| Gate | Test |
|---|---|
| `JOB(x)` | grown, `ctx.job` equals x (`none` and `nitwit` both count as "nitwit") |
| `PARENT` | grown, a baby `Villager` with `McaCompat.kin(v, baby) == KIN` within `CHILD_RANGE = 32.0` |
| `CHILD_AWAY` | MCA loaded, grown, a baby kin within `ESCORT_SEEK = 96.0` that is over `ESCORT_MIN_AWAY = 24.0` from `McaCompat.home(v)` (same dimension) and over 16 from `v` |
| `PARTNER` | a `Villager` within `KIN_NEAR = 64.0` with kin `PARTNER` |
| `KIN_GROWN` | a grown kin (not partner) within `KIN_NEAR` |
| `GRIEVING` | `v`'s `MEMORIES` hold a `KILLED_KIN` of a player other than you (named) |
| `CHILD` | `v.isBaby()` |
| `SAME_BLOOD` | `blood != null && blood == mine && !VillagerHeritage.common(blood)` |
| `RARE_BLOOD` | `blood != null && !common(blood) && blood != mine` |
| `PLAYER_RARE` | `mine != null && !common(mine) && (blood == null \|\| common(blood))` |
| `BLOOD(k)` | `blood == k` |
| `SAME_FAITH` | `faith != null && faith == sworn` |
| `OTHER_FAITH` | `faith != null && sworn != null && faith != sworn && faith != TRUTH` |
| `TRUTH_OLD` | `faith == TRUTH && sworn != null && sworn.seven` |
| `UNSWORN_DEVOUT` | `sworn == null && Temperaments.of(v) == DEVOUT` |
| `PRIEST` | `Accusations.priest(v)` |
| `AT_WAR` | `f != null && !Approaches.war(server, f).isEmpty()` |
| `RULED` | `f != null && f.leader().isPresent()` |
| `STRANGER` | `f != null`, no memories of you, not a member (`!f.members().containsKey(uuid)`) |
| `MEMBER` | `f != null && f.members().containsKey(player.getUUID())` |
| `TOWN` | `f != null` |
| `RAIN` | `level.isRaining() && !level.isThundering() && level.canSeeSky(v.blockPosition())` |
| `STORM` | `level.isThundering()` |
| `NIGHT` | `Vampires.night(ctx.clock)` |
| `MEM(kinds)` | they remember (not hearsay) you doing one of `kinds` |
| `REGULAR` | a TRADE memory with `count >= Recollections.REGULAR_TRADES` |
| `LENDER` | `trust >= LEND_TRUST`, `warmth >= LEND_WARMTH`, `Purse.count(player, EMERALD) < LOAN_POOR (4)`, no open REPAY at all |
| `CROOK_NEAR` | `trust >= SECRET_TRUST` and a crook near (3.5) |
| `NOT_OWED` | (added to every talk with a promise reply) no open promise to `v` |

### Job (16)

**1. `farmer_crop`**: JOB(farmer). Args: %2$s count, %3$s item (GOODS want).
- open: "The wheat's slow this year. Too little rain, then too much."
- a "I'll bring you what you need." → "Would you? %2$s %3$s would do, by the day after tomorrow." · +2/0/+3 · BRING_JOB(2)
- b "It'll come right. It always does." → "Easy said by someone who doesn't eat by it." · 0/0/+1
- c "Then work harder." → "Work harder. Right. I'll remember that." · -1/-1/-4

**2. `fisher_nets`**: JOB(fisherman). Args as 1.
- open: "My nets are more hole than net. The string won't last the week."
- a "I'll bring you some string." → "%2$s %3$s would mend them. By the day after tomorrow, then?" · +2/0/+3 · BRING_JOB(2)
- b "Fish with a rod, then." → "Tell that to six mouths at home." · 0/-1/-1
- c "Ask around. Someone will have some." → "Maybe. Kind of you to care." · 0/0/+1

**3. `shepherd_wolves`**: JOB(shepherd).
- open: "Wolves took a lamb last night. That's the third this month."
- a "I'll keep watch tonight, and come back tomorrow." → "You'd do that? Come and find me tomorrow and tell me what you saw." · +2/+1/+3 · COME_BACK(1)
- b "Wolves have to eat too." → "Not my lambs, they don't." · 0/-1/-2
- c "Here. For your loss." (EMERALDS 2) → "I... thank you. That's kind." · +2/0/+5 · PAY(2)

**4. `fletcher_feathers`**: JOB(fletcher). Args as 1.
- open: "A good arrow needs good feathers, and the chickens round here are plucked bald."
- a "I'll bring you feathers." → "%2$s %3$s, if you can. Within two days and I'll have my orders done." · +2/0/+3 · BRING_JOB(2)
- b "Use leaves." → "Leaves. You'd make a fine fletcher." · 0/-1/0
- c "Tell me about your craft." → "Not many ask. A straight shaft, true feathers, and patience. Thank you for asking." · 0/0/+3

**5. `librarian_pages`**: JOB(librarian).
- open: "Someone's torn pages out of my best book. If I find out who..."
- a "I'll keep an ear open." → "Do. And keep this between us." · +2/0/+1
- b "It wasn't me." → "I never said it was. Odd thing to say." · -2/0/0
- c "Books are only paper." → "Get out of my sight." · -2/-2/-5 · QUARREL

**6. `cartographer_edge`**: JOB(cartographer).
- open: "My maps stop where the roads do. What's past the hills, I couldn't tell you."
- a "I'll go and look, and come back to tell you." → "Would you? Come back within three days, before I forget I asked." · +1/+1/+2 · COME_BACK(3)
- b "Monsters, mostly." → "Ha! Then I'll draw dragons there." · 0/0/+2
- c "Why not go yourself?" → "My knees, friend. My knees." · 0/0/0

**7. `cleric_doubt`**: JOB(cleric).
- open: "Some days I wonder if anyone hears my prayers."
- a "Someone does." → "Thank you. I needed to hear that." · +1/0/+4 · KIND
- b "Then why pray?" → "Because the asking matters. I hope it does." · 0/+1/0
- c "No one does." → "You must be lonely, to think that." · 0/-2/-2

**8. `armorer_boast`**: JOB(armorer).
- open: "No blade in this town gets through my plate. Not one."
- a "I'd like to test that one day." → "Would you now? Bold. I like bold." · 0/+3/+1
- b "I've seen better." → "Have you. Where? Name the smith." · 0/-1/-3
- c "It's fine work." → "It is. Glad someone sees it." · 0/+1/+2

**9. `weaponsmith_coal`**: JOB(weaponsmith). Args as 1.
- open: "The forge eats coal faster than the miners can dig it."
- a "I'll bring you coal." → "%2$s %3$s would keep her lit two more days. Bring it by then?" · +2/0/+2 · BRING_JOB(2)
- b "Burn wood." → "And get soft steel? No." · 0/-1/0
- c "I'll find you a miner." → "If you do, send them my way." · 0/0/+1

**10. `toolsmith_apprentice`**: JOB(toolsmith).
- open: "My apprentice ran off to be a soldier. Fool boy."
- a "He'll be back." → "If he lives. Kind of you to say it." · 0/0/+2
- b "Good for him." → "Good for him. Bad for me." · 0/0/-1
- c "I'll ask the ruler to send him home." (RULER) → "You'd speak for me? Then do it soon. Within four days." · +2/+1/+2 · SPEAK_FOR(4)

**11. `butcher_tracks`**: JOB(butcher).
- open: "Something's been at my smokehouse at night. Big tracks, too."
- a "Give me some meat and I'll bait a trap." → "Here. Bring me its hide, if you get it." · +1/0/+1 · FAVOUR
- b "A bear, probably. Leave it be." → "Leave it be. While it eats my winter." · 0/-1/0
- c "I'll tell the guards." → "Aye. Tell them." · 0/0/+1

**12. `leatherworker_hides`**: JOB(leatherworker). Args as 1.
- open: "Rabbit hide. Soft as anything. I can't get enough of it."
- a "I'll bring you some." → "%2$s %3$s, within three days, and I'll make you something nice." · +2/0/+2 · BRING_JOB(3)
- b "Rabbits are hard to catch." → "That's why it's worth so much." · 0/0/0
- c "Is cow hide not good enough?" → "For boots. Not for gloves." · 0/0/+1

**13. `mason_wall`**: JOB(mason). Args as 1.
- open: "The town wall's cracking on the east side. Nobody listens to me."
- a "I'll speak to the ruler for you." (RULER) → "Would you? Within four days, before it falls on someone." · +2/+1/+2 · SPEAK_FOR(4)
- b "I'll bring you clay to patch it." → "%2$s %3$s, the day after tomorrow. That would hold it." · +2/0/+2 · BRING_JOB(2)
- c "Patch it yourself." → "With what? Clay doesn't grow on trees." · 0/-1/-2

**14. `nitwit_fly`**: JOB(nitwit).
- open: "I dreamed I could fly. Do you think people can fly?"
- a "With enough feathers, maybe." → "I'll start collecting!" · 0/0/+3
- b "No." → "Oh." · 0/0/-1
- c "Only in dreams." → "Then I'll sleep more." · 0/0/+1

**15. `guard_night`**: JOB(guard). Args: %2$s count, %3$s item (GOODS guard want: bread).
- open: "Long night on the wall. Quiet. I don't trust quiet."
- a "I'll walk the wall with you a while." → "Glad of the company." · +2/+2/+2
- b "Quiet's good." → "Quiet's when they come." · 0/0/0
- c "You look hungry. I'll bring you bread." → "%2$s %3$s by tomorrow would see me through. Thank you." · +1/0/+2 · BRING_JOB(1)

**16. `guard_taxes`**: JOB(guard) and RULED.
- open: "The ruler wants more coin from the farms again. Not my place to say so."
- a "Then I won't repeat it." → "Good." · +3/0/+1
- b "I'll tell the ruler you said that." → "You wouldn't." · -6/0/-4 · QUARREL
- c "The ruler knows best." → "Maybe. Maybe." · 0/+1/-1

### Family (8)

**17. `child_watch`**: PARENT, NOT_OWED. Args: %2$s the child's name.
- open: "%2$s runs off the moment I turn my back, and I've work to do."
- a "I'll look in on %2$s for the next two days." → "Would you? Bless you. Two days, and I'll owe you." · +2/0/+3 · WATCH_CHILD
- b "Children run. They come back." → "Not all of them do." · 0/0/-1
- c "Tie a bell on them." → "Ha! I might." · 0/0/+1

**18. `child_wandered`**: CHILD_AWAY, NOT_OWED, recent. Args: %2$s the child's name.
- open: "Have you seen %2$s? They wandered off, and it'll be dark before long."
- a "I'll bring %2$s home." (MCA) → "Please. Before it gets late." · +3/0/+4 · ESCORT_CHILD
- b "Children wander." → "Easy for you to say." · 0/0/-2
- c "%2$s is over that way." (CHILD_IN_SIGHT: the child within 48 of you) → "There! Oh, thank you." · +1/0/+2

**19. `partner_words`**: PARTNER. Args: %2$s the partner's name.
- open: "%2$s and I had words this morning. Over nothing, really."
- a "Go and make it up. Life's short." → "You're right. You're right." · +1/0/+3
- b "It's never nothing." → "No. It isn't, is it." · +2/+1/+1
- c "Leave them." → "I'd never!" · 0/-1/-3

**20. `kin_visit`**: KIN_GROWN. Args: %2$s that kin's name.
- open: "%2$s says I never come round. They live just over there."
- a "Then go. I'll wait." → "Maybe I will." · 0/0/+2
- b "Family always says that." → "Ha. True enough." · 0/0/+1
- c "I'd like to meet them." → "They'd like that. They like visitors more than me." · +1/0/+2

**21. `grief_kin`**: GRIEVING, once. Args: %2$s the dead kin's name (the memory's detail), %3$s the killer's name.
- open: "We buried %2$s last season. Some days I still set a place for them."
- a "I'm sorry. Tell me about them." → "They laughed too loud. I miss it." · +2/0/+4 · KIND
- b "Everyone loses someone." → "I know. It doesn't help." · 0/0/-1
- c "Who did it?" → "%3$s. If you see them, tell them I haven't forgotten." · +2/0/0

**22. `child_dreams`**: PARENT.
- open: "My little one wants to be an adventurer, like you. I'd sooner they were a baker."
- a "Let them dream." → "Dreams don't fill bellies. Still." · 0/0/+2
- b "Baking's a fine life." → "There! Someone sensible." · 0/+1/+2
- c "I'll teach them to use a sword." → "Over my dead body." · -1/-1/-3

**23. `kid_knight`**: CHILD.
- open: "Are you a knight? You look like a knight."
- a "I am." → "I knew it!" · 0/+2/+2
- b "Just a traveller." → "Travellers are nearly as good." · 0/0/+1
- c "Shh. It's a secret." → "I won't tell anyone! Ever!" · +2/0/+3

**24. `kid_dare`**: CHILD.
- open: "Bet you can't jump as high as the bell."
- a "Watch me." → "Whoa!" · 0/+2/+2
- b "Bet I can't." → "Grown-ups are boring." · 0/-1/0
- c "Go home. Your family will worry." → "You sound like my mum." · 0/0/-1

### Blood and heritage (5)

**25. `blood_same`**: SAME_BLOOD, once. Args: %2$s the blood (`heritage.jaymoskingdoms.<key>`).
- open: "You're %2$s, like me. There aren't many of us this far out."
- a "Then we look out for each other." → "Agreed. Come to me if you need anything." · +4/0/+4
- b "Blood's just blood." → "Spoken like someone who's never been spat at for it." · 0/0/-2
- c "Where does your family come from?" → "The old country. It's a long story. Another day." · +1/0/+2

**26. `blood_stares`**: RARE_BLOOD. Args: %2$s their blood.
- open: "People stare at me. Because I'm %2$s. Do you?"
- a "No." → "Liar. But a kind one." · +1/0/+2
- b "A little. It's new to me." → "Honest. I'll take honest." · +3/+1/+1
- c "Shouldn't I?" → "Forget I asked." · -1/0/-2

**27. `blood_yours`**: PLAYER_RARE, once. Args: %2$s your blood.
- open: "I've never met anyone %2$s. Is it true what they say?"
- a "Depends what they say." → "Ha! Fair." · 0/+1/+1
- b "Every word." → "Oh." · 0/+1/-1
- c "Ask me anything." → "Do you dream like we do? Never mind. Thank you." · +2/0/+2

**28. `blood_ember_cold`**: BLOOD(EMBERBORN), NOT_OWED. Args: %2$s 6, %3$s Coal.
- open: "The cold here gets into my bones. Back home the hearths never went out."
- a "I'll bring you coal for your fire." → "%2$s %3$s would do it. The day after tomorrow?" · +1/0/+3 · BRING(COAL, 6, 2)
- b "You'll get used to it." → "I won't." · 0/0/-1
- c "Why did you leave home?" → "Why does anyone? Work. Someone I loved." · +1/0/+1

**29. `blood_frost_heat`**: BLOOD(FROSTBORN).
- open: "Too warm here. Far too warm."
- a "Here, have some snow." (ITEM snowball 4) → "Ha! You'd be surprised how much that helps." · 0/0/+3 · GIVE(SNOWBALL, 4)
- b "Find some shade." → "There isn't enough shade in the world." · 0/0/+1
- c "Go back north, then." → "Maybe I will." · 0/0/-3

### Faith (5)

**30. `faith_same`**: SAME_FAITH. Args: %2$s the patron (`Patron.named()`).
- open: "I've seen you at the shrine. %2$s keeps us both."
- a "May they keep you well." → "And you." · +2/0/+3
- b "We'll see who keeps whom." → "Careful. They listen." · 0/-1/-1
- c "Pray for me." → "Every night, now." · +1/0/+2

**31. `faith_other`**: OTHER_FAITH. Args: %2$s your patron, %3$s theirs.
- open: "You keep %2$s, I hear. I keep %3$s. We'll not see eye to eye."
- a "We needn't. We can still be civil." → "Civil. Yes. That I can do." · +2/+2/+1
- b "Mine is the stronger." → "Is it." · 0/-1/-3
- c "Tell me about yours." → "You'd listen? Strange. Sit, then." · +2/+1/+3

**32. `faith_truth`**: TRUTH_OLD. Args: %2$s your patron.
- open: "The Truth needs no shrine. Why kneel to %2$s?"
- a "They answer me." → "So you say." · 0/+1/0
- b "Why does it matter to you?" → "Because I'd hate to see you lied to." · +1/0/+1
- c "Mind your own business." → "As you like." · 0/0/-2

**33. `faith_unsworn`**: UNSWORN_DEVOUT, NOT_OWED.
- open: "You don't pray, do you? I can always tell."
- a "Not yet." → "Not yet isn't never." · 0/0/+1
- b "I keep my own counsel." → "Fair enough." · 0/+1/0
- c "Teach me." → "Gladly! Come and find me tomorrow." · +1/0/+3 · COME_BACK(1)

**34. `faith_priest`**: PRIEST.
- open: "A soul carries its wrongs like stones in a pocket. Have you any to set down?"
- a "A few." → "Then the first step's taken." · +2/0/+1
- b "None." → "Then walk lightly." · 0/0/+1 (if `Recollections.leaning(v, player) < 0`, the answer is `.b.said_wronged` "Then you've a short memory." and the deltas are -1/0/-1)
- c "That's my business." → "So it is." · 0/0/0

### Realm (6)

**35. `realm_war`**: AT_WAR, NOT_OWED. Args: %2$s the enemy (first of `Approaches.war`).
- open: "We're at war with %2$s, they say. My son's of age. What do I do?"
- a "Keep him home. Wars end." → "If only the ruler thought so." · +1/0/+3
- b "Let him fight. It's his duty." → "Easy to say when it's not your son." · 0/+1/-2
- c "I'll ask the ruler to spare him." (RULER) → "You'd do that? Please. Within four days, before they march." · +2/+1/+3 · SPEAK_FOR(4)

**36. `realm_ruler`**: RULED.
- open: "Have you met our ruler? What are they really like?"
- a "Fair, I think." → "Good. That's good to hear." · +1/0/+1
- b "I wouldn't trust them." → "Careful who hears you say that." · +2/0/-1
- c "Not my place to say." → "Wise." · 0/+1/0

**37. `realm_stranger`**: STRANGER.
- open: "You're not from round here. What brings you?"
- a "Trade." → "Then you'll want the market." · +1/0/+1
- b "Work, if there is any." → "Ask at the bell. There's always something." · 0/+1/+1
- c "None of your business." → "Then I'll keep an eye on you." · -3/0/-2

**38. `realm_sworn`**: MEMBER, once.
- open: "You're one of ours now. Funny. I remember when you were a stranger."
- a "This is home now." → "Then welcome home." · +3/0/+4
- b "Only while it pays." → "Ah. A sellsword, then." · -2/-1/-2
- c "I still feel like one." → "You'll stop, one day." · +1/0/+2

**39. `realm_taxes`**: RULED and JOB(farmer|nitwit|shepherd|fisherman), NOT_OWED. Args: %2$s 6, %3$s Bread.
- open: "Taxes again. The ruler's men took half my stores."
- a "I'll bring you something to tide you over." → "%2$s %3$s, the day after tomorrow? Bless you." · +2/0/+3 · BRING(BREAD, 6, 2)
- b "The realm needs it more." → "Does it." · 0/+1/-3
- c "Hide the rest." → "Shh! I already have." · +3/0/+1

**40. `realm_roads`**: TOWN, NOT_OWED.
- open: "The roads aren't what they were. My cousin's late back from market."
- a "I'll look out for them, and come back to tell you." → "Would you? Come back within two days." · +1/+1/+2 · COME_BACK(2)
- b "They'll turn up." → "I hope so." · 0/0/0
- c "The roads are fine." → "For someone with a sword, maybe." · 0/-1/-1

### Recent events and your deeds (10)

**41. `recent_rain`**: RAIN, recent.
- open: "Wet enough for you?"
- a "Good for the crops." → "That's what I keep telling myself." · 0/0/+1
- b "I like the rain." → "You're odd. I like odd." · 0/0/+2
- c "Get inside. You'll catch a cold." → "Yes, mother." · 0/0/+1

**42. `recent_storm`**: STORM, recent.
- open: "Lightning struck the old tree last night. That's an omen, that is."
- a "An omen of what?" → "Lean in. I'll tell you what I've heard." · 0/0/+1 · RUMOUR
- b "It's only weather." → "That's what they said last time." · 0/0/0
- c "A good omen, surely." → "From your lips." · 0/0/+2

**43. `recent_night`**: NIGHT, recent.
- open: "Shouldn't you be indoors? Things walk at night."
- a "So should you." → "Can't sleep." · 0/0/+1
- b "I'm what walks at night." → "Don't joke like that." · -1/+1/-1
- c "I'll walk you home." → "That's kind. Thank you." · +2/0/+3

**44. `recent_crime`**: MEM(CRIME, ROBBED), recent, NOT_OWED. Args: %2$s the crime (the memory's detail).
- open: "I saw you at %2$s. I've not forgotten it."
- a "It won't happen again. I swear it." → "Then keep the peace here for five days, and I'll believe you." · +1/0/0 · KEEP_PEACE(5)
- b "Tell who you like." → "Maybe I will." · -3/-1/-2
- c "Here. For your silence." (EMERALDS 4) → "I saw nothing." · +1/-2/0 · BRIBE(4)

**45. `recent_saved`**: MEM(SAVED, CARRIED, GOT_UP, SHIELDED, HOME, FREED), recent.
- open: "I never thanked you properly. For that day."
- a "No need." → "There's every need. I owe you." · +2/+1/+3
- b "You can pay me back some day." → "I suppose I can." · 0/0/-1
- c "Just stay safe." → "I'll try. For you." · +1/0/+3

**46. `recent_struck`**: MEM(STRUCK), recent, NOT_OWED.
- open: "You hit me. I've not forgotten."
- a "I'm sorry. I was wrong." → "Words are cheap. But they're something." · +3/0/+2
- b "You had it coming." → "Did I." · -2/0/-4 · QUARREL
- c "It won't happen again." → "Then keep your hands to yourself here for a week." · +1/0/+1 · KEEP_PEACE(7)

**47. `recent_gift`**: MEM(GIFT), recent.
- open: "That gift you gave me. Why?"
- a "Because I wanted to." → "Then thank you." · +1/0/+3
- b "I want something from you." → "Ah. Of course you do." · -2/0/-3
- c "To say thank you." → "For what? Never mind. Thank you back." · +1/0/+2

**48. `recent_quest`**: MEM(QUEST_DONE), recent.
- open: "You did what I asked, and more. Most wouldn't."
- a "Anything for a friend." → "A friend. I like that." · +2/0/+3
- b "I did it for the pay." → "Honest, at least." · +1/+1/-1
- c "Got anything else?" → "Maybe. Ask me again tomorrow." · 0/+1/0

**49. `recent_regular`**: REGULAR, recent.
- open: "You're round here more often than my own family."
- a "You've the best prices." → "Flattery. It works, too. Here." · 0/0/+2 · FAVOUR
- b "Your wares aren't bad." → "Not bad! High praise." · 0/0/+1
- c "I'll find a cheaper stall." → "Do. See if I care." · 0/0/-3

**50. `recent_rude`**: MEM(RUDE, REBUFFED), recent.
- open: "You were short with me last time."
- a "I was tired. Forgive me." → "Forgiven. This once." · +1/0/+2
- b "You talk too much." → "And you say too little worth hearing." · -1/0/-4 · QUARREL
- c "Was I? I hadn't noticed." → "That's worse." · -1/0/-2

### Trust (2)

**51. `money_loan`**: LENDER, NOT_OWED. Args: %2$s 6.
- open: "You look short of coin. I could lend you a little."
- a "I'd be grateful. I'll pay you back in three days." → "%2$s emeralds, then. Three days. I'm trusting you." · +1/0/+1 · LOAN
- b "I manage." → "Proud. Fair enough." · 0/+1/0
- c "Keep your charity." → "Suit yourself." · 0/0/-2

**52. `secret_crook`**: CROOK_NEAR.
- open: "Can I tell you something? Just between us?"
- a "Of course." → "Then listen." · +2/0/+1 · SECRET
- b "I'd rather not know." → "Suit yourself." · 0/0/0
- c "Will it cost me?" → "Not everything has a price." · 0/0/-1

### Job goods (`TalkBook.GOODS`, by job: want item and count / favour item and count)

| Job | Want | Favour |
|---|---|---|
| farmer | BONE_MEAL 8 | BREAD 3 |
| fisherman | STRING 4 | COOKED_COD 3 |
| shepherd | WHEAT 10 | WHITE_WOOL 4 |
| fletcher | FEATHER 8 | ARROW 8 |
| librarian | INK_SAC 3 | BOOK 1 |
| cartographer | PAPER 6 | MAP 1 |
| cleric | CANDLE 2 | EXPERIENCE_BOTTLE 2 |
| armorer | IRON_INGOT 4 | SHIELD 1 |
| weaponsmith | COAL 8 | IRON_NUGGET 9 |
| toolsmith | IRON_INGOT 2 | IRON_SHOVEL 1 |
| butcher | OAK_LOG 8 | COOKED_PORKCHOP 3 |
| leatherworker | RABBIT_HIDE 4 | LEATHER 3 |
| mason | CLAY_BALL 8 | BRICK 8 |
| guard | BREAD 4 | TORCH 8 |
| nitwit | APPLE 3 | POPPY 1 |
| anything else | BREAD 4 | APPLE 2 |

Item counts in lines come from here (`%2$s`), names from `ItemStack.getHoverName()` (`%3$s`).

## 6. Where each new piece hooks in

| Existing method | New call | Why |
|---|---|---|
| `Recollections.remember` (private overload, after merge) | `Bearings.deed(who, id, kind, hearsay)` | every deed moves bearings |
| `Recollections.remember` (public, end) | `Promises.wronged(player, who, kind)` | KEEP_PEACE |
| `Recollections.refuses` | `\|\| Bearings.cold(who, player)` | refuses to talk |
| `Recollections.gossip` | trust gate and chance factor; body moved to `gossipLine` | rumours |
| `Recollections.show` | bearing line | ops testing |
| `Approaches.answer` (first line) | `Talks.answer(player, answer)` | talk screen answers |
| `Approaches.prepare` BARGAIN | `\|\| Bearings.fond(a.mob, player)` | friend discount |
| `VillagerPriceMixin.jaymoskingdoms$purist` | `Bearings.prices(villager, player)` | prices |
| `Theft.witnesses` | `removeIf(Bearings.shields)` | hiding you |
| `KingdomEvents.audience(player, folk, factionId)` | `Promises.audience(player, factionId)` | SPEAK_FOR |
| `ComingOver.reason` | `Reason.PROMISE` pick | reminders |
| `ChatPrompts` (after the lean line) | `Bearings.prompt(villager, player)` | AI chat hints |
| `SharedHistory.WRONGS` | `+ BROKE_WORD` | apologising for it |
| `Journal.open` | `Promises.journal(player)` | the promises list |
| `InteractActions.offers` / `act` | `PROMISE` | Keep Your Word |
| `McaDialogueMixin.jaymoskingdoms$homeTalk` | `Talks.ANSWER` branch, Chat chance | entry points |
| `McaAnswersMixin.jaymoskingdoms$tail` | hide `Talks.ANSWER` | entry point gate |
| `JaymosKingdoms.onInitialize` (after `SharedHistory.register()`) | `LOGGER.debug("promise attachment: {}", Promises.PROMISES); Bearings.register(); Talks.register(); Promises.register();` | loading |

`Talks.register()`: `ServerLifecycleEvents.SERVER_STOPPED` clears `OPEN`; `ServerPlayConnectionEvents.DISCONNECT`
removes the player's entry. `Promises.register()`: the tick, death, damage and disconnect listeners.
`Bearings.register()`: the fight listener. None registers a payload (TalkNet's are already registered by Approaches).

**Patrons and blessings.** No new blessing. Two patron deeds through the existing `Patrons.deed(Player, Patron, int, String)`:
a kept promise is a deed of The Truth (`DEED_MID`, "kept_word"); a child kept safe or brought home is also a deed of
the Hearthkeeper (`DEED_MID`, "kept_child"). The existing `Blessings.unseen` check stays first in `Theft.witnesses`,
so the Ash Whisperer's blessing still empties the list before bearings are asked.

## 7. Constants (all `static final`, top of each class)

`Bearings`: `LEAST = -100, MOST = 100; DRIFT_DAYS_TRUST = 4, DRIFT_DAYS_RESPECT = 4, DRIFT_DAYS_WARMTH = 2;
HEARSAY_DIV = 2; REP_PER_RESPECT = 40, REP_RESPECT_MOST = 20; HEARTS_PER_WARMTH = 5, HEARTS_WARMTH_MOST = 20;
FOND_WARMTH = 50, FOND_TRUST = 30, WARM_WARMTH = 20, TRUSTING_TRUST = 40, ADMIRING_RESPECT = 40, WARY_TRUST = -20,
COLD_WARMTH = -20, SCORN_RESPECT = -30; REFUSE_WARMTH = -50, REFUSE_TRUST = -60; GUARDED_TRUST = -15, CONFIDE_TRUST = 30,
CONFIDE_GOSSIP = 2.0F; RUMOUR_TRUST = 15, SECRET_TRUST = 35, LEND_TRUST = 35, LEND_WARMTH = 20, DOUBT_TRUST = -25,
FAVOUR_WARMTH = 10; SHIELD_TRUST = 50, SHIELD_WARMTH = 40; STAND_TRUST = 20, STAND_RESPECT = 30, STAND_RANGE = 16.0,
STAND_GAP = 40L, STAND_SAY_GAP = 600L, STAND_LINES = 3, WARN_LINES = 3; PRICE_SHARE_MOST = 0.2F, PRICE_LEAST_SWAY = 0.15F;
PROUD_RESPECT = 1.5F, SUSPICIOUS_TRUST_GAIN = 0.5F, GENTLE_WARMTH_LOSS = 0.5F, SPITEFUL_LOSS = 1.5F,
HOTHEADED_WARMTH_LOSS = 1.5F; MANNER_LINES = 3`.

`Talks`: `ANSWER = "jaymoskingdoms_word"; OPEN_TICKS = 1200L; TALK_ON_CHAT_CHANCE = 0.3F; TALKS_PER_DAY = 1;
TALK_REPEAT_DAYS = 24; TALKS_KEPT = 24; WEIGHT_RECENT = 3, WEIGHT_LENDER = 2, WEIGHT_SECRET = 2, WEIGHT_LIFE = 1;
TALK_HEARTS_AT = 3, TALK_WARM_HEARTS = 2, TALK_COLD_HEARTS = 2; QUARREL_DAYS = 1, QUARREL_HEARTS = 5;
FAVOUR_GAP_DAYS = 3; LOAN_EMERALDS = 6, LOAN_DAYS = 3, LOAN_POOR = 4; RUMOUR_RANGE = 64.0, CHILD_RANGE = 32.0,
KIN_NEAR = 64.0, ESCORT_MIN_AWAY = 24.0, ESCORT_SEEK = 96.0, CHILD_IN_SIGHT = 48.0; BROKEN_TALK_DAYS = 7;
NONE_LINES = 3, SULK_LINES = 3`.

`Promises`: `MAX_OPEN = 5, PER_VILLAGER = 1; TICK_EVERY = 20; COME_BACK_NEAR = 8.0, WATCH_NEAR = 16.0, WATCH_DAYS = 2;
ESCORT_TICKS = 6000L, ESCORT_HOME_NEAR = 8.0; PEACE_RANGE = 96.0; SETTLE_SAY_RANGE = 16.0, SETTLE_LIMIT_DAYS = 30;
KEPT_HEARTS = 10, BROKEN_HEARTS = 15, RELEASED_HEARTS = 2, KIN_KEPT_HEARTS = 3, KIN_BROKEN_HEARTS = 5; KEPT_REP = 5,
BROKEN_REP = 10; RELEASE_TRUST = -8, RELEASE_WARMTH = -2; CHILD_HURT_WARMTH = -5; LINES = 2`.

`Recollections.Kind` additions (weight, days, remark, tellable, prompt, heard prompt):
- `PROMISED(0, 30, false, false, "%1$s promised you they would %2$s.", "")`: recorded at `make` (for the AI prompt only)
- `KEPT_WORD(4, 120, true, true, "%1$s kept their promise to you (to %2$s).", "%1$s kept their promise to %3$s (to %2$s).")`
- `BROKE_WORD(-6, 180, true, true, "%1$s broke their promise to you (to %2$s).", "%1$s broke their promise to %3$s (to %2$s).")`

`ComingOver.Reason.PROMISE("promise", 9)`. `StoryHoldScreen.HINTS = 95`.

## 8. Lang (en_us.json; the talk text is in section 5)

```
"dialogue.main.jaymoskingdoms_word": "Have a word",
"gui.button.act.promise": "Keep Your Word",

"talk.jaymoskingdoms.word.doubt": "%s (they won't take your word)",
"talk.jaymoskingdoms.word.too_many": "%s (you've promised too much already)",
"talk.jaymoskingdoms.word.none.0": "Nothing much to say today, %1$s.",
"talk.jaymoskingdoms.word.none.1": "Ask me another time.",
"talk.jaymoskingdoms.word.none.2": "Can't think of a thing. Busy day.",
"talk.jaymoskingdoms.word.sulk.0": "Not now, %1$s. Not after that.",
"talk.jaymoskingdoms.word.sulk.1": "I've said all I mean to say to you today.",
"talk.jaymoskingdoms.word.sulk.2": "Leave it, %1$s. Tomorrow, maybe.",

"talk.jaymoskingdoms.word.manner.fond.0": "%1$s's face lights up when they see you.",
"talk.jaymoskingdoms.word.manner.fond.1": "%1$s smiles and steps closer.",
"talk.jaymoskingdoms.word.manner.fond.2": "%1$s greets you like an old friend.",
"talk.jaymoskingdoms.word.manner.warm.0": "%1$s gives you a friendly nod.",
"talk.jaymoskingdoms.word.manner.warm.1": "%1$s smiles as you come over.",
"talk.jaymoskingdoms.word.manner.warm.2": "%1$s seems glad of the company.",
"talk.jaymoskingdoms.word.manner.trusting.0": "%1$s talks to you openly, without a glance round.",
"talk.jaymoskingdoms.word.manner.trusting.1": "%1$s lowers their voice, as if to a friend.",
"talk.jaymoskingdoms.word.manner.trusting.2": "%1$s doesn't hesitate to talk to you.",
"talk.jaymoskingdoms.word.manner.admiring.0": "%1$s straightens up as you come over.",
"talk.jaymoskingdoms.word.manner.admiring.1": "%1$s looks at you with something like respect.",
"talk.jaymoskingdoms.word.manner.admiring.2": "%1$s nods to you, a little formally.",
"talk.jaymoskingdoms.word.manner.wary.0": "%1$s keeps a hand on their purse.",
"talk.jaymoskingdoms.word.manner.wary.1": "%1$s watches you carefully as they speak.",
"talk.jaymoskingdoms.word.manner.wary.2": "%1$s takes half a step back.",
"talk.jaymoskingdoms.word.manner.cold.0": "%1$s barely looks at you.",
"talk.jaymoskingdoms.word.manner.cold.1": "%1$s answers without any warmth.",
"talk.jaymoskingdoms.word.manner.cold.2": "%1$s folds their arms.",
"talk.jaymoskingdoms.word.manner.scornful.0": "%1$s looks you up and down.",
"talk.jaymoskingdoms.word.manner.scornful.1": "%1$s doesn't bother to hide a sneer.",
"talk.jaymoskingdoms.word.manner.scornful.2": "%1$s talks to you like a child.",
"talk.jaymoskingdoms.word.manner.plain.0": "%1$s turns to you.",
"talk.jaymoskingdoms.word.manner.plain.1": "%1$s looks up from their work.",
"talk.jaymoskingdoms.word.manner.plain.2": "%1$s waits for you to speak.",

"talk.jaymoskingdoms.word.rumour.clam.0": "I'd rather not say. Not to you.",
"talk.jaymoskingdoms.word.rumour.clam.1": "Ask someone else.",
"talk.jaymoskingdoms.word.rumour.crook": "Keep an eye on %2$s. Light fingers, that one. You didn't hear it from me.",
"talk.jaymoskingdoms.word.rumour.war": "Word is %2$s are raising men. More than last time.",
"talk.jaymoskingdoms.word.rumour.none.0": "Nothing worth telling. It's been quiet.",
"talk.jaymoskingdoms.word.rumour.none.1": "I've heard nothing, and I hear most things.",
"talk.jaymoskingdoms.word.favour.given": "Take these. Don't argue.",
"talk.jaymoskingdoms.word.favour.none": "I've nothing to spare just now. Sorry.",
"talk.jaymoskingdoms.word.stand.0": "I'm with you!",
"talk.jaymoskingdoms.word.stand.1": "Hold on, I'm coming!",
"talk.jaymoskingdoms.word.stand.2": "Not on my watch!",
"talk.jaymoskingdoms.word.warn.0": "Behind you!",
"talk.jaymoskingdoms.word.warn.1": "Look out!",
"talk.jaymoskingdoms.word.warn.2": "Watch yourself!",
"talk.jaymoskingdoms.word.faith_priest.b.said_wronged": "Then you've a short memory.",

"talk.jaymoskingdoms.word.promise_due.open": "Well? Have you brought it?",
"talk.jaymoskingdoms.word.promise_due.a": "Here it is.",
"talk.jaymoskingdoms.word.promise_due.b": "Not yet. Soon.",
"talk.jaymoskingdoms.word.promise_due.b.said": "Don't forget, %1$s.",
"talk.jaymoskingdoms.word.promise_due.c": "I can't. I'm sorry.",
"talk.jaymoskingdoms.word.promise_broken.open": "You gave me your word. You didn't keep it.",
"talk.jaymoskingdoms.word.promise_broken.a": "I'm sorry. I'll make it right.",
"talk.jaymoskingdoms.word.promise_broken.a.said": "We'll see.",
"talk.jaymoskingdoms.word.promise_broken.b": "Things came up.",
"talk.jaymoskingdoms.word.promise_broken.b.said": "They always do, with you.",
"talk.jaymoskingdoms.word.promise_broken.c": "I never promised you anything.",
"talk.jaymoskingdoms.word.promise_broken.c.said": "...Get away from me.",

"promise.jaymoskingdoms.what.bring": "bring me %s %s",
"promise.jaymoskingdoms.what.repay": "pay back the %s emeralds I lent you",
"promise.jaymoskingdoms.what.come_back": "come back",
"promise.jaymoskingdoms.what.watch_child": "look after my child",
"promise.jaymoskingdoms.what.escort_child": "walk my child home",
"promise.jaymoskingdoms.what.speak_for": "speak for me to the ruler",
"promise.jaymoskingdoms.what.keep_peace": "keep the peace here",
"promise.jaymoskingdoms.handed.bring.0": "You remembered! Thank you, %1$s.",
"promise.jaymoskingdoms.handed.bring.1": "Just what I needed. You're as good as your word.",
"promise.jaymoskingdoms.handed.repay.0": "Paid in full, and on time. I'd lend to you again, %1$s.",
"promise.jaymoskingdoms.handed.repay.1": "Honest as the day. Thank you.",
"promise.jaymoskingdoms.kept.come_back.0": "You came back! I wasn't sure you would.",
"promise.jaymoskingdoms.kept.come_back.1": "There you are, %1$s. Just as you said.",
"promise.jaymoskingdoms.kept.watch_child.0": "My little one's safe and sound. I owe you, %1$s.",
"promise.jaymoskingdoms.kept.watch_child.1": "They haven't stopped talking about you, %1$s. Thank you for watching them.",
"promise.jaymoskingdoms.kept.escort_child.0": "You found them! Oh, thank you, %1$s.",
"promise.jaymoskingdoms.kept.escort_child.1": "Home safe. I can breathe again.",
"promise.jaymoskingdoms.kept.speak_for.0": "I heard you spoke to the ruler for me. Thank you, %1$s.",
"promise.jaymoskingdoms.kept.speak_for.1": "Word came that you put in a word for me. That means something.",
"promise.jaymoskingdoms.kept.keep_peace.0": "You kept the peace, %1$s. I'll not forget it.",
"promise.jaymoskingdoms.kept.keep_peace.1": "Not a hand raised. You're as good as your word.",
"promise.jaymoskingdoms.broken.bring.0": "You said you'd %2$s. I waited, %1$s.",
"promise.jaymoskingdoms.broken.bring.1": "Never mind what you promised. I'll know better next time.",
"promise.jaymoskingdoms.broken.repay.0": "Where's my money, %1$s?",
"promise.jaymoskingdoms.broken.repay.1": "I lent you coin in good faith. Never again.",
"promise.jaymoskingdoms.broken.come_back.0": "You said you'd come back. You didn't.",
"promise.jaymoskingdoms.broken.come_back.1": "I waited for you, %1$s. All day.",
"promise.jaymoskingdoms.broken.watch_child.0": "You said you'd look after my child. Where were you?",
"promise.jaymoskingdoms.broken.watch_child.1": "I trusted you with my little one, %1$s.",
"promise.jaymoskingdoms.broken.escort_child.0": "I had to go and fetch them myself in the end.",
"promise.jaymoskingdoms.broken.escort_child.1": "You said you'd bring them home, %1$s.",
"promise.jaymoskingdoms.broken.child_lost.0": "You said you'd keep them safe. You said.",
"promise.jaymoskingdoms.broken.child_lost.1": "Don't. Don't speak to me, %1$s.",
"promise.jaymoskingdoms.broken.speak_for.0": "You never spoke to the ruler for me, did you?",
"promise.jaymoskingdoms.broken.speak_for.1": "So much for putting in a word, %1$s.",
"promise.jaymoskingdoms.broken.keep_peace.0": "You swore you'd keep the peace. Look what you did.",
"promise.jaymoskingdoms.broken.keep_peace.1": "Your word's worth nothing here now, %1$s.",
"promise.jaymoskingdoms.released.0": "Well. Thank you for telling me, at least.",
"promise.jaymoskingdoms.released.1": "I'd hoped... never mind. Better you say so.",
"promise.jaymoskingdoms.child_hurt": "My little one came home with a cut, %1$s. You said you'd watch them.",

"promise.jaymoskingdoms.journal.title": "A promise to %s",
"promise.jaymoskingdoms.journal.giver": "%s of %s",
"promise.jaymoskingdoms.journal.giver.alone": "%s",
"promise.jaymoskingdoms.journal.bring": "Bring %2$s %3$s to %1$s.",
"promise.jaymoskingdoms.journal.repay": "Pay %1$s back the %2$s emeralds they lent you.",
"promise.jaymoskingdoms.journal.come_back": "Go back and see %1$s.",
"promise.jaymoskingdoms.journal.watch_child": "Look in on %2$s, %1$s's child, once each day, and keep them safe.",
"promise.jaymoskingdoms.journal.escort_child": "Walk %2$s home to %1$s before it gets late.",
"promise.jaymoskingdoms.journal.speak_for": "Speak for %1$s to the ruler of %2$s: seek an audience.",
"promise.jaymoskingdoms.journal.keep_peace": "Raise no hand and do no crime in %2$s.",
"promise.jaymoskingdoms.journal.due.today": "Due by dawn",
"promise.jaymoskingdoms.journal.due.days": "%s days left",
"promise.jaymoskingdoms.journal.due.soon": "Soon, before it gets late",
"promise.jaymoskingdoms.journal.visited": "looked in on today",
"promise.jaymoskingdoms.journal.not_visited": "not looked in on today",
"promise.jaymoskingdoms.journal.both": "%s, %s",
"promise.jaymoskingdoms.journal.kept": "Kept. They'll hear of it.",
"promise.jaymoskingdoms.journal.broken": "Broken. They'll remember.",

"come.jaymoskingdoms.hail.promise.0": "There you are!",
"come.jaymoskingdoms.hail.promise.1": "A word, if you please!",
"come.jaymoskingdoms.promise.0": "You've not forgotten? You promised to %1$s. Today's the day.",
"come.jaymoskingdoms.promise.1": "Just making sure. You did say you'd %1$s.",
"come.jaymoskingdoms.promise.2": "I'm holding you to it, you know. You said you'd %1$s.",
"come.jaymoskingdoms.why.promise.0": "Your promise. To %1$s. I wanted to be sure you remembered.",
"come.jaymoskingdoms.why.promise.1": "You gave me your word. I'm only reminding you.",
"come.jaymoskingdoms.gist.promise": "(They hold up a hand, then point at you and back at themselves: you owe them something today.)",

"recall.jaymoskingdoms.kept_word.0": "You kept your word to me, %1$s. Not many do.",
"recall.jaymoskingdoms.kept_word.1": "I still think of how you kept your promise.",
"recall.jaymoskingdoms.kept_word.2": "You said you would, and you did. I won't forget it.",
"recall.jaymoskingdoms.heard.kept_word.0": "%3$s says you kept your word to them, %1$s. Good to know.",
"recall.jaymoskingdoms.heard.kept_word.1": "They say you're as good as your word. %3$s told me.",
"recall.jaymoskingdoms.about.kept_word.0": "%1$s made me a promise, and kept it.",
"recall.jaymoskingdoms.about.kept_word.1": "If %1$s gives you their word, you can count on it.",
"recall.jaymoskingdoms.broke_word.0": "You promised to %2$s, %1$s. You didn't.",
"recall.jaymoskingdoms.broke_word.1": "I remember what you promised me. I remember you didn't do it.",
"recall.jaymoskingdoms.broke_word.2": "Your word, %1$s? I've had your word before.",
"recall.jaymoskingdoms.heard.broke_word.0": "%3$s told me you broke your word to them, %1$s.",
"recall.jaymoskingdoms.heard.broke_word.1": "You're the one who let %3$s down. Don't make me any promises.",
"recall.jaymoskingdoms.about.broke_word.0": "%1$s gave me their word once. Once.",
"recall.jaymoskingdoms.about.broke_word.1": "Don't believe anything %1$s promises you.",
"recall.jaymoskingdoms.command.bearing": "Trust %s (%s), respect %s (%s), warmth %s (%s): stored (effective)",
"history.jaymoskingdoms.for.broke_word": "breaking your word to them",

"gui.jaymoskingdoms.story.hint.94": "Villagers keep their own counsel about you. How they talk to you, what they tell you and what they charge all follow from what you've done and said.",
"gui.jaymoskingdoms.story.hint.95": "A promise to a villager goes in your journal. Keep it and their family warms to you. Break it and they all hear."
```

The editor checks that every `recall.*`, `history.*` or `come.*` pattern the existing code builds for a new Kind or
Reason has a key (`Recollections.line` uses `REMARKS = 3`, `HEARD_REMARKS = 2`, `ABOUT_REMARKS = 2`). `PROMISED` has
`remark = false` and so needs no recall lines. Approaches' gossip ("about") lines only need `weight >= TELL_WEIGHT`:
`BROKE_WORD` (-6) and `KEPT_WORD` (4) both qualify, and both have "about" lines above.

## 9. Order of work (one track, one editor; kingdoms only)

1. `Recollections` (Bearing, Person, bear, Kinds, rememberHeard, gossipLine, show) and `Bearings` (reading, moving,
   DEEDS, temper, manner, prompt, behaviour checks, fight listener). Hook prices, gossip, refuses, witnesses,
   ChatPrompts, the Approaches discount. Build.
2. `Promises` (record, attachment, make, tick, settle, audience, wronged, journal, handOver), the Journal, the Interact
   action, ComingOver, SharedHistory and KingdomEvents hooks, and the class-load line. Build.
3. `TalkBook` and `Talks`, `dialogues/main.json`, both MCA mixins, `Approaches.answer`. Build.
4. Lang (sections 5 and 8), hint count, versions, MODLOGs. `cd kingdoms && ./gradlew build`, then the root
   `./gradlew build` (it bundles the Kingdoms jar by version). No dev client or server runs unless Andy asks.

Check against the 26.3 and MCA 7.8.0 sources before use: `Villager.getVillagerData().profession()`,
`MerchantOffer.addToSpecialPriceDiff`, `ServerLevel.getEntity(UUID)`, `Level.isThundering`,
`ServerLivingEntityEvents.AFTER_DEATH` and `AFTER_DAMAGE`, `net.minecraft.world.entity.monster.Enemy`,
`Faction.members()`, `Faction.leader()`, and MCA's follow (`McaBridge.follow`) on a child villager.

## 10. Risks and decisions taken

- **Collisions on the talk screen.** `TalkNet.Answer` has no session id. `Talks.answer` goes first and claims an
  answer only for its own unexpired entry with the same entity id. The other handlers (Careers, ZFighterFolk,
  ShipTalk, Skills, Pardon, Approaches) work as before. Talks never opens while `Approaches.talking(v)`.
- **Double counting.** Deeds move both the existing memory leaning and the new bearing. That's intended: leaning is
  "what they remember", bearing is "how they feel". Only the refusal check reads both.
- **Hidden means hidden.** Bearing and promises are never synced. The only places a value turns into text are the
  manner line, the greyed "(they won't take your word)", the chat AI prompt (in words) and the ops-only command.
- **"Have a word" lives in MCA's talk menu**, not on the Interact page. Since 0.7.454 the Interact page holds
  actions; it gets the hand-over (Keep Your Word).
- **Children.** ESCORT_CHILD relies on MCA's follow working on a child. If it doesn't in play, the reply's `need`
  (MCA) can be tightened to hide it, and WATCH_CHILD (no follow) still covers "look after my child".
- **Greenskins and folk** get no talks in this patch (no dialect variants written). Their bearings still move from
  deeds and still change prices (folk have no offers), gossip, fights and witnesses.
- **Unloaded villagers.** The villager's side of a kept or broken promise waits until they're loaded again (up to
  `SETTLE_LIMIT_DAYS`). The player's record changes at once, so the journal is right straight away.
- **Patch 9.** Quests can call `Promises.make` with their own terms. Nothing in Promises reads or writes `Quests.LOG`.

## 11. Versions, hints and MODLOG

- `kingdoms/gradle.properties`: `version=0.7.523`. Root `gradle.properties`: `version=0.20.25`.
- Loading hints 94 and 95 (above); `StoryHoldScreen.HINTS = 95` (master has 93).

`kingdoms/MODLOG.md`, appended:

```
## 0.7.523: talks that matter (pack 0.20.25)
Our own take on the idea of MCA: Conversations (none of its code). Plan: `docs/Talks-Plan.md`.
- **Trust, respect and warmth** (`Bearings`): every villager now holds three hidden feelings about each player, kept
  on their memory of you (`Recollections.Person.bearing`; a masked stranger's go to the costume). Everything they
  already remember moves them (gifts, trades, help, rescues, crimes seen, blows, bouts, quests, apologies, promises),
  word passed on moves them half as much, and they fade slowly. MCA hearts and realm reputation colour them. They're
  never shown: you see them in how villagers act. Fond ones charge less and sour ones more (up to a fifth), the
  trusting gossip more and tell you secrets, the wary say nothing, a guard who trusts and respects you comes to your
  side when a monster hits you (others shout a warning), one who's fond of you looks away from your crimes, and one
  who can't stand you won't talk at all. The chat AI is told in words.
- **Have a word** (MCA talk menu, and now and then Chat): a short talk on the talk screen with two or three replies.
  52 talks about their work, family, blood, faith, realm, the weather and what you've done. Each reply has their
  answer and shifts how they feel. Some lead to a promise, a favour, a rumour or a secret, a loan, or a quarrel (no
  more talk until tomorrow). An opening line hints at how they look at you.
- **Promises** (`Promises`, kept with the player): bring something by a day, come back, look in on a child for two
  days, walk a wandering child home, speak for them to the ruler (an audience does it), keep the peace in their
  town, pay back a loan. They're listed in the journal's Quests page. Keep Your Word on the Interact page hands over
  what you brought. On the due day they may come over to remind you. Kept: trust and warmth up a lot, hearts and
  reputation, their family hears, a deed of The Truth (and of the Hearthkeeper for a child). Broken: trust drops
  hard, hearts and reputation down, they remember and bring it up, and their kin and friends cool too. Saying
  honestly that you can't costs much less. Apologise covers a broken promise.
- Loading hints 94-95. Not played; compile-checked with `kingdoms/gradlew build`.
```

`MODLOG.md` (root), appended after the 0.20.19 line:

```
- **0.20.25**: no Powers change; pack bump for Kingdoms 0.7.523 (talks that matter: hidden trust, respect and warmth,
  branching talks, promises villagers hold you to).
```
