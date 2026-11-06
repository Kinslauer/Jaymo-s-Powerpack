package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Stakes;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * (0.7.641) The stakes in a vampire ({@link Stakes#STAKE}) are seen, stuck in like arrows: each skewer item at its own spot
 * picked by its seed (a part, weighted to the chest, then a point on one face of that part's cube), the point buried and the
 * grip standing out along that face, tipped a little at random. Drawn on the part's bone, so it moves with every pose, on
 * anything humanoid (players, MCA villagers, folk).
 */
public final class StakeLayer<S extends HumanoidRenderState> extends RenderLayer<S, HumanoidModel<S>> {
	/** The stakes this frame: each one's item and seed. */
	record Drawn(List<ItemStackRenderState> items, int[] seeds) {
	}

	static final RenderStateDataKey<Drawn> KEY = RenderStateDataKey.create(() -> "jaymoskingdoms:stakes");
	/** The item's size, and how far back along the blade from the centre it sits (item units: a third buried). */
	static final float SCALE = 0.5F, SINK = -0.28F;
	/** Of every 8 stakes, how many go in the body (the rest: head, arms, legs alike). */
	static final int BODY_SHARE = 3;
	/** How far they may lean off straight in (blend of the face's normal and a random direction). */
	static final float LEAN = 0.35F;
	private static final Vector3f BLADE = new Vector3f(1.0F, 1.0F, 0.0F).normalize();

	private StakeLayer(RenderLayerParent<S, HumanoidModel<S>> renderer) {
		super(renderer);
	}

	public static void register() {
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			if (renderer.getModel() instanceof HumanoidModel<?>) {
				helper.register(create(renderer));
			}
		});
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static StakeLayer<?> create(LivingEntityRenderer<?, ?, ?> renderer) {
		return new StakeLayer((RenderLayerParent) renderer);
	}

	/** From the render-state mixin: the stakes' items, or nothing. */
	public static void extract(LivingEntity entity, LivingEntityRenderState state) {
		List<Stakes.Stake> stakes = entity.getAttached(Stakes.STAKE);
		if (stakes == null || stakes.isEmpty()) {
			state.setData(KEY, null);
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		List<ItemStackRenderState> items = new ArrayList<>(stakes.size());
		int[] seeds = new int[stakes.size()];
		for (int i = 0; i < stakes.size(); i++) {
			ItemStackRenderState item = new ItemStackRenderState();
			minecraft.getItemModelResolver().updateForTopItem(item, stakes.get(i).stack(), ItemDisplayContext.NONE, minecraft.level, entity, entity.getId() + i);
			items.add(item);
			seeds[i] = stakes.get(i).seed();
		}
		state.setData(KEY, new Drawn(items, seeds));
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, S state, float yRot, float xRot) {
		Drawn drawn = state.getData(KEY);
		if (drawn == null || state.isInvisible) {
			return;
		}
		HumanoidModel<S> model = this.getParentModel();
		ModelPart[] limbs = {model.head, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg};
		for (int i = 0; i < drawn.items().size(); i++) {
			RandomSource random = RandomSource.create(drawn.seeds()[i]);
			ModelPart part = random.nextInt(8) < BODY_SHARE ? model.body : limbs[random.nextInt(limbs.length)];
			if (!part.visible || part.isEmpty()) {
				part = model.body;
			}
			ModelPart.Cube cube = part.getRandomCube(random);
			// a point on the cube: the face is the side the outward direction leans to most
			float fx = random.nextFloat(), fy = random.nextFloat(), fz = random.nextFloat();
			Vector3f out = new Vector3f(fx * 2.0F - 1.0F, fy * 2.0F - 1.0F, fz * 2.0F - 1.0F);
			float ax = Math.abs(out.x), ay = Math.abs(out.y), az = Math.abs(out.z);
			Vector3f normal = new Vector3f();
			if (ax >= ay && ax >= az) {
				fx = out.x > 0 ? 1.0F : 0.0F;
				normal.x = Math.signum(out.x);
			} else if (ay >= az) {
				fy = out.y > 0 ? 1.0F : 0.0F;
				normal.y = Math.signum(out.y);
			} else {
				fz = out.z > 0 ? 1.0F : 0.0F;
				normal.z = Math.signum(out.z);
			}
			if (out.lengthSquared() < 1.0E-4F) {
				out.set(normal);
			}
			Vector3f dir = normal.lerp(out.normalize(), LEAN, new Vector3f()).normalize();
			poseStack.pushPose();
			part.translateAndRotate(poseStack);
			poseStack.translate(Mth.lerp(fx, cube.minX, cube.maxX) / 16.0F, Mth.lerp(fy, cube.minY, cube.maxY) / 16.0F,
				Mth.lerp(fz, cube.minZ, cube.maxZ) / 16.0F);
			// the sprite's blade (bottom-left to top-right) turned to point in, against the outward lean
			poseStack.mulPose(new Matrix4f().rotation(new Quaternionf().rotationTo(BLADE, dir.negate(new Vector3f()))));
			poseStack.scale(SCALE, SCALE, SCALE);
			// slid back along the blade: the point buried, the rest standing out
			poseStack.translate(BLADE.x * SINK * 1.41421F, BLADE.y * SINK * 1.41421F, 0.0F);
			drawn.items().get(i).submit(poseStack, collector, lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
			poseStack.popPose();
		}
	}
}
