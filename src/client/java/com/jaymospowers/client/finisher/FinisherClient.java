package com.jaymospowers.client.finisher;

import com.jaymospowers.core.finisher.Finishers;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import com.jaymospowers.JaymosPowers;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;

/**
 * (0.17.7) The execution's hold is sneak + left-click's ({@link Finishers}): while both are held with the crosshair on someone,
 * the server is told who (once, and again when it changes or is let go: {@link Finishers.Execute}). The server times it
 * and decides whether they're down for it. Holding attack on an entity does nothing else in vanilla.
 * <p>
 * (0.17.97) Andy: "stop attacking while charging an execution". Better Combat swings again and again while attack is held,
 * and the click that started the hold was a blow of its own, so the charge took swings at the body it was meant to finish.
 * Now the hands are the hold's ({@link #holdsAttack}, from {@code HeldInputMixin} by way of {@link com.jaymospowers.client.HeldInput}): no click, held swing or digging
 * while you're sneaking with the crosshair on someone you could finish, nor while the finisher or execution plays.
 * <p>
 * (0.19.57) Andy: "it should be 3 bars that fill, and once the third is filled the execution plays ... maybe we try a double
 * left click as the override?", then "the execution should be activated by a double click and HOLD, no sneak, and the
 * execution can be cancelled if you let go of the mouse early". The hold begins on the second of two presses of attack
 * within {@link #DOUBLE_CLICK_TICKS} on the same helpless one ({@link #armed}), and lasts while that press is held; let go
 * and it's off. The first press is a blow as ever (sneaking at them, it isn't: the old way, and Quiet Word's choke still
 * wants sneak); the second is the hold's. The server's count ({@link Finishers.Charge}) is drawn as
 * {@link Finishers#HOLD_BARS} bars under the crosshair ({@link #bars}), each filling in turn. The hands stay the hold's
 * till attack is let go after the bars ({@link #locked}), as {@code HeldInput} did for the old action-bar dots.
 */
public final class FinisherClient {
	/** Who attack was last said to be held on (entity id), or -1. */
	private static int sent = -1;
	/** (0.18.97) The body the choice is put about (question or kill), or -1; and which way you faced when it was. */
	private static int choosing = -1;
	private static float choiceYaw;
	/** (0.18.97) The choice's slider: this many marks a side. */
	private static final int CHOICE_MARKS = 6;
	/** (0.19.57) Two presses of attack this close (client ticks) are a double click. */
	static final int DOUBLE_CLICK_TICKS = 6;
	/** (0.19.57) The bars: each this wide and tall (gui pixels), this far apart, this far under the crosshair. */
	static final int BAR_WIDTH = 14, BAR_HEIGHT = 3, BAR_GAP = 3, BAR_DROP = 12;
	/** (0.19.57) A count not renewed for this many ticks is over (let go, or it played). */
	static final int CHARGE_LINGER = 3;
	static final int BAR_BACK = 0x90000000, BAR_EMPTY = 0x60FFFFFF, BAR_FILLING = 0xFFB02020, BAR_FULL = 0xFFFF4040,
		BAR_CHOKE_FILLING = 0xFF8A8A8A, BAR_CHOKE_FULL = 0xFFE0E0E0;
	/** (0.19.57) The server's last count: whom, how far, a choke; when it came (client ticks). */
	private static int chargeTarget = -1, chargeHeld, chargeAt = Integer.MIN_VALUE / 2, ticks;
	private static boolean chargeChoke;
	/** (0.19.57) Attack's state last tick, when and on whom it was last pressed; whether the hands are the hold's till release. */
	private static boolean attackWasDown, locked;
	private static int pressedAt = Integer.MIN_VALUE / 2, pressedOn = -1;
	/** (0.19.57) The one a double click's second press is held on (entity id), or -1: the hold is going. */
	private static int armed = -1;

	private FinisherClient() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(FinisherClient::tick);
		// (0.18.97) the hold's full and the choice is yours: turn left to question, right to kill
		ClientPlayNetworking.registerGlobalReceiver(Finishers.Choose.TYPE, (payload, context) -> {
			choosing = payload.target();
			choiceYaw = context.player().getYRot();
		});
		// (0.19.57) the hold's count, drawn as three bars
		ClientPlayNetworking.registerGlobalReceiver(Finishers.Charge.TYPE, (payload, context) -> {
			chargeTarget = payload.target();
			chargeHeld = payload.held();
			chargeChoke = payload.choke();
			chargeAt = ticks;
		});
		HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, JaymosPowers.id("finisher_bars"), FinisherClient::bars);
		// (0.18.97) a player held down for questioning, and the one asking: their screens
		QuestionScreen.register();
	}

	private static void tick(Minecraft minecraft) {
		ticks++;
		LocalPlayer player = minecraft.player;
		boolean down = minecraft.options.keyAttack.isDown();
		boolean pressed = down && !attackWasDown;
		attackWasDown = down;
		locked = player != null && (charging() || choosing >= 0 || locked && down);
		boolean usable = player != null && minecraft.gui.screen() == null && !player.isSpectator();
		int aimed = usable ? helplessAimed(minecraft) : -1;
		if (pressed && aimed >= 0) {
			// (0.19.57) the double click: a second press on the same one, soon after the first, and held
			if (aimed == pressedOn && ticks - pressedAt <= DOUBLE_CLICK_TICKS) {
				armed = aimed;
				pressedAt = Integer.MIN_VALUE / 2;
				pressedOn = -1;
			} else {
				pressedAt = ticks;
				pressedOn = aimed;
			}
		}
		if (!down || !usable) {
			// (let go early: it's off)
			armed = -1;
		}
		int target = -1;
		if (armed >= 0 && choosing >= 0) {
			// (0.18.97) turning to choose takes the crosshair off them: the hold stays on whom it was
			target = choosing;
		} else if (armed >= 0 && minecraft.hitResult instanceof EntityHitResult hit && hit.getEntity().getId() == armed) {
			target = armed;
		} else if (armed >= 0) {
			// (looked away: off too)
			armed = -1;
		}
		if (choosing >= 0 && (player == null || target != choosing)) {
			// (let go: no choice)
			choosing = -1;
		}
		if (choosing >= 0) {
			choose(player);
		}
		if (target != sent && (player != null || target < 0)) {
			sent = target;
			if (player != null) {
				ClientPlayNetworking.send(new Finishers.Execute(target));
			}
		}
	}

	/** (0.19.57) The entity id of the helpless one (or, with Quiet Word, the one to take from behind) under the crosshair, or -1. */
	private static int helplessAimed(Minecraft minecraft) {
		return minecraft.player != null && minecraft.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof LivingEntity body
			&& (Finishers.helpless(body) || Finishers.chokeable(minecraft.player, body)) ? body.getId() : -1;
	}

	/** (0.19.57) Whether a press now, on the one under the crosshair, would be a double click's second. */
	private static boolean secondClick(Minecraft minecraft) {
		// (asked mid-tick, from the click: this tick's count isn't in yet)
		int aimed = helplessAimed(minecraft);
		return aimed >= 0 && aimed == pressedOn && ticks + 1 - pressedAt <= DOUBLE_CLICK_TICKS;
	}

	/** (0.19.57) Whether the server's count is still coming. */
	private static boolean charging() {
		return chargeTarget >= 0 && ticks - chargeAt <= CHARGE_LINGER;
	}

	/**
	 * (0.19.57) The three bars under the crosshair: each a third of {@link Finishers#HOLD_TICKS}, filling left to right,
	 * bright once full; the third full, the server begins it (and the bars go).
	 */
	private static void bars(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || !charging()) {
			return;
		}
		// (it counts a tick ahead of the last word, till the next comes: smooth, never past full)
		float held = Mth.clamp(chargeHeld + delta.getGameTimeDeltaPartialTick(false), 0.0F, Finishers.HOLD_TICKS);
		float per = Finishers.HOLD_TICKS / (float) Finishers.HOLD_BARS;
		int bars = Finishers.HOLD_BARS;
		int total = bars * BAR_WIDTH + (bars - 1) * BAR_GAP;
		int x = graphics.guiWidth() / 2 - total / 2, y = graphics.guiHeight() / 2 + BAR_DROP;
		int filling = chargeChoke ? BAR_CHOKE_FILLING : BAR_FILLING, full = chargeChoke ? BAR_CHOKE_FULL : BAR_FULL;
		for (int i = 0; i < bars; i++) {
			float share = Mth.clamp((held - i * per) / per, 0.0F, 1.0F);
			graphics.fill(x - 1, y - 1, x + BAR_WIDTH + 1, y + BAR_HEIGHT + 1, BAR_BACK);
			graphics.fill(x, y, x + BAR_WIDTH, y + BAR_HEIGHT, BAR_EMPTY);
			int w = Math.round(BAR_WIDTH * share);
			if (w > 0) {
				graphics.fill(x, y, x + w, y + BAR_HEIGHT, share >= 1.0F ? full : filling);
			}
			x += BAR_WIDTH + BAR_GAP;
		}
	}

	/**
	 * (0.18.97) Andy: "select either question or kill dragging either left or right". How far you've turned since the choice
	 * came: past {@link Finishers#CHOOSE_DEGREES} to the left it's question, to the right kill; till then a slider shows it.
	 */
	private static void choose(LocalPlayer player) {
		float turned = Mth.wrapDegrees(player.getYRot() - choiceYaw);
		if (Math.abs(turned) >= Finishers.CHOOSE_DEGREES) {
			ClientPlayNetworking.send(new Finishers.Pick(choosing, turned < 0.0F));
			choosing = -1;
			return;
		}
		int at = Math.round(turned / Finishers.CHOOSE_DEGREES * CHOICE_MARKS);
		StringBuilder slider = new StringBuilder();
		for (int i = -CHOICE_MARKS; i <= CHOICE_MARKS; i++) {
			slider.append(i == at ? '\u25C6' : '\u2500');
		}
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.finisher.choose", slider.toString()).withStyle(ChatFormatting.GOLD));
	}

	/**
	 * Whether held attack is the execution's, so the powers' own held-attack moves (Z Fighter's combos and race holds)
	 * stand aside: sneaking with the crosshair on someone helpless enough to execute, or (0.19.57) a double click's hold
	 * on them going, or its second press just coming.
	 */
	public static boolean aimingAtHelpless(Minecraft minecraft) {
		// ((0.18.97) or, with Quiet Word, someone on their feet to take from behind for questioning)
		return minecraft.player != null && (armed >= 0 || helplessAimed(minecraft) >= 0 && (minecraft.player.isShiftKeyDown() || secondClick(minecraft)));
	}

	/**
	 * (0.17.97) Whether attack is the execution's just now, not a blow: a finisher or a symbiote execution playing, or you
	 * sneaking with the crosshair on someone helpless (0.19.57: or pressing a second time on them, or holding that press)
	 * and something in hand that finishes them (a torch or a loaf finishes nobody, so it still swings: {@link Finishers#move}).
	 */
	public static boolean holdsAttack(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null || player.isSpectator()) {
			return false;
		}
		if (Finishers.busy(player) || locked) {
			// ((0.19.57) or the bars are filling, the choice is up, or attack's still held from either)
			return true;
		}
		if (!aimingAtHelpless(minecraft)) {
			return false;
		}
		ItemStack stack = player.getMainHandItem();
		return stack.isEmpty() || Finishers.kind(stack) != Finishers.Kind.FIST || stack.isDamageableItem();
	}
}
