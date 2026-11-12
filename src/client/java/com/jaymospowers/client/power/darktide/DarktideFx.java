package com.jaymospowers.client.power.darktide;

import com.jaymospowers.client.fx.MagicFxClient;
import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.power.darktide.DarktideFlow;
import com.jaymospowers.power.darktide.DarktideLooks;
import com.jaymospowers.power.darktide.DarktideMove;
import com.jaymospowers.power.darktide.DarktideNet;
import com.jaymospowers.power.darktide.DarktideState;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Darktide's client-only effects:
 * <ul>
 * <li>the riposte's <b>freeze frame</b>: both bodies' Darktide animations hold for {@link #FREEZE_TICKS} (only the drawn
 * poses; the server never pauses, so it's fair on the SMP), and a white <b>flash</b> on the screens of the two involved;</li>
 * <li>a Riptide flurry's hit flashes, a parry's glint;</li>
 * <li>Gedan's field: a mirror of still dark water (a {@code MagicFx} pool) spreading round anyone in the stance, a few
 * pale glints rising off it, and a faint blue tint on the screen of anyone standing inside one;</li>
 * <li>the tide on the body, from the synced {@link DarktideState#TIDE}: at high tide water lies at the feet and the eyes
 * shed a pale light; at spring tide the water is wider, ink rises off the shoulders and the blade paints a stroke
 * behind every swing.</li>
 * </ul>
 */
public final class DarktideFx {
	public static final int FREEZE_TICKS = 3;
	static final int FLASH_TICKS = 6, HIT_FLASH_TICKS = 2, PARRY_FLASH_TICKS = 3;
	static final int FIELD_COLOR = 0x5FA8E8;
	/** How solid the mirror and the water at the feet are drawn (0-1), and how far off the tide's looks are drawn (blocks). */
	static final float FIELD_SOLID = 0.8F, TIDE_SOLID = 0.85F;
	static final double LOOKS_RANGE = 64.0;
	/** The blade's trail at spring tide: how wide the stroke is and how long it stays (ticks). */
	static final float TRAIL_STROKE = 0.4F;
	static final int TRAIL_TICKS = 8;

	/** entity id → where its blade's tip was last tick, while it swings at spring tide. */
	private static final Map<Integer, Vec3> TIPS = new HashMap<>();

	/** entity id → the game time its freeze began. */
	private static final Map<Integer, Long> FROZEN = new HashMap<>();
	private static int flash;
	private static int flashLength = 1;
	private static int flashColor = 0xFFFFFF;
	private static float fieldTint;

	private DarktideFx() {
	}

	static void receive(DarktideNet.Fx fx) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer self = minecraft.player;
		if (self == null || minecraft.level == null) {
			return;
		}
		boolean involved = fx.entity() == self.getId() || fx.target() == self.getId();
		long now = minecraft.level.getGameTime();
		switch (fx.kind()) {
			case DarktideNet.Fx.RIPOSTE -> {
				FROZEN.put(fx.entity(), now);
				if (fx.target() >= 0) {
					FROZEN.put(fx.target(), now);
				}
				if (involved) {
					flash(FLASH_TICKS, 0xFFFFFF);
				}
			}
			case DarktideNet.Fx.HIT -> {
				if (involved) {
					flash(HIT_FLASH_TICKS, 0xCFE8FF);
				}
			}
			default -> {
				if (fx.entity() == self.getId()) {
					flash(PARRY_FLASH_TICKS, 0xBFE6FF);
				}
			}
		}
	}

	private static void flash(int ticks, int color) {
		flash = ticks;
		flashLength = ticks;
		flashColor = color;
	}

	/**
	 * The time a Darktide animation on {@code entity} is drawn at: the game time, except that a riposte's freeze holds it
	 * still for {@link #FREEZE_TICKS} and the rest of the move then plays on from there.
	 */
	static float animTime(Entity entity, float partialTicks) {
		float now = entity.level().getGameTime() + partialTicks;
		Long frozen = FROZEN.get(entity.getId());
		if (frozen == null || now < frozen) {
			return now;
		}
		if (now < frozen + FREEZE_TICKS) {
			return frozen;
		}
		return now - FREEZE_TICKS;
	}

	static void tick(Minecraft minecraft) {
		if (flash > 0) {
			flash--;
		}
		ClientLevel level = minecraft.level;
		LocalPlayer self = minecraft.player;
		if (level == null || self == null || minecraft.isPaused()) {
			fieldTint = Math.max(0.0F, fieldTint - 0.1F);
			return;
		}
		long now = level.getGameTime();
		for (Iterator<Map.Entry<Integer, Long>> it = FROZEN.entrySet().iterator(); it.hasNext(); ) {
			if (now - it.next().getValue() > 60) {
				it.remove();
			}
		}
		boolean inField = false;
		RandomSource random = level.getRandom();
		for (Player player : level.players()) {
			DarktideState.Move move = DarktideMove.current(player);
			if (move == null || move.kind() != DarktideMove.STANCE || player.distanceToSqr(self) > 64.0 * 64.0) {
				continue;
			}
			double r = fieldRadius(move, now);
			Vec3 c = player.position();
			// a few pale glints lifting off the mirror
			for (int i = 0; i < 2; i++) {
				double a = random.nextDouble() * Math.PI * 2.0;
				double d = Math.sqrt(random.nextDouble()) * r;
				level.addParticle(MagicFx.mote(DarktideLooks.PALE, 0.6F), c.x + Math.cos(a) * d, c.y + 0.1, c.z + Math.sin(a) * d, 0.0, 0.02, 0.0);
			}
			if (self.distanceTo(player) <= r) {
				inField = true;
			}
		}
		fieldTint = Mth.clamp(fieldTint + (inField ? 0.05F : -0.05F), 0.0F, 1.0F);
		tide(minecraft, level, self, random);
	}

	private static double fieldRadius(DarktideState.Move move, double now) {
		return DarktideLooks.FIELD_RADIUS * Mth.clamp((now - move.start()) / DarktideLooks.FIELD_GROW_TICKS, 0.0, 1.0);
	}

	private static int tideOf(Player player) {
		return player.isInvisible() || player.isSpectator() ? DarktideFlow.EBB : player.getAttachedOrElse(DarktideState.TIDE, DarktideFlow.EBB);
	}

	/** The tide on the body: the eyes' light from high tide; at spring tide ink off the shoulders and the blade's trail. */
	private static void tide(Minecraft minecraft, ClientLevel level, LocalPlayer self, RandomSource random) {
		long now = level.getGameTime();
		for (Player player : level.players()) {
			int tide = tideOf(player);
			if (tide == DarktideFlow.EBB || player.distanceToSqr(self) > LOOKS_RANGE * LOOKS_RANGE) {
				TIPS.remove(player.getId());
				continue;
			}
			boolean ownView = player == minecraft.getCameraEntity() && minecraft.options.getCameraType().isFirstPerson();
			Vec3 look = player.getLookAngle();
			Vec3 right = Vec3.directionFromRotation(0.0F, player.getYRot() + 90.0F);
			if (!ownView && now % 3 == 0) {
				Vec3 eyes = player.getEyePosition().add(look.scale(0.27));
				for (int side = -1; side <= 1; side += 2) {
					Vec3 eye = eyes.add(right.scale(side * 0.13));
					level.addParticle(MagicFx.mote(DarktideLooks.PALE, 0.35F), eye.x, eye.y, eye.z, 0.0, 0.0, 0.0);
				}
			}
			if (tide < DarktideFlow.SPRING) {
				TIPS.remove(player.getId());
				continue;
			}
			if (!ownView && now % 2 == 0) {
				Vec3 shoulder = player.position().add(right.scale(random.nextBoolean() ? 0.35 : -0.35)).add(0.0, player.getBbHeight() * 0.78, 0.0);
				level.addParticle(MagicFx.miasma(DarktideLooks.INK, 1.1F), shoulder.x, shoulder.y, shoulder.z, 0.0, 0.03, 0.0);
			}
			if (player.isSwinging() && !player.getMainHandItem().isEmpty()) {
				float swing = player.getSwingAnimation(1.0F);
				Vec3 tip = player.getEyePosition().add(look.scale(1.5)).add(right.scale(0.9 - 1.8 * swing)).add(0.0, -0.2 - 0.7 * swing, 0.0);
				Vec3 was = TIPS.put(player.getId(), tip);
				if (was != null && was.distanceToSqr(tip) > 0.01 && was.distanceToSqr(tip) < 9.0) {
					MagicFxClient.stroke(was, tip, TRAIL_STROKE, DarktideLooks.INK, TRAIL_TICKS);
				}
			} else {
				TIPS.remove(player.getId());
			}
		}
	}

	/** What lies on the ground under them, drawn each frame so it follows: Stillwater's mirror, the tide at the feet. */
	static void renderWorld(LevelRenderContext context) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null || minecraft.player == null) {
			return;
		}
		float partial = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Vec3 camera = context.levelState().cameraRenderState.pos;
		float time = (level.getGameTime() % 4000L) + partial;
		for (Player player : level.players()) {
			if (player.distanceToSqr(minecraft.player) > LOOKS_RANGE * LOOKS_RANGE) {
				continue;
			}
			Vec3 at = player.getPosition(partial).subtract(camera);
			DarktideState.Move move = DarktideMove.current(player);
			if (move != null && move.kind() == DarktideMove.STANCE) {
				MagicFxClient.drawPool(context.submitNodeCollector(), context.poseStack(), at, (float) fieldRadius(move, level.getGameTime() + partial),
					DarktideLooks.MIRROR, time, FIELD_SOLID);
				continue;
			}
			int tide = tideOf(player);
			if (tide != DarktideFlow.EBB && player.onGround()) {
				MagicFxClient.drawPool(context.submitNodeCollector(), context.poseStack(), at,
					tide >= DarktideFlow.SPRING ? DarktideLooks.SPRING_POOL : DarktideLooks.HIGH_POOL, DarktideLooks.WATER, time, TIDE_SOLID);
			}
		}
	}

	/** The flash and the field's tint, over everything (any player, any power: a riposte's victim sees it too). */
	static void renderOverlay(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		if (fieldTint > 0.0F) {
			int alpha = (int) (fieldTint * 0x28);
			graphics.fill(0, 0, width, height, alpha << 24 | FIELD_COLOR);
		}
		if (flash > 0) {
			float t = (flash - delta.getGameTimeDeltaPartialTick(false)) / flashLength;
			int alpha = (int) (Mth.clamp(t, 0.0F, 1.0F) * 0xB0);
			graphics.fill(0, 0, width, height, alpha << 24 | flashColor);
		}
	}
}
