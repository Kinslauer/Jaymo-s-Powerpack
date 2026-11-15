package com.jaymoskitchen.mill.power;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A windsock's reading (GT New Horizons picks, patch 8): every couple of seconds the server reads the wind at its top
 * ({@link Wind}, the same wind the windmills turn in) and sends it to clients when it has changed, and clients ease the
 * sock round to it and flutter it. Sheltered from the sky, it feels no wind.
 */
public class WindsockEntity extends BlockEntity {
	/** Ticks between readings. */
	static final int READ_TICKS = 40;
	/** A change smaller than this (radians, strength) isn't sent. */
	static final float ANGLE_STEP = 0.05F, STRENGTH_STEP = 0.03F;
	/** Client side: the share of the gap to the reading the sock closes a tick. */
	static final float EASE = 0.05F;
	/** Client side: how fast the sock flutters (radians a tick) in a calm, and more for each unit of wind. */
	static final float FLUTTER_CALM = 0.15F, FLUTTER_WIND = 0.9F;

	private float angle, strength;
	private int ticks;

	/** Client side: the way the sock points (radians), how hard it's blown, and its flutter's phase (radians); now and last tick. */
	public float shownAngle, shownStrength, flutter, lastAngle, lastStrength, lastFlutter;

	public WindsockEntity(BlockPos pos, BlockState state) {
		super(Power.WINDSOCK_ENTITY, pos, state);
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, WindsockEntity sock) {
		if (++sock.ticks < READ_TICKS) {
			return;
		}
		sock.ticks = 0;
		boolean open = level.dimensionType().hasSkyLight() && !level.dimensionType().hasCeiling() && level.canSeeSky(pos.above());
		float now = open ? Wind.strength(level, pos) : 0.0F;
		float way = Wind.angle(level);
		if (Math.abs(now - sock.strength) > STRENGTH_STEP || Math.abs(Mth.wrapDegrees((way - sock.angle) * Mth.RAD_TO_DEG)) * Mth.DEG_TO_RAD > ANGLE_STEP) {
			sock.strength = now;
			sock.angle = way;
			sock.setChanged();
			level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
		}
	}

	public static void clientTick(Level level, BlockPos pos, BlockState state, WindsockEntity sock) {
		sock.lastAngle = sock.shownAngle;
		sock.lastStrength = sock.shownStrength;
		sock.lastFlutter = sock.flutter;
		// turn the short way round
		float gap = Mth.wrapDegrees((sock.angle - sock.shownAngle) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD;
		sock.shownAngle += gap * EASE;
		sock.shownStrength += (sock.strength - sock.shownStrength) * EASE;
		sock.flutter += FLUTTER_CALM + sock.shownStrength * FLUTTER_WIND;
		if (sock.flutter > Mth.TWO_PI * 64.0F) {
			sock.flutter -= Mth.TWO_PI * 64.0F;
			sock.lastFlutter -= Mth.TWO_PI * 64.0F;
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		angle = input.getFloatOr("Angle", 0.0F);
		strength = input.getFloatOr("Strength", 0.0F);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putFloat("Angle", angle);
		output.putFloat("Strength", strength);
	}

	@Override
	public ClientboundBlockEntityDataPacket getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
		output.putFloat("Angle", angle);
		output.putFloat("Strength", strength);
		return output.buildResult();
	}
}
