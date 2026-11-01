package com.jaymospowers.core;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.api.Power;
import com.jaymospowers.power.web.KitchenCompat;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Ledge grab and climb-over, for every player (no power needed). Jump at a wall about two blocks high while holding
 * forward (and Jump): once your eyes come level with its top edge you catch it and hang there, arms up, body against the
 * wall. Press Jump again to pull yourself up and over (a short mantle: pull up, knee on the edge, stand); Sneak, Back or
 * looking away from the wall lets go.
 *
 * <p>(0.15.108) Under a gap only one block high (no room to stand on the top), going prone (the Crawl key) while you hang
 * pulls you up and in onto your belly instead, and you end the climb crawling. Jump does the same there, since standing
 * isn't possible. The catch is more forgiving too: an edge a little above your eyes, a wall a little further off, or the
 * top of a jump already falling are all caught, and Jump needn't be held (forward still is).
 *
 * <p>(0.19.40) The crouch grab: standing in front of a single block with a gap only one block high over it (no room to
 * stand up there), the crouch stance (the wheel's low crouch, (0.19.51) never plain Sneak) takes hold of its edge and pulls you up and in onto your belly the same way, with no jump
 * and no hang ({@link #crouchSpot}).
 *
 * <p>The moves are the client's (player movement is client-authoritative; see {@code client/LedgeClient}): it finds the
 * ledge with {@link #find}, holds the body still and walks it up the mantle's path. The server checks the rules
 * ({@link #canHang}), resets the fall, charges the climb's hunger, plays the sounds, syncs {@link #HOLD} so everyone
 * sees the pose, and keeps the player from being kicked for floating while they hang ({@link #relaxesMovement}).
 */
public final class Ledge {
	// ===== feel: the knobs to tweak after playtesting ===============================================================
	/** The edge may be at most this far above your eyes to be caught (blocks): "if you can see over it with a jump". */
	public static final double EDGE_ABOVE_EYES = 0.45;
	/** The edge must be at least this far above your feet when you catch it, so one-block steps are just jumped. */
	public static final double EDGE_ABOVE_FEET = 1.1;
	/** Rising, or no faster than this falling (blocks/tick: the top of the jump), to catch an edge. */
	public static final double GRAB_MIN_VY = -0.6;
	/** The wall's face may be at most this far ahead of your body to reach it (blocks). */
	public static final double REACH = 0.5;
	/** Facing within this many degrees of straight at the wall to grab it (a glancing jump goes past). */
	public static final float GRAB_FACING_DEGREES = 45.0F;
	/** The top of the edge needs at least this much area (square blocks) to hang from: no fence posts or panes. */
	public static final double MIN_TOP_AREA = 0.45;
	/** Blocks never grabbed (fences, walls, gates, cactus, and anything a datapack adds). */
	public static final TagKey<Block> NO_GRAB = TagKey.create(Registries.BLOCK, JaymosPowers.id("ledge_no_grab"));

	/** Hanging: your feet this far below the edge (your eyes about a quarter block under it, hands on top). */
	public static final double HANG_FEET_BELOW_EDGE = 1.85;
	/** Hanging: the gap between your body and the wall (blocks). */
	public static final double HANG_GAP = 0.02;
	/** Catching the edge: ticks to settle from where you caught it into the hang (the weight coming onto the arms). */
	public static final int GRAB_SETTLE_TICKS = 4;
	/** The grip gives out after this long hanging (ticks: 20 s), and you drop. */
	public static final int HANG_MAX_TICKS = 400;
	/** Looking more than this many degrees away from the wall lets go. */
	public static final float LOOK_AWAY_DEGREES = 100.0F;
	/** Letting go: pushed off the wall this fast (blocks/tick). */
	public static final double LET_GO_PUSH = 0.06;
	/** Ticks after letting go (or landing a climb) before another edge can be caught. */
	public static final int REGRAB_COOLDOWN_TICKS = 10;

	/** The climb (pull up, knee on the edge, stand) takes this long (ticks). */
	public static final int CLIMB_TICKS = 16;
	/** Of the climb, the share spent pulling straight up; the rest goes forward onto the top. */
	public static final float CLIMB_RISE_SHARE = 0.6F;
	/** Where the climb ends: your middle this far past the edge (blocks), standing this far above the top. */
	public static final double CLIMB_IN = 0.4, CLIMB_LIFT = 0.01;
	/** Hunger each climb costs (exhaustion: 4 is one hunger point; a sprint jump is 0.2). */
	public static final float CLIMB_EXHAUSTION = 0.6F;

	/** (0.15.108) The prone climb into a 1-high gap (pull up, tip forward, slide in on your belly) takes this long (ticks). */
	public static final int PRONE_CLIMB_TICKS = 20;
	/** Of the prone climb, the share spent rising to the top; the rest slides in across it. */
	public static final float PRONE_RISE_SHARE = 0.45F;
	/** Going prone, the body (now 0.6 high) is lifted this far so the eyes stay where they were (standing eyes 1.62, prone 0.4). */
	public static final double PRONE_EYE_KEEP = 1.22;
	/** Where the prone climb ends: your middle this far past the edge (blocks), or {@link #CLIMB_IN} when the gap is shallower. */
	public static final double PRONE_IN = 0.9;
	/**
	 * (0.19.40) The crouch grab: on the ground in the low crouch ({@link Stance#LOW}), facing a block whose top edge is this far over your feet
	 * (blocks: over a slab's step, no higher than the chest), with a gap only one block high above it, catches the edge
	 * and pulls you up and in onto your belly.
	 */
	public static final double CROUCH_EDGE_MIN = 0.7, CROUCH_EDGE_MAX = 1.6;

	/** Ticks the local client waits for the server to confirm a grab before giving up on it (a refused grab drops). */
	public static final int CONFIRM_TICKS = 20;
	/** After a hang or climb ends, the server goes easy on the player's movement this long (ticks). */
	public static final int RELEASE_GRACE_TICKS = 20;
	/** Server: a climb that hasn't been reported finished is ended this long after it should have (ticks). */
	public static final int CLIMB_SLACK_TICKS = 10;
	/** A hit while hanging or climbing knocks you off: you ragdoll ({@link Carry#THROWN}) and fall, pushed this far off the wall (b/t) and up. */
	public static final double KNOCKED_PUSH = 0.35, KNOCKED_LIFT = 0.12;
	// ================================================================================================================

	/**
	 * A player on a ledge, for everyone's client: the wall's side ({@code face}, a horizontal direction's 2D value),
	 * when the hang (or the climb, once {@code climbing}) began (game time).
	 */
	public record Hold(int face, long start, boolean climbing, boolean prone) {
		public static final StreamCodec<ByteBuf, Hold> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Hold::face,
			ByteBufCodecs.VAR_LONG, Hold::start,
			ByteBufCodecs.BOOL, Hold::climbing,
			ByteBufCodecs.BOOL, Hold::prone,
			Hold::new);

		public Hold(int face, long start, boolean climbing) {
			this(face, start, climbing, false);
		}

		/** How long this climb takes (ticks). */
		public int climbTicks() {
			return this.prone ? PRONE_CLIMB_TICKS : CLIMB_TICKS;
		}

		public Direction direction() {
			return Direction.from2DDataValue(this.face);
		}
	}

	/** On a player hanging from a ledge or climbing over it (synced to everyone, for the pose). */
	public static final AttachmentType<Hold> HOLD = AttachmentRegistry.create(JaymosPowers.id("ledge_hold"),
		builder -> builder.syncWith(Hold.STREAM_CODEC, AttachmentSyncPredicate.all()));

	/**
	 * A ledge found ahead: the block whose top is the edge ({@code pos}), the wall's side from the player
	 * ({@code face}), the edge's height ({@code top}), where the body hangs ({@code hang}) and where the climb ends
	 * ({@code onTop}). (0.15.108) {@code stand}: there's room to stand on the top (else only {@code onBelly}, where a prone
	 * climb ends, null when there's no room to lie there either).
	 */
	public record Spot(BlockPos pos, Direction face, double top, Vec3 hang, Vec3 onTop, boolean stand, @Nullable Vec3 onBelly) {
	}

	/** Client → server: caught the edge of the block at {@code pos}, on the wall at side {@code face} (2D value). */
	public record Grab(BlockPos pos, int face) implements CustomPacketPayload {
		public static final Type<Grab> TYPE = new Type<>(JaymosPowers.id("ledge_grab"));
		public static final StreamCodec<ByteBuf, Grab> CODEC = StreamCodec.composite(
			BlockPos.STREAM_CODEC, Grab::pos, ByteBufCodecs.VAR_INT, Grab::face, Grab::new);

		@Override
		public Type<Grab> type() {
			return TYPE;
		}
	}

	/** Client → server: the hanging player pulls up and over ({@code climb}; (0.15.108) onto their belly, {@code prone}), or lets go. */
	public record Act(boolean climb, boolean prone) implements CustomPacketPayload {
		public static final Type<Act> TYPE = new Type<>(JaymosPowers.id("ledge_act"));
		public static final StreamCodec<ByteBuf, Act> CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, Act::climb, ByteBufCodecs.BOOL, Act::prone, Act::new);

		public Act(boolean climb) {
			this(climb, false);
		}

		@Override
		public Type<Act> type() {
			return TYPE;
		}
	}

	/** Server: who's on a ledge, and the block they hold. */
	private static final Map<UUID, BlockPos> HOLDING = new HashMap<>();
	/** Server: until when (game time) a player who just left a ledge isn't kicked for how they move. */
	private static final Map<UUID, Long> GRACE = new HashMap<>();

	/** Client only: the local player's hold, ahead of the server's copy, so their own pose shows at once. */
	public static volatile @Nullable Hold localHold;
	/** Client only: the local player's entity id while {@link #localHold} is theirs. */
	public static volatile int localId = -1;

	private Ledge() {
	}

	public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(Grab.TYPE, Grab.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Act.TYPE, Act.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Grab.TYPE, (payload, ctx) -> grab(ctx.player(), payload));
		ServerPlayNetworking.registerGlobalReceiver(Act.TYPE, (payload, ctx) -> act(ctx.player(), payload.climb(), payload.prone()));
		ServerTickEvents.END_SERVER_TICK.register(Ledge::tick);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (entity instanceof ServerPlayer player && damageTaken > 0.0F && !blocked && HOLDING.containsKey(player.getUUID())
				&& (source.getEntity() != null || source.getDirectEntity() != null || source.is(DamageTypeTags.IS_EXPLOSION))) {
				knockedOff(player);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> clear());
	}

	private static void clear() {
		HOLDING.clear();
		GRACE.clear();
	}

	// ---- queries (both sides)

	/** The player's hold on a ledge (the local player's own copy on their client), or null. */
	public static @Nullable Hold hold(Player player) {
		if (player.level().isClientSide() && player.getId() == localId) {
			return localHold;
		}
		return player.getAttached(HOLD);
	}

	/** (0.15.108) Climbing onto their belly into a low gap (the crawl that begins with it doesn't end the hold). */
	public static boolean proneClimbing(Player player) {
		Hold hold = hold(player);
		return hold != null && hold.prone();
	}

	/** Hanging from a ledge or climbing over one. */
	public static boolean onLedge(Player player) {
		return hold(player) != null;
	}

	/**
	 * Whether this player may hang from a ledge at all right now: alive, on their own feet (not riding, carried or
	 * carrying anything), not in creative flight, an elytra, water, lava, a ladder or bed, and not in the middle of a
	 * power's own movement (a flight, a web, a wall walk...). Both sides; the server also asks the power
	 * ({@link #powerMoving}).
	 */
	public static boolean canHang(Player player) {
		if (!player.isAlive() || player.isSpectator() || player.isPassenger() || player.isVehicle() || player.getAbilities().flying
			|| player.isFallFlying() || player.isInWater() || player.isInLava() || player.isSwimming() || player.onClimbable()
			|| player.isSleeping() || player.isAutoSpinAttack()) {
			return false;
		}
		if (Carry.isCarried(player) || Carry.isCarrying(player) || Carry.isThrown(player) || KitchenCompat.isCarryingCorpse(player)
			|| Crawl.crawling(player) && !proneClimbing(player) || player.getAttached(Meditate.STATE) != null || KitchenCompat.isLimp(player)) {
			return false;
		}
		return !FlightRoll.powerFlying(player)
			&& player.getAttached(com.jaymospowers.power.web.WebPower.WINGS) == null
			&& player.getAttached(com.jaymospowers.power.web.WebPower.LINE) == null
			&& player.getAttached(com.jaymospowers.power.web.WebPower.LINE_OFF) == null
			&& player.getAttached(com.jaymospowers.power.web.WebPower.HELD) == null
			&& player.getAttached(com.jaymospowers.power.web.WebPower.HITCH) == null
			&& player.getAttached(com.jaymospowers.power.web.WebParkour.MOVE) == null
			&& player.getAttached(com.jaymospowers.power.web.WebWallRun.WALL) == null
			&& player.getAttached(com.jaymospowers.power.darktide.DarktideState.MOVE) == null
			&& !com.jaymospowers.power.darktide.DarktideStatus.stunned(player)
			&& !com.jaymospowers.power.darktide.DarktideStatus.crippled(player)
			&& !com.jaymospowers.power.chrono.ChronoFlow.stunned(player)
			&& !com.jaymospowers.power.web.WebSymbiote.crawlTendrils(player)
			&& !com.jaymospowers.power.web.WebPower.ragdolled(player)
			&& player.getAttached(com.jaymospowers.power.gravity.GravityPower.ROCK_FLIGHT) == null
			&& player.getAttached(com.jaymospowers.power.gravity.GravityPower.WEIGHTLESS_ON) == null
			&& player.getAttached(com.jaymospowers.power.gravity.GravityPower.LIFTED) == null
			&& player.getAttached(com.jaymospowers.power.gravity.GravityPower.HELD) == null
			&& player.getAttached(com.jaymospowers.power.gravity.GravityForward.FALLING) == null
			&& player.getAttached(com.jaymospowers.power.gravity.GravityWalk.SURFACE) == null
			&& player.getAttached(com.jaymospowers.power.chrono.ChronoSpeed.WALL_RUN) == null;
	}

	/** Server: the player's power is moving them itself (it keeps them up or lets them move faster than walking). */
	public static boolean powerMoving(ServerPlayer player) {
		Power power = Powers.of(player);
		return power != null && (power.allowsFloating(player) || power.skipsMovementCheck(player))
			|| FlightRoll.flying(player);
	}

	/**
	 * The ledge on the wall straight ahead of this player that they could catch right now, or null. The wall must be
	 * within {@link #REACH} of the body, its top edge no higher than {@link #EDGE_ABOVE_EYES} over the eyes and at least
	 * {@link #EDGE_ABOVE_FEET} over the feet, with a solid enough top ({@link #MIN_TOP_AREA}, not {@link #NO_GRAB}), room
	 * to stand on it and nothing in the way of the climb (the space straight above the player up to standing height on
	 * the top, and the way across onto it).
	 */
	public static @Nullable Spot find(Player player) {
		Direction face = Direction.fromYRot(player.getYRot());
		if (Math.abs(Mth.wrapDegrees(player.getYRot() - face.toYRot())) > GRAB_FACING_DEGREES) {
			return null;
		}
		Level level = player.level();
		double half = player.getBbWidth() * 0.5;
		double eyes = player.getEyeY();
		double feet = player.getY();
		// the column of blocks just ahead of the body
		double aheadX = player.getX() + face.getStepX() * (half + REACH + 0.01);
		double aheadZ = player.getZ() + face.getStepZ() * (half + REACH + 0.01);
		int fromY = Mth.floor(eyes + EDGE_ABOVE_EYES);
		int toY = Mth.floor(feet + EDGE_ABOVE_FEET) - 1;
		CollisionContext context = CollisionContext.of(player);
		for (int y = fromY; y >= toY; y--) {
			BlockPos pos = BlockPos.containing(aheadX, y, aheadZ);
			BlockState state = level.getBlockState(pos);
			VoxelShape shape = state.getCollisionShape(level, pos, context);
			if (shape.isEmpty()) {
				continue;
			}
			// the highest solid block in reach: its top is the edge, or the wall goes on up past it and there's none
			double top = pos.getY() + shape.max(Direction.Axis.Y);
			if (top > eyes + EDGE_ABOVE_EYES || top < feet + EDGE_ABOVE_FEET || state.is(NO_GRAB) || topArea(shape) < MIN_TOP_AREA) {
				return null;
			}
			return spot(player, level, pos, shape, face, top);
		}
		return null;
	}

	private static @Nullable Spot spot(Player player, Level level, BlockPos pos, VoxelShape shape, Direction face, double top) {
		double half = player.getBbWidth() * 0.5;
		// the wall's face toward the player, from the part of the block at the top
		int sign = face.getAxisDirection().getStep();
		double wall = face.getAxis() == Direction.Axis.X
			? pos.getX() + (sign > 0 ? shape.min(Direction.Axis.X) : shape.max(Direction.Axis.X))
			: pos.getZ() + (sign > 0 ? shape.min(Direction.Axis.Z) : shape.max(Direction.Axis.Z));
		double along = face.getAxis() == Direction.Axis.X ? player.getX() : player.getZ();
		double gap = (wall - along) * sign - half;
		if (gap < -0.05 || gap > REACH) {
			return null;
		}
		return finish(player, level, pos, face, top, wall, sign);
		}

		/**
		 * (0.15.12) The ledge hang under the edge of the block at {@code pos} the player is standing on, the way they were
		 * walking ({@code walk}): they turn round and hang from its far edge, facing the wall. Null when the block won't do or
		 * there's no room to hang. Used by the freerunner's sneak off an edge (Climber).
		 */
		public static @Nullable Spot edgeSpot(Player player, BlockPos pos, Direction walk) {
			Level level = player.level();
			BlockState state = level.getBlockState(pos);
			VoxelShape shape = state.getCollisionShape(level, pos, CollisionContext.of(player));
			if (shape.isEmpty() || state.is(NO_GRAB) || topArea(shape) < MIN_TOP_AREA) {
				return null;
			}
			double top = pos.getY() + shape.max(Direction.Axis.Y);
			if (Math.abs(top - player.getY()) > 0.1) {
				return null;
			}
			int sign = walk.getAxisDirection().getStep();
			double wall = walk.getAxis() == Direction.Axis.X
				? pos.getX() + (sign > 0 ? shape.max(Direction.Axis.X) : shape.min(Direction.Axis.X))
				: pos.getZ() + (sign > 0 ? shape.max(Direction.Axis.Z) : shape.min(Direction.Axis.Z));
			return finish(player, level, pos, walk.getOpposite(), top, wall, -sign);
		}

		/**
		 * (0.19.40) The crouch grab's spot: the block straight ahead whose top is {@link #CROUCH_EDGE_MIN} to
		 * {@link #CROUCH_EDGE_MAX} over the feet, within {@link #REACH}, with room above it to lie flat but not to stand.
		 * The "hang" is where the player stands, against the block. Null when there's none.
		 */
		public static @Nullable Spot crouchSpot(Player player) {
			Direction face = Direction.fromYRot(player.getYRot());
			if (Math.abs(Mth.wrapDegrees(player.getYRot() - face.toYRot())) > GRAB_FACING_DEGREES) {
				return null;
			}
			Level level = player.level();
			double half = player.getBbWidth() * 0.5;
			double feet = player.getY();
			double aheadX = player.getX() + face.getStepX() * (half + REACH + 0.01);
			double aheadZ = player.getZ() + face.getStepZ() * (half + REACH + 0.01);
			CollisionContext context = CollisionContext.of(player);
			for (int y = Mth.floor(feet + CROUCH_EDGE_MAX); y >= Mth.floor(feet + CROUCH_EDGE_MIN) - 1; y--) {
				BlockPos pos = BlockPos.containing(aheadX, y, aheadZ);
				BlockState state = level.getBlockState(pos);
				VoxelShape shape = state.getCollisionShape(level, pos, context);
				if (shape.isEmpty()) {
					continue;
				}
				double top = pos.getY() + shape.max(Direction.Axis.Y);
				if (top > feet + CROUCH_EDGE_MAX || top < feet + CROUCH_EDGE_MIN || state.is(NO_GRAB) || topArea(shape) < MIN_TOP_AREA) {
					return null;
				}
				int sign = face.getAxisDirection().getStep();
				double wall = face.getAxis() == Direction.Axis.X
					? pos.getX() + (sign > 0 ? shape.min(Direction.Axis.X) : shape.max(Direction.Axis.X))
					: pos.getZ() + (sign > 0 ? shape.min(Direction.Axis.Z) : shape.max(Direction.Axis.Z));
				double at = face.getAxis() == Direction.Axis.X ? player.getX() : player.getZ();
				double gap = (wall - at) * sign - half;
				if (gap < -0.05 || gap > REACH) {
					return null;
				}
				// stand where you are, up against the block
				Vec3 hang = along(face, player.position(), wall - sign * (half + HANG_GAP), feet);
				AABB box = player.getBoundingBox().move(hang.subtract(player.position())).deflate(1.0E-7);
				if (!level.noCollision(player, box)) {
					return null;
				}
				// room to stand up there: that's a step or a climb, not a crawl
				Vec3 onTop = along(face, hang, wall + sign * CLIMB_IN, top + CLIMB_LIFT);
				AABB standing = player.getDimensions(net.minecraft.world.entity.Pose.STANDING).makeBoundingBox(onTop).deflate(1.0E-7);
				if (level.noCollision(player, standing) && solidUnder(level, onTop, top)) {
					return null;
				}
				// the flat body (lifted so the eyes keep their place, as from a hang) up to the top, then in on the belly
				AABB flat = player.getDimensions(net.minecraft.world.entity.Pose.SWIMMING).makeBoundingBox(hang).deflate(1.0E-7);
				double from = Math.min(hang.y + PRONE_EYE_KEEP, top + CLIMB_LIFT);
				AABB flatRise = flat.move(0.0, from - hang.y, 0.0).expandTowards(0.0, top + CLIMB_LIFT - from, 0.0);
				if (!level.noCollision(player, flatRise)) {
					return null;
				}
				for (double in : new double[] {PRONE_IN, CLIMB_IN}) {
					Vec3 belly = along(face, hang, wall + sign * in, top + CLIMB_LIFT);
					AABB flatAcross = flat.move(0.0, top + CLIMB_LIFT - hang.y, 0.0).expandTowards(belly.x - hang.x, 0.0, belly.z - hang.z);
					if (level.noCollision(player, flatAcross) && solidUnder(level, belly, top)) {
						return new Spot(pos, face, top, hang, belly, false, belly);
					}
				}
				return null;
			}
			return null;
		}

		private static @Nullable Spot finish(Player player, Level level, BlockPos pos, Direction face, double top, double wall, int sign) {
			double half = player.getBbWidth() * 0.5;
			double hangAlong = wall - sign * (half + HANG_GAP);
		double onAlong = wall + sign * CLIMB_IN;
		Vec3 hang = face.getAxis() == Direction.Axis.X ? new Vec3(hangAlong, top - HANG_FEET_BELOW_EDGE, player.getZ())
			: new Vec3(player.getX(), top - HANG_FEET_BELOW_EDGE, hangAlong);
		// short of a full hang over low ground: as low as the body fits, never lower than the ground
		AABB box = player.getDimensions(net.minecraft.world.entity.Pose.STANDING).makeBoundingBox(hang).deflate(1.0E-7);
		for (int i = 0; i < 12 && !level.noCollision(player, box); i++) {
			hang = hang.add(0.0, 0.05, 0.0);
			box = box.move(0.0, 0.05, 0.0);
		}
		if (!level.noCollision(player, box) || hang.y > player.getY() + 0.6) {
			return null;
		}
		Vec3 onTop = along(face, hang, onAlong, top + CLIMB_LIFT);
		// straight up from the hang to standing height over the edge, then across onto the top
		AABB rise = box.expandTowards(0.0, top + CLIMB_LIFT - hang.y, 0.0);
		AABB across = box.move(0.0, top + CLIMB_LIFT - hang.y, 0.0).expandTowards(onTop.x - hang.x, 0.0, onTop.z - hang.z);
		boolean stand = level.noCollision(player, rise) && level.noCollision(player, across) && solidUnder(level, onTop, top);
		// (0.15.108) lying flat: up from where the prone body starts (eyes kept level) to the top, then in on the belly
		Vec3 onBelly = null;
		AABB flat = player.getDimensions(net.minecraft.world.entity.Pose.SWIMMING).makeBoundingBox(hang).deflate(1.0E-7);
		double from = Math.min(hang.y + PRONE_EYE_KEEP, top + CLIMB_LIFT);
		AABB flatRise = flat.move(0.0, from - hang.y, 0.0).expandTowards(0.0, top + CLIMB_LIFT - from, 0.0);
		if (level.noCollision(player, flatRise)) {
			for (double in : new double[] {PRONE_IN, CLIMB_IN}) {
				Vec3 belly = along(face, hang, wall + sign * in, top + CLIMB_LIFT);
				AABB flatAcross = flat.move(0.0, top + CLIMB_LIFT - hang.y, 0.0).expandTowards(belly.x - hang.x, 0.0, belly.z - hang.z);
				if (level.noCollision(player, flatAcross) && solidUnder(level, belly, top)) {
					onBelly = belly;
					break;
				}
			}
		}
		if (!stand && onBelly == null) {
			return null;
		}
		return new Spot(pos, face, top, hang, onTop, stand, onBelly);
	}

	/** {@code hang} moved to {@code at} along the wall's axis, at height {@code y}. */
	private static Vec3 along(Direction face, Vec3 hang, double at, double y) {
		return face.getAxis() == Direction.Axis.X ? new Vec3(at, y, hang.z) : new Vec3(hang.x, y, at);
	}

	/** The top really holds you up at {@code at} (not a sliver of a block under the middle). */
	private static boolean solidUnder(Level level, Vec3 at, double top) {
		BlockPos under = BlockPos.containing(at.x, top - 0.01, at.z);
		return !level.getBlockState(under).getCollisionShape(level, under).isEmpty();
	}

	/** The area of a shape's highest face (square blocks): a full block is 1, a fence post about 0.06. */
	private static double topArea(VoxelShape shape) {
		double top = shape.max(Direction.Axis.Y);
		double area = 0.0;
		for (AABB box : shape.toAabbs()) {
			if (box.maxY > top - 1.0E-4) {
				area += box.getXsize() * box.getZsize();
			}
		}
		return area;
	}

	/** Whether the block at {@code pos} still has something there to hold on to (both sides). */
	public static boolean holdable(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return !state.getCollisionShape(level, pos).isEmpty() && !state.is(NO_GRAB);
	}

	/** Server: a player on a ledge (or just off one) moves however the hang and climb put them, without being kicked. */
	public static boolean relaxesMovement(ServerPlayer player) {
		// (0.14.3) and a freerunner clinging to a wall, hanging from a bar or running along a wall (Freerun)
		if (HOLDING.containsKey(player.getUUID()) || com.jaymospowers.freerun.Freerun.relaxesMovement(player)) {
			return true;
		}
		Long until = GRACE.get(player.getUUID());
		return until != null && player.level().getGameTime() < until;
	}

	// ---- server

	private static void grab(ServerPlayer player, Grab grab) {
		ServerLevel level = player.level();
		BlockPos pos = grab.pos();
		if (grab.face() < 0 || grab.face() > 3 || !canHang(player) || powerMoving(player)
			|| player.position().distanceToSqr(Vec3.atCenterOf(pos)) > 9.0 || !holdable(level, pos)) {
			end(player);
			return;
		}
		HOLDING.put(player.getUUID(), pos.immutable());
		player.setAttached(HOLD, new Hold(grab.face(), level.getGameTime(), false));
		player.resetFallDistance();
		sound(level, player, pos, 0.7F, 0.8F);
	}

	private static void act(ServerPlayer player, boolean climb, boolean prone) {
		BlockPos pos = HOLDING.get(player.getUUID());
		Hold hold = player.getAttached(HOLD);
		if (pos == null || hold == null) {
			return;
		}
		if (!climb) {
			end(player);
			return;
		}
		if (hold.climbing()) {
			return;
		}
		ServerLevel level = player.level();
		if (prone) {
			// (0.15.108) onto the belly into a low gap: the crawl begins with the climb and holds after it
			if (!Crawl.canCrawl(player)) {
				return;
			}
			player.setAttached(Crawl.CRAWLING, true);
		}
		player.setAttached(HOLD, new Hold(hold.face(), level.getGameTime(), true, prone));
		player.causeFoodExhaustion(CLIMB_EXHAUSTION);
		player.resetFallDistance();
		sound(level, player, pos, 0.8F, 0.7F);
	}

	/** A hit takes you off the ledge: the grip goes and you ragdoll as you fall (the hold drops on its next tick, see canHang). */
	private static void knockedOff(ServerPlayer player) {
		player.setAttached(Carry.THROWN, player.level().getGameTime());
		BlockPos pos = HOLDING.get(player.getUUID());
		if (pos != null) {
			sound(player.level(), player, pos, 0.7F, 0.6F);
		}
	}

	private static void end(ServerPlayer player) {
		if (HOLDING.remove(player.getUUID()) != null || player.hasAttached(HOLD)) {
			GRACE.put(player.getUUID(), player.level().getGameTime() + RELEASE_GRACE_TICKS);
		}
		player.removeAttached(HOLD);
	}

	private static void tick(MinecraftServer server) {
		Iterator<Map.Entry<UUID, BlockPos>> it = HOLDING.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, BlockPos> entry = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			Hold hold = player == null ? null : player.getAttached(HOLD);
			if (player == null || hold == null) {
				it.remove();
				continue;
			}
			ServerLevel level = player.level();
			long age = level.getGameTime() - hold.start();
			boolean over = hold.climbing() ? age > hold.climbTicks() + CLIMB_SLACK_TICKS : age > HANG_MAX_TICKS + CONFIRM_TICKS;
			if (over || !canHang(player) || !hold.climbing() && !holdable(level, entry.getValue())) {
				it.remove();
				GRACE.put(player.getUUID(), level.getGameTime() + RELEASE_GRACE_TICKS);
				player.removeAttached(HOLD);
				continue;
			}
			player.resetFallDistance();
			if (hold.climbing() && age == Math.round(hold.climbTicks() * (hold.prone() ? PRONE_RISE_SHARE : CLIMB_RISE_SHARE))) {
				// the knee goes onto the edge
				sound(level, player, entry.getValue(), 0.5F, 1.0F);
			}
		}
		if (!GRACE.isEmpty() && server.getTickCount() % 100 == 0) {
			long now = server.overworld().getGameTime();
			GRACE.values().removeIf(until -> until < now);
		}
	}

	/** The held block's own sound, as the hands take it or the knee goes on. */
	private static void sound(ServerLevel level, ServerPlayer player, BlockPos pos, float volume, float pitch) {
		SoundType sound = level.getBlockState(pos).getSoundType();
		level.playSound(null, player.getX(), player.getY() + 1.6, player.getZ(), sound.getHitSound(), SoundSource.PLAYERS,
			sound.getVolume() * volume, sound.getPitch() * pitch);
	}
}
