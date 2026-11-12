package com.jaymoskingdoms.wild;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.mixin.MobAccessor;
import com.mojang.serialization.Codec;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.polarbear.PolarBear;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * A fed predator stops hunting. After a hunter in {@code jaymoskingdoms:sated_predators} (polar bears, wolves, foxes,
 * feral wolves, coyotes, and other mods' hunters) kills an animal, it doesn't pick another animal as its quarry for
 * {@link #FED_TICKS}, so one pack doesn't empty a valley. Only its own hunting stops: players and villagers are as
 * much its quarry as ever (a wolf's stalk, {@link WolfStalkers}, is no part of this), and it still turns on whatever
 * hurts it. The time it's fed until is kept on the animal ({@link #FED_UNTIL}), so it holds over a reload.
 *
 * <p>Hungry, it goes for the young first: when it picks an animal and one of the young it would hunt is in reach, it
 * takes that one instead ({@link #choose}, from {@code mixin/wild/PredatorTargetMixin}). Nothing here runs on a timer
 * of its own: it's asked only when the hunter's own search has found something.
 *
 * <p>And polar bears hunt muskox ({@link Muskox}).
 */
public final class FedPredators {
	/** Hunters that hunt only when hungry. */
	static final TagKey<EntityType<?>> SATED = TagKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id("sated_predators"));
	/** How long a kill feeds it (ticks: two days). */
	static final long FED_TICKS = 48000L;
	/** The polar bear's hunt for muskox: its place among the bear's targets, and how seldom it looks (one tick in this many). */
	static final int BEAR_HUNT_PRIORITY = 4, BEAR_HUNT_INTERVAL = 20;

	/** Game time a hunter is fed until. */
	static final AttachmentType<Long> FED_UNTIL = AttachmentRegistry.create(JaymosKingdoms.id("predator_fed_until"),
		builder -> builder.persistent(Codec.LONG));

	private static final Set<Mob> GIVEN = Collections.newSetFromMap(new WeakHashMap<>());

	private FedPredators() {
	}

	public static void register() {
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof Animal && source.getEntity() instanceof Mob killer && sated(killer) && killer.level() instanceof ServerLevel level) {
				killer.setAttached(FED_UNTIL, level.getGameTime() + FED_TICKS);
			}
		});
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof PolarBear bear && GIVEN.add(bear)) {
				((MobAccessor) bear).jaymoskingdoms$targets().addGoal(BEAR_HUNT_PRIORITY,
					new NearestAttackableTargetGoal<>(bear, Muskox.class, BEAR_HUNT_INTERVAL, true, false, (target, world) -> !bear.isBaby()));
			}
		});
	}

	static boolean sated(Mob mob) {
		return mob.getType().builtInRegistryHolder().is(SATED);
	}

	/** Whether {@code mob} has eaten lately. */
	public static boolean fed(Mob mob) {
		Long until = mob.getAttached(FED_UNTIL);
		return until != null && mob.level().getGameTime() < until;
	}

	/**
	 * (From {@code PredatorTargetMixin}) {@code mob}'s own search found {@code found}. What it hunts: nothing, if
	 * {@code found} is an animal and the hunter is fed; one of the young of what it was looking for ({@code kind}, in
	 * {@code area}, by its own {@code conditions}), if there is one; else {@code found}.
	 */
	public static @Nullable LivingEntity choose(Mob mob, LivingEntity found, Class<? extends LivingEntity> kind, TargetingConditions conditions, AABB area) {
		if (!(found instanceof Animal) || !sated(mob) || !(mob.level() instanceof ServerLevel level)) {
			return found;
		}
		if (fed(mob)) {
			return null;
		}
		if (found.isBaby()) {
			return found;
		}
		LivingEntity young = level.getNearestEntity(level.getEntitiesOfClass(kind, area, other -> other instanceof Animal && other.isBaby()),
			conditions, mob, mob.getX(), mob.getEyeY(), mob.getZ());
		return young != null ? young : found;
	}
}
