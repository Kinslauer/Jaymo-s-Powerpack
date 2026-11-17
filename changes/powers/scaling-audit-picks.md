# scaling audit picks: armour, power growth, Z Fighter, gear and tiers
Andy's picks from the scaling audit (pick page rows; early game kept weak, late game given room to grow).
- **Armour**: the armour-wear bonus (ArmourWear) now applies to players only, and is 1.15x armour (was 1.4x; toughness
  stays 1.25x), so armour no longer hits the 80% cap with Hearth gear. Mobs meet the world-tier armour as written.
- **Blight elites**: their armour and follow range are now flat per tier (+1.5 armour, +8 blocks), not multiplied
  (BlightMobs used a total multiplier: tier 3 had 5.5x armour and 25x follow range).
- **Wind**: Gust damage is capped at 100 after weather, Stormbound and Tempest.
- **Xenoform**: a player's blows in an alien form hit 10% harder per unlock (x1.4 at full mastery; XenoformDamageMixin).
- **Resonance**: Pulse grows 5 x (1 + 0.15 per unlock): 6.5 at unlock 2, 8 at full mastery.
- **Web**: the always-on physique starts at nothing and reaches its full +3 attack at full mastery (was +0.6 on day one).
- **Symbiote**: every symbiote attack gains up to +50% with bond (x1.5 at bond 100). **Rig**: the palm blast gains 15%
  per Mind level above the first (x1.6 at level 5). The crash is damage to the pilot, so it was left alone.
- **Z Fighter**: the punch cap is multiplied by the form's fist bonus (base 9, Super 1 ~10.8, Super 3 ~13.8, God ~16.6),
  so Super forms now show in punches. Fists start at 2 (was 3) and grow 1 per unlock to 6 at full mastery, as before.
- **Pacts and Void**: Cinder's toughness 4 -> 2; Arsenal's Void fills the Fate meter 4 per Enderman kill (was 10).
- **Tinkering**: head attack silver 2.25 -> 3.0, amethyst bronze 1.5 -> 3.0, steel 3.0 -> 4.0, manyullyn 3.5 -> 4.5.
  Forged-grade Tinkering plate now gets the grade's armour (Poor -1 to Master +1.5) like every other armour.
- **Legendary grade**: a fifth grade above Master (+2 armour a piece, attack Master's +1.5 more, x1.4 durability). A smith
  forges it only when they have reached Legend (Kingdoms; KingdomsCompat.legend): a Master result at Legend comes out
  Legendary half the time. Without Kingdoms it never forges.
- **Rust** reads the metal: Tinkering plate of copper, bronze, silver or gold no longer rusts, and the Gladiator set is
  tagged rustproof (new jaymospowers:rustproof tag).
- **Fishing**: the big-fish glut counts per angler per day across every village (it was per village, so carrying the
  catch to the next one dodged it). Old per-village glut data is no longer read.
- Comments fixed in GravityPower (a fresh slam is 0.48 of full) and ZFighterTraining (mastery ticks every 20 s).
- Built; not played.
