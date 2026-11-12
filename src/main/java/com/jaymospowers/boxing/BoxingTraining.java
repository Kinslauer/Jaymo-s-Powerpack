package com.jaymospowers.boxing;

import com.jaymospowers.JaymosPowers;
import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.zombie.Zombie;
import org.jspecify.annotations.Nullable;

/**
 * (Jung) How a boxing style ({@link Boxing}) is learned and raised. The teachers themselves are Kingdoms' (its
 * {@code BoxingTeachers}: the Fist school's master, village elders, veteran guards), which calls {@link #lesson},
 * {@link #lessonCount}, {@link #fistKill} and {@link #trained} here by name; everything they decide is in this class.
 * <ul>
 * <li><b>Learning a style</b>: a teacher sets the task, {@link #LEARN_KILLS} hostile mobs killed with fist gear in
 * hand. Back with the teacher, the style is learned at level 1. A second style only once every known one is mastered
 * ({@link Boxing#canLearn}).</li>
 * <li><b>Levels</b> come from fighting in the style: each punch landed on something that has hit the boxer in the last
 * {@link #RECENT_TICKS} is a point (only an opponent who hits back counts), {@link #XP_PER_LEVEL} points a level. A
 * guard's drill gives {@link #trained} points too.</li>
 * <li><b>The teacher's tasks</b>: at levels {@link #GATE_LEVELS} the style rises no further until a teacher has set a
 * task and it is done, with fist gear in hand: armoured zombies, then an orc, then a bandit or an orc chief
 * ({@link #GATE_KINDS}, {@link #GATE_KILLS}).</li>
 * </ul>
 * Nothing is written in chat: the teacher speaks (Kingdoms' bubbles) and a level is a sound.
 */
public final class BoxingTraining {
	// ---- tuning ----
	/** Hostile mobs to kill with fist gear to learn a style. */
	public static final int LEARN_KILLS = 15;
	/** Points a level, and how lately (ticks) a target must have hit the boxer for a punch on it to be one. */
	public static final int XP_PER_LEVEL = 80, RECENT_TICKS = 100;
	/** What was killed, as {@link #fistKill} is told it. A bandit counts where a chief is asked for. */
	public static final String HOSTILE = "hostile", ARMOURED_ZOMBIE = "armoured_zombie", ORC = "orc", CHIEF = "chief", BANDIT = "bandit";
	/** The levels a style waits at for a teacher's task, what each task hunts, and how many. */
	static final int[] GATE_LEVELS = {3, 6, 9};
	static final String[] GATE_KINDS = {ARMOURED_ZOMBIE, ORC, CHIEF};
	static final int[] GATE_KILLS = {5, 1, 1};

	/** The style being learned ({@link Boxing.Style}'s place plus one; 0 for none) in the low four bits, the kills above them. Saved. */
	public static final AttachmentType<Integer> LEARNING = AttachmentRegistry.create(JaymosPowers.id("boxing_learning"),
		builder -> builder.persistent(Codec.INT).copyOnDeath());
	/** Every style's points, ten bits each in {@link Boxing.Style} order. Saved. */
	public static final AttachmentType<Integer> XP = AttachmentRegistry.create(JaymosPowers.id("boxing_xp"),
		builder -> builder.persistent(Codec.INT).copyOnDeath());
	/** Every style's tasks, eight bits each: tasks done (two bits), a task set (one bit), its kills (five bits). Saved. */
	public static final AttachmentType<Integer> TASKS = AttachmentRegistry.create(JaymosPowers.id("boxing_tasks"),
		builder -> builder.persistent(Codec.INT).copyOnDeath());

	/** boxer → (who hit them, entity id → the game time of it). */
	private static final Map<UUID, Map<Integer, Long>> HIT_BY = new HashMap<>();
	/** boxer → the last kill counted (entity id), so one death is one kill whoever reports it. */
	private static final Map<UUID, Integer> COUNTED = new HashMap<>();

	private BoxingTraining() {
	}

	static void init() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (entity instanceof ServerPlayer player && source.getEntity() instanceof LivingEntity attacker && attacker != player
				&& Boxing.style(player) != null) {
				Map<Integer, Long> by = HIT_BY.computeIfAbsent(player.getUUID(), id -> new HashMap<>());
				long now = player.level().getGameTime();
				if (by.size() > 32) {
					by.values().removeIf(time -> now - time > RECENT_TICKS);
				}
				by.put(attacker.getId(), now);
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (source.getEntity() instanceof ServerPlayer player && source.getDirectEntity() == player) {
				if (entity instanceof Zombie zombie && armoured(zombie)) {
					fistKill(player, entity, ARMOURED_ZOMBIE);
				} else if (entity instanceof Enemy) {
					fistKill(player, entity, HOSTILE);
				}
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			HIT_BY.remove(handler.player.getUUID());
			COUNTED.remove(handler.player.getUUID());
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			HIT_BY.clear();
			COUNTED.clear();
		});
	}

	private static boolean armoured(Zombie zombie) {
		return !zombie.getItemBySlot(EquipmentSlot.HEAD).isEmpty() || !zombie.getItemBySlot(EquipmentSlot.CHEST).isEmpty()
			|| !zombie.getItemBySlot(EquipmentSlot.LEGS).isEmpty() || !zombie.getItemBySlot(EquipmentSlot.FEET).isEmpty();
	}

	// ---- stored numbers ----------------------------------------------------------------------------------------------

	private static int stored(ServerPlayer player, AttachmentType<Integer> type) {
		Integer value = player.getAttached(type);
		return value == null ? 0 : value;
	}

	private static Boxing.@Nullable Style learning(ServerPlayer player) {
		int index = stored(player, LEARNING) & 0xF;
		return index >= 1 && index <= Boxing.Style.values().length ? Boxing.Style.values()[index - 1] : null;
	}

	private static int learnKills(ServerPlayer player) {
		return stored(player, LEARNING) >> 4;
	}

	/** {@code player}'s points in {@code style}. */
	public static int xp(ServerPlayer player, Boxing.Style style) {
		return stored(player, XP) >> style.ordinal() * 10 & 0x3FF;
	}

	private static void setXp(ServerPlayer player, Boxing.Style style, int xp) {
		int shift = style.ordinal() * 10;
		player.setAttached(XP, stored(player, XP) & ~(0x3FF << shift) | Mth.clamp(xp, 0, 0x3FF) << shift);
	}

	/** The style's task bits: done (0-3), set, kills. */
	private static int tasks(ServerPlayer player, Boxing.Style style) {
		return stored(player, TASKS) >> style.ordinal() * 8 & 0xFF;
	}

	private static void setTasks(ServerPlayer player, Boxing.Style style, int done, boolean set, int kills) {
		int shift = style.ordinal() * 8;
		int bits = Mth.clamp(done, 0, 3) | (set ? 4 : 0) | Mth.clamp(kills, 0, 31) << 3;
		player.setAttached(TASKS, stored(player, TASKS) & ~(0xFF << shift) | bits << shift);
	}

	/** The level {@code style} can't pass yet: the first task not done, or the top. */
	private static int ceiling(ServerPlayer player, Boxing.Style style) {
		int done = tasks(player, style) & 3;
		return done < GATE_LEVELS.length ? GATE_LEVELS[done] : Boxing.MAX_LEVEL;
	}

	// ---- levels ------------------------------------------------------------------------------------------------------

	/** Gives {@code player} points in the style they box in, and the levels they come to (never past a task not done). */
	private static void gain(ServerPlayer player, int points) {
		Boxing.Style style = Boxing.style(player);
		if (style == null || points <= 0) {
			return;
		}
		int level = Boxing.level(player, style);
		if (level <= 0 || level >= Boxing.MAX_LEVEL) {
			return;
		}
		int ceiling = ceiling(player, style);
		// points fill up to the level a task holds the style at, and wait there
		int xp = Math.min(xp(player, style) + points, ceiling * XP_PER_LEVEL);
		setXp(player, style, xp);
		raise(player, style);
	}

	/** Brings {@code style}'s level up to what its points and tasks allow; a sound when it rises. */
	private static void raise(ServerPlayer player, Boxing.Style style) {
		int level = Boxing.level(player, style);
		int earned = Math.min(ceiling(player, style), 1 + xp(player, style) / XP_PER_LEVEL);
		if (earned > level) {
			Boxing.setLevel(player, style, earned);
			player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7F, 1.2F);
		}
	}

	/** A punch of {@code boxer}'s landed on {@code target}: a point, if that target has hit them lately. */
	static void landed(ServerPlayer boxer, LivingEntity target, long now) {
		Map<Integer, Long> by = HIT_BY.get(boxer.getUUID());
		Long hit = by == null ? null : by.get(target.getId());
		if (hit != null && now - hit <= RECENT_TICKS) {
			gain(boxer, 1);
		}
	}

	/** A guard's drill done while boxing: {@code points} toward the style (Kingdoms). */
	public static void trained(ServerPlayer player, int points) {
		if (Boxing.boxing(player)) {
			gain(player, points);
		}
	}

	// ---- kills -------------------------------------------------------------------------------------------------------

	/**
	 * {@code player} killed {@code victim}, a {@code kind} ({@link #HOSTILE}, {@link #ARMOURED_ZOMBIE}, {@link #ORC},
	 * {@link #CHIEF}, {@link #BANDIT}). Counts only with fist gear in hand: toward the style being learned (any kind), or
	 * toward the task set for the style they box in (its kind). Kingdoms reports its own folk; one death counts once.
	 */
	public static void fistKill(ServerPlayer player, LivingEntity victim, String kind) {
		if (!BoxingGear.isGear(player.getMainHandItem())) {
			return;
		}
		Boxing.Style learning = learning(player);
		if (learning != null) {
			Integer counted = COUNTED.put(player.getUUID(), victim.getId());
			if (counted != null && counted == victim.getId()) {
				return;
			}
			int kills = Math.min(LEARN_KILLS, learnKills(player) + 1);
			player.setAttached(LEARNING, learning.ordinal() + 1 | kills << 4);
			if (kills == LEARN_KILLS) {
				// the task is done: back to the teacher
				player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5F, 1.6F);
			}
			return;
		}
		Boxing.Style style = Boxing.style(player);
		if (style == null) {
			return;
		}
		int bits = tasks(player, style);
		int done = bits & 3;
		if ((bits & 4) == 0 || done >= GATE_KINDS.length) {
			return;
		}
		String wanted = GATE_KINDS[done];
		// (a bandit will do where a chief is asked for, and a chief where an orc is)
		if (!wanted.equals(kind) && !(wanted.equals(CHIEF) && BANDIT.equals(kind)) && !(wanted.equals(ORC) && CHIEF.equals(kind))) {
			return;
		}
		int kills = (bits >> 3) + 1;
		if (kills >= GATE_KILLS[done]) {
			setTasks(player, style, done + 1, false, 0);
			raise(player, style);
		} else {
			setTasks(player, style, done, true, kills);
		}
	}

	// ---- the lesson --------------------------------------------------------------------------------------------------

	/**
	 * {@code player} asks a teacher of {@code styleId} ({@link Boxing.Style#id}, or anything else for a teacher of every
	 * style) about boxing. Does what the visit earns and answers what happened, for the teacher to put into words:
	 * {@code begin} (the learning task is set), {@code learning} (it isn't done), {@code learned}, {@code one_at_a_time}
	 * (master what you know first), {@code taken_up} (a known style, boxed in again), {@code task_0}/{@code task_1}/
	 * {@code task_2} (a task is set), {@code task_wait} (it isn't done), {@code train} (keep fighting), {@code mastered}.
	 */
	public static String lesson(ServerPlayer player, String styleId) {
		Boxing.Style learning = learning(player);
		if (learning != null) {
			if (learnKills(player) < LEARN_KILLS) {
				return "learning";
			}
			player.setAttached(LEARNING, 0);
			return Boxing.learn(player, learning) ? "learned" : "one_at_a_time";
		}
		Boxing.Style style = Boxing.Style.byId(styleId);
		if (style == null) {
			// a teacher of every style: the one you box in while there's more of it to learn, else the next you can learn
			Boxing.Style mine = Boxing.style(player);
			style = mine;
			if (mine == null || Boxing.mastered(player, mine)) {
				for (Boxing.Style each : Boxing.Style.values()) {
					if (Boxing.canLearn(player, each)) {
						style = each;
						break;
					}
				}
			}
			if (style == null) {
				style = Boxing.Style.values()[0];
			}
		}
		int level = Boxing.level(player, style);
		if (level <= 0) {
			if (!Boxing.canLearn(player, style)) {
				return "one_at_a_time";
			}
			player.setAttached(LEARNING, style.ordinal() + 1);
			COUNTED.remove(player.getUUID());
			return "begin";
		}
		if (Boxing.style(player) != style) {
			Boxing.use(player, style);
			return "taken_up";
		}
		if (level >= Boxing.MAX_LEVEL) {
			return "mastered";
		}
		int bits = tasks(player, style);
		int done = bits & 3;
		if (done < GATE_LEVELS.length && level >= GATE_LEVELS[done] && xp(player, style) >= GATE_LEVELS[done] * XP_PER_LEVEL) {
			if ((bits & 4) != 0) {
				return "task_wait";
			}
			setTasks(player, style, done, true, 0);
			return "task_" + done;
		}
		return "train";
	}

	/** What is left of the task {@code player} is on (kills to go), for the teacher's words; 0 if none. */
	public static int lessonCount(ServerPlayer player) {
		if (learning(player) != null) {
			return LEARN_KILLS - learnKills(player);
		}
		Boxing.Style style = Boxing.style(player);
		if (style == null) {
			return 0;
		}
		int bits = tasks(player, style);
		int done = bits & 3;
		return (bits & 4) != 0 && done < GATE_KILLS.length ? GATE_KILLS[done] - (bits >> 3) : 0;
	}
}
