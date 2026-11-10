package com.jaymospowers.client.armory.guns;

import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.model.DefaultedEntityGeoModel;
import com.geckolib.renderer.GeoObjectRenderer;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.renderer.layer.builtin.TextureLayerGeoLayer;
import com.jaymospowers.JaymosPowers;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * Draws one of the flintlock guns with GeckoLib ({@code geckolib/models/entity/gun_<name>.geo.json}, written by
 * {@code tools/gen_guns_geckolib.py}), in whatever pose its hand's animation has it. The model is drawn where the
 * item model's transformation lays it (no offset of its own); its muzzle flash shows only while it fires, full-bright.
 */
final class GunRenderer extends GeoObjectRenderer<GunAnimatable, GunState, GeoRenderState> {
	static final DataTicket<GunState.Pose> POSE = DataTicket.create("jaymospowers_gun_pose", GunState.Pose.class);
	static final DataTicket<Boolean> AIMING = DataTicket.create("jaymospowers_gun_aiming", Boolean.class);
	static final DataTicket<Long> SHOT_START = DataTicket.create("jaymospowers_gun_shot_start", Long.class);
	static final DataTicket<Long> DRAW_START = DataTicket.create("jaymospowers_gun_draw_start", Long.class);
	static final DataTicket<Boolean> FIRING = DataTicket.create("jaymospowers_gun_firing", Boolean.class);
	static final DataTicket<Identifier> TEXTURE = DataTicket.create("jaymospowers_gun_texture", Identifier.class);
	@SuppressWarnings({"unchecked", "rawtypes"})
	static final DataTicket<List<String>> HIDDEN = (DataTicket) DataTicket.create("jaymospowers_gun_hidden", List.class);
	/** The bone holding the muzzle flash. */
	static final String FLASH = "flash";
	/** How long after a shot the flash may show (ticks); the fire animation itself shrinks it away sooner. */
	static final int FLASH_TICKS = 8;

	/** GeckoLib wants the camera for a render pass; a gun in the hand uses nothing of it (no name tags, no billboards). */
	private static final CameraRenderState CAMERA = new CameraRenderState();
	private static final Map<String, GunRenderer> BY_NAME = new ConcurrentHashMap<>();

	final GunAnimatable animatable;

	/** The renderer for gun model {@code name} (musket, blunderbuss, flintlock). */
	static GunRenderer of(String name) {
		return BY_NAME.computeIfAbsent(name, GunRenderer::new);
	}

	private GunRenderer(String name) {
		super(new Model(name));
		this.animatable = new GunAnimatable(name);
		withRenderLayer(new TextureLayerGeoLayer<>(this, JaymosPowers.id("textures/entity/gun_" + name + "_glow.png"), RenderTypes::eyes));
	}

	void submit(GunState state, PoseStack poseStack, SubmitNodeCollector collector, int light) {
		Minecraft minecraft = Minecraft.getInstance();
		performRenderPass(this.animatable, state, poseStack, collector, CAMERA, light,
			minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false));
	}

	@Override
	public long getInstanceId(GunAnimatable animatable, @Nullable GunState state) {
		return state == null ? 0L : state.instance();
	}

	@Override
	public void addRenderData(GunAnimatable animatable, @Nullable GunState state, GeoRenderState renderState, float partialTick) {
		if (state == null) {
			return;
		}
		renderState.addGeckolibData(POSE, state.pose());
		renderState.addGeckolibData(AIMING, state.aiming());
		renderState.addGeckolibData(SHOT_START, state.shotStart());
		renderState.addGeckolibData(DRAW_START, state.drawStart());
		renderState.addGeckolibData(FIRING, state.shotStart() >= 0 && AnimatedGunModel.ticksSince(state.shotStart()) < FLASH_TICKS);
		renderState.addGeckolibData(HIDDEN, state.hidden());
		if (state.texture() != null) {
			renderState.addGeckolibData(TEXTURE, state.texture());
		}
	}

	@Override
	public void adjustRenderPose(RenderPassInfo<GeoRenderState> info) {
		// the item model's transformation already lays the model where the gun goes in the item
	}

	@Override
	public void adjustModelBonesForRender(RenderPassInfo<GeoRenderState> info, BoneSnapshots snapshots) {
		super.adjustModelBonesForRender(info, snapshots);
		for (String bone : info.getOrDefaultGeckolibData(HIDDEN, List.of())) {
			snapshots.ifPresent(bone, b -> b.skipRender(true));
		}
		if (!info.getOrDefaultGeckolibData(FIRING, false)) {
			snapshots.ifPresent(FLASH, b -> b.skipRender(true));
		}
	}

	/** The gun's model and animations by name, its texture the item's own if it names one (a bayonet's metal, the void's). */
	private static final class Model extends DefaultedEntityGeoModel<GunAnimatable> {
		Model(String name) {
			super(JaymosPowers.id("gun_" + name));
		}

		@Override
		public Identifier getTextureResource(GeoRenderState state) {
			Identifier texture = state.getGeckolibData(TEXTURE);
			return texture != null ? texture : super.getTextureResource(state);
		}
	}
}
