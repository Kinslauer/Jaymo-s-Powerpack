package com.jaymospowers.power.ember;

import com.jaymospowers.core.HudBar;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Heat (Andy's Awakening pick: "Ember, beside the Flame Avatar"). Every fire move cast builds Heat, and Heat makes the whole fire
 * kit hit harder: up to {@link #MAX_BONUS} more at a full gauge (Kindle, the fireball, the fire kit, the avatar's moves, soak and
 * return, the vent, Backdraft and the cluster shells). At a full gauge a cast burns the user's own health ({@link #BURN_HEALTH}
 * a cast, never below {@link #SPARE_HEALTH}) to push past the cap, up to {@link #OVER_BONUS} more on top. It cools once the
 * casting stops. Running Hot is the fight's gauge (blows); this one is the casting's. Shown on the action bar as it changes.
 */
public final class EmberHeat {
	// ---- tuning ----
	/** Heat a cast adds (of {@link #FULL}), and the most past the cap that burning health can push it. */
	static final float CAST = 15.0F, FULL = 100.0F, OVER = 100.0F;
	/** The damage bonus at a full gauge, and the most more past it. */
	static final float MAX_BONUS = 0.3F, OVER_BONUS = 0.3F;
	/** Health a cast burns past the cap (and the health it never takes you under), heat pushed past the cap for it. */
	static final float BURN_HEALTH = 1.0F, SPARE_HEALTH = 4.0F, OVER_PER_BURN = 20.0F;
	/** Ticks after the last cast before it cools, and the cooling a tick. */
	static final int CALM_TICKS = 100;
	static final float COOL = 0.5F;

	private static final class Gauge {
		float heat;
		long last;
	}

	private static final Map<UUID, Gauge> GAUGES = new HashMap<>();

	private EmberHeat() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(EmberHeat::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> GAUGES.remove(handler.player.getUUID()));
	}

	/** A fire move was cast by {@code player}: the gauge climbs (past the cap, for a little health). */
	public static void cast(ServerPlayer player) {
		if (!RunningHot.user(player)) {
			return;
		}
		Gauge g = GAUGES.computeIfAbsent(player.getUUID(), k -> new Gauge());
		g.last = player.level().getGameTime();
		if (g.heat < FULL) {
			g.heat = Math.min(FULL, g.heat + CAST);
		} else if (g.heat < FULL + OVER && player.getHealth() - BURN_HEALTH >= SPARE_HEALTH && !player.getAbilities().invulnerable) {
			player.setHealth(player.getHealth() - BURN_HEALTH);
			g.heat = Math.min(FULL + OVER, g.heat + OVER_PER_BURN);
		}
		show(player, g);
	}

	/** How much harder {@code player}'s fire hits now (1: no Heat). */
	public static float factor(Player player) {
		Gauge g = GAUGES.get(player.getUUID());
		if (g == null) {
			return 1.0F;
		}
		return 1.0F + MAX_BONUS * Math.min(g.heat, FULL) / FULL + OVER_BONUS * Math.max(0.0F, g.heat - FULL) / OVER;
	}

	private static void show(ServerPlayer player, Gauge g) {
		HudBar.gauge(player, "ember_heat", Math.min(g.heat, FULL) / FULL, g.heat > FULL ? HudBar.YELLOW : HudBar.GOLD, Component.translatable("bar.jaymospowers.heat"),
			Component.translatable("bar.jaymospowers.ember_bonus", Math.round((factor(player) - 1.0F) * 100.0F)));
	}

	private static void tick(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		GAUGES.entrySet().removeIf(e -> {
			Gauge g = e.getValue();
			if (now - g.last > CALM_TICKS) {
				g.heat -= COOL;
			}
			return g.heat <= 0.0F;
		});
	}
}
