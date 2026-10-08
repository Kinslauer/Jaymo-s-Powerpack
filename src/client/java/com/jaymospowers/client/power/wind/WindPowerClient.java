package com.jaymospowers.client.power.wind;

import com.jaymospowers.api.PowerKey;
import com.jaymospowers.client.PowerClient;
import com.jaymospowers.client.PowerClients;
import com.jaymospowers.core.Powers;
import com.jaymospowers.power.wind.WindAbility;
import com.jaymospowers.power.wind.WindNet;
import com.jaymospowers.power.wind.WindPower;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/** Wind Control, client side: the flight, the camera effects, the pose and the HUD. */
public final class WindPowerClient implements PowerClient {
	public static final WindPowerClient INSTANCE = new WindPowerClient();

	private final WindFlightController flight = new WindFlightController();
	/** Ticks left on each ability's cooldown (counted down here between server updates). */
	private final int[] cooldowns = new int[WindAbility.values().length];
	private @Nullable WindAbility channel;
	private int channelTicks;

	private WindPowerClient() {
	}

	/** Called once from {@link com.jaymospowers.client.JaymosPowersClient}: renderers, client packets, the PowerClient. */
	public static void init() {
		PowerClients.register(WindPower.ID, INSTANCE);
		ClientPlayNetworking.registerGlobalReceiver(WindNet.Status.TYPE, (payload, ctx) -> INSTANCE.onStatus(ctx.client(), payload));
		// poses of everyone in view (other players may fly even when the local player has another power)
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			if (!minecraft.isPaused()) {
				WindPose.tick(minecraft);
			}
		});
	}

	public static boolean isActive(@Nullable Player player) {
		return player != null && Powers.has(player, WindPower.INSTANCE);
	}

	public WindFlightController flight() {
		return this.flight;
	}

	int cooldown(WindAbility ability) {
		return this.cooldowns[ability.ordinal()];
	}

	@Nullable WindAbility channel() {
		return this.channel;
	}

	int channelTicks() {
		return this.channelTicks;
	}

	/** While flying, pressing Jump in mid-air must not deploy an elytra (see LocalPlayerMixin). */
	public static boolean suppressElytra(LocalPlayer player) {
		return INSTANCE.flight.state().flying() && INSTANCE.flight.player() == player && isActive(player);
	}

	/** Shake and FOV only apply while the local player has this power (they're reset when it goes). */
	public static boolean cameraEffectsActive(Minecraft minecraft) {
		return minecraft.player != null && minecraft.getCameraEntity() == minecraft.player && isActive(minecraft.player);
	}

	@Override
	public void tick(Minecraft minecraft, LocalPlayer player) {
		this.flight.tick(minecraft, player);
		for (int i = 0; i < this.cooldowns.length; i++) {
			if (this.cooldowns[i] > 0) {
				this.cooldowns[i]--;
			}
		}
		if (this.channel != null && this.channelTicks < WindAbility.MAX_CHANNEL) {
			this.channelTicks++;
		}
	}

	@Override
	public void onKey(LocalPlayer player, PowerKey key, boolean down) {
		if (key == PowerKey.ABILITY_1 && down) {
			this.flight.toggle(Minecraft.getInstance(), player);
		}
	}

	@Override
	public void renderHud(GuiGraphicsExtractor graphics, DeltaTracker delta, LocalPlayer player) {
		WindHud.render(graphics, delta, player, this);
	}

	@Override
	public void onActiveChanged(LocalPlayer player, boolean active) {
		Minecraft minecraft = Minecraft.getInstance();
		if (!active) {
			this.flight.forceLand(player);
		} else {
			// forget presses made before the power arrived
			while (minecraft.options.keyJump.consumeClick()) {
			}
			while (minecraft.options.keyShift.consumeClick()) {
			}
		}
		this.flight.reset();
		if (!active) {
			// (not on gaining it: the server's cooldown update may already have arrived this tick)
			java.util.Arrays.fill(this.cooldowns, 0);
			this.channel = null;
			this.channelTicks = 0;
		}
	}

	private void onStatus(Minecraft minecraft, WindNet.Status status) {
		for (int i = 0; i < this.cooldowns.length && i < status.cooldowns().length; i++) {
			this.cooldowns[i] = status.cooldowns()[i];
		}
		this.channel = WindAbility.byId(status.channel());
		this.channelTicks = status.channelTicks();
		LocalPlayer player = minecraft.player;
		if (player == null) {
			return;
		}
		if (status.stopFlight()) {
			this.flight.forceLand(player);
		}
		if (status.dash() > 0.0F) {
			this.flight.dash(player, status.dash());
		}
	}
}
