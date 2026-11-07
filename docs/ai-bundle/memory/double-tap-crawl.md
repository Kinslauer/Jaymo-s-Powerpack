---
name: double-tap-crawl
description: Double-tap Jump+Sneak crawl (PR #82, 0.8.95) A/D crawl roll that puts out fire (PR #103, 0.8.113), own poses + held/quick rolls (PR #121, 0.9.18)
metadata:
  type: project
  modified: 2026-10-13T02:51:26.139Z
---
Andy's ask 2026-10-13: double-tap space+shift to crawl while space is held, to fit one-block gaps. Not a power; any player.
Merged PR #82 as pack 0.8.95. Code: core/Crawl (synced CRAWLING attachment + Crawl.Key packet), mixin CrawlPlayerMixin (swimming pose, same trick as Darktide cripple), CrawlJumpMixin (no hop while crawling), client/CrawlTaps. Combat Roll's double-tap Sneak ignores Sneak presses while Jump is down. Built only; not playtested in game.
Crawl roll merged PR #103 as pack 0.8.113 (Andy 2026-10-13): while crawling, double-tap A/D rolls one block that way, turning over once (CrawlRollPose, CrawlAvatarRendererMixin on AvatarRenderer.setupRotations); puts out fire on you and fire blocks rolled through. Animation direction unverified in game. Wind's double-tap hover moved to R only in PR #98.
PR #121 merged as pack 0.9.18 (2026-10-13): CrawlRollPose renamed CrawlPose; on dry land gesture crawlers get an elbow-crawl pose and a log-roll pose (arms past head) instead of vanilla swim strokes (CrawlModelFeatureRendererMixin). I read Andy's 'uses vanilla animation for swimming or crawling' as a complaint; unconfirmed. Holding A/D chains rolls; Andy wants rolls quick for stealthy movement: ROLL_TICKS 5 (a block per 0.25s).
PR #226 (pack 0.11.22, 2026-10-15): rolls one way last at most 2 s (ROLL_STREAK_TICKS 40), then that way is spent until you roll back; only a reversing roll puts out fire on you; 2 s still resets. Built, not playtested.
Same PR: prone now = hold Jump + Sneak 4 ticks (HOLD_TICKS in CrawlTaps), no double tap (Andy 2026-10-15).
PR #238 MERGED 2026-10-15 10:1xZ as pack 0.11.44 (2026-10-15): Andy wanted ONLY a Crawl key, on Down Arrow (KEY_DOWN): press to go prone, press again or Jump to get up (waits for headroom). Jump+Sneak gesture and sneak-into-gap auto-crawl removed at Andy's request; Combat Roll no longer ignores Sneak while Jump is down. Built, not playtested.
PR #810 MERGED 2026-10-31 (pack 0.19.5, renumbered to 0.19.9 via #815 and #816 after clashes): going prone = crouch then down onto hands, legs slide back; getting up reversed (client/ProneTransition, replaces vanilla swim tilt in CrawlPose.rotate); first-person view follows the head when supine (CrawlPose.supineEyeShift + ProneCameraMixin, shift relative to the belly view). Built, not playtested.
Same PR: lying prone from a Sneak-held slide, scrolling the stance wheel down locks prone (StanceScroll -> CrawlTaps.lockProne).
PR #839 MERGED 2026-11-01 04:06Z (Powers 0.19.40): crouch grab. A fresh Sneak on the ground facing a block 0.7-1.6 over your feet with only a 1-high gap above it starts the ledge prone climb straight away (Ledge.crouchSpot, LedgeClient.tryCrouchGrab). Built, not playtested.
