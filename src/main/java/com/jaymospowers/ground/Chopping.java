package com.jaymospowers.ground;

import com.jaymospowers.JaymosPowers;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.MapColor;
import org.jspecify.annotations.Nullable;

/**
 * Andy: "a chopping block like sevtech and GTNH so you can place wood and chop into planks easier" (our own, after the
 * idea in Pyrotech). Sneak and use an axe on the top of a log you've set down and it's cut into a chopping block of the
 * same wood ({@link #carve}); stand logs on it and split them with the axe ({@link ChoppingBlock}). It splits cleaner than
 * the ground does ({@link FirstTools}): {@link #BONUS} more planks a log, a few blows each (fewer with a better axe), the
 * logs stay loaded so you just keep swinging, and now and then the chips are swept up as sawdust.
 */
public final class Chopping {
	/** Planks a log gives on the block over what it gives split on the ground ({@link FirstTools#PLANKS_CRUDE} and up). */
	public static final int BONUS = 1;
	/** Blows a log takes on the block: crude axes (the flint hatchet, stone), copper-grade, iron-grade or better. */
	public static final int CHOPS_CRUDE = 4, CHOPS_FAIR = 3, CHOPS_FINE = 2;
	/** Ticks between two blows that count (holding use repeats faster than that). */
	public static final int CHOP_GAP = 8;
	/** Logs a block holds stood up on it (one is split at a time). */
	public static final int HOLDS = 16;
	/** How often a split log also leaves a sawdust ({@link StoneAge#SAWDUST}). */
	public static final float SAWDUST_CHANCE = 0.25F;

	/** The woods with a chopping block of their own look; every other log carves into the oak one. */
	static final List<String> WOODS = List.of("oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "pale_oak",
		"crimson", "warped");

	private static final Map<String, Block> BY_WOOD = new HashMap<>();
	public static Block OAK;
	public static BlockEntityType<ChoppingBlockEntity> ENTITY;

	private Chopping() {
	}

	public static void init() {
		for (String wood : WOODS) {
			boolean nether = wood.equals("crimson") || wood.equals("warped");
			BlockBehaviour.Properties properties = BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0F)
				.sound(nether ? SoundType.STEM : SoundType.WOOD).noOcclusion();
			BY_WOOD.put(wood, block(wood + "_chopping_block", nether ? properties : properties.ignitedByLava()));
		}
		OAK = BY_WOOD.get("oak");
		ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, JaymosPowers.id("chopping_block"),
			new BlockEntityType<>(ChoppingBlockEntity::new, Set.copyOf(BY_WOOD.values())));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> {
			for (String wood : WOODS) {
				output.accept(BY_WOOD.get(wood));
			}
		});
	}

	/** Every chopping block, for JEI. */
	public static List<Block> blocks() {
		return WOODS.stream().map(BY_WOOD::get).toList();
	}

	/** The planks a log splits into, or null for anything that isn't a log with planks of its own. */
	public static @Nullable Item planksOf(ItemStack stack) {
		if (!(stack.getItem() instanceof BlockItem item) || !item.getBlock().defaultBlockState().is(net.minecraft.tags.BlockTags.LOGS)) {
			return null;
		}
		return FirstTools.planksOf(item.getBlock().defaultBlockState());
	}

	/** Blows a log takes with {@code axe}, read off its speed the way {@link FirstTools} grades it. */
	static int chops(ItemStack axe) {
		float speed = axe.getDestroySpeed(net.minecraft.world.level.block.Blocks.OAK_LOG.defaultBlockState());
		return speed >= FirstTools.FINE_SPEED ? CHOPS_FINE : speed >= FirstTools.FAIR_SPEED ? CHOPS_FAIR : CHOPS_CRUDE;
	}

	/** Planks a log gives on the block with {@code axe}. */
	static int planks(ItemStack axe) {
		return FirstTools.planksFor(axe) + BONUS;
	}

	/**
	 * Sneak + use an axe on the top of a log: the log is cut down into a chopping block of its wood (the oak look for woods
	 * without their own). Called from {@link FirstTools}' axe-on-log hook.
	 */
	static InteractionResult carve(Player player, Level level, InteractionHand hand, ItemStack axe, BlockPos pos, Item planks) {
		if (level instanceof ServerLevel) {
			String wood = BuiltInRegistries.ITEM.getKey(planks).getPath().replace("_planks", "");
			Block block = BuiltInRegistries.ITEM.getKey(planks).getNamespace().equals("minecraft") ? BY_WOOD.getOrDefault(wood, OAK) : OAK;
			level.setBlock(pos, block.defaultBlockState(), Block.UPDATE_ALL);
			level.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
			level.playSound(null, pos, SoundEvents.AXE_STRIP.value(), SoundSource.BLOCKS, 1.0F, 0.7F);
			axe.hurtAndBreak(1, player, hand);
		}
		return InteractionResult.SUCCESS;
	}

	private static Block block(String id, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosPowers.id(id));
		Block block = Registry.register(BuiltInRegistries.BLOCK, key, new ChoppingBlock(properties.setId(key)));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, JaymosPowers.id(id));
		Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
		return block;
	}
}
