package com.jaymospowers.client.rig;

import com.jaymospowers.core.wounds.BodyPart;
import com.jaymospowers.core.wounds.Wounds;
import com.jaymospowers.rig.RigLog;
import java.util.ArrayDeque;
import java.util.Deque;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * The visor's combat log ({@link RigLog}) and the body's state, folded out under the Helm's HUD ({@link RigHud}) while blows are
 * flying: the last {@link #SHOWN} blows given and taken, then the worst-hurt part of the body (from Wounds) and how many bones are
 * broken. It folds away {@link #KEEP_MS} after the last blow.
 */
final class RigLogClient {
	/** How many blows it lists, and how long it stays out after the last (ms). */
	static final int SHOWN = 3;
	static final long KEEP_MS = 10_000L;
	/** A part shows on the vitals line under this share of its health. */
	static final float HURT = 0.75F;
	static final int DEALT = 0xFF9FE8FF, TAKEN = 0xFFFF9A8A, VITALS = 0xFFE0D0A0;

	private record Line(RigLog.Entry entry, long at) {
	}

	private static final Deque<Line> LINES = new ArrayDeque<>();

	private RigLogClient() {
	}

	static void init() {
		ClientPlayNetworking.registerGlobalReceiver(RigLog.Entry.TYPE, (payload, context) -> {
			LINES.addFirst(new Line(payload, System.currentTimeMillis()));
			while (LINES.size() > SHOWN) {
				LINES.removeLast();
			}
		});
	}

	/** How many lines the fold-out takes now (0: folded away). */
	static int lines() {
		Line last = LINES.peekFirst();
		return last == null || System.currentTimeMillis() - last.at() > KEEP_MS ? 0 : LINES.size() + 1;
	}

	/** Draws the fold-out from {@code row}; returns the row after it. */
	static int draw(GuiGraphicsExtractor graphics, Font font, LocalPlayer player, int x, int row, int width, int line) {
		if (lines() == 0) {
			return row;
		}
		for (Line l : LINES) {
			RigLog.Entry e = l.entry();
			Component text = Component.translatable(e.dealt() ? "rig.jaymospowers.hud.log.dealt" : "rig.jaymospowers.hud.log.taken", e.who(),
				String.format("%.1f", e.amount()));
			graphics.text(font, RigHud.cut(font, text.getString(), width - 6), x + 3, row, e.dealt() ? DEALT : TAKEN);
			row += line;
		}
		graphics.text(font, RigHud.cut(font, vitals(player).getString(), width - 6), x + 3, row, VITALS);
		return row + line;
	}

	/** The body's state: the worst-hurt part (if any is hurt) and the broken bones. */
	private static Component vitals(LocalPlayer player) {
		@Nullable BodyPart worst = null;
		float low = HURT;
		for (BodyPart part : BodyPart.values()) {
			float f = Wounds.fraction(player, part);
			if (f < low) {
				low = f;
				worst = part;
			}
		}
		int broken = Wounds.brokenLimbs(player);
		Component part = worst == null ? Component.translatable("rig.jaymospowers.hud.vitals.sound")
			: Component.translatable("rig.jaymospowers.hud.vitals.part", Component.translatable(worst.key()), Math.round(low * 100));
		return broken > 0 ? Component.translatable("rig.jaymospowers.hud.vitals.broken", part, broken) : Component.translatable("rig.jaymospowers.hud.vitals", part);
	}
}
