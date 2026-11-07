package com.jaymospowers.core;

import com.jaymospowers.JaymosPowers;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;

/**
 * (0.21.7) Andy: "you can pick up live animals, but not all of them like it, snakes for example will bite you". Any small
 * live animal can be lifted now ({@link #liftable}), not just {@link Carry#CARRYABLE}'s. A wild grown one that won't stand
 * for it fights its way out of your arms after a moment: it bites ({@link #BITES}: snakes, wolves, foxes; a snake's bite
 * poisons), scratches ({@link #SCRATCHES}: cats), or squirms free ({@link #SQUIRMS}: chickens, rabbits). Tamed ones and
 * the young stay put.
 */
public final class CarryTemper {
	// ---- tuning ----
	/** A live animal no bigger than this (blocks, standing) can be lifted by hand. */
	static final float LIVE_MAX_WIDTH = 0.8F, LIVE_MAX_HEIGHT = 1.0F;
	/** How long (ticks) an unwilling animal stands being held before it fights free: the least, plus up to this much more. */
	static final int BITE_MIN = 10, BITE_SPREAD = 30, SQUIRM_MIN = 50, SQUIRM_SPREAD = 70;
	/** A bite's and a scratch's damage (half hearts). */
	static final float BITE_DAMAGE = 2.0F, SCRATCH_DAMAGE = 1.0F;
	/** A snake's venom: poison this long (ticks) at this level (0 = I). */
	static final int VENOM_TICKS = 140, VENOM_AMP = 0;

	public static final TagKey<EntityType<?>> BITES = TagKey.create(Registries.ENTITY_TYPE, JaymosPowers.id("bites_when_held"));
	public static final TagKey<EntityType<?>> SCRATCHES = TagKey.create(Registries.ENTITY_TYPE, JaymosPowers.id("scratches_when_held"));
	public static final TagKey<EntityType<?>> SQUIRMS = TagKey.create(Registries.ENTITY_TYPE, JaymosPowers.id("squirms_when_held"));

	/** How long each unwilling animal has been held (ticks) and when it fights free. */
	private static final Map<Entity, int[]> HELD = new WeakHashMap<>();

	private CarryTemper() {
	}

	/** A small live animal (not a monster) anyone can lift by hand. */
	public static boolean liftable(Entity entity) {
		if (!(entity instanceof Animal) && !venomous(entity) || entity instanceof Enemy) {
			return false;
		}
		var size = entity.getType().getDimensions();
		return size.width() <= LIVE_MAX_WIDTH && size.height() <= LIVE_MAX_HEIGHT;
	}

	private enum Temper { BITES, SCRATCHES, SQUIRMS }

	private static Temper temper(LivingEntity animal) {
		if (animal instanceof TamableAnimal tame && tame.isTame() || animal.isBaby() || com.jaymospowers.core.wounds.Downed.isDowned(animal)) {
			return null;
		}
		var holder = animal.getType().builtInRegistryHolder();
		if (holder.is(BITES) || venomous(animal)) {
			return Temper.BITES;
		}
		if (holder.is(SCRATCHES)) {
			return Temper.SCRATCHES;
		}
		return holder.is(SQUIRMS) ? Temper.SQUIRMS : null;
	}

	/** A snake (or the like): its bite poisons. */
	static boolean venomous(Entity entity) {
		String path = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath();
		return path.contains("snake") || path.contains("viper") || path.contains("cobra") || path.contains("adder") || path.contains("scorpion");
	}

	/** {@code carrier} holds {@code carried} this tick: true once it has fought its way free (the carry should end). */
	static boolean tick(ServerPlayer carrier, Entity carried) {
		if (!(carried instanceof LivingEntity animal) || carried instanceof net.minecraft.world.entity.player.Player) {
			return false;
		}
		Temper temper = temper(animal);
		if (temper == null) {
			HELD.remove(carried);
			return false;
		}
		int[] held = HELD.computeIfAbsent(carried, e -> new int[] {0, temper == Temper.SQUIRMS
			? SQUIRM_MIN + carrier.getRandom().nextInt(SQUIRM_SPREAD) : BITE_MIN + carrier.getRandom().nextInt(BITE_SPREAD)});
		if (++held[0] < held[1]) {
			return false;
		}
		HELD.remove(carried);
		ServerLevel level = carrier.level();
		switch (temper) {
			case BITES -> {
				carrier.hurtServer(level, level.damageSources().mobAttack(animal), BITE_DAMAGE);
				if (venomous(animal)) {
					carrier.addEffect(new MobEffectInstance(MobEffects.POISON, VENOM_TICKS, VENOM_AMP), animal);
				}
				level.playSound(null, carrier.getX(), carrier.getY(), carrier.getZ(), SoundEvents.FOX_BITE, SoundSource.NEUTRAL, 0.9F, 0.8F);
			}
			case SCRATCHES -> {
				carrier.hurtServer(level, level.damageSources().mobAttack(animal), SCRATCH_DAMAGE);
				level.playSound(null, carrier.getX(), carrier.getY(), carrier.getZ(), SoundEvents.OCELOT_HURT, SoundSource.NEUTRAL, 0.9F, 1.2F);
			}
			case SQUIRMS -> level.playSound(null, carrier.getX(), carrier.getY(), carrier.getZ(), SoundEvents.PARROT_FLY, SoundSource.NEUTRAL, 0.7F, 1.0F);
		}
		carrier.sendOverlayMessage(Component.translatable("message.jaymospowers.carry.temper." + temper.name().toLowerCase(java.util.Locale.ROOT),
			animal.getDisplayName()).withStyle(ChatFormatting.GRAY));
		return true;
	}
}
