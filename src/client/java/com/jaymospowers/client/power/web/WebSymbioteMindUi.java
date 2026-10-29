package com.jaymospowers.client.power.web;

import com.jaymospowers.client.UiStyle;
import com.jaymospowers.power.web.WebSymbiote;
import com.jaymospowers.power.web.WebSymbioteMind;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * (0.17.150) The symbiote's mind on the client ({@link WebSymbioteMind}): the radial's Mind chip (which opens the Status
 * page, {@link WebSymbioteStatusScreen}), the stage and mood in the radial's heading, and the colour of the screen's edge
 * in each mood (Andy: "A mood with its own edge colour and voice"), which {@link WebSymbioteDread} asks for.
 */
final class WebSymbioteMindUi {
	/** The radial's pick value for the Mind chip (the abilities are 0 and up). */
	static final int PICK = -2;
	/** The chip: left of the mask, under the bond line (offsets from the radial's centre, and size). */
	private static final int CHIP_X = -36, CHIP_Y = 22, CHIP_W = 27, CHIP_H = 10;
	private static final int ACCENT = 0xFF8A5AB0;

	/** The edge in each mood (indexed by {@link WebSymbioteMind.Mood#ordinal()}): the vignette, and the tendrils' glow from faint to urgent. */
	private static final int[] VIGNETTE = {0x0A2A28, 0x2E2606, 0x3A0808, 0x0A1030};
	private static final int[] GLOW_FAINT = {0x2E8F82, 0x8F7A2E, 0x8F2E2E, 0x2E3F8F};
	private static final int[] GLOW_URGENT = {0x7CFFE8, 0xFFE27C, 0xFF6A6A, 0x7C9CFF};
	/** The text colour of each mood on the Status page. */
	static final int[] TEXT = {0xFF7CFFE8, 0xFFFFE27C, 0xFFFF6A6A, 0xFF9CB4FF};
	/** The faint steady edge each mood keeps even when it isn't speaking (0-1 of a line's strength); content keeps none. */
	private static final float[] EDGE = {0.0F, 0.14F, 0.22F, 0.12F};

	private WebSymbioteMindUi() {
	}

	/** The bonded local player's mood, or null (not bonded, or no mind synced yet). */
	private static WebSymbioteMind.Mood mood() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || !WebSymbiote.isBonded(player) || player.getAttached(WebSymbioteMind.MIND) == null) {
			return null;
		}
		return WebSymbioteMind.mood(player);
	}

	/** The screen edge's vignette colour: {@code base} unless it has a mood. */
	static int vignette(int base) {
		WebSymbioteMind.Mood mood = mood();
		return mood == null ? base : VIGNETTE[mood.ordinal()];
	}

	/** A tendril glow colour ({@code urgent}: the bright end): {@code base} unless it has a mood. */
	static int glow(int base, boolean urgent) {
		WebSymbioteMind.Mood mood = mood();
		return mood == null ? base : (urgent ? GLOW_URGENT : GLOW_FAINT)[mood.ordinal()];
	}

	/** How strongly its mood keeps the edge lit between lines (0 for none). */
	static float edge() {
		WebSymbioteMind.Mood mood = mood();
		return mood == null ? 0.0F : EDGE[mood.ordinal()];
	}

	static boolean overChip(int mouseX, int mouseY, int cx, int cy) {
		return UiStyle.over(mouseX, mouseY, cx + CHIP_X - 1, cy + CHIP_Y - 1, CHIP_W + 2, CHIP_H + 2);
	}

	/** The little Mind chip in the radial, lit under the mouse. */
	static void drawChip(GuiGraphicsExtractor graphics, Font font, int cx, int cy, boolean picked) {
		int x = cx + CHIP_X;
		int y = cy + CHIP_Y;
		WebSymbioteMind.Mood mood = mood();
		int tint = mood == null ? ACCENT : TEXT[mood.ordinal()];
		graphics.fill(x, y, x + CHIP_W, y + CHIP_H, picked ? UiStyle.ROW_HOVER : 0xFF1A1620);
		graphics.outline(x - 1, y - 1, CHIP_W + 2, CHIP_H + 2, picked ? ACCENT : tint & 0x80FFFFFF);
		Component label = Component.translatable("gui.jaymospowers.web_symbiote.status_chip");
		graphics.text(font, label, x + CHIP_W / 2 - font.width(label) / 2, y + 1, tint);
	}

	/** What the radial's panel says with nothing picked: the bond's stage and its mood, in place of the plain title. */
	static Component heading(Player player, Component fallback) {
		if (!WebSymbiote.isBonded(player)) {
			return fallback;
		}
		WebSymbioteMind.Mind mind = WebSymbioteMind.mind(player);
		return Component.translatable("gui.jaymospowers.web_symbiote.heading",
			Component.translatable("gui.jaymospowers.web_symbiote.stage." + WebSymbioteMind.stageName(WebSymbiote.state(player).bond())),
			Component.translatable("gui.jaymospowers.web_symbiote.mood." + mind.moodNow().key()));
	}

	static void open() {
		Minecraft minecraft = Minecraft.getInstance();
		minecraft.gui.setScreen(new WebSymbioteStatusScreen());
	}
}
