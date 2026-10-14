package com.jaymospowers.power.web;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.BlockCluster;
import com.jaymospowers.core.Debris;
import com.jaymospowers.core.Powers;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Web Whip: Sneak + R (or G) with no web out, aimed at a creature, a corpse or a block, and the web catches it (a
 * block tears out as a small chunk of ground) and holds it stuck. Nothing more happens until the other web is on it
 * too: Sneak + the other key (within {@link #CATCH_TICKS}, else the first lets go), and with both webs attached you
 * whip it about on the lines, slamming it into the ground: each slam hurts, and breaks a crater under it, bigger for
 * bigger creatures; a chunk of ground slammed down bursts into a creeper-sized crater. Every swing and every impact
 * throws dust and shakes your screen.
 * <ul>
 * <li>{@link #OVERHEAD}: hauled up over your head and brought down in front of you.</li>
 * <li>{@link #SEESAW}: over your head into the ground in front, then back over into the ground behind. Only on a
 * creature (a living enemy), so the second slam lands on something worth hitting.</li>
 * <li>{@link #LASSO}: swung round you once, then up over your head and down in front. Only in midair, and always
 * there: you hang in place while you whirl it, held up by the spin like a propeller.</li>
 * </ul>
 * The server runs it; the line (a {@link WebLine#YANK} line on that hand) shows everyone, and the arm follows it.
 */
public final class WebWhip {
	public static final int OVERHEAD = 0, SEESAW = 1, LASSO = 2;
	/** How far a whip reaches (blocks, times the symbiote's reach), and the slack the server allows the client's aim. */
	public static final double RANGE = 16.0;
	static final double RANGE_SLACK = 2.0;
	/** A block whipped tears out this many blocks around it (a small crater), none harder than {@code HARDEST}. */
	static final int PIECES = 4;
	static final float HARDEST = 3.0F;
	/** A block takes this long to pry loose (ticks) before the chunk comes away on the web, the blocks cracking meanwhile. */
	static final int PRY_TICKS = 30;
	/** Ticks before you can whip again, once one ends. */
	static final int COOLDOWN = 20;
	/** How long one web holds its catch waiting for the other (ticks), and how much it slows a caught creature (share of its speed kept). */
	static final int CATCH_TICKS = 60;
	static final double CATCH_DRAG = 0.2;
	/** How hard the whipped thing is steered toward its path (share of the gap a tick), and its top speed (blocks/tick). */
	static final double STEER = 0.75, MAX_SPEED = 3.2;
	/** The path: this high over your head (and this far back), slammed down this far ahead (or behind); the lasso's reach and height. */
	static final double OVER_UP = 3.2, OVER_BACK = 0.6, SLAM_REACH = 4.0, CHUNK_SLAM_REACH = 5.0, LASSO_RADIUS = 3.6, LASSO_UP = 1.4;
	/** Damage per slam to the creature whipped, and to anything else caught where it lands (within {@code SPLASH}). */
	static final float DAMAGE = 9.0F, SPLASH_DAMAGE = 5.0F;
	static final double SPLASH = 2.2;
	/**
	 * The crater under a slammed creature: an impact this fast (see {@link Debris#smash}), {@code CRATER_BASE} plus
	 * {@code CRATER_PER_SIZE} per block² of its silhouette (width × height), between the min and max. A chunk of ground
	 * slams as hard as {@code CHUNK_CRATER} (a creeper's crater), and {@code CHUNK_FIRST_CRATER} on a seesaw's first slam.
	 */
	static final double CRATER_BASE = 1.2, CRATER_PER_SIZE = 0.25, CRATER_MIN = 1.25, CRATER_MAX = 2.6;
	static final double CHUNK_CRATER = 2.25, CHUNK_FIRST_CRATER = 1.6;
	/** Screen shake: yours at each swing and each slam, a whipped player's at a slam, and anyone else's close by. */
	static final float SWING_SHAKE = 0.5F, SLAM_SHAKE = 1.6F, TARGET_SHAKE = 2.0F, NEAR_SHAKE = 0.7F;
	static final double NEAR_SHAKE_RANGE = 12.0;

	/** Where the path is at each frame: where it was caught, overhead, the ground ahead or behind, or round you (lasso). */
	private static final int FROM = 0, OVER = 1, FRONT = 2, BEHIND = 3, ORBIT = 10;
	/** Each way's frames (tick, where), the ticks it slams, and when it ends. */
	private static final int[][][] FRAMES = {
		{{0, FROM}, {10, OVER}, {16, FRONT}},
		{{0, FROM}, {9, OVER}, {14, FRONT}, {22, OVER}, {28, BEHIND}},
		{{0, FROM}, {5, ORBIT}, {8, ORBIT + 1}, {11, ORBIT + 2}, {14, ORBIT + 3}, {17, ORBIT + 4}, {20, ORBIT + 5}, {23, ORBIT + 6},
			{28, OVER}, {33, FRONT}},
	};
	private static final int[][] SLAMS = {{16}, {14, 28}, {33}};
	private static final int[] ENDS = {26, 38, 43};

	/** Client → server: whip what's under the crosshair with that hand's web. */
	public record Start(int hand) implements CustomPacketPayload {
		public static final Type<Start> TYPE = new Type<>(JaymosPowers.id("web_whip"));
		public static final StreamCodec<ByteBuf, Start> CODEC = ByteBufCodecs.VAR_INT.map(Start::new, Start::hand);

		@Override
		public Type<Start> type() {
			return TYPE;
		}
	}

	private static final class Whip {
		final int target;
		final int hand;
		final int kind;
		final long start;
		final Vec3 from;
		final boolean hadGravity;
		/** A lasso in midair: you hang there (and whether you floated anyway before it). */
		final boolean hover;
		final boolean hadNoGravity;
		int tick;

		Whip(int target, int hand, int kind, long start, Vec3 from, boolean hadGravity, boolean hover, boolean hadNoGravity) {
			this.target = target;
			this.hand = hand;
			this.kind = kind;
			this.start = start;
			this.from = from;
			this.hadGravity = hadGravity;
			this.hover = hover;
			this.hadNoGravity = hadNoGravity;
		}
	}

	/** One web on its catch, waiting for the other. */
	private record Catch(int target, int hand, long since) {
	}

	/** One web on a block, prying a chunk loose ({@link #PRY_TICKS}). */
	private record Pry(BlockHitResult hit, java.util.List<BlockPos> blocks, int hand, long since) {
	}

	private static final Map<UUID, Pry> PRIES = new HashMap<>();
	private static final Map<UUID, Whip> WHIPS = new HashMap<>();
	private static final Map<UUID, Catch> CATCHES = new HashMap<>();
	private static final Map<UUID, Long> READY = new HashMap<>();

	private WebWhip() {
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Start.TYPE, Start.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Start.TYPE, (payload, ctx) -> start(ctx.player(), payload.hand()));
	}

	// ---- aiming (both sides) ----------------------------------------------------------------------------------------

	/** Something a whip can catch: a creature (not a boss, not you or your mount) or a corpse light enough to lift. */
	public static boolean whippable(Player player, @Nullable Entity entity) {
		if (entity == null || entity == player || !entity.isAlive() || entity.isSpectator() || entity == player.getVehicle()
			|| player.hasPassenger(entity) || entity instanceof EnderDragon || entity instanceof WitherBoss) {
			return false;
		}
		if (com.jaymospowers.power.gravity.GravityPower.isCorpse(entity)) {
			return !KitchenCompat.isHeavy(entity) && !KitchenCompat.isCarried(entity);
		}
		return entity instanceof LivingEntity && !(entity instanceof Player other && (other.isCreative() || other.isSpectator()));
	}

	/** What a whip would catch: a whippable entity under the crosshair, else the block there (null: nothing in reach). */
	public static @Nullable HitResult aim(Player player, double range) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0F);
		Vec3 end = eye.add(look.scale(range));
		BlockHitResult block = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		if (block.getType() != HitResult.Type.MISS) {
			end = block.getLocation();
		}
		AABB box = player.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0);
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, box.inflate(0.3), e -> whippable(player, e), eye.distanceToSqr(end));
		if (hit != null) {
			return hit;
		}
		return block.getType() == HitResult.Type.MISS ? null : block;
	}

	// ---- server -----------------------------------------------------------------------------------------------------

	public static boolean active(Player player) {
		return WHIPS.containsKey(player.getUUID());
	}

	private static void start(ServerPlayer player, int hand) {
		if (hand != WebNet.MAIN && hand != WebNet.OFF || !player.isAlive() || player.isSpectator() || !Powers.has(player, WebPower.INSTANCE)
			|| WHIPS.containsKey(player.getUUID()) || hand == WebNet.OFF && !WebPower.offHandFree(player)) {
			return;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		if (now < READY.getOrDefault(player.getUUID(), 0L)) {
			return;
		}
		if (PRIES.containsKey(player.getUUID())) {
			return;
		}
		Catch caught = CATCHES.get(player.getUUID());
		if (caught != null) {
			if (caught.hand() == hand) {
				return;
			}
			// the second web: on the same catch, and the whip begins
			CATCHES.remove(player.getUUID());
			Entity target = level.getEntity(caught.target());
			if (target == null || !target.isAlive()) {
				player.removeAttached(WebPower.line(caught.hand()));
				return;
			}
			begin(player, hand, target, now);
			return;
		}
		HitResult hit = aim(player, RANGE * WebSymbiote.reachScale(player) + RANGE_SLACK);
		Entity target = null;
		if (hit instanceof EntityHitResult entityHit) {
			target = entityHit.getEntity();
		} else if (hit instanceof BlockHitResult blockHit) {
			java.util.List<BlockPos> blocks = BlockCluster.crater(level, blockHit.getBlockPos(), PIECES, HARDEST, player);
			if (!blocks.isEmpty()) {
				// the web sticks and starts to pry; the chunk comes away after PRY_TICKS
				PRIES.put(player.getUUID(), new Pry(blockHit, blocks, hand, now));
				player.setAttached(WebPower.line(hand), new WebLine(WebLine.YANK, blockHit.getLocation(), -1));
				level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.PLAYERS, 1.0F, 1.6F);
				return;
			}
		}
		if (target == null) {
			level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.PLAYERS, 0.5F, 2.0F);
			return;
		}
		// the first web: it holds its catch until the other web is on it too
		CATCHES.put(player.getUUID(), new Catch(target.getId(), hand, now));
		player.setAttached(WebPower.line(hand), new WebLine(WebLine.YANK, target.getBoundingBox().getCenter(), target.getId()));
		level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.PLAYERS, 1.0F, 1.6F);
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.SLIME_SQUISH_SMALL, SoundSource.PLAYERS, 0.8F, 1.2F);
	}

	/**
	 * Both webs on it: the whip. In midair it's always the lasso (and you hang there); on the ground the seesaw only for
	 * a creature, else the overhead.
	 */
	private static void begin(ServerPlayer player, int hand, Entity target, long now) {
		ServerLevel level = player.level();
		boolean midair = !player.onGround() && !player.isInWater() && !player.onClimbable();
		int kind;
		if (midair) {
			kind = LASSO;
		} else if (target instanceof LivingEntity && !com.jaymospowers.power.gravity.GravityPower.isCorpse(target)) {
			kind = level.getRandom().nextBoolean() ? SEESAW : OVERHEAD;
		} else {
			kind = OVERHEAD;
		}
		Whip whip = new Whip(target.getId(), hand, kind, now, target.position(), !target.isNoGravity(), midair, player.isNoGravity());
		target.setNoGravity(true);
		if (midair) {
			player.setNoGravity(true);
			hover(player);
		}
		WHIPS.put(player.getUUID(), whip);
		player.setAttached(WebPower.line(hand), new WebLine(WebLine.YANK, target.getBoundingBox().getCenter(), target.getId()));
		level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.PLAYERS, 1.0F, 1.3F);
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.SLIME_SQUISH_SMALL, SoundSource.PLAYERS, 0.8F, 1.0F);
	}

	/** Held still in the air by the spin. */
	private static void hover(ServerPlayer player) {
		player.setDeltaMovement(Vec3.ZERO);
		player.resetFallDistance();
		ServerPlayNetworking.send(player, new WebNet.Nudge(Vec3.ZERO));
	}

	/** A web prying at a block: it cracks, and at {@link #PRY_TICKS} the chunk tears out and is caught. */
	private static void tickPry(ServerPlayer player) {
		Pry pry = PRIES.get(player.getUUID());
		if (pry == null) {
			return;
		}
		ServerLevel level = player.level();
		WebLine line = player.getAttached(WebPower.line(pry.hand()));
		long t = level.getGameTime() - pry.since();
		if (!player.isAlive() || player.isSpectator() || line == null || line.entityId() != -1
			|| pry.hit().getLocation().distanceToSqr(player.getEyePosition()) > Mth.square(RANGE * WebSymbiote.reachScale(player) * 2.0)) {
			dropPry(player);
			return;
		}
		if (t < PRY_TICKS) {
			int stage = (int)Math.min(8L, t * 9L / PRY_TICKS);
			for (int i = 0; i < pry.blocks().size(); i++) {
				level.destroyBlockProgress(pryCrack(player, i), pry.blocks().get(i), stage);
			}
			if (t % 6 == 0) {
				BlockPos at = pry.blocks().get((int)(t / 6 % pry.blocks().size()));
				net.minecraft.world.level.block.state.BlockState state = level.getBlockState(at);
				level.playSound(null, at, state.getSoundType().getHitSound(), SoundSource.BLOCKS, 0.9F, 0.6F);
				Debris.puff(level, at, state, 0.3F);
			}
			return;
		}
		java.util.List<BlockPos> loose = new java.util.ArrayList<>();
		for (BlockPos pos : pry.blocks()) {
			if (BlockCluster.takeable(level, pos, HARDEST, player)) {
				loose.add(pos);
			}
		}
		dropPry(player);
		BlockCluster chunk = BlockCluster.of(level, loose, pry.hit().getDirection().getUnitVec3().scale(0.9), player);
		if (chunk == null) {
			return;
		}
		chunk.script(true, false);
		long now = level.getGameTime();
		CATCHES.put(player.getUUID(), new Catch(chunk.getId(), pry.hand(), now));
		player.setAttached(WebPower.line(pry.hand()), new WebLine(WebLine.YANK, chunk.getBoundingBox().getCenter(), chunk.getId()));
		level.playSound(null, chunk.getX(), chunk.getY(), chunk.getZ(), SoundEvents.SLIME_SQUISH_SMALL, SoundSource.PLAYERS, 0.8F, 1.2F);
	}

	/** The pry's off: the cracks fade (and the web stays off the wall). */
	private static void dropPry(ServerPlayer player) {
		Pry pry = PRIES.remove(player.getUUID());
		if (pry == null) {
			return;
		}
		for (int i = 0; i < pry.blocks().size(); i++) {
			player.level().destroyBlockProgress(pryCrack(player, i), pry.blocks().get(i), -1);
		}
		WebLine line = player.getAttached(WebPower.line(pry.hand()));
		if (line != null && line.entityId() == -1) {
			player.removeAttached(WebPower.line(pry.hand()));
		}
	}

	private static int pryCrack(ServerPlayer player, int i) {
		return player.getId() * 47 + 4400 + i;
	}

	/** One web on its catch: it holds it (a creature dragged nearly to a stop) until the other comes, or gives up. */
	private static void tickCatch(ServerPlayer player) {
		Catch caught = CATCHES.get(player.getUUID());
		if (caught == null) {
			return;
		}
		ServerLevel level = player.level();
		Entity target = level.getEntity(caught.target());
		WebLine line = player.getAttached(WebPower.line(caught.hand()));
		if (target == null || !target.isAlive() || !player.isAlive() || player.isSpectator() || line == null || line.entityId() != caught.target()
			|| level.getGameTime() - caught.since() > CATCH_TICKS || target.distanceToSqr(player) > Mth.square(RANGE * WebSymbiote.reachScale(player) * 2.0)) {
			dropCatch(player);
			return;
		}
		if (!(target instanceof BlockCluster)) {
			target.setDeltaMovement(target.getDeltaMovement().multiply(CATCH_DRAG, 1.0, CATCH_DRAG));
			target.needsSync = true;
			if (target instanceof Mob mob) {
				mob.getNavigation().stop();
			}
		}
	}

	/** The lone web lets go of its catch (a torn-out chunk drops). */
	private static void dropCatch(ServerPlayer player) {
		Catch caught = CATCHES.remove(player.getUUID());
		if (caught == null) {
			return;
		}
		WebLine line = player.getAttached(WebPower.line(caught.hand()));
		if (line != null && line.entityId() == caught.target()) {
			player.removeAttached(WebPower.line(caught.hand()));
		}
		if (player.level().getEntity(caught.target()) instanceof BlockCluster chunk) {
			chunk.script(false, false);
		}
	}

	/** Every tick, per web slinger. */
	static void tick(ServerPlayer player) {
		tickPry(player);
		tickCatch(player);
		Whip whip = WHIPS.get(player.getUUID());
		if (whip == null) {
			return;
		}
		ServerLevel level = player.level();
		Entity target = level.getEntity(whip.target);
		if (target == null || !target.isAlive() || !player.isAlive() || player.isSpectator()
			|| target.distanceToSqr(player) > Mth.square(RANGE * WebSymbiote.reachScale(player) * 2.0)) {
			end(player, whip, target);
			return;
		}
		int t = ++whip.tick;
		if (whip.hover) {
			hover(player);
		}
		int[][] frames = FRAMES[whip.kind];
		Vec3 point = path(player, whip, target, t);
		steer(player, target, point);
		for (int[] frame : frames) {
			if (frame[0] == t && frame[1] != FROM && !isSlam(whip.kind, t)) {
				swing(player, target);
			}
		}
		if (isSlam(whip.kind, t)) {
			int[] slams = SLAMS[whip.kind];
			slam(player, whip, target, t == slams[slams.length - 1]);
		}
		if (t >= ENDS[whip.kind]) {
			end(player, whip, target);
		}
	}

	private static boolean isSlam(int kind, int t) {
		for (int slam : SLAMS[kind]) {
			if (slam == t) {
				return true;
			}
		}
		return false;
	}

	/** Where the path has the whipped thing's middle {@code t} ticks in (eased between frames; held at the last). */
	private static Vec3 path(ServerPlayer player, Whip whip, Entity target, int t) {
		int[][] frames = FRAMES[whip.kind];
		int i = 0;
		while (i < frames.length - 1 && frames[i + 1][0] <= t) {
			i++;
		}
		if (i >= frames.length - 1) {
			return point(player, whip, target, frames[frames.length - 1][1]);
		}
		double s = (t - frames[i][0]) / (double)(frames[i + 1][0] - frames[i][0]);
		s = s * s * (3.0 - 2.0 * s);
		return point(player, whip, target, frames[i][1]).lerp(point(player, whip, target, frames[i + 1][1]), s);
	}

	private static Vec3 point(ServerPlayer player, Whip whip, Entity target, int where) {
		Vec3 look = player.getLookAngle();
		Vec3 ahead = new Vec3(look.x, 0.0, look.z);
		ahead = ahead.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0.0F, player.getYRot()) : ahead.normalize();
		Vec3 right = ahead.cross(new Vec3(0.0, 1.0, 0.0)).normalize();
		Vec3 feet = player.position();
		double half = target.getBbHeight() * 0.5;
		double reach = (target instanceof BlockCluster ? CHUNK_SLAM_REACH : SLAM_REACH) + target.getBbWidth() * 0.5;
		if (where >= ORBIT) {
			double angle = (where - ORBIT) * Math.PI / 3.0;
			return feet.add(right.scale(Math.cos(angle) * LASSO_RADIUS)).add(ahead.scale(Math.sin(angle) * LASSO_RADIUS))
				.add(0.0, LASSO_UP + half, 0.0);
		}
		return switch (where) {
			case OVER -> feet.add(0.0, player.getBbHeight() + OVER_UP + half, 0.0).subtract(ahead.scale(OVER_BACK));
			case FRONT -> feet.add(ahead.scale(reach)).add(0.0, half - 0.6, 0.0);
			case BEHIND -> feet.subtract(ahead.scale(reach)).add(0.0, half - 0.6, 0.0);
			default -> whip.from.add(0.0, half, 0.0);
		};
	}

	private static void steer(ServerPlayer player, Entity target, Vec3 point) {
		Vec3 v = point.subtract(target.getBoundingBox().getCenter()).scale(STEER);
		double speed = v.length();
		if (speed > MAX_SPEED) {
			v = v.scale(MAX_SPEED / speed);
		}
		if (target instanceof BlockCluster chunk) {
			// steered by hand: it goes only where it's put
			chunk.setPos(chunk.position().add(v));
			chunk.setDeltaMovement(v);
			chunk.refreshBox();
			return;
		}
		target.setDeltaMovement(v);
		target.needsSync = true;
		target.syncVelocity = true;
		target.resetFallDistance();
		if (target instanceof Mob mob) {
			mob.getNavigation().stop();
		}
		if (target instanceof ServerPlayer other) {
			ServerPlayNetworking.send(other, new WebNet.Nudge(v));
		}
	}

	/** A swing through the air: a crack of the line, a whoosh, a trail of dust, a jolt. */
	private static void swing(ServerPlayer player, Entity target) {
		ServerLevel level = player.level();
		Vec3 at = target.getBoundingBox().getCenter();
		level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 0.6F);
		level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 0.7F, 0.8F);
		level.sendParticles(ParticleTypes.POOF, at.x, at.y, at.z, 8, 0.4, 0.4, 0.4, 0.02);
		level.sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, 4, 0.3, 0.3, 0.3, 0.01);
		com.jaymospowers.core.Shakes.send(player, SWING_SHAKE, at);
	}

	/** Brought down hard: damage, a crater, dust, and every screen near it shakes. */
	private static void slam(ServerPlayer player, Whip whip, Entity target, boolean last) {
		ServerLevel level = player.level();
		Vec3 ground = ground(level, target.position());
		boolean chunk = target instanceof BlockCluster;
		double force;
		if (chunk) {
			force = last ? CHUNK_CRATER : CHUNK_FIRST_CRATER;
		} else {
			force = Mth.clamp(CRATER_BASE + target.getBbWidth() * target.getBbHeight() * CRATER_PER_SIZE, CRATER_MIN, CRATER_MAX);
		}
		if (target instanceof LivingEntity living) {
			living.hurtServer(level, level.damageSources().playerAttack(player), DAMAGE);
		}
		if (target instanceof ServerPlayer struck) {
			com.jaymospowers.core.Shakes.send(struck, TARGET_SHAKE, struck.position());
		}
		// anything else where it lands is caught under it
		for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, new AABB(ground, ground).inflate(SPLASH + force * 0.5),
			e -> e != player && e != target && e.isAlive() && !e.isSpectator())) {
			other.hurtServer(level, level.damageSources().playerAttack(player), SPLASH_DAMAGE);
			Vec3 push = other.position().subtract(ground).multiply(1.0, 0.0, 1.0);
			push = push.lengthSqr() < 1.0E-4 ? Vec3.ZERO : push.normalize().scale(0.5);
			other.setDeltaMovement(other.getDeltaMovement().add(push.x, 0.35, push.z));
			other.needsSync = true;
			other.syncVelocity = true;
		}
		Debris.smash(level, ground, force, player);
		int radius = Mth.ceil(Math.min(3.5, 1.2 + Math.max(0.0, force - 1.1) * 1.6)) + 1;
		Debris.ring(level, BlockPos.containing(ground).below(), radius, 1.2F);
		level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, ground.x, ground.y + 0.3, ground.z, 6 + radius * 3, radius * 0.5, 0.2, radius * 0.5, 0.02);
		level.playSound(null, ground.x, ground.y, ground.z, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.0F, chunk ? 0.7F : 0.9F);
		com.jaymospowers.core.Shakes.send(player, SLAM_SHAKE, ground);
		for (ServerPlayer near : level.players()) {
			if (near != player && near != target && near.distanceToSqr(ground) < NEAR_SHAKE_RANGE * NEAR_SHAKE_RANGE) {
				com.jaymospowers.core.Shakes.send(near, NEAR_SHAKE, ground);
			}
		}
		if (chunk && last) {
			// spent: the chunk bursts apart
			((BlockCluster)target).shatter(level);
		} else if (!chunk) {
			// a bounce off the ground
			target.setDeltaMovement(target.getDeltaMovement().multiply(0.2, 0.0, 0.2).add(0.0, 0.3, 0.0));
		}
	}

	/** The top of the ground under {@code at} (within a few blocks down; else {@code at} itself). */
	private static Vec3 ground(ServerLevel level, Vec3 at) {
		Vec3 from = at.add(0.0, 1.0, 0.0);
		BlockHitResult hit = level.clip(new ClipContext(from, at.add(0.0, -4.0, 0.0), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY,
			net.minecraft.world.phys.shapes.CollisionContext.empty()));
		return hit.getType() == HitResult.Type.MISS ? at : hit.getLocation();
	}

	private static void end(ServerPlayer player, Whip whip, @Nullable Entity target) {
		WHIPS.remove(player.getUUID());
		READY.put(player.getUUID(), player.level().getGameTime() + COOLDOWN);
		// both webs let go
		for (int hand : new int[] {WebNet.MAIN, WebNet.OFF}) {
			WebLine line = player.getAttached(WebPower.line(hand));
			if (line != null && line.entityId() == whip.target) {
				player.removeAttached(WebPower.line(hand));
			}
		}
		if (whip.hover && !whip.hadNoGravity) {
			player.setNoGravity(false);
		}
		if (target == null) {
			return;
		}
		if (target instanceof BlockCluster chunk) {
			// cut short: it falls as it is
			chunk.script(false, false);
			return;
		}
		if (whip.hadGravity) {
			target.setNoGravity(false);
		}
	}

	/** Cut short: the line let go (a swing web went out instead, or the player left). */
	static void cancel(ServerPlayer player) {
		dropPry(player);
		dropCatch(player);
		Whip whip = WHIPS.get(player.getUUID());
		if (whip != null) {
			end(player, whip, player.level().getEntity(whip.target));
		}
	}

	static void forget(ServerPlayer player) {
		cancel(player);
		READY.remove(player.getUUID());
	}
}
