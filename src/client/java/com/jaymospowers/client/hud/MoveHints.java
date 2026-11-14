package com.jaymospowers.client.hud;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.client.LedgeClient;
import com.jaymospowers.client.freerun.FreerunClient;
import com.jaymospowers.client.freerun.moves.LadderMoves;
import com.jaymospowers.client.freerun.moves.RunMovesClient;
import com.jaymospowers.freerun.Freerun;
import com.jaymospowers.freerun.moves.SkyDive;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Move hints (Andy's pick from Smart Key Prompts, OK'd as HUD, no chat): a small prompt in the top-left rail
 * ({@link HudZones#RAIL}, row {@link #ROW}) saying what the keys do right now in a move: "Jump: wall kick", "Sneak: let go"
 * on a wall, "Jump: pull up" on a ledge, and so on. The key is the player's own binding, by its name. It comes up once a
 * move has held the same hints for {@link #SETTLE_TICKS} ticks (so a flick of a move shows nothing) and fades out over
 * {@link #FADE_TICKS} when the move ends. Text is the {@code jaymospowers.hint.*} lang keys ({@code freerun_hints.json}).
 */
public final class MoveHints {
	// ===== the knobs ================================================================================================
	/** The rail row it sits in (under the power badge, kin sense and party). */
	public static final int ROW = 40;
	/** Ticks a set of hints must hold before it shows, and ticks to fade in or out. */
	static final int SETTLE_TICKS = 5, FADE_TICKS = 8;
	/** The most lines at once. */
	static final int MAX_LINES = 3;
	/** Its look: the line height and the padding (gui pixels), and the opacity of the whole prompt (0-1). */
	static final int LINE = 10, PAD = 3;
	static final float OPACITY = 0.8F;
	// ================================================================================================================

	private record Hint(KeyMapping key, String action) {
		String id() {
			return key.getName() + ">" + action;
		}
	}

	/** What's hinted now (as a signature, to see when it changes), what's being shown, and how visible it is (0-1) now and a tick ago. */
	private static String signature = "";
	private static List<Hint> wanted = List.of(), shown = List.of();
	private static int steady;
	private static float fade, lastFade;

	private MoveHints() {
	}

	/** From {@code JaymosPowersClient}. */
	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(MoveHints::tick);
		HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, JaymosPowers.id("move_hints"), MoveHints::draw);
	}

	private static void tick(Minecraft minecraft) {
		lastFade = fade;
		LocalPlayer player = minecraft.player;
		List<Hint> now = player == null || minecraft.gui.screen() != null || !player.isAlive() ? List.of() : hints(minecraft, player);
		StringBuilder sig = new StringBuilder();
		for (Hint hint : now) {
			sig.append(hint.id()).append(';');
		}
		steady = sig.toString().equals(signature) ? steady + 1 : 0;
		signature = sig.toString();
		wanted = now;
		boolean show = !now.isEmpty() && steady >= SETTLE_TICKS;
		if (show) {
			shown = now;
		}
		fade = Mth.clamp(fade + (show ? 1.0F : -1.0F) / FADE_TICKS, 0.0F, 1.0F);
		if (fade <= 0.0F) {
			shown = List.of();
		}
	}

	/** The hints for the move the local player is in (at most {@link #MAX_LINES}); none when in no move. */
	private static List<Hint> hints(Minecraft minecraft, LocalPlayer player) {
		Options options = minecraft.options;
		List<Hint> list = new ArrayList<>();
		int move = FreerunClient.move();
		if (LedgeClient.hanging()) {
			list.add(new Hint(options.keyJump, "pull_up"));
			list.add(new Hint(options.keyShift, "let_go"));
		} else if (LedgeClient.active()) {
			// the pull-up itself: nothing to press
		} else if (move == Freerun.CLING) {
			if (Freerun.perk(player, Freerun.Perk.FREERUNNER) >= 2) {
				list.add(new Hint(options.keyJump, "wall_kick"));
			}
			list.add(new Hint(options.keyShift, "let_go"));
		} else if (move == Freerun.WALL_RUN) {
			list.add(new Hint(options.keyJump, "kick_off"));
		} else if (move == Freerun.UP_RUN) {
			list.add(new Hint(options.keyJump, "kick_back"));
		} else if (move == Freerun.BAR) {
			list.add(new Hint(options.keyJump, "swing_off"));
			list.add(new Hint(options.keyShift, "drop"));
		} else if (move == Freerun.POLE) {
			list.add(new Hint(options.keyJump, "leap_off"));
			list.add(new Hint(options.keyShift, "slide_down"));
		} else if (move == Freerun.SLIDE) {
			list.add(new Hint(options.keyJump, "leap_out"));
		} else if (SkyDive.active(player)) {
			list.add(new Hint(options.keyJump, "fold_up"));
		} else if (player.isSwimming() && player.isInWater() && !player.isPassenger()) {
			if (RunMovesClient.burstReady(player)) {
				list.add(new Hint(options.keySprint, "surge"));
			}
			if (!player.isUnderWater() && RunMovesClient.breachReady(player)) {
				list.add(new Hint(options.keyJump, "breach"));
			}
		} else if (LadderMoves.climbing(player)) {
			list.add(new Hint(options.keyShift, "hold_on"));
		}
		return list.size() > MAX_LINES ? list.subList(0, MAX_LINES) : list;
	}

	private static void draw(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		float visible = Mth.lerp(delta.getGameTimeDeltaPartialTick(false), lastFade, fade) * OPACITY;
		if (visible <= 0.02F || shown.isEmpty()) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		List<Component> lines = new ArrayList<>();
		int width = 0;
		for (Hint hint : shown) {
			Component line = Component.empty().append(hint.key().getTranslatedKeyMessage().copy().withColor(HudStyle.GOLD & 0xFFFFFF))
				.append(Component.literal(": ").withColor(HudStyle.DIM & 0xFFFFFF))
				.append(Component.translatable("jaymospowers.hint." + hint.action()).withColor(HudStyle.TEXT & 0xFFFFFF));
			lines.add(line);
			width = Math.max(width, minecraft.font.width(line));
		}
		int w = width + PAD * 2, h = lines.size() * LINE + PAD * 2 - 1;
		int[] at = HudZones.place(HudZones.RAIL, "jaymospowers:move_hints", ROW, w, h);
		if (at == null) {
			return;
		}
		int alpha = Math.round(255.0F * visible);
		graphics.fill(at[0], at[1], at[0] + w, at[1] + h, HudStyle.PLATE_SOFT & 0xFFFFFF | Math.round(0x60 * visible) << 24);
		int y = at[1] + PAD;
		for (Component line : lines) {
			graphics.text(minecraft.font, line, at[0] + PAD, y, alpha << 24 | 0xFFFFFF, true);
			y += LINE;
		}
	}
}
