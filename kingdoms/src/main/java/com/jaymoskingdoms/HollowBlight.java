package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.643) The Hollow's blight: turning slowly, before dying. Andy (2026-11-07): "players that get infected and are turning
 * into hollow, shouldnt reincarnate the way everyone else does, if they die before fully turning, then they dont get a prompt
 * on death, just a longer black screen, an appropriate vignette, and a wakeup as a full turned hollow whichever they may be."
 * <p>
 * A blow from one of {@link Hollows}' turners (a zombie, a witch, a skeleton's arrow, a phantom, a necromancer, any other
 * undead; a vampire's bite is its own, {@link VampirePlayers#bitten}) that a player lives through may leave the blight in
 * them, of the turner's Hollow. It spreads over {@link #TURN_TICKS} (a Minecraft day): hunger first, then weakness and
 * sickness, each stage told once. Lived through, they turn where they stand. Died before that, Jaymo's Powers' death by
 * cause asks here first ({@code Mortality.MID_TURNS}): no come-to, no final death, a long black with the Hollow's colour at
 * its edges, and they rise the Hollow they were becoming. A golden apple burns it out while it's still spreading.
 * One who is Hollow already, an android, a vampire (or one bitten) or a werewolf can't take it.
 */
public final class HollowBlight {
    /** A turner's blow lands the blight at its {@link Hollows.Turn#chance} times this (a zombie: one blow in sixteen). */
    static final float BLIGHT_SHARE = 0.25F;
    /** How long the blight takes to turn one (ticks of game time: a Minecraft day). */
    static final long TURN_TICKS = 24000L;
    /** The stages (shares of {@link #TURN_TICKS}) at which it shows: hunger, then weakness and sickness. */
    static final float STAGE_HUNGRY = 0.33F, STAGE_WEAK = 0.66F;
    /** Out of every this many seconds, a stage's symptom comes on (for this many ticks). */
    static final int SYMPTOM_EVERY = 45, SYMPTOM_TICKS = 20 * 8;

    /** How long the black screen lasts while passing out (ticks; 14 s). */
    static final int TURN_DARK = 20 * 14;

    /** The vignette each Hollow's turning casts over the black (RGB): the pallid one is {@link #PALLID_TINT}. */
    static final int ZOMBIE_TINT = 0x5C8A24, SKELETON_TINT = 0xB8B094, WRAITH_TINT = 0x5A6EA8, REAPER_TINT = 0x3C8C82, PALLID_TINT = 0x8A8A8A;

    /** A blight in someone: the Hollow it's making (a variant key, or "pallid"), its wake line, when it took, the last stage told,
     * whether they have passed out waiting to rise turned, and the game time they should wake at. Persistent: logging out while
     * passed out keeps the pending turn. */
    record Blight(String look, String line, long since, int told, boolean passedOut, long wakeAt) {
        static final Codec<Blight> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("look").forGetter(Blight::look),
                Codec.STRING.fieldOf("line").forGetter(Blight::line),
                Codec.LONG.fieldOf("since").forGetter(Blight::since),
                Codec.INT.optionalFieldOf("told", 0).forGetter(Blight::told),
                Codec.BOOL.optionalFieldOf("passedOut", false).forGetter(Blight::passedOut),
                Codec.LONG.optionalFieldOf("wakeAt", 0L).forGetter(Blight::wakeAt))
                .apply(i, Blight::new));

        HeritageVariants.@Nullable Variant variant() {
            return HeritageVariants.Variant.byKey(this.look);
        }

        Hollows.Turn turn() {
            return new Hollows.Turn(variant(), 1.0F, this.line);
        }
    }

    static final AttachmentType<Blight> BLIGHT = AttachmentRegistry.create(JaymosKingdoms.id("hollow_blight"),
            builder -> builder.persistent(Blight.CODEC));

    private HollowBlight() {
    }

    static void register() {
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
            if (entity instanceof ServerPlayer player && taken > 0.0F && !blocked) {
                struck(player, source);
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(HollowBlight::tick);
        PowersCompat.tellMidTurns(HollowBlight::midTurn);
    }

    /** Whether {@code player} carries the blight. */
    static boolean blighted(ServerPlayer player) {
        return player.hasAttached(BLIGHT);
    }

    private static boolean immune(ServerPlayer player) {
        return player.isCreative() || player.isSpectator() || Heritage.of(player) == Heritage.Kind.HOLLOW
                || PowersCompat.android(player) || Vampires.isVampire(player)
                || player.hasAttached(Vampires.BITTEN) || Lycans.cursed(player);
    }

    /** A blow on {@code player} they lived through: one of the turners' may leave the blight. */
    private static void struck(ServerPlayer player, DamageSource source) {
        Entity by = source.getEntity();
        if (by == null || by == player || !player.isAlive() || player.isDeadOrDying() || blighted(player)
                || Vampires.isVampire(by) || immune(player)) {
            return;
        }
        Hollows.Turn turn = Hollows.turnOf(by);
        if (turn == null || player.getRandom().nextFloat() >= turn.chance() * BLIGHT_SHARE) {
            return;
        }
        String look = turn.look() == null ? "pallid" : turn.look().key();
        player.setAttached(BLIGHT, new Blight(look, turn.line(), player.level().getGameTime(), 0, false, 0L));
        player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.hollow_blight.took").withStyle(ChatFormatting.DARK_GRAY,
                ChatFormatting.ITALIC));
        JaymosKingdoms.LOGGER.info("{} took the Hollow's blight ({}) from {}", player.getScoreboardName(), look, by.getType().getDescriptionId());
    }

    private static void tick(MinecraftServer server) {
        long tick = server.getTickCount();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if ((tick + player.getId()) % 20 != 0 || !player.isAlive()) {
                continue;
            }
            Blight blight = player.getAttached(BLIGHT);
            if (blight == null) {
                continue;
            }

            // Passed out waiting to rise turned: wake at wakeAt and rise.
            if (blight.passedOut()) {
                if (player.level().getGameTime() >= blight.wakeAt()) {
                    Hollows.turn(player, blight.turn());
                    player.removeAttached(BLIGHT);
                }
                continue;
            }

            if (immune(player) && !player.isCreative() && !player.isSpectator()) {
                // turned some other way meanwhile (or already Hollow): nothing left for it to make
                player.removeAttached(BLIGHT);
                continue;
            }
            if (PowersCompat.waking(player)) {
                continue; // (left for dead: Powers finishes it as they rise)
            }
            float done = (player.level().getGameTime() - blight.since()) / (float) TURN_TICKS;
            if (done >= 1.0F) {
                turnNow(player, blight);
                continue;
            }
            int stage = done >= STAGE_WEAK ? 2 : done >= STAGE_HUNGRY ? 1 : 0;
            if (stage > blight.told()) {
                player.setAttached(BLIGHT, new Blight(blight.look(), blight.line(), blight.since(), stage, false, 0L));
                player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.hollow_blight.stage." + stage)
                        .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
            }
            // While the blight spreads, zombies don't attack you: they stand down and speak instead.
            if (stage > 0) {
                standDownAndSpeak(player);
            }
        }
    }

    /** While the blight spreads, nearby zombies stand down and speak to you instead of attacking.
     * Runs every tick for the whole blight (stage > 0), not only during symptom ticks.
     * Hooks their target choice so they don't re-target you next tick.
     */
    private static void standDownAndSpeak(ServerPlayer player) {
        Level level = player.level();
        for (Zombie z : level.getEntitiesOfClass(Zombie.class,
                player.getBoundingBox().inflate(16.0),
                z -> z.isAlive())) {
            if (z.getTarget() == player) {
                z.setTarget(null);
            }
            if (player.getRandom().nextInt(8) == 0) {
                level.playSound(null, z.blockPosition(), SoundEvents.ZOMBIE_AMBIENT, SoundSource.HOSTILE, 0.6F, 1.0F);
            }
        }
    }

    /** The blight has run its course while they lived: they pass out first, then rise turned. */
    private static void turnNow(ServerPlayer player, Blight blight) {
        player.setAttached(BLIGHT, new Blight(blight.look(), blight.line(), blight.since(), blight.told(), true,
                player.level().getGameTime() + TURN_DARK));
        player.level().playSound(null, player.blockPosition(), SoundEvents.ZOMBIE_VILLAGER_CONVERTED, SoundSource.PLAYERS, 0.8F, 0.6F);
        // Kingdoms never compiles against Powers: mark the player downed via its scoreboard tag so Powers'
        // downed system puts them to sleep until wakeAt. Kingdoms' Downed.DOWNED_TAG is the same string Powers reads.
        player.addTag(Downed.DOWNED_TAG);
    }

    /** For Powers' death by cause: one dying with the blight in them rises the Hollow it was making ({@code Mortality.MID_TURNS}). */
    static Map.@Nullable Entry<Integer, Runnable> midTurn(ServerPlayer player) {
        Blight blight = player.getAttached(BLIGHT);
        if (blight == null || immune(player)) {
            return null;
        }
        return Map.entry(tint(blight.variant()), () -> {
            player.removeAttached(BLIGHT);
            Hollows.turn(player, blight.turn());
        });
    }

    /** The colour of {@code look}'s turning, on the black. */
    static int tint(HeritageVariants.@Nullable Variant look) {
        if (look == null) {
            return PALLID_TINT;
        }
        return switch (look) {
            case ZOMBIE -> ZOMBIE_TINT;
            case SKELETON -> SKELETON_TINT;
            case WRAITH -> WRAITH_TINT;
            case REAPER -> REAPER_TINT;
            default -> PALLID_TINT;
        };
    }

    /** A golden apple eaten while the blight spreads burns it out. */
    public static void ateGold(ServerPlayer player) {
        if (player.removeAttached(BLIGHT) != null) {
            player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.hollow_blight.cured").withStyle(ChatFormatting.GOLD));
            player.level().playSound(null, player.blockPosition(), SoundEvents.ZOMBIE_VILLAGER_CURE, SoundSource.PLAYERS, 0.6F, 1.2F);
        }
    }
}
