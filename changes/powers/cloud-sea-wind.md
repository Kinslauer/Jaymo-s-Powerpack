# a change of wind in the Cloud Sea, and skyfalls stop there until Dread
Andy: "Does the game tell you that you're in the cloud sea?" ... "A change of wind works". Still no words: crossing into
the Cloud Sea (up out of the Overworld's sky or down out of the Aether) brings a gust, and while you're in the band a
steady high wind blows on the ambient channel, dying away as you leave (`client/aether/CloudSeaWind`, from the band
heights `AetherVeil` already reads). Only in a world with a Cloud Sea band. 
- **Skyfalls stop in the Cloud Sea until Dread** (Andy: "The skyfalls seem to still be falling, what if they stopped in
  the cloud sea until later?"). In a One Dimension world water can no longer flow down out of the floor of the lowest sky
  band (the Cloud Sea's; the Aether's in a world made without one) until someone online has reached the Dread tier
  (`mixin/SkyfallStopMixin` on `FlowingFluid.spreadTo`, rule in `aether/world/SkyfallCleanup.blocks`). A fall ends in
  open air there, without spreading into a sheet. Falls already pouring into the Overworld's sky are cut just under that
  floor when their chunk loads and every 10 s within 10 chunks of each player, so the water below drains. This replaces
  sealing the Aether's springs, so after Dread the heavenfalls can pour again. `logs/latest.log` says "Skyfall stop on"
  with the floor's height at start, and logs each cut.
Built, not played.
