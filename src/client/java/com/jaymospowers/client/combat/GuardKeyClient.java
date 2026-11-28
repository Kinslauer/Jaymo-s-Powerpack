package com.jaymospowers.client.combat;

import com.jaymospowers.client.JaymosPowersClient;
import com.jaymospowers.client.compat.ComboClashClient;
import com.jaymospowers.combat.GuardKey;
import com.jaymospowers.core.Powers;
import com.jaymospowers.power.chrono.ChronoFlow;
import com.jaymospowers.power.darktide.DarktidePower;
import com.jaymospowers.power.darktide.DarktideWeapons;
import com.jaymospowers.power.web.WebCounter;
import com.jaymospowers.power.web.WebPower;
import com.jaymospowers.power.web.WebStrength;
import com.jaymospowers.power.zfighter.ZFighterPower;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import org.jspecify.annotations.Nullable;

/**
 * The guard key (F) on this side ({@link GuardKey}). Andy (2026-11-28): "move blocking to f as well", and "the counters
 * should move to f as well". Held, it raises what blocks in your hands (a shield in the off hand first, else the weapon),
 * through vanilla's own use, so the guard, parry and perfect parry are {@code combat.Guard}'s as before; let go, it comes
 * down. Vanilla lets a raised item down when right click is up; while F holds it, it stays ({@code GuardKeyHoldMixin}).
 * <p>
 * The powers' own guards and counters read it too, in place of right click ({@link #down}, {@link #pressed}): Chrono's
 * Time Flow counter, Spider-Sense's counter and the webbed fists' web shield, the Z Fighter's bare-handed guard (sneak
 * for the Full Counter), the Darktide guard. While one of them has it ({@link #powerOwns}) no weapon comes up, and Combo
 * Clash's own block (also on F) stands aside ({@code ComboClashClient}). In Combo Clash's combat mode its block is F's
 * for fists and weapons, and ours stays down; a shield is ours in every mode ({@link GuardKey#shieldGuard}), so it's held
 * up in front and the shield bang, bash and shield moves work on it.
 */
public final class GuardKeyClient {
	private static @Nullable KeyMapping key;
	/** Presses of the key this tick, taken once at the start of the tick so every reader sees the same ones. */
	private static int presses;
	/** What we last told the server. */
	private static boolean sent;

	private GuardKeyClient() {
	}

	public static void init() {
		key = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymospowers.guard", InputConstants.KEY_F, JaymosPowersClient.CATEGORY));
		ClientTickEvents.START_CLIENT_TICK.register(ComboClashClient.BEFORE_CLASH, minecraft -> {
			presses = 0;
			KeyMapping k = key;
			while (k != null && k.consumeClick()) {
				presses++;
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(GuardKeyClient::tick);
		ClientPlayConnectionEvents.JOIN.register((handler, sender, minecraft) -> {
			sent = false;
			GuardKey.localHeld = false;
		});
	}

	/** The key's mapping (hints name its binding), or null before {@link #init}. */
	public static @Nullable KeyMapping key() {
		return key;
	}

	/** Whether the guard key is held. */
	public static boolean down() {
		KeyMapping k = key;
		return k != null && k.isDown();
	}

	/** Whether the guard key went down this tick (for a counter: one press, one counter). */
	public static boolean pressed() {
		return presses > 0;
	}

	/** Whether one of the powers has the guard key just now (its own guard or counter, not a raised weapon). */
	public static boolean powerOwns(LocalPlayer player) {
		if (ChronoFlow.on(player)) {
			// Time Flow: F is the counter
			return true;
		}
		if (Powers.has(player, WebPower.INSTANCE) && WebCounter.ready(player)) {
			return true;
		}
		boolean bare = player.getMainHandItem().isEmpty();
		if (bare && Boolean.TRUE.equals(player.getAttached(WebStrength.FISTS))) {
			// webbed fists: F spins up the web shield
			return true;
		}
		if (bare && Powers.has(player, ZFighterPower.INSTANCE)) {
			return true;
		}
		return DarktidePower.has(player) && DarktideWeapons.canGuard(player);
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		MultiPlayerGameMode gameMode = minecraft.gameMode;
		if (player == null || gameMode == null) {
			GuardKey.localHeld = false;
			return;
		}
		boolean held = down() && minecraft.gui.screen() == null && !player.isSpectator() && player.isAlive()
			&& (!ComboClashClient.active() || GuardKey.shieldGuard(player)) && !powerOwns(player);
		if (held != sent && ClientPlayNetworking.canSend(GuardKey.Held.TYPE)) {
			sent = held;
			// told before the raise below, so the server's use goes the same way
			ClientPlayNetworking.send(new GuardKey.Held(held));
		}
		GuardKey.localHeld = held;
		if (held) {
			InteractionHand hand = GuardKey.guardHand(player);
			if (hand != null && !player.isUsingItem() && !player.getCooldowns().isOnCooldown(player.getItemInHand(hand))) {
				gameMode.useItem(player, hand);
			}
		} else if (GuardKey.strayGuard(player)) {
			// let go of F (or a guard came up some other way): it comes down
			gameMode.releaseUsingItem(player);
		}
	}
}
