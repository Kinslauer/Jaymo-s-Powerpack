package com.jaymospowers.client;

import com.jaymospowers.power.web.WebParkour;
import com.jaymospowers.power.web.WebSymbiote;
import com.jaymospowers.power.web.WebSymbioteState;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.player.Player;

/**
 * (0.13.17) Our wall climbs, as Not Enough Animations sees them: clinging with webbed fists ({@link WebParkour#CLING}),
 * and the symbiote's Wall Crawl on a wall. With NEA installed, its ladder animation plays on them (its hands over hands
 * and stepping feet, as on a vine; {@code NeaLadderMixin}) and our own climbing pose stands aside.
 */
public final class WallClimbs {
	public static final boolean NEA = FabricLoader.getInstance().isModLoaded("notenoughanimations");

	private WallClimbs() {
	}

	/** Climbing a wall just now (any player this client sees). */
	public static boolean climbing(Player player) {
		if (player.onGround() || player.isPassenger() || player.isInWater()) {
			return false;
		}
		Integer move = player.getAttached(WebParkour.MOVE);
		if (move != null && WebParkour.kind(move) == WebParkour.CLING) {
			return true;
		}
		return WebSymbiote.isBonded(player) && WebSymbiote.state(player).has(WebSymbioteState.CRAWL) && WebSymbiote.touchingWall(player);
	}
}
