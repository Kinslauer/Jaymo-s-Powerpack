package com.jaymoskingdoms.client;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.RisenLooks;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * Mutant Monsters' mutant zombie as one of the present's old dead, like the zombies ({@link OldDeadSkins}): drawn in the
 * skin of where it rose and its land's clothes ({@link RisenLooks}, which marks mutants as it marks zombies), at one of
 * {@link #STAGES} stages of rot picked by its id (kingdoms/tools/gen_mutant_dead.py:
 * {@code old_dead/mutant/place/land_stage}). One with no mark (the future's) keeps the built-in pack's plain skin.
 * Read by mixin.mutantmonsters.MutantDeadMixin.
 */
public final class MutantDead {
	public static final int STAGES = 3;
	private static final Identifier[][][] SKINS = new Identifier[RisenLooks.Place.values().length][RisenLooks.Land.values().length][];
	static final RenderStateDataKey<Identifier> SKIN = RenderStateDataKey.create(() -> "jaymoskingdoms:mutant_dead_skin");

	static {
		for (RisenLooks.Place place : RisenLooks.Place.values()) {
			for (RisenLooks.Land land : place.lands) {
				Identifier[] skins = new Identifier[STAGES];
				for (int i = 0; i < STAGES; i++) {
					skins[i] = JaymosKingdoms.id("textures/entity/old_dead/mutant/" + place.id + "/" + land.id + "_" + i + ".png");
				}
				SKINS[place.ordinal()][land.ordinal()] = skins;
			}
		}
	}

	private MutantDead() {
	}

	/** From the mutant's renderer's extractRenderState: its skin, if it's marked with where it rose. */
	public static void extract(LivingEntity mutant, LivingEntityRenderState state) {
		Integer look = mutant.getAttached(RisenLooks.ROSE);
		if (look == null) {
			return;
		}
		RisenLooks.Place place = RisenLooks.place(look);
		RisenLooks.Land land = place.drawn(RisenLooks.land(look));
		state.setData(SKIN, SKINS[place.ordinal()][land.ordinal()][Math.floorMod(mutant.getUUID().hashCode(), STAGES)]);
	}

	public static @Nullable Identifier skin(LivingEntityRenderState state) {
		return state.getData(SKIN);
	}
}
