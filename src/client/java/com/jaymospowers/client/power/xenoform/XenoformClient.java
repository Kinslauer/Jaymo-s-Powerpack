package com.jaymospowers.client.power.xenoform;

import com.jaymospowers.power.xenoform.XenoformPower;
import com.jaymospowers.power.xenoform.XenoformState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** What everyone's client does with a transformed player: hides them, so {@link XenoformLayer} can draw the alien. */
public final class XenoformClient {
	/** The alien a player is, on their render state (null: themselves). */
	public static final RenderStateDataKey<String> FORM = RenderStateDataKey.create(() -> "jaymospowers:xenoform_form");
	/** True while the player's form is in its last seconds (the alien glows red). */
	public static final RenderStateDataKey<Boolean> ENDING = RenderStateDataKey.create(() -> "jaymospowers:xenoform_ending");
	/** (0.18.95) Rivet's shell: the block it's melded into (null: none). */
	public static final RenderStateDataKey<String> SHELL = RenderStateDataKey.create(() -> "jaymospowers:xenoform_shell");
	/** (0.18.96) The stalker's shed skin: drawn as a pale, see-through husk. */
	public static final RenderStateDataKey<Boolean> SHED = RenderStateDataKey.create(() -> "jaymospowers:xenoform_shed");
	/** (0.18.93) True while the alien is its Ultimate form (armoured, spiked, glowing seams). */
	public static final RenderStateDataKey<Boolean> ULTIMATE = RenderStateDataKey.create(() -> "jaymospowers:xenoform_ultimate");
	/** (0.18.119) How far into Towerback's grab (ticks; -1: not grabbing). */
	/** (0.18.125, patch 8) A rival band's alien (or wearer): darkened, its band and insignia red. */
	public static final RenderStateDataKey<Boolean> DARK = RenderStateDataKey.create(() -> "jaymospowers:xenoform_dark");
	/** (0.18.126, patch 9) How the band is dressed: the glow (RGB), the alien's look, the plate's shape and spikes. */
	public static final RenderStateDataKey<Integer> GLOW = RenderStateDataKey.create(() -> "jaymospowers:xenoform_glow");
	public static final RenderStateDataKey<String> LOOK = RenderStateDataKey.create(() -> "jaymospowers:xenoform_look");
	public static final RenderStateDataKey<String> PLATE = RenderStateDataKey.create(() -> "jaymospowers:xenoform_plate");
	public static final RenderStateDataKey<Boolean> SPIKES = RenderStateDataKey.create(() -> "jaymospowers:xenoform_spikes");
	public static final RenderStateDataKey<Float> TOWER_GRAB = RenderStateDataKey.create(() -> "jaymospowers:xenoform_tower_grab");
	/** (0.19.28) How far into Towerback's swat (ticks; -1: not swatting). */
	public static final RenderStateDataKey<Float> TOWER_SWAT = RenderStateDataKey.create(() -> "jaymospowers:xenoform_tower_swat");
	/**
	 * (0.19.28) Towerback's stride (radians: a foot comes down at every half turn past a quarter, {@link #stride}), so its
	 * legs keep to the ground it covers. Null: not Towerback.
	 */
	public static final RenderStateDataKey<Float> TOWER_STRIDE = RenderStateDataKey.create(() -> "jaymospowers:xenoform_tower_stride");
	/** How much of a stride's error is taken up each tick, once a footfall shows where a foot should be. */
	private static final double STRIDE_CATCH_UP = 0.3;

	/** One Towerback's walk on this client: its stride now and last tick, when it was last moved on, the error left to take up, its footfalls. */
	private static final class Stride {
		double phase, prev, catchUp;
		long tick = Long.MIN_VALUE;
		int steps = -1;
	}

	private static final java.util.Map<Player, Stride> STRIDES = new java.util.WeakHashMap<>();

	private XenoformClient() {
	}

	/** From the avatar renderer's extractRenderState, last. */
	public static void extract(Avatar entity, AvatarRenderState state, float partialTicks) {
		Player player = entity instanceof Player p ? p : null;
		String form = player != null ? player.getAttached(XenoformState.FORM) : null;
		state.setData(FORM, form);
		state.setData(XenoformSlap.KEY, XenoformSlap.extract(entity, form, partialTicks));
		state.setData(XenoBand.KEY, form == null && player != null && XenoformPower.has(player));
		state.setData(ENDING, form != null && XenoformState.ending(player));
		state.setData(DARK, player != null && XenoformState.rival(player));
		if (player != null && XenoformPower.has(player)) {
			com.jaymospowers.power.xenoform.BandStyle.Style style = com.jaymospowers.power.xenoform.BandStyle.get(player);
			state.setData(GLOW, com.jaymospowers.power.xenoform.BandStyle.glow(player));
			state.setData(LOOK, form == null ? null : style.look(form).id);
			state.setData(PLATE, style.plate());
			state.setData(SPIKES, style.spikes());
		} else {
			state.setData(GLOW, null);
			state.setData(LOOK, null);
			state.setData(PLATE, null);
			state.setData(SPIKES, false);
		}
		state.setData(ULTIMATE, form != null && Boolean.TRUE.equals(player.getAttached(XenoformState.ULTIMATE)));
		state.setData(SHELL, form != null ? player.getAttached(XenoformState.SHELL) : null);
		state.setData(TOWER_GRAB, form != null ? com.jaymospowers.power.xenoform.forms.Towerback.grabPhase(player, partialTicks) : -1.0F);
		state.setData(TOWER_SWAT, form != null ? com.jaymospowers.power.xenoform.forms.Towerback.swatPhase(player, partialTicks) : -1.0F);
		state.setData(TOWER_STRIDE, "towerback".equals(form) ? stride(player, partialTicks) : null);
		XenoformFlightPose.extract(entity, state, partialTicks);
		SkitterCeiling.extract(entity, state, partialTicks);
		if (form == null) {
			return;
		}
		if ("billow".equals(form)) {
			// (0.19.34) the cloud's low box is the swimming pose's, but it isn't swimming: draw it level
			state.swimAmount = 0.0F;
			state.isVisuallySwimming = false;
			if (state.pose == net.minecraft.world.entity.Pose.SWIMMING) {
				state.pose = net.minecraft.world.entity.Pose.STANDING;
			}
		}
		hideBody(state);
	}

	/**
	 * (0.19.28) Towerback's stride: half a turn for every step's length it moves over the ground (the server's
	 * {@code Towerback#STRIDE_PER_SCALE} times its size), so a foot comes down each time the ground shakes. Each new
	 * footfall from the server ({@link XenoformState#STEP}) eases the stride round to the nearest foot coming down.
	 */
	private static float stride(Player player, float partialTicks) {
		Stride s = STRIDES.computeIfAbsent(player, p -> new Stride());
		long now = player.level().getGameTime();
		if (s.tick != now) {
			s.prev = s.phase;
			double step = com.jaymospowers.power.xenoform.forms.Towerback.STRIDE_PER_SCALE * Math.max(1.0F, player.getScale());
			double moved = Math.sqrt(net.minecraft.util.Mth.square(player.getX() - player.xo) + net.minecraft.util.Mth.square(player.getZ() - player.zo));
			if (s.tick != Long.MIN_VALUE && moved < step) {
				// (a teleport doesn't spin the legs)
				s.phase += moved / step * Math.PI;
			}
			Integer steps = player.getAttached(XenoformState.STEP);
			int count = steps != null ? steps : 0;
			if (s.steps >= 0 && count != s.steps) {
				// a foot is down now: at a quarter turn past every half turn
				double down = Math.PI * Math.round((s.phase - Math.PI * 0.5) / Math.PI) + Math.PI * 0.5;
				s.catchUp = down - s.phase;
			}
			s.steps = count;
			double take = s.catchUp * STRIDE_CATCH_UP;
			s.phase += take;
			s.catchUp -= take;
			if (s.phase > Math.PI * 4.0) {
				s.phase -= Math.PI * 4.0;
				s.prev -= Math.PI * 4.0;
			}
			s.tick = now;
		}
		return (float)(s.prev + (s.phase - s.prev) * partialTicks);
	}

	/**
	 * (0.15.97) An afterimage of a transformed runner ({@code ChronoStreak}, for Velox's Velocity): the alien their frame
	 * wears, translucent in {@code tint}, laid out as the player's body would be. False (nothing drawn) for a player in
	 * their own shape.
	 */
	public static boolean submitGhost(AvatarRenderState state, PoseStack poseStack, SubmitNodeCollector collector, int light, int tint) {
		XenoFormModels.FormModel model = XenoFormModels.of(state.getData(FORM));
		if (model == null) {
			return false;
		}
		poseStack.pushPose();
		if (model.renderScale != 1.0F) {
			// as XenoformLayer: drawn up from the feet
			poseStack.translate(0.0F, 1.501F, 0.0F);
			poseStack.scale(model.renderScale, model.renderScale, model.renderScale);
			poseStack.translate(0.0F, -1.501F, 0.0F);
		}
		collector.submitModel(model, state, poseStack, RenderTypes.entityTranslucent(model.texture), light, OverlayTexture.NO_OVERLAY, tint, null,
			EntityRenderState.NO_OUTLINE);
		poseStack.popPose();
		return true;
	}

	/** The player's own body goes: no skin, no armour, no cape, nothing in the hands or on the head. */
	public static void hideBody(AvatarRenderState state) {
		state.isInvisible = true;
		state.isInvisibleToPlayer = true;
		state.headEquipment = ItemStack.EMPTY;
		state.chestEquipment = ItemStack.EMPTY;
		state.legsEquipment = ItemStack.EMPTY;
		state.feetEquipment = ItemStack.EMPTY;
		state.headItem.clear();
		state.wornHeadType = null;
		state.wornHeadProfile = null;
		state.heldOnHead.clear();
		state.rightHandItemState.clear();
		state.leftHandItemState.clear();
		state.leftHandItemStack = ItemStack.EMPTY;
		state.showCape = false;
		state.showExtraEars = false;
		state.parrotOnLeftShoulder = null;
		state.parrotOnRightShoulder = null;
		state.arrowCount = 0;
		state.stingerCount = 0;
	}
}
