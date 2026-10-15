package com.jaymoskitchen.stomach;

import com.jaymoskitchen.JaymosKitchen;
import com.jaymoskitchen.cooking.Quality;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.ToIntFunction;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.level.gamerules.GameRules;
import org.jspecify.annotations.Nullable;

/**
 * Hearty meals, our own take on Spice of Life: Valheim Edition (Valheim's food slots). You start with
 * {@link #STARTING_HEALTH} health; every meal you eat sits in one of {@link #BASE_SLOTS} stomach slots (plus one for a
 * drink) for a while, adding its own health and regeneration until it wears off. The same food can't fill two slots,
 * and a full slot only makes room once it's nearly gone ({@link #EAT_AGAIN}), so a varied diet is a sturdier body.
 * <p>
 * The hunger bar stays: it's what sprinting and every power burn. It no longer heals you, though: health comes back
 * only from what's in your stomach, and only once you haven't been hurt for a while ({@link #REGEN_DELAY}). Kitchen
 * dishes count for more than plain food ({@link #DISH_BONUS}), Perfect ones more still. Other mods add slots through
 * {@link #EXTRA_SLOTS} (Jaymo's Powers: a Z Fighter eats like three men).
 */
public final class Stomach {
	/** Health (half-hearts) with an empty stomach. */
	public static final float STARTING_HEALTH = 6.0F;
	/** Food slots everyone has (the drink slot is extra). */
	public static final int BASE_SLOTS = 3;
	/** Seconds a meal lasts per point of nutrition (a steak: 8 points, 24 minutes). */
	static final int SECONDS_PER_NUTRITION = 180;
	static final int MIN_TICKS = 5 * 60 * 20;
	static final int MAX_TICKS = 40 * 60 * 20;
	/** A slot can be eaten into again once this much of it is left. */
	public static final float EAT_AGAIN = 0.2F;
	/** Ticks without being hurt before your meals start healing you. */
	static final int REGEN_DELAY = 200;
	/** Regeneration with an empty stomach, and what each point of nutrition adds (per meal, 0.25 to 2). */
	static final float BASE_REGEN = 0.25F;
	static final float REGEN_PER_NUTRITION = 0.1F;
	/** At a regeneration of 1, one health point back every this many ticks. */
	static final int REGEN_INTERVAL = 80;
	/** A drink in its slot makes every food count this much more. */
	static final float DRINK_BONUS = 0.1F;
	/** Most health meals can add (an extra bar of hearts), and how much more each extra slot allows. */
	static final float MAX_FOOD_HEALTH = 34.0F;
	static final float MAX_PER_EXTRA_SLOT = 8.0F;
	/** Walking speed with every food slot full. */
	static final double FULL_SPEED = 0.1;
	/** A Kitchen dish against plain food (health, regeneration and time), and a Perfect one on top of that. */
	static final float DISH_BONUS = 1.25F;
	static final float PERFECT_BONUS = 1.2F;
	/** Golden apples: always a big meal. */
	static final int GOLDEN_NUTRITION = 10;
	static final float GOLDEN_REGEN = 1.5F;
	/** Drinks without food in them (potions, milk) count as this much nutrition. */
	static final int POTION_NUTRITION = 4;
	static final int MILK_NUTRITION = 6;

	private static final Identifier HEALTH_ID = JaymosKitchen.id("stomach_health");
	private static final Identifier SPEED_ID = JaymosKitchen.id("stomach_speed");

	/** Extra food slots another mod grants a player (summed). */
	public static final List<ToIntFunction<Player>> EXTRA_SLOTS = new CopyOnWriteArrayList<>();

	/** One meal in the stomach: what it was, what it gives, and how long it has left. */
	public record Meal(Item item, float health, float regen, int left, int total, boolean drink) {
		static final Codec<Meal> CODEC = RecordCodecBuilder.create(i -> i.group(
			BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(Meal::item),
			Codec.FLOAT.fieldOf("health").forGetter(Meal::health),
			Codec.FLOAT.fieldOf("regen").forGetter(Meal::regen),
			Codec.INT.fieldOf("left").forGetter(Meal::left),
			Codec.INT.fieldOf("total").forGetter(Meal::total),
			Codec.BOOL.fieldOf("drink").forGetter(Meal::drink)
		).apply(i, Meal::new));
		static final StreamCodec<RegistryFriendlyByteBuf, Meal> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.registry(Registries.ITEM), Meal::item,
			ByteBufCodecs.FLOAT, Meal::health,
			ByteBufCodecs.FLOAT, Meal::regen,
			ByteBufCodecs.VAR_INT, Meal::left,
			ByteBufCodecs.VAR_INT, Meal::total,
			ByteBufCodecs.BOOL, Meal::drink,
			Meal::new);

		/** How much is left (1 = just eaten). */
		public float fraction() {
			return total <= 0 ? 0.0F : Mth.clamp(left / (float) total, 0.0F, 1.0F);
		}

		boolean nearlyGone() {
			return fraction() <= EAT_AGAIN;
		}

		Meal withLeft(int ticks) {
			return new Meal(item, health, regen, ticks, total, drink);
		}
	}

	/** What's in your stomach (synced to you for the HUD; emptied when you die). */
	public static final AttachmentType<List<Meal>> MEALS = AttachmentRegistry.create(JaymosKitchen.id("stomach"),
		builder -> builder.persistent(Meal.CODEC.listOf()).syncWith(Meal.STREAM_CODEC.apply(ByteBufCodecs.list()), AttachmentSyncPredicate.targetOnly()));

	private static final Map<UUID, Long> LAST_HURT = new HashMap<>();
	private static final Map<UUID, Float> REGEN_PROGRESS = new HashMap<>();

	private Stomach() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Stomach::tick);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (entity instanceof ServerPlayer player && damageTaken > 0.0F) {
				LAST_HURT.put(player.getUUID(), player.level().getGameTime());
				REGEN_PROGRESS.remove(player.getUUID());
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			LAST_HURT.remove(handler.player.getUUID());
			REGEN_PROGRESS.remove(handler.player.getUUID());
		});
	}

	public static List<Meal> meals(Player player) {
		return player.getAttachedOrElse(MEALS, List.of());
	}

	public static int slots(Player player) {
		int slots = BASE_SLOTS;
		for (ToIntFunction<Player> extra : EXTRA_SLOTS) {
			slots += extra.applyAsInt(player);
		}
		return Math.max(1, slots);
	}

	// ---- what a meal is worth ----

	/** The meal this stack would make, or null if it isn't food or drink. */
	public static @Nullable Meal mealOf(ItemStack stack, boolean drink) {
		FoodProperties food = stack.get(DataComponents.FOOD);
		int nutrition;
		float regenMod = 1.0F;
		if (stack.is(Items.GOLDEN_APPLE) || stack.is(Items.ENCHANTED_GOLDEN_APPLE)) {
			nutrition = GOLDEN_NUTRITION;
			regenMod = GOLDEN_REGEN;
		} else if (food != null) {
			nutrition = food.nutrition();
		} else if (stack.is(Items.MILK_BUCKET)) {
			nutrition = MILK_NUTRITION;
		} else if (drink) {
			nutrition = POTION_NUTRITION;
		} else {
			return null;
		}
		float bonus = 1.0F;
		if (isDish(stack)) {
			bonus = DISH_BONUS;
			if (Quality.of(stack) == Quality.PERFECT) {
				bonus *= PERFECT_BONUS;
			}
		}
		float health = Math.max(nutrition, 2) * bonus;
		float regen = Mth.clamp(nutrition * REGEN_PER_NUTRITION * regenMod, 0.25F, 2.0F) * bonus;
		int ticks = Mth.clamp(Math.round(SECONDS_PER_NUTRITION * 20 * nutrition * bonus), MIN_TICKS, MAX_TICKS);
		return new Meal(stack.getItem(), health, regen, ticks, ticks, drink);
	}

	/** A Kitchen dish (or anything from Cooking for Blockheads): worth more than plain food. */
	static boolean isDish(ItemStack stack) {
		String ns = BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace();
		return Quality.of(stack) != null || ns.equals(JaymosKitchen.MOD_ID) || ns.equals("cookingforblockheads");
	}

	public static boolean isDrink(ItemStack stack) {
		Consumable consumable = stack.get(DataComponents.CONSUMABLE);
		return consumable != null && consumable.animation() == ItemUseAnimation.DRINK;
	}

	// ---- eating ----

	/** Where this meal would go: the slot index to replace, size() to add one, or -1 if there's no room. */
	private static int placeFor(Player player, List<Meal> meals, Item item, boolean drink) {
		for (int i = 0; i < meals.size(); i++) {
			Meal meal = meals.get(i);
			if (meal.item() == item) {
				return meal.nearlyGone() ? i : -1;
			}
		}
		if (drink) {
			for (int i = 0; i < meals.size(); i++) {
				if (meals.get(i).drink()) {
					return meals.get(i).nearlyGone() ? i : -1;
				}
			}
			return meals.size();
		}
		long foods = meals.stream().filter(m -> !m.drink()).count();
		if (foods < slots(player)) {
			return meals.size();
		}
		int best = -1;
		for (int i = 0; i < meals.size(); i++) {
			Meal meal = meals.get(i);
			if (!meal.drink() && meal.nearlyGone() && (best < 0 || meal.left() < meals.get(best).left())) {
				best = i;
			}
		}
		return best;
	}

	/** Whether eating this would put it in your stomach: you can eat then even with a full hunger bar. */
	public static boolean wouldTake(Player player, ItemStack stack) {
		if (player.getAbilities().instabuild) {
			return false;
		}
		boolean drink = isDrink(stack);
		return mealOf(stack, drink) != null && placeFor(player, meals(player), stack.getItem(), drink) >= 0;
	}

	/** Something eaten or drunk (the stack is still whole): into a slot if there's room. */
	public static void eat(ServerPlayer player, ItemStack stack, boolean drink) {
		Meal meal = mealOf(stack, drink);
		if (meal == null) {
			return;
		}
		List<Meal> meals = new ArrayList<>(meals(player));
		int at = placeFor(player, meals, stack.getItem(), drink);
		if (at < 0) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.stomach.full").withStyle(ChatFormatting.GRAY));
			return;
		}
		if (at < meals.size()) {
			meals.set(at, meal);
		} else {
			meals.add(meal);
		}
		player.setAttached(MEALS, List.copyOf(meals));
		apply(player, meals);
	}

	// ---- every tick ----

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!player.isAlive()) {
				continue;
			}
			List<Meal> meals = meals(player);
			long now = player.level().getGameTime();
			if (now % 20L == 0L) {
				if (!meals.isEmpty()) {
					List<Meal> next = new ArrayList<>(meals.size());
					for (Meal meal : meals) {
						if (meal.left() > 20) {
							next.add(meal.withLeft(meal.left() - 20));
						}
					}
					meals = List.copyOf(next);
					if (meals.isEmpty()) {
						player.removeAttached(MEALS);
					} else {
						player.setAttached(MEALS, meals);
					}
				}
				apply(player, meals);
			}
			regen(player, meals, now);
		}
	}

	/** Max health and the full-stomach stride from what's eaten. */
	private static void apply(ServerPlayer player, List<Meal> meals) {
		AttributeInstance health = player.getAttribute(Attributes.MAX_HEALTH);
		if (health != null) {
			double bonus = STARTING_HEALTH - 20.0F + Math.min(foodHealth(meals), MAX_FOOD_HEALTH + MAX_PER_EXTRA_SLOT * (slots(player) - BASE_SLOTS));
			AttributeModifier current = health.getModifier(HEALTH_ID);
			if (current == null || current.amount() != bonus) {
				health.addOrUpdateTransientModifier(new AttributeModifier(HEALTH_ID, bonus, AttributeModifier.Operation.ADD_VALUE));
			}
			if (player.getHealth() > player.getMaxHealth()) {
				player.setHealth(player.getMaxHealth());
			}
		}
		AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null) {
			boolean full = meals.stream().filter(m -> !m.drink()).count() >= BASE_SLOTS;
			if (full && !speed.hasModifier(SPEED_ID)) {
				speed.addOrUpdateTransientModifier(new AttributeModifier(SPEED_ID, FULL_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
			} else if (!full && speed.hasModifier(SPEED_ID)) {
				speed.removeModifier(SPEED_ID);
			}
		}
	}

	/** Health every meal adds together (a drink adds its own and makes the food count more). */
	public static float foodHealth(List<Meal> meals) {
		float food = 0.0F;
		boolean drink = false;
		for (Meal meal : meals) {
			food += meal.health();
			drink |= meal.drink();
		}
		return drink ? food * (1.0F + DRINK_BONUS) : food;
	}

	public static float regenRate(List<Meal> meals) {
		float rate = BASE_REGEN;
		for (Meal meal : meals) {
			rate += meal.regen();
		}
		return rate;
	}

	private static void regen(ServerPlayer player, List<Meal> meals, long now) {
		if (!player.isHurt() || !player.level().getGameRules().get(GameRules.NATURAL_HEALTH_REGENERATION)) {
			return;
		}
		UUID id = player.getUUID();
		if (now - LAST_HURT.getOrDefault(id, Long.MIN_VALUE / 2) < REGEN_DELAY) {
			return;
		}
		float progress = REGEN_PROGRESS.getOrDefault(id, 0.0F) + regenRate(meals) / REGEN_INTERVAL;
		if (progress >= 1.0F) {
			player.heal(1.0F);
			progress -= 1.0F;
		}
		REGEN_PROGRESS.put(id, progress);
	}
}
