package com.jaymospowers.core.wounds;

import com.jaymospowers.core.Carry;
import com.jaymospowers.power.web.Webbed;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;

/**
 * Andy (2026-11-23): "all the starts have a problem right now where you can spawn under attack. maybe player should be
 * invisible to enemies while they spawn until they move." One just put into the world (a wake after being left for dead
 * ({@link Mortality}), a story start (Kingdoms' LifeStory, by reflection), a respawn) is no enemy to any mob
 * ({@code SpawnCalmMixin}: {@code canBeSeenAsEnemy}, which every mob's target goes through), and mobs already on them let go,
 * until they move or strike something themselves.
 *
 * <p>Moving means pressing a movement key (or jump) once they're free to: being put somewhere, knocked out, webbed up,
 * carried or shoved moves them without it, and none of that counts. While they lie out cold, wear web or are carried,
 * the keys aren't read at all (struggling out of a web isn't walking off). Sneak isn't moving either (it's how one left
 * for dead comes to). Not saved: a player who logs out loses it.
 */
public final class SpawnCalm {
	// ---- tuning ----
	/** How far round a calm player (blocks) mobs on them are made to let go, and how often (ticks). */
	static final double LET_GO_RANGE = 48.0;
	static final int LET_GO_EVERY = 10;

	private static final Set<UUID> CALM = Collections.synchronizedSet(new HashSet<>());

	private SpawnCalm() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(SpawnCalm::tick);
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			if (!alive) {
				calm(newPlayer);
			}
		});
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (!level.isClientSide()) {
				CALM.remove(player.getUUID());
			}
			return InteractionResult.PASS;
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> CALM.remove(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> CALM.clear());
	}

	/** {@code player} has just been put into the world: no enemy to mobs until they move. Kingdoms calls it by reflection, so keep its parameter type. */
	public static void calm(ServerPlayer player) {
		CALM.add(player.getUUID());
		letGo(player);
	}

	/** Whether {@code player} is calm now (server side). */
	public static boolean isCalm(Player player) {
		return !CALM.isEmpty() && !player.level().isClientSide() && CALM.contains(player.getUUID());
	}

	private static void tick(MinecraftServer server) {
		if (CALM.isEmpty()) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!CALM.contains(player.getUUID())) {
				continue;
			}
			if (!player.isAlive() || player.isSpectator() || moved(player)) {
				CALM.remove(player.getUUID());
				continue;
			}
			if (server.getTickCount() % LET_GO_EVERY == 0) {
				letGo(player);
			}
		}
	}

	/** Whether {@code player} pressed a movement key while free to walk. */
	private static boolean moved(ServerPlayer player) {
		if (Downed.isDowned(player) || Mortality.waking(player) || Carry.isCarried(player) || Webbed.held(player) || player.isSleeping()) {
			return false;
		}
		Input input = player.getLastClientInput();
		return input.forward() || input.backward() || input.left() || input.right() || input.jump();
	}

	/** Mobs near {@code player} that are on them drop them (goal or brain). */
	private static void letGo(ServerPlayer player) {
		for (Mob mob : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(LET_GO_RANGE),
			mob -> mob.getTargetUnchecked() == player || brainTarget(mob) == player || mob.getLastHurtByMob() == player)) {
			if (mob.getTargetUnchecked() == player) {
				mob.setTarget(null);
			}
			if (brainTarget(mob) == player) {
				mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
			}
			if (mob.getLastHurtByMob() == player) {
				mob.setLastHurtByMob(null);
			}
		}
	}

	/** The brain's attack target of {@code mob} (null when its brain has no such memory: a goal mob). */
	private static net.minecraft.world.entity.@org.jspecify.annotations.Nullable LivingEntity brainTarget(Mob mob) {
		java.util.Optional<net.minecraft.world.entity.LivingEntity> held = mob.getBrain().getMemoryInternal(MemoryModuleType.ATTACK_TARGET);
		return held == null ? null : held.orElse(null);
	}
}
