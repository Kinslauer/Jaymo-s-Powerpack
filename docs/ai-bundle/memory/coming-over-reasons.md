---
name: coming-over-reasons
description: PR #809 (Kingdoms 0.7.449 / pack 0.19.2, MERGED 2026-10-31 a156230d): strangers only come over with a reason, "Ask what's going on" MCA option, "Accuse them of theft"
metadata:
  type: project
  modified: 2026-10-31T22:15:33.563Z
---
2026-10-31 Andy: villagers running up must have a good reason, not just to introduce themselves, and you can ask why. Also: confront whoever you think robbed you from the MCA menu; the wrong person costs you hearts.
PR #809, branch claude/project-thread-pnmvw9, built in the cloud, not playtested, MERGED 2026-10-31 22:3xZ.
`ComingOver` decides the reasons for `Handshakes` comers: hurt (gives bread), danger (monster glows), storm, thanks (Recollections remark), deed (Deeds.greeting), kin heritage, foreign, gear, beast (Sightings), mistaken. No reason means nobody comes. The answer jaymoskingdoms_whats_up shows for a day, and Approaches visits are recorded too.
`Crooks.ACCUSE_ANSWER` (jaymoskingdoms_thief) shows for a day after you notice a theft or scam: the real thief is confronted late, anyone else costs -8 hearts plus Recollections.Kind.ACCUSED (in SharedHistory WRONGS).
Related: [[villager-approaches]], [[shared-history-talk]], [[pickpocketing]].
