package com.jaymospowers.aether;

import com.jaymospowers.JaymosPowers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A gravitite block on its way up (see {@link FloatingBlock}): falling-sand physics with the sign flipped. When
 * something stops it (a ceiling, the world's top) or it has flown for {@link #MAX_AGE} ticks it becomes a block again
 * where it is (or an item, for the redstone-driven kind, or if the spot is taken).
 */
public class FloatingBlockEntity extends Entity {
	// ---- tuning ----
	/** Lift each tick (blocks a tick squared, falling sand's gravity), and the share of speed kept (its air drag). */
	public static final double LIFT = 0.04, DRAG = 0.98;
	/** Gives up and lands after this long (ticks): half a minute. */
	public static final int MAX_AGE = 600;

	private static final EntityDataAccessor<Integer> DATA_STATE = SynchedEntityData.defineId(FloatingBlockEntity.class, EntityDataSerializers.INT);

	public static EntityType<FloatingBlockEntity> TYPE;

	/** Lands as an item, not a block. */
	private boolean asItem;

	public FloatingBlockEntity(EntityType<? extends FloatingBlockEntity> type, Level level) {
		super(type, level);
	}

	static void register() {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JaymosPowers.id("floating_block"));
		TYPE = Registry.register(BuiltInRegistries.ENTITY_TYPE, key, EntityType.Builder.<FloatingBlockEntity>of(FloatingBlockEntity::new, MobCategory.MISC)
			.noLootTable()
			.sized(0.98F, 0.98F)
			.clientTrackingRange(10)
			.updateInterval(1)
			.build(key));
	}

	/** One lifting off from {@code pos} (the block there already cleared). */
	static FloatingBlockEntity create(ServerLevel level, BlockPos pos, BlockState state, boolean asItem) {
		FloatingBlockEntity entity = new FloatingBlockEntity(TYPE, level);
		entity.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
		entity.entityData.set(DATA_STATE, Block.getId(state));
		entity.asItem = asItem;
		return entity;
	}

	public BlockState blockState() {
		return Block.stateById(this.entityData.get(DATA_STATE));
	}

	@Override
	public void tick() {
		super.tick();
		this.setDeltaMovement(this.getDeltaMovement().add(0.0, LIFT, 0.0));
		this.move(MoverType.SELF, this.getDeltaMovement());
		this.setDeltaMovement(this.getDeltaMovement().scale(DRAG));
		if (this.level() instanceof ServerLevel level && (this.verticalCollision || this.tickCount > MAX_AGE || this.getY() + 1.0 >= level.getMaxY())) {
			this.land(level);
		}
	}

	private void land(ServerLevel level) {
		BlockState state = this.blockState();
		BlockPos pos = this.blockPosition();
		this.discard();
		if (!this.asItem && FloatingBlock.free(level.getBlockState(pos))) {
			level.setBlock(pos, state, Block.UPDATE_ALL);
		} else {
			Block.popResource(level, pos, new ItemStack(state.getBlock()));
		}
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		return false;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		entityData.define(DATA_STATE, 0);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		Block block = BuiltInRegistries.BLOCK.getValue(Identifier.parse(input.getStringOr("block", "minecraft:air")));
		this.entityData.set(DATA_STATE, Block.getId(block.defaultBlockState()));
		this.asItem = input.getBooleanOr("as_item", false);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		output.putString("block", BuiltInRegistries.BLOCK.getKey(this.blockState().getBlock()).toString());
		output.putBoolean("as_item", this.asItem);
	}
}
