package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.53) With Minecraft Comes Alive, a kingdom's court wizard ({@link Sorcery#WIZARD}) is one of its own villagers who
 * has grown old: they keep their name, family and hearts, put on the wizard's robe, hat and staff, and take the
 * profession {@code jaymoskingdoms:wizard}. MCA has no old age of its own (its villagers stay adults), so a villager's
 * age is counted from the day they came of age ({@link #grownDays}); one grown {@link #ELDER_DAYS} days is an elder,
 * and the eldest of a kingdom's spare villagers is the one who takes the office. A kingdom with no elder to spare waits
 * for one. The standalone wizard is no longer hired by kingdoms while MCA is loaded; any already standing retire.
 * <p>
 * The wizard lives as MCA's villagers do (talks, sleeps, keeps their home) until a foe of the realm comes near; then they
 * keep their distance and cast as the court wizard always has: arcane bolts, the ward, mending, the repulse, banishing.
 * Idle, they mend the townsfolk who are hurt.
 */
public final class CourtWizards {
	/** {@link Enlisted#grownSince}: not seen yet, or still a child. */
	public static final long UNKNOWN = Long.MIN_VALUE, CHILD = Long.MIN_VALUE + 1;
	/** Days grown before a villager counts as an elder (a Minecraft day is 20 minutes). */
	static final int ELDER_DAYS = 8;
	/** A villager first seen already grown has been for up to this many days (so a village has elders from the start). */
	static final int SPREAD_DAYS = 16;
	static final long DAY_TICKS = 24000L;
	/** A wizard backs away from a foe this near, and remembers whoever hit them this long (ticks). */
	static final double KEEP_AWAY = 7.0;
	static final int HIT_MEMORY = 200;
	/** How fast they walk to (or from) a fight, and how far past their reach they keep after a foe they have. */
	static final double FIGHT_SPEED = 0.65, CHASE_SLACK = 8.0;

	/** Each wizard's foe just now. */
	private static final Map<PathfinderMob, LivingEntity> FOES = new WeakHashMap<>();

	private CourtWizards() {
	}

	/** {@code role} is a kingdom's wizard, taken from its own villagers. */
	static boolean inPlace(Faction f, int role) {
		return McaCompat.LOADED && role == Sorcery.WIZARD && f.type().isRealm();
	}

	/** One of {@code f}'s elders (or {@code chosen}) made its court wizard and counted on its roster; null if it has none. */
	static @Nullable Worker enlist(ServerLevel level, Faction f, @Nullable PathfinderMob chosen) {
		return McaCompat.enlistWizard(level, f, chosen);
	}

	/** {@code /kingdom caster wizard}: the grown villager nearest {@code player} made {@code f}'s wizard, old or not. */
	static @Nullable Worker summon(ServerLevel level, Faction f, ServerPlayer player) {
		PathfinderMob nearest = McaCompat.nearestGrown(level, player);
		return nearest == null ? null : enlist(level, f, nearest);
	}

	/** Whether {@code stack} is the wizard's staff (put away when they leave the office). */
	public static boolean isStaff(ItemStack stack) {
		return stack.is(Arcana.WIZARD_STAFF);
	}

	// ---- age --------------------------------------------------------------------------------------------------------

	/** Notes when {@code v} came of age (from {@link Workers#step}, now and then). */
	static void age(ServerLevel level, Aging v) {
		long since = v.grownSince();
		if (v.mob().isBaby()) {
			if (since != CHILD) {
				v.setGrownSince(CHILD);
			}
		} else if (since == CHILD) {
			v.setGrownSince(level.getGameTime());
		} else if (since == UNKNOWN) {
			v.setGrownSince(level.getGameTime() - level.getRandom().nextInt((int)(SPREAD_DAYS * DAY_TICKS)));
		}
	}

	/** Whole days {@code v} has been grown, or -1 if they're young or not seen yet. */
	static long grownDays(ServerLevel level, Aging v) {
		long since = v.grownSince();
		return since == UNKNOWN || since == CHILD ? -1 : Math.max(0, (level.getGameTime() - since) / DAY_TICKS);
	}

	static boolean isElder(ServerLevel level, Aging v) {
		return grownDays(level, v) >= ELDER_DAYS;
	}

	// ---- the old wizards --------------------------------------------------------------------------------------------

	/** A standalone wizard of a kingdom, with MCA loaded: he retires (an elder will take his place). True if so. */
	static boolean retire(ServerLevel level, Folk folk) {
		Faction f = Factions.get(level.getServer(), folk.factionId());
		if (f == null || !inPlace(f, folk.role())) {
			return false;
		}
		level.sendParticles(ParticleTypes.ENCHANT, folk.getX(), folk.getY() + 1.0, folk.getZ(), 30, 0.4, 0.8, 0.4, 0.5);
		level.sendParticles(ParticleTypes.POOF, folk.getX(), folk.getY() + 1.0, folk.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
		Economy.lost(level.getServer(), f.id(), folk.role());
		folk.discard();
		return true;
	}

	// ---- their day ---------------------------------------------------------------------------------------------------

	/**
	 * A court wizard's step (from {@link Workers#step}): their timers run down; they mend their side and ward those
	 * fighting; true while they have a foe (their fight in place of their brain), false to live as a villager.
	 */
	static boolean step(ServerLevel level, Enlisted w) {
		int[] timers = w.spellTimers();
		for (int i = 0; i < timers.length; i++) {
			if (timers[i] > 0) {
				timers[i]--;
			}
		}
		PathfinderMob mob = w.mob();
		LivingEntity foe = FOES.get(mob);
		if (mob.tickCount % 10 != 0) {
			return foe != null;
		}
		foe = pickFoe(level, w, foe);
		if (foe == null) {
			FOES.remove(mob);
		} else {
			FOES.put(mob, foe);
			fight(level, w, foe);
		}
		support(level, w, foe);
		return foe != null;
	}

	/** Who they fight: the one they have while it's still a foe in reach, else the nearest foe they can see. */
	private static @Nullable LivingEntity pickFoe(ServerLevel level, Enlisted w, @Nullable LivingEntity current) {
		PathfinderMob mob = w.mob();
		if (current != null && foe(w, current) && mob.distanceTo(current) <= Sorcery.RANGE + CHASE_SLACK) {
			return current;
		}
		LivingEntity best = null;
		double bestD = Double.MAX_VALUE;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, mob.getBoundingBox().inflate(Sorcery.RANGE), e -> foe(w, e))) {
			double d = mob.distanceToSqr(e);
			if (d < bestD && mob.getSensing().hasLineOfSight(e)) {
				best = e;
				bestD = d;
			}
		}
		return best;
	}

	/** Out of reach or sight they close in; too near they back off; in reach they cast, as a standalone wizard does. */
	private static void fight(ServerLevel level, Enlisted w, LivingEntity foe) {
		PathfinderMob mob = w.mob();
		if (!isStaff(mob.getMainHandItem())) {
			// (whatever their day had them holding, the staff is back in hand)
			mob.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Arcana.WIZARD_STAFF));
		}
		mob.getLookControl().setLookAt(foe, 30.0F, 30.0F);
		double d = mob.distanceTo(foe);
		boolean sees = mob.getSensing().hasLineOfSight(foe);
		if (!sees || d > Sorcery.RANGE) {
			mob.getNavigation().moveTo(foe, FIGHT_SPEED);
			return;
		}
		if (d < KEEP_AWAY) {
			Vec3 away = mob.position().subtract(foe.position()).multiply(1.0, 0.0, 1.0);
			away = away.lengthSqr() < 1.0E-4 ? mob.getLookAngle().reverse() : away.normalize();
			Vec3 to = mob.position().add(away.scale(KEEP_AWAY));
			mob.getNavigation().moveTo(to.x, to.y, to.z, FIGHT_SPEED);
		} else {
			mob.getNavigation().stop();
		}
		if (mob.tickCount % Sorcery.CAST_TICKS != 0) {
			return;
		}
		int[] timers = w.spellTimers();
		mob.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		if (d <= Sorcery.CLOSE && timers[Sorcery.T_REPULSE] <= 0) {
			Sorcery.repulse(level, mob, timers, e -> foe(w, e));
		} else if (Thralls.isThrall(foe) && timers[Sorcery.T_BANISH] <= 0) {
			Sorcery.banish(level, mob, timers);
		} else {
			Sorcery.arcaneBolt(level, mob, foe);
		}
	}

	/** The ward on their side's fighters, else mending the worst hurt of the townsfolk, else banishing thralls. */
	private static void support(ServerLevel level, Enlisted w, @Nullable LivingEntity foe) {
		PathfinderMob mob = w.mob();
		int[] timers = w.spellTimers();
		List<LivingEntity> fighting = new ArrayList<>();
		List<LivingEntity> side = new ArrayList<>();
		side.add(mob);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, mob.getBoundingBox().inflate(Sorcery.SUPPORT_RANGE), e -> e != mob && e.isAlive())) {
			if (!ally(w, e)) {
				continue;
			}
			side.add(e);
			if (e instanceof Mob m && m.getTarget() != null && m.getTarget().isAlive()) {
				fighting.add(e);
			}
		}
		if (timers[Sorcery.T_WARD] <= 0 && (foe != null || !fighting.isEmpty())) {
			fighting.add(mob);
			Sorcery.ward(level, mob, timers, fighting);
		} else if (timers[Sorcery.T_MEND] <= 0) {
			Sorcery.mend(level, mob, timers, side);
		} else if (foe != null && timers[Sorcery.T_BANISH] <= 0) {
			Sorcery.banish(level, mob, timers);
		}
	}

	/** {@code e} is of the wizard's side: their realm's folk (or an ally's), its villagers, its iron golems. */
	private static boolean ally(Enlisted w, LivingEntity e) {
		if (e instanceof Folk f) {
			return sameSide(w, f.factionId());
		}
		if (e instanceof Worker other && other.factionId() >= 0) {
			return sameSide(w, other.factionId());
		}
		return e instanceof AbstractVillager || e instanceof net.minecraft.world.entity.animal.golem.IronGolem;
	}

	private static boolean sameSide(Enlisted w, int faction) {
		if (Hermits.is(w)) {
			// (0.5.60) a hermit has no realm
			return false;
		}
		if (faction == w.factionId()) {
			return true;
		}
		MinecraftServer server = w.mob().level().getServer();
		return server != null && Relations.between(server, w.factionId(), faction) == Relation.ALLIED;
	}

	/**
	 * {@code e} is a foe of the wizard's: a player the realm counts hostile (or one who just struck them), a barbarian or
	 * the folk of a realm at war with theirs, a monster, or anything going for their side. Never their own side, a
	 * player's thrall the realm doesn't mind, or a creative player.
	 */
	static boolean foe(Enlisted w, LivingEntity e) {
		PathfinderMob mob = w.mob();
		if (e == mob || !e.isAlive() || e instanceof Player p && (p.isCreative() || p.isSpectator()) || ally(w, e) || Thralls.refuses(mob, e)) {
			return false;
		}
		if (e == mob.getLastHurtByMob() && mob.tickCount - mob.getLastHurtByMobTimestamp() < HIT_MEMORY) {
			return true;
		}
		if (e instanceof ServerPlayer player) {
			// (0.5.60) a hermit minds only who strikes them
			return !Hermits.is(w) && Reputation.get(player, w.factionId()) <= Reputation.HOSTILE;
		}
		if (e instanceof Folk folk) {
			if (Hermits.is(w)) {
				return folk.style() == FactionType.BARBARIAN;
			}
			MinecraftServer server = mob.level().getServer();
			return folk.style() == FactionType.BARBARIAN || server != null && Relations.between(server, w.factionId(), folk.factionId()) == Relation.WAR;
		}
		if (e instanceof Mob m && m.getTarget() != null && (m.getTarget() == mob || ally(w, m.getTarget()))) {
			return true;
		}
		return e instanceof Enemy;
	}

	/** A court wizard died: a player who killed them takes a scroll from the body, as from any caster. */
	static void died(ServerLevel level, Worker w, DamageSource source) {
		if (w.role() == Sorcery.WIZARD && source.getEntity() instanceof ServerPlayer) {
			w.mob().spawnAtLocation(level, Arcana.scrollFor(Sorcery.WIZARD, level.getRandom()));
		}
		FOES.remove(w.mob());
	}

	/** {@code entity} is a court wizard: a standalone one, or (with MCA) a villager in the office. */
	public static boolean is(@Nullable Entity entity) {
		return entity instanceof Worker w && w.role() == Sorcery.WIZARD && entity.isAlive();
	}
}
