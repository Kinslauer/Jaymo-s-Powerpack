package com.jaymospowers.baubles.artifacts;

import com.jaymospowers.core.fx.MagicFx;
import static com.jaymospowers.baubles.artifacts.ArtifactBaubles.*;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.baubles.Baubles;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.consume_effects.TeleportRandomlyConsumeEffect;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import java.util.Optional;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * (Artifacts) What the Artifacts baubles do, rewritten for our slots. Attribute bonuses are on the items themselves
 * ({@link ArtifactBaubles}); everything here runs on the server, apart from the moves the client makes for the player
 * (double jump, floating, sinking, walking on water and lava), which are in ArtifactBaublesClient and the mixins.
 * Tuning numbers are the constants below, Artifacts' own defaults unless noted.
 */
public final class ArtifactEffects {
	/** How often (ticks) the worn effects are refreshed. */
	static final int EVERY = 10;
	/** Reed breather: water breathing topped up while your head is above water, so you get this long under. */
	static final int BREATHER_BREATH = 30 * 20;
	/** Owl mask and the scarf: effects are kept between these lengths so they never flicker. */
	static final int KEEP_MIN = 300, KEEP = 400;
	/** Antidote vessel: harmful effects last at most this long. */
	static final int ANTIDOTE_MAX = 5 * 20;
	/** Lodestone: how far it pulls dropped items, and how hard. */
	static final double ATTRACT_RANGE = 6.0, ATTRACT_PULL = 0.12;
	/** Cross necklace: extra invulnerable ticks after a hit, and its cooldown. */
	static final int CROSS_EXTRA = 20, CROSS_COOLDOWN = 2 * 20;
	/** Panic necklace: speed after a hit. */
	static final int PANIC_SPEED = 8 * 20, PANIC_COOLDOWN = 2 * 20;
	/** Obsidian skull: fire resistance when fire first hurts you, then a long rest. */
	static final int SKULL_FIRE_RES = 30 * 20, SKULL_COOLDOWN = 60 * 20;
	/** Pendants: chance to strike back when hit, and cooldown between strikes. */
	static final float SHOCK_CHANCE = 0.25F, FLAME_CHANCE = 0.4F, THORN_CHANCE = 0.5F;
	static final int PENDANT_COOLDOWN = 2 * 20, FLAME_SECONDS = 10, THORN_MIN = 2, THORN_MAX = 6;
	/** Fire gauntlet, withered bracelet, vampiric glove: what a bare melee hit adds. */
	static final float GAUNTLET_SECONDS = 8.0F, WITHER_CHANCE = 0.3F, VAMPIRE_SHARE = 0.2F, VAMPIRE_MAX = 6.0F;
	static final int WITHER_TICKS = 8 * 20, WITHER_LEVEL = 1;
	/** Chorus totem: health after it saves you, how far it throws you. Used up, like a totem. */
	static final float CHORUS_HEALTH = 9.0F, CHORUS_RANGE = 16.0F;
	/** Courier's boots (while sprinting), snowshoes (on snow), rider's hat (your mount): speed bonuses. */
	static final double RUNNING_SPEED = 0.4, RUNNING_STEP = 0.5, SNOW_SPEED = 0.3, MOUNT_SPEED = 0.4;
	/** Rooted boots: on grass or dirt, a hunger point back this often. */
	static final int ROOTED_EVERY = 10 * 20;
	/** Kitty slippers: creepers and phantoms this near lose interest. */
	static final double KITTY_RANGE = 16.0;
	/** The alewife's and jester's caps: how much faster you drink and eat. */
	public static final double HAT_DRINK = 2.5, HAT_EAT = 1.5;
	/** Golden hook: experience from kills, times this. */
	public static final double HOOK_XP = 1.5;
	/** Villager hat: reputation added when villagers price their trades. */
	public static final int HAT_REPUTATION = 75;
	/** Pearl pouch: hunger points a pearl costs instead of itself (and you need more than this to throw one free). */
	public static final int WARP_HUNGER = 2;
	/** Owl mask: how bright their sight is (a potion's is 1). */
	public static final float OWL_MASK_STRENGTH = 0.15F;
	/** Glutton's ring: seconds of Haste II per hunger point eaten. */
	static final int ONION_SECONDS_PER_POINT = 6;
	/** Jester's cushion: the chance of a noise when you sneak. */
	static final float FART_CHANCE = 0.12F;

	static final Identifier RUNNING_SPEED_ID = JaymosPowers.id("artifact_running_speed"), RUNNING_STEP_ID = JaymosPowers.id("artifact_running_step"),
		SNOW_SPEED_ID = JaymosPowers.id("artifact_snow_speed"), MOUNT_SPEED_ID = JaymosPowers.id("artifact_mount_speed");

	/** Smelter's bracer: what counts as an ore block, and what of its drops get smelted. */
	static final TagKey<Block> ORES = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("c", "ores"));
	static final TagKey<Item> RAW_MATERIALS = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", "raw_materials"));

	/** Who sat on which mount last tick with a rider's hat on, to take the bonus off again. */
	private static final Map<UUID, LivingEntity> MOUNTS = new HashMap<>();
	/** Who was sneaking last tick (the jester's cushion). */
	private static final Map<UUID, Boolean> SNEAKING = new HashMap<>();

	private ArtifactEffects() {
	}

	static void init() {
		ArtifactNet.init();
		ServerTickEvents.END_SERVER_TICK.register(ArtifactEffects::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			UUID id = handler.getPlayer().getUUID();
			LivingEntity mount = MOUNTS.remove(id);
			if (mount != null) {
				toggle(mount, Attributes.MOVEMENT_SPEED, MOUNT_SPEED_ID, 0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, false);
			}
			SNEAKING.remove(id);
		});
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(ArtifactEffects::allowDamage);
		ServerLivingEntityEvents.AFTER_DAMAGE.register(ArtifactEffects::afterDamage);
		ServerLivingEntityEvents.ALLOW_DEATH.register(ArtifactEffects::allowDeath);
		LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> {
			BlockState state = context.getOptional(LootContextParams.BLOCK_STATE);
			Entity breaker = context.getOptional(LootContextParams.THIS_ENTITY);
			if (state != null && state.is(ORES) && wears(breaker, SMELTERS_BRACER)) {
				drops.replaceAll(stack -> smelt(context.getLevel(), stack, context.getOptional(LootContextParams.ORIGIN)));
			}
		});
	}

	/** Smelter's bracer: a raw ore mined comes out smelted, with the furnace's experience. */
	private static ItemStack smelt(ServerLevel level, ItemStack stack, Vec3 at) {
		if (!stack.is(RAW_MATERIALS)) {
			return stack;
		}
		SingleRecipeInput input = new SingleRecipeInput(stack);
		Optional<RecipeHolder<SmeltingRecipe>> recipe = level.recipeAccess().getRecipeFor(RecipeType.SMELTING, input, level);
		if (recipe.isEmpty()) {
			return stack;
		}
		ItemStack result = recipe.get().value().assemble(input);
		if (result.isEmpty()) {
			return stack;
		}
		float xp = recipe.get().value().experience() * stack.getCount();
		int whole = Mth.floor(xp) + (level.getRandom().nextFloat() < Mth.frac(xp) ? 1 : 0);
		if (whole > 0 && at != null) {
			ExperienceOrb.award(level, at, whole);
		}
		return result.copyWithCount(result.getCount() * stack.getCount());
	}

	/** After eating (both sides; acts on the server): the glutton's ring's haste, the rooted boots' growth. */
	public static void ate(Level level, LivingEntity user, FoodProperties food) {
		if (level.isClientSide() || !(user instanceof Player player) || food.nutrition() <= 0) {
			return;
		}
		if (wears(player, GLUTTONS_RING)) {
			player.addEffect(new MobEffectInstance(MobEffects.HASTE, food.nutrition() * ONION_SECONDS_PER_POINT * 20, 1, false, false, true));
		}
		if (wears(player, ROOTED_BOOTS) && !food.canAlwaysEat() && player.onGround() && rooted(player)) {
			BoneMealItem.growCrop(new ItemStack(Items.BONE_MEAL), level, player.getOnPos());
		}
	}

	/** Whether {@code player} wears {@code item} where it belongs. */
	public static boolean wears(Player player, Item item) {
		return Baubles.wears(player, item);
	}

	/** Whether {@code entity} is a player wearing {@code item}. */
	public static boolean wears(Entity entity, Item item) {
		return entity instanceof Player player && Baubles.wears(player, item);
	}

	private static void tick(MinecraftServer server) {
		int now = server.getTickCount();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.isSpectator() || !player.isAlive()) {
				continue;
			}
			tickMoves(player);
			if (wears(player, LODESTONE_CHARM) && now % 2 == 0) {
				attract(player);
			}
			if (wears(player, JESTERS_CUSHION)) {
				boolean sneaking = player.isShiftKeyDown();
				if (sneaking && !Boolean.TRUE.equals(SNEAKING.get(player.getUUID())) && player.getRandom().nextFloat() < FART_CHANCE) {
					player.level().playSound(null, player.getX(), player.getY(), player.getZ(), FART, SoundSource.PLAYERS, 1.0F,
						0.9F + player.getRandom().nextFloat() * 0.2F);
				}
				SNEAKING.put(player.getUUID(), sneaking);
			}
			if ((now + player.getId()) % EVERY == 0) {
				tickEffects(player, now);
			}
		}
	}

	/** Every tick: the speed bonuses that follow what you're doing. */
	private static void tickMoves(ServerPlayer player) {
		boolean running = player.isSprinting() && wears(player, COURIERS_BOOTS);
		toggle(player, Attributes.MOVEMENT_SPEED, RUNNING_SPEED_ID, RUNNING_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, running);
		toggle(player, Attributes.STEP_HEIGHT, RUNNING_STEP_ID, RUNNING_STEP, AttributeModifier.Operation.ADD_VALUE, running);
		boolean snowy = player.onGround() && wears(player, SNOWSHOES) && onSnow(player);
		toggle(player, Attributes.MOVEMENT_SPEED, SNOW_SPEED_ID, SNOW_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, snowy);
		LivingEntity mount = player.getVehicle() instanceof LivingEntity living && wears(player, RIDERS_HAT) ? living : null;
		LivingEntity before = mount != null ? MOUNTS.put(player.getUUID(), mount) : MOUNTS.remove(player.getUUID());
		if (before != null && before != mount) {
			toggle(before, Attributes.MOVEMENT_SPEED, MOUNT_SPEED_ID, 0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, false);
		}
		if (mount != null) {
			toggle(mount, Attributes.MOVEMENT_SPEED, MOUNT_SPEED_ID, MOUNT_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, true);
		}
	}

	private static boolean onSnow(Player player) {
		BlockPos feet = player.blockPosition();
		return player.level().getBlockState(feet).is(BlockTags.SNOW) || player.getBlockStateOn().is(BlockTags.SNOW);
	}

	/** Every half second: effects kept topped up, cures, hunger, scaring cats' foes. */
	private static void tickEffects(ServerPlayer player, int now) {
		if (wears(player, REED_BREATHER) && !player.isEyeInFluid(FluidTags.WATER)) {
			keep(player, MobEffects.WATER_BREATHING, BREATHER_BREATH - EVERY, BREATHER_BREATH);
		}
		if (wears(player, OWL_MASK)) {
			keep(player, MobEffects.NIGHT_VISION, KEEP_MIN, KEEP);
		}
		// Artifacts toggles the scarf with a key; here it hides you while you sneak
		if (wears(player, SCARF_OF_INVISIBILITY) && player.isShiftKeyDown()) {
			keep(player, MobEffects.INVISIBILITY, EVERY * 2, EVERY * 3);
		}
		if (wears(player, ANTIDOTE_VESSEL)) {
			for (MobEffectInstance effect : java.util.List.copyOf(player.getActiveEffects())) {
				if (!effect.getEffect().value().isBeneficial() && !effect.isInfiniteDuration() && effect.getDuration() > ANTIDOTE_MAX) {
					player.removeEffect(effect.getEffect());
					player.addEffect(new MobEffectInstance(effect.getEffect(), ANTIDOTE_MAX, effect.getAmplifier(), effect.isAmbient(), effect.isVisible()));
				}
			}
		}
		if (wears(player, ROOTED_BOOTS) && now % ROOTED_EVERY < EVERY && player.onGround() && rooted(player)
			&& player.getFoodData().needsFood()) {
			player.getFoodData().eat(1, 0.5F);
		}
		if (wears(player, KITTY_SLIPPERS)) {
			AABB near = player.getBoundingBox().inflate(KITTY_RANGE);
			for (Mob mob : player.level().getEntitiesOfClass(Mob.class, near, m -> (m instanceof Creeper || m instanceof Phantom) && m.getTarget() == player)) {
				mob.setTarget(null);
				if (mob instanceof Creeper creeper) {
					creeper.setSwellDir(-1);
					Vec3 away = creeper.position().subtract(player.position()).normalize().scale(6.0);
					creeper.getNavigation().moveTo(creeper.getX() + away.x, creeper.getY(), creeper.getZ() + away.z, 1.2);
				}
			}
		}
	}

	/** Rooted boots: standing on grass, dirt, moss or the like. */
	static boolean rooted(LivingEntity entity) {
		return entity.getBlockStateOn().is(BlockTags.DIRT);
	}

	/** Keeps a hidden effect going: tops it back up to {@code length} once it drops under {@code min}. */
	private static void keep(LivingEntity entity, Holder<MobEffect> effect, int min, int length) {
		MobEffectInstance now = entity.getEffect(effect);
		if (now == null || (!now.isInfiniteDuration() && now.getDuration() < min && now.getAmplifier() == 0)) {
			entity.addEffect(new MobEffectInstance(effect, length, 0, true, false, true));
		}
	}

	/** Puts a transient modifier on or takes it off, only when that changes something. */
	static void toggle(LivingEntity entity, Holder<Attribute> attribute, Identifier id, double amount, AttributeModifier.Operation operation, boolean on) {
		AttributeInstance instance = entity.getAttribute(attribute);
		if (instance == null) {
			return;
		}
		if (on && !instance.hasModifier(id)) {
			instance.addTransientModifier(new AttributeModifier(id, amount, operation));
		} else if (!on && instance.hasModifier(id)) {
			instance.removeModifier(id);
		}
	}

	/** Lodestone: dropped items nearby drift to you (not ones still too fresh to pick up). */
	private static void attract(ServerPlayer player) {
		Vec3 to = player.position().add(0.0, player.getBbHeight() * 0.5, 0.0);
		for (ItemEntity item : player.level().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(ATTRACT_RANGE),
			e -> !e.hasPickUpDelay() && e.isAlive())) {
			Vec3 pull = to.subtract(item.position());
			double distance = pull.length();
			if (distance > 0.3) {
				item.setDeltaMovement(item.getDeltaMovement().scale(0.6).add(pull.scale(ATTRACT_PULL / Math.max(1.0, distance * 0.5))));
				item.needsSync = true;
			}
		}
	}

	private static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (!(entity instanceof ServerPlayer player)) {
			return true;
		}
		if (source.is(DamageTypes.LIGHTNING_BOLT) && wears(player, SHOCK_PENDANT)) {
			return false;
		}
		if (source.is(DamageTypes.ENDER_PEARL) && wears(player, ENDER_POUCH)) {
			return false;
		}
		if (source.is(DamageTypeTags.IS_FALL) && player.isInWater() && wears(player, CHARM_OF_SINKING)) {
			return false;
		}
		// strider shoes: the magma block's hot floor
		return !(source.is(DamageTypes.HOT_FLOOR) && wears(player, STRIDER_SHOES));
	}

	private static void afterDamage(LivingEntity entity, DamageSource source, float base, float taken, boolean blocked) {
		if (taken <= 0.0F || !(entity.level() instanceof ServerLevel level)) {
			return;
		}
		if (entity instanceof ServerPlayer player) {
			whenHurt(player, level, source);
		}
		if (source.getEntity() instanceof ServerPlayer attacker && source.getDirectEntity() == attacker && attacker != entity) {
			whenHitting(attacker, entity, taken);
		}
	}

	private static void whenHurt(ServerPlayer player, ServerLevel level, DamageSource source) {
		if (ready(player, CROSS_NECKLACE, CROSS_COOLDOWN)) {
			player.setInvulnerableTime(player.getInvulnerableTime() + CROSS_EXTRA);
		}
		if (ready(player, PANIC_NECKLACE, PANIC_COOLDOWN)) {
			player.addEffect(new MobEffectInstance(MobEffects.SPEED, PANIC_SPEED, 0, false, false, true));
		}
		if (source.is(DamageTypeTags.IS_FIRE) && ready(player, OBSIDIAN_SKULL, SKULL_COOLDOWN)) {
			player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, SKULL_FIRE_RES, 0, false, false, true));
		}
		if (!(source.getEntity() instanceof LivingEntity attacker) || attacker == player || !attacker.isAlive()) {
			return;
		}
		if (wears(player, SHOCK_PENDANT) && player.getRandom().nextFloat() < SHOCK_CHANCE && ready(player, SHOCK_PENDANT, PENDANT_COOLDOWN)) {
			// by registry id: EntityType's own field doesn't resolve on our compile classpath
			Entity made = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
				.getValue(Identifier.fromNamespaceAndPath("minecraft", "lightning_bolt")).create(level, EntitySpawnReason.TRIGGERED);
			if (made instanceof LightningBolt bolt) {
				bolt.snapTo(attacker.getX(), attacker.getY(), attacker.getZ());
				bolt.setCause(player);
				level.addFreshEntity(bolt);
			}
		}
		if (wears(player, FLAME_PENDANT) && player.getRandom().nextFloat() < FLAME_CHANCE && ready(player, FLAME_PENDANT, PENDANT_COOLDOWN)) {
			attacker.igniteForSeconds(FLAME_SECONDS);
			player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, FLAME_SECONDS * 20, 0, false, false, true));
		}
		if (wears(player, THORN_PENDANT) && player.getRandom().nextFloat() < THORN_CHANCE && ready(player, THORN_PENDANT, PENDANT_COOLDOWN)) {
			attacker.hurtServer(level, player.damageSources().thorns(player), Mth.nextInt(player.getRandom(), THORN_MIN, THORN_MAX));
		}
	}

	/** A worn bauble that isn't resting: if worn and off cooldown, puts it on cooldown and says yes. */
	private static boolean ready(ServerPlayer player, Item item, int cooldown) {
		ItemStack stack = Baubles.wornStack(player, item);
		if (stack.isEmpty() || player.getCooldowns().isOnCooldown(stack)) {
			return false;
		}
		player.getCooldowns().addCooldown(stack, cooldown);
		return true;
	}

	/** Bare melee hits (anything in hand counts; gloves work over it): burn, wither, drink. */
	private static void whenHitting(ServerPlayer attacker, LivingEntity target, float dealt) {
		if (wears(attacker, FIRE_GAUNTLET) && !target.fireImmune()) {
			target.igniteForSeconds(GAUNTLET_SECONDS);
		}
		if (wears(attacker, WITHERED_BRACELET) && attacker.getRandom().nextFloat() < WITHER_CHANCE) {
			target.addEffect(new MobEffectInstance(MobEffects.WITHER, WITHER_TICKS, WITHER_LEVEL), attacker);
		}
		if (wears(attacker, VAMPIRIC_GLOVE)) {
			attacker.heal(Math.min(VAMPIRE_MAX, dealt * VAMPIRE_SHARE));
		}
	}

	/** Chorus totem: worn, it turns a death aside by throwing you somewhere near, and is used up. */
	private static boolean allowDeath(LivingEntity entity, DamageSource source, float amount) {
		if (!(entity instanceof ServerPlayer player) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return true;
		}
		ItemStack totem = Baubles.wornStack(player, CHORUS_TOTEM);
		if (totem.isEmpty()) {
			return true;
		}
		totem.shrink(1);
		Baubles.setWorn(player, Baubles.worn(player));
		player.setHealth(CHORUS_HEALTH);
		player.removeAllEffects();
		player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 10 * 20, 1));
		new TeleportRandomlyConsumeEffect(CHORUS_RANGE, true).apply(player.level(), new ItemStack(Items.CHORUS_FRUIT), player);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0F, 1.2F);
		if (player.level() instanceof ServerLevel level) {
			level.sendParticles(MagicFx.VOID, player.getX(), player.getY() + 1.0, player.getZ(), 40, 0.4, 0.8, 0.4, 0.3);
		}
		return false;
	}
}
