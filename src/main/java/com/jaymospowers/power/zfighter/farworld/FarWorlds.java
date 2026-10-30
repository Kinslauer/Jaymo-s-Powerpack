package com.jaymospowers.power.zfighter.farworld;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.ground.LooseStonesBlock;
import com.jaymospowers.tinkering.TinkerMaterial;
import com.jaymospowers.tinkering.TinkerTool;
import com.jaymospowers.tinkering.ToolStats;
import com.jaymospowers.power.zfighter.sky.Charts;
import com.mojang.serialization.Codec;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * (0.18.53, SevTech: far worlds restart the stone age) On the six far worlds the ground starts again from the flint days.
 * <ul>
 * <li><b>Prospecting First</b>: ore there is swapped, the first time its chunk loads, for a {@link VeiledOreBlock} that looks like the
 * stone round it. A pick tapped on any block there shows the ore within {@link #TAP_RADIUS}; a read chart of the world shows the ore
 * in sight within {@link #SIGHT_RADIUS} ({@link #sight}).</li>
 * <li><b>Gear doesn't answer</b>: ore on a far world ({@link #FAR_ORE}) takes {@link #SLOWDOWN} times as long, and gives nothing,
 * unless the tool's head is that world's first metal ({@link #answers}). Your gear is kept; you start over to go on.</li>
 * <li>The world's loose rocks ({@link #ROCKS}, thrown like any rock) are knapped into the first metal's ingot at a
 * {@link #FIELD_ANVIL}; the metal is a Tinkers material, cast and forged only ({@code TinkerMaterial}, {@code Molten}).</li>
 * </ul>
 */
public final class FarWorlds {
	// ---- tuning ----
	/** Ore on a far world takes this many times as long to break with gear that does not answer. */
	public static final float SLOWDOWN = 4.0F;
	/** A pick tapped on a block shows the veiled ore within this many blocks; a read chart shows what is in sight within this many. */
	public static final int TAP_RADIUS = 3, SIGHT_RADIUS = 8;
	/** Ticks between chart sightings, between one player's taps, and the most ore one sweep shows. */
	static final int SIGHT_EVERY = 20, TAP_COOLDOWN = 6, MAX_REVEALS = 64;
	/** Loose rocks laid down in a new chunk: the tries, the chance each is a pile, and the sizes (1-4) in 100. */
	static final int ROCK_TRIES = 4;
	static final float ROCK_CHANCE = 0.5F;
	static final int[] ROCK_SIZES = {45, 30, 17, 8};
	/** Cinder has a ceiling: rocks are looked for on floors between these heights. */
	static final int CEILING_ROCK_TOP = 100, CEILING_ROCK_BOTTOM = 34;
	/** Rocks in each pile on a Maw deck. */
	static final int DECK_ROCKS = 2;

	public static final TagKey<Block> FAR_ORE = TagKey.create(Registries.BLOCK, JaymosPowers.id("far_ore"));

	public static Block VEILED_ORE, FIELD_ANVIL;
	public static final Map<FarWorld, Block> ROCKS = new EnumMap<>(FarWorld.class);

	/** Set on a chunk of a far world once its ore has been veiled (and its rocks laid), so ore a player sets down is left alone. */
	public static final AttachmentType<Boolean> VEILED = AttachmentRegistry.create(JaymosPowers.id("farworld_veiled"),
		builder -> builder.persistent(Codec.BOOL));

	private static final Map<UUID, Long> NEXT_TAP = new HashMap<>();

	private FarWorlds() {
	}

	public static void init() {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosPowers.id("veiled_ore"));
		VEILED_ORE = Registry.register(BuiltInRegistries.BLOCK, key, new VeiledOreBlock(BlockBehaviour.Properties.of().mapColor(MapColor.STONE)
			.strength(1.5F, 6.0F).requiresCorrectToolForDrops().sound(SoundType.STONE).setId(key)));
		ResourceKey<Block> anvilKey = ResourceKey.create(Registries.BLOCK, JaymosPowers.id("field_anvil"));
		FIELD_ANVIL = Registry.register(BuiltInRegistries.BLOCK, anvilKey, new FieldAnvilBlock(BlockBehaviour.Properties.of()
			.mapColor(MapColor.STONE).strength(3.5F, 6.0F).requiresCorrectToolForDrops().sound(SoundType.ANVIL).noOcclusion().setId(anvilKey)));
		ResourceKey<Item> anvilItem = ResourceKey.create(Registries.ITEM, JaymosPowers.id("field_anvil"));
		Registry.register(BuiltInRegistries.ITEM, anvilItem, new BlockItem(FIELD_ANVIL, new Item.Properties().setId(anvilItem).useBlockDescriptionPrefix()));
		for (FarWorld world : FarWorld.values()) {
			String id = world.key + "_rock";
			ResourceKey<Block> rockKey = ResourceKey.create(Registries.BLOCK, JaymosPowers.id(id));
			ResourceKey<Item> rockItem = ResourceKey.create(Registries.ITEM, JaymosPowers.id(id));
			Block rock = Registry.register(BuiltInRegistries.BLOCK, rockKey, new LooseStonesBlock.Rocks(BlockBehaviour.Properties.of()
				.mapColor(MapColor.STONE).instabreak().noCollision().noOcclusion().sound(SoundType.STONE).pushReaction(PushReaction.POPPED)
				.offsetType(BlockBehaviour.OffsetType.XZ).setId(rockKey), () -> BuiltInRegistries.ITEM.getValue(rockItem)));
			Registry.register(BuiltInRegistries.ITEM, rockItem, new BlockItem(rock, new Item.Properties().setId(rockItem).useBlockDescriptionPrefix()));
			ROCKS.put(world, rock);
		}
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.NATURAL_BLOCKS).register(output -> ROCKS.values().forEach(output::accept));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> output.accept(FIELD_ANVIL));

		ServerChunkEvents.CHUNK_LOAD.register(FarWorlds::chunkLoaded);
		AttackBlockCallback.EVENT.register(FarWorlds::tapped);
		PlayerBlockBreakEvents.BEFORE.register(FarWorlds::beforeBreak);
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % SIGHT_EVERY != 0) {
				return;
			}
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				FarWorld world = FarWorld.of(player.level());
				if (world != null && player.isAlive() && !player.isSpectator() && Charts.read(player, world.dest)) {
					sight(player.level(), player);
				}
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> NEXT_TAP.clear());
		PatrolWrit.init();
	}

	// ---------------------------------------------------------------- gear

	/** Whether a pick (or anything that bites stone) is in {@code stack}. */
	static boolean pick(ItemStack stack) {
		return stack.is(ItemTags.PICKAXES) || stack.getDestroySpeed(Blocks.STONE.defaultBlockState()) > 1.0F;
	}

	/** Whether the tool in the player's hand has the world's first metal for its head (and is not broken). */
	public static boolean answers(Player player, FarWorld world) {
		ItemStack held = player.getMainHandItem();
		if (!(held.getItem() instanceof TinkerTool) || ToolStats.isBroken(held)) {
			return false;
		}
		List<TinkerMaterial> materials = ToolStats.materials(held);
		return !materials.isEmpty() && materials.getFirst() == world.metal();
	}

	private static boolean beforeBreak(Level level, Player player, BlockPos pos, BlockState state, net.minecraft.world.level.block.entity.BlockEntity entity) {
		if (level.isClientSide() || player.isCreative() || !state.is(FAR_ORE) || !(level instanceof ServerLevel server)) {
			return true;
		}
		FarWorld world = FarWorld.of(level);
		if (world == null) {
			return true;
		}
		if (state.is(VEILED_ORE)) {
			// it is never dug as it is: the blow shows it (and what lies beside it)
			tap(server, pos);
			return false;
		}
		if (answers(player, world)) {
			return true;
		}
		// gear from home only chips it to nothing
		level.destroyBlock(pos, false, player);
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.farworld.crumbles").withStyle(net.minecraft.ChatFormatting.GRAY));
		return false;
	}

	// ---------------------------------------------------------------- prospecting

	private static InteractionResult tapped(Player player, Level level, net.minecraft.world.InteractionHand hand, BlockPos pos, Direction face) {
		if (!(level instanceof ServerLevel server) || player.isSpectator() || FarWorld.of(level) == null || !pick(player.getItemInHand(hand))) {
			return InteractionResult.PASS;
		}
		long now = level.getGameTime();
		if (now < NEXT_TAP.getOrDefault(player.getUUID(), 0L)) {
			return InteractionResult.PASS;
		}
		NEXT_TAP.put(player.getUUID(), now + TAP_COOLDOWN);
		tap(server, pos);
		return InteractionResult.PASS;
	}

	/** A pick has struck at {@code pos}: veiled ore within {@link #TAP_RADIUS} cracks and shows. */
	static void tap(ServerLevel level, BlockPos pos) {
		int shown = 0;
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int dx = -TAP_RADIUS; dx <= TAP_RADIUS; dx++) {
			for (int dy = -TAP_RADIUS; dy <= TAP_RADIUS; dy++) {
				for (int dz = -TAP_RADIUS; dz <= TAP_RADIUS; dz++) {
					if (dx * dx + dy * dy + dz * dz <= TAP_RADIUS * TAP_RADIUS + 1) {
						at.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
						if (reveal(level, at)) {
							shown++;
						}
					}
				}
			}
		}
		if (shown > 0) {
			level.playSound(null, pos, SoundType.STONE.getBreakSound(), net.minecraft.sounds.SoundSource.BLOCKS, 0.8F, 1.4F);
		}
	}

	/** A read chart: the ore in sight (a face open to the air) within {@link #SIGHT_RADIUS} of the player shows itself. */
	static void sight(ServerLevel level, ServerPlayer player) {
		BlockPos centre = player.blockPosition();
		int shown = 0;
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		BlockPos.MutableBlockPos near = new BlockPos.MutableBlockPos();
		for (int dx = -SIGHT_RADIUS; dx <= SIGHT_RADIUS && shown < MAX_REVEALS; dx++) {
			for (int dy = -SIGHT_RADIUS; dy <= SIGHT_RADIUS; dy++) {
				for (int dz = -SIGHT_RADIUS; dz <= SIGHT_RADIUS; dz++) {
					if (dx * dx + dy * dy + dz * dz > SIGHT_RADIUS * SIGHT_RADIUS) {
						continue;
					}
					at.set(centre.getX() + dx, centre.getY() + dy, centre.getZ() + dz);
					if (!level.getBlockState(at).is(VEILED_ORE)) {
						continue;
					}
					boolean open = false;
					for (Direction side : Direction.values()) {
						open |= !level.getBlockState(near.setWithOffset(at, side)).isSolidRender();
					}
					if (open && reveal(level, at)) {
						shown++;
					}
				}
			}
		}
	}

	/** Veiled ore at {@code pos} becomes the ore it is; true if there was any. */
	static boolean reveal(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (!state.is(VEILED_ORE)) {
			return false;
		}
		level.setBlock(pos, VeiledOreBlock.revealed(state), Block.UPDATE_ALL);
		level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 3, 0.35, 0.35, 0.35, 0.01);
		return true;
	}

	// ---------------------------------------------------------------- chunks

	private static void chunkLoaded(ServerLevel level, LevelChunk chunk, boolean generated) {
		FarWorld world = FarWorld.of(level);
		if (world == null || chunk.getAttachedOrElse(VEILED, false)) {
			return;
		}
		chunk.setAttached(VEILED, true);
		int x0 = chunk.getPos().x() * 16, z0 = chunk.getPos().z() * 16;
		LevelChunkSection[] sections = chunk.getSections();
		boolean changed = false;
		for (int i = 0; i < sections.length; i++) {
			LevelChunkSection section = sections[i];
			if (section.hasOnlyAir() || !section.maybeHas(s -> s.is(FAR_ORE) && !s.is(VEILED_ORE))) {
				continue;
			}
			int y0 = chunk.getMinY() + i * 16;
			for (int x = 0; x < 16; x++) {
				for (int y = 0; y < 16; y++) {
					for (int z = 0; z < 16; z++) {
						BlockState state = section.getBlockState(x, y, z);
						VeiledOreBlock.Kind kind = VeiledOreBlock.Kind.of(state);
						if (kind == null) {
							continue;
						}
						VeiledOreBlock.Host host = VeiledOreBlock.Kind.deep(state) ? VeiledOreBlock.Host.DEEPSLATE : world.host;
						chunk.setBlockState(new BlockPos(x0 + x, y0 + y, z0 + z), VEILED_ORE.defaultBlockState()
							.setValue(VeiledOreBlock.ORE, kind).setValue(VeiledOreBlock.HOST, host), 0);
						changed = true;
					}
				}
			}
		}
		if (generated && world != FarWorld.MAW) {
			changed |= scatter(level, chunk, world);
		}
		if (changed) {
			chunk.markUnsaved();
		}
	}

	/** A few piles of the world's own rocks on the ground of a newly made chunk. */
	private static boolean scatter(ServerLevel level, LevelChunk chunk, FarWorld world) {
		RandomSource random = level.getRandom();
		boolean ceiling = level.dimensionType().hasCeiling();
		boolean placed = false;
		int x0 = chunk.getPos().x() * 16, z0 = chunk.getPos().z() * 16;
		for (int i = 0; i < ROCK_TRIES; i++) {
			if (random.nextFloat() >= ROCK_CHANCE) {
				continue;
			}
			int x = random.nextInt(16), z = random.nextInt(16);
			BlockPos spot = null;
			if (ceiling) {
				for (int y = CEILING_ROCK_TOP; y > CEILING_ROCK_BOTTOM && spot == null; y--) {
					BlockPos at = new BlockPos(x0 + x, y, z0 + z);
					if (chunk.getBlockState(at).isAir() && chunk.getBlockState(at.below()).isSolidRender() && chunk.getBlockState(at.above()).isAir()) {
						spot = at;
					}
				}
			} else {
				BlockPos at = new BlockPos(x0 + x, chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z0 + z);
				BlockState below = chunk.getBlockState(at.below());
				if (chunk.getBlockState(at).isAir() && below.isSolidRender() && below.getFluidState().isEmpty()) {
					spot = at;
				}
			}
			if (spot != null) {
				chunk.setBlockState(spot, world.rock().defaultBlockState().setValue(LooseStonesBlock.ROCKS, size(random)), 0);
				placed = true;
			}
		}
		return placed;
	}

	private static int size(RandomSource random) {
		int roll = random.nextInt(100);
		for (int i = 0; i < ROCK_SIZES.length; i++) {
			roll -= ROCK_SIZES[i];
			if (roll < 0) {
				return i + 1;
			}
		}
		return 1;
	}

	/**
	 * The Maw is flat void with built wrecks (no chunk pass reaches it): a deck's underside holds two veiled ores (blackstone
	 * for a host) and its top a couple of the Maw's rocks. {@code centre} is the wreck's middle.
	 */
	public static void mawDeck(ServerLevel level, BlockPos centre, int index) {
		VeiledOreBlock.Kind[] kinds = {VeiledOreBlock.Kind.IRON, VeiledOreBlock.Kind.GOLD, VeiledOreBlock.Kind.DIAMOND, VeiledOreBlock.Kind.REDSTONE,
			VeiledOreBlock.Kind.LAPIS, VeiledOreBlock.Kind.COAL, VeiledOreBlock.Kind.EMERALD, VeiledOreBlock.Kind.COPPER, VeiledOreBlock.Kind.IRON};
		BlockPos[] under = {centre.offset(1, -1, 1), centre.offset(-1, -1, 0)};
		for (int i = 0; i < under.length; i++) {
			level.setBlock(under[i], VEILED_ORE.defaultBlockState().setValue(VeiledOreBlock.ORE, kinds[(index + i * 3) % kinds.length])
				.setValue(VeiledOreBlock.HOST, VeiledOreBlock.Host.BLACKSTONE), Block.UPDATE_CLIENTS);
		}
		for (BlockPos top : new BlockPos[] {centre.offset(2, 1, -1), centre.offset(-2, 1, 1)}) {
			level.setBlock(top, FarWorld.MAW.rock().defaultBlockState().setValue(LooseStonesBlock.ROCKS, DECK_ROCKS),
				Block.UPDATE_CLIENTS);
		}
	}
}
