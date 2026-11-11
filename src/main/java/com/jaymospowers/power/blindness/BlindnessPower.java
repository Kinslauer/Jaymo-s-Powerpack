package com.jaymospowers.power.blindness;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.api.Power;
import com.jaymospowers.api.PowerKey;
import com.jaymospowers.core.Mastery;
import com.jaymospowers.core.wounds.BodyPart;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * Blindness: you cannot see, but sound paints the world.
 * <ul>
 * <li>R, Echolocation (starter): sonar rings ripple from every sound, painting the world in sound-waves for a moment.
 * <li>G, One-Eye Blind (hold): close one eye; that side of the screen blurs ("Borrado") or darkens ("Escuro"), the remaining view stretches, and your aim drifts toward the blind side.
 * <li>V, Blindside Swap: toggle which eye is blind (left/right).
 * <li>B, Deafen (hold): silence your own sounds so you don't give away your position to other echolocators.
 * <li>Z, Variant Toggle: switch between Borrado (blur) and Escuro (darken) for the one-eye blind effect.
 * <li>C (ascended), Void Sight: total blindness becomes true sight—see entities through walls, hear heartbeats, but the world stays dark.
 * <li>C (corrupted), Blackout: your blindness spreads—nearby players share your blindness, and head/eye damage blinds everyone around you.
 * <li>Both, Abyssal Echo: C is Abyssal Echo; a pulse that blinds everyone in range and leaves them navigating by sound alone.
 * </ul>
 * The calling: wear a blindfold for a vigil, or carve out your own eyes ({@link BlindnessDevotion}). Those devout holders see
 * living things through the dark, and their Void Sight lifts the view high over them (Sky Sight).
 */
public final class BlindnessPower implements Power {
    public static final BlindnessPower INSTANCE = new BlindnessPower();
    public static final Identifier ID = JaymosPowers.id("blindness");
    public static final int COLOR = 0x1A1A2E;

    // ---- tuning (ticks) ----
    public static final int ECHO_COOLDOWN = 40;
    public static final int ECHO_COOLDOWN_MASTERED = 25;
    public static final int ON_EYE_COOLDOWN = 20;
    public static final int DEAFEN_COOLDOWN = 60;

    /** The owner's HUD state (cooldowns, variant, which eye is blind). Not saved: a relog clears cooldowns. */
    public static final AttachmentType<BlindnessState> STATE = AttachmentRegistry.create(JaymosPowers.id("blindness_state"),
            builder -> builder.syncWith(BlindnessState.STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));

    private BlindnessPower() {
    }

    /** Called once from {@link JaymosPowers#onInitialize()}. */
    public static void init() {
        com.jaymospowers.core.Powers.register(INSTANCE);
        BlindnessEcho.register();
        BlindnessEcholocation.init();
        BlindnessOneEye.init();
        BlindnessDeafen.init();
        BlindnessVoidSight.init();
        BlindnessBlackout.init();
        BlindnessAbyssalEcho.init();
        BlindnessDevotion.init();
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forget(handler.player));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            BlindnessEcholocation.clear();
            BlindnessOneEye.clear();
            BlindnessDeafen.clear();
            BlindnessVoidSight.clear();
            BlindnessBlackout.clear();
            BlindnessAbyssalEcho.clear();
        });
    }

    private static void forget(ServerPlayer player) {
        BlindnessEcholocation.stop(player);
        BlindnessOneEye.stop(player);
        BlindnessDeafen.stop(player);
        BlindnessVoidSight.forget(player);
        BlindnessBlackout.forget(player);
        BlindnessAbyssalEcho.forget(player);
    }

    @Override
    public Identifier id() {
        return ID;
    }

    @Override
    public int color() {
        return COLOR;
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.SCULK_SENSOR);
    }

    /** R (Echolocation) first, then G (One-Eye Blind), V (Blindside Swap), B (Deafen). */
    @Override
    public PowerKey[] unlockOrder() {
        return new PowerKey[]{PowerKey.ABILITY_1, PowerKey.ABILITY_2, PowerKey.ABILITY_3, PowerKey.ABILITY_4};
    }

    /** A fresh awakening echolocates on R. */
    @Override
    public @Nullable PowerKey starterKey() {
        return PowerKey.ABILITY_1;
    }

    /** Z is Variant Toggle; X is unused. */
    @Override
    public boolean slotUnlocked(Player player, String slot) {
        return "mode".equals(slot);
    }

    @Override
    public void onMode(ServerPlayer player, boolean down) {
        if (down) {
            BlindnessState state = state(player);
            player.setAttached(STATE, state.withVariant(state.variant() == BlindnessVariant.BORRADO ? BlindnessVariant.ESCURO : BlindnessVariant.BORRADO));
        }
    }

    @Override
    public void onGained(ServerPlayer player) {
        player.setAttached(STATE, BlindnessState.NONE);
    }

    @Override
    public void onDowned(ServerPlayer player) {
        BlindnessEcholocation.stop(player);
        BlindnessOneEye.stop(player);
        BlindnessDeafen.stop(player);
    }

    @Override
    public void onLost(ServerPlayer player) {
        forget(player);
        player.removeAttached(STATE);
    }

    @Override
    public void tick(ServerPlayer player) {
        BlindnessEcholocation.tick(player);
        BlindnessOneEye.tick(player);
        BlindnessDeafen.tick(player);
        BlindnessVoidSight.tick(player);
        BlindnessBlackout.tick(player);
        BlindnessAbyssalEcho.tick(player);
    }

    /** C: Void Sight (ascended), Blackout (corrupted), Abyssal Echo (both). */
    @Override
    public boolean ultimateWhenCorrupted() {
        return true;
    }

    @Override
    public void onUltimate(ServerPlayer player, boolean down) {
        if (com.jaymospowers.core.Fate.isCorrupted(player) && !com.jaymospowers.core.Fate.isAscended(player)) {
            BlindnessBlackout.key(player, down);
        } else if (com.jaymospowers.core.Fate.isAscended(player) && com.jaymospowers.core.Fate.isCorrupted(player)) {
            BlindnessAbyssalEcho.key(player, down);
        } else {
            BlindnessVoidSight.key(player, down);
        }
    }

    @Override
    public void onKey(ServerPlayer player, PowerKey key, boolean down) {
        if (down && key != PowerKey.ABILITY_1 && BlindnessVoidSight.active(player)) {
            return; // Void Sight: only echolocation works
        }
        switch (key) {
            case ABILITY_1 -> {
                if (down && ready(player, BlindnessState.ECHO)) {
                    BlindnessEcholocation.pulse(player);
                    cooldown(player, BlindnessState.ECHO, Mastery.unlocked(player) >= Mastery.ALL ? ECHO_COOLDOWN_MASTERED : ECHO_COOLDOWN);
                }
            }
            case ABILITY_2 -> {
                if (down && ready(player, BlindnessState.ONE_EYE)) {
                    BlindnessOneEye.start(player);
                } else if (!down) {
                    BlindnessOneEye.stop(player);
                }
            }
            case ABILITY_3 -> {
                if (down && ready(player, BlindnessState.BLINDSIDE)) {
                    BlindnessState state = state(player);
                    player.setAttached(STATE, state.withBlindSide(state.blindSide() == BlindSide.LEFT ? BlindSide.RIGHT : BlindSide.LEFT));
                    cooldown(player, BlindnessState.BLINDSIDE, ON_EYE_COOLDOWN);
                }
            }
            case ABILITY_4 -> {
                if (down && ready(player, BlindnessState.DEAFEN)) {
                    BlindnessDeafen.start(player);
                } else if (!down) {
                    BlindnessDeafen.stop(player);
                }
            }
        }
    }

    // ---------------------------------------------------------------- state

    public static BlindnessState state(Player player) {
        BlindnessState state = player.getAttached(STATE);
        return state == null ? BlindnessState.NONE : state;
    }

    static void set(ServerPlayer player, BlindnessState state) {
        player.setAttached(STATE, state);
    }

    static boolean ready(ServerPlayer player, int slot) {
        return state(player).left(slot, player.level().getGameTime()) <= 0;
    }

    static void cooldown(ServerPlayer player, int slot, int ticks) {
        set(player, state(player).withCooldown(slot, player.level().getGameTime(), ticks));
    }

    /** Global trigger: when a player takes head damage, nearby players with Blindness may go blind. */
    public static void onHeadDamage(ServerPlayer victim, float damage) {
        if (damage < 4.0F) {
            return; // only significant head hits
        }
        BlindnessBlackout.triggerFromHeadDamage(victim);
    }
}