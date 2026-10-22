package com.jaymospowers.power.xenoform.nadir;

import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Who and where Nadir's moves land: the same rules as every form's ({@code XenoForm.aimed}, {@code spared}). */
public final class NadirAim {
	private NadirAim() {
	}

	/** The player's own pets and mounts, and anyone on their team. */
	public static boolean spared(ServerPlayer player, Entity e) {
		return e instanceof OwnableEntity pet && pet.getOwner() == player || player.isAlliedTo(e);
	}

	public static boolean fair(ServerPlayer player, Entity e) {
		return e instanceof LivingEntity living && living.isAlive() && e != player && !e.isSpectator() && !(e instanceof ArmorStand)
			&& !e.isPassengerOfSameVehicle(player) && !spared(player, e);
	}

	/** The creature under the crosshair within {@code reach}, not behind a wall, or null. */
	public static @Nullable LivingEntity aimed(ServerPlayer player, double reach) {
		Vec3 eye = player.getEyePosition();
		Vec3 end = block(player, reach).getLocation();
		AABB box = player.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0);
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(player.level(), player, eye, end, box, e -> fair(player, e), 0.3F);
		return hit != null && hit.getEntity() instanceof LivingEntity living ? living : null;
	}

	/** Where the crosshair meets a block within {@code reach} (a miss, at full reach, if it meets nothing). */
	public static BlockHitResult block(ServerPlayer player, double reach) {
		Vec3 eye = player.getEyePosition();
		return player.level().clip(new ClipContext(eye, eye.add(player.getLookAngle().scale(reach)), ClipContext.Block.COLLIDER,
			ClipContext.Fluid.NONE, player));
	}

	public static boolean missed(BlockHitResult hit) {
		return hit.getType() == HitResult.Type.MISS;
	}

	/** Every creature within {@code radius} of {@code at} that Nadir may hit. */
	public static List<LivingEntity> around(ServerPlayer player, Vec3 at, double radius) {
		return player.level().getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius),
			e -> fair(player, e) && e.position().distanceToSqr(at) <= radius * radius);
	}
}
