package com.jaymoskingdoms.client.mixin.mca;

import net.conczin.mca.client.gui.VillagerEditorScreen;
import net.conczin.mca.entity.ai.relationship.Gender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** MCA's own man/woman switch (what its two buttons on the General page press), for the Life tab's first card. */
@Mixin(VillagerEditorScreen.class)
public interface McaEditorGenderInvoker {
	@Invoker("updateGender")
	void jaymoskingdoms$updateGender(Gender gender);
}
