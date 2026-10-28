package com.jaymoskingdoms.client.mixin.mca;

import com.jaymoskingdoms.BodySizes;
import com.jaymoskingdoms.SkinTones;
import com.jaymoskingdoms.VillagerHeritage;
import com.jaymoskingdoms.client.EditorFigure;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.entity.ai.Genetics;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * (0.7.236) Andy: "the sliders should only allow the color ranges that the heritage allows". On the figure of your
 * own character in MCA's editor ({@link EditorFigure}), melanin set by the skin picker is kept within your heritage's
 * range ({@link SkinTones#skinRange}), so that is what the editor saves. (0.7.290) Its Size and Width sliders too, to the
 * heritage's body ({@link BodySizes}).
 */
@Mixin(Genetics.class)
abstract class McaEditorSkinMixin {
	@Shadow
	@Final
	private VillagerLike<?> entity;

	@ModifyVariable(method = "setGene", at = @At("HEAD"), argsOnly = true)
	private float jaymoskingdoms$heritageSkin(float value, Genetics.GeneType type) {
		if (this.entity == null || this.entity.asEntity() != EditorFigure.current()) {
			return value;
		}
		if (type == Genetics.MELANIN) {
			return SkinTones.clampSkin(VillagerHeritage.of(this.entity.asEntity()), value);
		} else if (type == Genetics.SIZE) {
			return BodySizes.clampSize(VillagerHeritage.of(this.entity.asEntity()), value);
		} else if (type == Genetics.WIDTH) {
			return BodySizes.clampWidth(VillagerHeritage.of(this.entity.asEntity()), value);
		}
		return value;
	}
}
