# Darkvision no longer washes out daylight
- Bloods that see in the dark (Nightborn, Deepborn, Stoneborn, Hollow, goblins, orcs, moon elves, beast folk) had the
  ground, their hand and everything within 16 blocks turned a milky grey in broad daylight: the sight's post effect
  took any darkish pixel (sunlit grass included) for darkness and lifted it to grey.
- The sight is now off while the light at your eyes is 12 or more (lamps, or sky light by how high the Sun stands), and
  the shader only lifts pixels that are really dark (`DarkvisionClient.LIT`, `shaders/post/darkvision.fsh`).
- Compiled; not seen in game.
