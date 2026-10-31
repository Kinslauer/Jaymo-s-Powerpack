package com.jaymospowers.young;

import com.jaymospowers.JaymosPowers;
import com.mojang.serialization.Codec;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ServerLevelAccessor;

/**
 * (0.18.115, Motion patch 7) Young for the creatures the game never gave any: spiders and cave spiders, parrots, and our
 * own wild beasts, whales, aerwhales, skyroot lizards and pigeons (tag {@code jaymospowers:has_young}). Andy: "Just add
 * baby spiders and the wild and farm animal babies that don't already exist." Some natural spawns come with a brood or
 * a calf or two at their side. A youngling is the same creature made small through the scale attribute (so its hitbox,
 * model and any look it takes from its biome all follow), weaker in step, squeakier, drops nothing, keeps to its
 * elders, and grows to full size over {@link #GROW_TICKS}.
 */
public final class Younglings {
	/** The creatures that have young; the big ones among them (their young start smaller still). */
	public static final TagKey<EntityType<?>> HAS_YOUNG = TagKey.create(Registries.ENTITY_TYPE, JaymosPowers.id("has_young"));
	public static final TagKey<EntityType<?>> BIG_YOUNG = TagKey.create(Registries.ENTITY_TYPE, JaymosPowers.id("big_young"));
	/** How small a youngling starts (of full size), and a big creature's. */
	static final float START = 0.45F, BIG_START = 0.3F;
	/** How long it takes to grow up (ticks; a game day). */
	static final int GROW_TICKS = 24000;
	/** How often the young are looked at (ticks). */
	static final int EVERY = 100;
	/** The chance a natural spawn brings young, and how many: spiders a brood, others one or two. */
	static final float BROOD_CHANCE = 0.25F;
	static final int BROOD_MIN = 2, BROOD_MAX = 4, YOUNG_MIN = 1, YOUNG_MAX = 2;
	/** A youngling's voice, higher by this much. */
	static final float VOICE = 1.45F;
	/** Keeping to its elders: looked for this far, gone after past this far, at this pace. */
	static final double ELDER_RANGE = 24.0, ELDER_LEASH = 6.0, ELDER_SPEED = 1.15;

	/** Ticks left until a youngling is grown (only the young carry it). */
	public static final AttachmentType<Integer> YOUNG = AttachmentRegistry.create(JaymosPowers.id("young"),
		builder -> builder.persistent(Codec.INT));

	private static final Identifier SIZE = JaymosPowers.id("young_size");
	private static final Identifier MIGHT = JaymosPowers.id("young_might");
	private static final Set<Mob> LOADED = Collections.newSetFromMap(new WeakHashMap<>());
	/** Set while a brood is being made, so its members bring none of their own. */
	private static boolean breeding;

	private Younglings() {
	}

	public static void init() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Mob mob && mob.hasAttached(YOUNG)) {
				LOADED.add(mob);
				size(mob);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> LOADED.remove(entity));
		ServerTickEvents.END_SERVER_TICK.register(Younglings::tick);
	}

	/** {@code entity} is still young. */
	public static boolean young(Entity entity) {
		return entity.hasAttached(YOUNG);
	}

	/** A youngling's voice. */
	public static float voice(LivingEntity entity, float pitch) {
		return young(entity) ? pitch * VOICE : pitch;
	}

	/** Called as a mob finishes spawning: a natural one may bring young. */
	public static void spawned(Mob mob, ServerLevelAccessor accessor, EntitySpawnReason reason) {
		if (breeding || young(mob) || reason != EntitySpawnReason.NATURAL && reason != EntitySpawnReason.CHUNK_GENERATION
			|| !mob.getType().builtInRegistryHolder().is(HAS_YOUNG)) {
			return;
		}
		RandomSource random = accessor.getRandom();
		if (random.nextFloat() >= BROOD_CHANCE) {
			return;
		}
		boolean brood = mob.getType().builtInRegistryHolder().is(net.minecraft.tags.EntityTypeTags.ARTHROPOD);
		int n = brood ? Mth.nextInt(random, BROOD_MIN, BROOD_MAX) : Mth.nextInt(random, YOUNG_MIN, YOUNG_MAX);
		breeding = true;
		try {
			for (int i = 0; i < n; i++) {
				Entity made = mob.getType().create(accessor.getLevel(), reason);
				if (!(made instanceof Mob child)) {
					continue;
				}
				child.snapTo(mob.getX() + (random.nextFloat() - 0.5F), mob.getY(), mob.getZ() + (random.nextFloat() - 0.5F),
					random.nextFloat() * 360.0F, 0.0F);
				makeYoung(child, GROW_TICKS);
				accessor.addFreshEntityWithPassengers(child);
			}
		} finally {
			breeding = false;
		}
	}

	/** Makes {@code mob} a youngling with {@code left} ticks to grow. */
	public static void makeYoung(Mob mob, int left) {
		mob.setAttached(YOUNG, left);
		size(mob);
		mob.setHealth(mob.getMaxHealth());
		LOADED.add(mob);
	}

	/** Its size and might for how far it has grown. */
	private static void size(Mob mob) {
		Integer left = mob.getAttached(YOUNG);
		if (left == null) {
			return;
		}
		float start = mob.getType().builtInRegistryHolder().is(BIG_YOUNG) ? BIG_START : START;
		float grown = 1.0F - Mth.clamp(left / (float) GROW_TICKS, 0.0F, 1.0F);
		float size = Mth.lerp(grown, start, 1.0F);
		modify(mob, Attributes.SCALE, SIZE, size - 1.0F);
		modify(mob, Attributes.MAX_HEALTH, MIGHT, size - 1.0F);
		modify(mob, Attributes.ATTACK_DAMAGE, MIGHT, size - 1.0F);
		if (mob.getHealth() > mob.getMaxHealth()) {
			mob.setHealth(mob.getMaxHealth());
		}
	}

	private static void modify(Mob mob, Holder<Attribute> attribute, Identifier id, double amount) {
		AttributeInstance instance = mob.getAttribute(attribute);
		if (instance != null) {
			instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
	}

	private static void grownUp(Mob mob) {
		mob.removeAttached(YOUNG);
		for (Holder<Attribute> attribute : List.of(Attributes.SCALE, Attributes.MAX_HEALTH, Attributes.ATTACK_DAMAGE)) {
			AttributeInstance instance = mob.getAttribute(attribute);
			if (instance != null) {
				instance.removeModifier(attribute == Attributes.SCALE ? SIZE : MIGHT);
			}
		}
		LOADED.remove(mob);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % EVERY != 0 || LOADED.isEmpty()) {
			return;
		}
		for (Mob mob : List.copyOf(LOADED)) {
			if (!mob.isAlive() || !(mob.level() instanceof ServerLevel)) {
				LOADED.remove(mob);
				continue;
			}
			int left = mob.getAttachedOrElse(YOUNG, 0) - EVERY;
			if (left <= 0) {
				grownUp(mob);
				continue;
			}
			mob.setAttached(YOUNG, left);
			size(mob);
			keepClose(mob);
		}
	}

	/** A youngling with nothing to chase trails the nearest grown one of its kind. */
	private static void keepClose(Mob mob) {
		if (mob.getTarget() != null || mob.isPassenger() || mob.isLeashed()) {
			return;
		}
		Mob elder = null;
		double best = ELDER_RANGE * ELDER_RANGE;
		for (Mob other : mob.level().getEntitiesOfClass(Mob.class, mob.getBoundingBox().inflate(ELDER_RANGE),
			o -> o != mob && o.getType() == mob.getType() && o.isAlive() && !young(o))) {
			double d = other.distanceToSqr(mob);
			if (d < best) {
				best = d;
				elder = other;
			}
		}
		if (elder != null && best > ELDER_LEASH * ELDER_LEASH) {
			mob.getNavigation().moveTo(elder, ELDER_SPEED);
		}
	}
}
