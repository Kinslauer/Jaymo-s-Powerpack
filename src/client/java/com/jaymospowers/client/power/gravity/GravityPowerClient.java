package com.jaymospowers.client.power.gravity;

import com.jaymospowers.api.PowerKey;
import com.jaymospowers.client.JaymosPowersClient;
import com.jaymospowers.client.PowerClient;
import com.jaymospowers.client.PowerClients;
import com.jaymospowers.core.Fate;
import com.jaymospowers.core.Powers;
import com.jaymospowers.power.gravity.GravityCooldowns;
import com.jaymospowers.power.gravity.GravityPower;
import java.util.Locale;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Gravity's client half: the cooldown chips (bottom-right, Wind's style), Crush's charge bar, and the drift everyone
 * sees around a weightless player (and the dark pull around a collapsed one).
 */
public final class GravityPowerClient implements PowerClient {
	public static final GravityPowerClient INSTANCE = new GravityPowerClient();

	private static final int CHIP = 20;
	private static final int CHIP_GAP = 2;
	private static final int CHIP_COLOR = 0x8A6CFF;
	private static final int ULT_COLOR = 0xFFD54A;

	/** Game time B went down (for the charge bar), or -1. */
	private long crushFrom = -1;
	/** Game time R went down, while lifting (for the lift's own timer bar), or -1. */
	private long liftFrom = -1;
	/** What Lift would take right now, if R went down (for the mark over the crosshair): 0 nothing, 1 a thing, 2 a player. */
	private static int liftMark;

	private GravityPowerClient() {
	}

	public static void init() {
		PowerClients.register(GravityPower.ID, INSTANCE);
		GravityHeldClient.init();
		// every gravity user's aura, on every client
		ClientTickEvents.END_CLIENT_TICK.register(GravityPowerClient::auras);
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			LocalPlayer player = minecraft.player;
			if (player == null || minecraft.isPaused()) {
				return;
			}
			// straining to tear a block loose: the view shudders
			if (player.getAttached(GravityPower.RIPPING) != null && player.tickCount % 3 == 0) {
				com.jaymospowers.client.power.web.WebShake.start(0.35F);
			}
			GravityRockFlightClient.tick(minecraft, player);
			liftMark = liftMark(player);
		});
		// left click while Lift holds a chunk of the world (or anything else that can't be choked): it's thrown
		net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback.EVENT.register((minecraft, player, clicks) -> {
			Integer grip = player.getAttached(GravityPower.GRIP);
			if (clicks <= 0 || grip == null || !Powers.has(player, GravityPower.INSTANCE)) {
				return false;
			}
			net.minecraft.world.entity.Entity held = player.level().getEntity(grip);
			if (held instanceof net.minecraft.world.entity.item.ItemEntity || held == null) {
				return false;
			}
			// a creature is pushed away; anything else is thrown
			net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new com.jaymospowers.power.gravity.GravityGrip(true));
			return true;
		});
		// right click while holding something with Lift: choke, then slam (and the click does nothing else)
		ClientTickEvents.START_CLIENT_TICK.register(minecraft -> {
			LocalPlayer player = minecraft.player;
			if (player == null || !Powers.has(player, GravityPower.INSTANCE) || player.getAttached(GravityPower.GRIP) == null
				|| minecraft.gui.screen() != null) {
				return;
			}
			while (minecraft.options.keyUse.consumeClick()) {
				net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new com.jaymospowers.power.gravity.GravityGrip(false));
			}
			minecraft.options.keyUse.setDown(false);
		});
	}

	@Override
	public void onKey(LocalPlayer player, PowerKey key, boolean down) {
		long now = player.level().getGameTime();
		GravityCooldowns cooldowns = cooldowns(player);
		if (key == PowerKey.ABILITY_4) {
			crushFrom = down && now >= cooldowns.crush() ? now : -1;
		} else if (key == PowerKey.ABILITY_1) {
			liftFrom = down && now >= cooldowns.lift() ? now : -1;
		}
	}

	@Override
	public void onActiveChanged(LocalPlayer player, boolean active) {
		crushFrom = -1;
		liftFrom = -1;
	}

	private static GravityCooldowns cooldowns(LocalPlayer player) {
		GravityCooldowns cooldowns = player.getAttached(GravityPower.COOLDOWNS);
		return cooldowns == null ? GravityCooldowns.NONE : cooldowns;
	}

	/** Whether something Lift can take is under the crosshair and in reach, with R ready (see {@link #liftMark}). */
	private static int liftMark(LocalPlayer player) {
		if (!Powers.has(player, GravityPower.INSTANCE) || player.getAttached(GravityPower.GRIP) != null
			|| !com.jaymospowers.core.Mastery.isUsable(player, PowerKey.ABILITY_1) || player.level().getGameTime() < cooldowns(player).lift()) {
			return 0;
		}
		net.minecraft.world.entity.Entity target = GravityPower.liftableInSight(player, GravityPower.liftRange(player));
		if (target == null || !target.isAlive()) {
			return 0;
		}
		return target instanceof net.minecraft.world.entity.player.Player ? 2 : 1;
	}

	/** Weightless players shed slow motes; collapsed ones draw a faint darkness in. Client-side, near you only. */
	private static void auras(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null || minecraft.isPaused() || minecraft.player == null) {
			return;
		}
		RandomSource random = level.getRandom();
		for (AbstractClientPlayer player : level.players()) {
			if (!Powers.has(player, GravityPower.INSTANCE) || player.distanceToSqr(minecraft.player) > 48.0 * 48.0) {
				continue;
			}
			if (player.getAttached(GravityPower.WEIGHTLESS_ON) != null && player.tickCount % 3 == 0) {
				level.addParticle(ParticleTypes.END_ROD, player.getX() + (random.nextDouble() - 0.5) * 0.8, player.getY() + random.nextDouble() * 0.3,
					player.getZ() + (random.nextDouble() - 0.5) * 0.8, 0.0, 0.02, 0.0);
			}
			if (Fate.isCorrupted(player) && player.tickCount % 2 == 0) {
				double a = random.nextDouble() * Math.PI * 2.0;
				double r = 1.5 + random.nextDouble() * 1.5;
				level.addParticle(ParticleTypes.PORTAL, player.getX() + Math.cos(a) * r, player.getY() + 0.2 + random.nextDouble() * 1.8,
					player.getZ() + Math.sin(a) * r, -Math.cos(a) * 0.6, 0.0, -Math.sin(a) * 0.6);
			}
			if (Fate.isAscended(player) && player.tickCount % 4 == 0) {
				// a thin ring circling at the waist: the horizon
				double a = player.tickCount * 0.25;
				level.addParticle(ParticleTypes.REVERSE_PORTAL, player.getX() + Math.cos(a) * 0.9, player.getY() + 1.0, player.getZ() + Math.sin(a) * 0.9,
					0.0, 0.0, 0.0);
			}
		}
	}

	@Override
	public void renderHud(GuiGraphicsExtractor graphics, DeltaTracker delta, LocalPlayer player) {
		Minecraft minecraft = Minecraft.getInstance();
		Font font = minecraft.font;
		if (liftMark > 0 && minecraft.options.getCameraType().isFirstPerson()) {
			mark(graphics, graphics.guiWidth() / 2, graphics.guiHeight() / 2, liftMark == 2);
		}
		GravityCooldowns cooldowns = cooldowns(player);
		long now = player.level().getGameTime();
		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		boolean ascended = Fate.isAscended(player) || Fate.isCorrupted(player);
		int chips = ascended ? 5 : 4;
		int total = CHIP * chips + CHIP_GAP * (chips - 1);
		// right of the hotbar, past the off-hand slot; above the hunger bar on narrow screens
		int x = width / 2 + 91 + 30;
		int y = height - CHIP - 1;
		if (x + total > width - 2) {
			x = width / 2 + 91 - total;
			y = height - 22 - 30 - CHIP;
		}
		boolean weightless = player.getAttached(GravityPower.WEIGHTLESS_ON) != null;
		chip(graphics, font, x, y, PowerKey.ABILITY_1, Items.SHULKER_SHELL, cooldowns.lift() - now, GravityPower.LIFT_COOLDOWN,
			liftFrom >= 0 ? 1.0F - Mth.clamp((now - liftFrom) / (float)GravityPower.LIFT_MAX_TICKS, 0.0F, 1.0F) : -1.0F);
		chip(graphics, font, x + (CHIP + CHIP_GAP), y, PowerKey.ABILITY_2, Items.ENDER_EYE, cooldowns.well() - now, GravityPower.WELL_COOLDOWN, -1.0F);
		if (com.jaymospowers.core.Mastery.isUnlocked(player, PowerKey.ABILITY_3)) {
			chip(graphics, font, x + (CHIP + CHIP_GAP) * 2, y, PowerKey.ABILITY_3, Items.FEATHER, cooldowns.weightless() - now,
				GravityPower.WEIGHTLESS_COOLDOWN, weightless ? 1.0F : -1.0F);
		} else {
			// before Weightless: V held pulls loose items in
			chip(graphics, font, x + (CHIP + CHIP_GAP) * 2, y, PowerKey.ABILITY_3, Items.HOPPER, 0, 1, -1.0F);
		}
		float charge = crushFrom >= 0 ? Mth.clamp((now - crushFrom) / (float)GravityPower.CRUSH_FULL_CHARGE, 0.0F, 1.0F) : -1.0F;
		chip(graphics, font, x + (CHIP + CHIP_GAP) * 3, y, PowerKey.ABILITY_4, Items.ANVIL, cooldowns.crush() - now, cooldowns.crushLength(), charge);
		if (ascended) {
			ultimateChip(graphics, font, x + (CHIP + CHIP_GAP) * 4, y, cooldowns.singularity() - now,
				Fate.isAscended(player) && !Fate.isCorrupted(player) ? cooldowns.singularityLength() : GravityPower.ultimateCooldown(player));
		}
	}

	/** Lift's mark: four purple corners round the crosshair (brighter and wider on a player). */
	private static void mark(GuiGraphicsExtractor graphics, int cx, int cy, boolean player) {
		int r = player ? 8 : 6, arm = 3;
		int color = player ? 0xFFC9A6FF : 0xD08A6CFF;
		for (int sx = -1; sx <= 1; sx += 2) {
			for (int sy = -1; sy <= 1; sy += 2) {
				int x = cx + sx * r, y = cy + sy * r;
				// a corner: one stroke along, one down
				graphics.fill(Math.min(x, x - sx * arm), y, Math.max(x, x - sx * arm) + 1, y + 1, color);
				graphics.fill(x, Math.min(y, y - sy * arm), x + 1, Math.max(y, y - sy * arm) + 1, color);
			}
		}
	}

	/** One ability: icon, cooldown shading and seconds, the bound key; {@code bar} (0-1) lights a strip along the bottom. */
	private static void chip(GuiGraphicsExtractor graphics, Font font, int x, int y, PowerKey key, Item icon, long left, int total, float bar) {
		String label = font.plainSubstrByWidth(JaymosPowersClient.keyMapping(key).getTranslatedKeyMessage().getString(), CHIP - 2);
		if (!JaymosPowersClient.isUnlocked(key)) {
			graphics.fill(x, y, x + CHIP, y + CHIP, 0xB0000000);
			graphics.outline(x, y, CHIP, CHIP, 0x60505050);
			graphics.text(font, label, x + CHIP - 1 - font.width(label), y + CHIP - 8, 0xFF505050);
			return;
		}
		boolean ready = left <= 0;
		graphics.fill(x, y, x + CHIP, y + CHIP, 0x90000000);
		graphics.outline(x, y, CHIP, CHIP, ready ? 0xFF000000 | CHIP_COLOR : 0xA0505050);
		graphics.item(new ItemStack(icon), x + 2, y + 2);
		if (!ready) {
			int shade = (int)Math.ceil(16 * Math.min(1.0, left / (double)Math.max(1, total)));
			graphics.fill(x + 2, y + 2 + 16 - shade, x + 18, y + 18, 0xB0000000);
			String seconds = left >= 200 ? Long.toString((left + 19) / 20) : String.format(Locale.ROOT, "%.1f", left / 20.0);
			graphics.text(font, seconds, x + CHIP / 2 - font.width(seconds) / 2, y + 4, 0xFFFFFFFF);
		}
		if (bar >= 0.0F) {
			graphics.fill(x + 1, y + CHIP - 3, x + 1 + Math.round((CHIP - 2) * bar), y + CHIP - 1, 0xFF000000 | CHIP_COLOR);
		}
		graphics.text(font, label, x + CHIP - 1 - font.width(label), y + CHIP - 8, ready ? 0xFFFFFFFF : 0xFF909090);
	}

	/** The C (Meteor, black hole or Skyfall): gold rim, its own key. */
	private static void ultimateChip(GuiGraphicsExtractor graphics, Font font, int x, int y, long left, int total) {
		boolean ready = left <= 0;
		graphics.fill(x, y, x + CHIP, y + CHIP, 0x90000000);
		graphics.outline(x, y, CHIP, CHIP, ready ? 0xFF000000 | ULT_COLOR : 0xA0505050);
		graphics.item(new ItemStack(Items.ECHO_SHARD), x + 2, y + 2);
		if (!ready) {
			int shade = (int)Math.ceil(16 * Math.min(1.0, left / (double)Math.max(1, total)));
			graphics.fill(x + 2, y + 2 + 16 - shade, x + 18, y + 18, 0xB0000000);
			String seconds = Long.toString((left + 19) / 20);
			graphics.text(font, seconds, x + CHIP / 2 - font.width(seconds) / 2, y + 4, 0xFFFFFFFF);
		}
		String label = font.plainSubstrByWidth(JaymosPowersClient.ultimateKey().getTranslatedKeyMessage().getString(), CHIP - 2);
		graphics.text(font, label, x + CHIP - 1 - font.width(label), y + CHIP - 8, ready ? 0xFFFFFFFF : 0xFF909090);
	}
}
