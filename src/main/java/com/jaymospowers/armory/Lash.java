package com.jaymospowers.armory;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Debris;
import com.jaymospowers.core.Shakes;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.15.148) The Web Whip's mechanics on a weapon: a head on a line, swung round you, hauled overhead and slammed down
 * (see {@code WebWhip}). Andy: "Can these mechanics replace the flail? And maybe add a single leather whip?"
 * <ul>
 * <li><b>Flail</b> (Balkon's, which used to throw its ball on every hit; mixin/WeaponModFlailMixin): hold use and the
 * ball whirls round over your head on its chain, faster the longer you hold ({@link #WINDUP}), clipping whatever it
 * passes. Let go and it's hauled up over your head and brought down where you look, harder the faster it spun, with a
 * puff of dust and a jolt. Its left-click swings are plain blunt blows.</li>
 * <li><b>Leather whip</b> ({@link LeatherWhipItem}): use cracks the lash out to where you look ({@link #WHIP_REACH}).
 * A creature it cracks is stung; keep holding and the lash wraps it and holds it to you. Let go: a light one is hauled
 * over your head and slammed down in front of you, a heavy one yanked off its feet toward you and left reeling. Andy:
 * "the whip should also allow you to climb up a wall rappel style, trusting that you hooked the edge of an up to 7 block
 * wall": crack it at a wall and keep holding, and it's hooked over the top ({@link #HOOK}); (0.16.24) you hang off it,
 * jump by jump up the wall and over the top (Sneak too: down again), and letting go drops you. A crack that lands on
 * dropped items flings them back to you.</li>
 * <li>(0.16.24) <b>Swipe</b> (both): Andy: "add a far less powerful aoe swipe with both that follows your swing with the
 * physics whip/flail. dealing damage when it connects. this attack deals more with the flail, but still less favorable
 * to getting momentum with a swing." A charged left-click sends the head round in front of you ({@link #SWEEP}) and it
 * clips whatever it passes ({@link #swipe}); the whirl and the slam still hit far harder.</li>
 * </ul>
 * The server runs it all; the head's path is worked out the same on both sides from the synced {@link State} (a player
 * attachment), so the chain or lash is drawn from the hand with no lag (client/armory/LashRenderer).
 */
public final class Lash {
	public static final byte FLAIL = 0, WHIP = 1;
	/** What the head is doing: whirling, coming down, flying back; (whip) cracking out, wrapped round, hauling. */
	public static final byte WHIRL = 0, STRIKE = 1, RETURN = 2, CRACK = 3, SNAG = 4, HEAVE = 5, YANK = 6, HOOK = 7, SWEEP = 8;

	// ---- the flail --------------------------------------------------------------------------------------------------
	/**
	 * The whirl: the ball's circle round the hand (blocks out, and how far below the hand the chain hangs it), and how
	 * fast it goes round (radians a tick). (0.16.2) Andy: "it should all animate together coming from the hand", so it
	 * goes round the held-out hand at chest height instead of floating over your head.
	 */
	public static final double WHIRL_RADIUS = 2.1, WHIRL_DROOP = 0.2, SPIN_START = 0.28, SPIN_FULL = 0.62;
	/**
	 * (0.16.2) The flail arm, everyone sees it (client/armory/LashPose) and the chain hangs off its hand: raised this far
	 * from hanging (radians) at rest, while it whirls, overhead before the slam and down after it, and out to its side.
	 * The arm's length and the shoulder (share of your height, and out to the side) place the hand.
	 */
	public static final float REST_RAISE = 0.31F, WHIRL_RAISE = 1.15F, OVERHEAD_RAISE = 2.9F, DROP_RAISE = 0.75F, ARM_OUT = 0.12F;
	public static final double ARM_LENGTH = 0.66, SHOULDER = 0.716, SHOULDER_OUT = 0.29;
	/** Ticks to full speed, the ticks the chain takes to pay out to its full circle, and the least spin worth a slam. */
	public static final int WINDUP = 30, PAY_OUT = 5, MIN_WINDUP = 6;
	/** The whirl clips what it passes: this share of the flail's damage, this hard a shove, once per creature this often. */
	static final float GLANCE_SHARE = 0.35F;
	static final double GLANCE_PUSH = 0.6, BALL_RADIUS = 0.55;
	static final int GLANCE_EVERY = 10;
	/** The slam: how far ahead it can come down, the ticks it takes (up over your head, then down), the impact tick. */
	public static final double STRIKE_REACH = 4.2, OVER_UP = 1.9, OVER_BACK = 0.5;
	public static final int STRIKE_OVER = 4, STRIKE_TICKS = 8;
	/** Its damage: the flail's own times this, plus this much more at full spin; others this close take {@code SPLASH_SHARE}. */
	static final float SLAM_SHARE = 0.9F, SLAM_SPIN_BONUS = 0.8F, SPLASH_SHARE = 0.4F;
	static final double SLAM_HIT = 1.1, SPLASH = 1.6;
	/** Screen shake: yours at a slam, at full spin; a struck player's; anyone near's. */
	static final float SLAM_SHAKE = 1.1F, TARGET_SHAKE = 1.4F, NEAR_SHAKE = 0.4F;
	static final double NEAR_SHAKE_RANGE = 10.0;
	/** Hunger the whirl costs a tick, and the slam. */
	static final float WHIRL_EXHAUSTION = 0.01F, SLAM_EXHAUSTION = 0.3F;
	/** Ticks before the flail (or the whip) can be used again. */
	static final int FLAIL_COOLDOWN = 14, WHIP_COOLDOWN = 12;

	// ---- the whip ---------------------------------------------------------------------------------------------------
	/** How far the lash reaches ((0.16.11) Andy chose 10 blocks), and the ticks it takes to crack out. */
	public static final double WHIP_REACH = 10.0;
	public static final int CRACK_TICKS = 4;
	/** The crack's sting, and the slam's. */
	static final float CRACK_DAMAGE = 4.0F, WHIP_SLAM_DAMAGE = 6.0F;
	/** Wrapped round: held this close (blocks, pulled in a little a tick past it), let go of after this long anyway. */
	static final double HOLD_SLACK = 3.5, HOLD_PULL = 0.18;
	static final int SNAG_MAX = 80;
	/**
	 * A creature this light can be hauled overhead (width × width × height, blocks³: a player 0.65, a pig 0.73, a sheep
	 * 1.05); heavier ones are yanked off their feet instead, this hard, and reel (slowed) this long.
	 */
	static final double LIFT_VOLUME = 0.9, YANK_PULL = 1.0, YANK_UP = 0.35;
	/** (0.16.11) A yank from further off pulls harder (this much more a block away), up to this hard: from the lash's full reach. */
	static final double YANK_PER_BLOCK = 0.12, YANK_MAX = 2.0;
	static final int REEL_TICKS = 40;
	/** The haul: over your head at this tick, slammed in front at the next (ticks since letting go). */
	public static final int HEAVE_OVER = 7, HEAVE_SLAM = 12, YANK_TICKS = 6;
	static final double HEAVE_REACH = 3.2, STEER = 0.75, MAX_SPEED = 2.6;
	/**
	 * (rappel) A wall whose top is at most this far above your feet can be climbed ((0.16.11) whatever the lash reaches);
	 * the lash, cracked anywhere on its face, hooks over the top. (0.16.24) You hang on it, taut (client/armory/
	 * LashRenderer.climb): each jump hauls you up a length, and with your hands at the lip you go up and over, pushed in
	 * this hard; on the ground (more than this below the lip) it pays out as you walk; with Sneak held you let yourself down
	 * this fast. The hook gives after this long anyway.
	 */
	public static final double CLIMB_HEIGHT = WHIP_REACH + 1.5, OVER_THE_TOP = 0.7, OVER_PUSH = 0.3, LOWER_SPEED = 0.1;
	static final int HOOK_MAX = 1200;
	/**
	 * (0.16.24) The swipe: a left-click at least this charged sends the head this far round in front of you (radians
	 * either side of where you look), over this many ticks, at this reach from the chest (the whip's lash further). What it
	 * clips takes this share of the flail's blow (the whirl's glance is {@link #GLANCE_SHARE} and more as it speeds up),
	 * or this much from the whip, both times the swing's charge, pushed off this hard.
	 */
	public static final double SWEEP_ARC = 1.25, SWEEP_FLAIL_REACH = 2.0, SWEEP_WHIP_REACH = 4.2;
	public static final int SWEEP_TICKS = 6;
	static final float SWEEP_MIN_CHARGE = 0.6F, SWEEP_SHARE = 0.22F, SWEEP_WHIP_DAMAGE = 1.5F;
	static final double SWEEP_HIT = 0.6, SWEEP_PUSH = 0.25;
	/**
	 * (0.16.24) Andy: "also allow the whip to pull items in": a crack that lands on dropped items (within this of where
	 * it lands; the crack aims at one this near its line) flings them back to you, as the fishing rod does.
	 */
	static final double ITEM_GRAB = 1.2, ITEM_AIM = 0.7, ITEM_FLING = 0.1, ITEM_LOFT = 0.08;
	/** The head flies back to the hand in this many ticks. */
	public static final int RETURN_TICKS = 5;

	/**
	 * What a player's flail or whip is doing (synced to everyone near). {@code since}: the game tick it began;
	 * {@code target}: what it's wrapped round (-1: nothing); {@code from}: where the head was when it began; {@code aim}:
	 * where it's going (the slam's spot, the crack's).
	 */
	public record State(byte kind, byte phase, long since, int target, Vec3 from, Vec3 aim) {
		public static final StreamCodec<ByteBuf, State> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.BYTE, State::kind,
			ByteBufCodecs.BYTE, State::phase,
			ByteBufCodecs.VAR_LONG, State::since,
			ByteBufCodecs.VAR_INT, State::target,
			Vec3.STREAM_CODEC, State::from,
			Vec3.STREAM_CODEC, State::aim,
			State::new);

		State to(byte phase, long now, int target, Vec3 from, Vec3 aim) {
			return new State(this.kind, phase, now, target, from, aim);
		}
	}

	public static final AttachmentType<State> STATE = AttachmentRegistry.create(JaymosPowers.id("lash"),
		builder -> builder.syncWith(State.STREAM_CODEC, AttachmentSyncPredicate.all()));

	/** Server: the players with a lash out, and what each has been doing that the clients needn't know. */
	private static final Map<UUID, Run> RUNS = new HashMap<>();

	private static final class Run {
		final ServerPlayer player;
		/** The whirl's spin when it was let go (0-1), for the slam. */
		float spin;
		/** Creatures the whirl clipped, and when (game time). */
		final Map<Integer, Long> glanced = new HashMap<>();
		/** Whether the whip's target had gravity before it was hauled up. */
		boolean hadGravity = true;
		/** Still holding use (the whip waits for the crack to land before deciding to wrap). */
		boolean held = true;
		/** (0.16.24) A left-click swipe: no cooldown after it. */
		boolean swipe;

		Run(ServerPlayer player) {
			this.player = player;
		}
	}

	private Lash() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(Lash::tick);
		// (0.16.24) a blow at someone swipes too (an air swing comes in through mixin/ServerGamePacketListenerImplMixin)
		net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (player instanceof ServerPlayer server && hand == InteractionHand.MAIN_HAND) {
				swipe(server, entity);
			}
			return InteractionResult.PASS;
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			Run run = RUNS.remove(handler.player.getUUID());
			if (run != null) {
				end(run, null);
			}
		});
	}

	// ---- which items (both sides) -----------------------------------------------------------------------------------

	/** Whether {@code item} is one of Balkon's flails (by id, so there's no compile dependency on WeaponMod). */
	public static boolean isFlail(Item item) {
		Identifier id = BuiltInRegistries.ITEM.getKey(item);
		return id.getNamespace().equals("weaponmod") && id.getPath().startsWith("flail");
	}

	public static boolean isFlail(ItemStack stack) {
		return !stack.isEmpty() && isFlail(stack.getItem());
	}

	public static boolean isWhip(ItemStack stack) {
		return stack.getItem() instanceof LeatherWhipItem;
	}

	// ---- the item's hooks (both sides) ------------------------------------------------------------------------------

	/** Use pressed with a flail or whip: held for as long as use is (the release is what swings it). */
	public static InteractionResult use(Level level, Player player, InteractionHand hand, byte kind) {
		ItemStack stack = player.getItemInHand(hand);
		if (hand != InteractionHand.MAIN_HAND || player.getCooldowns().isOnCooldown(stack) || player.getAttached(STATE) != null) {
			return InteractionResult.FAIL;
		}
		player.startUsingItem(hand);
		if (player instanceof ServerPlayer server) {
			begin(server, kind);
		}
		return InteractionResult.CONSUME;
	}

	public static int useDuration() {
		return 72000;
	}

	public static ItemUseAnimation useAnimation(byte kind) {
		// (0.16.2) the flail's arm is posed by LashPose (the trident's raised arm floated the ball over the head)
		return ItemUseAnimation.NONE;
	}

	/** Use let go. */
	public static void release(Level level, LivingEntity entity) {
		if (entity instanceof ServerPlayer player) {
			Run run = RUNS.get(player.getUUID());
			if (run != null) {
				run.held = false;
				let(run);
			}
		}
	}

	// ---- the head's path (both sides) -------------------------------------------------------------------------------

	/** Where the head is at {@code time} (game ticks, with the partial tick on the client). */
	public static Vec3 head(Player player, State state, double time, float partialTick) {
		double t = Math.max(0.0, time - state.since());
		Vec3 feet = player.getPosition(partialTick);
		Vec3 hand = state.kind() == FLAIL ? armHand(player, partialTick, armRaise(state, t), ARM_OUT) : hand(player, partialTick);
		return switch (state.phase()) {
			case WHIRL -> {
				// (a whirl's aim.x is the angle it set off at, round you from the east) round the held-out hand
				double angle = state.aim().x + spinAngle(t);
				double out = Mth.clamp(t / PAY_OUT, 0.0, 1.0);
				double radius = Mth.lerp(out, 0.3, WHIRL_RADIUS);
				Vec3 center = armHand(player, partialTick, WHIRL_RAISE, ARM_OUT).add(0.0, -WHIRL_DROOP * out, 0.0);
				Vec3 circle = center.add(Math.cos(angle) * radius, 0.0, Math.sin(angle) * radius);
				yield out < 1.0 ? hand.lerp(circle, smooth(out)) : circle;
			}
			case STRIKE -> {
				Vec3 over = feet.add(0.0, player.getBbHeight() + OVER_UP, 0.0).subtract(ahead(player).scale(OVER_BACK));
				if (t < STRIKE_OVER) {
					yield state.from().lerp(over, smooth(t / STRIKE_OVER));
				}
				double s = Math.min(1.0, (t - STRIKE_OVER) / (STRIKE_TICKS - 1 - STRIKE_OVER));
				// brought down hard: it speeds up as it falls
				yield over.lerp(state.aim(), s * s);
			}
			case CRACK -> state.from().lerp(state.aim(), Math.min(1.0, t / CRACK_TICKS));
			case SWEEP -> {
				Vec3 arc = sweepPoint(player, state, t, partialTick);
				yield t < 2.0 ? state.from().lerp(arc, t / 2.0) : arc;
			}
			case HOOK -> state.aim();
			case SNAG, HEAVE, YANK -> {
				Entity target = player.level().getEntity(state.target());
				yield target == null ? state.aim() : target.getPosition(partialTick).add(0.0, target.getBbHeight() * 0.55, 0.0);
			}
			default -> state.from().lerp(hand, smooth(Math.min(1.0, t / RETURN_TICKS)));
		};
	}

	/**
	 * (0.16.24) The swipe's arc, {@code t} ticks in: round from your weapon side to the other, in front of your chest and
	 * tilted with where you looked ({@code aim}: yaw and pitch in radians, then the reach), speeding up then easing off.
	 */
	public static Vec3 sweepPoint(Player player, State state, double t, float partialTick) {
		double s = smooth(Mth.clamp(t / SWEEP_TICKS, 0.0, 1.0));
		int side = player.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT ? 1 : -1;
		double a = SWEEP_ARC * side * (1.0 - 2.0 * s);
		double yaw = state.aim().x, pitch = state.aim().y;
		Vec3 forward = new Vec3(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
		Vec3 right = new Vec3(-Math.cos(yaw), 0.0, -Math.sin(yaw));
		Vec3 chest = player.getEyePosition(partialTick).add(0.0, -0.4 * player.getScale(), 0.0);
		// a little low at the ends, highest across the front
		return chest.add(forward.scale(Math.cos(a) * state.aim().z)).add(right.scale(Math.sin(a) * state.aim().z))
			.add(0.0, -0.25 * Math.abs(Math.sin(a)), 0.0);
	}

	/** How far round the whirl has gone after {@code t} ticks (speeding up to full over {@link #WINDUP}). */
	public static double spinAngle(double t) {
		double ramp = Math.min(t, WINDUP);
		double angle = SPIN_START * ramp + (SPIN_FULL - SPIN_START) * ramp * ramp / (2.0 * WINDUP);
		return angle + Math.max(0.0, t - WINDUP) * SPIN_FULL;
	}

	/** The whirl's speed after {@code t} ticks, 0 (just started) to 1 (full). */
	public static float spin(double t) {
		return (float)Mth.clamp(t / WINDUP, 0.0, 1.0);
	}

	/** (0.16.2) Where a whirl sets off: straight out ahead of the body (both sides work it out the same). */
	public static double startAngle(Player player) {
		float yaw = player.yBodyRot * Mth.DEG_TO_RAD;
		return Math.atan2(Mth.cos(yaw), -Mth.sin(yaw));
	}

	/**
	 * (0.16.2) How far the flail arm is raised from hanging (radians), {@code t} ticks into {@code state}: out to the
	 * whirl as the chain pays out, up overhead and down hard for the slam, and back to rest.
	 */
	public static float armRaise(State state, double t) {
		return switch (state.phase()) {
			case WHIRL -> Mth.lerp((float)smooth(Mth.clamp(t / PAY_OUT, 0.0, 1.0)), REST_RAISE, WHIRL_RAISE);
			case STRIKE -> {
				if (t < STRIKE_OVER) {
					yield Mth.lerp((float)smooth(t / STRIKE_OVER), WHIRL_RAISE, OVERHEAD_RAISE);
				}
				float s = (float)Math.min(1.0, (t - STRIKE_OVER) / (STRIKE_TICKS - 1 - STRIKE_OVER));
				yield Mth.lerp(s * s, OVERHEAD_RAISE, DROP_RAISE);
			}
			default -> Mth.lerp((float)smooth(Math.min(1.0, t / RETURN_TICKS)), DROP_RAISE, REST_RAISE);
		};
	}

	/**
	 * (0.16.2) The weapon hand at the end of an arm raised {@code raise} from hanging (radians, forward) and {@code out}
	 * to its side, from the shoulder: where the flail's chain leaves it (LashPose poses the arm the same way).
	 */
	public static Vec3 armHand(Player player, float partialTick, double raise, double out) {
		float bodyYaw = Mth.lerp(partialTick, player.yBodyRotO, player.yBodyRot) * Mth.DEG_TO_RAD;
		double sin = Mth.sin(bodyYaw), cos = Mth.cos(bodyYaw), scale = player.getScale();
		int side = player.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT ? 1 : -1;
		Vec3 forward = new Vec3(-sin, 0.0, cos), right = new Vec3(-cos * side, 0.0, -sin * side);
		Vec3 shoulder = player.getPosition(partialTick).add(0.0, player.getBbHeight() * SHOULDER, 0.0).add(right.scale(SHOULDER_OUT * scale));
		Vec3 arm = forward.scale(Math.sin(raise)).add(0.0, -Math.cos(raise), 0.0).scale(Math.cos(out)).add(right.scale(Math.sin(out)));
		return shoulder.add(arm.scale(ARM_LENGTH * scale));
	}

	/** Roughly where the weapon hand is (the client draws from the real hand instead). */
	public static Vec3 hand(Player player, float partialTick) {
		float bodyYaw = Mth.lerp(partialTick, player.yBodyRotO, player.yBodyRot) * Mth.DEG_TO_RAD;
		double sin = Mth.sin(bodyYaw), cos = Mth.cos(bodyYaw);
		double right = (player.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT ? 1 : -1) * 0.35 * player.getScale();
		double forward = 0.5 * player.getScale();
		return player.getEyePosition(partialTick).add(-cos * right - sin * forward, -0.5 * player.getScale(), -sin * right + cos * forward);
	}

	private static Vec3 ahead(Player player) {
		Vec3 look = player.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0.0, look.z);
		return flat.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0.0F, player.getYRot()) : flat.normalize();
	}

	private static double smooth(double s) {
		return s * s * (3.0 - 2.0 * s);
	}

	// ---- server -----------------------------------------------------------------------------------------------------

	private static void begin(ServerPlayer player, byte kind) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		Run run = new Run(player);
		RUNS.put(player.getUUID(), run);
		Vec3 hand = hand(player, 1.0F);
		if (kind == FLAIL) {
			player.setAttached(STATE, new State(FLAIL, WHIRL, now, -1, hand, new Vec3(startAngle(player), 0.0, 0.0)));
			flailThrown(player, true);
			level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 0.8F, 1.2F);
			return;
		}
		// the whip cracks out at once, to what's under the crosshair (or as far as it reaches)
		HitResult aim = aim(player, WHIP_REACH);
		Vec3 at = aim == null ? player.getEyePosition().add(player.getLookAngle().scale(WHIP_REACH))
			: aim instanceof EntityHitResult hit ? hit.getEntity().getBoundingBox().getCenter() : aim.getLocation();
		int target = aim instanceof EntityHitResult hit ? hit.getEntity().getId() : -1;
		if (target < 0) {
			// (0.16.24) nothing alive under the crosshair: dropped items on the way are what it's cracked at
			ItemEntity item = aimItem(player, at);
			if (item != null) {
				at = item.position().add(0.0, item.getBbHeight() * 0.5, 0.0);
				target = item.getId();
			}
		}
		player.setAttached(STATE, new State(WHIP, CRACK, now, target, hand, at));
		player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.PLAYERS, 0.9F, 1.4F);
	}

	private static void tick(MinecraftServer server) {
		if (RUNS.isEmpty()) {
			return;
		}
		for (Iterator<Run> it = RUNS.values().iterator(); it.hasNext(); ) {
			Run run = it.next();
			ServerPlayer player = run.player;
			State state = player.getAttached(STATE);
			ItemStack held = player.getMainHandItem();
			boolean right = state != null && (state.kind() == FLAIL ? isFlail(held) : isWhip(held));
			if (state == null || !right || !player.isAlive() || player.isSpectator() || player.isRemoved()) {
				it.remove();
				end(run, state);
				continue;
			}
			if (!step(run, state)) {
				it.remove();
				end(run, player.getAttached(STATE));
			}
		}
	}

	/** One tick of a lash; false once it's done. */
	private static boolean step(Run run, State state) {
		ServerPlayer player = run.player;
		ServerLevel level = player.level();
		long now = level.getGameTime();
		long t = now - state.since();
		switch (state.phase()) {
			case WHIRL -> {
				if (run.held && !player.isUsingItem()) {
					// stopped using without a release (switched hands, a menu): it winds down
					run.held = false;
					let(run);
					return true;
				}
				whirl(run, state, t);
			}
			case STRIKE -> {
				if (t == STRIKE_TICKS - 1) {
					slam(run, state);
				}
				if (t >= STRIKE_TICKS) {
					back(player, state, now);
				}
			}
			case CRACK -> {
				if (t == CRACK_TICKS) {
					crack(run, state);
				}
			}
			case SNAG -> {
				Entity target = level.getEntity(state.target());
				if (!holdable(player, target) || t > SNAG_MAX || target.distanceTo(player) > WHIP_REACH * 1.5) {
					// it slips free
					back(player, state, now);
					level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.LEAD_BREAK, SoundSource.PLAYERS, 0.6F, 1.3F);
					return true;
				}
				if (run.held && !player.isUsingItem()) {
					run.held = false;
					let(run);
					return true;
				}
				hold(player, target);
			}
			case HEAVE -> {
				Entity target = level.getEntity(state.target());
				if (!holdable(player, target)) {
					back(player, state, now);
					return true;
				}
				steer(target, heavePoint(player, state, target, t));
				if (t == HEAVE_OVER) {
					level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 0.7F);
				}
				if (t == HEAVE_SLAM) {
					heaveSlam(run, target);
				}
				if (t > HEAVE_SLAM) {
					back(player, state, now);
				}
			}
			case SWEEP -> {
				sweep(run, state, t);
				if (t >= SWEEP_TICKS) {
					back(player, state, now);
				}
			}
			case YANK -> {
				if (t >= YANK_TICKS) {
					back(player, state, now);
				}
			}
			case HOOK -> {
				// the climb itself is the climber's own (client/armory/LashRenderer.climb); here, only when it's over
				player.resetFallDistance();
				boolean over = player.onGround() && player.getY() >= state.aim().y - 0.1;
				if (over || t > HOOK_MAX || run.held && !player.isUsingItem()
					|| player.getEyePosition().distanceTo(state.aim()) > WHIP_REACH + 3.0) {
					run.held = false;
					back(player, state, now);
				}
			}
			default -> {
				if (t >= RETURN_TICKS) {
					return false;
				}
			}
		}
		return true;
	}

	/** Let go of use: the flail comes down, the whip hauls (or, if it hadn't caught anything, flies back). */
	private static void let(Run run) {
		ServerPlayer player = run.player;
		State state = player.getAttached(STATE);
		if (state == null) {
			return;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		long t = now - state.since();
		Vec3 head = head(player, state, now, 1.0F);
		if (state.phase() == WHIRL) {
			if (t < MIN_WINDUP) {
				back(player, state, now);
				return;
			}
			run.spin = spin(t);
			player.setAttached(STATE, state.to(STRIKE, now, -1, head, strikeSpot(player)));
			level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 0.6F);
			return;
		}
		if (state.phase() == HOOK) {
			// let go of the lash: whatever height you're at, you drop
			back(player, state, now);
			return;
		}
		if (state.phase() != SNAG) {
			// (a crack still on its way decides when it lands)
			return;
		}
		Entity target = level.getEntity(state.target());
		if (!holdable(player, target)) {
			back(player, state, now);
			return;
		}
		if (liftable(target)) {
			run.hadGravity = !target.isNoGravity();
			target.setNoGravity(true);
			player.setAttached(STATE, state.to(HEAVE, now, target.getId(), target.position(), target.position()));
			level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.LEAD_TIED, SoundSource.PLAYERS, 1.0F, 0.8F);
			return;
		}
		// too heavy to lift: yanked off its feet toward you, and left reeling
		Vec3 pull = player.position().subtract(target.position()).multiply(1.0, 0.0, 1.0);
		double far = pull.length();
		pull = far < 1.0E-2 ? Vec3.ZERO : pull.scale(Math.min(YANK_MAX, YANK_PULL + far * YANK_PER_BLOCK) / far);
		shove(target, new Vec3(pull.x, YANK_UP, pull.z));
		if (target instanceof LivingEntity living) {
			living.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, REEL_TICKS, 2), player);
		}
		player.setAttached(STATE, state.to(YANK, now, target.getId(), head, head));
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.0F, 0.8F);
		Shakes.send(player, 0.4F, target.position());
	}

	/** The ball goes round: a whoosh each turn, rising as it speeds up, and it clips what it passes. */
	private static void whirl(Run run, State state, long t) {
		ServerPlayer player = run.player;
		ServerLevel level = player.level();
		player.causeFoodExhaustion(WHIRL_EXHAUSTION);
		float spin = spin(t);
		double turn = Math.PI * 2.0;
		if (Math.floor(spinAngle(t) / turn) != Math.floor(spinAngle(t - 1) / turn)) {
			level.playSound(null, player.getX(), player.getEyeY() + 0.5, player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS,
				0.4F + 0.4F * spin, 0.7F + 0.6F * spin);
			level.playSound(null, player.getX(), player.getEyeY() + 0.5, player.getZ(), SoundEvents.CHAIN_HIT, SoundSource.PLAYERS, 0.3F, 1.4F);
		}
		if (t < PAY_OUT) {
			return;
		}
		Vec3 ball = head(player, state, level.getGameTime(), 1.0F);
		Vec3 last = head(player, state, level.getGameTime() - 1, 1.0F);
		AABB swept = new AABB(last, ball).inflate(BALL_RADIUS);
		long now = level.getGameTime();
		float damage = flailDamage(player) * GLANCE_SHARE * (0.5F + 0.5F * spin);
		for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, swept, e -> struck(player, e))) {
			Long when = run.glanced.get(other.getId());
			if (when != null && now - when < GLANCE_EVERY) {
				continue;
			}
			run.glanced.put(other.getId(), now);
			if (other.hurtServer(level, level.damageSources().playerAttack(player), damage)) {
				Vec3 push = other.position().subtract(player.position()).multiply(1.0, 0.0, 1.0);
				push = push.lengthSqr() < 1.0E-4 ? Vec3.ZERO : push.normalize().scale(GLANCE_PUSH * (0.5 + spin));
				shove(other, new Vec3(push.x, 0.2, push.z));
				level.playSound(null, other.getX(), other.getY(), other.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 0.8F, 1.0F);
			}
		}
	}

	/** Where a let-go flail comes down: what you're looking at within reach, else the ground that far ahead. */
	private static Vec3 strikeSpot(ServerPlayer player) {
		HitResult aim = aim(player, STRIKE_REACH);
		if (aim instanceof EntityHitResult hit) {
			Entity entity = hit.getEntity();
			return entity.position().add(0.0, Math.min(entity.getBbHeight() * 0.6, 1.4), 0.0);
		}
		if (aim != null) {
			return aim.getLocation();
		}
		Vec3 out = player.position().add(ahead(player).scale(STRIKE_REACH * 0.8)).add(0.0, 1.0, 0.0);
		return ground(player.level(), out);
	}

	/** The ball comes down: the blow, a splash for whoever's beside, dust and a jolt. */
	private static void slam(Run run, State state) {
		ServerPlayer player = run.player;
		ServerLevel level = player.level();
		Vec3 at = state.aim();
		float damage = flailDamage(player) * (SLAM_SHARE + SLAM_SPIN_BONUS * run.spin);
		LivingEntity struck = null;
		double best = SLAM_HIT * SLAM_HIT;
		for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(SPLASH), e -> struck(player, e))) {
			double d = other.getBoundingBox().distanceToSqr(at);
			if (d <= best) {
				best = d;
				struck = other;
			}
		}
		if (struck != null) {
			struck.setInvulnerableTime(0);
			struck.hurtServer(level, level.damageSources().playerAttack(player), damage);
			if (struck instanceof ServerPlayer other) {
				Shakes.send(other, TARGET_SHAKE * (0.5F + run.spin), other.position());
			}
			level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 8 + (int)(10 * run.spin), 0.3, 0.3, 0.3, 0.25);
		}
		for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(SPLASH), e -> struck(player, e))) {
			if (other == struck) {
				continue;
			}
			other.hurtServer(level, level.damageSources().playerAttack(player), damage * SPLASH_SHARE);
			Vec3 push = other.position().subtract(at).multiply(1.0, 0.0, 1.0);
			push = push.lengthSqr() < 1.0E-4 ? Vec3.ZERO : push.normalize().scale(0.4);
			shove(other, new Vec3(push.x, 0.25, push.z));
		}
		player.getMainHandItem().hurtAndBreak(1, player, InteractionHand.MAIN_HAND);
		player.causeFoodExhaustion(SLAM_EXHAUSTION);
		Vec3 ground = ground(level, at);
		boolean onGround = ground.distanceToSqr(at) < 1.5 * 1.5;
		if (onGround) {
			Debris.ring(level, BlockPos.containing(ground).below(), 1 + (run.spin > 0.6F ? 1 : 0), 0.7F + 0.5F * run.spin);
			level.sendParticles(ParticleTypes.POOF, ground.x, ground.y + 0.1, ground.z, 4 + (int)(6 * run.spin), 0.4, 0.05, 0.4, 0.02);
		}
		level.playSound(null, at.x, at.y, at.z, onGround ? SoundEvents.MACE_SMASH_GROUND : SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS,
			0.7F + 0.3F * run.spin, 1.1F - 0.2F * run.spin);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.CHAIN_HIT, SoundSource.PLAYERS, 0.8F, 0.9F);
		Shakes.send(player, SLAM_SHAKE * (0.4F + 0.6F * run.spin), at);
		for (ServerPlayer near : level.players()) {
			if (near != player && near != struck && near.distanceToSqr(at) < NEAR_SHAKE_RANGE * NEAR_SHAKE_RANGE) {
				Shakes.send(near, NEAR_SHAKE * run.spin, at);
			}
		}
	}

	/** The crack lands: a sharp snap, a sting for a creature there, and (still holding) the lash wraps it. */
	private static void crack(Run run, State state) {
		ServerPlayer player = run.player;
		ServerLevel level = player.level();
		long now = level.getGameTime();
		Vec3 at = state.aim();
		level.playSound(null, at.x, at.y, at.z, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 0.9F, 1.9F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 0.7F, 1.6F);
		level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 5, 0.1, 0.1, 0.1, 0.15);
		player.getMainHandItem().hurtAndBreak(1, player, InteractionHand.MAIN_HAND);
		Entity target = level.getEntity(state.target());
		boolean caught = target instanceof LivingEntity && target.getBoundingBox().inflate(1.2).contains(at);
		if (!caught && (fling(player, at) || target instanceof ItemEntity)) {
			back(player, state, now);
			return;
		}
		if (caught) {
			if (target instanceof LivingEntity living && struck(player, living)) {
				living.setInvulnerableTime(0);
				living.hurtServer(level, level.damageSources().playerAttack(player), CRACK_DAMAGE);
			}
			if (run.held && player.isUsingItem() && holdable(player, target)) {
				player.setAttached(STATE, state.to(SNAG, now, target.getId(), at, at));
				level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.LEAD_TIED, SoundSource.PLAYERS, 0.9F, 1.2F);
				return;
			}
		} else {
			BlockPos pos = BlockPos.containing(at.add(player.getLookAngle().scale(0.1)));
			if (!level.getBlockState(pos).isAir()) {
				Debris.puff(level, pos, level.getBlockState(pos), 0.25F);
			}
			// (rappel) still holding, on a wall: it's hooked over the top, trusting it's there
			Vec3 lip = run.held && player.isUsingItem() ? lip(level, player, pos) : null;
			if (lip != null) {
				player.setAttached(STATE, state.to(HOOK, now, -1, at, lip));
				level.playSound(null, lip.x, lip.y, lip.z, SoundEvents.LEAD_TIED, SoundSource.PLAYERS, 1.0F, 0.9F);
				return;
			}
		}
		back(player, state, now);
	}

	/** Wrapped round and held: kept within the lash's slack, drawn in a little if it pulls away. */
	private static void hold(ServerPlayer player, Entity target) {
		Vec3 to = player.position().subtract(target.position());
		double gap = to.length();
		if (gap > HOLD_SLACK) {
			Vec3 pull = to.scale(HOLD_PULL * (gap - HOLD_SLACK) / gap);
			Vec3 v = target.getDeltaMovement();
			// it can't walk off along the lash
			Vec3 dir = to.scale(1.0 / gap);
			double away = v.dot(dir);
			if (away < 0.0) {
				v = v.subtract(dir.scale(away));
			}
			shove(target, v.add(pull.x, Math.max(0.0, pull.y), pull.z).subtract(target.getDeltaMovement()));
		}
		if (target instanceof Mob mob) {
			mob.getNavigation().stop();
		}
	}

	/** The haul's path for a light catch: from where it was, up over your head, then down in front of you. */
	private static Vec3 heavePoint(ServerPlayer player, State state, Entity target, long t) {
		Vec3 feet = player.position();
		double half = target.getBbHeight() * 0.5;
		Vec3 over = feet.add(0.0, player.getBbHeight() + 2.6 + half, 0.0).subtract(ahead(player).scale(0.6));
		Vec3 front = feet.add(ahead(player).scale(HEAVE_REACH + target.getBbWidth() * 0.5)).add(0.0, half - 0.6, 0.0);
		Vec3 from = state.from().add(0.0, half, 0.0);
		if (t <= HEAVE_OVER) {
			return from.lerp(over, smooth(t / (double)HEAVE_OVER));
		}
		return over.lerp(front, smooth(Math.min(1.0, (t - HEAVE_OVER) / (double)(HEAVE_SLAM - HEAVE_OVER))));
	}

	private static void steer(Entity target, Vec3 point) {
		Vec3 v = point.subtract(target.getBoundingBox().getCenter()).scale(STEER);
		double speed = v.length();
		if (speed > MAX_SPEED) {
			v = v.scale(MAX_SPEED / speed);
		}
		shove(target, v.subtract(target.getDeltaMovement()));
		target.resetFallDistance();
		if (target instanceof Mob mob) {
			mob.getNavigation().stop();
		}
	}

	/** Hauled over and brought down: the blow, dust, a bounce, and every screen near it shakes. */
	private static void heaveSlam(Run run, Entity target) {
		ServerPlayer player = run.player;
		ServerLevel level = player.level();
		if (run.hadGravity) {
			target.setNoGravity(false);
		}
		Vec3 ground = ground(level, target.position());
		if (target instanceof LivingEntity living) {
			living.setInvulnerableTime(0);
			living.hurtServer(level, level.damageSources().playerAttack(player), WHIP_SLAM_DAMAGE);
		}
		if (target instanceof ServerPlayer struck) {
			Shakes.send(struck, TARGET_SHAKE, struck.position());
		}
		Debris.ring(level, BlockPos.containing(ground).below(), 1, 0.9F);
		level.sendParticles(ParticleTypes.POOF, ground.x, ground.y + 0.1, ground.z, 8, 0.4, 0.05, 0.4, 0.02);
		level.playSound(null, ground.x, ground.y, ground.z, SoundEvents.MACE_SMASH_GROUND, SoundSource.PLAYERS, 0.9F, 1.0F);
		Shakes.send(player, SLAM_SHAKE * 0.8F, ground);
		for (ServerPlayer near : level.players()) {
			if (near != player && near != target && near.distanceToSqr(ground) < NEAR_SHAKE_RANGE * NEAR_SHAKE_RANGE) {
				Shakes.send(near, NEAR_SHAKE, ground);
			}
		}
		shove(target, target.getDeltaMovement().multiply(-0.8, 0.0, -0.8).add(0.0, 0.3 - target.getDeltaMovement().y, 0.0));
	}

	/** The head flies back to the hand (a whip's catch, if any, is let go). */
	private static void back(ServerPlayer player, State state, long now) {
		Vec3 head = head(player, state, now, 1.0F);
		player.setAttached(STATE, state.to(RETURN, now, -1, head, head));
	}

	/** It's over: the lash is put away, the cooldown begins, anything held up falls. */
	private static void end(Run run, @Nullable State state) {
		ServerPlayer player = run.player;
		player.removeAttached(STATE);
		if (state == null) {
			return;
		}
		if (state.kind() == FLAIL) {
			flailThrown(player, false);
		}
		if (state.phase() == HEAVE && run.hadGravity) {
			Entity target = player.level().getEntity(state.target());
			if (target != null) {
				target.setNoGravity(false);
			}
		}
		ItemStack held = player.getMainHandItem();
		if (!run.swipe && (state.kind() == FLAIL ? isFlail(held) : isWhip(held))) {
			player.getCooldowns().addCooldown(held, state.kind() == FLAIL ? FLAIL_COOLDOWN : WHIP_COOLDOWN);
		}
	}

	/**
	 * (0.16.24) A left-click with a flail or whip out of use (an air swing, or a blow at {@code attacked}): charged
	 * enough, the head swipes round in front of you ({@link #SWEEP}), clipping all it passes but what you struck.
	 * Called before the swing's charge is spent.
	 */
	public static void swipe(ServerPlayer player, @Nullable Entity attacked) {
		ItemStack held = player.getMainHandItem();
		boolean flail = isFlail(held);
		if (!flail && !isWhip(held) || player.getAttached(STATE) != null || player.isUsingItem() || player.isSpectator()
			|| player.getCooldowns().isOnCooldown(held) || player.getAttackStrengthScale(0.5F) < SWEEP_MIN_CHARGE) {
			return;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		Run run = new Run(player);
		run.held = false;
		run.swipe = true;
		run.spin = player.getAttackStrengthScale(0.5F);
		if (attacked != null) {
			run.glanced.put(attacked.getId(), now);
		}
		RUNS.put(player.getUUID(), run);
		Vec3 aim = new Vec3(player.getYRot() * Mth.DEG_TO_RAD, player.getXRot() * Mth.DEG_TO_RAD, flail ? SWEEP_FLAIL_REACH : SWEEP_WHIP_REACH);
		player.setAttached(STATE, new State(flail ? FLAIL : WHIP, SWEEP, now, -1, hand(player, 1.0F), aim));
		level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.7F,
			flail ? 0.8F : 1.3F);
		if (flail) {
			level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.CHAIN_HIT, SoundSource.PLAYERS, 0.4F, 1.2F);
		}
	}

	/** One tick of a swipe: whatever the head (and a whip's lash near it) passed through is struck, once. */
	private static void sweep(Run run, State state, long t) {
		if (t < 1) {
			return;
		}
		ServerPlayer player = run.player;
		ServerLevel level = player.level();
		long now = level.getGameTime();
		Vec3 hand = hand(player, 1.0F);
		Vec3 head = head(player, state, now, 1.0F), last = head(player, state, now - 1, 1.0F);
		boolean flail = state.kind() == FLAIL;
		AABB swept = new AABB(last, head).inflate(SWEEP_HIT);
		if (!flail) {
			// the lash's outer half sweeps too
			swept = swept.minmax(new AABB(hand.lerp(last, 0.5), hand.lerp(head, 0.5)).inflate(SWEEP_HIT * 0.5));
		}
		float damage = (flail ? flailDamage(player) * SWEEP_SHARE : SWEEP_WHIP_DAMAGE) * run.spin;
		for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, swept, e -> struck(player, e))) {
			if (run.glanced.containsKey(other.getId())) {
				continue;
			}
			run.glanced.put(other.getId(), now);
			if (other.hurtServer(level, level.damageSources().playerAttack(player), damage)) {
				Vec3 push = head.subtract(last).multiply(1.0, 0.0, 1.0);
				push = push.lengthSqr() < 1.0E-4 ? Vec3.ZERO : push.normalize().scale(SWEEP_PUSH);
				shove(other, new Vec3(push.x, 0.1, push.z));
				level.playSound(null, other.getX(), other.getY(), other.getZ(), flail ? SoundEvents.CHAIN_HIT : SoundEvents.PLAYER_ATTACK_CRIT,
					SoundSource.PLAYERS, 0.7F, flail ? 0.9F : 1.6F);
				Vec3 at = other.getBoundingBox().getCenter();
				level.sendParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
			}
		}
	}

	/** (0.16.24) A crack landed at {@code at}: dropped items there fly back to you. Whether there were any. */
	private static boolean fling(ServerPlayer player, Vec3 at) {
		List<ItemEntity> items = player.level().getEntitiesOfClass(ItemEntity.class, new AABB(at, at).inflate(ITEM_GRAB), Entity::isAlive);
		for (ItemEntity item : items) {
			Vec3 to = player.position().subtract(item.position());
			// the fishing rod's toss: most of the way each tick, lofted by how far
			shove(item, new Vec3(to.x * ITEM_FLING, to.y * ITEM_FLING + Math.sqrt(to.length()) * ITEM_LOFT, to.z * ITEM_FLING)
				.subtract(item.getDeltaMovement()));
		}
		if (!items.isEmpty()) {
			player.level().playSound(null, at.x, at.y, at.z, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.5F, 0.8F);
		}
		return !items.isEmpty();
	}

	/** (0.16.24) The nearest dropped item along the line from your eye to {@code end}, within {@link #ITEM_AIM} of it. */
	private static @Nullable ItemEntity aimItem(Player player, Vec3 end) {
		Vec3 eye = player.getEyePosition();
		Vec3 line = end.subtract(eye);
		double length = line.length();
		if (length < 1.0E-4) {
			return null;
		}
		Vec3 dir = line.scale(1.0 / length);
		ItemEntity best = null;
		double nearest = Double.MAX_VALUE;
		for (ItemEntity item : player.level().getEntitiesOfClass(ItemEntity.class, new AABB(eye, end).inflate(ITEM_AIM), Entity::isAlive)) {
			Vec3 at = item.position().add(0.0, item.getBbHeight() * 0.5, 0.0).subtract(eye);
			double along = Mth.clamp(at.dot(dir), 0.0, length);
			if (at.subtract(dir.scale(along)).length() <= ITEM_AIM && along < nearest) {
				nearest = along;
				best = item;
			}
		}
		return best;
	}

	/** (0.16.24) Hanging off a hooked lash (the server mustn't take it for flying). */
	public static boolean hooked(Player player) {
		State state = player.getAttached(STATE);
		return state != null && state.phase() == HOOK;
	}

	// ---- helpers ----------------------------------------------------------------------------------------------------

	/** What's under the crosshair within {@code range}: a creature the lash could strike, else a block (null: neither). */
	private static @Nullable HitResult aim(Player player, double range) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0F);
		Vec3 end = eye.add(look.scale(range));
		BlockHitResult block = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		if (block.getType() != HitResult.Type.MISS) {
			end = block.getLocation();
		}
		AABB box = player.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0);
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, box.inflate(0.3),
			e -> e instanceof LivingEntity living && struck(player, living), eye.distanceToSqr(end));
		if (hit != null) {
			return hit;
		}
		return block.getType() == HitResult.Type.MISS ? null : block;
	}

	/**
	 * (rappel) The top of the wall {@code hit} is part of, if the lash could hook over it: straight up from {@code hit},
	 * solid all the way to a top with two blocks of room over it, no more than {@link #CLIMB_HEIGHT} above your feet and
	 * within the lash's reach. Its lip: the top's edge on your side.
	 */
	private static @Nullable Vec3 lip(Level level, Player player, BlockPos hit) {
		double feet = player.getY();
		for (BlockPos pos = hit; pos.getY() <= Mth.floor(feet + CLIMB_HEIGHT); pos = pos.above()) {
			if (!solid(level, pos)) {
				// a gap: not one wall
				return null;
			}
			if (solid(level, pos.above()) || solid(level, pos.above(2))) {
				continue;
			}
			double top = pos.getY() + level.getBlockState(pos).getCollisionShape(level, pos).max(net.minecraft.core.Direction.Axis.Y);
			if (top - feet > CLIMB_HEIGHT || top - feet < 1.0) {
				return null;
			}
			Vec3 middle = Vec3.atBottomCenterOf(pos);
			net.minecraft.core.Direction side = net.minecraft.core.Direction.getApproximateNearest(player.getX() - middle.x, 0.0, player.getZ() - middle.z);
			Vec3 lip = new Vec3(middle.x + side.getStepX() * 0.5, top, middle.z + side.getStepZ() * 0.5);
			return lip.distanceTo(player.getEyePosition()) <= WHIP_REACH + 1.5 ? lip : null;
		}
		return null;
	}

	private static boolean solid(Level level, BlockPos pos) {
		return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
	}

	/** Something a swung head can hit: alive, not you or your mount, not a stand, not someone in creative. */
	private static boolean struck(Player player, LivingEntity entity) {
		return entity != player && entity.isAlive() && !entity.isSpectator() && entity.isAttackable() && !(entity instanceof ArmorStand)
			&& entity != player.getVehicle() && !player.hasPassenger(entity) && !player.isAlliedTo(entity)
			&& !(entity instanceof Player other && (other.isCreative() || other.isSpectator()));
	}

	/** Something the whip can wrap and hold: a living creature it can strike, not a boss. */
	private static boolean holdable(Player player, @Nullable Entity target) {
		return target instanceof LivingEntity living && struck(player, living) && !(target instanceof EnderDragon) && !(target instanceof WitherBoss);
	}

	private static boolean liftable(Entity target) {
		return target.getBbWidth() * target.getBbWidth() * target.getBbHeight() <= LIFT_VOLUME;
	}

	/** The flail's own blow (its attack damage, as held). */
	private static float flailDamage(ServerPlayer player) {
		return (float)Math.max(2.0, player.getAttributeValue(Attributes.ATTACK_DAMAGE));
	}

	/** Adds {@code dv} to the entity's motion, and tells its client (a player's own motion is theirs to move). */
	private static void shove(Entity entity, Vec3 dv) {
		entity.setDeltaMovement(entity.getDeltaMovement().add(dv));
		entity.needsSync = true;
		entity.syncVelocity = true;
		if (entity instanceof ServerPlayer other) {
			ServerPlayNetworking.send(other, new com.jaymospowers.power.web.WebNet.Shove(dv));
		}
	}

	/** The top of the ground under {@code at} (within a few blocks down; else {@code at} itself). */
	private static Vec3 ground(ServerLevel level, Vec3 at) {
		BlockHitResult hit = level.clip(new ClipContext(at.add(0.0, 1.0, 0.0), at.add(0.0, -4.0, 0.0), ClipContext.Block.COLLIDER,
			ClipContext.Fluid.ANY, net.minecraft.world.phys.shapes.CollisionContext.empty()));
		return hit.getType() == HitResult.Type.MISS ? at : hit.getLocation();
	}

	/**
	 * Balkon's flail draws its ball-less model while its ball is out (its {@code PlayerWeaponData.setFlailThrown}): so
	 * the ball isn't in the hand and on the chain at once. Reached by name, as nothing here compiles against WeaponMod.
	 */
	public static void flailThrown(ServerPlayer player, boolean thrown) {
		try {
			Class.forName("ckathode.weaponmod.PlayerWeaponData").getMethod("setFlailThrown", Player.class, boolean.class)
				.invoke(null, player, thrown);
		} catch (ReflectiveOperationException | LinkageError e) {
			// an older WeaponMod: the ball just shows in the hand too
		}
	}
}
