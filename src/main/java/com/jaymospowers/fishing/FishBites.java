package com.jaymospowers.fishing;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.fish.Cod;
import net.minecraft.world.entity.animal.fish.Pufferfish;
import net.minecraft.world.entity.animal.fish.Salmon;
import net.minecraft.world.entity.animal.fish.TropicalFish;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.21.61, Andy: "i need there to be real fish, as in if you see them in the water, you can cast and potentially get the
 * bite. but they have to ACTUALLY be there.") Every bite is a real fish. A baited hook draws a fish swimming within
 * {@link #DRAW_RANGE}: it turns and swims to the hook, nibbles it a few times (the bobber dips), then takes it, and the
 * fight is with that same fish. Which fish you get is whichever live there (vanilla's spawns put salmon in rivers and
 * cold seas, cod in the seas, tropical fish and pufferfish in warm ones, skipjack tuna at sea, and with Naturalist great
 * whites); a fish landed is gone, so a spot is fished out when its fish are. Tuna and sharks only go for a metal rod's
 * hook. Dawn, dusk and rain make fish keener ({@link FishWaters#bites}).
 */
public final class FishBites {
    /** A baited hook draws fish this far off (blocks). */
    static final double DRAW_RANGE = 10.0;
    /** Each check (4 a second), chance a fish in range notices the bait (before time and weather). */
    static final float NOTICE_CHANCE = 0.04F;
    /** A fish this close to the hook is at it: it nibbles, then takes it. */
    static final double AT_HOOK = 1.2;
    /** Nibbles before a fish takes the hook, at least and up to this many more. */
    static final int NIBBLES_LEAST = 1, NIBBLES_MORE = 3;
    /** Each check at the hook, chance of a nibble. */
    static final float NIBBLE_CHANCE = 0.35F;
    /** A fish that hasn't reached the hook in this many ticks loses interest. */
    static final int GIVE_UP_TICKS = 400;
    /** How fast a drawn fish swims to the hook (navigation speed, and the nudge when it can't path). */
    static final double SWIM_SPEED = 1.2, NUDGE = 0.04;
    /** How far the bobber dips on a nibble and on the take. */
    static final double NIBBLE_DIP = 0.12, TAKE_DIP = 0.3;

    /** A fish drawn to a hook. */
    private static final class Drawn {
        final UUID fish;
        final long since;
        int nibbles;
        final int takesAfter;

        Drawn(UUID fish, long since, int takesAfter) {
            this.fish = fish;
            this.since = since;
            this.takesAfter = takesAfter;
        }
    }

    /** Hook uuid → the fish coming to it. */
    private static final Map<UUID, Drawn> DRAWN = new HashMap<>();

    private FishBites() {
    }

    /** The kind ({@code FishingReal.FISH_*}) of fish {@code e} is, or -1 if it's no fish for a hook. */
    public static int kindOf(LivingEntity e) {
        if (e instanceof Skipjack) {
            return FishingReal.FISH_TUNA;
        }
        if (e instanceof Cod) {
            return FishingReal.FISH_COD;
        }
        if (e instanceof Salmon) {
            return FishingReal.FISH_SALMON;
        }
        if (e instanceof Pufferfish) {
            return FishingReal.FISH_PUFFERFISH;
        }
        if (e instanceof TropicalFish) {
            return FishingReal.FISH_TROPICAL;
        }
        return BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath().contains("shark") ? FishingReal.FISH_SHARK : -1;
    }

    /** Called every tick for a hook in water whose angler isn't fighting a fish. */
    public static void tick(ServerLevel level, FishingHook hook, ServerPlayer player) {
        if (level.getGameTime() % 4L != 0L) {
            return;
        }
        Drawn drawn = DRAWN.get(hook.getUUID());
        if (drawn == null) {
            net.minecraft.world.item.Item onHook = HookBait.bait(player);
            if (onHook == null) {
                return;
            }
            if (onHook == FishingGear.FISHING_MAGNET) {
                // (0.21.64) a magnet draws no fish; it snags scrap now and then
                FishingGear.magnet(level, hook, player);
                return;
            }
            LivingEntity fish = notice(level, hook, player);
            if (fish != null) {
                long now = level.getGameTime();
                DRAWN.values().removeIf(d -> now - d.since > GIVE_UP_TICKS);
                DRAWN.put(hook.getUUID(), new Drawn(fish.getUUID(), level.getGameTime(),
                        NIBBLES_LEAST + level.getRandom().nextInt(NIBBLES_MORE + 1)));
            }
            return;
        }
        LivingEntity fish = level.getEntity(drawn.fish) instanceof LivingEntity e ? e : null;
        if (fish == null || !fish.isAlive() || !fish.isInWater() || HookBait.bait(player) == null
                || level.getGameTime() - drawn.since > GIVE_UP_TICKS || fish.distanceTo(hook) > DRAW_RANGE * 1.5) {
            DRAWN.remove(hook.getUUID());
            return;
        }
        if (fish.distanceTo(hook) > AT_HOOK) {
            swimTo(fish, hook);
            return;
        }
        if (level.getRandom().nextFloat() >= NIBBLE_CHANCE) {
            return;
        }
        if (drawn.nibbles < drawn.takesAfter) {
            // a nibble: the bobber dips
            drawn.nibbles++;
            dip(level, hook, NIBBLE_DIP);
            level.playSound(null, hook.getX(), hook.getY(), hook.getZ(), SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.NEUTRAL, 0.15F, 1.6F);
            return;
        }
        // it takes the hook (and the bait with it)
        DRAWN.remove(hook.getUUID());
        dip(level, hook, TAKE_DIP);
        level.playSound(null, hook.getX(), hook.getY(), hook.getZ(), SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.NEUTRAL, 0.5F, 1.0F);
        level.sendParticles(ParticleTypes.BUBBLE, hook.getX(), hook.getY(), hook.getZ(), 8, 0.2, 0.1, 0.2, 0.1);
        taken(player);
        // (Track A) the strike window: use in time hooks it and starts the fight (FishBite)
        FishingReal.fishAtHook(player, kindOf(fish), fish, hook);
    }

    /** The hook was pulled out or broke: whatever fish was coming forgets it. */
    public static void forget(FishingHook hook) {
        DRAWN.remove(hook.getUUID());
    }

    /**
     * A fish in range that notices the bait this check, or null. (0.21.63, Track B) The rod decides which fish come at all
     * and from how far ({@link Tackle.Rod}), the bait which of them it draws most ({@link Tackle.Bait}; a lure draws
     * nothing on a rod that can't work it); groundbait and trolling make them keener and draw from farther.
     */
    private static @Nullable LivingEntity notice(ServerLevel level, FishingHook hook, ServerPlayer player) {
        Tackle.Rod rod = Tackle.rodOf(player);
        Tackle.Bait bait = Tackle.baitOf(HookBait.bait(player));
        if (bait == null || bait.lure() && !rod.lures) {
            return null;
        }
        boolean trolling = FishingGear.trolling(player);
        float keen = FishWaters.bites(level, hook.blockPosition()) * rod.keen * FishingGear.groundbait(level, hook.blockPosition());
        if (trolling) {
            keen *= HookBait.bait(player) == Tackle.TROLLING_SQUID ? FishingGear.TROLL_LURE_KEEN : FishingGear.TROLL_KEEN;
        }
        if (level.getRandom().nextFloat() >= NOTICE_CHANCE * keen) {
            return null;
        }
        double range = DRAW_RANGE * rod.range * (trolling ? FishingGear.TROLL_RANGE : 1.0);
        List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class, hook.getBoundingBox().inflate(range), e -> {
            int kind = kindOf(e);
            return kind >= 0 && e.isAlive() && e.isInWater() && !taken(e) && rod.takes(kind) && bait.draw(kind) > 0;
        });
        if (near.isEmpty()) {
            return null;
        }
        // the bait's favourites, and the nearer, the likelier
        double total = 0.0;
        double[] weight = new double[near.size()];
        for (int i = 0; i < near.size(); i++) {
            LivingEntity e = near.get(i);
            weight[i] = bait.draw(kindOf(e)) / (1.0 + e.distanceTo(hook));
            total += weight[i];
        }
        double roll = level.getRandom().nextDouble() * total;
        for (int i = 0; i < near.size(); i++) {
            roll -= weight[i];
            if (roll <= 0.0) {
                return near.get(i);
            }
        }
        return near.getLast();
    }

    /** A fish took {@code player}'s hook: natural bait is eaten; a lure stays on, unless it snaps off. */
    private static void taken(ServerPlayer player) {
        net.minecraft.world.item.Item onHook = HookBait.bait(player);
        Tackle.Bait bait = Tackle.baitOf(onHook);
        if (bait != null && bait.lure()) {
            if (player.getRandom().nextFloat() < bait.snap()) {
                HookBait.eaten(player);
                player.sendSystemMessage(Component.translatable("message.jaymospowers.fishing.lure_snapped",
                        new net.minecraft.world.item.ItemStack(onHook).getHoverName()).withStyle(ChatFormatting.RED));
            }
            return;
        }
        HookBait.eaten(player);
    }

    /** {@code e} is already coming to a hook, or on a line. */
    private static boolean taken(LivingEntity e) {
        for (Drawn d : DRAWN.values()) {
            if (d.fish.equals(e.getUUID())) {
                return true;
            }
        }
        return FishingReal.onLine(e);
    }

    private static void swimTo(LivingEntity fish, FishingHook hook) {
        if (fish instanceof Mob mob && !mob.isNoAi()) {
            mob.getNavigation().moveTo(hook.getX(), hook.getY() - 0.3, hook.getZ(), SWIM_SPEED);
        }
        Vec3 to = new Vec3(hook.getX() - fish.getX(), hook.getY() - 0.3 - fish.getY(), hook.getZ() - fish.getZ());
        if (to.lengthSqr() > 1.0E-4) {
            fish.setDeltaMovement(fish.getDeltaMovement().add(to.normalize().scale(NUDGE)));
            fish.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, hook.position());
        }
    }

    private static void dip(ServerLevel level, FishingHook hook, double depth) {
        hook.setDeltaMovement(hook.getDeltaMovement().add(0.0, -depth, 0.0));
        level.sendParticles(ParticleTypes.SPLASH, hook.getX(), hook.getY(), hook.getZ(), 4, 0.1, 0.0, 0.1, 0.0);
    }

    /** Tell an angler with a bare hook in the water, now and then, that nothing will come to it. */
    public static void bare(FishingHook hook, ServerPlayer player) {
        if (hook.tickCount % BARE_EVERY == 0 && HookBait.bait(player) == null) {
            player.sendOverlayMessage(Component.translatable("message.jaymospowers.fishing.bare_hook").withStyle(ChatFormatting.GRAY));
        }
    }

    /** How often (ticks) a bare hook says it wants bait. */
    static final int BARE_EVERY = 200;
}
