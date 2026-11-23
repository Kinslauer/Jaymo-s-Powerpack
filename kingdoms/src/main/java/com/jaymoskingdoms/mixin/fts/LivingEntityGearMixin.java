package com.jaymoskingdoms.mixin.fts;

import com.jaymoskingdoms.inhabitants.fts.FtsGear;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The armour of From the Shadows' bonuses that change a blow: the crust set, worn crouching, takes half of it
 * ({@link FtsGear#crouched}); and the diabolium set's wearer's blow tears for as long as it lands (Powers' wounds open a
 * cut for an attacker carrying the tag {@code jaymospowers.tears}).
 */
@Mixin(LivingEntity.class)
abstract class LivingEntityGearMixin {
	private static final String TEARS = "jaymospowers.tears";

	@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true)
	private float jaymoskingdoms$crustCrouch(float amount, ServerLevel level, DamageSource source) {
		return FtsGear.crouched((LivingEntity) (Object) this, source, amount);
	}

	@Inject(method = "hurtServer", at = @At("HEAD"))
	private void jaymoskingdoms$diaboliumTears(ServerLevel level, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
		if (source.getDirectEntity() instanceof LivingEntity wearer && source.getEntity() == wearer && FtsGear.diaboliumSet(wearer) && !wearer.entityTags().contains(TEARS)) {
			wearer.addTag(TEARS);
			wearer.addTag(TEARS + ".diabolium");
		}
	}

	@Inject(method = "hurtServer", at = @At("RETURN"))
	private void jaymoskingdoms$diaboliumTearsOver(ServerLevel level, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
		Entity direct = source.getDirectEntity();
		if (direct != null && direct.entityTags().contains(TEARS + ".diabolium")) {
			direct.removeTag(TEARS + ".diabolium");
			direct.removeTag(TEARS);
		}
	}
}
