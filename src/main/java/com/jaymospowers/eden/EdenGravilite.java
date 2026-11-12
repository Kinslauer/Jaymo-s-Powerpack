package com.jaymospowers.eden;

import com.jaymospowers.core.Powers;
import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.power.gravity.GravityPower;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A gravity user feels gravilite: the crystal bends the pull the way they do. Within {@link #REACH} blocks, through stone and
 * all, the nearest of it shows to them alone as a few motes rising from it, thicker the closer it is. Nobody else sees it.
 */
public final class EdenGravilite {
	/** How far (blocks) the sense reaches, how often (ticks) it is felt, and the most motes a feel gives. */
	public static final int REACH = 14, EVERY = 30, MOST = 8;
	private static final ParticleOptions MOTE = MagicFx.mote(0x7FD0FF, 0.7F);

	private EdenGravilite() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % EVERY != 0) {
				return;
			}
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (player.isAlive() && !player.isSpectator() && Powers.has(player, GravityPower.INSTANCE)) {
					feel(player);
				}
			}
		});
	}

	private static boolean gravilite(BlockState state) {
		return state.is(Eden.GRAVILITE_SHARDS) || state.is(Eden.GRAVILITE_BLOCK) || state.is(Eden.GRAVILITE_LAMP) || state.is(Eden.GRAVILITE_LANTERN);
	}

	private static void feel(ServerPlayer player) {
		ServerLevel level = player.level();
		BlockPos at = player.blockPosition();
		BlockPos nearest = null;
		double best = Double.MAX_VALUE;
		for (BlockPos pos : BlockPos.betweenClosed(at.offset(-REACH, -REACH, -REACH), at.offset(REACH, REACH, REACH))) {
			if (gravilite(level.getBlockState(pos))) {
				double d = pos.distSqr(at);
				if (d < best) {
					best = d;
					nearest = pos.immutable();
				}
			}
		}
		if (nearest == null) {
			return;
		}
		double near = 1.0 - Math.min(1.0, Math.sqrt(best) / REACH);
		int motes = 1 + (int)Math.round(near * (MOST - 1));
		Vec3 c = Vec3.atCenterOf(nearest);
		level.sendParticles(player, MOTE, false, false, c.x, c.y + 0.5, c.z, motes, 0.3, 0.4, 0.3, 0.02);
	}
}
