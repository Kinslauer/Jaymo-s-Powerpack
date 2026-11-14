# skipping the life story: a Life tab in MCA's creator, a start left to fate, presets

Andy: "people who skip the story go to a new character creator that they can choose everything explicitly except their
power (only affinity) and this can also be saved as a preset", then: "if they hit skip the story, their start is randomized
and ALL creation options are moved to a tab within the MCA creator".
- **Skipping the story goes straight to MCA's creator** (`LifeStoryScreen.tabbed`, `McaDestinyWaitMixin`). "On second
  thought..." (now on the who-are-you page too) no longer rolls a random life and reads it back. The story screen steps
  aside and MCA's creator opens with one more tab beside General, Character and Traits: **Life** (`CreatorTab`,
  `McaCreatorTabMixin`), in MCA's own buttons. Four small pages:
  - **Life**: heritage (every blood open to you, earned ones you have woken included), its look, who raised you (and their
    realm for a noble house), your childhood, class (all of them) and its first spell, element, patron, oath or sworn foe,
    and the power's affinity. Arrows step a row back and on; hovering a row says what it gives (+1 Might, the look's trait).
  - **Harder**: the four flaws and the four hard-road switches, as on the story page.
  - **Nature**: your ideal, bond and flaw.
  - **Presets**: see below, and Randomize (every row rolled again).
- The creator's figure wears the heritage and look as you pick them (`McaEditorHeritageMixin`). Man or woman is MCA's own
  pick again for a skipped story.
- **The power is not picked, only its affinity.** The Affinity row is the story's "Then one day..." answer (a spider's
  bite, a watch that ticked backwards, "Nothing, yet"). It never names the power; that still wakes on its own after the
  usual wait.
- **The start is randomized.** A skipped story is never asked its road: once the creator is done the server takes "Let
  fate decide" for you (`LifeStory.SKIPPED`, `LifeStory.askRoad`), by its usual odds.
- Nothing is sent till the creator is finished: then the story is sealed once, as the rows stand (`LifeStory.Skipped`),
  just before MCA is told the creator is done. What the story had answered before the skip is kept; the rest starts at
  random.
- **Presets** (`CharacterPresets`, `config/jaymoskingdoms-characters.json` on your own computer, up to 32). Type a name and
  Save preset; the load button steps through the saved ones; Delete removes the one named. A preset keeps every row of
  Life, Harder and Nature. It is kept by name, so it loads in any world or server: a realm that world doesn't have, or a
  blood not woken there, falls back to what the tab had. MCA's face, hair, body and name are not in it (MCA's creator has
  its own presets for those).
- With no MCA creator to come (a reincarnation that keeps its body), or if the creator never shows within 8 seconds, the
  same rows are one page of the story screen instead, with the start still fate's.
- Built with Gradle (`compileClientJava`). Not played: the tab's layout in MCA's creator has not been seen in game. The
  skip ask's second line is new text and has no narrator clip (the old `skip_desc` clip is no longer played).
