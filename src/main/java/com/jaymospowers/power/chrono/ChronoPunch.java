package com.jaymospowers.power.chrono;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.SwingAnimationType;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.Vec3;

/**
 * Chrono Punch, Timeless Chrono's ascended ability (C): time stutters and you unload a flurry of {@link #PUNCHES}
 * punches into everything in front of you, faster than they can move (they're held nearly still meanwhile). Each punch
 * lands harder than the last, and every punch grows with mastery; the last one throws them.
 */
final class ChronoPunch {
	static final int PUNCHES = 10, EVERY = 1;
	static final double REACH = 3.5;
	/** Cosine of how wide the flurry reaches either side of where you look. */
	static final double CONE = 0.45;
	/** A punch's damage: base, plus this per ability mastered; each punch after the first hits this share harder. */
	static final float DAMAGE_BASE = 0.6F, DAMAGE_PER_LEVEL = 0.2F, RAMP = 0.12F;
	/** The last punch's throw. */
	static final double FINISH_PUSH = 1.3;
	static final int COOLDOWN = 160;
	private static final DustParticleOptions GOLD = new DustParticleOptions(0xFFE08A, 1.0F);
	private static final SwingAnimation JAB = new SwingAnimation(SwingAnimationType.WHACK, EVERY);

	private static final Map<UUID, Integer> FLURRIES = new HashMap<>();

	private ChronoPunch() {
	}

	/**
	 * Starts the flurry. In Time Flow it's a grab instead: the barrage, locked onto the enemy close in front of you, and
	 * with no one in reach nothing happens. Returns whether it started (and the cooldown with it).
	 */
	static boolean punch(ServerPlayer player) {
		if (ChronoFlow.on(player)) {
			return ChronoFlow.barrage(player);
		}
		FLURRIES.put(player.getUUID(), 0);
		ServerLevel level = player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 1.0F, 1.8F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 0.7F, 1.6F);
		return true;
	}

	static void tick(MinecraftServer server) {
		for (Iterator<Map.Entry<UUID, Integer>> it = FLURRIES.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Integer> entry = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			int step = entry.getValue();
			if (player == null || !player.isAlive()) {
				it.remove();
				continue;
			}
			if (step % EVERY == 0) {
				int n = step / EVERY;
				hit(player, n, n == PUNCHES - 1);
				if (n >= PUNCHES - 1) {
					it.remove();
					continue;
				}
			}
			entry.setValue(step + 1);
		}
	}

	private static void hit(ServerPlayer player, int n, boolean last) {
		ServerLevel level = player.level();
		int side = n % 2 == 0 ? 1 : -1;
		player.setAttached(ChronoFlow.MOVE, new ChronoFlow.Move(ChronoFlow.RAPID, level.getGameTime(), -1, side));
		player.swing(side > 0 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND, JAB, true);
		int mastery = com.jaymospowers.core.Mastery.unlocked(player);
		float damage = (DAMAGE_BASE + DAMAGE_PER_LEVEL * mastery) * (1.0F + RAMP * n);
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		Vec3 fist = eye.add(look.scale(1.4)).add(0.0, -0.3, 0.0);
		ChronoFx.particles(level, player, GOLD, fist.x, fist.y, fist.z, 3, 0.25, 0.2, 0.25, 0.0);
		boolean any = false;
		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(REACH + 1.0),
			e -> e != player && e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand) && !player.isAlliedTo(e)
				&& !(e instanceof TamableAnimal pet && pet.isOwnedBy(player)))) {
			Vec3 to = target.getBoundingBox().getCenter().subtract(eye);
			double distance = to.length();
			if (distance > REACH + target.getBbWidth() * 0.5 || distance > 1.0E-3 && to.scale(1.0 / distance).dot(look) < CONE
				|| !player.hasLineOfSight(target)) {
				continue;
			}
			target.setInvulnerableTime(0);
			Vec3 before = target.getDeltaMovement();
			if (!target.hurtServer(level, player.damageSources().playerAttack(player), damage)) {
				continue;
			}
			any = true;
			if (last) {
				Vec3 away = to.horizontal().lengthSqr() < 1.0E-4 ? look.horizontal() : to.horizontal().normalize();
				target.setDeltaMovement(away.scale(FINISH_PUSH).add(0.0, 0.45, 0.0));
				ChronoFlow.reel(target, ChronoFlow.FLUNG, 0);
				ChronoFx.particles(level, player, ParticleTypes.EXPLOSION, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
			} else {
				// held in the moment: the punches pin them where they stand
				target.setDeltaMovement(before.scale(0.2));
				target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 10, 4, false, false, false), player);
			}
			target.syncVelocity = true;
			Vec3 at = target.getBoundingBox().getCenter();
			ChronoFx.crackSound(level, player, at.x, at.y, at.z);
			ChronoFx.particles(level, player, ParticleTypes.CRIT, at.x, at.y, at.z, 4, 0.25, 0.3, 0.25, 0.25);
		}
		float pitch = 1.0F + n * 0.06F;
		level.playSound(null, player.getX(), player.getY(), player.getZ(), any ? SoundEvents.PLAYER_ATTACK_STRONG : SoundEvents.PLAYER_ATTACK_WEAK,
			SoundSource.PLAYERS, last ? 1.2F : 0.7F, pitch);
		if (last) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.2F, 0.6F);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 0.8F, 1.5F);
		}
	}

	static void clear() {
		FLURRIES.clear();
	}
}
