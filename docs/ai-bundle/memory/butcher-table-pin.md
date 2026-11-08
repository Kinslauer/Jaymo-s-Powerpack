---
name: butcher-table-pin
description: Ragdoll bodies on a butcher table are pinned (torso held over the table top) so they don't flop off; PR #497 (Kitchen 0.5.24 / pack 0.16.4)
metadata:
  type: project
  modified: 2026-10-25T00:55:44.647Z
---
2026-10-25 Andy: carcasses put on the butcher table keep the ragdoll (he likes that) but often fall off. The ragdoll sim showed it: cow on 2x2 fell off 1 in 3, sheep/pig on 1 block always.
PR #497 (branch claude/project-thread-gbk9nm, Kitchen 0.5.24 / pack 0.16.4, MERGED 2026-10-25 01:18Z, not playtested): RagdollBody.pin() holds the torso corners over the table rect, limbs flop free. CorpseRagdoll pins on set-down and auto-pins any grounded body on butcher blocks (table up to 3x3, CorpseEntity.tableUnder). It unpins on a blast shove >0.4, an outside move >0.5 or a broken table. tools/ragdoll_sim has a table check. Also: item tag jaymoskitchen:butchering_tools (axes + optional jaymospowers:primitive_knife and bone_knife) butchers corpses; Andy 2026-10-25: the Primitive Knife keeps its name (not "Flint Knife"). Jars in /mnt/project-files/butcher-table/.
See [[physics-mod]], [[butcher-villagers]].
