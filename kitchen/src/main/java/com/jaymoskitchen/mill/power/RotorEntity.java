package com.jaymoskitchen.mill.power;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A windmill's or waterwheel's workings (after Better With Mods, MIT; our own code). About once a second it checks
 * whether it can turn ({@link Rotor#speed}), follows its shaft ({@link Shafts#follow}), sets the shaft turning and
 * keeps the machine at the end working ({@link ShaftDriven#turnFromShaft}). Clients just spin it at the synced speed.
 */
public class RotorEntity extends BlockEntity {
	/** Ticks between checks; the machine is kept working a little longer than this so it doesn't stutter. */
	static final int CHECK_TICKS = 20, DRIVE_TICKS = CHECK_TICKS + 10;
	/** One in this many checks while turning, a creak. */
	static final int CREAK_CHANCE = 6;

	/** Signed speed (0 = still), synced to clients. */
	private float speed;
	private int ticks;
	/** The shaft pieces set turning last time, to set still again when the run changes or it stops. */
	private List<BlockPos> turning = new ArrayList<>();

	/** Client side: the angle shown, now and last tick. */
	public float angle, lastAngle;

	public RotorEntity(BlockPos pos, BlockState state) {
		super(Power.ROTOR_ENTITY, pos, state);
	}

	public Rotor rotor() {
		return getBlockState().getBlock() instanceof RotorBlock block ? block.rotor : Rotor.WINDMILL;
	}

	/** The way its shaft runs from the hub. */
	public Direction facing() {
		return getBlockState().hasProperty(RotorBlock.FACING) ? getBlockState().getValue(RotorBlock.FACING) : Direction.NORTH;
	}

	public float speed() {
		return speed;
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, RotorEntity rotor) {
		// spread the checks out over the second
		if ((level.getGameTime() + pos.asLong()) % CHECK_TICKS == 0 && level instanceof ServerLevel server) {
			rotor.check(server);
		}
	}

	public static void clientTick(Level level, BlockPos pos, BlockState state, RotorEntity rotor) {
		rotor.lastAngle = rotor.angle;
		rotor.angle += rotor.speed * rotor.rotor().degreesPerTick;
		if (rotor.angle >= 360.0F || rotor.angle <= -360.0F) {
			rotor.angle %= 360.0F;
			rotor.lastAngle = rotor.angle - rotor.speed * rotor.rotor().degreesPerTick;
		}
	}

	private void check(ServerLevel level) {
		float now = rotor().speed(level, worldPosition, facing());
		List<BlockPos> run = new ArrayList<>();
		ShaftDriven machine = Shafts.follow(level, worldPosition, facing(), run);
		if (now != 0.0F && machine != null) {
			machine.turnFromShaft(Math.abs(now), DRIVE_TICKS);
		}
		List<BlockPos> turnNow = now != 0.0F ? run : List.of();
		for (BlockPos old : turning) {
			if (!turnNow.contains(old)) {
				Shafts.setTurning(level, old, false);
			}
		}
		for (BlockPos piece : turnNow) {
			Shafts.setTurning(level, piece, true);
		}
		if (!turning.equals(turnNow)) {
			turning = new ArrayList<>(turnNow);
			setChanged();
		}
		if (now != speed) {
			speed = now;
			setChanged();
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
		}
		if (now != 0.0F && level.getRandom().nextInt(CREAK_CHANCE) == 0) {
			level.playSound(null, worldPosition, SoundEvents.WOOD_STEP, SoundSource.BLOCKS, 0.4F, 0.5F + level.getRandom().nextFloat() * 0.2F);
		}
	}

	/** Taken down: its shaft stops. */
	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		if (level != null && !level.isClientSide()) {
			for (BlockPos piece : turning) {
				Shafts.setTurning(level, piece, false);
			}
			turning.clear();
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		speed = input.getFloatOr("Speed", 0.0F);
		turning = new ArrayList<>(input.read("Turning", BlockPos.CODEC.listOf()).orElse(List.of()));
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putFloat("Speed", speed);
		output.store("Turning", BlockPos.CODEC.listOf(), turning);
	}

	@Override
	public ClientboundBlockEntityDataPacket getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
		output.putFloat("Speed", speed);
		return output.buildResult();
	}
}
