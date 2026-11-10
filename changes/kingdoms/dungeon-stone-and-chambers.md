# Fix Stormborn Tomb: missing template pools and namespace mismatch

The Stormborn Tomb (PR #1137) was merged but its template pools were lost during the merge. Additionally, the template pool names used underscores (`stormborn_tomb_spawn`) while the Java code expects slashes (`stormborn_tomb/spawn`), causing the structure to fall back to vanilla trial chambers and generate solid stone fill.

## Changes
- Restored 12 missing template pools for all Stormborn Tomb pieces from the PR's merge commit (eeb8d0239)
- Fixed template pool names to use slashes (`stormborn_tomb/spawn`) instead of underscores (`stormborn_tomb_spawn`) to match the Java code in `StormbornTomb.java`
- Fixed the start pool (`overworld_stormborn_tomb.json`) to use the correct namespace

## Root causes identified
1. **Missing template pools**: The PR merge commit (0ad4bd9c6) didn't include the template pools that were added in a later PR commit (eeb8d0239) but lost in a subsequent rework commit (152223750).
2. **Namespace mismatch**: Template pools used `jaymoskingdoms:stormborn_tomb_spawn` but Java code references `jaymoskingdoms:stormborn_tomb/spawn`. This caused the structure to not find its pieces and fall back to vanilla trial chambers.

## Not tested in game
Template pools compile and names match Java code, but dungeon generation not verified in a live world.
