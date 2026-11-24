# Felled by a leg blow: ragdoll, prone, roll to get up
- **Crippled is not downed** (Andy: "a suffienct blow, to the legs forces you into a ragdoll then prone, but you can roll
  to recover. downed is what happens after losing your hearts"): a blow that lands on a leg or foot and takes 35% of your
  most health (7 of a player's 20), or breaks that leg, takes your legs out from under you. Falls, fire and bleeding
  never do.
- A player goes limp into Kitchen's ragdoll, flung a little away from the blow, and once the body comes to rest lies
  prone. Jump and the crawl key don't stand you up: a roll does (double-tap Left or Right, or press Dodge), and you come
  up on your feet as it ends. Two broken legs still keep you crawling. A tip card says how. Without Kitchen you go
  straight to prone.
- A mob built like a person goes down flat on its back (the leg sweep's fall) and gets up by itself.
- `LegFell` (new), hooked from `Wounds`' after-damage; `Crawl` (no standing up while felled, a roll brings you up),
  `CrawlTaps` (side rolls and the Dodge key while felled), `DodgeClient`. The share is `LegFell.BLOW_SHARE`.
- Built in CI; not played.
