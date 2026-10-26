package com.jaymospowers.client.alchemy;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.alchemy.Corruption;
import com.jaymospowers.alchemy.flux.FluxCorruption;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * What corruption does to your senses, drawn and heard on your own client only (nothing is real, nothing is sent), by
 * stage (see {@link FluxCorruption#STAGES}):
 * <ol>
 * <li>Whispers: a vague, quiet sound a few blocks off, now and then.</li>
 * <li>Sights: particles at the edge of your view, a mob's sound behind you, a dark figure standing far off in the
 * corner of your eye that is gone the moment you look straight at it.</li>
 * <li>and 4: the same, more often, and a violet vignette closes in on the screen.</li>
 * </ol>
 * Timers count down per event (never a bare modulo), so nothing sounds in lockstep.
 */
public final class CorruptionClient {
	// ---- tuning ----
	/** Ticks between whispers: this much plus up to this much more (shorter at higher stages). */
	public static final int WHISPER_MIN = 400, WHISPER_SPREAD = 800;
	/** The same for sights. */
	public static final int SIGHT_MIN = 500, SIGHT_SPREAD = 900;
	/** A shadow figure stays this long (ticks) unless looked at; looking within this many degrees of it for this many ticks ends it. */
	public static final int SHADOW_TICKS = 160, SHADOW_SEEN_TICKS = 5;
	public static final double SHADOW_SEEN_DEGREES = 22.0;
	/** The vignette: total corruption where it begins and where it is fullest, its colour and greatest opacity (0-255) and width. */
	public static final int VIGNETTE_FROM = FluxCorruption.STAGES[1], VIGNETTE_FULL = 170;
	public static final int VIGNETTE_COLOUR = 0x2A0A3A, VIGNETTE_ALPHA = 0xA0;
	public static final float VIGNETTE_EDGE = 0.25F;
	private static final int BANDS = 8;

	private static int whisperIn = WHISPER_MIN, sightIn = SIGHT_MIN;
	private static @Nullable Vec3 shadow;
	private static int shadowAge, shadowSeen;
	private static float shade;

	private CorruptionClient() {
	}

	static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(CorruptionClient::tick);
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, JaymosPowers.id("corruption_vignette"), CorruptionClient::render);
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		ClientLevel level = minecraft.level;
		if (player == null || level == null || minecraft.isPaused()) {
			return;
		}
		int total = Corruption.total(player);
		int stage = player.isAlive() && !player.isSpectator() ? FluxCorruption.stage(total) : 0;
		float target = stage >= 2 ? Mth.clamp((total - VIGNETTE_FROM) / (float) (VIGNETTE_FULL - VIGNETTE_FROM), 0.0F, 1.0F) : 0.0F;
		shade += (target - shade) * 0.02F;
		if (stage == 0) {
			shadow = null;
			return;
		}
		RandomSource random = level.getRandom();
		if (--whisperIn <= 0) {
			whisper(player, level, random);
			whisperIn = WHISPER_MIN + random.nextInt(WHISPER_SPREAD) / stage;
		}
		if (stage >= 2 && --sightIn <= 0) {
			sight(player, level, random);
			sightIn = SIGHT_MIN + random.nextInt(SIGHT_SPREAD) / (stage - 1);
		}
		keepShadow(player, level, random);
	}

	/** A direction on the ground, {@code degrees} off the way the player faces, as a unit vector. */
	private static Vec3 around(LocalPlayer player, double degrees) {
		double yaw = Math.toRadians(player.getYRot() + degrees);
		return new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
	}

	private static void sound(ClientLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
		level.playLocalSound(at.x, at.y, at.z, sound, SoundSource.AMBIENT, volume, pitch, false);
	}

	/** Something quiet and vague a few blocks away: a cave breath, a soul slipping out, a deep nearby pulse. */
	private static void whisper(LocalPlayer player, ClientLevel level, RandomSource random) {
		Vec3 at = player.getEyePosition().add(around(player, random.nextInt(360)).scale(3.0 + random.nextDouble() * 3.0));
		switch (random.nextInt(3)) {
			case 0 -> sound(level, at, SoundEvents.AMBIENT_CAVE.value(), 0.4F, 0.7F + random.nextFloat() * 0.3F);
			case 1 -> sound(level, at, SoundEvents.SOUL_ESCAPE.value(), 0.3F, 0.5F + random.nextFloat() * 0.3F);
			default -> sound(level, at, SoundEvents.WARDEN_NEARBY_CLOSE, 0.25F, 0.8F);
		}
	}

	/** One of: motes at the edge of view, a mob behind you, a figure far off in the corner of your eye. */
	private static void sight(LocalPlayer player, ClientLevel level, RandomSource random) {
		switch (random.nextInt(3)) {
			case 0 -> {
				double side = random.nextBoolean() ? 80.0 : -80.0;
				Vec3 at = player.getEyePosition().add(around(player, side + random.nextInt(21) - 10).scale(5.0 + random.nextDouble() * 4.0));
				for (int i = 0; i < 6; i++) {
					level.addParticle(ParticleTypes.SMOKE, at.x + (random.nextDouble() - 0.5), at.y - random.nextDouble() * 1.5, at.z + (random.nextDouble() - 0.5),
						0.0, 0.02, 0.0);
				}
			}
			case 1 -> {
				Vec3 at = player.getEyePosition().add(around(player, 180.0 + random.nextInt(61) - 30).scale(4.0 + random.nextDouble() * 4.0));
				SoundEvent[] mobs = {SoundEvents.ZOMBIE_AMBIENT, SoundEvents.SKELETON_AMBIENT, SoundEvents.SPIDER_AMBIENT, SoundEvents.ZOMBIE_STEP,
					SoundEvents.CREEPER_PRIMED};
				sound(level, at, mobs[random.nextInt(mobs.length)], 0.5F, 0.9F);
			}
			default -> {
				if (shadow == null) {
					Vec3 flat = player.position().add(around(player, (random.nextBoolean() ? 1 : -1) * (55.0 + random.nextInt(30))).scale(10.0 + random.nextDouble() * 6.0));
					BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(flat));
					shadow = new Vec3(flat.x, ground.getY(), flat.z);
					shadowAge = 0;
					shadowSeen = 0;
				}
			}
		}
	}

	/** The standing figure: a thin column of dark smoke, gone once it has been looked at or has stood long enough. */
	private static void keepShadow(LocalPlayer player, ClientLevel level, RandomSource random) {
		Vec3 at = shadow;
		if (at == null) {
			return;
		}
		Vec3 toIt = at.add(0.0, 1.0, 0.0).subtract(player.getEyePosition()).normalize();
		boolean looking = player.getLookAngle().dot(toIt) > Math.cos(Math.toRadians(SHADOW_SEEN_DEGREES));
		shadowSeen = looking ? shadowSeen + 1 : 0;
		if (++shadowAge > SHADOW_TICKS || shadowSeen >= SHADOW_SEEN_TICKS) {
			shadow = null;
			return;
		}
		for (double h : new double[] {0.1, 0.6, 1.1, 1.6, 1.95}) {
			level.addParticle(ParticleTypes.LARGE_SMOKE, at.x + (random.nextDouble() - 0.5) * 0.3, at.y + h, at.z + (random.nextDouble() - 0.5) * 0.3,
				0.0, 0.0, 0.0);
		}
	}

	private static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		if (shade < 0.01F) {
			return;
		}
		int w = graphics.guiWidth(), h = graphics.guiHeight();
		int edge = Math.max(24, (int) (h * VIGNETTE_EDGE));
		int alpha = Mth.clamp((int) (VIGNETTE_ALPHA * shade), 0, 255);
		int solid = alpha << 24 | VIGNETTE_COLOUR;
		graphics.fillGradient(0, 0, w, edge, solid, VIGNETTE_COLOUR);
		graphics.fillGradient(0, h - edge, w, h, VIGNETTE_COLOUR, solid);
		int band = Mth.ceil(edge / (float) BANDS);
		for (int i = 0; i < BANDS; i++) {
			int a = (int) (alpha * (1.0F - i / (float) BANDS) * 0.8F);
			graphics.fill(i * band, 0, i * band + band, h, a << 24 | VIGNETTE_COLOUR);
			graphics.fill(w - i * band - band, 0, w - i * band, h, a << 24 | VIGNETTE_COLOUR);
		}
	}
}
