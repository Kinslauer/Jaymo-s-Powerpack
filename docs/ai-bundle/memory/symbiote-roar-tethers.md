---
name: symbiote-roar-tethers
description: Symbiote roar (near-death takeover + big-body radial) and per-tendril wall tethers, merged PR #55 as pack 0.8.66 on 2026-10-12
metadata:
  type: project
---
2026-10-12 Andy asked: near-death takeover roars with flailing tendrils while the armour assembles suit -> tendrils -> big body;
the big body's radial Spike Burst becomes the same roar; Tendril Grab lifts you ~1 block off the wall, each tendril tethered
to its own cracked spot, elastic with movement, any other radial ability ends it, Shift+G and R still usable.
Built in PR #55 (merged on Andy's "You can merge that symbiote pr", pack 0.8.66). Code: WebSymbiote.startRoar/tickRoar/
roarBlast/endRoar, WebPose.ROAR, Wall record (rest, face, points), WebTendrilLayer.tethers, WebSymbiote.tendrilKeys
(R/G work tendrils while tethered even without Living Armor). Not playtested in game at merge.
2026-10-12: full symbiote write-up (all abilities/numbers from master) as a Claude Doc: https://claude.ai/code/artifact/f880ede6-ebc8-4191-8679-2079bf02e8f0 ; Andy will edit it with changes. Found: "worn too long" rampage never triggers (nothing sets live.rampage = true); Living Armor just withdraws at 60 s.
2026-10-12 23:5xZ: Andy's change list -> PR #71 MERGED 2026-10-12 23:55Z (pack 0.8.85; mask still covers first-person view under bond 60, MASK_CLEAR_BOND): tether view lock + aim sway, FPM hides own suit head/mask/maw, suits engulf armour, Wall Crawl walks on 4 tendrils (WebTendrilWalk), symbiote V has no pellet, dread vignette scales with bond (full only for low-bond lines + takeovers). Not playtested.
2026-10-13 00:17Z: PR #72 MERGED (pack 0.8.87): tendril Crush combo reach 12 (was 6); after a takeover's kill+meal it often (60%, always in village/crowd of 3+ within 12) coils a full charged jump and leaps clear (WebSymbiote.escape, WebNet.Escape, client tickEscape).
