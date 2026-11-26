package com.jaymoskitchen.climate;

import com.jaymoskitchen.JaymosKitchen;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.level.block.state.BlockState;
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
	/** A cup of the vintner's juice cools for a minute; ice cream cools harder for a few minutes. */
	static final int JUICE_TICKS = 60 * 20, ICE_CREAM_TICKS = 4 * 60 * 20;
	static final float ICE_CREAM_COOL = 16.0F;
	/** Hot food (the item tag {@code jaymoskitchen:hot_foods}) and meat fresh off the spit warm like a hot meal ({@link BodyHeat#HOT_MEAL}). */
	static final float HOT_FOOD = BodyHeat.HOT_MEAL;
	static final int HOT_FOOD_TICKS = BodyHeat.HOT_MEAL_TICKS;
	/** How long a piece torn off the spit stays hot enough to warm you, in ticks. */
	public static final int FRESH_TICKS = 3 * 60 * 20;
	/** The stamp is rounded to this many ticks (a minute), so pieces torn off one roast stack. */
	static final long FRESH_BUCKET = 60L * 20L;
	/** How long water stays hot off the fire, or cold out of the snow, in the waterskin. */
	static final int SKIN_KEEPS = 5 * 60 * 20;
	/** The land's cold per thousand blocks north of the world's spawn (warmth to the south), and its most. */
	static final float NORTH_COLD = 5.0F, NORTH_MOST = 25.0F;
	/** Damp air near the sea: how much colder it makes cold air feel and hotter hot air, at the most, and how far (blocks) round you it is read. */
	static final float DAMP_COLD = 8.0F, DAMP_HEAT = 6.0F;
	static final int DAMP_REACH = 24;
	/**
	 * Jaymo's Kingdoms is here, so the overworld's lands follow its climate map (cold poles to the north, the hot belt to
	 * the south): the lands' own warmth already grows colder northward, and {@link #north} isn't added on top.
	 */
	static final boolean CLIMATE_MAP = net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("jaymoskingdoms");

	/** The status icon of a newcomer's first-wake grace ({@link BodyHeat#CLEMENCY}). */
	public static net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> CLEMENCY_EFFECT;
	public static HearthBlock HEARTH_BLOCK;
	public static HearthBlock ICEBOX_BLOCK;
	public static BlockEntityType<HearthEntity> HEARTH_ENTITY;
	public static Item THERMOMETER;
	public static DataComponentType<Lining> LINING;
	/** On a piece of meat torn off the spit: the game time until which it is still hot ({@link #fresh}). */
	public static DataComponentType<Long> FRESH;
	public static HearthBlock REGULATOR_BLOCK;
	/** Food that is hot to eat (pies, roasts, hot drinks) and food that is cold ({@link #consumed}). */
	public static final net.minecraft.tags.TagKey<Item> HOT_FOODS = net.minecraft.tags.TagKey.create(Registries.ITEM, JaymosKitchen.id("hot_foods"));
	public static final net.minecraft.tags.TagKey<Item> COOL_FOODS = net.minecraft.tags.TagKey.create(Registries.ITEM, JaymosKitchen.id("cool_foods"));

	/** The rooms being warmed or cooled, by level, then by the hearth's position. */
	private static final Map<ResourceKey<Level>, Map<Long, Room>> ROOMS = new HashMap<>();
	/** A warm (positive) or cold (negative) drink, and when it wears off. */
	private static final Map<UUID, long[]> DRINKS = new HashMap<>();

	record Room(boolean cold, LongOpenHashSet cells) {
	}

	private Warmth() {
	}

	static void register() {
		CLEMENCY_EFFECT = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, JaymosKitchen.id("clemency"),
			new ClemencyEffect());
		LINING = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosKitchen.id("lining"),
			DataComponentType.<Lining>builder().persistent(Lining.CODEC).networkSynchronized(Lining.STREAM_CODEC).build());
		HEARTH_BLOCK = block("hearth", p -> new HearthBlock(false, p), BlockBehaviour.Properties.of()
			.mapColor(MapColor.STONE).strength(3.0F, 6.0F).requiresCorrectToolForDrops().sound(SoundType.STONE)
			.lightLevel(state -> state.getValue(HearthBlock.LIT) ? 13 : 0));
		ICEBOX_BLOCK = block("icebox", p -> new HearthBlock(true, p), BlockBehaviour.Properties.of()
			.mapColor(MapColor.WOOD).strength(2.5F).sound(SoundType.WOOD).ignitedByLava());
		Item hearth = blockItem("hearth", HEARTH_BLOCK);
		Item icebox = blockItem("icebox", ICEBOX_BLOCK);
		// the climate stove: one block that does both jobs, one way at a time ({@link RegulatorBlock})
		REGULATOR_BLOCK = block("regulator", p -> new RegulatorBlock(p), BlockBehaviour.Properties.of()
			.mapColor(MapColor.METAL).strength(3.5F, 6.0F).requiresCorrectToolForDrops().sound(SoundType.METAL)
			.lightLevel(state -> state.getValue(HearthBlock.LIT) && !state.getValue(RegulatorBlock.COLD) ? 11 : 0));
		Item regulator = blockItem("regulator", REGULATOR_BLOCK);
		HEARTH_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, JaymosKitchen.id("hearth"),
			new BlockEntityType<>(HearthEntity::new, Set.of(HEARTH_BLOCK, ICEBOX_BLOCK, REGULATOR_BLOCK)));
		FRESH = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosKitchen.id("fresh"),
			DataComponentType.<Long>builder().persistent(com.mojang.serialization.Codec.LONG)
				.networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.VAR_LONG).build());
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKitchen.id("thermometer"));
		THERMOMETER = Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().stacksTo(1).setId(key)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> {
			output.accept(hearth);
			output.accept(icebox);
			output.accept(regulator);
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

	/** How long {@code stack} keeps a climate stove cooling: ice, packed ice, blue ice or a snow block (not a snowball); else 0. */
	static int regulatorIce(ItemStack stack) {
		return stack.is(Items.SNOWBALL) ? 0 : burnTicks(stack, true);
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

	/** {@code air} (the push at a point, in body heat) as a warmed or cooled room that holds {@code pos} has it: what a gauge reads. */
	static float roomAt(ServerLevel level, BlockPos pos, float air) {
		Map<Long, Room> rooms = ROOMS.get(level.dimension());
		if (rooms == null || rooms.isEmpty()) {
			return air;
		}
		boolean warm = false, cool = false;
		for (Room room : rooms.values()) {
			if (room.cells.contains(pos.asLong())) {
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

	// ---- the room fill (the hearth's, and the fires') ----

	/**
	 * How far a walk of open air goes: {@code range} blocks across, {@code height} up and down, no further than
	 * {@code manhattan} blocks by steps, at most {@code cells} cells; {@code skyStops}: air that sees the sky is outside, and
	 * isn't entered.
	 */
	record Walk(int range, int height, int manhattan, int cells, boolean skyStops) {
	}

	/**
	 * Walks the air from {@code seeds} the way warmth spreads: through open air, open doors, gates and trapdoors, never
	 * through stone, closed doors or panes ({@link HearthEntity#open}). Every cell looked at, open or not, goes to
	 * {@code seen} with its state (a hot block is a cell that blocks the walk; the walk still sees it). The cells of open air
	 * are returned. {@code centre} is a block the walk
	 * starts beside and never enters (the hearth itself); with {@code seedsOpen} the seeds are taken as open air whatever they are
	 * (where a player stands). {@code seen} gets {@code at} as a shared mutable position: copy it to keep it.
	 */
	static LongOpenHashSet fill(ServerLevel level, BlockPos centre, java.util.List<BlockPos> seeds, boolean seedsOpen, Walk walk,
		java.util.function.@org.jspecify.annotations.Nullable BiConsumer<BlockPos, BlockState> seen) {
		LongOpenHashSet room = new LongOpenHashSet();
		LongOpenHashSet looked = new LongOpenHashSet();
		LongOpenHashSet forced = new LongOpenHashSet();
		it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue queue = new it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue();
		if (!seedsOpen) {
			looked.add(centre.asLong());
		}
		for (BlockPos seed : seeds) {
			if (looked.add(seed.asLong())) {
				queue.enqueue(seed.asLong());
			}
			if (seedsOpen) {
				forced.add(seed.asLong());
			}
		}
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		while (!queue.isEmpty() && room.size() < walk.cells()) {
			long packed = queue.dequeueLong();
			at.set(packed);
			if (at.distManhattan(centre) > walk.manhattan() || Math.abs(at.getX() - centre.getX()) > walk.range()
				|| Math.abs(at.getY() - centre.getY()) > walk.height() || Math.abs(at.getZ() - centre.getZ()) > walk.range()) {
				continue;
			}
			if (!com.jaymoskitchen.Loaded.made(level, at)) {
				continue;
			}
			BlockState state = level.getBlockState(at);
			if (seen != null) {
				seen.accept(at, state);
			}
			if (!forced.contains(packed) && (!HearthEntity.open(level, at, state) || walk.skyStops() && level.canSeeSky(at))) {
				continue;
			}
			room.add(packed);
			for (Direction d : Direction.values()) {
				long next = BlockPos.offset(packed, d);
				if (looked.add(next)) {
					queue.enqueue(next);
				}
			}
		}
		return room;
	}

	// ---- drinks ----

	/** A warm ({@code hot}) or cold drink, felt for {@link #DRINK_TICKS}. */
	static void drink(ServerPlayer player, boolean hot) {
		drink(player, hot, DRINK_TICKS, hot ? HOT_DRINK : COLD_DRINK);
	}

	/** A warm or cold bite or sip, felt for {@code ticks}, pushing the air {@code push} body heat the way it is warm or cold. */
	static void drink(ServerPlayer player, boolean hot, int ticks, float push) {
		DRINKS.put(player.getUUID(), new long[] {hot ? 1 : -1, player.level().getGameTime() + ticks, Math.round(push * 10.0F)});
	}

	/**
	 * Something eaten or drunk (the stack still whole): a cup of the vintner's juice cools you for a minute, ice cream (the
	 * item tag {@code jaymoskitchen:cool_foods}) for a few; a hot food (the tag {@code jaymoskitchen:hot_foods}: pies, roasts,
	 * hot drinks) or a bite still {@link #fresh} off the spit warms you as a hot meal does.
	 */
	public static void consumed(ServerPlayer player, ItemStack stack) {
		if (player.getAbilities().instabuild) {
			return;
		}
		if (com.jaymoskitchen.vintner.Juice.ofBottle(stack) != null) {
			drink(player, false, JUICE_TICKS, COLD_DRINK);
		} else if (stack.is(COOL_FOODS)) {
			drink(player, false, ICE_CREAM_TICKS, ICE_CREAM_COOL);
		} else if (stack.is(HOT_FOODS) || fresh(stack, player.level().getGameTime())) {
			drink(player, true, HOT_FOOD_TICKS, HOT_FOOD);
		}
	}

	/** Stamps {@code stack} as hot off the spit until {@link #FRESH_TICKS} from {@code now}; only food takes the mark. Returns the stack. */
	public static ItemStack stampFresh(ItemStack stack, long now) {
		if (stack.has(DataComponents.FOOD)) {
			// to the minute, so pieces from one roast stack
			stack.set(FRESH, (now + FRESH_TICKS) / FRESH_BUCKET * FRESH_BUCKET);
		}
		return stack;
	}

	/** Whether {@code stack} was torn off the spit and is still hot at game time {@code now}. */
	static boolean fresh(ItemStack stack, long now) {
		Long until = stack.get(FRESH);
		return until != null && until >= now;
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
		float push = drink.length > 2 ? drink[2] / 10.0F : COLD_DRINK;
		return drink[0] > 0 ? push : -push;
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

	/** Biomes whose air is wet like the shore's: swamps, bogs and marshes (a data tag, so other mods' wetlands can join). */
	public static final net.minecraft.tags.TagKey<net.minecraft.world.level.biome.Biome> DAMP_AIR =
		net.minecraft.tags.TagKey.create(Registries.BIOME, JaymosKitchen.id("damp_air"));
	/**
	 * (Biome feel tags) Lands whose own temperature gives the wrong feel say how they feel instead: the land's push in body
	 * heat, before the sun, the night and the height. Strongest first; a biome in none of them goes by its temperature.
	 */
	public static final float[] FEEL_PUSH = {55.0F, 35.0F, 15.0F, 0.0F, -12.0F, -28.0F, -50.0F};
	private static final net.minecraft.tags.TagKey<net.minecraft.world.level.biome.Biome>[] FEELS = feels(
		"feels_scorching", "feels_hot", "feels_warm", "feels_mild", "feels_cool", "feels_cold", "feels_frozen");

	@SuppressWarnings("unchecked")
	private static net.minecraft.tags.TagKey<net.minecraft.world.level.biome.Biome>[] feels(String... names) {
		net.minecraft.tags.TagKey<net.minecraft.world.level.biome.Biome>[] tags = new net.minecraft.tags.TagKey[names.length];
		for (int i = 0; i < names.length; i++) {
			tags[i] = net.minecraft.tags.TagKey.create(Registries.BIOME, JaymosKitchen.id(names[i]));
		}
		return tags;
	}

	/** How the land at {@code biome} feels when a feel tag says so, in body heat; null when its own temperature is right. */
	static @org.jspecify.annotations.Nullable Float feel(net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biome) {
		for (int i = 0; i < FEELS.length; i++) {
			if (biome.is(FEELS[i])) {
				return FEEL_PUSH[i];
			}
		}
		return null;
	}

	/** How damp the air is (0 to 1): the share of the land round {@code at} that is sea, shore, river, swamp or bog. */
	static float dampness(ServerLevel level, BlockPos at) {
		int wet = 0, n = 0;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				BlockPos p = at.offset(dx * DAMP_REACH, 0, dz * DAMP_REACH);
				if (!com.jaymoskitchen.Loaded.made(level, p)) {
					continue;
				}
				n++;
				var biome = level.getBiome(p);
				if (biome.is(net.minecraft.tags.BiomeTags.IS_OCEAN) || biome.is(net.minecraft.tags.BiomeTags.IS_BEACH)
					|| biome.is(net.minecraft.tags.BiomeTags.IS_RIVER) || biome.is(DAMP_AIR)) {
					wet += dx == 0 && dz == 0 ? 2 : 1;
				}
			}
		}
		return n == 0 ? 0.0F : Math.min(1.0F, wet / (float) (n + 1));
	}

	/**
	 * Damp's push on the air, in body heat: near the sea, on the shore and in a river's valley the air is wet, and wet air
	 * takes the heat out of cold ({@link #DAMP_COLD}, "a damp cold gets into the bones") and clings to hot ({@link #DAMP_HEAT},
	 * "muggy"); only under the open sky, and half again as much with rain or snow falling there. {@code land} is the dry
	 * land's push.
	 */
	static float damp(ServerLevel level, BlockPos at, boolean sky, boolean raining, float land) {
		if (!sky) {
			return 0.0F;
		}
		float damp = dampness(level, at);
		if (damp <= 0.0F) {
			return 0.0F;
		}
		if (raining) {
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
