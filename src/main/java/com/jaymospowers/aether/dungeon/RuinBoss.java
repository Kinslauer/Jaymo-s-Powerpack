package com.jaymospowers.aether.dungeon;

import com.jaymospowers.JaymosPowers;
import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

/**
 * A Sentry Ruins boss that isn't the Slider. For a while some ruins were built with the minotaur (Jaymo's Kingdoms'
 * {@code jaymoskingdoms:minotaur}) in the boss room; no new ruin is (it keeps the labyrinths under the Overworld now,
 * Kingdoms' {@code Labyrinths}), but the ones already standing in a world still work: the boss keeps to its room; while it
 * lives, a player inside shuts the room's doors and they open again once nobody is in it; its bar shows to the players in
 * the room; and its death opens the doors, breaks the way to the boss's crates and unlocks the walls, as the Slider's does.
 */
public final class RuinBoss {
	// ---- tuning ----
	/** How often the loaded bosses are looked over (ticks). */
	static final int CHECK_TICKS = 10;

	/** On a ruin's boss that isn't the Slider: its room's interior (min x, y, z, max x, y, z). */
	public static final AttachmentType<List<Integer>> ROOM = AttachmentRegistry.create(JaymosPowers.id("ruin_boss_room"),
		builder -> builder.persistent(Codec.INT.listOf()));

	private static final Set<Mob> LOADED = new HashSet<>();
	private static final Map<UUID, ServerBossEvent> BARS = new HashMap<>();
	/** Whether each loaded boss's doors were last shut (they're looked over only when that changes). */
	private static final Map<UUID, Boolean> SHUT = new HashMap<>();

	private RuinBoss() {
	}

	static void init() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Mob mob && mob.hasAttached(ROOM)) {
				LOADED.add(mob);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof Mob mob && LOADED.remove(mob)) {
				dropBar(mob);
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> died(entity));
		ServerTickEvents.END_SERVER_TICK.register(RuinBoss::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			LOADED.clear();
			BARS.values().forEach(ServerBossEvent::removeAllPlayers);
			BARS.clear();
			SHUT.clear();
		});
	}

	private static @Nullable BoundingBox room(LivingEntity boss) {
		List<Integer> r = boss.getAttached(ROOM);
		return r == null || r.size() != 6 ? null : new BoundingBox(r.get(0), r.get(1), r.get(2), r.get(3), r.get(4), r.get(5));
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % CHECK_TICKS != 0 || LOADED.isEmpty()) {
			return;
		}
		for (Mob boss : List.copyOf(LOADED)) {
			BoundingBox room = room(boss);
			if (!boss.isAlive() || room == null || !(boss.level() instanceof ServerLevel level)) {
				continue;
			}
			ServerBossEvent bar = BARS.computeIfAbsent(boss.getUUID(),
				id -> new ServerBossEvent(id, boss.getDisplayName(), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS));
			bar.setProgress(boss.getHealth() / boss.getMaxHealth());
			boolean anyone = false;
			for (ServerPlayer player : level.players()) {
				boolean inside = !player.isSpectator() && player.isAlive() && room.isInside(player.blockPosition());
				anyone |= inside && !player.isCreative();
				if (inside) {
					bar.addPlayer(player);
				} else {
					bar.removePlayer(player);
				}
			}
			// someone in the room: the doors shut behind them; nobody: they open again
			if (!Boolean.valueOf(anyone).equals(SHUT.put(boss.getUUID(), anyone))) {
				Doorways.setClosed(level, room, anyone);
			}
			// it keeps to its room
			if (boss.getTarget() == null && !room.isInside(boss.blockPosition())) {
				BlockPos middle = room.getCenter();
				boss.getNavigation().moveTo(middle.getX() + 0.5, room.minY(), middle.getZ() + 0.5, 1.0);
			}
		}
	}

	private static void died(LivingEntity entity) {
		if (!(entity instanceof Mob mob) || !(entity.level() instanceof ServerLevel level)) {
			return;
		}
		BoundingBox room = room(entity);
		LOADED.remove(mob);
		dropBar(mob);
		if (room == null) {
			return;
		}
		Doorways.setClosed(level, room, false);
		Doorways.breakTreasure(level, room);
		Doorways.unlock(level, room);
	}

	private static void dropBar(Mob boss) {
		SHUT.remove(boss.getUUID());
		ServerBossEvent bar = BARS.remove(boss.getUUID());
		if (bar != null) {
			bar.removeAllPlayers();
		}
	}
}
