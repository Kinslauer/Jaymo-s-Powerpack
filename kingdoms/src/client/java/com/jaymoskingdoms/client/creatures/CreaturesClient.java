package com.jaymoskingdoms.client.creatures;

import com.jaymoskingdoms.creatures.Basilisk;
import com.jaymoskingdoms.creatures.CloakFlier;
import com.jaymoskingdoms.creatures.Creatures;
import com.jaymoskingdoms.creatures.Harpy;
import com.jaymoskingdoms.creatures.Piercer;
import com.jaymoskingdoms.creatures.Troll;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;

/** The renderers of patch 9's creatures. (The blood bat's is with the vampire bat's, {@code NightfallClient}.) */
public final class CreaturesClient {
	private CreaturesClient() {
	}

	public static void register() {
		creature(Creatures.TROLL, "troll", 1.0F, Troll.FALLEN, -1, false);
		creature(Creatures.TROLL_LIMB, "troll_limb", 0.3F, -1, -1, false);
		creature(Creatures.RUST_BEAST, "rust_beast", 0.9F, -1, -1, false);
		creature(Creatures.BASILISK, "basilisk", 1.0F, -1, -1, false);
		creature(Creatures.CLEAR_CUBE, "clear_cube", 1.1F, -1, -1, false);
		creature(Creatures.PIERCER, "piercer", 0.3F, Piercer.GROUND, -1, false);
		creature(Creatures.CLOAK_FLIER, "cloak_flier", 0.0F, -1, CloakFlier.HUNTING, false);
		creature(Creatures.HARPY, "harpy", 0.4F, -1, Harpy.SINGING, false);
		creature(Creatures.DIGGER, "digger", 0.3F, -1, -1, false);
		creature(Creatures.SHADOW, "shadow", 0.0F, -1, -1, false);
		creature(Creatures.SHAPESHIFTER, "shapeshifter", 0.5F, -1, -1, false);
		creature(Creatures.BOG_LIGHT, "bog_light", 0.0F, -1, -1, true);
	}

	private static <T extends Mob> void creature(EntityType<T> type, String name, float shadow, int lieAt, int flapsFrom, boolean glows) {
		CreatureModel.Spec spec = CreatureModel.load(name);
		EntityRenderers.register(type, context -> new CreatureRenderer<T>(context, name, spec, shadow, lieAt, flapsFrom, glows));
	}
}
