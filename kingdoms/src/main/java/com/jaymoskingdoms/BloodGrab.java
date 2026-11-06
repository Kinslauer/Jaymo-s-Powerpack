package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.615) The grab-drink. Andy: "double tapping the right click gives your drink a one handed grab animation where you can
 * walk with your target, draining blood more rapidly, so be careful as it can kill quicker. releasing early strong throws
 * the target, and if you are nourished enough the throw can break blocks weaker than stone at close range".
 * <p>
 * A vampire who taps use on their prey twice in quick succession (the first tap is the usual sneak bite) takes it by the
 * throat in one hand ({@link #GRABBING}, synced: the client's {@code BloodGrabPose}) and holds it up in front of them, its
 * feet off the ground, while they walk ({@link #place}). Held, it can't fight back (its AI waits) and is drunk from
 * every {@link #DRAIN_EVERY} ticks, twice the bite's pace, so it dies twice as fast. Letting go of use while it still lives
 * throws it hard the way they look ({@link #THROW_SPEED}); a vampire well fed ({@link #NOURISHED_FOOD}) throws it through
 * whatever is softer than stone ({@link #breakable}) for the first {@link #BREAK_RANGE} blocks of its flight. It dies in the
 * hold, the vampire is hurt off it or downed, or it's torn away, and it's simply dropped.
 * <p>
 * (0.7.633) Andy (2026-11-06): "the grab should animate within the vampire's hand hands at the throat the whole time, even
 * if they go down". A held body going down stays up in the hand, hanging from it (Powers no longer lays it on the ground
 * while it's held: {@link #grabbed}), and its throat is put where the vampire's hand really is: the arm follows the look up
 * and down ({@link #raise}), and the body is placed at the hand's end of that arm ({@link #place}), so in first person
 * (First-person Model draws the arm) and third the hand is on the throat. Held, it claws at the wrist and kicks; once
 * down it hangs limp ({@code BloodGrabPose}).
 * <p>
 * Prey: whoever the bite takes that a hand can hold, grown villagers and folk ({@link Vampires#person}) and the Nether's
 * piglins and Hornkin ({@link Vampires#netherPrey}); never a player.
 */
public final class BloodGrab {
	/** The second tap must come within this many ticks of the first (checked on the client, which sends {@link Start}). */
	public static final int DOUBLE_TAP_TICKS = 8;
	/** A grab reaches this far (blocks, eyes to the prey's box). */
	static final double REACH = 3.0;
	/**
	 * (0.7.633) Where the hand is, as shares of the vampire's height (the player model's 32 pixels): the shoulder joint
	 * ({@code SHOULDER} up), the arm's length from it to the hand ({@code ARM}), the hand's way out to the side of the
	 * middle with the arm turned in ({@code SIDE}); the throat at this share of the prey's height ({@code THROAT}), its
	 * middle this far behind the throat (blocks, {@code NECK}); and never closer than this between the two boxes ({@code GAP}).
	 */
	static final double SHOULDER = 22.0 / 32.0, ARM = 10.0 / 32.0, SIDE = 3.0 / 32.0, THROAT = 0.76, NECK = 0.2, GAP = 0.05;
	/** (0.7.633) A body much taller than the vampire is held no lower than its feet this far into the ground (blocks). */
	static final double FEET_SINK = 0.3;
	/**
	 * (0.7.633) How far the holding arm is raised above level (radians) looking straight ahead, how much of the look's
	 * pitch it follows, and its limits (down, so the feet don't sink far into the ground; up).
	 */
	public static final float RAISE = 0.3F, RAISE_PER_PITCH = 0.8F, RAISE_MIN = -0.25F, RAISE_MAX = 0.9F;
	/** Torn away past this (blocks): the hold ends. */
	static final double LEASH = 4.0;
	/** A drink every this many ticks while held (the bite's own pace is {@link VampirePlayers#DRINK_EVERY}). */
	static final int DRAIN_EVERY = 10;
	/** The vampire walks slowed this much (Slowness amplifier) while holding someone up. */
	static final int CARRY_SLOWNESS = 0;
	/** The throw: speed along the look (blocks a tick) and lift. */
	static final double THROW_SPEED = 1.7, THROW_LIFT = 0.3;
	/** Food level from which a vampire is fed enough to throw through what's softer than stone, and how far that lasts (blocks). */
	static final int NOURISHED_FOOD = 16;
	static final double BREAK_RANGE = 6.0;
	/** A thrown body is watched this long (ticks); it's hurt this much for slamming into something at speed (at least this fast, b/t). */
	static final int THROWN_TICKS = 30;
	static final float SLAM_DAMAGE = 5.0F;
	static final double SLAM_SPEED = 0.6;

	/** The held prey's entity id while a vampire has it by the throat, synced to everyone (the hand that holds it). */
	public static final AttachmentType<Integer> GRABBING = AttachmentRegistry.create(JaymosKingdoms.id("blood_grab"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));

	/** Client to server: grab the prey with this id (a double tap of use on it). */
	public record Start(int target) implements CustomPacketPayload {
		public static final Type<Start> TYPE = new Type<>(JaymosKingdoms.id("blood_grab_start"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Start> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Start::target, Start::new);

		@Override
		public Type<Start> type() {
			return TYPE;
		}
	}

	/** Client to server: use let go while holding someone: throw them. */
	public record Release() implements CustomPacketPayload {
		public static final Type<Release> TYPE = new Type<>(JaymosKingdoms.id("blood_grab_release"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Release> CODEC = StreamCodec.unit(new Release());

		@Override
		public Type<Release> type() {
			return TYPE;
		}
	}

	/** One hold: the prey, how it was before (AI, gravity), and when it was taken. */
	private static final class Hold {
		final LivingEntity target;
		final boolean hadNoAi, hadNoGravity;
		final long since;

		Hold(LivingEntity target, long since) {
			this.target = target;
			this.hadNoAi = target instanceof Mob mob && mob.isNoAi();
			this.hadNoGravity = target.isNoGravity();
			this.since = since;
		}
	}

	/** A throw in flight: who threw it, from where, when, and whether it breaks what's softer than stone. */
	private static final class Throw {
		final LivingEntity body;
		final ServerPlayer thrower;
		final Vec3 from;
		final long at;
		final boolean breaks;
		/** Its speed last tick (a wall stops it dead, so the slam reads the speed it hit at). */
		double speed;

		Throw(LivingEntity body, ServerPlayer thrower, Vec3 from, long at, boolean breaks) {
			this.body = body;
			this.thrower = thrower;
			this.from = from;
			this.at = at;
			this.breaks = breaks;
			this.speed = THROW_SPEED;
		}
	}

	private static final Map<UUID, Hold> HOLDS = new HashMap<>();
	private static final Map<UUID, Throw> THROWS = new HashMap<>();

	private BloodGrab() {
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Start.TYPE, Start.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Release.TYPE, Release.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Start.TYPE, (payload, context) -> start(context.player(), payload.target()));
		ServerPlayNetworking.registerGlobalReceiver(Release.TYPE, (payload, context) -> release(context.player(), true));
		ServerTickEvents.END_SERVER_TICK.register(BloodGrab::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> release(handler.player, false));
	}

	/** Whether {@code player} has someone by the throat now. */
	static boolean holding(ServerPlayer player) {
		return HOLDS.containsKey(player.getUUID());
	}

	/** Prey a hand can hold up: the bite's people and the Nether's piglins and Hornkin, never a player; (0.7.625) downed ones too, but not one being carried. */
	static boolean prey(LivingEntity e) {
		return !(e instanceof net.minecraft.world.entity.player.Player) && (Vampires.person(e) || Vampires.netherPrey(e)) && !e.isPassenger();
	}

	static void start(ServerPlayer player, int id) {
		if (holding(player) || !Vampires.isVampire(player) || !player.isAlive() || Downed.isDowned(player) || !player.getMainHandItem().isEmpty()
			|| !(player.level() instanceof ServerLevel level) || !(level.getEntity(id) instanceof LivingEntity target) || !prey(target)) {
			return;
		}
		if (target.getBoundingBox().distanceToSqr(player.getEyePosition()) > REACH * REACH || isHeld(target)) {
			return;
		}
		Hold hold = new Hold(target, level.getGameTime());
		HOLDS.put(player.getUUID(), hold);
		THROWS.remove(target.getUUID());
		if (target instanceof Mob mob) {
			mob.setNoAi(true);
			mob.getNavigation().stop();
		}
		target.setNoGravity(true);
		player.setAttached(GRABBING, target.getId());
		level.playSound(null, target.blockPosition(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 0.8F, 0.6F);
		place(player, target);
		// the first gulp straight away
		VampirePlayers.drinkNow(level, player, target, Vampires.person(target));
	}

	/** Whether some vampire holds {@code e} now. */
	static boolean isHeld(Entity e) {
		for (Hold h : HOLDS.values()) {
			if (h.target == e) {
				return true;
			}
		}
		return false;
	}

	/** Lets go: {@code toss} throws them if they still live, else they drop where they are. */
	static void release(ServerPlayer player, boolean toss) {
		Hold hold = HOLDS.remove(player.getUUID());
		player.removeAttached(GRABBING);
		if (hold == null) {
			return;
		}
		LivingEntity target = hold.target;
		if (target instanceof Mob mob) {
			mob.setNoAi(hold.hadNoAi);
		}
		target.setNoGravity(hold.hadNoGravity);
		if (!toss || !target.isAlive() || target.isRemoved() || !(player.level() instanceof ServerLevel level)) {
			target.setDeltaMovement(player.getDeltaMovement().scale(0.5));
			target.needsSync = true;
			return;
		}
		Vec3 look = player.getLookAngle();
		target.setDeltaMovement(look.scale(THROW_SPEED).add(0.0, THROW_LIFT, 0.0));
		target.needsSync = true;
		boolean breaks = player.getFoodData().getFoodLevel() >= NOURISHED_FOOD;
		THROWS.put(target.getUUID(), new Throw(target, player, target.position(), level.getGameTime(), breaks));
		if (breaks) {
			// clear its first tick's path now, before it moves into it
			smash(level, player, target, target.getDeltaMovement());
		}
		level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.0F, 0.7F);
	}

	private static void tick(MinecraftServer server) {
		for (Iterator<Map.Entry<UUID, Hold>> it = HOLDS.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Hold> e = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
			Hold hold = e.getValue();
			LivingEntity target = hold.target;
			boolean gone = player == null || !player.isAlive() || Downed.isDowned(player) || !Vampires.isVampire(player) || !target.isAlive()
				|| target.isRemoved() || target.level() != player.level() || !player.getMainHandItem().isEmpty()
				|| target.distanceTo(player) > LEASH;
			if (gone) {
				it.remove();
				if (player != null) {
					player.removeAttached(GRABBING);
				}
				if (target instanceof Mob mob) {
					mob.setNoAi(hold.hadNoAi);
				}
				target.setNoGravity(hold.hadNoGravity);
				continue;
			}
			ServerLevel level = (ServerLevel) player.level();
			place(player, target);
			player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 10, CARRY_SLOWNESS, false, false, false));
			if ((level.getGameTime() - hold.since) % DRAIN_EVERY == 0 && level.getGameTime() != hold.since) {
				VampirePlayers.drinkNow(level, player, target, Vampires.person(target));
			}
		}
		if (THROWS.isEmpty()) {
			return;
		}
		for (Iterator<Map.Entry<UUID, Throw>> it = THROWS.entrySet().iterator(); it.hasNext(); ) {
			Throw t = it.next().getValue();
			LivingEntity body = t.body;
			if (body.isRemoved() || !(body.level() instanceof ServerLevel level) || level.getGameTime() - t.at > THROWN_TICKS
				|| (body.onGround() && level.getGameTime() - t.at > 2)) {
				it.remove();
				continue;
			}
			Vec3 v = body.getDeltaMovement();
			double speed = v.horizontalDistance();
			if (t.breaks && body.position().distanceTo(t.from) <= BREAK_RANGE && speed > 0.2) {
				smash(level, t.thrower, body, v);
			}
			if (body.horizontalCollision && t.speed > SLAM_SPEED) {
				body.hurtServer(level, level.damageSources().playerAttack(t.thrower), SLAM_DAMAGE);
				it.remove();
				continue;
			}
			t.speed = speed;
		}
	}

	/** (0.7.633) How far the holding arm is raised above level (radians) for a look pitched {@code pitch} degrees (down is positive). */
	public static float raise(float pitch) {
		return Mth.clamp(RAISE - pitch * Mth.DEG_TO_RAD * RAISE_PER_PITCH, RAISE_MIN, RAISE_MAX);
	}

	/**
	 * (0.7.633) The vampire holding {@code e} up by the throat now, or null: read off everyone's synced {@link #GRABBING}, so
	 * it answers the same on the client (the poses; Powers asks it by reflection so a held body isn't laid down).
	 */
	public static net.minecraft.world.entity.player.@Nullable Player holder(Entity e) {
		for (net.minecraft.world.entity.player.Player p : e.level().players()) {
			Integer id = p.getAttached(GRABBING);
			if (id != null && id == e.getId() && p != e) {
				return p;
			}
		}
		return null;
	}

	/** (0.7.633) Whether a vampire holds {@code e} up by the throat now (client or server). */
	public static boolean grabbed(Entity e) {
		return holder(e) != null;
	}

	/**
	 * Holds {@code target} up by the throat in front of {@code player}, facing them: (0.7.633) the throat at the hand's end
	 * of the arm as {@code BloodGrabPose} draws it, raised with the look ({@link #raise}), on the main hand's side.
	 */
	private static void place(ServerPlayer player, LivingEntity target) {
		float yaw = player.getYRot() * Mth.DEG_TO_RAD;
		double fx = -Mth.sin(yaw), fz = Mth.cos(yaw);
		// the right hand's side (facing +z, the right is -x)
		double side = player.getMainArm() == net.minecraft.world.entity.HumanoidArm.LEFT ? -1.0 : 1.0;
		double rx = -Mth.cos(yaw) * side, rz = -Mth.sin(yaw) * side;
		double h = player.getBbHeight();
		float up = raise(player.getXRot());
		double arm = ARM * h;
		double ahead = Math.max(arm * Mth.cos(up) + NECK, (player.getBbWidth() + target.getBbWidth()) * 0.5 + GAP);
		double x = player.getX() + fx * ahead + rx * SIDE * h, z = player.getZ() + fz * ahead + rz * SIDE * h;
		// the throat at the hand
		double y = player.getY() + h * SHOULDER + arm * Mth.sin(up) - target.getBbHeight() * THROAT;
		// (a body much taller than the vampire still only sinks its feet a little into the ground)
		y = Math.max(y, player.getY() - FEET_SINK);
		target.setPos(x, y, z);
		target.setDeltaMovement(Vec3.ZERO);
		target.fallDistance = 0.0F;
		float face = player.getYRot() + 180.0F;
		target.setYRot(face);
		target.setYHeadRot(face);
		target.setYBodyRot(face);
	}

	/** A thrown body bursting through what's softer than stone in its way. */
	private static void smash(ServerLevel level, ServerPlayer thrower, LivingEntity body, Vec3 v) {
		AABB box = body.getBoundingBox().expandTowards(v).inflate(0.1);
		for (BlockPos pos : BlockPos.betweenClosed(Mth.floor(box.minX), Mth.floor(box.minY), Mth.floor(box.minZ), Mth.floor(box.maxX),
			Mth.floor(box.maxY), Mth.floor(box.maxZ))) {
			BlockState state = level.getBlockState(pos);
			if (breakable(level, pos, state)) {
				level.destroyBlock(pos, true, thrower);
			}
		}
	}

	/** Softer than stone: not air or a fluid, breakable at all, and quicker to break than stone. */
	static boolean breakable(ServerLevel level, BlockPos pos, BlockState state) {
		if (state.isAir() || !state.getFluidState().isEmpty()) {
			return false;
		}
		float hardness = state.getDestroySpeed(level, pos);
		return hardness >= 0.0F && hardness < Blocks.STONE.defaultBlockState().getDestroySpeed(level, pos);
	}

	/** The prey {@code player} holds now, or null. */
	static @Nullable LivingEntity held(ServerPlayer player) {
		Hold h = HOLDS.get(player.getUUID());
		return h == null ? null : h.target;
	}
}
