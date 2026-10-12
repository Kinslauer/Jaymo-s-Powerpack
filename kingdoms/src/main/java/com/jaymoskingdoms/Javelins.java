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
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
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
 * <p>(0.5.18) A javelin is Balkon's WeaponMod javelin ({@link WeaponModJavelins}), lighter than a trident. Only the guards of
 * the wealthiest kingdoms ({@link #elite}) throw tridents. Without WeaponMod, the rest don't throw at all.
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
	/** (0.5.18) Tridents are for the guards of the {@link #ELITE_REALMS} wealthiest kingdoms, with this many emeralds or more. */
	static final int ELITE_REALMS = 2;
	static final long ELITE_TREASURY = 300L;

	/** Balkon's WeaponMod: Legacy, whose javelins they throw. */
	static final String WEAPONMOD = "weaponmod";
	static final boolean WEAPONMOD_LOADED = FabricLoader.getInstance().isModLoaded(WEAPONMOD);

	private static final Map<Folk, Long> NEXT = new WeakHashMap<>();
	private static final Map<Folk, Boolean> UNREACHABLE = new WeakHashMap<>();
	/** The javelins (and tridents) folk have thrown, so the damage they do is known as theirs. */
	private static final Set<Projectile> THROWN = Collections.newSetFromMap(new WeakHashMap<>());

	private Javelins() {
	}

	static void register() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(Javelins::allowDamage);
	}

	/** Its role throws: guards, and barbarians (with their chiefs). */
	static boolean thrower(Folk folk) {
		int role = folk.role();
		return role == Folk.GUARD || role == Folk.BARBARIAN || role == Folk.CHIEF;
	}

	/** From {@link Folk#customServerAiStep}: a throw at its foe, when it can't close with it and the javelin's ready. */
	static void tick(ServerLevel level, Folk folk) {
		LivingEntity foe = folk.getTarget();
		if (foe == null || !foe.isAlive() || !thrower(folk)) {
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
		Projectile thrown = javelin(level, folk);
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
		level.playSound(null, folk.getX(), folk.getY(), folk.getZ(), SoundEvents.TRIDENT_THROW, SoundSource.HOSTILE, 1.0F, 0.9F + level.getRandom().nextFloat() * 0.2F);
	}

	/** What it throws: a trident from an elite guard, else Balkon's javelin (null without WeaponMod). */
	private static @Nullable Projectile javelin(ServerLevel level, Folk folk) {
		if (folk.role() == Folk.GUARD && elite(level.getServer(), folk.factionId())) {
			return new ThrownTrident(level, folk, new ItemStack(Items.TRIDENT));
		}
		return WEAPONMOD_LOADED ? WeaponModJavelins.javelin(level, folk) : null;
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

	/** A javelin spares the thrower's own side (and, from a defender, the villagers and golems he guards). */
	private static boolean allowDamage(LivingEntity victim, DamageSource source, float amount) {
		if (!(source.getDirectEntity() instanceof Projectile javelin) || !THROWN.contains(javelin) || !(source.getEntity() instanceof Folk thrower)) {
			return true;
		}
		if (victim instanceof Folk other) {
			return !thrower.sameSide(other);
		}
		if (victim instanceof Villager || victim instanceof IronGolem) {
			return Assault.attacker(thrower);
		}
		return !(victim instanceof Player p && p.isCreative());
	}
}
