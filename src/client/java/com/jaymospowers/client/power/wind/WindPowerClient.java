package com.jaymospowers.client.power.wind;

import com.jaymospowers.api.PowerKey;
import com.jaymospowers.client.PowerClient;
import com.jaymospowers.client.PowerClients;
import com.jaymospowers.core.Powers;
import com.jaymospowers.power.wind.WindAbility;
import com.jaymospowers.power.wind.WindNet;
import com.jaymospowers.power.wind.WindPower;
import com.jaymospowers.power.wind.WindStaffSurge;
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
	/** Ticks to the channel's full charge (the server's; longer in the cold). */
	private int channelMax = 1;
	/** Cyclone Lance's cooldown (ascended). */
	private int lanceCooldown;
	/** The ground Gust's own cooldown (Tailwind, in the air, keeps the ability's). */
	private int gustCooldown;
	private int gustCooldownTotal = 1;
	/** Stood in the cold last tick (a line on the action bar when it first takes hold). */
	private boolean wasCold;
	private int lastColdNotice = -100000;
	/** Twirling the staff for a Staff Surge right now (the server and the watchers have been told). */
	private boolean surging;
	/** The cold's line shows at most this often (ticks), however often a biome edge is crossed. */
	private static final int COLD_NOTICE_SPACING = 1200;

	private WindPowerClient() {
	}

	/** Called once from {@link com.jaymospowers.client.JaymosPowersClient}: renderers, client packets, the PowerClient. */
	public static void init() {
		PowerClients.register(WindPower.ID, INSTANCE);
		WindCycloneClient.init();
		ClientPlayNetworking.registerGlobalReceiver(WindNet.Status.TYPE, (payload, ctx) -> INSTANCE.onStatus(ctx.client(), payload));
		// screen washes and lightning flashes reach anyone near a mode, wind or not
		ClientPlayNetworking.registerGlobalReceiver(WindNet.ScreenFx.TYPE, (payload, ctx) -> WindScreenFx.onPacket(payload));
		ClientPlayNetworking.registerGlobalReceiver(WindStaffSurge.Twirl.TYPE, (payload, ctx) -> {
			net.minecraft.world.entity.Entity entity = ctx.client().level == null ? null : ctx.client().level.getEntity(payload.entity());
			if (entity != null) {
				WindFlightSwing.twirl(entity, payload.ascended(), payload.held());
			}
		});
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
				WindJetClient.tick(minecraft);
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

	int channelMax() {
		return this.channelMax;
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
		boolean cold = com.jaymospowers.power.wind.WindCold.isCold(player);
		if (cold && !this.wasCold && player.tickCount - this.lastColdNotice >= COLD_NOTICE_SPACING) {
			this.lastColdNotice = player.tickCount;
			player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("message.jaymospowers.wind.cold")
				.withColor(0xA8E4FF));
		}
		this.wasCold = cold;
		this.flight.tick(minecraft, player);
		this.staffSurge(minecraft, player);
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
		if (this.channel != null && this.channelTicks < this.channelMax) {
			this.channelTicks++;
		}
	}

	/**
	 * The Staff Surge: mastered, quarterstaff in hand, already past 30 m/s, holding left mouse twirls the staff and
	 * the speed climbs slowly (faster when ascended). See {@link WindStaffSurge}.
	 */
	private void staffSurge(Minecraft minecraft, LocalPlayer player) {
		boolean held = minecraft.options.keyAttack.isDown() && minecraft.gui.screen() == null && !player.isSpectator()
			&& WindStaffSurge.eligible(player);
		double accel = WindStaffSurge.accel(player);
		boolean pushing = false;
		if (held) {
			if (this.flight.state().fast()) {
				// (keeps twirling while it's already going, even dipping under 30 m/s for a moment in a turn)
				pushing = (this.surging || this.flight.currentSpeed() > WindStaffSurge.MIN_SPEED) && this.flight.surge(accel, WindStaffSurge.room(player));
			} else {
				// falling, vaulting, thrown: push the body on along its own path
				net.minecraft.world.phys.Vec3 v = player.getDeltaMovement();
				double speed = v.length();
				if (speed > WindStaffSurge.MIN_SPEED) {
					player.setDeltaMovement(v.scale((speed + accel) / speed));
					pushing = true;
				}
			}
		}
		if (pushing != this.surging) {
			this.surging = pushing;
			boolean ascended = com.jaymospowers.core.Fate.isAscended(player);
			WindFlightSwing.twirl(player, ascended, pushing);
			if (pushing) {
				WindCamera.shake(ascended ? 0.6F : 0.4F, 5);
			}
			ClientPlayNetworking.send(new WindStaffSurge.Surge(pushing));
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
			this.lanceCooldown = com.jaymospowers.power.wind.WindCold.cooldown(player, WindTempest.LANCE_COOLDOWN);
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
			if (this.surging) {
				this.surging = false;
				WindFlightSwing.twirl(player, false, false);
				ClientPlayNetworking.send(new WindStaffSurge.Surge(false));
			}
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
		this.channelMax = status.channelMax();
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
