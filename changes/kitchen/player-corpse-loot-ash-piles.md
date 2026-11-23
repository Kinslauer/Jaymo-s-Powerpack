# Players' bodies keep everything they carried, heaped ash that crumbles when punched
- **A dead player's things stay on their body** (Andy: "when players are killed, they should keep the loot on their corpse
  like npcs do, now accessed with a right click. the only time loot should burst like the video is when the body is
  gibbed"). Nothing drops on death any more: the whole inventory, armour and off hand stay on the body (it used to keep
  only the hotbar; the rest burst onto the ground). Right-click (tap) the body to open its loot screen, a wider one for a
  player: their armour and off hand down the left, the body in the middle, their inventory in a 9 by 4 grid beside it
  (hotbar on the bottom row), anything else left on them (Kingdoms' harness and hidden blades, Powers' baubles and web
  shooters, up to 24 pieces) in a block on the right. Anyone can take from it; it isn't robbing the dead. The old one-tap "take your hotbar back" is gone.
- Where it all goes if the body goes: **blown apart** (a strong enough blast), it bursts out with the gore; **burned
  away**, it's left lying in the ash and never despawns (in lava it burns with the body); **eaten**, left lying as
  before; **gets up** as a zombie player, that zombie carries all of it (dropped when it's killed) and no longer despawns while carrying anything; a rebirth or
  Limbo's return gives it all back; a body butchered on a meat hook drops it.
- **Lost limbs shed their armour** (Andy: "as long as the torso is there, the loot should be as well, the leggings and arm
  harness armor should be ripped off as limbs go"): a body that died without its head drops the helmet, without a leg the
  boots, without both legs the leggings, and the harness pieces worn on the lost part (arm and hand plate with the arms,
  thigh and shin plate with the legs, hood and crest with the head; rings, bracelets, web shooters and hidden blades with
  the arms; foot baubles with the legs). A player torn apart by a blast leaves their torso with everything else on it. A
  part taken off the body later (cut, or a head taken on the meat hook) sheds its piece the same way. The body isn't
  drawn wearing the armour it holds (as before).
- Nothing is lost if a player respawns or leaves the game before their body appears: the body appears then.
- **Ash piles lie heaped** (Andy: "ashpiles should disintegrate into dust if you punch them, and they should resemble
  mounded piles, adding more for larger burned bodies. like the entrails, not sheets, until it starts to blow away and
  flatten out"): fresh ash is a mound, taller with more little heaps round it the deeper it is (a big body leaves more,
  deeper mounds), in one of eight turns. Once the wind takes its first layer it lies flat as before, and keeps blowing
  away. Punch it (anything but a shovel) and it crumbles into dust and is gone; a shovel still gathers it. Loose heaped
  ash has nothing to stand on.
- Classes: `PlayerMixin`, `PlayerCorpses` (`keepWith`, respawn/disconnect safety), `CorpseEntity` (`tearOff`,
  `lootPlayer`), `CorpseLoot`, `CorpseLootMenu`, `CorpseLootScreen`, `ZombiePlayer`, `Hanging`, `AshPileBlock`; models from
  `kitchen/tools/gen_ash_piles.py`.
- Built by CI; not played. The wide loot screen, the mound models and the punch-to-dust haven't been seen in game.
