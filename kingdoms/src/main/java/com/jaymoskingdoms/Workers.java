package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.21) A settlement's {@link Worker}s: who they are ({@link #near}), taking one on ({@link #body}), and their day.
 * With Minecraft Comes Alive a realm's workers are its own MCA villagers ({@link #inPlace}): one is promoted to the
 * job where they stand (the settlement's pick, or a player's through MCA's Professions menu), and keeps their name,
 * family and hearts. A realm's guards are MCA's guards and archers, counted on its roster and fighting its foes
 * ({@link McaCompat#foe}). By day their work takes the place of their brain ({@link #step}); at night, when they're
 * frightened or hurt, when someone's talking to them, or when there's nothing to do, they live as MCA's villagers do.
 */
public final class Workers {
	/** What an idle worker's task says: nothing in hand and nothing to do, so they live as villagers meanwhile. */
	static final Set<String> IDLE = Set.of("", "idle", "resting", "waiting for materials", "waiting for the mine", "the mine is worked out",
		"no trees to fell");
	/** How often a villager's job is squared with its profession (ticks). */
	static final int RECONCILE_TICKS = 20;
	/** How long after being hurt a worker runs instead of working (ticks). */
	static final int FLEE_TICKS = 100;

	private Workers() {
	}

	/**
	 * {@code role} is filled in place from {@code f}'s own MCA villagers: MCA's loaded, it isn't a camp, and it's a
	 * worker's job, or a realm's guard (made an MCA guard or archer; its archers on their posts stay soldiers).
	 */
	static boolean inPlace(Faction f, int role) {
		// ((0.7.293) and its wall archers, WallArchers)
		return McaCompat.LOADED && f.type() != FactionType.BARBARIAN && (Worker.isWork(role) || (role == Folk.GUARD || role == Folk.ARCHER) && f.type().isRealm());
	}

	/** A worker's day's work (a {@link Folk}'s from its own tick; an MCA villager's from {@link #step}). */
	static void work(ServerLevel level, Worker w) {
		if (Folk.isBuilder(w.role())) {
			Builders.tick(level, w);
		} else if (w.role() == Folk.FARMER) {
			Farmers.tick(level, w);
		} else if (Gatherers.isGatherer(w.role())) {
			Gatherers.tick(level, w);
		} else if (w.role() == Folk.PULLER) {
			Rickshaws.tick(level, w);
		} else if (Stables.isStableWorker(w.role())) {
			// (0.5.47)
			Stables.tick(level, w);
		}
	}

	/** The workers in {@code box} that pass {@code test}: folk, and (with MCA) villagers in a job. */
	static List<Worker> near(ServerLevel level, AABB box, Predicate<Worker> test) {
		List<Worker> found = new ArrayList<>(level.getEntitiesOfClass(Folk.class, box, test::test));
		if (McaCompat.LOADED) {
			found.addAll(McaCompat.workers(level, box, test));
		}
		return found;
	}

	/**
	 * A new {@code role} for {@code f}, keeping to {@code radius} of {@code home}: one of its villagers promoted (with
	 * MCA), else a new {@link Folk} at {@code at}. The caller counts them in the roster. Null if none could be had.
	 */
	static @Nullable Worker body(ServerLevel level, Faction f, int role, BlockPos at, BlockPos home, int radius) {
		if (inPlace(f, role)) {
			Worker w = McaCompat.enlist(level, f, role, at, home, radius);
			if (w != null) {
				return w;
			}
		}
		Folk folk = JaymosKingdoms.FOLK.create(level, EntitySpawnReason.MOB_SUMMONED);
		if (folk == null) {
			return null;
		}
		folk.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
		folk.setup(role, f, home, radius);
		level.addFreshEntity(folk);
		return folk;
	}

	/**
	 * A villager's server step (from {@code VillagerWorkMixin}, before its brain): true if it's at work, its work done
	 * in place of its brain this tick; false to let its brain run (not a worker, bedtime, frightened, hurt, talking to
	 * someone, or idle).
	 */
	public static boolean step(ServerLevel level, Worker w) {
		PathfinderMob mob = w.mob();
		if (SwornCaptains.free(mob)) {
			// (0.7.433) a captain sworn to a player: at their side, his brain his own (and never taken onto a roster)
			return SwornCaptains.step(level, w);
		}
		Boolean balmed = InBalming.step(level, w);
		if (balmed != null) {
			// (0.7.433) an orc or goblin a spirit in-balmed: fighting for their keeper, or at their side
			return balmed;
		}
		if (w.role() == Looters.LOOTER && !(mob instanceof Folk)) {
			// (0.7.295) a looter: his creeping and robbing are Looters' doing
			return true;
		}
		if (w.role() == Assassins.ASSASSIN) {
			// (0.7.295) an assassin, his own doings in place of his brain
			Assassins.act(level, mob);
			return true;
		}
		if (com.jaymoskingdoms.forms.Schools.isMaster(mob)) {
			// (0.7.384) a school master: held at the post in their hall
			return com.jaymoskingdoms.forms.Schools.step(level, mob);
		}
		if (ZFighterFolk.is(mob)) {
			// (0.7.319) one of Powers' Z Fighter characters: held at their post
			return ZFighterFolk.step(level, w);
		}
		if (Crewmen.tagged(mob)) {
			// (0.7.242) one of a ship's crew: aboard her, they do as her crew; ashore, they're villagers like any
			return Crewmen.step(level, w);
		}
		if (Caravans.isCaravanFolk(w)) {
			// (0.7.294) one of a caravan's carters or guards: along with it
			return Caravans.villagerStep(level, w);
		}
		if (w.role() == Caravans.CARTER && Drivers.step(level, w)) {
			// (0.7.294) a realm's driver in the saddle: the drive is his
			return true;
		}
		if (RangedTactics.brainStep(mob)) {
			// (0.7.184) a guard whose shots keep failing is on the move to a better place: its brain waits
			return true;
		}
		if (mob.tickCount % Standing.EVERY == 0 && (w.role() == Folk.GUARD || Rulers.leaderRole(w.role()) || mob.hasAttached(Standing.LINES))) {
			// (0.7.175) what MCA's screen shows of a ruler or a guard
			Standing.update(level, w);
		}
		if (mob.tickCount % RECONCILE_TICKS == 0) {
			if (w instanceof Enlisted e) {
				// (0.5.53) how long they've been grown (elders become court wizards)
				CourtWizards.age(level, e);
				// (0.7.218) a gifted Wind the storm has taken
				GiftedFolk.step(level, e.mob());
				// (0.7.361) weighed once for Valor (a ruler for theirs)
				ValorFolk.step(level, e);
				if (OldAge.step(level, e)) {
					// (0.5.54) they died of old age
					return false;
				}
			}
			if (!McaCompat.reconcile(level, w)) {
				return false;
			}
		}
		if (Rulers.leaderRole(w.role()) && w instanceof Enlisted e) {
			// (0.7.138) a realm's ruler, one of its villagers: their day at court and on their round
			return Rulers.step(level, e);
		}
		if (WarParties.isSoldier(w)) {
			// (0.7.295) a soldier of a war party: on the march as it sends him
			return WarParties.villagerStep(level, w);
		}
		if (w.role() == Folk.ARCHER && w instanceof Enlisted) {
			// (0.7.293) a wall archer, held to his post
			return WallArchers.step(level, w);
		}
		if (w.role() == Folk.GUARD && w instanceof Enlisted e) {
			// (0.7.138) one of its MCA guards on their ruler's escort, or leading a prisoner
			return Rulers.guardStep(level, e);
		}
		if (w.role() == Sorcery.WIZARD && w instanceof Enlisted e) {
			// (0.5.53) a court wizard lives as a villager until a foe comes near
			if (Hermits.is(e)) {
				// (0.5.62) a floating island's hermit: their fall caught, visitors greeted
				Hermits.step(level, e);
			}
			return !mob.isSleeping() && !McaCompat.busy(mob) && CourtWizards.step(level, e);
		}
		if (Casters.isOffice(w.role()) && w instanceof Enlisted e) {
			// (0.7.291) a court's necromancer, a camp's necromancer, shaman or blood witch: a villager until a foe comes near
			return !mob.isSleeping() && !McaCompat.busy(mob) && Casters.step(level, e);
		}
		if (CampChiefs.isRole(w.role()) || w.role() == Pirates.CAPTAIN && !Crewmen.tagged(mob) && Pirates.isCoveCaptain(mob)) {
			// (0.7.293) a camp's chief, king or warchief: his boss bar and his rally; (0.7.296) a cove's captain ashore too
			CampChiefs.step(level, w);
		}
		if (Captains.is(mob)) {
			// (0.7.431) one of a tribe's captains: his fears, rage and rally, the players he meets (on a band, the trail)
			Greenskins.step(level, w);
			Javelins.tick(level, w);
			return Captains.step(level, w);
		}
		if (Pedlars.party(mob)) {
			// (0.7.425) a tribe's pedlar on his road, or one of his guards at his back
			Greenskins.step(level, w);
			return Pedlars.step(level, w);
		}
		if (Trials.kingsGuard(mob)) {
			// (0.7.429) one of a king's guard, at their post by his door
			Greenskins.step(level, w);
			Javelins.tick(level, w);
			return Trials.guardStep(level, w);
		}
		if (Tribes.hunter(mob)) {
			// (0.7.425) one of a tribe's hunting band on a hated player's trail
			Greenskins.step(level, w);
			Javelins.tick(level, w);
			return Tribes.hunterStep(level, w);
		}
		if (!(mob instanceof Folk) && Raids.isRaider(mob)) {
			// (0.7.295) a camp's villager out on a raid: its march and plunder; its ways in a fight as at home
			if (Greenskins.isRole(w.role())) {
				Greenskins.step(level, w);
			}
			Javelins.tick(level, w);
			return Pirates.villagerStep(level, w) || Raids.villagerStep(level, w);
		}
		if (Greenskins.isRole(w.role())) {
			// (0.7.292) a goblin village's or an orc camp's villager: an orc's rage and slam, a grunt's javelins; MCA's guard
			// brain does the rest
			Greenskins.step(level, w);
			Javelins.tick(level, w);
			return false;
		}
		if (Barbarians.villagerRole(w.role())) {
			// (0.7.184) a camp's MCA villager keeps its folk's ranged ways: rocks, pistols, dynamite (their legs, for a bombardier's lobbing)
			Javelins.tick(level, w);
			return Pirates.villagerStep(level, w);
		}
		if (BloodHunters.step(level, w)) {
			// (0.7.341) a town's hunter out on a wounded beast's blood trail
			return true;
		}
		if (!Worker.isWork(w.role()) || Homes.bedtime(level) && !Rickshaws.onDuty(mob) || mob.isSleeping() || mob.isBaby() || Frights.afraid(mob.getUUID())
			|| mob.getLastHurtByMob() != null && mob.tickCount - mob.getLastHurtByMobTimestamp() < FLEE_TICKS || McaCompat.busy(mob) || Downed.helping(mob)) {
			return false;
		}
		if (!w.isCarrying() && !w.getMainHandItem().is(w.tool().getItem())) {
			// (back at work: their tool back in hand, whatever their own day had them holding)
			w.setCarried(net.minecraft.world.item.ItemStack.EMPTY);
		}
		work(level, w);
		return w.isCarrying() || !IDLE.contains(w.task());
	}

	/** A worker died: its place is to be filled, and whoever killed it is remembered. */
	public static void died(ServerLevel level, Worker w, DamageSource source) {
		if (w.isCarrying()) {
			// whatever they were hauling falls where they do
			w.mob().spawnAtLocation(level, w.takeCarried());
		}
		CourtWizards.died(level, w, source);
		Greenskins.died(level, w, source);
		CampChiefs.died(level, w, source);
		if (!(w.mob() instanceof Folk)) {
			// (0.7.295) a villager assassin
			Assassins.died(level, w.mob(), source);
		}
		KingdomEvents.workerDied(level, w, source);
	}
}
