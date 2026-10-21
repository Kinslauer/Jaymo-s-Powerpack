package com.jaymospowers.aether.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import java.util.function.BiConsumer;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * A vase (holystone, veradexian or brexallen): a pot that shatters at a touch, a hit by an arrow or a blast, and gives up what it
 * holds, rolled from its loot table at that moment. With Silk Touch it comes away whole (and empty).
 */
public class VaseBlock extends BaseEntityBlock {
	private static final VoxelShape SHAPE = net.minecraft.world.level.block.Block.box(3.0, 0.0, 3.0, 13.0, 14.0, 13.0);

	public VaseBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new VaseEntity(pos, state);
	}

	/** Breaks it open: the contents spill, with a crash of pottery. */
	public static void shatter(ServerLevel level, BlockPos pos, BlockState state) {
		if (level.getBlockEntity(pos) instanceof VaseEntity vase) {
			vase.unpackLootTable(null);
			Containers.dropContents(level, pos, vase);
			vase.clearContent();
		}
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 20, 0.25, 0.3, 0.25, 0.05);
		level.playSound(null, pos, SoundEvents.DECORATED_POT_SHATTER, SoundSource.BLOCKS, 1.0F, 1.0F);
	}

	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		if (level instanceof ServerLevel server && !player.isCreative()
			&& !net.minecraft.world.item.enchantment.EnchantmentHelper.hasTag(player.getMainHandItem(), net.minecraft.tags.EnchantmentTags.PREVENTS_DECORATED_POT_SHATTERING)) {
			shatter(server, pos, state);
		}
		return super.playerWillDestroy(level, pos, state, player);
	}

	@Override
	protected void onProjectileHit(Level level, BlockState state, BlockHitResult hit, Projectile projectile) {
		if (level instanceof ServerLevel server) {
			BlockPos pos = hit.getBlockPos();
			shatter(server, pos, state);
			server.removeBlock(pos, false);
		}
	}

	@Override
	protected void onExplosionHit(BlockState state, ServerLevel level, BlockPos pos, Explosion explosion, BiConsumer<ItemStack, BlockPos> drops) {
		shatter(level, pos, state);
		super.onExplosionHit(state, level, pos, explosion, drops);
	}
}
