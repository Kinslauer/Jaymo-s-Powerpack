package com.jaymospowers.eden;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.AABB;

/**
 * The flesh of a brain tree, shot through with copper, iron or gold. In the storm country it builds up charge: now and
 * then a block of it lights ({@link #ACTIVE}) and lets go, shocking every living thing within {@link #REACH} blocks that
 * is not wearing metal ({@link #GROUNDING}: the charge runs down the armour into the ground). Sometimes a bolt shows
 * over it, for the look only.
 */
public class BrainBlock extends Block {
	// ---- tuning ----
	/** Share of random ticks on which a block lets go, how far the shock reaches, its damage, and how long the block stays lit. */
	public static final float DISCHARGE_CHANCE = 0.3F;
	public static final double REACH = 4.0;
	public static final float SHOCK = 3.0F;
	public static final int LIT_TICKS = 30;
	/** Share of discharges that show a bolt over the block (no fire, no damage). */
	public static final float BOLT_CHANCE = 0.12F;
	/** Light of a lit block. */
	public static final int LIT_LIGHT = 12;

	public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
	/** Armour that carries the charge to ground. */
	public static final TagKey<Item> GROUNDING = TagKey.create(net.minecraft.core.registries.Registries.ITEM, com.jaymospowers.JaymosPowers.id("grounding_armor"));
	private static final EquipmentSlot[] ARMOUR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	public BrainBlock(BlockBehaviour.Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(ACTIVE, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(ACTIVE);
	}

	/** Whether the charge passes {@code entity} by. */
	public static boolean grounded(LivingEntity entity) {
		for (EquipmentSlot slot : ARMOUR) {
			if (entity.getItemBySlot(slot).is(GROUNDING)) {
				return true;
			}
		}
		return false;
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (state.getValue(ACTIVE) || random.nextFloat() >= DISCHARGE_CHANCE) {
			return;
		}
		level.setBlock(pos, state.setValue(ACTIVE, true), Block.UPDATE_CLIENTS);
		level.scheduleTick(pos, this, LIT_TICKS);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 12, 0.6, 0.6, 0.6, 0.15);
		boolean struck = false;
		for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, new AABB(pos).inflate(REACH), LivingEntity::isAlive)) {
			if (!grounded(living) && !living.isInvulnerable()) {
				living.hurtServer(level, level.damageSources().lightningBolt(), SHOCK);
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, living.getX(), living.getY(0.5), living.getZ(), 8, 0.3, 0.5, 0.3, 0.1);
				struck = true;
			}
		}
		if (struck) {
			level.playSound(null, pos, SoundEvents.TRIDENT_THUNDER.value(), SoundSource.BLOCKS, 0.5F, 1.6F + random.nextFloat() * 0.3F);
		}
		if (random.nextFloat() < BOLT_CHANCE && level.canSeeSky(pos.above())) {
			LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
			if (bolt != null) {
				bolt.snapTo(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
				bolt.setVisualOnly(true);
				level.addFreshEntity(bolt);
			}
		}
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (state.getValue(ACTIVE)) {
			level.setBlock(pos, state.setValue(ACTIVE, false), Block.UPDATE_CLIENTS);
		}
	}
}
