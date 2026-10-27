package com.jaymospowers.client.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.client.power.wind.WindPose;
import com.jaymospowers.power.zfighter.ZFighterForms;
import com.jaymospowers.power.zfighter.ZFighterInstinct;
import com.jaymospowers.power.zfighter.ZFighterLineage;
import com.jaymospowers.power.zfighter.ZFighterPower;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;
import org.joml.Quaternionf;

/**
 * Super's hair ({@code textures/entity/zfighter_hair.png}, pale strands tinted the form's colour, lit from within): a mass
 * of wide, tapering locks, one model per form in {@link ZFighterHairStyles}, in one of two sets that is his for good:
 * swept (every lock raked back, close together) or wild (long locks thrown up and to one side, a Super 3 mane that
 * billows out). The locks blow back when he moves or flies and flutter up when he charges. Earthlings and the
 * Warrior-born only (the Verdant grow, the Frost-born have no hair to change); never the Great Ape; never drawn in your
 * own first-person view, where it would hang in front of your eyes.
 */
public final class ZFighterHair {
	/** The form (1-3), its colour and his set (0-5, {@link ZFighterHairStyles}), or null for no Super hair. */
	public record Hair(int form, int color, int style) {
	}

	/** How many sets there are: swept, wild, flame, gale, crown and fringe. */
	public static final int STYLES = 6;

	/** How hard the hair blows back per joint at full wind (radians), and the root of each lock (a little less). */
	static final float BLOW_PER_JOINT = 0.1F;
	static final float BLOW_ROOT = 0.04F;
	/** Walking or running: wind from walk speed. Flying: from the flight pose, more when cruising fast. */
	static final float WALK_WIND = 0.45F;
	static final float HOVER_WIND = 0.45F;
	static final float CRUISE_WIND = 1.0F;
	/** Flutter (radians per joint) when still, per unit of wind, and while charging; how fast it ripples. */
	static final float FLUTTER_IDLE = 0.012F;
	static final float FLUTTER_WIND = 0.05F;
	static final float FLUTTER_CHARGE = 0.07F;
	static final float FLUTTER_SPEED = 0.2F;
	static final float FLUTTER_SPEED_WIND = 0.35F;
	static final float FLUTTER_SPEED_CHARGE = 0.55F;
	/** Charging: how much the locks straighten up out of their curve (the ki lifts them), and the mane lifts back. */
	static final float CHARGE_STRAIGHTEN = 0.5F;
	static final float CHARGE_LIFT = 0.04F;

	/** The set that is his: random across fighters, the same every time for one. */
	public static int style(java.util.UUID id) {
		long bits = id.getMostSignificantBits() ^ id.getLeastSignificantBits();
		return (int)Math.floorMod(bits ^ (bits >>> 32), (long)STYLES);
	}

	public static final RenderStateDataKey<Hair> KEY = RenderStateDataKey.create(() -> "jaymospowers:zfighter_hair");
	static final Identifier TEXTURE = JaymosPowers.id("textures/entity/zfighter_hair.png");
	static final int FULL_BRIGHT = 0xF000F0;
	/** The texture's 4 bands, darker at the roots, brighter toward the tips; each band is 16 pixels tall. */
	static final int BANDS = 4;

	private static final ModelPart[] CAPS = new ModelPart[ZFighterHairStyles.STYLES.length];
	private static final Map<Long, ModelPart> SEGMENTS = new HashMap<>();

	private ZFighterHair() {
	}

	private static ModelPart cap(int index) {
		if (CAPS[index] == null) {
			MeshDefinition mesh = new MeshDefinition();
			CubeListBuilder cubes = CubeListBuilder.create();
			for (float[] b : ZFighterHairStyles.STYLES[index].cap()) {
				cubes.texOffs(0, 0).addBox(b[0], b[1], b[2], b[3], b[4], b[5], new CubeDeformation(b[6]));
			}
			mesh.getRoot().addOrReplaceChild("cap", cubes, PartPose.ZERO);
			CAPS[index] = LayerDefinition.create(mesh, 64, 64).bakeRoot().getChild("cap");
		}
		return CAPS[index];
	}

	/** One lock segment: a box w x length x d standing up from its pivot, its strands from one band of the texture. */
	private static ModelPart segment(float w, float d, float length, int band) {
		long key = ((long)Math.round(w * 2) << 48) | ((long)Math.round(d * 2) << 32) | ((long)Math.round(length * 2) << 16) | band;
		return SEGMENTS.computeIfAbsent(key, k -> {
			MeshDefinition mesh = new MeshDefinition();
			mesh.getRoot().addOrReplaceChild("part", CubeListBuilder.create().texOffs(0, band * 16).addBox(-w / 2.0F, -length, -d / 2.0F, w, length, d),
				PartPose.ZERO);
			return LayerDefinition.create(mesh, 64, 64).bakeRoot().getChild("part");
		});
	}

	static void extract(Avatar entity, AvatarRenderState state) {
		Hair hair = null;
		Minecraft minecraft = Minecraft.getInstance();
		boolean ownView = entity == minecraft.getCameraEntity() && minecraft.options.getCameraType().isFirstPerson();
		if (entity instanceof Player player && !ZFighterLineage.ape(player) && !ownView) {
			Integer form = player.getAttached(ZFighterPower.FORM);
			int lineage = ZFighterLineage.of(player);
			if (form != null && form > 0 && (lineage == ZFighterLineage.NONE || lineage == ZFighterLineage.EARTHLING
				|| lineage == ZFighterLineage.WARRIOR)) {
				// (0.17.50) False Super wears the first Super's hair, the god form Super 3's in red, silver instinct in silver, the
				// legendary line Super 3's in green
				hair = new Hair(ZFighterForms.hairForm(form), ZFighterInstinct.active(player) ? ZFighterInstinct.COLOR : ZFighterLineage.color(lineage, form),
					style(player.getUUID()));
			}
		}
		state.setData(KEY, hair);
		if (hair != null) {
			// his own hair (the skin's hat layer) goes under the Super hair, or it pokes through
			state.showHat = false;
		}
	}

	/** How hard the wind is on his hair (0-1): from walking, or from flying (harder when cruising fast). */
	static float wind(AvatarRenderState state) {
		float wind = Mth.clamp(state.walkAnimationSpeed, 0.0F, 1.0F) * WALK_WIND;
		ZFighterFlightPose.Pose flight = state.getData(ZFighterFlightPose.KEY);
		if (flight != null && flight.amount() > 0.001F) {
			Float cruise = state.getData(WindPose.KEY);
			float fly = ZFighterFlightPose.smooth(flight.amount()) * Mth.lerp(cruise == null ? 0.0F : cruise, HOVER_WIND, CRUISE_WIND);
			wind = Math.max(wind, fly);
		}
		return Mth.clamp(wind, 0.0F, 1.0F);
	}

	/** How far into the charge pose he is (0-1). */
	static float charge(AvatarRenderState state) {
		ZFighterChargePose.Pose pose = state.getData(ZFighterChargePose.KEY);
		return pose == null ? 0.0F : ZFighterFlightPose.smooth(Mth.clamp(pose.amount(), 0.0F, 1.0F));
	}

	static final class Layer extends RenderLayer<AvatarRenderState, PlayerModel> {
		Layer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
			super(renderer);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, AvatarRenderState state, float yRot, float xRot) {
			Hair hair = state.getData(KEY);
			if (hair == null || state.isInvisible || com.jaymospowers.client.BodyCover.head(state)) {
				// (0.12.5) under a mask or hood that covers the head, or the symbiote
				return;
			}
			int index = Math.floorMod(hair.style(), STYLES) * 3 + Mth.clamp(hair.form(), 1, 3) - 1;
			ZFighterHairStyles.Style style = ZFighterHairStyles.STYLES[index];
			int color = 0xFF000000 | hair.color();
			float wind = wind(state);
			float charge = charge(state);
			float time = state.ageInTicks;
			float flutter = FLUTTER_IDLE + FLUTTER_WIND * wind + FLUTTER_CHARGE * charge;
			float speed = FLUTTER_SPEED + FLUTTER_SPEED_WIND * wind + FLUTTER_SPEED_CHARGE * charge;
			poseStack.pushPose();
			this.getParentModel().head.translateAndRotate(poseStack);
			collector.submitModelPart(cap(index), poseStack, RenderTypes.entityCutout(TEXTURE), FULL_BRIGHT, OverlayTexture.NO_OVERLAY, null, color);
			float[][] locks = style.locks();
			for (int l = 0; l < locks.length; l++) {
				float[] lock = locks[l];
				int n = (lock.length - 6) / 5;
				// "back" is toward +z: an upright lock leans back with less xRot, one hanging down (the mane) with more
				float back = Mth.cos(lock[3]) >= 0.0F ? -1.0F : 1.0F;
				float blow = wind * BLOW_PER_JOINT + (back > 0.0F ? charge * CHARGE_LIFT : 0.0F);
				float phase = l * 1.7F;
				poseStack.pushPose();
				poseStack.translate(lock[0] / 16.0F, lock[1] / 16.0F, lock[2] / 16.0F);
				for (int i = 0; i < n; i++) {
					int s = 6 + i * 5;
					float wave = flutter * Mth.sin(time * speed + phase + i * 0.7F);
					float sway = flutter * 0.6F * Mth.cos(time * speed * 1.3F + phase + i * 0.5F);
					float x, z;
					if (i == 0) {
						x = lock[3] + back * wind * BLOW_ROOT + wave * 0.5F;
						z = lock[5] + sway * 0.5F;
					} else {
						poseStack.translate(0.0F, (-lock[s - 3] + 0.5F) / 16.0F, 0.0F);
						x = lock[s + 3] * (1.0F - CHARGE_STRAIGHTEN * charge) + back * blow + wave;
						z = lock[s + 4] * (1.0F - CHARGE_STRAIGHTEN * charge) + sway;
					}
					poseStack.rotate(new Quaternionf().rotationZYX(z, i == 0 ? lock[4] : 0.0F, x));
					int band = Math.min(BANDS - 1, i * BANDS / n);
					collector.submitModelPart(segment(lock[s], lock[s + 1], lock[s + 2], band), poseStack, RenderTypes.entityCutout(TEXTURE), FULL_BRIGHT,
						OverlayTexture.NO_OVERLAY, null, color);
				}
				poseStack.popPose();
			}
			poseStack.popPose();
		}
	}
}
