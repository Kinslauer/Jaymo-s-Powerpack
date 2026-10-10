package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Folk;
import com.jaymoskingdoms.JaymosKingdoms;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.DyeColor;

/** The barbarians' and chiefs' skins: furs and hide, war paint in their camp's colour (drawn by {@code tools/gen_barbarians.py}). */
final class CampSkins {
	private static final Identifier[] BARBARIANS = new Identifier[DyeColor.values().length];
	private static final Identifier[] CHIEFS = new Identifier[DyeColor.values().length];

	private CampSkins() {
	}

	static void register() {
		for (DyeColor dye : DyeColor.values()) {
			BARBARIANS[dye.getId()] = JaymosKingdoms.id("textures/entity/barbarian/" + dye.getName() + ".png");
			CHIEFS[dye.getId()] = JaymosKingdoms.id("textures/entity/chief/" + dye.getName() + ".png");
		}
		JaymosKingdomsClient.SKINS.put(Folk.BARBARIAN, state -> BARBARIANS[dye(state.color)]);
		JaymosKingdomsClient.SKINS.put(Folk.CHIEF, state -> CHIEFS[dye(state.color)]);
	}

	private static int dye(int color) {
		return color >= 0 && color < BARBARIANS.length ? color : DyeColor.RED.getId();
	}
}
