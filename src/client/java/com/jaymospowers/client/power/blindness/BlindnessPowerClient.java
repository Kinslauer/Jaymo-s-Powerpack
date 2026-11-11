package com.jaymospowers.client.power.blindness;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.client.PowerClient;
import com.jaymospowers.client.PowerClients;
import com.jaymospowers.power.blindness.BlindnessPower;
import com.jaymospowers.power.blindness.BlindnessState;
import com.jaymospowers.power.blindness.BlindnessVariant;
import com.jaymospowers.power.blindness.BlindSide;
import com.jaymospowers.power.blindness.BlindnessEcho;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * Client-side blindness effects:
 * <ul>
 * <li>Total blindness: pitch-black vignette covering the screen, with echolocation rings from {@link BlindnessEcholocationClient}</li>
 * <li>One-eye blind: half-screen blur (Borrado) or darken (Escuro), remaining view stretches, aim drifts</li>
 * <li>Void Sight: entity outlines through walls, heartbeat sounds</li>
 * <li>Blackout: spreading darkness vignette</li>
 * <li>Abyssal Echo: full-screen flash + blindness</li>
 * </ul>
 * All rendering happens in a HUD element after MISC_OVERLAYS (like CorruptionClient).
 */
public final class BlindnessPowerClient implements PowerClient {
    public static final BlindnessPowerClient INSTANCE = new BlindnessPowerClient();

    // ---- tuning ----
    /** Total blindness vignette colour (near-black) and max alpha. */
    private static final int TOTAL_BLIND_COLOUR = 0x0A0A12;
    private static final int TOTAL_BLIND_ALPHA = 0xF0; // almost opaque
    /** The devout's (BlindnessDevotion) thinner dark, and thinner still from up in Sky Sight. */
    private static final int DEVOUT_BLIND_ALPHA = 0xB4;
    private static final int SKY_SIGHT_BLIND_ALPHA = 0x8C;
    /** One-eye Borrado (blur) - we simulate with a dark translucent overlay on one side. */
    private static final int BORRADO_COLOUR = 0x1A1A2E;
    private static final int BORRADO_ALPHA = 0xC0;
    /** One-eye Escuro (darken) - solid dark overlay on one side. */
    private static final int ESCURO_COLOUR = 0x05050A;
    private static final int ESCURO_ALPHA = 0xE0;
    /** Void Sight entity outline colour. */
    private static final int VOID_SIGHT_COLOUR = 0x8A2BE2; // blue-violet
    private static final int VOID_SIGHT_ALPHA = 0x80;
    /** Blackout spreading vignette. */
    private static final int BLACKOUT_COLOUR = 0x1A0000;
    private static final int BLACKOUT_ALPHA = 0xA0;
    /** Abyssal Echo flash. */
    private static final int ABYSSAL_FLASH_COLOUR = 0x4B0082;
    private static final int ABYSSAL_FLASH_ALPHA = 0xFF;
    private static final int ABYSSAL_FLASH_DURATION = 10; // ticks

    // ---- state ----
    private float totalBlindShade = 0.0F;
    private float oneEyeShade = 0.0F;
    private float voidSightShade = 0.0F;
    private float blackoutShade = 0.0F;
    private int abyssalFlashTicks = 0;
    private long lastGameTime = 0;

    private BlindnessPowerClient() {
    }

    /** Called once from {@link com.jaymospowers.client.JaymosPowersClient}: registers the PowerClient and HUD element. */
    public static void init() {
        PowerClients.register(BlindnessPower.ID, INSTANCE);
        ClientTickEvents.END_CLIENT_TICK.register(BlindnessPowerClient::tick);
        HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, JaymosPowers.id("blindness_overlay"), BlindnessPowerClient::render);
        BlindnessEcholocationClient.init();
        BlindnessSockets.register();
        ClientPlayNetworking.registerGlobalReceiver(BlindnessEcho.TYPE, (payload, context) ->
                context.client().execute(() -> BlindnessEcholocationClient.onEcho(payload)));
    }

    /** Client tick: update shades from server-synced BlindnessState. */
    private static void tick(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.isPaused()) {
            INSTANCE.resetShades();
            return;
        }
        INSTANCE.updateShades(player);
    }

    private void resetShades() {
        totalBlindShade = 0.0F;
        oneEyeShade = 0.0F;
        voidSightShade = 0.0F;
        blackoutShade = 0.0F;
        if (abyssalFlashTicks > 0) abyssalFlashTicks--;
    }

    private void updateShades(LocalPlayer player) {
        long gameTime = player.level().getGameTime();
        if (gameTime != lastGameTime) {
            lastGameTime = gameTime;
            if (abyssalFlashTicks > 0) abyssalFlashTicks--;
        }

        BlindnessState state = player.getAttached(BlindnessPower.STATE);
        if (state == null) {
            resetShades();
            return;
        }

        // Total blindness: always active when player has the power (fresh awakening or full blindness)
        // For now, total blindness is the base state - the world is dark, only echolocation rings reveal it
        totalBlindShade = Mth.clamp(totalBlindShade + (1.0F - totalBlindShade) * 0.1F, 0.0F, 1.0F);

        // One-eye blind (hold G)
        if (state.isActive(BlindnessState.ONE_EYE)) {
            oneEyeShade = Mth.clamp(oneEyeShade + 0.1F, 0.0F, 1.0F);
        } else {
            oneEyeShade = Mth.clamp(oneEyeShade - 0.05F, 0.0F, 1.0F);
        }

        // Void Sight (ascended C)
        if (state.voidSightActive()) {
            voidSightShade = Mth.clamp(voidSightShade + 0.1F, 0.0F, 1.0F);
        } else {
            voidSightShade = Mth.clamp(voidSightShade - 0.05F, 0.0F, 1.0F);
        }

        // Blackout (corrupted C)
        if (BlindnessBlackoutClient.active(player)) {
            blackoutShade = Mth.clamp(blackoutShade + 0.05F, 0.0F, 1.0F);
        } else {
            blackoutShade = Mth.clamp(blackoutShade - 0.02F, 0.0F, 1.0F);
        }

        // Abyssal Echo flash (both C)
        if (BlindnessAbyssalEchoClient.justActivated(player)) {
            abyssalFlashTicks = ABYSSAL_FLASH_DURATION;
        }
    }

    /** Renders all blindness overlays. */
    private static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        INSTANCE.doRender(graphics);
    }

    private void doRender(GuiGraphicsExtractor graphics) {
        int w = graphics.guiWidth();
        int h = graphics.guiHeight();
        if (w == 0 || h == 0) return;

        // 1. Total blindness vignette (base layer - covers everything)
        if (totalBlindShade > 0.01F) {
            // the devout's dark is thinner, so what lives burns through it, and thinner again from up in Sky Sight
            LocalPlayer self = Minecraft.getInstance().player;
            int full = BlindnessDevoutSight.devout(self)
                    ? (int) Mth.lerp(BlindnessDevoutSight.raised(), DEVOUT_BLIND_ALPHA, SKY_SIGHT_BLIND_ALPHA) : TOTAL_BLIND_ALPHA;
            int alpha = Mth.clamp((int) (full * totalBlindShade), 0, 255);
            int colour = alpha << 24 | TOTAL_BLIND_COLOUR;
            graphics.fill(0, 0, w, h, colour);
        }

        // 2. One-eye blind overlay (left or right half)
        if (oneEyeShade > 0.01F) {
            BlindnessState state = Minecraft.getInstance().player.getAttached(BlindnessPower.STATE);
            if (state != null) {
                boolean blindLeft = state.blindSide() == BlindSide.LEFT;
                int x1 = blindLeft ? 0 : w / 2;
                int x2 = blindLeft ? w / 2 : w;
                int alpha, colour;
                if (state.variant() == BlindnessVariant.BORRADO) {
                    alpha = Mth.clamp((int) (BORRADO_ALPHA * oneEyeShade), 0, 255);
                    colour = alpha << 24 | BORRADO_COLOUR;
                } else { // ESCURO
                    alpha = Mth.clamp((int) (ESCURO_ALPHA * oneEyeShade), 0, 255);
                    colour = alpha << 24 | ESCURO_COLOUR;
                }
                graphics.fill(x1, 0, x2, h, colour);
                // Aim drift indicator: a subtle line toward the blind side
                int driftAlpha = (int) (80 * oneEyeShade);
                int driftColour = driftAlpha << 24 | 0xFFFFFF;
                int cx = w / 2;
                int cy = h / 2;
                int driftX = blindLeft ? cx - 20 : cx + 20;
                graphics.fill(cx - 1, cy - 1, cx + 1, cy + 1, driftColour); // center dot
                graphics.fill(driftX - 1, cy - 1, driftX + 1, cy + 1, driftColour); // drift target
            }
        }

        // 3. Void Sight: entity outlines are rendered in world renderer (not here), but we can add a subtle screen tint
        if (voidSightShade > 0.01F) {
            int alpha = Mth.clamp((int) (VOID_SIGHT_ALPHA * voidSightShade), 0, 255);
            int colour = alpha << 24 | VOID_SIGHT_COLOUR;
            // Subtle full-screen tint
            graphics.fill(0, 0, w, h, colour);
        }

        // 4. Blackout spreading vignette
        if (blackoutShade > 0.01F) {
            int edge = Math.max(24, (int) (h * 0.35F * blackoutShade));
            int alpha = Mth.clamp((int) (BLACKOUT_ALPHA * blackoutShade), 0, 255);
            int solid = alpha << 24 | BLACKOUT_COLOUR;
            graphics.fillGradient(0, 0, w, edge, solid, BLACKOUT_COLOUR);
            graphics.fillGradient(0, h - edge, w, h, BLACKOUT_COLOUR, solid);
            int sideEdge = Math.max(24, (int) (w * 0.25F * blackoutShade));
            graphics.fillGradient(0, 0, sideEdge, h, solid, BLACKOUT_COLOUR);
            graphics.fillGradient(w - sideEdge, 0, w, h, BLACKOUT_COLOUR, solid);
        }

        // 5. Abyssal Echo flash
        if (abyssalFlashTicks > 0) {
            float progress = 1.0F - abyssalFlashTicks / (float) ABYSSAL_FLASH_DURATION;
            int alpha = Mth.clamp((int) (ABYSSAL_FLASH_ALPHA * (1.0F - progress)), 0, 255);
            int colour = alpha << 24 | ABYSSAL_FLASH_COLOUR;
            graphics.fill(0, 0, w, h, colour);
        }

        // 6. Echolocation rings (drawn on top so they're visible through blindness)
        BlindnessEcholocationClient.render(graphics);
    }

    // ---- PowerClient overrides ----

    @Override
    public void tick(Minecraft minecraft, LocalPlayer player) {
        // State updates happen in the static tick callback
    }

    @Override
    public void onActiveChanged(LocalPlayer player, boolean active) {
        if (!active) {
            resetShades();
        }
    }
}