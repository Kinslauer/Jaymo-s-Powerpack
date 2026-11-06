package com.jaymoskingdoms.mixin.spellengine;

import com.jaymoskingdoms.forms.FormSkills;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.container.SpellContainer;
import net.spell_engine.internals.container.SpellContainerSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * (0.7.550) Weapon Forms on Spell Engine's skill bar ({@link FormSkills}). Spell Engine only fills the bar when the held
 * item carries a spell container that resolves spells, and only with the spells that container takes.
 * <ul>
 * <li>A form weapon with no such container (a vanilla sword, a spear, a claw; a bare hand while locked on), held by
 * someone who knows a form for it, is given one that takes only {@code #jaymoskingdoms:weapon_forms}: their RPG Series class skills still need their own
 * weapons.</li>
 * <li>Any resolving container takes the form spells too, so a Rogues dagger shows its class skills and its forms; the
 * forms source only ever offers forms that fit the hand.</li>
 * </ul>
 * (0.7.624) The same for Powers' takedowns on a bare hand (in Time Flow, or locked on).
 * Runs on both sides, as Spell Engine does.
 */
@Mixin(value = SpellContainerSource.class, remap = false)
abstract class SpellContainerSourceMixin {
	@ModifyExpressionValue(method = "update", at = @At(value = "INVOKE",
		target = "Lnet/spell_engine/api/spell/container/SpellContainerHelper;containerFromItemStack(Lnet/minecraft/world/item/ItemStack;)Lnet/spell_engine/api/spell/container/SpellContainer;"))
	private static SpellContainer jaymoskingdoms$formWeapon(SpellContainer held, @Local(argsOnly = true) Player player) {
		if (player == null || held != null && held.isResolver() || FormSkills.spells(player).isEmpty()) {
			return held;
		}
		return new SpellContainer(SpellContainer.ContentType.TAG, FormSkills.TAG, "", "", 0, List.of(), 0);
	}

	@Inject(method = "spellMatchesContentType", at = @At("HEAD"), cancellable = true)
	private static void jaymoskingdoms$formsFitAny(Holder<Spell> spell, SpellContainer.ContentType type, TagKey<Spell> tag,
		CallbackInfoReturnable<Boolean> cir) {
		if (type != SpellContainer.ContentType.NONE && FormSkills.ours(spell.unwrapKey().map(key -> key.identifier()).orElse(null))) {
			cir.setReturnValue(true);
		}
	}
}
