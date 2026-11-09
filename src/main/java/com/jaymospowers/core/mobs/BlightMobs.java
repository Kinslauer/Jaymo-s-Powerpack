package com.jaymospowers.core.mobs;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.mixin.MobPersistenceAccess;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.ServerLevelAccessor;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * (Blight mobs) Elite variants of existing mobs that spawn rarely with boosted stats, a name, and a visible tell.
 * Scales with local difficulty. Reuses existing mobs (the pack's own monsters first).
 */
public final class BlightMobs {
	/** Mobs that can become blighted; defaults to all monsters but can be restricted via data pack. */
	public static final TagKey<EntityType<?>> CAN_BE_BLIGHTED = TagKey.create(Registries.ENTITY_TYPE, JaymosPowers.id("can_be_blighted"));

	/** Base chance for a natural spawn to be blighted (per difficulty point). */
	private static final float BASE_CHANCE_PER_DIFFICULTY = 0.008F; // ~0.8% at difficulty 1, ~3% at max
	/** Maximum blight chance cap. */
	private static final float MAX_CHANCE = 0.15F; // 15% at highest difficulty
	/** Blight tiers: 1=normal elite, 2=strong, 3=champion. */
	private static final int MAX_TIER = 3;
	/** Health multiplier per tier (additive on top of base). */
	private static final float HEALTH_PER_TIER = 0.75F; // +75% per tier
	/** Damage multiplier per tier. */
	private static final float DAMAGE_PER_TIER = 0.4F; // +40% per tier
	/** Speed multiplier per tier. */
	private static final float SPEED_PER_TIER = 0.15F; // +15% per tier
	/** How many name suffixes the blight names draw from (the words live in lang/parts/blight.json). */
	private static final int SUFFIXES = 4;

	/** Persistent attachment: holds the blight tier (0 = not blighted, 1-3 = tier). */
	public static final AttachmentType<Integer> BLIGHT = AttachmentRegistry.create(JaymosPowers.id("blight"),
		builder -> builder.persistent(Codec.INT));

	/** Attribute modifier IDs. */
	private static final Identifier HEALTH_ID = JaymosPowers.id("blight_health");
	private static final Identifier DAMAGE_ID = JaymosPowers.id("blight_damage");
	private static final Identifier SPEED_ID = JaymosPowers.id("blight_speed");
	private static final Identifier ARMOR_ID = JaymosPowers.id("blight_armor");
	private static final Identifier FOLLOW_RANGE_ID = JaymosPowers.id("blight_follow_range");

	/** Currently loaded blighted mobs for ticking. */
	private static final Set<Mob> LOADED = Collections.newSetFromMap(new WeakHashMap<>());
	/** Tick interval for blight effects (particles, etc.). */
	private static final int EVERY = 40;

	private BlightMobs() {
	}

	public static void init() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Mob mob && mob.hasAttached(BLIGHT)) {
				LOADED.add(mob);
				applyAttributes(mob);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> LOADED.remove(entity));
		ServerTickEvents.END_SERVER_TICK.register(BlightMobs::tick);
	}

	/** Called when a mob finishes spawning; may turn it into a blighted elite. */
	public static void spawned(Mob mob, ServerLevelAccessor accessor, EntitySpawnReason reason) {
		if (reason != EntitySpawnReason.NATURAL && reason != EntitySpawnReason.CHUNK_GENERATION) {
			return;
		}
		if (mob.hasAttached(BLIGHT)) {
			return; // already blighted (e.g. from a spawner or summon)
		}
		if (!(mob instanceof Monster)) {
			return; // only hostile monsters for now
		}
		if (!mob.getType().builtInRegistryHolder().is(CAN_BE_BLIGHTED)) {
			return;
		}

		float difficultyValue = accessor.getLevel() instanceof ServerLevel sl
			? sl.getCurrentDifficultyAt(mob.blockPosition()).getEffectiveDifficulty()
			: 2.0F;
		float chance = Mth.clamp(difficultyValue * BASE_CHANCE_PER_DIFFICULTY, 0, MAX_CHANCE);
		RandomSource random = accessor.getRandom();
		if (random.nextFloat() >= chance) {
			return;
		}

		// Determine tier based on difficulty (higher difficulty = higher tier chance)
		int tier = 1;
		if (difficultyValue > 2.0F && random.nextFloat() < 0.3F) tier = 2;
		if (difficultyValue > 3.0F && random.nextFloat() < 0.15F) tier = 3;

		makeBlighted(mob, tier, random);
	}

	/** Makes {@code mob} a blighted elite of the given {@code tier} (1-3). */
	public static void makeBlighted(Mob mob, int tier, RandomSource random) {
		tier = Mth.clamp(tier, 1, MAX_TIER);
		mob.setAttached(BLIGHT, tier);
		applyAttributes(mob);
		// Only a fresh blight heals: ENTITY_LOAD re-applies the modifiers, and healing there would mend elites every
		// time their chunk reloads.
		mob.setHealth(mob.getMaxHealth());
		setCustomName(mob, tier, random);
		LOADED.add(mob);
	}

	/** Applies attribute modifiers for the blight tier. */
	private static void applyAttributes(Mob mob) {
		Integer tier = mob.getAttached(BLIGHT);
		if (tier == null || tier <= 0) return;

		modify(mob, Attributes.MAX_HEALTH, HEALTH_ID, HEALTH_PER_TIER * tier);
		modify(mob, Attributes.ATTACK_DAMAGE, DAMAGE_ID, DAMAGE_PER_TIER * tier);
		modify(mob, Attributes.MOVEMENT_SPEED, SPEED_ID, SPEED_PER_TIER * tier);
		modify(mob, Attributes.ARMOR, ARMOR_ID, tier * 1.5); // +1.5 armor per tier
		modify(mob, Attributes.FOLLOW_RANGE, FOLLOW_RANGE_ID, tier * 8.0); // +8 blocks follow range per tier
	}

	private static void modify(Mob mob, Holder<Attribute> attribute, Identifier id, double amount) {
		AttributeInstance instance = mob.getAttribute(attribute);
		if (instance != null) {
			instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
	}

	/** Sets a custom name showing the blight tier. */
	private static void setCustomName(Mob mob, int tier, RandomSource random) {
		Component prefix = Component.translatable("blight.jaymospowers.prefix." + tier);
		Component suffix = Component.translatable("blight.jaymospowers.suffix." + (random.nextInt(SUFFIXES) + 1));
		mob.setCustomName(Component.translatable("blight.jaymospowers.name", prefix, mob.getType().getDescription(), suffix));
		mob.setCustomNameVisible(true);
		// Vanilla keeps named mobs alive forever; a blighted elite should still time out with its chunk.
		((MobPersistenceAccess) mob).jaymospowers$setPersistenceRequired(false);
	}

	/** Checks if an entity is blighted. */
	public static boolean isBlighted(Entity entity) {
		return entity.hasAttached(BLIGHT) && entity.getAttachedOrElse(BLIGHT, 0) > 0;
	}

	/** Gets the blight tier (0 if not blighted). */
	public static int getTier(Entity entity) {
		return entity.getAttachedOrElse(BLIGHT, 0);
	}

	/** Server tick: particle effects for blighted mobs. */
	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % EVERY != 0 || LOADED.isEmpty()) {
			return;
		}
		for (Mob mob : List.copyOf(LOADED)) {
			if (!mob.isAlive() || !(mob.level() instanceof ServerLevel)) {
				LOADED.remove(mob);
				continue;
			}
			Integer tier = mob.getAttached(BLIGHT);
			if (tier == null || tier <= 0) {
				LOADED.remove(mob);
				continue;
			}
			spawnParticles(mob, tier);
		}
	}

	/** Spawns ambient particles around a blighted mob. */
	private static void spawnParticles(Mob mob, int tier) {
		ServerLevel level = (ServerLevel) mob.level();
		RandomSource random = level.getRandom();
		double x = mob.getX();
		double y = mob.getY() + mob.getBbHeight() * 0.5;
		double z = mob.getZ();
		// Greenish plague particles using DustParticleOptions (packed RGB: 0x339933)
		int green = 0x339933;
		int count = tier * 2;
		for (int i = 0; i < count; i++) {
			double px = x + (random.nextDouble() - 0.5) * mob.getBbWidth();
			double py = y + random.nextDouble() * mob.getBbHeight();
			double pz = z + (random.nextDouble() - 0.5) * mob.getBbWidth();
			level.sendParticles(new DustParticleOptions(green, 1.0f), px, py, pz, 1, 0.1, 0.1, 0.1, 0.0);
		}
		// Occasional dark smoke for higher tiers
		if (tier >= 2 && random.nextFloat() < 0.3F) {
			level.sendParticles(ParticleTypes.SMOKE, x, y, z, 1, 0.2, 0.2, 0.2, 0.05);
		}
	}
}