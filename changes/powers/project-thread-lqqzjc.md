# the Cloud Sea - a band of cloud and open air between the Overworld and the Aether
- **The Cloud Sea** (Andy: "another dimension that is just clouds and air between the overworld and aether ... that way
  players wont be able to see it or get there at all easily"). New One Dimension worlds stack `jaymospowers:cloud_sea`
  between the Overworld and the Aether: 256 blocks of open sky with nothing in it but cloud. The islands are now that much
  farther up. **New worlds only**: a world keeps the stack it was made with (`compat/OneDimensionDefaults` brings an
  unedited `config/one-dimension.json` up to date for the next world).
- **The overcast**: near the top of the band one bank of cloud runs from horizon to horizon, 6 to 22 blocks thick. From
  underneath it is a ceiling: two layers of cloud are solid, so you find one of the wide wells through it (about 6% of
  the sky) or dig up through. From on top it is a floor you sink ankle-deep into, and it catches most of what falls off
  the Aether, unhurt. Lower down, loose banks drift over about a quarter of the sky.
- **Cloudbank**, the new block it is made of: a cold aercloud that daylight passes through, so the sky under the overcast
  is as bright as the sky over it. It drops itself.
- **Out of sight** (`client/aether/AetherVeil`): from under the Overworld's own clouds the sky looks as it always did
  (neither the Cloud Sea nor the Aether is drawn). Above them you see the Cloud Sea; the Aether only appears once you are
  up through the overcast.
- **The fall home**: Slow Falling now comes as you drop out of the Cloud Sea's floor into the Overworld's sky
  (`WindAether.stackedTick`), where it used to come as you left the Aether.
- Nothing spawns in the Cloud Sea and nothing is built there. The cloud shapes are world generation data from
  `tools/gen_cloud_sea.py` (deck heights, cover, thickness, wells: tune them at the top of that file).
- Compiled; the world generation data was loaded by a test server; not played in a stacked world.
