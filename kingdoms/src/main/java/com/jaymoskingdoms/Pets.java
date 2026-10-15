package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.46) Villagers keep cats and dogs, after Village Life's mob friendships (remade, not copied: that mod is All
 * Rights Reserved). A realm's settlement wants a pet for every {@link #GROWN_PER_PET} grown (up to {@link #MOST}): its
 * townsfolk take in the stray cats and wild wolves about the place and, with none to be had, a stray turns up now and
 * then. A pet is its villager's own (vanilla's taming, with the villager as owner): it trails after them, a dog wears
 * the realm's colour on its collar and goes for whatever hurts its villager, and players can't order it about. Its
 * villager stops to fuss over it; children throw sticks for the dogs. A dog never bites townsfolk. A pet that dies is mourned (MCA mood), and one
 * whose villager dies is taken in by another.
 */
final class Pets {
	/** How often pets are thought about (ticks); how far round a seat its townsfolk and pets are looked at. */
	static final int EVERY = 40;
	static final double WATCHED = 96.0;
	/** A pet wanted for this many grown, and no more than this many in a settlement. */
	static final int GROWN_PER_PET = 5, MOST = 4;
	/** How often a settlement looks to take a pet in (ticks); how often a stray turns up, at most (ticks). */
	static final long ADOPT_TICKS = 2400L, STRAY_TICKS = 24000L;
	/** A stray's chance to be a dog (else a cat); how far from its new villager it turns up. */
	static final float DOG_CHANCE = 0.6F;
	static final int STRAY_FROM = 6;
	/** The chance each think that a free villager near their pet fusses over it, and how near. */
	static final float FUSS_CHANCE = 0.06F;
	static final double FUSS_NEAR = 4.0;
	/** The chance each think that a child near a dog throws a stick for it: how near, how far it's thrown, how fast the dog goes. */
	static final float FETCH_CHANCE = 0.04F;
	static final double FETCH_NEAR = 8.0;
	static final int THROW = 7;
	static final double FETCH_SPEED = 1.3;
	/** How far round a dead pet its villager hears of it; their mood's fall (MCA). */
	static final double MOURN_RANGE = 48.0;
	static final int GRIEF = -15, JOY = 6;
	/** Lines (lang {@code pet.jaymoskingdoms.<kind>.N}). */
	static final int LINES = 4;
	static final double SPEECH_RANGE = 12.0;

	/** The realm a pet belongs to (marks the townsfolk's pets, not players'). */
	static final AttachmentType<Integer> VILLAGE_PET = AttachmentRegistry.create(JaymosKingdoms.id("village_pet"),
		builder -> builder.persistent(Codec.INT));

	/** Per realm: when it last looked to take in a pet, and when a stray last turned up. */
	private static final Map<Integer, Long> LOOKED = new HashMap<>();
	private static final Map<Integer, Long> STRAY = new HashMap<>();

	private Pets() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Pets::tick);
		ServerLivingEntityEvents.AFTER_DEATH.register(Pets::died);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(Pets::allowDamage);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			LOOKED.clear();
			STRAY.clear();
		});
	}

	/** {@code pet} is one of the townsfolk's. */
	static boolean villagePet(TamableAnimal pet) {
		return pet.hasAttached(VILLAGE_PET);
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		if (now % EVERY != 0) {
			return;
		}
		RandomSource random = level.getRandom();
		for (Faction f : Factions.all(server)) {
			if (f.fallen() || !f.type().isRealm() || !level.isPositionEntityTicking(f.seat())) {
				continue;
			}
			AABB box = new AABB(f.seat()).inflate(WATCHED);
			List<TamableAnimal> pets = level.getEntitiesOfClass(TamableAnimal.class, box, p -> p.isAlive() && villagePet(p));
			if (now - LOOKED.getOrDefault(f.id(), -ADOPT_TICKS) >= ADOPT_TICKS) {
				LOOKED.put(f.id(), now);
				adopt(level, f, box, pets, now);
			}
			for (TamableAnimal pet : pets) {
				if (!(pet.getOwner() instanceof Villager owner) || !owner.isAlive()) {
					continue;
				}
				if (random.nextFloat() < FUSS_CHANCE && !pet.isBaby() && owner.distanceTo(pet) <= FUSS_NEAR && Routines.free(owner, Royals.timeOfDay(level))
					&& !Routines.busy(owner) && !Festivals.going(owner)) {
					fuss(level, owner, pet, random);
				}
				if (pet instanceof Wolf dog && random.nextFloat() < FETCH_CHANCE && dog.getTarget() == null && !dog.isInSittingPose()) {
					List<Villager> kids = level.getEntitiesOfClass(Villager.class, dog.getBoundingBox().inflate(FETCH_NEAR), v -> v.isAlive() && v.isBaby());
					if (!kids.isEmpty()) {
						fetch(level, kids.get(random.nextInt(kids.size())), dog, random);
					}
				}
			}
		}
	}

	/** The settlement takes in a pet if it's short: a stray cat or wild wolf about the place, else (now and then) a stray. */
	private static void adopt(ServerLevel level, Faction f, AABB box, List<TamableAnimal> pets, long now) {
		RandomSource random = level.getRandom();
		List<Villager> grown = level.getEntitiesOfClass(Villager.class, box, v -> v.isAlive() && !v.isBaby() && !(v instanceof Worker w && w.role() >= 0));
		int wanted = Math.min(MOST, grown.size() / GROWN_PER_PET);
		// (an orphan first: its villager died, and another takes it in)
		for (TamableAnimal pet : pets) {
			if (!(pet.getOwner() instanceof Villager owner) || !owner.isAlive()) {
				Villager keeper = keeper(grown, pets, random);
				if (keeper != null) {
					keep(level, f, keeper, pet);
				}
				return;
			}
		}
		if (pets.size() >= wanted) {
			return;
		}
		Villager keeper = keeper(grown, pets, random);
		if (keeper == null) {
			return;
		}
		List<TamableAnimal> strays = level.getEntitiesOfClass(TamableAnimal.class, box,
			a -> a.isAlive() && !a.isTame() && !a.isBaby() && (a instanceof Cat || a instanceof Wolf w && !w.isAngry()));
		TamableAnimal pet = strays.isEmpty() ? null : strays.get(random.nextInt(strays.size()));
		if (pet == null) {
			if (now - STRAY.getOrDefault(f.id(), -STRAY_TICKS) < STRAY_TICKS) {
				return;
			}
			STRAY.put(f.id(), now);
			BlockPos at = Construction.standable(level, keeper.blockPosition().offset(random.nextInt(STRAY_FROM * 2 + 1) - STRAY_FROM, 0,
				random.nextInt(STRAY_FROM * 2 + 1) - STRAY_FROM), 3);
			pet = (random.nextFloat() < DOG_CHANCE ? EntityTypes.WOLF : EntityTypes.CAT).spawn(level, at, EntitySpawnReason.MOB_SUMMONED);
			if (pet == null) {
				return;
			}
		}
		keep(level, f, keeper, pet);
	}

	/** Someone grown without a pet of their own (and free to take one in). */
	private static @Nullable Villager keeper(List<Villager> grown, List<TamableAnimal> pets, RandomSource random) {
		List<Villager> without = grown.stream().filter(v -> Routines.free(v, Routines.WORK_TO)
			&& pets.stream().noneMatch(p -> p.getOwner() == v)).toList();
		return without.isEmpty() ? null : without.get(random.nextInt(without.size()));
	}

	private static void keep(ServerLevel level, Faction f, Villager keeper, TamableAnimal pet) {
		pet.setTame(true, true);
		pet.setOwner(keeper);
		pet.setOrderedToSit(false);
		pet.setInSittingPose(false);
		pet.setAttached(VILLAGE_PET, f.id());
		pet.setPersistenceRequired();
		if (pet instanceof Wolf dog) {
			dog.setComponent(DataComponents.WOLF_COLLAR, f.color());
		} else if (pet instanceof Cat cat) {
			cat.setComponent(DataComponents.CAT_COLLAR, f.color());
		}
		level.sendParticles(ParticleTypes.HEART, pet.getX(), pet.getEyeY() + 0.3, pet.getZ(), 5, 0.3, 0.2, 0.3, 0.0);
		McaCompat.cheer(keeper, JOY);
		say(level, keeper, "adopt", level.getRandom());
	}

	/** Its villager fusses over their pet: a pat, hearts, a purr or a happy pant. */
	private static void fuss(ServerLevel level, Villager owner, TamableAnimal pet, RandomSource random) {
		BehaviorUtils.setWalkAndLookTargetMemories(owner, pet, 0.5F, 1);
		owner.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		level.sendParticles(ParticleTypes.HEART, pet.getX(), pet.getEyeY() + 0.3, pet.getZ(), 2, 0.2, 0.1, 0.2, 0.0);
		level.playSound(null, pet.getX(), pet.getY(), pet.getZ(), pet instanceof Cat ? SoundEvents.CAT_SOUNDS.get(net.minecraft.world.entity.animal.feline.CatSoundVariants.SoundSet.CLASSIC).adultSounds().purrSound().value() : SoundEvents.WOLF_SOUNDS.get(net.minecraft.world.entity.animal.wolf.WolfSoundVariants.SoundSet.CLASSIC).adultSounds().pantSound().value(),
			SoundSource.NEUTRAL, 0.6F, 1.0F);
		if (random.nextInt(3) == 0) {
			say(level, owner, pet instanceof Cat ? "cat" : "dog", random);
		}
	}

	/** A child throws a stick: the dog bounds off after it. */
	private static void fetch(ServerLevel level, Villager kid, Wolf dog, RandomSource random) {
		Vec3 dir = dog.position().subtract(kid.position()).multiply(1, 0, 1);
		dir = dir.lengthSqr() < 0.01 ? new Vec3(1, 0, 0) : dir.normalize();
		dir = dir.yRot((random.nextFloat() - 0.5F) * 1.2F);
		BlockPos to = Construction.standable(level, BlockPos.containing(kid.position().add(dir.scale(THROW))), 3);
		kid.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, Items.STICK), to.getX() + 0.5, to.getY() + 0.3, to.getZ() + 0.5, 4, 0.1, 0.1, 0.1, 0.02);
		level.playSound(null, kid.getX(), kid.getY(), kid.getZ(), SoundEvents.VILLAGER_AMBIENT, SoundSource.NEUTRAL, 0.6F, 1.7F);
		dog.getNavigation().moveTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5, FETCH_SPEED);
		if (random.nextInt(3) == 0) {
			say(level, kid, "fetch", random);
		}
	}

	/** A village dog never bites townsfolk (its villager in a scuffle with a neighbour): it's called off instead. */
	private static boolean allowDamage(LivingEntity victim, DamageSource source, float amount) {
		if (victim instanceof Villager && source.getEntity() instanceof TamableAnimal pet && villagePet(pet)) {
			pet.setTarget(null);
			return false;
		}
		return true;
	}

	/** A village pet dies: its villager mourns it. */
	private static void died(LivingEntity dead, DamageSource source) {
		if (!(dead instanceof TamableAnimal pet) || !villagePet(pet) || !(dead.level() instanceof ServerLevel level)) {
			return;
		}
		if (pet.getOwner() instanceof Villager owner && owner.isAlive() && owner.distanceTo(pet) <= MOURN_RANGE) {
			McaCompat.cheer(owner, GRIEF);
			say(level, owner, "mourn", level.getRandom());
		}
	}

	private static void say(ServerLevel level, Villager v, String kind, RandomSource random) {
		Speech.say(level, v, Component.translatable("pet.jaymoskingdoms." + kind + "." + random.nextInt(LINES)), Speech.WHITE, SPEECH_RANGE);
	}
}
