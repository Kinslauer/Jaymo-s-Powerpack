package com.jaymospowers.client.fishing;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.fishing.FishFight;
import com.jaymospowers.fishing.FishingReal;
import com.jaymospowers.fishing.FishingPayloads;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

/**
 * Client side of realistic fishing ({@link FishingReal}): while a fish is on the line, holding use reels it in,
 * sending {@link FishingPayloads.ReelInput} to the server each tick. Draws a tension bar so the player can see how
 * close the line is to breaking.
 */
public final class FishingRealClient {
    private static final int BAR_W = 100, BAR_H = 6;

    private FishingRealClient() {
    }

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(FishingRealClient::tick);
        HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS,
                JaymosPowers.id("fishing_tension"), FishingRealClient::render);
    }

    /** Each tick while fighting a fish, tell the server whether the player is holding the reel key. */
    private static void tick(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.gui.screen() != null) {
            return;
        }
        FishFight state = player.getAttached(FishingReal.FIGHTING);
        if (state == null || !state.fighting()) {
            return;
        }
        ClientPlayNetworking.send(new FishingPayloads.ReelInput(minecraft.options.keyUse.isDown()));
    }

    /** Draw the tension bar above the hotbar while a fight is live. */
    private static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }
        FishFight state = player.getAttached(FishingReal.FIGHTING);
        if (state == null || !state.fighting()) {
            return;
        }
        int w = minecraft.getWindow().getWidth();
        int h = minecraft.getWindow().getHeight();
        int x = w / 2 - BAR_W / 2;
        int y = h - 40;
        Font font = minecraft.font;
        graphics.fill(x - 1, y - 1, x + BAR_W + 1, y + BAR_H + 1, 0xC0000000);
        graphics.fill(x, y, x + BAR_W, y + BAR_H, 0xFF202028);
        int tension = Math.round(BAR_W * Mth.clamp(state.tension(), 0.0F, 1.0F));
        graphics.fill(x, y, x + tension, y + BAR_H, 0xFFFF5050);
        String label = "Fishing: " + Mth.clamp((int) (state.tension() * 100), 0, 100) + "% tension";
        graphics.text(font, label, x + BAR_W / 2 - font.width(label) / 2, y - 10, 0xFFFFFFFF, true);
    }
}
