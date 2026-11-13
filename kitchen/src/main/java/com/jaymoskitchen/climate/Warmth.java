package com.jaymoskitchen.climate;

import com.jaymoskitchen.JaymosKitchen;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Warmth you build (Andy's TFC Hardrock picks, patch 1; ideas after Cold Sweat, GPL-3.0, written afresh): the hearth
 * and the icebox and the rooms they fill ({@link HearthBlock}), linings sewn into armour at a loom ({@link Lining}), the
 * thermometer (a held item; the client shows the air's heat while you hold it), warm and cold drinks from a waterskin,
 * and the land growing colder the further north you go. {@link BodyHeat} reads all of it.
 */
public final class Warmth {
	/** What a warmed room does to the air's push, in body heat: the hearth lifts cold air toward comfortable, the icebox
	 *  pulls hot air down toward it. Neither goes past comfortable. */
	static final float HEARTH = 45.0F, ICEBOX = 40.0F;
	/** Fuel in ticks. Hearth: coal or charcoal, a coal block, a log, planks, a stick. Icebox: ice, packed ice, blue ice, a
	 *  snow block, a snowball. */
	static final int COAL = 4800, COAL_BLOCK = 24000, LOG = 2400, PLANKS = 600, STICK = 150;
	static final int ICE = 2400, PACKED_ICE = 9600, BLUE_ICE = 24000, SNOW_BLOCK = 1200, SNOWBALL = 300;
	/** A warm or cold drink from a waterskin: how long it lasts and how hard it pushes. */
	static final int DRINK_TICKS = 2 * 60 * 20;
	static final float HOT_DRINK = 12.0F, COLD_DRINK = 12.0F;
	/** How long water stays hot off the fire, or cold out of the snow, in the waterskin. */
	static final int SKIN_KEEPS = 5 * 60 * 20;
	/** The land's cold per thousand blocks north of the world's spawn (warmth to the south), and its most. */
	static final float NORTH_COLD = 5.0F, NORTH_MOST = 25.0F;
	/** Damp air near the sea: how much colder it makes cold air feel and hotter hot air, at the most, and how far (blocks) round you it is read. */
	static final float DAMP_COLD = 8.0F, DAMP_HEAT = 6.0F;
	static final int DAMP_REACH = 24;

	public static HearthBlock HEARTH_BLOCK;
	public static HearthBlock ICEBOX_BLOCK;
	public static BlockEntityType<HearthEntity> HEARTH_ENTITY;
	public static Item THERMOMETER;
	public static DataComponentType<Lining> LINING;

	/** The rooms being warmed or cooled, by level, then by the hearth's position. */
	private static final Map<ResourceKey<Level>, Map<Long, Room>> ROOMS = new HashMap<>();
	/** A warm (positive) or cold (negative) drink, and when it wears off. */
	private static final Map<UUID, long[]> DRINKS = new HashMap<>();

	record Room(boolean cold, LongOpenHashSet cells) {
	}

	private Warmth() {
	}

	static void register() {
		LINING = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosKitchen.id("lining"),
			DataComponentType.<Lining>builder().persistent(Lining.CODEC).networkSynchronized(Lining.STREAM_CODEC).build());
		HEARTH_BLOCK = block("hearth", p -> new HearthBlock(false, p), BlockBehaviour.Properties.of()
			.mapColor(MapColor.STONE).strength(3.0F, 6.0F).requiresCorrectToolForDrops().sound(SoundType.STONE)
			.lightLevel(state -> state.getValue(HearthBlock.LIT) ? 13 : 0));
		ICEBOX_BLOCK = block("icebox", p -> new HearthBlock(true, p), BlockBehaviour.Properties.of()
			.mapColor(MapColor.WOOD).strength(2.5F).sound(SoundType.WOOD).ignitedByLava());
		Item hearth = blockItem("hearth", HEARTH_BLOCK);
		Item icebox = blockItem("icebox", ICEBOX_BLOCK);
		HEARTH_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, JaymosKitchen.id("hearth"),
			new BlockEntityType<>(HearthEntity::new, Set.of(HEARTH_BLOCK, ICEBOX_BLOCK)));
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKitchen.id("thermometer"));
		THERMOMETER = Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().stacksTo(1).setId(key)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> {
			output.accept(hearth);
			output.accept(icebox);
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> output.accept(THERMOMETER));
		// a world closed in single player mustn't leave its rooms or drinks behind for the next one opened
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			ROOMS.clear();
			DRINKS.clear();
		});

		ServerPlayNetworking.registerGlobalReceiver(com.jaymoskitchen.net.Payloads.FeedHearth.TYPE, (payload, ctx) -> HearthBlock.feed(ctx.player(), payload.pos()));
		Furs.register();
		Tailoring.register();
		// a lining: the piece in your main hand, the wool, rabbit hide or leather in your other, used on a loom
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || player.isSpectator() || !level.getBlockState(hit.getBlockPos()).is(Blocks.LOOM)) {
				return InteractionResult.PASS;
			}
			ItemStack piece = player.getMainHandItem();
			ItemStack cloth = player.getOffhandItem();
			Lining lining = Lining.of(cloth);
			if (lining == null || lining.tailored || !linable(piece) || piece.get(LINING) == lining) {
				return InteractionResult.PASS;
			}
			if (!level.isClientSide()) {
				piece.set(LINING, lining);
				if (!player.getAbilities().instabuild) {
					cloth.shrink(1);
				}
				level.playSound(null, hit.getBlockPos(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.BLOCKS, 0.8F, 1.1F);
			}
			return InteractionResult.SUCCESS;
		});
	}

	/** Worn on the head, body, legs or feet. */
	public static boolean linable(ItemStack stack) {
		Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
		if (equippable == null) {
			return false;
		}
		EquipmentSlot slot = equippable.slot();
		return slot == EquipmentSlot.HEAD || slot == EquipmentSlot.CHEST || slot == EquipmentSlot.LEGS || slot == EquipmentSlot.FEET;
	}

	/** How long {@code stack} keeps a hearth (or, {@code cold}, an icebox) going; 0 if it doesn't burn there. */
	static int burnTicks(ItemStack stack, boolean cold) {
		if (cold) {
			if (stack.is(Items.BLUE_ICE)) return BLUE_ICE;
			if (stack.is(Items.PACKED_ICE)) return PACKED_ICE;
			if (stack.is(Items.ICE)) return ICE;
			if (stack.is(Items.SNOW_BLOCK)) return SNOW_BLOCK;
			if (stack.is(Items.SNOWBALL)) return SNOWBALL;
			return 0;
		}
		if (stack.is(Items.COAL_BLOCK)) return COAL_BLOCK;
		if (stack.is(ItemTags.COALS)) return COAL;
		if (stack.is(ItemTags.LOGS_THAT_BURN)) return LOG;
		if (stack.is(ItemTags.PLANKS)) return PLANKS;
		if (stack.is(Items.STICK)) return STICK;
		return 0;
	}

	// ---- rooms ----

	static void set(ServerLevel level, BlockPos hearth, boolean cold, LongOpenHashSet cells) {
		ROOMS.computeIfAbsent(level.dimension(), k -> new HashMap<>()).put(hearth.asLong(), new Room(cold, cells));
	}

	/** How much air the hearth at {@code hearth} fills. */
	static int cells(ServerLevel level, BlockPos hearth) {
		Map<Long, Room> rooms = ROOMS.get(level.dimension());
		Room room = rooms == null ? null : rooms.get(hearth.asLong());
		return room == null ? 0 : room.cells().size();
	}

	static boolean has(ServerLevel level, BlockPos hearth) {
		Map<Long, Room> rooms = ROOMS.get(level.dimension());
		return rooms != null && rooms.containsKey(hearth.asLong());
	}

	static void clear(ServerLevel level, BlockPos hearth) {
		Map<Long, Room> rooms = ROOMS.get(level.dimension());
		if (rooms != null) {
			rooms.remove(hearth.asLong());
		}
	}

	/** {@code air} (the world's push on {@code player}, in body heat) as a warmed or cooled room they stand in has it. */
	static float room(ServerPlayer player, float air) {
		Map<Long, Room> rooms = ROOMS.get(player.level().dimension());
		if (rooms == null || rooms.isEmpty()) {
			return air;
		}
		long feet = player.blockPosition().asLong();
		long head = BlockPos.containing(player.getEyePosition()).asLong();
		boolean warm = false, cool = false;
		for (Room room : rooms.values()) {
			if (room.cells.contains(feet) || room.cells.contains(head)) {
				if (room.cold) {
					cool = true;
				} else {
					warm = true;
				}
			}
		}
		if (warm && air < 0.0F) {
			air = Math.min(0.0F, air + HEARTH);
		}
		if (cool && air > 0.0F) {
			air = Math.max(0.0F, air - ICEBOX);
		}
		return air;
	}

	// ---- drinks ----

	/** A warm ({@code hot}) or cold drink, felt for {@link #DRINK_TICKS}. */
	static void drink(ServerPlayer player, boolean hot) {
		DRINKS.put(player.getUUID(), new long[] {hot ? 1 : -1, player.level().getGameTime() + DRINK_TICKS});
	}

	/** What a recent warm or cold drink adds to the air's push. */
	static float drinkPush(ServerPlayer player) {
		long[] drink = DRINKS.get(player.getUUID());
		if (drink == null) {
			return 0.0F;
		}
		if (drink[1] < player.level().getGameTime()) {
			DRINKS.remove(player.getUUID());
			return 0.0F;
		}
		return drink[0] > 0 ? HOT_DRINK : -COLD_DRINK;
	}

	static void forget(UUID id) {
		DRINKS.remove(id);
	}

	// ---- the land ----

	/** The overworld's cold to the north and warmth to the south of the world's spawn, in body heat. */
	static float north(ServerLevel level, double z) {
		int spawnZ = level.getServer().overworld().getRespawnData().pos().getZ();
		// north is toward -Z
		return Math.max(-NORTH_MOST, Math.min(NORTH_MOST, (float) (z - spawnZ) / 1000.0F * NORTH_COLD));
	}

	/** How damp the air is (0 to 1): the share of the land round {@code at} that is sea, shore or river. */
	static float dampness(ServerLevel level, BlockPos at) {
		int wet = 0, n = 0;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				BlockPos p = at.offset(dx * DAMP_REACH, 0, dz * DAMP_REACH);
				if (!level.hasChunkAt(p)) {
					continue;
				}
				n++;
				var biome = level.getBiome(p);
				if (biome.is(net.minecraft.tags.BiomeTags.IS_OCEAN) || biome.is(net.minecraft.tags.BiomeTags.IS_BEACH)
					|| biome.is(net.minecraft.tags.BiomeTags.IS_RIVER)) {
					wet += dx == 0 && dz == 0 ? 2 : 1;
				}
			}
		}
		return n == 0 ? 0.0F : Math.min(1.0F, wet / (float) (n + 1));
	}

	/**
	 * Damp's push on the air, in body heat: near the sea, on the shore and in a river's valley the air is wet, and wet air
	 * takes the heat out of cold ({@link #DAMP_COLD}, "a damp cold gets into the bones") and clings to hot ({@link #DAMP_HEAT},
	 * "muggy"); only under the open sky, and half again as much in rain. {@code land} is the dry land's push.
	 */
	static float damp(ServerLevel level, BlockPos at, boolean sky, float land) {
		if (!sky) {
			return 0.0F;
		}
		float damp = dampness(level, at);
		if (damp <= 0.0F) {
			return 0.0F;
		}
		if (level.isRaining()) {
			damp = Math.min(1.0F, damp * 1.5F);
		}
		return land > 0.0F ? DAMP_HEAT * damp : -DAMP_COLD * damp;
	}

	/** Worn linings: how many warm pieces and how many cool pieces they add up to. */
	static float lined(Player player, boolean warm) {
		float n = 0.0F;
		for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			Lining lining = player.getItemBySlot(slot).get(LINING);
			if (lining != null) {
				n += warm ? lining.warm : lining.cool;
			}
		}
		return n;
	}

	private static <B extends Block> B block(String name, java.util.function.Function<BlockBehaviour.Properties, B> factory,
		BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosKitchen.id(name));
		return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
	}

	private static Item blockItem(String name, Block block) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKitchen.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, new BlockItem(block, new Item.Properties().useBlockDescriptionPrefix().setId(key)));
	}
}
