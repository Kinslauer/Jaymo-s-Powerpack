package com.jaymoskingdoms.sailing;

import com.jaymoskingdoms.JaymosKingdoms;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LinearInterpolationHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.7) A ship built from blocks, under sail. Every block joined to a {@link HelmBlock} leaves the world and sails
 * as one piece: drawn block for block, floating as deep as its weight against the water its hull keeps out ({@link Hull}),
 * driven by its sails and steered from the helm (W/S set the sails, A/D the rudder). You can walk its decks while it
 * sails ({@link Ships#collisions}), open the chests in its hold, and sneak at the helm to drop anchor: it swings to the
 * nearest straight heading and becomes blocks again.
 *
 * <p>Its blocks are kept in its own frame: x across (to port, the ship's left, is +x), y up from the keel, z along it
 * (forward is +z), the column amidships (halfway along its length and beam) at x = z = 0, about which it turns. The
 * entity's position is that column's middle at the keel.
 */
public final class Ship extends Entity {
	/** The most blocks one ship may hold ((0.7.112) 5000, was 3000: room for Jaki's galleon), and its longest side. */
	static final int MOST_BLOCKS = 5000, LONGEST = 64;
	/** Top speed (blocks/tick): per block of waterline, per (square root of) sail, its paddling speed with no sails, and the most. */
	static final double PER_LENGTH = 0.04, PER_SAIL = 0.1, PADDLE = 0.05, FASTEST = 0.8;
	/** How quickly it gathers or loses way (a share of the gap a tick), and its sails set or furled (a share a tick). */
	static final double GATHER = 0.015, SAIL_RATE = 0.02;
	/** Its best turn (degrees/tick), for a ship this long or shorter; longer ones turn slower. */
	static final float TURN = 2.5F, NIMBLE_LENGTH = 12.0F;
	/**
	 * (0.7.260) Warped off (a crew's captain, lying stuck: her boats out with a kedge, hauling her astern): how fast she
	 * goes, and how quickly she gathers that way.
	 */
	static final double KEDGE = 0.09, KEDGE_GATHER = 0.08;
	/** (0.7.285) The least a crew at her sweeps rows her (blocks a tick), however heavy she is. */
	static final double CREW_SWEEPS = 0.07;
	/** How far (blocks) it may be nudged to find room to drop anchor. */
	static final int NUDGE = 2;
	/** Slower than this (blocks/tick) it lies still enough to drop anchor. */
	static final double STILL = 0.05;
	private static final Rotation[] QUARTERS = {Rotation.NONE, Rotation.CLOCKWISE_90, Rotation.CLOCKWISE_180, Rotation.COUNTERCLOCKWISE_90};
	/** How fast it settles to its waterline (share of the gap a tick, and the most a tick). */
	static final double SETTLE = 0.12, SETTLE_MOST = 0.08;
	/** With no one aboard for this long (ticks), it anchors itself. */
	static final int ADRIFT = 100;
	/** How near a player must be to use a block of it. */
	static final double REACH = 7.0;
	/**
	 * (0.7.18) Rowing: her speed per (square root of) oar, held back by her weight per this much, the fastest oars take
	 * her, backing water's share of that, how long a stroke takes (ticks), and what it takes out of the one rowing (food
	 * exhaustion a tick).
	 */
	static final double ROW_PER_OAR = 0.12, ROW_MASS = 20.0, ROW_FASTEST = 0.22, ROW_BACK = 0.5;
	public static final int STROKE = 30;
	static final float ROW_TIRE = 0.008F;
	/**
	 * (0.7.18) Holed by round shot: how far round where it strikes her blocks are smashed out, the share of those that
	 * wash up as items, how fast she goes down once she can't float (blocks a tick), and the longest she takes to reach
	 * the bottom (ticks) before she's laid there as a wreck.
	 */
	static final double HOLE = 1.3, SINK = 0.03;
	static final float SALVAGE = 0.25F;
	static final int SINKING_MOST = 600;
	/** (0.7.18) A broadside fires the guns pointing within this of where you look (a cosine), this many ticks apart. */
	static final double BROADSIDE_ARC = 0.5;
	/** (0.7.28) How often (ticks) its torches and lanterns move their light along with it. */
	static final int LIGHT_EVERY = 2;
	static final int BROADSIDE_GAP = 3;

	/** One block of the ship, in its own frame. */
	public record Piece(int state, int x, int y, int z) {
		public static final StreamCodec<ByteBuf, Piece> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Piece::state,
			ByteBufCodecs.VAR_INT, Piece::x, ByteBufCodecs.VAR_INT, Piece::y, ByteBufCodecs.VAR_INT, Piece::z, Piece::new);

		public BlockState blockState() {
			return Block.stateById(this.state);
		}
	}

	public static final AttachmentType<List<Piece>> PIECES = AttachmentRegistry.create(JaymosKingdoms.id("ship_pieces"),
		builder -> builder.syncWith(Piece.STREAM_CODEC.apply(ByteBufCodecs.list()), AttachmentSyncPredicate.all()));
	/**
	 * (0.7.42) Where one of a ship's crew stands aboard her, in her frame (set on the crewman, kept and sent to everyone):
	 * crewmen ride the ship, each at their station, so they stay at their guns and the helm wherever she sails.
	 */
	public static final AttachmentType<Vec3> SEAT = AttachmentRegistry.create(JaymosKingdoms.id("ship_seat"),
		builder -> builder.persistent(Vec3.CODEC).syncWith(Vec3.STREAM_CODEC, AttachmentSyncPredicate.all()));
	/** (0.7.42) The most crew aboard one ship. */
	public static final int MOST_CREW = 24;

	/**
	 * (0.7.42) What sails a crewed ship ({@link Crew}) when no player is at her wheel: Jaymo's Kingdoms' pirates, navies and
	 * merchantmen ({@code com.jaymoskingdoms.Corsairs}). Called on the server only.
	 */
	public interface Captaincy {
		/** Her helm this tick (as a player's keys would set it), or null if nobody is steering her (her captain is gone). */
		@Nullable Input helm(ServerLevel level, Ship ship, Crew crew);

		/** She's going down, holed by {@code by} (her crew go down with her). */
		void foundering(ServerLevel level, Ship ship, Crew crew, @Nullable Entity by);

		/** {@code player} opens her hold: per player, what's theirs to take (null: it isn't theirs yet, and they're told why). */
		@Nullable Container hold(ServerLevel level, Ship ship, Crew crew, ServerPlayer player);
	}

	public static @Nullable Captaincy captaincy;

	public static EntityType<Ship> TYPE;
	/** The client's part each tick (carrying you with the deck), set by the client. */
	public static Consumer<Ship> clientTick = ship -> {
	};

	private @Nullable Hull hull;
	private @Nullable List<Piece> hullOf;
	/** Server: how far its sails are set (0 furled to 1 full), its way (blocks/tick) and its turn (degrees/tick). */
	private double sail, speed;
	private static final EntityDataAccessor<Float> SET = SynchedEntityData.defineId(Ship.class, EntityDataSerializers.FLOAT);
	/** (0.7.18) Whether she's rowing: 1 ahead, -1 astern, 0 not. */
	private static final EntityDataAccessor<Byte> ROWING = SynchedEntityData.defineId(Ship.class, EntityDataSerializers.BYTE);
	/** (0.7.18) Whether she's going down (her hold's flooded). */
	private static final EntityDataAccessor<Boolean> FOUNDERING = SynchedEntityData.defineId(Ship.class, EntityDataSerializers.BOOLEAN);
	/** (0.7.18) Server: ticks since she began to go down (0: afloat), and the guns of a broadside still to fire (piece, tick). */
	private int sinking;
	private final List<int[]> volley = new ArrayList<>();
	private float turn;
	private int adrift;
	/** (0.7.260) Server: ticks still to warp her off astern ({@link #kedge}). */
	private int kedging;
	/**
	 * (0.7.28) Server: where its torches and lanterns light the world as it sails, and how brightly. The game doesn't light
	 * the world from an entity, so each one aboard leaves an invisible light block in the world cell it's passing
	 * through, moved as the ship moves and taken up when it anchors, sinks or unloads.
	 */
	private final it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap lit = new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap();
	/** Server: the block entities aboard (chests and the like), by piece. */
	private final Map<Integer, BlockEntity> cargo = new HashMap<>();
	private final Map<Integer, CompoundTag> cargoTags = new HashMap<>();
	private boolean registered;
	/** (0.7.42) Her crew, if she has one (a pirate, a navy's warship, a merchantman); null for a ship players built and sail. */
	public @Nullable Crew crew;
	/** (0.7.42) Server: who last holed her, and how fast she's moving over the ground (blocks a tick, x and z). */
	private java.util.@Nullable UUID holedBy;
	private Vec3 drift = Vec3.ZERO;
	/** (0.7.206) Pulls on her from outside (a harpoon line to a whale: whaling), taken up at her next move (blocks this tick). */
	private Vec3 towed = Vec3.ZERO;
	/** Client: how far it heels over in its turns (degrees, now and last tick). */
	public float heel, heelO;

	public Ship(EntityType<? extends Ship> type, Level level) {
		super(type, level);
		this.noPhysics = true;
	}

	static void register() {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id("ship"));
		TYPE = Registry.register(BuiltInRegistries.ENTITY_TYPE, key, EntityType.Builder.<Ship>of(Ship::new, MobCategory.MISC)
			.noLootTable()
			.sized(1.0F, 1.0F)
			.clientTrackingRange(12)
			.updateInterval(1)
			.build(key));
	}

	// ---- its frame ----

	/** Its blocks, worked out (null while it has none). */
	public @Nullable Hull hull() {
		List<Piece> pieces = this.getAttached(PIECES);
		if (pieces == null || pieces.isEmpty()) {
			return null;
		}
		if (this.hull == null || this.hullOf != pieces) {
			this.hull = new Hull(pieces);
			this.hullOf = pieces;
		}
		return this.hull;
	}

	/** A point in its frame, in the world, with it at {@code at} turned {@code yaw}. */
	public static Vec3 toWorld(double x, double y, double z, Vec3 at, float yaw) {
		float c = Mth.cos(yaw * Mth.DEG_TO_RAD), s = Mth.sin(yaw * Mth.DEG_TO_RAD);
		return new Vec3(at.x + x * c - z * s, at.y + y, at.z + x * s + z * c);
	}

	/** A point in the world, in its frame, with it at {@code at} turned {@code yaw}. */
	public static Vec3 toLocal(double wx, double wy, double wz, Vec3 at, float yaw) {
		float c = Mth.cos(yaw * Mth.DEG_TO_RAD), s = Mth.sin(yaw * Mth.DEG_TO_RAD);
		double dx = wx - at.x, dz = wz - at.z;
		return new Vec3(dx * c + dz * s, wy - at.y, -dx * s + dz * c);
	}

	public Vec3 toWorld(double x, double y, double z) {
		return toWorld(x, y, z, this.position(), this.getYRot());
	}

	public Vec3 toLocal(Vec3 world) {
		return toLocal(world.x, world.y, world.z, this.position(), this.getYRot());
	}

	/** Whether a point (in its frame) is aboard: over its decks, up to a little above its highest block. */
	public boolean aboard(Vec3 local, double margin) {
		Hull h = this.hull();
		return h != null && local.x > h.minX - 0.5 - margin && local.x < h.maxX + 0.5 + margin && local.z > h.minZ - 0.5 - margin
			&& local.z < h.maxZ + 0.5 + margin && local.y > h.minY - 0.5 && local.y < h.maxY + 3.0;
	}

	@Override
	protected AABB makeBoundingBox(Vec3 pos) {
		Hull h = this.hull;
		if (h == null) {
			return super.makeBoundingBox(pos);
		}
		return new AABB(pos.x - h.radius, pos.y + h.minY, pos.z - h.radius, pos.x + h.radius, pos.y + h.maxY + 1.5, pos.z + h.radius);
	}

	/**
	 * (lag fix) The vanilla fluid check reads every block in the bounding box each tick, and a ship's box is her whole
	 * hull square (a cargo ship's is tens of thousands of blocks): a few moored ships took an eighth of the server's
	 * tick. A ship keeps her own waterline, so she skips it.
	 */
	@Override
	protected boolean updateFluidInteraction() {
		return false;
	}

	@Override
	public boolean isPushedByFluid() {
		return false;
	}

	@Override
	protected InterpolationHandler createInterpolationHandler() {
		return LinearInterpolationHandler.create(this, 3);
	}

	// ---- casting off ----

	/**
	 * {@code player} casts off the hull whose helm is at {@code helm}: every block joined to it leaves the world and sails
	 * as a ship, with them at the wheel. Tells them why if it can't.
	 */
	static void castOff(ServerLevel level, BlockPos helm, ServerPlayer player) {
		castOff(level, helm, player, null);
	}

	/**
	 * (0.7.42) Casts off the hull whose helm is at {@code helm}, with {@code player} at the wheel, or with nobody (a crewed
	 * ship: {@code crew}, launched by {@link Fleet}). The ship, or null (and {@code player} is told why) if it can't.
	 */
	static @Nullable Ship castOff(ServerLevel level, BlockPos helm, @Nullable ServerPlayer player, @Nullable Crew crew) {
		BlockState helmState = level.getBlockState(helm);
		if (!(helmState.getBlock() instanceof HelmBlock)) {
			return null;
		}
		// every block joined to the helm (water and loose plants are where the hull ends)
		List<BlockPos> found = new ArrayList<>();
		LongOpenHashSet seen = new LongOpenHashSet();
		ArrayDeque<BlockPos> open = new ArrayDeque<>();
		open.add(helm);
		seen.add(helm.asLong());
		int x0 = helm.getX(), x1 = x0, y0 = helm.getY(), y1 = y0, z0 = helm.getZ(), z1 = z0;
		while (!open.isEmpty()) {
			BlockPos p = open.poll();
			found.add(p);
			if (found.size() > MOST_BLOCKS) {
				tell(player, Component.translatable("ship.jaymoskingdoms.too_big", MOST_BLOCKS));
				return null;
			}
			x0 = Math.min(x0, p.getX());
			x1 = Math.max(x1, p.getX());
			y0 = Math.min(y0, p.getY());
			y1 = Math.max(y1, p.getY());
			z0 = Math.min(z0, p.getZ());
			z1 = Math.max(z1, p.getZ());
			if (x1 - x0 >= LONGEST || y1 - y0 >= LONGEST || z1 - z0 >= LONGEST) {
				tell(player, Component.translatable("ship.jaymoskingdoms.too_long", LONGEST));
				return null;
			}
			for (Direction d : Direction.values()) {
				BlockPos q = p.relative(d);
				if (seen.add(q.asLong()) && part(level.getBlockState(q))) {
					if (level.getBlockState(q).getBlock() instanceof ShipFrameBlock) {
						tell(player, Component.translatable("ship.jaymoskingdoms.finish_frame"));
						return null;
					}
					if (level.getBlockState(q).getDestroySpeed(level, q) < 0.0F) {
						tell(player, Component.translatable("ship.jaymoskingdoms.land"));
						return null;
					}
					open.add(q);
				}
			}
		}
		// it must float on water: water under some of its keel, and the sea's level beside it
		int sea = Integer.MIN_VALUE;
		boolean wetKeel = false;
		LongOpenHashSet mine = new LongOpenHashSet();
		for (BlockPos p : found) {
			mine.add(p.asLong());
		}
		for (BlockPos p : found) {
			if (!mine.contains(p.below().asLong()) && level.getFluidState(p.below()).is(FluidTags.WATER)) {
				wetKeel = true;
			}
			for (Direction d : Direction.Plane.HORIZONTAL) {
				BlockPos q = p.relative(d);
				if (!mine.contains(q.asLong()) && level.getFluidState(q).is(FluidTags.WATER)) {
					sea = Math.max(sea, q.getY());
				}
			}
		}
		if (!wetKeel || sea == Integer.MIN_VALUE) {
			tell(player, Component.translatable("ship.jaymoskingdoms.not_afloat"));
			return null;
		}
		// into its own frame: the helm's facing becomes forward (+z)
		Direction facing = helmState.getValue(HelmBlock.FACING);
		Rotation turn = Rotation.NONE;
		for (Rotation r : Rotation.values()) {
			if (r.rotate(facing) == Direction.SOUTH) {
				turn = r;
			}
		}
		int keel = y0 - helm.getY();
		// it turns about its middle: the frame's origin is the middle of its length and beam
		Rotation back = turn.getRotated(turn).getRotated(turn);
		int ax = Integer.MAX_VALUE, bx = Integer.MIN_VALUE, az = Integer.MAX_VALUE, bz = Integer.MIN_VALUE;
		for (BlockPos p : found) {
			BlockPos local = p.subtract(helm).rotate(turn);
			ax = Math.min(ax, local.getX());
			bx = Math.max(bx, local.getX());
			az = Math.min(az, local.getZ());
			bz = Math.max(bz, local.getZ());
		}
		BlockPos middle = new BlockPos(Math.floorDiv(ax + bx, 2), 0, Math.floorDiv(az + bz, 2));
		BlockPos origin = helm.offset(middle.rotate(back));
		List<Piece> pieces = new ArrayList<>(found.size());
		Map<Integer, CompoundTag> tags = new HashMap<>();
		for (BlockPos p : found) {
			BlockState state = level.getBlockState(p);
			if (state.hasProperty(BlockStateProperties.WATERLOGGED)) {
				state = state.setValue(BlockStateProperties.WATERLOGGED, false);
			}
			BlockPos local = p.subtract(helm).rotate(turn).subtract(middle);
			BlockEntity be = level.getBlockEntity(p);
			if (be != null) {
				tags.put(pieces.size(), be.saveWithFullMetadata(level.registryAccess()));
			}
			state = state.rotate(turn);
			if (state.getBlock() instanceof SailBlock) {
				// sails belly forward, with the wind
				state = SailBlock.toward(state, Direction.SOUTH);
			}
			pieces.add(new Piece(Block.getId(state), local.getX(), local.getY() - keel, local.getZ()));
		}
		Hull hull = new Hull(List.copyOf(pieces));
		if (!hull.floats) {
			tell(player, Component.translatable("ship.jaymoskingdoms.too_heavy"));
			return null;
		}
		Ship ship = new Ship(TYPE, level);
		ship.setAttached(PIECES, hull.pieces);
		ship.hull();
		ship.cargoTags.putAll(tags);
		ship.loadCargo(level);
		ship.snapTo(origin.getX() + 0.5, origin.getY() + keel, origin.getZ() + 0.5, facing.toYRot(), 0.0F);
		ship.crew = crew;
		// the blocks leave the world: water fills in below the sea's level, air above (the dry hold too)
		BlockState water = Blocks.WATER.defaultBlockState(), air = Blocks.AIR.defaultBlockState();
		int quiet = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS;
		List<BlockPos> order = new ArrayList<>(found);
		order.sort((a, b) -> Boolean.compare(level.getBlockState(a).isCollisionShapeFullBlock(level, a), level.getBlockState(b).isCollisionShapeFullBlock(level, b)));
		for (BlockPos p : order) {
			level.setBlock(p, p.getY() <= sea ? water : air, quiet);
		}
		for (long cell : hull.enclosed) {
			BlockPos p = origin.offset(BlockPos.of(cell).offset(0, keel, 0).rotate(back));
			if (p.getY() <= sea && level.getBlockState(p).isAir()) {
				level.setBlock(p, water, quiet);
			}
		}
		level.addFreshEntity(ship);
		if (player != null) {
			player.startRiding(ship);
			level.playSound(null, helm, SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 1.0F, 0.6F);
			level.playSound(null, helm, SoundEvents.BOAT_PADDLE_WATER, SoundSource.BLOCKS, 1.4F, 0.7F);
		}
		return ship;
	}

	private static void tell(@Nullable ServerPlayer player, Component message) {
		if (player != null) {
			player.sendOverlayMessage(message);
		}
	}

	/** Whether a block found joined to a helm is part of the ship: anything solid enough, not the sea and its weeds. */
	static boolean part(BlockState state) {
		if (state.isAir() || state.canBeReplaced()) {
			return false;
		}
		FluidState fluid = state.getFluidState();
		return fluid.isEmpty() || state.hasProperty(BlockStateProperties.WATERLOGGED);
	}

	// ---- under way ----

	@Override
	public void tick() {
		if (!this.registered) {
			Ships.add(this);
			this.registered = true;
		}
		super.tick();
		Hull h = this.hull();
		if (this.level().isClientSide()) {
			if (h != null) {
				clientTick.accept(this);
			}
			return;
		}
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		if (h == null) {
			this.discard();
			return;
		}
		if (this.sinking > 0) {
			this.goDown(level, h);
			return;
		}
		Vec3 from = this.position();
		float yawFrom = this.getYRot();
		ServerPlayer pilot = this.pilot();
		// (0.7.42) a crewed ship with nobody at her wheel is sailed by her captain, if she still has one
		Input steered = pilot == null && this.crew != null && captaincy != null ? captaincy.helm(level, this, this.crew) : null;
		Input input = pilot != null ? pilot.getLastClientInput() : steered != null ? steered : Input.EMPTY;
		boolean helmed = pilot != null || steered != null;
		boolean sails = h.sails > 0, oars = h.oars > 0;
		// the sails (W/S), the oars (W/S with no sails; Space with them, S astern once they're furled) and the rudder
		int row = 0;
		if (sails || !oars) {
			if (input.forward()) {
				this.sail = Math.min(1.0, this.sail + SAIL_RATE);
			} else if (input.backward()) {
				this.sail = Math.max(0.0, this.sail - SAIL_RATE);
			}
			if (oars && input.jump()) {
				row = 1;
			} else if (oars && input.backward() && this.sail <= 0.0) {
				row = -1;
			}
		} else {
			this.sail = 0.0;
			row = input.forward() ? 1 : input.backward() ? -1 : 0;
		}
		if (!helmed) {
			this.sail = Math.max(0.0, this.sail - SAIL_RATE);
			row = 0;
		}
		this.entityData.set(SET, (float)this.sail);
		this.entityData.set(ROWING, (byte)row);
		double top = this.topSpeed(h), rowTop = this.rowSpeed(h);
		if (steered != null && oars) {
			// (0.7.285) a whole crew at her sweeps moves even a big ship
			rowTop = Math.max(rowTop, CREW_SWEEPS);
		}
		double wanted = this.sail * top;
		if (row > 0) {
			wanted = Math.max(wanted, rowTop);
		} else if (row < 0) {
			wanted = -rowTop * ROW_BACK;
		} else if (!oars && this.sail <= 0.0 && input.backward()) {
			// with the sails furled and no oars, backing water (S) takes it slowly astern
			wanted = -PADDLE;
		}
		if (row != 0 && pilot != null) {
			pilot.causeFoodExhaustion(ROW_TIRE);
			if (this.tickCount % STROKE == 0) {
				level.playSound(null, this.getX(), this.getY() + h.draft, this.getZ(), SoundEvents.BOAT_PADDLE_WATER, SoundSource.NEUTRAL, 1.0F,
					0.8F + this.random.nextFloat() * 0.2F);
			}
		}
		// (0.7.18) a Wind user's gale or a Smoke user's jet in her sails
		double blown = Jets.blown(level, this, h, this.sail);
		if (blown != 0.0) {
			wanted = Mth.clamp(wanted + blown, -FASTEST, FASTEST);
		}
		this.fireVolley(level, h);
		double gather = GATHER;
		if (this.kedging > 0) {
			this.kedging--;
			if (pilot == null) {
				// her sails struck and her crew hauling her off astern on a kedge
				this.sail = 0.0;
				wanted = -KEDGE;
				gather = KEDGE_GATHER;
			}
		}
		this.speed += (wanted - this.speed) * gather;
		if (Math.abs(this.speed) < 1.0E-4) {
			this.speed = 0.0;
		}
		float steer = input.left() == input.right() ? 0.0F : input.left() ? -1.0F : 1.0F;
		// (0.7.285) under oars (or warped off) she's pulled round as hard as she'd turn under way, slow as she's going
		float best = TURN * Math.min(1.0F, NIMBLE_LENGTH / Math.max(NIMBLE_LENGTH, h.waterline))
			* (row != 0 || this.kedging > 0 && pilot == null ? 1.0F : 0.3F + 0.7F * (float)Math.min(1.0, Math.abs(this.speed) / 0.15));
		this.turn += (steer * best - this.turn) * 0.2F;
		float yaw = yawFrom + this.turn;
		double y = this.settle(level, h, from);
		Vec3 ahead = new Vec3(-Mth.sin(yaw * Mth.DEG_TO_RAD), 0.0, Mth.cos(yaw * Mth.DEG_TO_RAD)).scale(this.speed);
		Vec3 to = new Vec3(from.x + ahead.x + this.towed.x, y, from.z + ahead.z + this.towed.z);
		this.towed = Vec3.ZERO;
		if (this.strikes(level, h, to, yaw, 0)) {
			// (0.7.260) lying with some of her in the ground already, any move that drives her no further in is allowed:
			// before, a ship run up a beach (or wedged where the shore was built up round her) could never come off it
			int wedged = this.hits(level, h, from, yawFrom);
			// try it without the turn, then without the way, then hold still
			boolean struck = this.strikes(level, h, to, yaw, wedged);
			if (struck && this.speed > 0.15) {
				level.playSound(null, this.getX(), this.getY() + 1.0, this.getZ(), SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.BLOCKS, 1.0F, 0.6F);
			}
			if (!struck) {
				// (she's coming off)
			} else if (!this.strikes(level, h, to, yawFrom, wedged)) {
				yaw = yawFrom;
				this.turn = 0.0F;
			} else if (!this.strikes(level, h, new Vec3(from.x, y, from.z), yaw, wedged)) {
				to = new Vec3(from.x, y, from.z);
				this.speed = 0.0;
			} else {
				to = from;
				yaw = yawFrom;
				this.speed = 0.0;
				this.turn = 0.0F;
			}
		}
		this.setYRot(yaw);
		this.setPos(to);
		this.drift = to.subtract(from);
		this.carry(level, h, from, yawFrom);
		this.shine(level, h);
		// the wake and the creak of her timbers
		if (this.speed > 0.1 && this.tickCount % 4 == 0) {
			Vec3 bow = this.toWorld(0.0, h.draft, h.maxZ + 0.5);
			level.sendParticles(net.minecraft.core.particles.ParticleTypes.SPLASH, bow.x, bow.y, bow.z, 6, 0.6, 0.1, 0.6, 0.1);
		}
		if (this.tickCount % 90 == 0 && this.speed > 0.05) {
			level.playSound(null, this.getX(), this.getY() + h.draft, this.getZ(), SoundEvents.WOOD_STEP, SoundSource.BLOCKS, 0.6F, 0.5F);
		}
		if (pilot != null && this.tickCount % 10 == 0) {
			pilot.sendOverlayMessage(this.readout(level, h, row, sails ? top : oars ? rowTop : PADDLE));
		}
		// with no one aboard a while, it anchors itself (not a crewed ship under way: her captain keeps her at sea)
		boolean manned = helmed;
		for (Player p : level.players()) {
			manned |= this.aboard(this.toLocal(p.position()), 1.0);
		}
		this.adrift = manned ? 0 : this.adrift + 1;
		if (this.adrift > ADRIFT && this.adrift % 40 == 1) {
			this.anchor(null);
		}
	}

	/**
	 * Its top speed under sail (blocks/tick): its sails' push, as much as they catch of the wind on her heading and as
	 * hard as it blows ({@link Winds}), or its length, whichever is less (or paddling, with no sails).
	 */
	double topSpeed(Hull h) {
		if (h.sails == 0) {
			return PADDLE;
		}
		double push = PER_SAIL * Math.sqrt(h.sails) * Winds.catches(this.getYRot()) * Winds.strength(this.level(), this.getX(), this.getZ());
		return Math.min(FASTEST, Math.min(PER_LENGTH * h.waterline, push));
	}

	/** (0.7.18) Its top speed under oars (blocks/tick): more oars row faster, a heavier ship slower (none: no oars). */
	double rowSpeed(Hull h) {
		if (h.oars == 0) {
			return 0.0;
		}
		double row = ROW_PER_OAR * Math.sqrt(h.oars) / Math.sqrt(Math.max(1.0, h.mass / ROW_MASS));
		return Math.min(ROW_FASTEST, Math.min(PER_LENGTH * Math.max(4, h.waterline), row));
	}

	/** (0.7.18) What the one at the helm sees: sails, oars, speed and the wind, or (lying still, every few seconds) the keys. */
	private Component readout(ServerLevel level, Hull h, int row, double top) {
		net.minecraft.network.chat.MutableComponent line = Component.empty();
		String dot = "  ·  ";
		// lying still, the keys take turns with the rest
		if (Math.abs(this.speed) < STILL && this.tickCount / 60 % 2 == 1) {
			String keys = h.sails > 0 ? h.oars > 0 ? "sails_oars" : "sails" : h.oars > 0 ? "oars" : "paddle";
			line.append(Component.translatable("ship.jaymoskingdoms.keys." + keys));
			if (h.cannons.length > 0) {
				line.append(dot).append(Component.translatable("ship.jaymoskingdoms.keys.broadside"));
			}
			return line;
		}
		if (h.sails > 0) {
			line.append(Component.translatable("ship.jaymoskingdoms.hud.sails", Mth.floor(this.sail * 100.0))).append(dot);
		}
		if (row != 0) {
			line.append(Component.translatable(row > 0 ? "ship.jaymoskingdoms.hud.rowing" : "ship.jaymoskingdoms.hud.backing")).append(dot);
		}
		line.append(Component.translatable("ship.jaymoskingdoms.hud.speed", String.format("%.1f", this.speed * 20.0), String.format("%.1f", top * 20.0)));
		if (h.sails > 0) {
			float strength = Winds.strength(level, this.getX(), this.getZ());
			line.append(dot).append(Component.translatable("ship.jaymoskingdoms.hud.wind", Winds.word(strength), Winds.from(),
				Winds.point(this.getYRot()).label()));
		}
		return line;
	}

	/** Its keel's height next tick: easing toward its waterline on the water under it (where it is, if there's none). */
	private double settle(ServerLevel level, Hull h, Vec3 at) {
		double surface = Double.NEGATIVE_INFINITY;
		for (double z : new double[] {0.0, h.minZ + 0.5, h.maxZ - 0.5}) {
			Vec3 w = this.toWorld(0.0, 0.0, z);
			surface = Math.max(surface, surface(level, w.x, at.y + h.draft + 3.0, w.z));
		}
		if (surface == Double.NEGATIVE_INFINITY) {
			return at.y;
		}
		double target = surface - h.draft - h.minY;
		return at.y + Mth.clamp((target - at.y) * SETTLE, -SETTLE_MOST, SETTLE_MOST);
	}

	/** The water's surface under (x, z), looking down from {@code fromY} a few blocks (-infinity: none). */
	static double surface(Level level, double x, double fromY, double z) {
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(Mth.floor(x), Mth.floor(fromY), Mth.floor(z));
		for (int i = 0; i < 10; i++, p.move(Direction.DOWN)) {
			if (!level.hasChunkAt(p)) {
				return Double.NEGATIVE_INFINITY;
			}
			FluidState fluid = level.getFluidState(p);
			if (fluid.is(FluidTags.WATER)) {
				return p.getY() + fluid.getHeight(level, p);
			}
			if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()) {
				return Double.NEGATIVE_INFINITY;
			}
		}
		return Double.NEGATIVE_INFINITY;
	}

	/**
	 * Whether, at {@code at} turned {@code yaw}, more than {@code wedged} of its outside runs into something solid (or
	 * unloaded).
	 */
	private boolean strikes(ServerLevel level, Hull h, Vec3 at, float yaw, int wedged) {
		// (partly in unloaded ground she holds still, as ever)
		return wedged == Integer.MAX_VALUE || this.hits(level, h, at, yaw, wedged + 1) > wedged;
	}

	/** (0.7.260) How much of its outside, at {@code at} turned {@code yaw}, is in something solid (or unloaded). */
	private int hits(ServerLevel level, Hull h, Vec3 at, float yaw) {
		return this.hits(level, h, at, yaw, Integer.MAX_VALUE);
	}

	/** {@link #hits}, counting no further than {@code most}. */
	private int hits(ServerLevel level, Hull h, Vec3 at, float yaw, int most) {
		int n = 0;
		BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
		for (int i : h.skin) {
			Piece p = h.pieces.get(i);
			Vec3 w = toWorld(p.x(), p.y() + (h.lo[i] + Math.min(1.0F, h.hi[i])) * 0.5, p.z(), at, yaw);
			q.set(Mth.floor(w.x), Mth.floor(w.y), Mth.floor(w.z));
			if (!level.hasChunkAt(q)) {
				// (never into the unloaded)
				return Integer.MAX_VALUE;
			}
			BlockState there = level.getBlockState(q);
			if (!there.isAir() && !there.getCollisionShape(level, q).isEmpty() && ++n >= most) {
				return n;
			}
		}
		return n;
	}

	/** Everything (but players, whose own games move them) standing on it or in it goes along with it. */
	private void carry(ServerLevel level, Hull h, Vec3 from, float yawFrom) {
		float yaw = this.getYRot();
		if (from.equals(this.position()) && yaw == yawFrom) {
			return;
		}
		AABB around = this.getBoundingBox().inflate(1.0);
		for (Entity e : level.getEntities(this, around, e -> !(e instanceof Player) && !(e instanceof Ship) && !e.isPassenger() && !e.noPhysics)) {
			Vec3 local = toLocal(e.getX(), e.getY(), e.getZ(), from, yawFrom);
			if (!this.aboard(local, 0.3)) {
				continue;
			}
			Vec3 now = toWorld(local.x, local.y, local.z, this.position(), yaw);
			e.setPos(now);
			e.setYRot(e.getYRot() + yaw - yawFrom);
			if (e instanceof LivingEntity living) {
				living.setYHeadRot(living.getYHeadRot() + yaw - yawFrom);
				living.yBodyRot += yaw - yawFrom;
			}
		}
	}

	// ---- the helm ----

	/** (0.7.42) The player at her wheel (null: none; a crewed ship's captain steers her without being one). */
	public @Nullable ServerPlayer pilot() {
		for (Entity e : this.getPassengers()) {
			if (e instanceof ServerPlayer p) {
				return p;
			}
		}
		return null;
	}

	/** (0.7.42) The player at her wheel, on either side. */
	public @Nullable Player helmsman() {
		for (Entity e : this.getPassengers()) {
			if (e instanceof Player p) {
				return p;
			}
		}
		return null;
	}

	/** (0.7.42) Her crew aboard (riding her at their stations). */
	public List<LivingEntity> crewAboard() {
		List<LivingEntity> out = new ArrayList<>();
		for (Entity e : this.getPassengers()) {
			if (!(e instanceof Player) && e instanceof LivingEntity living && living.isAlive()) {
				out.add(living);
			}
		}
		return out;
	}

	/** (0.7.42) Takes {@code crewman} aboard at {@code seat} (in her frame). */
	public boolean takeAboard(LivingEntity crewman, Vec3 seat) {
		crewman.setAttached(SEAT, seat);
		Vec3 at = this.toWorld(seat.x, seat.y, seat.z);
		crewman.snapTo(at.x, at.y, at.z, this.getYRot(), 0.0F);
		if (!crewman.startRiding(this, true, false)) {
			crewman.removeAttached(SEAT);
			return false;
		}
		return true;
	}

	/** Where the one at the wheel stands: behind the helm, on its deck. */
	public Vec3 helmStand() {
		Hull h = this.hull();
		if (h == null || h.helm < 0) {
			return this.position();
		}
		Piece p = h.pieces.get(h.helm);
		return this.toWorld(p.x(), p.y(), p.z() - 1.0);
	}

	@Override
	protected void positionRider(Entity passenger, Entity.MoveFunction moveFunction) {
		// (0.7.42) her crew at their stations
		Vec3 seat = passenger instanceof Player ? null : passenger.getAttached(SEAT);
		Vec3 at = seat != null ? this.toWorld(seat.x, seat.y, seat.z) : this.helmStand();
		moveFunction.accept(passenger, at.x, at.y, at.z);
	}

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		if (passenger instanceof Player) {
			// one at her wheel, and (a crewed ship's) not while her captain still has it
			return this.helmsman() == null && (this.crew == null || !this.crew.captained(this));
		}
		// (0.7.42) her crew, at their stations
		return passenger.hasAttached(SEAT) && this.getPassengers().size() < MOST_CREW;
	}

	@Override
	protected void removePassenger(Entity passenger) {
		super.removePassenger(passenger);
		if (!(passenger instanceof Player)) {
			passenger.removeAttached(SEAT);
		}
		// leaving the wheel of a ship lying still drops anchor; under way, she drifts (sails furling) with you on deck
		if (this.level() instanceof ServerLevel && !this.isRemoved() && passenger instanceof ServerPlayer player && player.isShiftKeyDown()) {
			if (Math.abs(this.speed) > STILL) {
				player.sendOverlayMessage(Component.translatable("ship.jaymoskingdoms.left_helm"));
			} else {
				this.anchor(player);
			}
		}
	}

	@Override
	public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
		Vec3 seat = passenger instanceof Player ? null : passenger.getAttached(SEAT);
		return (seat != null ? this.toWorld(seat.x, seat.y, seat.z) : this.helmStand()).add(0.0, 0.05, 0.0);
	}

	// ---- dropping anchor ----

	/**
	 * It swings to the nearest straight heading and its blocks go back into the world where it lies, everyone aboard
	 * with them. False (and {@code by} is told why) if something is in the way.
	 */
	boolean anchor(@Nullable ServerPlayer by) {
		if (!(this.level() instanceof ServerLevel level)) {
			return false;
		}
		this.unshine(level);
		Hull h = this.hull();
		if (h == null) {
			this.discard();
			return true;
		}
		// the nearest straight heading where it lies, else the other one beside it, else nudged a block or two off
		float quarters = this.getYRot() / 90.0F;
		int near = Math.round(quarters);
		int[] headings = {near, quarters > near ? near + 1 : near - 1};
		BlockPos lies = BlockPos.containing(this.getX(), Math.round(this.getY()), this.getZ());
		int quarter = 0;
		BlockPos origin = null;
		List<BlockPos> where = null;
		search:
		for (int r = 0; r <= NUDGE; r++) {
			for (int heading : headings) {
				for (int dx = -r; dx <= r; dx++) {
					for (int dz = -r; dz <= r; dz++) {
						if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
							continue;
						}
						quarter = Math.floorMod(heading, 4);
						origin = lies.offset(dx, 0, dz);
						where = this.room(level, h, origin, QUARTERS[quarter]);
						if (where != null) {
							break search;
						}
					}
				}
			}
		}
		if (where == null) {
			if (by != null) {
				by.sendOverlayMessage(Component.translatable("ship.jaymoskingdoms.no_room"));
			}
			return false;
		}
		Rotation turn = QUARTERS[quarter];
		// everyone aboard comes down with it, where they stood on it
		Vec3 snapped = new Vec3(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5);
		float yaw = quarter * 90.0F;
		List<Entity> aboard = new ArrayList<>();
		List<Vec3> spots = new ArrayList<>();
		for (Entity e : level.getEntities(this, this.getBoundingBox().inflate(1.0), e -> !(e instanceof Ship))) {
			Vec3 local = this.toLocal(e.position());
			Vec3 seat = e.getVehicle() == this && !(e instanceof Player) ? e.getAttached(SEAT) : null;
			if (seat != null) {
				// (0.7.179) her crew where they stood, not all heaped at her wheel
				local = seat;
			} else if (e.getVehicle() == this && h.helm >= 0) {
				Piece helm = h.pieces.get(h.helm);
				local = new Vec3(helm.x(), helm.y(), helm.z() - 1.0);
			} else if (!this.aboard(local, 0.5)) {
				continue;
			}
			aboard.add(e);
			spots.add(toWorld(local.x, local.y, local.z, snapped, yaw));
		}
		this.saveCargo(level);
		int quiet = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
		for (int i = 0; i < h.pieces.size(); i++) {
			BlockState state = h.pieces.get(i).blockState().rotate(turn);
			level.setBlock(where.get(i), state, quiet);
		}
		for (Map.Entry<Integer, CompoundTag> e : this.cargoTags.entrySet()) {
			BlockPos at = where.get(e.getKey());
			BlockEntity be = BlockEntity.loadStatic(at, level.getBlockState(at), e.getValue(), level.registryAccess());
			if (be != null) {
				level.setBlockEntity(be);
			}
		}
		// the dry hold is dry again
		for (long cell : h.enclosed) {
			BlockPos at = origin.offset(BlockPos.of(cell).rotate(turn));
			if (!level.getFluidState(at).isEmpty() && !level.getBlockState(at).hasProperty(BlockStateProperties.WATERLOGGED)) {
				level.setBlock(at, Blocks.AIR.defaultBlockState(), quiet);
			}
		}
		this.cargo.clear();
		this.cargoTags.clear();
		this.ejectPassengers();
		this.discard();
		for (int i = 0; i < aboard.size(); i++) {
			Vec3 s = spots.get(i);
			Entity on = aboard.get(i);
			// (0.7.281) set down where they stood on deck, held or not
			com.jaymoskingdoms.Attention.carry(() -> on.teleportTo(s.x, s.y + 0.02, s.z));
		}
		level.playSound(null, origin, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 1.4F, 0.6F);
		level.playSound(null, origin, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 1.0F, 0.7F);
		return true;
	}

	/** Where each of its blocks would go, put down at {@code origin} turned {@code turn}; null if anything's in the way. */
	@Nullable
	private List<BlockPos> room(ServerLevel level, Hull h, BlockPos origin, Rotation turn) {
		// everything must have room: water, air or loose plants
		List<BlockPos> where = new ArrayList<>(h.pieces.size());
		for (Piece p : h.pieces) {
			BlockPos at = origin.offset(new BlockPos(p.x(), p.y(), p.z()).rotate(turn));
			BlockState there = level.getBlockState(at);
			if (!level.hasChunkAt(at) || !(there.isAir() || there.canBeReplaced() || !there.getFluidState().isEmpty() && !part(there))) {
				return null;
			}
			where.add(at);
		}
		return where;
	}

	// ---- its hold ----

	private void loadCargo(ServerLevel level) {
		Hull h = this.hull();
		if (h == null) {
			return;
		}
		for (Map.Entry<Integer, CompoundTag> e : this.cargoTags.entrySet()) {
			if (e.getKey() >= h.pieces.size()) {
				continue;
			}
			Piece p = h.pieces.get(e.getKey());
			BlockEntity be = BlockEntity.loadStatic(BlockPos.ZERO, p.blockState(), e.getValue(), level.registryAccess());
			if (be != null) {
				this.cargo.put(e.getKey(), be);
			}
		}
	}

	private void saveCargo(Level level) {
		for (Map.Entry<Integer, BlockEntity> e : this.cargo.entrySet()) {
			this.cargoTags.put(e.getKey(), e.getValue().saveWithFullMetadata(level.registryAccess()));
		}
	}

	/** {@code player} uses the block of it at (x, y, z) in its frame: the helm takes the wheel, a chest opens. */
	void use(ServerPlayer player, int x, int y, int z) {
		Hull h = this.hull();
		if (h == null || this.isRemoved()) {
			return;
		}
		int i = h.at(x, y, z);
		if (i < 0 || player.position().distanceTo(this.toWorld(x, y + 0.5, z)) > REACH) {
			return;
		}
		BlockState state = h.pieces.get(i).blockState();
		if (state.getBlock() instanceof HelmBlock) {
			if (i == h.helm && !player.isPassenger()) {
				if (this.crew != null && this.crew.captained(this)) {
					player.sendOverlayMessage(Component.translatable("ship.jaymoskingdoms.crew.helm_held"));
				} else if (this.helmsman() == null) {
					player.startRiding(this);
				}
			}
			return;
		}
		if (state.getBlock() instanceof ShipCannonBlock && this.level() instanceof ServerLevel level) {
			InteractionHand hand = player.getMainHandItem().isEmpty() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
			ShipCannonBlock.Handled done = ShipCannonBlock.handle(state, player.getItemInHand(hand), player, hand);
			if (done != null) {
				this.setPiece(i, done.next());
				if (done.fires()) {
					this.fireGun(level, i, done.next(), player);
				} else {
					Vec3 at = this.toWorld(x, y + 0.5, z);
					level.playSound(null, at.x, at.y, at.z, SoundEvents.SAND_PLACE, SoundSource.BLOCKS, 0.8F, 0.7F);
				}
			}
			return;
		}
		BlockEntity be = this.cargo.get(i);
		// (0.7.42) a crewed ship's hold: each player's own share, once the crew can't keep them out of it
		if (this.crew != null && this.crew.hold == BlockPos.asLong(x, y, z) && captaincy != null && this.level() instanceof ServerLevel level) {
			Container share = captaincy.hold(level, this, this.crew, player);
			if (share != null) {
				Container held = new Held(share, this, i);
				this.level().playSound(null, player.blockPosition(), SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.5F, 0.8F);
				player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new ChestMenu(MenuType.GENERIC_9x3, id, inventory, held, 3),
					Component.translatable("ship.jaymoskingdoms.crew.hold", this.crew.name)));
			}
			return;
		}
		if (be instanceof Container chest && chest.getContainerSize() % 9 == 0 && chest.getContainerSize() <= 54) {
			if (be.getLevel() == null) {
				be.setLevel(this.level());
			}
			int rows = chest.getContainerSize() / 9;
			Container held = new Held(chest, this, i);
			MenuType<?>[] types = {MenuType.GENERIC_9x1, MenuType.GENERIC_9x2, MenuType.GENERIC_9x3, MenuType.GENERIC_9x4, MenuType.GENERIC_9x5, MenuType.GENERIC_9x6};
			this.level().playSound(null, player.blockPosition(), SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.5F, 1.0F);
			player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new ChestMenu(types[rows - 1], id, inventory, held, rows), state.getBlock().getName()));
		} else if (be != null) {
			player.sendOverlayMessage(Component.translatable("ship.jaymoskingdoms.anchor_to_use"));
		}
	}

	/** A chest of its hold, open: it closes if the ship sails off or anchors. */
	private record Held(Container chest, Ship ship, int piece) implements Container {
		@Override
		public int getContainerSize() {
			return this.chest.getContainerSize();
		}

		@Override
		public boolean isEmpty() {
			return this.chest.isEmpty();
		}

		@Override
		public ItemStack getItem(int slot) {
			return this.chest.getItem(slot);
		}

		@Override
		public ItemStack removeItem(int slot, int count) {
			return this.chest.removeItem(slot, count);
		}

		@Override
		public ItemStack removeItemNoUpdate(int slot) {
			return this.chest.removeItemNoUpdate(slot);
		}

		@Override
		public void setItem(int slot, ItemStack stack) {
			this.chest.setItem(slot, stack);
		}

		@Override
		public void setChanged() {
		}

		@Override
		public boolean stillValid(Player player) {
			Hull h = this.ship.hull();
			if (this.ship.isRemoved() || h == null || this.piece >= h.pieces.size()) {
				return false;
			}
			Ship.Piece p = h.pieces.get(this.piece);
			return player.position().distanceTo(this.ship.toWorld(p.x(), p.y() + 0.5, p.z())) <= REACH;
		}

		@Override
		public void clearContent() {
			this.chest.clearContent();
		}
	}

	// ---- her guns ----

	/** One of her blocks becomes {@code state} (a gun loaded or fired). */
	private void setPiece(int i, BlockState state) {
		Hull h = this.hull();
		if (h == null || i < 0 || i >= h.pieces.size()) {
			return;
		}
		List<Piece> pieces = new ArrayList<>(h.pieces);
		Piece p = pieces.get(i);
		pieces.set(i, new Piece(Block.getId(state), p.x(), p.y(), p.z()));
		this.setAttached(PIECES, List.copyOf(pieces));
	}

	/** The way a gun aboard points, in the world (x, z), its muzzle facing {@code facing} in her frame. */
	private double[] worldWay(Direction facing) {
		float c = Mth.cos(this.getYRot() * Mth.DEG_TO_RAD), s = Mth.sin(this.getYRot() * Mth.DEG_TO_RAD);
		return new double[] {facing.getStepX() * c - facing.getStepZ() * s, facing.getStepX() * s + facing.getStepZ() * c};
	}

	// ---- (0.7.42) her guns, for her crew ----

	/** Her guns' keys (each one's place in her frame, packed as a block position), bow to stern. */
	public List<Long> guns() {
		Hull h = this.hull();
		List<Long> out = new ArrayList<>();
		if (h != null) {
			for (int i : h.cannons) {
				Piece p = h.pieces.get(i);
				out.add(BlockPos.asLong(p.x(), p.y(), p.z()));
			}
		}
		return out;
	}

	private @Nullable BlockState gunState(long key) {
		Hull h = this.hull();
		int i = h == null ? -1 : h.index.get(key);
		if (i < 0) {
			return null;
		}
		BlockState state = h.pieces.get(i).blockState();
		return state.getBlock() instanceof ShipCannonBlock ? state : null;
	}

	/** Whether the gun at {@code key} is still aboard, and loaded with powder and shot. */
	public boolean gunLoaded(long key) {
		BlockState state = this.gunState(key);
		return state != null && state.getValue(ShipCannonBlock.LOAD) == ShipCannonBlock.Load.SHOT;
	}

	/** Whether the gun at {@code key} is still aboard. */
	public boolean hasGun(long key) {
		return this.gunState(key) != null;
	}

	/** Her crew load the gun at {@code key} (powder and shot at once). */
	public void loadGun(long key) {
		BlockState state = this.gunState(key);
		if (state != null && state.getValue(ShipCannonBlock.LOAD) != ShipCannonBlock.Load.SHOT) {
			this.setPiece(this.hull().index.get(key), state.setValue(ShipCannonBlock.LOAD, ShipCannonBlock.Load.SHOT));
		}
	}

	/** The middle of the gun at {@code key}, in the world. */
	public Vec3 gunAt(long key) {
		BlockPos p = BlockPos.of(key);
		return this.toWorld(p.getX(), p.getY() + 0.5, p.getZ());
	}

	/** The way the gun at {@code key} points, in the world (x, z), or null if it's gone. */
	public double @Nullable [] gunWay(long key) {
		BlockState state = this.gunState(key);
		return state == null ? null : this.worldWay(state.getValue(ShipCannonBlock.FACING));
	}

	/** Her crew fire the (loaded) gun at {@code key} along {@code dir}, laid by {@code gunner}. False if it couldn't. */
	public boolean fireGunAlong(ServerLevel level, long key, Vec3 dir, @Nullable Entity gunner) {
		BlockState state = this.gunState(key);
		if (state == null || state.getValue(ShipCannonBlock.LOAD) != ShipCannonBlock.Load.SHOT) {
			return false;
		}
		this.setPiece(this.hull().index.get(key), state.setValue(ShipCannonBlock.LOAD, ShipCannonBlock.Load.EMPTY));
		ShipCannonBlock.fireAlong(level, this.gunAt(key), dir, gunner, this);
		return true;
	}

	/** (0.7.206) Pulls her (a whale on a harpoon line made fast aboard, or a whale's charge), taken up at her next move. */
	public void tow(Vec3 pull) {
		this.towed = this.towed.add(pull);
	}

	/** How far she moved last tick (blocks, in the world). */
	public Vec3 drift() {
		return this.drift;
	}

	/**
	 * (0.7.260) Her captain, lying stuck, has her warped off: her sails struck and her crew hauling her astern for
	 * {@code ticks} (as fast as {@link #KEDGE}), whatever her helm says.
	 */
	public void kedge(int ticks) {
		this.kedging = Math.max(this.kedging, ticks);
	}

	/** Whether she's being warped off ({@link #kedge}). */
	public boolean kedging() {
		return this.kedging > 0;
	}

	/** Her way (blocks a tick; astern below 0), and how far her sails are set (0 to 1). */
	public double way() {
		return this.speed;
	}

	public double sailsSet() {
		return this.sail;
	}

	/** Her top speed under sail on her heading, and under oars (blocks a tick; 0 with none). */
	public double sailSpeed() {
		Hull h = this.hull();
		return h == null ? 0.0 : this.topSpeed(h);
	}

	public double oarSpeed() {
		Hull h = this.hull();
		return h == null ? 0.0 : this.rowSpeed(h);
	}

	/** Gun {@code i} (now {@code state}, emptied) fires. */
	private void fireGun(ServerLevel level, int i, BlockState state, @Nullable Player gunner) {
		Hull h = this.hull();
		if (h == null) {
			return;
		}
		Piece p = h.pieces.get(i);
		double[] way = this.worldWay(state.getValue(ShipCannonBlock.FACING));
		ShipCannonBlock.fire(level, this.toWorld(p.x(), p.y() + 0.5, p.z()), way[0], way[1], gunner, this);
	}

	/**
	 * {@code by}, at her helm, fires a broadside: every loaded gun on the side they look to, one after another. Tells them
	 * if none are.
	 */
	void broadside(ServerPlayer by) {
		Hull h = this.hull();
		if (h == null || this.getFirstPassenger() != by || !(this.level() instanceof ServerLevel)) {
			return;
		}
		double lx = -Mth.sin(by.getYRot() * Mth.DEG_TO_RAD), lz = Mth.cos(by.getYRot() * Mth.DEG_TO_RAD);
		int when = this.tickCount + 1;
		boolean any = false;
		for (int i : h.cannons) {
			BlockState state = h.pieces.get(i).blockState();
			if (state.getValue(ShipCannonBlock.LOAD) != ShipCannonBlock.Load.SHOT) {
				continue;
			}
			double[] way = this.worldWay(state.getValue(ShipCannonBlock.FACING));
			if (way[0] * lx + way[1] * lz < BROADSIDE_ARC) {
				continue;
			}
			boolean queued = false;
			for (int[] v : this.volley) {
				queued |= v[0] == i;
			}
			if (!queued) {
				this.volley.add(new int[] {i, when});
				when += BROADSIDE_GAP;
				any = true;
			}
		}
		if (!any) {
			by.sendOverlayMessage(Component.translatable("ship.jaymoskingdoms.no_guns_that_way"));
		}
	}

	/** The guns of a broadside whose turn it is fire (if they're still there and loaded). */
	private void fireVolley(ServerLevel level, Hull h) {
		if (this.volley.isEmpty()) {
			return;
		}
		for (java.util.Iterator<int[]> it = this.volley.iterator(); it.hasNext(); ) {
			int[] v = it.next();
			if (v[1] > this.tickCount) {
				continue;
			}
			it.remove();
			Hull now = this.hull();
			if (now == null || v[0] >= now.pieces.size()) {
				continue;
			}
			BlockState state = now.pieces.get(v[0]).blockState();
			if (state.getBlock() instanceof ShipCannonBlock && state.getValue(ShipCannonBlock.LOAD) == ShipCannonBlock.Load.SHOT) {
				BlockState empty = state.setValue(ShipCannonBlock.LOAD, ShipCannonBlock.Load.EMPTY);
				this.setPiece(v[0], empty);
				this.fireGun(level, v[0], empty, this.pilot());
			}
		}
	}

	// ---- holed and sinking ----

	/**
	 * Round shot at {@code world}: if it's in one of her blocks, the blocks round it are smashed out (sailcloth is only
	 * torn, and the ball flies on) and true. She goes down if she's holed at the waterline (half her hold there flooded)
	 * or can no longer float. Her helm is too stout to smash.
	 */
	public boolean struck(Vec3 world, @Nullable Entity by) {
		Hull h = this.hull();
		if (h == null || this.isRemoved() || !(this.level() instanceof ServerLevel level)) {
			return false;
		}
		Vec3 l = this.toLocal(world);
		int x = Mth.floor(l.x + 0.5), y = Mth.floor(l.y), z = Mth.floor(l.z + 0.5);
		int i = h.at(x, y, z);
		if (i < 0) {
			return false;
		}
		if (by != null) {
			this.holedBy = by.getUUID();
		}
		BlockState state = h.pieces.get(i).blockState();
		boolean in = false;
		for (AABB box : state.getShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO).toAabbs()) {
			in |= box.inflate(0.05).contains(l.x - x + 0.5, l.y - y, l.z - z + 0.5);
		}
		if (!in) {
			return false;
		}
		java.util.Set<Integer> gone = new java.util.HashSet<>();
		if (state.getBlock() instanceof SailBlock) {
			gone.add(i);
			this.smash(level, h, gone);
			return false;
		}
		for (int j = 0; j < h.pieces.size(); j++) {
			Piece p = h.pieces.get(j);
			if (j != h.helm && l.distanceTo(new Vec3(p.x(), p.y() + 0.5, p.z())) <= HOLE) {
				gone.add(j);
			}
		}
		this.smash(level, h, gone);
		return true;
	}

	/** Her pieces {@code gone} are smashed out: splinters, some wash up, her chests spill. */
	private void smash(ServerLevel level, Hull h, java.util.Set<Integer> gone) {
		if (gone.isEmpty()) {
			return;
		}
		List<Piece> left = new ArrayList<>(h.pieces.size() - gone.size());
		Map<Integer, BlockEntity> cargo = new HashMap<>();
		Map<Integer, CompoundTag> tags = new HashMap<>();
		for (int i = 0; i < h.pieces.size(); i++) {
			Piece p = h.pieces.get(i);
			if (!gone.contains(i)) {
				int k = left.size();
				left.add(p);
				if (this.cargo.containsKey(i)) {
					cargo.put(k, this.cargo.get(i));
				}
				if (this.cargoTags.containsKey(i)) {
					tags.put(k, this.cargoTags.get(i));
				}
				continue;
			}
			BlockState state = p.blockState();
			Vec3 at = this.toWorld(p.x(), p.y() + 0.5, p.z());
			level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK, state),
				at.x, at.y, at.z, 12, 0.3, 0.3, 0.3, 0.1);
			BlockEntity be = this.cargo.get(i);
			if (be instanceof Container chest) {
				for (int slot = 0; slot < chest.getContainerSize(); slot++) {
					net.minecraft.world.Containers.dropItemStack(level, at.x, at.y, at.z, chest.removeItemNoUpdate(slot));
				}
			}
			if (be != null || this.random.nextFloat() < SALVAGE) {
				net.minecraft.world.Containers.dropItemStack(level, at.x, at.y, at.z, new ItemStack(state.getBlock().asItem()));
			}
		}
		this.cargo.clear();
		this.cargo.putAll(cargo);
		this.cargoTags.clear();
		this.cargoTags.putAll(tags);
		this.volley.clear();
		if (left.isEmpty()) {
			this.ejectPassengers();
			this.discard();
			return;
		}
		int dryBefore = dryBelowWaterline(h);
		this.setAttached(PIECES, List.copyOf(left));
		Hull now = this.hull();
		// holed at the waterline, her hold floods and she founders (and so does one too heavy to float at all)
		if (now != null && (!now.floats || dryBefore > 0 && dryBelowWaterline(now) * 2 < dryBefore) && this.sinking == 0) {
			this.sinking = 1;
			this.entityData.set(FOUNDERING, true);
			// (0.7.42) her crew go down with her
			if (this.crew != null && captaincy != null) {
				captaincy.foundering(level, this, this.crew, this.holedBy == null ? null : level.getEntity(this.holedBy));
			}
			for (Player p : level.players()) {
				if (p.getVehicle() == this || this.aboard(this.toLocal(p.position()), 1.0)) {
					p.sendOverlayMessage(Component.translatable("ship.jaymoskingdoms.sinking"));
				}
			}
			this.ejectPassengers();
			level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 2.0F, 0.4F);
		}
	}

	/** The dry cells of her hold at or below her waterline (what keeps a laden ship up). */
	private static int dryBelowWaterline(Hull h) {
		int n = 0;
		for (long cell : h.enclosed) {
			if (BlockPos.getY(cell) - h.minY < h.draft + 1.0F) {
				n++;
			}
		}
		return n;
	}

	/** Going down: she slows, settles under, and once she reaches the bottom (or has sunk a while) lies there as a wreck. */
	private void goDown(ServerLevel level, Hull h) {
		this.sinking++;
		this.speed *= 0.95;
		Vec3 from = this.position();
		float yaw = this.getYRot();
		Vec3 ahead = new Vec3(-Mth.sin(yaw * Mth.DEG_TO_RAD), 0.0, Mth.cos(yaw * Mth.DEG_TO_RAD)).scale(this.speed);
		Vec3 to = from.add(ahead.x, -SINK, ahead.z);
		if (this.sinking > SINKING_MOST || this.strikes(level, h, to, yaw, 0)) {
			this.wreck(level, h);
			return;
		}
		this.setPos(to);
		this.carry(level, h, from, yaw);
		this.shine(level, h);
		if (this.sinking % 3 == 0) {
			Vec3 at = this.toWorld((this.random.nextFloat() - 0.5) * (h.maxX - h.minX), h.draft, (this.random.nextFloat() - 0.5) * (h.maxZ - h.minZ));
			level.sendParticles(net.minecraft.core.particles.ParticleTypes.BUBBLE_COLUMN_UP, at.x, at.y, at.z, 6, 0.4, 0.2, 0.4, 0.05);
		}
		if (this.sinking % 40 == 0) {
			level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.WOOD_STEP, SoundSource.BLOCKS, 1.0F, 0.4F);
		}
	}

	/** She's laid on the bottom where she sank, block for block where there's room, her holds full of what they held. */
	private void wreck(ServerLevel level, Hull h) {
		this.unshine(level);
		int quarter = Math.floorMod(Math.round(this.getYRot() / 90.0F), 4);
		Rotation turn = QUARTERS[quarter];
		BlockPos origin = BlockPos.containing(this.getX(), Math.round(this.getY()), this.getZ());
		this.saveCargo(level);
		Map<Integer, BlockPos> laid = new HashMap<>();
		// (0.7.28) her solid timbers first, so the torches and lanterns hung on them don't pop off as they're put down
		List<Integer> order = new ArrayList<>(h.pieces.size());
		for (int i = 0; i < h.pieces.size(); i++) {
			order.add(i);
		}
		order.sort((a, b) -> Boolean.compare(!h.pieces.get(a).blockState().isCollisionShapeFullBlock(level, BlockPos.ZERO),
			!h.pieces.get(b).blockState().isCollisionShapeFullBlock(level, BlockPos.ZERO)));
		for (int i : order) {
			Piece p = h.pieces.get(i);
			BlockPos at = origin.offset(new BlockPos(p.x(), p.y(), p.z()).rotate(turn));
			BlockState there = level.getBlockState(at);
			if (!level.hasChunkAt(at) || !(there.isAir() || there.canBeReplaced() || !there.getFluidState().isEmpty() && !part(there))) {
				continue;
			}
			BlockState state = p.blockState().rotate(turn);
			if (state.hasProperty(BlockStateProperties.WATERLOGGED)) {
				state = state.setValue(BlockStateProperties.WATERLOGGED, level.getFluidState(at).is(FluidTags.WATER));
			}
			level.setBlock(at, state, Block.UPDATE_ALL);
			laid.put(i, at);
		}
		for (Map.Entry<Integer, CompoundTag> e : this.cargoTags.entrySet()) {
			BlockPos at = laid.get(e.getKey());
			if (at == null) {
				continue;
			}
			BlockEntity be = BlockEntity.loadStatic(at, level.getBlockState(at), e.getValue(), level.registryAccess());
			if (be != null) {
				level.setBlockEntity(be);
			}
		}
		this.cargo.clear();
		this.cargoTags.clear();
		level.playSound(null, origin, SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 2.0F, 0.5F);
		this.ejectPassengers();
		this.discard();
	}

	// ---- the rest ----

	@Override
	public void onRemoval(Entity.RemovalReason reason) {
		super.onRemoval(reason);
		if (this.level() instanceof ServerLevel level) {
			this.unshine(level);
		}
		Ships.remove(this);
	}

	/** (0.7.28) Its light sources light the world cells they're in now (see {@link #lit}). */
	private void shine(ServerLevel level, Hull h) {
		if (h.lights.length == 0 && this.lit.isEmpty() || this.tickCount % LIGHT_EVERY != 0) {
			return;
		}
		it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap want = new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap();
		for (int i : h.lights) {
			Piece p = h.pieces.get(i);
			long at = BlockPos.containing(this.toWorld(p.x(), p.y() + 0.5, p.z())).asLong();
			want.put(at, Math.max(want.get(at), p.blockState().getLightEmission()));
		}
		for (it.unimi.dsi.fastutil.longs.Long2IntMap.Entry e : this.lit.long2IntEntrySet()) {
			if (want.get(e.getLongKey()) != e.getIntValue()) {
				douse(level, BlockPos.of(e.getLongKey()));
			}
		}
		// what no longer matches is taken up; what something else took up (a bucket, a fill) is put back below
		this.lit.long2IntEntrySet().removeIf(e -> want.get(e.getLongKey()) != e.getIntValue()
			|| level.hasChunkAt(BlockPos.of(e.getLongKey())) && !level.getBlockState(BlockPos.of(e.getLongKey())).is(Blocks.LIGHT));
		for (it.unimi.dsi.fastutil.longs.Long2IntMap.Entry e : want.long2IntEntrySet()) {
			long key = e.getLongKey();
			if (this.lit.containsKey(key)) {
				continue;
			}
			BlockPos at = BlockPos.of(key);
			if (!level.hasChunkAt(at)) {
				continue;
			}
			BlockState there = level.getBlockState(at);
			boolean water = there.is(Blocks.WATER) && there.getFluidState().isSource();
			if (!there.isAir() && !water) {
				continue;
			}
			level.setBlock(at, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, e.getIntValue())
				.setValue(LightBlock.WATERLOGGED, water), Block.UPDATE_CLIENTS);
			this.lit.put(key, e.getIntValue());
		}
	}

	/** (0.7.28) Every light it left in the world taken up again. */
	private void unshine(ServerLevel level) {
		for (long key : this.lit.keySet()) {
			douse(level, BlockPos.of(key));
		}
		this.lit.clear();
	}

	private static void douse(ServerLevel level, BlockPos at) {
		if (!level.hasChunkAt(at)) {
			return;
		}
		BlockState there = level.getBlockState(at);
		if (there.is(Blocks.LIGHT)) {
			level.setBlock(at, there.getValue(LightBlock.WATERLOGGED) ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState(),
				Block.UPDATE_CLIENTS);
		}
	}

	@Override
	public void onClientRemoval() {
		super.onClientRemoval();
		Ships.remove(this);
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean canBeCollidedWith(@Nullable Entity other) {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		return false;
	}

	@Override
	public boolean shouldBeSaved() {
		return !this.isRemoved();
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		entityData.define(SET, 0.0F);
		entityData.define(ROWING, (byte)0);
		entityData.define(FOUNDERING, false);
	}

	/** Client: how far its sails are set (0 furled to 1 full). */
	public float sailSet() {
		return this.entityData.get(SET);
	}

	/** Whether she's going down, her hold flooded. */
	public boolean foundering() {
		return this.entityData.get(FOUNDERING);
	}

	/** Client: whether she's rowing (1 ahead, -1 astern, 0 not). */
	public int rowing() {
		return this.entityData.get(ROWING);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		List<BlockState> palette = input.read("Palette", BlockState.CODEC.listOf()).orElse(List.of());
		int[] cells = input.getIntArray("Blocks").orElse(new int[0]);
		List<Piece> pieces = new ArrayList<>(cells.length / 4);
		for (int i = 0; i + 3 < cells.length; i += 4) {
			BlockState state = cells[i] < palette.size() ? palette.get(cells[i]) : Blocks.AIR.defaultBlockState();
			if (!state.isAir()) {
				pieces.add(new Piece(Block.getId(state), cells[i + 1], cells[i + 2], cells[i + 3]));
			}
		}
		this.setAttached(PIECES, List.copyOf(pieces));
		this.hull = null;
		this.hull();
		this.cargoTags.clear();
		this.cargo.clear();
		for (CompoundTag tag : input.read("Hold", CompoundTag.CODEC.listOf()).orElse(List.of())) {
			int piece = tag.getIntOr("ShipPiece", -1);
			if (piece >= 0) {
				CompoundTag copy = tag.copy();
				copy.remove("ShipPiece");
				this.cargoTags.put(piece, copy);
			}
		}
		this.sail = input.getDoubleOr("Sail", 0.0);
		this.crew = input.read("Crew", Crew.CODEC).orElse(null);
		this.sinking = input.getIntOr("Sinking", 0);
		this.lit.clear();
		for (long key : input.read("Lit", com.mojang.serialization.Codec.LONG.listOf()).orElse(List.of())) {
			// level 0 matches no light it has: the first shine takes it up
			this.lit.put(key, 0);
		}
		this.entityData.set(FOUNDERING, this.sinking > 0);
		this.entityData.set(SET, (float)this.sail);
		if (this.level() instanceof ServerLevel level) {
			this.loadCargo(level);
		}
		this.setBoundingBox(this.makeBoundingBox(this.position()));
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		Hull h = this.hull();
		List<BlockState> palette = new ArrayList<>();
		Map<Integer, Integer> paletteOf = new HashMap<>();
		List<Piece> pieces = h == null ? List.of() : h.pieces;
		int[] cells = new int[pieces.size() * 4];
		for (int i = 0; i < pieces.size(); i++) {
			Piece p = pieces.get(i);
			int k = paletteOf.computeIfAbsent(p.state(), s -> {
				palette.add(Block.stateById(s));
				return palette.size() - 1;
			});
			cells[i * 4] = k;
			cells[i * 4 + 1] = p.x();
			cells[i * 4 + 2] = p.y();
			cells[i * 4 + 3] = p.z();
		}
		output.store("Palette", BlockState.CODEC.listOf(), palette);
		output.putIntArray("Blocks", cells);
		this.saveCargo(this.level());
		List<CompoundTag> hold = new ArrayList<>();
		for (Map.Entry<Integer, CompoundTag> e : this.cargoTags.entrySet()) {
			CompoundTag tag = e.getValue().copy();
			tag.putInt("ShipPiece", e.getKey());
			hold.add(tag);
		}
		output.store("Hold", CompoundTag.CODEC.listOf(), hold);
		output.putDouble("Sail", this.sail);
		if (this.crew != null) {
			output.store("Crew", Crew.CODEC, this.crew);
		}
		output.putInt("Sinking", this.sinking);
		// (0.7.28) where its lights are, so a reload takes up any the save caught in the world
		output.store("Lit", com.mojang.serialization.Codec.LONG.listOf(), List.copyOf(this.lit.keySet()));
	}
}
