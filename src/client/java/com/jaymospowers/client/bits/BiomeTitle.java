package com.jaymospowers.client.bits;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.client.hud.HudStyle;
import com.jaymospowers.client.hud.HudZones;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.locale.Language;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.core.Holder;
import org.jspecify.annotations.Nullable;

/**
 * Biome name: walking into a new biome puts its name, small, in the top-left corner (the HUD's rail) for {@link #SHOW_MS}, at
 * most once per biome per {@link #REPEAT_MS}. Just the biome's own name (nothing a pack's text adds), and only for a biome
 * that has one in the language file. A biome has to hold for {@link #SETTLE_TICKS} before it shows, so a step across a border
 * shows nothing. The lookup is the plain one at your feet, so One Dimension's stacked bands name whichever band you stand in.
 */
public final class BiomeTitle {
	// ===== the knobs ================================================================================================
	/** How long the name stays (ms), and how long before the same biome may show it again. */
	static final long SHOW_MS = 3000L, REPEAT_MS = 5L * 60L * 1000L;
	/** Fade in and out (ms). */
	static final long FADE_MS = 400L;
	/** Ticks a new biome must hold before it shows. */
	static final int SETTLE_TICKS = 20;
	/** The rail row (above the power badge) and the plate's padding. */
	static final int ROW = 5, PAD = 3;
	// ================================================================================================================

	/** The biome stood in now and the ticks it has held; the one last shown, so it isn't asked for again while you stay in it. */
	private static @Nullable Identifier current, last;
	private static int held;
	/** Biome to the clock when its name last showed. */
	private static final Map<Identifier, Long> SHOWN = new HashMap<>();
	private static @Nullable Component name;
	private static long since;

	private BiomeTitle() {
	}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(BiomeTitle::tick);
		ClientPlayConnectionEvents.JOIN.register((handler, sender, minecraft) -> reset());
		HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, JaymosPowers.id("biome_title"), BiomeTitle::draw);
	}

	private static void reset() {
		current = null;
		last = null;
		held = 0;
		name = null;
		SHOWN.clear();
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.level == null) {
			return;
		}
		Holder<Biome> biome = minecraft.level.getBiome(player.blockPosition());
		Identifier id = biome.unwrapKey().map(key -> key.identifier()).orElse(null);
		if (id == null) {
			return;
		}
		if (!id.equals(current)) {
			current = id;
			held = 0;
			return;
		}
		if (held < SETTLE_TICKS) {
			held++;
			return;
		}
		if (id.equals(last)) {
			return;
		}
		last = id;
		long now = Util.getMillis();
		Long before = SHOWN.get(id);
		String key = id.toLanguageKey("biome");
		if ((before == null || now - before >= REPEAT_MS) && Language.getInstance().has(key)) {
			SHOWN.put(id, now);
			name = Component.translatable(key);
			since = now;
		}
	}

	private static void draw(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		if (name == null) {
			return;
		}
		long age = Util.getMillis() - since;
		if (age >= SHOW_MS) {
			name = null;
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		float visible = Mth.clamp(Math.min(age, SHOW_MS - age) / (float)FADE_MS, 0.0F, 1.0F);
		int w = minecraft.font.width(name) + PAD * 2, h = minecraft.font.lineHeight + PAD * 2 - 1;
		int[] at = HudZones.place(HudZones.RAIL, "jaymospowers:biome_title", ROW, w, h);
		if (at == null) {
			return;
		}
		graphics.fill(at[0], at[1], at[0] + w, at[1] + h, HudStyle.PLATE_SOFT & 0xFFFFFF | Math.round(0x60 * visible) << 24);
		int alpha = Math.max(4, Math.round(255.0F * visible));
		graphics.text(minecraft.font, name, at[0] + PAD, at[1] + PAD, alpha << 24 | 0xFFFFFF, true);
	}
}
