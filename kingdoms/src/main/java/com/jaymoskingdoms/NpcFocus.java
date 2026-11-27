package com.jaymoskingdoms;

import com.jaymoskingdoms.mixin.MobAccessor;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Andy (2026-11-27): the spectate view's label "should label their current focus, or if they are thinking". What an NPC's AI is
 * really doing, for the dev view of its eyes ({@link NpcEyes.View}), read from the AI itself rather than from the fight senses
 * alone: its target, the goals (goal-based mobs) and behaviours (brain-based ones: villagers, MCA's people) that are running,
 * its brain's activity, the task Jade shows ({@link NpcTasks}), and where its path goes and whether it gets there.
 * <p>On top is its state:
 * <ul>
 * <li><b>Thinking, sizing up</b>: it has just picked out a player and takes a second before setting on them ({@link ThinkFirst});</li>
 * <li><b>Stuck</b>: it has a path but hasn't got {@link #STUCK_MOVE} blocks across the ground in {@link #STUCK_TICKS} (climbing
 * a wall's vines and dropping back counts as going nowhere);</li>
 * <li><b>Thinking, re-planning</b>: its path was made afresh {@link #REPLAN_MANY} or more times in {@link #REPLAN_TICKS};</li>
 * <li><b>Thinking, picking</b>: nothing chosen (no target, no path, no goal or behaviour running but the always-on ones);</li>
 * <li>otherwise its <b>focus</b>: the foe, else the first goal or behaviour it's running, else where it's walking.</li>
 * </ul>
 * Kept only for the mob a spectating operator looks at, sampled when the view is sent ({@link NpcEyes#DEV_EVERY}).
 */
public final class NpcFocus {
	/** Stuck: with a path, less than this far across the ground (blocks) in this long (ticks, 3 s). */
	static final double STUCK_MOVE = 1.0;
	static final int STUCK_TICKS = 60;
	/** Re-planning: this many new paths within this long (ticks, 3 s). */
	static final int REPLAN_MANY = 3, REPLAN_TICKS = 60;
	/** A watch not sampled for this long (ticks) is begun afresh. */
	static final int WATCH_GAP = 20;
	/** At most this many path nodes are sent, this many goals or behaviours named, each name cut to this length. */
	static final int PATH_MAX = 48, NAMES_MAX = 4, NAME_LEN = 56;

	/** The state on the top line, as sent. */
	public static final int STEADY = 0, THINKING = 1, REPLANNING = 2, STUCK = 3;

	/**
	 * Goals and behaviours every mob runs all the time (looking about, floating, opening doors, following its path), left out of
	 * what it's running: they never say what it's after. The walking itself shows as its path.
	 */
	static final Set<String> BACKGROUND = Set.of("FloatGoal", "LookAtPlayerGoal", "RandomLookAroundGoal", "OpenDoorGoal", "InteractGoal",
		"LookAtTargetSink", "MoveToTargetSink", "Swim", "InteractWithDoor", "WakeUp", "CountDownCooldownTicks", "UpdateActivityFromSchedule");

	/** What it's doing, for the view. */
	public record Focus(int state, List<Component> lines, List<BlockPos> path, boolean reaches, @Nullable Vec3 walk) {
	}

	/** Each watched mob: its last path, when it made new ones, and where and when it last got somewhere. */
	private static final class Watch {
		@Nullable Path path;
		final ArrayDeque<Long> replans = new ArrayDeque<>();
		Vec3 anchor = Vec3.ZERO;
		long anchorAt, sampled = Long.MIN_VALUE;
	}

	private static final Map<Mob, Watch> WATCHES = new WeakHashMap<>();

	private NpcFocus() {
	}

	/** What {@code mob} is doing; {@code alert} is the fight senses' line ({@link NpcEyes}). */
	static Focus of(Mob mob, Component alert) {
		long now = mob.level().getGameTime();
		PathNavigation nav = mob.getNavigation();
		Path path = nav.getPath();
		boolean walking = path != null && !path.isDone();

		// ---- the watch: new paths, and headway ----
		Watch w = WATCHES.computeIfAbsent(mob, m -> new Watch());
		boolean fresh = now - w.sampled > WATCH_GAP;
		w.sampled = now;
		if (fresh) {
			w.path = path;
			w.replans.clear();
			w.anchor = mob.position();
			w.anchorAt = now;
		} else if (path != null && path != w.path) {
			w.replans.addLast(now);
		}
		w.path = path;
		while (!w.replans.isEmpty() && now - w.replans.peekFirst() > REPLAN_TICKS) {
			w.replans.pollFirst();
		}
		Vec3 pos = mob.position();
		double dx = pos.x - w.anchor.x, dz = pos.z - w.anchor.z;
		if (!walking || dx * dx + dz * dz > STUCK_MOVE * STUCK_MOVE) {
			w.anchor = pos;
			w.anchorAt = now;
		}
		long stuckFor = walking ? now - w.anchorAt : 0L;

		// ---- what it's running ----
		List<String> running = new ArrayList<>();
		for (WrappedGoal g : ((MobAccessor)mob).jaymoskingdoms$goals().getAvailableGoals()) {
			if (g.isRunning()) {
				add(running, name(g.getGoal()));
			}
		}
		Brain<?> brain = mob.getBrain();
		for (BehaviorControl<?> b : brain.getRunningBehaviors()) {
			add(running, b.debugString());
		}
		running.removeIf(BACKGROUND::contains);
		LivingEntity target = mob.getTarget();
		if (target == null || !target.isAlive()) {
			target = memory(brain, MemoryModuleType.ATTACK_TARGET);
		}
		if (target != null && !target.isAlive()) {
			target = null;
		}
		WalkTarget walkTo = memory(brain, MemoryModuleType.WALK_TARGET);
		Vec3 walk = walkTo == null ? null : walkTo.getTarget().currentPosition();

		// ---- the state on top ----
		int state;
		MutableComponent top;
		ThinkFirst.Sizing sizing = ThinkFirst.sizing(mob);
		Player sized = sizing == null ? null : mob.level().getPlayerByUUID(sizing.who());
		if (sized != null) {
			// (ThinkFirst) it has just picked out a player and is making up its mind about them
			state = THINKING;
			top = Component.translatable("gui.jaymoskingdoms.eyes.sizing_up", sized.getDisplayName(), seconds(sizing.left()));
		} else if (stuckFor >= STUCK_TICKS) {
			state = STUCK;
			top = Component.translatable(mob.onClimbable() && !mob.onGround() ? "gui.jaymoskingdoms.eyes.stuck_climbing" : "gui.jaymoskingdoms.eyes.stuck",
				seconds(stuckFor));
		} else if (w.replans.size() >= REPLAN_MANY) {
			state = REPLANNING;
			top = Component.translatable("gui.jaymoskingdoms.eyes.replanning", w.replans.size(), seconds(REPLAN_TICKS));
		} else if (target == null && !walking && running.isEmpty() && walk == null) {
			state = THINKING;
			top = Component.translatable("gui.jaymoskingdoms.eyes.thinking");
		} else {
			state = STEADY;
			Component focus;
			if (target != null) {
				focus = Component.translatable("gui.jaymoskingdoms.eyes.focus.target", target.getDisplayName());
			} else if (!running.isEmpty()) {
				focus = Component.literal(running.get(0));
			} else {
				BlockPos to = walking ? path.getTarget() : BlockPos.containing(walk);
				focus = Component.translatable("gui.jaymoskingdoms.eyes.focus.walking", to.getX(), to.getY(), to.getZ());
			}
			top = Component.translatable("gui.jaymoskingdoms.eyes.focus", focus);
		}

		// ---- the lines under it ----
		List<Component> lines = new ArrayList<>();
		lines.add(top);
		lines.add(Component.translatable("gui.jaymoskingdoms.eyes.alert", alert));
		if (mob instanceof Villager v) {
			NpcTasks.Task task = NpcTasks.of(v);
			if (task != null) {
				String key = "npc_task.jaymoskingdoms." + task.key();
				lines.add(Component.translatable("gui.jaymoskingdoms.eyes.task",
					Component.translatableWithFallback(key, task.plain() != null ? task.plain() : task.key())));
			}
		}
		Optional<Activity> activity = brain.getActiveNonCoreActivity();
		if (activity.isPresent()) {
			lines.add(Component.translatable("gui.jaymoskingdoms.eyes.activity", activity.get().getName()));
		}
		if (!running.isEmpty()) {
			String names = String.join(", ", running.subList(0, Math.min(NAMES_MAX, running.size())));
			lines.add(running.size() > NAMES_MAX
				? Component.translatable("gui.jaymoskingdoms.eyes.running_more", names, running.size() - NAMES_MAX)
				: Component.translatable("gui.jaymoskingdoms.eyes.running", names));
		}
		if (target != null) {
			lines.add(Component.translatable("gui.jaymoskingdoms.eyes.target", target.getDisplayName(), metres(mob.distanceTo(target))));
		}
		List<BlockPos> nodes = new ArrayList<>();
		boolean reaches = true;
		if (walking) {
			BlockPos to = path.getTarget();
			reaches = path.canReach();
			int left = path.getNodeCount() - path.getNextNodeIndex();
			if (reaches) {
				lines.add(Component.translatable("gui.jaymoskingdoms.eyes.path", to.getX(), to.getY(), to.getZ(),
					metres(Math.sqrt(mob.distanceToSqr(Vec3.atBottomCenterOf(to)))), left));
			} else {
				BlockPos end = path.getEndNode() == null ? to : path.getEndNode().asBlockPos();
				lines.add(Component.translatable("gui.jaymoskingdoms.eyes.path_short", to.getX(), to.getY(), to.getZ(),
					metres(Math.sqrt(end.distSqr(to))), left));
			}
			for (int i = path.getNextNodeIndex(); i < path.getNodeCount() && nodes.size() < PATH_MAX; i++) {
				nodes.add(path.getNodePos(i));
			}
		}
		if (walk != null && (!walking || walk.distanceToSqr(Vec3.atBottomCenterOf(path.getTarget())) > 4.0)) {
			BlockPos at = BlockPos.containing(walk);
			lines.add(Component.translatable("gui.jaymoskingdoms.eyes.walk", at.getX(), at.getY(), at.getZ(), metres(Math.sqrt(mob.distanceToSqr(walk)))));
		}
		return new Focus(state, lines, nodes, reaches, walk);
	}

	/** Adds {@code name} (cut to {@link #NAME_LEN}) once. */
	private static void add(List<String> names, String name) {
		if (name == null || name.isBlank()) {
			return;
		}
		String cut = name.length() > NAME_LEN ? name.substring(0, NAME_LEN - 1) + "…" : name;
		if (!names.contains(cut)) {
			names.add(cut);
		}
	}

	/** A goal's class name as the code has it (an unnamed one by its full name's last part), so it can be looked up. */
	private static String name(Object o) {
		String simple = o.getClass().getSimpleName();
		if (!simple.isEmpty()) {
			return simple;
		}
		String full = o.getClass().getName();
		return full.substring(full.lastIndexOf('.') + 1);
	}

	/** {@code brain}'s memory of {@code type}, or null (also when it has no such memory at all). */
	private static <U> @Nullable U memory(Brain<?> brain, MemoryModuleType<U> type) {
		Optional<U> held = brain.getMemoryInternal(type);
		return held == null ? null : held.orElse(null);
	}

	private static String seconds(long ticks) {
		return String.format("%.1f", ticks / 20.0);
	}

	private static String metres(double d) {
		return String.format("%.1f", d);
	}
}
