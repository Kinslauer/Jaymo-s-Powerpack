# stop the server failing to load Kingdoms' Jade plugin at every start
- The server logged "Failed to load plugin from Jaymo's Kingdoms" (NoClassDefFoundError: NarrationSupplier) at every start: Jade loads plugin classes on the server too, and the strangers-and-goblins tooltip callback inside the plugin class reached client-only Minecraft classes. The callback now lives in its own client class that only the client calls; nothing changes in game.
- Built in CI; server start not tested here.
