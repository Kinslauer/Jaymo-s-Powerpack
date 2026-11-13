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
		// a pit's lid dressed as grass is tinted as grass is (the other looks have no tinted faces)
		net.fabricmc.fabric.api.client.rendering.v1.BlockColorRegistry.register(java.util.List.of(net.minecraft.client.color.block.BlockTintSources.grassBlock()), Creatures.TRAP_COVER);
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
		// the blossom lands' animals
		creature(com.jaymoskingdoms.blossomland.BlossomAnimals.KOI, "koi", 0.15F, -1, -1, false);
		creature(com.jaymoskingdoms.blossomland.BlossomAnimals.SNOW_MONKEY, "snow_monkey", 0.35F, -1, -1, false);
		creature(com.jaymoskingdoms.blossomland.BlossomAnimals.CRANE, "crane", 0.35F, -1, com.jaymoskingdoms.blossomland.Crane.DANCING, false);
		creature(com.jaymoskingdoms.blossomland.BlossomAnimals.SHRINE_DEER, "shrine_deer", 0.4F, -1, -1, false);
		creature(com.jaymoskingdoms.blossomland.BlossomAnimals.RACCOON_DOG, "raccoon_dog", 0.3F, -1, -1, false);
		creature(com.jaymoskingdoms.blossomland.BlossomAnimals.SEROW, "serow", 0.5F, -1, -1, false);
		creature(com.jaymoskingdoms.blossomland.BlossomAnimals.HORNET, "hornet", 0.1F, -1, 0, false);
		creature(com.jaymoskingdoms.blossomland.BlossomAnimals.SALAMANDER, "giant_salamander", 0.5F, -1, -1, false);
		// and their night things
		creature(com.jaymoskingdoms.blossomland.NightThings.RIVER_CHILD, "river_child", 0.3F, -1, -1, false);
		creature(com.jaymoskingdoms.blossomland.NightThings.SNOW_WIDOW, "snow_widow", 0.4F, -1, -1, false);
		creature(com.jaymoskingdoms.blossomland.NightThings.UNBURIED, "unburied", 1.4F, -1, -1, false);
		creature(com.jaymoskingdoms.blossomland.NightThings.BLACK_SWELL, "black_swell", 0.0F, -1, -1, false);
	}

	private static <T extends Mob> void creature(EntityType<T> type, String name, float shadow, int lieAt, int flapsFrom, boolean glows) {
		CreatureModel.Spec spec = CreatureModel.load(name);
		EntityRenderers.register(type, context -> new CreatureRenderer<T>(context, name, spec, shadow, lieAt, flapsFrom, glows));
	}
}
