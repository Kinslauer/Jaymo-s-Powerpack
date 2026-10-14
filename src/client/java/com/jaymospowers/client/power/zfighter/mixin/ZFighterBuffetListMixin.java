package com.jaymospowers.client.power.zfighter.mixin;

import com.jaymospowers.JaymosPowers;
import java.util.Collection;
import java.util.List;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * (0.10.93) Andy: the Verdant World isn't offered on the world-creation screen. Its land (Verdant Plains) is left out
 * of Create World's Single Biome list; the world itself is only reached through the Lookout's door.
 */
@Mixin(targets = "net.minecraft.client.gui.screens.CreateBuffetWorldScreen$BiomeList")
abstract class ZFighterBuffetListMixin {
	private static final Identifier VERDANT_PLAINS = JaymosPowers.id("verdant_plains");

	@ModifyArg(method = "filterEntries",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/CreateBuffetWorldScreen$BiomeList;replaceEntries(Ljava/util/Collection;)V"))
	private Collection<?> jaymospowers$noVerdant(Collection<?> entries) {
		List<?> kept = entries.stream()
			.filter(e -> !((ZFighterBiomeEntryAccessor) e).jaymospowers$biome().key().identifier().equals(VERDANT_PLAINS))
			.toList();
		return kept;
	}
}
