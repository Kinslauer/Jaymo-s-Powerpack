package com.jaymoskingdoms.client.beasts;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * The sand worm ({@code beasts/SandWorm}), our own: a tower of {@link #SEGMENTS} thick rings, each riding the one under
 * it so a bend runs up the whole body, with a wide collar at the top round an open maw and four hooked teeth. Its
 * poses follow the server's moves: hidden under the ground while the sand boils, pushed up through it, swaying while it
 * stands, reared back and then thrown down flat along its strike, and drawn back under. {@code kingdoms/tools/gen_sand_worm.py}
 * paints the hide over these boxes (keep both the same).
 */
final class SandWormModel extends EntityModel<SandWormRenderer.State> {
	static final int SEGMENTS = 7;
	/** A ring's height, and the whole body's with the collar (pixels). */
	static final float RING = 12.0F, TALL = SEGMENTS * RING + 8.0F;
	/** The server's timings (ticks). */
	static final float RISE_TICKS = 10.0F, SINK_TICKS = 20.0F, BITE_TELL = 16.0F, BITE_TICKS = 30.0F;
	/** The strike: how far it rears back first, and how far over it comes down (radians, the whole body's bend). */
	static final float REAR_BACK = -0.3F, STRIKE = 1.4F;

	private final ModelPart[] rings = new ModelPart[SEGMENTS];

	SandWormModel(ModelPart root) {
		super(root);
		ModelPart part = root;
		for (int i = 0; i < SEGMENTS; i++) {
			part = part.getChild("ring" + i);
			this.rings[i] = part;
		}
	}

	static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition part = mesh.getRoot();
		for (int i = 0; i < SEGMENTS; i++) {
			part = part.addOrReplaceChild("ring" + i, CubeListBuilder.create().texOffs(0, 0).addBox(-9.0F, -RING, -9.0F, 18.0F, RING, 18.0F, CubeDeformation.NONE),
				PartPose.offset(0.0F, i == 0 ? 24.0F : -RING, 0.0F));
		}
		PartDefinition head = part.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 30).addBox(-10.0F, -8.0F, -10.0F, 20.0F, 8.0F, 20.0F,
			CubeDeformation.NONE), PartPose.offset(0.0F, -RING, 0.0F));
		for (int tooth = 0; tooth < 4; tooth++) {
			float x = tooth < 2 ? (tooth == 0 ? -7.5F : 7.5F) : 0.0F, z = tooth < 2 ? 0.0F : (tooth == 2 ? -7.5F : 7.5F);
			head.addOrReplaceChild("tooth" + tooth, CubeListBuilder.create().texOffs(84, 0).addBox(-1.5F, -8.0F, -1.5F, 3.0F, 8.0F, 3.0F, CubeDeformation.NONE),
				PartPose.offsetAndRotation(x, -8.0F, z, z * 0.05F, 0.0F, -x * 0.05F));
		}
		return LayerDefinition.create(mesh, 128, 64);
	}

	@Override
	public void setupAnim(SandWormRenderer.State state) {
		super.setupAnim(state);
		String action = state.action;
		float t = state.actionAge;
		// how much of it is out of the ground
		float out = action == null || "warn".equals(action) ? 0.0F
			: "rise".equals(action) ? Mth.clamp(t / RISE_TICKS, 0.0F, 1.0F)
			: "sink".equals(action) ? 1.0F - Mth.clamp(t / SINK_TICKS, 0.0F, 1.0F) : 1.0F;
		this.rings[0].y = 24.0F + (1.0F - out) * (TALL + 10.0F);
		float bend = 0.0F;
		if ("bite".equals(action)) {
			if (t < BITE_TELL - 3.0F) {
				bend = REAR_BACK * Mth.clamp(t / (BITE_TELL - 3.0F), 0.0F, 1.0F);
			} else if (t < BITE_TELL) {
				bend = Mth.lerp((t - (BITE_TELL - 3.0F)) / 3.0F, REAR_BACK, STRIKE);
			} else {
				bend = STRIKE * (1.0F - Mth.clamp((t - BITE_TELL - 4.0F) / (BITE_TICKS - BITE_TELL - 4.0F), 0.0F, 1.0F));
			}
		}
		for (int i = 0; i < SEGMENTS; i++) {
			this.rings[i].xRot = bend / SEGMENTS + Mth.cos(state.ageInTicks * 0.07F + i * 0.5F) * 0.035F;
			this.rings[i].zRot = Mth.sin(state.ageInTicks * 0.08F + i * 0.6F) * 0.045F;
		}
	}
}
