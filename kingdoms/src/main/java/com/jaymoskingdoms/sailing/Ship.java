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
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
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
 * (0.7.5) A ship built from blocks, under sail. Every block joined to a {@link HelmBlock} leaves the world and sails
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
	/** The most blocks one ship may hold, and its longest side. */
	static final int MOST_BLOCKS = 3000, LONGEST = 64;
	/** Top speed (blocks/tick): per block of waterline, per (square root of) sail, its paddling speed with no sails, and the most. */
	static final double PER_LENGTH = 0.04, PER_SAIL = 0.1, PADDLE = 0.05, FASTEST = 0.8;
	/** How quickly it gathers or loses way (a share of the gap a tick), and its sails set or furled (a share a tick). */
	static final double GATHER = 0.015, SAIL_RATE = 0.02;
	/** Its best turn (degrees/tick), for a ship this long or shorter; longer ones turn slower. */
	static final float TURN = 2.5F, NIMBLE_LENGTH = 12.0F;
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

	public static EntityType<Ship> TYPE;
	/** The client's part each tick (carrying you with the deck), set by the client. */
	public static Consumer<Ship> clientTick = ship -> {
	};

	private @Nullable Hull hull;
	private @Nullable List<Piece> hullOf;
	/** Server: how far its sails are set (0 furled to 1 full), its way (blocks/tick) and its turn (degrees/tick). */
	private double sail, speed;
	private float turn;
	private int adrift;
	/** Server: the block entities aboard (chests and the like), by piece. */
	private final Map<Integer, BlockEntity> cargo = new HashMap<>();
	private final Map<Integer, CompoundTag> cargoTags = new HashMap<>();
	private boolean registered;
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
		BlockState helmState = level.getBlockState(helm);
		if (!(helmState.getBlock() instanceof HelmBlock)) {
			return;
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
				player.sendOverlayMessage(Component.translatable("ship.jaymoskingdoms.too_big", MOST_BLOCKS));
				return;
			}
			x0 = Math.min(x0, p.getX());
			x1 = Math.max(x1, p.getX());
			y0 = Math.min(y0, p.getY());
			y1 = Math.max(y1, p.getY());
			z0 = Math.min(z0, p.getZ());
			z1 = Math.max(z1, p.getZ());
			if (x1 - x0 >= LONGEST || y1 - y0 >= LONGEST || z1 - z0 >= LONGEST) {
				player.sendOverlayMessage(Component.translatable("ship.jaymoskingdoms.too_long", LONGEST));
				return;
			}
			for (Direction d : Direction.values()) {
				BlockPos q = p.relative(d);
				if (seen.add(q.asLong()) && part(level.getBlockState(q))) {
					if (level.getBlockState(q).getDestroySpeed(level, q) < 0.0F) {
						player.sendOverlayMessage(Component.translatable("ship.jaymoskingdoms.land"));
						return;
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
			player.sendOverlayMessage(Component.translatable("ship.jaymoskingdoms.not_afloat"));
			return;
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
			pieces.add(new Piece(Block.getId(state.rotate(turn)), local.getX(), local.getY() - keel, local.getZ()));
		}
		Hull hull = new Hull(List.copyOf(pieces));
		if (!hull.floats) {
			player.sendOverlayMessage(Component.translatable("ship.jaymoskingdoms.too_heavy"));
			return;
		}
		Ship ship = new Ship(TYPE, level);
		ship.setAttached(PIECES, hull.pieces);
		ship.hull();
		ship.cargoTags.putAll(tags);
		ship.loadCargo(level);
		ship.snapTo(origin.getX() + 0.5, origin.getY() + keel, origin.getZ() + 0.5, facing.toYRot(), 0.0F);
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
		player.startRiding(ship);
		level.playSound(null, helm, SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 1.0F, 0.6F);
		level.playSound(null, helm, SoundEvents.BOAT_PADDLE_WATER, SoundSource.BLOCKS, 1.4F, 0.7F);
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
		Vec3 from = this.position();
		float yawFrom = this.getYRot();
		ServerPlayer pilot = this.getFirstPassenger() instanceof ServerPlayer p ? p : null;
		Input input = pilot != null ? pilot.getLastClientInput() : Input.EMPTY;
		// the sails and the rudder
		if (input.forward()) {
			this.sail = Math.min(1.0, this.sail + SAIL_RATE);
		} else if (input.backward()) {
			this.sail = Math.max(0.0, this.sail - SAIL_RATE);
		}
		if (pilot == null) {
			this.sail = Math.max(0.0, this.sail - SAIL_RATE);
		}
		double top = this.topSpeed(h);
		// with the sails furled, backing water (S) takes it slowly astern
		double wanted = this.sail <= 0.0 && input.backward() ? -PADDLE : this.sail * top;
		this.speed += (wanted - this.speed) * GATHER;
		if (Math.abs(this.speed) < 1.0E-4) {
			this.speed = 0.0;
		}
		float steer = input.left() == input.right() ? 0.0F : input.left() ? -1.0F : 1.0F;
		float best = TURN * Math.min(1.0F, NIMBLE_LENGTH / Math.max(NIMBLE_LENGTH, h.waterline))
			* (0.3F + 0.7F * (float)Math.min(1.0, Math.abs(this.speed) / 0.15));
		this.turn += (steer * best - this.turn) * 0.2F;
		float yaw = yawFrom + this.turn;
		double y = this.settle(level, h, from);
		Vec3 ahead = new Vec3(-Mth.sin(yaw * Mth.DEG_TO_RAD), 0.0, Mth.cos(yaw * Mth.DEG_TO_RAD)).scale(this.speed);
		Vec3 to = new Vec3(from.x + ahead.x, y, from.z + ahead.z);
		if (this.strikes(level, h, to, yaw)) {
			// try it without the turn, then without the way, then hold still
			if (this.speed > 0.15) {
				level.playSound(null, this.getX(), this.getY() + 1.0, this.getZ(), SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.BLOCKS, 1.0F, 0.6F);
			}
			if (!this.strikes(level, h, to, yawFrom)) {
				yaw = yawFrom;
				this.turn = 0.0F;
			} else if (!this.strikes(level, h, new Vec3(from.x, y, from.z), yaw)) {
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
		this.carry(level, h, from, yawFrom);
		// the wake and the creak of her timbers
		if (this.speed > 0.1 && this.tickCount % 4 == 0) {
			Vec3 bow = this.toWorld(0.0, h.draft, h.maxZ + 0.5);
			level.sendParticles(net.minecraft.core.particles.ParticleTypes.SPLASH, bow.x, bow.y, bow.z, 6, 0.6, 0.1, 0.6, 0.1);
		}
		if (this.tickCount % 90 == 0 && this.speed > 0.05) {
			level.playSound(null, this.getX(), this.getY() + h.draft, this.getZ(), SoundEvents.WOOD_STEP, SoundSource.BLOCKS, 0.6F, 0.5F);
		}
		if (pilot != null && this.tickCount % 10 == 0) {
			pilot.sendOverlayMessage(Component.translatable("ship.jaymoskingdoms.under_way", Mth.floor(this.sail * 100.0),
				String.format("%.1f", this.speed * 20.0), String.format("%.1f", top * 20.0)));
		}
		// with no one aboard a while, it anchors itself
		boolean manned = pilot != null;
		for (Player p : level.players()) {
			manned |= this.aboard(this.toLocal(p.position()), 1.0);
		}
		this.adrift = manned ? 0 : this.adrift + 1;
		if (this.adrift > ADRIFT && this.adrift % 40 == 1) {
			this.anchor(null);
		}
	}

	/** Its top speed (blocks/tick): its sails' push or its length, whichever is less (or paddling, with no sails). */
	double topSpeed(Hull h) {
		if (h.sails == 0) {
			return PADDLE;
		}
		return Math.min(FASTEST, Math.min(PER_LENGTH * h.waterline, PER_SAIL * Math.sqrt(h.sails)));
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

	/** Whether, at {@code at} turned {@code yaw}, any of its outside runs into something solid (or unloaded). */
	private boolean strikes(ServerLevel level, Hull h, Vec3 at, float yaw) {
		BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
		for (int i : h.skin) {
			Piece p = h.pieces.get(i);
			Vec3 w = toWorld(p.x(), p.y() + (h.lo[i] + Math.min(1.0F, h.hi[i])) * 0.5, p.z(), at, yaw);
			q.set(Mth.floor(w.x), Mth.floor(w.y), Mth.floor(w.z));
			if (!level.hasChunkAt(q)) {
				return true;
			}
			BlockState there = level.getBlockState(q);
			if (!there.isAir() && !there.getCollisionShape(level, q).isEmpty()) {
				return true;
			}
		}
		return false;
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
		Vec3 at = this.helmStand();
		moveFunction.accept(passenger, at.x, at.y, at.z);
	}

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return this.getPassengers().isEmpty() && passenger instanceof Player;
	}

	@Override
	protected void removePassenger(Entity passenger) {
		super.removePassenger(passenger);
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
		return this.helmStand().add(0.0, 0.05, 0.0);
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
			if (e.getVehicle() == this && h.helm >= 0) {
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
			aboard.get(i).teleportTo(s.x, s.y + 0.02, s.z);
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
			if (i == h.helm && this.getPassengers().isEmpty() && !player.isPassenger()) {
				player.startRiding(this);
			}
			return;
		}
		BlockEntity be = this.cargo.get(i);
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

	// ---- the rest ----

	@Override
	public void onRemoval(Entity.RemovalReason reason) {
		super.onRemoval(reason);
		Ships.remove(this);
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
	}
}
