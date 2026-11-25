# Banks: a realm's counting house (Andy, 2026-11-23)

Andy, on Numismatic Overhaul (MIT; ideas only, nothing of its code): "no shop block or piggy bank, instead a new bank in
every realm with enough commerce to make sense". His picks: deposit and withdraw, interest on savings, loans, letters of
credit; "the money changers should work here as well, or at least their functionality. also realms can fall and so will
your money, meaning players can have a real (under)hand in their finances"; one account per realm, any of its banks;
built by wealthy realms with a market, a Coin-creed realm as soon as it has a market. (The purse icon and money bags
went in PR #2063.)

## What is built
- **The bank** (`Banks`, building `bank`): a stone counting house with a counter and a strongroom. A realm (kingdom or
  fiefdom) builds one once it has a market and is Wealthy; a Coin-creed realm (`GreatWars.Creed.COIN`) as soon as it
  has a market. One per realm. Its staff are the realm's money-changers (`Currencies.staff`, `Professions.CHANGER`): a
  realm with a bank always keeps one.
- **Banking at the counter**: a money-changer whose realm has a bank built gets "Banking" on MCA's talk menu (the
  changer's exchange stays on their trade screen, so the bank does both). The card offers, by what you have:
  deposit all your coin / half of it; withdraw 10, 50, all (in the realm's coin, with bits for change); a letter of
  credit for your balance; borrow; repay. Your balance is shown on the card.
- **Accounts**: one per player per realm. A fiefdom's bank is its overlord's (walking up the chain to the first
  kingdom). Kept in milli (`Money`), saved on the Overworld with the factions. The coin you deposit is weighed at the
  bank's rate (foreign coin at the going rate less the changer's cut). The coin goes into the realm's treasury
  (Andy: "yes if its spent they cant give it to you, or anyone. may help speed up the decline if that happens"):
  a withdrawal gets only what the vault holds, and the rest stays on your books until it fills again.
- **Interest**: every 7 days, 2% on up to 500 emeralds' worth of a balance, while the realm has coin of its own
  beyond what it holds for its depositors.
- **Loans**: borrow up to your standing with the realm (`Reputation`): 0 below 100, then a tenth of it in emeralds'
  worth (up to 100 at 1000), one loan at a time, 10% on top, due in 7 days, paid out of the treasury. Repay at any
  bank of the realm, from your coin or your balance. Overdue: the bank takes it from your balance; what it can't take
  costs you standing (-25 a day) until it's paid.
- **Letters of credit** (`letter_of_credit` item, the letter's art): a sealed note for an amount drawn on a realm, in
  your name. Only you can cash it, at any bank: free in its own realm, 12% at another realm's bank (the changer's 10% cut and 2%). A thief who lifts
  it has paper. The bank it's drawn on pays it out of its treasury; while that's short, the letter is handed back.
- **When a realm falls, its bank goes with it**: every balance in it is gone, every letter drawn on it is waste paper,
  and every loan it made is forgotten. Accounts are checked on the bank's daily round and whenever they're used, so no
  fall site needs to know about banks. Players who hold money there are told the next time they're online.
- **Dying for good**: your debts are taken from your balances first; the rest "goes to your next of kin, or is
  absorbed by the bank into their coffers" (Andy). Next of kin is the nearest player in MCA's family tree (partner,
  child, parent, sibling).

## Andy's answers (2026-11-24)
- Deposits go into the treasury (above). Rates (2% a week, 10% on loans, 12% abroad): "yes". Final death: next of kin,
  or the bank's coffers. The lore paragraph: approved.
