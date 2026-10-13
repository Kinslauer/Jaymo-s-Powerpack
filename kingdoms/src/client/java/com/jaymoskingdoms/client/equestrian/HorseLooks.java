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
 * (wider, taller, longer) on top of its size. Carried on the render state. (0.5.40) The coats are drawn for Mo' Creatures'
 * horse ({@link MocHorseModel}): {@code textures/entity/moc_horse/<breed>/<coat>.png} ({@code tools/gen_moc_horses.py}),
 * one texture for foals and grown horses alike.
 */
public final class HorseLooks {
	private static final Map<String, Identifier> TEXTURES = new HashMap<>();
	/** Undead coats (an undead horse's coat index picks one, whatever its breed). */
	static final String[] SKELETON = {"bone", "sooty", "mossy"}, ZOMBIE = {"rotten", "pale", "mottled"};
	/** Until a horse's breed reaches the client. */
	static final Identifier FALLBACK = texture("textures/entity/moc_horse/courser/bay.png");
	static final Identifier FALLBACK_SKELETON = texture("textures/entity/moc_horse/skeleton/bone.png");
	static final Identifier FALLBACK_ZOMBIE = texture("textures/entity/moc_horse/zombie/rotten.png");
	public static final RenderStateDataKey<Identifier> TEXTURE = RenderStateDataKey.create(() -> "jaymoskingdoms:equine_texture");
	public static final RenderStateDataKey<float[]> BUILD = RenderStateDataKey.create(() -> "jaymoskingdoms:equine_build");
	private static final Map<Breed, float[]> BUILDS = new HashMap<>();

	private HorseLooks() {
	}

	/** (From {@code AbstractHorseRendererMixin}) the texture and build of {@code horse}, onto its render state. */
	public static void extract(AbstractHorse horse, EquineRenderState state) {
		Equine e = Equestrian.covered(horse) && !(horse instanceof WarElephant) ? horse.getAttached(Equestrian.EQUINE) : null;
		if (e == null) {
			state.setData(TEXTURE, null);
			state.setData(BUILD, null);
			return;
		}
		String path;
		if (horse instanceof SkeletonHorse) {
			path = "skeleton/" + SKELETON[Math.floorMod(e.coat(), SKELETON.length)];
		} else if (horse instanceof ZombieHorse) {
			path = "zombie/" + ZOMBIE[Math.floorMod(e.coat(), ZOMBIE.length)];
		} else {
			path = e.breed().id() + "/" + e.coatName();
		}
		state.setData(TEXTURE, texture("textures/entity/moc_horse/" + path + ".png"));
		state.setData(BUILD, BUILDS.computeIfAbsent(e.breed(), b -> new float[] {b.width, b.height, b.length}));
	}

	static Identifier texture(String path) {
		return TEXTURES.computeIfAbsent(path, JaymosKingdoms::id);
	}

	public static @Nullable Identifier texture(EquineRenderState state) {
		return state.getData(TEXTURE);
	}
}
