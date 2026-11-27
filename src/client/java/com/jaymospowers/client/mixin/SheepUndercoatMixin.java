package com.jaymospowers.client.mixin;

import com.jaymospowers.client.EmfCompat;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.animal.sheep.SheepModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.layers.SheepWoolUndercoatLayer;
import net.minecraft.client.renderer.entity.state.SheepRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The game draws a dyed sheep's undercoat (a coat in its wool colour, shown once it's sheared) on every dyed sheep,
 * woolly or not. Fresh Animations reshapes the sheep and its wool but has no undercoat model, so the undercoat stays
 * vanilla's shape in vanilla's place and pokes out of the reshaped sheep as stray boxes. Pixel Perfection Fidelity's
 * undercoat is a solid coat (vanilla's is a few specks), so those boxes show plainly on every grey, black or dyed sheep.
 *
 * <p>Under wool the undercoat is never seen, so it isn't drawn there at all. On a sheared sheep it's left off only while
 * a resource pack has reshaped the sheep without giving the undercoat a matching shape: the sheep then shows its bare
 * skin, as sheared sheep did before the undercoat came in.
 */
@Mixin(SheepWoolUndercoatLayer.class)
abstract class SheepUndercoatMixin extends RenderLayer<SheepRenderState, SheepModel> {
	@Shadow
	@Final
	private EntityModel<SheepRenderState> model;

	private SheepUndercoatMixin(RenderLayerParent<SheepRenderState, SheepModel> parent) {
		super(parent);
	}

	@Inject(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/SheepRenderState;FF)V",
		at = @At("HEAD"), cancellable = true)
	private void jaymospowers$onlyWhereItFits(PoseStack pose, SubmitNodeCollector collector, int light, SheepRenderState state,
			float yRot, float xRot, CallbackInfo ci) {
		if (!state.isSheared || EmfCompat.customModel(getParentModel()) && !EmfCompat.customModel(model)) {
			ci.cancel();
		}
	}
}
