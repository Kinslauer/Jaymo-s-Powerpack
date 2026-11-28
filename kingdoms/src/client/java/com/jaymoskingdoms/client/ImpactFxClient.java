package com.jaymoskingdoms.client;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.sailing.ImpactFx;
import com.jaymoskingdoms.sailing.ImpactFx.Look;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The client's half of {@link ImpactFx}: each hit a gun or a mangonel lands is drawn here, from one small packet, so a
 * barrage costs the server nothing extra. Everything is made of vanilla particles, sized by the hit's {@link Look}:
 * <ul>
 * <li><b>Plume</b>: smoke that keeps rising from the hit for a while, mostly white with grey and black streaks; tall for
 * a shell or a mortar shell, a short puff for round and heavy shot ({@link Plume}).</li>
 * <li><b>Fireball</b>: a burst of flame and sparks at the heart of a shell's blast, in place of vanilla's grey puff.</li>
 * <li><b>Dust</b>: a low cloud rolling out along the ground from the hit, in the colour of the block it struck.</li>
 * <li><b>Debris</b>: chunks of the struck block thrown out on arcs, trailing a little smoke, bouncing as they land
 * ({@link Debris}). They are particles, not blocks: nothing to pick up, nothing left on the ground. A mangonel stone
 * breaks into rubble that rolls.</li>
 * <li><b>Haze</b>: a hit on dirt, sand or gravel close by puts a soft brown haze over your eyes for about a second,
 * fading out (a smooth wash, no pixels).</li>
 * </ul>
 * The game's particle setting (All, Decreased, Minimal) scales all of it, and {@link #MAX_DEBRIS} and {@link #MAX_PLUMES}
 * cap what is in the air at once, so a whole siege battery firing stays cheap.
 */
public final class ImpactFxClient {
	/** The most debris chunks and rising plumes in flight at once, over every hit. */
	static final int MAX_DEBRIS = 120, MAX_PLUMES = 16;
	/** How far (blocks) the dust haze reaches you, and how thick it is right at the hit (0-1). */
	static final double HAZE_REACH = 8.0;
	/** Past this (blocks) a hit's dust and chunks aren't drawn: too small to see, only the plume and fireball are. */
	static final double GROUND_REACH = 64.0;
	static final float HAZE_PEAK = 0.55F;
	/** How fast the haze clears each tick (it is gone in about a second from its thickest). */
	static final float HAZE_FADE = 0.03F;
	/** Gravity and drag on a debris chunk each tick, and how much of its speed a bounce keeps. */
	static final double DEBRIS_GRAVITY = 0.045, DEBRIS_DRAG = 0.98, BOUNCE_UP = 0.35, BOUNCE_ALONG = 0.6, ROLL_ALONG = 0.85;
	/** The most ticks a chunk flies. */
	static final int DEBRIS_LIFE = 45;
	/** The haze's colour (dusty brown). */
	static final int HAZE_RGB = 0x7A6244;
	/** The dust's colour off a body or the air (a pale grey-brown). */
	static final int DUST_RGB = 0xB8AE9E;

	private static final List<Plume> PLUMES = new ArrayList<>();
	private static final List<Debris> DEBRIS = new ArrayList<>();
	private static final RandomSource RANDOM = RandomSource.create();
	private static float haze, hazeBefore;

	private ImpactFxClient() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(ImpactFx.Hit.TYPE, (hit, context) -> start(hit));
		ClientTickEvents.END_CLIENT_TICK.register(client -> tick(client));
		HudElementRegistry.attachElementBefore(VanillaHudElements.CROSSHAIR, JaymosKingdoms.id("impact_haze"), ImpactFxClient::drawHaze);
	}

	/** A rising column of smoke: it puffs out {@code rate} particles a tick for {@code ticks} more ticks, climbing as it goes. */
	private static final class Plume {
		final Vec3 at;
		final double spread, climb;
		final int length;
		int ticks;
		final float rate;

		Plume(Vec3 at, int ticks, float rate, double spread, double climb) {
			this.at = at;
			this.length = ticks;
			this.ticks = ticks;
			this.rate = rate;
			this.spread = spread;
			this.climb = climb;
		}
	}

	/** A thrown chunk of the ground. */
	private static final class Debris {
		double x, y, z, dx, dy, dz;
		final BlockParticleOption look;
		final boolean rolls;
		int life, bounces;

		Debris(Vec3 at, Vec3 velocity, BlockState state, boolean rolls) {
			this.x = at.x;
			this.y = at.y;
			this.z = at.z;
			this.dx = velocity.x;
			this.dy = velocity.y;
			this.dz = velocity.z;
			this.look = new BlockParticleOption(ParticleTypes.BLOCK, state);
			this.rolls = rolls;
		}
	}

	/** How much of everything to draw: the game's particle setting. */
	private static float share(Minecraft minecraft) {
		ParticleStatus status = minecraft.options.particles().get();
		return status == ParticleStatus.ALL ? 1.0F : status == ParticleStatus.DECREASED ? 0.5F : 0.2F;
	}

	private static int count(int full, float share) {
		return share <= 0.0F ? 0 : Math.max(full > 0 ? 1 : 0, Math.round(full * share));
	}

	private static void start(ImpactFx.Hit hit) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null || hit.look() < 0 || hit.look() >= Look.values().length) {
			return;
		}
		Look look = Look.values()[hit.look()];
		Vec3 at = new Vec3(hit.x(), hit.y(), hit.z());
		Vec3 eye = minecraft.gameRenderer.mainCamera().position();
		double away = eye.distanceTo(at);
		// far off, only what shows from far off: the plume and the fireball, not the dust and chunks on the ground
		float share = share(minecraft), near = away > GROUND_REACH ? 0.0F : share;
		@Nullable BlockState ground = hit.ground() == 0 ? null : Block.stateById(hit.ground());
		if (ground != null && ground.isAir()) {
			ground = null;
		}
		double size = Mth.clamp(hit.size(), 0.5F, 10.0F);
		switch (look) {
			case SHELL, MORTAR -> {
				fireball(level, at, look == Look.SHELL ? 1.0 : 0.85, share);
				plume(at, look == Look.SHELL ? 30 : 24, 3.0F * share, size * 0.35, look == Look.SHELL ? 0.16 : 0.13);
				dust(level, at, ground, look == Look.SHELL ? 30 : 24, size, near);
				debris(at, ground, look == Look.SHELL ? 16 : 13, look == Look.SHELL ? 1.0 : 0.85, near, false);
			}
			case HEAVY -> {
				puff(level, at, 8, share);
				plume(at, 9, 2.0F * share, 0.6, 0.08);
				dust(level, at, ground, 18, size, near);
				debris(at, ground, 10, 0.7, near, false);
			}
			case ROUND -> {
				puff(level, at, 6, share);
				plume(at, 6, 1.5F * share, 0.4, 0.07);
				dust(level, at, ground, 12, size, near);
				debris(at, ground, 7, 0.55, near, false);
			}
			case CHAIN -> {
				spray(level, ParticleTypes.CRIT, at, count(10, near), 0.4, 0.2);
				dust(level, at, ground, 6, 1.5, near);
				debris(at, ground, 3, 0.4, near, false);
			}
			case GRAPE -> {
				spray(level, ParticleTypes.CRIT, at, count(30, near), 1.6, 0.4);
				spray(level, ParticleTypes.SMOKE, at, count(14, near), 1.2, 0.05);
				dust(level, at, ground, 8, 2.0, near);
				debris(at, ground, 4, 0.4, near, false);
			}
			case STONE -> {
				BlockState stone = Blocks.COBBLESTONE.defaultBlockState();
				puff(level, at, 4, share);
				plume(at, 5, 1.0F * share, 0.5, 0.05);
				// a ring of stone dust, and the ground's own
				dust(level, at, stone, 14, size, near);
				dust(level, at, ground, 12, size, near);
				// the stone breaks into rubble that rolls, and throws a little of the ground
				debris(at, stone, 12, 0.5, near, true);
				debris(at, ground, 5, 0.55, near, false);
			}
			case FIRE -> {
				fireball(level, at, 0.45, share);
				plume(at, 6, 1.2F * share, 0.4, 0.06);
			}
		}
		if (ground != null && dusty(ground)) {
			double d = away;
			if (d < HAZE_REACH) {
				float weight = switch (look) {
					case SHELL, MORTAR, STONE -> 1.0F;
					case HEAVY -> 0.8F;
					case ROUND -> 0.6F;
					default -> 0.35F;
				};
				haze = Math.max(haze, HAZE_PEAK * weight * (float) (1.0 - d / HAZE_REACH));
			}
		}
	}

	/** Loose ground that throws dust into the eyes: dirt, sand, gravel, and the like. */
	private static boolean dusty(BlockState state) {
		return state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(Blocks.GRAVEL) || state.is(Blocks.SUSPICIOUS_GRAVEL)
			|| state.is(Blocks.FARMLAND) || state.is(Blocks.DIRT_PATH) || state.is(Blocks.SOUL_SAND) || state.is(Blocks.SOUL_SOIL);
	}

	/** A shell's fireball: a tight knot of flame thrown out fast, then slowing, with sparks flying off it. */
	private static void fireball(ClientLevel level, Vec3 at, double scale, float share) {
		int flames = count((int) Math.round(34 * scale), share);
		for (int i = 0; i < flames; i++) {
			Vec3 dir = randomDir(0.6);
			double speed = (0.12 + RANDOM.nextDouble() * 0.22) * scale;
			level.addAlwaysVisibleParticle(ParticleTypes.FLAME, true, at.x + dir.x * 0.4, at.y + 0.3 + dir.y * 0.4, at.z + dir.z * 0.4,
				dir.x * speed, dir.y * speed + 0.04, dir.z * speed);
		}
		int sparks = count((int) Math.round(10 * scale), share);
		for (int i = 0; i < sparks; i++) {
			level.addParticle(ParticleTypes.LAVA, at.x, at.y + 0.3, at.z, 0.0, 0.0, 0.0);
		}
		level.addAlwaysVisibleParticle(ParticleTypes.EXPLOSION, true, at.x, at.y + 0.5, at.z, 0.0, 0.0, 0.0);
	}

	/** A quick grey puff where a solid shot strikes. */
	private static void puff(ClientLevel level, Vec3 at, int full, float share) {
		level.addParticle(ParticleTypes.EXPLOSION, at.x, at.y + 0.3, at.z, 0.0, 0.0, 0.0);
		spray(level, ParticleTypes.LARGE_SMOKE, at, count(full, share), 0.4, 0.03);
	}

	/** {@code n} of {@code particle} scattered within {@code spread} of {@code at}, each moving at up to {@code speed}. */
	private static void spray(ClientLevel level, ParticleOptions particle, Vec3 at, int n, double spread, double speed) {
		for (int i = 0; i < n; i++) {
			level.addParticle(particle, at.x + RANDOM.triangle(0.0, spread), at.y + RANDOM.triangle(0.0, spread * 0.5), at.z + RANDOM.triangle(0.0, spread),
				RANDOM.triangle(0.0, speed), RANDOM.triangle(0.0, speed) + speed * 0.3, RANDOM.triangle(0.0, speed));
		}
	}

	private static void plume(Vec3 at, int ticks, float rate, double spread, double climb) {
		if (PLUMES.size() < MAX_PLUMES && rate > 0.0F) {
			PLUMES.add(new Plume(at, ticks, rate, spread, climb));
		}
	}

	/**
	 * The dust cloud: soft smoke rolling out low along the ground from the hit, tinted the colour of the block it struck
	 * (its map colour), with a few specks of the block itself; wider for a bigger burst. Pale grey off a body or the air.
	 */
	private static void dust(ClientLevel level, Vec3 at, @Nullable BlockState ground, int full, double size, float share) {
		int n = count(full, share);
		int rgb = DUST_RGB;
		if (ground != null) {
			int col = ground.getMapColor(level, BlockPos.containing(at)).col;
			if (col != 0) {
				// a little paler than the block: dust in the air
				rgb = pale(col, 0.25F);
			}
		}
		float r = ARGB.red(rgb) / 255.0F, g = ARGB.green(rgb) / 255.0F, b = ARGB.blue(rgb) / 255.0F;
		double reach = 0.14 + size * 0.035;
		Minecraft minecraft = Minecraft.getInstance();
		for (int i = 0; i < n; i++) {
			double angle = RANDOM.nextDouble() * Mth.TWO_PI;
			double out = reach * (0.5 + RANDOM.nextDouble() * 0.7);
			double cx = Math.cos(angle), cz = Math.sin(angle);
			Particle puff = minecraft.particleEngine.createParticle(ParticleTypes.WHITE_SMOKE, at.x + cx * 0.4, at.y + 0.15, at.z + cz * 0.4, 0.0, 0.0, 0.0);
			if (puff == null) {
				continue;
			}
			puff.setParticleSpeed(cx * out, 0.01 + RANDOM.nextDouble() * 0.03, cz * out);
			puff.scale(1.6F + RANDOM.nextFloat() * 1.4F);
			puff.setLifetime(28 + RANDOM.nextInt(24));
			if (puff instanceof SingleQuadParticle quad) {
				float shade = 0.9F + RANDOM.nextFloat() * 0.15F;
				quad.setColor(Math.min(1.0F, r * shade), Math.min(1.0F, g * shade), Math.min(1.0F, b * shade));
			}
		}
		if (ground != null) {
			BlockParticleOption specks = new BlockParticleOption(ParticleTypes.BLOCK, ground);
			for (int i = 0, m = Math.max(1, n / 2); i < m; i++) {
				double angle = RANDOM.nextDouble() * Mth.TWO_PI;
				level.addParticle(specks, at.x + Math.cos(angle) * 0.5, at.y + 0.2, at.z + Math.sin(angle) * 0.5,
					Math.cos(angle) * reach, 0.15 + RANDOM.nextDouble() * 0.15, Math.sin(angle) * reach);
			}
		}
	}

	/** Chunks of {@code ground} thrown out from the hit; {@code rolls}: a broken stone's rubble, thrown low, rolling on. */
	private static void debris(Vec3 at, @Nullable BlockState ground, int full, double force, float share, boolean rolls) {
		if (ground == null || ground.getRenderShape() == net.minecraft.world.level.block.RenderShape.INVISIBLE) {
			return;
		}
		int n = count(full, share);
		for (int i = 0; i < n && DEBRIS.size() < MAX_DEBRIS; i++) {
			double angle = RANDOM.nextDouble() * Mth.TWO_PI;
			double out = (rolls ? 0.18 + RANDOM.nextDouble() * 0.2 : 0.15 + RANDOM.nextDouble() * 0.3) * force;
			double up = (rolls ? 0.12 + RANDOM.nextDouble() * 0.15 : 0.35 + RANDOM.nextDouble() * 0.4) * force;
			Vec3 velocity = new Vec3(Math.cos(angle) * out, up, Math.sin(angle) * out);
			DEBRIS.add(new Debris(at.add(0.0, 0.3, 0.0), velocity, ground, rolls));
		}
	}

	/** {@code rgb} moved {@code by} (0-1) of the way to white. */
	private static int pale(int rgb, float by) {
		int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
		return Math.round(r + (255 - r) * by) << 16 | Math.round(g + (255 - g) * by) << 8 | Math.round(b + (255 - b) * by);
	}

	private static Vec3 randomDir(double upBias) {
		Vec3 v = new Vec3(RANDOM.nextGaussian(), Math.abs(RANDOM.nextGaussian()) * upBias + RANDOM.nextGaussian() * (1.0 - upBias), RANDOM.nextGaussian());
		double length = v.length();
		return length < 1.0E-4 ? new Vec3(0.0, 1.0, 0.0) : v.scale(1.0 / length);
	}

	private static void tick(Minecraft minecraft) {
		hazeBefore = haze;
		haze = Math.max(0.0F, haze - HAZE_FADE);
		ClientLevel level = minecraft.level;
		if (level == null) {
			PLUMES.clear();
			DEBRIS.clear();
			haze = hazeBefore = 0.0F;
			return;
		}
		if (minecraft.isPaused()) {
			return;
		}
		for (Iterator<Plume> it = PLUMES.iterator(); it.hasNext(); ) {
			Plume p = it.next();
			if (--p.ticks < 0) {
				it.remove();
				continue;
			}
			// it climbs from the ground and widens as it goes
			int age = p.length - p.ticks;
			double rise = age * p.climb * 2.5;
			double width = p.spread * (1.0 + age * 0.06);
			int n = (int) p.rate + (RANDOM.nextFloat() < p.rate - (int) p.rate ? 1 : 0);
			for (int i = 0; i < n; i++) {
				double x = p.at.x + RANDOM.triangle(0.0, width), y = p.at.y + 0.3 + rise * RANDOM.nextDouble(), z = p.at.z + RANDOM.triangle(0.0, width);
				float pick = RANDOM.nextFloat();
				// mostly white, now and then a grey or a black streak
				ParticleOptions smoke = pick < 0.62F ? ParticleTypes.CAMPFIRE_COSY_SMOKE : pick < 0.86F ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SMOKE;
				double up = smoke == ParticleTypes.CAMPFIRE_COSY_SMOKE ? p.climb * 0.5 : p.climb;
				level.addAlwaysVisibleParticle(smoke, true, x, y, z, RANDOM.triangle(0.0, 0.02), up, RANDOM.triangle(0.0, 0.02));
			}
		}
		for (Iterator<Debris> it = DEBRIS.iterator(); it.hasNext(); ) {
			Debris d = it.next();
			if (++d.life > DEBRIS_LIFE) {
				it.remove();
				continue;
			}
			d.dy -= DEBRIS_GRAVITY;
			d.dx *= DEBRIS_DRAG;
			d.dy *= DEBRIS_DRAG;
			d.dz *= DEBRIS_DRAG;
			double nx = d.x + d.dx, ny = d.y + d.dy, nz = d.z + d.dz;
			BlockPos below = BlockPos.containing(nx, ny, nz);
			if (!level.getBlockState(below).getCollisionShape(level, below).isEmpty()) {
				// it lands: it bounces up a little and skids (rubble rolls on)
				double along = d.rolls ? ROLL_ALONG : BOUNCE_ALONG;
				d.dx *= along;
				d.dz *= along;
				d.dy = d.dy < 0.0 ? -d.dy * BOUNCE_UP : 0.0;
				if (++d.bounces > (d.rolls ? 5 : 2) || Math.abs(d.dy) < 0.02 && Math.hypot(d.dx, d.dz) < 0.02) {
					level.addParticle(d.look, d.x, d.y, d.z, 0.0, 0.0, 0.0);
					it.remove();
					continue;
				}
				// a stone wall: it stops short, a bit of it crumbling off
				BlockPos ahead = BlockPos.containing(nx, d.y, nz);
				if (!level.getBlockState(ahead).getCollisionShape(level, ahead).isEmpty()) {
					d.dx *= -0.3;
					d.dz *= -0.3;
				}
				nx = d.x + d.dx;
				ny = d.y;
				nz = d.z + d.dz;
			}
			d.x = nx;
			d.y = ny;
			d.z = nz;
			level.addParticle(d.look, d.x, d.y, d.z, 0.0, 0.0, 0.0);
			if (d.bounces == 0 && !d.rolls && (d.life & 1) == 0) {
				// a faint trail of smoke behind it while it's in the air
				level.addParticle(ParticleTypes.SMOKE, d.x, d.y, d.z, 0.0, 0.01, 0.0);
			}
		}
	}

	private static void drawHaze(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		float a = Mth.lerp(delta.getGameTimeDeltaPartialTick(false), hazeBefore, haze);
		if (a <= 0.005F) {
			return;
		}
		graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), ARGB.color(Math.round(a * 255.0F), HAZE_RGB));
	}
}
