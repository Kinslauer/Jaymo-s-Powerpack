package com.jaymospowers.combat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.21.20, Kingdoms Rivals patch 7) Fights against many, the way the Arkham and Shadow of War games stage them (ideas only):
 * <ul>
 * <li><b>Crowds take turns.</b> Only {@link #ACTIVE} melee foes come at a player at once; the rest wait their turn in a ring
 * round them ({@link #RING}), circling, watching, and now and then shouting, and can't strike while they wait
 * ({@link #WAITING_TAG}: {@link AttackTells#windUp} and Kingdoms' fighters hold their blow, and {@code mixin/CrowdWaitMixin}
 * keeps a goal-driven one from closing in). A foe's turn lasts {@link #TURN_TICKS}; then the next nearest gets it. Bosses and
 * anything too big to circle ({@link AttackTells#HEAVY_WIDTH} wide) and archers never wait. Nothing changes with fewer than
 * {@link #ACTIVE} + 1 foes. Better Combat and Kingdoms' {@code RangedTactics} did none of this: one swings, the other moves
 * only shooters.</li>
 * <li><b>Counter one, turn to the next.</b> A parry while another foe's blow is coming ({@link IncomingBlows#aimed}) turns you
 * to face him, and parries strung together within {@link #CHAIN_TICKS} of each other make each riposte land harder
 * ({@link #CHAIN_BONUS} more a link, at most {@link #CHAIN_MAX} links).</li>
 * </ul>
 */
public final class Crowds {
	// ================================================================================================================
	// Tuning
	/** At most this many melee foes come at one player at once. */
	public static final int ACTIVE = 3;
	/** Foes after a player within this are counted. */
	public static final double RANGE = 14.0;
	/** Those waiting keep out at about this far, and back off if nearer than {@link #RING_IN}. */
	public static final double RING = 6.0, RING_IN = 4.5;
	/** How far round the ring (degrees) a waiting foe moves each repath; how often it repaths (ticks). */
	public static final double DRIFT = 25.0;
	public static final int REPATH = 20;
	/** A foe's turn lasts this long (ticks) while others wait. */
	public static final int TURN_TICKS = 100;
	/** Each step, the chance one waiting foe shouts. */
	public static final float SHOUT_CHANCE = 0.08F;
	/** How often (ticks) the crowds are looked at. */
	public static final int STEP = 5;
	/** A parry turns you to another blow coming from this near; parries this close together chain. */
	public static final double TURN_RANGE = 7.0;
	public static final int CHAIN_TICKS = 60;
	public static final float CHAIN_BONUS = 0.15F;
	public static final int CHAIN_MAX = 4;
	/** Anything with this much health or more is a boss: it never waits. */
	public static final float BOSS_HEALTH = 100.0F;
	// ================================================================================================================

	/** On a foe waiting its turn. Kingdoms' fighters read it too. */
	public static final String WAITING_TAG = "jaymospowers.waiting_turn";

	/** Each foe's turn: when it began. */
	private static final Map<Mob, Long> TURNS = new java.util.WeakHashMap<>();
	/** Those tagged waiting now. */
	private static final Set<Mob> WAITING = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());

	/** A blow on its way to a player: from whom, landing when. */
	private record Blow(LivingEntity attacker, long lands) {
	}

	private static final Map<UUID, List<Blow>> INCOMING = new HashMap<>();
	/** Each player's parry chain: its length and the last parry's time. */
	private record Chain(int links, long at) {
	}

	private static final Map<UUID, Chain> CHAINS = new HashMap<>();

	private Crowds() {
	}

	public static void init() {
		ServerTickEvents.END_LEVEL_TICK.register(level -> {
			if (level.getGameTime() % STEP == 0L) {
				step(level);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			TURNS.clear();
			WAITING.clear();
			INCOMING.clear();
			CHAINS.clear();
		});
	}

	/** Whether {@code mob} is waiting its turn. */
	public static boolean waiting(Mob mob) {
		return mob.entityTags().contains(WAITING_TAG);
	}

	// ---- crowds -------------------------------------------------------------------------------------------------------------

	private static void step(ServerLevel level) {
		long now = level.getGameTime();
		Set<Mob> waitingNow = new HashSet<>();
		for (ServerPlayer p : level.players()) {
			if (!p.isAlive() || p.isSpectator() || p.isCreative()) {
				continue;
			}
			List<Mob> crowd = level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(RANGE), m -> m.isAlive() && m.getTarget() == p && melee(m));
			if (crowd.size() <= ACTIVE) {
				continue;
			}
			// (those mid-turn keep it till it's up; then the nearest of the rest)
			List<Mob> order = new ArrayList<>(crowd);
			order.sort(Comparator.<Mob>comparingInt(m -> {
				Long since = TURNS.get(m);
				return since != null && now - since < TURN_TICKS ? 0 : since == null ? 1 : 2;
			}).thenComparingDouble(m -> m.distanceTo(p)));
			for (int i = 0; i < order.size(); i++) {
				Mob m = order.get(i);
				if (i < ACTIVE) {
					TURNS.putIfAbsent(m, now);
					if (WAITING.contains(m)) {
						// (its turn again: a fresh one)
						TURNS.put(m, now);
					}
				} else {
					if (TURNS.containsKey(m) && now - TURNS.get(m) >= TURN_TICKS) {
						// (its turn is over: back of the line, its next one fresh)
						TURNS.remove(m);
					}
					waitingNow.add(m);
					circle(level, m, p, now);
				}
			}
		}
		for (Mob m : List.copyOf(WAITING)) {
			if (!waitingNow.contains(m)) {
				m.removeTag(WAITING_TAG);
				WAITING.remove(m);
			}
		}
		for (Mob m : waitingNow) {
			if (WAITING.add(m)) {
				m.addTag(WAITING_TAG);
				if (m instanceof PathfinderMob pm) {
					pm.getNavigation().stop();
				}
			}
		}
		if (!waitingNow.isEmpty() && level.getRandom().nextFloat() < SHOUT_CHANCE) {
			List<Mob> all = new ArrayList<>(waitingNow);
			all.get(level.getRandom().nextInt(all.size())).playAmbientSound();
		}
	}

	/** A foe that fights hand to hand and can circle: not an archer, not a boss, not too big. */
	private static boolean melee(Mob m) {
		return m instanceof PathfinderMob && !(m instanceof RangedAttackMob) && !(m.getMainHandItem().getItem() instanceof ProjectileWeaponItem)
			&& m.getBbWidth() < AttackTells.HEAVY_WIDTH && m.getMaxHealth() < BOSS_HEALTH && !m.isPassenger();
	}

	/** A waiting foe keeps its distance, round the ring, watching. */
	private static void circle(ServerLevel level, Mob m, ServerPlayer p, long now) {
		m.getLookControl().setLookAt(p, 30.0F, 30.0F);
		if (!(m instanceof PathfinderMob pm) || (now + m.getId()) % REPATH >= STEP && m.distanceTo(p) >= RING_IN) {
			return;
		}
		Vec3 from = m.position().subtract(p.position());
		double angle = Math.atan2(from.z, from.x) + Math.toRadians(DRIFT) * (m.getUUID().getLeastSignificantBits() % 2 == 0 ? 1 : -1);
		Vec3 to = p.position().add(Math.cos(angle) * RING, 0.0, Math.sin(angle) * RING);
		pm.getNavigation().moveTo(to.x, p.getY(), to.z, 0.8);
	}

	// ---- counter one, turn to the next -------------------------------------------------------------------------------------

	/** (From {@link IncomingBlows#aimed}) A blow is on its way to {@code target}, landing in {@code ticks}. */
	static void incoming(LivingEntity attacker, ServerPlayer target, int ticks) {
		long now = target.level().getGameTime();
		List<Blow> blows = INCOMING.computeIfAbsent(target.getUUID(), k -> new ArrayList<>());
		blows.removeIf(b -> b.lands() < now || b.attacker() == attacker);
		blows.add(new Blow(attacker, now + ticks));
	}

	/**
	 * (From {@link Guard#parried}) {@code player} parried {@code attacker}: turned to the next blow coming, and the riposte's
	 * share for the chain so far.
	 */
	static float counter(ServerPlayer player, @Nullable LivingEntity attacker) {
		long now = player.level().getGameTime();
		Chain was = CHAINS.get(player.getUUID());
		int links = was != null && now - was.at() <= CHAIN_TICKS ? Math.min(CHAIN_MAX, was.links() + 1) : 0;
		CHAINS.put(player.getUUID(), new Chain(links, now));
		LivingEntity next = null;
		long soonest = Long.MAX_VALUE;
		for (Blow b : INCOMING.getOrDefault(player.getUUID(), List.of())) {
			LivingEntity a = b.attacker();
			if (a != attacker && a.isAlive() && b.lands() >= now && b.lands() < soonest && a.level() == player.level() && a.distanceTo(player) <= TURN_RANGE) {
				next = a;
				soonest = b.lands();
			}
		}
		if (next != null) {
			player.lookAt(EntityAnchorArgument.Anchor.EYES, next, EntityAnchorArgument.Anchor.EYES);
		}
		return 1.0F + CHAIN_BONUS * links;
	}
}
