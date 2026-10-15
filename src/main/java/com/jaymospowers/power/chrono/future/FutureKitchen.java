package com.jaymospowers.power.chrono.future;

import java.util.UUID;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.monster.zombie.Zombie;
import org.jspecify.annotations.Nullable;

/**
 * Jaymo's Kitchen, if it's loaded: the future's "corpse zombies" are its zombie players (the dead who got back up,
 * wearing a player's face, grey-blue and in tatters), each given a random stranger's id so the client draws one of the
 * game's default skins at random. The only place the future touches Kitchen's classes.
 */
final class FutureKitchen {
	static final boolean LOADED = FabricLoader.getInstance().isModLoaded("jaymoskitchen");

	private FutureKitchen() {
	}

	/** A randomly skinned zombie player, not yet in the world; null without Kitchen. */
	static @Nullable Zombie corpseZombie(ServerLevel level) {
		return LOADED ? Kitchen.corpseZombie(level) : null;
	}

	private static final class Kitchen {
		static @Nullable Zombie corpseZombie(ServerLevel level) {
			com.jaymoskitchen.corpse.ZombiePlayer zombie = com.jaymoskitchen.corpse.PlayerCorpses.ZOMBIE_PLAYER.create(level, EntitySpawnReason.EVENT);
			if (zombie == null) {
				return null;
			}
			UUID stranger = UUID.randomUUID();
			zombie.setOwner(stranger, "");
			// the default skin a stranger's id picks: the first nine of eighteen have slim arms (a higher voice)
			zombie.setSlim(Math.floorMod(stranger.hashCode(), 18) < 9);
			return zombie;
		}
	}
}
