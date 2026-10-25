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
 * (0.16.3) Andy: RLCraft's start ("remove craftable planks, and add a flint hatchet you can craft by this route"):
 * <ol>
 * <li>flint (gravel, or loose on the ground: {@link GroundRocks}) knapped on the top of a hard block gives flint shards;</li>
 * <li>a shard on a stick makes the primitive knife;</li>
 * <li>grass cut with a knife gives plant fibre, 3 twist into plant string;</li>
 * <li>shard, string and stick make the flint hatchet (Armory's hand axe), the first thing that cuts wood;</li>
 * <li>logs don't craft into planks ({@code tinkering/Uncraftable}): set one down and right click its top with an axe.</li>
 * </ol>
 * (0.16.24) Andy: "a way to strike and start flame like flint and steel but with a rock in one hand and the flint in the
 * other, it chips the flint and breaks the rock": right click a block with flint and a rock in your two hands. A strike
 * catches {@link #STRIKE_CHANCE} of the time; when it does the rock breaks, and the flint chips down to a shard
 * {@link #FLINT_CHIP_CHANCE} of the time. Eight plant fibres in a ring make string (a data recipe).
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
	/** How often a strike of flint on a rock catches, and how often catching chips the flint down to a shard. */
	public static final float STRIKE_CHANCE = 0.35F;
	public static final float FLINT_CHIP_CHANCE = 0.33F;

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
			if (stack.is(Items.FLINT) && hard(level, hit.getBlockPos(), state) && !striking(player)) {
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

		// flint struck on a rock: after everything else (Kingdoms watches for fires being lit), so in a later phase
		Identifier late = JaymosPowers.id("strike_fire");
		UseBlockCallback.EVENT.addPhaseOrdering(net.fabricmc.fabric.api.event.Event.DEFAULT_PHASE, late);
		UseBlockCallback.EVENT.register(late, (player, level, hand, hit) -> {
			if (player.isSpectator() || !striking(player)) {
				return InteractionResult.PASS;
			}
			return strike(player, level, hand, hit);
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

	/** A rock in one hand and flint in the other. */
	static boolean striking(Player player) {
		ItemStack main = player.getMainHandItem(), off = player.getOffhandItem();
		return main.is(Items.FLINT) && rock(off) || rock(main) && off.is(Items.FLINT);
	}

	private static boolean rock(ItemStack stack) {
		return stack.getItem() instanceof net.minecraft.world.item.BlockItem item && GroundRocks.ROCKS.containsValue(item.getBlock());
	}

	/** Flint struck on a rock, at the clicked block: sparks, and sometimes a fire (or a campfire or candle lit). */
	private static InteractionResult strike(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
		player.swing(hand, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		if (!(level instanceof ServerLevel server)) {
			return InteractionResult.SUCCESS;
		}
		BlockPos pos = hit.getBlockPos();
		var at = hit.getLocation();
		server.playSound(null, pos, SoundEvents.STONE_HIT, SoundSource.PLAYERS, 0.9F, 1.6F + server.getRandom().nextFloat() * 0.3F);
		server.sendParticles(net.minecraft.core.particles.ParticleTypes.SMALL_FLAME, at.x, at.y, at.z, 2, 0.05, 0.05, 0.05, 0.01);
		server.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT, at.x, at.y, at.z, 4, 0.05, 0.05, 0.05, 0.15);
		if (server.getRandom().nextFloat() >= STRIKE_CHANCE || !light(server, player, hit)) {
			return InteractionResult.SUCCESS;
		}
		if (!player.getAbilities().instabuild) {
			ItemStack main = player.getMainHandItem();
			ItemStack rock = main.is(Items.FLINT) ? player.getOffhandItem() : main;
			ItemStack flint = main.is(Items.FLINT) ? main : player.getOffhandItem();
			rock.shrink(1);
			server.playSound(null, pos, SoundEvents.STONE_BREAK, SoundSource.PLAYERS, 0.8F, 1.2F);
			if (server.getRandom().nextFloat() < FLINT_CHIP_CHANCE) {
				flint.shrink(1);
				player.getInventory().placeItemBackInInventory(new ItemStack(FLINT_SHARD), net.minecraft.util.Prediction.SERVER_ONLY);
			}
		}
		return InteractionResult.SUCCESS;
	}

	/** As flint and steel lights: a campfire or candle that's out, else fire on the clicked face. Whether it caught. */
	private static boolean light(ServerLevel level, Player player, BlockHitResult hit) {
		BlockPos pos = hit.getBlockPos();
		BlockState state = level.getBlockState(pos);
		if (net.minecraft.world.level.block.CampfireBlock.canLight(state) || net.minecraft.world.level.block.CandleBlock.canLight(state)
			|| net.minecraft.world.level.block.CandleCakeBlock.canLight(state)) {
			level.playSound(null, pos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1.0F, 0.9F + level.getRandom().nextFloat() * 0.2F);
			level.setBlock(pos, state.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT, true), Block.UPDATE_ALL_IMMEDIATE);
			level.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
			return true;
		}
		BlockPos fire = pos.relative(hit.getDirection());
		if (!net.minecraft.world.level.block.BaseFireBlock.canBePlacedAt(level, fire, player.getDirection())) {
			return false;
		}
		level.playSound(null, fire, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1.0F, 0.9F + level.getRandom().nextFloat() * 0.2F);
		level.setBlock(fire, net.minecraft.world.level.block.BaseFireBlock.getState(level, fire), Block.UPDATE_ALL_IMMEDIATE);
		level.gameEvent(player, GameEvent.BLOCK_PLACE, fire);
		return true;
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
