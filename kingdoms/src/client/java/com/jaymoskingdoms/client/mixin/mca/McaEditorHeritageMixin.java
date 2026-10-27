package com.jaymoskingdoms.client.mixin.mca;

import com.jaymoskingdoms.Heritage;
import com.jaymoskingdoms.HeritageVariants;
import com.jaymoskingdoms.SkinTones;
import com.jaymoskingdoms.VillagerHeritage;
import com.jaymoskingdoms.client.EditorFigure;
import java.util.Objects;
import java.util.UUID;
import net.conczin.mca.client.gui.VillagerEditorScreen;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Genetics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (0.7.236) Andy: "apply the character's heritage to the character creator please". When MCA's editor is on your own
 * character (its start-up creator, which now comes after the life story, or the editor later), the figure you dress
 * wears your heritage and its look, as villagers of that blood are drawn: it's given your heritage and variant.
 */
@Mixin(VillagerEditorScreen.class)
abstract class McaEditorHeritageMixin {
	@Shadow
	@Final
	protected VillagerEntityMCA villager;

	@Shadow
	@Final
	UUID villagerUUID;

	@Shadow
	@Final
	UUID playerUUID;

	@Inject(method = "removed", at = @At("HEAD"))
	private void jaymoskingdoms$closed(CallbackInfo ci) {
		if (EditorFigure.current() == this.villager) {
			EditorFigure.set(null);
		}
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void jaymoskingdoms$yourBlood(CallbackInfo ci) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || this.villager == null || !this.villagerUUID.equals(this.playerUUID) || !player.getUUID().equals(this.playerUUID)) {
			return;
		}
		String heritage = player.getAttached(Heritage.HERITAGE), variant = player.getAttached(HeritageVariants.VARIANT);
		if (!Objects.equals(heritage, this.villager.getAttached(Heritage.HERITAGE))) {
			this.villager.setAttached(Heritage.HERITAGE, heritage);
		}
		if (!Objects.equals(variant, this.villager.getAttached(HeritageVariants.VARIANT))) {
			this.villager.setAttached(HeritageVariants.VARIANT, variant);
		}
		// (its skin kept to the heritage's range, McaEditorSkinMixin; one already outside it brought in)
		EditorFigure.set(this.villager);
		Genetics genes = this.villager.getGenetics();
		float melanin = genes.getGene(Genetics.MELANIN);
		if (SkinTones.clampSkin(VillagerHeritage.of(this.villager), melanin) != melanin) {
			genes.setGene(Genetics.MELANIN, SkinTones.clampSkin(VillagerHeritage.of(this.villager), melanin));
		}
	}
}
