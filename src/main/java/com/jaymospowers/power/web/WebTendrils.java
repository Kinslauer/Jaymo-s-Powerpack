package com.jaymospowers.power.web;

import com.jaymospowers.JaymosPowers;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A symbiote's grabbing tendrils, on the swing keys while the suit is on: R (right) and G (left). Held on a creature, an
 * item, a corpse or another player, the tendril snatches it and holds it up in front of you (everyone sees the cords);
 * let go and it's hurled where you look, hard enough to hurt when it hits. Aimed at nothing grabbable, the keys swing
 * on the tendrils instead (a short-range swing; see {@link WebSymbiote#swingScale}). The radial menu's Tendril Grab
 * snatches from further and hurls on its own after a moment.
 */
public final class WebTendrils {
	/** Reach of a grab on the keys (and of the radial grab). */
	public static final double GRAB_RANGE = 9.0;
	public static final double RADIAL_GRAB_RANGE = 20.0;
	/** Where it's held: this far ahead, this far to its side, this far below the eyes (in reach of a punch or Consume). */
	static final double HOLD_FORWARD = 1.9, HOLD_DROP = 0.45;
	/** Two held: each off to its own side by this much (blocks), and this much up or down, picked at random. */
	static final double HOLD_SIDE_MIN = 1.1, HOLD_SIDE_MAX = 1.6, HOLD_UP_MIN = -0.5, HOLD_UP_MAX = 0.7;
	/** How fast a tendril reaches out (blocks a tick): slower than a web shoots. */
	public static final double TENDRIL_SPEED = 0.9;
	static final double HOLD_STEER = 0.45, HOLD_MAX_SPEED = 1.4;
	/** Longest it can be held, and how long the radial grab holds before it throws by itself. */
	static final int MAX_HOLD_TICKS = 300;
	static final int RADIAL_HOLD_TICKS = 50;
	/** The throw: speed along the look, plus a lift; and the impact damage of a thrown body hitting something. */
	static final double THROW_SPEED = 2.4, THROW_LIFT = 0.25;
	static final float IMPACT_DAMAGE_MIN = 4.0F, IMPACT_DAMAGE_PER_SPEED = 6.0F;
	static final int THROWN_TICKS = 40;
	/**
	 * A thrown body bowls through creatures in its way: each it hits (once) takes {@code BOWL_DAMAGE_PER_SPEED} per
	 * block/tick of speed (at least {@code IMPACT_DAMAGE_MIN}) and is knocked along; the body takes {@code BOWL_SELF}
	 * of that and slows by {@code BOWL_SLOW}. Below {@code BOWL_MIN_SPEED} it's just falling and hits nothing.
	 */
	static final float BOWL_DAMAGE_PER_SPEED = 5.0F, BOWL_SELF = 0.5F;
	static final double BOWL_SLOW = 0.7, BOWL_MIN_SPEED = 0.5, BOWL_KNOCK = 0.6;

	private static final Identifier HOLD_GRAVITY = JaymosPowers.id("web_tendril_hold");

	/** What each tendril holds (entity ids, -1 for nothing): synced to everyone, who draw the cords. */
	public record Grips(int right, int left) {
		public static final Grips NONE = new Grips(-1, -1);
		public static final StreamCodec<ByteBuf, Grips> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Grips::right, ByteBufCodecs.VAR_INT, Grips::left, Grips::new);

		public int get(int hand) {
			return hand == WebNet.OFF ? left : right;
		}

		Grips with(int hand, int id) {
			return hand == WebNet.OFF ? new Grips(right, id) : new Grips(id, left);
		}

		boolean empty() {
			return right < 0 && left < 0;
		}
	}

	public static final AttachmentType<Grips> GRIPS = AttachmentRegistry.create(JaymosPowers.id("web_tendril_grips"),
		builder -> builder.syncWith(Grips.STREAM_CODEC, AttachmentSyncPredicate.all()));

	/**
	 * A tendril combo in progress (see {@link WebTendrilCombos}): which ({@link WebTendrilCombos#CLAP}, CRUSH, SHATTER),
	 * when it began (game time), and the two things in it (clap: main, off; crush: the debris, then what it lands on;
	 * shatter: main, off). Synced to everyone, who see the tendrils perform it.
	 */
	public record Combo(int kind, long start, int first, int second) {
		public static final StreamCodec<ByteBuf, Combo> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Combo::kind, ByteBufCodecs.VAR_LONG, Combo::start,
			ByteBufCodecs.VAR_INT, Combo::first, ByteBufCodecs.VAR_INT, Combo::second, Combo::new);
	}

	public static final AttachmentType<Combo> COMBO = AttachmentRegistry.create(JaymosPowers.id("web_tendril_combo"),
		builder -> builder.syncWith(Combo.STREAM_CODEC, AttachmentSyncPredicate.all()));

	/**
	 * Sneak + a tendril key at a wall or the ground (within {@code GRAB_RANGE}): the tendril tears a chunk loose, up to
	 * {@code RIP_PIECES} blocks no harder than {@code RIP_HARDEST}, and holds it like anything else.
	 */
	static final int RIP_PIECES = 6;
	static final float RIP_HARDEST = 4.0F;
	/** The {@link Grab} entity id that means "the block I'm aiming at". */
	public static final int GRAB_BLOCK = -2;
	/**
	 * Tearing a chunk loose isn't instant: the tendril reaches the wall, digs in and struggles with it, the blocks
	 * cracking and your view jarring, for {@code TUG_MIN} + {@code TUG_PER_HARDNESS} x the hardest block's hardness + up
	 * to {@code TUG_RANDOM} more ticks (never more than {@code TUG_MAX}; 0.9.8: a flat half second), then it tears loose
	 * and is hauled in, held. Let go of the key before then and nothing comes (as Gravity's Lift struggles). A spawner it
	 * digs into (aimed at, or in the crater) is wrecked when the chunk tears out: broken, as if mined.
	 */
	static final int TUG_MIN = 10, TUG_MAX = 10, TUG_RANDOM = 0;
	static final float TUG_PER_HARDNESS = 0.0F;
	/**
	 * A chunk swung with a click is a heavy cross, not a jab: hauled back behind that shoulder over {@code CROSS_WINDUP}
	 * ticks (slowly, at {@code CROSS_DRAW_SPEED}), whipped across in {@code CROSS_SWING} onto whatever's in front within
	 * {@code CROSS_REACH} ({@code CROSS_DAMAGE} + {@code CROSS_DAMAGE_PER_PIECE} a block, at most {@code CROSS_DAMAGE_MAX},
	 * knocked {@code CROSS_KNOCK} away), carried through to the other side for {@code CROSS_FOLLOW}, and only after
	 * {@code CROSS_RECOVER} more can anything be swung again.
	 */
	static final int CROSS_WINDUP = 22, CROSS_SWING = 5, CROSS_FOLLOW = 8, CROSS_RECOVER = 18;
	static final double CROSS_REACH = 5.0, CROSS_KNOCK = 1.1, CROSS_LIFT = 0.35, CROSS_DRAW_SPEED = 0.25, CROSS_WHIP_SPEED = 3.2;
	/** Where it's drawn back to: this far out to that side, behind you, and up (blocks). */
	static final double CROSS_SIDE = 1.5, CROSS_BACK = 1.3, CROSS_UP = 0.7;
	static final float CROSS_DAMAGE = 6.0F, CROSS_DAMAGE_PER_PIECE = 1.5F, CROSS_DAMAGE_MAX = 16.0F, CROSS_SHAKE = 1.2F;
	/** Wall Crawl's latches may be no further than this from your eyes. */
	static final double CLIMB_LATCH_REACH = 4.5;

	/**
	 * Where tendrils have latched onto the world (holding no thing): {@code mask} bit 1 the right (R) side's, at
	 * {@code right}; bit 2 the left (G) side's, at {@code left}. Synced to everyone, who draw the cords.
	 */
	public record Latch(Vec3 right, Vec3 left, int mask) {
		public static final Latch NONE = new Latch(Vec3.ZERO, Vec3.ZERO, 0);
		public static final StreamCodec<ByteBuf, Latch> STREAM_CODEC = StreamCodec.composite(
			Vec3.STREAM_CODEC, Latch::right, Vec3.STREAM_CODEC, Latch::left, ByteBufCodecs.VAR_INT, Latch::mask, Latch::new);

		public boolean has(int hand) {
			return (mask & (hand == WebNet.OFF ? 2 : 1)) != 0;
		}

		public Vec3 get(int hand) {
			return hand == WebNet.OFF ? left : right;
		}

		Latch with(int hand, Vec3 at) {
			return hand == WebNet.OFF ? new Latch(right, at, mask | 2) : new Latch(at, left, mask | 1);
		}

		Latch without(int hand) {
			return new Latch(right, left, mask & ~(hand == WebNet.OFF ? 2 : 1));
		}
	}

	/** Wall Crawl's tendrils latched onto the wall (the client climbs; it reports where they bite). */
	public static final AttachmentType<Latch> CLIMB = AttachmentRegistry.create(JaymosPowers.id("web_tendril_climb"),
		builder -> builder.syncWith(Latch.STREAM_CODEC, AttachmentSyncPredicate.all()));
	/** A tendril dug into a wall, struggling to tear a chunk loose. */
	public static final AttachmentType<Latch> TUG = AttachmentRegistry.create(JaymosPowers.id("web_tendril_tug"),
		builder -> builder.syncWith(Latch.STREAM_CODEC, AttachmentSyncPredicate.all()));

	/** Client → server: swing the chunk this tendril holds at what's in front (left click: main, right click: off). */
	public record Smash(int hand) implements CustomPacketPayload {
		public static final Type<Smash> TYPE = new Type<>(JaymosPowers.id("web_tendril_smash"));
		public static final StreamCodec<ByteBuf, Smash> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Smash::hand, Smash::new);

		@Override
		public Type<Smash> type() {
			return TYPE;
		}
	}

	/** Client → server: a tendril key on something (grab it), or let go (entity -1: throw; -2: tear a chunk out of the block aimed at). */
	public record Grab(int hand, int entityId) implements CustomPacketPayload {
		public static final Type<Grab> TYPE = new Type<>(JaymosPowers.id("web_tendril_grab"));
		public static final StreamCodec<ByteBuf, Grab> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Grab::hand, ByteBufCodecs.VAR_INT, Grab::entityId, Grab::new);

		@Override
		public Type<Grab> type() {
			return TYPE;
		}
	}

	/**
	 * {@code reachAt}: the tendril takes time to reach out ({@code TENDRIL_SPEED} blocks a tick); until then the target
	 * isn't held. {@code side}, {@code up}: where it's held while both tendrils are full (randomly off the middle of
	 * the view, on its own side), in blocks across and up.
	 */
	private record Hold(UUID player, int hand, long since, long throwAt, long reachAt, double side, double up) {
	}

	private record Thrown(ResourceKey<Level> dimension, UUID entity, UUID thrower, long until, java.util.Set<Integer> struck) {
	}

	private static final java.util.Map<UUID, Hold[]> HOLDS = new java.util.HashMap<>();
	private static final List<Thrown> THROWN = new ArrayList<>();

	/** A chunk being struggled loose: where the tendril bit, the blocks that will come, when it gets there, and when they tear. */
	private record Tug(net.minecraft.world.phys.BlockHitResult hit, List<net.minecraft.core.BlockPos> blocks, long reachAt, long tearAt) {
	}

	/** A cross in progress: which tendril, its chunk, and when it began. */
	private record Cross(int hand, int chunk, long start) {
	}

	/** Where a cross puts the chunk this tick, and how fast it may get there. */
	private record Aim(Vec3 point, double speed) {
	}

	private static final java.util.Map<UUID, Tug[]> TUGS = new java.util.HashMap<>();
	private static final java.util.Map<UUID, Cross> CROSSES = new java.util.HashMap<>();

	private WebTendrils() {
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Grab.TYPE, Grab.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Grab.TYPE, (payload, ctx) -> {
			ServerPlayer player = ctx.player();
			int hand = payload.hand() == WebNet.OFF ? WebNet.OFF : WebNet.MAIN;
			if (WebSymbiote.driving(player)) {
				// a takeover has your hands: nothing you press reaches them
				return;
			}
			if (payload.entityId() < 0 && payload.entityId() != GRAB_BLOCK && tugging(player, hand)) {
				// let go before it came loose: nothing comes
				cancelTug(player, hand);
				return;
			}
			if (payload.entityId() == GRAB_BLOCK) {
				if (WebSymbiote.tendrilKeys(player) && player.isAlive() && !player.isSpectator()) {
					rip(player, hand);
				}
			} else if (payload.entityId() < 0) {
				release(player, hand, true);
			} else if (WebSymbiote.tendrilKeys(player) && player.isAlive() && !player.isSpectator()) {
				Entity target = player.level().getEntity(payload.entityId());
				if (target != null && target.distanceTo(player) <= GRAB_RANGE + 2.5) {
					grab(player, hand, target, -1L);
				}
			}
		});
		PayloadTypeRegistry.serverboundPlay().register(Smash.TYPE, Smash.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Smash.TYPE, (payload, ctx) -> {
			if (!WebSymbiote.driving(ctx.player())) {
				smash(ctx.player(), payload.hand() == WebNet.OFF ? WebNet.OFF : WebNet.MAIN);
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(WebNet.ClimbLatch.TYPE, (payload, ctx) -> climbLatch(ctx.player(), payload));
		WebTendrilCombos.register();
		ServerTickEvents.END_SERVER_TICK.register(WebTendrils::serverTick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				releaseAll(player, false);
			}
			HOLDS.clear();
			THROWN.clear();
			TUGS.clear();
			CROSSES.clear();
		});
	}

	/** Anything a tendril can take: creatures, players (not in creative), items, corpses (not heavy ones). Not yourself. */
	public static boolean grabbable(Player player, Entity entity) {
		return entity != player && entity.isAlive() && !entity.isSpectator() && !(entity instanceof ArmorStand) && !KitchenCompat.isHeavy(entity)
			&& !(entity instanceof Player p && p.isCreative()) && entity.getVehicle() == null && !entity.isVehicle()
			&& (entity instanceof LivingEntity || entity instanceof net.minecraft.world.entity.item.ItemEntity
				|| entity instanceof com.jaymospowers.core.BlockCluster || com.jaymospowers.power.gravity.GravityPower.isCorpse(entity));
	}

	/** Sneak + a tendril key at a block: the tendril reaches it and struggles a chunk loose (see {@link #TUG_MIN}). */
	private static void rip(ServerPlayer player, int hand) {
		Vec3 eye = player.getEyePosition();
		net.minecraft.world.phys.BlockHitResult hit = player.level().clip(new net.minecraft.world.level.ClipContext(eye,
			eye.add(player.getLookAngle().scale(GRAB_RANGE)), net.minecraft.world.level.ClipContext.Block.COLLIDER,
			net.minecraft.world.level.ClipContext.Fluid.NONE, player));
		if (hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK || !player.mayBuild()) {
			return;
		}
		ServerLevel level = player.level();
		List<net.minecraft.core.BlockPos> blocks = new ArrayList<>(com.jaymospowers.core.BlockCluster.crater(level, hit.getBlockPos(), RIP_PIECES, RIP_HARDEST, player));
		if (spawner(level, hit.getBlockPos()) && level.mayInteract(player, hit.getBlockPos())) {
			blocks.add(0, hit.getBlockPos());
		}
		if (blocks.isEmpty()) {
			return;
		}
		release(player, hand, false);
		cancelTug(player, hand);
		float hardest = 0.0F;
		for (net.minecraft.core.BlockPos pos : blocks) {
			if (!spawner(level, pos)) {
				hardest = Math.max(hardest, level.getBlockState(pos).getDestroySpeed(level, pos));
			}
		}
		long now = level.getGameTime();
		long reach = (long)Math.ceil(hit.getLocation().distanceTo(eye) / TENDRIL_SPEED);
		int struggle = Mth.clamp(TUG_MIN + Math.round(hardest * TUG_PER_HARDNESS) + player.getRandom().nextInt(TUG_RANDOM + 1), TUG_MIN, TUG_MAX);
		TUGS.computeIfAbsent(player.getUUID(), k -> new Tug[2])[hand] = new Tug(hit, blocks, now + reach, now + reach + struggle);
		player.setAttached(TUG, player.getAttachedOrElse(TUG, Latch.NONE).with(hand, hit.getLocation()));
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_TENDRIL_CLICKS, SoundSource.PLAYERS, 0.9F, 0.6F);
	}

	/** A monster spawner (or a trial spawner): the tendrils can tear those apart. */
	private static boolean spawner(ServerLevel level, net.minecraft.core.BlockPos pos) {
		net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
		return state.is(net.minecraft.world.level.block.Blocks.SPAWNER) || state.is(net.minecraft.world.level.block.Blocks.TRIAL_SPAWNER);
	}

	/** That tendril is struggling with a chunk of wall. */
	static boolean tugging(ServerPlayer player, int hand) {
		Tug[] tugs = TUGS.get(player.getUUID());
		return tugs != null && tugs[hand] != null;
	}

	/** The struggle's off: its cracks fade and the tendril lets go of the wall. */
	private static void cancelTug(ServerPlayer player, int hand) {
		Tug[] tugs = TUGS.get(player.getUUID());
		if (tugs != null && tugs[hand] != null) {
			for (int i = 0; i < tugs[hand].blocks().size(); i++) {
				player.level().destroyBlockProgress(tugCrack(player, hand, i), tugs[hand].blocks().get(i), -1);
			}
			tugs[hand] = null;
			if (tugs[0] == null && tugs[1] == null) {
				TUGS.remove(player.getUUID());
			}
		}
		Latch latch = player.getAttached(TUG);
		if (latch != null) {
			Latch next = latch.without(hand);
			if (next.mask() == 0) {
				player.removeAttached(TUG);
			} else {
				player.setAttached(TUG, next);
			}
		}
	}

	private static int tugCrack(ServerPlayer player, int hand, int i) {
		return player.getId() * 47 + 4000 + hand * 16 + i;
	}

	/** The struggles, a tick at a time: dug in, the blocks crack and shudder; long enough, and they tear loose and are held. */
	private static void tickTugs(ServerPlayer player, long now) {
		Tug[] tugs = TUGS.get(player.getUUID());
		if (tugs == null) {
			return;
		}
		ServerLevel level = player.level();
		for (int hand = WebNet.MAIN; hand <= WebNet.OFF; hand++) {
			Tug tug = tugs[hand];
			if (tug == null) {
				continue;
			}
			if (!player.isAlive() || player.isSpectator() || !WebSymbiote.tendrilKeys(player)
				|| tug.hit().getLocation().distanceTo(player.getEyePosition()) > GRAB_RANGE + 3.0) {
				cancelTug(player, hand);
				continue;
			}
			if (now < tug.reachAt()) {
				// still reaching for the wall
				continue;
			}
			long t = now - tug.reachAt();
			long total = Math.max(1L, tug.tearAt() - tug.reachAt());
			if (now < tug.tearAt()) {
				// it strains: every block cracking, the tendril wrenching at it, your view jarring with it
				int stage = (int)Math.min(8L, t * 9L / total);
				for (int i = 0; i < tug.blocks().size(); i++) {
					level.destroyBlockProgress(tugCrack(player, hand, i), tug.blocks().get(i), stage);
				}
				if (t % 5 == 0) {
					net.minecraft.core.BlockPos at = tug.blocks().get((int)(t / 5 % tug.blocks().size()));
					net.minecraft.world.level.block.state.BlockState state = level.getBlockState(at);
					level.playSound(null, at, state.getSoundType().getHitSound(), SoundSource.BLOCKS, 1.0F, 0.5F);
					com.jaymospowers.core.Debris.puff(level, at, state, 0.4F);
				}
				if (t % 8 == 0) {
					ServerPlayNetworking.send(player, new WebNet.Shake(0.35F + 0.4F * t / total));
					level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 0.6F, 0.5F);
				}
				if (t == total / 2) {
					WebSymbiote.narrate(player, "tug");
				}
				continue;
			}
			// it gives: torn out, and hauled in
			List<net.minecraft.core.BlockPos> loose = new ArrayList<>();
			for (net.minecraft.core.BlockPos pos : tug.blocks()) {
				if (spawner(level, pos)) {
					// a spawner's cage is torn apart, not carried off
					level.destroyBlock(pos, true, player);
					ServerPlayNetworking.send(player, new WebNet.Shake(0.7F));
				} else if (com.jaymospowers.core.BlockCluster.takeable(level, pos, RIP_HARDEST, player)) {
					loose.add(pos);
				}
			}
			cancelTug(player, hand);
			com.jaymospowers.core.BlockCluster chunk = com.jaymospowers.core.BlockCluster.of(level, loose,
				tug.hit().getDirection().getUnitVec3().scale(0.9), player);
			if (chunk != null) {
				grab(player, hand, chunk, -1L, true);
				ServerPlayNetworking.send(player, new WebNet.Shake(0.9F));
			}
		}
	}

	/** Left or right click while that tendril holds a chunk: a heavy cross (see {@link #CROSS_WINDUP}). */
	static void smash(ServerPlayer player, int hand) {
		Grips grips = player.getAttached(GRIPS);
		if (grips == null || !WebSymbiote.tendrilKeys(player) || !player.isAlive() || CROSSES.containsKey(player.getUUID())) {
			return;
		}
		if (player.level().getEntity(grips.get(hand)) instanceof com.jaymospowers.core.BlockCluster chunk && reached(player, hand)) {
			CROSSES.put(player.getUUID(), new Cross(hand, chunk.getId(), player.level().getGameTime()));
			// the weight of it, dragged back
			ServerLevel level = player.level();
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_TENDRIL_CLICKS, SoundSource.PLAYERS, 1.0F, 0.4F);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_JUMP, SoundSource.PLAYERS, 1.0F, 0.4F);
		}
	}

	/** A cross is under way (or still recovering): no other swing until it's done. */
	static boolean crossing(Player player) {
		return CROSSES.containsKey(player.getUUID());
	}

	/** Where the cross puts its chunk now (null: back to the usual hold). */
	private static @org.jspecify.annotations.Nullable Aim crossAim(ServerPlayer player, Entity held, int hand, long now) {
		Cross cross = CROSSES.get(player.getUUID());
		if (cross == null || cross.hand() != hand || cross.chunk() != held.getId()) {
			return null;
		}
		long t = now - cross.start();
		Vec3 eye = player.getEyePosition();
		Vec3 dir = player.getLookAngle().horizontal();
		dir = dir.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : dir.normalize();
		Vec3 right = new Vec3(-dir.z, 0.0, dir.x);
		double sign = hand == WebNet.OFF ? -1.0 : 1.0;
		Vec3 back = eye.add(right.scale(sign * (CROSS_SIDE + held.getBbWidth() * 0.5))).subtract(dir.scale(CROSS_BACK)).add(0.0, CROSS_UP, 0.0);
		if (t < CROSS_WINDUP) {
			// drawn back, slow and grinding, and held there trembling
			double tremble = t > CROSS_WINDUP / 2 ? (player.getRandom().nextDouble() - 0.5) * 0.12 : 0.0;
			return new Aim(back.add(0.0, tremble, 0.0), CROSS_DRAW_SPEED);
		}
		LivingEntity target = crossTarget(player);
		Vec3 aim = target != null ? target.getBoundingBox().getCenter() : eye.add(player.getLookAngle().scale(2.4));
		if (t < CROSS_WINDUP + CROSS_SWING) {
			double k = (t - CROSS_WINDUP + 1) / (double)CROSS_SWING;
			return new Aim(back.lerp(aim, k * k), CROSS_WHIP_SPEED);
		}
		if (t < CROSS_WINDUP + CROSS_SWING + CROSS_FOLLOW) {
			// carried on through, across to the other side
			Vec3 through = eye.add(right.scale(-sign * 1.4)).add(dir.scale(1.6)).subtract(0.0, 0.4, 0.0);
			return new Aim(through, 1.2);
		}
		return null;
	}

	/** What a cross lands on: the nearest creature in front within {@link #CROSS_REACH}. */
	private static @org.jspecify.annotations.Nullable LivingEntity crossTarget(ServerPlayer player) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		LivingEntity best = null;
		double bestD = Double.MAX_VALUE;
		Grips grips = player.getAttachedOrElse(GRIPS, Grips.NONE);
		for (LivingEntity living : player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(CROSS_REACH + 1.0),
			e -> e != player && e.isAlive() && !e.isSpectator() && !player.isAlliedTo(e) && e.getId() != grips.right() && e.getId() != grips.left())) {
			Vec3 to = living.getBoundingBox().getCenter().subtract(eye);
			double d = to.length();
			if (d > CROSS_REACH + living.getBbWidth() * 0.5 || d > 1.0E-3 && to.scale(1.0 / d).dot(look) < 0.3) {
				continue;
			}
			if (d < bestD) {
				bestD = d;
				best = living;
			}
		}
		return best;
	}

	/** The cross, a tick at a time: the whoosh as it comes, the hit, and the end of the recovery. */
	private static void tickCross(ServerPlayer player, long now) {
		Cross cross = CROSSES.get(player.getUUID());
		if (cross == null) {
			return;
		}
		long t = now - cross.start();
		ServerLevel level = player.level();
		Grips grips = player.getAttachedOrElse(GRIPS, Grips.NONE);
		boolean holding = grips.get(cross.hand()) == cross.chunk() && level.getEntity(cross.chunk()) instanceof com.jaymospowers.core.BlockCluster;
		if (!holding && t < CROSS_WINDUP + CROSS_SWING || t >= CROSS_WINDUP + CROSS_SWING + CROSS_FOLLOW + CROSS_RECOVER || !player.isAlive()) {
			CROSSES.remove(player.getUUID());
			return;
		}
		if (t == CROSS_WINDUP) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.2F, 0.45F);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.MACE_SMASH_AIR, SoundSource.PLAYERS, 1.0F, 0.5F);
			player.swing(cross.hand() == WebNet.OFF ? net.minecraft.world.InteractionHand.OFF_HAND : net.minecraft.world.InteractionHand.MAIN_HAND,
				net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		}
		if (t != CROSS_WINDUP + CROSS_SWING || !(level.getEntity(cross.chunk()) instanceof com.jaymospowers.core.BlockCluster chunk)) {
			return;
		}
		LivingEntity target = crossTarget(player);
		if (target == null) {
			return;
		}
		float damage = chunk.allSoft() ? com.jaymospowers.core.BlockCluster.SOFT_HIT_DAMAGE
			: Math.min(CROSS_DAMAGE_MAX, CROSS_DAMAGE + CROSS_DAMAGE_PER_PIECE * chunk.pieces().size());
		target.hurtServer(level, level.damageSources().playerAttack(player), damage);
		Vec3 away = target.position().subtract(player.position()).horizontal();
		away = away.lengthSqr() < 1.0E-4 ? player.getLookAngle().horizontal() : away;
		away = away.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : away.normalize();
		Vec3 push = away.scale(CROSS_KNOCK).add(0.0, CROSS_LIFT, 0.0);
		target.setDeltaMovement(target.getDeltaMovement().add(push));
		target.needsSync = true;
		target.syncVelocity = true;
		if (target instanceof ServerPlayer other) {
			ServerPlayNetworking.send(other, new WebNet.Nudge(target.getDeltaMovement()));
			ServerPlayNetworking.send(other, new WebNet.Shake(CROSS_SHAKE * 1.5F));
		}
		Vec3 at = target.getBoundingBox().getCenter();
		level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 14, 0.3, 0.3, 0.3, 0.2);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.2F, 0.5F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.1F, 0.7F);
		ServerPlayNetworking.send(player, new WebNet.Shake(CROSS_SHAKE));
		WebSymbiote.narrate(player, "smash");
		chunk.chip(level);
		if (!chunk.isAlive()) {
			release(player, cross.hand(), false);
		}
	}

	/** Wall Crawl's client reports where a tendril bit into the wall (or let go). */
	private static void climbLatch(ServerPlayer player, WebNet.ClimbLatch payload) {
		int hand = payload.hand() == WebNet.OFF ? WebNet.OFF : WebNet.MAIN;
		Latch latch = player.getAttachedOrElse(CLIMB, Latch.NONE);
		Latch next;
		if (payload.on() && WebSymbiote.isToggled(player, WebSymbioteState.CRAWL) && player.isAlive() && !WebSymbiote.driving(player)
			&& payload.at().distanceTo(player.getEyePosition()) <= CLIMB_LATCH_REACH) {
			next = latch.with(hand, payload.at());
			ServerLevel level = player.level();
			level.playSound(null, payload.at().x, payload.at().y, payload.at().z, SoundEvents.SLIME_ATTACK, SoundSource.PLAYERS, 0.5F, 0.6F);
			if (player.getRandom().nextInt(4) == 0) {
				WebSymbiote.narrate(player, "climb");
			}
		} else {
			next = latch.without(hand);
		}
		if (next.mask() == 0) {
			player.removeAttached(CLIMB);
		} else {
			player.setAttached(CLIMB, next);
		}
	}

	/** Instinct: a tendril lashes out at an attacker, snatches it and flings it away a moment later. */
	static void snatch(ServerPlayer player, Entity target) {
		int hand = player.getAttachedOrElse(GRIPS, Grips.NONE).left() < 0 ? WebNet.OFF : WebNet.MAIN;
		release(player, hand, false);
		grab(player, hand, target, player.level().getGameTime() + INSTINCT_HOLD_TICKS);
	}

	/** How long an instinctive snatch holds before it flings. */
	static final int INSTINCT_HOLD_TICKS = 12;

	/** The radial menu's Tendril Grab: the right tendril snatches from afar and hurls it on its own after a moment. */
	static boolean radialGrab(ServerPlayer player) {
		Entity target = WebPower.entityInSight(player, RADIAL_GRAB_RANGE);
		if (target == null || !grabbable(player, target)) {
			return false;
		}
		release(player, WebNet.MAIN, false);
		grab(player, WebNet.MAIN, target, player.level().getGameTime() + RADIAL_HOLD_TICKS);
		return true;
	}

	static void grab(ServerPlayer player, int hand, Entity target, long throwAt) {
		grab(player, hand, target, throwAt, false);
	}

	/** {@code attached}: the tendril is already on it (it tore it loose), so there's no reach out first. */
	static void grab(ServerPlayer player, int hand, Entity target, long throwAt, boolean attached) {
		if (!grabbable(player, target) || heldBySomeone(target)) {
			return;
		}
		release(player, hand, false);
		weightless(target, true);
		Hold[] holds = HOLDS.computeIfAbsent(player.getUUID(), k -> new Hold[2]);
		long now = player.level().getGameTime();
		long reach = attached ? 0L : (long)Math.ceil(target.distanceTo(player) / TENDRIL_SPEED);
		// off to one side when both are full: this one's side, at a random spot (the middle of the view stays clear)
		Hold other = holds[hand == WebNet.OFF ? WebNet.MAIN : WebNet.OFF];
		// R's tendril (main) holds to the right, G's (off) to the left, so neither crosses the view
		double sign = hand == WebNet.OFF ? -1.0 : 1.0;
		double side = sign * (HOLD_SIDE_MIN + player.getRandom().nextDouble() * (HOLD_SIDE_MAX - HOLD_SIDE_MIN));
		double up = HOLD_UP_MIN + player.getRandom().nextDouble() * (HOLD_UP_MAX - HOLD_UP_MIN);
		holds[hand] = new Hold(player.getUUID(), hand, now, throwAt < 0 ? throwAt : throwAt + reach, now + reach, side, up);
		Grips grips = player.getAttachedOrElse(GRIPS, Grips.NONE);
		player.setAttached(GRIPS, grips.with(hand, target.getId()));
		ServerLevel level = player.level();
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.SLIME_ATTACK, SoundSource.PLAYERS, 1.0F, 0.5F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_TENDRIL_CLICKS, SoundSource.PLAYERS, 0.8F, 0.7F);
	}

	/** That tendril has reached what it holds (or holds nothing). */
	static boolean reached(ServerPlayer player, int hand) {
		Hold[] holds = HOLDS.get(player.getUUID());
		return holds == null || holds[hand] == null || player.level().getGameTime() >= holds[hand].reachAt();
	}

	/** Some tendril (anyone's) holds this. */
	static boolean held(Entity target) {
		return heldBySomeone(target);
	}

	private static boolean heldBySomeone(Entity target) {
		if (!(target.level() instanceof ServerLevel level)) {
			return false;
		}
		for (ServerPlayer other : level.players()) {
			Grips grips = other.getAttached(GRIPS);
			if (grips != null && (grips.right() == target.getId() || grips.left() == target.getId())) {
				return true;
			}
		}
		return false;
	}

	/** Lets go of what a tendril holds: thrown where you look ({@code hurl}), or just dropped. */
	static void release(ServerPlayer player, int hand, boolean hurl) {
		letGo(player, hand, hurl ? player.getLookAngle().scale(THROW_SPEED).add(0.0, THROW_LIFT, 0.0) : null);
	}

	/** Lets go of what a tendril holds, flung at {@code v} (blocks/tick) instead of where you look. */
	static void fling(ServerPlayer player, int hand, Vec3 v) {
		letGo(player, hand, v);
	}

	/** A tendril holding nothing (main first), or -1 if both are full. */
	static int freeHand(ServerPlayer player) {
		Grips grips = player.getAttachedOrElse(GRIPS, Grips.NONE);
		return grips.right() < 0 ? WebNet.MAIN : grips.left() < 0 ? WebNet.OFF : -1;
	}

	private static void letGo(ServerPlayer player, int hand, @org.jspecify.annotations.Nullable Vec3 thrown) {
		Grips grips = player.getAttached(GRIPS);
		Hold[] holds = HOLDS.get(player.getUUID());
		if (holds != null) {
			holds[hand] = null;
		}
		if (grips == null || grips.get(hand) < 0) {
			return;
		}
		Entity target = player.level().getEntity(grips.get(hand));
		Grips next = grips.with(hand, -1);
		if (next.empty()) {
			player.removeAttached(GRIPS);
		} else {
			player.setAttached(GRIPS, next);
		}
		if (target == null) {
			return;
		}
		weightless(target, false);
		if (thrown != null) {
			Vec3 v = thrown;
			target.setDeltaMovement(v);
			target.needsSync = true;
			target.syncVelocity = true;
			if (target instanceof ServerPlayer other) {
				ServerPlayNetworking.send(other, new WebNet.Nudge(v));
			}
			if (target instanceof com.jaymospowers.core.BlockCluster chunk) {
				// a chunk of wall does its own battering
				chunk.launch(player);
				player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 1.0F, 0.4F);
				return;
			}
			THROWN.removeIf(t -> t.entity().equals(target.getUUID()));
			THROWN.add(new Thrown(player.level().dimension(), target.getUUID(), player.getUUID(), player.level().getGameTime() + THROWN_TICKS,
				new java.util.HashSet<>()));
			ServerLevel level = player.level();
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 1.0F, 0.5F);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_JUMP, SoundSource.PLAYERS, 1.0F, 0.5F);
		}
	}

	static void releaseAll(ServerPlayer player, boolean hurl) {
		release(player, WebNet.MAIN, hurl);
		release(player, WebNet.OFF, hurl);
		HOLDS.remove(player.getUUID());
	}

	/** Held: no weight (so a held player isn't kicked for floating, and nothing sags). */
	private static void weightless(Entity entity, boolean on) {
		if (entity instanceof LivingEntity living) {
			AttributeInstance gravity = living.getAttribute(Attributes.GRAVITY);
			if (gravity != null) {
				if (on) {
					gravity.addOrUpdateTransientModifier(new AttributeModifier(HOLD_GRAVITY, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
				} else {
					gravity.removeModifier(HOLD_GRAVITY);
				}
			}
		} else {
			entity.setNoGravity(on);
		}
	}

	/** Every tick, per web slinger: whatever the tendrils hold hangs where it's held (and the radial grab throws). */
	static void tick(ServerPlayer player) {
		WebTendrilCombos.tick(player);
		long gameTime = player.level().getGameTime();
		tickTugs(player, gameTime);
		tickCross(player, gameTime);
		if (player.getAttached(CLIMB) != null && (!WebSymbiote.isToggled(player, WebSymbioteState.CRAWL) || !player.isAlive())) {
			player.removeAttached(CLIMB);
		}
		Grips grips = player.getAttached(GRIPS);
		if (grips == null) {
			return;
		}
		if (!player.isAlive() || player.isSpectator()
			|| !WebSymbiote.armored(player) && WebSymbiote.tendrilsOut(player) < player.level().getGameTime()) {
			releaseAll(player, false);
			return;
		}
		Hold[] holds = HOLDS.computeIfAbsent(player.getUUID(), k -> new Hold[2]);
		long now = player.level().getGameTime();
		for (int hand = WebNet.MAIN; hand <= WebNet.OFF; hand++) {
			int id = grips.get(hand);
			if (id < 0) {
				continue;
			}
			Entity target = player.level().getEntity(id);
			Hold hold = holds[hand];
			if (target == null || !target.isAlive() || target.level() != player.level() || target.distanceTo(player) > GRAB_RANGE * 2.0
				|| hold != null && now - hold.since() > MAX_HOLD_TICKS) {
				release(player, hand, false);
				continue;
			}
			// a combo steers what it uses (and nothing's thrown out from under it)
			Vec3 combo = WebTendrilCombos.point(player, target, hand, now);
			Aim cross = combo == null ? crossAim(player, target, hand, now) : null;
			if (cross != null) {
				combo = cross.point();
			}
			if (combo == null && hold != null && hold.throwAt() > 0 && now >= hold.throwAt()) {
				release(player, hand, true);
				continue;
			}
			if (combo == null && hold != null && now < hold.reachAt()) {
				// still reaching for it
				continue;
			}
			Vec3 look = player.getLookAngle();
			Vec3 right = look.cross(new Vec3(0.0, 1.0, 0.0));
			right = right.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : right.normalize();
			// one held sits right in front (in reach of a punch); with two, each is off to its own side (see Hold)
			boolean both = grips.left() >= 0 && grips.right() >= 0;
			double side = both && hold != null ? hold.side() + Math.signum(hold.side()) * target.getBbWidth() * 0.5 : 0.0;
			double up = both && hold != null ? hold.up() : 0.0;
			Vec3 point = player.getEyePosition().add(look.scale(HOLD_FORWARD + target.getBbWidth() * 0.5)).add(right.scale(side))
				.add(0.0, up - HOLD_DROP, 0.0);
			// held debris is swung along with a heavy strike (see WebSymbiote.debrisStrikePoint)
			Vec3 swing = WebSymbiote.debrisStrikePoint(player, target, now);
			double maxSpeed = HOLD_MAX_SPEED;
			double steer = HOLD_STEER;
			if (combo != null) {
				point = combo;
				maxSpeed = cross != null ? cross.speed() : WebTendrilCombos.SPEED;
				steer = WebTendrilCombos.STEER;
			} else if (swing != null) {
				point = swing;
				maxSpeed = WebSymbiote.HEAVY_DEBRIS_SPEED;
			}
			Vec3 to = point.subtract(target.getBoundingBox().getCenter());
			Vec3 v = to.scale(steer);
			double speed = v.length();
			if (speed > maxSpeed) {
				v = v.scale(maxSpeed / speed);
			}
			target.setDeltaMovement(v);
			target.needsSync = true;
			target.syncVelocity = true;
			target.resetFallDistance();
			if (target instanceof Mob mob) {
				mob.getNavigation().stop();
			}
			if (target instanceof ServerPlayer other && now % 2 == 0) {
				ServerPlayNetworking.send(other, new WebNet.Nudge(v));
			}
		}
	}

	/** Thrown bodies: the first hard knock (a wall, the ground) hurts by how fast they were going. */
	private static void serverTick(MinecraftServer server) {
		if (THROWN.isEmpty()) {
			return;
		}
		for (Iterator<Thrown> it = THROWN.iterator(); it.hasNext(); ) {
			Thrown thrown = it.next();
			ServerLevel level = server.getLevel(thrown.dimension());
			Entity entity = level == null ? null : level.getEntity(thrown.entity());
			if (!(entity instanceof LivingEntity living) || !living.isAlive() || level.getGameTime() >= thrown.until()) {
				it.remove();
				continue;
			}
			double speed = living.getDeltaMovement().length();
			if (speed >= BOWL_MIN_SPEED) {
				bowl(server, level, thrown, living, speed);
			}
			if (living.horizontalCollision || living.verticalCollision && living.getDeltaMovement().y <= 0.0 && level.getGameTime() + THROWN_TICKS - thrown.until() > 3) {
				it.remove();
				float damage = (float)Math.max(IMPACT_DAMAGE_MIN, speed * IMPACT_DAMAGE_PER_SPEED);
				// hard enough, and the wall (or ground) gives
				WebImpacts.smash(level, living.getBoundingBox().getCenter().add(living.getDeltaMovement().normalize().scale(0.6)), speed,
					server.getPlayerList().getPlayer(thrown.thrower()));
				ServerPlayer thrower = server.getPlayerList().getPlayer(thrown.thrower());
				living.hurtServer(level, thrower != null ? level.damageSources().playerAttack(thrower) : level.damageSources().flyIntoWall(), damage);
				level.sendParticles(ParticleTypes.POOF, living.getX(), living.getY() + living.getBbHeight() * 0.5, living.getZ(), 8, 0.3, 0.3, 0.3, 0.05);
				level.playSound(null, living.getX(), living.getY(), living.getZ(), SoundEvents.PLAYER_BIG_FALL, SoundSource.PLAYERS, 1.0F, 0.8F);
			}
		}
	}

	/** The flying body hits whoever's in its way (each once), knocking them along and slowing down itself. */
	private static void bowl(MinecraftServer server, ServerLevel level, Thrown thrown, LivingEntity body, double speed) {
		Vec3 v = body.getDeltaMovement();
		AABB sweep = body.getBoundingBox().expandTowards(v).inflate(0.3);
		for (LivingEntity hit : level.getEntitiesOfClass(LivingEntity.class, sweep,
			e -> e != body && e.isAlive() && !e.isSpectator() && !e.getUUID().equals(thrown.thrower())
				&& !(e instanceof net.minecraft.world.entity.decoration.ArmorStand) && !thrown.struck().contains(e.getId()))) {
			thrown.struck().add(hit.getId());
			float damage = (float)Math.max(IMPACT_DAMAGE_MIN, speed * BOWL_DAMAGE_PER_SPEED);
			ServerPlayer thrower = server.getPlayerList().getPlayer(thrown.thrower());
			hit.hurtServer(level, thrower != null ? level.damageSources().playerAttack(thrower) : level.damageSources().flyIntoWall(), damage);
			Vec3 push = v.normalize().scale(BOWL_KNOCK * speed);
			hit.setDeltaMovement(hit.getDeltaMovement().add(push.x, 0.3, push.z));
			hit.needsSync = true;
			hit.syncVelocity = true;
			body.setInvulnerableTime(0);
			body.hurtServer(level, thrower != null ? level.damageSources().playerAttack(thrower) : level.damageSources().flyIntoWall(), damage * BOWL_SELF);
			v = v.scale(BOWL_SLOW);
			body.setDeltaMovement(v);
			body.needsSync = true;
			body.syncVelocity = true;
			Vec3 at = hit.position().add(0.0, hit.getBbHeight() * 0.5, 0.0);
			level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 10, 0.3, 0.3, 0.3, 0.2);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.0F, 0.7F);
		}
	}

	static void forget(ServerPlayer player) {
		HOLDS.remove(player.getUUID());
		TUGS.remove(player.getUUID());
		CROSSES.remove(player.getUUID());
	}

	static double clampSide(double v) {
		return Mth.clamp(v, -1.0, 1.0);
	}
}
