package com.jaymoskingdoms;

import com.jaymoskingdoms.mixin.MobAccessor;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.level.ChunkPos;

/**
 * (am-feuds, medieval picks; Armed Mobs' faction feuds, an idea only) The monster families' old feuds, when they meet
 * in the wild (not on a realm's land, nor in a raid: a town's own fights are scripted elsewhere). The undead and the
 * illagers go for each other; wither skeletons go for piglins (who already go for them); and orcs and goblins, each
 * out away from their own camp, go for each other ({@link Orcs#prey}, {@link Goblins#prey}). A feud waits behind
 * a monster's own quarry: players and villagers come first ({@link #PRIORITY}).
 */
final class Feuds {
	/** A feud's place among a monster's targets: after players (2) and villagers (3). */
	static final int PRIORITY = 4;
	/** A greenskin this near its own camp's seat (blocks) is at home, not in the wild. */
	static final double CAMP_HOME = 48.0;

	private Feuds() {
	}

	static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> loaded(entity));
	}

	private static boolean undead(Entity e) {
		return e instanceof Zombie && !(e instanceof ZombifiedPiglin) || e instanceof AbstractSkeleton && !(e instanceof WitherSkeleton);
	}

	private static void loaded(Entity entity) {
		if (!(entity instanceof Mob mob)) {
			return;
		}
		if (undead(mob)) {
			feud(mob, AbstractIllager.class);
		} else if (mob instanceof AbstractIllager) {
			feud(mob, Zombie.class);
			feud(mob, AbstractSkeleton.class);
		} else if (mob instanceof WitherSkeleton) {
			feud(mob, AbstractPiglin.class);
		}
	}

	private static <T extends LivingEntity> void feud(Mob mob, Class<T> foe) {
		((MobAccessor)mob).jaymoskingdoms$targets().addGoal(PRIORITY, new NearestAttackableTargetGoal<>(mob, foe, true,
			(target, level) -> feuds(mob, target) && wild(level, mob) && wild(level, target)));
	}

	/** {@code mob}'s family is at feud with {@code target}'s. */
	private static boolean feuds(Mob mob, LivingEntity target) {
		if (undead(mob)) {
			return target instanceof AbstractIllager;
		}
		if (mob instanceof AbstractIllager) {
			return undead(target);
		}
		return mob instanceof WitherSkeleton && target instanceof AbstractPiglin;
	}

	/** {@code e} is out in the wild: on nobody's land, not in a raid. */
	private static boolean wild(ServerLevel level, LivingEntity e) {
		if (e instanceof Raider r && r.hasActiveRaid()) {
			return false;
		}
		return level != Factions.level(level.getServer()) || Banners.owner(level.getServer(), ChunkPos.containing(e.blockPosition())) == null;
	}

	/**
	 * (Orcs, Goblins) {@code target} is a greenskin of the other kind (a goblin, to the orcs of camp {@code camp}; an orc,
	 * to the goblins of {@code camp}), met in the wild: each away from their own camp, on nobody's land.
	 */
	static boolean greenskins(int camp, LivingEntity target, boolean orcs) {
		if (!(target instanceof Worker w) || w.factionId() < 0 || w.factionId() == camp || !(target.level() instanceof ServerLevel level)) {
			return false;
		}
		MinecraftServer server = level.getServer();
		if (orcs ? !Camps.goblin(server, w.factionId()) : !Camps.orc(server, w.factionId())) {
			return false;
		}
		BlockPos at = target.blockPosition();
		Faction mine = Factions.get(server, camp), theirs = Factions.get(server, w.factionId());
		return (mine == null || mine.seat().distSqr(at) > CAMP_HOME * CAMP_HOME) && (theirs == null || theirs.seat().distSqr(at) > CAMP_HOME * CAMP_HOME)
			&& wild(level, target);
	}
}
