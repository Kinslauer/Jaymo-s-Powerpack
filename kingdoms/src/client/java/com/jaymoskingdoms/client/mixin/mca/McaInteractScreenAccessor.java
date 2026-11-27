package com.jaymoskingdoms.client.mixin.mca;

import net.conczin.mca.client.gui.InteractScreen;
import net.conczin.mca.client.gui.MCAButton;
import net.conczin.mca.entity.VillagerLike;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** (0.7.55) Who MCA's interact screen is for, so Change Clothes shows only on a tailor ({@code McaTailorButtonMixin}). */
@Mixin(InteractScreen.class)
public interface McaInteractScreenAccessor {
	@Accessor("villager")
	VillagerLike<?> jaymoskingdoms$villager();

	/** Holding out a gift (the talk wheel stands aside, {@code TalkFirst}). */
	@Accessor("inGiftMode")
	boolean jaymoskingdoms$giftMode();

	/** A button of MCA's pressed as its widget would (a talk wheel spoke, {@code TalkFirst}). */
	@Invoker("buttonPressed")
	void jaymoskingdoms$press(MCAButton button);
}
