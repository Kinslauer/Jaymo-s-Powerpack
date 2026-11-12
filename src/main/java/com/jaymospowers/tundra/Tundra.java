package com.jaymospowers.tundra;

import com.jaymospowers.JaymosPowers;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.registry.FlammableBlockRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.GlowLichenBlock;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * The tundra's blocks and plants: banded iron ore in smooth basalt, dwarfed spruce (a thin log and a thinner post, with
 * stripped forms), three shrubs, yellow moss and its carpet, white ice, and the two sisters of glow lichen
 * ({@link SproutedLichenBlock}, and the snipped lichen shears leave). What the lichen does to rock is in
 * {@link LichenLife}; how moss and shrubs follow it is in {@link YellowMossBlock}. Art, models, loot, recipes and tags
 * come from tools/gen_tundra.py (the ore's raw iron count, BANDED_IRON_MIN/MAX, is set there with the loot table).
 */
public final class Tundra {
	// ---- tuning ----
	/** Light a lichen gives off, as vanilla glow lichen. */
	public static final int LICHEN_LIGHT = 7;
	/** Widths of the dwarfed spruce log and post, in pixels of a block. */
	public static final int LOG_WIDTH = 10, POST_WIDTH = 6;
	/** How dwarfed spruce catches and burns (a log's numbers). */
	public static final int LOG_IGNITE = 5, LOG_BURN = 5;
	/** How shrubs catch and burn (short grass's numbers). */
	public static final int SHRUB_IGNITE = 60, SHRUB_BURN = 100;

	/** Rock the lichen breaks into gravel, gravel it breaks into sand or coarse dirt, coarse dirt it breaks into dirt. */
	public static final TagKey<Block> LICHEN_TO_GRAVEL = blockTag("lichen/to_gravel"), LICHEN_TO_SOIL = blockTag("lichen/to_soil"),
		LICHEN_TO_DIRT = blockTag("lichen/to_dirt");
	/** All three of the above. */
	public static final TagKey<Block> LICHEN_BREAKABLE = blockTag("lichen/breakable");
	/** Vanilla glow lichen and our two. */
	public static final TagKey<Block> GLOW_LICHENS = blockTag("glow_lichens");
	/** What a shrub stands on. */
	public static final TagKey<Block> SHRUB_GROUND = blockTag("shrub_ground");
	public static final TagKey<Block> SHRUBS = blockTag("tundra_shrubs");

	public static Block BANDED_IRON_ORE, WHITE_ICE;
	public static Block DWARFED_SPRUCE_LOG, DWARFED_SPRUCE_POST, STRIPPED_DWARFED_SPRUCE_LOG, STRIPPED_DWARFED_SPRUCE_POST;
	public static Block STUNTED_RED_SHRUB, ORANGE_SHRUB, YELLOW_SHRUB;
	public static Block YELLOW_MOSS, YELLOW_MOSS_CARPET;
	public static Block SPROUTED_GLOW_LICHEN, SNIPPED_GLOW_LICHEN;

	private Tundra() {
	}

	public static void init() {
		blocks();
		burning();
		tabs();
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (player.isSpectator() || !player.mayBuild() || !level.mayInteract(player, hit.getBlockPos())) {
				return InteractionResult.PASS;
			}
			InteractionResult lichen = LichenLife.use(player, level, hand, hit.getBlockPos());
			return lichen != InteractionResult.PASS ? lichen : strip(player, level, hand, hit.getBlockPos());
		});
	}

	private static void blocks() {
		BANDED_IRON_ORE = block("banded_iron_ore", Block::new, BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_ORE)
			.mapColor(MapColor.COLOR_BLACK).sound(SoundType.BASALT));
		// packed ice's hardness and slide (the same slide as plain ice); a plain block, so it never melts
		WHITE_ICE = block("white_ice", Block::new, BlockBehaviour.Properties.ofFullCopy(Blocks.PACKED_ICE).mapColor(MapColor.SNOW));

		DWARFED_SPRUCE_LOG = block("dwarfed_spruce_log", p -> new DwarfedLogBlock(LOG_WIDTH, p), thin(Blocks.SPRUCE_LOG));
		DWARFED_SPRUCE_POST = block("dwarfed_spruce_post", p -> new DwarfedLogBlock(POST_WIDTH, p), thin(Blocks.SPRUCE_LOG));
		STRIPPED_DWARFED_SPRUCE_LOG = block("stripped_dwarfed_spruce_log", p -> new DwarfedLogBlock(LOG_WIDTH, p), thin(Blocks.STRIPPED_SPRUCE_LOG));
		STRIPPED_DWARFED_SPRUCE_POST = block("stripped_dwarfed_spruce_post", p -> new DwarfedLogBlock(POST_WIDTH, p), thin(Blocks.STRIPPED_SPRUCE_LOG));

		STUNTED_RED_SHRUB = block("stunted_red_shrub", ShrubBlock::new, shrub(MapColor.COLOR_RED));
		ORANGE_SHRUB = block("orange_shrub", ShrubBlock::new, shrub(MapColor.COLOR_ORANGE));
		YELLOW_SHRUB = block("yellow_shrub", ShrubBlock::new, shrub(MapColor.COLOR_YELLOW));

		YELLOW_MOSS = block("yellow_moss", YellowMossBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.MOSS_BLOCK)
			.mapColor(MapColor.COLOR_YELLOW).randomTicks());
		YELLOW_MOSS_CARPET = block("yellow_moss_carpet", CarpetBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.MOSS_CARPET)
			.mapColor(MapColor.COLOR_YELLOW));

		SPROUTED_GLOW_LICHEN = block("sprouted_glow_lichen", SproutedLichenBlock::new, lichen().randomTicks());
		SNIPPED_GLOW_LICHEN = block("snipped_glow_lichen", MultifaceBlock::new, lichen());
	}

	/** A log's wood, but a thin pillar: it hides no neighbour's face. */
	private static BlockBehaviour.Properties thin(Block log) {
		return BlockBehaviour.Properties.ofFullCopy(log).noOcclusion();
	}

	private static BlockBehaviour.Properties shrub(MapColor colour) {
		return BlockBehaviour.Properties.of().mapColor(colour).noCollision().instabreak().sound(SoundType.SWEET_BERRY_BUSH)
			.offsetType(BlockBehaviour.OffsetType.XZ).ignitedByLava().pushReaction(PushReaction.POPPED);
	}

	/** Glow lichen's own stuff (thin, walk-through, replaceable, popped by pistons), glowing on every face. */
	private static BlockBehaviour.Properties lichen() {
		return BlockBehaviour.Properties.ofFullCopy(Blocks.GLOW_LICHEN).lightLevel(GlowLichenBlock.emission(LICHEN_LIGHT));
	}

	private static void burning() {
		FlammableBlockRegistry flammable = FlammableBlockRegistry.getDefaultInstance();
		for (Block log : new Block[] {DWARFED_SPRUCE_LOG, DWARFED_SPRUCE_POST, STRIPPED_DWARFED_SPRUCE_LOG, STRIPPED_DWARFED_SPRUCE_POST}) {
			flammable.add(log, LOG_IGNITE, LOG_BURN);
		}
		for (Block shrub : new Block[] {STUNTED_RED_SHRUB, ORANGE_SHRUB, YELLOW_SHRUB}) {
			flammable.add(shrub, SHRUB_IGNITE, SHRUB_BURN);
		}
	}

	private static void tabs() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.NATURAL_BLOCKS).register(output -> {
			for (Block block : new Block[] {BANDED_IRON_ORE, WHITE_ICE, DWARFED_SPRUCE_LOG, DWARFED_SPRUCE_POST, STUNTED_RED_SHRUB,
				ORANGE_SHRUB, YELLOW_SHRUB, YELLOW_MOSS, YELLOW_MOSS_CARPET, SPROUTED_GLOW_LICHEN, SNIPPED_GLOW_LICHEN}) {
				output.accept(block);
			}
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.BUILDING_BLOCKS).register(output -> {
			for (Block block : new Block[] {DWARFED_SPRUCE_LOG, STRIPPED_DWARFED_SPRUCE_LOG, DWARFED_SPRUCE_POST, STRIPPED_DWARFED_SPRUCE_POST}) {
				output.accept(block);
			}
		});
	}

	/** An axe strips a dwarfed log or post where it stands (it keeps its axis and its water). */
	private static InteractionResult strip(Player player, Level level, InteractionHand hand, BlockPos pos) {
		ItemStack axe = player.getItemInHand(hand);
		BlockState state = level.getBlockState(pos);
		Block stripped = state.is(DWARFED_SPRUCE_LOG) ? STRIPPED_DWARFED_SPRUCE_LOG : state.is(DWARFED_SPRUCE_POST) ? STRIPPED_DWARFED_SPRUCE_POST : null;
		if (stripped == null || !axe.is(ItemTags.AXES)) {
			return InteractionResult.PASS;
		}
		if (!level.isClientSide()) {
			level.setBlockAndUpdate(pos, stripped.defaultBlockState().setValue(RotatedPillarBlock.AXIS, state.getValue(RotatedPillarBlock.AXIS))
				.setValue(DwarfedLogBlock.WATERLOGGED, state.getValue(DwarfedLogBlock.WATERLOGGED)));
			level.playSound(null, pos, SoundEvents.AXE_STRIP.value(), SoundSource.BLOCKS, 1.0F, 1.0F);
			level.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
			axe.hurtAndBreak(1, player, hand);
		}
		return InteractionResult.SUCCESS;
	}

	private static TagKey<Block> blockTag(String path) {
		return TagKey.create(Registries.BLOCK, JaymosPowers.id(path));
	}

	private static Block block(String id, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosPowers.id(id));
		Block block = Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, JaymosPowers.id(id));
		Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
		return block;
	}
}
