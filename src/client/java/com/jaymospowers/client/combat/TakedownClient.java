package com.jaymospowers.client.combat;

import com.jaymospowers.combat.Takedowns;
import com.jaymospowers.power.chrono.ChronoFlow;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.21.23, Kingdoms Rivals patch 9) Sneak + attack, bare-handed, for Chrono's and Web's takedowns
 * ({@link Takedowns}): the click is the takedown's, not a punch. Time Flow's own attack hook asks here first
 * ({@code ChronoFlowClient}); for a web slinger this one does. Chrono's reaches out to 24 blocks (the blink at a marked
 * foe); Web's is the foe under the crosshair.
 */
public final class TakedownClient {
	private TakedownClient() {
	}

	public static void init() {
		ClientPreAttackCallback.EVENT.register((minecraft, player, clicks) -> clicks > 0 && !ChronoFlow.on(player) && tryTakedown(minecraft, player));
	}

	/** If this click is a takedown, asks for it and swallows the click. */
	public static boolean tryTakedown(Minecraft minecraft, LocalPlayer player) {
		if (!Takedowns.mayAsk(player)) {
			return false;
		}
		LivingEntity target = target(minecraft, player, ChronoFlow.on(player) ? Takedowns.ASK_RANGE : 6.0);
		if (target == null) {
			return false;
		}
		ClientPlayNetworking.send(new Takedowns.Ask(target.getId()));
		return true;
	}

	private static @Nullable LivingEntity target(Minecraft minecraft, LocalPlayer player, double range) {
		if (minecraft.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof LivingEntity living) {
			return living;
		}
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getViewVector(1.0F).scale(range));
		AABB box = player.getBoundingBox().expandTowards(player.getViewVector(1.0F).scale(range)).inflate(1.0);
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, box,
			(Entity e) -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && e != player, range * range);
		return hit != null && hit.getEntity() instanceof LivingEntity living ? living : null;
	}
}
