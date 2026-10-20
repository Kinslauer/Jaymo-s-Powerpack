package com.jaymospowers.power.smoke;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * V, Smoke Bomb: you throw a grey ball (a snowball wearing a firework star, so no new entity), and where it lands it
 * bursts into a cloud of {@link #AMOUNT} ({@link #AMOUNT_MASTERED} mastered) blocks' worth of smoke. Whatever is right
 * by the burst is blinded for a moment.
 */
final class SmokeBomb {
	static final float AMOUNT = 130.0F;
	static final float AMOUNT_MASTERED = 190.0F;
	static final float SPEED = 1.3F;
	/** Blinded within this many blocks of the burst, for this many ticks. */
	static final double BLIND_RADIUS = 2.5;
	static final int BLIND_TICKS = 60;
	/** Corrupted (Choke Bomb): poison on those it blinds, and the smog each coughs out. */
	static final int CHOKE_POISON_TICKS = 100;
	static final float CHOKE_COUGH = 6.0F;
	/** A bomb still flying after this long bursts where it is. */
	static final int FUSE = 100;

	private static final class Bomb {
		final Snowball ball;
		final UUID owner;
		final float amount;
		Vec3 last;
		int age;

		Bomb(Snowball ball, UUID owner, float amount) {
			this.ball = ball;
			this.owner = owner;
			this.amount = amount;
			this.last = ball.position();
		}
	}

	private static final List<Bomb> BOMBS = new ArrayList<>();

	private SmokeBomb() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(SmokeBomb::tick);
	}

	static boolean throwBomb(ServerPlayer player) {
		ServerLevel level = player.level();
		ItemStack look = new ItemStack(Items.FIREWORK_STAR);
		Snowball ball = Projectile.spawnProjectileFromRotation((l, owner, stack) -> new Snowball(l, owner, stack), level, look, player, 0.0F, SPEED,
			1.0F);
		SmokeMoves.begin(player, SmokeMoves.BOMB);
		BOMBS.add(new Bomb(ball, player.getUUID(), SmokePower.mastered(player) ? AMOUNT_MASTERED : AMOUNT));
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.6F, 0.5F);
		return true;
	}

	private static void tick(MinecraftServer server) {
		if (BOMBS.isEmpty()) {
			return;
		}
		Map<Bomb, Vec3> bursts = new HashMap<>();
		BOMBS.removeIf(bomb -> {
			if (bomb.ball.isRemoved() || ++bomb.age >= FUSE) {
				bursts.put(bomb, bomb.ball.isRemoved() ? bomb.last : bomb.ball.position());
				bomb.ball.discard();
				return true;
			}
			bomb.last = bomb.ball.position();
			return false;
		});
		bursts.forEach(SmokeBomb::burst);
	}

	private static void burst(Bomb bomb, Vec3 at) {
		if (!(bomb.ball.level() instanceof ServerLevel level)) {
			return;
		}
		ServerPlayer owner = level.getServer().getPlayerList().getPlayer(bomb.owner);
		SmokeField.puff(level, at, bomb.amount, owner);
		level.sendParticles(SmokeParticles.burst(owner, ParticleTypes.LARGE_SMOKE), at.x, at.y, at.z, 30, 0.6, 0.6, 0.6, 0.08);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.0F, 0.5F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 1.2F, 0.6F);
		com.jaymospowers.core.Shakes.around(level, at, 10.0, 0.4F);
		boolean choke = owner != null && com.jaymospowers.core.Fate.isCorrupted(owner);
		for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(BLIND_RADIUS),
			e -> e.isAlive() && e != owner)) {
			living.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, BLIND_TICKS, 0), owner);
			if (choke) {
				// Choke Bomb (smog): poisoned, and they cough out smog of their own
				living.addEffect(new MobEffectInstance(MobEffects.POISON, CHOKE_POISON_TICKS, 0), owner);
				SmokeField.puff(level, living.getEyePosition(), CHOKE_COUGH, owner);
			}
		}
	}

	static void clear() {
		BOMBS.clear();
	}
}
