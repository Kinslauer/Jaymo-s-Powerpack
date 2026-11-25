package com.jaymoskitchen.forage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity.Occupant;
import net.minecraft.world.phys.Vec3;

/**
 * A bee in a jar. Used on a Harvest Hive with room it lets the bee in; used on anything else it lets the bee go beside
 * the block. Either way the jar comes back empty.
 */
public class JarredBeeItem extends Item {
	public JarredBeeItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Occupant bee = context.getItemInHand().get(Forage.JARRED_BEE_DATA);
		if (bee == null || !(context.getLevel() instanceof ServerLevel level)) {
			return InteractionResult.SUCCESS;
		}
		BlockPos pos = context.getClickedPos();
		Player player = context.getPlayer();
		if (level.getBlockEntity(pos) instanceof HarvestHiveEntity hive) {
			if (!hive.add(bee)) {
				return InteractionResult.FAIL;
			}
			level.playSound(null, pos, SoundEvents.BEEHIVE_ENTER, SoundSource.BLOCKS, 1.0F, 1.0F);
		} else {
			Direction face = context.getClickedFace();
			Entity entity = bee.createEntity(level, pos);
			if (entity == null) {
				return InteractionResult.FAIL;
			}
			Vec3 at = Vec3.atCenterOf(pos).relative(face, 0.8);
			entity.snapTo(at.x, at.y, at.z, level.getRandom().nextFloat() * 360.0F, 0.0F);
			level.addFreshEntity(entity);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.BEEHIVE_EXIT, SoundSource.NEUTRAL, 1.0F, 1.0F);
		}
		ItemStack jar = new ItemStack(Forage.BEE_JAR);
		context.getItemInHand().consume(1, player);
		if (player == null || !player.getInventory().add(jar)) {
			Block.popResource(level, pos.above(), jar);
		}
		return InteractionResult.SUCCESS;
	}
}
