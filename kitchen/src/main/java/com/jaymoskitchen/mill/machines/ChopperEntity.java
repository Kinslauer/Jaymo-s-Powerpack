package com.jaymoskitchen.mill.machines;

import com.jaymoskitchen.JaymosKitchen;
import com.jaymoskitchen.mill.MachineRecipes;
import com.jaymoskitchen.mill.power.ShaftDriven;
import com.jaymoskitchen.tanning.Tanning;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;

/**
 * A bloody chopper's workings (after Better With Mods' saw, MIT; our own code). Only while a shaft turns it: a log in
 * front is sawn into {@link MachineRecipes#chop the horse chopper's planks} (6) and a piece of {@link Tanning#BARK bark},
 * and anything living against the blade is cut ({@link #CHOPPER} damage) and bleeds.
 */
public class ChopperEntity extends BlockEntity implements ShaftDriven {
	/** "%1$s was cut to pieces by a bloody chopper". */
	public static final ResourceKey<DamageType> CHOPPER = ResourceKey.create(Registries.DAMAGE_TYPE, JaymosKitchen.id("bloody_chopper"));
	/** Damage per cut, and ticks between cuts while something is against the blade. */
	static final float CHOPPER_DAMAGE = 4.0F;
	static final int CHOPPER_HURT_TICKS = 10;
	/** Ticks to saw through a log at rate 1 (a windmill in clear weather). */
	static final int CUT_TICKS = 30;
	/** Bark from a log that still has its bark on. */
	static final int CHOPPER_BARK = 1;
	/** How far in front of the box the blade's reach goes (it sticks out 3 pixels in its own block). */
	static final double BLADE_REACH = 0.2;

	private int shaftTicks;
	private float shaftRate;
	/** Sawing done on the log in front, 0 to CUT_TICKS. */
	private float cut;
	private int hurtCooldown;

	public ChopperEntity(BlockPos pos, BlockState state) {
		super(PowerMachines.CHOPPER_ENTITY, pos, state);
	}

	private Direction facing() {
		return getBlockState().hasProperty(ChopperBlock.FACING) ? getBlockState().getValue(ChopperBlock.FACING) : Direction.NORTH;
	}

	@Override
	public boolean takesShaftFrom(Direction side) {
		// any side but the blade's
		return side != facing();
	}

	@Override
	public void turnFromShaft(float rate, int ticks) {
		shaftRate = rate;
		shaftTicks = Math.max(shaftTicks, ticks);
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, ChopperEntity chopper) {
		if (level instanceof ServerLevel server) {
			chopper.tick(server, state);
		}
	}

	private void tick(ServerLevel level, BlockState state) {
		boolean turning = shaftTicks > 0;
		if (state.hasProperty(ChopperBlock.TURNING) && state.getValue(ChopperBlock.TURNING) != turning) {
			level.setBlock(worldPosition, state.setValue(ChopperBlock.TURNING, turning), Block.UPDATE_CLIENTS);
		}
		if (!turning) {
			cut = 0.0F;
			return;
		}
		shaftTicks--;
		if (hurtCooldown > 0) {
			hurtCooldown--;
		} else if (hurtAgainstBlade(level)) {
			hurtCooldown = CHOPPER_HURT_TICKS;
		}
		BlockPos front = worldPosition.relative(facing());
		BlockState log = level.getBlockState(front);
		if (!log.is(BlockTags.LOGS)) {
			cut = 0.0F;
			return;
		}
		cut += Math.max(shaftRate, 0.5F);
		if (cut >= CUT_TICKS) {
			cut = 0.0F;
			saw(level, front, log);
		}
		setChanged();
	}

	/** Saws the log in front into planks and bark. */
	private void saw(ServerLevel level, BlockPos front, BlockState log) {
		ItemStack planks = MachineRecipes.chop(new ItemStack(log.getBlock().asItem()), true);
		if (planks == null) {
			return;
		}
		String name = BuiltInRegistries.BLOCK.getKey(log.getBlock()).getPath();
		level.destroyBlock(front, false);
		level.playSound(null, front, SoundEvents.AXE_STRIP.value(), SoundSource.BLOCKS, 1.0F, 0.7F);
		Block.popResourceFromFace(level, worldPosition, facing(), planks);
		if (!name.startsWith("stripped_") && !name.contains("bamboo")) {
			Block.popResourceFromFace(level, worldPosition, facing(), new ItemStack(Tanning.BARK, CHOPPER_BARK));
		}
	}

	/** Cuts everything living against the blade; true if it cut anything. */
	private boolean hurtAgainstBlade(ServerLevel level) {
		Direction facing = facing();
		double inside = 3.0 / 16.0;
		AABB block = new AABB(worldPosition);
		// the blade's slice of its own block, then a little way out in front
		AABB blade = block.move(facing.getStepX() * (1.0 - inside), facing.getStepY() * (1.0 - inside), facing.getStepZ() * (1.0 - inside))
			.intersect(block).expandTowards(facing.getStepX() * BLADE_REACH, facing.getStepY() * BLADE_REACH, facing.getStepZ() * BLADE_REACH);
		boolean any = false;
		DamageSource source = level.damageSources().source(CHOPPER);
		BlockParticleOption blood = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState());
		for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, blade, LivingEntity::isAlive)) {
			if (victim.hurtServer(level, source, CHOPPER_DAMAGE)) {
				any = true;
				level.sendParticles(blood, victim.getX(), victim.getY() + victim.getBbHeight() * 0.5, victim.getZ(), 14, 0.2, 0.25, 0.2, 0.1);
			}
		}
		return any;
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		shaftTicks = input.getIntOr("ShaftTicks", 0);
		shaftRate = input.getFloatOr("ShaftRate", 0.0F);
		cut = input.getFloatOr("Cut", 0.0F);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putInt("ShaftTicks", shaftTicks);
		output.putFloat("ShaftRate", shaftRate);
		output.putFloat("Cut", cut);
	}
}
