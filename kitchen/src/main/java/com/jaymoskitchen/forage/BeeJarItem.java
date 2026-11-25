package com.jaymoskitchen.forage;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.bee.Bee;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity.Occupant;

/**
 * An empty bee jar. Used on a bee it catches it (the jar then holds the bee: {@link JarredBeeItem}). Like bottling a
 * fairy: the bee keeps its age and anything it was carrying is dropped. A bee out working for a hive is caught too, and
 * its hive then has room for another.
 */
public class BeeJarItem extends Item {
	public BeeJarItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
		if (!(target instanceof Bee bee) || !bee.isAlive()) {
			return InteractionResult.PASS;
		}
		if (!player.level().isClientSide()) {
			HiveWorker.abandon(bee);
			ItemStack jar = new ItemStack(Forage.JARRED_BEE);
			jar.set(Forage.JARRED_BEE_DATA, Occupant.of(bee));
			bee.discard();
			player.level().playSound(null, bee.blockPosition(), SoundEvents.BOTTLE_FILL, SoundSource.PLAYERS, 1.0F, 1.2F);
			stack.consume(1, player);
			if (!player.getInventory().add(jar)) {
				Block.popResource(player.level(), player.blockPosition(), jar);
			}
		}
		return InteractionResult.SUCCESS;
	}
}
