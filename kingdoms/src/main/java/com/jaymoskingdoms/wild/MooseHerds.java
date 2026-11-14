package com.jaymoskingdoms.wild;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.mixin.MobAccessor;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.biome.v1.ModificationPhase;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Cube Animals' moose beside Kingdoms' bull ({@link Moose}), told apart by their antlers (Andy): one without is a cow
 * (the bull brings them), one with is a young bull. Young bulls are {@link #YOUNG_SCALE} the size with small antlers,
 * spawn in twos and threes where moose roam, and spar: every few minutes two of them walk up to each other and knock
 * antlers for a while, hurting nobody ({@link SparGoal}). The big bull runs a young one off his cows
 * ({@code Moose.DriveOff}), and goes for whatever hurts a cow near him.
 */
public final class MooseHerds {
	/** Marks a Cube moose we have made a young bull (its size and antlers are set once). */
	static final String YOUNG_TAG = "jaymoskingdoms.young_bull";
	/** A cow's antler scale is {@link Moose#COW_ANTLERS}; anything at or under this counts as none. */
	static final float NO_ANTLERS = -0.99F;
	/** A young bull's size (of a Cube moose), and its antlers (Cube adds the scale to 1). */
	static final double YOUNG_SCALE = 0.85;
	static final float YOUNG_ANTLERS_MIN = -0.55F, YOUNG_ANTLERS_MAX = -0.35F;
	/** Young bulls' own spawns in the moose biomes: weight among Cube's animals, and how many together. */
	static final TagKey<Biome> HAS_MOOSE = TagKey.create(Registries.BIOME, JaymosKingdoms.id("has_moose"));
	static final int YOUNG_WEIGHT = 6, YOUNG_MIN = 2, YOUNG_MAX = 3;
	/** Sparring: how far they find each other, how near they lock antlers (blocks), how long a bout lasts, the beat of the knocks and the rest between bouts (ticks). */
	static final double SPAR_RANGE = 24.0, SPAR_REACH = 2.8;
	static final int SPAR_TICKS_MIN = 160, SPAR_TICKS_MAX = 320, CLASH_EVERY = 24, SPAR_REST_MIN = 1200, SPAR_REST_MAX = 3600;
	static final int SPAR_LOOK_EVERY = 40;
	/** How hard a knock shoves each of them back. */
	static final double SPAR_PUSH = 0.35;
	/** A bull this near a hurt cow goes for what hurt her (blocks). */
	static final double DEFEND_RANGE = 24.0;

	private static final Set<PathfinderMob> GIVEN = Collections.newSetFromMap(new WeakHashMap<>());
	/** Who each sparring young bull is sparring with, and when each may spar again (game time). */
	private static final Map<PathfinderMob, PathfinderMob> PARTNER = new WeakHashMap<>();
	private static final Map<PathfinderMob, Long> REST = new WeakHashMap<>();

	private MooseHerds() {
	}

	static void register() {
		if (!FabricLoader.getInstance().isModLoaded(Identifier.parse(Moose.COW).getNamespace())) {
			return;
		}
		// (after Encounters has taken Cube's own moose spawns out)
		BiomeModifications.create(JaymosKingdoms.id("young_bull_moose")).add(ModificationPhase.POST_PROCESSING, BiomeSelectors.tag(HAS_MOOSE), context -> {
			EntityType<?> type = Moose.cowType();
			if (type != null) {
				context.getMobSpawnSettings().addSpawn(type, YOUNG_WEIGHT, YOUNG_MIN, YOUNG_MAX);
			}
		});
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity.getType() == Moose.cowType() && entity instanceof PathfinderMob mob && GIVEN.add(mob) && antlers(mob) > NO_ANTLERS) {
				young(mob);
			}
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (cow(entity) && source.getEntity() instanceof LivingEntity attacker && !(attacker instanceof Player player && player.isCreative())) {
				for (Moose bull : entity.level().getEntitiesOfClass(Moose.class, entity.getBoundingBox().inflate(DEFEND_RANGE))) {
					if (bull.getTarget() == null && bull.canAttack(attacker)) {
						bull.setTarget(attacker);
					}
				}
			}
		});
	}

	/** A Cube moose without antlers. */
	static boolean cow(Entity entity) {
		return entity.getType() == Moose.cowType() && !entity.entityTags().contains(YOUNG_TAG);
	}

	/** A grown Cube moose with antlers. */
	static boolean youngBull(Entity entity) {
		return entity.entityTags().contains(YOUNG_TAG) && entity.isAlive() && !(entity instanceof LivingEntity living && living.isBaby());
	}

	/** Makes a Cube moose with antlers a young bull (once: its size and antlers), and gives it the sparring. */
	private static void young(PathfinderMob mob) {
		if (mob.addTag(YOUNG_TAG)) {
			RandomSource random = mob.getRandom();
			antlers(mob, YOUNG_ANTLERS_MIN + random.nextFloat() * (YOUNG_ANTLERS_MAX - YOUNG_ANTLERS_MIN));
			AttributeInstance scale = mob.getAttribute(Attributes.SCALE);
			if (scale != null) {
				scale.setBaseValue(YOUNG_SCALE);
			}
		}
		((MobAccessor) mob).jaymoskingdoms$goals().addGoal(2, new SparGoal(mob));
	}

	/** Cube's antler scale ({@code MooseEntity}, reached by name so Kingdoms builds without it); 0 if it has none. */
	static float antlers(Entity moose) {
		try {
			Method get = moose.getClass().getMethod("getAntlersScale");
			return (float) get.invoke(moose);
		} catch (ReflectiveOperationException changed) {
			return 0.0F;
		}
	}

	static void antlers(Entity moose, float scale) {
		try {
			moose.getClass().getMethod("setAntlersScale", float.class).invoke(moose, scale);
		} catch (ReflectiveOperationException changed) {
			// a Cube Animals without that setter: the moose keeps the antlers it rolled
		}
	}

	/** Free to spar: grown, nobody's mount, not hurt, not after anyone. */
	private static boolean free(PathfinderMob mob) {
		return youngBull(mob) && !mob.isLeashed() && !mob.isVehicle() && mob.getTarget() == null && mob.getLastHurtByMob() == null;
	}

	/** Ends a young bull's bout (the big bull has run it off) and keeps it from another for a while. */
	static void breakOff(PathfinderMob mob) {
		PathfinderMob other = PARTNER.remove(mob);
		if (other != null) {
			PARTNER.remove(other);
		}
		REST.put(mob, mob.level().getGameTime() + SPAR_REST_MIN);
	}

	/** Two young bulls knock antlers: they close, shove each other back on a beat, close again, and part after a while. */
	private static final class SparGoal extends Goal {
		private final PathfinderMob mob;
		private @Nullable PathfinderMob other;
		private long endAt, nextClash;

		SparGoal(PathfinderMob mob) {
			this.mob = mob;
			this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			PathfinderMob asked = PARTNER.get(this.mob);
			if (asked != null) {
				// (another young bull has picked this one)
				this.other = asked;
				return free(this.mob) && asked.isAlive();
			}
			long now = this.mob.level().getGameTime();
			if ((now + this.mob.getId()) % SPAR_LOOK_EVERY != 0 || now < REST.getOrDefault(this.mob, 0L) || !free(this.mob)) {
				return false;
			}
			PathfinderMob nearest = null;
			for (PathfinderMob near : this.mob.level().getEntitiesOfClass(PathfinderMob.class, this.mob.getBoundingBox().inflate(SPAR_RANGE),
				e -> e != this.mob && free(e) && !PARTNER.containsKey(e) && now >= REST.getOrDefault(e, 0L))) {
				if (nearest == null || near.distanceToSqr(this.mob) < nearest.distanceToSqr(this.mob)) {
					nearest = near;
				}
			}
			if (nearest == null) {
				return false;
			}
			this.other = nearest;
			PARTNER.put(this.mob, nearest);
			PARTNER.put(nearest, this.mob);
			return true;
		}

		@Override
		public void start() {
			long now = this.mob.level().getGameTime();
			this.endAt = now + SPAR_TICKS_MIN + this.mob.getRandom().nextInt(SPAR_TICKS_MAX - SPAR_TICKS_MIN);
			this.nextClash = now;
		}

		@Override
		public boolean canContinueToUse() {
			return this.other != null && PARTNER.get(this.mob) == this.other && free(this.mob) && free(this.other)
				&& this.mob.level().getGameTime() < this.endAt && this.mob.distanceToSqr(this.other) < 4.0 * SPAR_RANGE * SPAR_RANGE;
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			PathfinderMob other = this.other;
			if (other == null) {
				return;
			}
			this.mob.getLookControl().setLookAt(other, 30.0F, 30.0F);
			if (this.mob.distanceToSqr(other) > SPAR_REACH * SPAR_REACH) {
				this.mob.getNavigation().moveTo(other, 1.0);
				return;
			}
			this.mob.getNavigation().stop();
			long now = this.mob.level().getGameTime();
			// (one of the two calls the knocks, for both)
			if (this.mob.getId() < other.getId() && now >= this.nextClash) {
				this.nextClash = now + CLASH_EVERY + this.mob.getRandom().nextInt(CLASH_EVERY / 2);
				Vec3 apart = other.position().subtract(this.mob.position()).multiply(1.0, 0.0, 1.0);
				apart = apart.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : apart.normalize();
				this.mob.playSound(SoundEvents.GOAT_RAM_IMPACT, 0.8F, 0.7F + this.mob.getRandom().nextFloat() * 0.2F);
				other.push(apart.x * SPAR_PUSH, 0.08, apart.z * SPAR_PUSH);
				this.mob.push(-apart.x * SPAR_PUSH, 0.08, -apart.z * SPAR_PUSH);
			}
		}

		@Override
		public void stop() {
			long rest = this.mob.level().getGameTime() + SPAR_REST_MIN + this.mob.getRandom().nextInt(SPAR_REST_MAX - SPAR_REST_MIN);
			PathfinderMob other = this.other;
			if (other != null && PARTNER.get(other) == this.mob) {
				PARTNER.remove(other);
				REST.put(other, rest);
			}
			PARTNER.remove(this.mob);
			REST.merge(this.mob, rest, Math::max);
			this.other = null;
			this.mob.getNavigation().stop();
		}
	}
}
