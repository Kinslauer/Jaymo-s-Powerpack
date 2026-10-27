package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Meditate;
import com.jaymospowers.core.Powers;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/**
 * (0.17.45, Andy's NOEA picks) Meditation dodge: a passive chance to slip a melee blow completely. It grows with the
 * time he has spent shadowboxing (up to {@link #MAX_CHANCE} after {@link #FULL_SECONDS}), and the nearer he is to
 * outclassing the attacker in speed and in strength the more of that he gets ({@link #BASE_SHARE} on even terms). Not
 * while guarding, charging, or as the Great Ape. A slipped blow does nothing: a quick side-step, a puff and a whoosh.
 */
public final class ZFighterSidestep {
	/** The best chance there is. */
	static final float MAX_CHANCE = 0.20F;
	/** Seconds of shadowboxing for the full chance (heavy gravity counts double). */
	static final int FULL_SECONDS = 3600;
	/** The share of the chance he has against an attacker his equal; the rest comes from out-doing it (0-1). */
	static final float BASE_SHARE = 0.6F;
	/** A player's usual walking speed, and a zombie's: his speed is compared against the attacker's on this scale. */
	static final double PLAYER_SPEED = 0.1, MOB_SPEED = 0.23;
	/** How far he steps aside. */
	static final double STEP = 0.35;

	/** Seconds spent shadowboxing, kept for good. */
	public static final AttachmentType<Integer> TRAINED = AttachmentRegistry.create(JaymosPowers.id("zfighter_sidestep"),
		builder -> builder.persistent(Codec.INT).copyOnDeath());

	private ZFighterSidestep() {
	}

	static void init() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (!(entity instanceof ServerPlayer player) || amount <= 0.0F || !(source.getEntity() instanceof LivingEntity attacker)
				|| source.getDirectEntity() != attacker || attacker == player || source.is(DamageTypeTags.IS_PROJECTILE)
				|| source.is(DamageTypeTags.IS_EXPLOSION) || source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypeTags.BYPASSES_ARMOR)
				|| !Powers.has(player, ZFighterPower.INSTANCE)) {
				return true;
			}
			ZFighterPower.Live live = ZFighterPower.live(player);
			if (live.guarding || live.charging || ZFighterLineage.ape(player) || ZFighterPoise.stunned(player)
				|| player.getRandom().nextFloat() >= chance(player, attacker)) {
				return true;
			}
			slip(player, attacker);
			return false;
		});
	}

	/** The chance he slips a blow from {@code attacker}, 0 to {@link #MAX_CHANCE}. */
	static float chance(ServerPlayer player, LivingEntity attacker) {
		float trained = Mth.clamp(player.getAttachedOrElse(TRAINED, 0) / (float)FULL_SECONDS, 0.0F, 1.0F);
		double mine = player.getAttributeValue(Attributes.MOVEMENT_SPEED) / PLAYER_SPEED * MOB_SPEED;
		AttributeInstance theirSpeed = attacker.getAttribute(Attributes.MOVEMENT_SPEED);
		double faster = theirSpeed == null || theirSpeed.getValue() <= 0.0 ? 1.0 : Mth.clamp(mine / theirSpeed.getValue() - 1.0, 0.0, 1.0);
		AttributeInstance theirFist = attacker.getAttribute(Attributes.ATTACK_DAMAGE);
		double stronger = theirFist == null || theirFist.getValue() <= 0.0 ? 1.0
			: Mth.clamp(player.getAttributeValue(Attributes.ATTACK_DAMAGE) / theirFist.getValue() - 1.0, 0.0, 1.0);
		float edge = (float)((faster + stronger) / 2.0);
		return MAX_CHANCE * trained * (BASE_SHARE + (1.0F - BASE_SHARE) * edge);
	}

	private static void slip(ServerPlayer player, LivingEntity attacker) {
		ServerLevel level = player.level();
		Vec3 away = player.position().subtract(attacker.position());
		Vec3 side = new Vec3(-away.z, 0.0, away.x);
		side = side.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : side.normalize().scale(player.getRandom().nextBoolean() ? STEP : -STEP);
		player.setDeltaMovement(player.getDeltaMovement().add(side));
		player.syncVelocity = true;
		Vec3 at = player.position();
		level.sendParticles(ZFighterPower.dust(ZFighterPower.live(player)), true, false, at.x, at.y + 1.0, at.z, 8, 0.2, 0.5, 0.2, 0.0);
		level.sendParticles(ParticleTypes.POOF, true, false, at.x, at.y + 0.8, at.z, 4, 0.2, 0.3, 0.2, 0.02);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.5F, 1.9F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.BREEZE_JUMP, SoundSource.PLAYERS, 0.5F, 2.0F);
	}

	/** Every tick, per Z Fighter: each second of shadowboxing is counted. */
	static void tick(ServerPlayer player) {
		if (!Meditate.shadowboxing(player)) {
			return;
		}
		if (player.tickCount % 20 == 0) {
			int heavy = ZFighterTraining.heavyGravity(player) ? 2 : 1;
			player.setAttached(TRAINED, Math.min(FULL_SECONDS, player.getAttachedOrElse(TRAINED, 0) + heavy));
		}
	}
}
