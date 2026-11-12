package com.jaymoskingdoms.mixin.mca;

import com.jaymoskingdoms.MaskedFaces;
import net.conczin.mca.entity.ai.chatAI.modules.RelationModule;
import net.conczin.mca.entity.ai.Relationship;
import net.conczin.mca.entity.ai.relationship.CompassionateEntity;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** A villager doesn't know a masked player for family ({@link MaskedFaces#kinHidden}), so none of MCA's ties to them show where it tells the chat AI they're family. */
@Mixin(RelationModule.class)
abstract class McaKinChatRelationMixin {
	@Redirect(method = "apply", at = @At(value = "INVOKE",
		target = "Lnet/conczin/mca/entity/ai/Relationship$Predicate;test(Lnet/conczin/mca/entity/ai/relationship/CompassionateEntity;Lnet/minecraft/world/entity/Entity;)Z"),
		remap = false, require = 1)
	private static boolean jaymoskingdoms$maskedKin(Relationship.Predicate predicate, CompassionateEntity<?> villager, Entity who) {
		return !MaskedFaces.kinHidden(villager, who) && predicate.test(villager, who);
	}
}
