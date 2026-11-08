package com.jaymospowers.fishing;

import com.mojang.serialization.Codec;
import com.jaymospowers.JaymosPowers;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * (Track A) How a hooked fish fights back, and the rod's drag. A fish surges in runs, left or right of the player, now and
 * then jumping clear of the water (Advanced Fishing's "fish resistance" and True Fishing's jumps, our own code); reeling into
 * a run spikes the tension. The drag (Legendary Fishing's idea) is a setting on the rod, 1..{@link #DRAG_STEPS}: past its slip
 * point line goes out (the fish gains) instead of the line taking the strain; a tight drag lands fish faster but leaves less
 * room before the line snaps. Turn it with sneak + scroll or sneak + use with no hook out. {@link FishingReal}'s fight tick
 * uses all of this.
 */
public final class FishRun {
    /** The rod's drag step. */
    public static DataComponentType<Integer> DRAG;
    public static final int DRAG_STEPS = 5, DRAG_DEFAULT = 3;
    /** Where each drag step lets line slip, as a share of the breaking point. */
    public static final float[] DRAG_SLIP = {0.45F, 0.6F, 0.72F, 0.84F, 0.95F};
    /** How much faster each drag step brings the fish in while reeling. */
    static final float[] DRAG_REEL = {0.7F, 0.85F, 1.0F, 1.15F, 1.3F};
    /** Share of the tension above the slip point the drag keeps (the rest goes out as line). */
    static final float DRAG_HOLD = 0.3F;
    /** Progress lost per tick of slipping line, per unit of fish strength. */
    static final float DRAG_LINE_OUT = 0.0025F;
    /** Ticks between the reel's clicks while line slips. */
    static final int DRAG_CLICK_EVERY = 3;

    /** Ticks before a fresh fight's first run. */
    static final int FIRST_RUN_TICKS = 30;
    /** Ticks between runs (shortened by a strong fish) and how long a run lasts. */
    static final int RUN_GAP_LEAST = 40, RUN_GAP_MOST = 110;
    static final int RUN_TICKS_LEAST = 15, RUN_TICKS_MOST = 35;
    /** Reeling into a run multiplies the tension it adds. */
    static final float RUN_REEL_SPIKE = 3.0F;
    /** Tension a run adds per tick while the player eases off, per unit of fish strength. */
    static final float RUN_PULL = 0.015F;
    /** Progress a run takes back per tick while the player eases off, per unit of fish strength. */
    static final float RUN_LINE_OUT = 0.004F;
    /** How hard a running fish swims sideways (blocks per tick, per unit of strength). */
    static final double RUN_SPEED = 0.07;
    /** Chance a run starts with a jump clear of the water, and how high. */
    static final float JUMP_CHANCE = 0.35F;
    static final double JUMP_UP = 0.55;

    private static final class Run {
        long fightStart;
        int dir;
        long endsAt;
        long nextAt;
    }

    /** Server side: each fight's run, keyed by player uuid. */
    private static final Map<UUID, Run> RUNS = new HashMap<>();

    private FishRun() {
    }

    public static void init() {
        DRAG = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosPowers.id("rod_drag"),
                DataComponentType.<Integer>builder().persistent(Codec.intRange(1, DRAG_STEPS)).networkSynchronized(ByteBufCodecs.VAR_INT).build());
        ServerPlayNetworking.registerGlobalReceiver(FishingPayloads.SetDrag.TYPE,
                (payload, context) -> turnDrag(context.player(), Integer.signum(payload.delta())));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            RUNS.remove(handler.player.getUUID());
            FishBite.clear(handler.player);
        });
    }

    /**
     * Start, steer and end the fish's runs for this tick of {@code state}'s fight. Returns -1 or 1 while the fish runs to the
     * player's left or right, 0 while it holds.
     */
    static int tick(ServerPlayer player, LivingEntity fish, FishFight state, long now) {
        Run run = RUNS.get(player.getUUID());
        if (run == null || run.fightStart != state.startTime()) {
            run = new Run();
            run.fightStart = state.startTime();
            run.nextAt = state.startTime() + FIRST_RUN_TICKS;
            RUNS.put(player.getUUID(), run);
        }
        RandomSource random = player.getRandom();
        boolean held = fish instanceof Mob mob && mob.isNoAi();
        if (run.dir != 0 && now >= run.endsAt) {
            run.dir = 0;
            run.nextAt = now + Math.round(Mth.nextInt(random, RUN_GAP_LEAST, RUN_GAP_MOST) / (0.5F + state.strength()));
        }
        if (run.dir == 0 && now >= run.nextAt) {
            run.dir = random.nextBoolean() ? 1 : -1;
            run.endsAt = now + Mth.nextInt(random, RUN_TICKS_LEAST, RUN_TICKS_MOST);
            if (!held && fish.isInWater() && random.nextFloat() < JUMP_CHANCE) {
                fish.setDeltaMovement(fish.getDeltaMovement().add(0.0D, JUMP_UP, 0.0D));
                player.level().playSound(null, fish.getX(), fish.getY(), fish.getZ(),
                        SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.NEUTRAL, 0.8F, 0.8F);
            }
        }
        if (run.dir == 0) {
            return 0;
        }
        // sideways from the player's view of the fish: their right is (-z, x) of the line toward it
        double fx = fish.getX() - player.getX();
        double fz = fish.getZ() - player.getZ();
        double len = Math.sqrt(fx * fx + fz * fz);
        if (len > 1.0E-4D) {
            double speed = RUN_SPEED * state.strength() * run.dir;
            double sx = -fz / len * speed;
            double sz = fx / len * speed;
            if (held) {
                fish.setPos(fish.getX() + sx, fish.getY(), fish.getZ() + sz);
            } else {
                fish.setDeltaMovement(fish.getDeltaMovement().add(sx, 0.0D, sz));
            }
        }
        return run.dir;
    }

    /** The drag lets line slip past its setting: returns the tension it holds, and takes line off {@code progress[0]}. */
    static float slip(ServerPlayer player, FishFight state, float tension, float breakAt, float[] progress, long now) {
        float slip = DRAG_SLIP[drag(rodInHand(player)) - 1] * breakAt;
        if (tension <= slip) {
            return tension;
        }
        progress[0] -= state.strength() * DRAG_LINE_OUT;
        if (now % DRAG_CLICK_EVERY == 0L) {
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.TRIPWIRE_CLICK_ON, SoundSource.PLAYERS, 0.35F, 1.9F);
        }
        return slip + (tension - slip) * DRAG_HOLD;
    }

    /** How much faster the held rod's drag brings a fish in. */
    static float reelFactor(ServerPlayer player) {
        return DRAG_REEL[drag(rodInHand(player)) - 1];
    }

    /** The rod's drag step, 1..{@link #DRAG_STEPS}. */
    public static int drag(ItemStack rod) {
        Integer drag = DRAG == null ? null : rod.get(DRAG);
        return drag == null ? DRAG_DEFAULT : Mth.clamp(drag, 1, DRAG_STEPS);
    }

    /** Whether {@code stack} is a fishing rod (vanilla or metal). */
    public static boolean isRod(ItemStack stack) {
        // every fishing rod: vanilla's, the tackle rods (cane pole, spinning, ledger) and the metal rod
        return HookBait.isRod(stack);
    }

    /** The rod the player holds (main hand first), or empty. */
    public static ItemStack rodInHand(Player player) {
        ItemStack main = player.getMainHandItem();
        if (isRod(main)) {
            return main;
        }
        ItemStack off = player.getOffhandItem();
        return isRod(off) ? off : ItemStack.EMPTY;
    }

    /** Turn the held rod's drag by {@code delta} steps; {@code 0} cycles it round (sneak + use). */
    public static void turnDrag(ServerPlayer player, int delta) {
        ItemStack rod = rodInHand(player);
        if (rod.isEmpty() || DRAG == null) {
            return;
        }
        int was = drag(rod);
        int next = delta == 0 ? was % DRAG_STEPS + 1 : Mth.clamp(was + delta, 1, DRAG_STEPS);
        rod.set(DRAG, next);
        player.sendOverlayMessage(Component.translatable("message.jaymospowers.fishing.drag", next, DRAG_STEPS)
                .withStyle(ChatFormatting.AQUA));
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.TRIPWIRE_CLICK_ON, SoundSource.PLAYERS, 0.4F, 1.2F + next * 0.15F);
    }
}
