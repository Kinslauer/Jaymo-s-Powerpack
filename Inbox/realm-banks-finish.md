---
status: in-review
for: claude-cloud
from: coordinator
asked: 2026-11-23
thread: cmsg_017LSt6rWz6CzxSGvzDd41XWRihgeu7KNw14feoHe7EEoR
claimed_by: claude-cloud
pr: 2082
suggested_owns:
  - kingdoms Banks.java, LetterOfCredit.java (new)
  - kingdoms Buildings.java (one realm() line), Planner.java (bank gate in eligible and utilitiesDone)
  - kingdoms mixin/mca/McaAnswersMixin.java, McaDialogueMixin.java (banking answer)
  - kingdoms lang/parts/banks.json, data/jaymoskingdoms/dialogues/main.json
---
# Finish realm banks (branch claude/realm-banks, WIP pushed)

**Andy's words:** on Numismatic Overhaul: "no shop block or piggy bank, instead a new bank in every realm with enough
commerce to make sense." Picks: deposit and withdraw, interest on savings, loans, letters of credit; "the money changers
should work here as well, or at least their functionality. also realms can fall and so will your money, meaning players
can have a real (under)hand in their finances"; one account per realm, any of its banks; built by wealthy realms with a
market, a Coin-creed realm as soon as it has a market. (Decisions.md, 2026-11-23.)

**What's wanted:** a realm with the trade for it raises a bank; its money-changers stand at the counter in working hours
and, through MCA's talk menu ("Banking"), take deposits, pay out, pay interest, lend, and draw and cash letters of credit;
a realm that falls takes every balance, letter and loan of its bank with it.

**Where to start:** `docs/Banks-Plan.md` on the branch (the design and the open questions), then `Banks.java`.

## State (2026-11-23, 11:59 PM)
- Pushed to `claude/realm-banks` as one WIP commit (7e6103dd41). **Never built**: CI hasn't seen it, so expect compile
  errors. Treat everything as unverified.
- Written: `Banks` (ledger on the Overworld keyed by realm id + seat, interest, loans, overdue, the daily books,
  the realm-fall wipe, tellers walked to the counter, the banking card, the building's design); `LetterOfCredit` (item +
  `credit_note` component, item model using the sealed letter's texture); registration in `JaymosKingdoms`; the
  building in `Buildings.register` (kingdoms and fiefdoms, from 24 folk); `Planner.eligible` gate
  (`Banks.mayBuild`) and `utilitiesDone` exclusion (or the wall would never go up below Wealthy); `LifeStory` calls
  `Banks.reborn` on dying for good; MCA answer `jaymoskingdoms_banking` (main.json, McaAnswersMixin filter,
  McaDialogueMixin route); lang part `banks.json`.
- Shipped separately already: the purse icon and money bags, PR #2063 (CI green).

## Left to do
1. Build in CI (no PC builds while Andy plays) and fix what fails. Watch: `Buildings.Sketch`/`Kit` are package-private
   nested classes used from `Banks`; `Quests.day(ServerLevel)` with `player.level()`; `ZFighterFolk.ask` signature;
   `ByteBufCodecs.fromCodec` for the component; unused imports.
2. Change note `changes/kingdoms/realm-banks.md` (none yet), then the PR; turn Auto-fix on.
3. Check the teller walk doesn't fight Routines/Unstick (changers have no job-site block, so Unstick leaves them).
4. Lore: add a `(new)` paragraph for the banks to Lore.md (who keeps the counting houses, why a fallen realm's notes are
   paper) for Andy to approve.
5. Ask Andy the open questions in `docs/Banks-Plan.md`: deposits kept apart from the treasury (current) or in it (a run
   on the bank); the rates (2% a week on up to 500, 10% loans for 7 days, letter fee 12% abroad); what dying for good
   does (current: accounts, loans and old letters are wiped).
6. Not done: the money-changer's exchange stays on their trade screen (not on the bank card); banking needs MCA (no
   talk menu without it); no `/kingdom` command to see balances.

## Notes
- Claude (side session, 2026-11-23): handing over at Andy's ask ("put this work up on the vault for coord claude to
  pickup tmr").
- 2026-11-24 17:59 2026-11-24 Andy: "finish here": cloud Claude finishes this in thread cmsg_017LSt6rWz6CzxSGvzDd41XWRihgeu7KNw14feoHe7EEoR; bots leave it.
