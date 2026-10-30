package com.jaymoskingdoms.mixin.mca;

import com.jaymoskingdoms.Bearers;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Optional;
import net.conczin.mca.entity.ai.Relationship;
import net.conczin.mca.server.world.data.GraveyardManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * (0.7.391) Andy: "villagers / bandits should not instantly get tombstones". The tick after an MCA villager dies, MCA lays
 * them in the nearest empty tombstone within 10 blocks, and one with family or a player's hearts gets a fresh tombstone
 * raised where they fell; bandits and raiders too, since MCA doesn't count them hostile. With Kitchen's corpses the body
 * lies there instead, so the stone stood over a corpse. Now, while the dead are seen to ({@link Bearers#seenTo}), MCA
 * finds no tombstone and raises none: the body stays where it fell until a bearer carries it to the graveyard and digs
 * its grave (or burns it, if it was an outlaw's). MCA's mourning still runs, with no grave to go to.
 */
@Mixin(Relationship.class)
abstract class McaTombstoneMixin {
	@WrapOperation(method = "finalizeDeath", at = @At(value = "INVOKE",
		target = "Lnet/conczin/mca/server/world/data/GraveyardManager;findNearest(Lnet/minecraft/core/BlockPos;Lnet/conczin/mca/server/world/data/GraveyardManager$TombstoneState;I)Ljava/util/Optional;"))
	private Optional<BlockPos> jaymoskingdoms$noEmptyStone(GraveyardManager graves, BlockPos pos, GraveyardManager.TombstoneState state, int range,
		Operation<Optional<BlockPos>> original) {
		return Bearers.seenTo() ? Optional.empty() : original.call(graves, pos, state, range);
	}

	@Inject(method = "placeTombstone", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$noNewStone(ServerLevel level, BlockPos pos, CallbackInfoReturnable<Optional<BlockPos>> cir) {
		if (Bearers.seenTo()) {
			cir.setReturnValue(Optional.empty());
		}
	}
}
