package com.jaymospowers;

import net.fabricmc.api.DedicatedServerModInitializer;

/** Dedicated server only: runs after every mod's main entrypoint, before the server reads its data. */
public final class JaymosPowersServer implements DedicatedServerModInitializer {
	@Override
	public void onInitializeServer() {
		com.jaymospowers.armory.TaczGuns.trimDefaultPack();
	}
}
