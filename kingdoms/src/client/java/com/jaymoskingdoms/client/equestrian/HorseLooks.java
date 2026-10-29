package com.jaymoskingdoms.client.equestrian;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.equestrian.Breed;
import com.jaymoskingdoms.equestrian.Equestrian;
import com.jaymoskingdoms.equestrian.Equine;
import com.jaymoskingdoms.equestrian.WarElephant;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.renderer.entity.state.EquineRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.equine.SkeletonHorse;
import net.minecraft.world.entity.animal.equine.ZombieHorse;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.34) How an upgraded horse is drawn: its breed's coat, or a skeleton's or zombie's own, and its breed's build
 * (wider, taller, longer) on top of its size. Carried on the render state. (0.7.305) Each breed is drawn in its own
 * build of {@link BreedHorseModel} (the nightmare as a courser, the undead as palfreys), in a coat painted for it:
 * {@code textures/entity/breed_horse/<breed>/<coat>.png} ({@code tools/gen_horses.py}), one texture for foals and grown
 * horses alike.
 */
public final class HorseLooks {
	private static final Map<String, Identifier> TEXTURES = new HashMap<>();
	/** Undead coats (an undead horse's coat index picks one, whatever its breed). */
	static final String[] SKELETON = {"bone", "sooty", "mossy"}, ZOMBIE = {"rotten", "pale", "mottled"};
	/** Until a horse's breed reaches the client. */
	static final Identifier FALLBACK = texture("textures/entity/breed_horse/courser/bay.png");
	static final Identifier FALLBACK_SKELETON = texture("textures/entity/breed_horse/skeleton/bone.png");
	static final Identifier FALLBACK_ZOMBIE = texture("textures/entity/breed_horse/zombie/rotten.png");
	public static final RenderStateDataKey<Identifier> TEXTURE = RenderStateDataKey.create(() -> "jaymoskingdoms:equine_texture");
	public static final RenderStateDataKey<float[]> BUILD = RenderStateDataKey.create(() -> "jaymoskingdoms:equine_build");
	/** (0.7.305) Which build of {@link BreedHorseModel} it's drawn in. */
	static final RenderStateDataKey<String> MODEL = RenderStateDataKey.create(() -> "jaymoskingdoms:equine_model");
	static final String FALLBACK_BUILD = "courser", UNDEAD_BUILD = "palfrey", MOONLIT_BUILD = "moonlit";
	private static final Map<Breed, float[]> BUILDS = new HashMap<>();

	private HorseLooks() {
	}

	/** (From {@code AbstractHorseRendererMixin}) the texture and build of {@code horse}, onto its render state. */
	public static void extract(AbstractHorse horse, EquineRenderState state) {
		Equine e = Equestrian.covered(horse) && !(horse instanceof WarElephant) ? horse.getAttached(Equestrian.EQUINE) : null;
		if (e == null) {
			state.setData(TEXTURE, null);
			state.setData(BUILD, null);
			state.setData(MODEL, horse instanceof SkeletonHorse || horse instanceof ZombieHorse ? UNDEAD_BUILD : null);
			return;
		}
		String path;
		if (horse instanceof SkeletonHorse) {
			path = "skeleton/" + SKELETON[Math.floorMod(e.coat(), SKELETON.length)];
			state.setData(MODEL, UNDEAD_BUILD);
		} else if (horse instanceof ZombieHorse) {
			path = "zombie/" + ZOMBIE[Math.floorMod(e.coat(), ZOMBIE.length)];
			state.setData(MODEL, UNDEAD_BUILD);
		} else if (e.moonlit()) {
			// (0.7.309) the moonlit horse: its own shape and painting, whatever its breed
			path = "moonlit/moonlit";
			state.setData(MODEL, MOONLIT_BUILD);
		} else {
			path = e.breed().id() + "/" + e.coatName();
			state.setData(MODEL, e.breed() == Breed.NIGHTMARE ? "courser" : e.breed().id());
		}
		state.setData(TEXTURE, texture("textures/entity/breed_horse/" + path + ".png"));
		state.setData(BUILD, BUILDS.computeIfAbsent(e.breed(), b -> new float[] {b.width, b.height, b.length}));
	}

	static Identifier texture(String path) {
		return TEXTURES.computeIfAbsent(path, JaymosKingdoms::id);
	}

	public static @Nullable Identifier texture(EquineRenderState state) {
		return state.getData(TEXTURE);
	}

	/** The build {@code state}'s horse is drawn in. */
	static String build(EquineRenderState state) {
		String build = state.getData(MODEL);
		return build != null ? build : FALLBACK_BUILD;
	}
}
