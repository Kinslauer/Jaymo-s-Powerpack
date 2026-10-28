package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;

/**
 * (0.7.281) Andy: "villagers shouldn't teleport if they were interacted with by a player within the last 30s." A villager
 * (an MCA villager or one of our folk) a player has just talked to, traded with, hit or opened the menu of stays where
 * they are for {@link #HOLD_TICKS}, and for as long as the player is still in their menu or trading. MCA's own
 * walk-target teleport ({@code McaTeleportMixin}) and every {@link Entity#teleportTo(double, double, double)} on them
 * ({@code EntityTeleportMixin}: our stuck puffs, return-to-post, back-home-unseen) wait until then. Moves that carry them
 * with the player or set them down after something else (a ride's hop, a lift, a ship broken up, a werewolf turning
 * back) go through {@link #carry}.
 */
public final class Attention {
	/** Ticks a player's dealings keep a villager from teleporting (30 s). */
	public static final int HOLD_TICKS = 20 * 30;
	/** How often old entries are swept out (ticks). */
	static final int SWEEP_TICKS = 20 * 60;

	/** Villager → the game time a player last dealt with them. */
	private static final Map<UUID, Long> LAST = new HashMap<>();
	/** Above zero while a {@link #carry} move runs (server thread only). */
	private static int carrying;

	private Attention() {
	}

	static void register() {
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (!level.isClientSide()) {
				heed(entity);
			}
			return InteractionResult.PASS;
		});
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (!level.isClientSide()) {
				heed(entity);
			}
			return InteractionResult.PASS;
		});
		// (an arrow, a thrown rock, a spell: any hurt a player is behind)
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (source.getEntity() instanceof Player) {
				heed(entity);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % SWEEP_TICKS == 0 && !LAST.isEmpty()) {
				long now = server.overworld().getGameTime();
				LAST.values().removeIf(at -> now - at >= HOLD_TICKS || at > now);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> LAST.clear());
	}

	/** Villagers this holds: MCA's (and vanilla) villagers and our folk. */
	static boolean villager(Entity e) {
		return e instanceof AbstractVillager || e instanceof Folk;
	}

	/** A player has just dealt with {@code e} (talked, traded, hit, opened their menu). */
	public static void heed(Entity e) {
		if (villager(e) && !e.level().isClientSide()) {
			LAST.put(e.getUUID(), e.level().getGameTime());
		}
	}

	/** True while {@code e} mustn't be teleported: a player dealt with them in the last {@link #HOLD_TICKS}, or is trading with them now. */
	public static boolean held(Entity e) {
		if (carrying > 0 || !villager(e) || e.level().isClientSide()) {
			return false;
		}
		if (e instanceof AbstractVillager v && v.isTrading()) {
			heed(e);
			return true;
		}
		Long at = LAST.get(e.getUUID());
		if (at == null) {
			return false;
		}
		long since = e.level().getGameTime() - at;
		return since >= 0 && since < HOLD_TICKS;
	}

	/** Runs {@code move} with nobody held: a teleport that carries them along with the player, or puts them back after something else. */
	public static void carry(Runnable move) {
		carrying++;
		try {
			move.run();
		} finally {
			carrying--;
		}
	}
}
