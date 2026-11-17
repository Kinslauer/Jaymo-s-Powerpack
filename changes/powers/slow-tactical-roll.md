# Slower prone roll, weighed by armour
- **Prone roll** (double-tap Left or Right while prone; Andy: "Rolling should be slower and look like snake from metal gear, it should be even slower the more weight your gear has. To an upper limit."): each one-block roll now takes 0.6 s instead of 0.25 s, with the arms folded in to the chest (round a weapon) like Snake's instead of stretched past the head. You stay prone after it, as before.
- **Gear weight**: armour points plus toughness (full netherite = 32, the cap) stretch each roll up to 1.6x as long (about 0.95 s); nothing past the cap slows it more. Knobs: `Crawl.ROLL_TICKS`, `HEAVIEST`, `HEAVY_SLOW`; arms: `CrawlPose.ROLL_ARM_X`, `ROLL_ARM_IN`.
- The roll's length is synced, so others see a heavy roll at its own pace. Rolling fire out (roll back the other way) and the 2 s one-way limit are unchanged. The Alt dodge is untouched.
- `Crawl`, `CrawlTaps`, `CrawlPose`. Compile-checked; not played.
