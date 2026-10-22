package com.jaymospowers.power.chrono;

import com.jaymospowers.JaymosPowers;
import io.netty.buffer.ByteBuf;
import java.util.Set;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
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
 * <li>Running into a wall holding forward, you run straight up it (the client drives it: {@code ChronoSpeedClient}).
 * Since 0.11.40 for as long as you hold forward, with no sideways drift, a hop onto the top, and the body turned onto
 * the wall, legs running, the way Gravity's wall walk stands on it ({@link #WALL_RUN}, drawn by {@code GravityWalkPose}).</li>
 * <li>Sprinting, water holds you up: you run across it (see {@code ChronoWaterRunMixin}).</li>
 * <li>(0.11.40) Holding Shift you walk at your normal speed (no Speed V, no sneaking slowdown): fine control.</li>
 * <li>V again, facing a wall: you vibrate through it, up to {@link #PHASE_DEPTH} blocks thick, to the first space you
 * fit on the other side. Every {@link #PHASE_GAP} ticks at most.</li>
 * </ul>
 * (0.15.97) Velox's Velocity (R, a toggle) runs on all of this too but phasing, in a light blue streak instead of
 * golden ({@link #SKY}): it calls {@link #tick(ServerPlayer, ServerLevel, boolean, int)} and {@link #clear} itself.
 */
public final class ChronoSpeed {
	// ---- tuning ----
	/** Sprinting while overclocked: movement speed x(1 + this), on top of Overclock's Speed V. */
	static final double SPRINT_BOOST = 1.2;
	/** Extra step height while overclocked (vanilla 0.6, so 1.25 in all: a full block without jumping). */
	static final double STEP_BOOST = 0.65;
	/** Wall running: how fast you climb (blocks a tick), and how hard you're held against the wall (blocks a tick into it). */
	public static final double WALL_CLIMB = 0.5;
	public static final double WALL_STICK = 0.08;
	/** Running off the top of a wall: the hop up and over onto it. */
	public static final double WALL_TOP_UP = 0.42, WALL_TOP_IN = 0.3;
	/** How fast the legs pump on a wall run (walk animation per tick). */
	public static final float WALL_STRIDE = 1.3F;
	/** Phasing: how thick a wall you can pass through, how far ahead the wall may start, and the gap between phases. */
	static final int PHASE_DEPTH = 4;
	static final double PHASE_REACH = 1.5;
	static final int PHASE_GAP = 20;
	/** The streak's colours ({@link #FLASH}'s value): Chrono's gold, and Velox's light blue. */
	public static final int GOLD = 0, SKY = 1;
	/** Velox's streak behind you (server particles): a light blue dust. */
	public static final DustParticleOptions SKY_DUST = new DustParticleOptions(0x8FD8FF, 1.2F);

	static final Identifier SPRINT_MODIFIER = JaymosPowers.id("chrono_flash_sprint");
	static final Identifier STEP_MODIFIER = JaymosPowers.id("chrono_flash_step");
	static final Identifier CONTROL_SPEED_MODIFIER = JaymosPowers.id("chrono_flash_control");
	static final Identifier CONTROL_SNEAK_MODIFIER = JaymosPowers.id("chrono_flash_control_sneak");

	/**
	 * The game time the client last saw its own player overclocked (the server's answer comes from {@link ChronoPower}).
	 * Stale after a tick or two, so leaving a world, or losing the power, can't leave it on.
	 */
	public static volatile long clientOverclockedAt = Long.MIN_VALUE;

	/** On a player running up a wall: the wall's side (a horizontal direction's 3D index, into the wall), for everyone to draw it. */
	public static final AttachmentType<Integer> WALL_RUN = AttachmentRegistry.create(JaymosPowers.id("chrono_wall_run"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));

	/**
	 * (0.11.57) On a player while Overclock runs, for everyone to see the speed-force streak, the run and the glyphs
	 * ({@code ChronoStreak}, {@code ChronoRunPose}). Only on while overclocked, never saved. Since 0.15.97 the streak's
	 * colour ({@link #GOLD} or {@link #SKY}).
	 */
	public static final AttachmentType<Integer> FLASH = AttachmentRegistry.create(JaymosPowers.id("chrono_flash"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));

	/** Client → server: running up the wall on side {@code face} (into it), or -1 for off it. */
	public record WallRun(int face) implements CustomPacketPayload {
		public static final Type<WallRun> TYPE = new Type<>(JaymosPowers.id("chrono_wall_run"));
		public static final StreamCodec<ByteBuf, WallRun> CODEC = ByteBufCodecs.VAR_INT.map(WallRun::new, WallRun::face);

		@Override
		public Type<WallRun> type() {
			return TYPE;
		}
	}

	private ChronoSpeed() {
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(WallRun.TYPE, WallRun.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(WallRun.TYPE, (payload, ctx) -> wallRun(ctx.player(), payload.face()));
	}

	private static void wallRun(ServerPlayer player, int face) {
		Direction side = face >= 0 && face < Direction.values().length ? Direction.from3DDataValue(face) : null;
		if (side != null && side.getAxis().isHorizontal() && overclocked(player) && player.isAlive() && !player.isPassenger()) {
			player.setAttached(WALL_RUN, side.get3DDataValue());
			player.resetFallDistance();
		} else if (player.getAttached(WALL_RUN) != null) {
			player.removeAttached(WALL_RUN);
		}
	}

	/** Whether {@code player} is overclocked (or in Velox's Velocity) right now, on either side. */
	public static boolean overclocked(Player player) {
		if (player.level().isClientSide()) {
			// (0.11.27) never seen is Long.MIN_VALUE, and now minus that overflows to a large negative number, which is
			// "<= 2": every client thought its player overclocked, so anyone sprinting ran on water. Ago must be 0..2.
			long ago = player.level().getGameTime() - clientOverclockedAt;
			return player.isLocalPlayer() && ago >= 0L && ago <= 2L;
		}
		return player instanceof ServerPlayer sp && (ChronoPower.overclocked(sp) || velocity(sp));
	}

	/** (0.15.97) Whether {@code player}'s speed is Velox's Velocity (its light blue streak is on), on either side. */
	public static boolean velocity(Player player) {
		Integer palette = player.getAttached(FLASH);
		return palette != null && palette == SKY;
	}

	/** Whether {@code player} is overclocked as any client sees it (synced {@link #FLASH}, or the local player's own state). */
	public static boolean flashing(Player player) {
		return player.getAttached(FLASH) != null || overclocked(player);
	}

	/** Water and (since 0.11.40) lava hold up an overclocked player who's sprinting. Slow down on lava and you sink in. */
	public static boolean runsOn(Player player, FluidState fluid) {
		return (fluid.is(FluidTags.WATER) || fluid.is(FluidTags.LAVA)) && runsOnFluids(player);
	}

	/** Overclocked and sprinting (not sneaking): the water run is on (which fluids hold you is {@link #runsOn}'s). */
	public static boolean runsOnFluids(Player player) {
		return player.isSprinting() && !player.isShiftKeyDown() && overclocked(player);
	}

	/** Each tick (server): the sprint and step modifiers follow Overclock and sprinting; the streak behind you. */
	static void tick(ServerPlayer player, ServerLevel level, boolean overclocked) {
		tick(player, level, overclocked, GOLD);
	}

	/** As {@link #tick(ServerPlayer, ServerLevel, boolean)}, the streak in {@code palette}'s colours ({@link #GOLD}, {@link #SKY}). */
	public static void tick(ServerPlayer player, ServerLevel level, boolean overclocked, int palette) {
		control(player, overclocked && player.isShiftKeyDown() && !player.isPassenger());
		Integer shown = player.getAttached(FLASH);
		if (overclocked && (shown == null || shown != palette)) {
			player.setAttached(FLASH, palette);
		} else if (!overclocked && shown != null) {
			player.removeAttached(FLASH);
		}
		boolean sprinting = overclocked && player.isSprinting();
		modifier(player.getAttribute(Attributes.MOVEMENT_SPEED), SPRINT_MODIFIER, SPRINT_BOOST, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, sprinting);
		modifier(player.getAttribute(Attributes.STEP_HEIGHT), STEP_MODIFIER, STEP_BOOST, AttributeModifier.Operation.ADD_VALUE, overclocked);
		if (sprinting) {
			// a golden (or light blue) streak where you just were
			Vec3 was = new Vec3(player.xo, player.yo, player.zo);
			Vec3 moved = player.position().subtract(was);
			if (moved.lengthSqr() > 0.04) {
				for (int i = 0; i < 3; i++) {
					Vec3 at = was.add(moved.scale(i / 3.0));
					ChronoFx.particles(level, player, ParticleTypes.ELECTRIC_SPARK, at.x, at.y + 0.3 + level.getRandom().nextDouble() * 1.2, at.z, 1, 0.05, 0.05, 0.05, 0.0);
				}
				if (palette == SKY) {
					ChronoFx.particles(level, player, SKY_DUST, was.x, was.y + 0.9, was.z, 2, 0.2, 0.4, 0.2, 0.0);
				} else {
					ChronoFx.particles(level, player, ParticleTypes.WAX_ON, was.x, was.y + 0.9, was.z, 1, 0.2, 0.4, 0.2, 0.0);
				}
			}
			FluidState under = level.getFluidState(player.blockPosition().below());
			if (player.onGround() && under.is(FluidTags.LAVA)) {
				// lava spits behind your heels
				ChronoFx.particles(level, player, ParticleTypes.LAVA, player.getX(), player.getY() + 0.1, player.getZ(), 1, 0.3, 0.0, 0.3, 0.0);
				ChronoFx.particles(level, player, ParticleTypes.SMOKE, player.getX(), player.getY() + 0.1, player.getZ(), 3, 0.3, 0.0, 0.3, 0.02);
			} else if (player.isInWater() || player.onGround() && !under.isEmpty()) {
				ChronoFx.particles(level, player, ParticleTypes.SPLASH, player.getX(), player.getY() + 0.1, player.getZ(), 6, 0.3, 0.0, 0.3, 0.1);
			}
		}
		if (overclocked && (player.horizontalCollision || player.getAttached(WALL_RUN) != null)) {
			// running up a wall is no fall
			player.resetFallDistance();
		}
		if (!overclocked && player.getAttached(WALL_RUN) != null) {
			player.removeAttached(WALL_RUN);
		}
	}

	/**
	 * (0.11.40) Shift while overclocked: back to plain walking speed. Whatever is speeding you up (Speed V and the rest)
	 * is scaled away, and sneaking no longer slows you, so you move exactly as you'd walk. Updated only when it changes.
	 */
	private static void control(ServerPlayer player, boolean on) {
		AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		AttributeInstance sneak = player.getAttribute(Attributes.SNEAKING_SPEED);
		if (!on) {
			if (speed != null && speed.hasModifier(CONTROL_SPEED_MODIFIER)) {
				speed.removeModifier(CONTROL_SPEED_MODIFIER);
			}
			if (sneak != null && sneak.hasModifier(CONTROL_SNEAK_MODIFIER)) {
				sneak.removeModifier(CONTROL_SNEAK_MODIFIER);
			}
			return;
		}
		if (speed != null) {
			AttributeModifier old = speed.getModifier(CONTROL_SPEED_MODIFIER);
			double without = speed.getValue() / (1.0 + (old == null ? 0.0 : old.amount()));
			double amount = without > speed.getBaseValue() + 1.0E-4 ? speed.getBaseValue() / without - 1.0 : 0.0;
			if (old == null || Math.abs(old.amount() - amount) > 1.0E-3) {
				speed.removeModifier(CONTROL_SPEED_MODIFIER);
				speed.addTransientModifier(new AttributeModifier(CONTROL_SPEED_MODIFIER, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			}
		}
		if (sneak != null) {
			AttributeModifier old = sneak.getModifier(CONTROL_SNEAK_MODIFIER);
			double without = sneak.getValue() - (old == null ? 0.0 : old.amount());
			double amount = Math.max(0.0, 1.0 - without);
			if (old == null || Math.abs(old.amount() - amount) > 1.0E-3) {
				sneak.removeModifier(CONTROL_SNEAK_MODIFIER);
				sneak.addTransientModifier(new AttributeModifier(CONTROL_SNEAK_MODIFIER, amount, AttributeModifier.Operation.ADD_VALUE));
			}
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

	/** Take both modifiers off (power lost, Overclock cut short, Velocity switched off). */
	public static void clear(ServerPlayer player) {
		control(player, false);
		if (player.getAttached(FLASH) != null) {
			player.removeAttached(FLASH);
		}
		if (player.getAttached(WALL_RUN) != null) {
			player.removeAttached(WALL_RUN);
		}
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
