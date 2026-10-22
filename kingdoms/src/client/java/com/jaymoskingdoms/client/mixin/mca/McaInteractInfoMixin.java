package com.jaymoskingdoms.client.mixin.mca;

import com.jaymoskingdoms.Standing;
import java.util.List;
import net.conczin.mca.client.gui.InteractScreen;
import net.conczin.mca.entity.VillagerLike;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (0.7.175) MCA's interact screen shows a ruler's or a guard's {@link Standing} under its own lines (name, trade, mood,
 * personality, traits): a ruler's title, temperament, reign and what they're about; a guard's realm, ruler and duty.
 * Hovering a line shows more, as MCA's personality line does.
 */
@Mixin(InteractScreen.class)
abstract class McaInteractInfoMixin {
	/** MCA's rows: 17 apart, the first of them (the trade) at 30 + 17; ours start below its last. */
	private static final int ROW = 17, TOP = 30, LEFT = 10, WIDTH = 128;

	@Shadow
	@Final
	private VillagerLike<?> villager;

	@Shadow
	private boolean hoveringOverText(int x, int y, int w) {
		throw new AssertionError();
	}

	@Inject(method = "drawTextPopups", at = @At("TAIL"))
	private void jaymoskingdoms$standing(GuiGraphicsExtractor graphics, CallbackInfo ci) {
		Standing.Lines standing = this.villager.asEntity().getAttached(Standing.LINES);
		if (standing == null || this.villager.asEntity().isBaby()) {
			return;
		}
		// (MCA's rows: trade 1, mood 2, personality 3, and traits 4 if they have any)
		int row = this.villager.getTraits().getTraits().isEmpty() ? 4 : 5;
		McaDynamicScreenInvoker screen = (McaDynamicScreenInvoker)this;
		for (int i = 0; i < standing.lines().size(); i++, row++) {
			int y = TOP + ROW * row;
			Component hover = i < standing.hovers().size() ? standing.hovers().get(i) : Component.empty();
			if (!hover.getString().isEmpty() && this.hoveringOverText(LEFT, y, WIDTH)) {
				screen.jaymoskingdoms$drawTooltip(graphics, List.of(standing.lines().get(i), hover), LEFT, y);
			} else {
				screen.jaymoskingdoms$drawTooltip(graphics, standing.lines().get(i), LEFT, y);
			}
		}
	}
}
