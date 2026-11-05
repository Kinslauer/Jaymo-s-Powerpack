package com.jaymospowers.client.power.gravity;

import com.jaymospowers.power.gravity.GravityRocks;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * (0.21.9) A gravity user's harnessed rocks (GravityRocks), drawn for everyone: the rock ball hanging over the shoulder,
 * a sphere of the rocks they carry (bigger with more), that flies at the foe and back when it strikes; or the ring of
 * rocks orbiting at the waist, rocks peeling off one by one to pelt the foe and coming back to their place.
 * Each rock is the rock item's own model, so the ball is made of the stone you picked up.
 */
public final class GravityRocksLayer {
	// ===== the knobs ================================================================================================
	/** The ball's radius (blocks): this much, plus this per rock. */
	static final float BALL_RADIUS = 0.12F, BALL_RADIUS_PER_ROCK = 0.016F;
	/** Where the ball hangs: out to the side, and up (a share of the body's height), and how far it bobs. */
	static final float BALL_SIDE = 0.85F, BALL_UP = 1.1F, BALL_BOB = 0.08F;
	/** How fast the ball turns, and the ring goes round (radians a tick). */
	static final float BALL_SPIN = 0.05F, ORBIT_SPIN = 0.12F;
	/** Most rocks drawn in the ring, how big each is (blocks), and at what share of the body's height. */
	static final int ORBIT_DRAWN = 16;
	static final float ORBIT_ROCK = 0.26F, ORBIT_UP = 0.55F;
	/** The player model's own scale, which the renderer applies before the layers (AvatarRenderer.scale). */
	static final float AVATAR_SCALE = 0.9375F;
	// ================================================================================================================

	static final RenderStateDataKey<Drawn> KEY = RenderStateDataKey.create(() -> "jaymospowers:gravity_rocks");

	/** One rock to draw: where (blocks from the feet, world axes), how big, how turned. */
	record Rock(float x, float y, float z, float size, float yaw, float pitch) {
	}

	record Drawn(ItemStackRenderState item, Vec3 center, float modelSize, float bodyRot, Rock[] rocks) {
	}

	private GravityRocksLayer() {
	}

	public static void init() {
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((entityType, renderer, helper, context) -> {
			if (renderer instanceof net.minecraft.client.renderer.entity.player.AvatarRenderer<?> && renderer.getModel() instanceof HumanoidModel<?>) {
				helper.register(layer(renderer));
			}
		});
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static Layer<?, ?> layer(LivingEntityRenderer<?, ?, ?> renderer) {
		return new Layer((RenderLayerParent)renderer);
	}

	/** From every living renderer's extractRenderState: the rocks, if this is a gravity user with them out. */
	public static void extract(LivingEntity entity, EntityRenderState state, float partial) {
		GravityRocks.Rocks rocks = entity instanceof Player ? entity.getAttached(GravityRocks.ROCKS) : null;
		if (rocks == null || rocks.count() <= 0 || !(state instanceof LivingEntityRenderState living)) {
			state.setData(KEY, null);
			return;
		}
		ItemStack stack = rocks.stack();
		if (stack.isEmpty()) {
			state.setData(KEY, null);
			return;
		}
		ItemStackRenderState item = new ItemStackRenderState();
		Minecraft.getInstance().getItemModelResolver().updateForLiving(item, stack, ItemDisplayContext.NONE, entity);
		if (item.isEmpty()) {
			state.setData(KEY, null);
			return;
		}
		AABB box = item.getModelBoundingBox();
		float modelSize = (float)Math.max(0.05, Math.max(box.getXsize(), Math.max(box.getYsize(), box.getZsize())));
		float time = entity.level().getGameTime() + partial;
		float bodyRot = living.bodyRot;
		float yaw = bodyRot * Mth.DEG_TO_RAD;
		float height = entity.getBbHeight();
		Vec3 feet = entity.getPosition(partial);
		Rock[] drawn;
		if (rocks.form() == GravityRocks.BALL) {
			// home: over the right shoulder, bobbing
			Vec3 right = new Vec3(-Mth.cos(yaw), 0.0, -Mth.sin(yaw));
			Vec3 home = right.scale(BALL_SIDE).add(0.0, height * BALL_UP + BALL_BOB * Mth.sin(time * 0.1F), 0.0);
			Vec3 at = flight(entity, rocks, home, feet, time, partial, rocks.from(), GravityRocks.BALL_FLIGHT);
			float radius = BALL_RADIUS + BALL_RADIUS_PER_ROCK * rocks.count();
			int n = rocks.count();
			drawn = new Rock[n + 1];
			float each = 3.9F * radius / Mth.sqrt(n);
			float spin = time * BALL_SPIN;
			for (int i = 0; i < n; i++) {
				// spread evenly over the sphere (a golden-angle spiral), turning
				float y = 1.0F - 2.0F * (i + 0.5F) / n;
				float r = Mth.sqrt(Math.max(0.0F, 1.0F - y * y));
				float phi = i * 2.39996F + spin;
				drawn[i] = new Rock((float)at.x + radius * r * Mth.cos(phi), (float)at.y + radius * y, (float)at.z + radius * r * Mth.sin(phi), each,
					phi * Mth.RAD_TO_DEG + i * 53.0F, i * 71.0F);
			}
			// a core, so it isn't hollow
			drawn[n] = new Rock((float)at.x, (float)at.y, (float)at.z, radius * 1.5F, spin * Mth.RAD_TO_DEG, 20.0F);
		} else {
			int n = Math.min(rocks.count(), ORBIT_DRAWN);
			drawn = new Rock[n];
			float spin = time * ORBIT_SPIN;
			for (int i = 0; i < n; i++) {
				float angle = Mth.TWO_PI * i / n + spin;
				Vec3 slot = new Vec3(GravityRocks.ORBIT_RADIUS * Mth.cos(angle), height * ORBIT_UP + 0.12F * Mth.sin(time * 0.15F + i * 1.7F),
					GravityRocks.ORBIT_RADIUS * Mth.sin(angle));
				// a rock of the volley under way leaves its place for the foe and comes back
				Vec3 at = slot;
				if (rocks.target() >= 0) {
					for (int shot = i; shot < rocks.shots(); shot += n) {
						long leaves = rocks.from() + (long)shot * GravityRocks.VOLLEY_EVERY;
						Vec3 out = flight(entity, rocks, slot, feet, time, partial, leaves, GravityRocks.ROCK_FLIGHT);
						if (out != slot) {
							at = out;
							break;
						}
					}
				}
				drawn[i] = new Rock((float)at.x, (float)at.y, (float)at.z, ORBIT_ROCK, angle * Mth.RAD_TO_DEG * 2.0F + i * 41.0F, time * 6.0F + i * 67.0F);
			}
		}
		state.setData(KEY, new Drawn(item, box.getCenter(), modelSize, bodyRot, drawn));
	}

	/**
	 * Where something that sets off from {@code home} at {@code leaves} for the follow-up's foe is now: out over
	 * {@code ticks}, back over as many. {@code home} itself when it isn't flying.
	 */
	private static Vec3 flight(LivingEntity entity, GravityRocks.Rocks rocks, Vec3 home, Vec3 feet, float time, float partial, long leaves, int ticks) {
		if (rocks.target() < 0) {
			return home;
		}
		float t = (time - leaves) / ticks;
		if (t < 0.0F || t >= 2.0F) {
			return home;
		}
		Entity target = entity.level().getEntity(rocks.target());
		if (target == null) {
			return home;
		}
		Vec3 aim = target.getPosition(partial).add(0.0, target.getBbHeight() * 0.55, 0.0).subtract(feet);
		float k = t < 1.0F ? t * t : (2.0F - t) * (2.0F - t) * (3.0F - 2.0F * (2.0F - t));
		return home.lerp(aim, Mth.clamp(k, 0.0F, 1.0F));
	}

	static final class Layer<S extends HumanoidRenderState, M extends HumanoidModel<S>> extends RenderLayer<S, M> {
		Layer(RenderLayerParent<S, M> renderer) {
			super(renderer);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, S state, float yRot, float xRot) {
			Drawn drawn = state.getData(KEY);
			if (drawn == null || state.isInvisible) {
				return;
			}
			poseStack.pushPose();
			// undo what the renderer did before the layers (LivingEntityRenderer.submit, a standing body): back to the feet,
			// world axes, world size
			poseStack.translate(0.0F, 1.501F, 0.0F);
			poseStack.scale(1.0F / AVATAR_SCALE, 1.0F / AVATAR_SCALE, 1.0F / AVATAR_SCALE);
			poseStack.scale(-1.0F, -1.0F, 1.0F);
			poseStack.rotateDegrees(Axis.YP, -(180.0F - drawn.bodyRot()));
			float scale = state.scale <= 0.0F ? 1.0F : state.scale;
			poseStack.scale(1.0F / scale, 1.0F / scale, 1.0F / scale);
			Vec3 center = drawn.center();
			for (Rock rock : drawn.rocks()) {
				poseStack.pushPose();
				poseStack.translate(rock.x(), rock.y(), rock.z());
				poseStack.rotateDegrees(Axis.YP, rock.yaw());
				poseStack.rotateDegrees(Axis.XP, rock.pitch());
				float s = rock.size() / drawn.modelSize();
				poseStack.scale(s, s, s);
				poseStack.translate(-center.x, -center.y, -center.z);
				drawn.item().submit(poseStack, collector, lightCoords, OverlayTexture.NO_OVERLAY, 0);
				poseStack.popPose();
			}
			poseStack.popPose();
		}
	}
}
