package com.jaymospowers.client.finisher;

import com.jaymospowers.core.finisher.Finishers;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
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
 */
public final class FinisherClient {
	/** Who attack was last said to be held on (entity id), or -1. */
	private static int sent = -1;
	/** (0.18.96) The body the choice is put about (question or kill), or -1; and which way you faced when it was. */
	private static int choosing = -1;
	private static float choiceYaw;
	/** (0.18.96) The choice's slider: this many marks a side. */
	private static final int CHOICE_MARKS = 6;

	private FinisherClient() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(FinisherClient::tick);
		// (0.18.96) the hold's full and the choice is yours: turn left to question, right to kill
		ClientPlayNetworking.registerGlobalReceiver(Finishers.Choose.TYPE, (payload, context) -> {
			choosing = payload.target();
			choiceYaw = context.player().getYRot();
		});
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		int target = -1;
		boolean held = player != null && player.isShiftKeyDown() && minecraft.options.keyAttack.isDown() && minecraft.gui.screen() == null
			&& !player.isSpectator();
		if (held && choosing >= 0) {
			// (0.18.96) turning to choose takes the crosshair off them: the hold stays on whom it was
			target = choosing;
		} else if (held && minecraft.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof LivingEntity body) {
			target = body.getId();
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

	/**
	 * (0.18.96) Andy: "select either question or kill dragging either left or right". How far you've turned since the choice
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
	 * Whether you're sneaking with the crosshair on someone helpless enough to execute: sneak + left-click held there is the
	 * execution's, so the powers' own held-attack moves (Z Fighter's combos and race holds) stand aside.
	 */
	public static boolean aimingAtHelpless(Minecraft minecraft) {
		return minecraft.player != null && minecraft.player.isShiftKeyDown() && minecraft.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof LivingEntity body && Finishers.helpless(body);
	}

	/**
	 * (0.17.97) Whether attack is the execution's just now, not a blow: a finisher or a symbiote execution playing, or you
	 * sneaking with the crosshair on someone helpless and something in hand that finishes them (a torch or a loaf finishes
	 * nobody, so it still swings: {@link Finishers#move}).
	 */
	public static boolean holdsAttack(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null || player.isSpectator()) {
			return false;
		}
		if (Finishers.busy(player)) {
			return true;
		}
		if (!aimingAtHelpless(minecraft)) {
			return false;
		}
		ItemStack stack = player.getMainHandItem();
		return stack.isEmpty() || Finishers.kind(stack) != Finishers.Kind.FIST || stack.isDamageableItem();
	}
}
