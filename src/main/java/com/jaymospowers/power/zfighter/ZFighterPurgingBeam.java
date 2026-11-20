package com.jaymospowers.power.zfighter;

import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.LockOn;
import com.jaymospowers.core.Soundscape;
import com.jaymospowers.core.wounds.Downed;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Purging Beam: hurts the corrupted and the dark far more, breaks possession and living nightmares.
 * Charged technique on V ({@link ZFighterTraining#PURGING_BEAM}).
 * Taught by Galactic Patrolmen ({@link ZFighterPatrol}).
 */
public final class ZFighterPurgingBeam {
    /** Ki to start, max at full charge. */
    static final float MIN_KI = 35.0F;
    static final float MAX_KI = 75.0F;
    /** Damage at min and max charge (base). */
    static final float DAMAGE_MIN = 12.0F;
    static final float DAMAGE_MAX = 25.0F;
    /** Damage multiplier against corrupted/dark/nightmare entities. */
    static final float CORRUPTED_MULT = 3.0F;
    /** Ticks to full charge. */
    public static final int FULL_TICKS = 70;
    /** Cooldown after firing. */
    static final int COOLDOWN = 260;
    /** Range (blocks). */
    static final double RANGE = 55.0;
    /** Beam width (blocks). */
    static final double WIDTH = 1.0;
    /** Speed (blocks/tick). */
    static final double SPEED = 3.0;

    private ZFighterPurgingBeam() {
    }

    static void init() {
    }

    static void clear() {
    }

    static void start(ServerPlayer player, ZFighterPower.Live live) {
        // not used for charged
    }

    /** Whether {@code entity} is corrupted, dark, or a nightmare (takes extra damage). */
    	static boolean isCorrupted(LivingEntity entity) {
    		if (entity.hasEffect(MobEffects.WITHER) || entity.hasEffect(MobEffects.DARKNESS)) {
    			return true;
    		}
    		if (Downed.isDowned(entity)) {
    			return true;
    		}
    		if (entity.entityTags().contains("corrupted") || entity.entityTags().contains("demon_realm")
    				|| entity.entityTags().contains("jaymospowers.dream_phantom")) {
    			return true;
    		}
    		// Corrupted players (Fate corruption)
    		if (entity instanceof ServerPlayer player && com.jaymospowers.core.Fate.isCorrupted(player)) {
    			return true;
    		}
    		// Possession: check for Revenant's possession tag or other possession indicators
    		if (entity.entityTags().contains("jaymospowers.possession")) {
    			return true;
    		}
    		for (MobEffectInstance effect : entity.getActiveEffects()) {
    			if (effect.getEffect().value().getCategory() == net.minecraft.world.effect.MobEffectCategory.HARMFUL
    					&& (effect.getEffect().value().getDescriptionId().contains("possession")
    							|| effect.getEffect().value().getDescriptionId().contains("corrupt"))) {
    				return true;
    			}
    		}
    		return false;
    	}

    /** V held: gathering the purging beam. */
    static void gather(ServerPlayer player, ZFighterPower.Live live, float charge) {
        ServerLevel level = player.level();
        Vec3 look = LockOn.aim(player);
        int color = 0xFF4AFF;
        Vec3 hands = ZFighterKi.waveHands(player, look);
        double size = 0.25 + 0.7 * charge;
        ZFighterKi.orb(level, -(player.getId() * 4 + 8), hands, size, color);
        if (charge >= 1.0F && level.getGameTime() % 3 == 0) {
            level.sendParticles(MagicFx.CRACKLE, true, false, hands.x, hands.y, hands.z, 4, 0.3, 0.3, 0.3, 0.2);
        }
        if (Soundscape.due(player, "ki.wave", 8, 13)) {
            Soundscape.play(level, player.position(), SoundSource.PLAYERS, ZFighterKi.KI_HUM.pitched(0.6F + charge * 0.5F), ZFighterKi.KI_SURGE);
        }
    }

    /** V released: fire the purging beam. */
    static void fire(ServerPlayer player, ZFighterPower.Live live, float charge) {
        ServerLevel level = player.level();
        Vec3 look = LockOn.aim(player);
        float kiPower = ZFighterPower.kiPower(player, live);
        int color = 0xFF4AFF;
        float damage = Mth.lerp(charge, DAMAGE_MIN, DAMAGE_MAX) * kiPower;

        Vec3 from = ZFighterKi.waveHands(player, look).add(look.scale(0.5));

        ZFighterWaveBeam.fire(player, from, look, new ZFighterWaveBeam.Spec(RANGE, SPEED, WIDTH, damage,
                (0.8F + charge * 0.5F), 0, true, 0.0F, net.minecraft.world.level.Level.ExplosionInteraction.NONE, false,
                color, ZFighterKi.NEEDLE, ZFighterWaveBeam.strength(charge), false, 1.5F));

        purgePath(level, player, from, look, damage * (CORRUPTED_MULT - 1.0F), color);

        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.3F, 1.0F);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.8F);
        ZFighterHands.thrust(player, ZFighterHands.BOTH, ZFighterHands.PUSH);
        ZFighterHands.shout(player);
        if (!player.isCreative()) {
            player.causeFoodExhaustion(1.8F * (1.0F + charge));
        }
    }

    private static void purgePath(ServerLevel level, ServerPlayer player, Vec3 from, Vec3 dir, float extraDamage, int color) {
        double step = 1.0;
        for (double d = 0; d < RANGE; d += step) {
            Vec3 at = from.add(dir.scale(d));
            for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
                    new AABB(at, at).inflate(WIDTH * 2.0), e -> ZFighterArts.valid(player, e) && e != player)) {
                if (isCorrupted(target)) {
                    target.setInvulnerableTime(0);
                    target.hurtServer(level, ZFighterKi.source(player), extraDamage);
                    level.sendParticles(MagicFx.wisp(0xFFFFFF, 2.0F), true, false, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                            12, 0.4, 0.4, 0.4, 0.1);
                    level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 2.0F);
                }
            }
        }
    }

    static String blocker(ZFighterPower.Live live) {
        return null;
    }

    static float min(ServerPlayer player) {
        return MIN_KI;
    }

    static float max(ServerPlayer player) {
        return MAX_KI;
    }

    static int cooldown(ServerPlayer player) {
        return COOLDOWN;
    }
}