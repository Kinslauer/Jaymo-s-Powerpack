package com.jaymoskitchen.climate;

import com.jaymoskitchen.JaymosKitchen;
import com.jaymoskitchen.ModItems;
import com.jaymoskitchen.Stacked;
import com.jaymoskitchen.brewing.Brewing;
import com.jaymoskitchen.cooking.Dish;
import com.jaymoskitchen.kitchen.KitchenBlocks;
import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * (0.5.25) Thirst, our own take on Dehydration (GPL, 1.21.1 only, so nothing is taken from it). Everyone carries
 * {@link #MAX} points of water beside the hunger bar. It drains by the minute, faster sprinting or swimming and much
 * faster when you're hot ({@link BodyHeat}). Low on water you can't sprint ({@link #NO_SPRINT}), then you're parched
 * (weak), and at nothing it hurts you, as starving does.
 * <p>
 * River water is dirty: a bottle from a pond or a sip from the bank ({@link #trySip}: sneak and use an empty hand on
 * water) may turn your stomach ({@link #SICK_CHANCE}). Boiled over a lit campfire it's Clean Water, which also cools
 * you down; rain caught on the tongue (sneak, look up, use an empty hand) is clean too. A {@link WaterskinItem} carries
 * several drinks. Milk, Kitchen's brews, stews and juicy fruit all count a little; strong spirits take water away.
 * <p>
 * (0.5.48) <b>Fire drinkers</b> (Kingdoms' Emberborn, set by {@link #setFireDrinker}) keep a thermal core instead: water
 * only hisses to steam on their tongue and gives them nothing. Sneaking with an empty hand they drink from a fire or a
 * lit campfire ({@link #FIRE_DRINK}; a loose fire goes out, a campfire keeps burning) or gulp straight from lava
 * ({@link #LAVA_DRINK}), and soaking in lava fills them slowly ({@link #LAVA_SOAK_EVERY}). The Nether doesn't dry them.
 * <p>
 * (0.5.80) <b>Iron guts</b> (Kingdoms' Iron Stomach perk, set by {@link #setIronGut}; Andy: "the rotten meat eating skill
 * should let you drink water from wherever too"): dirty water never turns their stomach, whatever it came from (a sip
 * from a river, swamp or puddle, a pond bottle, a dirty waterskin).
 */
public final class Thirst {
	public static final int MAX = 20;
	/** Points of water lost a second at rest (one every 75 s: a full bar lasts most of a day). */
	static final float DRAIN = 1.0F / 75.0F;
	/** On top of that, as a share of {@link #DRAIN}: sprinting, swimming, being in the Nether. */
	static final float SPRINT_DRAIN = 1.5F, SWIM_DRAIN = 0.5F, NETHER_DRAIN = 0.5F;
	/** At or below this you can't sprint, and at or below this you're parched (weakness). */
	public static final int NO_SPRINT = 6, PARCHED = 3;
	/** Seconds between hurts at no water, and how much each takes. */
	static final int HURT_EVERY = 6;
	static final float HURT = 1.0F;
	/** Chance a drink of dirty water turns your stomach, by how much of it you drank. */
	static final float SICK_CHANCE = 0.35F, SIP_SICK_CHANCE = 0.15F;
	/** How long a turned stomach lasts (ticks): nausea and hunger. */
	static final int SICK_NAUSEA = 200, SICK_HUNGER = 400;
	/** A sip from the bank or the sky: water it gives, and ticks before another. */
	static final int SIP = 1, SIP_COOLDOWN = 10;
	/** Water from the drinks themselves. */
	static final int DIRTY_BOTTLE = 5, CLEAN_BOTTLE = 7, POTION = 3, MILK = 6, HONEY = 1, BREW = 4, SPIRITS = -2, LEMONADE = 6;
	/** Water from food: a stew or soup, a melon slice, a berry or apple. */
	static final int STEW = 3, MELON = 2, FRUIT = 1;
	/** Clean water and a fresh sip cool you this much (body heat points). */
	static final float CLEAN_COOL = 12.0F;
	/** (0.5.48) A fire drinker: water from a fire or campfire, from a gulp of lava; ticks before the next gulp. */
	static final int FIRE_DRINK = 4, LAVA_DRINK = 12, FIRE_COOLDOWN = 20, LAVA_COOLDOWN = 60;
	/** A gulp of lava warms the core: regeneration this long (ticks), at this amplifier. */
	static final int LAVA_REGEN_TICKS = 160, LAVA_REGEN_LEVEL = 0;
	/** Soaking in lava: a point of water every this many seconds. */
	static final int LAVA_SOAK_EVERY = 3;
	/** Water hisses away: ticks before the hiss sounds again. */
	static final int HISS_COOLDOWN = 20;

	/** Water you carry (absent = full). Synced to you for the HUD; filled back up when you die. */
	public static final AttachmentType<Integer> WATER = AttachmentRegistry.create(JaymosKitchen.id("thirst"),
		builder -> builder.persistent(Codec.INT).syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.targetOnly()));

	static final ResourceKey<DamageType> DEHYDRATION = ResourceKey.create(Registries.DAMAGE_TYPE, JaymosKitchen.id("dehydration"));

	private static final Map<UUID, Float> DRAINED = new HashMap<>();
	private static final Map<UUID, Long> LAST_SIP = new HashMap<>();
	private static final Map<UUID, Long> LAST_HISS = new HashMap<>();
	private static Predicate<Player> fireDrinker = player -> false;
	private static Predicate<Player> ironGut = player -> false;

	private Thirst() {
	}

	/** (0.5.48) For other mods (by reflection): who drinks fire and lava, not water (Kingdoms' Emberborn). */
	public static void setFireDrinker(Predicate<Player> drinker) {
		fireDrinker = drinker;
	}

	/** (0.5.80) For other mods (by reflection): whose stomach dirty water never turns (Kingdoms' Iron Stomach). */
	public static void setIronGut(Predicate<Player> gut) {
		ironGut = gut;
	}

	/** (0.5.80) Whether dirty water is as good as clean to {@code player}'s stomach. */
	public static boolean ironGut(Player player) {
		return ironGut.test(player);
	}

	/** (0.5.48) Whether {@code player} drinks fire and lava instead of water. Both sides. */
	public static boolean fireDrinker(Player player) {
		return fireDrinker.test(player);
	}

	public static int water(Player player) {
		return player.getAttachedOrElse(WATER, MAX);
	}

	static void set(ServerPlayer player, int water) {
		water = Mth.clamp(water, 0, MAX);
		if (water == MAX) {
			player.removeAttached(WATER);
		} else if (water != water(player)) {
			player.setAttached(WATER, water);
		}
	}

	static void forget(UUID id) {
		DRAINED.remove(id);
		LAST_SIP.remove(id);
		LAST_HISS.remove(id);
	}

	// ---- every second ----

	static void second(ServerPlayer player, long seconds) {
		UUID id = player.getUUID();
		ServerLevel level = player.level();
		if (level.getDifficulty() == Difficulty.PEACEFUL) {
			// as hunger does on peaceful: it comes back by itself
			if (seconds % 5L == 0L) {
				set(player, water(player) + 1);
			}
			return;
		}
		float rate = 1.0F;
		if (player.isSprinting()) {
			rate += SPRINT_DRAIN;
		}
		if (player.isSwimming()) {
			rate += SWIM_DRAIN;
		}
		boolean fire = fireDrinker(player);
		if (Stacked.in(player, Level.NETHER) && !fire) {
			rate += NETHER_DRAIN;
		}
		if (fire && player.isInLava() && seconds % LAVA_SOAK_EVERY == 0L) {
			// (0.5.48) the core soaks it up
			set(player, water(player) + 1);
		}
		rate *= BodyHeat.thirstFactor(player);
		float drained = DRAINED.getOrDefault(id, 0.0F) + DRAIN * rate;
		if (drained >= 1.0F) {
			set(player, water(player) - 1);
			drained -= 1.0F;
		}
		DRAINED.put(id, drained);

		int water = water(player);
		if (water <= PARCHED) {
			player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 50, 0, true, false, true));
		}
		if (water <= 0 && seconds % HURT_EVERY == 0L && !player.entityTags().contains(BodyHeat.DOWNED_TAG)) {
			// as starving: easy leaves you half your health, normal half a heart, hard kills
			Difficulty difficulty = level.getDifficulty();
			boolean hurts = difficulty == Difficulty.HARD
				|| difficulty == Difficulty.NORMAL && player.getHealth() > 1.0F
				|| difficulty == Difficulty.EASY && player.getHealth() > player.getMaxHealth() / 2.0F;
			if (hurts) {
				DamageSource source = new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DEHYDRATION));
				player.hurtServer(level, source, HURT);
			}
		}
	}

	/** Every tick: too thirsty to sprint (the client stops it too, so it doesn't rubber-band). */
	static void tick(ServerPlayer player) {
		if (player.isSprinting() && water(player) <= NO_SPRINT && !player.getAbilities().mayfly) {
			player.setSprinting(false);
		}
	}

	// ---- drinking ----

	/** Something eaten or drunk (the stack still whole): the water in it. */
	public static void consumed(ServerPlayer player, ItemStack stack) {
		if (player.getAbilities().instabuild) {
			return;
		}
		if (stack.is(Items.POTION)) {
			PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
			if (contents != null && contents.is(Potions.WATER)) {
				drink(player, DIRTY_BOTTLE, false, SICK_CHANCE);
			} else if (fireDrinker(player)) {
				// (a potion's magic still goes down; only water turns to steam)
				set(player, water(player) + POTION);
			} else {
				drink(player, POTION, true, 0.0F);
			}
			return;
		}
		if (stack.is(Climate.CLEAN_WATER) && fireDrinker(player)) {
			hiss(player);
			return;
		}
		if (stack.is(Climate.CLEAN_WATER)) {
			drink(player, CLEAN_BOTTLE, true, 0.0F);
			BodyHeat.cool(player, CLEAN_COOL);
			return;
		}
		int water = waterIn(stack);
		if (water != 0) {
			set(player, water(player) + water);
		}
	}

	/** Water in a food or drink that isn't water itself (0 for most). */
	static int waterIn(ItemStack stack) {
		if (stack.is(Items.MILK_BUCKET)) {
			return MILK;
		}
		if (stack.is(Items.HONEY_BOTTLE)) {
			return HONEY;
		}
		if (stack.is(Brewing.STRONG_SPIRITS)) {
			return SPIRITS;
		}
		if (stack.is(KitchenBlocks.FOODS.get("lemonade"))) {
			return LEMONADE;
		}
		if (Brewing.brewOf(stack) != null) {
			return BREW;
		}
		if (stack.is(Items.MELON_SLICE)) {
			return MELON;
		}
		if (stack.is(Items.SWEET_BERRIES) || stack.is(Items.GLOW_BERRIES) || stack.is(Items.APPLE) || stack.is(Items.CHORUS_FRUIT)) {
			return FRUIT;
		}
		if (stack.is(Items.MUSHROOM_STEW) || stack.is(Items.BEETROOT_SOUP) || stack.is(Items.RABBIT_STEW) || stack.is(Items.SUSPICIOUS_STEW)) {
			return STEW;
		}
		Dish dish = ModItems.dishOf(stack.getItem());
		return dish != null && dish.bowlFood() ? STEW : 0;
	}

	/**
	 * (0.5.58) Told of every drink of water a player takes (a sip, a bottle, a cup): Jaymo's Powers' water in the lungs may
	 * send it down the wrong pipe. Filled by reflection.
	 */
	public static final java.util.List<java.util.function.Consumer<ServerPlayer>> DRANK = new java.util.concurrent.CopyOnWriteArrayList<>();

	/** A drink of water: {@code clean} or with {@code sickChance} of a turned stomach. */
	public static void drink(ServerPlayer player, int water, boolean clean, float sickChance) {
		if (fireDrinker(player)) {
			hiss(player);
			return;
		}
		set(player, water(player) + water);
		for (java.util.function.Consumer<ServerPlayer> listener : DRANK) {
			listener.accept(player);
		}
		if (!clean && player.getRandom().nextFloat() < sickChance && !ironGut(player)) {
			player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, SICK_NAUSEA, 0));
			player.addEffect(new MobEffectInstance(MobEffects.HUNGER, SICK_HUNGER, 0));
			player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.thirst.dirty").withStyle(ChatFormatting.DARK_GREEN));
		}
	}

	/**
	 * Sneaking with an empty hand: a sip of the rain if you're out in it looking up, else of the water you're looking at
	 * (dirty). Returns whether you drank. The server works it out again whoever asks (the client's packet or a block use).
	 */
	public static boolean trySip(ServerPlayer player) {
		if (!player.isShiftKeyDown() || !player.getMainHandItem().isEmpty() || player.isSpectator()) {
			return false;
		}
		if (fireDrinker(player)) {
			return tryFireSip(player);
		}
		long now = player.level().getGameTime();
		Long last = LAST_SIP.get(player.getUUID());
		if (last != null && now - last < SIP_COOLDOWN) {
			// still swallowing the last one: counts as drinking, so nothing else happens either
			return lookingAtWater(player) || inRainLookingUp(player);
		}
		boolean rain = inRainLookingUp(player);
		if (!rain && !lookingAtWater(player)) {
			return false;
		}
		LAST_SIP.put(player.getUUID(), now);
		if (water(player) >= MAX) {
			return true;
		}
		drink(player, SIP, rain, SIP_SICK_CHANCE);
		if (rain) {
			BodyHeat.cool(player, CLEAN_COOL / 4.0F);
		}
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_DRINK.value(), SoundSource.PLAYERS,
			0.5F, 0.9F + player.getRandom().nextFloat() * 0.2F);
		return true;
	}

	// ---- (0.5.48) fire drinkers ----

	/** Water on a fire drinker's tongue: it hisses to steam and gives nothing. */
	static void hiss(ServerPlayer player) {
		long now = player.level().getGameTime();
		Long last = LAST_HISS.get(player.getUUID());
		if (last != null && now - last < HISS_COOLDOWN) {
			return;
		}
		LAST_HISS.put(player.getUUID(), now);
		ServerLevel level = player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.6F, 1.6F);
		level.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD, player.getX(), player.getEyeY() - 0.2, player.getZ(), 6, 0.15, 0.1, 0.15, 0.02);
		player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.thirst.steam").withStyle(ChatFormatting.GRAY));
	}

	/** A fire drinker sneaking with an empty hand: a gulp of the lava, fire or campfire they look at; water only hisses. */
	static boolean tryFireSip(ServerPlayer player) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		BlockPos fire = fireLookedAt(player);
		boolean lava = fire == null && lookingAtLava(player);
		if (fire == null && !lava) {
			if (lookingAtWater(player) || inRainLookingUp(player)) {
				hiss(player);
				return true;
			}
			return false;
		}
		Long last = LAST_SIP.get(player.getUUID());
		if (last != null && now - last < (lava ? LAVA_COOLDOWN : FIRE_COOLDOWN)) {
			return true;
		}
		if (water(player) >= MAX) {
			return true;
		}
		LAST_SIP.put(player.getUUID(), now);
		set(player, water(player) + (lava ? LAVA_DRINK : FIRE_DRINK));
		if (lava) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_DRINK.value(), SoundSource.PLAYERS, 1.0F, 0.6F);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.LAVA_POP, SoundSource.PLAYERS, 0.8F, 0.8F);
			level.sendParticles(net.minecraft.core.particles.ParticleTypes.LAVA, player.getX(), player.getEyeY() - 0.2, player.getZ(), 6, 0.2, 0.1, 0.2, 0.0);
			player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, LAVA_REGEN_TICKS, LAVA_REGEN_LEVEL, false, false, true));
		} else {
			BlockState state = level.getBlockState(fire);
			if (state.getBlock() instanceof BaseFireBlock) {
				// a loose fire is drunk down to nothing; a campfire is a hearth and keeps burning
				level.removeBlock(fire, false);
			}
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.6F, 1.3F);
			level.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME, player.getX(), player.getEyeY() - 0.2, player.getZ(), 8, 0.15, 0.1, 0.15, 0.02);
		}
		return true;
	}

	/** The fire or lit campfire {@code player} looks at in reach, or null. Both sides. */
	static @org.jspecify.annotations.Nullable BlockPos fireLookedAt(Player player) {
		HitResult hit = player.pick(player.blockInteractionRange(), 0.0F, false);
		if (!(hit instanceof BlockHitResult block) || hit.getType() != HitResult.Type.BLOCK) {
			return null;
		}
		BlockPos pos = block.getBlockPos();
		for (BlockPos at : new BlockPos[] {pos, pos.relative(block.getDirection())}) {
			BlockState state = player.level().getBlockState(at);
			if (state.getBlock() instanceof BaseFireBlock || CampfireBlock.isLitCampfire(state)) {
				return at.immutable();
			}
		}
		return null;
	}

	static boolean lookingAtLava(Player player) {
		HitResult hit = player.pick(player.blockInteractionRange(), 0.0F, true);
		return hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK
			&& player.level().getFluidState(block.getBlockPos()).is(FluidTags.LAVA);
	}

	static boolean lookingAtWater(Player player) {
		HitResult hit = player.pick(player.blockInteractionRange(), 0.0F, true);
		return hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK
			&& player.level().getFluidState(block.getBlockPos()).is(FluidTags.WATER);
	}

	static boolean inRainLookingUp(Player player) {
		BlockPos head = BlockPos.containing(player.getEyePosition());
		return player.getXRot() < -60.0F && player.level().precipitationAt(head) == Biome.Precipitation.RAIN;
	}
}
