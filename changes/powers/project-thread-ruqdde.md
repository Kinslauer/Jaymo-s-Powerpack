# Limp body in the Octo arms
- **Carried on the Octo Core's arm walk** (held up off the ground between the four arms, not gripping a wall), the
  wearer now hangs limp between them instead of walking the air (Andy: "the player should ragdoll within the arms the
  same way their legs hang when using the tendrils"). The legs dangle and swing like Wall Crawl's tendril hang, the arms
  hang loose at the sides and swing a beat behind the legs, both trail back while you move and settle over a few
  seconds after you stop, and the knees and elbows give a little. The head keeps to the look, so first person still
  follows it. A swing, a drawn bow or anything held up in use keeps the arms. Every client sees it.
- `WebHangPose` (new `limp` on its record, `bendLimbs`), `RigOctoClient.carried`, `TendModelFeatureRendererMixin`.
- Built; not played.
