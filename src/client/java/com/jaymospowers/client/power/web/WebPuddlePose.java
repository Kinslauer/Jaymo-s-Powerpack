package com.jaymospowers.client.power.web;

import com.jaymospowers.power.web.WebPuddle;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;

/**
 * A host in {@link WebPuddle}: the player model is hidden (for everyone, and for you in third person; in first person
 * your hands go and the camera sits at the puddle's low eye) and a flat spread of goo globs, the suit's own skin in the
 * strain's colour, lies in its place. Each glob is a squashed lobe drawn with {@link WebGooGlobs}' sphere; they creep
 * slowly about the middle and swell, so the puddle never lies still. The melt and the pour ease over
 * {@link WebPuddle#SPREAD_TICKS}.
 */
public final class WebPuddlePose extends RenderLayer<AvatarRenderState, PlayerModel> {
	/** On the render state of a puddled host: how far spread, 0 to 1. */
	public static final RenderStateDataKey<Float> KEY = RenderStateDataKey.create(() -> "jaymospowers:web_puddle");

	/** How many lobes, how far from the middle they may creep (blocks), and the size range of one (half-width, blocks). */
	private static final int GLOBS = 9;
	private static final float CREEP = 0.26F, SIZE_MIN = 0.13F, SIZE_MAX = 0.24F;
	/** How flat a lobe lies (its thickness, share of its width) and the ground in model space (blocks below the origin). */
	private static final float FLAT = 0.3F, GROUND = 1.5F;
	/** Surface heaving, as the globs on the suit do. */
	private static final float WOBBLE = 0.08F;

	public WebPuddlePose(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
		super(renderer);
	}

	/** From the avatar renderer's extractRenderState: puts the spread on the state and hides the model. */
	public static void extract(Avatar entity, AvatarRenderState state, float partialTicks) {
		if (!(entity instanceof Player player) || !WebPuddle.puddled(player)) {
			state.setData(KEY, null);
			return;
		}
		long since = WebPuddle.since(player);
		float age = player.level().getGameTime() - since + partialTicks;
		float into = Mth.clamp(age / WebPuddle.SPREAD_TICKS, 0.0F, 1.0F);
		float out = Mth.clamp((WebPuddle.MAX_TICKS - age) / WebPuddle.SPREAD_TICKS, 0.0F, 1.0F);
		state.setData(KEY, Math.min(into, out));
		state.isInvisible = true;
		state.isInvisibleToPlayer = true;
		state.headEquipment = ItemStack.EMPTY;
		state.chestEquipment = ItemStack.EMPTY;
		state.legsEquipment = ItemStack.EMPTY;
		state.feetEquipment = ItemStack.EMPTY;
		state.headItem.clear();
		state.rightHandItemState.clear();
		state.leftHandItemState.clear();
		state.showCape = false;
		state.parrotOnLeftShoulder = null;
		state.parrotOnRightShoulder = null;
	}

	/** The state is a puddled host (first person draws no hands then). */
	public static boolean hidden(AvatarRenderState state) {
		return state != null && state.getData(KEY) != null;
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, AvatarRenderState state, float yRot, float xRot) {
		Float spread = state.getData(KEY);
		if (spread == null || spread <= 0.02F) {
			return;
		}
		float ease = spread * spread * (3.0F - 2.0F * spread);
		float time = state.ageInTicks;
		List<float[]> lobes = new ArrayList<>();
		Vector3f up = new Vector3f(0.0F, -1.0F, 0.0F);
		Vector3f e1 = new Vector3f(1.0F, 0.0F, 0.0F);
		Vector3f e2 = new Vector3f(0.0F, 0.0F, -1.0F);
		for (int i = 0; i < GLOBS; i++) {
			double seed = state.id * 31.0 + i * 12.9898;
			float around = (float)(Math.PI * 2.0 * (i + 0.37 * hash(seed)) / GLOBS);
			float far = i == 0 ? 0.0F : CREEP * (0.45F + 0.55F * (float)hash(seed + 1.0));
			float size = Mth.lerp((float)hash(seed + 2.0), SIZE_MIN, SIZE_MAX) * (i == 0 ? 1.4F : 1.0F);
			float speed = 0.02F + 0.03F * (float)hash(seed + 3.0);
			float phase = (float)(hash(seed + 4.0) * Math.PI * 2.0);
			float x = far * ease * Mth.cos(around + time * speed);
			float z = far * ease * Mth.sin(around + time * speed);
			float swell = 1.0F + 0.2F * Mth.sin(time * 0.08F + phase);
			float r = size * swell * ease;
			float thick = r * FLAT;
			Vector3f c = new Vector3f(x, GROUND - thick * 0.3F, z);
			float turn = phase + time * 0.012F;
			Vector3f d1 = new Vector3f(e1).mul(Mth.cos(turn)).add(new Vector3f(e2).mul(Mth.sin(turn)));
			Vector3f d2 = new Vector3f(d1).cross(up).normalize();
			lobes.add(WebGooGlobs.lobe(c, d1, up, d2, r * 1.3F, thick, r, phase, WOBBLE));
		}
		Identifier texture = WebStrainLook.texture(WebSymbioteLayer.TEXTURE, state);
		float[] patch = WebGooGlobs.SUIT_PATCH;
		collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(texture), (pose, buffer) -> {
			for (float[] lobe : lobes) {
				WebGooGlobs.sphere(buffer, pose, lobe, patch, time, lightCoords);
			}
		});
	}

	private static double hash(double seed) {
		double x = Math.sin(seed) * 43758.5453;
		return x - Math.floor(x);
	}
}
