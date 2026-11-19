package com.jaymoskitchen.vintner;

import com.jaymoskitchen.climate.BodyHeat;
import com.jaymoskitchen.cooking.hands.HandsBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A tea pot, set on a table. Pour cups of tea into it ({@link #MAX_CUPS}); while it holds tea it pours for everyone sitting
 * on a cushion within {@link #RANGE} blocks, a cup at a time: a short calm ({@link #CALM_TICKS} of slow healing) and a
 * little warmth ({@link #WARMTH}). Nobody seated, nothing poured.
 */
public class TeaPotBlock extends Block {
	public static final int MAX_CUPS = 3;
	public static final IntegerProperty CUPS = IntegerProperty.create("cups", 0, MAX_CUPS);
	/** How far the pot reaches, how often it looks for who is seated (ticks), and what a cup gives. */
	private static final int RANGE = 4, LOOK_EVERY = 100, CALM_TICKS = 160;
	private static final float WARMTH = 12.0F;
	private static final VoxelShape SHAPE = Block.box(4, 0, 4, 12, 7, 12);

	public TeaPotBlock(BlockBehaviour.Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(CUPS, 0));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(CUPS);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected InteractionResult useItemOn(ItemStack held, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
		net.minecraft.world.phys.BlockHitResult hit) {
		int cups = state.getValue(CUPS);
		if (held.is(HandsBlocks.TEA) && cups < MAX_CUPS) {
			if (level instanceof ServerLevel server) {
				player.setItemInHand(hand, ItemUtils.createFilledResult(held, player, new ItemStack(Items.GLASS_BOTTLE)));
				level.setBlock(pos, state.setValue(CUPS, cups + 1), Block.UPDATE_CLIENTS);
				level.playSound(null, pos, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 0.8F, 1.2F);
				if (cups == 0) {
					server.scheduleTick(pos, this, LOOK_EVERY);
				}
			}
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.TRY_WITH_EMPTY_HAND;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		return InteractionResult.PASS;
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		int cups = state.getValue(CUPS);
		if (cups <= 0) {
			return;
		}
		boolean poured = false;
		for (Player player : level.getEntitiesOfClass(Player.class, new AABB(pos).inflate(RANGE))) {
			if (seated(player)) {
				player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, CALM_TICKS, 0));
				BodyHeat.Heat heat = BodyHeat.heat(player);
				player.setAttached(BodyHeat.HEAT, new BodyHeat.Heat(Math.min(BodyHeat.MAX, heat.body() + WARMTH), heat.ambient(), heat.air()));
				poured = true;
			}
		}
		if (poured) {
			cups--;
			level.setBlock(pos, state.setValue(CUPS, cups), Block.UPDATE_CLIENTS);
			level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 2, 0.05, 0.02, 0.05, 0.01);
			level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 0.6F, 1.1F);
		}
		if (cups > 0) {
			level.scheduleTick(pos, this, LOOK_EVERY);
		}
	}

	/** Sitting on a cushion (a seat Kingdoms puts by its tables and benches; the vanilla cushion entity). */
	private static boolean seated(Player player) {
		Entity vehicle = player.getVehicle();
		return vehicle != null && vehicle.getType() == EntityTypes.CUSHION;
	}
}
