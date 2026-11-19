package com.jaymospowers.rig;

import com.jaymospowers.core.Shakes;
import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.rig.RigActives.State;
import com.jaymospowers.rig.RigMoves;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Rig's gun actives (Andy's picks from the Zemo&Baki Blue Beetle mod; ideas only, our own code).
 */
public final class RigGuns {

    // ---- palm blast held charge ------------------------------------------------------------------------------------
    static final int CHARGE_EXTRA_COST = 80, CHARGE_MAX_HOLD = 40;
    static final float CHARGE_DAMAGE_MULT = 2.5F, CHARGE_BURST_MULT = 2.0F, CHARGE_KNOCKBACK_MULT = 1.8F;
    static final float CHARGE_GLOW_MAX = 1.0F;

    // ---- gatling arm -----------------------------------------------------------------------------------------------
    static final int GATLING_SPIN_COST = 60, GATLING_TICK_COST = 8, GATLING_MAX_HEAT = 120, GATLING_VENT_COOLDOWN = 80;
    static final int GATLING_BARRELS = 6;
    static final double GATLING_SPREAD = 0.08, GATLING_SPEED = 2.2;
    static final float GATLING_DAMAGE = 2.0F;

    // ---- railgun ---------------------------------------------------------------------------------------------------
    static final int RAIL_COST = 350, RAIL_COOLDOWN = 300, RAIL_CHARGE_TICKS = 20;
    static final double RAIL_RANGE = 60.0, RAIL_WIDTH = 0.5;
    static final float RAIL_DAMAGE = 12.0F;
    static final double RAIL_PUSH = 0.8;
    static final double RAIL_SCAR_RADIUS = 1.0;

    // ---- sonic cannon ----------------------------------------------------------------------------------------------
    static final int SONIC_COST = 200, SONIC_TICK_COST = 15, SONIC_MAX_HOLD = 60;
    static final float SONIC_CONE_DEG = 45.0F, SONIC_LENGTH = 16.0F;
    static final float SONIC_DAMAGE = 4.0F;
    static final int SONIC_STUN_TICKS = 40;
    static final double SONIC_KNOCKBACK = 1.5;

    // ---- twin beam -------------------------------------------------------------------------------------------------
    static final int TWIN_COST = 400, TWIN_COOLDOWN = 240;
    static final int TWIN_CHARGE_TICKS = 10, TWIN_BEAM_TICKS = 14;
    static final double TWIN_RANGE = 32.0, TWIN_WIDTH = 1.2;
    static final float TWIN_DAMAGE = 5.0F, TWIN_BURN_SECONDS = 3.0F;
    static final double TWIN_PUSH = 0.3;
    static final int TWIN_BRACE_SLOW = 1;

    // ---- siege mode ------------------------------------------------------------------------------------------------
    static final int SIEGE_ENTER_COST = 100, SIEGE_SHELL_COST = 80, SIEGE_SHELL_COOLDOWN = 25, SIEGE_MAX_SHELLS = 12;
    static final double SIEGE_RANGE = 80.0, SIEGE_SCATTER = 2.5;
    static final float SIEGE_BURST = 3.0F;
    static final double SIEGE_HEIGHT = 35.0, SIEGE_FALL_TICKS = 28.0;

    private RigGuns() {}

    // ==================================================================================================================
    // Palm blast held charge
    // ==================================================================================================================
    static void palmCharge(ServerPlayer player, State state) {
        if (state.palmCooldown > 0) return;
        if (!RigActives.needs(player, Rig.LOCK_LEVEL)) return;
        boolean over = RigActives.overcharged(player);
        int cost = over ? 0 : RigActives.PALM_COST + CHARGE_EXTRA_COST;
        if (!over && !Rig.spend(player, cost)) { RigMind.empty(player); return; }
        state.palmCooldown = over ? (int)(RigActives.PALM_COOLDOWN * RigActives.OVER_COOLDOWN_SHARE) : RigActives.PALM_COOLDOWN + 10;
        RigMoves.start(player, RigMoves.PALM_CHARGE);
        state.palmChargeTicks = 0;
    }

    static void tickPalmCharge(ServerPlayer player, State state) {
        if (state.palmChargeTicks < 0) return;
        if (!Rig.fullSet(player) || !player.isAlive()) { state.palmChargeTicks = -1; player.removeAttached(RigMoves.MOVE); return; }
        state.palmChargeTicks++;
        ServerLevel level = player.level();
        Vec3 palm = player.getEyePosition().add(player.getLookAngle().scale(0.6)).add(0.0, -0.35, 0.0);
        float share = Math.min(1.0F, state.palmChargeTicks / (float)CHARGE_MAX_HOLD);
        if (state.palmChargeTicks % 4 == 0) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.5F, 0.8F + share * 1.0F);
            for (int i = 0; i < 3; i++) {
                Vec3 p = palm.add(level.getRandom().nextGaussian() * 0.3, level.getRandom().nextGaussian() * 0.3, level.getRandom().nextGaussian() * 0.3);
                level.sendParticles(MagicFx.mote(RigForgeBlock.FX_COLOR, 0.6F + share * 0.5F), p.x, p.y, p.z, 0, 0.0, 0.0, 0.0, 0.0);
            }
        }
        if (state.palmChargeTicks >= CHARGE_MAX_HOLD) firePalmCharge(player, state, true);
    }

    static void releasePalmCharge(ServerPlayer player, State state) {
        if (state.palmChargeTicks <= 0) return;
        firePalmCharge(player, state, false);
    }

    private static void firePalmCharge(ServerPlayer player, State state, boolean maxed) {
        if (!Rig.fullSet(player)) { state.palmChargeTicks = -1; return; }
        ServerLevel level = player.level();
        Rig.Augment augment = Rig.augment(player);
        boolean over = RigActives.overcharged(player);
        float share = Math.min(1.0F, state.palmChargeTicks / (float)CHARGE_MAX_HOLD);
        state.palmChargeTicks = -1;
        player.removeAttached(RigMoves.MOVE);
        RigActives.later(player, 2, () -> {
            Vec3 eye = player.getEyePosition();
            Vec3 look = player.getLookAngle();
            LivingEntity lock = RigActives.locked(player);
            if (lock != null) {
                Vec3 to = lock.getBoundingBox().getCenter().subtract(eye);
                if (to.length() <= RigActives.PALM_RANGE && to.normalize().dot(look) > RigActives.PALM_LOCK_DOT && player.hasLineOfSight(lock)) look = to.normalize();
            }
            Vec3 end = eye.add(look.scale(RigActives.PALM_RANGE));
            BlockHitResult block = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (block.getType() != HitResult.Type.MISS) end = block.getLocation();
            EntityHitResult hit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(player, eye, end,
                    player.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0),
                    e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && e != player, eye.distanceToSqr(end));
            if (hit != null) end = hit.getLocation();
            Vec3 from = eye.add(look.scale(0.6)).add(0.0, -0.35, 0.0);
            double length = from.distanceTo(end);
            Vec3 dir = end.subtract(from).normalize();
            int color = over ? RigActives.OVER_COLOR : RigForgeBlock.FX_COLOR;
            float width = (over ? 0.16F : 0.08F) * (0.5F + share * 1.0F);
            MagicFx.beam(level, from, from.add(dir.scale(length)), width, color, 8);
            MagicFx.blast(level, end, (over ? 0.9F : 0.5F) * (0.5F + share * CHARGE_BURST_MULT), color);
            Shakes.send(player, RigActives.PALM_SHAKE * (over ? 1.5F : 1.0F) * (0.5F + share), end);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0F, 1.5F + share * 0.5F);
            if (hit != null && hit.getEntity() instanceof LivingEntity target) {
                DamageSource source = level.damageSources().playerAttack(player);
                float damage = RigActives.PALM_DAMAGE * RigActives.levelDamage(player) * Rig.frame(player).shotDamage * (over ? RigActives.OVER_DAMAGE : 1.0F) * (0.5F + share * CHARGE_DAMAGE_MULT);
                target.hurtServer(level, source, damage);
                double knock = RigActives.PALM_KNOCKBACK * (augment == Rig.Augment.RESONANCE ? RigActives.RESONANCE_KNOCKBACK : 1.0) * (0.5F + share * CHARGE_KNOCKBACK_MULT);
                target.knockback(knock, -look.x, -look.z, source, damage);
                target.syncVelocity = true;
                if (augment == Rig.Augment.WEB) { target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, RigActives.WEB_SLOW_TICKS, RigActives.WEB_SLOW_LEVEL), player); level.sendParticles(ParticleTypes.WHITE_SMOKE, end.x, end.y, end.z, 8, 0.4, 0.4, 0.4, 0.01); }
                else if (augment == Rig.Augment.SMOKE) { target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, RigActives.SMOKE_BLIND_TICKS, 0), player); level.sendParticles(ParticleTypes.LARGE_SMOKE, end.x, end.y, end.z, 10, 0.4, 0.4, 0.4, 0.01); }
            } else if (hit == null && block.getType() != HitResult.Type.MISS) {
                com.jaymospowers.rig.RigBank.palm(player, block.getLocation(), block.getDirection(), RigActives.PALM_DAMAGE * RigActives.levelDamage(player) * Rig.frame(player).shotDamage * (over ? RigActives.OVER_DAMAGE : 1.0F) * (0.5F + share * CHARGE_DAMAGE_MULT), RigForgeBlock.FX_COLOR);
            }
        });
    }

    // ==================================================================================================================
    // Gatling arm
    // ==================================================================================================================
    static void gatling(ServerPlayer player, State state) {
        if (state.gatlingCooldown > 0 || state.gatlingHeat >= GATLING_MAX_HEAT) return;
        if (!RigActives.needs(player, Rig.SCAN_LEVEL)) return;
        if (!Rig.spend(player, GATLING_SPIN_COST)) { RigMind.empty(player); return; }
        state.gatlingFiring = true; state.gatlingHeat = 0; state.gatlingSpinTicks = 0;
        RigMoves.start(player, RigMoves.GATLING);
        RigMind.say(player, "gatling", 1.0F);
    }

    static void stopGatling(ServerPlayer player, State state) {
        if (!state.gatlingFiring) return;
        state.gatlingFiring = false;
        state.gatlingCooldown = state.gatlingHeat >= GATLING_MAX_HEAT ? GATLING_VENT_COOLDOWN : 20;
        player.removeAttached(RigMoves.MOVE);
        if (state.gatlingHeat >= GATLING_MAX_HEAT) ventGatling(player, state);
    }

    static void tickGatling(ServerPlayer player, State state) {
        if (!state.gatlingFiring) { if (state.gatlingCooldown > 0) state.gatlingCooldown--; if (state.gatlingHeat > 0 && state.gatlingCooldown == 0) state.gatlingHeat = Math.max(0, state.gatlingHeat - 4); return; }
        if (!Rig.fullSet(player) || !player.isAlive()) { stopGatling(player, state); return; }
        ServerLevel level = player.level();
        if (state.gatlingSpinTicks < 10) { state.gatlingSpinTicks++; if (state.gatlingSpinTicks % 3 == 0) level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.4F, 0.7F + state.gatlingSpinTicks * 0.05F); return; }
        if (!Rig.spend(player, GATLING_TICK_COST)) { RigMind.empty(player); stopGatling(player, state); return; }
        state.gatlingHeat += 2;
        if (state.gatlingHeat >= GATLING_MAX_HEAT) { stopGatling(player, state); return; }
        Vec3 look = player.getLookAngle();
        Vec3 right = new Vec3(-look.z, 0.0, look.x).normalize();
        Vec3 shoulder = player.getEyePosition().add(0.0, 0.25, 0.0);
        for (int b = 0; b < GATLING_BARRELS; b++) {
            double side = (b % 2 == 0 ? 1 : -1) * 0.2 * (1 + b / 2);
            Vec3 start = shoulder.add(right.scale(side)).add(look.scale(0.5));
            Vec3 velocity = look.add(level.getRandom().nextGaussian() * GATLING_SPREAD, level.getRandom().nextGaussian() * GATLING_SPREAD, level.getRandom().nextGaussian() * GATLING_SPREAD).normalize().scale(GATLING_SPEED);
            Vec3 boltEnd = start.add(velocity.scale(40.0));
            BlockHitResult block = level.clip(new ClipContext(start, boltEnd, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (block.getType() != HitResult.Type.MISS) boltEnd = block.getLocation();
            EntityHitResult hit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(level, player, start, boltEnd, new AABB(start, boltEnd).inflate(0.5), e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && e != player, 0.3F);
            if (hit != null) boltEnd = hit.getLocation();
            MagicFx.beam(level, start, boltEnd, 0.04F, RigForgeBlock.FX_COLOR, 3);
            if (hit != null && hit.getEntity() instanceof LivingEntity target) { DamageSource source = level.damageSources().playerAttack(player); float damage = GATLING_DAMAGE * Rig.frame(player).shotDamage; target.hurtServer(level, source, damage); target.knockback(0.3, -look.x, -look.z, source, damage); target.syncVelocity = true; }
        }
        if (state.gatlingSpinTicks % 4 == 0) level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 0.3F, 1.8F);
        state.gatlingSpinTicks++;
        Vec3 vent = shoulder.add(0.0, 0.1, 0.0).add(look.scale(0.3));
        level.sendParticles(MagicFx.miasma(0x404050, 0.6F), vent.x, vent.y, vent.z, 2, 0.1, 0.05, 0.1, 0.01);
    }

    private static void ventGatling(ServerPlayer player, State state) {
        ServerLevel level = player.level();
        Vec3 shoulder = player.getEyePosition().add(0.0, 0.25, 0.0);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.8F, 0.6F);
        for (int i = 0; i < 12; i++) RigActives.later(player, i * 2, () -> { level.sendParticles(MagicFx.miasma(0x606080, 1.0F), shoulder.x, shoulder.y, shoulder.z, 6, 0.3, 0.3, 0.3, 0.05); level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.3F, 0.5F + level.getRandom().nextFloat() * 0.3F); });
        Shakes.send(player, 0.5F);
    }

    // ==================================================================================================================
    // Railgun
    // ==================================================================================================================
    static void railgun(ServerPlayer player, State state) {
        if (state.railgunCooldown > 0 || state.railgunCharge >= 0) return;
        if (!RigActives.needs(player, Rig.UNIBEAM_LEVEL)) return;
        if (!Rig.spend(player, RAIL_COST)) { RigMind.empty(player); return; }
        state.railgunCharge = 0; state.railgunCooldown = RAIL_COOLDOWN;
        RigMoves.start(player, RigMoves.RAILGUN);
        RigMind.say(player, "railgun", 1.0F);
    }

    static void tickRailgun(ServerPlayer player, State state) {
        if (state.railgunCharge < 0) { if (state.railgunCooldown > 0) state.railgunCooldown--; return; }
        if (!Rig.fullSet(player) || !player.isAlive()) { state.railgunCharge = -1; player.removeAttached(RigMoves.MOVE); return; }
        ServerLevel level = player.level();
        state.railgunCharge++;
        Vec3 chest = player.getEyePosition().add(0.0, -0.5, 0.0).add(player.getLookAngle().scale(0.35));
        float share = Math.min(1.0F, state.railgunCharge / (float)RAIL_CHARGE_TICKS);
        if (state.railgunCharge % 3 == 0) { level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.6F, 0.8F + share * 1.2F); for (int i = 0; i < 4; i++) { Vec3 p = chest.add(level.getRandom().nextGaussian() * 0.5, level.getRandom().nextGaussian() * 0.5, level.getRandom().nextGaussian() * 0.5); level.sendParticles(MagicFx.mote(0x88CCFF, 0.5F + share * 0.8F), p.x, p.y, p.z, 0, 0.0, 0.0, 0.0, 0.0); } }
        if (state.railgunCharge >= RAIL_CHARGE_TICKS) fireRailgun(player, state);
    }

    static void releaseRailgun(ServerPlayer player, State state) { if (state.railgunCharge <= 0) return; fireRailgun(player, state); }

    private static void fireRailgun(ServerPlayer player, State state) {
        if (!Rig.fullSet(player)) { state.railgunCharge = -1; return; }
        ServerLevel level = player.level();
        float share = Math.min(1.0F, state.railgunCharge / (float)RAIL_CHARGE_TICKS);
        state.railgunCharge = -1; player.removeAttached(RigMoves.MOVE);
        RigActives.later(player, 1, () -> {
            Vec3 eye = player.getEyePosition(); Vec3 look = player.getLookAngle();
            LivingEntity lock = RigActives.locked(player);
            if (lock != null) { Vec3 to = lock.getBoundingBox().getCenter().subtract(eye); if (to.length() <= RAIL_RANGE && to.normalize().dot(look) > RigActives.PALM_LOCK_DOT && player.hasLineOfSight(lock)) look = to.normalize(); }
            Vec3 end = eye.add(look.scale(RAIL_RANGE));
            BlockHitResult block = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (block.getType() != HitResult.Type.MISS) { end = block.getLocation(); com.jaymospowers.core.BeamScars.touch(player, block, RAIL_SCAR_RADIUS); }
            double length = eye.distanceTo(end);
            for (double d = 0.5; d < length; d += 0.3) { Vec3 p = eye.add(look.scale(d)); level.sendParticles(MagicFx.mote(0x88CCFF, 1.2F), p.x, p.y, p.z, 0, 0.02, 0.02, 0.02, 0.0); if (level.getRandom().nextInt(3) == 0) level.sendParticles(MagicFx.spark(0xFFFFFF, 0.8F), p.x, p.y, p.z, 1, 0.1, 0.1, 0.1, 0.02); }
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, end.x, end.y, end.z, 8, 0.3, 0.3, 0.3, 0.1);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.2F, 1.8F);
            Shakes.send(player, 1.8F, end);
            DamageSource source = level.damageSources().playerAttack(player);
            for (LivingEntity target : caught(player, eye, end)) { target.hurtServer(level, source, RAIL_DAMAGE * share); target.push(look.x * RAIL_PUSH, look.y * RAIL_PUSH * 0.5, look.z * RAIL_PUSH); target.syncVelocity = true; }
        });
    }

    private static List<LivingEntity> caught(ServerPlayer player, Vec3 from, Vec3 to) {
        List<LivingEntity> caught = new ArrayList<>();
        for (LivingEntity entity : player.level().getEntitiesOfClass(LivingEntity.class, new AABB(from, to).inflate(RAIL_WIDTH + 1.0), e -> e != player && e.isAlive() && !e.isSpectator())) {
            AABB box = entity.getBoundingBox().inflate(RAIL_WIDTH);
            if (box.contains(from) || box.clip(from, to).isPresent()) caught.add(entity);
        }
        return caught;
    }

    // ==================================================================================================================
    // Sonic cannon
    // ==================================================================================================================
    static void sonic(ServerPlayer player, State state) {
        if (state.sonicCooldown > 0 || state.sonicFiring) return;
        if (!RigActives.needs(player, Rig.UNIBEAM_LEVEL)) return;
        if (!Rig.spend(player, SONIC_COST)) { RigMind.empty(player); return; }
        state.sonicFiring = true; state.sonicTicks = 0;
        RigMoves.start(player, RigMoves.SONIC);
        RigMind.say(player, "sonic", 1.0F);
    }

    static void stopSonic(ServerPlayer player, State state) { if (!state.sonicFiring) return; state.sonicFiring = false; state.sonicCooldown = 40; player.removeAttached(RigMoves.MOVE); }

    static void tickSonic(ServerPlayer player, State state) {
        if (!state.sonicFiring) { if (state.sonicCooldown > 0) state.sonicCooldown--; return; }
        if (!Rig.fullSet(player) || !player.isAlive() || state.sonicTicks >= SONIC_MAX_HOLD) { stopSonic(player, state); return; }
        ServerLevel level = player.level();
        if (!Rig.spend(player, SONIC_TICK_COST)) { RigMind.empty(player); stopSonic(player, state); return; }
        state.sonicTicks++;
        Vec3 eye = player.getEyePosition(); Vec3 look = player.getLookAngle();
        double angleRad = Math.toRadians(SONIC_CONE_DEG);
        Vec3 right = new Vec3(-look.z, 0.0, look.x).normalize();
        for (int i = 0; i < 3; i++) { double spread = (i - 1) * angleRad * 0.5; Vec3 dir = look.scale(Math.cos(spread)).add(right.scale(Math.sin(spread))); MagicFx.wave(level, eye.add(dir.scale(1.0)), dir, SONIC_LENGTH, (float)(SONIC_LENGTH * Math.sin(angleRad) * 2.0), 0xA0D0FF, 6); }
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 0.7F, 1.2F);
        DamageSource source = level.damageSources().playerAttack(player);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, new AABB(eye, eye.add(look.scale(SONIC_LENGTH))).inflate(SONIC_LENGTH * Math.sin(angleRad) + 1.0), e -> e != player && e.isAlive() && !e.isSpectator() && !e.isAlliedTo(player))) {
            Vec3 to = target.getBoundingBox().getCenter().subtract(eye);
            if (to.lengthSqr() > SONIC_LENGTH * SONIC_LENGTH) continue;
            double dot = to.normalize().dot(look);
            if (dot < Math.cos(angleRad)) continue;
            target.hurtServer(level, source, SONIC_DAMAGE * Rig.frame(player).shotDamage);
            target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SONIC_STUN_TICKS, 4, false, false, false));
            target.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, SONIC_STUN_TICKS, 4, false, false, false));
            target.knockback((float)SONIC_KNOCKBACK, -look.x, -look.z, source, SONIC_DAMAGE);
            target.syncVelocity = true;
        }
        if (Rig.augment(player) == Rig.Augment.RESONANCE) {
                    for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, new AABB(eye, eye.add(look.scale(SONIC_LENGTH * 1.5))).inflate(SONIC_LENGTH * Math.sin(angleRad) * 1.5 + 1.0),
                            e -> e != player && e.isAlive() && !e.isSpectator() && e.hasEffect(MobEffects.GLOWING))) {
                        Vec3 to = target.getBoundingBox().getCenter().subtract(eye);
                        if (to.lengthSqr() > SONIC_LENGTH * SONIC_LENGTH * 2.25) continue;
                        double dot = to.normalize().dot(look);
                        if (dot < Math.cos(angleRad)) continue;
                        target.addEffect(new MobEffectInstance(MobEffects.NAUSEA, SONIC_STUN_TICKS, 0, false, false, false));
                    }
                }

    // ==================================================================================================================
    // Twin beam (Unibeam variant)
    // ==================================================================================================================
        }
    static void twinBeam(ServerPlayer player, State state) {
        if (state.twinCooldown > 0 || state.twinBeam >= 0) return;
        if (!RigActives.needs(player, Rig.MAX_LEVEL)) return;
        if (!Rig.spend(player, TWIN_COST)) { RigMind.empty(player); return; }
        state.twinBeam = 0; state.twinCooldown = TWIN_COOLDOWN;
        RigMoves.start(player, RigMoves.TWIN);
        RigMind.say(player, "twin_beam", 1.0F);
    }

    static void tickTwinBeam(ServerPlayer player, State state) {
        if (state.twinBeam < 0) { if (state.twinCooldown > 0) state.twinCooldown--; return; }
        if (!Rig.fullSet(player) || !player.isAlive()) { state.twinBeam = -1; player.removeAttached(RigMoves.MOVE); return; }
        int t = state.twinBeam++;
        ServerLevel level = player.level();
        Vec3 chest = player.getEyePosition().add(0.0, -0.5, 0.0).add(player.getLookAngle().scale(0.35));
        if (t < TWIN_CHARGE_TICKS) { float share = t / (float)TWIN_CHARGE_TICKS; if (t % 3 == 0) level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.7F, 0.9F + share * 1.0F); for (int i = 0; i < 5; i++) { Vec3 p = chest.add(level.getRandom().nextGaussian() * 0.8, level.getRandom().nextGaussian() * 0.8, level.getRandom().nextGaussian() * 0.8); level.sendParticles(MagicFx.mote(RigForgeBlock.FX_COLOR, 0.6F + share * 1.0F), p.x, p.y, p.z, 0, 0.0, 0.0, 0.0, 0.0); } player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 4, TWIN_BRACE_SLOW, false, false, false)); return; }
        int beam = t - TWIN_CHARGE_TICKS;
        if (beam >= TWIN_BEAM_TICKS) { state.twinBeam = -1; return; }
        if (beam == 0) { level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.3F, 1.6F); Shakes.send(player, 1.8F); }
        if (beam % 3 == 0) level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.6F, 1.4F);
        player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 4, TWIN_BRACE_SLOW, false, false, false));
        Vec3 look = aimTwin(player, chest);
        Vec3 end = chest.add(look.scale(TWIN_RANGE));
        BlockHitResult block = level.clip(new ClipContext(chest, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (block.getType() != HitResult.Type.MISS) { end = block.getLocation(); com.jaymospowers.core.BeamScars.touch(player, block, TWIN_WIDTH); }
        double length = chest.distanceTo(end);
        for (double d = 0.8; d < length; d += 0.3) { Vec3 p = chest.add(look.scale(d)); level.sendParticles(MagicFx.mote(RigForgeBlock.FX_COLOR, 1.5F), p.x, p.y, p.z, 2, 0.05, 0.05, 0.05, 0.0); if (level.getRandom().nextInt(2) == 0) level.sendParticles(ParticleTypes.FLAME, p.x, p.y, p.z, 2, 0.1, 0.1, 0.1, 0.03); }
        level.sendParticles(ParticleTypes.FLAME, end.x, end.y, end.z, 6, 0.3, 0.3, 0.3, 0.05);
        if (beam % 2 != 0) return;
        DamageSource source = level.damageSources().playerAttack(player);
        for (LivingEntity target : caughtTwin(player, chest, end)) { target.hurtServer(level, source, TWIN_DAMAGE * Rig.frame(player).shotDamage); target.igniteForSeconds(TWIN_BURN_SECONDS); target.push(look.x * TWIN_PUSH, look.y * TWIN_PUSH * 0.5, look.z * TWIN_PUSH); target.syncVelocity = true; }
    }

    private static Vec3 aimTwin(ServerPlayer player, Vec3 chest) { Vec3 look = player.getLookAngle(); LivingEntity lock = RigActives.locked(player); if (lock != null) { Vec3 to = lock.getBoundingBox().getCenter().subtract(chest); if (to.length() <= TWIN_RANGE && to.normalize().dot(look) > RigActives.PALM_LOCK_DOT && player.hasLineOfSight(lock)) return to.normalize(); } return look; }

    private static List<LivingEntity> caughtTwin(ServerPlayer player, Vec3 from, Vec3 to) { List<LivingEntity> caught = new ArrayList<>(); for (LivingEntity entity : player.level().getEntitiesOfClass(LivingEntity.class, new AABB(from, to).inflate(TWIN_WIDTH + 1.0), e -> e != player && e.isAlive() && !e.isSpectator())) { AABB box = entity.getBoundingBox().inflate(TWIN_WIDTH); if (box.contains(from) || box.clip(from, to).isPresent()) caught.add(entity); } return caught; }

    // ==================================================================================================================
    // Siege mode
    // ==================================================================================================================
    static void siegeMode(ServerPlayer player, State state) {
        if (state.siegeActive || state.siegeCooldown > 0) return;
        if (!RigActives.needs(player, Rig.MAX_LEVEL)) return;
        if (Rig.frame(player) != RigFrame.SIEGE) { player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("message.jaymospowers.rig.guns.siege_frame")); return; }
        if (!Rig.spend(player, SIEGE_ENTER_COST)) { RigMind.empty(player); return; }
        state.siegeActive = true; state.siegeShellsFired = 0; state.siegeShellCooldown = 0;
        RigMoves.start(player, RigMoves.SIEGE);
        RigMind.say(player, "siege_mode", 1.0F);
    }

    static void exitSiegeMode(ServerPlayer player, State state) { if (!state.siegeActive) return; state.siegeActive = false; state.siegeCooldown = 120; player.removeAttached(RigMoves.MOVE); }

    static void siegeShell(ServerPlayer player, State state) {
        if (!state.siegeActive || state.siegeShellCooldown > 0 || state.siegeShellsFired >= SIEGE_MAX_SHELLS) return;
        if (!Rig.spend(player, SIEGE_SHELL_COST)) { RigMind.empty(player); exitSiegeMode(player, state); return; }
        state.siegeShellCooldown = SIEGE_SHELL_COOLDOWN; state.siegeShellsFired++;
        ServerLevel level = player.level();
        Vec3 eye = player.getEyePosition(); Vec3 look = player.getLookAngle();
        Vec3 targetPos = eye.add(look.scale(SIEGE_RANGE));
        BlockHitResult hit = level.clip(new ClipContext(eye, targetPos, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, player));
        if (hit.getType() != HitResult.Type.MISS) targetPos = hit.getLocation();
        Vec3 cannon = eye.add(0.0, 1.2, 0.0).add(player.getLookAngle().scale(-0.8));
        MagicFx.stroke(level, cannon, cannon.add(0.0, SIEGE_HEIGHT * 0.3, 0.0).add(look.scale(2.0)), 0.15F, RigActives.FLAME_COLOR);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 1.0F, 0.5F);
        Shakes.send(player, 0.6F);
        Vec3 targetPosFinal = targetPos;
        RigActives.later(player, (int)SIEGE_FALL_TICKS, () -> { double angle = level.getRandom().nextDouble() * Math.PI * 2.0; double far = Math.sqrt(level.getRandom().nextDouble()) * SIEGE_SCATTER; Vec3 land = targetPosFinal.add(Math.cos(angle) * far, 0.0, Math.sin(angle) * far); BlockHitResult ground = level.clip(new ClipContext(land.add(0.0, 6.0, 0.0), land.add(0.0, -6.0, 0.0), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, player)); Vec3 finalLand = ground.getType() == HitResult.Type.MISS ? land : ground.getLocation(); MagicFx.stroke(level, finalLand.add(0.0, SIEGE_HEIGHT, 0.0), finalLand, 0.3F, RigActives.FLAME_HOT); level.explode(player, finalLand.x, finalLand.y + 0.2, finalLand.z, SIEGE_BURST + (Rig.frame(player) == RigFrame.SIEGE ? RigFrame.GUN_BURST : 0.0F), Level.ExplosionInteraction.NONE); level.playSound(null, finalLand.x, finalLand.y, finalLand.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.0F, 0.8F); BlockHitResult blockHit = level.clip(new ClipContext(finalLand.add(0.0, 1.0, 0.0), finalLand.add(0.0, -1.0, 0.0), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, player)); if (blockHit.getType() == HitResult.Type.BLOCK) com.jaymospowers.core.BeamScars.touch(player, blockHit, SIEGE_BURST * 0.4); });
    }

    static void tickSiegeMode(ServerPlayer player, State state) {
        if (!state.siegeActive) { if (state.siegeCooldown > 0) state.siegeCooldown--; return; }
        if (!Rig.fullSet(player) || !player.isAlive() || player.isShiftKeyDown()) { exitSiegeMode(player, state); return; }
        player.setDeltaMovement(0.0, player.getDeltaMovement().y, 0.0); player.syncVelocity = true;
        if (state.siegeShellCooldown > 0) state.siegeShellCooldown--;
    }
}