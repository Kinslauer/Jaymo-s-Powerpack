package com.jaymoskingdoms.client.mixin.mca;

import com.jaymoskingdoms.BodySizes;
import com.jaymoskingdoms.Heritage;
import com.jaymoskingdoms.HeritageVariants;
import com.jaymoskingdoms.LifeStory;
import com.jaymoskingdoms.SkinTones;
import com.jaymoskingdoms.VillagerHeritage;
import com.jaymoskingdoms.client.EditorFigure;
import java.util.Objects;
import java.util.UUID;
import net.conczin.mca.client.gui.DestinyScreen;
import net.conczin.mca.client.gui.VillagerEditorScreen;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Genetics;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
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

	/** (0.7.304) Your character's data is in from the server, and the story's gender has been put on it since. */
	@Unique
	private boolean jaymoskingdoms$loaded, jaymoskingdoms$gendered;

	@Shadow
	private void updateGender(Gender gender) {
	}

	@Inject(method = "setVillagerData", at = @At("TAIL"))
	private void jaymoskingdoms$loaded(CompoundTag data, CallbackInfo ci) {
		this.jaymoskingdoms$loaded = true;
	}

	/** (0.7.304) Andy: "you only need the first pick prompt": the start-up creator shows no gender buttons once the story picked it. */
	@Inject(method = "drawGender", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$pickedAlready(int x, int y, CallbackInfo ci) {
		LocalPlayer player = Minecraft.getInstance().player;
		if ((Object)this instanceof DestinyScreen && player != null && player.getAttached(LifeStory.GENDER) != null) {
			ci.cancel();
		}
	}

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
		// (0.7.304) Andy: "The first screen should be choosing your gender": MCA's start-up creator begins on the gender the
		// story's first page picked (once the server's data for you is in, so it isn't written over; its gender buttons are gone)
		String gender = player.getAttached(LifeStory.GENDER);
		if (!this.jaymoskingdoms$gendered && this.jaymoskingdoms$loaded && gender != null && (Object)this instanceof DestinyScreen) {
			Gender want = LifeStory.FEMALE.equals(gender) ? Gender.FEMALE : Gender.MALE;
			if (this.villager.getGenetics().getGender() == want) {
				this.jaymoskingdoms$gendered = true;
			} else {
				// (MCA takes one editor change at a time: tried again next tick if one was still on its way)
				updateGender(want);
			}
		}
		// (its skin kept to the heritage's range, McaEditorSkinMixin; one already outside it brought in)
		EditorFigure.set(this.villager);
		Genetics genes = this.villager.getGenetics();
		float melanin = genes.getGene(Genetics.MELANIN);
		if (SkinTones.clampSkin(VillagerHeritage.of(this.villager), melanin) != melanin) {
			genes.setGene(Genetics.MELANIN, SkinTones.clampSkin(VillagerHeritage.of(this.villager), melanin));
		}
		// (0.7.290) and its height and width to the heritage's body (BodySizes)
		float size = genes.getGene(Genetics.SIZE), width = genes.getGene(Genetics.WIDTH);
		if (BodySizes.clampSize(VillagerHeritage.of(this.villager), size) != size) {
			genes.setGene(Genetics.SIZE, BodySizes.clampSize(VillagerHeritage.of(this.villager), size));
		}
		if (BodySizes.clampWidth(VillagerHeritage.of(this.villager), width) != width) {
			genes.setGene(Genetics.WIDTH, BodySizes.clampWidth(VillagerHeritage.of(this.villager), width));
		}
	}
}
