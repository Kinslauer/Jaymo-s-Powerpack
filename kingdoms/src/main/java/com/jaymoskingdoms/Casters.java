package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.291) Andy (2026-10-28): "court necromancers need to be mca too", and then "all of em, no more folk im retiring the
 * folk. all mca please." With Minecraft Comes Alive, a tyrant's <b>court necromancer</b> is one of the realm's own
 * villagers who takes the office (one with no work of their own, else someone who moves in for it), and a barbarian
 * camp's <b>necromancer</b>, <b>shaman</b> and <b>blood witch</b> are villagers by its fire, as its raiders are. They keep
 * their name, family and hearts, take the profession ({@link Professions}), carry the staff, and dress in our robes
 * ({@link Uniforms}). Folk casters already standing hand over to one in their place ({@link #mayConvert}).
 * <p>
 * Like the court wizard ({@link CourtWizards}) they live as villagers until a foe of their side comes near; then they keep
 * their distance and cast as they always have ({@link Sorcery#cast}): the necromancer's drain, raised dead and bone
 * spikes, the shaman's fire, hex and war chant, the blood witch's cuts, grip and hemorrhage; and they mend or drink
 * ({@link Sorcery#support}) whether or not they have a foe of their own. The dead near a necromancer in a fight still rise.
 */
public final class Casters {
	/** They back away from a foe this near, and remember whoever hit them this long (ticks). */
	static final double KEEP_AWAY = 7.0;
	static final int HIT_MEMORY = 200;
	/** How fast they walk to (or from) a fight, and how far past their reach they keep after a foe they have. */
	static final double FIGHT_SPEED = 0.65, CHASE_SLACK = 8.0;

	/** Each caster's foe just now. */
	private static final Map<PathfinderMob, LivingEntity> FOES = new WeakHashMap<>();

	private Casters() {
	}

	/** {@code role} is one of the offices a villager takes here (the wizard's is {@link CourtWizards}'). */
	public static boolean isOffice(int role) {
		// ((0.7.292) and an orc camp's shaman, an orc himself: Greenskins)
		return role == Sorcery.NECROMANCER || role == Sorcery.SHAMAN || role == Sorcery.BLOOD_WITCH || role == Orcs.SHAMAN;
	}

	/** With MCA, {@code f}'s {@code role} is a villager's: a realm's necromancer, a barbarian camp's casters. */
	static boolean inPlace(Faction f, int role) {
		if (!McaCompat.LOADED || !isOffice(role)) {
			return false;
		}
		return f.type().isRealm() ? role == Sorcery.NECROMANCER : f.type() == FactionType.BARBARIAN;
	}

	/**
	 * {@code f}'s new {@code role}: {@code chosen}, else (a realm's) one of its villagers with no work, else someone who
	 * moves in (a camp's, by its fire); counted on its roster. Null without MCA or if nobody could be had.
	 */
	static @Nullable Worker enlist(ServerLevel level, Faction f, int role, @Nullable PathfinderMob chosen) {
		return McaCompat.enlistCaster(level, f, role, chosen);
	}

	/** {@code folk}, a caster of a side whose casters are villagers, at peace: one of them is to take its place. */
	static boolean mayConvert(ServerLevel level, Folk folk) {
		if (!isOffice(folk.role()) || folk.getTarget() != null || folk.isPassenger() || Downed.isDowned(folk) || Assault.attacker(folk)
			|| level.dimension() != Level.OVERWORLD) {
			return false;
		}
		Faction f = Factions.get(level.getServer(), folk.factionId());
		return f != null && !f.fallen() && inPlace(f, folk.role());
	}

	/** The staff {@code role} carries. */
	static Item staff(int role) {
		return role == Sorcery.SHAMAN || role == Orcs.SHAMAN ? Arcana.TOTEM_STAFF : Arcana.BONE_STAFF;
	}

	/** The health their folk had: a villager in the office is never frailer. */
	static double health(int role) {
		return role == Sorcery.NECROMANCER ? Sorcery.NECROMANCER_HEALTH : role == Sorcery.SHAMAN ? Sorcery.SHAMAN_HEALTH
			: role == Orcs.SHAMAN ? Orcs.SHAMAN_HEALTH : Sorcery.BLOOD_WITCH_HEALTH;
	}

	/** {@code e} is a camp's caster (a villager): one of the camp's own, as its raiders are ({@link Barbarians#isCampVillager}). */
	static boolean campCaster(Entity e) {
		if (e instanceof Folk || !(e instanceof Worker w) || !isOffice(w.role())) {
			return false;
		}
		MinecraftServer server = e.level().getServer();
		Faction f = server == null ? null : Factions.get(server, w.factionId());
		return f != null && f.type() == FactionType.BARBARIAN;
	}

	// ---- their day ---------------------------------------------------------------------------------------------------

	/**
	 * A caster's step (from {@link Workers#step}): their timers run down; they help their side; true while they have a foe
	 * (their fight in place of their brain), false to live as a villager.
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
		Sorcery.support(level, caster(w));
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

	/** Out of reach or sight they close in; too near they back off; in reach they cast. */
	private static void fight(ServerLevel level, Enlisted w, LivingEntity foe) {
		PathfinderMob mob = w.mob();
		Item staff = staff(w.role());
		if (!mob.getMainHandItem().is(staff)) {
			// (whatever their day had them holding, the staff is back in hand)
			mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(staff));
		}
		mob.getLookControl().setLookAt(foe, 30.0F, 30.0F);
		double d = mob.distanceTo(foe);
		if (!mob.getSensing().hasLineOfSight(foe) || d > Sorcery.RANGE) {
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
		if (mob.tickCount % Sorcery.CAST_TICKS == 0) {
			Sorcery.cast(caster(w), foe);
		}
	}

	/** {@code w} as a {@link Sorcery.Caster}. */
	private static Sorcery.Caster caster(Enlisted w) {
		return new Sorcery.Caster() {
			@Override
			public PathfinderMob mob() {
				return w.mob();
			}

			@Override
			public int role() {
				return w.role();
			}

			@Override
			public int factionId() {
				return w.factionId();
			}

			@Override
			public int[] timers() {
				return w.spellTimers();
			}

			@Override
			public @Nullable LivingEntity target() {
				return FOES.get(w.mob());
			}

			@Override
			public Climate climate() {
				return climateOf((ServerLevel)w.mob().level(), w.mob());
			}

			@Override
			public boolean foe(LivingEntity e) {
				return Casters.foe(w, e);
			}

			@Override
			public List<LivingEntity> fightingAllies(ServerLevel level) {
				List<LivingEntity> out = new ArrayList<>();
				for (LivingEntity e : side(level)) {
					if (e != w.mob() && foeOf(e) != null) {
						out.add(e);
					}
				}
				return out;
			}

			@Override
			public List<LivingEntity> side(ServerLevel level) {
				PathfinderMob mob = w.mob();
				List<LivingEntity> out = new ArrayList<>();
				out.add(mob);
				out.addAll(level.getEntitiesOfClass(LivingEntity.class, mob.getBoundingBox().inflate(Sorcery.SUPPORT_RANGE),
					e -> e != mob && e.isAlive() && ally(w, e)));
				return out;
			}
		};
	}

	/** Whom {@code e} (a caster, or anyone of a side) is fighting just now: a folk's or a mob's target, a villager caster's foe. */
	static @Nullable LivingEntity foeOf(LivingEntity e) {
		LivingEntity foe = e instanceof PathfinderMob m ? FOES.get(m) : null;
		if (foe == null && e instanceof Mob m) {
			foe = m.getTarget();
		}
		return foe != null && foe.isAlive() ? foe : null;
	}

	/** The land of {@code e}'s side's seat (a folk's own), else where they stand: what kind of dead they raise. */
	static Climate climateOf(ServerLevel level, LivingEntity e) {
		if (e instanceof Folk folk) {
			return folk.climate();
		}
		Faction f = e instanceof Worker w ? Factions.get(level.getServer(), w.factionId()) : null;
		return Climate.at(level, f != null ? f.seat() : e.blockPosition());
	}

	/** {@code e} is of the caster's side: its folk and villagers (an ally's too, for a realm's), a realm's townsfolk and golems. */
	private static boolean ally(Enlisted w, LivingEntity e) {
		int faction = e instanceof Worker other ? other.factionId() : -1;
		if (faction >= 0 && (faction == w.factionId() || realm(w) && allied(w, faction))) {
			return true;
		}
		return realm(w) && faction < 0 && (e instanceof net.minecraft.world.entity.npc.villager.AbstractVillager
			|| e instanceof net.minecraft.world.entity.animal.golem.IronGolem);
	}

	private static boolean realm(Enlisted w) {
		MinecraftServer server = w.mob().level().getServer();
		Faction f = server == null ? null : Factions.get(server, w.factionId());
		return f != null && f.type().isRealm();
	}

	private static boolean allied(Enlisted w, int faction) {
		MinecraftServer server = w.mob().level().getServer();
		return server != null && Relations.between(server, w.factionId(), faction) == Relation.ALLIED;
	}

	/**
	 * {@code e} is a foe of the caster's: for a court's, as for its wizard ({@link CourtWizards#foe}); for a camp's,
	 * whoever its raiders go for ({@link Barbarians#villagerPrey}), whoever struck them, a monster, or anything going for
	 * their side. Never their own side, their own thralls, or a creative player.
	 */
	static boolean foe(Enlisted w, LivingEntity e) {
		PathfinderMob mob = w.mob();
		if (e == mob || !e.isAlive() || e instanceof Player p && (p.isCreative() || p.isSpectator()) || ally(w, e) || Thralls.refuses(mob, e)) {
			return false;
		}
		if (realm(w)) {
			return CourtWizards.foe(w, e);
		}
		if (e == mob.getLastHurtByMob() && mob.tickCount - mob.getLastHurtByMobTimestamp() < HIT_MEMORY) {
			return true;
		}
		if (Barbarians.villagerPrey(w.role(), w.factionId(), e)) {
			return true;
		}
		if (e instanceof Mob m && m.getTarget() != null && (m.getTarget() == mob || ally(w, m.getTarget()))) {
			return true;
		}
		return e instanceof Enemy;
	}
}
