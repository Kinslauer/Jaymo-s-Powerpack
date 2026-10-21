package com.jaymospowers.aether.dungeon;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;

/**
 * The sentry spawner's brain: while it is lit and a player is within {@link #PLAYER_RANGE}, every 150-250 ticks it sends up
 * to {@link #SPAWN_COUNT} detonation sentries out round it (never more than {@link #MAX_NEARBY} near it). It holds
 * {@link #CHARGES} sentries in all; when they are spent it goes dark until it is lit again.
 */
public class SentrySpawnerEntity extends BlockEntity {
	// ---- tuning (the notes: max 6 nearby, player range 16, spawn count 4, spawn range 4, delay 150-250, first delay 20) ----
	public static final int MAX_NEARBY = 6, PLAYER_RANGE = 16, SPAWN_COUNT = 4, SPAWN_RANGE = 4;
	public static final int FIRST_DELAY = 20, MIN_DELAY = 150, MAX_DELAY = 250, CHARGES = 12;
	/** How many tries to find a free spot for each sentry. */
	private static final int TRIES = 12;

	private int delay = FIRST_DELAY;
	private int charges = CHARGES;

	public SentrySpawnerEntity(BlockPos pos, BlockState state) {
		super(DungeonBlocks.SPAWNER_ENTITY, pos, state);
	}

	/** Lit afresh: all its sentries again, and the first wave soon. */
	public void arm() {
		this.charges = CHARGES;
		this.delay = FIRST_DELAY;
		this.setChanged();
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, SentrySpawnerEntity spawner) {
		if (!(level instanceof ServerLevel server) || !state.getValue(SentryBlock.LIT)) {
			return;
		}
		if (server.getNearestPlayer(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, PLAYER_RANGE, false) == null) {
			return;
		}
		if (spawner.delay > 0) {
			spawner.delay--;
			return;
		}
		spawner.delay = MIN_DELAY + server.getRandom().nextInt(MAX_DELAY - MIN_DELAY + 1);
		AABB near = new AABB(pos).inflate(SPAWN_RANGE * 2);
		List<DetonationSentry> around = server.getEntitiesOfClass(DetonationSentry.class, near);
		int room = Math.min(SPAWN_COUNT, MAX_NEARBY - around.size());
		RandomSource random = server.getRandom();
		int made = 0;
		for (int i = 0; i < room && spawner.charges > 0; i++) {
			for (int t = 0; t < TRIES; t++) {
				BlockPos at = pos.offset(random.nextInt(SPAWN_RANGE * 2 + 1) - SPAWN_RANGE, random.nextInt(3) - 1, random.nextInt(SPAWN_RANGE * 2 + 1) - SPAWN_RANGE);
				if (!server.getBlockState(at).isAir() || !server.getBlockState(at.above()).isAir() || !server.getBlockState(at.below()).isSolid()) {
					continue;
				}
				DetonationSentry sentry = DungeonMobs.DETONATION_SENTRY.spawn(server, at, EntitySpawnReason.SPAWNER);
				if (sentry != null) {
					made++;
					spawner.charges--;
					server.sendParticles(ParticleTypes.SMOKE, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 8, 0.3, 0.3, 0.3, 0.02);
					break;
				}
			}
		}
		if (made > 0) {
			server.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.7F, 1.6F);
		}
		if (spawner.charges <= 0) {
			server.setBlock(pos, state.setValue(SentryBlock.LIT, false), 3);
		}
		spawner.setChanged();
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		this.delay = input.getIntOr("delay", FIRST_DELAY);
		this.charges = input.getIntOr("charges", CHARGES);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putInt("delay", this.delay);
		output.putInt("charges", this.charges);
	}
}
