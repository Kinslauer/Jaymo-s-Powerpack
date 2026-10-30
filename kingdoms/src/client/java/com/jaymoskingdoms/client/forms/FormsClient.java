package com.jaymoskingdoms.client.forms;

import com.jaymoskingdoms.CombatArts;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.PowersCompat;
import com.jaymoskingdoms.client.Ui;
import com.jaymoskingdoms.forms.Forms;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
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
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.util.Mth;

/**
 * (0.7.366) Weapon Forms on the client: the Combat Art key's hold and tap ({@link #key}), the belt strip above the hotbar
 * while in stance, the chain counter beside the crosshair, Alt and scroll for the belt slot, and Powers' lock-on taken on
 * with the stance ({@link PowersCompat#lockOnNearest}; without Powers the stance works without it).
 * <p>
 * The chain counter sits to the right of the crosshair, clear of Powers' parry timing bar below it. Weapon Forms has no
 * parry window of its own.
 */
public final class FormsClient {
	/** How long the Combat Art key is held to take (or drop) the stance (ticks). */
	static final int HOLD_TICKS = 10;
	/** Ticks between tries at the lock-on after taking the stance, until it takes. */
	static final int LOCK_RETRY_TICKS = 10;
	/** The chain counter: shown from this many links, graded at these, fading over the last ticks of the chain's window. */
	static final int COUNTER_FROM = 2, GOOD = 3, FIERCE = 6, RELENTLESS = 10, FADE_TICKS = 10;
	static final int GOOD_COLOR = 0xFFE8E8E8, FIERCE_COLOR = 0xFFFFC21A, RELENTLESS_COLOR = 0xFFE05050;
	/** The counter's gap right of the crosshair (pixels). */
	static final int COUNTER_GAP = 14;
	/** The belt strip: a slot's size, the gap between, and how far above the screen's bottom the strip sits (over the hearts and armour). */
	static final int SLOT_W = 38, SLOT_H = 12, SLOT_GAP = 2, STRIP_ABOVE = 66;
	static final int SLOT_BACK = 0xA0000000, SLOT_PICKED = 0xFFFFC21A, SLOT_WRONG = 0xFF9A6A6A;

	private static int held;
	private static boolean holdDone, wasStance;
	private static int lockedByUs = -1, lockRetry;
	private static int chainLinks;
	private static long chainAt = Long.MIN_VALUE / 2;

	private FormsClient() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(Forms.Chain.TYPE, (payload, context) -> {
			chainLinks = payload.links();
			chainAt = context.client().level == null ? 0L : context.client().level.getGameTime();
		});
		ClientPlayNetworking.registerGlobalReceiver(Forms.OpenBelt.TYPE, (payload, context) -> context.client().gui.setScreen(new BeltScreen()));
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, JaymosKingdoms.id("forms_belt"), FormsClient::belt);
		HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, JaymosKingdoms.id("forms_chain"), FormsClient::chain);
		ClientTickEvents.END_CLIENT_TICK.register(FormsClient::lockOn);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, minecraft) -> {
			held = 0;
			wasStance = false;
			lockedByUs = -1;
			chainLinks = 0;
		});
	}

	// ---- the key -------------------------------------------------------------------------------------------------------

	/**
	 * The Combat Art key, every tick. Without a form's weapon in hand (or any form) it is what it always was: a press runs
	 * {@code art} (Charge, War Cry, Shield Throw). With one, a hold of {@value #HOLD_TICKS} ticks takes or drops the stance,
	 * and a tap (judged on release) is the art out of stance and a form in it.
	 */
	public static void key(Minecraft minecraft, KeyMapping key, Runnable art) {
		boolean clicked = false;
		while (key.consumeClick()) {
			clicked = true;
		}
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.gui.screen() != null) {
			held = 0;
			return;
		}
		Forms.Book book = Forms.book(player);
		boolean forms = book.stance() || !book.practice().isEmpty() && Forms.canStance(player);
		if (held == 0 && !forms) {
			if (clicked) {
				art.run();
			}
			return;
		}
		if (clicked && held == 0) {
			held = 1;
			holdDone = false;
		} else if (held > 0 && key.isDown()) {
			held++;
			if (!holdDone && held > HOLD_TICKS && ClientPlayNetworking.canSend(Forms.Stance.TYPE)) {
				holdDone = true;
				ClientPlayNetworking.send(new Forms.Stance(!book.stance()));
			}
		}
		if (held > 0 && !key.isDown()) {
			if (!holdDone) {
				if (book.stance()) {
					if (ClientPlayNetworking.canSend(Forms.Fire.TYPE)) {
						ClientPlayNetworking.send(new Forms.Fire(player.isShiftKeyDown()));
					}
				} else {
					art.run();
				}
			}
			held = 0;
		}
	}

	/** Alt and scroll in stance move the selected slot (scroll alone stays Powers' lock-on). True when it took the scroll. */
	public static boolean scroll(double x, double y) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		double wheel = y != 0.0 ? y : x;
		if (player == null || wheel == 0.0 || minecraft.gui.screen() != null || !Forms.book(player).stance()
			|| !(InputConstants.isKeyDown(InputConstants.KEY_LALT) || InputConstants.isKeyDown(InputConstants.KEY_RALT))) {
			return false;
		}
		if (ClientPlayNetworking.canSend(Forms.Pick.TYPE)) {
			// (up is left, as on the hotbar)
			ClientPlayNetworking.send(new Forms.Pick(wheel > 0.0 ? -1 : 1));
		}
		return true;
	}

	/** Taking the stance locks onto the foe nearest the crosshair (Powers' lock-on); leaving lets that lock go. */
	private static void lockOn(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		boolean stance = player != null && Forms.book(player).stance();
		if (stance && !wasStance) {
			lockRetry = 0;
		}
		if (stance && lockedByUs < 0 && lockRetry-- <= 0) {
			lockRetry = LOCK_RETRY_TICKS;
			CustomPacketPayload packet = PowersCompat.lockOnNearest(minecraft, player);
			if (packet != null) {
				lockedByUs = PowersCompat.lockedId();
				ClientPlayNetworking.send(packet);
			}
		} else if (!stance && wasStance && lockedByUs >= 0) {
			CustomPacketPayload packet = PowersCompat.lockOff(lockedByUs);
			if (packet != null) {
				ClientPlayNetworking.send(packet);
			}
			lockedByUs = -1;
		}
		wasStance = stance;
	}

	// ---- the HUD -------------------------------------------------------------------------------------------------------

	/** The belt strip above the hotbar, in stance: five slots, the picked one outlined, a form that doesn't fit the weapon in hand dimmed. */
	private static void belt(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || !Forms.book(player).stance()) {
			return;
		}
		Forms.Book book = Forms.book(player);
		Font font = minecraft.font;
		boolean named = graphics.guiWidth() >= Forms.BELT_SLOTS * (SLOT_W + SLOT_GAP) + 8;
		int w = named ? SLOT_W : 14;
		int x0 = (graphics.guiWidth() - (Forms.BELT_SLOTS * w + (Forms.BELT_SLOTS - 1) * SLOT_GAP)) / 2;
		int y = graphics.guiHeight() - STRIP_ABOVE;
		for (int i = 0; i < Forms.BELT_SLOTS; i++) {
			int x = x0 + i * (w + SLOT_GAP);
			boolean picked = i == book.slot();
			Forms.Form form = Forms.get(book.belt().get(i));
			graphics.fill(x - 1, y - 1, x + w + 1, y + SLOT_H + 1, picked ? SLOT_PICKED : SLOT_BACK);
			graphics.fill(x, y, x + w, y + SLOT_H, SLOT_BACK);
			int color = form == null ? Ui.HINT : !form.family().holds(player.getMainHandItem()) ? SLOT_WRONG : picked ? Ui.TEXT : Ui.DIM;
			String text = named ? (i + 1) + " " + (form == null ? "-" : form.name().getString()) : String.valueOf(i + 1);
			graphics.text(font, Ui.cut(font, text, w - 4), x + 2, y + 2, color);
		}
	}

	/** The chain counter right of the crosshair: links and a grade, fading as the chain's window runs out. */
	private static void chain(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null || chainLinks < COUNTER_FROM) {
			return;
		}
		float age = minecraft.level.getGameTime() - chainAt + delta.getGameTimeDeltaPartialTick(false);
		float left = CombatArts.CHAIN_TICKS - age;
		if (left <= 0.0F) {
			return;
		}
		int alpha = Math.max(8, (int)(255.0F * Mth.clamp(left / FADE_TICKS, 0.0F, 1.0F)));
		int links = chainLinks;
		String grade = links >= RELENTLESS ? "relentless" : links >= FIERCE ? "fierce" : links >= GOOD ? "good" : null;
		int color = links >= RELENTLESS ? RELENTLESS_COLOR : links >= FIERCE ? FIERCE_COLOR : GOOD_COLOR;
		Component text = grade == null ? Component.translatable("hud.jaymoskingdoms.chain", links)
			: Component.translatable("hud.jaymoskingdoms.chain.graded", links, Component.translatable("hud.jaymoskingdoms.chain." + grade));
		graphics.text(minecraft.font, text, graphics.guiWidth() / 2 + COUNTER_GAP, graphics.guiHeight() / 2 - 4, alpha << 24 | color & 0xFFFFFF);
	}
}
