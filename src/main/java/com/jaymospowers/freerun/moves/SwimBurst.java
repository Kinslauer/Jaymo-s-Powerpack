package com.jaymospowers.freerun.moves;

import com.jaymospowers.freerun.Freerun;
import com.jaymospowers.stamina.Feathers;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * The swim burst (Andy's pick from Moves Like Mafuyu's swimming boost; the code is ours): tap Sprint while swimming and
 * you surge forward the way you look for a moment, at the price of a little air ({@link #AIR_COST}) and
 * {@link #COST} feathers. It is a one-tap surge, not {@link FastSwim}, which is a sustained faster swim after a long sprint
 * swim: both can run together (the surge lifts FastSwim's speed cap while it lasts, {@code RunMovesClient}). The client
 * throws the body; here it is paid for and shown.
 */
public final class SwimBurst {
	// ===== the knobs ================================================================================================
	/** Feathers (half-feathers) a burst costs. */
	public static final int COST = 2;
	/** Air supply (ticks; 300 is a full breath) a burst costs, and the least air that must be left after it (no drowning on one). */
	public static final int AIR_COST = 30, AIR_LEFT = 40;
	/** The surge: added along your look (blocks a tick), and the fastest you may be going after it. */
	public static final double PUSH = 0.5, MAX_SPEED = 0.95;
	/** How long (ticks) the surge lifts FastSwim's own speed cap. */
	public static final int SURGE_TICKS = 14;
	/** Ticks before another burst (the client's wait; the server allows a little under it for lag). */
	public static final int COOLDOWN = 30;
	static final int GAP_TICKS = 24;
	// ================================================================================================================

	private static final Map<UUID, Long> LAST = new HashMap<>();

	private SwimBurst() {
	}

	/** Whether {@code player} has the air left for a burst (both sides). */
	public static boolean hasAir(net.minecraft.world.entity.player.Player player) {
		return player.getAirSupply() - AIR_COST >= AIR_LEFT;
	}

	static void burst(ServerPlayer player) {
		long now = player.level().getGameTime();
		Long last = LAST.get(player.getUUID());
		if (last != null && now - last < GAP_TICKS || !player.isInWater() || player.isPassenger() || !Freerun.canMove(player) || !hasAir(player)) {
			return;
		}
		if (!Feathers.spend(player, COST)) {
			return;
		}
		LAST.put(player.getUUID(), now);
		if (LAST.size() > 256) {
			LAST.entrySet().removeIf(e -> now - e.getValue() > 1200L);
		}
		player.setAirSupply(player.getAirSupply() - AIR_COST);
		Vec3 back = player.getLookAngle().scale(-0.8);
		player.level().sendParticles(ParticleTypes.BUBBLE, player.getX() + back.x, player.getY() + 0.3 + back.y, player.getZ() + back.z, 14, 0.25, 0.2, 0.25, 0.06);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMBIENT_UNDERWATER_ENTER, SoundSource.PLAYERS, 0.9F, 0.8F);
		player.addTag(Freerun.XP_TAG);
	}
}
