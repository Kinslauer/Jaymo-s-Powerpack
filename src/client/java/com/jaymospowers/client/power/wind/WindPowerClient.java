package com.jaymospowers.client.power.wind;

import com.jaymospowers.api.PowerKey;
import com.jaymospowers.client.PowerClient;
import com.jaymospowers.client.PowerClients;
import com.jaymospowers.core.Powers;
import com.jaymospowers.power.wind.WindAbility;
import com.jaymospowers.power.wind.WindNet;
import com.jaymospowers.power.wind.WindPower;
import com.jaymospowers.power.wind.WindTempest;
import com.jaymospowers.power.wind.WindWeather;
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
	private final int[] cooldownTotals = new int[WindAbility.values().length];
	private @Nullable WindAbility channel;
	private int channelTicks;
	/** Cyclone Lance's cooldown (ascended). */
	private int lanceCooldown;
	/** The ground Gust's own cooldown (Tailwind, in the air, keeps the ability's). */
	private int gustCooldown;
	private int gustCooldownTotal = 1;

	private WindPowerClient() {
	}

	/** Called once from {@link com.jaymospowers.client.JaymosPowersClient}: renderers, client packets, the PowerClient. */
	public static void init() {
		PowerClients.register(WindPower.ID, INSTANCE);
		WindCycloneClient.init();
		ClientPlayNetworking.registerGlobalReceiver(WindNet.Status.TYPE, (payload, ctx) -> INSTANCE.onStatus(ctx.client(), payload));
		// screen washes and lightning flashes reach anyone near a mode, wind or not
		ClientPlayNetworking.registerGlobalReceiver(WindNet.ScreenFx.TYPE, (payload, ctx) -> WindScreenFx.onPacket(payload));
		net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.attachElementAfter(
			net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements.MISC_OVERLAYS,
			com.jaymospowers.JaymosPowers.id("wind_screen_fx"), WindScreenFx::render);
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> WindScreenFx.clear());
		// poses of everyone in view (other players may fly even when the local player has another power)
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			if (!minecraft.isPaused()) {
				WindPose.tick(minecraft);
				WindCycloneClient.tickSounds(minecraft);
				WindAura.tick(minecraft);
				WindWisps.tick(minecraft);
				WindScreenFx.tick();
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
		if (ability == WindAbility.TAILWIND && groundedGust()) {
			return this.gustCooldown;
		}
		return this.cooldowns[ability.ordinal()];
	}

	/** How long the current cooldown was when it started, for the HUD. */
	public int cooldownTotal(WindAbility ability) {
		if (ability == WindAbility.TAILWIND && groundedGust()) {
			return Math.max(1, this.gustCooldownTotal);
		}
		return Math.max(1, this.cooldownTotals[ability.ordinal()]);
	}

	/** G on the ground is the Gust, with its own cooldown; in the air (or flying) it's Tailwind. */
	private boolean groundedGust() {
		LocalPlayer player = Minecraft.getInstance().player;
		return player != null && player.onGround() && !this.flight.state().flying();
	}

	int lanceCooldown() {
		return this.lanceCooldown;
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
		WindWeather.tick(player);
		this.flight.tick(minecraft, player);
		WindCycloneClient.root(player);
		// the cloak of wind (every wind user, from the start): holding Jump in mid-air slows the fall
		if (!this.flight.state().flying() && !player.onGround() && !player.isInWater() && !player.isFallFlying()
			&& !player.getAbilities().flying && player.input.keyPresses.jump()
			&& player.getDeltaMovement().y < -com.jaymospowers.power.wind.WindModes.CLOAK_FALL_SPEED) {
			net.minecraft.world.phys.Vec3 v = player.getDeltaMovement();
			player.setDeltaMovement(v.x, -com.jaymospowers.power.wind.WindModes.CLOAK_FALL_SPEED, v.z);
		}
		if (this.lanceCooldown > 0) {
			this.lanceCooldown--;
		}
		if (this.gustCooldown > 0) {
			this.gustCooldown--;
		}
		for (int i = 0; i < this.cooldowns.length; i++) {
			if (this.cooldowns[i] > 0) {
				this.cooldowns[i]--;
			}
		}
		if (this.channel != null && this.channelTicks < this.channel.maxChannel) {
			this.channelTicks++;
		}
	}

	@Override
	public void onKey(LocalPlayer player, PowerKey key, boolean down) {
		if (key == PowerKey.ABILITY_1 && down && !com.jaymospowers.power.wind.WindCyclone.isActive(player)) {
			this.flight.toggle(Minecraft.getInstance(), player);
		}
	}

	@Override
	public void onUltimate(LocalPlayer player, boolean down) {
		if (down && this.lanceCooldown <= 0) {
			// the server throws the lance; the thrower feels it
			WindCamera.shake(1.2F, 8);
			this.lanceCooldown = WindTempest.LANCE_COOLDOWN;
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
			this.lanceCooldown = 0;
			this.gustCooldown = 0;
			this.channel = null;
			this.channelTicks = 0;
		}
	}

	private void onStatus(Minecraft minecraft, WindNet.Status status) {
		for (int i = 0; i < this.cooldowns.length && i < status.cooldowns().length; i++) {
			// a fresh cooldown sets the length the HUD shades against (Tailwind's varies with the charge)
			if (status.cooldowns()[i] > this.cooldowns[i]) {
				this.cooldownTotals[i] = status.cooldowns()[i];
			}
			this.cooldowns[i] = status.cooldowns()[i];
		}
		this.lanceCooldown = status.lanceCooldown();
		if (status.gustCooldown() > this.gustCooldown) {
			this.gustCooldownTotal = status.gustCooldown();
		}
		this.gustCooldown = status.gustCooldown();
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
