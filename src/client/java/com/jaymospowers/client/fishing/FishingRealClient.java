package com.jaymospowers.client.fishing;

import com.jaymospowers.client.UiStyle;
import com.jaymospowers.client.hud.HudStyle;
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
 * breaking point; the line shows the drag's slip point and which way the fish runs; a cast bar fills while charging a cast;
 * and the held rod bends with the strain ({@link #bend}).
 */
public final class FishingRealClient {
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

    /** (2026-11-23) The fight's readout: how far below the crosshair it sits. */
    private static final int READOUT_BELOW = 34;
    /** (2026-11-26) The fight's plaque and the cast's, their size; the line's height and its twist (px); the fight's pips. */
    private static final int PLAQUE_W = 200, PLAQUE_H = 41, CAST_W = 110, CAST_H = 17, ROPE_H = 4, TWIST = 4, PIPS = 8;
    /** The keys show under the plaque this long into a fight (ticks). */
    private static final int KEYS_TICKS = 200;
    /** Hemp, slack line, the plaque's light and faint lettering (the pack's paper tones), an unlit pip, a fish still taking the lure. */
    private static final int HEMP = 0xFFC9B07A, ROPE_SLACK = 0xFF2A241C, INK_LIGHT = UiStyle.PAPER, INK_FAINT = 0xFFA89878,
            DIM_PIP = 0xFF4A4236, MOOD_TAKING = 0xFF9EC4CC;

    private static int scrollReelTicks;
    private static float scrollNotches;
    private static int creakIn;
    /** (2026-11-23) Sneak + use has let this fish go already (sent once a press). */
    private static boolean releaseSent;
    /** Use was down last tick: a release is a fresh press, never Shift added to a held reel. */
    private static boolean useWasDown;

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
        // the tackle bench's screen, and what is fitted to a rod
        net.minecraft.client.gui.screens.MenuScreens.register(com.jaymospowers.fishing.TackleBenchMenu.TYPE, TackleBenchScreen::new);
        ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
            com.jaymospowers.fishing.FishTackle.Fit fit = com.jaymospowers.fishing.FishTackle.fit(stack);
            int at = Math.min(1, lines.size());
            if (fit.hook() == com.jaymospowers.fishing.FishTackle.BARBED) {
                lines.add(at, Component.translatable("tooltip.jaymospowers.fit.barbed").withStyle(ChatFormatting.GRAY));
            }
            if (fit.line() != com.jaymospowers.fishing.FishTackle.NO_LINE) {
                lines.add(at, Component.translatable(fit.line() == com.jaymospowers.fishing.FishTackle.BRAIDED
                        ? "tooltip.jaymospowers.fit.braided" : "tooltip.jaymospowers.fit.weighted").withStyle(ChatFormatting.GRAY));
            }
            net.minecraft.world.item.Item bead = com.jaymospowers.fishing.FishTackle.bead(fit);
            if (bead != null) {
                lines.add(at, Component.translatable("tooltip.jaymospowers.fit.float", new ItemStack(bead).getHoverName()).withStyle(ChatFormatting.GRAY));
            }
        });
        // (Track D) a fisherman's screen, and the fisher's journal
        ClientPlayNetworking.registerGlobalReceiver(com.jaymospowers.fishing.Fishermen.View.TYPE,
                (payload, context) -> FishermanScreen.receive(context.client(), payload));
        net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.register((player, level, hand) -> {
            if (level.isClientSide() && com.jaymospowers.fishing.FishLog.JOURNAL != null
                    && player.getItemInHand(hand).is(com.jaymospowers.fishing.FishLog.JOURNAL)) {
                // (Andy's skill-tree locks) the journal is learned: Angler, on Survival's Trades page
                if (com.jaymospowers.core.TreeLocks.check(player, com.jaymospowers.core.TreeLocks.ANGLER)) {
                    Minecraft.getInstance().gui.setScreen(new JournalScreen());
                }
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
            releaseSent = false;
            useWasDown = minecraft.options.keyUse.isDown();
            return;
        }
        boolean scrolled = scrollReelTicks > 0;
        if (scrolled) {
            scrollReelTicks--;
        }
        if (minecraft.gui.screen() == null) {
            boolean use = minecraft.options.keyUse.isDown();
            if (use && !useWasDown) {
                // (Andy, 2026-11-23) sneak + use lets the fish go; (2026-11-26: "shift shouldnt stop your cast") only a press
                // of use made while already sneaking: Shift pressed while you hold use to reel changes nothing
                releaseSent = player.isShiftKeyDown();
                if (releaseSent) {
                    ClientPlayNetworking.send(new FishingPayloads.ReleaseFish());
                }
            } else if (!use) {
                releaseSent = false;
            }
            useWasDown = use;
            ClientPlayNetworking.send(new FishingPayloads.ReelInput(!releaseSent && (use || scrolled)));
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

    /**
     * Draw the fight's plaque below the crosshair while a fight is live, or the cast bar while charging a cast. (Andy,
     * 2026-11-26: "make it less modern looking and fit the pack aesthetic") A dark plate in the pack's gold frame with its
     * corner diamonds (the screens' {@link UiStyle#frame}, the tip cards' plate), no percentages: the fish and its weight,
     * what it's doing in words, the line drawn as twisted hemp that reddens as it nears breaking (the drag's slip point a
     * gold notch on it, a run's way a chevron at its end), the fish's fight left in pips, the drag in diamonds, and the line
     * out in metres. The keys show under it for the first moments of a fight.
     */
    private static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }
        int w = graphics.guiWidth();
        int h = graphics.guiHeight();
        int cx = w / 2;
        Font font = minecraft.font;
        FishFight state = fight(player);
        if (!state.fighting()) {
            float charge = castCharge(player);
            if (charge >= 0.0F) {
                int x = cx - CAST_W / 2, y = h - 52;
                plate(graphics, x, y, CAST_W, CAST_H, UiStyle.FRAME);
                Component label = Component.translatable("hud.jaymospowers.fishing.cast");
                graphics.text(font, label, x + 6, y + 5, INK_LIGHT, true);
                int rx = x + 10 + font.width(label);
                rope(graphics, rx, y + 7, x + CAST_W - 7 - rx, charge, HEMP);
            }
            return;
        }
        int x = cx - PLAQUE_W / 2;
        int y = h / 2 + READOUT_BELOW;
        float strain = state.strain();
        int moodColour = state.mood() == FishFight.TAKING ? MOOD_TAKING : state.easeOff() ? UiStyle.GOLD : UiStyle.GOOD;
        plate(graphics, x, y, PLAQUE_W, PLAQUE_H, moodColour);
        // the fish, its weight, and what it's doing
        com.jaymospowers.fishing.Species sp = com.jaymospowers.fishing.Species.of(state.fishKind());
        Component name = sp == null ? Component.translatable("hud.jaymospowers.fishing.a_fish") : Component.translatable(sp.nameKey());
        Component mood = Component.translatable("hud.jaymospowers.fishing.mood." + state.mood());
        int moodW = font.width(mood);
        Component title = Component.translatable("hud.jaymospowers.fishing.title", name, String.format(java.util.Locale.ROOT, "%.1f", state.kg()));
        UiStyle.line(graphics, font, title, x + 7, y + 5, PLAQUE_W - 18 - moodW, UiStyle.GOLD);
        graphics.text(font, mood, x + PLAQUE_W - 7 - moodW, y + 5, moodColour, true);
        // the line: hemp, reddening near its breaking point, the drag's slip point notched on it, a run's way at its end
        int ropeX = x + 14, ropeW = PLAQUE_W - 28, ropeY = y + 19;
        int hemp = strain >= CREAK_AT ? HudStyle.mix(HEMP, UiStyle.BAD, (strain - CREAK_AT) / (1.0F - CREAK_AT)) : HEMP;
        rope(graphics, ropeX, ropeY, ropeW, strain, hemp);
        int drag = FishRun.drag(FishRun.rodInHand(player));
        int slipX = ropeX + Math.round(ropeW * FishRun.DRAG_SLIP[drag - 1]);
        graphics.fill(slipX, ropeY - 2, slipX + 1, ropeY + ROPE_H + 2, UiStyle.FRAME);
        if (state.run() != 0) {
            chevron(graphics, state.run() > 0 ? ropeX + ropeW + 4 : ropeX - 5, ropeY + ROPE_H / 2, state.run(), UiStyle.GOLD);
        }
        // its fight left, the drag, and the line out
        int rowY = y + 29;
        Component fightLabel = Component.translatable("hud.jaymospowers.fishing.fight");
        graphics.text(font, fightLabel, x + 7, rowY, INK_LIGHT, true);
        int pipX = x + 10 + font.width(fightLabel);
        int lit = Mth.ceil(Mth.clamp(state.energy(), 0.0F, 1.0F) * PIPS);
        for (int i = 0; i < PIPS; i++) {
            int px = pipX + i * 4;
            graphics.fill(px, rowY + 2, px + 3, rowY + 5, i < lit ? INK_LIGHT : DIM_PIP);
        }
        Component dragLabel = Component.translatable("hud.jaymospowers.fishing.drag");
        int dragX = pipX + PIPS * 4 + 8;
        graphics.text(font, dragLabel, dragX, rowY, INK_LIGHT, true);
        int dx = dragX + font.width(dragLabel) + 5;
        for (int i = 0; i < FishRun.DRAG_STEPS; i++) {
            diamond(graphics, dx + i * 6, rowY + 3, i < drag ? UiStyle.FRAME : DIM_PIP);
        }
        Component spool = Component.translatable("hud.jaymospowers.fishing.spool", Math.round(state.lineOut()), Math.round(state.lineMax()));
        graphics.text(font, spool, x + PLAQUE_W - 7 - font.width(spool), rowY, INK_FAINT, true);
        // the keys, for the first moments of a fight
        long fought = player.level().getGameTime() - state.startTime();
        if (fought < KEYS_TICKS) {
            Component keys = Component.translatable("hud.jaymospowers.fishing.keys", Component.keybind("key.use"), Component.keybind("key.sneak"),
                    Component.keybind("key.jaymospowers.dodge"));
            UiStyle.lineCentred(graphics, font, keys, cx, y + PLAQUE_H + 3, Math.max(PLAQUE_W, w - 40), INK_FAINT);
        }
    }

    /** The plate the fight and the cast sit on: the tip cards' dark plate in the screens' gold frame, ticked in {@code accent}. */
    private static void plate(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int accent) {
        graphics.fill(x, y, x + w, y + h, HudStyle.PLATE);
        UiStyle.frame(graphics, x, y, w, h, accent);
    }

    /**
     * A line drawn as twisted hemp, {@code share} of it taut ({@code colour}), the rest slack and dark: a sunken groove with
     * a slanted twist every few pixels.
     */
    private static void rope(GuiGraphicsExtractor graphics, int x, int y, int w, float share, int colour) {
        graphics.fill(x - 1, y - 1, x + w + 1, y + ROPE_H + 1, 0xC0000000);
        graphics.fill(x, y, x + w, y + ROPE_H, ROPE_SLACK);
        int filled = Math.round(w * Mth.clamp(share, 0.0F, 1.0F));
        graphics.fill(x, y, x + filled, y + ROPE_H, colour);
        int twist = HudStyle.mix(colour, 0xFF000000, 0.35F);
        for (int px = 0; px < filled; px++) {
            int row = (px % TWIST) * ROPE_H / TWIST;
            graphics.fill(x + px, y + row, x + px + 1, y + row + 1, twist);
        }
    }

    /** A small chevron pointing {@code dir} (1 right, -1 left), its tip at {@code (x, y)}. */
    private static void chevron(GuiGraphicsExtractor graphics, int x, int y, int dir, int colour) {
        for (int i = 0; i <= 2; i++) {
            int px = x - dir * i;
            graphics.fill(px, y - i, px + 1, y - i + 1, colour);
            graphics.fill(px, y + i, px + 1, y + i + 1, colour);
        }
    }

    /** A little diamond (5 px across) centred on {@code (cx, cy)}. */
    private static void diamond(GuiGraphicsExtractor graphics, int cx, int cy, int colour) {
        for (int dy = -2; dy <= 2; dy++) {
            int half = 2 - Math.abs(dy);
            graphics.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, colour);
        }
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
