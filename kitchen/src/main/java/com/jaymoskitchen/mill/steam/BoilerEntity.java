package com.jaymoskitchen.mill.steam;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleVariantStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A small boiler's workings (GT New Horizons picks, patch 8). While the firebox under it burns, its heat climbs; at
 * {@link #BOIL} the water boils, every bit of the fire's heat going into steam, so it stays there as long as there's
 * water. Steam builds up until the engine draws it ({@link #takeSteam}) or, full, the safety valve lifts with a scream
 * of steam. Boiled dry over the fire, the empty drum keeps heating and glows at {@link #RED_HOT}: water poured into it
 * then flashes to steam all at once and it bursts (a gentle explosion, {@link BoilerMetal#burst}; it breaks blocks
 * only where the mob griefing rule is on, as the pack has it). Let it cool below red-hot first and it fills safely.
 *
 * <p>Water is held in Fabric's transfer API ({@link #water}), so a pipe that speaks it can fill the boiler (never
 * empty it); a pipe filling a red-hot boiler bursts it too, on the next tick. Every rate is per tick, so extra ticks
 * (Jaymo's Powers' Time Bloom) only run it faster: it burns, boils and drinks quicker, as if time went faster.
 */
public class BoilerEntity extends BlockEntity {
	/** Droplets in one mB (Fabric's units), and in a bucket. */
	static final long MB = FluidConstants.BUCKET / 1000L, BUCKET = FluidConstants.BUCKET;
	/** The heat (a scale of our own) water boils at, the most a dry boiler reaches, and where a dry one glows. */
	static final float BOIL = 100.0F, HEAT_MOST = 500.0F, RED_HOT = 400.0F;
	/** Where it starts ticking and looks hot. */
	static final float HOT = 60.0F;
	/** Heat a burning firebox puts in a tick (times the metal's {@link BoilerMetal#conducts}), and heat lost a tick without it. */
	static final float FIRE_HEAT = 0.5F, COOLING = 0.25F;
	/** Heat it takes to boil a mB of water: so a copper boiler on a fire makes half a mB of steam a tick. */
	static final float HEAT_PER_MB = 1.0F;
	/** Without a fire, the share of its steam that's left after a tick (it condenses). */
	static final float CONDENSE = 0.999F;
	/** Less steam than this (mB) is none. */
	static final float STEAM_GONE = 0.01F;
	/** When the safety valve lifts it lets the steam down to this share of full. */
	static final float VALVE_TO = 0.8F;

	private float heat;
	/** Steam ready for the engine (mB of water boiled). */
	private float steam;
	/** A pipe poured water into it red-hot: it bursts on its next tick (not inside the pipe's transaction). */
	private boolean bursting;

	/** The water in it. Pipes may fill it, never empty it. */
	final SingleVariantStorage<FluidVariant> water = new SingleVariantStorage<>() {
		@Override
		protected FluidVariant getBlankVariant() {
			return FluidVariant.blank();
		}

		@Override
		protected long getCapacity(FluidVariant variant) {
			return metal().water * MB;
		}

		@Override
		protected boolean canInsert(FluidVariant variant) {
			return variant.isOf(Fluids.WATER);
		}

		@Override
		protected boolean canExtract(FluidVariant variant) {
			return false;
		}

		@Override
		protected void onFinalCommit() {
			if (heat >= RED_HOT) {
				bursting = true;
			}
			setChanged();
		}
	};

	public BoilerEntity(BlockPos pos, BlockState state) {
		super(Steam.BOILER_ENTITY, pos, state);
	}

	public BoilerMetal metal() {
		return getBlockState().getBlock() instanceof BoilerBlock block ? block.metal : BoilerMetal.COPPER;
	}

	/** Steam in it now, as a share of what it holds (the pressure). */
	public float pressure() {
		return steam / metal().steam;
	}

	/** The engine draws up to {@code amount} mB of steam; what it got. */
	public float takeSteam(float amount) {
		float took = Math.min(amount, steam);
		steam -= took;
		return took;
	}

	/** Pours in {@code droplets} of water: false (none poured) if it won't all fit. Red-hot, it bursts. */
	boolean pour(long droplets) {
		if (water.amount + droplets > water.getCapacity()) {
			return false;
		}
		if (heat >= RED_HOT && level instanceof ServerLevel server) {
			burst(server);
			return true;
		}
		water.variant = FluidVariant.of(Fluids.WATER);
		water.amount += droplets;
		if (heat >= BOIL) {
			// cold water on a hot drum
			level.playSound(null, worldPosition, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 0.5F, 1.4F);
		}
		setChanged();
		return true;
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, BoilerEntity boiler) {
		if (!(level instanceof ServerLevel server)) {
			return;
		}
		if (boiler.bursting) {
			boiler.burst(server);
			return;
		}
		BoilerMetal metal = boiler.metal();
		float heatWas = boiler.heat, steamWas = boiler.steam;
		long waterWas = boiler.water.amount;
		boolean fire = level.getBlockEntity(pos.below()) instanceof FireboxEntity firebox && firebox.burning();
		if (fire) {
			boiler.heat = Math.min(HEAT_MOST, boiler.heat + FIRE_HEAT * metal.conducts);
		} else {
			boiler.heat = Math.max(0.0F, boiler.heat - COOLING);
			boiler.steam = boiler.steam < STEAM_GONE ? 0.0F : boiler.steam * CONDENSE;
		}
		if (boiler.heat > BOIL && boiler.water.amount > 0) {
			// the heat over boiling goes into steam
			long boiled = Math.min(boiler.water.amount, (long) ((boiler.heat - BOIL) / HEAT_PER_MB * MB));
			boiler.water.amount -= boiled;
			boiler.steam += (float) boiled / MB;
			boiler.heat -= (float) boiled / MB * HEAT_PER_MB;
		}
		if (boiler.steam > metal.steam) {
			boiler.steam = metal.steam * VALVE_TO;
			level.playSound(null, pos, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 0.8F, 0.5F);
			server.sendParticles(ParticleTypes.CLOUD, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 12, 0.1, 0.05, 0.1, 0.12);
		}
		// a cold, idle boiler doesn't mark its chunk (nor wake comparators) every tick
		if (boiler.heat != heatWas || boiler.steam != steamWas || boiler.water.amount != waterWas) {
			boiler.setChanged();
		}
		boiler.show(state);
	}

	/** Keeps the gauge glass and the drum's look in step. */
	private void show(BlockState state) {
		float full = (float) water.amount / water.getCapacity();
		int gauge = water.amount <= 0 ? 0 : full < 0.34F ? 1 : full < 0.67F ? 2 : 3;
		int look = heat >= RED_HOT ? 2 : heat >= HOT ? 1 : 0;
		if (state.getValue(BoilerBlock.WATER) != gauge || state.getValue(BoilerBlock.HEAT) != look) {
			level.setBlock(worldPosition, state.setValue(BoilerBlock.WATER, gauge).setValue(BoilerBlock.HEAT, look), Block.UPDATE_CLIENTS);
		}
	}

	/** Water on a red-hot drum: it flashes to steam and the boiler bursts. */
	private void burst(ServerLevel level) {
		BlockPos pos = worldPosition;
		float power = metal().burst;
		level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		level.sendParticles(ParticleTypes.CLOUD, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 60, 1.0, 1.0, 1.0, 0.2);
		level.playSound(null, pos, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 2.0F, 0.6F);
		// breaks blocks where mob griefing is on (the pack keeps it on), like a creeper
		level.explode(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, power, Level.ExplosionInteraction.MOB);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		SingleVariantStorage.readValue(water, FluidVariant.CODEC, FluidVariant::blank, input);
		heat = input.getFloatOr("Heat", 0.0F);
		steam = input.getFloatOr("Steam", 0.0F);
		bursting = input.getBooleanOr("Bursting", false);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		SingleVariantStorage.writeValue(water, FluidVariant.CODEC, output);
		output.putFloat("Heat", heat);
		output.putFloat("Steam", steam);
		output.putBoolean("Bursting", bursting);
	}
}
