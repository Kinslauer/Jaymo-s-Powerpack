package com.jaymospowers.client.power.wind;

import com.jaymospowers.power.web.WebSymbiote;
import com.jaymospowers.power.wind.WindCyclone;
import com.jaymospowers.power.wind.WindKick;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The X key for a wind user: a tap is the Tempest Kick, a hold of {@link WindCyclone#HOLD_TICKS} the Cyclone, kept
 * for as long as it's held. X is registered by the symbiote's menu (the same key); for a wind user its presses are
 * taken here, at the start of the tick, before the symbiote's handler would see them. Also the roar of every
 * Cyclone in view.
 */
final class WindCycloneClient {
	/** The power skill key's mapping (registered in {@code WebSymbioteClient}). */
	private static final String KEY_NAME = "key.jaymospowers.web_symbiote";
	/** The landing's jolt in first person (degrees, ticks). */
	private static final float LANDING_SHAKE = 1.0F;
	private static final int LANDING_SHAKE_TICKS = 8;

	private enum Phase {
		IDLE,
		/** Down, not yet long enough to be a Cyclone. */
		PRESSING,
		/** Held into a Cyclone (asked the server for one). */
		HOLDING,
		/** Held past the threshold with no Cyclone to give; the release does nothing. */
		SPENT
	}

	private static @Nullable KeyMapping key;
	private static Phase phase = Phase.IDLE;
	private static int heldTicks;
	/** The server formed this hold's Cyclone (the cooldown only follows a real one). */
	private static boolean formed;
	private static int cooldown;
	/** How long the running cooldown was when it began, for the HUD's shading. */
	private static int cooldownTotal = 1;
	private static boolean wasActive;
	/** entity id → its roar. */
	private static final Map<Integer, WindCycloneSound> SOUNDS = new HashMap<>();

	private WindCycloneClient() {
	}

	static void init() {
		ClientTickEvents.START_CLIENT_TICK.register(WindCycloneClient::input);
	}

	static @Nullable KeyMapping key(Minecraft minecraft) {
		if (key == null) {
			for (KeyMapping mapping : minecraft.options.keyMappings) {
				if (KEY_NAME.equals(mapping.getName())) {
					key = mapping;
					break;
				}
			}
		}
		return key;
	}

	/** The bound key's short name, for the HUD. */
	static String keyLabel(Minecraft minecraft) {
		KeyMapping mapping = key(minecraft);
		return mapping == null ? "X" : mapping.getTranslatedKeyMessage().getString();
	}

	static int cooldown() {
		return cooldown;
	}

	static int cooldownTotal() {
		return Math.max(1, cooldownTotal);
	}

	private static void input(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		KeyMapping mapping = key(minecraft);
		if (mapping == null) {
			return;
		}
		if (player == null || !WindPowerClient.isActive(player) || WebSymbiote.isBonded(player)) {
			// not ours: leave the key's presses to the symbiote
			phase = Phase.IDLE;
			return;
		}
		boolean pressed = false;
		while (mapping.consumeClick()) {
			pressed = true;
		}
		if (minecraft.isPaused()) {
			return;
		}
		if (cooldown > 0) {
			cooldown--;
		}
		boolean active = WindCyclone.isActive(player);
		if (active && !wasActive) {
			// down onto one knee
			WindCamera.shake(LANDING_SHAKE, LANDING_SHAKE_TICKS);
		}
		wasActive = active;
		formed |= active && phase == Phase.HOLDING;
		boolean down = mapping.isDown() && minecraft.gui.screen() == null && !player.isSpectator();
		switch (phase) {
			case IDLE -> {
				if (down) {
					phase = Phase.PRESSING;
					heldTicks = 0;
				} else if (pressed && minecraft.gui.screen() == null && !player.isSpectator()) {
					// pressed and let go inside one tick
					ClientPlayNetworking.send(new WindKick.Kick());
				}
			}
			case PRESSING -> {
				if (!down) {
					ClientPlayNetworking.send(new WindKick.Kick());
					phase = Phase.IDLE;
				} else if (++heldTicks >= com.jaymospowers.power.wind.WindCold.windup(player, WindCyclone.HOLD_TICKS)) {
					if (WindCyclone.isAvailable(player) && cooldown <= 0) {
						ClientPlayNetworking.send(new WindCyclone.Hold(true));
						formed = false;
						phase = Phase.HOLDING;
					} else {
						phase = Phase.SPENT;
					}
				}
			}
			case HOLDING -> {
				if (!down) {
					ClientPlayNetworking.send(new WindCyclone.Hold(false));
					if (formed) {
						cooldown = com.jaymospowers.power.wind.WindCold.cooldown(player, WindCyclone.COOLDOWN);
						cooldownTotal = cooldown;
					}
					phase = Phase.IDLE;
				}
			}
			case SPENT -> {
				if (!down) {
					phase = Phase.IDLE;
				}
			}
		}
	}

	/** From the power's client tick: rooted in the stance, the body keeps no sideways momentum. */
	static void root(LocalPlayer player) {
		if (WindCyclone.isActive(player)) {
			Vec3 v = player.getDeltaMovement();
			player.setDeltaMovement(0.0, Math.min(v.y, 0.0), 0.0);
		}
	}

	/** Every tick: a roar for each Cyclone in view (anyone's), dropped once it has faded out. */
	static void tickSounds(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null) {
			SOUNDS.clear();
			return;
		}
		SOUNDS.values().removeIf(WindCycloneSound::isStopped);
		for (AbstractClientPlayer player : level.players()) {
			if (WindCyclone.isActive(player) && !SOUNDS.containsKey(player.getId())) {
				WindCycloneSound sound = new WindCycloneSound(player);
				SOUNDS.put(player.getId(), sound);
				minecraft.getSoundManager().play(sound);
			}
		}
	}

	/** The Cyclone's line over the crosshair while it lasts. */
	static Component label() {
		return Component.translatable("hud.jaymospowers.wind.cyclone");
	}
}
