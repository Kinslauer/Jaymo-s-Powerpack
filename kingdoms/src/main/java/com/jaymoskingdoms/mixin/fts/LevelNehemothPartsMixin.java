package com.jaymoskingdoms.mixin.fts;

import com.jaymoskingdoms.inhabitants.fts.NehemothPart;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A Nehemoth's targetable parts ({@link NehemothPart}) are handed out with the entities near a box, as vanilla hands out the
 * ender dragon's: that is what a crosshair, an arrow and a mob's sight ask the level for. Vanilla has no hook for it but the
 * dragon, so this is it.
 */
@Mixin(Level.class)
abstract class LevelNehemothPartsMixin {
	@Inject(method = "getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;",
		at = @At("RETURN"))
	private void jaymoskingdoms$nehemothParts(@Nullable Entity except, AABB box, Predicate<? super Entity> selector, CallbackInfoReturnable<List<Entity>> cir) {
		NehemothPart.addNear((Level)(Object)this, except, box, selector, cir.getReturnValue());
		// the dragons' parts too (their heads, necks, tails and wings), the same way
		com.jaymoskingdoms.dragons.DragonPart.addNear((Level)(Object)this, except, box, selector, cir.getReturnValue());
	}
}
