package com.jaymospowers.client.compat;

import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * (0.15.47) Jade's panel shows only for things that live or lie in the world: players, mobs and items on the ground.
 * Looking at a block or a fluid shows nothing. Done as a ray-trace callback rather than Jade's "display blocks" option, so
 * it holds for every player whatever their own Jade settings say. Only loaded by Jade (the {@code jade} entrypoint).
 */
@WailaPlugin
public final class JadeEntitiesOnly implements IWailaPlugin {
	@Override
	public void registerClient(IWailaClientRegistration registration) {
		registration.addRayTraceCallback((hit, accessor, original) -> accessor instanceof BlockAccessor ? null : accessor);
	}
}
