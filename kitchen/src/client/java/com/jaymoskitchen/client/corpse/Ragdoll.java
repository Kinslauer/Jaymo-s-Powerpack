package com.jaymoskitchen.client.corpse;

import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.RandomSource;

/**
 * How a player's body lies where it fell: arms and legs splayed out along the ground at odd but possible angles,
 * the head lolled to one side. Worked out from the corpse's id, so each body always lies the same way. Applied to
 * the player model by part name right after it animates (see {@code ModelFeatureRendererMixin}).
 */
public record Ragdoll(boolean faceUp, float headX, float headY, float rightArmX, float rightArmZ, float leftArmX, float leftArmZ,
	float rightLegX, float rightLegZ, float leftLegX, float leftLegZ) {
	public static final RenderStateDataKey<Ragdoll> KEY = RenderStateDataKey.create(() -> "jaymoskitchen:ragdoll");

	public static Ragdoll of(long seed) {
		RandomSource random = RandomSource.create(seed);
		boolean faceUp = random.nextFloat() < 0.6F;
		// limbs lift a little off the ground rather than into it: forward when lying face up, back when face down
		float lift = faceUp ? -1.0F : 1.0F;
		return new Ragdoll(faceUp,
			(random.nextFloat() - 0.5F) * 0.4F,
			(random.nextFloat() * 2.0F - 1.0F) * 1.0F,
			lift * random.nextFloat() * 0.25F, 0.2F + random.nextFloat() * 1.35F,
			lift * random.nextFloat() * 0.25F, -(0.2F + random.nextFloat() * 1.35F),
			lift * random.nextFloat() * 0.2F, 0.05F + random.nextFloat() * 0.45F,
			lift * random.nextFloat() * 0.2F, -(0.05F + random.nextFloat() * 0.45F));
	}

	void apply(ModelPart root) {
		set(root, "head", headX, headY, 0.0F);
		set(root, "right_arm", rightArmX, 0.0F, rightArmZ);
		set(root, "left_arm", leftArmX, 0.0F, leftArmZ);
		set(root, "right_leg", rightLegX, 0.0F, rightLegZ);
		set(root, "left_leg", leftLegX, 0.0F, leftLegZ);
	}

	private static void set(ModelPart root, String name, float x, float y, float z) {
		if (root.hasChild(name)) {
			ModelPart part = root.getChild(name);
			part.xRot = x;
			part.yRot = y;
			part.zRot = z;
		}
	}
}
