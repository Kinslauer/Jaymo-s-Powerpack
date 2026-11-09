---
status: in-review
task: realistic-water-trial
branch: claude/realistic-water-trial
with: 
pr: 
mods: 
versions: 
started: 2026-11-08
owns:
  - 
---

# Realistic Water Trial

**Task:** Test whether Realistic Water (Fabric 26.3, 1.3.2) runs beside our renderer (Sodium/Iris/EMF stack). The mod requires Vulkan graphics API. Test on a copy of a world, report if it launches, if Sodium still works, frame rate with water on screen, and whether Streams Reflowing still makes rivers.

## Plan
- Download Realistic Water 1.3.2 for Fabric 26.3 from GitHub releases
- Add to a test modpack (not the live modpack/mods)
- Launch test client with a copy of a world
- Test: launch success, Sodium/Iris compatibility, FPS with water, Streams Reflowing rivers
- Report findings plainly

## Notes
- Downloaded Realistic Water 1.3.2 (realistic-water-1.3.2.jar, 2.23 MB) from GitHub releases (tag 10, released 2026-11-08)
- Added as localRuntime dependency in build.gradle
- Mod compiles with the modpack (verified earlier compileJava success)
- Dev environment has pre-existing incompatibility: Stormie's Spiders 3.3.1 requires fabric-api as a runtime mod, but fabric-api is dropped from dev runtime (packMods.drop(1)). Workaround: marked Stormie's Spiders as `dev: false`.
- Loom cache corruption from previous crashed daemon runs prevents further Gradle operations (cache lock, "Failed to split minecraft" error)
- Test environment is headless (no display) — cannot verify Vulkan initialization, Sodium/Iris rendering, FPS, or Streams Reflowing rivers in-game
- Realistic Water mod requires setting Graphics API to Vulkan in Video Settings, then restart

## Done
**What was verified:** Realistic Water 1.3.2 jar downloads and compiles with the modpack. No compile-time conflicts.

**What was NOT tested in game (blocked by dev environment instability + headless environment):**
- Whether the game launches with Realistic Water + Vulkan
- Whether Sodium/Iris/EMF stack still works
- Frame rate with water on screen (/water stats)
- Whether Streams Reflowing still generates rivers
- Any visual rendering artifacts or crashes

**Recommendation:** Test on a local machine with display by adding the jar to modpack/mods, setting Graphics API to Vulkan, and launching with a copy of a world. If Sodium/Iris breaks, that is the answer — no workaround needed.