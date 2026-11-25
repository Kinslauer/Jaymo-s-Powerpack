package com.jaymoskingdoms;

import com.jaymoskingdoms.mixin.MobAccessor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import net.minecraft.world.entity.npc.InventoryCarrier;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.item.component.UseRemainder;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.consume_effects.ConsumeEffect;
import net.minecraft.world.item.consume_effects.TeleportRandomlyConsumeEffect;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Patch 2 of the fighting AI (break off and mend; the ideas are Hostile Humans', none of its code): a badly hurt fighter
 * gets out of the fight, mends with what it carries, and comes back. Folk and illagers through {@link MendGoal} and
 * {@link HoldGoal}; MCA's villagers through {@link #brainStep} (from {@code Workers.step}, their brain paused while this
 * has their body).
 * <ul>
 * <li><b>Fall back</b>: under {@link #RETREAT} of its health a fighter breaks off and walks away from whoever is on it,
 * along a path checked node by node (no lava, fire or other harm on it, no drop it would take fall damage from), for up
 * to {@link #RETREAT_TICKS} or {@link #RETREAT_BLOCKS}. It lets go of its foe meanwhile, so it neither swings nor shoots.</li>
 * <li><b>Mend</b>: with no foe within {@link #HEAL_CLEAR} it uses one thing it carries every {@link #HEAL_GAP}: a bandage
 * ({@link PackUse#BANDAGES}), food (the plainest first, never one that harms, the precious last; a bowl or bottle kept), or a
 * healing or regeneration potion, from its hands or its pack (an MCA villager's or a pillager's inventory, a folk's
 * {@link BeltPouch}). Each is spent. Nothing in hand or pack, no mending: such a fighter falls back once a fight (to get clear and
 * call for cover), then fights on hurt till the fight is over.</li>
 * <li><b>Come back</b>: at {@link #RETURN} or more (or after {@link #MEND_LONGEST} out) it rejoins: to its leader if he's
 * away from it (a band's or the player it follows), then its foe; with no foe left, its post (a mob with goals walks there in
 * {@link MendGoal}).</li>
 * <li><b>Nerve</b>: the fearless and the reckless ({@link Temperaments#fearless}, {@link Duellists#reckless}) break off only
 * under {@link #RETREAT_BOLD}, the timid under {@link #RETREAT_TIMID}; a band leader near ({@link BandLeaders}) shouts and
 * the band holds {@link #LEADER_HOLDS} longer.</li>
 * <li><b>Cornered</b>: with no safe way out (no safe spot, or pressed again with every leg used) it fights on.</li>
 * <li><b>Cover</b>: every friend within {@link #COVER_NEAR} who leans GUARD or MEDIC ({@link Comrades}; the nearest
 * {@link #COVER_MAX}) steps between the one falling back and a foe chasing it for {@link #COVER_TICKS}, and takes that foe on.</li>
 * </ul>
 * Lines ({@link Callouts}): {@code fallback}, {@code cover} (with friends to cover), {@code back}, and the leader's
 * {@code lead.hold}. Never against a downed body, never a downed fighter falling back, never a guard making an arrest.
 */
public final class BreakOff {
	/** Health shares: under this it falls back (the fearless and reckless, the timid); at this or more it comes back. */
	static final float RETREAT = 0.20F, RETREAT_BOLD = 0.10F, RETREAT_TIMID = 0.30F, RETURN = 0.35F;
	/** A band leader near holds his band this much longer (a share of health off the line). */
	static final float LEADER_HOLDS = 0.05F;
	/** Falling back lasts this long at most (ticks), and goes this far at most (blocks); spots are looked for at least this far off. */
	static final int RETREAT_TICKS = 160;
	static final double RETREAT_BLOCKS = 16.0, RETREAT_LEAST = 8.0;
	/** Spots tried, the arc (degrees either side of straight away) they're tried in, and how many of the best get a path worked out. */
	static final int SAMPLES = 16, PATH_TRIES = 4;
	static final double ARC = 80.0;
	/** A path to a spot is no longer than this (nodes), and passes no nearer than this to a foe (blocks) after its first nodes. */
	static final int PATH_NODES = 28;
	static final double PASS_FOE = 2.5;
	/** How fast it falls back, comes back and covers (a share of its speed). */
	static final double RETREAT_SPEED = 1.2, BACK_SPEED = 1.0, COVER_SPEED = 1.2;
	/** Mending: no foe this near (blocks); one thing this often (ticks); given up and back after this long out (ticks). */
	static final double HEAL_CLEAR = 8.0;
	static final int HEAL_GAP = 30, MEND_LONGEST = 300;
	/** A foe this near (blocks) while it mends presses it: another leg, at most this many in all, then it fights. */
	static final double PRESSED = 4.0;
	static final int LEGS = 3;
	/** Foes counted this far round it (blocks); whoever struck it counts for this long (ticks). */
	static final double THREAT_RANGE = 16.0;
	static final int STRUCK_RECENT = 100;
	/** Back to a leader farther than this (blocks), for this long at most (ticks); its foe is taken on again within this (blocks). */
	static final double LEADER_NEAR = 6.0, REJOIN_RANGE = 32.0;
	static final int BACK_TICKS = 100;
	/** After coming back it doesn't break off again for this long (ticks); cornered, it fights at least this long (ticks). */
	static final int AGAIN_GAP = 300, CORNER_GAP = 100;
	/** With nothing to mend with it falls back once a fight; a fight is over once it has had no foe this long (ticks). */
	static final int FIGHT_OVER = 200;
	/** Back to its post with no foe left: walked for this long at most (ticks). */
	static final int HOME_TICKS = 200;
	/** Cover: friends this near (blocks), at most this many (for the cost), for this long (ticks); they stand this far in front (blocks). */
	static final double COVER_NEAR = 8.0, COVER_GAP = 2.5;
	static final int COVER_MAX = 6, COVER_TICKS = 100;
	/** A cover walks when this far from its spot (blocks), and stops this near. */
	static final double COVER_GO = 3.0, COVER_AT = 1.5;
	/** A fighter is looked at for breaking off this often (ticks). */
	static final int LOOK = 5;
	/** Food that would harm or carry it off: never eaten to mend (as well as any whose eating brings on a harmful effect). */
	static final Set<Item> NOT_FOOD = Set.of(Items.ROTTEN_FLESH, Items.SPIDER_EYE, Items.POISONOUS_POTATO, Items.PUFFERFISH, Items.CHORUS_FRUIT,
		Items.SUSPICIOUS_STEW);
	/** Food too precious to eat before the plain (as well as any above common rarity): eaten last, after potions. */
	static final Set<Item> PRECIOUS_FOOD = Set.of(Items.GOLDEN_APPLE, Items.ENCHANTED_GOLDEN_APPLE, Items.GOLDEN_CARROT);

	enum Phase {
		AWAY, MEND, BACK
	}

	private static final class State {
		@Nullable LivingEntity foe;
		Phase phase = Phase.AWAY;
		Vec3 from;
		long phaseStart, nextHeal;
		@Nullable BlockPos dest;
		int legs = 1;
		@Nullable LivingEntity leader;

		State(@Nullable LivingEntity foe, Vec3 from, long now) {
			this.foe = foe;
			this.from = from;
			this.phaseStart = now;
		}
	}

	private static final class Cover {
		final Mob ward;
		final LivingEntity foe;
		final long until;
		boolean walking;

		Cover(Mob ward, LivingEntity foe, long until) {
			this.ward = ward;
			this.foe = foe;
			this.until = until;
		}
	}

	private static final Map<Mob, State> STATES = new WeakHashMap<>();
	private static final Map<Mob, Cover> COVERS = new WeakHashMap<>();
	/** Not before (game time): after coming back, or fighting on cornered. */
	private static final Map<Mob, Long> NOT_BEFORE = new WeakHashMap<>();
	/** Vanilla illagers given the goals already (folk add them themselves). */
	private static final Set<Mob> GIVEN = Collections.newSetFromMap(new WeakHashMap<>());
	/** Fell back this fight with nothing to mend with: last seen in it (game time). It fights on hurt until the fight is over. */
	private static final Map<Mob, Long> EMPTY = new WeakHashMap<>();
	/** On its way back to its post with no foe left (a mob with goals): until (game time). */
	private static final Map<Mob, Long> HOMEWARD = new WeakHashMap<>();

	private BreakOff() {
	}

	static void register() {
		// (a folk's belt pouch, what it mends from)
		BeltPouch.register();
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof AbstractIllager illager && GIVEN.add(illager)) {
				((MobAccessor)illager).jaymoskingdoms$goals().addGoal(MendGoal.PRIORITY, new MendGoal(illager));
				((MobAccessor)illager).jaymoskingdoms$targets().addGoal(0, new HoldGoal(illager));
			}
		});
	}

	// ---- who ---------------------------------------------------------------------------------------------------------

	/** {@code m} is falling back, mending, or on its way back. */
	static boolean away(Mob m) {
		if (!STATES.containsKey(m)) {
			return false;
		}
		if (m.isAlive() && !m.isRemoved()) {
			return true;
		}
		// (dead or gone while away: nothing steps it any more, so its state goes now)
		STATES.remove(m);
		COVERS.values().removeIf(c -> c.ward == m);
		return false;
	}

	/** A fighter this is for: on its feet, thinking, not tending, arresting or held, and not a ruler or a posted archer. */
	private static boolean able(ServerLevel level, Mob m) {
		if (!m.isAlive() || m.isRemoved() || m.isBaby() || m.isNoAi() || m.isPassenger() || m.isSleeping() || Downed.isDowned(m)
			|| !Temperaments.humanoid(m)) {
			return false;
		}
		if (Arrests.busy(m) || Arrests.held(m) || FieldMedics.busy(m) || Downed.helping(m) || Grapples.busy(m) || Grapples.held(m)) {
			return false;
		}
		if (m instanceof Folk f) {
			return f.combatant() && !Rulers.leaderRole(f.role()) && !f.isCarrying() && !(f.getVehicle() instanceof Cannon) && !WallArchers.posted(level, f);
		}
		if (m instanceof Villager v) {
			return !(v instanceof Worker w && Rulers.leaderRole(w.role()));
		}
		return m instanceof AbstractIllager && GIVEN.contains(m);
	}

	/** The share of its health under which {@code m} falls back, by its nerve. */
	static float line(Mob m) {
		if (Temperaments.fearless(m) || Duellists.reckless(m)) {
			return RETREAT_BOLD;
		}
		return Temperaments.is(m, Temperaments.Temperament.TIMID) ? RETREAT_TIMID : RETREAT;
	}

	private static float share(LivingEntity e) {
		return e.getHealth() / Math.max(1.0F, e.getMaxHealth());
	}

	/** On its feet and in this fight: alive, here, not downed, not a creative or spectating player. */
	private static boolean valid(@Nullable LivingEntity e, Mob m) {
		return e != null && e.isAlive() && !e.isRemoved() && e.level() == m.level() && !Downed.isDowned(e)
			&& !(e instanceof Player p && (p.isCreative() || p.isSpectator()));
	}

	// ---- breaking off -----------------------------------------------------------------------------------------------

	/** Looks at whether {@code m} should break off now, and does it. */
	private static void consider(ServerLevel level, Mob m) {
		if (STATES.containsKey(m) || (m.tickCount + m.getId()) % LOOK != 0) {
			return;
		}
		LivingEntity foe = Comrades.target(m);
		float share = share(m);
		if (!valid(foe, m)) {
			return;
		}
		long now = level.getGameTime();
		Long emptySeen = EMPTY.get(m);
		if (emptySeen != null) {
			// (fell back with nothing to mend with: a fresh fight once it has been without a foe a while)
			if (now - emptySeen > FIGHT_OVER) {
				EMPTY.remove(m);
			} else {
				EMPTY.put(m, now);
			}
		}
		if (share >= RETREAT_TIMID) {
			return;
		}
		if (now < NOT_BEFORE.getOrDefault(m, 0L) || !able(level, m) || Comrades.busy(m)) {
			return;
		}
		float line = line(m);
		LivingEntity leader = BandLeaders.leaderOf(level, m, Comrades.side(m));
		if (leader != null) {
			line -= LEADER_HOLDS;
			if (share < line + LEADER_HOLDS && share >= line && leader instanceof Mob shouter) {
				// he shouts, and this one holds on a little longer
				Callouts.say(shouter, "lead.hold", m, false, m.getDisplayName());
			}
		}
		if (share >= line) {
			return;
		}
		boolean empty = find(m) == null;
		if (empty && EMPTY.containsKey(m)) {
			// nothing to mend with, and it has fallen back once this fight already: it fights on hurt
			return;
		}
		List<LivingEntity> threats = threats(level, m, foe);
		BlockPos spot = safeSpot(level, m, threats);
		if (spot == null) {
			// cornered: no safe way out, so it fights on
			NOT_BEFORE.put(m, now + CORNER_GAP);
			return;
		}
		if (empty) {
			// (once, to get clear and call for cover)
			EMPTY.put(m, now);
		}
		State s = new State(foe, m.position(), now);
		s.dest = spot;
		STATES.put(m, s);
		RangedTactics.cancel(m);
		Lean.lean(m, 0);
		Comrades.stand(m);
		Path path = m.getNavigation().createPath(spot, 0);
		if (path != null) {
			m.getNavigation().moveTo(path, RETREAT_SPEED);
		}
		int covered = cover(level, m, threats, now);
		Callouts.say(m, covered > 0 ? "cover" : "fallback", foe, true);
	}

	/** Whoever is on {@code m}: its foe, whoever struck it lately, and every mob set on it; on their feet, not its side. */
	private static List<LivingEntity> threats(ServerLevel level, Mob m, @Nullable LivingEntity foe) {
		List<LivingEntity> out = new ArrayList<>();
		String mine = Comrades.side(m);
		if (valid(foe, m) && foe.distanceToSqr(m) < THREAT_RANGE * THREAT_RANGE) {
			out.add(foe);
		}
		LivingEntity struck = m.getLastHurtByMob();
		if (valid(struck, m) && !out.contains(struck) && m.tickCount - m.getLastHurtByMobTimestamp() < STRUCK_RECENT
			&& struck.distanceToSqr(m) < THREAT_RANGE * THREAT_RANGE && !Comrades.side(struck).equals(mine)) {
			out.add(struck);
		}
		for (Mob e : level.getEntitiesOfClass(Mob.class, m.getBoundingBox().inflate(THREAT_RANGE), e -> e != m && Comrades.target(e) == m && valid(e, m))) {
			if (!out.contains(e) && !Comrades.side(e).equals(mine)) {
				out.add(e);
			}
		}
		return out;
	}

	private static double nearest(Vec3 at, List<LivingEntity> threats) {
		double best = Double.MAX_VALUE;
		for (LivingEntity t : threats) {
			best = Math.min(best, t.position().distanceTo(at));
		}
		return best;
	}

	/** A spot away from {@code threats} it can walk to safely, or null: it is cornered. */
	private static @Nullable BlockPos safeSpot(ServerLevel level, Mob m, List<LivingEntity> threats) {
		Vec3 here = m.position();
		Vec3 centre = Vec3.ZERO;
		for (LivingEntity t : threats) {
			centre = centre.add(t.position());
		}
		double base;
		if (threats.isEmpty()) {
			base = m.getRandom().nextDouble() * Math.PI * 2.0;
		} else {
			centre = centre.scale(1.0 / threats.size());
			Vec3 away = here.subtract(centre);
			base = away.horizontalDistanceSqr() < 1.0E-4 ? m.getRandom().nextDouble() * Math.PI * 2.0 : Math.atan2(away.z, away.x);
		}
		double now = threats.isEmpty() ? 0.0 : nearest(here, threats);
		List<BlockPos> spots = new ArrayList<>();
		List<Double> scores = new ArrayList<>();
		for (int i = 0; i < SAMPLES; i++) {
			double angle = base + Math.toRadians((m.getRandom().nextDouble() * 2.0 - 1.0) * ARC);
			double r = RETREAT_LEAST + m.getRandom().nextDouble() * (RETREAT_BLOCKS - 2.0 - RETREAT_LEAST);
			BlockPos ground = RangedTactics.ground(level, new Vec3(here.x + Math.cos(angle) * r, here.y, here.z + Math.sin(angle) * r), m.getBlockY());
			if (ground == null || harmful(level, ground)) {
				continue;
			}
			double score = threats.isEmpty() ? r : nearest(Vec3.atBottomCenterOf(ground), threats);
			if (!threats.isEmpty() && score < now + 3.0) {
				// (not really away from them)
				continue;
			}
			int at = 0;
			while (at < scores.size() && scores.get(at) >= score) {
				at++;
			}
			spots.add(at, ground);
			scores.add(at, score);
		}
		for (int i = 0; i < Math.min(PATH_TRIES, spots.size()); i++) {
			Path path = m.getNavigation().createPath(spots.get(i), 0);
			if (path != null && path.canReach() && safe(level, m, path, threats)) {
				return spots.get(i);
			}
		}
		return null;
	}

	/** Every node of {@code path} is free of harm, no step down is a fall that would hurt, and it doesn't pass by a foe. */
	private static boolean safe(ServerLevel level, Mob m, Path path, List<LivingEntity> threats) {
		if (path.getNodeCount() > PATH_NODES) {
			return false;
		}
		double fall = m.getAttributeValue(Attributes.SAFE_FALL_DISTANCE);
		int y = m.getBlockY();
		for (int i = 0; i < path.getNodeCount(); i++) {
			Node n = path.getNode(i);
			PathType t = n.type;
			if (t == PathType.LAVA || t == PathType.FIRE || t == PathType.FIRE_IN_NEIGHBOR || t == PathType.DAMAGING || t == PathType.DAMAGING_IN_NEIGHBOR
				|| t == PathType.DAMAGE_CAUTIOUS || t == PathType.POWDER_SNOW) {
				return false;
			}
			if (y - n.y >= fall || harmful(level, n.asBlockPos())) {
				return false;
			}
			if (i >= 2 && nearest(n.asVec3(), threats) < PASS_FOE) {
				return false;
			}
			y = n.y;
		}
		return true;
	}

	/** Lava or fire on the spot or next to it, or a magma floor. */
	static boolean harmful(ServerLevel level, BlockPos at) {
		if (level.getBlockState(at.below()).is(Blocks.MAGMA_BLOCK)) {
			return true;
		}
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				for (int dy = -1; dy <= 0; dy++) {
					BlockPos p = at.offset(dx, dy, dz);
					if (level.getFluidState(p).is(FluidTags.LAVA) || level.getBlockState(p).is(BlockTags.FIRE)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/** Another leg away (pressed while it mends); false if there's no safe way (cornered). */
	private static boolean leg(ServerLevel level, Mob m, State s, List<LivingEntity> threats, long now) {
		if (s.legs >= LEGS) {
			return false;
		}
		BlockPos spot = safeSpot(level, m, threats);
		if (spot == null) {
			return false;
		}
		s.legs++;
		s.phase = Phase.AWAY;
		s.phaseStart = now;
		s.from = m.position();
		s.dest = spot;
		Path path = m.getNavigation().createPath(spot, 0);
		if (path != null) {
			m.getNavigation().moveTo(path, RETREAT_SPEED);
		}
		Callouts.say(m, "fallback", s.foe, false);
		return true;
	}

	// ---- the step ----------------------------------------------------------------------------------------------------

	/** One tick: true if this has {@code m}'s legs (falling back, mending, coming back, or walking to cover a friend). */
	static boolean step(Mob m) {
		if (!(m.level() instanceof ServerLevel level)) {
			return false;
		}
		State s = STATES.get(m);
		if (s == null) {
			return coverStep(m, level.getGameTime());
		}
		long now = level.getGameTime();
		if (!able(level, m)) {
			// downed, taken up with something else, or gone: dropped where it is
			end(m, now, false);
			return false;
		}
		switch (s.phase) {
			case AWAY -> away(level, m, s, now);
			case MEND -> mend(level, m, s, now);
			case BACK -> back(level, m, s, now);
		}
		return STATES.containsKey(m);
	}

	private static void away(ServerLevel level, Mob m, State s, long now) {
		Comrades.stand(m);
		boolean far = m.position().distanceTo(s.from) >= RETREAT_BLOCKS;
		boolean there = s.dest == null || m.position().distanceToSqr(Vec3.atBottomCenterOf(s.dest)) < 2.0;
		if (far || there || now - s.phaseStart >= RETREAT_TICKS) {
			m.getNavigation().stop();
			s.phase = Phase.MEND;
			s.phaseStart = now;
			return;
		}
		if (m.getNavigation().isDone() && (now - s.phaseStart) % 10 == 0) {
			// (something stopped it: on again, along a path checked as safe, or it stops here)
			Path path = m.getNavigation().createPath(s.dest, 0);
			if (path != null && path.canReach() && safe(level, m, path, threats(level, m, s.foe))) {
				m.getNavigation().moveTo(path, RETREAT_SPEED);
			} else {
				s.phase = Phase.MEND;
				s.phaseStart = now;
			}
		}
	}

	private static void mend(ServerLevel level, Mob m, State s, long now) {
		Comrades.stand(m);
		if (share(m) >= RETURN || now - s.phaseStart >= MEND_LONGEST) {
			comeBack(level, m, s, now);
			return;
		}
		if ((now - s.phaseStart) % LOOK != 0) {
			return;
		}
		List<LivingEntity> threats = threats(level, m, s.foe);
		LivingEntity close = null;
		double best = HEAL_CLEAR;
		for (LivingEntity t : threats) {
			double d = t.distanceTo(m);
			if (d < best) {
				best = d;
				close = t;
			}
		}
		if (close != null) {
			m.getLookControl().setLookAt(close, 30.0F, 30.0F);
			if (best < PRESSED && !leg(level, m, s, threats, now)) {
				// pressed with nowhere left to go: it fights on
				end(m, now, false);
				NOT_BEFORE.put(m, now + CORNER_GAP);
				Comrades.aim(m, close);
			}
			return;
		}
		if (now >= s.nextHeal && use(level, m)) {
			s.nextHeal = now + HEAL_GAP;
		}
	}

	private static void comeBack(ServerLevel level, Mob m, State s, long now) {
		s.phase = Phase.BACK;
		s.phaseStart = now;
		Callouts.say(m, "back", s.foe, true);
		String side = Comrades.side(m);
		LivingEntity leader = Comrades.playerOf(level, side);
		if (leader == null) {
			leader = BandLeaders.leaderOf(level, m, side);
		}
		s.leader = valid(leader, m) && leader.distanceTo(m) > LEADER_NEAR ? leader : null;
		if (s.leader == null) {
			end(m, now, true);
		}
	}

	private static void back(ServerLevel level, Mob m, State s, long now) {
		LivingEntity leader = s.leader;
		if (!valid(leader, m) || leader.distanceTo(m) <= LEADER_NEAR || now - s.phaseStart >= BACK_TICKS) {
			end(m, now, true);
			return;
		}
		Comrades.stand(m);
		if ((now - s.phaseStart) % 10 == 0 || m.getNavigation().isDone()) {
			m.getNavigation().moveTo(leader, BACK_SPEED);
		}
	}

	/** It's done ({@code rejoin}: back to its foe, or with none, its post); its covers are let go. */
	private static void end(Mob m, long now, boolean rejoin) {
		State s = STATES.remove(m);
		COVERS.values().removeIf(c -> c.ward == m);
		if (s == null) {
			return;
		}
		NOT_BEFORE.put(m, now + AGAIN_GAP);
		if (EMPTY.containsKey(m)) {
			// (still this fight: the time away doesn't count as the fight gone quiet)
			EMPTY.put(m, now);
		}
		if (!rejoin) {
			return;
		}
		if (valid(s.foe, m) && s.foe.distanceTo(m) < REJOIN_RANGE) {
			Comrades.aim(m, s.foe);
		} else if (!(m instanceof Villager) && m.hasHome()) {
			// (a villager's brain takes it home itself; a mob with goals keeps its MendGoal for the walk, see homeward)
			BlockPos post = m.getHomePosition();
			if (m.getNavigation().moveTo(post.getX() + 0.5, post.getY(), post.getZ() + 0.5, BACK_SPEED)) {
				HOMEWARD.put(m, now + HOME_TICKS);
			}
		}
	}

	/** Walking back to its post (from {@link #end}): till it's there, has a foe again, or has walked {@link #HOME_TICKS}. */
	private static boolean homeward(Mob m) {
		Long until = HOMEWARD.get(m);
		if (until == null) {
			return false;
		}
		if (m.level().getGameTime() > until || m.getNavigation().isDone() || Comrades.target(m) != null || !m.isAlive() || Downed.isDowned(m)) {
			HOMEWARD.remove(m);
			return false;
		}
		return true;
	}

	// ---- mending -----------------------------------------------------------------------------------------------------

	/** What it carries that mends: a bandage (cheapest first), then food (the plainest first), then a potion. */
	private record Mender(ItemStack stack, @Nullable SimpleContainer pack, int rank) {
	}

	private static int rank(ItemStack s) {
		if (s.isEmpty()) {
			return -1;
		}
		String id = BuiltInRegistries.ITEM.getKey(s.getItem()).toString();
		for (int i = 0; i < PackUse.BANDAGES.length; i++) {
			if (PackUse.BANDAGES[i].equals(id)) {
				return i;
			}
		}
		FoodProperties food = s.get(DataComponents.FOOD);
		if (food != null && !NOT_FOOD.contains(s.getItem()) && !harmful(s)) {
			// (the plainest first; the precious last of all)
			return (PRECIOUS_FOOD.contains(s.getItem()) || s.getRarity() != Rarity.COMMON ? PRECIOUS : 10) + food.nutrition();
		}
		return mendingPotion(s) ? POTION : -1;
	}

	/** Ranks past the bandages and plain food: a potion, then precious food. */
	private static final int POTION = 100, PRECIOUS = 200;

	/** Eating it brings on an effect that isn't good for it, or carries it off. */
	private static boolean harmful(ItemStack s) {
		Consumable consumable = s.get(DataComponents.CONSUMABLE);
		if (consumable == null) {
			return false;
		}
		for (ConsumeEffect effect : consumable.onConsumeEffects()) {
			if (effect instanceof TeleportRandomlyConsumeEffect) {
				return true;
			}
			if (effect instanceof ApplyStatusEffectsConsumeEffect apply) {
				for (MobEffectInstance e : apply.effects()) {
					if (!e.getEffect().value().isBeneficial()) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/** A drinkable potion with healing or regeneration in it. */
	private static boolean mendingPotion(ItemStack s) {
		if (!s.is(Items.POTION)) {
			return false;
		}
		PotionContents contents = s.get(DataComponents.POTION_CONTENTS);
		if (contents == null) {
			return false;
		}
		for (MobEffectInstance effect : contents.getAllEffects()) {
			if (effect.getEffect().is(MobEffects.INSTANT_HEALTH) || effect.getEffect().is(MobEffects.REGENERATION)) {
				return true;
			}
		}
		return false;
	}

	private static @Nullable Mender find(Mob m) {
		Mender best = null;
		for (ItemStack s : new ItemStack[] {m.getMainHandItem(), m.getOffhandItem()}) {
			int r = rank(s);
			if (r >= 0 && (best == null || r < best.rank())) {
				best = new Mender(s, null, r);
			}
		}
		SimpleContainer pack = m instanceof InventoryCarrier carrier ? carrier.getInventory() : BeltPouch.open(m);
		if (pack != null) {
			for (int i = 0; i < pack.getContainerSize(); i++) {
				ItemStack s = pack.getItem(i);
				int r = rank(s);
				if (r >= 0 && (best == null || r < best.rank())) {
					best = new Mender(s, pack, r);
				}
			}
		}
		return best;
	}

	/** {@code m} uses one thing it carries on itself, spending it. False if it carries nothing that mends. */
	private static boolean use(ServerLevel level, Mob m) {
		Mender found = find(m);
		if (found == null) {
			return false;
		}
		ItemStack one = found.stack().copyWithCount(1);
		found.stack().shrink(1);
		if (found.pack() != null) {
			found.pack().setChanged();
		}
		m.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		if (found.rank() < PackUse.BANDAGES.length) {
			m.heal(PackUse.BANDAGE_HEAL[found.rank()]);
			m.playSound(SoundEvents.ARMOR_EQUIP_LEATHER.value(), 0.8F, 1.0F);
			Callouts.say(m, "pack.bandage", null, false, one.getHoverName());
		} else if (found.rank() != POTION) {
			FoodProperties food = one.get(DataComponents.FOOD);
			m.heal(Math.max(1.0F, food == null ? 1.0F : food.nutrition()));
			Consumable consumable = one.get(DataComponents.CONSUMABLE);
			if (consumable != null) {
				// (a golden apple's regeneration, and the like)
				for (ConsumeEffect effect : consumable.onConsumeEffects()) {
					effect.apply(level, one, m);
				}
			}
			m.playSound(SoundEvents.GENERIC_EAT.value(), 0.8F, 1.0F);
			// a stew's bowl, a honey bottle's bottle: kept, or left at its feet
			UseRemainder remainder = one.get(DataComponents.USE_REMAINDER);
			if (remainder != null) {
				keep(level, m, found.pack(), remainder.convertInto().create());
			}
			Callouts.say(m, "pack.eat", null, false, one.getHoverName());
		} else {
			PotionContents contents = one.get(DataComponents.POTION_CONTENTS);
			if (contents != null) {
				contents.applyToLivingEntity(m, 1.0F);
			}
			m.playSound(SoundEvents.GENERIC_DRINK.value(), 0.8F, 1.0F);
			// the bottle is kept, or left at its feet
			keep(level, m, found.pack(), new ItemStack(Items.GLASS_BOTTLE));
			Callouts.say(m, "pack.drink", null, false);
		}
		return true;
	}

	/** What's left of a thing used (a bottle, a bowl) goes back in the pack it came from, or is left at its feet. */
	private static void keep(ServerLevel level, Mob m, @Nullable SimpleContainer pack, ItemStack left) {
		if (pack != null) {
			left = pack.addItem(left);
		}
		if (!left.isEmpty()) {
			m.spawnAtLocation(level, left);
		}
	}

	// ---- friends covering --------------------------------------------------------------------------------------------

	/**
	 * Every friend within {@link #COVER_NEAR} of {@code ward} who leans GUARD or MEDIC (the nearest {@link #COVER_MAX}) steps between
	 * it and a foe chasing it: the chasers shared out among them, the nearest chaser first (with none set on it, the nearest foe).
	 * How many came.
	 */
	private static int cover(ServerLevel level, Mob ward, List<LivingEntity> threats, long now) {
		List<LivingEntity> chasers = new ArrayList<>();
		for (LivingEntity t : threats) {
			if (t instanceof Mob chaser && Comrades.target(chaser) == ward) {
				chasers.add(t);
			}
		}
		if (chasers.isEmpty()) {
			LivingEntity nearest = null;
			double best = Double.MAX_VALUE;
			for (LivingEntity t : threats) {
				double d = t.distanceToSqr(ward);
				if (d < best) {
					best = d;
					nearest = t;
				}
			}
			if (nearest == null) {
				return 0;
			}
			chasers.add(nearest);
		}
		chasers.sort((a, b) -> Double.compare(a.distanceToSqr(ward), b.distanceToSqr(ward)));
		String side = Comrades.side(ward);
		List<Mob> friends = level.getEntitiesOfClass(Mob.class, ward.getBoundingBox().inflate(COVER_NEAR), a -> a != ward
			&& a.distanceToSqr(ward) <= COVER_NEAR * COVER_NEAR && coverer(level, a) && Comrades.side(a).equals(side));
		friends.sort((a, b) -> Double.compare(a.distanceToSqr(ward), b.distanceToSqr(ward)));
		int came = 0;
		for (Mob a : friends) {
			if (came >= COVER_MAX) {
				break;
			}
			LivingEntity foe = chasers.get(came % chasers.size());
			COVERS.put(a, new Cover(ward, foe, now + COVER_TICKS));
			Comrades.aim(a, foe);
			came++;
		}
		return came;
	}

	private static boolean coverer(ServerLevel level, Mob a) {
		if (STATES.containsKey(a) || COVERS.containsKey(a) || !able(level, a) || Comrades.busy(a) || share(a) < line(a)) {
			return false;
		}
		Comrades.Leanings l = Comrades.leanings(a);
		return l.has(Comrades.Leaning.GUARD) || l.has(Comrades.Leaning.MEDIC);
	}

	/** Where a cover stands: just in front of the one falling back, toward the foe. */
	private static Vec3 between(Mob ward, LivingEntity foe) {
		Vec3 to = foe.position().subtract(ward.position());
		double d = Math.sqrt(to.horizontalDistanceSqr());
		if (d < 1.0E-3) {
			return ward.position();
		}
		return ward.position().add(to.x / d * Math.min(COVER_GAP, d * 0.5), 0.0, to.z / d * Math.min(COVER_GAP, d * 0.5));
	}

	/** Whether a cover is still on, and wants to walk (true: it has the legs this tick). */
	private static boolean coverWalks(Mob a, long now, boolean drive) {
		Cover c = COVERS.get(a);
		if (c == null) {
			return false;
		}
		if (now > c.until || !STATES.containsKey(c.ward) || !(a.level() instanceof ServerLevel level) || !able(level, a) || !valid(c.foe, a)) {
			COVERS.remove(a);
			return false;
		}
		Vec3 spot = between(c.ward, c.foe);
		double d = Math.sqrt(a.position().subtract(spot).horizontalDistanceSqr());
		if (d < COVER_AT || !c.walking && d < COVER_GO) {
			c.walking = false;
			return false;
		}
		if (drive) {
			c.walking = true;
			if (a.tickCount % 10 == 0 || a.getNavigation().isDone()) {
				BlockPos ground = RangedTactics.ground(level, spot, c.ward.getBlockY());
				Vec3 to = ground == null ? spot : Vec3.atBottomCenterOf(ground);
				a.getNavigation().moveTo(to.x, to.y, to.z, COVER_SPEED);
			}
			a.getLookControl().setLookAt(c.foe, 30.0F, 30.0F);
		}
		return true;
	}

	private static boolean coverStep(Mob a, long now) {
		return coverWalks(a, now, true);
	}

	// ---- hooks -------------------------------------------------------------------------------------------------------

	/**
	 * For an MCA villager (from {@code Workers.step}, before {@link RangedTactics#brainStep}): true to skip its brain this
	 * tick, this having its body.
	 */
	public static boolean brainStep(Mob mob) {
		if (!(mob.level() instanceof ServerLevel level)) {
			return false;
		}
		consider(level, mob);
		boolean took = step(mob);
		if (took && mob instanceof Villager v) {
			v.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		}
		return took;
	}

	/** Falling back, mending, coming back and covering, for a mob with goals (folk, illagers): it takes the legs and the eyes. */
	public static final class MendGoal extends Goal {
		/**
		 * Ahead of every other goal that moves it (the shooters' and weapon sense's run at 0), so its check runs every tick and it
		 * takes the legs as soon as the fighter breaks off.
		 */
		public static final int PRIORITY = -1;
		private final Mob mob;

		public MendGoal(Mob mob) {
			this.mob = mob;
			this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			if (!(this.mob.level() instanceof ServerLevel level)) {
				return false;
			}
			consider(level, this.mob);
			return STATES.containsKey(this.mob) || coverWalks(this.mob, level.getGameTime(), false);
		}

		@Override
		public boolean canContinueToUse() {
			return STATES.containsKey(this.mob) || COVERS.containsKey(this.mob) && COVERS.get(this.mob).walking || homeward(this.mob);
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			step(this.mob);
		}

		@Override
		public void stop() {
			Cover c = COVERS.get(this.mob);
			if (c != null) {
				c.walking = false;
			}
			if (!STATES.containsKey(this.mob)) {
				this.mob.getNavigation().stop();
			}
		}
	}

	/** While it's away nothing sets it on a foe again (its target goals wait). */
	public static final class HoldGoal extends Goal {
		private final Mob mob;

		public HoldGoal(Mob mob) {
			this.mob = mob;
			this.setFlags(EnumSet.of(Flag.TARGET));
		}

		@Override
		public boolean canUse() {
			return STATES.containsKey(this.mob);
		}

		@Override
		public boolean canContinueToUse() {
			return STATES.containsKey(this.mob);
		}

		@Override
		public void start() {
			this.mob.setTarget(null);
		}
	}
}
