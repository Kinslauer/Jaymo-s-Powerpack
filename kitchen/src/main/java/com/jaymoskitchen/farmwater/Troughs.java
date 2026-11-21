package com.jaymoskitchen.farmwater;

import com.jaymoskitchen.JaymosKitchen;
import com.mojang.serialization.Codec;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * What a {@link TroughBlock} does to the animals around it (Let's Do's trough, our own). Farm animals of its pen come
 * to a trough with something in it, eat what they would eat in the wild (a cow wheat, a pig carrots, a hen seeds) and
 * stay fed for a while ({@link #FED_FOR}): a fed adult that can breed falls in love, a fed one is healed, and a fed
 * sheep leaves the grass alone ({@code EatBlockGoalMixin}), so a fed herd crops the ground less. A bite now and then
 * takes a level from the trough; when it's empty they are left to wander and graze as before.
 */
public final class Troughs {
	/** Ticks between looks at a trough, and how far from it (blocks) its pen reaches and how near an animal must be to eat. */
	public static final int CHECK = 40;
	static final double PEN = 8.0, REACH = 2.2;
	/** How long an animal stays fed after a bite (ticks). */
	static final long FED_FOR = 6000L;
	/** One bite in this many (per animal, per look) is taken at all; one bite in this many takes a level from the trough. */
	static final int BITE_ONE_IN = 2, LEVEL_ONE_IN = 10;
	/** A fed adult that can breed falls in love one bite in this many. */
	static final int LOVE_ONE_IN = 3;
	/** What a pen animal is offered to see whether it eats the trough's feed. */
	private static final Item[] FEEDS = {Items.WHEAT, Items.CARROT, Items.WHEAT_SEEDS};

	/** Farm animals a trough feeds. */
	public static final TagKey<EntityType<?>> FARM_ANIMALS = TagKey.create(Registries.ENTITY_TYPE, JaymosKitchen.id("farm_animals"));
	/** The game time an animal is fed until. Saved on it. */
	public static final AttachmentType<Long> FED_UNTIL = AttachmentRegistry.create(JaymosKitchen.id("fed_until"), b -> b.persistent(Codec.LONG));

	private Troughs() {
	}

	public static void register() {
		JaymosKitchen.LOGGER.debug("trough attachment: {}", FED_UNTIL);
	}

	/** Whether {@code animal} has been fed at a trough lately. */
	public static boolean fed(Animal animal) {
		return animal.getAttachedOrElse(FED_UNTIL, 0L) > animal.level().getGameTime();
	}

	private static boolean eats(Animal animal) {
		for (Item feed : FEEDS) {
			if (animal.isFood(new ItemStack(feed))) {
				return true;
			}
		}
		return false;
	}

	/** One look at a trough; whether it still has anything in it (so another look is due). */
	static boolean tend(ServerLevel level, BlockPos pos, BlockState state, RandomSource random) {
		int fill = state.getValue(TroughBlock.FILL);
		if (fill <= 0) {
			return false;
		}
		long now = level.getGameTime();
		List<Animal> pen = level.getEntitiesOfClass(Animal.class, new AABB(pos).inflate(PEN),
			a -> a.isAlive() && a.getType().builtInRegistryHolder().is(FARM_ANIMALS) && eats(a));
		double x = pos.getX() + 0.5, y = pos.getY(), z = pos.getZ() + 0.5;
		for (Animal animal : pen) {
			if (animal.distanceToSqr(x, y, z) > REACH * REACH) {
				if (animal.getNavigation().isDone()) {
					animal.getNavigation().moveTo(x, y, z, 1.0);
				}
				continue;
			}
			if (random.nextInt(BITE_ONE_IN) != 0) {
				continue;
			}
			animal.setAttached(FED_UNTIL, now + FED_FOR);
			animal.heal(1.0F);
			animal.playSound(SoundEvents.GENERIC_EAT.value(), 0.5F, 1.0F);
			level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, Items.WHEAT), x, y + 0.6, z, 3, 0.2, 0.05, 0.2, 0.03);
			if (animal.getAge() == 0 && animal.canFallInLove() && random.nextInt(LOVE_ONE_IN) == 0) {
				animal.setInLove(null);
			}
			if (random.nextInt(LEVEL_ONE_IN) == 0) {
				fill--;
				if (fill <= 0) {
					break;
				}
			}
		}
		if (fill != state.getValue(TroughBlock.FILL)) {
			level.setBlockAndUpdate(pos, state.setValue(TroughBlock.FILL, Math.max(0, fill)));
		}
		return fill > 0;
	}
}
