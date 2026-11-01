package com.jaymospowers.rig;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * (0.11.87) The Mind's experience, kept on the Core ({@link Rig#XP}; levels at {@link Rig#LEVEL_XP}). It learns from
 * what it lives through with you, all while the Core is worn: kills (hostiles, players, anything boss-sized), wrecks
 * salvaged (the dead suits teach it), time worn, and warnings that turned out right ({@link RigMind}: you were still
 * alive after it). Each new level it remarks on in its own voice and names what it can do now.
 */
public final class RigLevels {
	// (0.19.28) every source halved (the pack-wide training pass): the levels take about twice as long, and saved
	// experience keeps the level it had
	/** Kills: a hostile, a player, anything with at least {@link #BOSS_HEALTH} max health. Other kills teach nothing. */
	static final int KILL_XP = 3, KILL_PLAYER_XP = 12, KILL_BOSS_XP = 60;
	static final float BOSS_HEALTH = 150.0F;
	/** Stripping a wreck. */
	static final int SALVAGE_XP = 20;
	/** Time worn: this much every {@link #WORN_EVERY} ticks (two minutes: 30 an hour). */
	static final int WORN_XP = 1, WORN_EVERY = 2400;
	/** A danger warning proved right. */
	static final int WARNING_XP = 4;
	/** Where the experience stops counting (well past the top level). */
	static final int XP_CAP = 100000;

	private RigLevels() {
	}

	static void init() {
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer dead) {
				// a warning you didn't live through proves nothing
				RigMind.forget(dead.getUUID());
			}
			if (source.getEntity() instanceof ServerPlayer killer && killer != entity) {
				int xp = entity instanceof Player ? KILL_PLAYER_XP : entity.getMaxHealth() >= BOSS_HEALTH ? KILL_BOSS_XP
					: entity instanceof Enemy ? KILL_XP : 0;
				add(killer, xp);
			}
		});
	}

	/** While any piece is worn. */
	static void tick(ServerPlayer player) {
		if (player.tickCount % WORN_EVERY == 0 && player.tickCount > 0) {
			add(player, WORN_XP);
		}
	}

	/** A wreck stripped by this player. */
	static void salvaged(ServerPlayer player) {
		add(player, SALVAGE_XP);
	}

	/** Teaches the worn Core's Mind (once it's awake); a new level is announced. */
	static void add(ServerPlayer player, int xp) {
		ItemStack core = Rig.core(player);
		if (xp <= 0 || core.isEmpty() || core.get(Rig.MIND) == null) {
			return;
		}
		int before = Rig.level(core);
		core.set(Rig.XP, Math.min(XP_CAP, core.getOrDefault(Rig.XP, 0) + xp));
		int after = Rig.level(core);
		for (int level = before + 1; level <= after; level++) {
			RigMind.levelUp(player, core, level);
		}
	}
}
