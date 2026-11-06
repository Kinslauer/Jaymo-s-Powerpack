---
name: symbiote-checklist-651
description: PR #651 symbiote Collect mode, swing engulf and Andy's checklist picks (mind, body, life tracks); classes, keys, bond gates
metadata:
  type: project
---
PR #651 MERGED 2026-10-29 02:4xZ (pack 0.17.158; compile-checked, not playtested). Ideas from the Bonding Experience mod (Forge 1.20.1 ARR, ideas only). Checklist doc: https://claude.ai/artifact/TxHYwDu1FSEAvJojXqwmJJ
- Collect: WebSymbioteCollect, wheel slot 9, bond 70, fetch within 12, one first-look inspection per item kind ever (SEEN), Use skips. Swing engulf: client WebEngulf.
- Living Armor no longer brings tendrils out; Tendrils slot unlocks at bond 30 (WebSymbiote.TENDRILS_BOND).
- Mind: WebSymbioteMind (Protect/Hunt/Hide orders, trust, mood edge tint + voice, named stages, requests); Status page via wheel Mind chip; Mind resets on every death (even rebond). WebSymbioteDig: ignored deep request digs you down.
- Body: WebSymbioteBody, arm slots K stow/draw, U use (bond 25), mantle N (bond 40, state flag 128), strain traits (Black night hunter, Red heartbeat sense bond 20, White refuses rotten flesh), C feeds meat / with a held mob kills then consumes (ultimateAnswers true for any bonded player).
- Life: WebSymbioteLife, starving to bond 0 consumes you; other deaths leave the blob holding your items at the spot to rebond as you were; misfires while settling; meteor blob rampages round its crater. Debug: /powerdebug web symbiote life ...
- Andy left out: no cure, build from the arm, dim the flashing.
