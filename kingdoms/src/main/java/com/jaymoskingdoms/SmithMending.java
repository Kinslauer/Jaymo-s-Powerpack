package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.animal.golem.AbstractGolem;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (gv_smith_mend, after Guard Villagers' smiths: its idea only) The town's smiths mend what the guards wear and what the
 * golems are made of, beside {@link Whetting} (a smith does one or the other, never both: {@link #working} and
 * {@link Whetting#working} are each other's busy state). In the working day a weaponsmith, armourer or toolsmith with
 * nothing else to do picks a guard whose gear (the blade in hand or a piece of armour) is half worn or worse, or a golem
 * hurt past {@link #GOLEM_BELOW} of its health, walks to them, hammers at it with the anvil ringing, and then the piece
 * has {@link #MENDS} of its durability back (or the golem is patched up by {@link #PATCH} health). The iron for it comes out
 * of the settlement's storehouse ({@link #IRON} ingots a job, {@link Storage}); a bare storehouse means no work.
 */
public final class SmithMending {
	/** How often smiths think of it (ticks); how far round a seat they are watched. */
	static final int EVERY = 20;
	static final double WATCHED = 80.0;
	/** The working day (time of day). */
	static final long FROM = 1000L, TO = 11000L;
	/** The chance, each think, that an idle smith sees to someone's gear. */
	static final float START_CHANCE = 0.08F;
	/** A piece of gear this worn (the share of its durability gone) or worse is worth the trouble. */
	static final float WORN = 0.5F;
	/** Of its durability a mended piece gets back; a golem hurt below this share of its health is patched, by this much health. */
	static final float MENDS = 0.6F, GOLEM_BELOW = 0.7F, PATCH = 25.0F;
	/** Iron ingots a job costs out of the storehouse. */
	static final int IRON = 1;
	/** How far from the smith a patient is looked for (blocks). */
	static final double REACH = 24.0;
	/** Their pace; how close is there; the longest they try to get there, and how long the hammering takes (ticks). */
	static final float WALK = 0.55F;
	static final double ARRIVE = 2.2;
	static final int TRAVEL_TICKS = 700, WORK_MIN = 100, WORK_MORE = 60;
	/** The sparks off the anvil and how big. */
	static final int SPARKS = 4;
	static final float SPARK_SCALE = 0.5F;

	private static final class Job {
		final UUID patient;
		/** The slot mended, or null for a golem. */
		final @Nullable EquipmentSlot slot;
		final long started;
		final int length;
		long arrived = -1;

		Job(UUID patient, @Nullable EquipmentSlot slot, long started, int length) {
			this.patient = patient;
			this.slot = slot;
			this.started = started;
			this.length = length;
		}
	}

	private static final Map<UUID, Job> JOBS = new HashMap<>();

	private SmithMending() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(SmithMending::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> JOBS.clear());
	}

	/** {@code smith} is at a mending job just now (so {@link Whetting} leaves them be). */
	static boolean working(Villager smith) {
		return JOBS.containsKey(smith.getUUID());
	}

	private static boolean smith(Villager v) {
		return v.getVillagerData().profession().is(VillagerProfession.WEAPONSMITH) || v.getVillagerData().profession().is(VillagerProfession.ARMORER)
			|| v.getVillagerData().profession().is(VillagerProfession.TOOLSMITH);
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		if (now % EVERY != 0) {
			return;
		}
		long t = Royals.timeOfDay(level);
		RandomSource random = level.getRandom();
		for (Faction f : Factions.all(server)) {
			if (f.fallen() || f.type() == FactionType.BARBARIAN || !level.isPositionEntityTicking(f.seat())) {
				continue;
			}
			for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(f.seat()).inflate(WATCHED), Villager::isAlive)) {
				if (!smith(v)) {
					continue;
				}
				Job job = JOBS.get(v.getUUID());
				if (job != null) {
					if (!step(level, v, job, t, now)) {
						JOBS.remove(v.getUUID());
						v.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
					}
				} else if (t >= FROM && t < TO && random.nextFloat() < START_CHANCE && Routines.idle(v) && !Rulers.is(v) && !Whetting.working(v)) {
					start(level, f, v, now);
				}
			}
		}
		JOBS.values().removeIf(j -> now - j.started > TRAVEL_TICKS + WORK_MIN + WORK_MORE + 200);
	}

	/** The share of its durability gone from {@code stack} (0 for something that does not wear). */
	private static float worn(ItemStack stack) {
		return stack.isEmpty() || !stack.isDamageableItem() ? 0.0F : stack.getDamageValue() / (float)stack.getMaxDamage();
	}

	private static boolean claimed(LivingEntity e) {
		return JOBS.values().stream().anyMatch(j -> j.patient.equals(e.getUUID()));
	}

	private static void start(ServerLevel level, Faction f, Villager smith, long now) {
		LivingEntity patient = null;
		EquipmentSlot slot = null;
		float worst = WORN - 0.0001F;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, smith.getBoundingBox().inflate(REACH),
			e -> e != smith && e.isAlive() && (Downed.isGuard(e) || e instanceof AbstractGolem) && !Downed.isDowned(e) && !claimed(e))) {
			if (e instanceof AbstractGolem) {
				float lost = 1.0F - e.getHealth() / e.getMaxHealth();
				if (lost > 1.0F - GOLEM_BELOW && lost > worst) {
					worst = lost;
					patient = e;
					slot = null;
				}
				continue;
			}
			for (EquipmentSlot s : new EquipmentSlot[] {EquipmentSlot.MAINHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
				float w = worn(e.getItemBySlot(s));
				if (w > worst) {
					worst = w;
					patient = e;
					slot = s;
				}
			}
		}
		if (patient == null || Storage.take(level, f, Items.IRON_INGOT, IRON).isEmpty()) {
			return;
		}
		JOBS.put(smith.getUUID(), new Job(patient.getUUID(), slot, now, WORK_MIN + level.getRandom().nextInt(WORK_MORE)));
		BehaviorUtils.setWalkAndLookTargetMemories(smith, patient.blockPosition(), WALK, 1);
	}

	/** One think of the work: false when it is over (done, or broken off). */
	private static boolean step(ServerLevel level, Villager smith, Job job, long t, long now) {
		if (!(level.getEntity(job.patient) instanceof LivingEntity patient) || !patient.isAlive() || !Routines.idle(smith) || Whetting.working(smith)
			|| t >= TO + 1000L) {
			return false;
		}
		if (job.arrived < 0) {
			boolean there = smith.distanceToSqr(patient) <= ARRIVE * ARRIVE;
			if (!there && now - job.started < TRAVEL_TICKS) {
				if (smith.getNavigation().isDone()) {
					BehaviorUtils.setWalkAndLookTargetMemories(smith, patient.blockPosition(), WALK, 1);
				}
				return true;
			}
			if (!there) {
				return false;
			}
			job.arrived = now;
			// a hammer in hand as they work
			smith.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE));
		}
		smith.getNavigation().stop();
		smith.getLookControl().setLookAt(patient, 30.0F, 30.0F);
		RandomSource random = level.getRandom();
		if (random.nextInt(2) == 0) {
			smith.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
			level.playSound(null, patient.getX(), patient.getY(), patient.getZ(), SoundEvents.ANVIL_USE, SoundSource.NEUTRAL, 0.25F, 1.5F + random.nextFloat() * 0.3F);
			Vec3 at = patient.position().add(0.0, patient.getBbHeight() * 0.6, 0.0);
			if (!PowersCompat.clashSparks(level, at, smith.position().subtract(at), SPARKS, SPARK_SCALE)) {
				level.sendParticles(Fx.spark(0xFFC060, 0.3F), at.x, at.y, at.z, SPARKS, 0.15, 0.1, 0.15, 0.05);
			}
		}
		if (now - job.arrived >= job.length) {
			mend(patient, job.slot);
			level.playSound(null, patient.getX(), patient.getY(), patient.getZ(), SoundEvents.ANVIL_PLACE, SoundSource.NEUTRAL, 0.3F, 1.6F);
			return false;
		}
		return true;
	}

	/** The piece in {@code slot} has durability back, or the golem is patched up. */
	private static void mend(LivingEntity patient, @Nullable EquipmentSlot slot) {
		if (slot == null) {
			patient.heal(PATCH);
			return;
		}
		ItemStack stack = patient.getItemBySlot(slot);
		if (!stack.isEmpty() && stack.isDamageableItem()) {
			stack.setDamageValue(Math.max(0, stack.getDamageValue() - Math.round(stack.getMaxDamage() * MENDS)));
		}
	}
}
