# The game starts again

- Fix: every client crashed at startup ("Mixin transformation of com.jaymospowers.client.fishing.mixin.HookBaitState
  failed"). `HookBaitState`, the interface that carries a hook's bait on its render state, sat in the fishing mixin
  package, and Mixin refuses to load a plain class from a mixin package. Moved to `com.jaymospowers.client.fishing`.
