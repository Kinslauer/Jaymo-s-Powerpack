package com.jaymospowers.mixin;

import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.world.entity.Entity;

/**
 * Diagnostic only: vanilla discards a non-finite rotation in Entity.setYRot / setXRot with a bare "Invalid entity
 * rotation: NaN, discarding." line and no stack trace. This logs the entity and a stack trace for the first few, so a
 * log names the caller. Behaviour is unchanged (vanilla still discards the value).
 */
@Mixin(Entity.class)
abstract class NanRotationDiagnosticMixin {
	/** Traces logged per run, all entity types together. */
	private static final int MAX_TRACES = 5;
	private static final Logger LOGGER = LoggerFactory.getLogger("Jaymo's Powers");
	/** Entity types already traced this run (one trace per type). */
	private static final Set<String> TRACED_TYPES = new HashSet<>();
	private static int traces = 0;

	@Inject(method = "setYRot", at = @At("HEAD"))
	private void jaymospowers$nanYaw(float yRot, CallbackInfo ci) {
		if (!Float.isFinite(yRot)) jaymospowers$report("setYRot", yRot);
	}

	@Inject(method = "setXRot", at = @At("HEAD"))
	private void jaymospowers$nanPitch(float xRot, CallbackInfo ci) {
		if (!Float.isFinite(xRot)) jaymospowers$report("setXRot", xRot);
	}

	private void jaymospowers$report(String setter, float value) {
		Entity self = (Entity)(Object)this;
		String type = self.getType().toString();
		synchronized (TRACED_TYPES) {
			if (traces >= MAX_TRACES || !TRACED_TYPES.add(type)) return;
			traces++;
		}
		LOGGER.warn("Non-finite rotation {} passed to Entity.{}: type={} id={} name={} pos={} level={}",
				value, setter, type, self.getId(), self.getName().getString(), self.position(),
				self.level().dimension(), new Throwable("NaN rotation source"));
	}
}
