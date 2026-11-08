package com.jaymoskingdoms.mixin.dungeon;

import com.jaymoskingdoms.dungeon.NoBuildDungeons;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ServerExplosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * (Andy, 2026-11-08) a player's blast doesn't break the walls of a tagged no-build dungeon
 * ({@link NoBuildDungeons}): blocks inside one are spared from the blast.
 */
@Mixin(ServerExplosion.class)
public class ServerExplosionNoBuildMixin {
    @ModifyVariable(method = "interactWithBlocks", at = @At("HEAD"), argsOnly = true)
    private List<BlockPos> jaymoskingdoms$spareDungeonWalls(List<BlockPos> blown) {
        ServerExplosion self = (ServerExplosion) (Object) this;
        if (self.getBlockInteraction() == Explosion.BlockInteraction.KEEP) {
            return blown;
        }
        Entity source = self.getIndirectSourceEntity();
        if (!(source instanceof ServerPlayer)) {
            return blown;
        }
        ServerLevel level = self.level();
        blown.removeIf(pos -> NoBuildDungeons.is(level, pos));
        return blown;
    }
}
