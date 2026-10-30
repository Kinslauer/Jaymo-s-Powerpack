package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Powers;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;

/**
 * Z Fighter's things, our own:
 * <ul>
 * <li><b>Hermit Beans</b>: a slow, rare crop (seeds turn up in jungle temple chests). One bean heals you fully, fills your
 * stomach and a Z Fighter's ki, and clears what ails you. Anyone can eat one.</li>
 * <li><b>Training Weights</b>, worn on the feet (ankle weights and wristbands): slower, lower jumps; a Z Fighter
 * wearing them trains faster ({@link #LIGHT_RATE}, heavy {@link #HEAVY_RATE}).</li>
 * <li>The <b>Sparring Dummy</b> ({@link ZFighterSparring}) and the <b>Training Post</b>, whose drills are played on the
 * client and paid for by {@link ZFighterDrills}.</li>
 * </ul>
 */
public final class ZFighterItems {
	/** One random growth tick in this many is let through: a slow crop. */
	static final int GROWTH_CHANCE = 4;
	static final float SEEDS_TEMPLE_CHANCE = 0.35F;
	static final float BEAN_TEMPLE_CHANCE = 0.12F;
	static final int BEAN_COOLDOWN_SECONDS = 5;

	static final float LIGHT_RATE = 1.5F;
	static final float HEAVY_RATE = 2.0F;
	static final double LIGHT_SLOW = -0.12;
	static final double HEAVY_SLOW = -0.25;
	static final double LIGHT_JUMP = -0.1;
	static final double HEAVY_JUMP = -0.2;

	public static final ResourceKey<EquipmentAsset> WEIGHTS_ASSET = ResourceKey.create(EquipmentAssets.ROOT_ID, JaymosPowers.id("training_weights"));

	public static Block HERMIT_BEANS;
	public static Item HERMIT_BEAN_SEEDS;
	public static Item HERMIT_BEAN;
	public static Item TRAINING_WEIGHTS;
	public static Item HEAVY_TRAINING_WEIGHTS;
	public static Item SPARRING_DUMMY;
	public static Block TRAINING_POST;
	public static Item TRAINING_POST_ITEM;

	private ZFighterItems() {
	}

	static void init() {
		ResourceKey<Block> cropKey = ResourceKey.create(Registries.BLOCK, JaymosPowers.id("hermit_beans"));
		HERMIT_BEANS = Registry.register(BuiltInRegistries.BLOCK, cropKey, new HermitBeansBlock(BlockBehaviour.Properties.of()
			.mapColor(MapColor.PLANT).noCollision().randomTicks().instabreak().sound(SoundType.CROP).pushReaction(PushReaction.POPPED).setId(cropKey)));
		ResourceKey<Item> seedsKey = item("hermit_bean_seeds");
		HERMIT_BEAN_SEEDS = Registry.register(BuiltInRegistries.ITEM, seedsKey, new BlockItem(HERMIT_BEANS, new Item.Properties().setId(seedsKey)
			.useItemDescriptionPrefix().rarity(Rarity.UNCOMMON)));
		ResourceKey<Item> beanKey = item("hermit_bean");
		HERMIT_BEAN = Registry.register(BuiltInRegistries.ITEM, beanKey, new HermitBean(new Item.Properties().setId(beanKey).rarity(Rarity.RARE)
			.food(new FoodProperties.Builder().nutrition(20).saturationModifier(1.0F).alwaysEdible().build(),
				Consumables.defaultFood().consumeSeconds(0.6F).build())
			.useCooldown(BEAN_COOLDOWN_SECONDS)));
		TRAINING_WEIGHTS = weights("training_weights", LIGHT_SLOW, LIGHT_JUMP, Rarity.COMMON);
		HEAVY_TRAINING_WEIGHTS = weights("heavy_training_weights", HEAVY_SLOW, HEAVY_JUMP, Rarity.UNCOMMON);
		ResourceKey<Item> dummyKey = item("sparring_dummy");
		SPARRING_DUMMY = Registry.register(BuiltInRegistries.ITEM, dummyKey, new ZFighterSparring.DummyItem(new Item.Properties().setId(dummyKey)
			.stacksTo(1).rarity(Rarity.UNCOMMON)));
		ResourceKey<Block> postKey = ResourceKey.create(Registries.BLOCK, JaymosPowers.id("training_post"));
		TRAINING_POST = Registry.register(BuiltInRegistries.BLOCK, postKey, new TrainingPostBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD)
			.strength(2.0F).sound(SoundType.WOOD).noOcclusion().setId(postKey)));
		ResourceKey<Item> postItemKey = item("training_post");
		TRAINING_POST_ITEM = Registry.register(BuiltInRegistries.ITEM, postItemKey, new BlockItem(TRAINING_POST, new Item.Properties().setId(postItemKey)
			.useBlockDescriptionPrefix()));

		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FOOD_AND_DRINKS).register(output -> output.accept(HERMIT_BEAN));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.NATURAL_BLOCKS).register(output -> output.accept(HERMIT_BEAN_SEEDS));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> {
			output.accept(TRAINING_WEIGHTS);
			output.accept(HEAVY_TRAINING_WEIGHTS);
			output.accept(SPARRING_DUMMY);
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> output.accept(TRAINING_POST_ITEM));
		LootTableEvents.MODIFY.register((table, builder, source, registries) -> {
			if (!source.isBuiltin() || !table.equals(BuiltInLootTables.JUNGLE_TEMPLE)) {
				return;
			}
			builder.withPool(LootPool.lootPool().when(LootItemRandomChanceCondition.randomChance(SEEDS_TEMPLE_CHANCE))
				.add(LootItem.lootTableItem(HERMIT_BEAN_SEEDS)));
			builder.withPool(LootPool.lootPool().when(LootItemRandomChanceCondition.randomChance(BEAN_TEMPLE_CHANCE))
				.add(LootItem.lootTableItem(HERMIT_BEAN)));
		});
	}

	private static ResourceKey<Item> item(String name) {
		return ResourceKey.create(Registries.ITEM, JaymosPowers.id(name));
	}

	private static Item weights(String name, double slow, double jump, Rarity rarity) {
		ResourceKey<Item> key = item(name);
		ItemAttributeModifiers modifiers = ItemAttributeModifiers.builder()
			.add(Attributes.MOVEMENT_SPEED, new AttributeModifier(JaymosPowers.id(name + "_speed"), slow, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL),
				EquipmentSlotGroup.FEET)
			.add(Attributes.JUMP_STRENGTH, new AttributeModifier(JaymosPowers.id(name + "_jump"), jump, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL),
				EquipmentSlotGroup.FEET)
			.build();
		return Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key).stacksTo(1).rarity(rarity)
			.component(net.minecraft.core.component.DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.FEET)
				.setEquipSound(SoundEvents.ARMOR_EQUIP_CHAIN)
				.setAsset(WEIGHTS_ASSET)
				.setAllowedEntities(EntityTypes.PLAYER)
				.build())
			.attributes(modifiers)));
	}

	/** How much faster {@code player} trains for what's on his feet (1 with no weights). */
	static float weightRate(Player player) {
		ItemStack feet = player.getItemBySlot(EquipmentSlot.FEET);
		if (feet.is(HEAVY_TRAINING_WEIGHTS)) {
			return HEAVY_RATE;
		}
		return feet.is(TRAINING_WEIGHTS) ? LIGHT_RATE : 1.0F;
	}

	/** The crop: four stages, slow to grow. */
	static final class HermitBeansBlock extends CropBlock {
		static final IntegerProperty AGE = BlockStateProperties.AGE_3;

		HermitBeansBlock(BlockBehaviour.Properties properties) {
			super(properties);
		}

		@Override
		protected IntegerProperty getAgeProperty() {
			return AGE;
		}

		@Override
		public int getMaxAge() {
			return 3;
		}

		@Override
		protected ItemLike getBaseSeedId() {
			return HERMIT_BEAN_SEEDS;
		}

		@Override
		protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
			if (random.nextInt(GROWTH_CHANCE) == 0) {
				super.randomTick(state, level, pos, random);
			}
		}

		@Override
		protected int getBonemealAgeIncrease(Level level) {
			return level.getRandom().nextInt(4) == 0 ? 1 : 0;
		}

		@Override
		protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
			builder.add(AGE);
		}
	}

	/** A Hermit Bean: everything back at once. */
	static final class HermitBean extends Item {
		HermitBean(Properties properties) {
			super(properties);
		}

		@Override
		public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
			ItemStack left = super.finishUsingItem(stack, level, entity);
			if (level instanceof ServerLevel server) {
				// lost limbs grown back and bones set, then whole ((0.14.2) in that order: a part still missing or unset can't take
				// the healing, and Wounds would refuse it)
				com.jaymospowers.core.Dismember.regrow(entity, true);
				com.jaymospowers.core.Fractures.heal(entity);
				entity.setHealth(entity.getMaxHealth());
				com.jaymospowers.core.wounds.Wounds.reconcile(entity);
				List<MobEffectInstance> ailments = new ArrayList<>();
				for (MobEffectInstance effect : entity.getActiveEffects()) {
					if (effect.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
						ailments.add(effect);
					}
				}
				for (MobEffectInstance effect : ailments) {
					entity.removeEffect(effect.getEffect());
				}
				entity.clearFire();
				if (entity instanceof ServerPlayer player) {
					player.getFoodData().setFoodLevel(20);
					player.getFoodData().setSaturation(20.0F);
					if (Powers.has(player, ZFighterPower.INSTANCE)) {
						ZFighterPower.live(player).ki = ZFighterPower.maxKi(player);
					}
				}
				server.sendParticles(ParticleTypes.HAPPY_VILLAGER, entity.getX(), entity.getY() + 1.0, entity.getZ(), 16, 0.4, 0.6, 0.4, 0.05);
				server.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6F, 1.8F);
			}
			return left;
		}
	}
}
