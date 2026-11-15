package com.jaymoskitchen.farm;

import com.jaymoskitchen.JaymosKitchen;
import com.mojang.serialization.Codec;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.cow.AbstractCow;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

/**
 * Slow herds (the GT New Horizons picks, patch 2). Young animals grow up, and parents wait to breed again, at
 * {@link #GROWTH} of the speed ({@code HerdAgeMixin} skips that share of the age clock's ticks); hens lay at
 * {@link #HENS} of their speed ({@link LayingHens}); a cow, mooshroom or goat lets down milk once every
 * {@link #MILK_TICKS}, and a bucket on one milked since gets nothing ({@link #MILK_READY}, synced so the client agrees).
 * <p>
 * Crowded pens: more than {@link #CROWD} grown farm animals of one kind within {@link #CROWD_RANGE} blocks
 * ({@code CrowdedPenMixin}) won't take to breeding, and now and then one of them butts another
 * ({@link #CROWD_HURT}, {@link #CROWD_DAMAGE}). Room to stand ends it.
 * <p>
 * Jaymo's Powers' Time Bloom (by reflection) runs these clocks on with {@link #bloom}.
 */
public final class Herds {
	/** The share of ticks a young animal's growing up, and a parent's wait to breed again, still count. */
	public static final float GROWTH = 0.5F;
	/** The share of its speed a hen's egg timer runs at. */
	public static final float HENS = 0.5F;
	/** A milked animal lets down milk again after this long (ticks: 20 minutes). */
	public static final long MILK_TICKS = 24000L;
	/** More than this many grown animals of a kind this near (blocks) is a crowd; looked at every so many ticks. */
	public static final int CROWD = 12;
	public static final double CROWD_RANGE = 8.0;
	static final int CROWD_CHECK = 100;
	/** A crowded animal's chance, each look, to butt one of the others, and how hard (half-hearts). */
	static final float CROWD_HURT = 0.25F, CROWD_DAMAGE = 1.0F;

	/** The game time a milked animal can be milked again. Saved on it, synced to everyone. */
	public static final AttachmentType<Long> MILK_READY = AttachmentRegistry.create(JaymosKitchen.id("milk_ready"),
		b -> b.persistent(Codec.LONG).syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.all()));

	private Herds() {
	}

	/** Before {@link Familiarity}, so a milked cow lets down no extra bucket either. */
	public static void register() {
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (player.isSpectator() || !player.getItemInHand(hand).is(Items.BUCKET)
				|| !(entity instanceof AbstractCow || entity instanceof Goat) || ((Animal) entity).isBaby()) {
				return InteractionResult.PASS;
			}
			long now = level.getGameTime();
			if (now < entity.getAttachedOrElse(MILK_READY, 0L)) {
				return InteractionResult.FAIL;
			}
			if (!level.isClientSide()) {
				entity.setAttached(MILK_READY, now + MILK_TICKS);
			}
			return InteractionResult.PASS;
		});
	}

	/** Whether this tick of {@code mob}'s age clock (growing up, or waiting to breed) is skipped. */
	public static boolean holdsBack(AgeableMob mob) {
		return mob instanceof Animal && mob.getRandom().nextFloat() >= GROWTH;
	}

	/** Whether {@code animal} is a grown farm animal standing in a crowd of its own kind. */
	public static boolean crowded(Animal animal) {
		return !animal.isBaby() && animal.getType().builtInRegistryHolder().is(Familiarity.FARM_ANIMALS) && herd(animal).size() > CROWD;
	}

	/** The grown animals of {@code animal}'s kind within {@link #CROWD_RANGE}, itself among them. */
	private static List<Animal> herd(Animal animal) {
		double reach = CROWD_RANGE * CROWD_RANGE;
		return animal.level().getEntitiesOfClass(Animal.class, new AABB(animal.blockPosition()).inflate(CROWD_RANGE),
			other -> other.getType() == animal.getType() && other.isAlive() && !other.isBaby() && other.distanceToSqr(animal) <= reach);
	}

	/** Every tick of an animal (server): now and then, a crowded one butts another. */
	public static void tick(Animal animal) {
		if (!(animal.level() instanceof ServerLevel level) || (animal.tickCount + animal.getId()) % CROWD_CHECK != 0 || !crowded(animal)
			|| animal.getRandom().nextFloat() >= CROWD_HURT) {
			return;
		}
		List<Animal> herd = herd(animal);
		herd.remove(animal);
		Animal butted = herd.get(animal.getRandom().nextInt(herd.size()));
		butted.hurtServer(level, animal.damageSources().mobAttack(animal), CROWD_DAMAGE);
		animal.resetLove();
		butted.resetLove();
	}

	/**
	 * Jaymo's Powers' Time Bloom: {@code entity}'s slowed clocks run on {@code ticks} at once (growing up, the wait to
	 * breed again, a hen's egg, the milk). Plain Java types, so it can be reached by reflection.
	 */
	public static void bloom(Entity entity, int ticks) {
		if (!(entity instanceof Animal animal) || !(animal.level() instanceof ServerLevel) || !animal.isAlive()) {
			return;
		}
		int age = animal.getAge();
		if (age < 0 && animal.canAgeUp()) {
			animal.setAge(Math.min(0, age + ticks));
		} else if (age > 0) {
			animal.setAge(Math.max(0, age - ticks));
		}
		if (animal instanceof Chicken hen && !hen.isBaby() && !hen.isChickenJockey()) {
			hen.eggTime = Math.max(1, hen.eggTime - ticks);
		}
		Long ready = animal.getAttached(MILK_READY);
		if (ready != null) {
			animal.setAttached(MILK_READY, ready - ticks);
		}
	}
}
