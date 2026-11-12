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
import org.jspecify.annotations.Nullable;

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
	/** Whether Towerback is hauling (sprinting): its arms hardly swing and it leans in ({@code XenoFormModels.Towerback}). */
	public static final RenderStateDataKey<Boolean> TOWER_HAUL = RenderStateDataKey.create(() -> "jaymospowers:xenoform_tower_haul");
	/** (0.19.62) A four-legged alien's ability pose from {@code XenoformState.ACT}: "charge", "roll", "spin" or null. */
	public static final RenderStateDataKey<String> ACT = RenderStateDataKey.create(() -> "jaymospowers:xenoform_act");
	/** (0.19.66) A low-box alien (Croaker) holding sneak, which its pose can't show. */
	public static final RenderStateDataKey<Boolean> SNEAK = RenderStateDataKey.create(() -> "jaymospowers:xenoform_sneak");
	/** (0.20.9) The change-in morph this player is in ({@link XenoMorph}), or null. */
	public static final RenderStateDataKey<XenoMorph.Morph> MORPH = RenderStateDataKey.create(() -> "jaymospowers:xenoform_morph");
	/**
	 * (0.20.60) Which of Bulwark's four arms throws the swing now (0 upper right, 1 upper left, 2 lower right, 3 lower
	 * left; null: not Bulwark). Each new swing goes on to the next arm, so its attacks and its Fist Combo go round all four
	 * (Andy: "attacking with bulwark should alternate between all arms").
	 */
	public static final RenderStateDataKey<Integer> BULWARK_ARM = RenderStateDataKey.create(() -> "jaymospowers:xenoform_bulwark_arm");
	/** Asterion: how many ticks its act ({@link #ACT}) has gone on, the big weapon in its hand (null: none, it swings its hammer), and its swings so far. */
	public static final RenderStateDataKey<Float> ACT_TICKS = RenderStateDataKey.create(() -> "jaymospowers:xenoform_act_ticks");
	public static final RenderStateDataKey<net.minecraft.client.renderer.item.ItemStackRenderState> HELD =
		RenderStateDataKey.create(() -> "jaymospowers:xenoform_held");
	public static final RenderStateDataKey<Integer> SWING_COUNT = RenderStateDataKey.create(() -> "jaymospowers:xenoform_swing_count");
	/** How much of a stride's error is taken up each tick, once a footfall shows where a foot should be. */
	private static final double STRIDE_CATCH_UP = 0.3;

	/** One Bulwark's swings on this client: how many it has thrown, and how far into the swing it was when last drawn. */
	private static final class Swings {
		int count;
		float last;
	}

	private static final java.util.Map<Player, Swings> SWINGS = new java.util.WeakHashMap<>();

	/** One Towerback's walk on this client: its stride now and last tick, when it was last moved on, the error left to take up, its footfalls. */
	private static final class Stride {
		double phase, prev, catchUp;
		long tick = Long.MIN_VALUE;
		int steps = -1;
	}

	private static final java.util.Map<Player, Stride> STRIDES = new java.util.WeakHashMap<>();

	/** One player's act on this client, and the game time it was first seen. */
	private static final class ActClock {
		@Nullable String act;
		long since;
	}

	private static final java.util.Map<Player, ActClock> ACT_CLOCKS = new java.util.WeakHashMap<>();
	/** Asterion's weapon, drawn in its hand: one render state per player, refilled each frame. */
	private static final java.util.Map<Player, net.minecraft.client.renderer.item.ItemStackRenderState> HELD_ITEMS = new java.util.WeakHashMap<>();

	private XenoformClient() {
	}

	/**
	 * (0.20.9) The model a player wearing {@code form} is drawn with: the Ultimate's ({@link XenoFormModels#ultimate}) while their Ultimate is
	 * on and that alien has one, else the alien's own ({@link XenoFormModels#of}). Null: not transformed.
	 */
	public static XenoFormModels.@Nullable FormModel model(@Nullable String form, boolean ultimate) {
		if (ultimate) {
			XenoFormModels.FormModel evolved = XenoFormModels.ultimate(form);
			if (evolved != null) {
				return evolved;
			}
		}
		return XenoFormModels.of(form);
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
		state.setData(MORPH, form != null && player != null ? XenoMorph.get(player, partialTicks) : null);
		state.setData(SHELL, form != null ? player.getAttached(XenoformState.SHELL) : null);
		state.setData(TOWER_GRAB, form != null ? com.jaymospowers.power.xenoform.forms.Towerback.grabPhase(player, partialTicks) : -1.0F);
		state.setData(TOWER_SWAT, form != null ? com.jaymospowers.power.xenoform.forms.Towerback.swatPhase(player, partialTicks) : -1.0F);
		state.setData(ACT, form != null ? player.getAttached(XenoformState.ACT) : null);
		// (0.19.66) a low-box alien's pose never crouches, so its sneak is read off the key (Croaker's flat crouch)
		state.setData(SNEAK, form != null && XenoformState.lowBox(player) && player.isShiftKeyDown() && !player.isInWater());
		state.setData(TOWER_STRIDE, "towerback".equals(form) ? stride(player, partialTicks) : null);
		state.setData(TOWER_HAUL, "towerback".equals(form) && player.isSprinting() ? Boolean.TRUE : null);
		state.setData(BULWARK_ARM, "bulwark".equals(form) ? bulwarkArm(player, state.swingAnimation) : null);
		boolean asterion = "asterion".equals(form);
		state.setData(SWING_COUNT, asterion ? bulwarkArm(player, state.swingAnimation) : null);
		state.setData(ACT_TICKS, asterion ? actTicks(player, state.getData(ACT), partialTicks) : null);
		state.setData(HELD, asterion ? held(player) : null);
		XenoformFlightPose.extract(entity, state, partialTicks);
		WispTail.extract(entity, state, partialTicks);
		WispPhaseClient.extract(entity, state, partialTicks);
		SkitterCeiling.extract(entity, state, partialTicks);
		if (form == null) {
			return;
		}
		if (player != null && XenoformState.lowBox(player) || "skitter".equals(form) && player != null && SkitterCeiling.spiderOrRising(player)
			&& (state.getData(SkitterCeiling.CRAWL_KEY) != null || state.swimAmount > 0.0F) || "gnasher".equals(form)) {
			// (0.19.34) the cloud's low box is the swimming pose's, but it isn't swimming: draw it level (0.19.62, and
			// Skitter's spider, prone, and the swim tilt still easing out once it stands; 0.19.82, and Gnasher's shark,
			// whose body is already level: it swims and crawls on its own, so the swim tilt would stand it on its nose)
			state.swimAmount = 0.0F;
			state.isVisuallySwimming = false;
			if (state.pose == net.minecraft.world.entity.Pose.SWIMMING) {
				state.pose = net.minecraft.world.entity.Pose.STANDING;
			}
		}
		hideBody(state);
	}

	/** How many ticks {@code act} has gone on for {@code player}, counted from when this client first saw it. */
	private static float actTicks(Player player, @Nullable String act, float partialTicks) {
		ActClock clock = ACT_CLOCKS.computeIfAbsent(player, p -> new ActClock());
		long now = player.level().getGameTime();
		if (!java.util.Objects.equals(clock.act, act)) {
			clock.act = act;
			clock.since = now;
		}
		return now - clock.since + partialTicks;
	}

	/** What Asterion holds (its hands are empty unless it wields a big weapon), ready to draw; null when empty. */
	private static net.minecraft.client.renderer.item.@Nullable ItemStackRenderState held(Player player) {
		ItemStack stack = player.getMainHandItem();
		if (stack.isEmpty()) {
			HELD_ITEMS.remove(player);
			return null;
		}
		net.minecraft.client.renderer.item.ItemStackRenderState held = HELD_ITEMS.computeIfAbsent(player,
			p -> new net.minecraft.client.renderer.item.ItemStackRenderState());
		net.minecraft.client.Minecraft.getInstance().getItemModelResolver().updateForLiving(held, stack,
			net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, player);
		return held;
	}

	/**
	 * (0.20.60) The arm Bulwark's swing is thrown with: a swing that starts (from rest, or over one still going, as the
	 * combo's do) moves it on to the next of the four, in turn upper right, upper left, lower right, lower left.
	 */
	private static int bulwarkArm(Player player, float swing) {
		Swings s = SWINGS.computeIfAbsent(player, p -> new Swings());
		if (swing > 0.0F && (s.last <= 0.0F || swing < s.last - 0.1F)) {
			s.count = (s.count + 1) & 3;
		}
		s.last = swing;
		return s.count;
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
			double step = com.jaymospowers.power.xenoform.forms.Towerback.stride(player, Math.max(1.0F, player.getScale()));
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
		XenoFormModels.FormModel model = model(state.getData(FORM), Boolean.TRUE.equals(state.getData(ULTIMATE)));
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
		collector.submitModel(model, state, poseStack, RenderTypes.entityTranslucent(model instanceof XenoFormModels.UltimateModel ? model.ultimateTexture : model.texture), light, OverlayTexture.NO_OVERLAY, tint, null,
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
