# Brief for the agents building powers

You are one of four agents building **Jaymo's Powers**, a Fabric mod for Minecraft Java **26.3**. Each agent builds one
power, in parallel, in its own git worktree. The lead (who wrote the core) merges your branch afterwards, so stay
inside the files you own.

The modpack is this mod plus the user's own Jaymo's Kitchen mod. Every player awakens a random power at some point
in survival, or picks one in creative. The user said: "just make it simple for now, we will upgrade em all later."
So aim for a small, solid, fun first version that works. It should have good feel (sounds, particles and a HUD) and
no half-finished systems.

## Environment
- Windows 10, i5-6600K (4 cores), 16 GB RAM, GTX 960. JDK: Temurin 25.
- Build (PowerShell, from your worktree root):
  ```
  $env:JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot"; $env:GRADLE_USER_HOME="E:\gradle-home"; .\gradlew.bat build --console=plain
  ```
  It must end with `BUILD SUCCESSFUL`. Build output goes to `E:\jaymospowers-build\<your worktree folder name>`.
- **Do not run `runClient` or `runServer`.** Four agents share 16 GB of RAM. The lead playtests after merging.
- Minecraft 26.3 ships **unobfuscated**, so it uses Mojang names directly with no mappings.
  - Decompiled sources: `E:\mcsrc-26.3\common\...` and `E:\mcsrc-26.3\client\...`. **Grep them for every API you use.
    Don't guess.** Many names changed in 26.x.
  - Fabric API jars (no sources) are under `E:\gradle-home\caches\modules-2\files-2.1\net.fabricmc.fabric-api\`. Read
    their signatures with `"C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot\bin\javap.exe" -cp <jar> <class>`.
- Worked examples of 26.3 code (items, entities, payloads, attachments, screens, mixins, renderers, HUD-style
  drawing) are in the user's other mod, **read-only**: `C:\Users\toopo\OneDrive\Desktop\Test Mod\jaymos-kitchen\src`.
  Its `MODLOG.md` has a "26.3 API facts" section. Some known facts:
  - `ResourceLocation` is now `Identifier`.
  - GUI and HUD drawing goes through `extractRenderState(GuiGraphicsExtractor ...)`, with `graphics.fill`, `text`,
    `centeredText` and `item`. **Colours are ARGB, so include `0xFF` alpha.**
  - Key codes are SDL-style. Use the `InputConstants.KEY_*` constants, never raw GLFW numbers.
  - `ServerPlayer.level()` returns `ServerLevel`, and `serverLevel.sendParticles(...)` reaches every nearby client.
  - Items register with
    `Registry.register(BuiltInRegistries.ITEM, key, factory.apply(new Item.Properties().setId(key)))`, where
    `key = ResourceKey.create(Registries.ITEM, JaymosPowers.id(...))`.
  - Item assets need **both** `assets/jaymospowers/items/<id>.json`, which is
    `{"model":{"type":"minecraft:model","model":"jaymospowers:item/<id>"}}`, and `models/item/<id>.json`.
  - Creative tabs: `CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.X).register(output -> output.accept(item))`.

## Read the core first
- `src/main/java/com/jaymospowers/api/Power.java` is the server half of a power. It has hooks for `onGained`,
  `onLost`, `tick`, `onKey(player, key, down)`, `allowsFloating` and `skipsMovementCheck`.
- `src/main/java/com/jaymospowers/api/PowerKey.java` defines the four ability keys `ABILITY_1..4`. They default to
  **R, G, V and B** and can be rebound.
- `src/main/java/com/jaymospowers/core/Powers.java` holds the registry. `Powers.of(player)` works on both sides,
  because the power is a synced attachment. `Powers.has(player, power)` is also there.
- `src/main/java/com/jaymospowers/core/PowerInput.java`: `PowerInput.isDown(player, key)` reports held keys on the
  server.
- `src/client/java/com/jaymospowers/client/PowerClient.java` is the client half, with `tick`, `onKey`, `renderHud`
  and `onActiveChanged`. The core sends key events to **both** halves. `JaymosPowersClient.keyMapping(key)` gives you
  the key binding, for showing the bound key in your HUD.
- The core already draws a badge in the top-left of the HUD (icon and name, about 24 px tall). Keep your HUD clear of
  it: use the area above the hotbar or near the crosshair.
- `src/main/java/com/jaymospowers/mixin/ServerGamePacketListenerImplMixin.java` uses `allowsFloating` and
  `skipsMovementCheck` so that powers don't get players kicked or rubber-banded on dedicated servers. Return true
  from them while your movement ability is active.
- The powers screen (H) lists each power with its description and key hints from lang.

## Rules
1. **Only touch the files you own** (listed in your task). Never edit the core, `build.gradle`, `fabric.mod.json`,
   other powers' files, or the shared `lang/parts/core.json`. If you need something from the core, work around it
   inside your package and mention it in your final report.
2. **Registry, packet and attachment ids** must start with `<power>_`, for example `jaymospowers:web_line`. That keeps
   them from clashing with the other agents' ids. (The arsenal agent may give its weapon items plain weapon names.)
3. **Lang:** use only `src/main/resources/assets/jaymospowers/lang/parts/<power>.json`, which the build merges into
   `en_us.json`. It must include:
   - `power.jaymospowers.<power>` (the name)
   - `.desc` (at most two short sentences)
   - `.key.ability_1` … `.key.ability_4` (short, e.g. "Hold: Tailwind (speed)"; leave out keys you don't use)
   - any item, entity, message and sound subtitle names you add
4. **Client/server split:**
   - Code under `src/main` must never reference `net.minecraft.client.*`, because it would crash dedicated servers.
   - Client code goes in `src/client/java/com/jaymospowers/client/power/<power>/`.
   - Mixins go in your own packages, listed in your own configs: `jaymospowers.<power>.mixins.json` and
     `jaymospowers.<power>.client.mixins.json`. These are already registered in `fabric.mod.json`.
5. **Movement:**
   - Player motion is client-authoritative. Move the local player in `PowerClient.tick` with
     `player.setDeltaMovement(...)` and similar.
   - Fall damage is decided on the server. While your movement is active, and until the player lands after it, call
     `player.resetFallDistance()` (check the exact name) server-side in `Power.tick`.
6. **Multiplayer must work** on a dedicated server with several players.
   - The server is authoritative for damage, cooldowns, items and effects.
   - Other players should see your effects, through server `sendParticles` or a synced attachment or packet.
7. **Art:**
   - All original. Generate pixel art with Python + Pillow (installed), as in the kitchen's `tools/gen_assets.py`.
   - Put your generator in `tools/gen_<power>.py` and commit both the script and its output PNG/JSON files.
   - Never download or copy other mods' assets.
   - Use vanilla sounds and particles; don't add custom sound files.
8. Give every ability a **cooldown**, shown on your HUD. Keep it survival-fair and don't grief: break blocks only if
   it's clearly the point.
9. Write `docs/<power>.md`. Cover the design, controls, tuning constants, how to test (`/power set @s <power>` plus any
   `/give` commands), and known limitations or upgrade ideas.
10. When it builds cleanly, commit everything in your worktree:
    ```
    git add -A; git -c user.name="toopo" -c user.email="toopoint09@gmail.com" commit -m "<Power>: <summary>" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
    ```
11. **Final report** (your last message, kept concise):
    - your branch name and commit hash
    - the files you added
    - the controls
    - test steps
    - anything you're unsure works at runtime (you can't playtest, so flag the risky parts)
    - any wishes for the core
