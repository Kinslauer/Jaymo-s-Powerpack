# Town golems and the Bulwark Diamond: behaviour spec

Clean-room spec in the REA-Prompts style (behaviour, not code). Source of ideas: **Special Golems** (CurseForge
`special-golems`, leo1011101; "Custom License", Forge/NeoForge 1.19.2 to 1.21.1). Its files are not used: what's below
is read off its public description only, then our own design.

## What the original does (as described)
| Golem | Described behaviour | Ours |
|---|---|---|
| Clay / Terracotta | 40 HP, holds weapons, wears armour | **Clay Porter**: hauls loose items into chests (no gear) |
| Snow | 4 HP, rain-proof with pumpkin, 1-damage snowballs | **Frost Tender**: puts out fires and burning folk, snowballs monsters |
| Iron | 100 HP, follows its maker, runs at targets | not remade (villages already have iron golems, realms have guards) |
| Gold | 200 HP, slow, long laser, more harm to undead | **Gilt Lamp Warden**: night glow, beam, double on undead + ignites |
| Diamond | 500 HP, sits on command, while sitting takes damage aimed at allies | **Bulwark Diamond** (an item, Andy's ask) |
| Emerald | three parts: head watches an ally so they won't die, chest heals, legs re-form | **Verdant Mender**: heals and keeps one from dying, one body |
| Lapis | stationary, slowing/weakening laser, engraves a random enchantment upgrade (item then can't be enchanted), weaker once out of engravings | **Lapis Scribe**: same idea, with ink per day / lapis blocks |
| Redstone | unfinished | not remade (we have our own Redstone Golem beast) |
UNKNOWN: exact ranges, cooldowns, summoning shapes (sources disagree); all numbers here are ours.

## Ours
- Look: one rune golem model of our own (stone body, carved core, glowing eyes), five materials, vanilla-ramp paint.
- How you get them: golemworks realms keep them; anyone with Golemancy makes them on the infusion altar.
- Golemworks: 15% of realms, folk attuned to mana, roster 3/2/2/1/1, rebuilt one per 5 minutes.
- Bulwark Diamond: use to raise a ward; you are rooted; blows from something aimed at allies within 8 blocks drain the
  diamond's 500-point ward instead of hurting them; refills when lowered (faster with mana).

## Differences from the original (deliberate)
No summoning by block shape (altar only), no Laslem's Pumpkin, no armour/weapons on clay golems, no following iron golem,
no three-part emerald golem, diamond golem is an item, all numbers our own.
