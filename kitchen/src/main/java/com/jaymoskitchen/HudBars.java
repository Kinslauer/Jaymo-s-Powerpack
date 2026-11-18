package com.jaymoskitchen;

import java.util.function.Consumer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * Progress bars drawn like the execution bars instead of text on the action bar: Jaymo's Powers draws them (its
 * {@code core.HudBar}, which says what each kind looks like). The mods don't depend on each other, so a bar goes to it
 * through Fabric Loader's object share; without it, the old kind of line goes on the action bar. Kingdoms has the same class
 * ({@code com.jaymoskingdoms.HudBars}): keep the copies the same.
 */
public final class HudBars {
	public static final int HOLD = 0, GAUGE = 1, MARKS = 2, TUG = 3;
	public static final int RED = 0, GREY = 1, GOLD = 2, PURPLE = 3, AQUA = 4, GREEN = 5, DARK_RED = 6, YELLOW = 7;
	private static final String SHARE = "jaymos:hud_bar";

	private HudBars() {
	}

	/** Something being done, {@code fill} of the way; {@code locks}: it has the hands (no swinging till let go). */
	public static void hold(ServerPlayer player, String id, float fill, int tint, @Nullable Component caption, boolean locks) {
		send(player, id, HOLD, tint, 0, locks, fill, caption, null);
	}

	/** A level, {@code fill} of full, with its label and maybe a number. */
	public static void gauge(ServerPlayer player, String id, float fill, int tint, Component label, @Nullable Component number) {
		send(player, id, GAUGE, tint, 0, false, fill, label, number);
	}

	/** {@code filled} of {@code of} marks. */
	public static void marks(ServerPlayer player, String id, int filled, int of, int tint, @Nullable Component caption) {
		send(player, id, MARKS, tint, of, false, of <= 0 ? 1.0F : filled / (float) of, caption, null);
	}

	/** A contest: {@code lead} of the bar is yours (0.5 even); {@code them}, the other side. */
	public static void tug(ServerPlayer player, String id, float lead, Component them) {
		send(player, id, TUG, GOLD, 0, true, lead, them, null);
	}

	@SuppressWarnings("unchecked")
	private static void send(ServerPlayer player, String id, int kind, int tint, int count, boolean locks, float fill,
			@Nullable Component caption, @Nullable Component number) {
		fill = Math.max(0.0F, Math.min(1.0F, fill));
		Object sender = FabricLoader.getInstance().getObjectShare().get(SHARE);
		if (sender instanceof Consumer<?> consumer) {
			int style = kind | tint << 3 | Math.min(255, count) << 7 | (locks ? 1 << 15 : 0);
			((Consumer<Object[]>) consumer).accept(new Object[] {player, id, style, fill, caption, number});
			return;
		}
		int n = count > 0 ? count : 10, filled = Math.round(fill * n);
		MutableComponent line = caption == null ? Component.empty() : caption.copy().append(" ");
		line.append(Component.literal("■".repeat(filled)).withStyle(ChatFormatting.GOLD))
			.append(Component.literal("□".repeat(n - filled)).withStyle(ChatFormatting.DARK_GRAY));
		if (number != null) {
			line.append(" ").append(number);
		}
		player.sendOverlayMessage(line);
	}
}
