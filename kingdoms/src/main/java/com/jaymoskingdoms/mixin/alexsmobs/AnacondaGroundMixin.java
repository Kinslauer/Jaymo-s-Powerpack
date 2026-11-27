package com.jaymoskingdoms.mixin.alexsmobs;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * An Alex's Mobs anaconda's body piece, once placed behind the one before it, is lifted onto the ground if it ended
 * up inside it. Alex's own lift only climbs out of the one block the piece's middle is in, so a piece that the bend of
 * the body had carried two blocks into a slope stayed buried. This climbs up to {@link #CLIMB} blocks, and the next
 * piece follows from the lifted one. Skipped without Alex's Mobs.
 */
@Pseudo
@Mixin(targets = "com.github.alexthe666.alexsmobs.entity.EntityAnacondaPart", remap = false)
abstract class AnacondaGroundMixin {
	/** How many blocks a buried piece may climb. */
	@Unique
	private static final int CLIMB = 3;

	@Inject(method = "tickMultipartPosition", at = @At("RETURN"), cancellable = true, require = 0)
	private void jaymoskingdoms$onTheGround(CallbackInfoReturnable<Vec3> cir) {
		Entity part = (Entity) (Object) this;
		Vec3 at = cir.getReturnValue();
		if (at == null || part.noPhysics) {
			return;
		}
		Level level = part.level();
		double y = at.y;
		BlockPos.MutableBlockPos p = BlockPos.containing(at.x, y + 1.0E-4, at.z).mutable();
		for (int i = 0; i < CLIMB; i++) {
			BlockState state = level.getBlockState(p);
			if (!state.getFluidState().isEmpty() || !state.isSuffocating(level, p)) {
				break;
			}
			VoxelShape shape = state.getCollisionShape(level, p);
			if (shape.isEmpty()) {
				break;
			}
			y = Math.max(y, p.getY() + shape.max(net.minecraft.core.Direction.Axis.Y));
			p.move(0, 1, 0);
		}
		if (y > at.y) {
			Vec3 lifted = new Vec3(at.x, y, at.z);
			part.snapTo(lifted.x, lifted.y, lifted.z, part.getYRot(), part.getXRot());
			cir.setReturnValue(lifted);
		}
	}
}
