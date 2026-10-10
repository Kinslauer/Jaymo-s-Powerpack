package com.jaymospowers.power.gravity;

import com.jaymospowers.core.BlockCluster;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Rock flight (ascended or corrupted; hold V, a tap still toggles Weightless). You stomp, and the ground under you
 * (the solid blocks of the 3 x 3 below) tears up and clumps together under your feet; you ride the slab, steering it
 * like a wind's flight (the client flies it: {@code GravityRockFlightClient}), but it hugs the ground: it can't climb
 * far ({@link #MAX_ALTITUDE}) and is always sinking a little, like falling debris. Dust and grit pour from its
 * underside. Let go of V and it crumbles. The slab is {@link GravityPower#ROCK_FLIGHT}'s chunk, carried under you.
 */
final class GravityRockFlight {
	/** How high over the ground it can go, and how far below the feet the slab rides. */
	public static final double MAX_ALTITUDE = 4.0;
	static final double SLAB_DROP = 1.2;
	static final float EXHAUSTION = 0.02F, HARDEST = 50.0F;

	private GravityRockFlight() {
	}

	/** The stomp: the ground comes up under you. Returns false if there was nothing to take. */
	static boolean start(ServerPlayer player) {
		ServerLevel level = player.level();
		BlockPos under = player.blockPosition().below();
		List<BlockPos> taken = new ArrayList<>();
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				BlockPos pos = under.offset(dx, 0, dz);
				if (BlockCluster.takeable(level, pos, HARDEST, player) && level.getBlockState(pos).isSolid()) {
					taken.add(pos);
				}
			}
		}
		if (taken.isEmpty()) {
			return false;
		}
		BlockCluster slab = BlockCluster.of(level, taken, Vec3.ZERO, player);
		if (slab == null) {
			return false;
		}
		slab.script(true, true);
		player.setAttached(GravityPower.ROCK_FLIGHT, slab.getId());
		// up off the ground with it
		ServerPlayNetworking.send(player, new com.jaymospowers.power.web.WebNet.Nudge(new Vec3(0.0, 0.55, 0.0)));
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.2F, 0.7F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_EMERGE, SoundSource.PLAYERS, 0.8F, 1.4F);
		com.jaymospowers.core.Debris.puff(level, under, level.getBlockState(under.below()), 2.0F);
		return true;
	}

	/** Every tick while riding: the slab stays under your feet, and grit pours from it. */
	static void tick(ServerPlayer player, int slabId) {
		ServerLevel level = player.level();
		Entity slab = level.getEntity(slabId);
		if (!(slab instanceof BlockCluster cluster) || !cluster.scripted()) {
			stop(player);
			return;
		}
		Vec3 to = player.position().subtract(0.0, SLAB_DROP, 0.0);
		Vec3 was = slab.position();
		slab.setPos(to);
		slab.setDeltaMovement(to.subtract(was));
		player.resetFallDistance();
		player.causeFoodExhaustion(EXHAUSTION);
		if (player.tickCount % 2 == 0 && !cluster.pieces().isEmpty()) {
			// only from underneath: dust and grit falling away
			BlockParticleOption dust = new BlockParticleOption(ParticleTypes.FALLING_DUST, cluster.pieces().get(0).blockState());
			level.sendParticles(dust, to.x, to.y + 0.1, to.z, 4, 0.8, 0.0, 0.8, 0.0);
		}
		if (player.tickCount % 30 == 0) {
			level.playSound(null, to.x, to.y, to.z, SoundEvents.GRAVEL_STEP, SoundSource.PLAYERS, 0.6F, 0.5F);
		}
	}

	/** Off: the slab falls away and breaks up. */
	static void stop(ServerPlayer player) {
		Integer id = player.getAttached(GravityPower.ROCK_FLIGHT);
		player.removeAttached(GravityPower.ROCK_FLIGHT);
		if (id == null) {
			return;
		}
		if (player.level().getEntity(id) instanceof BlockCluster cluster) {
			cluster.script(false, false);
			cluster.setDeltaMovement(0.0, -0.3, 0.0);
			cluster.launch(null);
		}
		player.resetFallDistance();
	}
}
