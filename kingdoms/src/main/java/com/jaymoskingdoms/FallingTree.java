package com.jaymoskingdoms;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.9) A felled tree toppling over, the way Dynamic Trees' do (our own code): the moment it's cut, its logs and the
 * leaves they held leave the world and become this, hinged at the cut. It tips away from whoever cut it, slowly at
 * first and faster as it goes over (a falling pole: its swing grows with how far it leans, and a tall tree swings
 * slower than a short one), until its wood strikes the ground or it lies flat. Where it lands it breaks apart with a
 * crash: every log and leaf gives what it would have, where it came to rest (or into a woodcutter's bag), and anything
 * standing where it comes down is struck.
 */
public final class FallingTree extends Entity {
	/** Its first push (degrees a tick), a steady nudge, and the pull of its own weight (scaled by how far it leans). */
	static final float START_SPEED = 0.3F, NUDGE = 0.03F, WEIGHT = 4.0F;
	/** It lies flat at this lean; it never takes longer than this to come down (ticks). */
	static final float FLAT = 90.0F;
	static final int LONGEST = 200;
	/** Down, it lies a moment (ticks) before it breaks apart. */
	static final int LIES = 14;
	/** What it strikes where it lands: the most damage, and a share of it per log. */
	static final float CRUSH_MOST = 10.0F, CRUSH_PER_LOG = 0.6F;
	/** Of its blocks, every this-many'th log and leaf breaks with a puff and a sound (the rest only a puff of chips). */
	static final int LOG_SOUNDS = 3, LEAF_SOUNDS = 9;

	/** One block of the tree: its state, and where it stood relative to the cut. */
	public record Piece(int state, int x, int y, int z) {
		public static final StreamCodec<ByteBuf, Piece> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Piece::state,
			ByteBufCodecs.VAR_INT, Piece::x, ByteBufCodecs.VAR_INT, Piece::y, ByteBufCodecs.VAR_INT, Piece::z, Piece::new);

		public BlockState blockState() {
			return Block.stateById(this.state);
		}
	}

	public static final AttachmentType<List<Piece>> PIECES = AttachmentRegistry.create(JaymosKingdoms.id("falling_tree_pieces"),
		builder -> builder.syncWith(Piece.STREAM_CODEC.apply(ByteBufCodecs.list()), AttachmentSyncPredicate.all()));
	/** Which way it falls (radians: 0 is east, along +x; a quarter turn is south, +z). */
	public static final AttachmentType<Float> HEADING = AttachmentRegistry.create(JaymosKingdoms.id("falling_tree_heading"),
		builder -> builder.syncWith(ByteBufCodecs.FLOAT, AttachmentSyncPredicate.all()));
	/** How far over it leans (degrees from upright). */
	private static final EntityDataAccessor<Float> LEAN = SynchedEntityData.defineId(FallingTree.class, EntityDataSerializers.FLOAT);

	public static EntityType<FallingTree> TYPE;

	/** Client side: the lean last tick, to draw it smoothly between ticks. */
	public float leanO;
	private float speed = START_SPEED;
	/** When it came down (-1: still falling). */
	private int downAt = -1;
	private float reach = 2.0F;
	private int logs;
	private @Nullable Entity cutter;
	private ItemStack tool = ItemStack.EMPTY;
	private @Nullable Consumer<ItemStack> sink;

	public FallingTree(EntityType<? extends FallingTree> type, Level level) {
		super(type, level);
		this.noPhysics = true;
	}

	static void register() {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id("falling_tree"));
		TYPE = Registry.register(BuiltInRegistries.ENTITY_TYPE, key, EntityType.Builder.<FallingTree>of(FallingTree::new, MobCategory.MISC)
			.noLootTable()
			.sized(1.0F, 1.0F)
			.clientTrackingRange(10)
			.updateInterval(1)
			.build(key));
	}

	/**
	 * The tree whose {@code logs} and {@code leaves} stood on {@code cut} comes down toward {@code heading}: they leave
	 * the world now, and fall as one. What it gives goes to {@code sink} (null: where each block comes to rest).
	 */
	static void topple(ServerLevel level, BlockPos cut, List<BlockPos> logs, List<BlockPos> leaves, float heading, @Nullable Entity cutter,
		ItemStack tool, @Nullable Consumer<ItemStack> sink) {
		FallingTree tree = new FallingTree(TYPE, level);
		tree.setPos(cut.getX() + 0.5, cut.getY(), cut.getZ() + 0.5);
		List<Piece> pieces = new ArrayList<>();
		int top = 1;
		for (List<BlockPos> part : List.of(logs, leaves)) {
			for (BlockPos p : part) {
				BlockState state = level.getBlockState(p);
				if (state.isAir()) {
					continue;
				}
				pieces.add(new Piece(Block.getId(state), p.getX() - cut.getX(), p.getY() - cut.getY(), p.getZ() - cut.getZ()));
				top = Math.max(top, p.getY() - cut.getY() + 1);
			}
		}
		if (pieces.isEmpty()) {
			return;
		}
		// they leave the world all at once (the leaves first, so none decay as the wood goes)
		for (int i = pieces.size() - 1; i >= 0; i--) {
			Piece p = pieces.get(i);
			level.setBlock(cut.offset(p.x(), p.y(), p.z()), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
		}
		for (Piece p : pieces) {
			BlockPos at = cut.offset(p.x(), p.y(), p.z());
			level.updateNeighborsAt(at, Blocks.AIR);
		}
		tree.setAttached(PIECES, List.copyOf(pieces));
		tree.setAttached(HEADING, heading);
		tree.reach = Math.max(2.0F, top * 0.55F);
		tree.logs = logs.size();
		tree.cutter = cutter;
		tree.tool = tool.copy();
		tree.sink = sink;
		level.addFreshEntity(tree);
		level.playSound(null, cut, SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 1.4F, 0.5F);
	}

	/** The way from {@code from} to {@code cut} (radians, as {@link #HEADING}), or any way if there's no one. */
	static float away(@Nullable Entity from, BlockPos cut, ServerLevel level) {
		if (from == null) {
			return level.getRandom().nextFloat() * Mth.TWO_PI;
		}
		double dx = cut.getX() + 0.5 - from.getX(), dz = cut.getZ() + 0.5 - from.getZ();
		return dx * dx + dz * dz < 1.0E-4 ? from.getYRot() * Mth.DEG_TO_RAD + Mth.HALF_PI : (float)Math.atan2(dz, dx);
	}

	public float lean() {
		return this.entityData.get(LEAN);
	}

	public List<Piece> pieces() {
		List<Piece> pieces = this.getAttached(PIECES);
		return pieces == null ? List.of() : pieces;
	}

	public float heading() {
		Float h = this.getAttached(HEADING);
		return h == null ? 0.0F : h;
	}

	/** The turn of a tree leaning {@code lean} degrees toward {@code heading}: upright tips over toward the heading. */
	public static Quaternionf turn(float lean, float heading) {
		float dx = Mth.cos(heading), dz = Mth.sin(heading);
		return new Quaternionf().rotateAxis(lean * Mth.DEG_TO_RAD, dz, 0.0F, -dx);
	}

	/** Where a piece's middle is with the tree leaning {@code lean}. */
	private Vec3 where(Piece p, Quaternionf turn) {
		Vector3f v = turn.transform(new Vector3f(p.x(), p.y() + 0.5F, p.z()));
		return this.position().add(v.x, v.y, v.z);
	}

	@Override
	public void tick() {
		this.leanO = this.lean();
		super.tick();
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		List<Piece> pieces = this.pieces();
		if (pieces.isEmpty()) {
			this.discard();
			return;
		}
		if (this.downAt >= 0) {
			if (this.tickCount - this.downAt >= LIES) {
				this.breakApart(level, pieces);
			}
			return;
		}
		float lean = this.lean();
		this.speed += NUDGE + WEIGHT * Mth.sin(lean * Mth.DEG_TO_RAD) / this.reach;
		float next = Math.min(FLAT, lean + this.speed);
		boolean landed = next >= FLAT || this.tickCount >= LONGEST || this.strikes(level, pieces, next);
		if (!landed) {
			this.entityData.set(LEAN, next);
			// the rush of air and creaking as it goes over
			if (this.tickCount % 6 == 0) {
				level.playSound(null, this.blockPosition(), SoundEvents.WOOD_STEP, SoundSource.BLOCKS, 0.8F, 0.5F + lean / 180.0F);
			}
			return;
		}
		this.land(level, pieces, this.strikes(level, pieces, next) ? lean : next);
		this.downAt = this.tickCount;
	}

	/** Whether its wood, leaning {@code lean}, runs into anything solid (beyond its own foot). */
	private boolean strikes(ServerLevel level, List<Piece> pieces, float lean) {
		Quaternionf turn = turn(lean, this.heading());
		for (Piece p : pieces) {
			if (p.y() < 2 || !p.blockState().is(BlockTags.LOGS)) {
				continue;
			}
			BlockPos at = BlockPos.containing(this.where(p, turn));
			BlockState there = level.getBlockState(at);
			if (!there.isAir() && !there.canBeReplaced() && !(there.getBlock() instanceof LeavesBlock) && there.isCollisionShapeFullBlock(level, at)) {
				return true;
			}
		}
		return false;
	}

	/** It comes down: a thud and a cloud of leaves, and anything under it is struck. */
	private void land(ServerLevel level, List<Piece> pieces, float lean) {
		this.entityData.set(LEAN, lean);
		Quaternionf turn = turn(lean, this.heading());
		Set<LivingEntity> struck = new HashSet<>();
		Vec3 middle = Vec3.ZERO;
		int count = 0;
		for (Piece p : pieces) {
			if (!p.blockState().is(BlockTags.LOGS)) {
				continue;
			}
			Vec3 at = this.where(p, turn);
			middle = middle.add(at);
			count++;
			struck.addAll(level.getEntitiesOfClass(LivingEntity.class, new AABB(BlockPos.containing(at)).inflate(0.3),
				e -> e.isAlive() && e != this.cutter && !e.isSpectator()));
		}
		float crush = Math.min(CRUSH_MOST, 2.0F + CRUSH_PER_LOG * this.logs);
		DamageSource source = level.damageSources().fallingBlock(this);
		for (LivingEntity e : struck) {
			e.hurtServer(level, source, crush);
		}
		if (count > 0) {
			middle = middle.scale(1.0 / count);
			level.playSound(null, middle.x, middle.y, middle.z, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.BLOCKS, 1.2F, 0.5F);
			level.playSound(null, middle.x, middle.y, middle.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 0.4F, 1.7F);
			level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, middle.x, middle.y, middle.z, 6, 1.5, 0.2, 1.5, 0.01);
		}
	}

	/** Lain a moment, it breaks apart where it lies, giving what it would have. */
	private void breakApart(ServerLevel level, List<Piece> pieces) {
		Quaternionf turn = turn(this.lean(), this.heading());
		int logCount = 0, leafCount = 0;
		for (Piece p : pieces) {
			BlockState state = p.blockState();
			Vec3 at = this.where(p, turn);
			BlockPos pos = BlockPos.containing(at);
			boolean log = state.is(BlockTags.LOGS);
			for (ItemStack drop : Block.getDrops(state, level, pos, null, this.cutter, this.tool)) {
				if (this.sink != null) {
					this.sink.accept(drop);
				} else {
					Block.popResource(level, pos, drop);
				}
			}
			if (log ? logCount++ % LOG_SOUNDS == 0 : leafCount++ % LEAF_SOUNDS == 0) {
				level.levelEvent(2001, pos, Block.getId(state));
			} else {
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), at.x, at.y, at.z, 3, 0.3, 0.3, 0.3, 0.05);
			}
		}
		this.discard();
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		return false;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		entityData.define(LEAN, 0.0F);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
	}
}
