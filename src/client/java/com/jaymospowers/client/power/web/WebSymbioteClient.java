package com.jaymospowers.client.power.web;

import com.jaymospowers.client.JaymosPowersClient;
import com.jaymospowers.core.Fate;
import com.jaymospowers.power.web.WebNet;
import com.jaymospowers.power.web.WebSymbiote;
import com.jaymospowers.power.web.WebSymbioteState;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The symbiote and the ascended form, client side: the radial menu key (X), Wall Crawl movement (player motion is
 * client-authoritative), being steered by a takeover, the sidestep nudges, the black suit's render state, and
 * Spider-Sense's red outlines.
 */
public final class WebSymbioteClient {
	/** Whether a player's render state should get the black suit layer. */
	public static final RenderStateDataKey<Boolean> SUIT = RenderStateDataKey.create(() -> "jaymospowers:web_symbiote_suit");
	/** Living Armor's tendrils on this player: how grown (0-1), how far into a lash (0-1), and a clock for the sway. */
	public static final RenderStateDataKey<Tendrils> TENDRILS = RenderStateDataKey.create(() -> "jaymospowers:web_symbiote_tendrils");

	/**
	 * @param strike   how far through a strike (0-1), or -1
	 * @param aimYaw   where the strike goes, relative to the body (radians, + to the wearer's left)
	 * @param aimPitch and how high (radians, + up)
	 * @param side     which side's tendrils strike (+1 left, -1 right)
	 */
	public record Tendrils(float growth, float time, float strike, float aimYaw, float aimPitch, float side) {
	}

	/** A meal in progress: how far through it (0-1), for the jaws. */
	public static final RenderStateDataKey<Float> EATING = RenderStateDataKey.create(() -> "jaymospowers:web_symbiote_eating");
	/** Which meal this is (when it started), so each bite's size is random but the same for everyone. */
	public static final RenderStateDataKey<Long> EATING_SEED = RenderStateDataKey.create(() -> "jaymospowers:web_symbiote_eating_seed");

	/** Player entity id → game time their Living Armor came up (for the tendrils' growth). */
	private static final java.util.Map<Integer, Long> ARMOR_SINCE = new java.util.HashMap<>();

	// ===== wall crawl ===============================================================================================
	/** Climbing speed up and down walls, and crawling speed along walls and ceilings (blocks/tick). */
	static final double CRAWL_CLIMB = 0.2;
	static final double CRAWL_SPEED = 0.17;
	/** Pressed up into a ceiling this hard each tick to stay stuck. */
	static final double CEILING_PRESS = 0.1;
	/** Climbing off the top of a wall: a little hop forward onto the ledge. */
	static final double LEDGE_HOP = 0.35;
	static final double LEDGE_FORWARD = 0.18;
	// ===== takeover ================================================================================================
	/** How fast it drags you at its prey, and how hard it wrenches your view around (0..1 per tick). */
	static final double TAKEOVER_SPEED = 0.34;
	static final float TAKEOVER_TURN = 0.35F;
	// ===== spider-sense ============================================================================================
	/** Red outline colour, and how long a server threat list stays good without a refresh. */
	static final int SENSE_COLOR = 0xFFE02424;
	static final int SENSE_STALE_TICKS = 30;
	// ===============================================================================================================

	static KeyMapping menuKey;

	private static boolean wallClinging;
	private static boolean ceilingClinging;
	private static int takeoverTicks;
	private static int takeoverTarget = -1;
	private static final Set<Integer> THREATS = new HashSet<>();
	private static long threatsAt;

	private WebSymbioteClient() {
	}

	static void init() {
		menuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymospowers.web_symbiote", InputConstants.KEY_X, JaymosPowersClient.CATEGORY));
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			// worked out once a tick, not once per entity per frame
			LocalPlayer self = minecraft.player;
			sensing = self != null && WebPowerClient.isActive(self) && Fate.isAscended(self);
			while (menuKey.consumeClick()) {
				LocalPlayer player = minecraft.player;
				if (player != null && minecraft.gui.screen() == null && !player.isSpectator() && WebSymbiote.isBonded(player) && takeoverTicks <= 0) {
					minecraft.gui.setScreen(new WebSymbioteScreen());
				}
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(WebNet.Takeover.TYPE, (payload, context) -> {
			takeoverTarget = payload.entityId();
			takeoverTicks = payload.entityId() < 0 ? 0 : payload.ticks();
			if (takeoverTicks > 0 && context.client().gui.screen() instanceof WebSymbioteScreen) {
				context.client().gui.setScreen(null);
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(WebNet.Shake.TYPE, (payload, context) -> WebShake.start(payload.strength()));
		ClientPlayNetworking.registerGlobalReceiver(WebNet.Nudge.TYPE, (payload, context) -> {
			LocalPlayer player = context.player();
			if (player != null) {
				player.setDeltaMovement(payload.velocity());
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(WebNet.Sense.TYPE, (payload, context) -> {
			THREATS.clear();
			THREATS.addAll(payload.ids());
			threatsAt = context.player() == null ? 0L : context.player().level().getGameTime();
		});
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((entityType, renderer, helper, context) -> {
			if (renderer instanceof AvatarRenderer<?> avatar) {
				helper.register(new WebSymbioteLayer(avatar));
				helper.register(new WebTendrilLayer(avatar));
				helper.register(new WebMouthLayer(avatar));
			}
		});
	}

	static void reset() {
		wallClinging = false;
		ceilingClinging = false;
		takeoverTicks = 0;
		takeoverTarget = -1;
		THREATS.clear();
	}

	/** From the avatar renderer's extractRenderState: does this player wear the suit. */
	public static void extract(Entity entity, AvatarRenderState state) {
		state.setData(SUIT, entity instanceof Player player && WebSymbiote.suitShown(player) ? Boolean.TRUE : null);
		Long eating = entity.getAttached(WebSymbiote.EATING);
		state.setData(EATING, eating == null ? null
			: net.minecraft.util.Mth.clamp((entity.level().getGameTime() - eating) / (float)WebSymbiote.EAT_TICKS, 0.0F, 1.0F));
		state.setData(EATING_SEED, eating == null ? null : eating * 31L + entity.getId());
		if (state.getData(SUIT) != null || entity instanceof Player p && WebSymbiote.isToggled(p, WebSymbioteState.ARMOR)) {
			// no cape poking out through the suit
			state.showCape = false;
		}
		// Living Armor grows tendrils (whether or not the suit is showing)
		if (entity instanceof Player player && WebSymbiote.isBonded(player) && WebSymbiote.isToggled(player, WebSymbioteState.ARMOR)) {
			long now = player.level().getGameTime();
			long since = ARMOR_SINCE.computeIfAbsent(player.getId(), id -> now);
			float growth = Math.min(1.0F, (now - since) / WebTendrilLayer.GROWN_TICKS);
			// a strike of their own (the server picks the target)
			float strike = -1.0F, aimYaw = 0.0F, aimPitch = 0.0F, side = 1.0F;
			WebSymbiote.Strike s = player.getAttached(WebSymbiote.STRIKE);
			Entity target = s == null ? null : player.level().getEntity(s.target());
			if (s != null && target != null) {
				strike = net.minecraft.util.Mth.clamp((now - s.start()) / (float)WebSymbiote.STRIKE_TICKS, 0.0F, 1.0F);
				net.minecraft.world.phys.Vec3 to = target.getBoundingBox().getCenter().subtract(player.getEyePosition());
				float yaw = (float)(net.minecraft.util.Mth.atan2(to.z, to.x) * net.minecraft.util.Mth.RAD_TO_DEG) - 90.0F;
				aimYaw = net.minecraft.util.Mth.wrapDegrees(yaw - player.yBodyRot) * net.minecraft.util.Mth.DEG_TO_RAD;
				aimPitch = (float)net.minecraft.util.Mth.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z));
				side = aimYaw >= 0.0F ? -1.0F : 1.0F;
			}
			state.setData(TENDRILS, new Tendrils(growth, player.tickCount, strike, aimYaw, aimPitch, side));
		} else {
			ARMOR_SINCE.remove(entity.getId());
			state.setData(TENDRILS, null);
		}
	}

	// ---- per tick (while the web power is active) --------------------------------------------------------------------

	static void tick(Minecraft minecraft, LocalPlayer player, WebSwing swing) {
		if (takeoverTicks > 0) {
			tickTakeover(player);
		}
		tickCrawl(player, swing);
	}

	/** It's steering: straight at its prey, view and all. */
	private static void tickTakeover(LocalPlayer player) {
		Entity target = player.level().getEntity(takeoverTarget);
		if (target == null || !target.isAlive() || --takeoverTicks <= 0) {
			takeoverTicks = 0;
			return;
		}
		Vec3 to = target.getBoundingBox().getCenter().subtract(player.getEyePosition());
		float yaw = (float)(Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90.0F;
		float pitch = (float)(-Mth.atan2(to.y, to.horizontalDistance()) * Mth.RAD_TO_DEG);
		player.setYRot(player.getYRot() + Mth.wrapDegrees(yaw - player.getYRot()) * TAKEOVER_TURN);
		player.setXRot(Mth.clamp(player.getXRot() + (pitch - player.getXRot()) * TAKEOVER_TURN, -90.0F, 90.0F));
		Vec3 flat = new Vec3(to.x, 0.0, to.z);
		if (flat.lengthSqr() > 1.5 * 1.5) {
			flat = flat.normalize().scale(TAKEOVER_SPEED);
			Vec3 v = player.getDeltaMovement();
			double up = v.y;
			if (player.onGround() && player.horizontalCollision) {
				up = 0.42;
			}
			player.setDeltaMovement(flat.x, up, flat.z);
		}
	}

	/** Wall Crawl: stick to walls and ceilings and climb them. */
	private static void tickCrawl(LocalPlayer player, WebSwing swing) {
		if (!WebSymbiote.isToggled(player, WebSymbioteState.CRAWL) || player.getAbilities().flying || player.isPassenger() || player.isInWater()
			|| player.isInLava() || swing.attached() || swing.pull != null || swing.wingsTicks > 0 || player.isFallFlying()) {
			wallClinging = false;
			ceilingClinging = false;
			return;
		}
		AABB box = player.getBoundingBox();
		boolean wall = !player.level().noCollision(player, box.inflate(0.12, -0.1, 0.12));
		boolean ceiling = !player.onGround() && !player.level().noCollision(player, box.move(0.0, 0.15, 0.0).deflate(0.05, 0.0, 0.05));
		Input input = player.input.keyPresses;
		Vec3 move = inputDirection(player, input);
		if (ceiling && !input.shift() && (input.jump() || ceilingClinging)) {
			// on the ceiling: pressed up into it, crawling about upside down
			ceilingClinging = true;
			wallClinging = false;
			player.setDeltaMovement(move.x * CRAWL_SPEED, CEILING_PRESS, move.z * CRAWL_SPEED);
			player.resetFallDistance();
			return;
		}
		ceilingClinging = false;
		if (wall && (!player.onGround() || input.forward() && player.horizontalCollision)) {
			double vy = input.jump() || input.forward() ? CRAWL_CLIMB : input.shift() ? -CRAWL_CLIMB : 0.0;
			player.setDeltaMovement(move.x * CRAWL_SPEED, vy, move.z * CRAWL_SPEED);
			player.resetFallDistance();
			wallClinging = true;
			return;
		}
		if (wallClinging && !player.onGround() && player.getDeltaMovement().y > 0.0) {
			// over the top: hop onto the ledge
			Vec3 forward = player.getLookAngle().horizontal();
			forward = forward.lengthSqr() < 1.0E-4 ? Vec3.ZERO : forward.normalize();
			player.setDeltaMovement(forward.x * LEDGE_FORWARD, LEDGE_HOP, forward.z * LEDGE_FORWARD);
		}
		wallClinging = false;
	}

	/** The movement keys as a world direction (unit or zero), from where the player faces. */
	private static Vec3 inputDirection(LocalPlayer player, Input input) {
		double forward = (input.forward() ? 1 : 0) - (input.backward() ? 1 : 0);
		double strafe = (input.left() ? 1 : 0) - (input.right() ? 1 : 0);
		if (forward == 0 && strafe == 0) {
			return Vec3.ZERO;
		}
		float yaw = player.getYRot() * Mth.DEG_TO_RAD;
		double sin = Mth.sin(yaw);
		double cos = Mth.cos(yaw);
		Vec3 dir = new Vec3(strafe * cos - forward * sin, 0.0, forward * cos + strafe * sin);
		return dir.normalize();
	}

	// ---- HUD ---------------------------------------------------------------------------------------------------------

	/** A takeover darkens the edges of the screen, pulsing like a heartbeat. */
	static void renderHud(GuiGraphicsExtractor graphics, LocalPlayer player) {
		if (takeoverTicks <= 0) {
			return;
		}
		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		float pulse = 0.55F + 0.45F * Mth.sin(player.tickCount * 0.6F);
		for (int band = 0; band < 6; band++) {
			int alpha = (int)((150 - band * 24) * pulse);
			int color = alpha << 24 | 0x0E0614;
			int inset = band * 6;
			graphics.fill(inset, inset, width - inset, inset + 6, color);
			graphics.fill(inset, height - inset - 6, width - inset, height - inset, color);
			graphics.fill(inset, inset + 6, inset + 6, height - inset - 6, color);
			graphics.fill(width - inset - 6, inset + 6, width - inset, height - inset - 6, color);
		}
	}

	// ---- spider-sense ------------------------------------------------------------------------------------------------

	/** Whether Spider-Sense is on for us this tick. */
	private static boolean sensing;

	/** The outline colour for {@code entity} on our screen, or 0: red if it's about to hurt an ascended web slinger. */
	public static int senseColor(Entity entity) {
		if (!sensing) {
			return 0;
		}
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || entity == player) {
			return 0;
		}
		if (entity.distanceToSqr(player) > com.jaymospowers.power.web.WebAscension.SENSE_RANGE * com.jaymospowers.power.web.WebAscension.SENSE_RANGE) {
			return 0;
		}
		boolean fresh = player.level().getGameTime() - threatsAt <= SENSE_STALE_TICKS;
		if (fresh && THREATS.contains(entity.getId())) {
			return SENSE_COLOR;
		}
		if (entity instanceof Creeper creeper && creeper.getSwelling(0.0F) > 0.0F) {
			return SENSE_COLOR;
		}
		if (entity instanceof AbstractSkeleton skeleton && skeleton.isUsingItem()) {
			return SENSE_COLOR;
		}
		return 0;
	}
}
