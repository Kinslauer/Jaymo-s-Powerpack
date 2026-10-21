package com.jaymospowers.core.wounds;

import static com.jaymospowers.core.wounds.BodyPart.*;

import com.jaymospowers.JaymosPowers;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.decoration.ArmorStand;
import org.jspecify.annotations.Nullable;

/** Which parts a kind of creature has, and each one's share of its most health (the shares are normalised to sum to 1). */
public enum BodyPlan {
	HUMANOID(new BodyPart[] {HEAD, BODY, LEFT_ARM, RIGHT_ARM, LEFT_LEG, RIGHT_LEG, LEFT_FOOT, RIGHT_FOOT},
		new float[] {0.15F, 0.30F, 0.10F, 0.10F, 0.12F, 0.12F, 0.055F, 0.055F}, new BodyPart[] {LEFT_FOOT, RIGHT_FOOT}),
	QUADRUPED(new BodyPart[] {HEAD, BODY, FRONT_LEGS, HIND_LEGS}, new float[] {0.2F, 0.4F, 0.2F, 0.2F}, new BodyPart[] {FRONT_LEGS, HIND_LEGS}),
	ARTHROPOD(new BodyPart[] {HEAD, BODY, LEGS}, new float[] {0.25F, 0.45F, 0.3F}, new BodyPart[] {LEGS}),
	FLYER(new BodyPart[] {HEAD, BODY, WINGS}, new float[] {0.25F, 0.45F, 0.3F}, new BodyPart[] {WINGS}),
	/** One part, so no locational damage: left to vanilla health. */
	SIMPLE(new BodyPart[] {BODY}, new float[] {1.0F}, new BodyPart[] {BODY});

	public static final TagKey<EntityType<?>> BOSSES = TagKey.create(Registries.ENTITY_TYPE, net.minecraft.resources.Identifier.fromNamespaceAndPath("c", "bosses"));
	private static final TagKey<EntityType<?>> HUMANOID_TAG = tag("body/humanoid");
	private static final TagKey<EntityType<?>> QUADRUPED_TAG = tag("body/quadruped");
	private static final TagKey<EntityType<?>> ARTHROPOD_TAG = tag("body/arthropod");
	private static final TagKey<EntityType<?>> FLYER_TAG = tag("body/flyer");
	private static final TagKey<EntityType<?>> SIMPLE_TAG = tag("body/simple");
	/** A creature of no tag, taller than this many times its width, is built like a person; anything else like an animal. */
	private static final double TALL = 1.3;
	private static final Map<EntityType<?>, BodyPlan> CACHE = new ConcurrentHashMap<>();

	private final BodyPart[] parts;
	private final float[] shares;
	private final BodyPart[] fallParts;

	BodyPlan(BodyPart[] parts, float[] shares, BodyPart[] fallParts) {
		this.parts = parts;
		float sum = 0.0F;
		for (float share : shares) {
			sum += share;
		}
		this.shares = new float[shares.length];
		for (int i = 0; i < shares.length; i++) {
			this.shares[i] = shares[i] / sum;
		}
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

	/** Share of the creature's most health that part {@code index} holds. */
	public float share(int index) {
		return this.shares[index];
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
		return entity.getBbHeight() > entity.getBbWidth() * TALL ? HUMANOID : QUADRUPED;
	}
}
