package com.jaymoskingdoms.client.mixin;

import com.jaymoskingdoms.forms.Lasso;
import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hogtied, in first person: you see from where your head lies, down on the ground (LassoPose lays the body on its side). */
@Mixin(Camera.class)
abstract class LassoCameraMixin {
	/** The eyes of a body lying on its side are this high (blocks). */
	private static final double TIED_EYES = 0.35;

	@Shadow
	private boolean detached;
	@Shadow
	private Entity entity;

	@Shadow
	public abstract Vec3 position();

	@Shadow
	protected abstract void setPosition(Vec3 position);

	@Inject(method = "alignWithEntity", at = @At("TAIL"))
	private void jaymoskingdoms$tiedView(float partialTicks, CallbackInfo ci) {
		Integer tied = this.entity instanceof Player p ? p.getAttached(Lasso.TIED) : null;
		if (this.detached || !(this.entity instanceof Player player) || player.isPassenger() || tied == null || tied < 0
			|| com.jaymoskingdoms.PowersCompat.carried(player)) {
			return;
		}
		Vec3 at = this.position();
		this.setPosition(new Vec3(at.x, Mth.lerp(partialTicks, player.yo, player.getY()) + TIED_EYES, at.z));
	}
}
