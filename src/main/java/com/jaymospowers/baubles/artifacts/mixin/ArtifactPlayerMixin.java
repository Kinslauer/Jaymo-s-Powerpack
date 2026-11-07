package com.jaymospowers.baubles.artifacts.mixin;

import com.jaymospowers.baubles.artifacts.ArtifactBaubles;
import com.jaymospowers.baubles.artifacts.ArtifactEffects;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * (Artifacts) Digging claws: bare-handed, you dig as with a stone tool, so stone, coal, copper, iron and lapis drop
 * (not what needs iron or better).
 */
@Mixin(Player.class)
public abstract class ArtifactPlayerMixin {
	@ModifyReturnValue(method = "hasCorrectToolForDrops", at = @At("RETURN"))
	private boolean jaymospowers$diggingClaws(boolean original, BlockState state) {
		Player self = (Player) (Object) this;
		if (original || !self.getMainHandItem().isEmpty() || !ArtifactEffects.wears(self, ArtifactBaubles.DIGGING_CLAWS)) {
			return original;
		}
		boolean dug = state.is(BlockTags.MINEABLE_WITH_PICKAXE) || state.is(BlockTags.MINEABLE_WITH_SHOVEL);
		return dug && !state.is(BlockTags.NEEDS_IRON_TOOL) && !state.is(BlockTags.NEEDS_DIAMOND_TOOL);
	}
}
