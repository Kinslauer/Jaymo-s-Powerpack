package com.jaymoskitchen.corpse;

import com.jaymoskitchen.JaymosKitchen;
import java.util.List;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * (0.5.18) Andy: "large blunt impacts or slams on spiders should crush them, leaving no corpse to grab, but only a
 * random amount of legs that disappear after a time." A {@link #CRUSHABLE} creature (spiders, cave spiders) that dies
 * crushed leaves no corpse: it bursts into {@link #LEGS_MIN}-{@link #LEGS_MAX} loose legs ({@link SpiderLeg}) that skitter
 * out, lie about and sink away, and whatever it carried drops loose, its eyes excepted (squashed).
 * <p>Crushed is a death from a {@link #CRUSHES} blow (a long fall, a falling anvil or block, a mace's smash, being flung
 * into a wall), or one another mod marked with {@link #CRUSHED_TAG}: Jaymo's Powers marks heavy blunt weapons' kills, a
 * blunt finisher, and its slams and crushes.
 */
public final class SpiderCrush {
	/** What a crushing death squashes into legs instead of a corpse. */
	public static final TagKey<EntityType<?>> CRUSHABLE = TagKey.create(Registries.ENTITY_TYPE, JaymosKitchen.id("crushable"));
	/** Damage that crushes what it kills. */
	public static final TagKey<DamageType> CRUSHES = TagKey.create(Registries.DAMAGE_TYPE, JaymosKitchen.id("crushes"));
	/** (Scoreboard) tag another mod puts on a creature whose death just now was a crushing one. */
	public static final String CRUSHED_TAG = "jaymoskitchen.crushed";

	/** Legs left, at random between these (a spider has eight; the rest are pulp). */
	static final int LEGS_MIN = 2, LEGS_MAX = 6;
	/** How hard the legs fly out (blocks a tick), sideways and up. */
	static final double LEG_SPREAD = 0.22, LEG_HOP = 0.25;

	public static EntityType<SpiderLeg> SPIDER_LEG;

	private SpiderCrush() {
	}

	public static void register() {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JaymosKitchen.id("spider_leg"));
		SPIDER_LEG = Registry.register(BuiltInRegistries.ENTITY_TYPE, key,
			EntityType.Builder.<SpiderLeg>of(SpiderLeg::new, MobCategory.MISC)
				.sized(0.5F, 0.15F)
				.clientTrackingRange(6)
				.updateInterval(2)
				.noSummon()
				.build(key));
	}

	/** Whether {@code mob}, just now dying, was crushed (and is a creature that crushes). */
	public static boolean crushed(LivingEntity mob) {
		if (!mob.is(CRUSHABLE)) {
			return false;
		}
		if (mob.entityTags().contains(CRUSHED_TAG)) {
			return true;
		}
		DamageSource last = mob.getLastDamageSource();
		return last != null && last.is(CRUSHES);
	}

	/** {@code mob} is crushed: its legs scatter, its loot (and gear) drops loose, and it leaves no corpse. */
	public static void crush(LivingEntity mob, List<ItemStack> loot) {
		if (!(mob.level() instanceof ServerLevel level)) {
			return;
		}
		RandomSource random = level.getRandom();
		String look = Corpses.look(mob);
		float size = mob.getScale() * (mob.is(EntityTypes.CAVE_SPIDER) ? SpiderLeg.CAVE_SPIDER_SIZE : 1.0F);
		double reach = mob.getBbWidth() * 0.35;
		int legs = LEGS_MIN + random.nextInt(LEGS_MAX - LEGS_MIN + 1);
		for (int i = 0; i < legs; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double cos = Math.cos(angle), sin = Math.sin(angle);
			SpiderLeg leg = new SpiderLeg(SPIDER_LEG, level);
			leg.snapTo(mob.getX() + cos * reach, mob.getY() + 0.1, mob.getZ() + sin * reach, random.nextFloat() * 360.0F, 0.0F);
			double out = LEG_SPREAD * (0.4 + random.nextDouble() * 0.6) * size;
			leg.setDeltaMovement(cos * out, LEG_HOP * (0.5 + random.nextDouble() * 0.5), sin * out);
			leg.setUp(look, size, SpiderLeg.LIFE_MIN + random.nextInt(SpiderLeg.LIFE_MAX - SpiderLeg.LIFE_MIN + 1));
			level.addFreshEntity(leg);
		}
		for (ItemStack stack : loot) {
			drop(level, mob, stack);
		}
		for (EquipmentSlot slot : EquipmentSlot.VALUES) {
			ItemStack worn = mob.getItemBySlot(slot);
			if (!worn.isEmpty()) {
				drop(level, mob, worn.copy());
				mob.setItemSlot(slot, ItemStack.EMPTY);
			}
		}
		level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.SLIME_SQUISH, SoundSource.HOSTILE, 1.0F, 0.6F);
		level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.BONE_BLOCK_BREAK, SoundSource.HOSTILE, 0.9F, 1.4F);
	}

	private static void drop(ServerLevel level, LivingEntity mob, ItemStack stack) {
		if (stack.isEmpty() || stack.is(Items.SPIDER_EYE)) {
			// the eyes went with the head
			return;
		}
		ItemEntity item = new ItemEntity(level, mob.getX(), mob.getY() + 0.2, mob.getZ(), stack);
		item.setDefaultPickUpDelay();
		level.addFreshEntity(item);
	}
}
