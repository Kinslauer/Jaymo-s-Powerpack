package com.jaymoskingdoms;

import net.fabricmc.api.DedicatedServerModInitializer;

/** What must wait until every mod's main entrypoint has run, on a dedicated server (the client does the same in its own). */
public final class JaymosKingdomsServer implements DedicatedServerModInitializer {
	@Override
	public void onInitializeServer() {
		com.jaymoskingdoms.blossomland.BlossomWilds.addBiomes();
	}
}
