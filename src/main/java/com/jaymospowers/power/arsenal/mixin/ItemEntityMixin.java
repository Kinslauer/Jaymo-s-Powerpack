package com.jaymospowers.power.arsenal.mixin;

import com.jaymospowers.power.arsenal.ArsenalPower;
import com.jaymospowers.power.arsenal.Summoned;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A summoned weapon lying on the ground can't be picked up (by players, mobs or allays) and dissolves after a few seconds. */
@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin extends Entity {
	@Shadow
	public abstract ItemStack getItem();

	@Shadow
	public abstract int getAge();

	@Shadow
	public abstract void setNeverPickUp();

	private ItemEntityMixin(EntityType<?> type, Level level) {
		super(type, level);
	}

	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$arsenalDissolve(CallbackInfo ci) {
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		Summoned summoned = Summoned.of(this.getItem());
		if (summoned == null) {
			return;
		}
		this.setNeverPickUp();
		if (this.getAge() >= ArsenalPower.DROPPED_TICKS || level.getGameTime() >= summoned.expires()) {
			Summoned.dissolveEffects(level, this.position().add(0, 0.2, 0));
			this.discard();
			ci.cancel();
		}
	}
}
