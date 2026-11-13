package com.jaymoskingdoms.client.films;

import com.jaymoskingdoms.FarmhandWake;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.client.hud.HudZones;
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
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Family farmhand's wake-up as a film ({@link FarmhandWake}). Andy: "the letterboxing from waking up should stay for a
 * speaking scene with him as you wake and he tells you all this. panning to the chest at the end." The bars come in as the
 * eyes open and stay for the whole talk; the HUD clears. The camera is from the pillow: it starts on the ceiling, comes
 * down to the farmer by the bed, and follows whoever is talking (framed between their face and the words over their head);
 * at the end it pans to the gear chest and holds there, then the bars go and the player is up beside the bed facing it.
 * You aren't drawn (the camera is at your head). The view keeps your plain FOV ({@code FilmPlainFovMixin}), the in-bed
 * screen is hidden and deaf ({@code InBedChatScreenMixin}), and Backspace skips it.
 */
public final class FarmWakeFilmClient {
	/** Each bar's height as a share of the screen's; how much of the way they slide a tick. */
	static final float BAR_SHARE = 0.12F, BAR_EASE = 0.2F;
	/** How much of the way the camera goes to its next framing a tick: on a speaker, and on the pan to the chest. */
	static final double EASE_SPEAKER = 0.07, EASE_CHEST = 0.06;
	/** The pillow shot: how far up from your eyes and toward the speaker the camera sits, and how far to one side (blocks). */
	static final double PILLOW_UP = 0.55, PILLOW_TOWARD = 0.5, PILLOW_SIDE = 0.35;
	/** On the chest: a little higher (sitting up), and a touch toward it (blocks). */
	static final double CHEST_UP = 0.85, CHEST_TOWARD = 0.3;
	/** Where between a speaker's face and the words over their head the frame sits; how high the words are over the head (blocks). */
	static final double WORDS_SHARE = 0.35, WORDS_UP = 1.0;
	/** Given up on if the server goes quiet this long (ticks), and how long past a chest pan's hold it may run. */
	static final int QUIET_TICKS = 20 * 30, CHEST_GRACE = 20;
	/** Kept this far off a wall the camera would otherwise sink into (blocks). */
	static final double WALL_MARGIN = 0.25;
	/** The vanilla HUD hidden while it runs. */
	private static final Identifier[] HIDDEN = {VanillaHudElements.CROSSHAIR, VanillaHudElements.HOTBAR, VanillaHudElements.ARMOR_BAR,
		VanillaHudElements.HEALTH_BAR, VanillaHudElements.FOOD_BAR, VanillaHudElements.AIR_BAR, VanillaHudElements.MOUNT_HEALTH,
		VanillaHudElements.INFO_BAR, VanillaHudElements.EXPERIENCE_LEVEL, VanillaHudElements.HELD_ITEM_TOOLTIP, VanillaHudElements.MOB_EFFECTS,
		VanillaHudElements.BOSS_BAR};

	private static boolean running, panning;
	private static int speaker = -1, quiet, panTicks, panAge;
	private static float side = 1.0F, bars, barsO;
	private static @Nullable Vec3 chest;
	/** The camera now and last tick (eased toward its framing each tick, drawn between the two). */
	private static Vec3 pos = Vec3.ZERO, posO = Vec3.ZERO, look = Vec3.ZERO, lookO = Vec3.ZERO;

	private FarmWakeFilmClient() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(FarmhandWake.Shot.TYPE, (payload, context) -> shot(context.client(), payload));
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			if (!minecraft.isPaused()) {
				tick(minecraft);
			}
		});
		for (Identifier id : HIDDEN) {
			HudElementRegistry.replaceElement(id, old -> (graphics, delta) -> {
				if (!running) {
					old.extractRenderState(graphics, delta);
				}
			});
		}
		HudElementRegistry.addLast(JaymosKingdoms.id("farm_wake_film"), (HudElement)FarmWakeFilmClient::render);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			running = false;
			bars = barsO = 0.0F;
		});
	}

	/** Whether the film is on (the camera is ours, the HUD and the in-bed screen hidden, the plain FOV). */
	public static boolean running() {
		return running;
	}

	/** (Backspace, from the in-bed screen) Skipped: the server puts the rest in chat and gets them up. */
	public static void skip() {
		if (running && ClientPlayNetworking.canSend(FarmhandWake.Skip.TYPE)) {
			ClientPlayNetworking.send(new FarmhandWake.Skip());
		}
		running = false;
	}

	private static void shot(Minecraft minecraft, FarmhandWake.Shot shot) {
		LocalPlayer self = minecraft.player;
		if (self == null) {
			return;
		}
		quiet = 0;
		switch (shot.kind()) {
			case FarmhandWake.Shot.SPEAK -> {
				if (!running) {
					running = true;
					panning = false;
					chest = null;
					side = self.getRandom().nextBoolean() ? 1.0F : -1.0F;
					// the eyes open on the ceiling over the bed
					Vec3 eyes = self.getEyePosition();
					pos = posO = eyes.add(0.0, PILLOW_UP * 0.5, 0.0);
					look = lookO = eyes.add(0.0, 4.0, 0.0).add(Vec3.directionFromRotation(0.0F, self.getYRot()).scale(0.5));
				}
				if (shot.entity() >= 0) {
					speaker = shot.entity();
				}
			}
			case FarmhandWake.Shot.CHEST -> {
				if (running) {
					panning = true;
					chest = shot.at();
					panTicks = shot.ticks();
					panAge = 0;
				}
			}
			default -> running = false;
		}
	}

	private static void tick(Minecraft minecraft) {
		barsO = bars;
		LocalPlayer self = minecraft.player;
		if (running) {
			if (self == null || !self.isAlive() || ++quiet > QUIET_TICKS || panning && ++panAge > panTicks + CHEST_GRACE) {
				running = false;
			} else {
				posO = pos;
				lookO = look;
				Vec3[] want = framing(self);
				if (want != null) {
					double ease = panning ? EASE_CHEST : EASE_SPEAKER;
					pos = pos.lerp(want[0], ease);
					look = look.lerp(want[1], ease);
				}
			}
		}
		bars += ((running ? 1.0F : 0.0F) - bars) * BAR_EASE;
		if (!running && bars < 0.01F) {
			bars = 0.0F;
		}
	}

	/** Where the camera is going and what it looks at: the one talking, or the chest; null to stay put. */
	private static Vec3 @Nullable [] framing(LocalPlayer self) {
		Vec3 eyes = self.getEyePosition();
		if (panning && chest != null) {
			Vec3 on = chest.add(0.0, 0.2, 0.0);
			Vec3 toward = flat(on.subtract(eyes), self);
			Vec3 across = new Vec3(toward.z, 0.0, -toward.x).scale(side * 0.3);
			return new Vec3[] {clipped(self, eyes, eyes.add(0.0, CHEST_UP, 0.0).add(toward.scale(CHEST_TOWARD)).add(across)), on};
		}
		Entity who = speaker >= 0 && self.level() != null ? self.level().getEntity(speaker) : null;
		if (who == null) {
			return null;
		}
		Vec3 face = who.getEyePosition();
		Vec3 words = face.add(0.0, who.getBbHeight() - who.getEyeHeight() + WORDS_UP, 0.0);
		Vec3 on = face.lerp(words, WORDS_SHARE);
		Vec3 toward = flat(face.subtract(eyes), self);
		Vec3 across = new Vec3(toward.z, 0.0, -toward.x).scale(side * PILLOW_SIDE);
		return new Vec3[] {clipped(self, eyes, eyes.add(0.0, PILLOW_UP, 0.0).add(toward.scale(PILLOW_TOWARD)).add(across)), on};
	}

	private static Vec3 flat(Vec3 v, LocalPlayer self) {
		Vec3 h = v.horizontal();
		return h.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0.0F, self.getYRot()) : h.normalize();
	}

	/** Where the camera is and which way it looks this frame ({x, y, z, yaw, pitch}), or null when the film isn't running. */
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

	/** The bars, and the skip hint in the lower one. */
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
		if (running && shown > 0.9F) {
			Minecraft minecraft = Minecraft.getInstance();
			Component hint = Component.translatable("gui.jaymoskingdoms.farm_wake.skip");
			int w = minecraft.font.width(hint);
			graphics.text(minecraft.font, hint, width - w - 8, height - bar / 2 - 4, 0xFF777777);
		}
	}
}
