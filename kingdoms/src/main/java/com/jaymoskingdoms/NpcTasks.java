package com.jaymoskingdoms;

import com.jaymoskingdoms.forms.Duels;
import com.jaymoskingdoms.forms.GuardDrills;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Andy (2026-11-26): what a person is doing right now, shown under their name in Jade's panel ("Hunting", "Cooking",
 * "On patrol", "Going home"). Worked out on the server from the systems that keep them busy, most pressing first: a
 * fight, a talk, the sick and the dead, their trade and the town's work, a routine of the day, and last MCA's and
 * vanilla's schedule. It is only what anyone could see them doing, so a stranger's shows too ({@link Introductions}
 * hides their name and trade, not what they're at).
 *
 * <p>The answer is a lang key under {@code npc_task.jaymoskingdoms.} and, for a settlement worker's own task (free
 * text, {@link Worker#task}), the words to show if the key has no line.
 */
public final class NpcTasks {
	/** Beyond this far from their bed in the evening, they're on their way home; from their workstation, on their way to work. */
	static final double HOME_NEAR = 6.0, WORK_NEAR = 4.0;

	/** A lang key's last part, and the plain words if it has no lang line (null: it always has one). */
	public record Task(String key, @Nullable String plain) {
	}

	private NpcTasks() {
	}

	/** What {@code v} is doing just now, or null for nothing worth saying. */
	public static @Nullable Task of(Villager v) {
		String k = key(v);
		if (k != null) {
			return new Task(k, null);
		}
		if (v instanceof Worker w && w.role() >= 0) {
			String task = w.task();
			// (an escort's task carries whom they escort after a colon: only the kind of work is shown)
			task = task == null ? "" : task.split(":", 2)[0];
			if (!task.isBlank() && !task.equals("idle")) {
				String plain = task.replace('_', ' ');
				return new Task("work." + task.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_"),
					Character.toUpperCase(plain.charAt(0)) + plain.substring(1));
			}
			if (w.role() == Folk.GUARD || w.role() == Folk.ARCHER) {
				return new Task("patrol", null);
			}
			if (w.role() == Folk.KING || w.role() == Folk.LORD || w.role() == Folk.CHIEF) {
				return new Task("ruling", null);
			}
		}
		String late = schedule(v);
		return late == null ? null : new Task(late, null);
	}

	/** The pressing things and the day's doings, before a worker's own task. */
	private static @Nullable String key(Villager v) {
		if (Downed.isDowned(v)) {
			return "downed";
		}
		if (v.isSleeping()) {
			return "sleeping";
		}
		if (McaCompat.panicking(v) || Frights.afraid(v.getUUID())) {
			return "fleeing";
		}
		LivingEntity foe = v.getTarget();
		if (foe != null && foe.isAlive() || Duels.busy(v.getUUID())) {
			return "fighting";
		}
		if (GuardDrills.busy(v.getUUID())) {
			return "drilling";
		}
		if (v.getTradingPlayer() != null) {
			return "trading";
		}
		if (McaCompat.busy(v) || Approaches.talking(v) || Quests.listening(v) || Neighbours.meeting(v)) {
			return "talking";
		}
		if (FieldMedics.busy(v) || Downed.helping(v)) {
			return "tending";
		}
		if (Bearers.busy(v) || RoyalRemains.busy(v)) {
			return "bearing";
		}
		if (Undertakers.busy(v)) {
			return "burying";
		}
		if (Arrests.busy(v) || Grapples.busy(v)) {
			return "arresting";
		}
		if (Unmasking.busy(v)) {
			return "unmasking";
		}
		if (Looters.busy(v) || Scavengers.busy(v)) {
			return "looting";
		}
		if (Meals.eating(v)) {
			return "eating";
		}
		if (Hunters.busy(v)) {
			return "hunting";
		}
		if (Whetting.working(v)) {
			return "sharpening";
		}
		if (SmithMending.working(v)) {
			return "mending";
		}
		if (KitchenCompat.butchering(v)) {
			return "butchering";
		}
		if (WorkScenes.working(v)) {
			return Meals.cook(v) ? "cooking" : trade(v);
		}
		if (Couriers.carrying(v)) {
			return "delivering";
		}
		if (Directions.guiding(v)) {
			return "guiding";
		}
		if (Firesides.sitting(v)) {
			return "by_fire";
		}
		if (Tag.playing(v)) {
			return "playing";
		}
		if (Crewmen.tagged(v)) {
			return "crewing";
		}
		if (v.isPassenger()) {
			return "riding";
		}
		String chore = McaCompat.chore(v);
		if (chore != null) {
			return "chore." + chore;
		}
		if (McaCompat.following(v) != null) {
			return "following";
		}
		if (McaCompat.staying(v)) {
			return "staying";
		}
		String routine = Routines.doing(v);
		return routine == null ? null : "routine." + routine;
	}

	/** By MCA's and vanilla's schedule, when nothing else has them. */
	private static @Nullable String schedule(Villager v) {
		if (McaCompat.isGuard(v) || Downed.isGuard(v)) {
			return "patrol";
		}
		Optional<Activity> now = v.getBrain().getActiveNonCoreActivity();
		if (now.isEmpty()) {
			return null;
		}
		Activity a = now.get();
		if (a == Activity.REST) {
			return far(v, MemoryModuleType.HOME, HOME_NEAR) ? "going_home" : "resting";
		}
		if (a == Activity.WORK) {
			return far(v, MemoryModuleType.JOB_SITE, WORK_NEAR) ? "going_to_work" : trade(v);
		}
		if (a == Activity.MEET) {
			return "meeting";
		}
		if (a == Activity.PLAY) {
			return "playing";
		}
		if (a == Activity.PANIC) {
			return "fleeing";
		}
		if (a == Activity.RAID || a == Activity.PRE_RAID || a == Activity.HIDE) {
			return "sheltering";
		}
		if (a == Activity.CELEBRATE) {
			return "celebrating";
		}
		if (a == Activity.IDLE) {
			return "idle";
		}
		// MCA's own: a chore set by a player (named above), and mourning at a grave
		return a.toString().endsWith("grieve") ? "grieving" : null;
	}

	/** At their trade: the lang key for their profession ({@code trade.<profession>}), "working" if it has none. */
	private static String trade(Villager v) {
		String job = BuiltInRegistries.VILLAGER_PROFESSION.getKey(v.getVillagerData().profession().value()).getPath();
		return "trade." + job;
	}

	/** {@code v} is further than {@code near} from the place {@code memory} holds (and it's in this world). */
	private static boolean far(Villager v, MemoryModuleType<GlobalPos> memory, double near) {
		Optional<GlobalPos> at = v.getBrain().getMemoryInternal(memory);
		if (at == null || at.isEmpty() || at.get().dimension() != v.level().dimension()) {
			return false;
		}
		BlockPos pos = at.get().pos();
		return v.position().distanceToSqr(Vec3.atBottomCenterOf(pos)) > near * near;
	}
}
