# Your game starts again: the Sodium beta leaves your profile
- Andy's game wouldn't start (2026-11-12 00:10, "Sodium 0.9.3-beta.4 is incompatible with Iris 1.11.7"): ImmersiveVFX's
  test (2026-11-10) brought Modrinth's Sodium beta as `sodium-2MZWfUts.jar`; when the test ended and Iris came back, the
  profile kept it (it never downgrades a mod, and the removal list only knew CurseForge's `sodium-fabric-0.9.3` name).
  `removedFromProfile` now lists `sodium-2MZWfUts` too, so Build Modpack moves it out and puts the pack's Sodium 0.9.2 in.
