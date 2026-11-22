# meat hooks take a carried or dragged carcass on any click
- **Meat hooks**: right-clicking a meat hook while carrying or dragging a carcass now hangs it, wherever in the hook's block space you click (the hook, the air around it, the beam above). Before, your own game let go of the carcass before the click ever reached the server, unless you clicked a butcher block, so the hook never took it. Out of reach, you keep hold and are told to get under the hook.
- `MinecraftUseMixin` (client: walks the view for a hook, sends `HangOn`), `Payloads.HangOn`, `JaymosKitchen` (its receiver).
- Built by CI; not played.
