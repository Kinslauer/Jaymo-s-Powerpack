package com.jaymoskingdoms;

import com.google.common.collect.ImmutableSet;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.object.builder.v1.world.poi.PoiHelper;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.trading.TradeSet;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * The town herbalist (Hunter's Road, patch 7): a villager's trade at an <b>herbalist's table</b> (its job site), who sells
 * herbs, formulas, spirits and empty phials and buys what is cut from the beasts of the road (the data pack's trades,
 * {@code trade_set/herbalist}). The order herbalist ({@link HerbalistWares}) is a different person and stays as it is.
 * <p>
 * <b>Tonics</b> (Remedies' spirit: no modern medicine, only herb and water): at the table, a water bottle in one hand and
 * a stack of one herb in the other makes a tonic from up to {@link #MOST_HERBS} of that herb. Drunk, a tonic feeds a little
 * ({@link #FOOD}), gives the herb's good effect and its bad one, each lasting longer for every herb in it. Herbs are looked
 * up by id (Kingdoms never compiles against Powers); one that isn't there makes nothing.
 */
public final class Herbalists {
	/** The most herbs one tonic takes. */
	public static final int MOST_HERBS = 5;
	/** A tonic's good effect lasts this long (ticks) with one herb, its bad one this long; each extra herb adds half as much again. */
	static final int GOOD_TICKS = 20 * 45, BAD_TICKS = 20 * 12;
	static final float EXTRA_PER_HERB = 0.5F;
	/** What a tonic feeds: food points and saturation. */
	static final int FOOD = 2;
	static final float SATURATION = 1.0F;
	/** The drinking time (ticks). */
	static final int DRINK_TICKS = 32;

	/** One herb's tonic: its good effect and its bad one (the bad one is the price). */
	record Brew(Holder<MobEffect> good, Holder<MobEffect> bad) {
	}

	/** Which herb gives which: every herb here is Powers' (an id), so a tonic of one needs Powers. */
	static final Map<String, Brew> BREWS = new LinkedHashMap<>();

	static {
		brew("feverfew", MobEffects.REGENERATION, MobEffects.HUNGER);
		brew("valerian", MobEffects.RESISTANCE, MobEffects.SLOWNESS);
		brew("juniper_berries", MobEffects.SPEED, MobEffects.NAUSEA);
		brew("cave_ivy", MobEffects.NIGHT_VISION, MobEffects.DARKNESS);
		brew("bracken", MobEffects.JUMP_BOOST, MobEffects.WEAKNESS);
		brew("cardinal_flower", MobEffects.WATER_BREATHING, MobEffects.SLOWNESS);
		brew("earthball", MobEffects.RESISTANCE, MobEffects.NAUSEA);
		brew("honey_fungus", MobEffects.ABSORPTION, MobEffects.WEAKNESS);
		brew("mandrake_root", MobEffects.STRENGTH, MobEffects.DARKNESS);
		brew("comfrey", MobEffects.REGENERATION, MobEffects.SLOWNESS);
		brew("yarrow", MobEffects.ABSORPTION, MobEffects.HUNGER);
		brew("plantain", MobEffects.SPEED, MobEffects.HUNGER);
		brew("wild_garlic", MobEffects.STRENGTH, MobEffects.NAUSEA);
		brew("chamomile", MobEffects.RESISTANCE, MobEffects.MINING_FATIGUE);
	}

	/** A tonic's contents: which herb, how many. */
	public record Tonic(String herb, int herbs) {
		static final Codec<Tonic> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("herb").forGetter(Tonic::herb),
			Codec.INT.fieldOf("herbs").forGetter(Tonic::herbs)).apply(i, Tonic::new));
		static final StreamCodec<RegistryFriendlyByteBuf, Tonic> STREAM = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, Tonic::herb, ByteBufCodecs.VAR_INT, Tonic::herbs, Tonic::new);
	}

	public static DataComponentType<Tonic> TONIC;
	public static Item TONIC_ITEM, TABLE_ITEM;
	public static Block TABLE;
	private static ResourceKey<net.minecraft.world.entity.ai.village.poi.PoiType> POI;

	private Herbalists() {
	}

	private static void brew(String herb, Holder<MobEffect> good, Holder<MobEffect> bad) {
		BREWS.put(herb, new Brew(good, bad));
	}

	private static Identifier herbId(String herb) {
		return Identifier.fromNamespaceAndPath("jaymospowers", herb);
	}

	/** The herbalist's table, its item, the point of interest and the profession that works at it ({@link Professions#register}). */
	static VillagerProfession profession() {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosKingdoms.id("herbalist_table"));
		TABLE = Registry.register(BuiltInRegistries.BLOCK, key, new TableBlock(BlockBehaviour.Properties.of().setId(key)
			.mapColor(MapColor.WOOD).strength(2.0F).sound(SoundType.WOOD).noOcclusion()));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("herbalist_table"));
		TABLE_ITEM = Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(TABLE, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
		ResourceKey<Item> tonicKey = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("herbal_tonic"));
		TONIC = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosKingdoms.id("herbal_tonic"),
			DataComponentType.<Tonic>builder().persistent(Tonic.CODEC).networkSynchronized(Tonic.STREAM).build());
		TONIC_ITEM = Registry.register(BuiltInRegistries.ITEM, tonicKey, new TonicItem(new Item.Properties().setId(tonicKey).stacksTo(8)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> output.accept(TABLE_ITEM));
		PoiHelper.register(JaymosKingdoms.id("herbalist"), 1, 1, TABLE);
		POI = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, JaymosKingdoms.id("herbalist"));
		Int2ObjectOpenHashMap<ResourceKey<TradeSet>> trades = new Int2ObjectOpenHashMap<>();
		for (int level = 1; level <= 4; level++) {
			trades.put(level, ResourceKey.create(Registries.TRADE_SET, JaymosKingdoms.id("herbalist/level_" + level)));
		}
		return new VillagerProfession(Component.translatable("entity.minecraft.villager.jaymoskingdoms.herbalist"), h -> h.is(POI), h -> h.is(POI),
			ImmutableSet.of(), ImmutableSet.of(), SoundEvents.VILLAGER_WORK_CLERIC, trades);
	}

	/** Every tonic of every herb, for the creative tab. */
	static void register() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FOOD_AND_DRINKS).register(output -> {
			for (String herb : BREWS.keySet()) {
				if (BuiltInRegistries.ITEM.containsKey(herbId(herb))) {
					output.accept(tonic(herb, 1));
				}
			}
		});
	}

	static ItemStack tonic(String herb, int herbs) {
		ItemStack stack = new ItemStack(TONIC_ITEM);
		stack.set(TONIC, new Tonic(herb, Math.max(1, Math.min(MOST_HERBS, herbs))));
		return stack;
	}

	/** How long an effect lasts when it takes {@code herbs} herbs (ticks). */
	static int ticks(int base, int herbs) {
		return Math.round(base * (1.0F + EXTRA_PER_HERB * (Math.max(1, Math.min(MOST_HERBS, herbs)) - 1)));
	}

	private static boolean water(ItemStack stack) {
		return stack.is(Items.POTION) && stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).is(Potions.WATER);
	}

	/** The herb's short id if {@code stack} is a herb a tonic is made of (and that mod is there), else null. */
	private static @Nullable String herbOf(ItemStack stack) {
		if (stack.isEmpty()) {
			return null;
		}
		Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
		return id.getNamespace().equals("jaymospowers") && BREWS.containsKey(id.getPath()) ? id.getPath() : null;
	}

	/** The herbalist's table: with a water bottle in one hand and a stack of a herb in the other, use it to make a tonic. */
	static final class TableBlock extends Block {
		TableBlock(Properties properties) {
			super(properties);
		}

		@Override
		protected InteractionResult useWithoutItem(BlockState state, Level level, net.minecraft.core.BlockPos pos, Player player, BlockHitResult hit) {
			if (!level.isClientSide()) {
				player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.herbalist_table.how"));
			}
			return InteractionResult.SUCCESS;
		}

		@Override
		protected InteractionResult useItemOn(ItemStack held, BlockState state, Level level, net.minecraft.core.BlockPos pos, Player player,
			InteractionHand hand, BlockHitResult hit) {
			InteractionHand other = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
			ItemStack bottle = water(held) ? held : player.getItemInHand(other), herbs = bottle == held ? player.getItemInHand(other) : held;
			String herb = water(bottle) ? herbOf(herbs) : null;
			if (herb == null) {
				return InteractionResult.TRY_WITH_EMPTY_HAND;
			}
			if (hand != InteractionHand.MAIN_HAND) {
				return InteractionResult.SUCCESS;
			}
			if (level instanceof ServerLevel server) {
				int used = Math.min(MOST_HERBS, herbs.getCount());
				herbs.shrink(used);
				bottle.shrink(1);
				ItemStack made = tonic(herb, used);
				player.getInventory().placeItemBackInInventory(made, net.minecraft.util.Prediction.SERVER_ONLY);
				server.playSound(null, pos, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 0.8F, 1.2F);
				Fx.burst(server, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 0x7FB050, 0.5F, 5, 0.2, 0.1, 0.2);
			}
			return InteractionResult.SUCCESS;
		}
	}

	/** A tonic: drunk, it feeds a little, gives its herb's good effect and its bad one, longer for each herb in it. */
	static final class TonicItem extends Item {
		TonicItem(Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			player.startUsingItem(hand);
			return InteractionResult.CONSUME;
		}

		@Override
		public int getUseDuration(ItemStack stack, LivingEntity user) {
			return DRINK_TICKS;
		}

		@Override
		public ItemUseAnimation getUseAnimation(ItemStack stack) {
			return ItemUseAnimation.DRINK;
		}

		@Override
		public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity user) {
			Tonic tonic = stack.get(TONIC);
			Brew brew = tonic == null ? null : BREWS.get(tonic.herb());
			if (!level.isClientSide() && brew != null) {
				user.addEffect(new MobEffectInstance(brew.good(), ticks(GOOD_TICKS, tonic.herbs()), 0));
				user.addEffect(new MobEffectInstance(brew.bad(), ticks(BAD_TICKS, tonic.herbs()), 0));
				level.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.GENERIC_DRINK.value(), SoundSource.PLAYERS, 0.8F, 1.0F);
			}
			if (user instanceof Player player) {
				if (!level.isClientSide()) {
					player.getFoodData().eat(FOOD, SATURATION);
				}
				if (!player.hasInfiniteMaterials()) {
					stack.shrink(1);
					if (stack.isEmpty()) {
						return new ItemStack(Items.GLASS_BOTTLE);
					}
					player.getInventory().placeItemBackInInventory(new ItemStack(Items.GLASS_BOTTLE), net.minecraft.util.Prediction.SERVER_ONLY);
				}
			}
			return stack;
		}

		@Override
		public Component getName(ItemStack stack) {
			Tonic tonic = stack.get(TONIC);
			if (tonic == null) {
				return super.getName(stack);
			}
			Item herb = BuiltInRegistries.ITEM.getOptional(herbId(tonic.herb())).orElse(null);
			return Component.translatable("item.jaymoskingdoms.herbal_tonic.of", herb == null ? tonic.herb() : new ItemStack(herb).getHoverName());
		}

		@Override
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
			Tonic tonic = stack.get(TONIC);
			Brew brew = tonic == null ? null : BREWS.get(tonic.herb());
			if (brew != null) {
				out.accept(Component.translatable("item.jaymoskingdoms.herbal_tonic.good", brew.good().value().getDisplayName(), ticks(GOOD_TICKS, tonic.herbs()) / 20)
					.withStyle(net.minecraft.ChatFormatting.BLUE));
				out.accept(Component.translatable("item.jaymoskingdoms.herbal_tonic.bad", brew.bad().value().getDisplayName(), ticks(BAD_TICKS, tonic.herbs()) / 20)
					.withStyle(net.minecraft.ChatFormatting.RED));
				out.accept(Component.translatable("item.jaymoskingdoms.herbal_tonic.herbs", tonic.herbs(), MOST_HERBS).withStyle(net.minecraft.ChatFormatting.GRAY));
			}
		}
	}
}
