package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Heritage;
import com.jaymoskingdoms.JaymosKingdoms;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * (0.7.96) A wounded Warrior-born's fury, seen (Andy: "Warrior-born low health visual indication/boost"). Below half
 * health ({@link Heritage#rage}) the screen's edges burn red and throb with their pulse, quicker and deeper the closer
 * they are to death; everyone sees them breathe hot, ragged puffs, more of them as the rage grows. Health is synced to
 * every client, so nothing new goes over the wire.
 */
public final class WarriorFury {
	/** How much the edges redden (0-1): at the first of the rage, at its full, and at the top of a beat. */
	static final float EDGE = 0.25F, EDGE_FULL = 0.6F, EDGE_BEAT = 0.3F;
	/** Heartbeat period (ticks): at the first of the rage, and at its full. */
	static final float BEAT = 22.0F, BEAT_FULL = 11.0F;
	/** How fast the red comes in and goes out (per tick). */
	static final float FADE = 0.05F;
	/** A breath every this many ticks at the first of the rage, and at its full. */
	static final int BREATH_EVERY = 30, BREATH_EVERY_FULL = 8;

	private static final Identifier VIGNETTE = Identifier.withDefaultNamespace("textures/misc/vignette.png");

	/** The local player's rage, eased toward the real one. */
	private static float rage, lastRage;

	private WarriorFury() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(WarriorFury::tick);
		HudElementRegistry.attachElementBefore(VanillaHudElements.MISC_OVERLAYS, JaymosKingdoms.id("warrior_fury"), WarriorFury::edges);
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer self = minecraft.player;
		ClientLevel level = minecraft.level;
		lastRage = rage;
		if (self == null || level == null) {
			rage = lastRage = 0.0F;
			return;
		}
		float target = furious(self) ? Heritage.rage(self) : 0.0F;
		rage = target > rage ? Math.min(target, rage + FADE) : Math.max(target, rage - FADE);
		if (minecraft.isPaused()) {
			return;
		}
		for (AbstractClientPlayer player : level.players()) {
			if (!furious(player) || player.isInvisible() || player == minecraft.getCameraEntity() && minecraft.options.getCameraType().isFirstPerson()) {
				continue;
			}
			float r = Heritage.rage(player);
			int every = Math.round(Mth.lerp(r, BREATH_EVERY, BREATH_EVERY_FULL));
			if (r > 0.0F && (player.tickCount + player.getId()) % every == 0) {
				breathe(level, player, player.getRandom());
			}
		}
	}

	private static boolean furious(AbstractClientPlayer player) {
		return Heritage.of(player) == Heritage.Kind.WARRIORBORN && player.isAlive() && !player.isSpectator();
	}

	/** A hot puff out of the mouth, carried forward the way they face. */
	private static void breathe(ClientLevel level, AbstractClientPlayer player, RandomSource random) {
		Vec3 look = player.getViewVector(1.0F);
		Vec3 mouth = player.getEyePosition().add(look.scale(0.35)).add(0.0, -0.15, 0.0);
		for (int i = 0; i < 2; i++) {
			level.addParticle(ParticleTypes.WHITE_SMOKE, mouth.x, mouth.y, mouth.z, look.x * 0.06 + (random.nextDouble() - 0.5) * 0.02,
				0.015 + random.nextDouble() * 0.01, look.z * 0.06 + (random.nextDouble() - 0.5) * 0.02);
		}
	}

	/** The beat now: 0 at rest, 1 at the top of the thump, a smaller second thump after it. */
	private static float beat(Minecraft minecraft, float r, float partialTicks) {
		float period = Mth.lerp(r, BEAT, BEAT_FULL);
		float t = ((minecraft.level.getGameTime() % 1000L) + partialTicks) % period / period;
		return Math.max(thump(t, 0.0F), 0.6F * thump(t, 0.22F));
	}

	private static float thump(float t, float at) {
		float d = (t - at) / 0.09F;
		return d < 0.0F || d > 1.0F ? 0.0F : Mth.sin(d * Mth.PI);
	}

	/** The red edges, as {@link BloodVision}'s: the vignette pipeline darkens green and blue and leaves red. */
	private static void edges(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		float partialTicks = delta.getGameTimeDeltaPartialTick(false);
		float r = Mth.lerp(partialTicks, lastRage, rage);
		if (r <= 0.0F || minecraft.level == null) {
			return;
		}
		float k = Math.min(1.0F, Mth.lerp(r, EDGE, EDGE_FULL) + EDGE_BEAT * r * beat(minecraft, r, partialTicks));
		int color = ARGB.colorFromFloat(1.0F, 0.1F * k, k, k);
		graphics.blit(RenderPipelines.VIGNETTE, VIGNETTE, 0, 0, 0.0F, 0.0F, graphics.guiWidth(), graphics.guiHeight(), graphics.guiWidth(),
			graphics.guiHeight(), color);
	}
}
