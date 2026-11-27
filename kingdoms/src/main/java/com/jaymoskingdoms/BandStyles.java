package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Patch 7 of the fighting AI (band styles; the ideas are Hostile Humans', none of its code): how each kind of band fights. Folk run it
 * through {@link StyleGoal} (from {@link Folk#registerGoals}), MCA's villagers through {@link #brainStep} (from {@code Workers.step});
 * the slowing blow and the second arrow are heard wherever they happen.
 * <ul>
 * <li><b>Heavy hitters</b> (st-heavy, {@link #heavyHitter}: a barbarian camp's raiders and chief, an orc grunt or warchief): a blow of
 * theirs that lands slows the struck for a moment ({@link #HEAVY_SLOW_TICKS}). From {@link #LEAP_MIN} to {@link #LEAP_MAX} blocks off
 * they now and then ({@link #LEAP_CHANCE}, {@link #LEAP_COOLDOWN} apart) leap in to strike: a visible jump at the foe with nothing in the
 * way, its blow coming as it comes down (it counts as {@link Blows}' timed jump, so a heavy weapon's lands as a falling critical).</li>
 * <li><b>Shield advance</b> (st-shield, {@link #shieldBearer}: a realm's soldier, not a bowman, with a shield in the off hand): closing on
 * its foe from within {@link #ADVANCE_RANGE} it keeps the shield up ({@link MobShields#advance}) and comes on at {@link #ADVANCE_PACE} of
 * its pace, harder to knock back ({@link #ADVANCE_FIRM}); its foe in reach of its blow, the shield comes down to swing (the foe in reach
 * always comes first). In a fight, each shield-bearing neighbour of its own side close by ({@link Formations#shieldNeighbours}) makes it
 * firmer still ({@link #NEIGHBOUR_FIRM}, up to {@link #NEIGHBOURS_MOST}).</li>
 * <li><b>Double shot</b> (st-archers, {@link #skilled}: the Huntsman's rangers, a road veteran, a realm's veteran or leader of the bow):
 * each bow shot is followed {@link #SECOND_SHOT_GAP} ticks later by a second, through its own shot (a folk's
 * {@link Folk#performRangedAttack}, an MCA villager's own), each arrow spent from its quiver ({@link MobAmmo}): none left, no second.</li>
 * </ul>
 * The fourth style, the dodgers' roll, two blades and shield on the back, is {@link NpcDodge}'s and {@link DualBlades}'. Never at a downed
 * body, never from one making an arrest or otherwise busy ({@link Comrades#busy}).
 */
public final class BandStyles {
	// ---- st-heavy ----
	/** A heavy hitter's blow that lands slows the struck this long (ticks), at this level (0 is Slowness I). */
	static final int HEAVY_SLOW_TICKS = 30, HEAVY_SLOW_LEVEL = 1;
	/** The leap: a foe from this far to this far (blocks, eyes to its body), at most this much higher or lower (blocks). */
	static final double LEAP_MIN = 3.0, LEAP_MAX = 6.5, LEAP_RISE = 1.5;
	/** The least time between two leaps (ticks); one is rolled for at most this often (ticks), with this chance. */
	static final int LEAP_COOLDOWN = 100, LEAP_ROLL_EVERY = 20;
	static final float LEAP_CHANCE = 0.35F;
	/** The leap's lift (blocks a tick), its carry for each block to cover (blocks a tick), at least and at most; it lands this short (blocks). */
	static final double LEAP_UP = 0.45, LEAP_CARRY_PER_BLOCK = 0.125, LEAP_LEAST = 0.3, LEAP_MOST = 0.75, LEAP_SHORT = 1.0;

	// ---- st-shield ----
	/** A foe this near (blocks) is advanced on behind the shield; the advance is held this long past each tick it's kept (ticks). */
	static final double ADVANCE_RANGE = 14.0;
	static final int ADVANCE_HOLD = 3;
	/** Its pace while it advances behind the shield (a share of its own), and how much harder it is to knock back. */
	static final double ADVANCE_PACE = 0.8, ADVANCE_FIRM = 0.35;
	/** Each shield-bearing neighbour this near (blocks) adds this much; counted up to this many, this often (ticks). */
	static final double NEIGHBOUR_RANGE = 2.5, NEIGHBOUR_FIRM = 0.15;
	static final int NEIGHBOURS_MOST = 3, NEIGHBOUR_EVERY = 10;

	// ---- st-archers ----
	/** The second arrow goes this long (ticks) after the first. */
	static final int SECOND_SHOT_GAP = 5;
	/** Who is what is looked at again this often (ticks). */
	static final int KIND_EVERY = 100;

	private static final Identifier ADVANCE_PACE_ID = JaymosKingdoms.id("band_advance_pace"), FIRM_ID = JaymosKingdoms.id("band_firm");

	private static final class State {
		/** What it is (looked at every {@link #KIND_EVERY}): a heavy hitter, a realm's shield-bearer. */
		boolean heavy, soldier;
		long kindAt = Long.MIN_VALUE / 2;
		/** Its last step (game time); a state not stepped for a moment has its modifiers taken off. */
		long stepped;
		long nextLeap, nextLeapRoll;
		/** Advancing behind its shield; the firmness it has on now; shield-bearing neighbours, counted at. */
		boolean advancing;
		double firm;
		int neighbours;
		long neighboursAt = Long.MIN_VALUE / 2;
		/** A second arrow due (game time; 0 none), at whom. */
		long shotAt;
		@Nullable LivingEntity shotAtFoe;
	}

	private static final Map<Mob, State> STATES = new WeakHashMap<>();
	/** The shooter loosing a second arrow just now (its own shot then plans no third). */
	private static @Nullable Mob following;

	private BandStyles() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(BandStyles::tick);
		ServerLivingEntityEvents.AFTER_DAMAGE.register(BandStyles::struck);
		NpcDodge.register();
		DualBlades.register();
	}

	private static State state(Mob mob) {
		return STATES.computeIfAbsent(mob, m -> new State());
	}

	// ---- who -------------------------------------------------------------------------------------------------------------

	/** (st-heavy) A barbarian camp's raider or chief (not a bandit's, nor a smuggler's), or an orc grunt or warchief; folk or MCA villager. */
	static boolean heavyHitter(Mob mob) {
		if (!(mob instanceof Worker w)) {
			return false;
		}
		int role = w.role();
		if (role == Orcs.GRUNT || role == Orcs.WARCHIEF) {
			return true;
		}
		if (role != Folk.BARBARIAN && role != Folk.CHIEF) {
			return false;
		}
		MinecraftServer server = mob.level().getServer();
		if (server == null || NpcDodge.bandit(mob)) {
			return false;
		}
		Faction camp = Factions.get(server, w.factionId());
		return camp != null && camp.type() == FactionType.BARBARIAN && Camps.BARBARIAN.equals(Camps.kind(server, w.factionId()));
	}

	/** (st-shield) A realm's soldier ({@link Ranks#of}), not of the bow. Its shield is looked at as it fights. */
	static boolean realmSoldier(Mob mob) {
		int code = Ranks.of(mob);
		return code >= 0 && !Ranks.bow(code);
	}

	/** (st-shield) A realm's soldier with a shield in the off hand and a hand free for it ({@link MobShields#shieldHand}). */
	static boolean shieldBearer(Mob mob) {
		return mob.getOffhandItem().has(DataComponents.BLOCKS_ATTACKS) && MobShields.shieldHand(mob) && realmSoldier(mob);
	}

	/**
	 * (st-archers) A skilled archer: one of the Huntsman's rangers ({@link ForestRangers#TAG}), a road veteran or a fallen knight
	 * ({@link Veterans#grade}), or a realm's veteran or leader of the bow ({@link Ranks}).
	 */
	static boolean skilled(Mob mob) {
		if (mob.entityTags().contains(ForestRangers.TAG) || Veterans.grade(mob) >= Veterans.VETERAN) {
			return true;
		}
		int code = Ranks.of(mob);
		return code >= 0 && Ranks.bow(code) && Ranks.step(code) >= Ranks.VETERAN;
	}

	/** A fight this is for: alive, on its feet, not riding, not busy (an arrest, falling back), its foe alive and on its feet. */
	private static boolean fighting(Mob mob, @Nullable LivingEntity foe) {
		return foe != null && foe.isAlive() && !Downed.isDowned(foe) && mob.isAlive() && !Downed.isDowned(mob) && !mob.isPassenger() && !mob.isSleeping()
			&& !Comrades.busy(mob);
	}

	// ---- the step --------------------------------------------------------------------------------------------------------

	/** A folk's band style each tick (no flags: it runs beside its other goals). */
	static final class StyleGoal extends Goal {
		private final Folk folk;

		StyleGoal(Folk folk) {
			this.folk = folk;
			this.setFlags(EnumSet.noneOf(Goal.Flag.class));
		}

		@Override
		public boolean canUse() {
			return this.folk.combatant() && this.folk.getTarget() != null;
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			step(this.folk);
		}
	}

	/**
	 * (from {@code Workers.step}, before an MCA villager's brain) Its band style this tick; true while it rolls out of a blow
	 * ({@link NpcDodge}: its brain waits).
	 */
	public static boolean brainStep(Mob mob) {
		if (!McaCompat.isMca(mob)) {
			return false;
		}
		if (Rulers.target(mob) != null) {
			step(mob);
		}
		return NpcDodge.rolling(mob);
	}

	/** One tick of {@code mob}'s style against its foe. */
	static void step(Mob mob) {
		if (!(mob.level() instanceof ServerLevel level)) {
			return;
		}
		LivingEntity foe = Rulers.target(mob);
		long now = level.getGameTime();
		State s = STATES.get(mob);
		if (!fighting(mob, foe)) {
			if (s != null) {
				ease(mob, s);
			}
			return;
		}
		if (s == null) {
			s = state(mob);
		}
		s.stepped = now;
		if (now - s.kindAt >= KIND_EVERY) {
			s.kindAt = now;
			s.heavy = heavyHitter(mob);
			s.soldier = realmSoldier(mob);
		}
		if (s.heavy) {
			leap(level, mob, foe, s, now);
		}
		if (s.soldier && mob.getOffhandItem().has(DataComponents.BLOCKS_ATTACKS) && MobShields.shieldHand(mob)) {
			advance(level, mob, foe, s, now);
		} else if (s.advancing || s.firm != 0.0) {
			ease(mob, s);
		}
	}

	// ---- st-heavy --------------------------------------------------------------------------------------------------------

	/** Now and then, from a few blocks off with a clear way, it leaps at its foe to strike as it comes down. */
	private static void leap(ServerLevel level, Mob mob, LivingEntity foe, State s, long now) {
		if (now < s.nextLeap || now < s.nextLeapRoll || !mob.onGround() || mob.isInWater() || mob.isInLava() || mob.onClimbable() || mob.isUsingItem()
			|| MobCombat.swinging(mob) || mob.getMainHandItem().getItem() instanceof ProjectileWeaponItem || mob.entityTags().contains(MobCombat.WAITING_TAG)) {
			return;
		}
		double gap = MobCombat.gap(mob, foe);
		if (gap < Math.max(LEAP_MIN, Blows.hitReach(mob) + Blows.JUMP_TRAVEL) || gap > LEAP_MAX || Math.abs(foe.getY() - mob.getY()) > LEAP_RISE) {
			return;
		}
		s.nextLeapRoll = now + LEAP_ROLL_EVERY;
		if (mob.getRandom().nextFloat() >= LEAP_CHANCE || !mob.getSensing().hasLineOfSight(foe) || !clearFlight(level, mob, foe)) {
			return;
		}
		Vec3 to = new Vec3(foe.getX() - mob.getX(), 0.0, foe.getZ() - mob.getZ());
		double flat = to.length();
		if (flat < 1.0E-3) {
			return;
		}
		double cover = flat - (mob.getBbWidth() + foe.getBbWidth()) / 2.0 - LEAP_SHORT;
		double carry = Math.clamp(cover * LEAP_CARRY_PER_BLOCK, LEAP_LEAST, LEAP_MOST);
		Vec3 dir = to.scale(1.0 / flat);
		// (where it comes down, a block short of the foe: ground to land on, no drop, no lava or fire)
		double landX = mob.getX() + dir.x * Math.max(0.0, cover), landZ = mob.getZ() + dir.z * Math.max(0.0, cover);
		if (!Blows.landing(level, BlockPos.containing(landX, foe.getY(), landZ)) && !Blows.landing(level, BlockPos.containing(landX, mob.getY(), landZ))) {
			return;
		}
		mob.getNavigation().stop();
		float yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
		mob.setYRot(yaw);
		mob.setYBodyRot(yaw);
		mob.setYHeadRot(yaw);
		mob.setDeltaMovement(dir.x * carry, LEAP_UP, dir.z * carry);
		mob.syncVelocity = true;
		mob.getLookControl().setLookAt(foe, 30.0F, 30.0F);
		// (its blow on the way down is a timed jump's: Blows)
		Blows.leapt(mob, now);
		s.nextLeap = now + LEAP_COOLDOWN;
		level.sendParticles(ParticleTypes.POOF, mob.getX(), mob.getY() + 0.1, mob.getZ(), 6, 0.3, 0.05, 0.3, 0.02);
		level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.GOAT_LONG_JUMP, mob.getSoundSource(), 1.0F, 0.7F);
	}

	/** Nothing solid between its eyes and the foe's, nor over its head on the way. */
	private static boolean clearFlight(ServerLevel level, Mob mob, LivingEntity foe) {
		Vec3 from = mob.getEyePosition().add(0.0, 0.4, 0.0), to = foe.getEyePosition();
		return level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob)).getType() == HitResult.Type.MISS
			&& level.clip(new ClipContext(mob.getEyePosition(), to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob)).getType() == HitResult.Type.MISS;
	}

	/** (st-heavy) A heavy hitter's blow that lands (not one on a shield) slows the struck for a moment. */
	private static void struck(LivingEntity victim, DamageSource source, float baseDamage, float damage, boolean blocked) {
		if (blocked || damage <= 0.0F || !(source.getEntity() instanceof Mob mob) || source.getDirectEntity() != mob || !victim.isAlive() || Downed.isDowned(victim)
			|| !heavyHitter(mob)) {
			return;
		}
		victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, HEAVY_SLOW_TICKS, HEAVY_SLOW_LEVEL), mob);
	}

	// ---- st-shield -------------------------------------------------------------------------------------------------------

	/** Closing on its foe it keeps its shield up and comes on steadily; in a fight its shield-bearing neighbours make it firmer. */
	private static void advance(ServerLevel level, Mob mob, LivingEntity foe, State s, long now) {
		boolean inReach = MobShields.strikes(mob, foe);
		boolean closing = !inReach && mob.distanceToSqr(foe) <= ADVANCE_RANGE * ADVANCE_RANGE && mob.getSensing().hasLineOfSight(foe)
			&& !Arrests.busy(mob);
		boolean up = closing && MobShields.advance(mob, ADVANCE_HOLD);
		if (s.advancing && !up) {
			// (its foe in reach: the shield comes down to swing)
			MobShields.endAdvance(mob);
		}
		s.advancing = up;
		set(mob, Attributes.MOVEMENT_SPEED, ADVANCE_PACE_ID, up ? ADVANCE_PACE - 1.0 : 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		if (now - s.neighboursAt >= NEIGHBOUR_EVERY) {
			s.neighboursAt = now;
			s.neighbours = Formations.shieldNeighbours(mob, NEIGHBOUR_RANGE);
		}
		double firm = (up ? ADVANCE_FIRM : 0.0) + Math.min(NEIGHBOURS_MOST, s.neighbours) * NEIGHBOUR_FIRM;
		if (firm != s.firm) {
			s.firm = firm;
			set(mob, Attributes.KNOCKBACK_RESISTANCE, FIRM_ID, firm, AttributeModifier.Operation.ADD_VALUE);
		}
	}

	/** Out of the fight (or not stepped any more): the advance's pace and firmness come off. */
	private static void ease(Mob mob, State s) {
		if (s.advancing) {
			MobShields.endAdvance(mob);
		}
		s.advancing = false;
		s.firm = 0.0;
		set(mob, Attributes.MOVEMENT_SPEED, ADVANCE_PACE_ID, 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		set(mob, Attributes.KNOCKBACK_RESISTANCE, FIRM_ID, 0.0, AttributeModifier.Operation.ADD_VALUE);
	}

	/** A transient modifier on, or (at 0) off. */
	private static void set(Mob mob, Holder<Attribute> attribute, Identifier id, double amount, AttributeModifier.Operation op) {
		AttributeInstance instance = mob.getAttribute(attribute);
		if (instance == null) {
			return;
		}
		AttributeModifier on = instance.getModifier(id);
		if (amount == 0.0) {
			if (on != null) {
				instance.removeModifier(id);
			}
		} else if (on == null || on.amount() != amount || on.operation() != op) {
			// (only when it changes: an update marks the attribute for sending)
			instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, op));
		}
	}

	// ---- st-archers ------------------------------------------------------------------------------------------------------

	/**
	 * (from {@link Folk#performRangedAttack} and {@code McaDoubleShotMixin}, after a shot) A skilled archer's bow shot at {@code foe} has
	 * gone: a second follows {@link #SECOND_SHOT_GAP} ticks later.
	 */
	public static void loosed(Mob shooter, LivingEntity foe) {
		if (shooter == following || !(shooter.level() instanceof ServerLevel level) || !(shooter.getMainHandItem().getItem() instanceof BowItem)
			|| !skilled(shooter)) {
			return;
		}
		State s = state(shooter);
		s.shotAt = level.getGameTime() + SECOND_SHOT_GAP;
		s.shotAtFoe = foe;
	}

	/** The second arrow, through the shooter's own shot, spent from its quiver: still in sight, still drawn on it, a bow still in hand. */
	private static void second(ServerLevel level, Mob shooter, LivingEntity foe) {
		if (!shooter.isAlive() || Downed.isDowned(shooter) || shooter.isSleeping() || !foe.isAlive() || Downed.isDowned(foe) || foe.level() != level
			|| Arrests.busy(shooter) || WeaponSense.swapping(shooter) || !(shooter.getMainHandItem().getItem() instanceof BowItem)
			|| Rulers.target(shooter) != foe || !shooter.getSensing().hasLineOfSight(foe) || !MobAmmo.has(shooter, MobAmmo.Kind.ARROWS)
			|| MobShields.holdsFire(shooter, false)) {
			return;
		}
		following = shooter;
		try {
			shooter.getLookControl().setLookAt(foe, 30.0F, 30.0F);
			if (shooter instanceof Folk folk) {
				// (its own shot spends its own arrow)
				folk.performRangedAttack(foe, 1.0F);
			} else if (McaCompat.isMca(shooter) && shooter instanceof RangedAttackMob archer) {
				// (an MCA villager's shot spends its own arrow too, and its arrow is left to be picked up: McaShieldFireMixin, MobAmmo)
				archer.performRangedAttack(foe, 1.0F);
			} else if (shooter instanceof RangedAttackMob archer && MobAmmo.spend(shooter, MobAmmo.Kind.ARROWS)) {
				List<AbstractArrow> before = arrows(level, shooter);
				archer.performRangedAttack(foe, 1.0F);
				boolean loosed = false;
				for (AbstractArrow arrow : arrows(level, shooter)) {
					if (!before.contains(arrow)) {
						// (an arrow out of its quiver: it can be picked up again, as every counted arrow can)
						MobAmmo.canBePickedUp(arrow);
						loosed = true;
					}
				}
				if (!loosed) {
					// (nothing went: the arrow stays in the quiver)
					MobAmmo.give(shooter, MobAmmo.Kind.ARROWS, 1);
				}
			}
		} finally {
			following = null;
		}
	}

	/** The arrows of {@code shooter}'s just loosed, round it. */
	private static List<AbstractArrow> arrows(ServerLevel level, Mob shooter) {
		return level.getEntitiesOfClass(AbstractArrow.class, shooter.getBoundingBox().inflate(3.0), a -> a.getOwner() == shooter && a.tickCount <= 1);
	}

	// ---- the tick --------------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		if (STATES.isEmpty()) {
			return;
		}
		for (Map.Entry<Mob, State> e : new ArrayList<>(STATES.entrySet())) {
			Mob mob = e.getKey();
			State s = e.getValue();
			if (mob == null || mob.isRemoved() || !(mob.level() instanceof ServerLevel level)) {
				STATES.remove(mob);
				continue;
			}
			long now = level.getGameTime();
			if (s.shotAt > 0 && now >= s.shotAt) {
				LivingEntity foe = s.shotAtFoe;
				s.shotAt = 0;
				s.shotAtFoe = null;
				if (foe != null) {
					second(level, mob, foe);
				}
			}
			// (not stepped this tick or the last: out of the fight, or its brain busy with something else)
			if ((s.advancing || s.firm != 0.0) && now - s.stepped > 1) {
				ease(mob, s);
			}
			if (!mob.isAlive() || s.shotAt == 0 && !s.advancing && s.firm == 0.0 && now - s.stepped > 1200) {
				STATES.remove(mob);
			}
		}
	}
}
