package com.jaymoskingdoms.traps;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Slime mould (Andy's pick F3: green slime that eats metal and wood), a glistening green growth on a cave's ceiling that drips.
 * <ul>
 * <li><b>The drip</b> falls straight down up to {@link #FALL} blocks. Wood and metal it lands on is eaten, one block at a time,
 * any that holds items excepted (a chest keeps its things); a metal or wooden item on the ground dissolves, and on a
 * person it chews the gear they stand in it with down to a thread (never to nothing).</li>
 * <li><b>Fed by it.</b> Each thing eaten fills it ({@link #FED}, 0 to {@link #FULL}); full, it spreads along the ceiling.
 * With nothing to eat beneath it for a while it shrinks, and the last of it dies: a cave with no timbers and no rails loses its slime.</li>
 * <li><b>The answer: fire, or vinegar.</b> A flame beside it burns it off; a bottle of vinegar used on it dissolves the patch and
 * its neighbours.</li>
 * </ul>
 */
public class SlimeMouldBlock extends Block {
	public static final int FULL = 3;
	public static final IntegerProperty FED = IntegerProperty.create("fed", 0, FULL);
	// ---- tuning ----
	static final int FALL = 8, DRIP_EVERY = 40;
	/** Chances each random tick: to drip, to eat what the drip lands on, to spread when full, to waste when nothing is beneath. */
	static final float DRIP = 0.5F, EAT = 0.3F, SPREAD = 0.3F, WASTE = 0.35F;
	/** The wear a drip puts on gear (durability points). */
	static final int WEAR = 6;

	public SlimeMouldBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(FED, 1));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FED);
	}

	@Override
	protected boolean isRandomlyTicking(BlockState state) {
		return true;
	}

	/** Wood or metal that holds nothing: the drip may eat it. */
	static boolean edible(ServerLevel level, BlockPos pos) {
		BlockState s = level.getBlockState(pos);
		if (s.isAir() || s.is(Blocks.BEDROCK) || level.getBlockEntity(pos) instanceof BlockEntity || s.getBlock() instanceof SlimeMouldBlock) {
			return false;
		}
		SoundType sound = s.getSoundType();
		return sound == SoundType.WOOD || sound == SoundType.METAL || sound == SoundType.CHAIN || sound == SoundType.COPPER
			|| sound == SoundType.ANVIL || sound == SoundType.LANTERN
			|| s.is(BlockTags.LOGS) || s.is(BlockTags.PLANKS);
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		// the answer: a flame beside it
		for (Direction d : Direction.values()) {
			BlockState n = level.getBlockState(pos.relative(d));
			if (n.is(BlockTags.FIRE) || n.is(Blocks.LAVA) || n.is(Blocks.TORCH) || n.is(Blocks.WALL_TORCH) || n.is(Blocks.LANTERN)) {
				burn(level, pos);
				return;
			}
		}
		int fed = state.getValue(FED);
		BlockPos landed = null;
		for (int i = 1; i <= FALL; i++) {
			BlockPos p = pos.below(i);
			if (!level.getBlockState(p).isAir() && !level.getBlockState(p).getCollisionShape(level, p).isEmpty()) {
				landed = p;
				break;
			}
		}
		boolean ate = false;
		if (random.nextFloat() < DRIP) {
			double x = pos.getX() + 0.5, z = pos.getZ() + 0.5;
			level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, x, pos.getY() - 0.1, z, 2, 0.15, 0.0, 0.15, 0.0);
			level.playSound(null, pos, SoundEvents.SLIME_BLOCK_STEP, SoundSource.BLOCKS, 0.3F, 1.6F);
			ate = wear(level, pos, random);
			if (landed != null && edible(level, landed) && random.nextFloat() < EAT) {
				level.destroyBlock(landed, false);
				level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, landed.getX() + 0.5, landed.getY() + 0.5, landed.getZ() + 0.5, 8, 0.4, 0.4, 0.4, 0.0);
				ate = true;
			}
		}
		if (ate) {
			if (fed < FULL) {
				level.setBlockAndUpdate(pos, state.setValue(FED, fed + 1));
			} else if (random.nextFloat() < SPREAD) {
				BlockPos next = Surfaces.neighbour(level, pos, null, random, true);
				if (next != null) {
					level.setBlockAndUpdate(next, state.setValue(FED, 1));
				}
			}
		} else if (landed == null || !edible(level, landed)) {
			// nothing beneath to eat: it wastes
			if (random.nextFloat() < WASTE) {
				if (fed > 0) {
					level.setBlockAndUpdate(pos, state.setValue(FED, fed - 1));
				} else {
					level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
				}
			}
		}
	}

	/** The drip on whoever stands beneath it, and on metal or wood lying there. True if it found something to chew. */
	private boolean wear(ServerLevel level, BlockPos pos, RandomSource random) {
		boolean found = false;
		AABB column = new AABB(pos.getX() + 0.1, pos.getY() - FALL, pos.getZ() + 0.1, pos.getX() + 0.9, pos.getY(), pos.getZ() + 0.9);
		for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, column)) {
			ItemStack stack = item.getItem();
			if (metalOrWood(stack)) {
				item.discard();
				found = true;
			}
		}
		for (Player player : level.getEntitiesOfClass(Player.class, column, p -> !p.isSpectator())) {
			EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};
			EquipmentSlot slot = slots[random.nextInt(slots.length)];
			ItemStack stack = player.getItemBySlot(slot);
			if (metalOrWood(stack) && stack.isDamageableItem()) {
				stack.setDamageValue(Math.min(stack.getMaxDamage() - 1, stack.getDamageValue() + WEAR));
				player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("message.jaymoskingdoms.slime_mould.eats"));
				found = true;
			}
		}
		return found;
	}

	/** Items the slime chews: tools, weapons and armour of metal or wood, and the things themselves (ingots, planks). */
	static boolean metalOrWood(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		return stack.is(net.minecraft.tags.ItemTags.PLANKS) || stack.is(net.minecraft.tags.ItemTags.LOGS) || stack.is(Items.STICK)
			|| stack.is(net.minecraft.tags.ItemTags.IRON_TOOL_MATERIALS) || stack.is(net.minecraft.tags.ItemTags.COPPER_TOOL_MATERIALS)
			|| stack.is(net.minecraft.tags.ItemTags.GOLD_TOOL_MATERIALS) || stack.is(net.minecraft.tags.ItemTags.WOODEN_TOOL_MATERIALS)
			|| stack.is(Items.IRON_INGOT) || stack.is(Items.COPPER_INGOT) || stack.is(Items.GOLD_INGOT) || stack.is(Items.SHIELD)
			|| stack.is(Items.BOW) || stack.is(Items.CROSSBOW)
			|| stack.getItem().components().has(net.minecraft.core.component.DataComponents.EQUIPPABLE) && stack.isDamageableItem()
				&& !stack.is(Items.DIAMOND_HELMET) && !stack.is(Items.DIAMOND_CHESTPLATE) && !stack.is(Items.DIAMOND_LEGGINGS)
				&& !stack.is(Items.DIAMOND_BOOTS) && !stack.is(Items.LEATHER_HELMET) && !stack.is(Items.LEATHER_CHESTPLATE)
				&& !stack.is(Items.LEATHER_LEGGINGS) && !stack.is(Items.LEATHER_BOOTS);
	}

	/** Vinegar used on it: this patch and the ones touching it dissolve. */
	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
		BlockHitResult hit) {
		if (!stack.is(Traps.VINEGAR)) {
			return super.useItemOn(stack, state, level, pos, player, hand, hit);
		}
		if (level instanceof ServerLevel server) {
			for (BlockPos p : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 1, 1))) {
				if (server.getBlockState(p).getBlock() instanceof SlimeMouldBlock) {
					dissolve(server, p);
				}
			}
			server.playSound(null, pos, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 1.0F, 0.9F);
			if (!player.getAbilities().instabuild) {
				stack.shrink(1);
				ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
				player.getInventory().placeItemBackInInventory(bottle, net.minecraft.util.Prediction.SERVER_ONLY);
			}
		}
		return InteractionResult.SUCCESS;
	}

	private static void dissolve(ServerLevel level, BlockPos pos) {
		level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
		level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 12, 0.5, 0.5, 0.5, 0.0);
		level.sendParticles(ParticleTypes.CLOUD, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 4, 0.4, 0.4, 0.4, 0.01);
	}

	private static void burn(ServerLevel level, BlockPos pos) {
		level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
		level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 8, 0.4, 0.4, 0.4, 0.02);
		level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.6F, 1.2F);
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (random.nextInt(10) == 0) {
			level.addParticle(ParticleTypes.SPORE_BLOSSOM_AIR, pos.getX() + 0.2 + random.nextDouble() * 0.6, pos.getY() - 0.05,
				pos.getZ() + 0.2 + random.nextDouble() * 0.6, 0.0, -0.05, 0.0);
		}
	}
}
