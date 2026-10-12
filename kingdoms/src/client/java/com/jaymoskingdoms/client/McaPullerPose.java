package com.jaymoskingdoms.client;

import net.conczin.mca.client.model.VillagerEntityBaseModelMCA;
import net.conczin.mca.client.model.VillagerEntityModelMCA;
import net.minecraft.client.model.Model;

/**
 * (0.5.26) An MCA villager's model, bent into a rickshaw's shafts ({@link PullerPose}): its breasts lean with its body and
 * its clothes layers follow its limbs again. Only loaded with MCA.
 */
final class McaPullerPose {
	private McaPullerPose() {
	}

	static void follow(Model<?> model, float lean, float k) {
		if (model instanceof VillagerEntityBaseModelMCA base) {
			base.breasts.xRot += lean;
			base.breasts.y += 3.2F * k;
		}
		if (model instanceof VillagerEntityModelMCA villager) {
			villager.syncWearParts();
		}
	}
}
