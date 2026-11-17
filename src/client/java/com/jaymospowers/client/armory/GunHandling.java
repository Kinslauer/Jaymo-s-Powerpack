package com.jaymospowers.client.armory;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.armory.TaczGuns;
import com.jaymospowers.core.Crawl;
import com.jaymospowers.core.Kneel;
import com.jaymospowers.power.chrono.future.FutureGuns;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * How a gun from the future moves in your hands, first person (Andy's picks from TaCZ Additions; nothing of it copied).
 * TaCZ draws the gun; this moves the pose it's drawn from, through TaCZ's own "before the hand is drawn" event.
 * <ul>
 * <li><b>Momentum and strafe tilt</b> (ad_sway): turning, the gun lags behind the view and catches up; stepping
 * sideways, it leans over. Kingdoms' lean tilts the whole view, and this sits inside it.</li>
 * <li><b>Breathing</b> (ad_idle): standing, the gun rises and falls with your breath, harder when you're winded (just ran)
 * or hurt (under half health).</li>
 * <li><b>Tuck</b> (ad_tuck): up against a wall the gun comes back to your chest, muzzle up, and won't fire until there's
 * room ({@link #tucked}). The musket and blunderbuss tuck too ({@link MusketSights}).</li>
 * <li><b>Scope sway</b> (ad_scopesway): looking down a 4x or stronger scope, the view drifts in a slow figure eight
 * ({@link #applyCamera}), and your aim with it. Lying prone, kneeling, the low crouch or resting the gun on a wall top steadies it.</li>
 * <li><b>Scroll zoom</b> (ad_zoom): aiming down sights, the wheel steps the scope's zoom (TaCZ's zoom key, {@link #scroll}).</li>
 * <li><b>Recoil by kind</b> (ad_recoil): each kind kicks the view by its own share of TaCZ's recoil, and the view stays
 * where the kick left it instead of settling back ({@code TaczRecoilMixin}); the gun itself jumps back in your hands.</li>
 * </ul>
 * Aiming down sights calms the sway and breathing to {@link #AIMED_SHARE}. TaCZ is reached by reflection.
 */
public final class GunHandling {
	/** Turning: degrees of lag per degree turned this tick, the most it lags, and how fast it catches up (share per tick). */
	static final float SWAY_PER_DEGREE = 0.35F, SWAY_MAX = 6.0F, SWAY_RETURN = 0.25F;
	/** Stepping sideways: degrees of roll per block a tick of sideways speed, and the most. */
	static final float TILT_PER_SPEED = 55.0F, TILT_MAX = 7.0F, TILT_RETURN = 0.2F;
	/** Breathing: one breath every this many ticks, the gun rising this far (blocks); winded or hurt, this many times as much and as fast. */
	static final float BREATH_TICKS = 80.0F, BREATH_RISE = 0.006F, WINDED = 2.5F;
	/** Winded for this many ticks after sprinting stops. */
	static final int WINDED_TICKS = 60;
	/** Aiming down sights, sway and breathing are this share of what they'd be. */
	static final float AIMED_SHARE = 0.3F;
	/** Tuck: a wall within this far of the eye along the look (blocks) by kind (pistol to sniper); pulled back and turned up this much. */
	static final double[] TUCK_REACH = {0.55, 0.75, 0.95, 1.05, 0.9, 1.15};
	static final double TUCK_REACH_UNKNOWN = 0.9;
	static final float TUCK_BACK = 0.16F, TUCK_DOWN = 0.06F, TUCK_PITCH = 50.0F, TUCK_SPEED = 0.25F;
	/** The gun's grip in the view (blocks, right hand), where the tuck turns it about. */
	static final float GRIP_X = 0.2F, GRIP_Y = -0.2F, GRIP_Z = -0.35F;
	/** Scope sway: at 4x and up, degrees of drift side to side; one figure eight every this many ticks. */
	static final float SCOPE_ZOOM = 4.0F, SCOPE_SWAY = 0.22F, SCOPE_TICKS = 140.0F;
	/** Scope sway steadied (share left): prone, kneeling (still in the low crouch), the low crouch, a sneak, the gun on a wall top. */
	static final float STEADY_PRONE = 0.25F, STEADY_KNEEL = 0.35F, STEADY_LOW = 0.6F, STEADY_SNEAK = 0.8F, STEADY_RESTED = 0.4F;
	/** Recoil by kind (pistol, SMG, rifle, machine gun, shotgun, sniper): share of TaCZ's view kick up, and sideways. */
	static final float[] RECOIL_UP = {1.1F, 0.8F, 1.0F, 0.9F, 1.6F, 1.4F};
	static final float[] RECOIL_SIDE = {1.0F, 1.2F, 1.0F, 1.3F, 0.8F, 0.6F};
	/** The gun's jump in your hands per shot by kind: back (blocks) and up (degrees); gone in this many ticks. */
	static final float[] KICK_BACK = {0.05F, 0.03F, 0.045F, 0.04F, 0.09F, 0.08F};
	static final float[] KICK_UP = {6.0F, 2.5F, 3.5F, 3.0F, 9.0F, 7.0F};
	static final float KICK_TICKS = 4.0F;
	/** (ad_zoom) The wheel steps the zoom only on a scope this strong or more (iron sights keep the hotbar), at most once per this many ms. */
	static final float ZOOM_SCROLL_MIN = 1.5F;
	static final long ZOOM_SCROLL_GAP = 150L;

	private static float swayYaw, swayPitch, swayYawBefore, swayPitchBefore, tilt, tiltBefore;
	private static float breath, breathBefore, tuck, tuckBefore, kick, kickBefore;
	private static int winded, kickKind = FutureGuns.KIND_RIFLE;
	private static float lastYRot, lastXRot;
	private static long lastZoom;
	/** (ad_scopesway) The drift laid on your aim last tick (degrees), taken off again before the new one goes on. */
	private static float driftYaw, driftPitch;
	/** The held view kick of the shot in progress (TaCZ's spline values, cut to the peak): see {@link #recoilPitch}. */
	private static double peakPitch, peakYaw;

	private static boolean looked;
	private static @Nullable Method fromLocal, isAim, aimingProgress, getIGun, aimingZoom;
	private static @Nullable CustomPacketPayload zoomMessage;

	private GunHandling() {
	}

	public static void init() {
		if (!TaczGuns.LOADED) {
			return;
		}
		ClientTickEvents.END_CLIENT_TICK.register(GunHandling::tick);
		listen("com.tacz.guns.api.client.event.BeforeRenderHandEvent", event -> {
			try {
				if (event.getClass().getMethod("getPoseStack").invoke(event) instanceof PoseStack poseStack) {
					apply(poseStack);
				}
			} catch (ReflectiveOperationException e) {
				// leave the gun where TaCZ put it
			}
		});
		listen("com.tacz.guns.api.event.common.GunShootEvent", event -> {
			// up against a wall, your own gun doesn't fire
			if (tuck > 0.5F && local(event)) {
				try {
					event.getClass().getMethod("setCanceled", boolean.class).invoke(event, true);
				} catch (ReflectiveOperationException e) {
					// fire after all
				}
			}
		});
		listen("com.tacz.guns.api.event.common.GunFireEvent", event -> {
			if (local(event)) {
				shot();
			}
		});
	}

	/** Hears TaCZ's event {@code eventClass} (its {@code CALLBACK} and {@code Callback.post}), as {@code MuzzleFlash} does. */
	private static void listen(String eventClass, Consumer<Object> heard) {
		try {
			Class<?> event = Class.forName(eventClass);
			Class<?> callback = Class.forName(eventClass + "$Callback");
			Object listener = Proxy.newProxyInstance(GunHandling.class.getClassLoader(), new Class<?>[] {callback}, (proxy, method, args) -> {
				if (method.getDeclaringClass() == Object.class) {
					return switch (method.getName()) {
						case "hashCode" -> System.identityHashCode(proxy);
						case "equals" -> proxy == args[0];
						default -> "GunHandling";
					};
				}
				try {
					heard.accept(args[0]);
				} catch (RuntimeException e) {
					// never get in TaCZ's way
				}
				return null;
			});
			@SuppressWarnings("unchecked")
			Event<Object> hook = (Event<Object>) event.getField("CALLBACK").get(null);
			hook.register(listener);
		} catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
			JaymosPowers.LOGGER.warn("Couldn't hear TaCZ's {}: {}", eventClass, e.toString());
		}
	}

	/** Whether TaCZ's event is the local player's own, on this side. */
	private static boolean local(Object event) {
		try {
			Object side = event.getClass().getMethod("getLogicalSide").invoke(event);
			Object shooter = event.getClass().getMethod("getShooter").invoke(event);
			return "CLIENT".equals(String.valueOf(side)) && shooter == Minecraft.getInstance().player;
		} catch (ReflectiveOperationException e) {
			return false;
		}
	}

	/** Your shot: the gun jumps, and a new view kick starts. */
	private static void shot() {
		LocalPlayer player = Minecraft.getInstance().player;
		int kind = player == null ? -1 : FutureGuns.kindOf(player.getMainHandItem());
		kickKind = kind < 0 ? FutureGuns.KIND_RIFLE : kind;
		kick = 1.0F;
		peakPitch = 0.0;
		peakYaw = 0.0;
	}

	private static net.minecraft.world.item.@Nullable Item gunItem;

	private static boolean holdingGun(LocalPlayer player) {
		if (gunItem == null) {
			gunItem = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse("tacz:modern_kinetic_gun"));
		}
		return player.getMainHandItem().is(gunItem);
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		swayYawBefore = swayYaw;
		swayPitchBefore = swayPitch;
		tiltBefore = tilt;
		breathBefore = breath;
		tuckBefore = tuck;
		kickBefore = kick;
		if (player == null || minecraft.isPaused()) {
			return;
		}
		float turnedYaw = Mth.wrapDegrees(player.getYRot() - lastYRot), turnedPitch = player.getXRot() - lastXRot;
		lastYRot = player.getYRot();
		lastXRot = player.getXRot();
		kick = Math.max(0.0F, kick - 1.0F / KICK_TICKS);
		if (!holdingGun(player)) {
			swayYaw = swayPitch = tilt = tuck = 0.0F;
			return;
		}
		swayYaw = Mth.clamp(swayYaw * (1.0F - SWAY_RETURN) + turnedYaw * SWAY_PER_DEGREE, -SWAY_MAX, SWAY_MAX);
		swayPitch = Mth.clamp(swayPitch * (1.0F - SWAY_RETURN) + turnedPitch * SWAY_PER_DEGREE, -SWAY_MAX, SWAY_MAX);
		Vec3 look = player.getLookAngle();
		Vec3 right = new Vec3(-look.z, 0.0, look.x);
		double sideways = right.lengthSqr() < 1.0E-6 ? 0.0 : player.getDeltaMovement().dot(right.normalize());
		float tiltTo = Mth.clamp((float) sideways * TILT_PER_SPEED, -TILT_MAX, TILT_MAX);
		tilt += (tiltTo - tilt) * TILT_RETURN;
		winded = player.isSprinting() ? WINDED_TICKS : Math.max(0, winded - 1);
		boolean hurt = player.getHealth() < player.getMaxHealth() * 0.5F;
		breath += (winded > 0 || hurt) ? WINDED : 1.0F;
		tuck = Mth.clamp(tuck + (wallAhead(minecraft, player, reach(player.getMainHandItem())) ? TUCK_SPEED : -TUCK_SPEED), 0.0F, 1.0F);
	}

	private static double reach(ItemStack gun) {
		int kind = FutureGuns.kindOf(gun);
		return kind < 0 ? TUCK_REACH_UNKNOWN : TUCK_REACH[kind];
	}

	/**
	 * Whether a wall stands within {@code reach} of the eye along the look (in first person, from the camera, where
	 * Kingdoms' lean has put it). Floors and ceilings don't count: aiming down while prone isn't a wall.
	 */
	static boolean wallAhead(Minecraft minecraft, LocalPlayer player, double reach) {
		if (minecraft.level == null) {
			return false;
		}
		Vec3 eye = minecraft.options.getCameraType().isFirstPerson() ? minecraft.gameRenderer.mainCamera().position() : player.getEyePosition();
		Vec3 to = eye.add(player.getLookAngle().scale(reach));
		net.minecraft.world.phys.BlockHitResult hit = minecraft.level.clip(new ClipContext(eye, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		return hit.getType() == HitResult.Type.BLOCK && hit.getDirection().getAxis() != net.minecraft.core.Direction.Axis.Y;
	}

	/** Moves TaCZ's first-person gun: sway, tilt, breath, tuck and kick, eased between ticks. */
	private static void apply(PoseStack poseStack) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || !holdingGun(player)) {
			return;
		}
		float pt = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		float calm = Mth.lerp(aimed(player, pt), 1.0F, AIMED_SHARE);
		float yaw = Mth.lerp(pt, swayYawBefore, swayYaw) * calm, pitch = Mth.lerp(pt, swayPitchBefore, swayPitch) * calm;
		float roll = Mth.lerp(pt, tiltBefore, tilt) * calm;
		float rise = Mth.sin(Mth.lerp(pt, breathBefore, breath) / BREATH_TICKS * Mth.TWO_PI) * BREATH_RISE * calm
			* (winded > 0 ? WINDED : 1.0F);
		float t = ease(Mth.lerp(pt, tuckBefore, tuck));
		float k = Mth.lerp(pt, kickBefore, kick);
		k = k * k;
		poseStack.translate(0.0F, rise, 0.0F);
		poseStack.rotateDegrees(Axis.YP, yaw);
		poseStack.rotateDegrees(Axis.XP, pitch);
		poseStack.rotateDegrees(Axis.ZP, -roll);
		if (k > 0.0F) {
			poseStack.translate(0.0F, 0.0F, KICK_BACK[kickKind] * k);
			pivot(poseStack, KICK_UP[kickKind] * k);
		}
		if (t > 0.0F) {
			poseStack.translate(0.0F, -TUCK_DOWN * t, TUCK_BACK * t);
			pivot(poseStack, TUCK_PITCH * t);
		}
	}

	/** Turns the gun muzzle up by {@code degrees} about its grip. */
	private static void pivot(PoseStack poseStack, float degrees) {
		poseStack.translate(GRIP_X, GRIP_Y, GRIP_Z);
		poseStack.rotateDegrees(Axis.XP, degrees);
		poseStack.translate(-GRIP_X, -GRIP_Y, -GRIP_Z);
	}

	private static float ease(float t) {
		return t * t * (3.0F - 2.0F * t);
	}

	/** Whether your gun is tucked against a wall just now (it won't fire). */
	public static boolean tucked() {
		return tuck > 0.5F;
	}

	/**
	 * (ad_scopesway) Your aim, looking down a strong scope: it drifts in a slow figure eight, steadied by how you hold it.
	 * The drift turns you (your aim really moves, the crosshair with it), a little each frame; the scope's zoom magnifies
	 * it. Called once a frame ({@code ScreenShakeMixin}); the pose isn't touched.
	 */
	public static void applyCamera(PoseStack poseStack, float pt) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		float yaw = 0.0F, pitch = 0.0F;
		if (player != null && !minecraft.isPaused() && holdingGun(player) && minecraft.options.getCameraType().isFirstPerson()) {
			float aimed = aimed(player, pt);
			if (aimed > 0.0F && zoom(player.getMainHandItem()) >= SCOPE_ZOOM) {
				float t = (player.tickCount + pt) / SCOPE_TICKS * Mth.TWO_PI;
				float a = SCOPE_SWAY * steadiness(minecraft, player) * aimed * (winded > 0 ? WINDED : 1.0F);
				yaw = Mth.sin(t) * a;
				pitch = Mth.sin(t * 2.0F) * a * 0.5F;
			}
		}
		if (player != null && (yaw != driftYaw || pitch != driftPitch)) {
			float dYaw = yaw - driftYaw, dPitch = pitch - driftPitch;
			player.setYRot(player.getYRot() + dYaw);
			player.setXRot(Mth.clamp(player.getXRot() + dPitch, -90.0F, 90.0F));
			// (the sway reads turning off these: the drift isn't you turning)
			lastYRot += dYaw;
			lastXRot += dPitch;
		}
		driftYaw = yaw;
		driftPitch = pitch;
	}

	private static float steadiness(Minecraft minecraft, LocalPlayer player) {
		float steady = 1.0F;
		if (Crawl.crawling(player) || player.isVisuallyCrawling()) {
			steady = STEADY_PRONE;
		} else if (Kneel.crouched(player)) {
			steady = player.getDeltaMovement().horizontalDistanceSqr() < 1.0E-4 ? STEADY_KNEEL : STEADY_LOW;
		} else if (player.isCrouching()) {
			steady = STEADY_SNEAK;
		}
		return Math.min(steady, rested(minecraft, player) ? STEADY_RESTED : 1.0F);
	}

	/** Whether the gun rests on a wall top: something solid just under the barrel ahead, and air in front of the eye. */
	private static boolean rested(Minecraft minecraft, LocalPlayer player) {
		if (minecraft.level == null) {
			return false;
		}
		Vec3 eye = player.getEyePosition();
		Vec3 ahead = new Vec3(player.getLookAngle().x, 0.0, player.getLookAngle().z);
		if (ahead.lengthSqr() < 1.0E-4) {
			return false;
		}
		Vec3 front = eye.add(ahead.normalize().scale(0.8));
		BlockPos under = BlockPos.containing(front.x, eye.y - 0.45, front.z);
		return !minecraft.level.getBlockState(under).getCollisionShape(minecraft.level, under).isEmpty()
			&& minecraft.level.getBlockState(BlockPos.containing(front)).getCollisionShape(minecraft.level, BlockPos.containing(front)).isEmpty();
	}

	/** (ad_zoom) The wheel while aiming down sights steps the scope's zoom, not the hotbar. */
	public static boolean scroll(double amount) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (amount == 0.0 || player == null || minecraft.gui.screen() != null || !holdingGun(player) || !aiming(player) || !look()
			|| zoom(player.getMainHandItem()) < ZOOM_SCROLL_MIN) {
			return false;
		}
		// one step per notch: a touchpad's run of small scrolls steps once (TaCZ's zoom only steps on to the next setting)
		long now = net.minecraft.util.Util.getMillis();
		if (zoomMessage != null && now - lastZoom >= ZOOM_SCROLL_GAP) {
			lastZoom = now;
			ClientPlayNetworking.send(zoomMessage);
		}
		return true;
	}

	/**
	 * (ad_recoil) TaCZ's view kick up for this moment of the shot ({@code value}, along its recoil curve): this kind's share
	 * of it, held at its highest so the view doesn't settle back.
	 */
	public static double recoilPitch(double value) {
		peakPitch = held(peakPitch, value * RECOIL_UP[kickKind]);
		return peakPitch;
	}

	/** (ad_recoil) The same sideways. */
	public static double recoilYaw(double value) {
		peakYaw = held(peakYaw, value * RECOIL_SIDE[kickKind]);
		return peakYaw;
	}

	private static double held(double peak, double value) {
		return Math.abs(value) > Math.abs(peak) ? value : peak;
	}

	private static boolean aiming(LocalPlayer player) {
		if (!look()) {
			return false;
		}
		try {
			return Boolean.TRUE.equals(isAim.invoke(fromLocal.invoke(null, player)));
		} catch (ReflectiveOperationException | RuntimeException e) {
			return false;
		}
	}

	/** How far up to the sights the gun is (0-1). */
	private static float aimed(LocalPlayer player, float pt) {
		if (!look()) {
			return 0.0F;
		}
		try {
			return ((Number) aimingProgress.invoke(fromLocal.invoke(null, player), pt)).floatValue();
		} catch (ReflectiveOperationException | RuntimeException e) {
			return 0.0F;
		}
	}

	/** The gun's zoom aimed (its scope's, or its iron sights'), 1 if it can't be read. */
	private static float zoom(ItemStack gun) {
		if (!look()) {
			return 1.0F;
		}
		try {
			Object iGun = getIGun.invoke(null, gun);
			return iGun == null ? 1.0F : ((Number) aimingZoom.invoke(iGun, gun)).floatValue();
		} catch (ReflectiveOperationException | RuntimeException e) {
			return 1.0F;
		}
	}

	private static boolean look() {
		if (!looked) {
			looked = true;
			try {
				Class<?> operator = Class.forName("com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator");
				fromLocal = operator.getMethod("fromLocalPlayer", LocalPlayer.class);
				isAim = operator.getMethod("isAim");
				aimingProgress = operator.getMethod("getClientAimingProgress", float.class);
				Class<?> iGun = Class.forName("com.tacz.guns.api.item.IGun");
				getIGun = iGun.getMethod("getIGunOrNull", ItemStack.class);
				aimingZoom = iGun.getMethod("getAimingZoom", ItemStack.class);
				zoomMessage = (CustomPacketPayload) Class.forName("com.tacz.guns.network.message.ClientMessagePlayerZoom").getField("INSTANCE").get(null);
			} catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
				JaymosPowers.LOGGER.warn("Couldn't reach TaCZ's aiming: {}", e.toString());
				fromLocal = null;
			}
		}
		return fromLocal != null;
	}
}
