package com.jaymospowers.rig;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Shakes;
import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.core.wounds.Mortality;
import com.jaymospowers.power.web.WebBlossom;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Rig suit-ups (Legends patch 4, Andy: "the rig should have a few different suit ups ... and one where it assembles on you
 * while falling from a great height, then thrusters kick in to stop the momentum"). Four, picked from the situation when
 * the suit is called ({@link RigCall}) or put on by hand:
 * <ul>
 * <li>{@link #SLAM}: standing on the ground, the plates come down hard one piece after another, each landing with a thud
 * and a puff of dust, the last with a ring of force that harms no one.</li>
 * <li>{@link #STEP}: called while you stand at the parked Rig's post ({@link RigSentry}): the empty shell opens round you and
 * folds shut, plate by plate.</li>
 * <li>{@link #FLIGHT}: called in the air, flying or falling a short way: the plates stream in from behind and lock on in
 * mid-air.</li>
 * <li>{@link #FALL}: called while falling from a great height with the Thrusters to hand: the suit assembles piece by
 * piece through the fall, then the thrusters fire and kill the momentum (no fall damage, a landing burst on the ground).
 * It costs {@link #BRAKE_COST} charge; with less than that the thrusters only sputter and the fall is the fall.</li>
 * <li>{@link #WRAP}: called standing still and out of a fight (Andy: suit-ups "like these", the Iron Man 2 suit-up and
 * its game remake): you throw your arms out wide, the suit flies in as one shell and comes apart round you; the gauntlets
 * slide up the outstretched arms from past the hands, the chest closes over the reactor, the legs clamp shut, and last the
 * helm folds over the head from behind, the faceplate drops and the eyes light. Your own view swings round to film it from
 * the front ({@code RigSuitUpCamera}).</li>
 * </ul>
 * The plates' flight is drawn by the client ({@code RigSuit}) from {@link #STYLE}; the body's pose from the
 * {@link RigMoves} kinds {@link RigMoves#SUITUP_SLAM} to {@link RigMoves#SUITUP_BRAKE}.
 */
public final class RigSuitUp {
	public static final int NONE = 0, SLAM = 1, STEP = 2, FLIGHT = 3, FALL = 4, WRAP = 5;
	/** The wrap needs you still (horizontal speed under this, blocks/tick) and unhurt for this long (ticks). */
	static final double WRAP_STILL = 0.08;
	static final int WRAP_CALM = 100;
	/** How long the wrap's call stays open after the helm lands (ticks): its plates' flights end by then. */
	static final int WRAP_SETTLE = 20;

	/** The style of the suit-up under way (synced to everyone, so the plates are drawn flying in that way). */
	public static final AttachmentType<Integer> STYLE = AttachmentRegistry.create(JaymosPowers.id("rig_suitup_style"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));

	/** How far from its parked post you may be and still step into it (blocks). */
	static final double STEP_RANGE = 3.0;
	/** A fall of at least this (blocks) is "from a great height" for the falling assembly; a shorter one is a flight-in. */
	static final float FALL_MIN = 7.0F;
	/** The thrusters' price (Core charge); the brake: upward gain a tick (blocks/tick), the slowest it lets you sink, how much sideways drift it keeps. */
	static final int BRAKE_COST = 50;
	static final double BRAKE_GAIN = 0.32, SINK_SPEED = 0.22, DRIFT_KEPT = 0.82;
	/** How long the brake may run before it gives up (ticks), and the landing's grace for fall damage after it. */
	static final int BRAKE_MAX_TICKS = 160, LAND_GRACE = 10;
	/** Even a hard fall is slowed over at least this many blocks of the brake's own reach: the brake starts this much early. */
	static final double BRAKE_MARGIN = 9.0;
	/** Hand-donned falling suit-up: how long the plates take before the thrusters fire (ticks). */
	static final int HAND_ASSEMBLE = 28;
	/** The landing burst: how far it throws things (blocks), how hard, and the shake's range. Harmless to what it throws. */
	static final double BURST_RADIUS = 4.5, BURST_PUSH = 0.7, SHAKE_RANGE = 28.0;
	static final float BURST_SHAKE = 1.4F;

	/** When each piece ({@link Rig.Piece} order: core, arms, thrusters, helm) lands, ticks after the first, and the streak's lead-in, per style. */
	private static final int[][] LAND_AT = {null, {0, 3, 6, 9}, {0, 9, 18, 27}, {0, 5, 10, 15}, {0, 20, 40, 60}, {14, 0, 28, 44}};
	private static final int[] INBOUND = {12, 5, 14, 9, 12, 16};

	private enum Phase { ASSEMBLE, BRAKE }

	private static final class Fall {
		Phase phase = Phase.ASSEMBLE;
		long since;
		long assembleUntil = Long.MIN_VALUE;
		long graceUntil = Long.MIN_VALUE;
		boolean sputtered;
	}

	/** Players whose suit is dropping them through a falling assembly. */
	private static final Map<UUID, Fall> FALLS = new HashMap<>();
	/** Players seen wearing a piece last tick (a piece appearing by hand starts a suit-up). */
	private static final Set<UUID> WEARING = new HashSet<>();

	private RigSuitUp() {
	}

	static void init() {
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forget(handler.player));
		ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) -> forget(player, true));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player) {
				forget(player);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			FALLS.clear();
			WEARING.clear();
		});
		// the thrusters' catch: no fall damage while they are braking, nor the moment after the landing
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (FALLS.isEmpty() && GRACE.isEmpty() || !(entity instanceof ServerPlayer player) || !source.is(DamageTypeTags.IS_FALL)) {
				return true;
			}
			Fall fall = FALLS.get(player.getUUID());
			return !(fall != null && fall.phase == Phase.BRAKE) && !graced(player);
		});
	}

	/** Style {@code style}'s lead-in before the first piece lands (ticks). */
	static int inbound(int style) {
		return INBOUND[Math.max(0, Math.min(style, INBOUND.length - 1))];
	}

	/** When style {@code style}'s pieces land, ticks after the first. */
	static int[] landAt(int style) {
		return LAND_AT[Math.max(SLAM, Math.min(style, WRAP))];
	}

	/** The move (pose) a style plays. */
	static int move(int style) {
		return switch (style) {
			case STEP -> RigMoves.SUITUP_STEP;
			case FLIGHT -> RigMoves.SUITUP_FLIGHT;
			case FALL -> RigMoves.SUITUP_FALL;
			case WRAP -> RigMoves.SUITUP_WRAP;
			default -> RigMoves.SUITUP_SLAM;
		};
	}

	/**
	 * Which suit-up this situation gets: a fall from height (with the Thrusters on you or parked) the falling assembly;
	 * airborne otherwise (or under thrust) the flight-in; at the parked post the step-in; standing still out of a fight the
	 * wrap; else (moving, or hurt lately) the slam.
	 *
	 * @param post where the suit was standing sentry, if it was
	 * @param thrusters whether the Thrusters are worn or parked
	 */
	static int pick(ServerPlayer player, @Nullable Vec3 post, boolean thrusters) {
		boolean airborne = !player.onGround() && !player.isInWater() && !player.isPassenger() && !player.getAbilities().flying;
		if (airborne && thrusters && player.fallDistance >= FALL_MIN && RigMoves.thrust(player) == RigMoves.THRUST_NONE) {
			return FALL;
		}
		if (airborne || RigMoves.thrust(player) != RigMoves.THRUST_NONE) {
			return FLIGHT;
		}
		if (post != null && post.distanceToSqr(player.position()) <= STEP_RANGE * STEP_RANGE) {
			return STEP;
		}
		Vec3 moving = player.getKnownMovement();
		boolean still = moving.x * moving.x + moving.z * moving.z < WRAP_STILL * WRAP_STILL && !player.isSprinting() && !player.isShiftKeyDown();
		boolean calm = player.hurtTime == 0 && player.tickCount - player.getLastHurtByMobTimestamp() > WRAP_CALM;
		return still && calm && player.onGround() ? WRAP : SLAM;
	}

	/** A suit-up of {@code style} begins for this player: its pose, its plates' manner (synced) and its first sounds. */
	static void begin(ServerPlayer player, int style, long assembleTicks) {
		player.setAttached(STYLE, style);
		RigMoves.start(player, move(style));
		ServerLevel level = player.level();
		Vec3 at = player.position();
		switch (style) {
			case STEP -> level.playSound(null, at.x, at.y, at.z, SoundEvents.IRON_DOOR_OPEN, SoundSource.PLAYERS, 0.9F, 0.6F);
			case FLIGHT -> level.playSound(null, at.x, at.y, at.z, SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 0.8F, 1.3F);
			case FALL -> {
				level.playSound(null, at.x, at.y, at.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.9F, 1.8F);
				Fall fall = new Fall();
				fall.since = level.getGameTime();
				if (assembleTicks > 0) {
					fall.assembleUntil = fall.since + assembleTicks;
				}
				FALLS.put(player.getUUID(), fall);
			}
			case WRAP -> {
				// the shell on its way, whole, before it comes apart round you
				level.playSound(null, at.x, at.y, at.z, SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 0.7F, 0.6F);
				level.playSound(null, at.x, at.y, at.z, SoundEvents.ARMOR_EQUIP_NETHERITE.value(), SoundSource.PLAYERS, 0.6F, 0.7F);
			}
			default -> level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, 0.6F, 0.7F);
		}
	}

	/** One piece has landed on the wearer in this style: its extra look and sound. */
	static void landed(ServerPlayer player, int style, int index, double y) {
		ServerLevel level = player.level();
		Vec3 at = new Vec3(player.getX(), y, player.getZ());
		switch (style) {
			case SLAM -> {
				dust(level, player.position(), 14);
				MagicFx.ring(level, player.position().add(0.0, 0.1, 0.0), 1.6F + index * 0.3F, RigActives.FLAME_COLOR);
				level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.8F, 0.55F + index * 0.1F);
				Shakes.around(level, player.position(), 12.0, 0.5F);
				if (index == 3) {
					// the last plate: a ring of force (harmless) and the suit is on
					MagicFx.ring(level, player.position().add(0.0, 0.1, 0.0), 4.0F, RigActives.FLAME_HOT);
					MagicFx.blast(level, player.position().add(0.0, 0.4, 0.0), 1.4F, RigActives.FLAME_HOT);
					level.playSound(null, at.x, at.y, at.z, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.0F, 0.8F);
				}
			}
			case STEP -> {
				MagicFx.blast(level, at, 0.7F, RigActives.FLAME_HOT);
				level.playSound(null, at.x, at.y, at.z, SoundEvents.IRON_DOOR_CLOSE, SoundSource.PLAYERS, 0.7F, 0.9F + index * 0.12F);
				if (index == 3) {
					MagicFx.ring(level, player.position().add(0.0, 1.0, 0.0), null, 1.8F, RigActives.FLAME_HOT, 10);
				}
			}
			case WRAP -> {
				// (index in the call's order: core, arms, thrusters, helm; the arms land first)
				switch (index) {
					case 1 -> {
						// the gauntlets ratchet up the arms: a run of clicks out at both hands
						float yaw = player.yBodyRot * net.minecraft.util.Mth.DEG_TO_RAD;
						double sx = net.minecraft.util.Mth.cos(yaw), sz = net.minecraft.util.Mth.sin(yaw);
						for (int side = -1; side <= 1; side += 2) {
							level.sendParticles(MagicFx.spark(RigActives.FLAME_HOT, 0.8F), player.getX() + sx * 1.2 * side, player.getY() + 1.4,
								player.getZ() + sz * 1.2 * side, 5, 0.1, 0.05, 0.1, 0.08);
						}
						level.playSound(null, at.x, at.y, at.z, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.PLAYERS, 0.7F, 1.6F);
						level.playSound(null, at.x, at.y, at.z, SoundEvents.CROSSBOW_LOADING_END.value(), SoundSource.PLAYERS, 0.8F, 1.3F);
					}
					case 0 -> {
						// the chest closes over the reactor, and it lights
						MagicFx.blast(level, player.position().add(0.0, 1.2, 0.0), 0.7F, RigActives.FLAME_HOT);
						level.playSound(null, at.x, at.y, at.z, SoundEvents.IRON_DOOR_CLOSE, SoundSource.PLAYERS, 0.8F, 1.1F);
						level.playSound(null, at.x, at.y, at.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.5F, 1.8F);
					}
					case 2 -> level.playSound(null, at.x, at.y, at.z, SoundEvents.PISTON_CONTRACT, SoundSource.PLAYERS, 0.8F, 1.2F);
					default -> {
						// the helm folds over, the faceplate drops, the eyes come on
						level.playSound(null, at.x, at.y, at.z, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.PLAYERS, 0.9F, 0.8F);
						level.playSound(null, at.x, at.y, at.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.6F, 2.0F);
						MagicFx.ring(level, player.position().add(0.0, 1.0, 0.0), null, 1.6F, RigActives.FLAME_HOT, 10);
					}
				}
			}
			case FLIGHT -> {
				MagicFx.blast(level, at, 0.8F, RigActives.FLAME_COLOR);
				level.playSound(null, at.x, at.y, at.z, SoundEvents.PISTON_EXTEND, SoundSource.PLAYERS, 0.8F, 1.4F + index * 0.1F);
			}
			default -> {
				// falling: each piece locks on the wind
				MagicFx.blast(level, at, 0.9F, RigActives.FLAME_HOT);
				level.sendParticles(MagicFx.spark(RigActives.FLAME_HOT, 1.0F), at.x, at.y, at.z, 8, 0.3, 0.2, 0.3, 0.2);
				level.playSound(null, at.x, at.y, at.z, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.PLAYERS, 0.9F, 1.1F + index * 0.1F);
			}
		}
	}

	/** Every tick, for every player. */
	static void tick(ServerPlayer player) {
		UUID id = player.getUUID();
		boolean wearing = Rig.wearsAny(player) && !player.isSpectator();
		if (!wearing) {
			WEARING.remove(id);
		} else if (WEARING.add(id) && player.tickCount > 40 && !RigCall.calling(player) && !FALLS.containsKey(id)) {
			// put on by hand: the pieces build themselves on (RigSuit), the body plays the suit-up that fits the moment
			byHand(player);
		}
		Fall fall = FALLS.get(id);
		if (fall != null) {
			tickFall(player, fall);
		}
	}

	private static void byHand(ServerPlayer player) {
		boolean thrusters = Rig.wears(player, Rig.Piece.THRUSTERS) && Rig.wears(player, Rig.Piece.CORE);
		int style = pick(player, null, thrusters);
		if (style == WRAP) {
			// one piece put on by hand is no film: the wrap is for a called suit
			style = SLAM;
		}
		if (style == FALL) {
			begin(player, FALL, HAND_ASSEMBLE);
			return;
		}
		RigMoves.start(player, move(style));
		ServerLevel level = player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), style == SLAM ? SoundEvents.ANVIL_PLACE : SoundEvents.ARMOR_EQUIP_NETHERITE.value(),
			SoundSource.PLAYERS, 0.8F, style == SLAM ? 0.7F : 1.2F);
		if (style == SLAM) {
			dust(level, player.position(), 10);
		}
	}

	/** The falling assembly: through it the suit builds on; then, braking, the thrusters kill the fall. */
	private static void tickFall(ServerPlayer player, Fall fall) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		if (!player.isAlive() || player.isSpectator() || Rig.core(player).isEmpty() && fall.phase == Phase.BRAKE) {
			forget(player);
			return;
		}
		if (fall.phase == Phase.ASSEMBLE) {
			boolean assembling = RigCall.calling(player) || now < fall.assembleUntil;
			if (player.onGround() || player.isInWater() || RigMoves.thrust(player) != RigMoves.THRUST_NONE && !assembling) {
				forget(player);
				return;
			}
			// (a server player's own delta movement stays near zero: the known movement is how far it really moved)
			double speed = Math.max(0.0, -player.getKnownMovement().y);
			double gap = groundGap(player, 256.0);
			// a fall too short for the whole assembly: whatever has not locked on yet locks on now, and the thrusters go; it starts
			// from the distance the brake needs at the speed we really have, plus a tick's travel, so a fast fall cannot skip it
			boolean late = gap >= 0.0 && gap <= stopDistance(speed + 0.5) + BRAKE_MARGIN + speed;
			if (late && assembling) {
				RigCall.finishNow(player);
			}
			if (!assembling || late) {
				brake(player, fall, now);
			}
			return;
		}
		// braking
		if (now - fall.since > BRAKE_MAX_TICKS) {
			// the brake gave out in the air: what is left is not a fresh fall
			player.resetFallDistance();
			forget(player);
			return;
		}
		Vec3 v = player.getKnownMovement();
		if (player.onGround()) {
			land(player, fall, now);
			return;
		}
		double up = Math.min(-SINK_SPEED, v.y + BRAKE_GAIN);
		if (v.y > -SINK_SPEED) {
			up = v.y;
		}
		double gap = groundGap(player, 8.0);
		if (gap >= 0.0 && gap < 2.0 && up < -0.5) {
			// the ground is right here: never faster than this at the end
			up = -0.5;
		}
		player.setDeltaMovement(v.x * DRIFT_KEPT, up, v.z * DRIFT_KEPT);
		player.syncVelocity = true;
		player.resetFallDistance();
		Vec3 feet = player.position();
		level.sendParticles(MagicFx.spark(RigActives.FLAME_HOT, 1.1F), feet.x, feet.y + 0.1, feet.z, 3, 0.2, 0.05, 0.2, 0.25);
		level.sendParticles(ParticleTypes.FLAME, feet.x, feet.y + 0.2, feet.z, 2, 0.15, 0.05, 0.15, 0.08);
		if ((now - fall.since) % 3 == 0) {
			MagicFx.blast(level, feet.add(0.0, 0.2, 0.0), 0.6F, RigActives.FLAME_COLOR);
		}
	}

	private static void brake(ServerPlayer player, Fall fall, long now) {
		if (fall.phase == Phase.BRAKE) {
			return;
		}
		if (!Rig.wears(player, Rig.Piece.THRUSTERS) || !Rig.spend(player, BRAKE_COST)) {
			// no thrusters on, or the Core too empty for them: they cough, and the fall is the fall
			ServerLevel level = player.level();
			if (!fall.sputtered) {
				fall.sputtered = true;
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 1.0F, 0.6F);
				level.sendParticles(ParticleTypes.SMOKE, player.getX(), player.getY() + 0.1, player.getZ(), 10, 0.2, 0.05, 0.2, 0.02);
			}
			forget(player);
			return;
		}
		fall.phase = Phase.BRAKE;
		fall.since = now;
		RigMoves.start(player, RigMoves.SUITUP_BRAKE);
		ServerLevel level = player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 1.2F, 0.5F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.5F, 1.6F);
		Shakes.send(player, 1.0F, player.position());
		MagicFx.ring(level, player.position().add(0.0, 0.2, 0.0), 2.4F, RigActives.FLAME_COLOR);
	}

	/** The thrusters have set you down: a burst of force round you (it throws, it does not hurt) and the one-knee landing. */
	private static void land(ServerPlayer player, Fall fall, long now) {
		ServerLevel level = player.level();
		Vec3 at = player.position();
		fall.graceUntil = now + LAND_GRACE;
		RigMoves.start(player, RigMoves.KNEE);
		MagicFx.ring(level, at.add(0.0, 0.1, 0.0), 5.0F, RigActives.FLAME_COLOR);
		MagicFx.ring(level, at.add(0.0, 0.1, 0.0), 3.0F, RigActives.FLAME_HOT);
		MagicFx.blast(level, at.add(0.0, 0.3, 0.0), 1.8F, RigActives.FLAME_HOT);
		BlockState ground = level.getBlockState(BlockPos.containing(at.x, at.y - 0.5, at.z));
		if (!ground.isAir()) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), at.x, at.y + 0.1, at.z, 40, 1.8, 0.1, 1.8, 0.15);
		}
		level.playSound(null, at.x, at.y, at.z, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.3F, 0.7F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.8F, 0.5F);
		Shakes.heavy(player, at, SHAKE_RANGE, BURST_SHAKE);
		for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(BURST_RADIUS),
			e -> e != player && !Mortality.boss(e) && WebBlossom.fight(player, e))) {
			Vec3 away = near.position().subtract(at);
			Vec3 flat = new Vec3(away.x, 0.0, away.z);
			flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : flat.normalize();
			near.setDeltaMovement(near.getDeltaMovement().add(flat.scale(BURST_PUSH)).add(0.0, 0.25, 0.0));
			near.needsSync = true;
			near.syncVelocity = true;
		}
		player.resetFallDistance();
		FALLS.remove(player.getUUID());
		GRACE.put(player.getUUID(), fall.graceUntil);
		end(player);
	}

	/** After a landing: until when (game time) fall damage is still waved off. */
	private static final Map<UUID, Long> GRACE = new HashMap<>();
	/** Whether a thruster landing's grace still covers this player's fall damage. */
	static boolean graced(ServerPlayer player) {
		Long until = GRACE.get(player.getUUID());
		if (until == null) {
			return false;
		}
		if (player.level().getGameTime() > until) {
			GRACE.remove(player.getUUID());
			return false;
		}
		return true;
	}

	/** How far (blocks) the thrusters need to stop a fall at {@code speed} (blocks/tick), a little generous. */
	static double stopDistance(double speed) {
		return speed <= SINK_SPEED ? 0.0 : (speed * speed - SINK_SPEED * SINK_SPEED) / (2.0 * BRAKE_GAIN) + speed * 2.0;
	}

	/** The air between the feet and the first solid thing below, up to {@code max} blocks; -1 if there is none. */
	private static double groundGap(ServerPlayer player, double max) {
		Vec3 from = player.position();
		BlockHitResult hit = player.level().clip(new ClipContext(from, from.subtract(0.0, max, 0.0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		return hit.getType() == HitResult.Type.BLOCK ? from.y - hit.getLocation().y : -1.0;
	}

	private static void dust(ServerLevel level, Vec3 at, int count) {
		BlockState ground = level.getBlockState(BlockPos.containing(at.x, at.y - 0.5, at.z));
		if (!ground.isAir()) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), at.x, at.y + 0.1, at.z, count, 0.5, 0.1, 0.5, 0.12);
		}
	}

	/** The style's end: the plates have landed (or the run is called off). */
	static void end(ServerPlayer player) {
		player.removeAttached(STYLE);
	}

	/** Gone: everything of this player's ends (death, leaving, the server stopping). */
	static void forget(ServerPlayer player) {
		forget(player, false);
	}

	/** {@code keepWearing}: a change of world is not a suit being put on again, so what is worn stays remembered. */
	static void forget(ServerPlayer player, boolean keepWearing) {
		FALLS.remove(player.getUUID());
		GRACE.remove(player.getUUID());
		if (!keepWearing) {
			WEARING.remove(player.getUUID());
		}
		RigMoves.Move move = RigMoves.get(player);
		if (move != null && (move.kind() == RigMoves.SUITUP_FALL || move.kind() == RigMoves.SUITUP_BRAKE)) {
			player.removeAttached(RigMoves.MOVE);
		}
		end(player);
	}
}
