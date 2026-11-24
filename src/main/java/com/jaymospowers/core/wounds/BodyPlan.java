package com.jaymospowers.core.wounds;

import static com.jaymospowers.core.wounds.BodyPart.*;

import com.jaymospowers.JaymosPowers;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.decoration.ArmorStand;
import org.jspecify.annotations.Nullable;

/**
 * Which parts a kind of creature has, and the size of each one's own pool as a share of its most health. (Survival picks,
 * lso-pools) The pools sit on top of the health, apart from it: a humanoid's head 0.4, body 0.6, each arm 0.8, each leg
 * 1.2, each foot 0.8 (8/12/16/24/16 on 20 health). The other plans are scaled the same way: head 0.4, body 0.6, legs 1.2,
 * wings 0.8 (as arms). A simple body's one part is its health.
 * <p>
 * Andy: players "were breaking their limbs off short jumps", so the limbs (arms, legs, feet, wings) hold twice what they
 * did; head and body are as before. A limb breaks, and a blow past {@link com.jaymospowers.core.Dismember#SEVER_OVERKILL}
 * of it severs it, only after twice the damage.
 */
public enum BodyPlan {
	HUMANOID(new BodyPart[] {HEAD, BODY, LEFT_ARM, RIGHT_ARM, LEFT_LEG, RIGHT_LEG, LEFT_FOOT, RIGHT_FOOT},
		new float[] {Pools.HEAD, Pools.BODY, Pools.ARM, Pools.ARM, Pools.LEG, Pools.LEG, Pools.FOOT, Pools.FOOT}, new BodyPart[] {LEFT_FOOT, RIGHT_FOOT}),
	QUADRUPED(new BodyPart[] {HEAD, BODY, FRONT_LEGS, HIND_LEGS}, new float[] {Pools.HEAD, Pools.BODY, Pools.LEG, Pools.LEG}, new BodyPart[] {FRONT_LEGS, HIND_LEGS}),
	ARTHROPOD(new BodyPart[] {HEAD, BODY, LEGS}, new float[] {Pools.HEAD, Pools.BODY, Pools.LEG}, new BodyPart[] {LEGS}),
	FLYER(new BodyPart[] {HEAD, BODY, WINGS}, new float[] {Pools.HEAD, Pools.BODY, Pools.ARM}, new BodyPart[] {WINGS}),
	/** One part, so no locational damage: left to vanilla health. */
	SIMPLE(new BodyPart[] {BODY}, new float[] {1.0F}, new BodyPart[] {BODY});

	/** (Survival picks, lso-pools) Each pool's size as a share of the most health; tuning lives here. */
	public static final class Pools {
		public static final float HEAD = 0.4F, BODY = 0.6F, ARM = 0.8F, LEG = 1.2F, FOOT = 0.8F;

		private Pools() {
		}
	}

	public static final TagKey<EntityType<?>> BOSSES = TagKey.create(Registries.ENTITY_TYPE, net.minecraft.resources.Identifier.fromNamespaceAndPath("c", "bosses"));
	private static final TagKey<EntityType<?>> HUMANOID_TAG = tag("body/humanoid");
	private static final TagKey<EntityType<?>> QUADRUPED_TAG = tag("body/quadruped");
	private static final TagKey<EntityType<?>> ARTHROPOD_TAG = tag("body/arthropod");
	private static final TagKey<EntityType<?>> FLYER_TAG = tag("body/flyer");
	private static final TagKey<EntityType<?>> SIMPLE_TAG = tag("body/simple");
	/**
	 * A creature of no tag, taller than this many times its width, is built like a person; anything else like an animal.
	 * (0.20.53) Never a wild creature (see {@link #wild}): a tall deer or elephant is still an animal.
	 */
	private static final double TALL = 1.3;
	private static final Map<EntityType<?>, BodyPlan> CACHE = new ConcurrentHashMap<>();

	private final BodyPart[] parts;
	private final float[] shares;
	/** The pools' shares added up (a humanoid's 6.6). */
	private final float total;
	private final BodyPart[] fallParts;

	BodyPlan(BodyPart[] parts, float[] shares, BodyPart[] fallParts) {
		this.parts = parts;
		this.shares = shares.clone();
		float sum = 0.0F;
		for (float share : shares) {
			sum += share;
		}
		this.total = sum;
		this.fallParts = fallParts;
	}

	private static TagKey<EntityType<?>> tag(String path) {
		return TagKey.create(Registries.ENTITY_TYPE, JaymosPowers.id(path));
	}

	public int size() {
		return this.parts.length;
	}

	public BodyPart part(int index) {
		return this.parts[index];
	}

	/** The size of part {@code index}'s own pool, as a share of the creature's most health. */
	public float share(int index) {
		return this.shares[index];
	}

	/** Every pool's share added up: all of them healed from nothing is this many times the most health. */
	public float total() {
		return this.total;
	}

	/** This part's place, or -1 if the plan has none. */
	public int index(BodyPart part) {
		for (int i = 0; i < this.parts.length; i++) {
			if (this.parts[i] == part) {
				return i;
			}
		}
		return -1;
	}

	/** The part itself if the plan has it, else where its damage would spill (ending at the body, which every plan has). */
	public BodyPart resolve(BodyPart part) {
		BodyPart at = part;
		while (this.index(at) < 0) {
			at = at.overflow();
		}
		return at;
	}

	/** What a fall lands on. */
	public BodyPart[] fallParts() {
		return this.fallParts;
	}

	/** The plan for {@code entity}, or null if it keeps plain vanilla health (bosses, armor stands). */
	public static @Nullable BodyPlan of(LivingEntity entity) {
		if (entity instanceof ArmorStand || entity instanceof EnderDragon || entity instanceof WitherBoss
			|| entity.getType().builtInRegistryHolder().is(BOSSES)) {
			return null;
		}
		BodyPlan plan = CACHE.get(entity.getType());
		if (plan == null) {
			plan = pick(entity);
			CACHE.put(entity.getType(), plan);
		}
		return plan;
	}

	private static BodyPlan pick(LivingEntity entity) {
		var type = entity.getType().builtInRegistryHolder();
		if (type.is(HUMANOID_TAG)) {
			return HUMANOID;
		}
		if (type.is(QUADRUPED_TAG)) {
			return QUADRUPED;
		}
		if (type.is(ARTHROPOD_TAG)) {
			return ARTHROPOD;
		}
		if (type.is(FLYER_TAG)) {
			return FLYER;
		}
		if (type.is(SIMPLE_TAG)) {
			return SIMPLE;
		}
		return !wild(entity.getType().getCategory()) && entity.getBbHeight() > entity.getBbWidth() * TALL ? HUMANOID : QUADRUPED;
	}

	/**
	 * (0.20.53) Andy (2026-11-05): "downed animals should fall to their side, i think they're trying to animate like
	 * humanoids". Cube Animals' roe deer (0.9 by 1.5) was taller than wide, so it was taken for a person: it went down on
	 * its back, propped up like one, and was hurt in arms and feet. Whatever spawns as wildlife (animals, ambient things,
	 * sea life) isn't a person unless it's tagged one (the wandering trader is).
	 */
	public static boolean wild(MobCategory category) {
		return category == MobCategory.CREATURE || category == MobCategory.AMBIENT || category == MobCategory.WATER_CREATURE
			|| category == MobCategory.WATER_AMBIENT || category == MobCategory.UNDERGROUND_WATER_CREATURE || category == MobCategory.AXOLOTLS;
	}
}
