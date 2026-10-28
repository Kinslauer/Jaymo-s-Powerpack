package com.jaymoskingdoms.client.mixin.mca;

import com.jaymoskingdoms.HeritageVariants;
import com.jaymoskingdoms.Verdants;
import net.conczin.mca.client.render.VillagerVisuals;
import net.conczin.mca.client.render.layer.SkinLayer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * (0.7.23) A Verdant villager's skin is their shade of green instead of MCA's melanin colours ({@link Verdants}).
 * (0.7.214) Anyone else's (players are drawn by MCA too) as their heritage wears it: a Stoneborn's greyed, a variant's
 * tinted ({@link HeritageVariants#skin}).
 */
@Mixin(SkinLayer.class)
abstract class McaVerdantSkinMixin {
	/** (0.7.304) A player, or the figure of one on MCA's creator or the life story. */
	@Unique
	private static boolean yours(Entity entity) {
		return entity instanceof net.minecraft.world.entity.player.Player || entity == com.jaymoskingdoms.client.EditorFigure.current();
	}

	@Inject(method = "getColor(Lnet/conczin/mca/client/render/VillagerVisuals;F)I", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$verdantSkin(VillagerVisuals visuals, float partialTicks, CallbackInfoReturnable<Integer> cir) {
		Minecraft minecraft = Minecraft.getInstance();
		Entity entity = minecraft.level == null ? null : minecraft.level.getEntity(visuals.entityId());
		if (entity == null) {
			// (0.7.236) the figure on MCA's editor, which isn't in the world
			entity = com.jaymoskingdoms.client.EditorFigure.byId(visuals.entityId());
		}
		// (0.7.304) a player's green (and their figure's) follows MCA's skin instead: see below
		if (entity != null && !yours(entity)) {
			int green = Verdants.skin(entity);
			if (green == 0) {
				// (0.7.292) a goblin's or an orc's own green
				green = com.jaymoskingdoms.Greenskins.skin(entity);
			}
			if (green != 0) {
				cir.setReturnValue(green);
			}
		}
	}

	@Inject(method = "getColor(Lnet/conczin/mca/client/render/VillagerVisuals;F)I", at = @At("RETURN"), cancellable = true)
	private void jaymoskingdoms$heritageSkin(VillagerVisuals visuals, float partialTicks, CallbackInfoReturnable<Integer> cir) {
		Minecraft minecraft = Minecraft.getInstance();
		Entity entity = minecraft.level == null ? null : minecraft.level.getEntity(visuals.entityId());
		if (entity == null) {
			// (0.7.236) the figure on MCA's editor, which isn't in the world
			entity = com.jaymoskingdoms.client.EditorFigure.byId(visuals.entityId());
		}
		if (entity != null && yours(entity) && com.jaymoskingdoms.VillagerHeritage.of(entity) == com.jaymoskingdoms.Heritage.Kind.VERDANT) {
			// (0.7.304) a Verdant player's shade of green, as dark as MCA's skin slider sets it (as a Rubber-folk's pink is)
			cir.setReturnValue(Verdants.fromSkin(entity, cir.getReturnValueI()));
		} else if (entity != null && !Verdants.is(entity)) {
			int color = cir.getReturnValueI();
			int tinted = HeritageVariants.skin(entity, color);
			if (tinted != color) {
				cir.setReturnValue(tinted);
			}
		}
	}
}
