package com.jaymospowers.core;

import com.jaymospowers.JaymosPowers;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A chunk of blocks torn out of a wall or the ground, held together: Living Armor's tendrils rip one loose (Sneak + R/G
 * at a block), Gravity's Lift tears one up (R aimed at a block). Held, it hangs in front of you like anything else
 * held; thrown, it batters whatever it flies into and shatters on what stops it. Left lying, it crumbles after a while.
 * Never saved: a chunk left in an unloaded area is simply gone.
 */
public class BlockCluster extends Entity {
	/** How big a chunk can be, and what holds it together: hit damage grows with its weight (blocks) and speed. */
	public static final int MAX_PIECES = 9;
	static final float HIT_DAMAGE_BASE = 2.5F, HIT_DAMAGE_PER_SPEED = 2.5F, HIT_DAMAGE_MAX = 12.0F;
	/** A swing of a held chunk (Living Armor): this, plus per block, at most {@code SWING_DAMAGE_MAX}; it chips a block off. */
	static final float SWING_DAMAGE_BASE = 3.0F, SWING_DAMAGE_PER_PIECE = 0.8F, SWING_DAMAGE_MAX = 10.0F;
	static final double SWING_REACH = 4.5;
	/** Left lying (not held, not flying) it crumbles after this long; nothing lasts longer than {@code MAX_AGE}. */
	static final int IDLE_TICKS = 100, MAX_AGE = 2400;
	/** A broken chunk drops each of its blocks this often. */
	static final float DROP_CHANCE = 0.3F;
	/** How ragged a torn-out chunk is: the jitter on each block's distance when choosing which come with it. */
	static final double RAGGED = 1.3;
	/** Soft blocks (dirt, sand, gravel, leaves, snow... and wool): no harder than this. A chunk made only of them hits for at most {@code SOFT_HIT_DAMAGE}. */
	public static final float SOFT_HARDNESS = 0.6F, SOFT_HIT_DAMAGE = 1.0F;

	/** One block of the chunk: its state, and where it sits relative to the chunk's middle (blocks). */
	public record Piece(int state, int x, int y, int z) {
		public static final StreamCodec<ByteBuf, Piece> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Piece::state,
			ByteBufCodecs.VAR_INT, Piece::x, ByteBufCodecs.VAR_INT, Piece::y, ByteBufCodecs.VAR_INT, Piece::z, Piece::new);

		public BlockState blockState() {
			return Block.stateById(this.state);
		}
	}

	/** Steered by a power (a slab you ride, a block orbiting you, a meteor): drawn without tumbling unless it's falling. */
	public static final AttachmentType<Boolean> STEADY = AttachmentRegistry.create(JaymosPowers.id("block_cluster_steady"),
		builder -> builder.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));

	/** Drawn this many times its size (a big meteor's rock); unset is 1. */
	public static final AttachmentType<Float> SCALE = AttachmentRegistry.create(JaymosPowers.id("block_cluster_scale"),
		builder -> builder.syncWith(ByteBufCodecs.FLOAT, AttachmentSyncPredicate.all()));

	/** The chunk's blocks (synced to everyone, who draw them). */
	public static final AttachmentType<List<Piece>> PIECES = AttachmentRegistry.create(JaymosPowers.id("block_cluster_pieces"),
		builder -> builder.syncWith(Piece.STREAM_CODEC.apply(ByteBufCodecs.list()), AttachmentSyncPredicate.all()));

	public static EntityType<BlockCluster> TYPE;

	private @Nullable UUID thrower;
	private boolean thrown;
	private Vec3 lastVelocity = Vec3.ZERO;
	private final Set<Integer> struck = new HashSet<>();
	private int idle;
	/** Moved by whoever owns it (setPos each tick): no physics of its own, no crumbling. */
	private boolean scripted;

	public BlockCluster(EntityType<? extends BlockCluster> type, Level level) {
		super(type, level);
	}

	public static void register() {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JaymosPowers.id("block_cluster"));
		TYPE = Registry.register(BuiltInRegistries.ENTITY_TYPE, key, EntityType.Builder.<BlockCluster>of(BlockCluster::new, MobCategory.MISC)
			.noLootTable()
			.sized(1.4F, 1.4F)
			.clientTrackingRange(8)
			.updateInterval(1)
			.build(key));
	}

	public List<Piece> pieces() {
		List<Piece> pieces = this.getAttached(PIECES);
		return pieces == null ? List.of() : pieces;
	}

	/**
	 * Tears a chunk out where {@code hit} struck: up to {@code max} blocks around it (nearest first), none harder than
	 * {@code hardest}, nothing with contents, no fluids. Returns the chunk (already in the world), or null if nothing
	 * there would come loose.
	 */
	public static @Nullable BlockCluster rip(ServerLevel level, BlockHitResult hit, int max, float hardest, @Nullable Player by) {
		List<BlockPos> taken = crater(level, hit.getBlockPos(), max, hardest, by);
		if (taken.isEmpty()) {
			return null;
		}
		return of(level, taken, hit.getDirection().getUnitVec3().scale(0.9), by);
	}

	/**
	 * The blocks a chunk torn out at {@code center} would take, in order: up to {@code max}, nearest first but ragged
	 * (like a small blast's crater), none harder than {@code hardest}, nothing with contents, no fluids.
	 */
	public static List<BlockPos> crater(ServerLevel level, BlockPos center, int max, float hardest, @Nullable Player by) {
		List<BlockPos> around = new ArrayList<>();
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-2, -2, -2), center.offset(2, 2, 2))) {
			around.add(pos.immutable());
		}
		java.util.Map<BlockPos, Double> rank = new java.util.HashMap<>();
		for (BlockPos pos : around) {
			rank.put(pos, Math.sqrt(pos.distSqr(center)) + level.getRandom().nextDouble() * RAGGED);
		}
		around.sort(Comparator.comparingDouble(rank::get));
		List<BlockPos> taken = new ArrayList<>();
		for (BlockPos pos : around) {
			if (taken.size() >= Math.min(max, MAX_PIECES)) {
				break;
			}
			if (takeable(level, pos, hardest, by)) {
				taken.add(pos);
			}
		}
		return taken;
	}

	/** Soft: it does little more than dust you when it lands (see {@link #SOFT_HARDNESS}). */
	public static boolean soft(BlockState state) {
		float hardness = state.getDestroySpeed(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
		return hardness >= 0.0F && hardness <= SOFT_HARDNESS || state.is(net.minecraft.tags.BlockTags.WOOL);
	}

	/** Every block of the chunk is soft. */
	public boolean allSoft() {
		List<Piece> pieces = this.pieces();
		if (pieces.isEmpty()) {
			return false;
		}
		for (Piece piece : pieces) {
			if (!soft(piece.blockState())) {
				return false;
			}
		}
		return true;
	}

	/** A block that can come loose: something solid, not too hard, with no contents and no fluid. */
	public static boolean takeable(ServerLevel level, BlockPos pos, float hardest, @Nullable Player by) {
		BlockState state = level.getBlockState(pos);
		float hardness = state.getDestroySpeed(level, pos);
		return !state.isAir() && state.getFluidState().isEmpty() && !state.hasBlockEntity() && hardness >= 0.0F && hardness <= hardest
			&& (by == null || level.mayInteract(by, pos));
	}

	/** A chunk made of the blocks at {@code taken} (they're torn out), set down at their middle plus {@code out}. */
	public static @Nullable BlockCluster of(ServerLevel level, List<BlockPos> taken, Vec3 out, @Nullable Player by) {
		List<BlockState> states = new ArrayList<>();
		for (BlockPos pos : taken) {
			states.add(level.getBlockState(pos));
		}
		if (taken.isEmpty()) {
			return null;
		}
		Vec3 middle = Vec3.ZERO;
		for (BlockPos pos : taken) {
			middle = middle.add(Vec3.atCenterOf(pos));
		}
		middle = middle.scale(1.0 / taken.size());
		BlockPos base = BlockPos.containing(middle);
		// (base: the chunk's middle block; each piece sits relative to it)
		List<Piece> pieces = new ArrayList<>();
		for (int i = 0; i < taken.size(); i++) {
			BlockPos pos = taken.get(i);
			pieces.add(new Piece(Block.getId(states.get(i)), pos.getX() - base.getX(), pos.getY() - base.getY(), pos.getZ() - base.getZ()));
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			Debris.puff(level, pos, states.get(i), 1.4F);
		}
		BlockCluster cluster = new BlockCluster(TYPE, level);
		// out of the hole it came from
		Vec3 at = Vec3.atCenterOf(base).add(out).subtract(0.0, 0.7, 0.0);
		cluster.setPos(at);
		cluster.setAttached(PIECES, pieces);
		level.addFreshEntity(cluster);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.PLAYERS, 1.0F, 0.6F);
		level.playSound(null, at.x, at.y, at.z, states.get(0).getSoundType().getBreakSound(), SoundSource.BLOCKS, 1.2F, 0.7F);
		return cluster;
	}

	/** A chunk made of blocks that were never placed (a meteor's rock): {@code states} in a rough ball at {@code at}. */
	public static BlockCluster made(ServerLevel level, List<BlockState> states, Vec3 at) {
		List<Piece> pieces = new ArrayList<>();
		int[][] spots = {{0, 0, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}, {1, 1, 0}, {-1, -1, 0}};
		for (int i = 0; i < states.size() && i < spots.length; i++) {
			pieces.add(new Piece(Block.getId(states.get(i)), spots[i][0], spots[i][1], spots[i][2]));
		}
		BlockCluster cluster = new BlockCluster(TYPE, level);
		cluster.setPos(at);
		cluster.setAttached(PIECES, pieces);
		level.addFreshEntity(cluster);
		return cluster;
	}

	/** Steered from now on (or let go of): it moves only where it's put, and never crumbles on its own. */
	public void script(boolean on, boolean steady) {
		this.scripted = on;
		this.setNoGravity(on);
		this.noPhysics = on;
		if (steady) {
			this.setAttached(STEADY, true);
		} else if (this.getAttached(STEADY) != null) {
			this.removeAttached(STEADY);
		}
	}

	public boolean scripted() {
		return this.scripted;
	}

	/** A big one (see {@link #SCALE}) is seen from as far off as its size deserves. */
	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		Float scale = this.getAttached(SCALE);
		return scale != null && scale > 1.0F ? distance < 256.0 * 256.0 : super.shouldRenderAtSqrDistance(distance);
	}

	/** Into {@code player}'s inventory as its blocks (what doesn't fit drops at their feet), and the chunk is gone. */
	public void pocket(Player player) {
		for (Piece piece : this.pieces()) {
			ItemStack stack = new ItemStack(piece.blockState().getBlock());
			if (!stack.isEmpty() && !player.getInventory().add(stack) && this.level() instanceof ServerLevel level) {
				level.addFreshEntity(new ItemEntity(level, player.getX(), player.getY() + 0.5, player.getZ(), stack));
			}
		}
		this.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.8F, 0.7F);
		this.discard();
	}

	/** Thrown (by whoever): it now batters what it flies into, and breaks on what stops it. */
	public void launch(@Nullable Entity by) {
		this.thrown = true;
		this.thrower = by == null ? null : by.getUUID();
		this.struck.clear();
		this.lastVelocity = this.getDeltaMovement();
	}

	/**
	 * A held chunk swung at what's in front of {@code player} (Living Armor's left or right click): it lands on the
	 * nearest creature in reach, harder the bigger the chunk, and a block chips off. Returns true if it hit.
	 */
	public boolean swingFrom(Player player) {
		if (!(this.level() instanceof ServerLevel level)) {
			return false;
		}
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		LivingEntity target = null;
		double best = Double.MAX_VALUE;
		for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(SWING_REACH + 1.0),
			e -> e != player && e.isAlive() && !e.isSpectator() && !player.isAlliedTo(e))) {
			Vec3 to = living.getBoundingBox().getCenter().subtract(eye);
			double d = to.length();
			if (d > SWING_REACH + living.getBbWidth() * 0.5 || d > 1.0E-3 && to.scale(1.0 / d).dot(look) < 0.4) {
				continue;
			}
			if (d < best) {
				best = d;
				target = living;
			}
		}
		// swung out at it (or at the air ahead), and pulled back by the hold
		Vec3 aim = target != null ? target.getBoundingBox().getCenter().subtract(this.getBoundingBox().getCenter()) : look.scale(2.0);
		this.setDeltaMovement(aim.normalize().scale(1.4));
		this.needsSync = true;
		this.syncVelocity = true;
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 0.6F);
		if (target == null) {
			return false;
		}
		float damage = Math.min(SWING_DAMAGE_MAX, SWING_DAMAGE_BASE + SWING_DAMAGE_PER_PIECE * this.pieces().size());
		target.hurtServer(level, player.damageSources().playerAttack(player), damage);
		Vec3 push = target.position().subtract(player.position()).horizontal();
		if (push.lengthSqr() > 1.0E-4) {
			target.setDeltaMovement(target.getDeltaMovement().add(push.normalize().scale(0.6)).add(0.0, 0.2, 0.0));
			target.syncVelocity = true;
		}
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.0F, 0.5F);
		this.chip(level);
		return true;
	}

	/** A block knocked off (in a puff of its dust); the last one and the chunk is gone. */
	public void chip(ServerLevel level) {
		List<Piece> pieces = new ArrayList<>(this.pieces());
		if (pieces.isEmpty()) {
			this.discard();
			return;
		}
		Piece off = pieces.remove(level.getRandom().nextInt(pieces.size()));
		Debris.puff(level, this.blockPosition(), off.blockState(), 1.2F);
		if (pieces.isEmpty()) {
			this.discard();
		} else {
			this.setAttached(PIECES, pieces);
		}
	}

	/** It breaks up: dust from every block, and some of them land as items. */
	public void shatter(ServerLevel level) {
		for (Piece piece : this.pieces()) {
			BlockPos at = BlockPos.containing(this.position().add(piece.x(), piece.y() + 0.7, piece.z()));
			BlockState state = piece.blockState();
			Debris.puff(level, at, state, 1.6F);
			if (level.getRandom().nextFloat() < DROP_CHANCE && !state.isAir()) {
				ItemStack drop = new ItemStack(state.getBlock());
				if (!drop.isEmpty()) {
					level.addFreshEntity(new ItemEntity(level, this.getX(), this.getY() + 0.5, this.getZ(), drop));
				}
			}
		}
		if (!this.pieces().isEmpty()) {
			level.playSound(null, this.getX(), this.getY(), this.getZ(), this.pieces().get(0).blockState().getSoundType().getBreakSound(),
				SoundSource.BLOCKS, 1.5F, 0.6F);
		}
		this.discard();
	}

	@Override
	public void tick() {
		super.tick();
		if (this.scripted) {
			// put where its owner puts it
			if (this.level() instanceof ServerLevel level && this.pieces().isEmpty() && this.tickCount > 5) {
				this.discard();
			}
			return;
		}
		if (!this.isNoGravity()) {
			this.setDeltaMovement(this.getDeltaMovement().add(0.0, -0.06, 0.0));
		}
		this.move(MoverType.SELF, this.getDeltaMovement());
		this.setDeltaMovement(this.getDeltaMovement().scale(this.onGround() ? 0.6 : 0.98));
		if (this.level() instanceof ServerLevel level) {
			this.serverTick(level);
		}
	}

	private void serverTick(ServerLevel level) {
		if (this.pieces().isEmpty() && this.tickCount > 5 || this.tickCount > MAX_AGE) {
			this.shatter(level);
			return;
		}
		if (this.thrown) {
			double speed = this.lastVelocity.length();
			Entity by = this.thrower == null ? null : level.getEntity(this.thrower);
			if (speed > 0.4) {
				float weight = Mth.clamp(this.pieces().size() / 4.0F, 0.5F, 2.0F);
				boolean soft = this.allSoft();
				for (LivingEntity hit : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(0.2).expandTowards(this.lastVelocity),
					e -> e.isAlive() && !e.isSpectator() && e != by && !this.struck.contains(e.getId()))) {
					this.struck.add(hit.getId());
					float damage = Math.min(soft ? SOFT_HIT_DAMAGE : HIT_DAMAGE_MAX, (HIT_DAMAGE_BASE + HIT_DAMAGE_PER_SPEED * (float)speed) * weight);
					DamageSource source = by instanceof Player p ? level.damageSources().mobProjectile(this, p) : level.damageSources().fallingBlock(this);
					hit.hurtServer(level, source, damage);
					hit.setDeltaMovement(hit.getDeltaMovement().add(this.lastVelocity.scale(0.5)).add(0.0, 0.25, 0.0));
					hit.syncVelocity = true;
				}
			}
			if ((this.horizontalCollision || this.verticalCollision) && speed > 0.3) {
				if (speed >= 1.1) {
					Debris.smash(level, this.getBoundingBox().getCenter().add(this.lastVelocity.normalize().scale(0.8)), speed, by);
				}
				this.shatter(level);
				return;
			}
			this.lastVelocity = this.getDeltaMovement();
			return;
		}
		// left lying (not held: held things have no gravity), it crumbles in a while
		if (!this.isNoGravity() && this.onGround()) {
			if (++this.idle > IDLE_TICKS) {
				this.shatter(level);
			}
		} else {
			this.idle = 0;
		}
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	public boolean isPickable() {
		return true;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		// a hard enough blow breaks it apart
		if (damage >= 4.0F && !this.isNoGravity()) {
			this.shatter(level);
			return true;
		}
		return false;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
	}

	/** For the renderer: the chunk's blocks, laid out around its middle. */
	public static AABB bounds(List<Piece> pieces) {
		AABB box = null;
		for (Piece p : pieces) {
			AABB one = new AABB(p.x(), p.y(), p.z(), p.x() + 1, p.y() + 1, p.z() + 1);
			box = box == null ? one : box.minmax(one);
		}
		return box == null ? new AABB(0, 0, 0, 1, 1, 1) : box;
	}
}
