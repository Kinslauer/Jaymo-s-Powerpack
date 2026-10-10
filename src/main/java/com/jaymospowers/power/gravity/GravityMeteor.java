package com.jaymospowers.power.gravity;

import com.jaymospowers.core.BlockCluster;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Event Horizon's C: the Meteor. Where you aim ({@code RANGE}), a rock is torn out of the sky and pulled down onto
 * it over {@code FALL_TICKS}: a tumbling mass of stone and magma, burning and trailing smoke, its shadow spreading on
 * the ground below. It hits like the end of the world: a {@code POWER} blast that tears a crater, up to
 * {@code DAMAGE_MAX} at the centre falling off to nothing at {@code RADIUS}, everything thrown outward, a ring of
 * debris and a shockwave. {@code COOLDOWN} before another.
 */
final class GravityMeteor {
	static final double RANGE = 48.0, HEIGHT = 70.0, RADIUS = 25.0;
	static final int FALL_TICKS = 40, COOLDOWN = 900;
	static final float POWER = 7.0F, DAMAGE_MAX = 40.0F;
	static final double THROW = 2.2;
	private static final List<BlockState> ROCK = List.of(Blocks.MAGMA_BLOCK.defaultBlockState(), Blocks.BLACKSTONE.defaultBlockState(),
		Blocks.MAGMA_BLOCK.defaultBlockState(), Blocks.BASALT.defaultBlockState(), Blocks.OBSIDIAN.defaultBlockState(),
		Blocks.MAGMA_BLOCK.defaultBlockState(), Blocks.BLACKSTONE.defaultBlockState(), Blocks.TUFF.defaultBlockState(),
		Blocks.MAGMA_BLOCK.defaultBlockState());
	private static final DustParticleOptions SHADOW = new DustParticleOptions(0x120A1A, 2.0F);

	private record Meteor(ResourceKey<Level> dimension, UUID owner, int rock, Vec3 from, Vec3 to, long start) {
	}

	private static final List<Meteor> METEORS = new ArrayList<>();

	private GravityMeteor() {
	}

	/** C: call it down where you aim. Returns false if nothing came. */
	static boolean call(ServerPlayer player) {
		ServerLevel level = player.level();
		Vec3 to = GravityPower.aimPoint(player, RANGE);
		// down out of the sky, at a slant from behind you
		Vec3 back = player.getLookAngle().horizontal();
		back = back.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : back.normalize();
		Vec3 from = to.add(back.scale(-HEIGHT * 0.45)).add(0.0, HEIGHT, 0.0);
		from = new Vec3(from.x, Math.min(from.y, level.getMaxY() - 2), from.z);
		BlockCluster rock = BlockCluster.made(level, ROCK, from);
		rock.script(true, false);
		METEORS.add(new Meteor(level.dimension(), player.getUUID(), rock.getId(), from, to, level.getGameTime()));
		level.playSound(null, to.x, to.y, to.z, SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 2.0F, 0.4F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.5F, 0.4F);
		return true;
	}

	static void tick(MinecraftServer server) {
		for (Iterator<Meteor> it = METEORS.iterator(); it.hasNext(); ) {
			Meteor meteor = it.next();
			ServerLevel level = server.getLevel(meteor.dimension());
			if (level == null) {
				it.remove();
				continue;
			}
			Entity rock = level.getEntity(meteor.rock());
			long t = level.getGameTime() - meteor.start();
			float p = Mth.clamp(t / (float)FALL_TICKS, 0.0F, 1.0F);
			// accelerating down its line
			Vec3 at = meteor.from().lerp(meteor.to(), p * p);
			if (rock != null) {
				Vec3 was = rock.position();
				rock.setPos(at);
				rock.setDeltaMovement(at.subtract(was));
				rock.syncVelocity = true;
			}
			// burning and smoking; the shadow spreads under it
			level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 0.5, at.z, 8, 0.6, 0.6, 0.6, 0.02);
			level.sendParticles(ParticleTypes.FLAME, at.x, at.y + 0.5, at.z, 10, 0.8, 0.8, 0.8, 0.05);
			level.sendParticles(ParticleTypes.LAVA, at.x, at.y + 0.5, at.z, 2, 0.5, 0.5, 0.5, 0.0);
			Vec3 ground = meteor.to();
			double shadow = 1.0 + p * RADIUS * 0.35;
			for (int i = 0; i < 12; i++) {
				double a = i / 12.0 * Math.PI * 2.0 + t * 0.1;
				level.sendParticles(SHADOW, ground.x + Math.cos(a) * shadow, ground.y + 0.1, ground.z + Math.sin(a) * shadow, 1, 0.1, 0.0, 0.1, 0.0);
			}
			if (t % 8 == 0) {
				level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 2.0F, 0.4F + p * 0.3F);
			}
			if (t < FALL_TICKS) {
				continue;
			}
			it.remove();
			if (rock != null) {
				rock.discard();
			}
			impact(level, server.getPlayerList().getPlayer(meteor.owner()), ground);
		}
	}

	/** It lands: the crater, the blast, everything thrown, the shockwave. */
	private static void impact(ServerLevel level, ServerPlayer owner, Vec3 at) {
		level.explode(owner, at.x, at.y, at.z, POWER, true, Level.ExplosionInteraction.TNT);
		for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(RADIUS),
			e -> e.isAlive() && !e.isSpectator() && e != owner && !(e instanceof ArmorStand))) {
			Vec3 away = living.getBoundingBox().getCenter().subtract(at);
			double d = away.length();
			if (d > RADIUS) {
				continue;
			}
			float share = (float)(1.0 - d / RADIUS);
			DamageSource source = owner != null ? level.damageSources().indirectMagic(owner, owner) : level.damageSources().magic();
			living.setInvulnerableTime(0);
			if (owner != null) {
				GravityPower.touch(living, owner);
			}
			living.hurtServer(level, source, DAMAGE_MAX * share * share + 2.0F * share);
			Vec3 push = (d < 0.3 ? new Vec3(0.0, 1.0, 0.0) : away.normalize()).scale(THROW * share).add(0.0, 0.6 * share + 0.2, 0.0);
			living.setDeltaMovement(push);
			living.syncVelocity = true;
			if (living instanceof ServerPlayer player) {
				net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, new com.jaymospowers.power.web.WebNet.Nudge(push));
			}
		}
		// the shockwave running out along the ground, and the dust
		for (int ring = 1; ring <= 4; ring++) {
			double r = RADIUS * ring / 4.0;
			for (int i = 0; i < 20 + ring * 8; i++) {
				double a = i / (20.0 + ring * 8) * Math.PI * 2.0;
				level.sendParticles(ParticleTypes.EXPLOSION, at.x + Math.cos(a) * r, at.y + 0.3, at.z + Math.sin(a) * r, 1, 0.0, 0.0, 0.0, 0.0);
			}
		}
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y + 1.0, at.z, 3, 2.0, 1.0, 2.0, 0.0);
		level.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, at.x, at.y + 1.0, at.z, 60, 4.0, 2.0, 4.0, 0.05);
		level.sendParticles(ParticleTypes.LAVA, at.x, at.y + 1.0, at.z, 60, 3.0, 1.0, 3.0, 0.0);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 6.0F, 0.4F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 4.0F, 0.5F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 4.0F, 0.5F);
		GravityPower.shake(level, at, RADIUS * 2.0, 1.5F);
	}

	static void clear() {
		METEORS.clear();
	}
}
