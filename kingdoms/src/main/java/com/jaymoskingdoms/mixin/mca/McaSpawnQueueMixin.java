package com.jaymoskingdoms.mixin.mca;

import com.jaymoskingdoms.Loaded;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.conczin.mca.server.SpawnQueue;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * MCA swaps the villagers and zombies of a newly made village for its own, one a tick from a queue ({@code SpawnQueue}),
 * where the old one stood. With Chunky making land fast on the Steam Deck, one stood in a chunk already gone or not yet
 * loaded, and spawning there (the difficulty read for it) waited on the server thread a minute: the server down (crash
 * 2026-11-13 22:32). Now one whose land isn't made and loaded goes to the back of its queue and waits; if none is ready,
 * the tick waits too. None is lost.
 */
@Mixin(SpawnQueue.class)
abstract class McaSpawnQueueMixin {
	/** Queued ones looked at per queue per tick for one that's ready. */
	private static final int LOOK = 16;

	@Shadow
	@Final
	private ConcurrentLinkedQueue<Villager> villagerSpawnQueue;
	@Shadow
	@Final
	private ConcurrentLinkedQueue<ZombieVillager> zombieVillagerSpawnQueue;
	@Shadow
	@Final
	private ConcurrentLinkedQueue<Zombie> zombieSpawnList;

	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$loadedOnly(CallbackInfo ci) {
		if (!jaymoskingdoms$ready(villagerSpawnQueue) | !jaymoskingdoms$ready(zombieVillagerSpawnQueue) | !jaymoskingdoms$ready(zombieSpawnList)) {
			ci.cancel();
		}
	}

	/** Puts the first one ready (or gone) at the head of {@code queue}; false: none of the first few is. */
	private static <T extends Entity> boolean jaymoskingdoms$ready(Queue<T> queue) {
		for (int i = 0, n = Math.min(queue.size(), LOOK); i < n; i++) {
			T head = queue.peek();
			if (head == null || head.isRemoved() || Loaded.made(head.level(), head.blockPosition())) {
				return true;
			}
			T moved = queue.poll();
			if (moved != null) {
				queue.offer(moved);
			}
		}
		return queue.isEmpty();
	}
}
