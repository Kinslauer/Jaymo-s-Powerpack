package com.jaymospowers.ground;

import com.jaymospowers.JaymosPowers;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;

/**
 * (0.16.2) Andy: RLCraft's start ("remove craftable planks, and add a flint hatchet you can craft by this route"):
 * <ol>
 * <li>flint (gravel, or loose on the ground: {@link GroundRocks}) knapped on the top of a hard block gives flint shards;</li>
 * <li>a shard on a stick makes the flint knife (Armory's primitive knife);</li>
 * <li>grass cut with a knife gives plant fibre, 3 twist into plant string;</li>
 * <li>shard, string and stick make the flint hatchet (Armory's hand axe), the first thing that cuts wood;</li>
 * <li>logs don't craft into planks ({@code tinkering/Uncraftable}): set one down and right click its top with an axe.</li>
 * </ol>
 */
public final class FirstTools {
	/** Shards one flint knaps into. */
	public static final int SHARDS_PER_FLINT = 2;
	/** Planks a log gives under an axe: crude ones (the flint hatchet, stone), copper-grade, and iron-grade or better. */
	public static final int PLANKS_CRUDE = 2;
	public static final int PLANKS_FAIR = 3;
	public static final int PLANKS_FINE = 4;
	/** An axe's speed on oak at or over these is fair, fine (stone is 4, copper 5, iron 6). */
	public static final float FAIR_SPEED = 4.5F;
	public static final float FINE_SPEED = 5.5F;
	/** Fibre from cutting a grass block with a knife (twice that for tall grass and large ferns). */
	public static final int FIBRE_PER_GRASS = 1;

	/** What cuts plant fibre: the knives (a data tag, so other mods' knives can join). */
	public static final TagKey<Item> FIBRE_KNIVES = TagKey.create(Registries.ITEM, JaymosPowers.id("fiber_knives"));
	/** What gives plant fibre (a data tag): grass, ferns, dry grass, bushes. */
	public static final TagKey<Block> FIBRE_PLANTS = TagKey.create(Registries.BLOCK, JaymosPowers.id("fiber_plants"));

	public static Item FLINT_SHARD;
	public static Item PLANT_FIBRE;
	public static Item PLANT_STRING;

	private FirstTools() {
	}

	public static void init() {
		FLINT_SHARD = item("flint_shard");
		PLANT_FIBRE = item("plant_fiber");
		PLANT_STRING = item("plant_string");
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(output -> {
			output.accept(FLINT_SHARD);
			output.accept(PLANT_FIBRE);
			output.accept(PLANT_STRING);
		});

		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (player.isSpectator() || hit.getDirection() != Direction.UP) {
				return InteractionResult.PASS;
			}
			ItemStack stack = player.getItemInHand(hand);
			BlockState state = level.getBlockState(hit.getBlockPos());
			if (stack.is(Items.FLINT) && hard(level, hit.getBlockPos(), state)) {
				return knap(player, level, hand, stack, hit);
			}
			if (stack.is(ItemTags.AXES) && state.is(BlockTags.LOGS)) {
				Item planks = planksOf(state);
				if (planks != null) {
					return plank(player, level, hand, stack, hit.getBlockPos(), planks);
				}
			}
			return InteractionResult.PASS;
		});

		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			if (level.isClientSide() || player.getAbilities().instabuild || !state.is(FIBRE_PLANTS)
				|| !player.getMainHandItem().is(FIBRE_KNIVES)) {
				return;
			}
			boolean tall = state.is(Blocks.TALL_GRASS) || state.is(Blocks.LARGE_FERN) || state.is(Blocks.TALL_DRY_GRASS);
			Block.popResource(level, pos, new ItemStack(PLANT_FIBRE, tall ? FIBRE_PER_GRASS * 2 : FIBRE_PER_GRASS));
			player.getMainHandItem().hurtAndBreak(1, player, InteractionHand.MAIN_HAND);
		});
	}

	private static Item item(String id) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id(id));
		return Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key)));
	}

	/** Stone, bricks, ore and the like: anything that wants a pickaxe and isn't soft. */
	private static boolean hard(Level level, BlockPos pos, BlockState state) {
		return state.requiresCorrectToolForDrops() && state.getDestroySpeed(level, pos) >= 1.0F && state.is(BlockTags.MINEABLE_WITH_PICKAXE);
	}

	private static InteractionResult knap(Player player, Level level, InteractionHand hand, ItemStack flint, BlockHitResult hit) {
		if (level instanceof ServerLevel) {
			flint.consume(1, player);
			player.getInventory().placeItemBackInInventory(new ItemStack(FLINT_SHARD, SHARDS_PER_FLINT), net.minecraft.util.Prediction.SERVER_ONLY);
			level.playSound(null, hit.getBlockPos(), SoundEvents.STONE_HIT, SoundSource.PLAYERS, 1.0F, 1.4F + level.getRandom().nextFloat() * 0.3F);
		}
		player.swing(hand, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		return InteractionResult.SUCCESS;
	}

	private static InteractionResult plank(Player player, Level level, InteractionHand hand, ItemStack axe, BlockPos pos, Item planks) {
		if (level instanceof ServerLevel) {
			float speed = axe.getDestroySpeed(Blocks.OAK_LOG.defaultBlockState());
			int count = speed >= FINE_SPEED ? PLANKS_FINE : speed >= FAIR_SPEED ? PLANKS_FAIR : PLANKS_CRUDE;
			level.removeBlock(pos, false);
			level.gameEvent(player, GameEvent.BLOCK_DESTROY, pos);
			Block.popResource(level, pos.above(), new ItemStack(planks, count));
			level.playSound(null, pos, SoundEvents.AXE_STRIP.value(), SoundSource.BLOCKS, 1.0F, 0.9F + level.getRandom().nextFloat() * 0.2F);
			axe.hurtAndBreak(1, player, hand);
		}
		return InteractionResult.SUCCESS;
	}

	/**
	 * The planks a log saws into, by name: {@code oak_log}, {@code stripped_oak_wood}, {@code crimson_stem},
	 * {@code bamboo_block}... become {@code oak_planks}, {@code crimson_planks}, {@code bamboo_planks} of the same
	 * namespace (so other mods' woods work too), or null if there are none.
	 */
	static Item planksOf(BlockState log) {
		Identifier id = BuiltInRegistries.BLOCK.getKey(log.getBlock());
		String wood = id.getPath();
		if (wood.startsWith("stripped_")) {
			wood = wood.substring("stripped_".length());
		}
		for (String suffix : new String[] {"_log", "_wood", "_stem", "_hyphae", "_block"}) {
			if (wood.endsWith(suffix)) {
				wood = wood.substring(0, wood.length() - suffix.length());
				break;
			}
		}
		// failing that, drop leading words (an infected_guardian_log is guardian wood)
		while (true) {
			var planks = BuiltInRegistries.ITEM.getOptional(Identifier.fromNamespaceAndPath(id.getNamespace(), wood + "_planks"));
			if (planks.isPresent()) {
				return planks.get();
			}
			int cut = wood.indexOf('_');
			if (cut < 0) {
				return null;
			}
			wood = wood.substring(cut + 1);
		}
	}
}
