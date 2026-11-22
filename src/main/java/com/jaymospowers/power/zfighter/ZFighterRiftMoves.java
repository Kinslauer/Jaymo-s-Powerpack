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
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Rift Fist and Rift Step: Divine-born techniques.
 * <ul>
 * <li>{@link #RIFT_FIST} (G): punch into a small rift to hit from behind.</li>
 * <li>{@link #RIFT_STEP} (sneak+G): step through a rift over a short gap.</li>
 * </ul>
 */
public final class ZFighterRiftMoves {
    // ---- Rift Fist (G) ----
    static final float FIST_KI = 15.0F;
    static final int FIST_COOLDOWN = 100;
    static final double FIST_RANGE = 12.0;
    static final float FIST_DAMAGE = 8.0F;
    static final double FIST_REACH = 3.0;

    // ---- Rift Step (sneak+G) ----
    static final float STEP_KI = 20.0F;
    static final int STEP_COOLDOWN = 160;
    static final double STEP_RANGE = 16.0;
    static final int STEP_DURATION = 20;

    // ---- Shared ----
    static final int COLOR = 0xE040FF;

    /** A rift portal pair (entry and exit). */
    private static class Rift {
        final ServerLevel level;
        final UUID owner;
        final Vec3 entry;
        final Vec3 exit;
        final int duration;
        int age;
        Rift(ServerLevel level, UUID owner, Vec3 entry, Vec3 exit, int duration) {
            this.level = level;
            this.owner = owner;
            this.entry = entry;
            this.exit = exit;
            this.duration = duration;
            this.age = 0;
        }
    }

    /** Active rifts. */
    private static final Map<UUID, Rift> RIFTS = new HashMap<>();

    /** StreamCodec for Vec3 using the built-in Vec3.CODEC. */
    private static final StreamCodec<io.netty.buffer.ByteBuf, Vec3> VEC3_STREAM_CODEC = StreamCodec.of(
            (buf, vec) -> {
                ByteBufCodecs.DOUBLE.encode(buf, vec.x());
                ByteBufCodecs.DOUBLE.encode(buf, vec.y());
                ByteBufCodecs.DOUBLE.encode(buf, vec.z());
            },
            buf -> new Vec3(ByteBufCodecs.DOUBLE.decode(buf), ByteBufCodecs.DOUBLE.decode(buf), ByteBufCodecs.DOUBLE.decode(buf))
    );

    /** Attachment on the owner for Rift Step: where their rift exit is. */
    public static final AttachmentType<Vec3> RIFT_EXIT = AttachmentRegistry.create(JaymosPowers.id("zfighter_rift_exit"),
            builder -> builder.syncWith(VEC3_STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));

    private ZFighterRiftMoves() {
    }

    static void init() {
        ServerTickEvents.END_SERVER_TICK.register(ZFighterRiftMoves::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> RIFTS.clear());
    }

    static void clear() {
        RIFTS.clear();
    }

    // =========================== Rift Fist (G) ===========================

    /** G (quick): punch into a rift to hit a foe from behind. Returns cooldown, or 0. */
    	static int riftFist(ServerPlayer player, ZFighterPower.Live live) {
    		// Divine-born only
    		if (!ZFighterLineage.divine(player)) {
    			player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_rift.divine_only").withStyle(net.minecraft.ChatFormatting.GRAY));
    			return 0;
    		}
    		if (!ZFighterPower.spend(player, live, FIST_KI)) {
            return 0;
        }
        ServerLevel level = player.level();
        LivingEntity target = ZFighterArts.find(player, FIST_RANGE);
        if (target == null) {
            ZFighterArts.noFoe(player);
            return 0;
        }
        Vec3 targetPos = target.position();
        Vec3 look = LockOn.aim(player);
        Vec3 behind = targetPos.subtract(look.scale(FIST_REACH)).add(0.0, target.getBbHeight() * 0.5, 0.0);
        if (!level.noCollision(target, target.getBoundingBox().move(behind.subtract(targetPos)))) {
            player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_rift_fist.blocked").withStyle(net.minecraft.ChatFormatting.GRAY));
            return 0;
        }
        float kiPower = ZFighterPower.kiPower(player, live);
        float damage = FIST_DAMAGE * kiPower;
        target.setInvulnerableTime(0);
        target.hurtServer(level, ZFighterKi.source(player), damage);
        ZFighterKi.push(target, look, 0.8, 0.2);
        level.sendParticles(MagicFx.wisp(COLOR, 1.5F), true, false, behind.x, behind.y, behind.z, 16, 0.3, 0.3, 0.3, 0.1);
        level.sendParticles(ParticleTypes.PORTAL, true, false, behind.x, behind.y, behind.z, 8, 0.2, 0.2, 0.2, 0.05);
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8F, 1.4F);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.7F, 1.6F);
        ZFighterHands.thrust(player, ZFighterHands.hand(player, true), ZFighterHands.JAB);
        if (!player.isCreative()) {
            player.causeFoodExhaustion(0.4F);
        }
        return FIST_COOLDOWN;
    }

    // =========================== Rift Step (sneak+G) ===========================

    /** Sneak+G: open a rift and step through it. Returns cooldown, or 0. */
    	static int riftStep(ServerPlayer player, ZFighterPower.Live live) {
    		// Divine-born only
    		if (!ZFighterLineage.divine(player)) {
    			player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_rift.divine_only").withStyle(net.minecraft.ChatFormatting.GRAY));
    			return 0;
    		}
    		if (!ZFighterPower.spend(player, live, STEP_KI)) {
            return 0;
        }
        ServerLevel level = player.level();
        Vec3 look = player.getViewVector(1.0F);
        Vec3 from = player.position().add(0.0, player.getBbHeight() * 0.5, 0.0);
        Vec3 to = from.add(look.scale(STEP_RANGE));
        BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 dest = hit.getType() == HitResult.Type.MISS ? to : hit.getLocation().subtract(look.scale(0.5));
        if (!level.noCollision(player, player.getBoundingBox().move(dest.subtract(player.position())))) {
            player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_rift_step.blocked").withStyle(net.minecraft.ChatFormatting.GRAY));
            return 0;
        }
        Rift rift = new Rift(level, player.getUUID(), from, dest, STEP_DURATION);
        RIFTS.put(player.getUUID(), rift);
        player.setAttached(RIFT_EXIT, dest);
        player.teleportTo(level, dest.x, dest.y, dest.z, java.util.Set.of(), player.getYRot(), player.getXRot(), false);
        player.resetFallDistance();
        level.sendParticles(MagicFx.wisp(COLOR, 1.5F), true, false, from.x, from.y, from.z, 20, 0.4, 0.4, 0.4, 0.1);
        level.sendParticles(ParticleTypes.PORTAL, true, false, from.x, from.y, from.z, 12, 0.3, 0.3, 0.3, 0.05);
        level.sendParticles(MagicFx.wisp(COLOR, 1.5F), true, false, dest.x, dest.y, dest.z, 20, 0.4, 0.4, 0.4, 0.1);
        level.sendParticles(ParticleTypes.PORTAL, true, false, dest.x, dest.y, dest.z, 12, 0.3, 0.3, 0.3, 0.05);
        level.playSound(null, from.x, from.y, from.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.2F);
        level.playSound(null, dest.x, dest.y, dest.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.2F);
        ZFighterHands.thrust(player, ZFighterHands.BOTH, ZFighterHands.PUSH);
        if (!player.isCreative()) {
            player.causeFoodExhaustion(0.6F);
        }
        return STEP_COOLDOWN;
    }

    /** Server tick: draw rift particles and expire them. */
    private static void tick(MinecraftServer server) {
        if (RIFTS.isEmpty()) {
            return;
        }
        for (java.util.Iterator<Map.Entry<UUID, Rift>> it = RIFTS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Rift> entry = it.next();
            Rift rift = entry.getValue();
            rift.age++;
            if (rift.age >= rift.duration) {
                it.remove();
                ServerPlayer owner = server.getPlayerList().getPlayer(entry.getKey());
                if (owner != null) {
                    owner.removeAttached(RIFT_EXIT);
                }
                continue;
            }
            if (rift.age % 2 == 0) {
                rift.level.sendParticles(MagicFx.wisp(COLOR, 1.0F), true, false, rift.entry.x, rift.entry.y, rift.entry.z, 4, 0.2, 0.2, 0.2, 0.02);
                rift.level.sendParticles(MagicFx.wisp(COLOR, 1.0F), true, false, rift.exit.x, rift.exit.y, rift.exit.z, 4, 0.2, 0.2, 0.2, 0.02);
                rift.level.sendParticles(ParticleTypes.PORTAL, true, false, rift.entry.x, rift.entry.y, rift.entry.z, 2, 0.1, 0.1, 0.1, 0.01);
                rift.level.sendParticles(ParticleTypes.PORTAL, true, false, rift.exit.x, rift.exit.y, rift.exit.z, 2, 0.1, 0.1, 0.1, 0.01);
            }
        }
    }
}