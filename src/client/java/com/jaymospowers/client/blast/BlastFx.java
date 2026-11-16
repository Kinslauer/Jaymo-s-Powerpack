package com.jaymospowers.client.blast;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.client.LowEndMode;
import com.jaymospowers.client.ScreenShake;
import com.jaymospowers.client.sound.Blasts;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Andy's pick from Explosion Overhaul (Vinlanx; custom licence, so its ideas only, our own code): what a blast does
 * besides shake you (that's {@link ScreenShake}, timed with its sound by {@link Blasts}). Every explosion, from any mod:
 * <ul>
 * <li><b>debris:</b> chunks of the blocks it broke are flung up and out, tumble, bounce where they land and lie a while
 * ({@link DebrisChunk}). Only when it broke blocks, more the more it broke.</li>
 * <li><b>dust:</b> a ring of dust the colour of the ground it went off on rolls out along it, and dark smoke rises from the
 * middle.</li>
 * <li><b>cave-ins:</b> underground, the ceiling round it sheds dust and loose grit for a couple of seconds.</li>
 * <li><b>a flash:</b> a short warm whiteout when one goes off close by in your sight, stronger for a bigger blast and when
 * you're looking at it.</li>
 * <li><b>ringing ears:</b> right beside one, your ears ring, everything else goes quiet and comes back slowly, and the
 * view sways a little ({@link ScreenShake#daze}).</li>
 * </ul>
 * Not its realistic explosions: blocks break as they always have. All of it is on this side only (nothing is sent), and
 * there's less of it on lower particle settings and in low-end mode.
 */
public final class BlastFx {
	/** Nothing is made for a blast farther off than this (blocks). */
	static final double SEEN_FROM = 128.0;

	/** Debris: chunks for a blast that broke anything, one more for every this many blocks broken, at most CHUNKS_MAX. */
	static final int CHUNKS_BASE = 6, CHUNKS_MAX = 110;
	static final float BLOCKS_PER_CHUNK = 2.0F;
	/** At most this many chunks are made in one tick, all blasts together (a chain of TNT, a meteor's rings). */
	static final int CHUNK_BUDGET = 300;
	/** How fast they're flung (blocks/tick): this, and this much more per block of the blast's radius. */
	static final double FLING = 0.35, FLING_PER_RADIUS = 0.11;
	static final int CHUNK_LIFE_MIN = 60, CHUNK_LIFE_MAX = 140;
	/** How big a chunk is drawn (times a block-breaking crumb). */
	static final float CHUNK_SIZE_MIN = 1.4F, CHUNK_SIZE_MAX = 3.2F;
	/** Points looked at inside the blast to find what it was made of. */
	static final int SAMPLES = 32;

	/** Dust: puffs per block of radius (at most DUST_MAX), how fast the ring rolls out (blocks/tick), how long a puff lasts. */
	static final float DUST_PER_RADIUS = 7.0F;
	static final int DUST_MAX = 60, DUST_LIFE_MIN = 50, DUST_LIFE_MAX = 110;
	static final double DUST_SPEED = 0.05, DUST_SPEED_PER_RADIUS = 0.012;
	/** Smoke from the middle: puffs per block of radius, at most SMOKE_MAX. */
	static final float SMOKE_PER_RADIUS = 1.5F;
	static final int SMOKE_MAX = 10;
	/** The ground's colour is mixed this far toward a pale dusty brown-grey (DUST), and the smoke is dark grey. */
	static final float GROUND_SHARE = 0.6F;
	static final float[] DUST = {0.62F, 0.56F, 0.49F}, SMOKE = {0.22F, 0.21F, 0.21F};
	/** How far down (blocks past the radius) the ground is looked for. */
	static final int GROUND_SEARCH = 3;

	/** Cave-ins: how far up the ceiling is looked for (blocks), how long it sheds (ticks), over how wide (times the radius). */
	static final int CEILING_SEARCH = 14, CAVE_TICKS = 50;
	static final double CAVE_REACH = 2.0;
	/** Grit a tick per block of radius at first (fewer as it settles); one in this many is a falling chunk, not dust. */
	static final float GRIT_PER_RADIUS = 0.8F;
	static final int CHUNK_EVERY = 6;

	/** The flash: seen this many radii away, at most this opaque, this long (ticks); looking away keeps this much of it. */
	static final double FLASH_REACH = 14.0;
	static final float FLASH_ALPHA = 0.55F, FLASH_AWAY = 0.35F;
	static final int FLASH_TICKS = 7;
	/** A blast of this radius flashes at full strength (TNT is 4). */
	static final float FLASH_FULL_RADIUS = 6.0F;
	static final int FLASH_COLOUR = 0xFFF2D8;

	/** Ringing ears: within this many radii (plus a block), for this long (ticks, at the edge to right on top of it). */
	static final double DEAF_REACH = 1.8;
	static final int DEAF_TICKS_MIN = 40, DEAF_TICKS_MAX = 140;
	/** Other sounds play at this share of their volume at the worst of it, coming back as it passes. */
	static final float MUFFLED = 0.25F;
	/** How far the view sways (degrees) at the worst of it. */
	static final float DAZE = 2.5F;
	static final Identifier RING = JaymosPowers.id("blast.ring");
	private static final SoundEvent RING_SOUND = SoundEvent.createVariableRangeEvent(RING);

	private record CaveIn(Vec3 at, float radius, int ticks, int[] age) {
	}

	private static final List<CaveIn> CAVE_INS = new ArrayList<>();
	private static int chunksThisTick;
	private static int flashTicks;
	private static float flashStrength;
	private static int deafTicks, deafLength;
	private static float deafStrength;

	private BlastFx() {
	}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(BlastFx::tick);
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, JaymosPowers.id("blast_flash"), BlastFx::renderFlash);
	}

	/** An explosion's packet has come in (before its blocks are gone on this side, so they can still be seen). */
	public static void explosion(Vec3 at, float radius, int blocksBroken) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		Player player = minecraft.player;
		if (level == null || player == null || radius <= 0.0F) {
			return;
		}
		Vec3 eye = player.getEyePosition();
		double d = eye.distanceTo(at);
		if (d > SEEN_FROM) {
			return;
		}
		RandomSource random = level.getRandom();
		float share = share(minecraft);
		BlockPos ground = ground(level, at, radius);
		if (blocksBroken > 0) {
			debris(minecraft, level, at, radius, blocksBroken, ground, share, random);
		}
		dust(minecraft, level, at, radius, ground, share, random);
		if (ceiling(level, BlockPos.containing(at), CEILING_SEARCH) != null) {
			CAVE_INS.add(new CaveIn(at, radius, CAVE_TICKS, new int[1]));
		}
		flash(level, player, eye, at, radius, d);
		double deafReach = radius * DEAF_REACH + 1.0;
		if (d < deafReach) {
			float c = (float)(1.0 - d / deafReach);
			// just after its own boom has played, so the boom itself isn't muffled
			Blasts.later(1, () -> deafen(minecraft, c));
		}
	}

	// ---- debris ------------------------------------------------------------------------------------------------------

	private static void debris(Minecraft minecraft, ClientLevel level, Vec3 at, float radius, int blocksBroken, @Nullable BlockPos ground, float share,
		RandomSource random) {
		List<BlockState> states = new ArrayList<>();
		for (int i = 0; i < SAMPLES; i++) {
			Vec3 dir = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).normalize();
			Vec3 p = at.add(dir.scale(radius * 0.9 * Math.cbrt(random.nextDouble())));
			BlockState state = level.getBlockState(BlockPos.containing(p));
			if (debrisOf(state)) {
				states.add(state);
			}
		}
		if (states.isEmpty() && ground != null && debrisOf(level.getBlockState(ground))) {
			states.add(level.getBlockState(ground));
		}
		if (states.isEmpty()) {
			return;
		}
		int chunks = Math.round(Math.min(CHUNKS_MAX, CHUNKS_BASE + blocksBroken / BLOCKS_PER_CHUNK) * share);
		chunks = Math.min(chunks, CHUNK_BUDGET - chunksThisTick);
		double fling = FLING + FLING_PER_RADIUS * radius;
		for (int i = 0; i < chunks; i++) {
			BlockState state = states.get(random.nextInt(states.size()));
			Vec3 dir = new Vec3(random.nextGaussian(), Math.abs(random.nextGaussian()) * 1.3 + 0.4, random.nextGaussian()).normalize();
			double speed = fling * (0.35 + random.nextDouble() * 0.65);
			double x = at.x + random.nextGaussian() * radius * 0.25, y = at.y + 0.3 + random.nextDouble() * radius * 0.2,
				z = at.z + random.nextGaussian() * radius * 0.25;
			DebrisChunk chunk = new DebrisChunk(level, x, y, z, dir.x * speed, dir.y * speed, dir.z * speed, state);
			chunk.scale(Mth.lerp(random.nextFloat(), CHUNK_SIZE_MIN, CHUNK_SIZE_MAX));
			chunk.setLifetime(CHUNK_LIFE_MIN + random.nextInt(CHUNK_LIFE_MAX - CHUNK_LIFE_MIN + 1));
			minecraft.particleEngine.add(chunk);
		}
		chunksThisTick += Math.max(0, chunks);
	}

	private static boolean debrisOf(BlockState state) {
		return !state.isAir() && state.getFluidState().isEmpty() && state.shouldSpawnTerrainParticles();
	}

	// ---- dust and smoke ----------------------------------------------------------------------------------------------

	private static void dust(Minecraft minecraft, ClientLevel level, Vec3 at, float radius, @Nullable BlockPos ground, float share, RandomSource random) {
		if (ground != null) {
			float[] tint = groundTint(level, ground);
			double y = ground.getY() + 1.0;
			int puffs = Math.round(Math.min(DUST_MAX, radius * DUST_PER_RADIUS) * share);
			double roll = DUST_SPEED + DUST_SPEED_PER_RADIUS * radius;
			for (int i = 0; i < puffs; i++) {
				double a = random.nextDouble() * Mth.TWO_PI, r = radius * (0.3 + random.nextDouble() * 0.3);
				double out = roll * (0.5 + random.nextDouble() * 0.5);
				Vec3 v = new Vec3(Math.cos(a) * out, 0.004 + random.nextDouble() * 0.02, Math.sin(a) * out);
				add(minecraft, ParticleTypes.CAMPFIRE_COSY_SMOKE, at.x + Math.cos(a) * r, y + 0.2 + random.nextDouble(), at.z + Math.sin(a) * r, v, tint,
					DUST_LIFE_MIN + random.nextInt(DUST_LIFE_MAX - DUST_LIFE_MIN + 1));
			}
		}
		int smoke = Math.round(Math.min(SMOKE_MAX, radius * SMOKE_PER_RADIUS) * share);
		for (int i = 0; i < smoke; i++) {
			Vec3 v = new Vec3(random.nextGaussian() * 0.02, 0.06 + random.nextDouble() * 0.06, random.nextGaussian() * 0.02);
			add(minecraft, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, at.x + random.nextGaussian() * radius * 0.2, at.y + random.nextDouble() * radius * 0.4,
				at.z + random.nextGaussian() * radius * 0.2, v, SMOKE, 60 + random.nextInt(60));
		}
	}

	/** The ground's own colour (its map colour), mixed toward a dusty grey-brown. */
	private static float[] groundTint(ClientLevel level, BlockPos ground) {
		int col = level.getBlockState(ground).getMapColor(level, ground).col;
		if (col == 0) {
			return DUST;
		}
		float[] tint = new float[3];
		for (int i = 0; i < 3; i++) {
			float channel = (col >> (16 - 8 * i) & 0xFF) / 255.0F;
			tint[i] = Mth.lerp(GROUND_SHARE, DUST[i], channel);
		}
		return tint;
	}

	/** The first solid block at or under the blast, within its radius and a little (null: it went off in the air). */
	private static @Nullable BlockPos ground(ClientLevel level, Vec3 at, float radius) {
		BlockPos.MutableBlockPos pos = BlockPos.containing(at).mutable();
		for (int i = 0; i <= radius + GROUND_SEARCH; i++, pos.move(0, -1, 0)) {
			if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
				return pos.immutable();
			}
		}
		return null;
	}

	/** The first solid block above {@code from} within {@code search} blocks with no sky beyond it (null: open sky, or none). */
	private static @Nullable BlockPos ceiling(ClientLevel level, BlockPos from, int search) {
		if (level.canSeeSky(from)) {
			return null;
		}
		BlockPos.MutableBlockPos pos = from.mutable();
		for (int i = 0; i < search; i++) {
			pos.move(0, 1, 0);
			if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
				return pos.immutable();
			}
		}
		return null;
	}

	// ---- cave-ins ----------------------------------------------------------------------------------------------------

	private static void tick(Minecraft minecraft) {
		chunksThisTick = 0;
		ClientLevel level = minecraft.level;
		if (level == null) {
			CAVE_INS.clear();
			flashTicks = 0;
			deafTicks = 0;
			return;
		}
		if (minecraft.isPaused()) {
			return;
		}
		if (flashTicks > 0) {
			flashTicks--;
		}
		if (deafTicks > 0) {
			deafTicks--;
		}
		RandomSource random = level.getRandom();
		float share = share(minecraft);
		for (Iterator<CaveIn> it = CAVE_INS.iterator(); it.hasNext(); ) {
			CaveIn cave = it.next();
			int age = ++cave.age[0];
			if (age >= cave.ticks) {
				it.remove();
				continue;
			}
			float left = 1.0F - age / (float)cave.ticks;
			int grit = (int)(cave.radius * GRIT_PER_RADIUS * left * left * share + random.nextFloat());
			for (int i = 0; i < grit; i++) {
				double a = random.nextDouble() * Mth.TWO_PI, r = cave.radius * CAVE_REACH * Math.sqrt(random.nextDouble());
				BlockPos column = BlockPos.containing(cave.at.x + Math.cos(a) * r, cave.at.y, cave.at.z + Math.sin(a) * r);
				BlockPos top = ceiling(level, column, CEILING_SEARCH);
				if (top == null) {
					continue;
				}
				BlockState state = level.getBlockState(top);
				double x = top.getX() + random.nextDouble(), y = top.getY() - 0.1, z = top.getZ() + random.nextDouble();
				if (random.nextInt(CHUNK_EVERY) == 0 && debrisOf(state) && chunksThisTick < CHUNK_BUDGET) {
					DebrisChunk chunk = new DebrisChunk(level, x, y - 0.2, z, 0.0, -0.05, 0.0, state).falling();
					chunk.scale(Mth.lerp(random.nextFloat(), 0.8F, 1.6F));
					chunk.setLifetime(40 + random.nextInt(40));
					minecraft.particleEngine.add(chunk);
					chunksThisTick++;
				} else {
					add(minecraft, new BlockParticleOption(ParticleTypes.FALLING_DUST, state), x, y, z, Vec3.ZERO, null, 0);
				}
			}
		}
	}

	// ---- the flash ---------------------------------------------------------------------------------------------------

	private static void flash(ClientLevel level, Player player, Vec3 eye, Vec3 at, float radius, double d) {
		double reach = radius * FLASH_REACH;
		if (d >= reach) {
			return;
		}
		if (d > radius) {
			HitResult hit = level.clip(new ClipContext(eye, at, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player));
			if (hit.getType() != HitResult.Type.MISS && hit.getLocation().distanceTo(at) > radius * 0.5 + 1.0) {
				return;
			}
		}
		float facing = d < 0.5 ? 1.0F : (float)Mth.clamp(player.getViewVector(1.0F).dot(at.subtract(eye).normalize()), 0.0, 1.0);
		float near = (float)(1.0 - d / reach);
		float s = Math.min(1.0F, radius / FLASH_FULL_RADIUS) * near * near * Mth.lerp(facing, FLASH_AWAY, 1.0F);
		if (s < 0.03F) {
			return;
		}
		flashStrength = flashTicks > 0 ? Math.max(flashStrength * flashTicks / FLASH_TICKS, s) : s;
		flashTicks = FLASH_TICKS;
	}

	private static void renderFlash(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		if (flashTicks <= 0) {
			return;
		}
		float left = Mth.clamp((flashTicks - delta.getGameTimeDeltaPartialTick(false)) / FLASH_TICKS, 0.0F, 1.0F);
		int alpha = Mth.clamp((int)(FLASH_ALPHA * flashStrength * left * left * 255.0F), 0, 255);
		if (alpha > 0) {
			graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), alpha << 24 | FLASH_COLOUR);
		}
	}

	// ---- ringing ears ------------------------------------------------------------------------------------------------

	private static void deafen(Minecraft minecraft, float c) {
		int ticks = Math.round(Mth.lerp(c, DEAF_TICKS_MIN, DEAF_TICKS_MAX));
		if (deafTicks <= 0 || c >= deafStrength * deafTicks / (float)deafLength) {
			deafStrength = c;
			deafTicks = ticks;
			deafLength = ticks;
		}
		minecraft.getSoundManager().play(SimpleSoundInstance.forUI(RING_SOUND, 1.0F, Mth.clamp(0.3F + c * 0.7F, 0.0F, 1.0F)));
		ScreenShake.daze(DAZE * c, ticks);
	}

	/** How much of its volume a sound starting now keeps (1 unless your ears are ringing). */
	public static float hearing(SoundInstance sound) {
		if (deafTicks <= 0 || deafLength <= 0) {
			return 1.0F;
		}
		SoundSource source = sound.getSource();
		if (source == SoundSource.MASTER || source == SoundSource.MUSIC || source == SoundSource.UI || RING.equals(sound.getIdentifier())) {
			return 1.0F;
		}
		float left = deafTicks / (float)deafLength;
		return 1.0F - (1.0F - MUFFLED) * deafStrength * left;
	}

	// ---- shared ------------------------------------------------------------------------------------------------------

	/** Fewer of everything on decreased particles, far fewer on minimal, and half again in low-end mode. */
	private static float share(Minecraft minecraft) {
		ParticleStatus status = minecraft.options.particles().get();
		float share = status == ParticleStatus.MINIMAL ? 0.25F : status == ParticleStatus.DECREASED ? 0.5F : 1.0F;
		return LowEndMode.on() ? share * 0.5F : share;
	}

	private static void add(Minecraft minecraft, ParticleOptions options, double x, double y, double z, Vec3 velocity, float @Nullable [] tint, int life) {
		Particle particle = minecraft.particleEngine.createParticle(options, x, y, z, velocity.x, velocity.y, velocity.z);
		if (particle == null) {
			return;
		}
		particle.setParticleSpeed(velocity.x, velocity.y, velocity.z);
		if (life > 0) {
			particle.setLifetime(life);
		}
		if (tint != null && particle instanceof SingleQuadParticle quad) {
			float shade = 0.9F + minecraft.level.getRandom().nextFloat() * 0.2F;
			quad.setColor(tint[0] * shade, tint[1] * shade, tint[2] * shade);
		}
	}
}
