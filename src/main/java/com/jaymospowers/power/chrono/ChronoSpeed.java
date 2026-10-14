package com.jaymospowers.power.chrono;

import com.jaymospowers.JaymosPowers;
import java.util.Set;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * (0.10.28) The speedster half of Overclock (V), after the Flash. While Overclock runs:
 * <ul>
 * <li>Sprinting, you're far faster still ({@link #SPRINT_BOOST}) and stride up whole blocks without jumping.</li>
 * <li>Running into a wall holding forward, you run straight up it (the client drives it: {@code ChronoSpeedClient}).</li>
 * <li>Sprinting, water holds you up: you run across it (see {@code ChronoWaterRunMixin}).</li>
 * <li>V again, facing a wall: you vibrate through it, up to {@link #PHASE_DEPTH} blocks thick, to the first space you
 * fit on the other side. Every {@link #PHASE_GAP} ticks at most.</li>
 * </ul>
 */
public final class ChronoSpeed {
	// ---- tuning ----
	/** Sprinting while overclocked: movement speed x(1 + this), on top of Overclock's Speed V. */
	static final double SPRINT_BOOST = 1.2;
	/** Extra step height while overclocked (vanilla 0.6, so 1.25 in all: a full block without jumping). */
	static final double STEP_BOOST = 0.65;
	/** Wall running: how fast you climb (blocks a tick), and for how long at most before you have to touch ground again. */
	public static final double WALL_CLIMB = 0.5;
	public static final int WALL_TICKS = 60;
	/** Phasing: how thick a wall you can pass through, how far ahead the wall may start, and the gap between phases. */
	static final int PHASE_DEPTH = 4;
	static final double PHASE_REACH = 1.5;
	static final int PHASE_GAP = 20;

	static final Identifier SPRINT_MODIFIER = JaymosPowers.id("chrono_flash_sprint");
	static final Identifier STEP_MODIFIER = JaymosPowers.id("chrono_flash_step");

	/**
	 * The game time the client last saw its own player overclocked (the server's answer comes from {@link ChronoPower}).
	 * Stale after a tick or two, so leaving a world, or losing the power, can't leave it on.
	 */
	public static volatile long clientOverclockedAt = Long.MIN_VALUE;

	private ChronoSpeed() {
	}

	/** Whether {@code player} is overclocked right now, on either side. */
	public static boolean overclocked(Player player) {
		if (player.level().isClientSide()) {
			return player.isLocalPlayer() && player.level().getGameTime() - clientOverclockedAt <= 2L;
		}
		return player instanceof ServerPlayer sp && ChronoPower.overclocked(sp);
	}

	/** Water holds up an overclocked player who's sprinting (lava doesn't: they'd still burn). */
	public static boolean runsOn(Player player, FluidState fluid) {
		return fluid.is(FluidTags.WATER) && runsOnFluids(player);
	}

	/** Overclocked and sprinting (not sneaking): the water run is on (which fluids hold you is {@link #runsOn}'s). */
	public static boolean runsOnFluids(Player player) {
		return player.isSprinting() && !player.isShiftKeyDown() && overclocked(player);
	}

	/** Each tick (server): the sprint and step modifiers follow Overclock and sprinting; the streak behind you. */
	static void tick(ServerPlayer player, ServerLevel level, boolean overclocked) {
		boolean sprinting = overclocked && player.isSprinting();
		modifier(player.getAttribute(Attributes.MOVEMENT_SPEED), SPRINT_MODIFIER, SPRINT_BOOST, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, sprinting);
		modifier(player.getAttribute(Attributes.STEP_HEIGHT), STEP_MODIFIER, STEP_BOOST, AttributeModifier.Operation.ADD_VALUE, overclocked);
		if (sprinting) {
			// a golden streak where you just were
			Vec3 was = new Vec3(player.xo, player.yo, player.zo);
			Vec3 moved = player.position().subtract(was);
			if (moved.lengthSqr() > 0.04) {
				for (int i = 0; i < 3; i++) {
					Vec3 at = was.add(moved.scale(i / 3.0));
					ChronoFx.particles(level, player, ParticleTypes.ELECTRIC_SPARK, at.x, at.y + 0.3 + level.getRandom().nextDouble() * 1.2, at.z, 1, 0.05, 0.05, 0.05, 0.0);
				}
				ChronoFx.particles(level, player, ParticleTypes.WAX_ON, was.x, was.y + 0.9, was.z, 1, 0.2, 0.4, 0.2, 0.0);
			}
			if (player.isInWater() || player.onGround() && !level.getFluidState(player.blockPosition().below()).isEmpty()) {
				ChronoFx.particles(level, player, ParticleTypes.SPLASH, player.getX(), player.getY() + 0.1, player.getZ(), 6, 0.3, 0.0, 0.3, 0.1);
			}
		}
		if (overclocked && player.horizontalCollision) {
			// running up a wall is no fall
			player.resetFallDistance();
		}
	}

	private static void modifier(AttributeInstance attribute, Identifier id, double amount, AttributeModifier.Operation op, boolean on) {
		if (attribute == null || attribute.hasModifier(id) == on) {
			return;
		}
		if (on) {
			// transient: never saved, so a crash or relog can't leave it behind
			attribute.addTransientModifier(new AttributeModifier(id, amount, op));
		} else {
			attribute.removeModifier(id);
		}
	}

	/** Take both modifiers off (power lost, Overclock cut short). */
	static void clear(ServerPlayer player) {
		modifier(player.getAttribute(Attributes.MOVEMENT_SPEED), SPRINT_MODIFIER, SPRINT_BOOST, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, false);
		modifier(player.getAttribute(Attributes.STEP_HEIGHT), STEP_MODIFIER, STEP_BOOST, AttributeModifier.Operation.ADD_VALUE, false);
	}

	/**
	 * V while overclocked: vibrate through the wall ahead. True if you went through. The wall must start within
	 * {@link #PHASE_REACH} of you; you come out at the first spot you fit, at most {@link #PHASE_DEPTH} blocks in.
	 */
	static boolean phase(ServerPlayer player) {
		ServerLevel level = player.level();
		Vec3 look = player.getLookAngle().multiply(1.0, 0.0, 1.0);
		if (look.lengthSqr() < 1.0E-4 || player.isPassenger()) {
			return false;
		}
		look = look.normalize();
		Vec3 here = player.position();
		AABB box = player.getBoundingBox();
		// a wall right ahead?
		double wall = -1.0;
		for (double d = 0.25; d <= PHASE_REACH; d += 0.25) {
			if (!level.noCollision(player, box.move(look.scale(d)))) {
				wall = d;
				break;
			}
		}
		if (wall < 0.0) {
			return false;
		}
		Vec3 out = null;
		for (double d = wall + 0.25; d <= wall + PHASE_DEPTH + 0.75; d += 0.25) {
			Vec3 to = here.add(look.scale(d));
			if (level.noCollision(player, box.move(to.subtract(here))) && level.getWorldBorder().isWithinBounds(to.x, to.z)) {
				out = to;
				break;
			}
		}
		if (out == null) {
			return false;
		}
		ChronoFx.particles(level, player, ParticleTypes.ELECTRIC_SPARK, here.x, here.y + 1.0, here.z, 25, 0.3, 0.7, 0.3, 0.15);
		level.playSound(null, here.x, here.y, here.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 2.0F);
		player.teleportTo(level, out.x, out.y, out.z, Set.of(), player.getYRot(), player.getXRot(), false);
		ChronoFx.particles(level, player, ParticleTypes.ELECTRIC_SPARK, out.x, out.y + 1.0, out.z, 25, 0.3, 0.7, 0.3, 0.15);
		ChronoFx.particles(level, player, ParticleTypes.WAX_ON, out.x, out.y + 1.0, out.z, 12, 0.3, 0.7, 0.3, 0.3);
		level.playSound(null, out.x, out.y, out.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.6F, 2.0F);
		return true;
	}
}
