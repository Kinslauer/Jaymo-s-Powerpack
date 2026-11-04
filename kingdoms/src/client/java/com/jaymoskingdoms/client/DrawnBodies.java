package com.jaymoskingdoms.client;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.lang.ref.WeakReference;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.479) Bodies drawn that aren't in the world (Jaymo's Kitchen's corpses draw the dead mob rebuilt from its saved
 * data, under an id of its own), remembered by id as they're drawn, so that whatever looks one up by the id on its render
 * state (MCA's skin colour, {@code McaVerdantSkinMixin}) still finds it. Andy: "Orcs turn back into humans when they
 * die": a dead orc's green skin was looked up in the world, found nothing, and MCA drew a man's.
 */
public final class DrawnBodies {
	/** Past this many remembered, the forgotten ones are swept out. */
	private static final int SWEEP_AT = 256;
	private static final Int2ObjectMap<WeakReference<Entity>> BODIES = new Int2ObjectOpenHashMap<>();

	private DrawnBodies() {
	}

	/** From the living renderer's extractRenderState: {@code entity}, if it isn't the world's own, is remembered. */
	public static void drawn(Entity entity) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null || minecraft.level.getEntity(entity.getId()) == entity) {
			return;
		}
		WeakReference<Entity> known = BODIES.get(entity.getId());
		if (known != null && known.get() == entity) {
			return;
		}
		if (BODIES.size() >= SWEEP_AT) {
			BODIES.values().removeIf(ref -> ref.get() == null);
		}
		BODIES.put(entity.getId(), new WeakReference<>(entity));
	}

	/** The entity with {@code id}: the world's, else a body drawn outside it, else null. */
	public static @Nullable Entity get(int id) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null) {
			return null;
		}
		Entity entity = minecraft.level.getEntity(id);
		if (entity != null) {
			return entity;
		}
		WeakReference<Entity> known = BODIES.get(id);
		return known == null ? null : known.get();
	}
}
