package com.jaymoskitchen.mill.steam;

import com.jaymoskitchen.mill.power.ShaftDriven;
import com.jaymoskitchen.mill.power.Shafts;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
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
import org.jspecify.annotations.Nullable;

/**
 * A steam engine's workings (GT New Horizons picks, patch 8). Each tick it draws steam from the boiler it touches (any
 * side but its front): the higher the pressure, the faster it runs ({@link BoilerMetal#rateAtFull} at full), and the
 * faster it runs the more steam it drinks ({@link #STEAM_PER_RATE}), so it settles where the fire's steam keeps up with
 * it. About once a second it turns the shaft out of its front at its average speed, like a windmill
 * ({@link ShaftDriven#turnFromShaft}). Clients turn the flywheel at the synced speed and puff and chuff with each stroke.
 */
public class SteamEngineEntity extends BlockEntity {
	/** Ticks between turns of the shaft; the machine is kept working a little longer than this so it doesn't stutter. */
	static final int CHECK_TICKS = 20, DRIVE_TICKS = CHECK_TICKS + 10;
	/** Steam (mB) a tick for each unit of speed: a copper boiler's fire (half a mB a tick) keeps it at speed 1. */
	static final float STEAM_PER_RATE = 0.5F;
	/** Below this speed the pressure doesn't move the piston: it waits for steam. */
	static final float MIN_RATE = 0.2F;
	/** A change of speed smaller than this isn't sent to clients. */
	static final float SPEED_STEP = 0.05F;
	/** Degrees the flywheel turns a tick at speed 1. */
	public static final float DEGREES_PER_TICK = 9.0F;

	private float speed;
	private float work;
	private int ticks, worked;
	private List<BlockPos> turning = new ArrayList<>();

	/** Client side: the flywheel's angle, now and last tick. */
	public float angle, lastAngle;

	public SteamEngineEntity(BlockPos pos, BlockState state) {
		super(Steam.ENGINE_ENTITY, pos, state);
	}

	public Direction facing() {
		return getBlockState().hasProperty(SteamEngineBlock.FACING) ? getBlockState().getValue(SteamEngineBlock.FACING) : Direction.NORTH;
	}

	public float speed() {
		return speed;
	}

	/** The boiler it's set on or beside, if any. */
	private @Nullable BoilerEntity boiler() {
		for (Direction side : Direction.values()) {
			if (side == facing()) {
				continue;
			}
			BlockPos at = worldPosition.relative(side);
			if (com.jaymoskitchen.Loaded.made(level, at) && level.getBlockEntity(at) instanceof BoilerEntity boiler) {
				return boiler;
			}
		}
		return null;
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, SteamEngineEntity engine) {
		BoilerEntity boiler = engine.boiler();
		float rate = 0.0F;
		if (boiler != null) {
			rate = boiler.metal().rateAtFull * boiler.pressure();
			rate = rate < MIN_RATE ? 0.0F : boiler.takeSteam(rate * STEAM_PER_RATE) / STEAM_PER_RATE;
		}
		engine.work += rate;
		engine.worked++;
		if (++engine.ticks >= CHECK_TICKS) {
			engine.ticks = 0;
			engine.drive(engine.work / engine.worked);
			engine.work = 0.0F;
			engine.worked = 0;
		}
	}

	/** Turns the shaft out of the front at {@code rate} (0: still). */
	private void drive(float rate) {
		List<BlockPos> run = new ArrayList<>();
		ShaftDriven machine = Shafts.follow(level, worldPosition, facing(), run);
		if (rate > 0.0F && machine != null) {
			machine.turnFromShaft(rate, DRIVE_TICKS);
		}
		List<BlockPos> turnNow = rate > 0.0F ? run : List.of();
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
		}
		if (Math.abs(rate - speed) > SPEED_STEP || (rate == 0.0F) != (speed == 0.0F)) {
			speed = rate;
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
		}
		setChanged();
	}

	public static void clientTick(Level level, BlockPos pos, BlockState state, SteamEngineEntity engine) {
		engine.lastAngle = engine.angle;
		engine.angle += engine.speed * DEGREES_PER_TICK;
		// a stroke each half turn: a puff of steam from the exhaust and a chuff
		if (engine.speed > 0.0F && (int) (engine.angle / 180.0F) != (int) (engine.lastAngle / 180.0F)) {
			Direction back = engine.facing().getOpposite();
			double x = pos.getX() + 0.5 + back.getStepX() * 0.3, y = pos.getY() + 1.0, z = pos.getZ() + 0.5 + back.getStepZ() * 0.3;
			for (int i = 0; i < 3; i++) {
				level.addParticle(ParticleTypes.CLOUD, x, y, z, (level.getRandom().nextDouble() - 0.5) * 0.05, 0.06, (level.getRandom().nextDouble() - 0.5) * 0.05);
			}
			level.playLocalSound(x, y, z, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.12F, 1.6F + level.getRandom().nextFloat() * 0.2F, false);
			level.playLocalSound(x, y, z, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 0.08F, 0.6F, false);
		}
		if (engine.angle >= 360.0F) {
			engine.angle -= 360.0F;
			engine.lastAngle -= 360.0F;
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
