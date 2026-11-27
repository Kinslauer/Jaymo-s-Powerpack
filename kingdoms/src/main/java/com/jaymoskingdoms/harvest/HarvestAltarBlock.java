package com.jaymoskingdoms.harvest;

import com.jaymoskingdoms.Fx;
import com.jaymoskingdoms.KitchenCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The altar in a ruined barn ({@code tools/small_designs/wicked_barn.py}). Wicked resin ({@link #RESIN_NEEDED} of it, from the
 * witch's cauldron) laid on it raises the Harvest Lich, but the altar only answers in late autumn, at night, once the
 * harvest is in (Kitchen's calendar, {@link KitchenCompat#lateAutumn}); at any other time it stays cold: a little pale smoke
 * and the resin kept. Only one lich stands at a time within {@link #ONE_LICH_RANGE} blocks.
 */
public final class HarvestAltarBlock extends Block {
	public static final int RESIN_NEEDED = 2;
	public static final double ONE_LICH_RANGE = 96.0;
	private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 12, 16);

	public HarvestAltarBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected InteractionResult useItemOn(ItemStack held, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
		BlockHitResult hit) {
		if (!held.is(WickedHarvest.WICKED_RESIN) || held.getCount() < RESIN_NEEDED) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		if (!(level instanceof ServerLevel server)) {
			return InteractionResult.SUCCESS;
		}
		Vec3 top = Vec3.atCenterOf(pos).add(0.0, 0.8, 0.0);
		boolean answers = KitchenCompat.lateAutumn(server) && WitchCauldronBlock.night(server)
			&& server.getEntitiesOfClass(HarvestLich.class, new AABB(pos).inflate(ONE_LICH_RANGE)).isEmpty();
		if (!answers) {
			server.playSound(null, pos, SoundEvents.SOUL_ESCAPE.value(), SoundSource.BLOCKS, 0.6F, 0.6F);
			Fx.pool(server, top, 0.6F, 0x8A8F99, 40);
			return InteractionResult.SUCCESS;
		}
		held.consume(RESIN_NEEDED, player);
		raise(server, top);
		return InteractionResult.SUCCESS;
	}

	/** The lich climbs out of the floor above the altar. */
	static void raise(ServerLevel level, Vec3 top) {
		HarvestLich lich = WickedHarvest.LICH.create(level, EntitySpawnReason.TRIGGERED);
		if (lich == null) {
			return;
		}
		lich.snapTo(top.x, top.y, top.z, level.getRandom().nextFloat() * 360.0F, 0.0F);
		level.addFreshEntity(lich);
		Fx.sigil(level, top, 3.0F, WickedHarvest.FIRE, 80);
		Fx.blast(level, top.add(0.0, 1.0, 0.0), 2.5F, WickedHarvest.SOUL);
		level.playSound(null, lich.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 1.0F, 0.6F);
	}
}
