package com.jaymoskingdoms;

import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.pathfinder.Path;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.12) Guards (and barbarians) throw javelins at a foe they can't close with: one up on a wall or a tower, one they
 * have no path to, or a bowman keeping his distance. So an archer on the wall is no longer safe from the men below.
 * A javelin can't be picked up; it doesn't hurt the thrower's own side, nor (thrown by defenders) the villagers and golems
 * about them.
 * <p>(0.5.28) What's thrown depends on the thrower's purse ({@link #javelin}): the guards of the wealthiest kingdoms
 * ({@link #elite}) throw tridents; poor kingdoms' guards, barbarians and their chiefs throw rocks; everyone else throws
 * Balkon's WeaponMod javelin ({@link WeaponModJavelins}), or rocks without WeaponMod.
 */
final class Javelins {
	/** It's thrown from this far to this far (blocks). */
	static final double NEAREST = 4.0, FURTHEST = 22.0;
	/** A foe this much higher counts as out of reach (up on a wall). */
	static final double ABOVE = 2.5;
	/** A bowman further than this is thrown at even if he could be reached. */
	static final double BOWMAN = 7.0;
	/** Between throws (ticks), plus up to {@link #JITTER} more. */
	static final int COOLDOWN = 70, JITTER = 30;
	/** How hard it's thrown, and how wide it may stray. */
	static final float SPEED = 1.6F, SPREAD = 5.0F;
	/** How often a path to the foe is checked (ticks). */
	static final int PATH_CHECK = 20;
	/** (0.5.28) Tridents are for the guards of the {@link #ELITE_REALMS} wealthiest kingdoms, with this many emeralds or more. */
	static final int ELITE_REALMS = 2;
	static final long ELITE_TREASURY = 300L;
	/** (0.5.28) A kingdom with fewer emeralds than this is poor: its guards throw rocks. A rock hits for this much. */
	static final long POOR_TREASURY = 50L;
	static final float ROCK_DAMAGE = 3.0F;

	/** Balkon's WeaponMod: Legacy, whose javelins they throw. */
	static final String WEAPONMOD = "weaponmod";
	static final boolean WEAPONMOD_LOADED = FabricLoader.getInstance().isModLoaded(WEAPONMOD);

	private static final Map<PathfinderMob, Long> NEXT = new WeakHashMap<>();
	private static final Map<PathfinderMob, Boolean> UNREACHABLE = new WeakHashMap<>();
	/** The javelins (and tridents) folk have thrown, so the damage they do is known as theirs. */
	private static final Set<Projectile> THROWN = Collections.newSetFromMap(new WeakHashMap<>());

	private Javelins() {
	}

	static void register() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(Javelins::allowDamage);
	}

	/** Its role throws: guards, barbarians (with their chiefs), and orc grunts. */
	static boolean thrower(Folk folk) {
		int role = folk.role();
		return (role == Folk.GUARD || role == Folk.BARBARIAN || role == Folk.CHIEF || role == Orcs.GRUNT) && !Muskets.armed(folk);
	}

	/** (0.7.184) A camp's MCA villager throws too (a raider as its folk did; the pirates with a pistol or dynamite in hand don't). */
	static boolean thrower(Worker w) {
		// ((0.7.292) and an orc camp's grunts; (0.7.293) and a chief)
		return w.role() == Folk.BARBARIAN || w.role() == Folk.CHIEF || w.role() == Orcs.GRUNT;
	}

	/** (0.5.64) A shot of one of the folk's (a musket ball, a cannonball): its damage is known as theirs, and spares their side. */
	static void mark(Projectile shot) {
		THROWN.add(shot);
	}

	/** From {@link Folk#customServerAiStep}: a throw at its foe, when it can't close with it and the javelin's ready. */
	static void tick(ServerLevel level, Folk folk) {
		if (thrower(folk)) {
			tick(level, folk, folk.factionId());
		}
	}

	/** (0.7.184) The same throw from a camp's MCA villager ({@code Workers.step}). */
	static void tick(ServerLevel level, Worker w) {
		if (thrower(w)) {
			tick(level, w.mob(), w.factionId());
		}
	}

	private static void tick(ServerLevel level, PathfinderMob folk, int faction) {
		// ((0.7.292) a villager's foe is its brain's)
		LivingEntity foe = Rulers.target(folk);
		if (foe == null || !foe.isAlive() || folk.isPassenger() || Downed.isDowned(folk)) {
			return;
		}
		long now = level.getGameTime();
		if (now < NEXT.getOrDefault(folk, 0L)) {
			return;
		}
		double d = folk.distanceTo(foe);
		if (d < NEAREST || d > FURTHEST || !folk.hasLineOfSight(foe)) {
			return;
		}
		if (now % PATH_CHECK == 0) {
			Path path = folk.getNavigation().createPath(foe, 1);
			UNREACHABLE.put(folk, path == null || !path.canReach());
		}
		boolean above = foe.getY() - folk.getY() >= ABOVE;
		boolean bowman = d > BOWMAN && (holdsBow(foe) || foe instanceof Folk f && f.role() == Folk.ARCHER);
		if (!above && !bowman && !(d > BOWMAN && UNREACHABLE.getOrDefault(folk, false))) {
			return;
		}
		NEXT.put(folk, now + COOLDOWN + level.getRandom().nextInt(JITTER));
		Projectile thrown = javelin(level, folk, faction);
		if (thrown == null) {
			return;
		}
		THROWN.add(thrown);
		ItemStack javelin = thrown instanceof AbstractArrow arrow ? arrow.getPickupItemStackOrigin() : ItemStack.EMPTY;
		double dx = foe.getX() - folk.getX(), dz = foe.getZ() - folk.getZ();
		double dy = foe.getY(0.3333) - thrown.getY();
		double flat = Math.sqrt(dx * dx + dz * dz);
		folk.getLookControl().setLookAt(foe, 30.0F, 30.0F);
		folk.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		Projectile.spawnProjectileUsingShoot(thrown, level, javelin, dx, dy + flat * 0.2, dz, SPEED, SPREAD);
		level.playSound(null, folk.getX(), folk.getY(), folk.getZ(), thrown instanceof Snowball ? SoundEvents.SNOWBALL_THROW : SoundEvents.TRIDENT_THROW.value(),
			SoundSource.HOSTILE, 1.0F, 0.9F + level.getRandom().nextFloat() * 0.2F);
	}

	/**
	 * What it throws: a trident from an elite kingdom's guard, a rock from a poor kingdom's guard or a barbarian, else
	 * Balkon's javelin (a rock without WeaponMod).
	 */
	private static @Nullable Projectile javelin(ServerLevel level, PathfinderMob folk, int faction) {
		MinecraftServer server = level.getServer();
		Faction f = Factions.get(server, faction);
		boolean poor = f == null || !f.type().isRealm() || f.treasury() < POOR_TREASURY;
		if (!poor && elite(server, faction)) {
			return new ThrownTrident(level, folk, new ItemStack(Items.TRIDENT));
		}
		Projectile javelin = !poor && WEAPONMOD_LOADED ? WeaponModJavelins.javelin(level, folk) : null;
		return javelin != null ? javelin : rock(level, folk);
	}

	/** A rock: a thrown cobblestone that hits for {@link #ROCK_DAMAGE} ({@link #allowDamage} gives a snowball its weight). */
	private static Projectile rock(ServerLevel level, LivingEntity folk) {
		return new Snowball(level, folk, new ItemStack(Items.COBBLESTONE));
	}

	/** A kingdom among the {@link #ELITE_REALMS} wealthiest, with at least {@link #ELITE_TREASURY} emeralds. */
	static boolean elite(MinecraftServer server, int faction) {
		return Factions.all(server).stream().filter(f -> f.type().isRealm() && !f.fallen() && f.treasury() >= ELITE_TREASURY)
			.sorted(Comparator.comparingLong(Faction::treasury).reversed()).limit(ELITE_REALMS).anyMatch(f -> f.id() == faction);
	}

	private static boolean holdsBow(LivingEntity e) {
		return e.getMainHandItem().getItem() instanceof BowItem || e.getMainHandItem().getItem() instanceof CrossbowItem
			|| e.getOffhandItem().getItem() instanceof BowItem || e.getOffhandItem().getItem() instanceof CrossbowItem;
	}

	/** Whether a shot of {@code thrower}'s may hurt {@code victim}: never its own side, nor (from a defender) the villagers and golems he guards. */
	static boolean harms(Folk thrower, LivingEntity victim) {
		if (victim instanceof Folk other) {
			return !thrower.sameSide(other);
		}
		if (victim instanceof Villager || victim instanceof IronGolem) {
			return Assault.attacker(thrower);
		}
		return !(victim instanceof Player p && p.isCreative());
	}

	/**
	 * (0.7.184) The same for a camp's MCA villager as thrower: never its own camp's folk or villagers, nor a creative
	 * player; the villagers and golems the camps hunt are fair game.
	 */
	static boolean harms(LivingEntity thrower, LivingEntity victim) {
		if (thrower instanceof Folk folk) {
			return harms(folk, victim);
		}
		if (thrower instanceof Worker w && !Barbarians.isCampVillager(thrower)) {
			// (0.7.295) a realm's MCA villager (a gunner at his cannon): never his own side, nor the villagers and golems
			// he guards; others only if they're his foes
			if (victim == thrower || victim instanceof Worker o && o.factionId() == w.factionId() || victim instanceof Villager || victim instanceof IronGolem) {
				return false;
			}
			if (victim instanceof Player p) {
				return !p.isCreative();
			}
			return victim instanceof net.minecraft.world.entity.monster.Enemy || McaCompat.foe(thrower, victim);
		}
		if (victim == thrower || Barbarians.isCampVillager(victim)) {
			return false;
		}
		if (victim instanceof Folk other) {
			return other.style() != FactionType.BARBARIAN;
		}
		return !(victim instanceof Player p && p.isCreative());
	}

	/** While a rock's blow is being dealt again (so it isn't again and again). */
	private static boolean redealing;

	/** A javelin spares the thrower's own side (and, from a defender, the villagers and golems he guards). */
	private static boolean allowDamage(LivingEntity victim, DamageSource source, float amount) {
		if (!(source.getDirectEntity() instanceof Projectile javelin) || !THROWN.contains(javelin) || !(source.getEntity() instanceof LivingEntity thrower)) {
			return true;
		}
		boolean allowed = harms(thrower, victim);
		if (allowed && !redealing && javelin instanceof Snowball && amount < ROCK_DAMAGE && victim.level() instanceof ServerLevel level) {
			// a snowball does no harm: the rock's blow, dealt again with its weight (once: some mobs, like MCA's
			// villagers, soften a blow before it gets here)
			redealing = true;
			try {
				victim.hurtServer(level, source, ROCK_DAMAGE);
			} finally {
				redealing = false;
			}
			return false;
		}
		return allowed;
	}
}
