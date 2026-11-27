package com.jaymoskingdoms.client.compat;

import com.jaymoskingdoms.Classes.PlayerClass;
import com.jaymoskingdoms.JaymosKingdoms;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IIngredientAliasRegistration;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/**
 * (Andy, 2026-11-25: "all items related to classes should get some type of tag so typing the class name in jei gets you
 * all the related items") Every item in a class's tag, {@code jaymoskingdoms:class/<class>} (its focus, the weapons it is
 * trained with, the armour made for it and the things only it uses; kingdoms/tools/class_item_tags.py writes them), takes
 * the class's name as a JEI search alias, so typing "cleric" in JEI's search lists them all beside anything named so.
 * The alias is the class's name key, which JEI puts in the player's own language. Only loaded by JEI, through its entrypoint.
 */
@JeiPlugin
public final class ClassJei implements IModPlugin {
	@Override
	public Identifier getPluginUid() {
		return JaymosKingdoms.id("class_items");
	}

	@Override
	public void registerIngredientAliases(IIngredientAliasRegistration registration) {
		for (PlayerClass cls : PlayerClass.values()) {
			String alias = "class.jaymoskingdoms." + cls.key();
			for (Holder<Item> item : BuiltInRegistries.ITEM.getTagOrEmpty(tag(cls))) {
				registration.addAlias(item.value(), alias);
			}
		}
	}

	/** The tag of the items that go with {@code cls}. */
	static TagKey<Item> tag(PlayerClass cls) {
		return TagKey.create(Registries.ITEM, JaymosKingdoms.id("class/" + cls.key()));
	}
}
