---
name: no-static-itemstacks
description: Never build ItemStacks in static fields/initialisers (crashes 26.3 at startup); boot a dev server before merging new registries
metadata:
  type: feedback
  modified: 2026-10-21T19:02:26.086Z
---
2026-10-21: the Hornkin PR (#373, Kingdoms 0.7.131) had `static final List<ItemStack> WARES = List.of(new ItemStack(...))`. It compiled, but the game and server crashed at startup ("Components not bound yet") once the class loaded during registration; another thread hotfixed it as Kingdoms 0.7.134 (commit b00fc89f) with Suppliers.
**Why:** in 26.3 an ItemStack needs item components bound, which happens after mod init. A cloud `gradlew build` doesn't catch it.
**How to apply:** keep static item lists as Items or `Supplier<ItemStack>`; for any PR that adds entity types/items/registries, boot `runServer` once in the cloud (see [[cloud-playtest-setup]]) before saying it's ready, and say plainly if it wasn't booted.
