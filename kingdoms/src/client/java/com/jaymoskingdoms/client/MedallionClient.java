package com.jaymoskingdoms.client;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.MedallionBoard;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * The Cleric's medallion board on the client ({@link MedallionBoard}): the medallion key lifts and lowers it, and while the
 * server says it is up the number keys 1 to 9 lay its marks and pick no hotbar slot (combat mode's forms and art keys wait).
 * The board is drawn in the middle of the screen: the medallion's face, its rim of marks, the nine sigils (1 2 3 on top), the
 * ones laid lit and joined by crackling strokes; beside it the prayers the Cleric knows, with their patterns, the ones the
 * marks so far could still become bright.
 */
public final class MedallionClient {
	/** The medallion's radius, the gap between its sigils and a sigil's half size (gui pixels). */
	static final int RADIUS = 46, SPACING = 22, NODE = 4;
	/** How many marks go round the rim. */
	static final int RIM_MARKS = 36;
	/** The face, the rim, an unlit sigil, a lit one and its strokes, and the text. */
	static final int FACE = 0x90201810, RIM = 0xFFB08040, UNLIT = 0xFF8A5A2A, LIT = 0xFFFFE9A0, GLOW = 0x60FFE9A0, BRIGHT = 0xFFF0E6C8,
		DIM = 0xFF807060;

	private static KeyMapping key;
	private static boolean up;
	private static String marks = "";

	private MedallionClient() {
	}

	/** Whether the board is up: the number keys are its marks. */
	public static boolean up() {
		return up;
	}

	static void register() {
		key = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymoskingdoms.medallion", InputConstants.KEY_Y, ClassKeys.category()));
		ClientPlayNetworking.registerGlobalReceiver(MedallionBoard.State.TYPE, (payload, context) -> {
			up = payload.up();
			marks = payload.marks();
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, minecraft) -> {
			up = false;
			marks = "";
		});
		ClientTickEvents.START_CLIENT_TICK.register(minecraft -> {
			LocalPlayer player = minecraft.player;
			if (player == null) {
				up = false;
				return;
			}
			if (!ClientPlayNetworking.canSend(MedallionBoard.Raise.TYPE)) {
				return;
			}
			while (key.consumeClick()) {
				ClientPlayNetworking.send(new MedallionBoard.Raise(!up));
			}
			if (!up || minecraft.gui.screen() != null) {
				return;
			}
			for (int i = 0; i < 9; i++) {
				while (minecraft.options.keyHotbarSlots[i].consumeClick()) {
					ClientPlayNetworking.send(new MedallionBoard.Mark(i + 1));
				}
			}
		});
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, JaymosKingdoms.id("medallion_board"), MedallionClient::draw);
	}

	private static void draw(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer self = minecraft.player;
		if (!up || self == null) {
			return;
		}
		int cx = graphics.guiWidth() / 2, cy = graphics.guiHeight() / 2;
		float time = self.tickCount + delta.getGameTimeDeltaPartialTick(false);
		// the face: a filled disc
		for (int dy = -RADIUS; dy <= RADIUS; dy++) {
			int half = (int) Math.sqrt(RADIUS * RADIUS - dy * dy);
			graphics.fill(cx - half, cy + dy, cx + half, cy + dy + 1, FACE);
		}
		// the rim: a ring of marks, every third one longer, turning slowly
		for (int i = 0; i < RIM_MARKS; i++) {
			double a = Math.PI * 2 * i / RIM_MARKS + time * 0.004;
			int length = i % 3 == 0 ? 4 : 2;
			for (int r = RADIUS - length; r <= RADIUS; r++) {
				int x = cx + (int) Math.round(Math.cos(a) * r), y = cy + (int) Math.round(Math.sin(a) * r);
				graphics.fill(x, y, x + 1, y + 1, RIM);
			}
		}
		// the strokes between the marks laid, crackling
		for (int i = 1; i < marks.length(); i++) {
			int[] from = node(cx, cy, marks.charAt(i - 1) - '0'), to = node(cx, cy, marks.charAt(i) - '0');
			line(graphics, from[0], from[1], to[0], to[1], (int) time + i * 7);
		}
		// the nine sigils, numbered as the keys are
		Font font = minecraft.font;
		for (int mark = 1; mark <= 9; mark++) {
			int[] at = node(cx, cy, mark);
			boolean lit = marks.indexOf((char) ('0' + mark)) >= 0;
			if (lit) {
				diamond(graphics, at[0], at[1], NODE + 2, GLOW);
			}
			diamond(graphics, at[0], at[1], NODE, lit ? LIT : UNLIT);
			graphics.text(font, String.valueOf(mark), at[0] - font.width(String.valueOf(mark)) / 2 + 1, at[1] + NODE + 1, lit ? LIT : DIM);
		}
		// beside it, the prayers known and their patterns
		List<String> known = MedallionBoard.known(self);
		int x = cx + RADIUS + 8, y = cy - known.size() * 5;
		for (String spell : known) {
			String pattern = MedallionBoard.PATTERNS.get(spell);
			boolean open = pattern.startsWith(marks);
			graphics.text(font, Component.translatable("class.jaymoskingdoms.option." + spell), x, y, open ? BRIGHT : DIM);
			graphics.text(font, pattern, x + 96, y, open ? LIT : DIM);
			y += 10;
		}
		if (known.isEmpty()) {
			graphics.text(font, Component.translatable("message.jaymoskingdoms.medallion.none"), x, cy - 4, DIM);
		}
	}

	/** Where sigil {@code mark} (1 to 9) sits on the board. */
	private static int[] node(int cx, int cy, int mark) {
		int row = (mark - 1) / 3, col = (mark - 1) % 3;
		return new int[] {cx + (col - 1) * SPACING, cy + (row - 1) * SPACING};
	}

	private static void diamond(GuiGraphicsExtractor graphics, int x, int y, int size, int colour) {
		for (int dy = -size; dy <= size; dy++) {
			int half = size - Math.abs(dy);
			graphics.fill(x - half, y + dy, x + half + 1, y + dy + 1, colour);
		}
	}

	/** A stroke from one sigil to the next, two pixels wide, shaking a little as it goes. */
	private static void line(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int seed) {
		int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
		for (int s = 0; s <= steps; s++) {
			float t = steps == 0 ? 0.0F : (float) s / steps;
			int jitter = (int) (Mth.sin((s + seed) * 1.7F) * 1.5F);
			int x = Math.round(Mth.lerp(t, x0, x1)) + (y0 == y1 ? 0 : jitter), y = Math.round(Mth.lerp(t, y0, y1)) + (y0 == y1 ? jitter : 0);
			graphics.fill(x - 1, y - 1, x + 1, y + 1, LIT);
		}
	}
}
