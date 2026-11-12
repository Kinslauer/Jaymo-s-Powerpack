package com.jaymoskingdoms;

import java.util.HashSet;
import java.util.Set;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * (0.7.576) Monsters fear a lit campfire (Andy's pick from the seven-mods picklist, after HearthGuard's idea; our own
 * code): a monster that comes within {@link #WARD} blocks of a lit campfire or soul campfire near a player turns away
 * and backs off, letting go of whoever it was after if they're by the fire, so camping out at night is safer. The fire's
 * own light already keeps monsters from spawning beside it. Bosses and raiders (illagers on a raid, barbarians) aren't
 * held back. Only campfires within {@link #LOOK} blocks of a player are looked for, once a second.
 */
final class CampfireWard {
	static final int EVERY = 20;
	/** How near a player a campfire counts (blocks across, and up or down). */
	static final int LOOK = 10, LOOK_UP = 4;
	/** How near the fire a monster may come; how far it backs off. */
	static final double WARD = 8.0;
	static final int BACK_OFF = 12;
	static final double SPEED = 1.15;

	private CampfireWard() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(CampfireWard::tick);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % EVERY != 0) {
			return;
		}
		for (ServerLevel level : server.getAllLevels()) {
			if (level.players().isEmpty()) {
				continue;
			}
			Set<BlockPos> fires = new HashSet<>();
			for (ServerPlayer p : level.players()) {
				if (p.isSpectator()) {
					continue;
				}
				BlockPos at = p.blockPosition();
				for (BlockPos pos : BlockPos.betweenClosed(at.offset(-LOOK, -LOOK_UP, -LOOK), at.offset(LOOK, LOOK_UP, LOOK))) {
					BlockState state = level.getBlockState(pos);
					if (state.getBlock() instanceof CampfireBlock && CampfireBlock.isLitCampfire(state)) {
						fires.add(pos.immutable());
					}
				}
			}
			for (BlockPos fire : fires) {
				ward(level, fire);
			}
		}
	}

	private static void ward(ServerLevel level, BlockPos fire) {
		Vec3 at = Vec3.atCenterOf(fire);
		for (PathfinderMob mob : level.getEntitiesOfClass(PathfinderMob.class, new AABB(fire).inflate(WARD), m -> m.isAlive() && feared(m))) {
			if (mob.distanceToSqr(at) > WARD * WARD) {
				continue;
			}
			LivingEntity target = mob.getTarget();
			if (target != null && target.distanceToSqr(at) < (WARD + 4.0) * (WARD + 4.0)) {
				mob.setTarget(null);
			}
			Vec3 away = DefaultRandomPos.getPosAway(mob, BACK_OFF, 4, at);
			if (away != null) {
				mob.getNavigation().moveTo(away.x, away.y, away.z, SPEED);
			}
		}
	}

	/** A monster that heeds the fire: not a boss, not a raider. */
	private static boolean feared(PathfinderMob mob) {
		return mob instanceof Enemy && !(mob instanceof Raider) && !(mob instanceof WitherBoss)
			&& !(mob instanceof PatronChampion) && !(mob instanceof Folk) && !(mob instanceof com.jaymoskingdoms.hunger.Gaunt);
	}
}
