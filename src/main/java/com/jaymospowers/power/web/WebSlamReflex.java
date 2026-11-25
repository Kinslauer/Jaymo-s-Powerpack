package com.jaymospowers.power.web;

import com.jaymospowers.core.Shakes;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * The two-foe slam reflex: when exactly two enemies are within {@link #REACH} of a bonded host at once, the instinct
 * throws a tendril at each, holds them, and claps them together ({@link WebTendrilCombos#startClap}, the clap combo's own
 * code), on its own {@link #COOLDOWN}. It is the symbiote acting alone, so it speaks first ({@code say.slam.N}, in each
 * strain's voice) and your screen shakes. It stays out of it with the Hide order, a dormant bond, creative mode, hands
 * already full, or the symbiote at the wheel.
 */
public final class WebSlamReflex {
	/** Enemies this close (blocks) count; ticks between looks, between reflexes, and how long a grab may take to reach before it lets go. */
	public static final double REACH = 4.0;
	static final int CHECK_EVERY = 5, COOLDOWN = 400, GIVE_UP = 40;
	static final float SHAKE = 0.6F;
	/** How many lines of {@code say.slam}. */
	static final int LINES = 3;

	/** Held and waiting to reach: when it gives up (game time). */
	private static final Map<UUID, Long> PENDING = new HashMap<>();
	/** When each host may reflex again. */
	private static final Map<UUID, Long> READY = new HashMap<>();

	private WebSlamReflex() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(WebSlamReflex::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			PENDING.clear();
			READY.clear();
		});
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Long until = PENDING.get(player.getUUID());
			if (until != null) {
				holding(player, until);
			} else if (player.tickCount % CHECK_EVERY == 0 && WebSymbiote.isBonded(player)) {
				look(player);
			}
		}
	}

	/** Holds two, waits for the tendrils to reach them, claps. */
	private static void holding(ServerPlayer player, long until) {
		if (!player.isAlive() || WebTendrilCombos.startClap(player)) {
			PENDING.remove(player.getUUID());
			return;
		}
		if (player.level().getGameTime() > until) {
			// slipped away (or too far): let go
			PENDING.remove(player.getUUID());
			WebTendrils.releaseAll(player, false);
		}
	}

	private static void look(ServerPlayer player) {
		if (!player.isAlive() || player.isSpectator() || player.isCreative() || !WebSymbiote.tendrilsUnlocked(player) || WebSymbioteBond.dormant(player)
			|| WebSymbiote.driving(player) || WebSymbioteMind.hiding(player) || WebExecutions.busy(player) || WebHeldGrab.holding(player)
			|| WebTendrilCombos.active(player) || !player.getAttachedOrElse(WebTendrils.GRIPS, WebTendrils.Grips.NONE).empty()) {
			return;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		if (READY.getOrDefault(player.getUUID(), 0L) > now) {
			return;
		}
		List<LivingEntity> foes = level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(REACH),
			e -> WebLash.foe(player, e) && e.distanceTo(player) - e.getBbWidth() * 0.5 <= REACH);
		if (foes.size() != 2 || !WebTendrils.grabbable(player, foes.get(0)) || !WebTendrils.grabbable(player, foes.get(1))) {
			return;
		}
		READY.put(player.getUUID(), now + COOLDOWN);
		PENDING.put(player.getUUID(), now + GIVE_UP);
		WebTendrils.grab(player, WebNet.MAIN, foes.get(0), -1L);
		WebTendrils.grab(player, WebNet.OFF, foes.get(1), -1L);
		// (it speaks and your screen shakes: it did this on its own)
		WebSymbiote.whisper(player, "say." + WebSymbiote.state(player).kind().lines() + "slam." + (1 + player.getRandom().nextInt(LINES)));
		Shakes.send(player, SHAKE);
	}
}
