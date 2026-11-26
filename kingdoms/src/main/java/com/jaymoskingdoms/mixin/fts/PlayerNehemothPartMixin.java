package com.jaymoskingdoms.mixin.fts;

import com.jaymoskingdoms.inhabitants.fts.NehemothPart;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * What a weapon does after it hits (wear, fire aspect and the rest) happens to the Nehemoth when the blow landed on one of
 * its parts, as vanilla does for the ender dragon's.
 */
@Mixin(Player.class)
abstract class PlayerNehemothPartMixin {
	@ModifyVariable(method = "itemAttackInteraction", at = @At("HEAD"), argsOnly = true)
	private Entity jaymoskingdoms$partIsItsBody(Entity entity) {
		return entity instanceof NehemothPart part ? part.parent
			: entity instanceof com.jaymoskingdoms.dragons.DragonPart dragonPart ? dragonPart.parent : entity;
	}
}
