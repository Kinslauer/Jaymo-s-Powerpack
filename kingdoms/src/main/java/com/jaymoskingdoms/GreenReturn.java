package com.jaymoskingdoms;

import com.jaymoskingdoms.Classes.PlayerClass;
import java.util.Comparator;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * The Druid's rite of the green return (nature's rites, {@link NatureRites}): within {@link #WINDOW_TICKS} of its death, a
 * fresh body that still has its head is brought back alive, not undead, by a Druid who looks at it. It gets up as who it was
 * (its kind and saved look, as the death school's raising rebuilds it, {@link Thralls#raiseAs}) with a share of its health and
 * a weak spell after. A player's body, a headless one, a boss and an old one are not brought back.
 */
public final class GreenReturn {
	public static final String SPELL = "green_return";
	/** How soon after death the rite works (ticks), how far the Druid may stand (blocks), and what comes back. */
	static final int WINDOW_TICKS = 20 * 60 * 3;
	static final double REACH = 8.0;
	static final float HEALTH_SHARE = 0.4F;
	static final int WEAK_TICKS = 20 * 30;
	static final int GREEN = 0x7BC96A;

	private GreenReturn() {
	}

	static boolean cast(ServerLevel level, ServerPlayer caster) {
		if (!Classes.is(caster, PlayerClass.DRUID)) {
			caster.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.green_return.druids"));
			return false;
		}
		Vec3 eye = caster.getEyePosition();
		Vec3 look = caster.getLookAngle();
		Entity corpse = level.getEntities(caster, caster.getBoundingBox().inflate(REACH), c -> KitchenCompat.isFreeCorpse(c) && !KitchenCompat.isPlayerCorpse(c)).stream()
			.filter(c -> {
				Vec3 to = c.getBoundingBox().getCenter().subtract(eye);
				return to.length() <= REACH && to.normalize().dot(look) > 0.8;
			})
			.min(Comparator.comparingDouble(caster::distanceToSqr)).orElse(null);
		if (corpse == null) {
			caster.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.green_return.none"));
			return false;
		}
		if (!KitchenCompat.headOn(corpse)) {
			caster.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.green_return.headless"));
			return false;
		}
		if (KitchenCompat.sinceDeath(corpse) > WINDOW_TICKS) {
			caster.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.green_return.late"));
			return false;
		}
		Mob back = revive(level, corpse);
		if (back == null) {
			caster.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.green_return.wont"));
			return false;
		}
		Fx.ring(level, back.position().add(0.0, 0.1, 0.0), 1.6F, GREEN);
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, back.getX(), back.getY(0.5), back.getZ(), 16, 0.4, 0.5, 0.4, 0.0);
		level.playSound(null, back.getX(), back.getY(), back.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 0.7F);
		return true;
	}

	/** The body gets up alive as who it was and is gone; null if it can't (not a mob, or too great a thing). */
	private static Mob revive(ServerLevel level, Entity corpse) {
		EntityType<?> type = KitchenCompat.sourceType(corpse);
		CompoundTag saved = KitchenCompat.lookTag(corpse);
		if (type == null || type == EntityTypes.PLAYER || type == EntityTypes.WITHER || type == EntityTypes.ENDER_DRAGON || type == EntityTypes.WARDEN
			|| type == EntityTypes.ELDER_GUARDIAN || saved == null || !(type.create(level, EntitySpawnReason.MOB_SUMMONED) instanceof Mob mob)) {
			return null;
		}
		CompoundTag look = saved.copy();
		UUID was = look.read("UUID", UUIDUtil.CODEC).orElse(null);
		for (String gone : new String[] {"UUID", "Health", "DeathTime", "HurtTime", "Pos", "Motion", "Rotation", "Passengers", "leash", "Fire", "Brain", "NoAI"}) {
			look.remove(gone);
		}
		try {
			mob.load(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess(), look));
		} catch (RuntimeException e) {
			// (another mod's saved part that won't load: the body keeps what was read before it)
			JaymosKingdoms.LOGGER.warn("A revived {} only partly loaded: {}", type, e.toString());
		}
		// the same one it was, unless that id is taken
		mob.setUUID(was != null && level.getEntity(was) == null ? was : UUID.randomUUID());
		mob.entityTags().removeIf(t -> t.contains("downed"));
		mob.snapTo(corpse.getX(), corpse.getY(), corpse.getZ(), corpse.getYRot(), 0.0F);
		mob.deathTime = 0;
		mob.hurtTime = 0;
		mob.clearFire();
		mob.setHealth(Math.max(1.0F, mob.getMaxHealth() * HEALTH_SHARE));
		mob.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, WEAK_TICKS, 0));
		level.addFreshEntity(mob);
		corpse.discard();
		return mob;
	}
}
