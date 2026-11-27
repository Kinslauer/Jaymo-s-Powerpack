package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;

/**
 * Food a player hands to someone hungry ({@link Approaches}: "Spare a crust...") is eaten there and then. Andy
 * (2026-11-26): they should actually eat after receiving your items. They hold it in their hand and eat it over
 * {@link #EAT_TICKS} (stood still, a bite every {@link #BITE_EVERY} ticks with the sound and the crumbs), then it's gone,
 * its nutrition healed and whatever they held before back in their hand. While they eat, talking to them gets a line
 * about the meal ({@link Recollections}: {@code recall.jaymoskingdoms.just.fed_eating.*}).
 */
public final class HandedFood {
	/** How long a handed meal takes to eat, and how often a bite (ticks). */
	static final int EAT_TICKS = 60, BITE_EVERY = 5;

	private record Eating(Mob mob, ItemStack food, ItemStack held, long start) {
	}

	private static final Map<UUID, Eating> EATING = new HashMap<>();

	private HandedFood() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(HandedFood::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (Eating e : EATING.values()) {
				e.mob.setItemInHand(InteractionHand.MAIN_HAND, e.held);
			}
			EATING.clear();
		});
	}

	/** {@code mob} starts eating one of {@code food} (a copy is taken; the caller takes it from the giver). */
	static void eat(Mob mob, ItemStack food) {
		Eating was = EATING.remove(mob.getUUID());
		ItemStack held = was != null ? was.held : mob.getMainHandItem().copy();
		mob.setItemInHand(InteractionHand.MAIN_HAND, food.copyWithCount(1));
		EATING.put(mob.getUUID(), new Eating(mob, food.copyWithCount(1), held, mob.level().getGameTime()));
	}

	/** {@code who} is eating food handed to them just now. */
	public static boolean eating(LivingEntity who) {
		return EATING.containsKey(who.getUUID());
	}

	private static void tick(MinecraftServer server) {
		if (EATING.isEmpty()) {
			return;
		}
		for (Iterator<Eating> it = EATING.values().iterator(); it.hasNext(); ) {
			Eating e = it.next();
			Mob mob = e.mob;
			if (!mob.isAlive() || mob.isRemoved() || !(mob.level() instanceof ServerLevel level)) {
				it.remove();
				continue;
			}
			long t = level.getGameTime() - e.start;
			if (t >= EAT_TICKS) {
				it.remove();
				FoodProperties props = e.food.get(DataComponents.FOOD);
				mob.heal(props == null ? 2.0F : props.nutrition());
				mob.setItemInHand(InteractionHand.MAIN_HAND, e.held);
				level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.PLAYER_BURP, SoundSource.NEUTRAL, 0.5F,
					0.9F + level.getRandom().nextFloat() * 0.2F);
				continue;
			}
			mob.getNavigation().stop();
			if (t % BITE_EVERY == 0) {
				mob.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
				level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, e.food.getItem()), mob.getX(), mob.getEyeY() - 0.2, mob.getZ(),
					5, 0.12, 0.08, 0.12, 0.04);
				level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.GENERIC_EAT.value(), SoundSource.NEUTRAL, 0.6F,
					0.9F + level.getRandom().nextFloat() * 0.2F);
			}
		}
	}
}
