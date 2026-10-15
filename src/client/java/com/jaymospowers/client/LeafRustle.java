package com.jaymospowers.client;

import com.jaymospowers.client.mixin.FallingLeavesInvoker;
import com.jaymospowers.core.Leaves;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.FallingParticlesLeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * The look and sound of soft leaves ({@link Leaves}): anything moving through leaves rustles them (the leaves' own step
 * sound, soft), knocks bits off, and shakes real falling leaves loose out of the bottom of the block. All vanilla sounds
 * and particles drawn on this side, so it's the same under Iris. Particles are off when Particles is set to Minimal.
 */
public final class LeafRustle {
	/** Bodies moving through leaves are heard and seen this far away. */
	static final double RADIUS = 24.0;
	/** Slower than this (blocks a tick) and nothing rustles. */
	static final double MIN_SPEED = 0.04;
	/** At least this many ticks between rustles of one body; faster bodies rustle up to every tick past this speed. */
	static final int RUSTLE_EVERY = 6;
	static final double FAST = 0.3;
	static final float VOLUME = 0.35F, PITCH = 1.15F;
	/** Bits knocked off per rustle, and the chance a rustle shakes falling leaves loose. */
	static final int BITS = 4;
	static final float SHAKE_CHANCE = 0.6F;

	private static final Map<Entity, Integer> LAST = new WeakHashMap<>();
	private static ClientLevel lastLevel;

	private LeafRustle() {
	}

	static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(LeafRustle::tick);
	}

	private static void tick(Minecraft mc) {
		ClientLevel level = mc.level;
		LocalPlayer player = mc.player;
		if (level == null || player == null || mc.isPaused()) {
			return;
		}
		if (level != lastLevel) {
			lastLevel = level;
			LAST.clear();
		}
		boolean particles = mc.options.particles().get() != ParticleStatus.MINIMAL;
		RandomSource random = level.getRandom();
		AABB around = player.getBoundingBox().inflate(RADIUS);
		for (Entity entity : level.getEntities((Entity)null, around, e -> !e.isSpectator() && Leaves.passesThrough(e))) {
			double dx = entity.getX() - entity.xo, dy = entity.getY() - entity.yo, dz = entity.getZ() - entity.zo;
			double speed = Math.sqrt(dx * dx + dy * dy + dz * dz);
			if (speed < MIN_SPEED) {
				continue;
			}
			Integer last = LAST.get(entity);
			int every = speed >= FAST ? 1 : RUSTLE_EVERY;
			if (last != null && entity.tickCount - last < every && entity.tickCount >= last) {
				continue;
			}
			BlockPos leaves = leavesIn(level, entity.getBoundingBox());
			if (leaves == null) {
				continue;
			}
			LAST.put(entity, entity.tickCount);
			rustle(level, entity, leaves, level.getBlockState(leaves), random, particles);
		}
	}

	/** A leaves block the box is in (the middle of the body first, then the feet, then the head), or null. */
	private static BlockPos leavesIn(ClientLevel level, AABB box) {
		double x = (box.minX + box.maxX) * 0.5, z = (box.minZ + box.maxZ) * 0.5;
		for (double y : new double[] {(box.minY + box.maxY) * 0.5, box.minY + 0.1, box.maxY - 0.1}) {
			BlockPos pos = BlockPos.containing(x, y, z);
			if (Leaves.is(level.getBlockState(pos))) {
				return pos;
			}
		}
		return null;
	}

	private static void rustle(ClientLevel level, Entity entity, BlockPos pos, BlockState state, RandomSource random, boolean particles) {
		float size = entity instanceof LivingEntity ? Math.min(1.5F, 0.6F + entity.getBbWidth()) : 0.4F;
		level.playLocalSound(entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(), state.getSoundType().getStepSound(),
			entity.getSoundSource(), VOLUME * size, PITCH + (random.nextFloat() - 0.5F) * 0.3F, false);
		if (!particles) {
			return;
		}
		BlockParticleOption bits = new BlockParticleOption(ParticleTypes.BLOCK, state);
		AABB box = entity.getBoundingBox();
		for (int i = 0; i < BITS * size; i++) {
			level.addParticle(bits, box.minX + random.nextDouble() * box.getXsize(), box.minY + random.nextDouble() * box.getYsize(),
				box.minZ + random.nextDouble() * box.getZsize(), 0.0, 0.0, 0.0);
		}
		if (state.getBlock() instanceof FallingParticlesLeavesBlock falling && random.nextFloat() < SHAKE_CHANCE) {
			((FallingLeavesInvoker)falling).jaymospowers$shakeLoose(level, pos, random);
		}
	}
}
