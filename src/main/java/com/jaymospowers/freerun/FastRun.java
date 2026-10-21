package com.jaymospowers.freerun;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Ledge;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * (0.15.11) The fast run, for everyone (no perk): after {@link #DELAY} ticks of unbroken sprinting you break into a faster
 * run, {@link #BONUS} more movement speed (an attribute modifier, so the client feels it too), a forward lean (see
 * {@code client/freerun/RunPose}) and dust at the feet. It ends on: no longer sprinting, a turn sharper than
 * {@link #TURN_DEGREES} within a few ticks, taking a hit, hunger at {@link #MIN_FOOD} or below, or entering water.
 * Attacking out of it does not end it. Freerunner rank 1 makes the bonus {@link #BONUS_RANK1}; rank 2 shortens the delay
 * to {@link #DELAY_RANK2}. A little extra hunger every tick; no stamina. Behaviour inspired by ParCool (alRex-U); no code taken.
 */
public final class FastRun {
	// ===== feel: the knobs to tweak after playtesting ===============================================================
	/** Ticks of unbroken sprinting before the break into a fast run (2 s; Freerunner rank 2: 1.25 s). */
	public static final int DELAY = 40, DELAY_RANK2 = 25;
	/** The extra movement speed (0.3 = +30%; Freerunner rank 1 and up +40%). */
	public static final double BONUS = 0.30, BONUS_RANK1 = 0.40;
	/** A change of heading beyond this many degrees within {@link #TURN_TICKS} ticks ends the run. */
	public static final double TURN_DEGREES = 60.0;
	public static final int TURN_TICKS = 4;
	/** Hunger at or below this ends the run (and a run can't begin). */
	public static final int MIN_FOOD = 6;
	/** Extra hunger (exhaustion) every tick of the run, over the sprint's own. */
	public static final float EXHAUSTION = 0.006F;
	/** Airborne this many ticks (outside a held move) ends the run. */
	public static final int AIR_GRACE_TICKS = 14;
	/** Not sprinting is tolerated this long while Sneak is held or a slide has just ended (the sneak that starts a slide stops the sprint). */
	public static final int SNEAK_GRACE_TICKS = 10;
	/** Dust at the feet every this many ticks. */
	public static final int DUST_EVERY = 3;
	// ================================================================================================================

	/** On a player in a fast run (synced to everyone, for the lean and for the moves that need one). */
	public static final AttachmentType<Boolean> ACTIVE = AttachmentRegistry.create(JaymosPowers.id("freerun_fast"),
		builder -> builder.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));

	private static final Identifier SPEED_ID = JaymosPowers.id("freerun_fast_run");

	private static final class State {
		int sprinting, airborne, notSprinting;
		final ArrayDeque<Vec3> trail = new ArrayDeque<>();
	}

	private static final Map<UUID, State> STATES = new HashMap<>();

	private FastRun() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(FastRun::tick);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (entity instanceof ServerPlayer player && damageTaken > 0.0F) {
				end(player, STATES.get(player.getUUID()));
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> STATES.remove(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> STATES.clear());
	}

	/** Whether this player is in a fast run (any side, from the synced flag). */
	public static boolean active(Player player) {
		return player.hasAttached(ACTIVE);
	}

	private static void end(ServerPlayer player, State state) {
		if (state != null) {
			state.sprinting = 0;
			state.trail.clear();
		}
		player.removeAttached(ACTIVE);
		Freerun.set(player, Attributes.MOVEMENT_SPEED, SPEED_ID, 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			State state = STATES.computeIfAbsent(player.getUUID(), id -> new State());
			boolean on = player.hasAttached(ACTIVE);
			boolean sliding = Slide.sliding(player) || Slide.justEnded(player, SNEAK_GRACE_TICKS);
			boolean allowed = player.isAlive() && (Freerun.canMove(player) || sliding) && !Ledge.powerMoving(player)
				&& !player.isInWater() && !player.isInLava() && player.getFoodData().getFoodLevel() > MIN_FOOD;
			if (!allowed) {
				if (on || state.sprinting > 0) {
					end(player, state);
				}
				continue;
			}
			state.notSprinting = player.isSprinting() ? 0 : state.notSprinting + 1;
			state.airborne = player.onGround() ? 0 : state.airborne + 1;
			boolean sneakBreak = (player.isShiftKeyDown() || sliding) && state.notSprinting <= SNEAK_GRACE_TICKS;
			if (state.notSprinting > 0 && !sneakBreak || state.airborne > AIR_GRACE_TICKS && !Freerun.holdingMove(player)) {
				if (on || state.sprinting > 0) {
					end(player, state);
				}
				continue;
			}
			// the heading over the last few ticks against the ones before
			state.trail.addFirst(new Vec3(player.getX(), 0.0, player.getZ()));
			while (state.trail.size() > 2 * TURN_TICKS + 1) {
				state.trail.removeLast();
			}
			if (on && turned(state)) {
				end(player, state);
				continue;
			}
			if (state.notSprinting > 0) {
				continue;
			}
			state.sprinting++;
			if (!on) {
				if (state.sprinting >= (Freerun.perk(player, Freerun.Perk.FREERUNNER) >= 2 ? DELAY_RANK2 : DELAY)) {
					player.setAttached(ACTIVE, true);
					Freerun.moved(player);
				} else {
					continue;
				}
			}
			Freerun.set(player, Attributes.MOVEMENT_SPEED, SPEED_ID, Freerun.perk(player, Freerun.Perk.FREERUNNER) >= 1 ? BONUS_RANK1 : BONUS,
				AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
			player.causeFoodExhaustion(EXHAUSTION);
			if (player.onGround() && server.getTickCount() % DUST_EVERY == 0) {
				dust(player);
			}
		}
	}

	/** Whether the heading of the last {@link #TURN_TICKS} ticks differs from the {@link #TURN_TICKS} before by more than {@link #TURN_DEGREES}. */
	private static boolean turned(State state) {
		if (state.trail.size() < 2 * TURN_TICKS + 1) {
			return false;
		}
		Vec3[] p = state.trail.toArray(new Vec3[0]);
		Vec3 now = p[0].subtract(p[TURN_TICKS]);
		Vec3 before = p[TURN_TICKS].subtract(p[2 * TURN_TICKS]);
		double a = now.length(), b = before.length();
		if (a < 0.2 || b < 0.2) {
			return false;
		}
		return Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, now.dot(before) / (a * b))))) > TURN_DEGREES;
	}

	private static void dust(ServerPlayer player) {
		ServerLevel level = player.level();
		BlockPos below = BlockPos.containing(player.getX(), player.getY() - 0.05, player.getZ());
		BlockState state = level.getBlockState(below);
		if (!state.isAir()) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), player.getX(), player.getY() + 0.1, player.getZ(), 3, 0.2, 0.02, 0.2, 0.05);
		}
	}
}
