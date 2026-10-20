package com.jaymospowers.aether;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.enchantment.EnchantmentHelper;
import net.minecraft.world.enchantment.Enchantments;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** An ore that gives experience when mined properly (what it drops is its loot table). No experience with Silk Touch. */
public class AetherOreBlock extends Block {
	private final int minXp, maxXp;

	public AetherOreBlock(int minXp, int maxXp, BlockBehaviour.Properties properties) {
		super(properties);
		this.minXp = minXp;
		this.maxXp = maxXp;
	}

	@Override
	public void playerDestroy(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity, ItemStack destroyedWith) {
		super.playerDestroy(level, player, pos, state, blockEntity, destroyedWith);
		if (this.maxXp <= 0 || !destroyedWith.isCorrectToolForDrops(state)) {
			return;
		}
		int silk = EnchantmentHelper.getEnchantmentsForCrafting(destroyedWith)
			.getLevel(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH));
		int xp = this.minXp + level.getRandom().nextInt(this.maxXp - this.minXp + 1);
		if (silk <= 0 && xp > 0) {
			ExperienceOrb.award(level, Vec3.atCenterOf(pos), xp);
		}
	}
}
