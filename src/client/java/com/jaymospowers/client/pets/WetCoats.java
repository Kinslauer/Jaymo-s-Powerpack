package com.jaymospowers.client.pets;

import com.jaymospowers.JaymosPowers;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

/**
 * (Motion patch 6) Wet coats, after Actions &amp; Stuff's wet wolves (Andy: "Do them all"). Every furry animal
 * ({@code #jaymospowers:furry}: cats, foxes, horses, sheep, llamas, rabbits, bears, goats, camels, our Aether beasts)
 * comes out of water or rain with its coat a shade darker, and once on dry ground a moment it shakes itself off (a quick
 * shudder and a spray of drops), most of the wet going with it; the rest dries off over a minute. Wolves already do
 * all of this in vanilla. Worked out from where each animal is, so everyone sees the same.
 */
public final class WetCoats {
	// ===== the knobs ================================================================================================
	public static final TagKey<EntityType<?>> FURRY = TagKey.create(Registries.ENTITY_TYPE, JaymosPowers.id("furry"));
	/** How much darker a soaked coat is drawn (share of its colour). */
	static final float DARKEST = 0.32F;
	/** Out of the wet this long on the ground before the shake, the shake's length (ticks), and how wet it leaves the coat. */
	static final int SHAKE_AFTER = 12, SHAKE_TICKS = 20;
	static final float AFTER_SHAKE = 0.25F;
	/** Drying off without a shake, per tick. */
	static final float DRY = 1.0F / 1200.0F;
	/** The shudder: how far it rolls each way (degrees) and how fast (radians a tick). */
	static final float SHUDDER = 9.0F, SHUDDER_RATE = 2.4F;
	/** Only animals this near the camera are watched (blocks). */
	static final double WATCH = 64.0;
	// ================================================================================================================

	/** {@code wet} 0-1; {@code shake} ticks into a shake, or negative. */
	public record Coat(float wet, float shake) {
	}

	static final RenderStateDataKey<Coat> KEY = RenderStateDataKey.create(() -> "jaymospowers:wet_coat");

	private static final class Track {
		float wet;
		int dryFor;
		long shakeFrom = -1;
	}

	private static final Int2ObjectOpenHashMap<Track> TRACKS = new Int2ObjectOpenHashMap<>();

	private WetCoats() {
	}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(WetCoats::tick);
	}

	private static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		Entity camera = minecraft.getCameraEntity();
		if (level == null || camera == null) {
			TRACKS.clear();
			return;
		}
		if (minecraft.isPaused()) {
			return;
		}
		long now = level.getGameTime();
		for (Entity e : level.entitiesForRendering()) {
			if (!(e instanceof LivingEntity living) || !e.getType().builtInRegistryHolder().is(FURRY) || e.distanceToSqr(camera) > WATCH * WATCH) {
				continue;
			}
			boolean wet = e.isInWaterOrRain();
			Track t = TRACKS.get(e.getId());
			if (t == null) {
				if (!wet) {
					continue;
				}
				t = new Track();
				TRACKS.put(e.getId(), t);
			}
			if (wet) {
				t.wet = 1.0F;
				t.dryFor = 0;
				t.shakeFrom = -1;
				continue;
			}
			t.dryFor++;
			if (t.shakeFrom < 0 && t.wet > 0.5F && t.dryFor >= SHAKE_AFTER && e.onGround() && living.isAlive() && !e.isPassenger()) {
				t.shakeFrom = now;
			}
			if (t.shakeFrom >= 0) {
				long into = now - t.shakeFrom;
				if (into < SHAKE_TICKS) {
					spray(level, e);
				} else if (into == SHAKE_TICKS) {
					t.wet = Math.min(t.wet, AFTER_SHAKE);
				}
			}
			t.wet = Math.max(0.0F, t.wet - DRY);
			if (t.wet <= 0.0F) {
				TRACKS.remove(e.getId());
			}
		}
		if (now % 200 == 0) {
			TRACKS.keySet().removeIf(id -> level.getEntity(id) == null);
		}
	}

	/** Drops flung off the coat as it shakes. */
	private static void spray(ClientLevel level, Entity e) {
		float w = e.getBbWidth(), h = e.getBbHeight();
		for (int i = 0; i < 3; i++) {
			double x = e.getX() + (level.getRandom().nextDouble() - 0.5) * w * 1.4;
			double z = e.getZ() + (level.getRandom().nextDouble() - 0.5) * w * 1.4;
			level.addParticle(ParticleTypes.SPLASH, x, e.getY() + h * (0.4 + level.getRandom().nextDouble() * 0.5), z, 0.0, 0.0, 0.0);
		}
	}

	public static void extract(LivingEntity entity, LivingEntityRenderState state, float partialTicks) {
		Track t = TRACKS.isEmpty() ? null : TRACKS.get(entity.getId());
		if (t == null) {
			state.setData(KEY, null);
			return;
		}
		float shake = t.shakeFrom >= 0 ? entity.level().getGameTime() - t.shakeFrom + partialTicks : -1.0F;
		state.setData(KEY, new Coat(t.wet, shake < SHAKE_TICKS ? shake : -1.0F));
	}

	/** After the body faces its way: the shudder as it shakes off. */
	public static void rotate(LivingEntityRenderState state, PoseStack poseStack) {
		Coat coat = state.getData(KEY);
		if (coat == null || coat.shake() < 0.0F || state.deathTime > 0.0F) {
			return;
		}
		float fade = Mth.sin(coat.shake() / SHAKE_TICKS * Mth.PI);
		poseStack.rotateDegrees(Axis.ZP, SHUDDER * fade * Mth.sin(coat.shake() * SHUDDER_RATE));
	}

	/** The model's colour, a shade darker while wet (LivingEntityRenderer.getModelTint). */
	public static int tint(LivingEntityRenderState state, int color) {
		Coat coat = state.getData(KEY);
		if (coat == null || coat.wet() <= 0.0F) {
			return color;
		}
		float k = 1.0F - DARKEST * coat.wet();
		int r = (int)(((color >> 16) & 0xFF) * k), g = (int)(((color >> 8) & 0xFF) * k), b = (int)((color & 0xFF) * k);
		return color & 0xFF000000 | r << 16 | g << 8 | b;
	}
}
