package com.jaymospowers.rig;

import com.jaymospowers.JaymosPowers;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * (Rig patch 6) Tech override: golems shut down and doors obey you.
 * Iron and clockwork golems freeze for a moment, redstone doors open at a look,
 * and androids and mechs (built from Frostborn designs) stutter.
 */
public final class RigOverride {
    /** Charge cost and cooldown (ticks). */
    static final int COST = 180, COOLDOWN = 400;
    /** How far it reaches (blocks). */
    static final double RANGE = 16.0;
    /** How long golems freeze (ticks), and doors stay open (ticks). */
    static final int GOLEM_FREEZE_TICKS = 60, DOOR_HOLD_TICKS = 100;
    /** The override's colour (a sharp electric cyan). */
    static final int OVERRIDE_COLOR = 0x00FFFF;

    private static int cooldown;

    private RigOverride() {
    }

    static void init() {
        ServerPlayNetworking.registerGlobalReceiver(OverridePacket.TYPE, (payload, context) -> use(context.player()));
    }

    /** Client → server: fire the tech override. */
    public record OverridePacket() implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {
        public static final net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<OverridePacket> TYPE = new net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<>(JaymosPowers.id("rig_override"));
        public static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, OverridePacket> CODEC = net.minecraft.network.codec.StreamCodec.unit(new OverridePacket());

        @Override
        public net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<OverridePacket> type() {
            return TYPE;
        }
    }

    /** Use the tech override. */
    	public static void use(ServerPlayer player) {
        if (!Rig.fullSet(player)) {
            player.sendOverlayMessage(Component.translatable("message.jaymospowers.rig.incomplete"));
            return;
        }
        if (cooldown > 0) {
            return;
        }
        if (Rig.charge(player) < COST && !player.isCreative()) {
            RigMind.empty(player);
            return;
        }
        if (!Rig.spend(player, COST)) {
            RigMind.empty(player);
            return;
        }
        cooldown = COOLDOWN;
        ServerLevel level = player.level();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 end = eye.add(look.scale(RANGE));

        // the beam from the visor
        com.jaymospowers.core.fx.MagicFx.beam(level, eye, end, 0.1F, OVERRIDE_COLOR, 10);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.7F, 1.8F);
        com.jaymospowers.core.Shakes.send(player, 0.3F, end);

        // 1) Freeze iron golems and clockwork golems in range
        AABB box = new AABB(eye, end).inflate(2.0);
        List<LivingEntity> golems = level.getEntitiesOfClass(LivingEntity.class, box, e -> {
            EntityType<?> type = e.getType();
            String typeName = type.toString().toLowerCase(java.util.Locale.ROOT);
            return typeName.contains("iron_golem")
                || typeName.contains("snow_golem")
                || typeName.contains("golem");
        });
        for (LivingEntity golem : golems) {
            if (golem.distanceToSqr(player) <= RANGE * RANGE) {
                freezeGolem(golem, level);
            }
        }

        // 2) Open redstone doors (iron doors, trapdoors, fence gates) in a cone ahead
        openDoors(player, level, eye, look);

        // 3) Stutter androids/mechs (Frostborn designs) - entities with "android" or "mech" in their type name
        List<LivingEntity> tech = level.getEntitiesOfClass(LivingEntity.class, box, e -> {
            String name = e.getType().toString().toLowerCase(java.util.Locale.ROOT);
            return name.contains("android") || name.contains("mech");
        });
        for (LivingEntity entity : tech) {
            if (entity.distanceToSqr(player) <= RANGE * RANGE) {
                stutter(entity, level);
            }
        }

        RigMind.say(player, "override", 1.4F);
    }

    /** Freeze a golem: give it a long slowness and disable its AI briefly. */
    private static void freezeGolem(LivingEntity golem, ServerLevel level) {
        golem.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOWNESS, GOLEM_FREEZE_TICKS, 255, false, false, true));
        // a visual burst
        com.jaymospowers.core.fx.MagicFx.blast(level, golem.position().add(0.0, golem.getBbHeight() * 0.5, 0.0), 1.2F, OVERRIDE_COLOR);
        level.playSound(null, golem.getX(), golem.getY(), golem.getZ(), SoundEvents.IRON_GOLEM_HURT, SoundSource.HOSTILE, 0.6F, 0.8F);
    }

    /** Open doors and trapdoors in a cone ahead. */
    private static void openDoors(ServerPlayer player, ServerLevel level, Vec3 eye, Vec3 look) {
        int checked = 0;
        for (BlockPos pos : BlockPos.betweenClosed(
                BlockPos.containing(eye).offset(-4, -2, -4),
                BlockPos.containing(eye.add(look.scale(RANGE))).offset(4, 2, 4))) {
            if (checked++ > 200) break;
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() == Blocks.IRON_DOOR && !state.getValue(DoorBlock.OPEN)) {
                if (look.dot(Vec3.atCenterOf(pos).subtract(eye).normalize()) > 0.7) {
                    level.setBlockAndUpdate(pos, state.setValue(DoorBlock.OPEN, true));
                    level.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 0.5F, 1.2F);
                    com.jaymospowers.core.fx.MagicFx.blast(level, Vec3.atCenterOf(pos), 0.6F, OVERRIDE_COLOR);
                    // close again after a while
                    scheduleDoorClose(level, pos, DOOR_HOLD_TICKS);
                }
            } else if (state.getBlock() == Blocks.IRON_TRAPDOOR && !state.getValue(net.minecraft.world.level.block.TrapDoorBlock.OPEN)) {
                if (look.dot(Vec3.atCenterOf(pos).subtract(eye).normalize()) > 0.7) {
                    level.setBlockAndUpdate(pos, state.setValue(net.minecraft.world.level.block.TrapDoorBlock.OPEN, true));
                    level.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.5F, 1.2F);
                    com.jaymospowers.core.fx.MagicFx.blast(level, Vec3.atCenterOf(pos), 0.6F, OVERRIDE_COLOR);
                    scheduleDoorClose(level, pos, DOOR_HOLD_TICKS);
                }
            }
        }
    }

    /** Schedule a door to close after a delay. */
    private static final Map<BlockPos, Long> DOOR_CLOSE_AT = new java.util.HashMap<>();

    private static void scheduleDoorClose(ServerLevel level, BlockPos pos, int delayTicks) {
        DOOR_CLOSE_AT.put(pos.immutable(), level.getGameTime() + delayTicks);
    }

    /** Check and close doors whose time has come. */
    static void tickDoors(ServerLevel level) {
        long now = level.getGameTime();
        DOOR_CLOSE_AT.entrySet().removeIf(entry -> {
            if (now >= entry.getValue()) {
                BlockPos pos = entry.getKey();
                BlockState state = level.getBlockState(pos);
                if (state.getBlock() == Blocks.IRON_DOOR && state.getValue(DoorBlock.OPEN)) {
                    level.setBlockAndUpdate(pos, state.setValue(DoorBlock.OPEN, false));
                } else if (state.getBlock() == Blocks.IRON_TRAPDOOR && state.getValue(net.minecraft.world.level.block.TrapDoorBlock.OPEN)) {
                    level.setBlockAndUpdate(pos, state.setValue(net.minecraft.world.level.block.TrapDoorBlock.OPEN, false));
                }
                return true;
            }
            return false;
        });
    }

    /** Stutter an android/mech: brief paralysis and visual glitch. */
    private static void stutter(LivingEntity entity, ServerLevel level) {
        entity.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOWNESS, 40, 10, false, false, true));
        entity.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MINING_FATIGUE, 40, 5, false, false, true));
        com.jaymospowers.core.fx.MagicFx.blast(level, entity.position().add(0.0, entity.getBbHeight() * 0.5, 0.0), 0.8F, OVERRIDE_COLOR);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(), 12, 0.4, 0.4, 0.4, 0.02);
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 0.4F, 1.5F);
    }

    /** Every tick: count down cooldown. */
    static void tick() {
        if (cooldown > 0) {
            cooldown--;
        }
    }
}