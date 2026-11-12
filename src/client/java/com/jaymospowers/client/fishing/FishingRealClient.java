package com.jaymospowers.client.fishing;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.fishing.FishCast;
import com.jaymospowers.fishing.FishCatch;
import com.jaymospowers.fishing.FishFight;
import com.jaymospowers.fishing.FishRun;
import com.jaymospowers.fishing.FishingPayloads;
import com.jaymospowers.fishing.FishingReal;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Client side of realistic fishing ({@link FishingReal}): while a fish is on the line, holding use reels it in,
 * sending {@link FishingPayloads.ReelInput} to the server each tick. Draws a tension bar so the player can see how
 * close the line is to breaking. (Track A) Scrolling reels too; sneak + scroll turns the drag; the line creaks near its
 * breaking point; the bar shows the drag's slip point and which way the fish runs; a cast bar fills while charging a cast;
 * and the held rod bends with the strain ({@link #bend}).
 */
public final class FishingRealClient {
    private static final int BAR_W = 100, BAR_H = 6;
    /** Strain (share of the breaking point) above which the line creaks, and the slowest and fastest creak (ticks). */
    private static final float CREAK_AT = 0.75F;
    private static final int CREAK_SLOW = 10, CREAK_FAST = 3;
    /** Ticks of reeling one scroll notch is worth, and the most that can be banked. */
    private static final int REEL_TICKS_PER_NOTCH = 3, REEL_TICKS_MOST = 12;
    /** Rod bend (degrees): the tip dips this far at the breaking point, swings this far toward a run, shakes this much. */
    private static final float BEND_DEG = 28.0F, RUN_SWING_DEG = 12.0F, SHAKE_DEG = 3.5F, SHAKE_SPEED = 1.7F;
    /** How far the rod is drawn back over the shoulder at a full cast charge (degrees). */
    private static final float CHARGE_BACK_DEG = 35.0F;
    /** Flip if the third-person rod bends up instead of down (its pose frame differs from first person's). */
    private static final float THIRD_PERSON_SIGN = 1.0F;

    private static int scrollReelTicks;
    private static float scrollNotches;
    private static int creakIn;

    private FishingRealClient() {
    }

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(FishingRealClient::tick);
        HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS,
                JaymosPowers.id("fishing_tension"), FishingRealClient::render);
        // (0.21.62) the skipjack tuna
        net.minecraft.client.renderer.entity.EntityRenderers.register(com.jaymospowers.fishing.Skipjack.SKIPJACK, SkipjackRenderer::new);
        // (Track C) every new species with its own model, and (Track B leftovers, D) the angler's blocks
        for (java.util.Map.Entry<com.jaymospowers.fishing.Species, net.minecraft.world.entity.EntityType<?>> e
                : com.jaymospowers.fishing.FishSpecies.types().entrySet()) {
            register(e.getValue(), e.getKey());
        }
        net.minecraft.client.renderer.blockentity.BlockEntityRenderers.register(com.jaymospowers.fishing.FishingBlocks.ROD_POD_ENTITY, RodPodRenderer::new);
        net.minecraft.client.renderer.blockentity.BlockEntityRenderers.register(com.jaymospowers.fishing.FishingBlocks.TROPHY_PLAQUE_ENTITY,
                TrophyPlaqueRenderer::new);
        // (Track D) a fisherman's screen, and the fisher's journal
        ClientPlayNetworking.registerGlobalReceiver(com.jaymospowers.fishing.Fishermen.View.TYPE,
                (payload, context) -> FishermanScreen.receive(context.client(), payload));
        net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.register((player, level, hand) -> {
            if (level.isClientSide() && com.jaymospowers.fishing.FishLog.JOURNAL != null
                    && player.getItemInHand(hand).is(com.jaymospowers.fishing.FishLog.JOURNAL)) {
                Minecraft.getInstance().gui.setScreen(new JournalScreen());
                return net.minecraft.world.InteractionResult.SUCCESS;
            }
            return net.minecraft.world.InteractionResult.PASS;
        });
        // (0.21.62) a landed fish shows its weight, a rod the bait on its hook
        ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
            FishCatch.Info info = FishCatch.info(stack);
            Float kg = info == null ? FishCatch.weight(stack) : null;
            if (info != null) {
                // (Track D) the catch's story: length and weight, who and when, and alive or not
                Minecraft mc = Minecraft.getInstance();
                long now = mc.level == null ? 0L : mc.level.getGameTime();
                int at = Math.min(1, lines.size());
                lines.add(at, Component.translatable("tooltip.jaymospowers.fish_catch", String.format(java.util.Locale.ROOT, "%.1f", info.kg()),
                        info.cm()).withStyle(ChatFormatting.GRAY));
                if (!info.angler().isEmpty()) {
                    lines.add(at + 1, Component.translatable("tooltip.jaymospowers.fish_caught_by", info.angler(), info.day())
                            .withStyle(ChatFormatting.DARK_GRAY));
                }
                if (info.alive(now)) {
                    lines.add(at + 1, Component.translatable("tooltip.jaymospowers.fish_alive", (info.aliveUntil() - now) / 20L)
                            .withStyle(ChatFormatting.AQUA));
                }
            }
            if (kg != null) {
                lines.add(Math.min(1, lines.size()), Component.translatable("tooltip.jaymospowers.fish_weight",
                        String.format(java.util.Locale.ROOT, "%.1f", kg)).withStyle(ChatFormatting.GRAY));
            }
            net.minecraft.resources.Identifier bait = com.jaymospowers.fishing.HookBait.BAIT == null ? null
                    : stack.get(com.jaymospowers.fishing.HookBait.BAIT);
            if (bait != null) {
                net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(bait).orElse(null);
                if (item != null) {
                    lines.add(Math.min(1, lines.size()), Component.translatable("tooltip.jaymospowers.hook_bait",
                            item.getName(new net.minecraft.world.item.ItemStack(item))).withStyle(ChatFormatting.GRAY));
                }
            } else if (com.jaymospowers.fishing.HookBait.isRod(stack)) {
                lines.add(Math.min(1, lines.size()), Component.translatable("tooltip.jaymospowers.hook_bare").withStyle(ChatFormatting.DARK_GRAY));
            }
            // (0.21.64) Track B tackle: what each rod is for, and how to use bait and lures
            if (stack.is(com.jaymospowers.fishing.Tackle.CANE_POLE) || stack.is(com.jaymospowers.fishing.Tackle.SPINNING_ROD)
                    || stack.is(com.jaymospowers.fishing.Tackle.LEDGER_ROD)) {
                String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
                lines.add(Math.min(1, lines.size()), Component.translatable("tooltip.jaymospowers.rod." + id).withStyle(ChatFormatting.GRAY));
            } else if (stack.is(com.jaymospowers.fishing.HookBait.BAITS)) {
                lines.add(Math.min(1, lines.size()), Component.translatable(com.jaymospowers.fishing.Tackle.isLure(stack.getItem())
                        ? "tooltip.jaymospowers.lure" : "tooltip.jaymospowers.bait").withStyle(ChatFormatting.GRAY));
            }
            // (Track B leftovers, D) what the angler's new blocks and tools are for
            String path = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
            if (net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals(JaymosPowers.MOD_ID)
                    && java.util.Set.of("ice_auger", "rod_pod", "crab_pot", "keepnet", "trophy_plaque", "fishers_journal").contains(path)) {
                lines.add(Math.min(1, lines.size()), Component.translatable("tooltip.jaymospowers." + path).withStyle(ChatFormatting.GRAY));
            }
            if (info != null && info.alive(Minecraft.getInstance().level == null ? 0L : Minecraft.getInstance().level.getGameTime())) {
                lines.add(Component.translatable("tooltip.jaymospowers.release").withStyle(ChatFormatting.DARK_GRAY));
            }
            // (Track A) a rod shows its drag
            if (FishRun.isRod(stack)) {
                lines.add(Math.min(1, lines.size()), Component.translatable("tooltip.jaymospowers.rod_drag",
                        FishRun.drag(stack), FishRun.DRAG_STEPS).withStyle(ChatFormatting.GRAY));
            }
        });
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void register(net.minecraft.world.entity.EntityType<?> type, com.jaymospowers.fishing.Species species) {
        net.minecraft.client.renderer.entity.EntityRenderers.register((net.minecraft.world.entity.EntityType) type,
                context -> new WildFishRenderer<>(context, species));
    }

    private static FishFight fight(Player player) {
        FishFight state = player.getAttached(FishingReal.FIGHTING);
        return state == null ? FishFight.NONE : state;
    }

    /** Each tick while fighting a fish, tell the server whether the player is reeling (holding use, or scrolled), and creak. */
    private static void tick(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }
        FishFight state = fight(player);
        if (!state.fighting()) {
            scrollReelTicks = 0;
            scrollNotches = 0.0F;
            return;
        }
        boolean scrolled = scrollReelTicks > 0;
        if (scrolled) {
            scrollReelTicks--;
        }
        if (minecraft.gui.screen() == null) {
            ClientPlayNetworking.send(new FishingPayloads.ReelInput(minecraft.options.keyUse.isDown() || scrolled));
        }
        // (Track A) the line creaks near its breaking point, faster the closer it gets
        float strain = state.strain();
        if (strain < CREAK_AT) {
            creakIn = 0;
        } else if (--creakIn <= 0) {
            float near = (strain - CREAK_AT) / (1.0F - CREAK_AT);
            creakIn = Math.round(Mth.lerp(near, CREAK_SLOW, CREAK_FAST));
            player.level().playLocalSound(player.getX(), player.getEyeY(), player.getZ(), SoundEvents.CROSSBOW_LOADING_MIDDLE.value(),
                    SoundSource.PLAYERS, 0.35F + 0.4F * near, 0.7F + 0.6F * near, false);
        }
    }

    /**
     * (Track A) The mouse wheel, from FishingScrollMixin: sneak + scroll with a rod in hand turns its drag; scrolling while a
     * fish is on reels it in. Returns true when the scroll was used here (the hotbar keeps still).
     */
    public static boolean scroll(double amount) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || amount == 0.0D || FishRun.rodInHand(player).isEmpty()) {
            return false;
        }
        if (player.isShiftKeyDown()) {
            ClientPlayNetworking.send(new FishingPayloads.SetDrag(amount > 0.0D ? 1 : -1));
            return true;
        }
        if (!fight(player).fighting()) {
            return false;
        }
        scrollNotches += (float) Math.abs(amount);
        while (scrollNotches >= 1.0F) {
            scrollNotches -= 1.0F;
            scrollReelTicks = Math.min(REEL_TICKS_MOST, scrollReelTicks + REEL_TICKS_PER_NOTCH);
        }
        return true;
    }

    /** Draw the tension bar above the hotbar while a fight is live, or the cast bar while charging a cast. */
    private static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }
        int w = graphics.guiWidth();
        int h = graphics.guiHeight();
        int x = w / 2 - BAR_W / 2;
        int y = h - 40;
        Font font = minecraft.font;
        FishFight state = fight(player);
        if (!state.fighting()) {
            float charge = castCharge(player);
            if (charge >= 0.0F) {
                bar(graphics, x, y, charge, 0xFF50C8FF);
                Component label = Component.translatable("hud.jaymospowers.fishing.cast", Math.round(charge * 100));
                graphics.text(font, label, x + BAR_W / 2 - font.width(label) / 2, y - 10, 0xFFFFFFFF, true);
            }
            return;
        }
        float strain = state.strain();
        bar(graphics, x, y, strain, strain >= CREAK_AT ? 0xFFFF3030 : 0xFFFF8050);
        // the drag's slip point
        int drag = FishRun.drag(FishRun.rodInHand(player));
        int slipX = x + Math.round(BAR_W * FishRun.DRAG_SLIP[drag - 1]);
        graphics.fill(slipX, y - 2, slipX + 1, y + BAR_H + 2, 0xFF60E0FF);
        // which way the fish runs
        if (state.run() != 0) {
            String arrows = state.run() > 0 ? ">>" : "<<";
            int ax = state.run() > 0 ? x + BAR_W + 4 : x - 4 - font.width(arrows);
            graphics.text(font, Component.literal(arrows), ax, y - 1, 0xFFFFD040, true);
        }
        int percent = Mth.clamp(Math.round(strain * 100), 0, 100);
        Component label = Component.translatable("hud.jaymospowers.fishing.tension_drag", percent, drag, FishRun.DRAG_STEPS);
        graphics.text(font, label, x + BAR_W / 2 - font.width(label) / 2, y - 10, 0xFFFFFFFF, true);
    }

    private static void bar(GuiGraphicsExtractor graphics, int x, int y, float fill, int color) {
        graphics.fill(x - 1, y - 1, x + BAR_W + 1, y + BAR_H + 1, 0xC0000000);
        graphics.fill(x, y, x + BAR_W, y + BAR_H, 0xFF202028);
        graphics.fill(x, y, x + Math.round(BAR_W * Mth.clamp(fill, 0.0F, 1.0F)), y + BAR_H, color);
    }

    /** How full {@code player}'s cast charge is, 0..1, or -1 when they aren't charging a cast. */
    private static float castCharge(Player player) {
        if (!player.isUsingItem() || player.fishing != null || !FishRun.isRod(player.getUseItem())) {
            return -1.0F;
        }
        return FishCast.charge(player.getTicksUsingItem());
    }

    /**
     * (Track A) Bend the held rod (FishingRodBendMixin, first and third person): its tip dips with the line's strain, swings
     * toward a running fish and shakes near the breaking point; while charging a cast it's drawn back. Still the plain rod
     * model: only its pose changes.
     */
    public static void bend(Entity holder, ItemStack stack, PoseStack poseStack, boolean thirdPerson, float partialTick) {
        if (!(holder instanceof Player player) || !FishRun.isRod(stack)) {
            return;
        }
        float sign = thirdPerson ? THIRD_PERSON_SIGN : 1.0F;
        FishFight state = fight(player);
        if (state.fighting()) {
            float strain = state.strain();
            float time = player.tickCount + partialTick;
            float shake = Mth.sin(time * SHAKE_SPEED) * SHAKE_DEG * strain * strain;
            poseStack.rotateDegrees(Axis.XN, sign * (BEND_DEG * strain + shake));
            if (state.run() != 0) {
                poseStack.rotateDegrees(Axis.ZN, RUN_SWING_DEG * strain * state.run());
            }
            return;
        }
        float charge = castCharge(player);
        if (charge > 0.0F) {
            poseStack.rotateDegrees(Axis.XP, sign * CHARGE_BACK_DEG * charge);
        }
    }
}
