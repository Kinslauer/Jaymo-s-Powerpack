# The Last Lantern: a complete written-start example

Requires the written-start system from PR #2153. This is another Hearthkeeper way-hut under Lore.md's existing
"Way-huts" placement: a keeper stocks it in the wild for travellers caught after dark. It introduces no new people or faith.

The build is 13 blocks wide, 14 deep and 12 high. Template layer 4 sits on the ground. There is a stone room under a
pitched spruce roof, a covered porch with a lantern, a cold hearth, a cot, a table and an underground store.

## What the player does

1. Wake beside the cot with no story kit. Find bread, apples and flint and steel in the room's chest.
2. Pick up the keeper's note from the table. This opens the iron trapdoor to the cellar.
3. Go down the ladder and pick up the packing slip. Take the tools, boots and food if wanted.
4. Keep a short watch by the hearth at night. Howls and door noises accompany the tale; these are sounds, not scripted attackers.
5. After the watch, wait for daylight (sleeping is allowed), then follow the tracker to the nearest town.

Leaving the watch after it begins also advances to the town journey. The hut is ordinary, breakable Minecraft blocks;
the hatch demonstrates a tale gate, not an unbreakable adventure-mode lock. Lighting the hearth is optional.

## Fixed supplies

| Container | Contents |
|---|---|
| Room chest | 4 bread, 2 apples, flint and steel, 6 sticks, 2 string |
| Cellar chest | Stone axe, pickaxe and shovel, 12 torches, leather boots |
| Cellar barrel | 8 potatoes, 6 coal, 8 dried kelp |

The start has `bare: true`, `shared: false`, and weight 1. With the original way-hut also loaded, either start can be
drawn; there is no individual start picker. For a focused test, use a test-world datapack that overrides
`data/jaymoskingdoms/written_starts/way_hut.json` with a copy of `last_lantern.json`. Both entries then lead to this
build and tale. A weight of 0 does not disable a start: the loader raises it to 1.
Choose **Brought in from the dark** in a new life. No changes to a live server are needed to author this example.

## Files to edit

- `tools/last_lantern.py`: the actual block grid and chest slots. Uses the original way-hut's grid and NBT helpers.
- `kingdoms/src/main/resources/data/jaymoskingdoms/structure/written_starts/last_lantern.nbt`: the saved build.
- `kingdoms/src/main/resources/data/jaymoskingdoms/written_starts/last_lantern.json`: placement and tale selection.
- `kingdoms/src/main/resources/data/jaymoskingdoms/tales/the_last_lantern.json`: the words and progression.

The Data-mode structure-block marks are `spawn:west`, `place:board`, `place:store`, `place:hearth` and `gate:cellar`.
The gate mark is directly above the trapdoor. The packing slip is a search step so it must actually be collected
below the floor; a plain go step checks horizontal distance and could complete upstairs.

Regenerate the template and optional block previews from the repository root:

```text
python -I tools/last_lantern.py <26.3-client.jar> <preview-directory>
python tools/tale_check.py kingdoms/src/main/resources/data/jaymoskingdoms/tales/the_last_lantern.json
```

The three pictures show the saved grid with markers removed: exterior, room with roof and near walls hidden, cellar
with floor and near walls hidden. They use flat block colours and full cubes, not Minecraft's textured models.
Placement, gate opening, sleep, clue pickup and the tale still need a game test.
