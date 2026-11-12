package com.jaymospowers.power.web;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.BlockCluster;
import com.jaymospowers.core.Crushing;
import com.jaymospowers.core.Debris;
import com.jaymospowers.core.GiantTrees;
import com.jaymospowers.core.Shakes;
import com.jaymospowers.power.gravity.GravityShockwave;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The big suit's ground-wrecking moves (the server side of all four; the pose is the client's {@code WebBrutePose}).
 * <ul>
 * <li><b>Rip and throw</b> (G at a block within {@link #RIP_RANGE}, standing, nothing in the way to grab): a struggle of
 * {@link #RIP_TICKS}, the blocks cracking and the screen shaking, then a ragged chunk of up to {@link #RIP_PIECES} blocks
 * tears out ({@link BlockCluster}, the same chunk Gravity's Lift and the tendrils tear) and is held overhead. Left click
 * hurls it where you look; G again drops it. With Jaymo's Kingdoms, G at a felled tree (or a standing one) lifts that
 * instead, through {@link GiantTrees}, and it is thrown the same way.</li>
 * <li><b>Ground tear</b> (Sneak + G aimed at the ground, standing, both hands empty): both fists drive in and a line of
 * ground {@link #TEAR_LENGTH} blocks long erupts ahead, a step a tick, throwing up lumps of earth ({@link GravityShockwave#lump}),
 * hurting and tossing what is on it.</li>
 * <li><b>Shoulder ram</b> (gallop for {@link #RAM_BUILD_TICKS}): the charge builds under your feet; charged, the first
 * creature run into is hit hard and flung, striking a wall within a few blocks for more; soft blocks in the way break;
 * a hard wall staggers you.</li>
 * </ul>
 * Everything is synced as a {@link Move} so everyone sees the pose.
 */
public final class WebBrute {
	/** What the suit is doing (the {@link Move} kinds). */
	public static final int RIP = 1, HOLD = 2, TEAR = 3, RAM_BUILD = 4, RAM = 5, STAGGER = 6, THROW = 7;

	// ===== rip and throw ============================================================================================
	/** How far the block can be, how many blocks come out, the hardest of them (as the tendrils' rip), how long the struggle is (ticks). */
	static final double RIP_RANGE = 4.0;
	static final int RIP_PIECES = 6;
	static final float RIP_HARDEST = 4.0F;
	public static final int RIP_TICKS = 20;
	/** Drifting this far (blocks) beyond the range from the wall calls the struggle off. */
	static final double RIP_LEASH = 3.0;
	/** The wearer's screen: a rumble while it struggles (and how often, ticks), the jolt as the chunk tears free. */
	static final float RIP_RUMBLE = 0.35F, RIP_TEAR_SHAKE = 1.4F;
	static final int RIP_RUMBLE_EVERY = 3;
	/** Held: this far above the head (blocks), rising there over {@code HOLD_RISE} ticks; at most {@code HOLD_MAX_TICKS}. */
	static final double HOLD_CLEAR = 0.3;
	static final int HOLD_RISE = 8, HOLD_MAX_TICKS = 600;
	/** The throw: speed along the look (blocks a tick), a little lift, the pose's length (ticks), the wearer's jolt. */
	static final double THROW_SPEED = 1.9, THROW_LIFT = 0.12;
	public static final int THROW_TICKS = 8;
	static final float THROW_SHAKE = 1.0F;
	/** A tree: how far round the aim it is reached for (blocks), where along the look it is looked for, how fast it is thrown. */
	static final double TREE_REACH = 2.5, TREE_HAND_UP = 0.9, TREE_THROW_SPEED = 1.5;
	static final double[] TREE_PROBES = {1.5, 3.0, 4.5};
	/** Ticks before the next move can begin. */
	static final int MOVE_COOLDOWN = 20;

	// ===== ground tear ==============================================================================================
	/** How far the ground aimed at can be; the fists' wind-up, the line's length (a block a tick) and the recovery (ticks). */
	static final double TEAR_RANGE = 6.0;
	public static final int TEAR_WINDUP = 6, TEAR_LENGTH = 10, TEAR_RECOVER = 8;
	/** The line starts this far ahead of you; its half width and height for what it hits; the lumps stand this far to each side. */
	static final double TEAR_START = 1.2, TEAR_HALF_WIDTH = 1.7, TEAR_HEIGHT = 2.0, TEAR_LATERAL = 0.8;
	/** What a step does to a creature on it: damage, and the toss (to the side, along the line, up). */
	static final float TEAR_DAMAGE = 9.0F;
	static final double TEAR_TOSS_SIDE = 0.5, TEAR_TOSS_ALONG = 0.35, TEAR_TOSS_UP = 0.85;
	/** The cracks left in the ground (stages), the shakes (the fists landing, each step), and how far others feel them. */
	static final int TEAR_CRACK_MIN = 3, TEAR_CRACK_MAX = 6;
	static final float TEAR_SLAM_SHAKE = 1.6F, TEAR_STEP_SHAKE = 0.5F;
	static final double TEAR_SHAKE_RANGE = 24.0;

	// ===== shoulder ram =============================================================================================
	/** Galloping this many ticks builds the charge; the gallop must move at least this fast (blocks a tick), with this much grace (ticks). */
	public static final int RAM_BUILD_TICKS = 30;
	static final double RAM_MIN_SPEED = 0.2, RAM_KEEP_SPEED = 0.1;
	static final int RAM_GRACE = 6, RAM_SLACK_END = 12, RAM_MAX_TICKS = 240, RAM_COOLDOWN = 40;
	/** The hit: damage, the fling (along the run, and up), how far ahead of the body it reaches (blocks). */
	static final float RAM_DAMAGE = 14.0F;
	static final double RAM_KNOCK = 1.4, RAM_LIFT = 0.3, RAM_REACH = 0.7;
	/** A flung creature that strikes a wall within {@code RAM_FLING_TICKS} (having moved at least {@code RAM_WALL_SPEED} a tick) takes this much more. */
	static final float RAM_WALL_DAMAGE = 8.0F;
	static final int RAM_FLING_TICKS = 12;
	static final double RAM_WALL_SPEED = 0.5;
	/** The softest-to-hardest blocks it breaks: planks (and logs, fences, leaves...) up to this, doors up to this, glass up to this. */
	static final float RAM_HARDEST = 2.0F, RAM_DOOR_HARDEST = 3.0F, RAM_GLASS_HARDEST = 0.5F;
	static final int RAM_BREAKS_PER_TICK = 10;
	static final float RAM_DROP_CHANCE = 0.5F;
	/** Running into a hard wall: slowed for {@code STAGGER_TICKS} (amplifier {@code STAGGER_SLOW}), thrown back this much. */
	public static final int STAGGER_TICKS = 24;
	static final int STAGGER_SLOW = 2;
	static final double STAGGER_SHOVE = 0.6;
	/** The wearer's screen: a rumble building (and charged), the jolt as the charge is ready, as it hits, as it staggers. */
	static final float RAM_BUILD_RUMBLE = 0.2F, RAM_RUMBLE = 0.25F, RAM_READY_SHAKE = 0.8F, RAM_HIT_SHAKE = 1.4F, STAGGER_SHAKE = 2.0F;
	static final int RAM_RUMBLE_EVERY = 4;

	/** What the suit is doing now and since when (game time): synced to everyone, who see the pose. */
	public record Move(int kind, long since) {
		public static final StreamCodec<ByteBuf, Move> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Move::kind, ByteBufCodecs.VAR_LONG, Move::since, Move::new);
	}

	public static final AttachmentType<Move> MOVE = AttachmentRegistry.create(JaymosPowers.id("web_brute"),
		builder -> builder.syncWith(Move.STREAM_CODEC, AttachmentSyncPredicate.all()));

	/** Client → server: G, for whichever move the situation calls for (or to drop what is held, or call off the struggle). */
	public record Press() implements CustomPacketPayload {
		public static final Type<Press> TYPE = new Type<>(JaymosPowers.id("web_brute_press"));
		public static final StreamCodec<ByteBuf, Press> CODEC = StreamCodec.unit(new Press());

		@Override
		public Type<Press> type() {
			return TYPE;
		}
	}

	/** Client → server: left click while holding a chunk or a tree overhead. */
	public record Throw() implements CustomPacketPayload {
		public static final Type<Throw> TYPE = new Type<>(JaymosPowers.id("web_brute_throw"));
		public static final StreamCodec<ByteBuf, Throw> CODEC = StreamCodec.unit(new Throw());

		@Override
		public Type<Throw> type() {
			return TYPE;
		}
	}

	/** A creature flung by the ram, watched for the wall it may strike. */
	private static final class Fling {
		final ResourceKey<Level> dimension;
		final int id;
		final long until;
		final Vec3 dir;
		Vec3 last;
		double prev;

		Fling(ResourceKey<Level> dimension, Entity entity, long until, Vec3 dir) {
			this.dimension = dimension;
			this.id = entity.getId();
			this.until = until;
			this.dir = dir;
			this.last = entity.position();
			this.prev = RAM_KNOCK;
		}
	}

	/** One wearer's moves in progress. */
	private static final class Run {
		int kind;
		long since;
		long readyAt;
		// rip
		BlockPos hit = BlockPos.ZERO;
		List<BlockPos> blocks = List.of();
		// held
		int chunk = -1;
		boolean tree;
		Vec3 liftFrom = Vec3.ZERO;
		// tear
		Vec3 start = Vec3.ZERO, dir = Vec3.ZERO;
		double groundY;
		int missed;
		final Set<Integer> hurt = new HashSet<>();
		// ram
		int gallop, slack;
		long coolUntil;
		@Nullable Vec3 last;
		double lastSpeed;
		final List<Fling> flings = new ArrayList<>();
	}

	private static final Map<UUID, Run> RUNS = new HashMap<>();

	private WebBrute() {
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Press.TYPE, Press.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Throw.TYPE, Throw.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Press.TYPE, (payload, ctx) -> press(ctx.player()));
		ServerPlayNetworking.registerGlobalReceiver(Throw.TYPE, (payload, ctx) -> hurl(ctx.player()));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player) {
				forget(player);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> RUNS.clear());
	}

	static void forget(ServerPlayer player) {
		Run run = RUNS.remove(player.getUUID());
		if (run != null && run.kind == HOLD) {
			release(player, run);
		}
		player.removeAttached(MOVE);
	}

	// ===== what the client asks about, on either side ================================================================

	/** No move holds the suit (a gallop's charge does not count). */
	private static boolean idle(Player player) {
		Move move = player.getAttached(MOVE);
		return move == null || move.kind() == RAM_BUILD || move.kind() == RAM;
	}

	/** A chunk or tree is held overhead, or being torn loose: G calls it off or drops it, and a click throws it. Both sides. */
	public static boolean busyHolding(Player player) {
		Move move = player.getAttached(MOVE);
		return move != null && (move.kind() == RIP || move.kind() == HOLD);
	}

	/** A chunk or tree is held overhead (a click throws it). Both sides. */
	public static boolean holding(Player player) {
		Move move = player.getAttached(MOVE);
		return move != null && move.kind() == HOLD;
	}

	/**
	 * G, with nothing to grab, would start a move: the big suit up and standing, hands free of the tendrils; sneaking, the
	 * ground (a top face) within {@link #TEAR_RANGE} and both hands empty; otherwise a block within {@link #RIP_RANGE}. Both sides.
	 */
	public static boolean wants(Player player) {
		if (!WebSymbiote.bulked(player) || !player.onGround() || !idle(player) || WebHeldGrab.holding(player) || tendrilsFull(player)) {
			return false;
		}
		if (player.isShiftKeyDown()) {
			if (!player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty()) {
				return false;
			}
			BlockHitResult hit = clip(player, TEAR_RANGE);
			return hit != null && hit.getDirection() == Direction.UP;
		}
		return clip(player, RIP_RANGE) != null;
	}

	private static boolean tendrilsFull(Player player) {
		WebTendrils.Grips grips = player.getAttached(WebTendrils.GRIPS);
		return grips != null && !grips.empty();
	}

	private static @Nullable BlockHitResult clip(Player player, double range) {
		Vec3 eye = player.getEyePosition();
		BlockHitResult hit = player.level().clip(new ClipContext(eye, eye.add(player.getLookAngle().scale(range)), ClipContext.Block.COLLIDER,
			ClipContext.Fluid.NONE, player));
		return hit.getType() == HitResult.Type.BLOCK ? hit : null;
	}

	// ===== the keys ==================================================================================================

	private static boolean able(ServerPlayer player) {
		return player.isAlive() && !player.isSpectator() && WebSymbiote.bulked(player) && !WebSymbiote.driving(player)
			&& com.jaymospowers.core.Powers.has(player, WebPower.INSTANCE);
	}

	private static void press(ServerPlayer player) {
		if (!able(player)) {
			return;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		Run run = RUNS.computeIfAbsent(player.getUUID(), id -> new Run());
		if (run.kind == RIP) {
			// called off: the wall stays whole
			set(player, run, 0, now);
			return;
		}
		if (run.kind == HOLD) {
			drop(player, run, now);
			return;
		}
		if (!idle(player) || now < run.readyAt || !wants(player) && !treeNear(player)) {
			return;
		}
		if (WebTendrils.tugging(player, WebNet.MAIN) || WebTendrils.tugging(player, WebNet.OFF)) {
			return;
		}
		if (player.isShiftKeyDown()) {
			tearStart(player, run, now);
		} else if (!treeStart(player, run, now)) {
			ripStart(player, run, now);
		}
	}

	private static void set(ServerPlayer player, Run run, int kind, long now) {
		run.kind = kind;
		run.since = now;
		if (kind == 0) {
			player.removeAttached(MOVE);
		} else {
			player.setAttached(MOVE, new Move(kind, now));
		}
	}

	/** Whether a tree is in reach of the aim (only ever with Kingdoms; otherwise the answer is no). */
	private static boolean treeNear(ServerPlayer player) {
		return !player.isShiftKeyDown() && WebSymbiote.bulked(player) && player.onGround() && treeAim(player, false) != null;
	}

	// ===== rip and throw =============================================================================================

	private static void ripStart(ServerPlayer player, Run run, long now) {
		BlockHitResult hit = clip(player, RIP_RANGE);
		ServerLevel level = player.level();
		if (hit == null || !player.mayBuild()) {
			return;
		}
		List<BlockPos> blocks = new ArrayList<>(BlockCluster.crater(level, hit.getBlockPos(), RIP_PIECES, RIP_HARDEST, player));
		blocks.removeIf(pos -> underfoot(player, pos));
		if (blocks.isEmpty()) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.web_ground.rip_nothing"));
			return;
		}
		run.hit = hit.getBlockPos();
		run.blocks = blocks;
		set(player, run, RIP, now);
		Vec3 at = Vec3.atCenterOf(run.hit);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.WARDEN_TENDRIL_CLICKS, SoundSource.PLAYERS, 1.0F, 0.5F);
		Shakes.send(player, RIP_RUMBLE, at);
	}

	/** The block you stand on (or next to your feet): it never comes out from under you. */
	private static boolean underfoot(Player player, BlockPos pos) {
		return pos.getY() < player.getBlockY() && Math.abs(pos.getX() + 0.5 - player.getX()) < 1.3 && Math.abs(pos.getZ() + 0.5 - player.getZ()) < 1.3;
	}

	private static void tickRip(ServerPlayer player, Run run, long now) {
		ServerLevel level = player.level();
		Vec3 at = Vec3.atCenterOf(run.hit);
		if (!player.onGround() || player.position().distanceTo(at) > RIP_RANGE + RIP_LEASH) {
			set(player, run, 0, now);
			return;
		}
		long t = now - run.since;
		float progress = Mth.clamp(t / (float)RIP_TICKS, 0.0F, 1.0F);
		int stage = Math.max(1, Math.round(progress * WebCracks.MAX_STAGE));
		for (BlockPos pos : run.blocks) {
			WebCracks.crack(level, pos, stage);
		}
		if (t % RIP_RUMBLE_EVERY == 0) {
			Shakes.send(player, RIP_RUMBLE * (0.5F + progress), at);
		}
		if (t % 5 == 0) {
			Shakes.around(level, at, 16.0, 0.3F, Shakes.ticksFor(0.3F), player);
		}
		if (t % 4 == 1) {
			BlockPos pos = run.blocks.get(level.getRandom().nextInt(run.blocks.size()));
			BlockState state = level.getBlockState(pos);
			Debris.puff(level, pos, state, 0.6F);
			level.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, state.getSoundType().getBreakSound(), SoundSource.BLOCKS, 0.7F,
				0.6F + progress * 0.4F);
		}
		if (t >= RIP_TICKS) {
			tearOut(player, run, now);
		}
	}

	private static void tearOut(ServerPlayer player, Run run, long now) {
		ServerLevel level = player.level();
		List<BlockPos> loose = new ArrayList<>();
		for (BlockPos pos : run.blocks) {
			if (BlockCluster.takeable(level, pos, RIP_HARDEST, player) && !underfoot(player, pos)) {
				loose.add(pos);
			}
		}
		BlockCluster chunk = loose.isEmpty() ? null : BlockCluster.of(level, loose, Vec3.ZERO, player);
		if (chunk == null) {
			set(player, run, 0, now);
			return;
		}
		chunk.script(true, true);
		run.chunk = chunk.getId();
		run.tree = false;
		run.liftFrom = chunk.position();
		set(player, run, HOLD, now);
		Vec3 at = chunk.position();
		Shakes.heavy(player, at, 20.0, RIP_TEAR_SHAKE);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.6F, 1.2F);
	}

	/** Where the held chunk's entity sits: its blocks' middle (drawn 0.7 above it) centred a little over the head. */
	private static Vec3 overhead(Player player, BlockCluster chunk) {
		double half = BlockCluster.bounds(chunk.pieces()).getYsize() * 0.5;
		return player.position().add(0.0, player.getBbHeight() + HOLD_CLEAR + half - 0.7, 0.0);
	}

	private static void tickHold(ServerPlayer player, Run run, long now) {
		ServerLevel level = player.level();
		long age = now - run.since;
		if (age > HOLD_MAX_TICKS) {
			drop(player, run, now);
			return;
		}
		if (run.tree) {
			Vec3 forward = player.getLookAngle().horizontal();
			Vec3 hand = player.position().add(0.0, player.getBbHeight() + TREE_HAND_UP, 0.0).add(forward.lengthSqr() < 1.0E-4 ? Vec3.ZERO : forward.normalize().scale(0.3));
			if (!GiantTrees.tick(level, player, (int)Math.min(age, 1000), Integer.MAX_VALUE, hand)) {
				set(player, run, 0, now);
				run.readyAt = now + MOVE_COOLDOWN;
			}
			return;
		}
		if (!(level.getEntity(run.chunk) instanceof BlockCluster chunk) || chunk.isRemoved() || !chunk.scripted()) {
			set(player, run, 0, now);
			run.readyAt = now + MOVE_COOLDOWN;
			return;
		}
		double k = Mth.clamp(age / (double)HOLD_RISE, 0.0, 1.0);
		k = k * k * (3.0 - 2.0 * k);
		Vec3 to = run.liftFrom.lerp(overhead(player, chunk), k);
		Vec3 was = chunk.position();
		chunk.setPos(to);
		chunk.setDeltaMovement(to.subtract(was));
		if (age % 6 == 0 && !chunk.pieces().isEmpty()) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.FALLING_DUST, chunk.pieces().get(0).blockState()), to.x, to.y + 0.3, to.z, 3, 0.6, 0.1, 0.6,
				0.0);
		}
	}

	private static void hurl(ServerPlayer player) {
		Run run = RUNS.get(player.getUUID());
		if (run == null || run.kind != HOLD || !able(player)) {
			return;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		Vec3 look = player.getLookAngle();
		boolean thrown;
		if (run.tree) {
			thrown = GiantTrees.throwHeld(level, player, TREE_THROW_SPEED);
		} else if (level.getEntity(run.chunk) instanceof BlockCluster chunk && !chunk.isRemoved()) {
			chunk.script(false, false);
			chunk.setPos(chunk.position().add(look.scale(0.6)));
			chunk.setDeltaMovement(look.scale(THROW_SPEED).add(0.0, THROW_LIFT, 0.0));
			chunk.needsSync = true;
			chunk.syncVelocity = true;
			chunk.launch(player);
			thrown = true;
		} else {
			thrown = false;
		}
		run.chunk = -1;
		run.tree = false;
		run.readyAt = now + MOVE_COOLDOWN;
		if (!thrown) {
			set(player, run, 0, now);
			return;
		}
		set(player, run, THROW, now);
		level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.2F, 0.5F);
		Shakes.heavy(player, player.position().add(look.scale(-1.0)), 16.0, THROW_SHAKE);
	}

	/** What is held falls where it is. */
	private static void drop(ServerPlayer player, Run run, long now) {
		release(player, run);
		run.readyAt = now + MOVE_COOLDOWN;
		set(player, run, 0, now);
	}

	private static void release(ServerPlayer player, Run run) {
		if (run.tree) {
			GiantTrees.drop(player);
		} else if (run.chunk >= 0 && player.level().getEntity(run.chunk) instanceof BlockCluster chunk && !chunk.isRemoved()) {
			chunk.script(false, false);
			chunk.setDeltaMovement(0.0, 0.05, 0.0);
			chunk.needsSync = true;
			chunk.syncVelocity = true;
		}
		run.chunk = -1;
		run.tree = false;
	}

	/** The point a tree is looked for near: along the look, and where the aim meets a block. Null if no tree is there. */
	private static @Nullable Vec3 treeAim(ServerPlayer player, boolean take) {
		ServerLevel level = player.level();
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		BlockHitResult hit = clip(player, RIP_RANGE + 1.0);
		double limit = hit == null ? RIP_RANGE + 1.0 : eye.distanceTo(hit.getLocation());
		List<Vec3> probes = new ArrayList<>();
		for (double d : TREE_PROBES) {
			if (d <= limit) {
				probes.add(eye.add(look.scale(d)));
			}
		}
		if (hit != null) {
			probes.add(hit.getLocation());
		}
		for (Vec3 probe : probes) {
			if (GiantTrees.treeNear(level, player, probe, TREE_REACH)) {
				return probe;
			}
		}
		return null;
	}

	private static boolean treeStart(ServerPlayer player, Run run, long now) {
		Vec3 probe = treeAim(player, true);
		if (probe == null || !GiantTrees.take(player.level(), player, probe, TREE_REACH)) {
			return false;
		}
		run.tree = true;
		run.chunk = -1;
		set(player, run, HOLD, now);
		Shakes.heavy(player, probe, 20.0, RIP_TEAR_SHAKE);
		return true;
	}

	// ===== ground tear ===============================================================================================

	private static void tearStart(ServerPlayer player, Run run, long now) {
		if (!player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty()) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.web_ground.tear_hands"));
			return;
		}
		BlockHitResult hit = clip(player, TEAR_RANGE);
		if (hit == null || hit.getDirection() != Direction.UP) {
			return;
		}
		Vec3 dir = player.getLookAngle().horizontal();
		dir = dir.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0.0F, player.getYRot()) : dir.normalize();
		run.dir = dir;
		run.start = player.position().add(dir.scale(TEAR_START));
		run.groundY = player.getY();
		run.missed = 0;
		run.hurt.clear();
		set(player, run, TEAR, now);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_TENDRIL_CLICKS, SoundSource.PLAYERS, 1.0F, 0.4F);
		Shakes.send(player, 0.4F);
	}

	private static void tickTear(ServerPlayer player, Run run, long now) {
		ServerLevel level = player.level();
		long t = now - run.since;
		if (t < TEAR_WINDUP) {
			return;
		}
		int step = (int)(t - TEAR_WINDUP);
		if (step == 0) {
			Vec3 fists = run.start;
			level.playSound(null, fists.x, fists.y, fists.z, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.6F, 0.5F);
			Shakes.heavy(player, fists, TEAR_SHAKE_RANGE, TEAR_SLAM_SHAKE);
		}
		if (step < TEAR_LENGTH) {
			tearStep(player, run, step);
		} else if (t >= TEAR_WINDUP + TEAR_LENGTH + TEAR_RECOVER) {
			run.readyAt = now + MOVE_COOLDOWN;
			set(player, run, 0, now);
		}
	}

	/** One block of the line: lumps shoved up on both sides, cracks, and everything standing on it hurt and tossed. */
	private static void tearStep(ServerPlayer player, Run run, int step) {
		ServerLevel level = player.level();
		if (run.missed >= 2) {
			return;
		}
		Vec3 c = run.start.add(run.dir.scale(step + 0.5));
		BlockPos ground = GravityShockwave.ground(level, c.x, run.groundY, c.z);
		if (ground == null) {
			run.missed++;
			return;
		}
		run.missed = 0;
		run.groundY = ground.getY() + 1.0;
		Vec3 perp = new Vec3(-run.dir.z, 0.0, run.dir.x);
		for (int side = -1; side <= 1; side += 2) {
			double x = c.x + perp.x * side * TEAR_LATERAL, z = c.z + perp.z * side * TEAR_LATERAL;
			GravityShockwave.lump(level, x, run.groundY, z, Math.atan2(perp.z * side, perp.x * side));
			BlockPos edge = GravityShockwave.ground(level, c.x + perp.x * side * 1.6, run.groundY, c.z + perp.z * side * 1.6);
			if (edge != null) {
				WebCracks.crack(level, edge, TEAR_CRACK_MIN);
			}
		}
		if (step % 2 == 0) {
			GravityShockwave.lump(level, c.x, run.groundY, c.z, Math.atan2(run.dir.z, run.dir.x));
		}
		WebCracks.crack(level, ground, TEAR_CRACK_MIN + level.getRandom().nextInt(TEAR_CRACK_MAX - TEAR_CRACK_MIN + 1));
		BlockState top = level.getBlockState(ground);
		Debris.puff(level, ground, top, 1.1F);
		if (step % 2 == 1) {
			level.playSound(null, c.x, c.y, c.z, top.getSoundType().getBreakSound(), SoundSource.BLOCKS, 1.1F, 0.55F);
		}
		Shakes.heavy(player, c, TEAR_SHAKE_RANGE, TEAR_STEP_SHAKE);
		AABB zone = new AABB(c.x - TEAR_HALF_WIDTH, ground.getY() - 0.5, c.z - TEAR_HALF_WIDTH, c.x + TEAR_HALF_WIDTH, ground.getY() + 1.0 + TEAR_HEIGHT,
			c.z + TEAR_HALF_WIDTH);
		for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, zone,
			e -> e != player && e.isAlive() && !e.isSpectator() && !player.isAlliedTo(e) && !run.hurt.contains(e.getId()))) {
			run.hurt.add(living.getId());
			living.hurtServer(level, level.damageSources().playerAttack(player), TEAR_DAMAGE);
			Crushing.mark(living);
			double across = living.position().subtract(c).dot(perp);
			double side = Math.abs(across) < 0.2 ? (level.getRandom().nextBoolean() ? 1.0 : -1.0) : Math.signum(across);
			Vec3 toss = perp.scale(side * TEAR_TOSS_SIDE).add(run.dir.scale(TEAR_TOSS_ALONG)).add(0.0, TEAR_TOSS_UP, 0.0);
			living.setDeltaMovement(living.getDeltaMovement().add(toss));
			living.needsSync = true;
			living.syncVelocity = true;
			if (living instanceof ServerPlayer other) {
				ServerPlayNetworking.send(other, new WebNet.Nudge(other.getDeltaMovement()));
			}
		}
	}

	// ===== shoulder ram ==============================================================================================

	/** How much a ram weighs in a clash of blows, times its damage. */
	static final float CLASH_STRENGTH = 2.0F;

	private static void tickRam(ServerPlayer player, Run run, long now) {
		Vec3 pos = player.position();
		Vec3 moved = run.last == null ? Vec3.ZERO : pos.subtract(run.last).multiply(1.0, 0.0, 1.0);
		run.last = pos;
		double speed = moved.length();
		double previous = run.lastSpeed;
		run.lastSpeed = speed;
		if (run.kind == STAGGER) {
			if (now - run.since >= STAGGER_TICKS) {
				set(player, run, 0, now);
			}
			return;
		}
		if (run.kind != RAM) {
			boolean galloping = player.isSprinting() && player.onGround() && speed >= RAM_MIN_SPEED && now >= run.coolUntil;
			if (galloping) {
				run.gallop++;
				run.slack = 0;
			} else if (++run.slack > RAM_GRACE) {
				run.gallop = 0;
				if (run.kind != 0) {
					set(player, run, 0, now);
				}
				return;
			}
			if (run.gallop >= RAM_BUILD_TICKS) {
				set(player, run, RAM, now);
				run.slack = 0;
				ServerLevel level = player.level();
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 0.9F, 0.7F);
				Shakes.send(player, RAM_READY_SHAKE);
			} else if (run.gallop >= 3) {
				if (run.kind != RAM_BUILD) {
					set(player, run, RAM_BUILD, now - run.gallop);
				}
				if (run.gallop % RAM_RUMBLE_EVERY == 0) {
					Shakes.send(player, RAM_BUILD_RUMBLE * run.gallop / RAM_BUILD_TICKS);
				}
			}
			return;
		}
		// charged
		if (!player.isSprinting() || speed < RAM_KEEP_SPEED) {
			if (++run.slack > RAM_SLACK_END) {
				endRam(player, run, now);
				return;
			}
		} else {
			run.slack = 0;
		}
		if (now - run.since > RAM_MAX_TICKS) {
			endRam(player, run, now);
			return;
		}
		ServerLevel level = player.level();
		Vec3 dir = speed > 0.05 ? moved.normalize() : player.getLookAngle().horizontal();
		if (dir.lengthSqr() < 1.0E-4) {
			return;
		}
		dir = dir.normalize();
		// ramming into another brute's great blow: the two lock (BlowClash)
		if (com.jaymospowers.core.BlowClash.meets(player, dir, RAM_DAMAGE * CLASH_STRENGTH)) {
			return;
		}
		if (now % RAM_RUMBLE_EVERY == 0) {
			Shakes.send(player, RAM_RUMBLE);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAVAGER_STEP, SoundSource.PLAYERS, 0.8F, 0.6F);
		}
		BlockState under = level.getBlockState(player.blockPosition().below());
		if (!under.isAir() && now % 2 == 0) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, under), player.getX(), player.getY() + 0.1, player.getZ(), 4, 0.3, 0.05, 0.3, 0.1);
		}
		LivingEntity struck = rammed(player, dir);
		if (struck != null) {
			ram(player, run, struck, dir, now);
		}
		breakSoft(player, dir);
		if (struck == null && previous >= RAM_KEEP_SPEED && blocked(level, player, dir)) {
			stagger(player, run, dir, now);
		}
	}

	private static void endRam(ServerPlayer player, Run run, long now) {
		run.gallop = 0;
		run.slack = 0;
		run.coolUntil = now + RAM_COOLDOWN;
		set(player, run, 0, now);
	}

	/** The nearest creature the body is running into. */
	private static @Nullable LivingEntity rammed(ServerPlayer player, Vec3 dir) {
		AABB zone = player.getBoundingBox().expandTowards(dir.scale(RAM_REACH)).inflate(0.2, 0.0, 0.2);
		LivingEntity best = null;
		double bestDistance = Double.MAX_VALUE;
		for (LivingEntity living : player.level().getEntitiesOfClass(LivingEntity.class, zone,
			e -> e != player && e.isAlive() && !e.isSpectator() && !player.isAlliedTo(e) && !e.isPassengerOfSameVehicle(player))) {
			double d = living.distanceToSqr(player);
			if (d < bestDistance) {
				bestDistance = d;
				best = living;
			}
		}
		return best;
	}

	private static void ram(ServerPlayer player, Run run, LivingEntity target, Vec3 dir, long now) {
		ServerLevel level = player.level();
		target.hurtServer(level, level.damageSources().playerAttack(player), RAM_DAMAGE);
		Crushing.mark(target);
		target.setDeltaMovement(target.getDeltaMovement().add(dir.scale(RAM_KNOCK)).add(0.0, RAM_LIFT, 0.0));
		target.needsSync = true;
		target.syncVelocity = true;
		if (target instanceof ServerPlayer other) {
			ServerPlayNetworking.send(other, new WebNet.Nudge(other.getDeltaMovement()));
		}
		run.flings.add(new Fling(level.dimension(), target, now + RAM_FLING_TICKS, dir));
		Vec3 at = target.getBoundingBox().getCenter();
		level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.4F, 0.5F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.MACE_SMASH_GROUND, SoundSource.PLAYERS, 1.0F, 0.8F);
		level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 14, 0.4, 0.5, 0.4, 0.3);
		Shakes.heavy(player, at, 20.0, RAM_HIT_SHAKE);
		// the run is spent, and slowed by the weight it hit
		ServerPlayNetworking.send(player, new WebNet.Nudge(player.getDeltaMovement().multiply(0.4, 1.0, 0.4)));
		endRam(player, run, now);
	}

	/** Struck flings: a creature that hits a wall soon after the blow takes more. */
	private static void tickFlings(ServerPlayer player, Run run) {
		for (Iterator<Fling> it = run.flings.iterator(); it.hasNext();) {
			Fling fling = it.next();
			ServerLevel level = player.level();
			Entity entity = level.dimension() == fling.dimension ? level.getEntity(fling.id) : null;
			if (!(entity instanceof LivingEntity living) || !living.isAlive() || level.getGameTime() > fling.until) {
				it.remove();
				continue;
			}
			Vec3 p = living.position();
			double d = p.subtract(fling.last).multiply(1.0, 0.0, 1.0).length();
			fling.last = p;
			if (fling.prev >= RAM_WALL_SPEED * 0.5 && d < fling.prev * 0.3 && blocked(level, living, fling.dir)) {
				Vec3 wall = p.add(fling.dir.scale(living.getBbWidth() * 0.5 + 0.4)).add(0.0, living.getBbHeight() * 0.5, 0.0);
				living.hurtServer(level, level.damageSources().playerAttack(player), RAM_WALL_DAMAGE);
				BlockPos wallPos = BlockPos.containing(wall);
				BlockState state = level.getBlockState(wallPos);
				WebCracks.crack(level, wallPos, 6);
				Debris.puff(level, wallPos, state, 1.2F);
				level.playSound(null, wall.x, wall.y, wall.z, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.2F, 0.7F);
				Shakes.heavy(player, wall, 20.0, RAM_HIT_SHAKE * 0.6F);
				it.remove();
				continue;
			}
			fling.prev = d;
		}
	}

	/** A solid wall right ahead of {@code entity} on its way along {@code dir}, above the height of a step. */
	private static boolean blocked(ServerLevel level, Entity entity, Vec3 dir) {
		Vec3 ahead = entity.position().add(dir.scale(entity.getBbWidth() * 0.5 + 0.4));
		for (int dy = 1; dy <= 2; dy++) {
			BlockPos pos = BlockPos.containing(ahead.x, entity.getY() + dy, ahead.z);
			if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
				return true;
			}
		}
		return false;
	}

	private static void stagger(ServerPlayer player, Run run, Vec3 dir, long now) {
		ServerLevel level = player.level();
		Vec3 ahead = player.position().add(dir.scale(player.getBbWidth() * 0.5 + 0.4)).add(0.0, 1.0, 0.0);
		BlockPos wall = BlockPos.containing(ahead);
		WebCracks.crack(level, wall, 6);
		Debris.puff(level, wall, level.getBlockState(wall), 1.3F);
		level.playSound(null, ahead.x, ahead.y, ahead.z, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.4F, 0.6F);
		player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, STAGGER_TICKS, STAGGER_SLOW, false, false));
		ServerPlayNetworking.send(player, new WebNet.Nudge(new Vec3(-dir.x * STAGGER_SHOVE, 0.25, -dir.z * STAGGER_SHOVE)));
		Shakes.heavy(player, ahead, 20.0, STAGGER_SHAKE);
		run.gallop = 0;
		run.slack = 0;
		run.coolUntil = now + RAM_COOLDOWN;
		set(player, run, STAGGER, now);
	}

	/** A block the ram goes through: wood, leaves, wool, glass; never anything with contents, nor harder than planks (doors a little more). */
	static boolean soft(ServerLevel level, BlockPos pos, BlockState state) {
		if (state.isAir() || state.hasBlockEntity() || !state.getFluidState().isEmpty()) {
			return false;
		}
		float hardness = state.getDestroySpeed(level, pos);
		if (hardness < 0.0F) {
			return false;
		}
		if (state.is(BlockTags.LEAVES) || state.is(BlockTags.WOOL)) {
			return hardness <= RAM_HARDEST;
		}
		if (state.is(BlockTags.PLANKS) || state.is(BlockTags.LOGS) || state.is(BlockTags.WOODEN_FENCES) || state.is(BlockTags.FENCE_GATES)
			|| state.is(BlockTags.WOODEN_SLABS) || state.is(BlockTags.WOODEN_STAIRS)) {
			return hardness <= RAM_HARDEST;
		}
		if (state.is(BlockTags.WOODEN_DOORS) || state.is(BlockTags.WOODEN_TRAPDOORS)) {
			return hardness <= RAM_DOOR_HARDEST;
		}
		// glass and panes (iron bars are no pane: far too hard)
		return (state.is(BlockTags.IMPERMEABLE) || state.getBlock() instanceof IronBarsBlock) && hardness <= RAM_GLASS_HARDEST;
	}

	/** Whether the wearer may break blocks at all: where they may build, and mob griefing is on. */
	private static boolean mayBreak(ServerPlayer player, ServerLevel level) {
		return player.mayBuild() && level.getGameRules().get(GameRules.MOB_GRIEFING);
	}

	/** The soft blocks in the body's way break. */
	private static void breakSoft(ServerPlayer player, Vec3 dir) {
		ServerLevel level = player.level();
		if (!mayBreak(player, level)) {
			return;
		}
		AABB zone = player.getBoundingBox().move(dir.scale(RAM_REACH * 0.8)).deflate(0.0, 0.1, 0.0);
		int broken = 0;
		for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(zone.minX, zone.minY, zone.minZ), BlockPos.containing(zone.maxX, zone.maxY, zone.maxZ))) {
			if (broken >= RAM_BREAKS_PER_TICK) {
				return;
			}
			BlockState state = level.getBlockState(pos);
			if (soft(level, pos, state) && level.mayInteract(player, pos)) {
				level.destroyBlock(pos.immutable(), level.getRandom().nextFloat() < RAM_DROP_CHANCE, player);
				broken++;
			}
		}
	}

	// ===== every tick ================================================================================================

	/** Every tick, for web slingers. */
	static void tick(ServerPlayer player) {
		Run run = RUNS.get(player.getUUID());
		boolean on = player.isAlive() && !player.isSpectator() && WebSymbiote.bulked(player) && !WebSymbiote.driving(player);
		if (run == null) {
			if (!on) {
				return;
			}
			run = new Run();
			RUNS.put(player.getUUID(), run);
		}
		long now = player.level().getGameTime();
		tickFlings(player, run);
		if (!on) {
			// the suit went (or the wearer did): whatever was held falls, whatever was building is gone
			if (run.kind == HOLD) {
				release(player, run);
			}
			if (run.kind != 0) {
				set(player, run, 0, now);
			}
			run.gallop = 0;
			if (run.flings.isEmpty()) {
				RUNS.remove(player.getUUID());
			}
			return;
		}
		switch (run.kind) {
			case RIP -> tickRip(player, run, now);
			case HOLD -> tickHold(player, run, now);
			case TEAR -> tickTear(player, run, now);
			case THROW -> {
				if (now - run.since >= THROW_TICKS) {
					set(player, run, 0, now);
				}
			}
			default -> tickRam(player, run, now);
		}
	}
}
