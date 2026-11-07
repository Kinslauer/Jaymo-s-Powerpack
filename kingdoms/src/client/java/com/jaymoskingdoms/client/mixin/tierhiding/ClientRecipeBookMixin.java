package com.jaymoskingdoms.client.mixin.tierhiding;

import com.jaymoskingdoms.client.TierHidingClient;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** (TierHiding) The recipe book leaves out recipes that only make what's hidden from this player. */
@Mixin(ClientRecipeBook.class)
public class ClientRecipeBookMixin {
	@ModifyArg(method = "rebuildCollections", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/ClientRecipeBook;categorizeAndGroupRecipes(Ljava/lang/Iterable;)Ljava/util/Map;"))
	private Iterable<RecipeDisplayEntry> jaymoskingdoms$hideLocked(Iterable<RecipeDisplayEntry> recipes) {
		if (TierHidingClient.hidden().isEmpty()) {
			return recipes;
		}
		List<RecipeDisplayEntry> kept = new ArrayList<>();
		for (RecipeDisplayEntry entry : recipes) {
			if (!TierHidingClient.hidden(entry)) {
				kept.add(entry);
			}
		}
		return kept;
	}
}
