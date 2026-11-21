# Menu redesign plan (2026-11-18)

Andy's asks (thread "Redesign menus with bigger info panels"):
- "we need a UI Redesign, something like this menus are smaller, and info boxes are bigger, our character display should
  stay along the left of the screen" (reference: a "Choose your lineage" picker: compact two-column list of choices with
  icon + one-line subtitle, a large info panel with Intrinsic / Strengths / Weaknesses / Paths, dark panels, thin gold
  borders).
- "our H menu should encompass everything including the inventroy and be activated with E, it should also contain
  abosultely everything within dropboxes along the top" (reference: a "Codex" character menu).
- He then ticked 26 rows of the pick list https://claude.ai/artifact/FdMtkuyPa51VfZHZsWmv1w (db `picks/andy`). Ideas only
  from Tensura: Celerity (All Rights Reserved): no code, art or names copied.

Mock pictures: project files `art-overhaul/menu-redesign/` (`picker-heritage.png`, `menu-inventory-dropdowns.png`,
`menu-character-sidelist.png`, script `mock.py`).

Rules for every track: screens never cut text (slide/scroll, `Ui.line`/`UiStyle`); never hand-edit `UiStyle`, `Ui`,
`KitchenUi` or `ui/FlatButton` (edit `tools/ui_style/*.txt` / `tools/ui_style.py` and run it); Kingdoms never compiles
against Powers (reflection through `MenuCompat`); new lang keys in the feature's own `lang/parts/*.json`; lore first
(`Lore.md`); change notes in `changes/<mod>/`; no version numbers in comments; build to check.

## Track 1: the menu (Powers `client/menu/`, Kingdoms `MenuCompat`)
Rows: menu_e, menu_dropdowns, menu_sidebar, menu_everything, menu_locked, codex_character, sys_status, look_showcase.
- **Inventory page, E**: the vanilla `InventoryScreen` (survival only; creative stays vanilla) becomes a MenuTabs page
  (`owns: s instanceof InventoryScreen`), first in group You. The bar is drawn over it and clicks on the bar go to the bar.
  E (the inventory key) opens the inventory page as now; on any menu page E or Esc closes the menu. E no longer turns
  pages; Q no longer turns pages either (Q is drop item in the inventory). The wheel over the bar still turns pages.
  H opens the last page.
- **Groups**: every tab gets a group: You, Powers, World, People, Notices. New reflective `MenuTabs.group(id, group)` and
  `MenuTabs.locked(id, Supplier<Component> hint)` (plain types); old `register` signature kept.
- **Dropdown bar** (default): a bar across the top (`STRIP_Y`, `STRIP_H`), one plate per group with a ▾; hover or
  click opens its dropdown list of pages (icon + name), the open page's name shown after the plates. Locked pages greyed,
  hover shows the hint (`UiStyle.tip`).
- **Side list** (switch): a slim column of every page down the left, grouped with small headings, as in the Codex
  screenshot. Pages leave room for it (`MenuTabs.ROOM` top, new `MenuTabs.SIDE` left; 0 when the bar is used).
  Switch: a small button at the bar's/list's end and `/menulook bar|side`, saved in a client config file like
  `/screenlook`.
- **Everything**: every screen a player can open on their own gets a tab (baubles, backpack, wardrobe, quests, lore
  codex, magic, valor, followers, boons, world tier, sound browser...; list each in the PR). Screens needing a block or a
  villager stay out. Their own keys still work and open their tab.
- **Character page** in the Codex shape (see mock): status header (level, heritage + class, title, XP bar); tall figure
  in a box with worn gear slots down both sides (armour + baubles if present, display only), name, heritage, class
  under it, a soft glow behind it and a shadow under the feet; right side panels: power card (power, mastery bar, fate),
  vitals bars (health, ki, mana, stamina, hunger... whatever exists), Attributes and Resistances side by side, Record
  (kills, deaths, days, places found...; only data we already track). Kingdoms supplies its rows through
  `characterRows` (extend with a panel tag if needed, plain types).

## Track 2: picker screens (Kingdoms `HeritageScreen`, `ClassScreen`, `PatronScreen`, `LifeStoryScreen`, `tools/ui_style`)
Rows: pick_layout, pick_paths, pick_become, pick_subtitles, ros_pills, look_frame, card_spec, look_showcase (StoryFigure).
- One shared `client/ui/PickerLayout` (Kingdoms): three columns over the full screen width: figure column on the LEFT
  (as the choice pointed at would make you, `StoryFigure`), a compact two-column list (icon, name, one-line subtitle),
  and a big info panel (icon, name, subtitle, pills, story, labelled sections: Intrinsic, Strengths (green),
  Weaknesses (red), Look, Paths). Info panel scrolls (wheel + bar). The confirm button sits under the figure and names
  the choice ("Become a Stoneborn"). Small screens fall back to fewer list columns / narrower figure, never cut text.
- Used by HeritageScreen, ClassScreen, PatronScreen and LifeStoryScreen's answer pages (question pages, realm pages; the
  road carousel and the summary keep their own shape but get the new frame).
- **Paths**: rows of small icons + a name for what the choice already grows into: heritage looks and earned
  heritages, class options/second classes, patron boons. No new progression (heritage paths are being brainstormed
  separately with Andy).
- **Subtitles**: a `.tag` lang key for every heritage, class, patron and life-story answer, fitting Lore.md.
- **Pills**: `pill(graphics, font, x, y, label, value, color)` in the generated `Ui`/`UiStyle`.
- **Frame**: thin gold frame with small diamonds at the corners and a short accent tick over a panel's title, in
  `tools/ui_style` so every mod's panels share it.
- **Specialization costs** (card_spec): changing a class option after the first pick costs coin and has a wait; mastery
  earned is kept. Use the existing class option/server code; numbers as `static final` constants.

## Track 3: menu pages (after Track 1)
Rows: sys_stats, sys_quests, sys_titles, sys_alerts, sys_rewards.
- Stats: Attributes tab gets + buttons (Shift = 5) and an effect line per attribute.
- Daily task: one small daily task with progress, reset timer, streak, Claim (coin/renown). No penalty.
- Titles: pick which earned title (SkillTitles, JobTitles) shows under your name.
- Notices: the last 50 Tidings/TipCards/rewards/level ups, client-side log, a menu page.
- Rewards: rewards held until claimed (bounty pay, rescue gifts, wages), server side.

## Track 4: cards, rosters, pop-ups (after Track 2)
Rows: card_license, card_meter, ros_roster, ros_arrange, ros_colour, look_titlecards, look_dread.
- Papers card (heritage, class, realm, standing, renown), from the Character page; shown to others.
- Scouter reading screen: "yes scouters should read ki period" (Andy): reads ki/power level of what you point at.
- Followers roster (FollowersScreen/PartyScreen), arrange-a-combination screen, one shared colour picker.
- Title cards for big moments (TitleCard beside TipCard), dread vignette near far stronger foes.

## Open
- Heritage progressions: Andy asked to brainstorm them; ideas posted in the thread, nothing built until he picks.
