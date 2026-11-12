package com.jaymospowers;

import net.fabricmc.api.DedicatedServerModInitializer;

/** Dedicated server only: runs after every mod's main entrypoint, before the server reads its data. */
public final class JaymosPowersServer implements DedicatedServerModInitializer {
	@Override
	public void onInitializeServer() {
		com.jaymospowers.armory.TaczGuns.trimDefaultPack();
		com.jaymospowers.power.zfighter.ZFighterDemonRealm.addBiomes();
		// (and the tundra's place in the Overworld, for the same reason)
		com.jaymospowers.tundra.world.TundraWorld.place();
	}
}
