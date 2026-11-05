package com.jaymoskingdoms.mixin.spellengine;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.forms.WeaponSkills;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.spell_engine.api.spell.SpellDataComponents;
import net.spell_engine.api.spell.container.SpellContainer;
import net.spell_engine.api.spell.container.SpellContainers;
import net.spell_engine.internals.container.SpellAssignments;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (0.7.601) Every melee weapon gets an RPG Series weapon skill ({@link WeaponSkills}). Spell Engine fills its item
 * assignments as the server starts (data, then its own id-matching fallback) and then encodes them for the clients; just
 * before that, the weapons still without a skill (no entry, or an empty one) get theirs. Server side; clients get it
 * through Spell Engine's own sync.
 */
@Mixin(value = SpellAssignments.class, remap = false)
abstract class SpellAssignmentsMixin {
	@Inject(method = "encodeContent", at = @At("HEAD"))
	private static void jaymoskingdoms$everyWeaponASkill(CallbackInfo ci) {
		int given = 0;
		for (Item item : BuiltInRegistries.ITEM) {
			Identifier id = BuiltInRegistries.ITEM.getKey(item);
			SpellContainer existing = SpellAssignments.containers.get(id);
			if (existing != null && !existing.spell_ids().isEmpty()) {
				continue;
			}
			Identifier skill = WeaponSkills.skillFor(item, item.components().has(SpellDataComponents.SPELL_CONTAINER));
			if (skill != null) {
				SpellAssignments.containers.put(id, SpellContainers.forMeleeWeapon().withSpellId(skill));
				given++;
			}
		}
		JaymosKingdoms.LOGGER.info("Weapon skills: {} more weapons given one", given);
	}
}
