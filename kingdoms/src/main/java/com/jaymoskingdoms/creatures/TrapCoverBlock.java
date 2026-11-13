package com.jaymoskingdoms.creatures;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A brush-covered pit's lid: sticks and leaf litter laid over a hole the diggers ({@link Digger}) dug, looking like the
 * ground round it. Anything with weight that walks onto it goes through, to the stakes below; a careful walker who is sneaking
 * does not break it. A watchful eye finds it first ({@link Warrens}).
 */
public class TrapCoverBlock extends Block {
	public TrapCoverBlock(Properties properties) {
		super(properties);
	}

	@Override
	public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
		if (level instanceof ServerLevel server && entity instanceof LivingEntity living && !living.isShiftKeyDown() && living.getBbWidth() > 0.3F
			 && !(living instanceof Digger)) {
			server.playSound(null, pos, SoundEvents.AZALEA_LEAVES_BREAK, SoundSource.BLOCKS, 1.0F, 0.7F);
			server.destroyBlock(pos, false);
			Warrens.alarm(server, pos, living);
		}
		super.stepOn(level, pos, state, entity);
	}
}
