package com.jaymoskingdoms.client.dragons;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.dragons.DragonLook;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.Identifier;

/**
 * (0.7.135) Where a dragon's look ({@link DragonLook}) is drawn from: every species keeps its hides and its glowing
 * marking patterns under {@code textures/entity/dragon/<species>/} ({@code hide_<n>.png}, {@code marks_<n>.png}, painted
 * by {@code tools/gen_dragons.py}). The markings layer has the eyes too, so every dragon's eyes glow in the dark.
 */
final class DragonLooks {
	private static final Map<String, Identifier> CACHE = new HashMap<>();

	private DragonLooks() {
	}

	static Identifier hide(String species, DragonLook look) {
		return texture(species, "hide_" + look.hide());
	}

	static Identifier marks(String species, DragonLook look) {
		return texture(species, "marks_" + look.marks());
	}

	private static Identifier texture(String species, String name) {
		return CACHE.computeIfAbsent(species + "/" + name, key -> JaymosKingdoms.id("textures/entity/dragon/" + key + ".png"));
	}
}
