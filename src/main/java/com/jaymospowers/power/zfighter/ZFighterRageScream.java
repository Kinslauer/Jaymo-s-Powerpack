package com.jaymospowers.power.zfighter;

import com.jaymospowers.core.Shakes;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * (0.17.46, Andy's NOEA picks) Rage Scream, a quick technique (G): a shout that throws everyone within {@link #RADIUS}
 * blocks back and breaks their poise ({@link ZFighterPoise}: a stagger, or a K.O. on one already staggered). It can only be
 * let out in Rage, or when his health is under {@link #LOW_HEALTH} of the most he has. In Rage it costs no ki, as nothing
 * does. Drawn as rings of rage-red motes running out along the ground.
 */
final class ZFighterRageScream {
	static final float KI = 30.0F;
	static final int COOLDOWN = 400;
	static final double RADIUS = 8.0;
	/** Usable under this share of max health. */
	static final float LOW_HEALTH = 0.25F;
	/** How hard they are thrown (sideways, up), a little damage (times Ki Power), and poise worn (enough to break any bar). */
	static final double PUSH = 2.2, LIFT = 0.45;
	static final float DAMAGE = 3.0F;
	static final float POISE_WEAR = 1000.0F;
	static final int RINGS = 3;

	private ZFighterRageScream() {
	}

	/** Whether he is in Rage or low enough on health. */
	static boolean open(ServerPlayer player, ZFighterPower.Live live) {
		return live.raging() || player.isCreative() || player.getHealth() < player.getMaxHealth() * LOW_HEALTH;
	}

	/** G: the shout. Returns the cooldown, or 0 if it did not go off. */
	static int start(ServerPlayer player, ZFighterPower.Live live) {
		if (!open(player, live)) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_scream_calm").withStyle(ChatFormatting.GRAY));
			return 0;
		}
		if (!ZFighterPower.spend(player, live, KI)) {
			return 0;
		}
		ServerLevel level = player.level();
		Vec3 at = player.position();
		float damage = DAMAGE * ZFighterPower.kiPower(player, live);
		var source = ZFighterKi.source(player);
		for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(RADIUS),
			e -> e != player && e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand))) {
			Vec3 away = near.position().subtract(at);
			double distance = away.length();
			if (distance > RADIUS) {
				continue;
			}
			double falloff = 1.0 - 0.4 * distance / RADIUS;
			ZFighterKi.push(near, away.lengthSqr() < 1.0E-4 ? player.getLookAngle() : away, PUSH * falloff, LIFT);
			if (ZFighterArts.valid(player, near)) {
				near.setInvulnerableTime(0);
				near.hurtServer(level, source, damage);
				ZFighterPoise.wear(near, POISE_WEAR);
			}
		}
		DustParticleOptions rage = new DustParticleOptions(ZFighterBattle.RAGE_COLOR, 2.0F);
		for (int ring = 1; ring <= RINGS; ring++) {
			double r = RADIUS * ring / RINGS;
			int points = 16 + ring * 12;
			for (int i = 0; i < points; i++) {
				double a = i * Math.PI * 2.0 / points;
				level.sendParticles(rage, true, false, at.x + Math.cos(a) * r * 0.3, at.y + 0.3, at.z + Math.sin(a) * r * 0.3, 0, Math.cos(a), 0.05, Math.sin(a),
					r * 0.35);
			}
		}
		level.sendParticles(ParticleTypes.CLOUD, true, false, at.x, at.y + 0.2, at.z, 30, 0.5, 0.1, 0.5, 0.5);
		level.sendParticles(ParticleTypes.SONIC_BOOM, true, false, at.x, at.y + player.getBbHeight() * 0.7, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 2.0F, 0.6F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.2F, 0.8F);
		Shakes.heavy(player, at, 24.0, 1.4F);
		if (!player.isCreative()) {
			player.causeFoodExhaustion(1.5F);
		}
		return COOLDOWN;
	}
}
