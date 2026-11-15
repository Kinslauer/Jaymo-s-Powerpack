package com.jaymoskingdoms.client.mixin;

import com.jaymoskingdoms.client.SpiderSkins;
import com.jaymoskingdoms.wild.SpiderKinds;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.monster.spider.SpiderModel;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (medieval picks, de-netcaster) The netcaster stands high on long thin legs, like the real net-casting spiders: each leg
 * drawn {@link #LONGER} times its length (the knee and shin with it, {@link SpiderLegsMixin}) and the body raised
 * {@link #RAISE} pixels to stand on them. Every other spider is left as it is.
 */
@Mixin(value = SpiderModel.class, priority = 1100)
abstract class NetcasterLegsMixin {
	@Unique
	private static final float LONGER = 1.4F, RAISE = 3.5F;
	@Unique
	private static final String[] LEGS = {"right_hind_leg", "left_hind_leg", "right_middle_hind_leg", "left_middle_hind_leg",
		"right_middle_front_leg", "left_middle_front_leg", "right_front_leg", "left_front_leg"};
	@Unique
	private static final String[] BODY = {"head", "body0", "body1"};

	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;)V", at = @At("TAIL"))
	private void jaymoskingdoms$longLegs(LivingEntityRenderState state, CallbackInfo ci) {
		ModelPart root = ((EntityModel<?>) (Object) this).root();
		boolean netcaster = state.getData(SpiderSkins.KIND) == SpiderKinds.Kind.NETCASTER;
		for (String name : LEGS) {
			ModelPart leg = root.getChild(name);
			leg.xScale = netcaster ? LONGER : 1.0F;
			if (netcaster) {
				leg.y -= RAISE;
			}
		}
		if (netcaster) {
			for (String name : BODY) {
				root.getChild(name).y -= RAISE;
			}
		}
	}
}
