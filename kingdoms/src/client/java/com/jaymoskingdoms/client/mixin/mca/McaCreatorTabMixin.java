package com.jaymoskingdoms.client.mixin.mca;

import com.jaymoskingdoms.client.CreatorTab;
import java.util.Arrays;
import net.conczin.mca.client.gui.DestinyScreen;
import net.conczin.mca.client.gui.VillagerEditorScreen;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Andy: "if they hit skip the story, their start is randomized and ALL creation options are moved to a tab within the MCA
 * creator". For one who skipped the life story, MCA's start-up creator has one more page beside General, Character and
 * Traits: Life ({@link CreatorTab}), built here in the editor's data column whenever that page is set. Finishing the
 * creator (it sets the page "destiny") seals the story first, for the gender the creator made, so the server has the
 * story before it hears the creator is done.
 */
@Mixin(DestinyScreen.class)
abstract class McaCreatorTabMixin extends Screen {
	protected McaCreatorTabMixin() {
		super(Component.empty());
	}

	@Shadow
	protected abstract void setPage(String page);

	@Inject(method = "getPages", at = @At("RETURN"), cancellable = true)
	private void jaymoskingdoms$lifeTab(CallbackInfoReturnable<String[]> cir) {
		if (CreatorTab.on()) {
			String[] pages = cir.getReturnValue();
			String[] more = Arrays.copyOf(pages, pages.length + 1);
			more[pages.length] = CreatorTab.PAGE;
			cir.setReturnValue(more);
		}
	}

	@Inject(method = "setPage", at = @At("HEAD"))
	private void jaymoskingdoms$sealStory(String page, CallbackInfo ci) {
		if ("destiny".equals(page) && CreatorTab.on()) {
			VillagerEditorScreen editor = (VillagerEditorScreen)(Object)this;
			CreatorTab.seal(editor.getVillager() != null && editor.getVillager().getGenetics().getGender() == Gender.FEMALE);
		}
	}

	@Inject(method = "setPage", at = @At("TAIL"))
	private void jaymoskingdoms$lifePage(String page, CallbackInfo ci) {
		if (CreatorTab.PAGE.equals(page) && CreatorTab.on()) {
			CreatorTab.build(this.width, this.height, this.font, this::addRenderableWidget, () -> setPage(CreatorTab.PAGE));
		}
	}
}
