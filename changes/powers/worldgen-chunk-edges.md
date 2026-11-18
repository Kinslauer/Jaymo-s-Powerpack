# worldgen no longer reads or writes past its chunk edge
- **Sky springs**: the flood fill that checks whether a spring's water would run out of the Aether's bottom now stops at the 3x3 chunks round the spring's chunk (past that counts as a leak, as an unloaded chunk already did), so it no longer reads terrain it may not touch. Thousands of "unsafe terrain read" warnings a day.
- **Eden island caves**: tunnels are cut and vented only inside the 3x3 chunks round the feature's origin chunk, so blocks are no longer lost to a far chunk.
- Not played; nothing built on this PC (CI compiles).
