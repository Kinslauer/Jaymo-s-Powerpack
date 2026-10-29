package com.jaymoskingdoms.client;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.VictimMemory;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

/**
 * (0.7.332) Victim memory, seen ({@link VictimMemory.Flash}): on waking, a blurred dark-red silhouette of whoever bit
 * them stands at the centre of the screen for {@link #TICKS} ticks, fading in and out. It is the biter's skin (found in the
 * tab list by UUID, or the default skin of that UUID if they're not online) drawn as a flat figure (head, body, arms and legs)
 * tinted {@link #RED}, three times over with small offsets for the blur. No name.
 */
public final class VictimClient {
	/** How long it shows (ticks), and how long it takes to fade in and out. */
	static final int TICKS = 60, FADE = 15;
	/** Its tint (red, green, blue; 0-1) and strongest alpha. */
	static final float RED = 0.55F, GREEN = 0.03F, BLUE = 0.03F, ALPHA = 0.7F;
	/** Its height as a share of the screen's, and the blur's reach (skin pixels). */
	static final float HEIGHT = 0.55F, BLUR = 0.6F;
	/** The wake-up thump's volume and pitch. */
	static final float THUMP_VOLUME = 0.6F, THUMP_PITCH = 0.5F;

	private static final int SKIN = 64;

	private static Identifier skin;
	private static int left;

	private VictimClient() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(VictimMemory.Flash.TYPE, (payload, context) -> {
			skin = skinOf(context.client(), payload.biter());
			left = TICKS;
			context.client().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.WARDEN_HEARTBEAT, THUMP_PITCH, THUMP_VOLUME));
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> left = 0);
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (left > 0 && !client.isPaused()) {
				left--;
			}
		});
		HudElementRegistry.attachElementBefore(VanillaHudElements.MISC_OVERLAYS, JaymosKingdoms.id("victim_memory"), VictimClient::hud);
	}

	/** The biter's skin: from the tab list if they're online, else the default skin their UUID would get. */
	private static Identifier skinOf(Minecraft minecraft, UUID id) {
		PlayerInfo info = minecraft.getConnection() == null ? null : minecraft.getConnection().getPlayerInfo(id);
		return (info != null ? info.getSkin() : DefaultPlayerSkin.get(id)).body().texturePath();
	}

	private static void hud(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		if (left <= 0 || skin == null) {
			return;
		}
		float t = left - delta.getGameTimeDeltaPartialTick(false);
		float fade = Mth.clamp(Math.min(t, TICKS - t) / FADE, 0.0F, 1.0F);
		if (fade <= 0.0F) {
			return;
		}
		int k = Math.max(1, Math.round(graphics.guiHeight() * HEIGHT / 32.0F));
		int cx = graphics.guiWidth() / 2 - 4 * k, top = graphics.guiHeight() / 2 - 16 * k;
		int reach = Math.max(1, Math.round(BLUR * k));
		// (the blur: the figure drawn at three offsets, the middle one strongest)
		figure(graphics, cx - reach, top, k, fade * 0.25F);
		figure(graphics, cx + reach, top, k, fade * 0.25F);
		figure(graphics, cx, top, k, fade);
	}

	/** Head, body, arms and legs (the front of each, from the skin's layout), each skin pixel {@code k} screen pixels wide. */
	private static void figure(GuiGraphicsExtractor g, int x, int y, int k, float strength) {
		int color = ARGB.colorFromFloat(ALPHA * strength, RED, GREEN, BLUE);
		part(g, color, x, y, k, 8, 8, 8, 8);
		part(g, color, x, y + 8 * k, k, 20, 20, 8, 12);
		part(g, color, x - 4 * k, y + 8 * k, k, 44, 20, 4, 12);
		part(g, color, x + 8 * k, y + 8 * k, k, 36, 52, 4, 12);
		part(g, color, x, y + 20 * k, k, 4, 20, 4, 12);
		part(g, color, x + 4 * k, y + 20 * k, k, 20, 52, 4, 12);
	}

	private static void part(GuiGraphicsExtractor g, int color, int x, int y, int k, int u, int v, int w, int h) {
		g.blit(RenderPipelines.GUI_TEXTURED, skin, x, y, (float) u, (float) v, w * k, h * k, w, h, SKIN, SKIN, color);
	}
}
