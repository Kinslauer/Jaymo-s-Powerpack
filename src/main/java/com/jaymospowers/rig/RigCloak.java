package com.jaymospowers.rig;

import com.jaymospowers.JaymosPowers;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import com.jaymospowers.core.fx.MagicFx;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * (Rig patch 6) Active camouflage: a timed cloak that shimmers, mobs lose you, and the first blow from it is an ambush.
 * Builds on {@link RigVisibilityMixin}: while cloaked the detect share is scaled further. The first hit out of cloak
 * does extra damage and ends the cloak.
 */
public final class RigCloak {
    /** Charge to start, what it drains every {@link #DRAIN_EVERY} ticks, and how long it lasts at most (ticks). */
    static final int COST = 120, DRAIN_EVERY = 10, MAX_TICKS = 200;
    /** While cloaked: the detect share multiplier (on top of the frame's and sneak's). */
    static final float CLOAK_DETECT_SHARE = 0.15F;
    /** The ambush multiplier on the first hit that breaks cloak. */
    static final float AMBUSH_MULTIPLIER = 2.5F;
    /** The shimmer interval (ticks) and the ring radius. */
    static final int SHIMMER_EVERY = 20;
    static final float SHIMMER_RING = 1.8F;
    /** The cloak's colour (a ghostly teal shimmer). */
    static final int CLOAK_COLOR = 0x5FE6FF;

    /** Per-player cloak state. */
    private static final Map<UUID, State> STATES = new HashMap<>();

    private static class State {
        boolean cloaked;
        int ticks;
        int shimmerTicks;
        /** The ambush is ready: the next hit that lands while cloaked gets the multiplier. */
        boolean ambushReady;
    }

    private RigCloak() {
    }

    static void init() {
        ServerPlayNetworking.registerGlobalReceiver(CloakPacket.TYPE, (payload, context) -> {
            if (payload.cloak()) {
                start(context.player());
            } else {
                stop(context.player());
            }
        });
    }

    /** Client → server: toggle the cloak. */
    public record CloakPacket(boolean cloak) implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {
        public static final net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<CloakPacket> TYPE = new net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<>(JaymosPowers.id("rig_cloak"));
        public static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, CloakPacket> CODEC = net.minecraft.network.codec.StreamCodec.of(
                (buf, p) -> buf.writeBoolean(p.cloak()),
                buf -> new CloakPacket(buf.readBoolean())
        );

        @Override
        public net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<CloakPacket> type() {
            return TYPE;
        }
    }

    /** Start the cloak. */
    private static void start(ServerPlayer player) {
        if (!Rig.fullSet(player)) {
            player.sendOverlayMessage(Component.translatable("message.jaymospowers.rig.incomplete"));
            return;
        }
        if (Rig.charge(player) < COST && !player.isCreative()) {
            RigMind.empty(player);
            return;
        }
        State state = STATES.computeIfAbsent(player.getUUID(), u -> new State());
        if (state.cloaked) {
            return;
        }
        if (!Rig.spend(player, COST)) {
            RigMind.empty(player);
            return;
        }
        state.cloaked = true;
        state.ticks = 0;
        state.shimmerTicks = 0;
        state.ambushReady = true;
        ServerLevel level = player.level();
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.5F, 1.8F);
        // initial shimmer
        MagicFx.ring(level, player.position().add(0.0, 0.1, 0.0), SHIMMER_RING, CLOAK_COLOR);
        RigMind.say(player, "cloak_on", 1.2F);
    }

    /** Stop the cloak. */
    private static void stop(ServerPlayer player) {
        State state = STATES.get(player.getUUID());
        if (state == null || !state.cloaked) {
            return;
        }
        state.cloaked = false;
        state.ambushReady = false;
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.4F, 1.6F);
        RigMind.say(player, "cloak_off", 1.0F);
    }

    /** Called when the wearer hits something while cloaked: the ambush. */
    static boolean tryAmbush(ServerPlayer player, LivingEntity target, DamageSource source, float baseDamage) {
        State state = STATES.get(player.getUUID());
        if (state == null || !state.cloaked || !state.ambushReady) {
            return false;
        }
        state.ambushReady = false;
        stop(player);
        ServerLevel level = player.level();
        // the hit lands with the ambush multiplier
        target.hurtServer(level, source, baseDamage * AMBUSH_MULTIPLIER);
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 0.8F, 1.4F);
        MagicFx.blast(level, target.position().add(0.0, target.getBbHeight() * 0.5, 0.0), 0.7F, CLOAK_COLOR);
        RigMind.say(player, "ambush", 1.6F);
        return true;
    }

    /** Every tick on the server. */
    static void tick(ServerPlayer player) {
        State state = STATES.get(player.getUUID());
        if (state == null || !state.cloaked) {
            return;
        }
        state.ticks++;
        if (state.ticks > MAX_TICKS) {
            stop(player);
            return;
        }
        // drain charge
        if (state.ticks % DRAIN_EVERY == 0) {
            if (!Rig.spend(player, 1)) {
                stop(player);
                RigMind.empty(player);
                return;
            }
        }
        // shimmer
        if (++state.shimmerTicks >= SHIMMER_EVERY) {
            state.shimmerTicks = 0;
            ServerLevel level = player.level();
            MagicFx.ring(level, player.position().add(0.0, 0.1, 0.0), SHIMMER_RING, CLOAK_COLOR);
            level.sendParticles(MagicFx.miasma(CLOAK_COLOR, 0.5F), player.getX(), player.getY() + 1.0, player.getZ(), 8, 0.3, 0.3, 0.3, 0.01);
        }
    }

    /** Whether the player is currently cloaked. */
    public static boolean cloaked(Player player) {
        State state = STATES.get(player.getUUID());
        return state != null && state.cloaked;
    }

    /** The detect share multiplier while cloaked (for the mixin). */
    public static float detectShare(Player player) {
        return cloaked(player) ? CLOAK_DETECT_SHARE : 1.0F;
    }

    /** Whether the ambush is ready (first hit out of cloak). */
    public static boolean ambushReady(Player player) {
        State state = STATES.get(player.getUUID());
        return state != null && state.cloaked && state.ambushReady;
    }

    /** Toggle the cloak. */
        public static void toggle(ServerPlayer player) {
            State state = STATES.get(player.getUUID());
            if (state != null && state.cloaked) {
                stop(player);
            } else {
                start(player);
            }
        }
    }