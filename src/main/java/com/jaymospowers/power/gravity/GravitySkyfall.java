package com.jaymospowers.power.gravity;

import com.jaymospowers.core.BlockCluster;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Collapsing Horizon's C, the ultimate: <b>Skyfall</b>. Gravity turns over around you ({@code RADIUS}): every
 * creature and a host of loose blocks lift off the ground and fall <i>up</i> into the sky for {@code RISE_TICKS},
 * tumbling and struggling against nothing. Then you close your fist and gravity comes back {@code SLAM}-fold: it all
 * comes down together. Creatures take what the fall gives them and then some ({@code EXTRA_PER_BLOCK} more per block
 * fallen, more the heavier they are); the blocks shatter where they land; the ground shakes. {@code COOLDOWN} after.
 */
final class GravitySkyfall {
	static final double RADIUS = 20.0;
	static final int RISE_TICKS = 60, COOLDOWN = 1800;
	/** Rising speed (blocks/tick) and how hard the struggle jostles; the fall's speed at the slam. */
	static final double RISE_SPEED = 0.55, JOSTLE = 0.08, SLAM = 3.5;
	/** How many loose blocks come up with them, and how hard a block may be. */
	static final int BLOCKS = 40;
	static final float BLOCK_HARDNESS = 3.0F;
	/** Extra damage per block fallen (on top of the fall's own), scaled by size. */
	static final float EXTRA_PER_BLOCK = 0.4F;

	private static final class Skyfall {
		final ResourceKey<Level> dimension;
		final UUID owner;
		final Vec3 center;
		final long start;
		final List<Integer> lifted = new ArrayList<>();
		final List<Integer> blocks = new ArrayList<>();
		/** Each creature's height when the slam began, by id (for the extra damage on landing). */
		final java.util.Map<Integer, Double> slamFrom = new java.util.HashMap<>();
		boolean slammed;

		Skyfall(ResourceKey<Level> dimension, UUID owner, Vec3 center, long start) {
			this.dimension = dimension;
			this.owner = owner;
			this.center = center;
			this.start = start;
		}
	}

	private static final List<Skyfall> SKYFALLS = new ArrayList<>();

	private GravitySkyfall() {
	}

	/** C, both ascended and corrupted: turn the sky over. */
	static boolean call(ServerPlayer player) {
		ServerLevel level = player.level();
		Vec3 center = player.position();
		Skyfall skyfall = new Skyfall(level.dimension(), player.getUUID(), center, level.getGameTime());
		for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(RADIUS),
			e -> e != player && e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand) && e.distanceToSqr(center) <= RADIUS * RADIUS
				&& !(e instanceof ServerPlayer p && p.isCreative()))) {
			skyfall.lifted.add(living.getId());
			living.setNoGravity(true);
			living.resetFallDistance();
			GravityPower.touch(living, player);
			if (living instanceof Mob mob) {
				mob.getNavigation().stop();
			}
		}
		// the ground comes up too: loose surface blocks, torn loose one by one
		List<BlockPos> surface = new ArrayList<>();
		int r = (int)RADIUS;
		for (int dx = -r; dx <= r; dx += 2) {
			for (int dz = -r; dz <= r; dz += 2) {
				if (dx * dx + dz * dz > RADIUS * RADIUS) {
					continue;
				}
				int x = Mth.floor(center.x) + dx, z = Mth.floor(center.z) + dz;
				BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z)).below();
				if (Math.abs(top.getY() - center.y) < 12 && BlockCluster.takeable(level, top, BLOCK_HARDNESS, player)) {
					surface.add(top);
				}
			}
		}
		Collections.shuffle(surface, new java.util.Random(level.getRandom().nextLong()));
		for (BlockPos pos : surface.subList(0, Math.min(BLOCKS, surface.size()))) {
			BlockCluster block = BlockCluster.of(level, List.of(pos), new Vec3(0.0, 1.0, 0.0), player);
			if (block != null) {
				block.script(true, false);
				skyfall.blocks.add(block.getId());
			}
		}
		SKYFALLS.add(skyfall);
		level.playSound(null, center.x, center.y, center.z, SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 3.0F, 0.3F);
		level.playSound(null, center.x, center.y, center.z, SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 2.0F, 0.5F);
		for (int i = 0; i < 48; i++) {
			double a = i / 48.0 * Math.PI * 2.0;
			level.sendParticles(ParticleTypes.REVERSE_PORTAL, center.x + Math.cos(a) * RADIUS * 0.8, center.y + 0.3, center.z + Math.sin(a) * RADIUS * 0.8,
				4, 0.2, 0.0, 0.2, 0.3);
		}
		GravityPower.shake(level, center, RADIUS * 1.5, 0.8F);
		return true;
	}

	static void tick(MinecraftServer server) {
		for (Iterator<Skyfall> it = SKYFALLS.iterator(); it.hasNext(); ) {
			Skyfall skyfall = it.next();
			ServerLevel level = server.getLevel(skyfall.dimension);
			if (level == null) {
				it.remove();
				continue;
			}
			long t = level.getGameTime() - skyfall.start;
			ServerPlayer owner = server.getPlayerList().getPlayer(skyfall.owner);
			var random = level.getRandom();
			if (t < RISE_TICKS) {
				// falling up, struggling
				for (int id : skyfall.lifted) {
					Entity e = level.getEntity(id);
					if (e == null || !e.isAlive()) {
						continue;
					}
					Vec3 v = new Vec3((random.nextDouble() - 0.5) * JOSTLE, RISE_SPEED * (1.0 - t / (double)RISE_TICKS * 0.5), (random.nextDouble() - 0.5) * JOSTLE);
					e.setDeltaMovement(v);
					e.syncVelocity = true;
					e.resetFallDistance();
					if (e instanceof ServerPlayer player) {
						ServerPlayNetworking.send(player, new com.jaymospowers.power.web.WebNet.Nudge(v));
					}
					if (t % 4 == 0) {
						level.sendParticles(ParticleTypes.REVERSE_PORTAL, e.getX(), e.getY(), e.getZ(), 2, 0.2, 0.1, 0.2, 0.05);
					}
				}
				for (int id : skyfall.blocks) {
					Entity block = level.getEntity(id);
					if (block != null) {
						Vec3 was = block.position();
						Vec3 to = was.add((random.nextDouble() - 0.5) * 0.1, RISE_SPEED * (0.8 + (id % 5) * 0.08), (random.nextDouble() - 0.5) * 0.1);
						block.setPos(to);
						block.setDeltaMovement(to.subtract(was));
					}
				}
				if (t % 20 == 0) {
					level.playSound(null, skyfall.center.x, skyfall.center.y + 10.0, skyfall.center.z, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(),
						SoundSource.PLAYERS, 2.0F, 0.4F);
				}
				continue;
			}
			if (!skyfall.slammed) {
				// the fist closes: everything comes down at once
				skyfall.slammed = true;
				for (int id : skyfall.lifted) {
					Entity e = level.getEntity(id);
					if (e == null) {
						continue;
					}
					e.setNoGravity(false);
					skyfall.slamFrom.put(id, e.getY());
					Vec3 v = new Vec3(0.0, -SLAM, 0.0);
					e.setDeltaMovement(v);
					e.syncVelocity = true;
					if (e instanceof ServerPlayer player) {
						ServerPlayNetworking.send(player, new com.jaymospowers.power.web.WebNet.Nudge(v));
					}
				}
				for (int id : skyfall.blocks) {
					Entity block = level.getEntity(id);
					if (block instanceof BlockCluster cluster) {
						cluster.script(false, false);
						cluster.setDeltaMovement(0.0, -SLAM, 0.0);
						cluster.launch(owner);
					}
				}
				level.playSound(null, skyfall.center.x, skyfall.center.y, skyfall.center.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 3.0F, 0.4F);
				continue;
			}
			// landing: the fall does its work, and the weight adds to it
			boolean airborne = false;
			for (Iterator<Integer> ids = skyfall.lifted.iterator(); ids.hasNext(); ) {
				Entity e = level.getEntity(ids.next());
				if (!(e instanceof LivingEntity living) || !living.isAlive()) {
					ids.remove();
					continue;
				}
				if (!living.onGround() && !living.isInWater()) {
					airborne = true;
					living.setDeltaMovement(living.getDeltaMovement().add(0.0, -0.3, 0.0));
					continue;
				}
				ids.remove();
				double fell = Math.max(0.0, skyfall.slamFrom.getOrDefault(living.getId(), living.getY()) - living.getY());
				float size = Mth.clamp(living.getBbWidth() * living.getBbHeight(), 0.5F, 3.0F);
				living.setInvulnerableTime(0);
				living.hurtServer(level, level.damageSources().fall(), (float)fell * EXTRA_PER_BLOCK * size);
				com.jaymospowers.core.Debris.puff(level, living.blockPosition().below(), level.getBlockState(living.blockPosition().below()), 2.0F);
				level.playSound(null, living.getX(), living.getY(), living.getZ(), SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.5F, 0.6F);
			}
			if (!airborne && t > RISE_TICKS + 5 || t > RISE_TICKS + 200) {
				for (Iterator<Integer> ids = skyfall.lifted.iterator(); ids.hasNext(); ) {
					Entity e = level.getEntity(ids.next());
					if (e != null) {
						e.setNoGravity(false);
					}
				}
				level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, skyfall.center.x, skyfall.center.y + 0.5, skyfall.center.z, 2, 4.0, 0.5, 4.0, 0.0);
				GravityPower.shake(level, skyfall.center, RADIUS * 2.0, 1.4F);
				it.remove();
			}
		}
	}

	static void clear() {
		SKYFALLS.clear();
	}
}
