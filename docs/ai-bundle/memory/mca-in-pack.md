---
name: mca-in-pack
description: Minecraft Comes Alive is shipped as a self-built 26.3 jar in libs/ (PR #18, Powers 0.8.21 / Kingdoms 0.4.2) until an official 26.3 release
metadata:
  type: project
---

2026-10-11: Andy wanted MCA Reborn in the pack. CurseForge 535291 / Modrinth `minecraft-comes-alive-reborn` had Fabric
only up to 26.2 (8.1.11), but upstream github.com/Luke100000/minecraft-comes-alive has a `dev/26.3` branch. PR #18
ships a jar built from it (3badc42) as `libs/mca-fabric-*.jar` override plus Kingdoms McaCompat tie-in.

**Why:** no official 26.3 build; GPL-3.0 allows it.

**How to apply:** when a Fabric 26.3 file appears on CurseForge, move MCA into packMods and delete the libs jar
(TODO in build.gradle). Building MCA in the cloud: `./gradlew :fabric:build -x test --no-configuration-cache` with a
~/.gradle/init.d script that rewrites repo.maven.apache.org to the Google mirror (Central 429s). See [[pack-worldgen-and-wishlist]].

Andy's rule: settlements may draft any MCA villager into service (friends, spouses, kids: "builds character").
