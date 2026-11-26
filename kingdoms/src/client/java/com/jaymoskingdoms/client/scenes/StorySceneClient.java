package com.jaymoskingdoms.client.scenes;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.StoryScenes;
import com.jaymoskingdoms.StoryScenes.Shot;
import com.jaymoskingdoms.client.hud.HudZones;
import com.jaymoskingdoms.client.music.SceneMusic;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A story scene ({@link StoryScenes}) on this game: the letterbox, the HUD and chat cleared, the camera on its shots, and
 * the talk itself on {@link StorySceneScreen}. Each beat's {@code shot} picks where the camera goes, framed on the scene's
 * anchor (the shrine, the one met) and you:
 * <ul>
 * <li>{@code wide}: from well out to one side, taking in the place, drifting slowly round.</li>
 * <li>{@code shoulder}: close over your shoulder on the one speaking (the anchor).</li>
 * <li>{@code face}: in front of you, on your face, for a hard question.</li>
 * <li>{@code orbit}: circling the two of you, for a rite.</li>
 * <li>{@code low}: from down by the anchor, up at you (kneeling before it).</li>
 * <li>{@code high}: from above, looking down on both.</li>
 * </ul>
 * Your own replies are shot side-on, from outside the two of you, the other side each time (as {@code DialogueCinematic}
 * does). The camera glides from shot to shot ({@link #EASE}) and walls pull it in. The view keeps your plain FOV and
 * Andy's 180 degree rule: every shot stays on your side of the line through you and the anchor except the side-on ones,
 * which alternate.
 */
public final class StorySceneClient {
	/** Bar height as a share of the screen, and how much of the way they slide a tick. */
	static final float BAR_SHARE = 0.11F, BAR_EASE = 0.22F;
	/** How much of the way to its next framing the camera goes a tick. */
	static final double EASE = 0.08;
	/** Shot distances (blocks): wide out and up, shoulder back/side/up, face out, side-on out, orbit radius/up, high up. */
	static final double WIDE_OUT = 6.0, WIDE_UP = 2.5, SHOULDER_BACK = 0.9, SHOULDER_SIDE = 0.55, SHOULDER_UP = 0.15, FACE_OUT = 1.3,
		PROFILE_OUT = 2.4, ORBIT_RADIUS = 3.2, ORBIT_UP = 1.2, HIGH_UP = 5.0;
	/** How fast the wide shot drifts and the orbit turns (degrees a tick). */
	static final double WIDE_DRIFT = 0.25, ORBIT_TURN = 1.1;
	/** Kept this far off a wall (blocks). */
	static final double WALL_MARGIN = 0.3;
	private static final Identifier[] HIDDEN = {VanillaHudElements.CROSSHAIR, VanillaHudElements.HOTBAR, VanillaHudElements.ARMOR_BAR,
		VanillaHudElements.HEALTH_BAR, VanillaHudElements.FOOD_BAR, VanillaHudElements.AIR_BAR, VanillaHudElements.MOUNT_HEALTH,
		VanillaHudElements.INFO_BAR, VanillaHudElements.EXPERIENCE_LEVEL, VanillaHudElements.HELD_ITEM_TOOLTIP, VanillaHudElements.MOB_EFFECTS,
		VanillaHudElements.BOSS_BAR, VanillaHudElements.CHAT};

	/** Scenes with their own music. */
	private static final java.util.Map<String, Identifier> OWN_MUSIC = java.util.Map.of("midnight_bargain", JaymosKingdoms.id("scene_music.midnight_bargain"));

	private static boolean running;
	private static String musicFor = "";
	private static @Nullable Shot shot;
	private static String shotName = "wide";
	private static Vec3 anchor = Vec3.ZERO;
	private static float bars, barsO;
	private static int age, shotAge;
	private static boolean cut, profileLeft;
	private static Vec3 pos = Vec3.ZERO, posO = Vec3.ZERO, look = Vec3.ZERO, lookO = Vec3.ZERO;

	private StorySceneClient() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(Shot.TYPE, (payload, context) -> receive(context.client(), payload));
		ClientTickEvents.END_CLIENT_TICK.register(StorySceneClient::tick);
		for (Identifier id : HIDDEN) {
			HudElementRegistry.replaceElement(id, old -> (graphics, delta) -> {
				if (!running) {
					old.extractRenderState(graphics, delta);
				}
			});
		}
		HudElementRegistry.addLast(JaymosKingdoms.id("story_scene"), (HudElement)StorySceneClient::render);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			end(false);
			bars = barsO = 0.0F;
			VoiceEars.stopAll();
		});
	}

	/** Whether a scene is playing on this game. */
	public static boolean running() {
		return running;
	}

	private static void receive(Minecraft minecraft, Shot next) {
		if (next.kind() == Shot.END) {
			end(false);
			return;
		}
		LocalPlayer self = minecraft.player;
		if (self == null) {
			return;
		}
		if (!running) {
			running = true;
			cut = true;
			age = 0;
			profileLeft = self.getRandom().nextBoolean();
		}
		anchor = next.anchor();
		musicFor = next.scene();
		StorySceneScreen screen = minecraft.gui.screen() instanceof StorySceneScreen open ? open : null;
		if (screen == null) {
			screen = new StorySceneScreen();
			minecraft.gui.setScreen(screen);
		}
		screen.beat(next);
	}

	/** The screen is showing {@code beat} now (after any reply of yours has had its moment): its shot is the camera's. */
	static void showing(Shot beat) {
		shot = beat;
		frame(beat.shot());
	}

	/** The camera on {@code name} (a scene shot, or "you" for your own reply side-on). */
	static void frame(String name) {
		if (name.equals("you")) {
			profileLeft = !profileLeft;
		}
		shotName = name;
		shotAge = 0;
	}

	/** The scene is over here ({@code leave}: you left it, the server is told). */
	static void end(boolean leave) {
		if (leave && running && shot != null && ClientPlayNetworking.canSend(StoryScenes.Answer.TYPE)) {
			ClientPlayNetworking.send(new StoryScenes.Answer(shot.scene(), shot.node(), StoryScenes.LEAVE));
		}
		running = false;
		shot = null;
		VoiceEars.stop(StorySceneScreen.EARS);
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.gui.screen() instanceof StorySceneScreen screen) {
			screen.closing = true;
			minecraft.gui.setScreen(null);
		}
	}

	static void answer(int choice) {
		if (running && shot != null && ClientPlayNetworking.canSend(StoryScenes.Answer.TYPE)) {
			ClientPlayNetworking.send(new StoryScenes.Answer(shot.scene(), shot.node(), choice));
		}
	}

	private static void tick(Minecraft minecraft) {
		barsO = bars;
		LocalPlayer self = minecraft.player;
		if (running) {
			if (self == null || !self.isAlive() || !(minecraft.gui.screen() instanceof StorySceneScreen)) {
				end(true);
			} else if (!minecraft.isPaused()) {
				age++;
				shotAge++;
				posO = pos;
				lookO = look;
				Vec3[] want = framing(self);
				if (cut) {
					pos = posO = want[0];
					look = lookO = want[1];
					cut = false;
				} else {
					pos = pos.lerp(want[0], EASE);
					look = look.lerp(want[1], EASE);
				}
			}
		}
		// music under it: the scene's own track where it has one (sounds.json scene_music.<scene>), else a disc
		SceneMusic.scene("story_scene", running, OWN_MUSIC.get(musicFor));
		bars += ((running ? 1.0F : 0.0F) - bars) * BAR_EASE;
		if (!running && bars < 0.01F) {
			bars = 0.0F;
		}
	}

	/** Where the camera wants to be and what it looks at, for the shot on now. */
	private static Vec3[] framing(LocalPlayer self) {
		Vec3 eyes = self.getEyePosition();
		Vec3 toward = anchor.subtract(eyes).horizontal();
		toward = toward.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0.0F, self.getYRot()) : toward.normalize();
		Vec3 side = new Vec3(-toward.z, 0.0, toward.x);
		Vec3 mid = eyes.lerp(anchor, 0.5);
		Vec3 up = new Vec3(0.0, 1.0, 0.0);
		Vec3 at, on;
		switch (shotName) {
			case "wide" -> {
				Vec3 round = turn(side, Math.toRadians(shotAge * WIDE_DRIFT));
				at = mid.add(round.scale(WIDE_OUT)).subtract(toward.scale(WIDE_OUT * 0.3)).add(up.scale(WIDE_UP));
				on = mid;
			}
			case "face" -> {
				at = eyes.add(toward.scale(FACE_OUT)).add(side.scale(0.25)).add(up.scale(0.05));
				on = eyes;
			}
			case "you" -> {
				at = mid.add(side.scale(profileLeft ? PROFILE_OUT : -PROFILE_OUT)).add(up.scale(0.2));
				on = mid;
			}
			case "orbit" -> {
				Vec3 round = turn(side, Math.toRadians(age * ORBIT_TURN));
				at = mid.add(round.scale(ORBIT_RADIUS)).add(up.scale(ORBIT_UP));
				on = mid;
			}
			case "low" -> {
				at = anchor.lerp(eyes, 0.3).add(side.scale(0.5)).subtract(up.scale(0.7));
				on = eyes;
			}
			case "high" -> {
				at = mid.add(up.scale(HIGH_UP)).add(side.scale(1.5)).subtract(toward.scale(1.0));
				on = mid;
			}
			default -> {
				at = eyes.subtract(toward.scale(SHOULDER_BACK)).add(side.scale(SHOULDER_SIDE)).add(up.scale(SHOULDER_UP));
				on = anchor;
			}
		}
		return new Vec3[] {clipped(self, on, at), on};
	}

	/** {@code v} turned round the up axis by {@code angle} (radians). */
	private static Vec3 turn(Vec3 v, double angle) {
		double c = Math.cos(angle), s = Math.sin(angle);
		return new Vec3(v.x * c - v.z * s, v.y, v.x * s + v.z * c);
	}

	/** {@code want}, or short of it where a wall stands between it and {@code from}. */
	private static Vec3 clipped(LocalPlayer self, Vec3 from, Vec3 want) {
		HitResult hit = self.level().clip(new ClipContext(from, want, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, self));
		if (hit.getType() == HitResult.Type.MISS) {
			return want;
		}
		Vec3 back = hit.getLocation().subtract(from);
		double far = back.length();
		return far > WALL_MARGIN ? from.add(back.scale((far - WALL_MARGIN) / far)) : from;
	}

	/** Where the camera is and which way it looks this frame ({x, y, z, yaw, pitch}), or null with no scene on. */
	public static double @Nullable [] view(Minecraft minecraft, float partialTicks) {
		LocalPlayer self = minecraft.player;
		if (!running || self == null || minecraft.getCameraEntity() != self) {
			return null;
		}
		Vec3 at = posO.lerp(pos, partialTicks);
		Vec3 to = lookO.lerp(look, partialTicks);
		Vec3 dir = to.subtract(at);
		dir = dir.lengthSqr() < 1.0E-6 ? Vec3.directionFromRotation(0.0F, self.getYRot()) : dir.normalize();
		double yaw = Math.toDegrees(Math.atan2(-dir.x, dir.z));
		double pitch = Math.toDegrees(-Math.asin(Mth.clamp(dir.y, -1.0, 1.0)));
		return new double[] {at.x, at.y, at.z, yaw, pitch};
	}

	/** The bars' height now (GUI pixels), for the screen to lay out above and below. */
	static int bar(int height, float partialTicks) {
		return Math.round(height * BAR_SHARE * Mth.lerp(partialTicks, barsO, bars));
	}

	private static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		float shown = Mth.lerp(delta.getGameTimeDeltaPartialTick(false), barsO, bars);
		if (shown <= 0.0F) {
			return;
		}
		if (running) {
			HudZones.hideFor(100);
		}
		int width = graphics.guiWidth(), height = graphics.guiHeight();
		int bar = Math.round(height * BAR_SHARE * shown);
		graphics.fill(0, 0, width, bar, 0xFF000000);
		graphics.fill(0, height - bar, width, height, 0xFF000000);
	}
}
