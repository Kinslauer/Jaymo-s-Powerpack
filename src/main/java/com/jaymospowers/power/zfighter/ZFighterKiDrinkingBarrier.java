package com.jaymospowers.power.zfighter;

import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.LockOn;
import com.jaymospowers.core.Soundscape;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ZFighterKiDrinkingBarrier {
    static final float KI_COST = 10.0F;
    static final int COOLDOWN = 180;
    static final int BASE_DURATION = 200;
    static final float KI_PER_BLAST = 15.0F;
    static final float MAX_ABSORBED = 100.0F;
    static final double RADIUS = 1.8;
    static final int COLOR = 0x40E0F0;

    private static class Barrier {
        final ServerLevel level;
        final UUID owner;
        int ticksLeft;
        float absorbed;
        Barrier(ServerLevel level, UUID owner, int ticksLeft) {
            this.level = level;
            this.owner = owner;
            this.ticksLeft = ticksLeft;
            this.absorbed = 0.0F;
        }
    }

    private static final Map<UUID, Barrier> BARRIERS = new HashMap<>();

    public static final AttachmentType<Float> ABSORBED_KI = AttachmentRegistry.create(JaymosPowers.id("zfighter_absorbed_ki"),
            builder -> builder.syncWith(ByteBufCodecs.FLOAT, AttachmentSyncPredicate.targetOnly()));

    private ZFighterKiDrinkingBarrier() {
    }

    static void init() {
        ServerTickEvents.END_SERVER_TICK.register(ZFighterKiDrinkingBarrier::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> BARRIERS.clear());
    }

    static void clear() {
        BARRIERS.clear();
    }

    static int start(ServerPlayer player, ZFighterPower.Live live) {
        if (!ZFighterAndroid.android(player)) {
            player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_ki_barrier.android_only").withStyle(net.minecraft.ChatFormatting.GRAY));
            return 0;
        }
        if (!ZFighterPower.spend(player, live, KI_COST)) {
            return 0;
        }
        ServerLevel level = player.level();
        BARRIERS.put(player.getUUID(), new Barrier(level, player.getUUID(), BASE_DURATION));
        player.setAttached(ABSORBED_KI, 0.0F);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.8F, 1.3F);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.7F, 1.5F);
        if (!player.isCreative()) {
            player.causeFoodExhaustion(0.2F);
        }
        return COOLDOWN;
    }

    public static boolean onKiHit(ServerPlayer player, ZFighterKi.Bolt bolt) {
        Barrier barrier = BARRIERS.get(player.getUUID());
        if (barrier == null || barrier.ticksLeft <= 0) {
            return false;
        }
        if (bolt.kind != ZFighterKi.BOLT && bolt.kind != ZFighterKi.DISC && bolt.kind != ZFighterKi.HOMING) {
            return false;
        }
        barrier.absorbed = Math.min(MAX_ABSORBED, barrier.absorbed + KI_PER_BLAST);
        player.setAttached(ABSORBED_KI, barrier.absorbed);
        ServerLevel level = barrier.level;
        level.sendParticles(MagicFx.wisp(COLOR, 2.0F), true, false, bolt.pos.x, bolt.pos.y, bolt.pos.z, 8, 0.2, 0.2, 0.2, 0.05);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, true, false, bolt.pos.x, bolt.pos.y, bolt.pos.z, 4, 0.1, 0.1, 0.1, 0.02);
        level.playSound(null, bolt.pos.x, bolt.pos.y, bolt.pos.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.6F, 2.0F);
        ZFighterPower.Live live = ZFighterPower.live(player);
        live.ki = Math.min(ZFighterPower.maxKi(player), live.ki + KI_PER_BLAST);
        player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_ki_barrier.absorbed", (int)KI_PER_BLAST).withStyle(net.minecraft.ChatFormatting.AQUA));
        return true;
    }

    private static void tick(MinecraftServer server) {
        if (BARRIERS.isEmpty()) {
            return;
        }
        for (java.util.Iterator<Map.Entry<UUID, Barrier>> it = BARRIERS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Barrier> entry = it.next();
            Barrier barrier = entry.getValue();
            ServerPlayer owner = server.getPlayerList().getPlayer(entry.getKey());
            if (owner == null || !owner.isAlive() || owner.level() != barrier.level) {
                it.remove();
                continue;
            }
            barrier.ticksLeft--;
            if (barrier.ticksLeft <= 0) {
                it.remove();
                owner.removeAttached(ABSORBED_KI);
                owner.level().playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.6F, 1.3F);
                continue;
            }
            if (server.getTickCount() % 3 == 0) {
                ServerLevel level = barrier.level;
                RandomSource random = level.getRandom();
                for (int i = 0; i < 4; i++) {
                    double a = random.nextDouble() * Math.PI * 2.0;
                    double h = random.nextDouble() * owner.getBbHeight();
                    double r = RADIUS * (0.9 + random.nextDouble() * 0.2);
                    level.sendParticles(MagicFx.wisp(COLOR, 1.2F), true, false, owner.getX() + Math.cos(a) * r, owner.getY() + h, owner.getZ() + Math.sin(a) * r, 0, 0.0, 0.0, 0.0, 0.0);
                }
            }
            if (barrier.ticksLeft % 40 == 0) {
                barrier.level.playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 0.4F, 1.2F + barrier.absorbed * 0.01F);
            }
        }
    }

    public static boolean hasBarrier(ServerPlayer player) {
        Barrier b = BARRIERS.get(player.getUUID());
        return b != null && b.ticksLeft > 0;
    }
}