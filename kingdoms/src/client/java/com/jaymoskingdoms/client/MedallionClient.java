package com.jaymoskingdoms.client;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.MedallionBoard;
import com.jaymoskingdoms.client.mixin.MedallionKeyMappingAccessor;
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
 * server says it is up the numpad's 1 to 9 lay its marks (rebindable, under the class keys). The board is drawn in the middle
 * of the screen laid out as the pad is (7 8 9 on top): the medallion's face, its rim of marks, nine sigils each its own, the
 * ones laid lit and joined by crackling strokes; beside it the prayers the Cleric knows, each with its shape drawn small, the
 * ones the marks so far could still become bright.
 */
public final class MedallionClient {
	/** The medallion's radius, the gap between its sigils, a sigil's half size, and the small pattern's gap (gui pixels). */
	static final int RADIUS = 46, SPACING = 22, NODE = 6, MINI = 5;
	/** How many marks go round the rim. */
	static final int RIM_MARKS = 36;
	/** The face, the rim, an unlit sigil, a lit one and its strokes, its glow, and the text. */
	static final int FACE = 0x90201810, RIM = 0xFFB08040, UNLIT = 0xFF8A5A2A, LIT = 0xFFFFE9A0, GLOW = 0x60FFE9A0, BRIGHT = 0xFFF0E6C8,
		DIM = 0xFF807060;
	/**
	 * The nine sigils, one for each mark (1 to 9, the numpad's keys), each 9 by 9: the root, the cup, the gate (the bottom
	 * row); the flame, the eye, the waters (the middle); the star, the crescent, the crown (the top).
	 */
	private static final String[][] SIGILS = {
		{"....#....", "....#....", "....#....", "....#....", "..#.#.#..", ".#..#..#.", "#...#...#", "#.......#", "........."},
		{"#.......#", "#.......#", ".#.....#.", ".#.....#.", "..#####..", "....#....", "....#....", "...###...", "........."},
		{".#######.", ".#.....#.", ".#.....#.", ".#..#..#.", ".#.###.#.", ".#..#..#.", ".#.....#.", ".#.....#.", "........."},
		{"....#....", "...#.#...", "...#.#...", "..#...#..", "..#.#.#..", ".#..#..#.", ".#.....#.", "..#####..", "........."},
		{".........", "...###...", ".##...##.", "#...#...#", "#..###..#", "#...#...#", ".##...##.", "...###...", "........."},
		{".........", "..#...#..", ".#.#.#.#.", "#...#...#", ".........", "..#...#..", ".#.#.#.#.", "#...#...#", "........."},
		{"....#....", ".#..#..#.", "..#.#.#..", "...###...", "#########", "...###...", "..#.#.#..", ".#..#..#.", "....#...."},
		{"...###...", ".##......", ".#.......", "#........", "#........", "#........", ".#.......", ".##......", "...###..."},
		{".........", "#...#...#", "##.###.##", "#.#####.#", "#.......#", "#########", ".........", "..#.#.#..", "........."}};

	private static KeyMapping key;
	private static final KeyMapping[] MARKS = new KeyMapping[9];
	/** The marks held down: a held key lays one mark, its repeats none. */
	private static final boolean[] HELD = new boolean[9];
	private static boolean up;
	private static String marks = "";

	private MedallionClient() {
	}

	/** The nine mark keys. */
	public static KeyMapping[] marks() {
		return MARKS;
	}

	/**
	 * Whether the board is up and {@code key} is on the numpad: then only the marks hear it, no other binding (Andy: "all numpad
	 * binds should be disabled while the cleric board is active"; {@code MedallionNumpadMixin}).
	 */
	public static boolean holdsPad(InputConstants.Key key) {
		if (!up || key.getType() != InputConstants.Type.KEYBOARD) {
			return false;
		}
		int code = key.getValue();
		// the keypad's scancodes: divide to period, equals, comma, and the rarer keypad keys
		return code >= 84 && code <= 99 || code == 103 || code == 133 || code >= 176 && code <= 221;
	}

	/**
	 * Whether {@code mapping}, bound to {@code bound}, is silenced by the board: the board is up, the key is on the numpad, and
	 * the mapping isn't one of the marks or the medallion key itself. It then reads as not held and its presses are thrown away ({@code MedallionNumpadMixin}).
	 */
	public static boolean silenced(KeyMapping mapping, InputConstants.Key bound) {
		if (!up || bound == null || mapping == key || !holdsPad(bound)) {
			return false;
		}
		for (KeyMapping mark : MARKS) {
			if (mark == mapping) {
				return false;
			}
		}
		return true;
	}

	/** While the board is up, throws away the presses waiting on the numpad's other bindings, so none fire once it is lowered. */
	private static void silencePad() {
		for (KeyMapping mapping : MedallionKeyMappingAccessor.jaymoskingdoms$all().values()) {
			MedallionKeyMappingAccessor m = (MedallionKeyMappingAccessor) mapping;
			if (m.jaymoskingdoms$clicks() > 0 && silenced(mapping, m.jaymoskingdoms$key())) {
				m.jaymoskingdoms$setClicks(0);
			}
		}
	}

	static void register() {
		key = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymoskingdoms.medallion", InputConstants.KEY_Y, ClassKeys.category()));
		int[] pad = {InputConstants.KEY_NUMPAD1, InputConstants.KEY_NUMPAD2, InputConstants.KEY_NUMPAD3, InputConstants.KEY_NUMPAD4, InputConstants.KEY_NUMPAD5,
			InputConstants.KEY_NUMPAD6, InputConstants.KEY_NUMPAD7, InputConstants.KEY_NUMPAD8, InputConstants.KEY_NUMPAD9};
		for (int i = 0; i < 9; i++) {
			MARKS[i] = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymoskingdoms.medallion_mark_" + (i + 1), pad[i], ClassKeys.category()));
		}
		ClientPlayNetworking.registerGlobalReceiver(MedallionBoard.State.TYPE, (payload, context) -> {
			up = payload.up();
			marks = payload.marks();
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, minecraft) -> {
			up = false;
			marks = "";
		});
		ClientTickEvents.START_CLIENT_TICK.register(minecraft -> {
			if (minecraft.player == null || !ClientPlayNetworking.canSend(MedallionBoard.Raise.TYPE)) {
				up = false;
				return;
			}
			while (key.consumeClick()) {
				ClientPlayNetworking.send(new MedallionBoard.Raise(!up));
			}
			if (up) {
				silencePad();
			}
			for (int i = 0; i < 9; i++) {
				boolean clicked = false;
				while (MARKS[i].consumeClick()) {
					clicked = true;
				}
				if (up && clicked && !HELD[i] && minecraft.gui.screen() == null) {
					ClientPlayNetworking.send(new MedallionBoard.Mark(i + 1));
				}
				HELD[i] = MARKS[i].isDown();
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
			int[] from = node(cx, cy, SPACING, marks.charAt(i - 1) - '0'), to = node(cx, cy, SPACING, marks.charAt(i) - '0');
			line(graphics, from[0], from[1], to[0], to[1], (int) time + i * 7);
		}
		// the nine sigils, laid out as the numpad
		for (int mark = 1; mark <= 9; mark++) {
			int[] at = node(cx, cy, SPACING, mark);
			boolean lit = marks.indexOf((char) ('0' + mark)) >= 0;
			if (lit) {
				diamond(graphics, at[0], at[1], NODE + 2, GLOW);
			}
			sigil(graphics, at[0], at[1], mark, lit ? LIT : UNLIT);
		}
		// beside it, the prayers known, each with its shape
		Font font = minecraft.font;
		List<String> known = MedallionBoard.known(self);
		int x = cx + RADIUS + 10, y = cy - known.size() * 7;
		for (String spell : known) {
			String pattern = MedallionBoard.pattern(spell);
			boolean open = pattern.startsWith(marks);
			mini(graphics, x + MINI, y + MINI, pattern, open ? LIT : DIM);
			graphics.text(font, MedallionBoard.name(spell), x + 3 * MINI + 4, y + 2, open ? BRIGHT : DIM);
			y += 14;
		}
		if (known.isEmpty()) {
			graphics.text(font, Component.translatable("message.jaymoskingdoms.medallion.none"), x, cy - 4, DIM);
		}
	}

	/** Where mark {@code mark} (1 to 9) sits on a board centred on ({@code cx}, {@code cy}): as the numpad, 7 8 9 on top. */
	private static int[] node(int cx, int cy, int spacing, int mark) {
		int row = 2 - (mark - 1) / 3, col = (mark - 1) % 3;
		return new int[] {cx + (col - 1) * spacing, cy + (row - 1) * spacing};
	}

	private static void sigil(GuiGraphicsExtractor graphics, int x, int y, int mark, int colour) {
		String[] rows = SIGILS[mark - 1];
		for (int r = 0; r < rows.length; r++) {
			for (int c = 0; c < rows[r].length(); c++) {
				if (rows[r].charAt(c) == '#') {
					graphics.fill(x - 4 + c, y - 4 + r, x - 3 + c, y - 3 + r, colour);
				}
			}
		}
	}

	/** A prayer's shape drawn small: its path over the nine points, its first mark larger. */
	private static void mini(GuiGraphicsExtractor graphics, int cx, int cy, String pattern, int colour) {
		for (int mark = 1; mark <= 9; mark++) {
			int[] at = node(cx, cy, MINI, mark);
			graphics.fill(at[0], at[1], at[0] + 1, at[1] + 1, DIM);
		}
		for (int i = 1; i < pattern.length(); i++) {
			int[] from = node(cx, cy, MINI, pattern.charAt(i - 1) - '0'), to = node(cx, cy, MINI, pattern.charAt(i) - '0');
			int steps = Math.max(Math.abs(to[0] - from[0]), Math.abs(to[1] - from[1]));
			for (int s = 0; s <= steps; s++) {
				float t = (float) s / steps;
				int px = Math.round(Mth.lerp(t, from[0], to[0])), py = Math.round(Mth.lerp(t, from[1], to[1]));
				graphics.fill(px, py, px + 1, py + 1, colour);
			}
		}
		int[] first = node(cx, cy, MINI, pattern.charAt(0) - '0');
		graphics.fill(first[0] - 1, first[1] - 1, first[0] + 2, first[1] + 2, colour);
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
