---
name: symbiote-stuck-crawl
description: Andy stuck lying in the symbiote suit 2026-10-29 after brawl downs; PR #659 (pack 0.17.164) symbiote knits broken legs + Jump ends a server-held crawl
metadata:
  type: project
  modified: 2026-10-29T04:16:55.282Z
---
2026-10-29 Andy: "I was stuck downed while transformed" (clip MedalTVMinecraft20261029000334677, his PC client log). Caelshire brawlers downed him twice (Retaliation "You're down" lines). The symbiote rescue got him up, but he was in a non-standing pose from 00:02:52 to quitting, with no give-up line, so he wasn't downed. Creative didn't free him. Diagnosis (inferred, not confirmed): both legs broken (Breaks.crawlOnly holds Crawl.CRAWLING till a priest), and Jump can't end a crawl the client didn't start.

PR #659 MERGED 2026-10-29 04:18Z (pack 0.17.164, built, not playtested; next free 0.17.165): WebSymbioteRescue knits bones on rescue + WebSymbioteRescue.knit each second while bonded (10 satiety, whisper); CrawlTaps Jump sends Crawl.Key(false) for a server-held crawl when the player isn't downed or crawlOnly. Andy's PC client logs are readable via device folder "...Instances\Jaymo's Powers\logs" (pre-approved). FootGrounding "off:pose" log lines = player not standing. See [[downed-guards]], [[meteor-stuck-downed]].
