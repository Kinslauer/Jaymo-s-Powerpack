package com.jaymoskingdoms.client.illagers;

import com.jaymoskingdoms.JaymosKingdoms;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.ArmorModelSet;

/** The ported illager kinds' model layers (registered in {@link IllagerKindsClient}). */
public final class ModModelLayers {
	public static final ModelLayerLocation ARCHIVIST = layer("archivist", "main");
	public static final ModelLayerLocation ARCHIVIST_BOOK = layer("archivist", "book");
	public static final ModelLayerLocation PROVOKER = layer("provoker", "main");
	public static final ModelLayerLocation SURRENDERED = layer("surrendered", "main");
	public static final ArmorModelSet<ModelLayerLocation> SURRENDERED_ARMOR = new ArmorModelSet<>(layer("surrendered", "helmet"),
		layer("surrendered", "chestplate"), layer("surrendered", "leggings"), layer("surrendered", "boots"));
	public static final ModelLayerLocation INVOKER = layer("invoker", "main");
	public static final ModelLayerLocation INVOKER_ARMOR = layer("invoker", "armor");
	public static final ModelLayerLocation INVOKER_FANGS = layer("invoker_fangs", "main");
	public static final ModelLayerLocation INQUISITOR = layer("illager_inquisitor", "main");
	public static final ModelLayerLocation FIRECALLER = layer("firecaller", "main");
	public static final ModelLayerLocation ALCHEMIST = layer("alchemist", "main");
	public static final ModelLayerLocation BASHER = layer("basher", "main");
	public static final ModelLayerLocation MARAUDER = layer("marauder", "main");

	private ModModelLayers() {
	}

	private static ModelLayerLocation layer(String model, String part) {
		return new ModelLayerLocation(JaymosKingdoms.id(model), part);
	}
}
