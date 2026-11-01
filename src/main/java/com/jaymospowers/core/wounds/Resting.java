package com.jaymospowers.core.wounds;

import com.jaymospowers.core.Meditate;
import com.jaymospowers.core.Stance;
import com.jaymospowers.frost.Freezing;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * (0.19.20) Rest by a fire and soaking in a hot spring: the gentle, slow kinds of healing that never mend a bone.
 * <ul>
 * <li><b>Campfire rest:</b> sneaking, in the low crouch or meditating, hardly moving, within {@link #FIRE_RANGE} blocks of
 * a lit campfire: every {@link #REST_INTERVAL} ticks the most hurt part heals {@link #REST_HEAL}, never above
 * {@link #SELF_HEAL_CAP} of that part, and the pain eases ({@link Pain#ease}). Bones are never set by it.</li>
 * <li><b>Hot spring:</b> in water with a magma block no more than {@link #SPRING_DEPTH} blocks under your feet (the pools
 * {@code HotSprings} generates, or one you make): the pain eases, frostbite and the Freezing effect thaw, short bouts of
 * Nausea and Slowness (a daze) clear, and every {@link #SOAK_HEAL_INTERVAL} ticks the most hurt part heals
 * {@link #SOAK_HEAL}, to the same cap.</li>
 * </ul>
 * The ember and steam particles are the client's ({@code RestClient}); the server adds a puff over a soaker.
 */
public final class Resting {
	// ---- tuning ----
	/** (0.19.20) Self-care stops at this share of a part (Kitchen's {@code Stomach.SELF_HEAL_CAP}, kept here so Powers doesn't need Kitchen). */
	public static final float SELF_HEAL_CAP = 0.6F;
	/** (0.19.20) How far from a lit campfire (blocks) rest counts. */
	public static final int FIRE_RANGE = 3;
	/** (0.19.20) Ticks between the heals of a rest, and what each heals (health points). */
	public static final int REST_INTERVAL = 100;
	public static final float REST_HEAL = 0.75F;
	/** (0.19.20) How long each rest or soak eases the pain for (ticks), refreshed every second. */
	public static final int EASE_TICKS = 100;
	/** (0.19.20) The most a player may have moved in a second (blocks, squared) and still count as keeping still. */
	public static final double STILL_DISTANCE_SQR = 0.04;
	/** (0.19.20) How far under your feet the magma may lie for a soak (blocks). */
	public static final int SPRING_DEPTH = 3;
	/** (0.19.20) Ticks between the heals of a soak, and what each heals (health points). */
	public static final int SOAK_HEAL_INTERVAL = 40;
	public static final float SOAK_HEAL = 0.5F;
	/** (0.19.20) Frozen ticks a second of soaking thaws (full freeze is 140). */
	public static final int SOAK_THAW = 60;
	/** (0.19.20) A Nausea or Slowness shorter than this (ticks) is a daze, and a soak clears it. */
	public static final int DAZE_MAX_TICKS = 400;

	private static final Map<ServerPlayer, Vec3> LAST = new WeakHashMap<>();

	private Resting() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Resting::tick);
	}

	// ---- queries (both sides) ---------------------------------------------------------------------------------------------

	/** Whether {@code state} is a lit campfire (or soul campfire). */
	public static boolean litCampfire(BlockState state) {
		return state.getBlock() instanceof CampfireBlock && state.getValue(CampfireBlock.LIT);
	}

	/** Whether a lit campfire is within {@link #FIRE_RANGE} blocks of {@code pos}. */
	public static boolean nearFire(Level level, BlockPos pos) {
		for (BlockPos at : BlockPos.betweenClosed(pos.offset(-FIRE_RANGE, -FIRE_RANGE, -FIRE_RANGE), pos.offset(FIRE_RANGE, FIRE_RANGE, FIRE_RANGE))) {
			if (at.distSqr(pos) <= FIRE_RANGE * FIRE_RANGE && litCampfire(level.getBlockState(at))) {
				return true;
			}
		}
		return false;
	}

	/** Whether there is a magma block within {@link #SPRING_DEPTH} blocks under {@code feet} (the block the feet are in). */
	public static boolean magmaBelow(Level level, BlockPos feet) {
		for (int i = 1; i <= SPRING_DEPTH; i++) {
			if (level.getBlockState(feet.below(i)).is(Blocks.MAGMA_BLOCK)) {
				return true;
			}
		}
		return false;
	}

	/** Whether {@code player} sits low: sneaking, in the low crouch, or meditating. */
	public static boolean restingPose(ServerPlayer player) {
		return player.isShiftKeyDown() || player.hasAttached(Stance.LOW_CROUCH) || player.getAttached(Meditate.STATE) != null;
	}

	// ---- the tick ---------------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 20 != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Vec3 now = player.position();
			Vec3 was = LAST.put(player, now);
			if (!player.isAlive() || player.isCreative() || player.isSpectator() || !(player.level() instanceof ServerLevel level)) {
				continue;
			}
			boolean still = was != null && was.distanceToSqr(now) < STILL_DISTANCE_SQR;
			if (still && restingPose(player) && nearFire(level, player.blockPosition())) {
				rest(player, server.getTickCount());
			}
			if (player.isInWater() && magmaBelow(level, player.blockPosition())) {
				soak(player, level, server.getTickCount());
			}
		}
	}

	private static void rest(ServerPlayer player, int tick) {
		Pain.ease(player, EASE_TICKS);
		if (tick % REST_INTERVAL == 0) {
			healWorst(player, REST_HEAL);
		}
	}

	private static void soak(ServerPlayer player, ServerLevel level, int tick) {
		Pain.ease(player, EASE_TICKS);
		if (player.getTicksFrozen() > 0) {
			player.setTicksFrozen(Math.max(0, player.getTicksFrozen() - SOAK_THAW));
		}
		if (player.hasEffect(Freezing.FREEZING)) {
			player.removeEffect(Freezing.FREEZING);
		}
		MobEffectInstance nausea = player.getEffect(MobEffects.NAUSEA);
		if (nausea != null && nausea.getDuration() <= DAZE_MAX_TICKS) {
			player.removeEffect(MobEffects.NAUSEA);
		}
		MobEffectInstance slow = player.getEffect(MobEffects.SLOWNESS);
		if (slow != null && slow.getDuration() <= DAZE_MAX_TICKS) {
			player.removeEffect(MobEffects.SLOWNESS);
		}
		if (tick % SOAK_HEAL_INTERVAL == 0) {
			healWorst(player, SOAK_HEAL);
		}
		level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getEyeY() + 0.3, player.getZ(), 2, 0.3, 0.1, 0.3, 0.01);
	}

	/**
	 * Heals the most hurt part that is below {@link #SELF_HEAL_CAP} and able to heal by up to {@code amount} (never above the
	 * cap, and a broken limb that hasn't been set only to what its break allows, so bones are never set). True if any healed.
	 */
	public static boolean healWorst(ServerPlayer player, float amount) {
		BodyPlan plan = Wounds.plan(player);
		if (plan == null) {
			return false;
		}
		List<Integer> hurt = new ArrayList<>();
		for (int i = 0; i < plan.size(); i++) {
			if (!Breaks.missing(player, plan.part(i)) && Wounds.fraction(player, plan.part(i)) < SELF_HEAL_CAP - 0.001F) {
				hurt.add(i);
			}
		}
		hurt.sort((a, b) -> Float.compare(Wounds.fraction(player, plan.part(a)), Wounds.fraction(player, plan.part(b))));
		for (int i : hurt) {
			BodyPart part = plan.part(i);
			float room = Wounds.cap(player, plan, i) * SELF_HEAL_CAP - Wounds.points(player, part);
			if (room > 0.001F && Wounds.healPart(player, part, Math.min(amount, room)) > 0.0F) {
				return true;
			}
		}
		return false;
	}
}
