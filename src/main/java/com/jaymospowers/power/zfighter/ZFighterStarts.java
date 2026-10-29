package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.wounds.MedicineItems;
import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.StructureTags;
import net.minecraft.util.Mth;
import net.minecraft.util.Prediction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.illager.Vindicator;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * (0.17.198) Andy: "add new starts, involving the other worlds". Jaymo's Kingdoms' life story ends with where you wake; these
 * six wake you off the overworld, and Kingdoms calls {@link #land} (by reflection) to put you there:
 * <ul>
 * <li><b>Stranded on the Pale Moon</b> ({@code moon}): your miners' pod came down hard, far from the moon's pad. The
 * {@link #WRECK} won't fly till it is mended with what the moon's ore gives (iron, copper, redstone, coal, in that order: the
 * torn pod log in your pack says as much); then it is a pod again.</li>
 * <li><b>Raised under the green sky</b> ({@code verdant}, Verdant-born only): you wake in a village of the Verdant World near
 * where the Lookout floats over it, with a compass the village keyed to the gate back ({@link ZFighterSites#verdantGate}).</li>
 * <li><b>Escaped the Iron-Master's mines</b> ({@code rustfall}): you break out at a mine mouth under Rustfall's weight, the
 * Iron-Master's overseers ({@link #HUNTED}) hunt you for a day, and the way home is the pod on their pad.</li>
 * <li><b>Salvager at the Maw</b> ({@code maw}): your crew is gone into the black hole; you wake on the Maw's pad by a working
 * pod, with deck plates to reach the wrecks.</li>
 * <li><b>Raised by a demon master</b> ({@code demon}): you leave a demon village's hall in the Nether's demon biomes, its army
 * leaving you be ({@link #WARD}) till you first leave the Nether, with an old portal close by.</li>
 * <li><b>Back from the afterlife</b> ({@code afterlife}): you wake among the dead in the Other World
 * ({@link ZFighterAfterlife#strand}), and go back by its own ways. (Kingdoms makes you Divine-born for it.)</li>
 * </ul>
 * Kingdoms keeps where you landed as your respawn till you first leave that world.
 */
public final class ZFighterStarts {
	// ---- tuning ----
	/** The moon: how far from the moon's middle (where its pad is looked for first) the wreck lies (blocks), and how many spots are tried. */
	static final int WRECK_MIN = 600, WRECK_MAX = 900, SPOT_TRIES = 24;
	/** The scorched ground round the wreck (radius, blocks) and the bits of hull strewn about. */
	static final int SCORCH_RADIUS = 4, DEBRIS = 6;
	/** What the wreck needs, step by step: the item (or {@link #FUEL} for any coal), and how many. At most 8 each. */
	static final Item[] REPAIR_ITEMS = {Items.IRON_INGOT, Items.COPPER_INGOT, Items.REDSTONE, Items.COAL};
	static final int[] REPAIR_COUNTS = {6, 4, 8, 4};
	static final String[] REPAIR_PARTS = {"hull", "wiring", "controls", "fuel"};
	static final int FUEL = 3;
	/** The moon start's torches. */
	static final int MOON_TORCHES = 16;
	/** The Verdant World: how far the village is looked for from where the Lookout floats over it (chunks), and how far from the village the gate is (blocks). */
	static final int VILLAGE_SEARCH = 100, VILLAGE_NEAR = 14, GATE_MIN = 150, GATE_MAX = 300;
	/** The Verdant start's seeds and bandages. */
	static final int VERDANT_SEEDS = 8, VERDANT_BANDAGES = 3;
	/** Rustfall: how far from the Iron-Master's pad the mine mouth is (blocks). */
	static final int MINE_MIN = 160, MINE_MAX = 260;
	/** The overseers' hunt: how long (ticks: a day), how many at once, how often another comes (ticks), how far off they come (blocks), and how near counts. */
	static final long HUNT_TICKS = 24000L;
	static final int HUNTERS = 2, HUNT_EVERY = 900, HUNT_MIN = 20, HUNT_MAX = 36;
	static final double HUNT_NEAR = 64.0;
	/** The overseers' health. */
	static final double OVERSEER_HEALTH = 20.0;
	/** The Maw: the salvager's deck plates. */
	static final int DECK_PLATES = 48;
	/** The demon start: how far the Gloam is looked for (blocks, and the steps), how many cells round it a village is looked for, and how far off the old portal stands (blocks). */
	static final int DEMON_SEARCH = 4096, DEMON_STEP = 32, DEMON_VERTICAL_STEP = 32, DEMON_CELLS = 3, DEMON_CELLS_FAR = 8, PORTAL_MIN = 14, PORTAL_MAX = 24;
	/** Spots tried for the portal. */
	static final int PORTAL_TRIES = 16;

	static final String OVERSEER_TAG = "jaymospowers_overseer";

	/** On a Rustfall escapee: the game time the overseers give up the hunt. Saved, kept through death; gone when they leave Rustfall. */
	public static final AttachmentType<Long> HUNTED = AttachmentRegistry.create(JaymosPowers.id("start_hunted"),
		builder -> builder.persistent(Codec.LONG).copyOnDeath());
	/** On a demon master's ward: the demon army leaves them be. Saved, kept through death; gone when they first leave the Nether. */
	public static final AttachmentType<Boolean> WARD = AttachmentRegistry.create(JaymosPowers.id("start_ward"),
		builder -> builder.persistent(Codec.BOOL).copyOnDeath());

	public static Block WRECK;

	/** When each hunted escapee's next overseer may come (game time). Not saved. */
	private static final Map<UUID, Long> NEXT_HUNTER = new HashMap<>();

	private ZFighterStarts() {
	}

	static void init() {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosPowers.id("space_pod_wreck"));
		WRECK = Registry.register(BuiltInRegistries.BLOCK, key, new WreckBlock(BlockBehaviour.Properties.of().strength(-1.0F, 3600000.0F)
			.noOcclusion().noLootTable().sound(SoundType.COPPER).setId(key)));
		ServerTickEvents.END_SERVER_TICK.register(ZFighterStarts::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> NEXT_HUNTER.clear());
		ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) -> {
			if (origin.dimension() == Level.NETHER) {
				player.removeAttached(WARD);
			}
			if (origin.dimension() == ZFighterSpace.DESTS[ZFighterSpace.RUSTFALL].level()) {
				player.removeAttached(HUNTED);
			}
		});
	}

	/** Whether {@code player} is a demon master's ward the army leaves be. */
	public static boolean ward(Player player) {
		return Boolean.TRUE.equals(player.getAttached(WARD));
	}

	// ---------------------------------------------------------------- landing

	/**
	 * Jaymo's Kingdoms (by reflection): puts {@code player} where life story start {@code start} wakes them, makes what it needs
	 * and hands them its own few things. {@code back} is a spot in the overworld for one who'll come back there (the afterlife's
	 * way home). False if the start's world isn't there or no place could be found (Kingdoms falls back to the wild).
	 */
	public static boolean land(ServerPlayer player, String start, BlockPos back) {
		MinecraftServer server = player.level().getServer();
		RandomSource random = player.getRandom();
		try {
			return switch (start.toLowerCase(Locale.ROOT)) {
				case "moon" -> moon(server, player, random);
				case "verdant" -> verdant(server, player, random);
				case "rustfall" -> rustfall(server, player, random);
				case "maw" -> maw(server, player);
				case "demon" -> demon(server, player, random);
				case "afterlife" -> ZFighterAfterlife.strand(player, server.overworld(), back);
				default -> false;
			};
		} catch (RuntimeException e) {
			JaymosPowers.LOGGER.warn("Couldn't land {} for the {} start", player.getGameProfile().name(), start, e);
			return false;
		}
	}

	private static void put(ServerPlayer player, ServerLevel level, BlockPos feet) {
		player.teleportTo(level, feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, Set.of(), player.getRandom().nextFloat() * 360.0F, 0.0F, true);
		player.resetFallDistance();
		player.setDeltaMovement(Vec3.ZERO);
	}

	private static void give(ServerPlayer player, ItemStack stack) {
		player.getInventory().placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
	}

	/** A compass that points at {@code at} in {@code level}, as a lodestone's does (no lodestone needed). */
	private static ItemStack compass(ResourceKey<Level> level, BlockPos at) {
		ItemStack stack = new ItemStack(Items.COMPASS);
		stack.set(DataComponents.LODESTONE_TRACKER, new LodestoneTracker(Optional.of(GlobalPos.of(level, at)), false));
		return stack;
	}

	/** Open ground at column (x, z): two clear blocks over a dry floor; null if not. */
	private static @Nullable BlockPos open(ServerLevel level, int x, int z) {
		level.getChunk(x >> 4, z >> 4);
		BlockPos feet = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
		BlockState floor = level.getBlockState(feet.below());
		if (feet.getY() <= level.getMinY() + 4 || !floor.getFluidState().isEmpty() || !floor.isFaceSturdy(level, feet.below(), Direction.UP)
			|| !level.getBlockState(feet).isAir() || !level.getBlockState(feet.above()).isAir()) {
			return null;
		}
		return feet;
	}

	/** Open ground {@code min}-{@code max} blocks from {@code centre}, flat enough for a pad if it can be; null if none. */
	private static @Nullable BlockPos spotNear(ServerLevel level, BlockPos centre, int min, int max, RandomSource random, boolean flat) {
		BlockPos rough = null;
		for (int i = 0; i < SPOT_TRIES; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			int d = min + random.nextInt(Math.max(1, max - min + 1));
			int x = centre.getX() + Mth.floor(Math.cos(angle) * d), z = centre.getZ() + Mth.floor(Math.sin(angle) * d);
			BlockPos feet = open(level, x, z);
			if (feet == null) {
				continue;
			}
			if (!flat || ZFighterSpace.surface(level, x, z) != null) {
				return feet;
			}
			if (rough == null) {
				rough = feet;
			}
		}
		return rough;
	}

	// ---------------------------------------------------------------- the Pale Moon

	private static boolean moon(MinecraftServer server, ServerPlayer player, RandomSource random) {
		ServerLevel moon = server.getLevel(ZFighterSpace.DESTS[ZFighterSpace.MOON].level());
		if (moon == null) {
			return false;
		}
		BlockPos spot = spotNear(moon, BlockPos.ZERO, WRECK_MIN, WRECK_MAX, random, true);
		if (spot == null) {
			return false;
		}
		crash(moon, spot, random);
		put(player, moon, spot.offset(2, 0, 1));
		give(player, new ItemStack(Items.STONE_PICKAXE));
		give(player, new ItemStack(Items.TORCH, MOON_TORCHES));
		give(player, podLog());
		return true;
	}

	/** The crash: a ring of scorched ground, the wreck in the middle, bits of hull strewn round it, and a little smoke. */
	private static void crash(ServerLevel level, BlockPos spot, RandomSource random) {
		for (int dx = -SCORCH_RADIUS; dx <= SCORCH_RADIUS; dx++) {
			for (int dz = -SCORCH_RADIUS; dz <= SCORCH_RADIUS; dz++) {
				if (dx * dx + dz * dz > SCORCH_RADIUS * SCORCH_RADIUS || random.nextInt(4) == 0) {
					continue;
				}
				BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spot.offset(dx, 0, dz)).below();
				if (Math.abs(ground.getY() - spot.getY() + 1) <= 2 && level.getBlockState(ground).isSolidRender()) {
					level.setBlock(ground, (random.nextInt(3) == 0 ? Blocks.BLACKSTONE : random.nextBoolean() ? Blocks.TUFF : Blocks.COBBLED_DEEPSLATE)
						.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
			}
		}
		level.setBlock(spot, WRECK.defaultBlockState(), Block.UPDATE_ALL);
		Block[] bits = {Blocks.IRON_BARS, Blocks.POLISHED_ANDESITE_SLAB, Blocks.IRON_TRAPDOOR, Blocks.ANVIL};
		for (int i = 0; i < DEBRIS; i++) {
			int dx = random.nextInt(SCORCH_RADIUS * 2 + 3) - SCORCH_RADIUS - 1, dz = random.nextInt(SCORCH_RADIUS * 2 + 3) - SCORCH_RADIUS - 1;
			if (Math.abs(dx) < 2 && Math.abs(dz) < 2) {
				continue;
			}
			BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spot.offset(dx, 0, dz));
			if (level.getBlockState(at).isAir() && level.getBlockState(at.below()).isSolidRender()) {
				level.setBlock(at, bits[random.nextInt(bits.length)].defaultBlockState(), Block.UPDATE_CLIENTS);
			}
		}
		level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, spot.getX() + 0.5, spot.getY() + 1.0, spot.getZ() + 0.5, 12, 0.4, 0.2, 0.4, 0.01);
	}

	/** The torn pod log: a few pages in the pilot's hand (written in the lang file), the last of them how to mend her. */
	private static ItemStack podLog() {
		ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
		List<Filterable<Component>> pages = List.of(
			Filterable.passThrough(Component.translatable("book.jaymospowers.pod_log.page1")),
			Filterable.passThrough(Component.translatable("book.jaymospowers.pod_log.page2")),
			Filterable.passThrough(Component.translatable("book.jaymospowers.pod_log.page3")));
		book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough("Pod Log"), "Pilot Venn", 2, pages, true));
		return book;
	}

	/**
	 * The wreck of a pod: it can't be broken, and it won't fly. Each step of the mending wants one thing ({@link #REPAIR_ITEMS});
	 * use it on the wreck to put in what you have. Mended, it is a pod again ({@link ZFighterSpace#POD}).
	 */
	static final class WreckBlock extends Block {
		static final IntegerProperty STEP = IntegerProperty.create("step", 0, 3);
		static final IntegerProperty FILL = IntegerProperty.create("fill", 0, 7);
		private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 12, 15);

		WreckBlock(BlockBehaviour.Properties properties) {
			super(properties);
			registerDefaultState(stateDefinition.any().setValue(STEP, 0).setValue(FILL, 0));
		}

		@Override
		protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
			builder.add(STEP, FILL);
		}

		@Override
		protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
			return SHAPE;
		}

		private static boolean wanted(ItemStack stack, int step) {
			return step == FUEL ? stack.is(ItemTags.COALS) : stack.is(REPAIR_ITEMS[step]);
		}

		@Override
		protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
			BlockHitResult hit) {
			int step = state.getValue(STEP);
			if (!wanted(stack, step)) {
				return InteractionResult.TRY_WITH_EMPTY_HAND;
			}
			if (level instanceof ServerLevel server) {
				int fill = state.getValue(FILL);
				int take = Math.min(stack.getCount(), REPAIR_COUNTS[step] - fill);
				stack.consume(take, player);
				fill += take;
				server.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.5F, 1.2F + 0.1F * step);
				server.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5, 10, 0.3, 0.3, 0.3, 0.05);
				if (fill < REPAIR_COUNTS[step]) {
					server.setBlock(pos, state.setValue(FILL, fill), Block.UPDATE_ALL);
					needs(player, step, REPAIR_COUNTS[step] - fill);
				} else if (step + 1 < REPAIR_ITEMS.length) {
					server.setBlock(pos, state.setValue(STEP, step + 1).setValue(FILL, 0), Block.UPDATE_ALL);
					needs(player, step + 1, REPAIR_COUNTS[step + 1]);
				} else {
					mended(server, pos, player);
				}
			}
			return InteractionResult.SUCCESS;
		}

		@Override
		protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
			if (!level.isClientSide()) {
				int step = state.getValue(STEP);
				needs(player, step, REPAIR_COUNTS[step] - state.getValue(FILL));
				level.playSound(null, pos, SoundEvents.COPPER_HIT, SoundSource.BLOCKS, 0.8F, 0.7F);
			}
			return InteractionResult.SUCCESS;
		}

		private static void needs(Player player, int step, int left) {
			Component what = step == FUEL ? Component.translatable("message.jaymospowers.pod_wreck.coal") : REPAIR_ITEMS[step].getName(new ItemStack(REPAIR_ITEMS[step]));
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.pod_wreck.needs", left, what,
				Component.translatable("message.jaymospowers.pod_wreck.part." + REPAIR_PARTS[step])).withStyle(ChatFormatting.GOLD));
		}

		private static void mended(ServerLevel level, BlockPos pos, Player player) {
			level.setBlock(pos, ZFighterSpace.POD.defaultBlockState(), Block.UPDATE_ALL);
			level.playSound(null, pos, SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.BLOCKS, 1.0F, 0.5F);
			level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.8F, 1.4F);
			level.sendParticles(ParticleTypes.FLAME, pos.getX() + 0.5, pos.getY() + 0.2, pos.getZ() + 0.5, 30, 0.4, 0.1, 0.4, 0.05);
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.pod_wreck.mended").withStyle(ChatFormatting.AQUA));
		}
	}

	// ---------------------------------------------------------------- the Verdant World

	private static boolean verdant(MinecraftServer server, ServerPlayer player, RandomSource random) {
		ServerLevel verdant = server.getLevel(ZFighterSites.VERDANT_WORLD);
		if (verdant == null) {
			return false;
		}
		BlockPos tower = ZFighterSites.data(server).tower().orElseThrow();
		BlockPos below = new BlockPos(tower.getX(), verdant.getSeaLevel(), tower.getZ());
		BlockPos village = verdant.findNearestMapStructure(StructureTags.VILLAGE, below, VILLAGE_SEARCH, false);
		BlockPos home = village == null ? below : village;
		BlockPos feet = spotNear(verdant, home, 0, VILLAGE_NEAR, random, false);
		if (feet == null) {
			feet = spotNear(verdant, home, VILLAGE_NEAR, VILLAGE_NEAR * 4, random, false);
		}
		if (feet == null) {
			return false;
		}
		// the gate stands out on dry, open ground a walk away
		BlockPos ring = spotNear(verdant, home, GATE_MIN, GATE_MAX, random, true);
		if (ring == null) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			ring = home.offset(Mth.floor(Math.cos(angle) * GATE_MIN), 0, Mth.floor(Math.sin(angle) * GATE_MIN));
		}
		Vec3 gate = ZFighterSites.verdantGate(verdant, ring.getX(), ring.getZ());
		put(player, verdant, feet);
		give(player, compass(ZFighterSites.VERDANT_WORLD, BlockPos.containing(gate)));
		give(player, new ItemStack(Items.WHEAT_SEEDS, VERDANT_SEEDS));
		give(player, new ItemStack(Items.WOODEN_HOE));
		if (MedicineItems.LINEN_BANDAGE != null) {
			give(player, new ItemStack(MedicineItems.LINEN_BANDAGE, VERDANT_BANDAGES));
		}
		return true;
	}

	// ---------------------------------------------------------------- Rustfall

	private static boolean rustfall(MinecraftServer server, ServerPlayer player, RandomSource random) {
		ZFighterSpace.Dest dest = ZFighterSpace.DESTS[ZFighterSpace.RUSTFALL];
		ServerLevel rustfall = server.getLevel(dest.level());
		if (rustfall == null) {
			return false;
		}
		BlockPos pad = ZFighterSpace.pad(rustfall, dest);
		BlockPos feet = spotNear(rustfall, pad, MINE_MIN, MINE_MAX, random, false);
		if (feet == null) {
			return false;
		}
		mineMouth(rustfall, feet, random);
		put(player, rustfall, feet);
		player.setAttached(HUNTED, rustfall.getGameTime() + HUNT_TICKS);
		NEXT_HUNTER.put(player.getUUID(), rustfall.getGameTime() + HUNT_EVERY / 3);
		give(player, new ItemStack(Items.STONE_PICKAXE));
		give(player, compass(dest.level(), pad));
		return true;
	}

	/** A mine mouth behind where they stand: a timber frame, rails running in under it, and the broken shackle they slipped. */
	private static void mineMouth(ServerLevel level, BlockPos feet, RandomSource random) {
		BlockPos mouth = feet.north(2);
		BlockState post = Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState();
		for (int up = 0; up < 3; up++) {
			level.setBlock(mouth.west(2).above(up), post, Block.UPDATE_CLIENTS);
			level.setBlock(mouth.east(2).above(up), post, Block.UPDATE_CLIENTS);
		}
		for (int dx = -2; dx <= 2; dx++) {
			level.setBlock(mouth.offset(dx, 3, 0), Blocks.DARK_OAK_PLANKS.defaultBlockState(), Block.UPDATE_CLIENTS);
		}
		for (int dz = 0; dz < 4; dz++) {
			BlockPos rail = mouth.north(dz);
			if (level.getBlockState(rail.below()).isSolidRender() && level.getBlockState(rail).canBeReplaced()) {
				level.setBlock(rail, Blocks.RAIL.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
		}
		level.setBlock(mouth.west(1).above(2), Blocks.LANTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true),
			Block.UPDATE_CLIENTS);
		if (level.getBlockState(feet.east()).isAir()) {
			level.setBlock(feet.east(), Blocks.IRON_BARS.defaultBlockState(), Block.UPDATE_CLIENTS);
		}
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 20 != 0) {
			return;
		}
		ServerLevel rustfall = server.getLevel(ZFighterSpace.DESTS[ZFighterSpace.RUSTFALL].level());
		if (rustfall == null) {
			return;
		}
		long now = rustfall.getGameTime();
		for (ServerPlayer player : rustfall.players()) {
			Long until = player.getAttached(HUNTED);
			if (until == null) {
				continue;
			}
			if (now >= until) {
				player.removeAttached(HUNTED);
				NEXT_HUNTER.remove(player.getUUID());
				continue;
			}
			if (!player.isAlive() || player.isCreative() || player.isSpectator()) {
				continue;
			}
			long next = NEXT_HUNTER.getOrDefault(player.getUUID(), 0L);
			if (now < next) {
				continue;
			}
			int near = rustfall.getEntitiesOfClass(Vindicator.class, player.getBoundingBox().inflate(HUNT_NEAR),
				e -> e.isAlive() && e.entityTags().contains(OVERSEER_TAG)).size();
			if (near < HUNTERS) {
				overseer(rustfall, player);
			}
			NEXT_HUNTER.put(player.getUUID(), now + HUNT_EVERY);
		}
	}

	/** One of the Iron-Master's overseers, come out of the dust after the escapee (bare-handed: they want him back alive). */
	private static void overseer(ServerLevel level, ServerPlayer player) {
		RandomSource random = level.getRandom();
		BlockPos at = spotNear(level, player.blockPosition(), HUNT_MIN, HUNT_MAX, random, false);
		if (at == null) {
			return;
		}
		Vindicator overseer = EntityTypes.VINDICATOR.create(level, EntitySpawnReason.EVENT);
		if (overseer == null) {
			return;
		}
		overseer.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
		overseer.setCustomName(Component.translatable("entity.jaymospowers.overseer"));
		overseer.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(OVERSEER_HEALTH);
		overseer.setHealth((float)OVERSEER_HEALTH);
		overseer.addTag(OVERSEER_TAG);
		overseer.setPersistenceRequired();
		overseer.setTarget(player);
		level.addFreshEntity(overseer);
		level.playSound(null, at, SoundEvents.VINDICATOR_CELEBRATE, SoundSource.HOSTILE, 1.0F, 0.8F);
	}

	// ---------------------------------------------------------------- the Maw

	private static boolean maw(MinecraftServer server, ServerPlayer player) {
		ZFighterSpace.Dest dest = ZFighterSpace.DESTS[ZFighterSpace.MAW];
		ServerLevel maw = server.getLevel(dest.level());
		if (maw == null) {
			return false;
		}
		BlockPos pad = ZFighterSpace.pad(maw, dest);
		put(player, maw, pad.offset(0, 0, 2));
		give(player, new ItemStack(Items.POLISHED_BLACKSTONE_BRICKS, DECK_PLATES));
		give(player, new ItemStack(Items.LANTERN));
		return true;
	}

	// ---------------------------------------------------------------- the demon realm

	private static boolean demon(MinecraftServer server, ServerPlayer player, RandomSource random) {
		ServerLevel nether = server.getLevel(Level.NETHER);
		if (nether == null) {
			return false;
		}
		BlockPos spawn = server.overworld().getRespawnData().pos();
		BlockPos from = new BlockPos(spawn.getX() / 8, 64, spawn.getZ() / 8);
		com.mojang.datafixers.util.Pair<BlockPos, net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome>> gloam = nether.findClosestBiome3d(
			b -> b.is(ZFighterDemonRealm.BIOMES.get(0)), from, DEMON_SEARCH, DEMON_STEP, DEMON_VERTICAL_STEP);
		if (gloam == null) {
			gloam = nether.findClosestBiome3d(b -> ZFighterDemonRealm.BIOMES.stream().anyMatch(b::is), from, DEMON_SEARCH, DEMON_STEP, DEMON_VERTICAL_STEP);
		}
		if (gloam == null) {
			return false;
		}
		BlockPos village = ZFighterDemonRealm.raiseHome(nether, gloam.getFirst(), DEMON_CELLS);
		if (village == null) {
			village = ZFighterDemonRealm.raiseHome(nether, gloam.getFirst(), DEMON_CELLS_FAR);
		}
		if (village == null) {
			return false;
		}
		// before the master's door, the fire at your back
		Vec3 master = ZFighterDemonRealm.masterSpot(village);
		put(player, nether, BlockPos.containing(master.x + 2.0, village.getY(), master.z));
		player.setAttached(WARD, true);
		ZFighterDemonRealm.master(nether, village);
		oldPortal(nether, village, random);
		ItemStack potion = new ItemStack(Items.POTION);
		potion.set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.FIRE_RESISTANCE));
		give(player, potion);
		return true;
	}

	/** An old portal near the village, still alight: an obsidian frame worn half to crying obsidian, a little blackstone at its foot. */
	private static void oldPortal(ServerLevel level, BlockPos village, RandomSource random) {
		for (int i = 0; i < PORTAL_TRIES; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			int d = PORTAL_MIN + random.nextInt(PORTAL_MAX - PORTAL_MIN + 1);
			int x = village.getX() + Mth.floor(Math.cos(angle) * d), z = village.getZ() + Mth.floor(Math.sin(angle) * d);
			int y = ZFighterDemonRealm.floorAt(level, x, z);
			// on a floor near the village's height, the same under both of its inner columns
			if (y < 0 || Math.abs(y - village.getY()) > 6 || ZFighterDemonRealm.floorAt(level, x + 1, z) != y) {
				continue;
			}
			BlockPos base = new BlockPos(x, y, z);
			// the frame: 4 wide, 5 tall, along x, the inside 2 by 3
			for (int dx = -1; dx <= 2; dx++) {
				for (int up = -1; up <= 3; up++) {
					BlockPos at = base.offset(dx, up, 0);
					boolean frame = dx == -1 || dx == 2 || up == -1 || up == 3;
					if (frame) {
						level.setBlock(at, (random.nextInt(3) == 0 ? Blocks.CRYING_OBSIDIAN : Blocks.OBSIDIAN).defaultBlockState(), Block.UPDATE_CLIENTS);
					}
				}
			}
			for (int dx = 0; dx <= 1; dx++) {
				for (int up = 0; up <= 2; up++) {
					// room to walk in from either side
					for (int dz = -1; dz <= 1; dz += 2) {
						if (!level.getBlockState(base.offset(dx, up, dz)).isAir()) {
							level.setBlock(base.offset(dx, up, dz), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
						}
					}
					level.setBlock(base.offset(dx, up, 0), Blocks.NETHER_PORTAL.defaultBlockState().setValue(NetherPortalBlock.AXIS, Direction.Axis.X),
						Block.UPDATE_CLIENTS);
				}
			}
			for (int dx = -2; dx <= 3; dx++) {
				for (int dz = -1; dz <= 1; dz += 2) {
					BlockPos foot = base.offset(dx, -1, dz);
					if (random.nextBoolean() && !level.getBlockState(foot).isAir()) {
						level.setBlock(foot, Blocks.BLACKSTONE.defaultBlockState(), Block.UPDATE_CLIENTS);
					}
				}
			}
			return;
		}
	}
}
