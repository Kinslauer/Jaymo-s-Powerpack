package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.Vec3;

/** A stolen Sylvan face follows a solitary traveller, calling from behind them. */
final class ChangelingLures {
	static final double ALONE_RADIUS = 32.0, FIND_RANGE = 24.0, GIVE_UP_RANGE = 40.0;
	static final double FOLLOW_DISTANCE = 10.0, REVEAL_DISTANCE = 3.0;
	static final float WALK_SPEED = 0.6F;
	static final int WALK_CLOSE = 2;
	static final int REPATH = 20, VOICE_GAP = 300, MAX_CALLS = 3, STALK_TICKS = 1200;
	private static final Map<UUID, Stalk> STALKS = new HashMap<>();

	private static final class Stalk {
		final Villager copy;
		final ServerPlayer target;
		final long started;
		long nextCall;
		int calls;

		Stalk(Villager copy, ServerPlayer target, long now) {
			this.copy = copy;
			this.target = target;
			this.started = now;
			this.nextCall = now;
		}
	}

	private ChangelingLures() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> tick());
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> STALKS.remove(entity.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> STALKS.clear());
	}

	/** Called only for the still-disguised copies Doubles already visits. */
	static void notice(Villager copy, ServerPlayer player, long now) {
		if (!STALKS.containsKey(copy.getUUID()) && Doubles.changelingCopy(copy) && eligible(copy, player)
			&& copy.distanceToSqr(player) <= FIND_RANGE * FIND_RANGE) {
			STALKS.put(copy.getUUID(), new Stalk(copy, player, now));
		}
	}

	static boolean stalking(Villager copy, ServerPlayer player) {
		Stalk stalk = STALKS.get(copy.getUUID());
		return stalk != null && stalk.target == player && eligible(copy, player);
	}

	private static boolean eligible(Villager copy, ServerPlayer player) {
		if (!copy.isAlive() || copy.isRemoved() || copy.isSleeping() || Downed.isDowned(copy) || !Doubles.changelingCopy(copy)
			|| !player.isAlive() || player.isRemoved() || player.isCreative() || player.isSpectator() || Downed.isDowned(player)
			|| copy.level() != player.level() || copy.distanceToSqr(player) > GIVE_UP_RANGE * GIVE_UP_RANGE) return false;
		return player.level().players().stream().noneMatch(other -> other != player && other.isAlive() && !other.isSpectator()
			&& other.distanceToSqr(player) < ALONE_RADIUS * ALONE_RADIUS);
	}

	private static void tick() {
		var it = STALKS.values().iterator();
		while (it.hasNext()) {
			Stalk stalk = it.next();
			Villager copy = stalk.copy;
			ServerPlayer target = stalk.target;
			if (!eligible(copy, target)) {
				copy.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
				copy.getNavigation().stop();
				it.remove();
				continue;
			}
			ServerLevel level = target.level();
			long now = level.getGameTime();
			if (copy.distanceToSqr(target) <= REVEAL_DISTANCE * REVEAL_DISTANCE || now - stalk.started >= STALK_TICKS) {
				it.remove();
				copy.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
				copy.getNavigation().stop();
				Doubles.expose(level, copy, target);
				continue;
			}
			if (now % REPATH == 0) {
				// Prefer the traveller's rear, keeping the borrowed face out of their view where the path permits.
				Vec3 behind = target.position().subtract(target.getLookAngle().multiply(FOLLOW_DISTANCE, 0.0, FOLLOW_DISTANCE));
				BehaviorUtils.setWalkAndLookTargetMemories(copy, BlockPos.containing(behind), WALK_SPEED, WALK_CLOSE);
			}
			if (now >= stalk.nextCall && stalk.calls < MAX_CALLS) {
				stalk.nextCall = now + VOICE_GAP;
				if (ChangelingVoiceCompat.lure(copy, target)) stalk.calls++;
			}
		}
	}
}
