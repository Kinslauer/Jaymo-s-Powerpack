package com.jaymospowers.mixin;

import java.util.List;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A creature's changed attributes are handled from a copy of the dirty set, not the set itself. Handling one (max
 * health setting the health, scale refreshing the size) can make another dirty through other mods' hooks, and the set
 * growing under vanilla's loop broke its iterator: a NullPointerException in {@code refreshDirtyAttributes} that stopped
 * the Deck server (an ocelot, 2026-11-16 2:58 AM; a zombie, 2026-11-08). Attributes made dirty meanwhile are handled
 * in a later round rather than dropped.
 */
@Mixin(LivingEntity.class)
abstract class DirtyAttributesMixin {
	/** Rounds per refresh; anything still dirty after them waits for the next refresh. */
	private static final int ROUNDS = 4;

	@Shadow
	public abstract AttributeMap getAttributes();

	@Shadow
	protected abstract void onAttributeUpdated(Holder<Attribute> attribute);

	@Inject(method = "refreshDirtyAttributes", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$refreshFromCopy(CallbackInfo ci) {
		Set<AttributeInstance> dirty = this.getAttributes().getAttributesToUpdate();
		for (int round = 0; round < ROUNDS && !dirty.isEmpty(); round++) {
			List<AttributeInstance> now = List.copyOf(dirty);
			dirty.clear();
			for (AttributeInstance attribute : now) {
				this.onAttributeUpdated(attribute.getAttribute());
			}
		}
		ci.cancel();
	}
}
