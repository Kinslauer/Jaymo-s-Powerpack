package com.jaymospowers.client.power.web;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.client.hud.HudStyle;
import com.jaymospowers.client.hud.HudZones;
import com.jaymospowers.power.web.WebStamina;
import com.jaymospowers.power.web.WebSymbiote;
import com.jaymospowers.power.web.WebSymbioteMind;
import com.jaymospowers.power.web.WebSymbioteState;
import com.jaymospowers.power.web.WebSymbioteWords;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The symbiote's bits of HUD. Beside the hotbar (the left dock, with the arm's chips): a small bond pip filled to the
 * bond in the strain's colour with a socket in the middle lit in its mood's colour, its hunger bar, the tendrils'
 * stamina bar ({@link WebStamina}) and the icon of the standing order it holds. And its words ({@link WebSymbioteWords}):
 * low and centred on a dark plate, subtitle style, fading out, in place of the action bar. All of it goes with F1.
 */
final class WebSymbioteHud {
	/** The block's size and where it sits in the left dock (its row). */
	private static final int PIP = 12, BAR_W = 40, ICON = 16, GAP = 3, ROW = 12, HEIGHT = 16;
	private static final int HUNGER_COLOR = 0xFFB07AD0, STARVING_COLOR = 0xFFB02030, STAMINA_COLOR = 0xFFD0A040, TIRED_COLOR = 0xFF8A5A30;
	private static final int PIP_BACK = 0xC0100818, PIP_RIM = 0xFF5A2A80;
	/** The plate: widest it gets (gui pixels), the padding round the text, the line height, and the bottom's distance from the screen's bottom. */
	private static final int PLATE_MAX_W = 260, PLATE_PAD = 4, PLATE_LINE = 10, PLATE_BOTTOM = 70;
	private static final int PLATE_FILL = 0xB0000000, PLATE_RIM = 0x60404050;
	/** How long a line stays: a base, and more for each character (ms), the cap, and the fade in and out. */
	private static final long PLATE_BASE_MS = 2500L, PLATE_CHAR_MS = 45L, PLATE_MAX_MS = 9000L, PLATE_FADE_IN_MS = 150L, PLATE_FADE_OUT_MS = 700L;

	private static Component words;
	private static long wordsAt;
	private static final ItemStack SHIELD = new ItemStack(Items.SHIELD), SWORD = new ItemStack(Items.IRON_SWORD), CARPET = new ItemStack(Items.ENDER_EYE);

	private WebSymbioteHud() {
	}

	static void init() {
		ClientPlayNetworking.registerGlobalReceiver(WebSymbioteWords.Words.TYPE, (payload, context) -> {
			words = payload.text();
			wordsAt = Util.getMillis();
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> words = null);
		WebSymbioteSettingsClient.init();
		WebStageCards.init();
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, JaymosPowers.id("web_symbiote_pip"), WebSymbioteHud::pip);
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, JaymosPowers.id("web_symbiote_words"), WebSymbioteHud::plate);
	}

	/** The strain's colour for its pip and cards (ARGB). */
	static int strainColor(WebSymbioteState state) {
		return switch (state.kind()) {
			case WHITE -> 0xFFF0EEE6;
			case RED -> 0xFFD02030;
			default -> 0xFFB070E0;
		};
	}

	private static void pip(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || player.isSpectator() || !WebSymbiote.isBonded(player)) {
			return;
		}
		WebSymbioteState state = WebSymbiote.state(player);
		int[] at = HudZones.place(HudZones.DOCK_LEFT, "jaymospowers:symbiote_pip", ROW, PIP + GAP + BAR_W + GAP + ICON, HEIGHT);
		if (at == null) {
			return;
		}
		int x = at[0];
		int y = at[1];
		// the bond pip: filled from the bottom to the bond, a socket in the middle in the mood's colour
		graphics.fill(x, y + 2, x + PIP, y + 2 + PIP, PIP_BACK);
		int filled = Math.round((PIP - 2) * Mth.clamp(state.bond() / (float)WebSymbiote.BOND_MAX, 0.0F, 1.0F));
		graphics.fill(x + 1, y + 2 + PIP - 1 - filled, x + PIP - 1, y + 2 + PIP - 1, strainColor(state));
		graphics.outline(x, y + 2, PIP, PIP, PIP_RIM);
		int mood = moodColor(player);
		graphics.fill(x + 3, y + 5, x + PIP - 3, y + PIP - 1, 0xFF000000);
		graphics.fill(x + 4, y + 6, x + PIP - 4, y + PIP - 2, mood);
		// hunger and stamina, thin bars side by side with the pip
		int bx = x + PIP + GAP;
		HudStyle.bar(graphics, bx, y + 4, BAR_W, 3, Mth.clamp(state.satiety() / (float)WebSymbiote.SATIETY_MAX, 0.0F, 1.0F),
			state.satiety() <= WebSymbiote.STARVING ? STARVING_COLOR : HUNGER_COLOR);
		HudStyle.bar(graphics, bx, y + 11, BAR_W, 3, WebStamina.get(player) / (float)WebStamina.MAX, WebStamina.tired(player) ? TIRED_COLOR : STAMINA_COLOR);
		// the standing order it holds
		ItemStack icon = orderIcon(player);
		if (icon != null) {
			graphics.item(icon, bx + BAR_W + GAP, y);
		}
	}

	private static int moodColor(LocalPlayer player) {
		if (player.getAttached(WebSymbioteMind.MIND) == null) {
			return 0xFF707080;
		}
		return WebSymbioteMindUi.TEXT[WebSymbioteMind.mood(player).ordinal()];
	}

	private static ItemStack orderIcon(LocalPlayer player) {
		if (player.getAttached(WebSymbioteMind.MIND) == null) {
			return null;
		}
		return switch (WebSymbioteMind.mind(player).order()) {
			case WebSymbioteMind.PROTECT -> SHIELD;
			case WebSymbioteMind.HUNT -> SWORD;
			case WebSymbioteMind.HIDE -> CARPET;
			default -> null;
		};
	}

	/** Its latest line, on a plate low and centred, fading in and out. */
	private static void plate(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		if (words == null) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		Font font = minecraft.font;
		int length = words.getString().length();
		long life = Math.min(PLATE_MAX_MS, PLATE_BASE_MS + length * PLATE_CHAR_MS);
		long age = Util.getMillis() - wordsAt;
		if (age >= life) {
			words = null;
			return;
		}
		float alpha = Math.min(Mth.clamp(age / (float)PLATE_FADE_IN_MS, 0.0F, 1.0F), Mth.clamp((life - age) / (float)PLATE_FADE_OUT_MS, 0.0F, 1.0F));
		int text = (int)(255 * alpha);
		if (text < 8) {
			return;
		}
		int width = Math.min(PLATE_MAX_W, graphics.guiWidth() - 40);
		List<FormattedCharSequence> lines = font.split(words, width);
		int widest = 0;
		for (FormattedCharSequence line : lines) {
			widest = Math.max(widest, font.width(line));
		}
		int w = widest + PLATE_PAD * 2;
		int h = lines.size() * PLATE_LINE + PLATE_PAD * 2 - 1;
		int x = (graphics.guiWidth() - w) / 2;
		int y = graphics.guiHeight() - PLATE_BOTTOM - h;
		graphics.fill(x, y, x + w, y + h, fade(PLATE_FILL, alpha));
		graphics.outline(x, y, w, h, fade(PLATE_RIM, alpha));
		for (int i = 0; i < lines.size(); i++) {
			FormattedCharSequence line = lines.get(i);
			graphics.text(font, line, (graphics.guiWidth() - font.width(line)) / 2, y + PLATE_PAD + i * PLATE_LINE, text << 24 | 0xFFFFFF, true);
		}
	}

	private static int fade(int argb, float alpha) {
		return ((int)((argb >>> 24) * alpha)) << 24 | (argb & 0xFFFFFF);
	}
}
