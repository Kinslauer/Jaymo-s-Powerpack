package com.jaymoskingdoms;

import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectionContext;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The gone-before (Andy's picks fa_remains to fa_far, the medieval deco patch 3). Folk went out to take the wild back and most
 * never came home: their bones lie where they fell, one kind for each place ({@link #KINDS}: frozen in snow, tangled in jungle,
 * drowned in a river, pinned under dripstone, at a cliff's foot, sun-dried, webbed in a cave, under the sky islands' edges, in
 * the soul sand valleys and on the End's island), seeded by {@link RemainsFeature} and, sealed in rock, {@link LastCamp}.
 * <ul>
 * <li><b>Search</b>: break one with an empty hand and what they carried ({@code loot_table/remains/<kind>}, worn or broken, and
 * pieces of the pack's tinkering where Powers is loaded) is yours. About one in {@link #TRAP_IN} is a trap
 * ({@link #spring}); at night one may rise ({@link #rise}); about one in {@link #PAGE_IN} holds a written scrap that points at a
 * real place nearby ({@link #page}, filed in the Lore Codex); about one in {@link #TOKEN_IN} wears a token of its home village
 * ({@link #token}). What each one holds follows from where it lies, so the block saves nothing.</li>
 * <li><b>Rites</b>: a shovel buries them under a grave mound (the token and the scrap come out, nothing else), and so does the
 * rite of keeping ({@link DeathRites}). Carry a token to its village and show it to one of its people: their kin thank you.
 * Leaving the dead alone costs nothing.</li>
 * </ul>
 */
public final class Remains {
	// ---- tuning ----
	/** The kinds, in the order of the block's kind property. */
	public static final String[] KINDS = {"snow", "jungle", "river", "dripstone", "cliff", "desert", "cave", "sky", "nether", "end"};
	public static final int SNOW = 0, JUNGLE = 1, RIVER = 2, DRIPSTONE = 3, CLIFF = 4, DESERT = 5, CAVE = 6, SKY = 7, NETHER = 8, END = 9;
	/** One in this many lies over a trap, holds a scrap, wears a token, and (at night) rises when searched. */
	static final int TRAP_IN = 8, PAGE_IN = 5, TOKEN_IN = 4, RISE_IN = 3;
	/** A piece of gear is worn down to this share of its life at the most (and sometimes broken outright). */
	static final float WEAR_LEAST = 0.55F;
	/** A tinker part's chance among a lay's finds. */
	static final float PARTS_CHANCE = 0.3F;
	/** How far (chunks) a scrap's place is looked for among the structures already made, and how near it may not be (blocks). */
	static final int PLACE_CHUNKS = 7, PLACE_MIN = 24;
	/** Distances (blocks) a scrap calls near and far. */
	static final int NEAR = 70, FAR = 170;
	/** A shovel's wear for a burial, the experience for it, and how far a rite of keeping reaches. */
	static final int SHOVEL_WEAR = 2, BURY_XP = 4, RITE_REACH = 5;
	/** A token shown within this far of its village's seat is taken home; what the kin give, and the standing. */
	static final int HOME_REACH = 110, GIFT_MIN = 4, GIFT_MAX = 9, STANDING = 40, GIFT_XP = 12;
	/** The villages a token may name: this many of the nearest. */
	static final int HOMES = 4;

	public static Block BLOCK;
	public static Block WEAK_FLOOR;
	public static Item ITEM, WEAK_FLOOR_ITEM, RING, SPOON;
	private static final String HOME = "remains_home";

	private Remains() {
	}

	static void register() {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosKingdoms.id("remains"));
		BLOCK = Registry.register(BuiltInRegistries.BLOCK, key, new RemainsBlock(BlockBehaviour.Properties.of().mapColor(MapColor.SAND).strength(0.5F)
			.sound(SoundType.BONE_BLOCK).noOcclusion().noCollision().setId(key)));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("remains"));
		ITEM = Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(BLOCK, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
		ResourceKey<Block> floorKey = ResourceKey.create(Registries.BLOCK, JaymosKingdoms.id("weak_floor"));
		WEAK_FLOOR = Registry.register(BuiltInRegistries.BLOCK, floorKey, new WeakFloorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(0.8F)
			.sound(SoundType.WOOD).setId(floorKey)));
		ResourceKey<Item> floorItemKey = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("weak_floor"));
		WEAK_FLOOR_ITEM = Registry.register(BuiltInRegistries.ITEM, floorItemKey, new BlockItem(WEAK_FLOOR, new Item.Properties().setId(floorItemKey).useBlockDescriptionPrefix()));
		RING = token("remains_ring");
		SPOON = token("remains_spoon");
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.NATURAL_BLOCKS).register(output -> {
			output.accept(ITEM);
			output.accept(WEAK_FLOOR_ITEM);
		});
		Registry.register(BuiltInRegistries.FEATURE_TYPE, JaymosKingdoms.id("remains"), RemainsFeature.CODEC);
		Registry.register(BuiltInRegistries.FEATURE_TYPE, JaymosKingdoms.id("last_camp"), LastCamp.CODEC);
		addFeatures();
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, entity) -> {
			if (level instanceof ServerLevel server && player instanceof ServerPlayer sp && state.is(BLOCK) && sp.getMainHandItem().isEmpty()) {
				search(server, sp, pos, state, true);
			}
		});
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp) || !player.getItemInHand(hand).is(ItemTags.SHOVELS)
				|| !level.getBlockState(hit.getBlockPos()).is(BLOCK)) {
				return InteractionResult.PASS;
			}
			bury(server, sp, hit.getBlockPos());
			player.getItemInHand(hand).hurtAndBreak(SHOVEL_WEAR, player, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
			return InteractionResult.SUCCESS;
		});
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			ItemStack held = player.getItemInHand(hand);
			if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp) || !(held.is(RING) || held.is(SPOON))
				|| !(entity instanceof Villager || entity instanceof Folk)) {
				return InteractionResult.PASS;
			}
			return bringHome(server, sp, held, entity) ? InteractionResult.SUCCESS : InteractionResult.PASS;
		});
		RuinTraps.register();
		HeadlessRider.register();
	}

	private static Item token(String name) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key).stacksTo(1)));
	}

	// ---- where they lie ------------------------------------------------------------------------------------------------------

	private static void addFeatures() {
		Predicate<BiomeSelectionContext> overworld = BiomeSelectors.foundInOverworld();
		Predicate<BiomeSelectionContext> dry = overworld.and(BiomeSelectors.tag(BiomeTags.IS_OCEAN).negate()).and(BiomeSelectors.tag(BiomeTags.IS_DEEP_OCEAN).negate());
		add(SNOW, BiomeSelectors.includeByKey(Biomes.SNOWY_PLAINS, Biomes.SNOWY_TAIGA, Biomes.SNOWY_SLOPES, Biomes.GROVE, Biomes.ICE_SPIKES, Biomes.FROZEN_PEAKS,
			Biomes.SNOWY_BEACH), GenerationStep.Decoration.VEGETAL_DECORATION);
		add(JUNGLE, BiomeSelectors.includeByKey(Biomes.JUNGLE, Biomes.SPARSE_JUNGLE, Biomes.BAMBOO_JUNGLE), GenerationStep.Decoration.VEGETAL_DECORATION);
		add(RIVER, BiomeSelectors.includeByKey(Biomes.RIVER, Biomes.FROZEN_RIVER), GenerationStep.Decoration.VEGETAL_DECORATION);
		add(DRIPSTONE, BiomeSelectors.includeByKey(Biomes.DRIPSTONE_CAVES), GenerationStep.Decoration.UNDERGROUND_DECORATION);
		add(CLIFF, BiomeSelectors.tag(BiomeTags.IS_MOUNTAIN).or(BiomeSelectors.tag(BiomeTags.IS_HILL)), GenerationStep.Decoration.VEGETAL_DECORATION);
		add(DESERT, BiomeSelectors.includeByKey(Biomes.DESERT, Biomes.BADLANDS, Biomes.ERODED_BADLANDS, Biomes.WOODED_BADLANDS), GenerationStep.Decoration.VEGETAL_DECORATION);
		add(CAVE, dry, GenerationStep.Decoration.UNDERGROUND_DECORATION);
		add(SKY, ctx -> ctx.getBiomeKey().identifier().getNamespace().equals("aether"), GenerationStep.Decoration.VEGETAL_DECORATION);
		add(NETHER, BiomeSelectors.includeByKey(Biomes.SOUL_SAND_VALLEY), GenerationStep.Decoration.UNDERGROUND_DECORATION);
		add(END, BiomeSelectors.includeByKey(Biomes.THE_END), GenerationStep.Decoration.VEGETAL_DECORATION);
		for (int t = 0; t < 2; t++) {
			BiomeModifications.addFeature(dry, GenerationStep.Decoration.UNDERGROUND_DECORATION,
				ResourceKey.create(Registries.PLACED_FEATURE, JaymosKingdoms.id("last_camp_" + t)));
		}
	}

	private static void add(int kind, Predicate<BiomeSelectionContext> biomes, GenerationStep.Decoration step) {
		BiomeModifications.addFeature(biomes, step, ResourceKey.create(Registries.PLACED_FEATURE, JaymosKingdoms.id("remains_" + KINDS[kind])));
	}

	// ---- searching -----------------------------------------------------------------------------------------------------------

	/** What lies at {@code pos} is worked out from where it is: the same bones always hold the same things. */
	private static RandomSource seed(ServerLevel level, BlockPos pos) {
		return RandomSource.create(level.getSeed() ^ (pos.asLong() * 0x9E3779B97F4A7C15L));
	}

	/**
	 * {@code player} searches the remains that lay at {@code pos}: with {@code gear} everything (its gear, and the trap and the
	 * risen dead), without it (a burial) only what a caring hand finds, the token and the scrap.
	 */
	static void search(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state, boolean gear) {
		int kind = state.getValue(RemainsBlock.KIND);
		RandomSource random = seed(level, pos);
		boolean trap = random.nextInt(TRAP_IN) == 0, page = random.nextInt(PAGE_IN) == 0, token = random.nextInt(TOKEN_IN) == 0;
		boolean rises = random.nextInt(RISE_IN) == 0;
		if (gear) {
			for (ItemStack stack : carried(level, pos, KINDS[kind], random)) {
				Block.popResource(level, pos, stack);
			}
		}
		if (token) {
			Block.popResource(level, pos, token(level, pos, random));
		}
		if (page) {
			Block.popResource(level, pos, page(level, pos, kind, random));
		}
		if (gear && trap) {
			spring(level, player, pos, random);
		} else if (gear && rises && level.isDarkOutside()) {
			rise(level, player, pos);
		}
	}

	/** What a lay of {@code kind} carried: its loot table's finds, worn or broken, and now and then a tinker's part. */
	private static List<ItemStack> carried(ServerLevel level, BlockPos pos, String kind, RandomSource random) {
		List<ItemStack> out = new ArrayList<>();
		LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos)).create(LootContextParamSets.CHEST);
		roll(level, "remains/" + kind, params, random, out);
		if (random.nextFloat() < PARTS_CHANCE) {
			roll(level, "remains/tinker_parts", params, random, out);
		}
		for (ItemStack stack : out) {
			if (stack.isDamageableItem()) {
				int max = stack.getMaxDamage();
				stack.setDamageValue(random.nextInt(3) == 0 ? max - 1 : Math.min(max - 1, Mth.floor(max * (WEAR_LEAST + random.nextFloat() * (1.0F - WEAR_LEAST)))));
			}
		}
		return out;
	}

	private static void roll(ServerLevel level, String table, LootParams params, RandomSource random, List<ItemStack> out) {
		LootTable loot = level.getServer().reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE, JaymosKingdoms.id(table)));
		out.addAll(loot.getRandomItems(params, random.nextLong()));
	}

	// ---- the surprise --------------------------------------------------------------------------------------------------------

	/** The trap under the bones: an arrow from the ground, gravel from overhead, or a spiders' nest in the ribs. */
	private static void spring(ServerLevel level, ServerPlayer player, BlockPos pos, RandomSource random) {
		switch (random.nextInt(3)) {
			case 0 -> {
				Arrow arrow = new Arrow(level, pos.getX() + 0.5, pos.getY() - 0.4, pos.getZ() + 0.5, new ItemStack(Items.ARROW), null);
				Vec3 to = player.getEyePosition().subtract(arrow.position());
				arrow.shoot(to.x, to.y, to.z, 1.6F, 1.0F);
				arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
				level.addFreshEntity(arrow);
				level.playSound(null, pos, SoundEvents.DISPENSER_LAUNCH, SoundSource.BLOCKS, 1.0F, 1.2F);
			}
			case 1 -> {
				BlockPos top = player.blockPosition().above(4);
				for (int i = 0; i < 3; i++) {
					BlockPos at = top.offset(i == 1 ? 1 : 0, i == 2 ? 1 : 0, i == 2 ? 1 : 0);
					if (level.getBlockState(at).isAir()) {
						FallingBlockEntity.fall(level, at, Blocks.GRAVEL.defaultBlockState());
					}
				}
				level.playSound(null, pos, SoundEvents.GRAVEL_BREAK, SoundSource.BLOCKS, 1.0F, 0.5F);
			}
			default -> {
				for (int i = 0; i < 3; i++) {
					Mob spider = EntityTypes.CAVE_SPIDER.create(level, EntitySpawnReason.EVENT);
					if (spider != null) {
						spider.snapTo(pos.getX() + 0.5 + random.nextInt(3) - 1, pos.getY() + 0.2, pos.getZ() + 0.5 + random.nextInt(3) - 1, random.nextFloat() * 360.0F, 0.0F);
						spider.setTarget(player);
						level.addFreshEntity(spider);
					}
				}
				if (level.getBlockState(pos).isAir()) {
					level.setBlockAndUpdate(pos, Blocks.COBWEB.defaultBlockState());
				}
				level.playSound(null, pos, SoundEvents.SPIDER_AMBIENT, SoundSource.HOSTILE, 1.0F, 0.7F);
			}
		}
	}

	/** At night one of the old dead gets up out of the bones (a barrow's knight, or a plain skeleton without the barrows). */
	private static void rise(ServerLevel level, ServerPlayer player, BlockPos pos) {
		Mob dead = com.jaymoskingdoms.beasts.Beasts.ZOMBIE_KNIGHT != null ? com.jaymoskingdoms.beasts.Beasts.ZOMBIE_KNIGHT.create(level, EntitySpawnReason.EVENT)
			: EntityTypes.SKELETON.create(level, EntitySpawnReason.EVENT);
		if (dead != null) {
			dead.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
			dead.setTarget(player);
			level.addFreshEntity(dead);
			level.playSound(null, pos, SoundEvents.SKELETON_AMBIENT, SoundSource.HOSTILE, 1.0F, 0.6F);
		}
	}

	// ---- the scrap -----------------------------------------------------------------------------------------------------------

	/** What kind of place a structure is, as a scrap would name it (or null: not one a scrap points at). */
	private static @Nullable String placeOf(String path) {
		if (path.contains("mineshaft")) {
			return "cave";
		}
		if (path.contains("monolith")) {
			return "monolith";
		}
		if (path.contains("buried") || path.contains("crypt") || path.contains("tomb") || path.contains("vault")) {
			return "room";
		}
		if (path.contains("ruin") || path.contains("tower") || path.contains("fort") || path.contains("keep") || path.contains("castle") || path.contains("portal")) {
			return "ruin";
		}
		return null;
	}

	/** A written scrap in the dead one's voice that points at a real place near them, filed in the Lore Codex; empty if there is none to point at. */
	static ItemStack page(ServerLevel level, BlockPos pos, int kind, RandomSource random) {
		Registry<Structure> registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
		BlockPos best = null;
		String bestPlace = null;
		double bestDistance = Double.MAX_VALUE;
		ChunkPos middle = ChunkPos.containing(pos);
		for (int dx = -PLACE_CHUNKS; dx <= PLACE_CHUNKS; dx++) {
			for (int dz = -PLACE_CHUNKS; dz <= PLACE_CHUNKS; dz++) {
				ChunkAccess chunk = level.getChunkSource().getChunkNow(middle.x() + dx, middle.z() + dz);
				if (chunk == null) {
					continue;
				}
				for (Map.Entry<Structure, LongSet> e : chunk.getAllReferences().entrySet()) {
					Identifier id = registry.getKey(e.getKey());
					String place = id == null ? null : placeOf(id.getPath());
					if (place == null) {
						continue;
					}
					for (long ref : e.getValue()) {
						ChunkPos from = ChunkPos.unpack(ref);
						ChunkAccess startChunk = level.getChunkSource().getChunkNow(from.x(), from.z());
						StructureStart start = startChunk == null ? null : startChunk.getStartForStructure(e.getKey());
						if (start == null || !start.isValid()) {
							continue;
						}
						BlockPos centre = start.getBoundingBox().getCenter();
						double distance = Math.sqrt(centre.distSqr(pos));
						if (distance >= PLACE_MIN && distance < bestDistance) {
							best = centre;
							bestPlace = place;
							bestDistance = distance;
						}
					}
				}
			}
		}
		if (best == null) {
			return ItemStack.EMPTY;
		}
		String far = bestDistance < NEAR ? "near" : bestDistance < FAR ? "mid" : "far";
		Component text = Component.translatable("remains.page." + KINDS[kind] + "." + random.nextInt(3), Component.translatable("remains.place." + bestPlace),
			Component.translatable("remains.dir." + direction(pos, best)), Component.translatable("remains.far." + far));
		ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
		book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough("A Last Page"), "", 0, List.of(Filterable.passThrough(text)), true));
		book.set(DataComponents.CUSTOM_NAME, Component.translatable("item.jaymoskingdoms.remains_page"));
		LoreBooks.tag(book, "remains:" + Long.toHexString(pos.asLong()));
		return book;
	}

	private static String direction(BlockPos from, BlockPos to) {
		double angle = Math.toDegrees(Math.atan2(to.getZ() - from.getZ(), to.getX() - from.getX()));
		String[] names = {"e", "se", "s", "sw", "w", "nw", "n", "ne"};
		return names[Math.floorMod((int)Math.round(angle / 45.0), 8)];
	}

	// ---- the token -----------------------------------------------------------------------------------------------------------

	/**
	 * A token of a dead one's home: a ring or a carved spoon that names one of the {@link #HOMES} villages nearest {@code near}
	 * (chosen with {@code random}). Other code gives these too (a fishing catch's debris); empty if there is no village at all.
	 */
	public static ItemStack token(ServerLevel level, BlockPos near, RandomSource random) {
		List<Faction> villages = new ArrayList<>();
		for (Faction f : Factions.all(level.getServer(), FactionType.VILLAGE)) {
			if (!f.fallen()) {
				villages.add(f);
			}
		}
		if (villages.isEmpty()) {
			return ItemStack.EMPTY;
		}
		villages.sort(Comparator.comparingDouble(f -> f.seat().distSqr(near)));
		Faction home = villages.get(random.nextInt(Math.min(HOMES, villages.size())));
		ItemStack stack = new ItemStack(random.nextBoolean() ? RING : SPOON);
		CompoundTag tag = new CompoundTag();
		tag.putInt(HOME, home.id());
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		stack.set(DataComponents.CUSTOM_NAME, Component.translatable(stack.is(RING) ? "item.jaymoskingdoms.remains_ring" : "item.jaymoskingdoms.remains_spoon"));
		stack.set(DataComponents.LORE, new ItemLore(List.of(Component.translatable("item.jaymoskingdoms.remains_token.lore", home.name()).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC))));
		return stack;
	}

	/** Shows a token to one of its village's people: their kin thank you with a gift and a little standing; false if it is not theirs. */
	private static boolean bringHome(ServerLevel level, ServerPlayer player, ItemStack held, Entity person) {
		CustomData data = held.get(DataComponents.CUSTOM_DATA);
		int id = data == null ? -1 : data.copyTag().getIntOr(HOME, -1);
		Faction home = id < 0 ? null : Factions.get(level.getServer(), id);
		if (home == null || home.fallen() || person.blockPosition().distSqr(home.seat()) > (double)HOME_REACH * HOME_REACH) {
			return false;
		}
		held.shrink(1);
		RandomSource random = level.getRandom();
		player.getInventory().placeItemBackInInventory(new ItemStack(Items.EMERALD, GIFT_MIN + random.nextInt(GIFT_MAX - GIFT_MIN + 1)), net.minecraft.util.Prediction.SERVER_ONLY);
		if (random.nextInt(3) == 0) {
			player.getInventory().placeItemBackInInventory(new ItemStack(Items.GOLDEN_CARROT, 2), net.minecraft.util.Prediction.SERVER_ONLY);
		}
		player.giveExperiencePoints(GIFT_XP);
		Reputation.change(player, home, STANDING);
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.remains.thanks", home.name()).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
		level.playSound(null, person.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1.0F, 1.0F);
		return true;
	}

	// ---- the rites -----------------------------------------------------------------------------------------------------------

	/** Lays the remains at {@code pos} to rest under a grave mound; what a caring hand finds (the token, the scrap) comes out. */
	static void bury(ServerLevel level, ServerPlayer player, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (!state.is(BLOCK)) {
			return;
		}
		search(level, player, pos, state, false);
		level.setBlock(pos, Graves.GRAVE.defaultBlockState(), Block.UPDATE_ALL);
		level.playSound(null, pos, SoundEvents.GRAVEL_BREAK, SoundSource.BLOCKS, 1.0F, 0.7F);
		player.giveExperiencePoints(BURY_XP);
	}

	/** The rite of keeping, said over {@code player}'s feet: every remains within reach is laid to rest. False if there were none. */
	static boolean rite(ServerLevel level, ServerPlayer player) {
		boolean any = false;
		for (BlockPos pos : BlockPos.betweenClosed(player.blockPosition().offset(-RITE_REACH, -2, -RITE_REACH), player.blockPosition().offset(RITE_REACH, 2, RITE_REACH))) {
			if (level.getBlockState(pos).is(BLOCK)) {
				bury(level, player, pos.immutable());
				any = true;
			}
		}
		return any;
	}
}
