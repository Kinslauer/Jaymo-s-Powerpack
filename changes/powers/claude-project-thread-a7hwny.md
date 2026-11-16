# offline accounts can join the server (EasyAuth)
- **EasyAuth 3.4.4** (MIT, NikitaCartes) joins the pack on the server only (modpack/mods, the Deck and Serverfiles; never
  the CurseForge import or your profile). With online-mode still on, premium players are checked with Mojang as before and
  keep their UUIDs and inventories; offline accounts join with a password kept for their name (Jaymo's Kingdoms' sign-in
  screen). build.gradle `serverOnlyMods`.
- **Whitelist helper** `deck/whitelist.py`: `on` whitelists everyone who has ever joined and switches the whitelist on;
  `add <name>` adds a premium name by its Mojang UUID or an offline name by its offline UUID (`--offline` for an
  offline friend whose name a premium account also has); `remove`, `list`. deck/README and the pbdr skill updated.
- Built; not played.
