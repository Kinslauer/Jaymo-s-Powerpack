package com.jaymoskitchen.delight;

import com.jaymoskitchen.JaymosKitchen;
import com.mojang.serialization.Codec;
import java.util.Collections;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.item.v1.EnchantingContext;
import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.component.Weapon;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipePropertySet;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * (Farmer's Delight's skillet, MIT; its stats, sounds and cooking, our own code.) The Skillet, which is the Kitchen's
 * old Frying Pan (same id, {@code jaymoskitchen:frying_pan}, so pans in existing worlds are skillets now): a melee
 * weapon that rings when it hits, and a pan to cook in on the go. Stand near a fire, hold something a campfire could
 * cook in the other hand and hold use: it sizzles for a while and the cooked food lands in your pockets. Click while it
 * cooks to flip it. Sneak to set it down as a block ({@link CookwareBlock}) instead.
 */
public class SkilletItem extends BlockItem {
	/** How long a flip of the pan takes, in ticks. */
	public static final float FLIP_TIME = 12;
	/** The pan is as tough as iron. */
	public static final ToolMaterial MATERIAL = ToolMaterial.IRON;
	/** Held cooking takes this fraction of a campfire's time (a campfire's 30 seconds becomes 6), cut by Fire Aspect. */
	private static final float COOKING_TIME_FRACTION = 0.2F;
	/** Fire Aspect takes this much more off the fraction for each level. */
	private static final float FIRE_ASPECT_REDUCTION = 0.05F;
	/** The shortest a held cook can be, in ticks. */
	private static final int MINIMUM_COOKING_TIME = 60;
	/** The campfire time (ticks) assumed for a pan with no recipe time stored. */
	private static final int DEFAULT_COOKING_TIME = 600;
	/** Hits softer than this much of a full swing ring the weak sound. */
	private static final float WEAK_ATTACK_SCALE = 0.8F;
	/** Extra knockback the pan gives a hit. */
	private static final float KNOCKBACK = 1.0F;
	private static final float ATTACK_DAMAGE = 5.0F;
	private static final float ATTACK_SPEED = -3.1F;
	private static final Identifier KNOCKBACK_ID = JaymosKitchen.id("base_attack_knockback");

	/** The campfire cooking time of what's in the pan, in ticks. */
	public static DataComponentType<Integer> COOKING_TIME;
	/** The one item cooking in the pan. */
	public static DataComponentType<StackBox> INGREDIENT;
	/** The game tick the pan was last flipped. */
	public static DataComponentType<Long> FLIP_TIMESTAMP;
	/** Which side of the food is up. */
	public static DataComponentType<Boolean> FLIPPED;

	/** How hard the last swing was (set before the game resets it, see {@link #hurtEnemy}); one server thread. */
	private static float attackPower = 1.0F;
	/** Who is looking at the bar (the client sets this: the common side can't name the client). */
	public static Supplier<@Nullable Player> viewer = () -> null;

	public SkilletItem(Block block, Properties properties) {
		super(block, properties
			.durability(MATERIAL.durability())
			.repairable(MATERIAL.repairItems())
			.enchantable(MATERIAL.enchantmentValue())
			.attributes(createAttributes(MATERIAL, ATTACK_DAMAGE, ATTACK_SPEED))
			.component(DataComponents.TOOL, new Tool(Collections.emptyList(), 1.0F, 1, false))
			.component(DataComponents.WEAPON, new Weapon(1)));
	}

	/** The data components (before the first pan is made: the item doesn't need them, its stacks do). */
	public static void registerComponents() {
		COOKING_TIME = component("skillet_cooking_time", DataComponentType.<Integer>builder()
			.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.INT).build());
		INGREDIENT = component("skillet_ingredient", DataComponentType.<StackBox>builder()
			.persistent(StackBox.CODEC).networkSynchronized(StackBox.STREAM_CODEC).cacheEncoding().build());
		FLIP_TIMESTAMP = component("skillet_flip_timestamp", DataComponentType.<Long>builder()
			.persistent(Codec.LONG).networkSynchronized(ByteBufCodecs.VAR_LONG).cacheEncoding().build());
		FLIPPED = component("skillet_flipped", DataComponentType.<Boolean>builder()
			.persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL).cacheEncoding().build());
	}

	private static <T> DataComponentType<T> component(String id, DataComponentType<T> type) {
		return Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosKitchen.id(id), type);
	}

	public static ItemAttributeModifiers createAttributes(ToolMaterial material, float attackDamage, float attackSpeed) {
		return ItemAttributeModifiers.builder()
			.add(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_ID, attackDamage + material.attackDamageBonus(), AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
			.add(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_ID, attackSpeed, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
			.add(Attributes.ATTACK_KNOCKBACK, new AttributeModifier(KNOCKBACK_ID, KNOCKBACK, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
			.build();
	}

	/** Notes how hard a player's swing is, before the game resets it (from an attack callback, see {@link DelightHome}). */
	public static void noteSwing(Player player) {
		attackPower = player.getAttackStrengthScale(0.5F);
	}

	@Override
	public boolean allowComponentsUpdateAnimation(Player player, InteractionHand hand, ItemStack oldStack, ItemStack newStack) {
		if (changed(oldStack, newStack, FLIP_TIMESTAMP) || changed(oldStack, newStack, COOKING_TIME) || changed(oldStack, newStack, INGREDIENT)) {
			return false;
		}
		return super.allowComponentsUpdateAnimation(player, hand, oldStack, newStack);
	}

	private static boolean changed(ItemStack oldStack, ItemStack newStack, DataComponentType<?> type) {
		return oldStack.has(type) && newStack.has(type) && !oldStack.get(type).equals(newStack.get(type));
	}

	@Override
	public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
		stack.hurtAndBreak(1, attacker, EquipmentSlot.MAINHAND);
		// the ring of the pan: a soft one for a half-charged swing
		Level level = attacker.level();
		if (level.isClientSide()) {
			return;
		}
		if (attacker instanceof Player && attackPower <= WEAK_ATTACK_SCALE) {
			level.playSound(null, attacker, DelightHome.SKILLET_ATTACK_WEAK, attacker.getSoundSource(), 0.8F, 0.9F);
		} else {
			float pitch = 0.9F + attacker.getRandom().nextFloat() * 0.2F;
			level.playSound(null, attacker, DelightHome.SKILLET_ATTACK_STRONG, attacker.getSoundSource(), 1.0F, pitch);
		}
		attackPower = 1.0F;
	}

	/** Whether there's a fire (or you are one) close enough to cook in the hand. */
	private static boolean nearHeat(Player player, Level level) {
		if (player.isOnFire()) {
			return true;
		}
		BlockPos pos = player.blockPosition();
		for (BlockPos near : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 1, 1))) {
			if (CookwareBlock.hot(level.getBlockState(near))) {
				return true;
			}
		}
		return false;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
		builder.accept(Component.translatable("tooltip.jaymoskitchen.placeable_sneaking").withStyle(net.minecraft.ChatFormatting.DARK_GRAY, net.minecraft.ChatFormatting.ITALIC));
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity entity) {
		int fireAspect = 0;
		Optional<Holder.Reference<Enchantment>> holder = entity.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(Enchantments.FIRE_ASPECT);
		if (holder.isPresent()) {
			fireAspect = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(holder.get());
		}
		return cookingTime(stack.getOrDefault(COOKING_TIME, 0), fireAspect);
	}

	/**
	 * The time held cooking takes: a fifth of the campfire's, less for each level of Fire Aspect, in whole seconds,
	 * never under {@link #MINIMUM_COOKING_TIME} (nor over the campfire's own).
	 */
	public static int cookingTime(int campfireTime, int fireAspect) {
		int time = campfireTime > 0 ? campfireTime : DEFAULT_COOKING_TIME;
		float fraction = COOKING_TIME_FRACTION - fireAspect * FIRE_ASPECT_REDUCTION;
		int result = (int) (time / 20 * fraction) * 20;
		return Mth.clamp(result, MINIMUM_COOKING_TIME, Math.max(campfireTime, MINIMUM_COOKING_TIME));
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack skillet = player.getItemInHand(hand);
		if (!nearHeat(player, level)) {
			return InteractionResult.PASS;
		}
		InteractionHand otherHand = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
		ItemStack cooking = player.getItemInHand(otherHand);
		if (!skillet.getOrDefault(INGREDIENT, StackBox.EMPTY).stack().isEmpty()) {
			player.startUsingItem(hand);
			return InteractionResult.PASS;
		}
		RecipePropertySet cookable = level.recipeAccess().propertySet(RecipePropertySet.CAMPFIRE_INPUT);
		if (!cookable.test(cooking)) {
			player.sendOverlayMessage(Component.translatable("item.jaymoskitchen.frying_pan.how_to_cook"));
			return InteractionResult.PASS;
		}
		if (player.isUnderWater()) {
			player.sendOverlayMessage(Component.translatable("item.jaymoskitchen.frying_pan.underwater"));
			return InteractionResult.PASS;
		}
		if (level instanceof ServerLevel server) {
			Optional<RecipeHolder<CampfireCookingRecipe>> recipe = recipeFor(cooking, server);
			if (recipe.isEmpty()) {
				return InteractionResult.CONSUME;
			}
			ItemStack rest = cooking.copy();
			ItemStack one = rest.split(1);
			skillet.set(INGREDIENT, new StackBox(one));
			skillet.set(COOKING_TIME, recipe.get().value().cookingTime());
			player.startUsingItem(hand);
			player.setItemInHand(otherHand, rest);
		}
		return InteractionResult.CONSUME;
	}

	@Override
	public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
		if (!(entity instanceof Player player)) {
			return;
		}
		if (stack.has(FLIP_TIMESTAMP)) {
			long since = level.getGameTime() - stack.get(FLIP_TIMESTAMP);
			if (since > FLIP_TIME) {
				stack.remove(FLIP_TIMESTAMP);
				stack.set(FLIPPED, !stack.getOrDefault(FLIPPED, false));
			} else if (level.isClientSide() && since == FLIP_TIME - 8) {
				// plays just before it lands, on the client only so it's instant
				level.playSound(player, entity, DelightHome.SKILLET_ADD_FOOD, SoundSource.PLAYERS, 0.4F, level.getRandom().nextFloat() * 0.2F + 0.9F);
			} else if (level.isClientSide() && (level.getRandom().nextInt(50) == 0 && since < FLIP_TIME - 8 || since > FLIP_TIME - 3)) {
				level.playSound(player, entity, DelightHome.SKILLET_SIZZLE, SoundSource.PLAYERS, 0.4F, level.getRandom().nextFloat() * 0.2F + 0.9F);
			}
		} else if (level.isClientSide() && level.getRandom().nextInt(50) == 0) {
			level.playSound(player, entity, DelightHome.SKILLET_SIZZLE, SoundSource.PLAYERS, 0.4F, level.getRandom().nextFloat() * 0.2F + 0.9F);
		}
	}

	/** Let go early: the food goes back in your pockets, raw. */
	@Override
	public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
		if (entity instanceof Player player) {
			ItemStack cooking = stack.getOrDefault(INGREDIENT, StackBox.EMPTY).stack();
			if (!cooking.isEmpty()) {
				player.getInventory().placeItemBackInInventory(cooking, Prediction.PREDICTED);
				clear(stack);
			}
		}
		return super.releaseUsing(stack, level, entity, timeLeft);
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
		if (entity instanceof Player player && level instanceof ServerLevel server) {
			ItemStack cooking = stack.getOrDefault(INGREDIENT, StackBox.EMPTY).stack();
			if (!cooking.isEmpty()) {
				recipeFor(cooking, server).ifPresent(recipe -> {
					ItemStack result = recipe.value().assemble(new SingleRecipeInput(cooking));
					if (!player.getInventory().add(result)) {
						player.drop(result, false, Prediction.SERVER_ONLY);
					}
					if (player instanceof ServerPlayer serverPlayer) {
						CriteriaTriggers.CONSUME_ITEM.trigger(serverPlayer, stack);
					}
				});
				InteractionHand other = entity.getUsedItemHand() == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
				player.getItemInHand(other).shrink(1);
				clear(stack);
			}
		}
		return stack;
	}

	private static void clear(ItemStack stack) {
		stack.remove(INGREDIENT);
		stack.remove(COOKING_TIME);
		stack.remove(FLIP_TIMESTAMP);
		stack.remove(FLIPPED);
	}

	/** The bar fills as the food cooks (the client's player is the one using it). */
	@Override
	public int getBarWidth(ItemStack stack) {
		Player player = viewer.get();
		if (player != null && stack.has(COOKING_TIME) && player.isUsingItem()) {
			int total = getUseDuration(stack, player);
			return Mth.clamp(Math.round(13.0F - player.getUseItemRemainingTicks() * 13.0F / total), 0, 13);
		}
		return stack.has(COOKING_TIME) ? 0 : super.getBarWidth(stack);
	}

	@Override
	public int getBarColor(ItemStack stack) {
		return stack.has(COOKING_TIME) ? 0xFF8B4F : super.getBarColor(stack);
	}

	@Override
	public boolean isBarVisible(ItemStack stack) {
		return super.isBarVisible(stack) || stack.has(COOKING_TIME);
	}

	private static Optional<RecipeHolder<CampfireCookingRecipe>> recipeFor(ItemStack stack, ServerLevel level) {
		if (stack.isEmpty()) {
			return Optional.empty();
		}
		return level.recipeAccess().getRecipeFor(RecipeType.CAMPFIRE_COOKING, new SingleRecipeInput(stack), level);
	}

	@Override
	public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity entity) {
		if (!level.isClientSide() && state.getDestroySpeed(level, pos) != 0.0F) {
			stack.hurtAndBreak(1, entity, EquipmentSlot.MAINHAND);
		}
		return true;
	}

	/** Only sneaking sets it down; otherwise the click is left for {@link #use}. */
	@Override
	public InteractionResult place(BlockPlaceContext context) {
		Player player = context.getPlayer();
		if (player != null && player.isShiftKeyDown()) {
			return super.place(context);
		}
		return InteractionResult.PASS;
	}

	@Override
	public boolean canBeEnchantedWith(ItemStack stack, Holder<Enchantment> enchantment, EnchantingContext context) {
		if (enchantment.is(Enchantments.SWEEPING_EDGE)) {
			return false;
		}
		return super.canBeEnchantedWith(stack, enchantment, context);
	}

	/** Client → server: flip the pan being used. */
	public record Flip() implements CustomPacketPayload {
		public static final Type<Flip> TYPE = new Type<>(JaymosKitchen.id("flip_skillet"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Flip> CODEC = StreamCodec.unit(new Flip());

		@Override
		public Type<Flip> type() {
			return TYPE;
		}

		public void handle(ServerPlayer player) {
			ItemStack stack = player.getUseItem();
			if (stack.getItem() instanceof SkilletItem && !stack.has(FLIP_TIMESTAMP)) {
				stack.set(FLIP_TIMESTAMP, player.level().getGameTime());
			}
		}
	}
}
