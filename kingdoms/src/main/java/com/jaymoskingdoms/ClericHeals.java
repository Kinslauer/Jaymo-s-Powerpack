package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.AABB;

/**
 * (gv_cleric_heal, after Guard Villagers' clerics: its idea only) The town healer throws healing splashes. A cleric of a
 * settlement with nothing else to do, who sees a guard or a villager badly hurt ({@link #BADLY} of their health or less)
 * within {@link #REACH}, throws a splash potion of healing at their feet, a few times a day ({@link #PER_DAY}) so the
 * cleric's stock runs short: the same splash heals whoever stands in it. The fallen ({@link Downed}) are the town's tending
 * and {@link FieldMedics}, not this.
 */
public final class ClericHeals {
	/** How often clerics look (ticks); how far round a seat they're watched; how far they throw. */
	static final int EVERY = 20;
	static final double WATCHED = 80.0, REACH = 14.0;
	/** A patient at or below this share of their health. */
	static final float BADLY = 0.5F;
	/** Splashes a cleric throws a day; the pause between two (ticks). */
	static final int PER_DAY = 4, GAP = 160;
	/** The throw: its speed (of the potion), and how far it may stray. */
	static final float POWER = 0.6F, STRAY = 2.0F;

	private static final Map<UUID, long[]> USED = new HashMap<>();

	private ClericHeals() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(ClericHeals::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> USED.clear());
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		if (now % EVERY != 0) {
			return;
		}
		long day = level.getDayTime() / 24000L;
		for (Faction f : Factions.all(server)) {
			if (f.fallen() || f.type() == FactionType.BARBARIAN || !level.isPositionEntityTicking(f.seat())) {
				continue;
			}
			for (Villager cleric : level.getEntitiesOfClass(Villager.class, new AABB(f.seat()).inflate(WATCHED),
				v -> v.isAlive() && !v.isBaby() && v.getVillagerData().profession().is(VillagerProfession.CLERIC))) {
				if (!Routines.idle(cleric)) {
					continue;
				}
				long[] used = USED.computeIfAbsent(cleric.getUUID(), id -> new long[] {day, 0L, 0L});
				if (used[0] != day) {
					used[0] = day;
					used[1] = 0L;
				}
				if (used[1] >= PER_DAY || now - used[2] < GAP) {
					continue;
				}
				LivingEntity patient = patient(level, cleric);
				if (patient != null) {
					splash(level, cleric, patient);
					used[1]++;
					used[2] = now;
				}
			}
		}
	}

	/** The most badly hurt guard or villager the cleric can see within reach, or null. */
	private static LivingEntity patient(ServerLevel level, Villager cleric) {
		LivingEntity worst = null;
		float lowest = BADLY;
		List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class, cleric.getBoundingBox().inflate(REACH),
			e -> e != cleric && e.isAlive() && (e instanceof Villager || Downed.isGuard(e)) && !Downed.isDowned(e));
		for (LivingEntity e : near) {
			float share = e.getHealth() / e.getMaxHealth();
			if (share <= lowest && cleric.hasLineOfSight(e)) {
				lowest = share;
				worst = e;
			}
		}
		return worst;
	}

	/** Throws a splash potion of healing at {@code patient}'s feet. */
	private static void splash(ServerLevel level, Villager cleric, LivingEntity patient) {
		double xd = patient.getX() - cleric.getX(), zd = patient.getZ() - cleric.getZ();
		double yd = patient.getY() - cleric.getEyeY() + 0.5;
		double dist = Math.sqrt(xd * xd + zd * zd);
		cleric.getLookControl().setLookAt(patient, 30.0F, 30.0F);
		cleric.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		ItemStack potion = PotionContents.createItemStack(Items.SPLASH_POTION, Potions.HEALING);
		Projectile.spawnProjectileUsingShoot(ThrownSplashPotion::new, level, potion, cleric, xd, yd + dist * 0.2, zd, POWER, STRAY);
		level.playSound(null, cleric.getX(), cleric.getY(), cleric.getZ(), SoundEvents.WITCH_THROW, SoundSource.NEUTRAL, 1.0F, 1.2F);
	}
}
