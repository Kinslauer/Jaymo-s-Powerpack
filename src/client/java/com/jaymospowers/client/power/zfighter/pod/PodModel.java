package com.jaymospowers.client.power.zfighter.pod;

import com.jaymospowers.JaymosPowers;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.resources.Identifier;

/**
 * (0.18.14, Andy) The space pod's model ({@code textures/entity/space_pod.png}, 128x128), our own, one {@link ModelPart} for
 * each piece that can come off, so {@link PodRenderer} draws only the slots that are filled: a keel (iron cross at the
 * base), three fins (a slab on a foot), the thruster bell, the brass tank with its red band, four bone-white hull plates
 * (a porthole on the front, a hatch on the right), a stepped glass canopy and the mast. A damaged plate also draws its
 * gash ({@code gash_*}), a damaged canopy only its lower step ({@code canopy_low}), a damaged fin is bent by the renderer.
 * Pixels, model space (y down, the pod's feet at y 0, +z its front, +x its right). tools/gen_pod.py writes the cube
 * table below and paints the sheet, so change the shape there.
 */
final class PodModel {
	static final Identifier TEXTURE = JaymosPowers.id("textures/entity/space_pod.png");

	/** {part, u, v, x, y, z, w, h, d}. */
	private static final Object[][] CUBES = {
		// cubes:start
		{"keel", 8, 103, -12F, -3F, -2F, 24F, 3F, 4F},
		{"keel", 0, 40, -2F, -3F, -12F, 4F, 3F, 24F},
		{"keel", 54, 89, -4F, -6F, -4F, 8F, 3F, 8F},
		{"keel", 108, 0, 10F, -30F, 10F, 2F, 27F, 2F},
		{"keel", 108, 0, -12F, -30F, 10F, 2F, 27F, 2F},
		{"keel", 108, 0, 10F, -30F, -12F, 2F, 27F, 2F},
		{"keel", 108, 0, -12F, -30F, -12F, 2F, 27F, 2F},
		{"fin_r", 56, 40, 11F, -19F, -4F, 2F, 16F, 8F},
		{"fin_r", 54, 89, 11F, -3F, -4F, 8F, 3F, 8F},
		{"fin_l", 56, 40, -13F, -19F, -4F, 2F, 16F, 8F},
		{"fin_l", 54, 89, -19F, -3F, -4F, 8F, 3F, 8F},
		{"fin_b", 64, 67, -4F, -19F, -13F, 8F, 16F, 2F},
		{"fin_b", 54, 89, -4F, -3F, -19F, 8F, 3F, 8F},
		{"bell", 86, 89, -3F, -11F, -3F, 6F, 5F, 6F},
		{"bell", 14, 89, -5F, -8F, -5F, 10F, 2F, 10F},
		{"tank", 44, 0, -8F, -29F, -8F, 16F, 18F, 16F},
		{"plate_f", 76, 40, -10F, -30F, 10F, 20F, 20F, 2F},
		{"plate_r", 0, 0, 10F, -30F, -10F, 2F, 20F, 20F},
		{"plate_b", 76, 40, -10F, -30F, -12F, 20F, 20F, 2F},
		{"plate_l", 0, 0, -12F, -30F, -10F, 2F, 20F, 20F},
		{"gash_f", 110, 89, -4F, -23F, 11.5F, 6F, 8F, 1F},
		{"gash_b", 110, 89, 0F, -25F, -12.5F, 6F, 8F, 1F},
		{"gash_r", 0, 89, 11.5F, -22F, -3F, 1F, 8F, 6F},
		{"gash_l", 0, 89, -12.5F, -24F, 0F, 1F, 8F, 6F},
		{"canopy_low", 0, 67, -8F, -36F, -8F, 16F, 6F, 16F},
		{"canopy", 0, 67, -8F, -36F, -8F, 16F, 6F, 16F},
		{"canopy", 84, 67, -5F, -41F, -5F, 10F, 5F, 10F},
		{"mast", 0, 103, -1F, -48F, -1F, 2F, 7F, 2F},
		{"mast", 64, 103, -2F, -50F, -2F, 4F, 2F, 4F},
		{"mast_lit", 96, 103, -1F, -52F, -1F, 2F, 2F, 2F},
		{"grade_1", 80, 103, 9F, -5F, 9F, 4F, 1F, 4F},
		{"grade_1", 80, 103, -13F, -5F, 9F, 4F, 1F, 4F},
		{"grade_1", 80, 103, 9F, -5F, -13F, 4F, 1F, 4F},
		{"grade_1", 80, 103, -13F, -5F, -13F, 4F, 1F, 4F},
		{"grade_2", 80, 103, 9F, -7F, 9F, 4F, 1F, 4F},
		{"grade_2", 80, 103, -13F, -7F, 9F, 4F, 1F, 4F},
		{"grade_2", 80, 103, 9F, -7F, -13F, 4F, 1F, 4F},
		{"grade_2", 80, 103, -13F, -7F, -13F, 4F, 1F, 4F},
		{"grade_3", 80, 103, 9F, -9F, 9F, 4F, 1F, 4F},
		{"grade_3", 80, 103, -13F, -9F, 9F, 4F, 1F, 4F},
		{"grade_3", 80, 103, 9F, -9F, -13F, 4F, 1F, 4F},
		{"grade_3", 80, 103, -13F, -9F, -13F, 4F, 1F, 4F},
		// cubes:end
	};

	private static Map<String, ModelPart> parts;

	private PodModel() {
	}

	/** The piece called {@code name} ({@code keel}, {@code fin_r}, {@code plate_f}, {@code gash_f}...). */
	static ModelPart part(String name) {
		if (parts == null) {
			build();
		}
		return parts.get(name);
	}

	private static void build() {
		Map<String, CubeListBuilder> builders = new HashMap<>();
		for (Object[] c : CUBES) {
			CubeListBuilder cubes = builders.computeIfAbsent((String)c[0], k -> CubeListBuilder.create());
			cubes.texOffs((Integer)c[1], (Integer)c[2]).addBox((Float)c[3], (Float)c[4], (Float)c[5], (Float)c[6], (Float)c[7], (Float)c[8]);
		}
		MeshDefinition mesh = new MeshDefinition();
		for (Map.Entry<String, CubeListBuilder> entry : builders.entrySet()) {
			mesh.getRoot().addOrReplaceChild(entry.getKey(), entry.getValue(), PartPose.ZERO);
		}
		ModelPart root = LayerDefinition.create(mesh, 128, 128).bakeRoot();
		Map<String, ModelPart> baked = new HashMap<>();
		for (String name : builders.keySet()) {
			baked.put(name, root.getChild(name));
		}
		parts = baked;
	}
}
