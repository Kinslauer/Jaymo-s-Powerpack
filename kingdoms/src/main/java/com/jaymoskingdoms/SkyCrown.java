package com.jaymoskingdoms;

import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * <b>Six shards of one crown</b>: whoever ruled the Aether before it was left untended wore a crown, and it broke when
 * the remnants fell. Six shards of it came down far apart ((a fallen Giant, a sentry ruins chest, a Nether fortress chest, an End city chest, an ancient city chest, a shipwreck's treasure chest)): in a monolith's broken heart (a fallen
 * Giant), the sentry ruins, a Nether fortress, an End city, the deep dark and a sunken ship's hold. Nobody below knows
 * what they are; the shards are named "cursed glass".
 * <p>
 * Carrying a shard gives its small gift ({@link #gifts}): a lighter step, a pull on metal like lodestone, a ward against
 * cold, eyes for the dark, breath under water, a quicker heart. Set all six in a crown stand ({@link Stand}) and use it
 * empty-handed: they become the Sky Crown, worn on the head, which gives all six at once.
 */
public final class SkyCrown {
	/** The six shards, in the order of their places, and the gift each gives. */
	static final String[] KINDS = {"step", "lode", "cold", "night", "breath", "heart"};
	/** The gifts: how many extra blocks of fall are safe, the pull's reach (blocks) and speed, the night eyes and breath's lasting (ticks), the quicker swing. */
	static final double SAFE_FALL = 3.0, PULL_RANGE = 7.0, PULL_SPEED = 0.18, ATTACK_SPEED = 0.4;
	static final int REFRESH = 10, NIGHT_TICKS = 260, BREATH_TICKS = 60;
	/** The chance each place's chest (or fallen Giant) holds its shard. */
	static final float GIANT_HEART = 0.5F, SENTRY_BOSS = 1.0F, SENTRY_RARE = 0.25F, FORTRESS = 0.15F, END_CITY = 0.3F, DEEP_DARK = 0.3F, SHIP = 0.35F;
	/** Which metals the lodestone gift pulls (words in an item's id). */
	static final List<String> METALS = List.of("iron", "gold", "copper", "netherite", "ingot", "nugget", "raw_", "chain", "rail");
	static final Identifier FALL_ID = JaymosKingdoms.id("sky_crown_step"), SWING_ID = JaymosKingdoms.id("sky_crown_heart");

	public static Item[] SHARDS = new Item[6];
	public static Item CROWN, STAND_ITEM;
	public static Block STAND;
	static final BooleanProperty[] SET = new BooleanProperty[6];

	static {
		for (int i = 0; i < 6; i++) {
			SET[i] = BooleanProperty.create("s" + i);
		}
	}

	private SkyCrown() {
	}

	/** The crown stand: shards set in it, six slots in its blockstate. */
	private static final class Stand extends Block {
		private static final VoxelShape SHAPE = Block.box(3.0, 0.0, 3.0, 13.0, 12.0, 13.0);

		Stand(BlockBehaviour.Properties properties) {
			super(properties);
			BlockState state = this.stateDefinition.any();
			for (BooleanProperty p : SET) {
				state = state.setValue(p, false);
			}
			registerDefaultState(state);
		}

		@Override
		protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
			builder.add(SET);
		}

		@Override
		protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
			return SHAPE;
		}

		@Override
		protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
			BlockHitResult hit) {
			int kind = kind(stack);
			if (kind < 0) {
				return InteractionResult.TRY_WITH_EMPTY_HAND;
			}
			if (state.getValue(SET[kind])) {
				return InteractionResult.SUCCESS;
			}
			if (level instanceof ServerLevel server) {
				BlockState now = state.setValue(SET[kind], true);
				server.setBlock(pos, now, 3);
				stack.shrink(1);
				server.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F, 0.8F + 0.1F * kind);
				if (player instanceof ServerPlayer sp) {
					sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.crown.set", count(now)));
				}
			}
			return InteractionResult.SUCCESS;
		}

		@Override
		protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
			if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp)) {
				return InteractionResult.SUCCESS;
			}
			int set = count(state);
			if (set < 6) {
				sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.crown.set", set));
				return InteractionResult.SUCCESS;
			}
			BlockState empty = state;
			for (BooleanProperty p : SET) {
				empty = empty.setValue(p, false);
			}
			server.setBlock(pos, empty, 3);
			ItemStack crown = new ItemStack(CROWN);
			sp.getInventory().placeItemBackInInventory(crown, net.minecraft.util.Prediction.SERVER_ONLY);
			server.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0F, 1.4F);
			sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.crown.whole"));
			return InteractionResult.SUCCESS;
		}
	}

	private static int count(BlockState state) {
		int n = 0;
		for (BooleanProperty p : SET) {
			n += state.getValue(p) ? 1 : 0;
		}
		return n;
	}

	/** Which shard {@code stack} is (an index into {@link #KINDS}), or -1. */
	static int kind(ItemStack stack) {
		for (int i = 0; i < 6; i++) {
			if (stack.is(SHARDS[i])) {
				return i;
			}
		}
		return -1;
	}

	static void register() {
		for (int i = 0; i < 6; i++) {
			ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("sky_shard_" + KINDS[i]));
			SHARDS[i] = Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key).stacksTo(1).rarity(Rarity.RARE)));
		}
		ResourceKey<Item> crownKey = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("sky_crown"));
		CROWN = Registry.register(BuiltInRegistries.ITEM, crownKey, new Item(new Item.Properties().setId(crownKey).stacksTo(1).rarity(Rarity.EPIC)
			.component(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.HEAD).setAsset(EquipmentAssets.GOLD).build())));
		ResourceKey<Block> standKey = ResourceKey.create(Registries.BLOCK, JaymosKingdoms.id("crown_stand"));
		STAND = Registry.register(BuiltInRegistries.BLOCK, standKey, new Stand(BlockBehaviour.Properties.of().setId(standKey)
			.mapColor(MapColor.STONE).sound(SoundType.STONE).strength(2.0F, 6.0F).requiresCorrectToolForDrops().noOcclusion()));
		ResourceKey<Item> standItemKey = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("crown_stand"));
		STAND_ITEM = Registry.register(BuiltInRegistries.ITEM, standItemKey, new BlockItem(STAND, new Item.Properties().setId(standItemKey).useBlockDescriptionPrefix()));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> output.accept(STAND_ITEM));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(output -> {
			for (Item shard : SHARDS) {
				output.accept(shard);
			}
			output.accept(CROWN);
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % REFRESH == 0) {
				for (ServerPlayer player : server.getPlayerList().getPlayers()) {
					gifts(player);
				}
			}
		});
		loot();
		// a fallen Giant leaves its monolith's broken heart, and in it a shard
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (com.jaymoskingdoms.remnants.Monoliths.giant(entity) && entity.level() instanceof ServerLevel level
				&& level.getRandom().nextFloat() < GIANT_HEART) {
				entity.spawnAtLocation(level, new ItemStack(SHARDS[0]));
			}
		});
	}

	// ---- where they lie ----------------------------------------------------------------------------------------------

	/** The sentry ruins' chests are Powers' (a loot table named by id; nothing is compiled against Powers). */
	private static ResourceKey<LootTable> sentry(String path) {
		return ResourceKey.create(Registries.LOOT_TABLE, Identifier.fromNamespaceAndPath("jaymospowers", "chests/sentry_ruins/" + path));
	}

	private static void loot() {
		// kind, chance, by chest
		Map<ResourceKey<LootTable>, Object[]> where = Map.of(
			sentry("boss"), new Object[] {1, SENTRY_BOSS}, sentry("rare"), new Object[] {1, SENTRY_RARE},
			BuiltInLootTables.NETHER_BRIDGE, new Object[] {2, FORTRESS}, BuiltInLootTables.END_CITY_TREASURE, new Object[] {3, END_CITY},
			BuiltInLootTables.ANCIENT_CITY, new Object[] {4, DEEP_DARK}, BuiltInLootTables.SHIPWRECK_TREASURE, new Object[] {5, SHIP});
		LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> {
			ResourceKey<LootTable> key = table.unwrapKey().orElse(null);
			Object[] at = key == null ? null : where.get(key);
			if (at != null && context.getRandom().nextFloat() < (float)at[1]) {
				drops.add(new ItemStack(SHARDS[(int)at[0]]));
			}
		});
	}

	// ---- what they give ----------------------------------------------------------------------------------------------

	/** Whether {@code player} has gift {@code i}: its shard carried, or the crown worn. */
	private static boolean has(ServerPlayer player, int i, boolean crown) {
		if (crown) {
			return true;
		}
		for (ItemStack s : player.getInventory().getNonEquipmentItems()) {
			if (s.is(SHARDS[i])) {
				return true;
			}
		}
		return player.getOffhandItem().is(SHARDS[i]);
	}

	/** Gives {@code player} the gifts of the shards they carry (all six if they wear the crown). */
	private static void gifts(ServerPlayer player) {
		boolean crown = player.getItemBySlot(EquipmentSlot.HEAD).is(CROWN);
		modifier(player, Attributes.SAFE_FALL_DISTANCE, FALL_ID, SAFE_FALL, has(player, 0, crown));
		if (has(player, 1, crown)) {
			pull(player);
		}
		if (has(player, 2, crown) && player.getTicksFrozen() > 0) {
			player.setTicksFrozen(0);
		}
		if (has(player, 3, crown)) {
			player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, NIGHT_TICKS, 0, true, false, false));
		}
		if (has(player, 4, crown)) {
			player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, BREATH_TICKS, 0, true, false, false));
		}
		modifier(player, Attributes.ATTACK_SPEED, SWING_ID, ATTACK_SPEED, has(player, 5, crown));
	}

	private static void modifier(ServerPlayer player, Holder<Attribute> attribute, Identifier id, double amount, boolean on) {
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance == null) {
			return;
		}
		if (on && !instance.hasModifier(id)) {
			instance.addTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE));
		} else if (!on && instance.hasModifier(id)) {
			instance.removeModifier(id);
		}
	}

	/** Metal lying about within {@link #PULL_RANGE} drifts to you, as to a lodestone. */
	private static void pull(ServerPlayer player) {
		Vec3 to = player.position().add(0.0, 0.5, 0.0);
		for (ItemEntity item : player.level().getEntitiesOfClass(ItemEntity.class, new AABB(player.blockPosition()).inflate(PULL_RANGE),
			e -> e.isAlive() && metal(e.getItem()))) {
			Vec3 way = to.subtract(item.position());
			if (way.lengthSqr() > 0.6) {
				item.setDeltaMovement(item.getDeltaMovement().scale(0.5).add(way.normalize().scale(PULL_SPEED)));
				item.syncVelocity = true;
			}
		}
	}

	private static boolean metal(ItemStack stack) {
		String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
		for (String word : METALS) {
			if (path.contains(word)) {
				return true;
			}
		}
		return false;
	}
}
