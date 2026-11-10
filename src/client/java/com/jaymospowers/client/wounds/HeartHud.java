package com.jaymospowers.client.wounds;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.wounds.HeartCrystals;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * The heart crystal cap ({@link HeartCrystals#HEARTS}): a faint extra run under the health bar showing how many
 * permanent half-hearts the player holds. Quiet on purpose - it never looks like vanilla hearts.
 */
public final class HeartHud {
    private HeartHud() {
    }

    public static void init() {
        HudElementRegistry.attachElementAfter(VanillaHudElements.HEALTH_BAR, JaymosPowers.id("heart_crystals"), HeartHud::render);
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || !player.isAlive()) {
            return;
        }
        int hearts = HeartCrystals.getHearts(player);
        if (hearts <= 0) {
            return;
        }
        graphics.text(minecraft.font, Component.literal("+" + hearts + " hearts"), 2, 26, 0x808080, false);
    }
}
