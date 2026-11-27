package com.jaymospowers.core.wet;

import com.jaymospowers.JaymosPowers;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Rusting iron (after Rust by adccadc, CC0, its textures): iron left out in the wet rusts on the block as it does on the
 * body ({@link Rust}). A block of iron, iron bars, an iron door or trapdoor, an iron chain and a lantern (or a soul
 * lantern) go spotted, then rusty, then corroded, a step at a time, while they're wet: rained on, standing in or against
 * water, or out in the salt air of the sea and the shore (sea water rusts twice as fast, as it does armour: the Deep Mother
 * rusts what the Anvil-Father makes). Kept dry under a roof, iron stays as it is. Each step softens it a little.
 * <p>
 * As copper's: honeycomb waxes iron at any step and it rusts no further (on the block, or with honeycomb at the crafting
 * grid); an axe takes the wax off, or scrapes a step of rust away; so does a scrub (sneaking, a straw wisp or sand in hand,
 * the rubbing the pack's {@code Scrubbing} does for armour's grime: a wisp wears, the sand is used up). Armour's rust
 * stays for good (Andy: "nothing removes rust"); a block's is only on its face, so it can be scoured off.
 * <p>
 * The game's own iron blocks are given random ticks for this ({@code mixin.RustTickMixin}); the steps are blocks of their
 * own, each its own item, so a builder can pick the look.
 */
public final class RustingIron {
	/** The chance a random tick moves wet iron on a step: in or against water, rained on, out in sea air when dry. */
	static final float IN_WATER = 0.05F, RAINED_ON = 0.03F, SEA_AIR = 0.004F;
	/** How much softer (of its breaking time) each step is: spotted, rusty, corroded. */
	static final float[] SOFTER = {0.9F, 0.75F, 0.6F};

	private static final String[] STEPS = {"spotted_", "rusty_", "corroded_"};
	/** Each block to the next step of rust, and back. */
	private static final Map<Block, Block> NEXT = new HashMap<>(), PREVIOUS = new HashMap<>();
	/** Each block to its waxed self, and back. */
	private static final Map<Block, Block> WAXED = new HashMap<>(), UNWAXED = new HashMap<>();
	private static final List<Item> ITEMS = new ArrayList<>();

	private RustingIron() {
	}

	public static void init() {
		kind(Blocks.IRON_BLOCK, "iron_block", Block::new);
		kind(Blocks.IRON_BARS, "iron_bars", IronBarsBlock::new);
		kind(Blocks.IRON_DOOR, "iron_door", p -> new DoorBlock(BlockSetType.IRON, p));
		kind(Blocks.IRON_TRAPDOOR, "iron_trapdoor", p -> new TrapDoorBlock(BlockSetType.IRON, p));
		kind(Blocks.IRON_CHAIN, "iron_chain", ChainBlock::new);
		kind(Blocks.LANTERN, "lantern", LanternBlock::new);
		kind(Blocks.SOUL_LANTERN, "soul_lantern", LanternBlock::new);
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.BUILDING_BLOCKS).register(output -> ITEMS.forEach(output::accept));
		UseBlockCallback.EVENT.register(RustingIron::use);
	}

	/** One kind of iron: its three steps of rust and the four waxed, after {@code plain}, the game's own. */
	private static void kind(Block plain, String name, Function<BlockBehaviour.Properties, Block> make) {
		Block before = plain, waxedBefore = register("waxed_" + name, plain, make, 1.0F);
		WAXED.put(plain, waxedBefore);
		for (int i = 0; i < STEPS.length; i++) {
			Block step = register(STEPS[i] + name, plain, make, SOFTER[i]);
			Block waxed = register("waxed_" + STEPS[i] + name, plain, make, SOFTER[i]);
			NEXT.put(before, step);
			PREVIOUS.put(step, before);
			WAXED.put(step, waxed);
			before = step;
			waxedBefore = waxed;
		}
		for (Map.Entry<Block, Block> e : WAXED.entrySet()) {
			UNWAXED.put(e.getValue(), e.getKey());
		}
	}

	private static Block register(String id, Block plain, Function<BlockBehaviour.Properties, Block> make, float softer) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosPowers.id(id));
		BlockBehaviour.Properties properties = BlockBehaviour.Properties.ofFullCopy(plain).setId(key)
			.destroyTime(plain.defaultDestroyTime() * softer);
		if (!id.startsWith("waxed_") && !id.startsWith("corroded_")) {
			properties.randomTicks();
		}
		Block block = Registry.register(BuiltInRegistries.BLOCK, key, make.apply(properties));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, JaymosPowers.id(id));
		ITEMS.add(Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix())));
		return block;
	}

	/** Whether {@code block} is one of the game's own iron blocks that rust (they're given random ticks for it). */
	public static boolean plainIron(Block block) {
		return block == Blocks.IRON_BLOCK || block == Blocks.IRON_BARS || block == Blocks.IRON_DOOR || block == Blocks.IRON_TRAPDOOR
			|| block == Blocks.IRON_CHAIN || block == Blocks.LANTERN || block == Blocks.SOUL_LANTERN;
	}

	/** A random tick of {@code state}: wet iron may rust a step. */
	public static void weather(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		Block next = NEXT.get(state.getBlock());
		if (next == null) {
			return;
		}
		if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
			return; // a door rusts from its lower half, both halves at once
		}
		float chance = wet(state, level, pos);
		if (chance > 0.0F && random.nextFloat() < chance) {
			become(level, pos, state, next);
		}
	}

	/** How fast iron at {@code pos} rusts: 0 dry. Sea water and the shore's salt air rust it faster ({@link Rust#SEA}). */
	private static float wet(BlockState state, ServerLevel level, BlockPos pos) {
		boolean sea = level.getBiome(pos).is(BiomeTags.IS_OCEAN) || level.getBiome(pos).is(BiomeTags.IS_BEACH);
		float salt = sea ? Rust.SEA : 1.0F;
		if (state.hasProperty(BlockStateProperties.WATERLOGGED) && state.getValue(BlockStateProperties.WATERLOGGED)) {
			return IN_WATER * salt;
		}
		for (Direction d : Direction.values()) {
			if (level.getFluidState(pos.relative(d)).is(FluidTags.WATER)) {
				return IN_WATER * salt;
			}
		}
		BlockPos up = top(level, pos, state).above();
		if (level.isRainingAt(up)) {
			return RAINED_ON * salt;
		}
		return sea && level.canSeeSky(up) ? SEA_AIR * salt : 0.0F;
	}

	/** A door's top half (the rain falls on that), else {@code pos}. */
	private static BlockPos top(ServerLevel level, BlockPos pos, BlockState state) {
		return state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) ? pos.above() : pos;
	}

	/** Swaps the iron at {@code pos} (both halves of a door) for {@code to}, its shape and state kept. */
	private static void become(Level level, BlockPos pos, BlockState state, Block to) {
		if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
			BlockPos other = state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
			BlockState otherState = level.getBlockState(other);
			if (otherState.is(state.getBlock())) {
				level.setBlock(other, to.withPropertiesOf(otherState), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
			}
			level.setBlock(pos, to.withPropertiesOf(state), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
			return;
		}
		level.setBlockAndUpdate(pos, to.withPropertiesOf(state));
	}

	/** Honeycomb waxes it; an axe takes the wax off or scrapes a step of rust away; so does a sneaking scrub with a straw wisp or sand. */
	private static InteractionResult use(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
		if (player.isSpectator()) {
			return InteractionResult.PASS;
		}
		BlockPos pos = hit.getBlockPos();
		BlockState state = level.getBlockState(pos);
		Block block = state.getBlock();
		ItemStack held = player.getItemInHand(hand);
		Block to;
		int event;
		SoundEvent sound;
		if (held.is(Items.HONEYCOMB) && WAXED.containsKey(block)) {
			to = WAXED.get(block);
			event = LevelEvent.PARTICLES_WAX_ON;
			sound = SoundEvents.HONEYCOMB_WAX_ON;
		} else if (held.is(ItemTags.AXES) && UNWAXED.containsKey(block)) {
			to = UNWAXED.get(block);
			event = LevelEvent.PARTICLES_WAX_OFF;
			sound = SoundEvents.AXE_WAX_OFF.value();
		} else if ((held.is(ItemTags.AXES) || player.isShiftKeyDown() && scrub(held)) && PREVIOUS.containsKey(block)) {
			to = PREVIOUS.get(block);
			event = LevelEvent.PARTICLES_SCRAPE;
			sound = held.is(ItemTags.AXES) ? SoundEvents.AXE_SCRAPE.value() : SoundEvents.BRUSH_GENERIC;
		} else {
			return InteractionResult.PASS;
		}
		if (level instanceof ServerLevel server) {
			become(server, pos, state, to);
			level.levelEvent(null, event, pos, 0);
			level.playSound(null, pos, sound, SoundSource.BLOCKS, 1.0F, 1.0F);
			if (!player.getAbilities().instabuild) {
				if (held.isDamageableItem()) {
					held.hurtAndBreak(1, player, hand == InteractionHand.MAIN_HAND ? net.minecraft.world.entity.EquipmentSlot.MAINHAND
						: net.minecraft.world.entity.EquipmentSlot.OFFHAND);
				} else {
					held.shrink(1);
				}
			}
		}
		return InteractionResult.SUCCESS;
	}

	/** What scrubs rust off a block's face: the straw wisp ({@link Scrubbing#STRAW_WISP}) or sand. */
	private static boolean scrub(ItemStack held) {
		return Scrubbing.STRAW_WISP != null && held.is(Scrubbing.STRAW_WISP) || held.is(ItemTags.SAND);
	}
}
