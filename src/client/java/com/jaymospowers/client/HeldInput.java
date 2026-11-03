package com.jaymospowers.client;

import com.jaymospowers.client.finisher.FinisherClient;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * (0.17.129) Andy: "pickpocketing shouldnt keep activating an input while holding, same for any click holds. while there is a
 * bar charging you shouldnt keep swinging". One rule for every hold with a charge bar, ours and Kingdoms' and Kitchen's alike:
 * while one fills, held attack neither swings, hits nor digs, and held use (whose repeats the server counts as the hold going
 * on) still reaches the server but no longer swings the arm every 4 ticks. The lock lasts till both buttons are let go, so
 * the hold that finished the bar doesn't swing straight after either.
 *
 * <p>A charge bar is the action-bar line every hold draws ({@code "|".repeat(filled) + ".".repeat(rest)}: pickpocketing,
 * lifting someone, treating wounds, reviving, finishers, butchering a corpse, Nadir's beacon), so new holds are covered
 * without telling this class about them. Holds timed on the client join through {@link #locksAttack} (the execution's
 * sneak + left-click, {@link FinisherClient#holdsAttack}).
 */
public final class HeldInput {
	/** A bar this many characters long or more (the shortest, lifting someone and finishers, are 8). */
	static final int BAR_MIN = 8;
	/** Holds redraw their bar every tick or two: one not redrawn for this many ticks is over. */
	static final int BAR_LINGER = 5;

	/** A run of {@code |} then {@code .}, nothing else of either beside it. */
	private static final Pattern BAR = Pattern.compile("(?<![|.])\\|*\\.*(?![|.])");

	/** Client ticks so far; the tick the last bar came; whether the hands are the hold's. */
	private static int ticks, barAt = Integer.MIN_VALUE / 2;
	private static boolean locked;

	private HeldInput() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(HeldInput::tick);
	}

	private static void tick(Minecraft minecraft) {
		ticks++;
		boolean held = minecraft.options.keyAttack.isDown() || minecraft.options.keyUse.isDown();
		locked = minecraft.player != null && (barShowing() || locked && held);
	}

	/** An action-bar line came ({@code HeldInputOverlayMixin}): a bar keeps the hold going, anything else ends it. */
	public static void overlay(Component message) {
		if (isBar(message.getString())) {
			barAt = ticks;
			locked = true;
		} else {
			barAt = Integer.MIN_VALUE / 2;
		}
	}

	static boolean isBar(String text) {
		Matcher m = BAR.matcher(text);
		while (m.find()) {
			if (m.end() - m.start() >= BAR_MIN) {
				return true;
			}
		}
		return false;
	}

	private static boolean barShowing() {
		return ticks - barAt <= BAR_LINGER;
	}

	/** Whether a hold has the attack key: no click, held swing (Better Combat's too) or digging. */
	public static boolean locksAttack(Minecraft minecraft) {
		return locked || FinisherClient.holdsAttack(minecraft);
	}

	/** Whether a hold has the use key: its repeats still go to the server, but don't swing the arm. */
	public static boolean locksUseSwing() {
		return locked;
	}
}
