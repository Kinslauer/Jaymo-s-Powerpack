package com.jaymospowers.power.cryo;

import com.jaymospowers.core.fx.MagicFx;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Ice Javelin (Andy's Awakening pick, "Encase and javelin": Cryokinesis). Sprint + V: a javelin of ice thrown along your look
 * that flies dead straight (no drop, {@link #SPEED} blocks a tick, up to {@link #RANGE}) and strikes the first creature or wall
 * it meets: hard, and Frostbite with it. Shares the shards' cooldown.
 */
public final class CryoJavelin {
	// ---- tuning ----
	static final double SPEED = 3.0, RANGE = 64.0;
	static final float DAMAGE = 9.0F;
	static final int BITE = 3, COOLDOWN = 100;
	static final int COLOR = 0x8FD3F5;

	private static final class Javelin {
		final ServerPlayer owner;
		final ServerLevel level;
		final Vec3 dir;
		final float strength;
		Vec3 pos;
		double flown;

		Javelin(ServerPlayer owner, Vec3 pos, Vec3 dir) {
			this.owner = owner;
			this.level = owner.level();
			this.pos = pos;
			this.dir = dir;
			this.strength = CryoPower.strength(owner);
		}
	}

	private static final List<Javelin> FLYING = new ArrayList<>();

	private CryoJavelin() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(CryoJavelin::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> FLYING.clear());
	}

	static void throwFrom(ServerPlayer player) {
		if (!CryoPower.ready(player, CryoState.SHARDS)) {
			return;
		}
		Vec3 dir = player.getViewVector(1.0F).normalize();
		FLYING.add(new Javelin(player, player.getEyePosition().add(dir.scale(0.6)).add(0.0, -0.15, 0.0), dir));
		CryoPower.cooldown(player, CryoState.SHARDS, COOLDOWN);
		CryoMoves.begin(player, CryoMoves.SHARDS);
		com.jaymospowers.core.Mastery.used(player);
		player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 1.0F, 1.3F);
	}

	private static void tick(MinecraftServer server) {
		for (Iterator<Javelin> it = FLYING.iterator(); it.hasNext();) {
			Javelin j = it.next();
			if (j.owner.isRemoved() || j.owner.level() != j.level || j.flown >= RANGE) {
				it.remove();
				continue;
			}
			Vec3 next = j.pos.add(j.dir.scale(SPEED));
			HitResult wall = j.level.clip(new ClipContext(j.pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, j.owner));
			Vec3 end = wall.getType() == HitResult.Type.MISS ? next : wall.getLocation();
			LivingEntity struck = null;
			double best = Double.MAX_VALUE;
			for (LivingEntity e : j.level.getEntitiesOfClass(LivingEntity.class, new AABB(j.pos, end).inflate(0.4), e -> foe(j.owner, e))) {
				var hit = e.getBoundingBox().inflate(0.3).clip(j.pos, end);
				if (hit.isPresent() && hit.get().distanceToSqr(j.pos) < best) {
					best = hit.get().distanceToSqr(j.pos);
					struck = e;
				}
			}
			if (struck != null) {
				end = j.pos.add(j.dir.scale(Math.sqrt(best)));
			}
			MagicFx.stroke(j.level, j.pos, end, 0.12F, COLOR);
			j.level.sendParticles(ParticleTypes.SNOWFLAKE, end.x, end.y, end.z, 2, 0.05, 0.05, 0.05, 0.0);
			j.flown += j.pos.distanceTo(end);
			j.pos = end;
			if (struck != null) {
				struck.hurtServer(j.level, j.owner.damageSources().playerAttack(j.owner), DAMAGE * j.strength);
				struck.clearFire();
				CryoFrostbite.bite(struck, j.owner, BITE);
				shatter(j, end);
				it.remove();
			} else if (wall.getType() != HitResult.Type.MISS) {
				shatter(j, end);
				it.remove();
			}
		}
	}

	private static boolean foe(ServerPlayer owner, LivingEntity e) {
		return e != owner && e.isAlive() && !e.isSpectator() && !(e instanceof TamableAnimal pet && pet.isOwnedBy(owner))
			&& !(e instanceof Player other && !owner.canHarmPlayer(other));
	}

	private static void shatter(Javelin j, Vec3 at) {
		j.level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PACKED_ICE.defaultBlockState()), at.x, at.y, at.z, 14, 0.2, 0.2, 0.2, 0.1);
		j.level.playSound(null, at.x, at.y, at.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.0F, 1.2F);
	}
}
