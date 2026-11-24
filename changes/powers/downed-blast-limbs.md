# Blasts blow limbs off the downed
- Andy: "blasts on downed enemies should blow limbs off". A blast that hits someone already down (and doesn't end
  them) takes an arm or a leg at twice a standing survivor's chance: certain from about two thirds of the way in to the
  blast's centre, now and then two. Before, a player's blast on a downed mob only counted as a wound and took nothing,
  and anyone else's took a limb no more often than from someone standing.
- `BlastGore.downedBlast` (`DOWNED_LIMB_SCALE`), called from `Downed`'s damage check before the downed state keeps the
  blow; the usual after-damage roll skips that body for the tick. Killing blasts still gib or take a limb off the corpse
  as before, and the blast still knocks them out cold (`BlastOut`).
- Built in CI; not played.
