---
name: leg-cleave-halve
description: Double leg cleave + waist halving with heavy chops, more on zombies; PR #962 MERGED 2026-11-05 01:2xZ (Powers 0.20.61 / Kingdoms 0.7.552)
metadata:
  type: project
  modified: 2026-11-05T00:37:59.873Z
---
2026-11-05 Andy: "cleave both legs off once I'm strong enough ... proc more often on zombie humanoids than anyone else", then "why not splitting a torso clean off too".
PR #962 (branch claude/project-thread-dw1tnn, Powers 0.20.52). MERGED on Andy's "merge", renumbered to Powers 0.20.61 / Kingdoms 0.7.552. Built, not played. Jars /mnt/project-files/leg-cleave/.
- Dismember.cleave: axe/greatsword (Finishers.Kind) blow >=10 that severs a leg takes the other at 25% (zombies 60%), x Grim/Rubber.
- Dismember.halve: axe/greatsword >=16 on torso, killing or downing, 20% (zombies 50%): both legs off, dies not downed (Downed LAST hook). Zombies on any such blow, crawl on (Wounds.onDamage).
- Answers given: all blades share one sever roll (30% on overkill; skeletons 90%; players 30%); damage decides via overkill.
Related: [[grabbable-severed-parts]], [[darktide-wind-fixes]], [[accurate-hitboxes]].
- 01:02Z Andy: Rubber-folk survive these; absorb while downed; swallow within 10 s of a torso chop starts slow regen, vulnerable till regrown. Done in same PR (+Kingdoms 0.7.549): split -> downed + tag jaymospowers_rubber_split, 10 s (Dismember.SPLIT_TICKS) else die; swallow -> jaymospowers_rubber_mending, legs on 20 s clock, can't get up till legs back (Downed.getUp veto). HeritageHold.downedStill lets Rubber-folk absorb downed. Beheading still kills (asked Andy).
