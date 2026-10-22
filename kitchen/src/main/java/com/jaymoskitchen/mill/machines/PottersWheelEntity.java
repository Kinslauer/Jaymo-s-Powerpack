package com.jaymoskitchen.mill.machines;

import com.jaymoskitchen.mill.power.ShaftDriven;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A potter's wheel's workings (after Better With Mods' turntable, MIT; our own code). It turns while a shaft drives it
 * ({@link ShaftDriven}, from below) or for a few turns after a hand spins it. Each full turn is a step on the clay
 * block on top, with clay crumbs and a squelch; after the entry's steps it becomes the pot. Clients spin the wheel at
 * the synced speed.
 */
public class PottersWheelEntity extends BlockEntity implements ShaftDriven {
	/** Ticks a full turn takes at speed 1 (a hand, or a windmill in clear weather). */
	static final int TURN_TICKS = 20;
	/** Turns a hand's spin adds, and the most turns it can be ahead. */
	static final int HAND_TURNS = 3, HAND_MAX_TURNS = 6;
	/** Hunger a hand's spin costs (sprinting a block costs 0.1). */
	static final float HAND_EXHAUSTION = 0.4F;
	/** Fastest a shaft turns it, in turns per TURN_TICKS. */
	static final float MAX_SHAFT_SPEED = 2.0F;

	private int handTicks;
	private int shaftTicks;
	private float shaftRate;
	/** Part of the current turn done, 0 to TURN_TICKS. */
	private float turn;
	/** The block on top being thrown, and the steps done on it. */
	private Identifier throwing;
	private int steps;
	/** Which of the top block's entries to throw (sneak-use steps through them). */
	private int pick;
	/** Synced: how fast it turns now (0 = still). */
	private float speed;

	/** Client side: the angle shown, now and last tick. */
	public float angle, lastAngle;

	public PottersWheelEntity(BlockPos pos, BlockState state) {
		super(PowerMachines.POTTERS_WHEEL_ENTITY, pos, state);
	}

	@Override
	public boolean takesShaftFrom(Direction side) {
		return side == Direction.DOWN;
	}

	@Override
	public void turnFromShaft(float rate, int ticks) {
		shaftRate = Math.min(rate, MAX_SHAFT_SPEED);
		shaftTicks = Math.max(shaftTicks, ticks);
	}

	/** Used with an empty hand: a few more turns, for a little hunger. */
	void spinByHand(Player player) {
		if (handTicks >= (HAND_MAX_TURNS - HAND_TURNS) * TURN_TICKS) {
			return;
		}
		handTicks += HAND_TURNS * TURN_TICKS;
		player.causeFoodExhaustion(HAND_EXHAUSTION);
		level.playSound(null, worldPosition, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 0.4F, 1.4F);
		setChanged();
	}

	/** Sneak-used: throw the clay on top into the next thing it can become. */
	void nextPick(Player player) {
		Block top = level.getBlockState(worldPosition.above()).getBlock();
		List<PottersWheelRecipes.Entry> options = PottersWheelRecipes.forInput(top);
		if (options.isEmpty()) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.potters_wheel.nothing"));
			return;
		}
		Identifier id = BuiltInRegistries.BLOCK.getKey(top);
		pick = id.equals(throwing) ? (pick + 1) % options.size() : 1 % options.size();
		throwing = id;
		steps = 0;
		PottersWheelRecipes.Entry entry = options.get(pick);
		player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.potters_wheel.throwing",
			entry.outputBlock().map(Block::getName).orElse(Component.empty())));
		setChanged();
	}

	public float speed() {
		return speed;
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, PottersWheelEntity wheel) {
		if (level instanceof ServerLevel server) {
			wheel.tick(server);
		}
	}

	public static void clientTick(Level level, BlockPos pos, BlockState state, PottersWheelEntity wheel) {
		wheel.lastAngle = wheel.angle;
		wheel.angle += wheel.speed * 360.0F / TURN_TICKS;
		if (wheel.angle >= 360.0F) {
			wheel.angle -= 360.0F;
			wheel.lastAngle -= 360.0F;
		}
	}

	private void tick(ServerLevel level) {
		float now = 0.0F;
		if (shaftTicks > 0) {
			shaftTicks--;
			now = shaftRate;
		}
		if (handTicks > 0) {
			handTicks--;
			now = Math.max(now, 1.0F);
		}
		if (now != speed) {
			speed = now;
			setChanged();
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
		}
		if (now <= 0.0F) {
			return;
		}
		turn += now;
		if (turn >= TURN_TICKS) {
			turn -= TURN_TICKS;
			step(level);
		}
	}

	/** One full turn: a step on the clay on top. */
	private void step(ServerLevel level) {
		BlockPos above = worldPosition.above();
		BlockState top = level.getBlockState(above);
		List<PottersWheelRecipes.Entry> options = PottersWheelRecipes.forInput(top.getBlock());
		Identifier id = BuiltInRegistries.BLOCK.getKey(top.getBlock());
		if (!id.equals(throwing)) {
			throwing = id;
			steps = 0;
			pick = 0;
		}
		if (options.isEmpty()) {
			return;
		}
		PottersWheelRecipes.Entry entry = options.get(pick % options.size());
		steps++;
		SoundType sound = top.getSoundType();
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, top), above.getX() + 0.5, above.getY() + 0.5, above.getZ() + 0.5,
			6, 0.35, 0.3, 0.35, 0.05);
		level.playSound(null, above, sound.getHitSound(), SoundSource.BLOCKS, 0.6F, 0.8F + level.getRandom().nextFloat() * 0.3F);
		if (steps >= entry.steps()) {
			Block out = entry.outputBlock().orElse(null);
			if (out != null) {
				BlockState made = out.defaultBlockState();
				level.setBlock(above, made, Block.UPDATE_ALL);
				level.playSound(null, above, made.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0F, 1.0F);
				level.sendParticles(ParticleTypes.HAPPY_VILLAGER, above.getX() + 0.5, above.getY() + 0.5, above.getZ() + 0.5,
					5, 0.3, 0.3, 0.3, 0.0);
			}
			throwing = null;
			steps = 0;
			pick = 0;
		}
		setChanged();
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		speed = input.getFloatOr("Speed", 0.0F);
		handTicks = input.getIntOr("HandTicks", 0);
		shaftTicks = input.getIntOr("ShaftTicks", 0);
		shaftRate = input.getFloatOr("ShaftRate", 0.0F);
		turn = input.getFloatOr("Turn", 0.0F);
		throwing = input.read("Throwing", Identifier.CODEC).orElse(null);
		steps = input.getIntOr("Steps", 0);
		pick = input.getIntOr("Pick", 0);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putFloat("Speed", speed);
		output.putInt("HandTicks", handTicks);
		output.putInt("ShaftTicks", shaftTicks);
		output.putFloat("ShaftRate", shaftRate);
		output.putFloat("Turn", turn);
		if (throwing != null) {
			output.store("Throwing", Identifier.CODEC, throwing);
		}
		output.putInt("Steps", steps);
		output.putInt("Pick", pick);
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
