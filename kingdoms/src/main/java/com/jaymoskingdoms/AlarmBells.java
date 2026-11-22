package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (gv_bell, after Guard Villagers' alarm bells: its idea only) A town's bell, rung while the town is under attack, calls
 * its guards. Ring the bell of a settlement with a foe at its gates (a raid or siege on it, {@link Economy#underAttack}, or
 * a monster within {@link #HOSTILE_NEAR} of the bell) and every guard of it within {@link #CALL_REACH} turns on the foe
 * nearest the bell and goes to it ({@link Comrades#aim}), and the people near hear the answer.
 * <p>
 * Ringing it with nothing wrong is a false alarm: the first in a day only earns a reproach, each after it costs
 * {@link #FALSE_ALARM} reputation with the town ({@link Reputation#change}). A bell just rung is not answered again for
 * {@link #GAP} ticks, so ringing it over and over does neither more good nor more harm.
 */
public final class AlarmBells {
	/** The guards that answer are within this of the bell (blocks); a monster this near it is a foe at the gates. */
	static final double CALL_REACH = 96.0, HOSTILE_NEAR = 48.0;
	/** The wait before a bell is answered or scolded again (ticks); the reputation a false alarm costs (after the first of the day). */
	static final int GAP = 400, FALSE_ALARM = 3;
	/** Guards walk to the foe at this pace. */
	static final float RUSH = 1.1F;
	/** The people who hear the answer are this near the bell (blocks). */
	static final double HEARD = 48.0;

	private static final Map<Integer, Long> LAST = new HashMap<>();
	private static final Map<String, long[]> FALSE_RINGS = new HashMap<>();

	private AlarmBells() {
	}

	static void register() {
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (hand == InteractionHand.MAIN_HAND && world instanceof ServerLevel level && player instanceof ServerPlayer sp && !sp.isSpectator()
				&& level.getBlockState(hit.getBlockPos()).is(Blocks.BELL)) {
				ring(level, sp, hit.getBlockPos());
			}
			// (the bell rings as it always did)
			return InteractionResult.PASS;
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			LAST.clear();
			FALSE_RINGS.clear();
		});
	}

	private static void ring(ServerLevel level, ServerPlayer player, BlockPos bell) {
		Faction f = Factions.at(level.getServer(), level, bell);
		if (f == null || f.fallen() || f.type() == FactionType.BARBARIAN) {
			return;
		}
		long now = level.getGameTime();
		Long last = LAST.get(f.id());
		if (last != null && now - last < GAP) {
			return;
		}
		LAST.put(f.id(), now);
		Vec3 at = Vec3.atCenterOf(bell);
		LivingEntity foe = foe(level, bell);
		if (foe == null && !Economy.underAttack(level.getServer(), f)) {
			falseAlarm(level, player, f, now);
			return;
		}
		int answered = answer(level, bell, CALL_REACH, foe, m -> true);
		if (answered > 0) {
			Fx.ring(level, at.add(0.0, 1.0, 0.0), 2.0F, 0xE8C050);
			for (ServerPlayer p : level.players()) {
				if (p.distanceToSqr(at) <= HEARD * HEARD) {
					p.sendSystemMessage(Component.translatable("message.jaymoskingdoms.bell.answered", f.name()));
				}
			}
		}
	}

	/**
	 * The guards within {@code reach} of {@code at} that {@code whose} allows turn on {@code foe} (each on the monster
	 * nearest it, if null) and go to it. How many answered.
	 */
	static int answer(ServerLevel level, BlockPos at, double reach, @Nullable LivingEntity foe, java.util.function.Predicate<Mob> whose) {
		int answered = 0;
		List<Mob> guards = level.getEntitiesOfClass(Mob.class, new net.minecraft.world.phys.AABB(at).inflate(reach), m -> m.isAlive() && Downed.isGuard(m)
			&& !Downed.isDowned(m) && whose.test(m));
		for (Mob guard : guards) {
			LivingEntity target = foe != null ? foe : nearestFoe(level, guard);
			if (target == null) {
				continue;
			}
			Comrades.aim(guard, target);
			if (guard instanceof Villager v) {
				BehaviorUtils.setWalkAndLookTargetMemories(v, target.blockPosition(), RUSH, 2);
			} else {
				guard.getNavigation().moveTo(target, RUSH);
			}
			answered++;
		}
		return answered;
	}

	/** The monster nearest the bell within {@link #HOSTILE_NEAR}, or null. */
	private static @Nullable LivingEntity foe(ServerLevel level, BlockPos bell) {
		LivingEntity best = null;
		double bestD = Double.MAX_VALUE;
		for (Mob m : level.getEntitiesOfClass(Mob.class, new net.minecraft.world.phys.AABB(bell).inflate(HOSTILE_NEAR), m -> m.isAlive() && m instanceof Enemy)) {
			double d = m.distanceToSqr(Vec3.atCenterOf(bell));
			if (d < bestD) {
				bestD = d;
				best = m;
			}
		}
		return best;
	}

	/** The monster nearest {@code guard} within reach, or null. */
	private static @Nullable LivingEntity nearestFoe(ServerLevel level, Mob guard) {
		LivingEntity best = null;
		double bestD = Double.MAX_VALUE;
		for (Mob m : level.getEntitiesOfClass(Mob.class, guard.getBoundingBox().inflate(HOSTILE_NEAR), m -> m.isAlive() && m instanceof Enemy)) {
			double d = m.distanceToSqr(guard);
			if (d < bestD) {
				bestD = d;
				best = m;
			}
		}
		return best;
	}

	private static void falseAlarm(ServerLevel level, ServerPlayer player, Faction f, long now) {
		String key = player.getUUID() + "|" + f.id();
		long day = level.getGameTime() / 24000L;
		long[] rings = FALSE_RINGS.computeIfAbsent(key, k -> new long[] {day, 0L});
		if (rings[0] != day) {
			rings[0] = day;
			rings[1] = 0L;
		}
		rings[1]++;
		if (rings[1] <= 1L) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.bell.reproach", f.name()));
		} else {
			Reputation.change(player, f, -FALSE_ALARM);
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.bell.annoyed", f.name()));
		}
	}
}
