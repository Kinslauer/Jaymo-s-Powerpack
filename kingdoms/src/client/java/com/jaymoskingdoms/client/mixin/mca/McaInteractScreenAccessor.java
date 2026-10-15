package com.jaymoskingdoms.client.mixin.mca;

import net.conczin.mca.client.gui.InteractScreen;
import net.conczin.mca.entity.VillagerLike;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** (0.7.53) Who MCA's interact screen is for, so Change Clothes shows only on a tailor ({@code McaTailorButtonMixin}). */
@Mixin(InteractScreen.class)
public interface McaInteractScreenAccessor {
	@Accessor("villager")
	VillagerLike<?> jaymoskingdoms$villager();
}
