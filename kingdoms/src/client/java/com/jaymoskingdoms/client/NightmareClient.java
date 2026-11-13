package com.jaymoskingdoms.client;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.LivingNightmare;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The phantoms of a living nightmare ({@link LivingNightmare}), seen by the dreamer and by the ki attuned near them. The
 * server only says how many, over how long, whose and from which seed ({@link LivingNightmare.Visions}); each is a phantom
 * made on this client and never known to the server, and every client that sees one nightmare draws the same phantoms from
 * the seed. It comes out of the dark some way off and above the dreamer, circles closer, then dives straight at their face
 * and is gone the instant before it strikes. Your own: a bite in your ears, a jolt, a flash of dark, and through a waking
 * nightmare the edges of the screen breathe a cold, dark blue. Another's, watched: only the bite, at them. Struck at, a
 * vision is gone at once.
 */
public final class NightmareClient {
	// ================================================================================================================
	// Tuning
	/** Where a vision first shows: this far off (and up to this much more), this high above your eyes (and up to this much more). */
	static final double SPAWN_FAR = 14.0, SPAWN_MORE = 8.0, SPAWN_UP = 5.0, SPAWN_UP_MORE = 5.0;
	/** Circling: this long at the least (ticks) and up to this much more; the circle tightens to this far, at this speed (blocks a tick). */
	static final int CIRCLE_TICKS = 50, CIRCLE_MORE = 70;
	static final double CIRCLE_NEAR = 7.0, CIRCLE_SPEED = 0.45;
	/** The dive: speed (blocks a tick), and how near your eyes it vanishes. */
	static final double DIVE_SPEED = 0.95, VANISH = 1.6;
	/** The longest a vision lasts (ticks), in case it never reaches you. */
	static final int LONGEST = 20 * 20;
	/** The jolt: how far the view kicks (degrees); the dark flash's length (ticks). */
	static final float JOLT = 4.0F;
	static final int FLASH_TICKS = 6;
	/** The waking nightmare's edges, and how they breathe. */
	static final float EDGE = 0.35F, EDGE_BREATH = 0.15F, BREATH_TICKS = 50.0F;
	/** Visions' entity ids, counting down from here so they never meet the server's. */
	static final int FIRST_ID = -1_000_000;
	// ================================================================================================================

	private static final Identifier VIGNETTE = Identifier.withDefaultNamespace("textures/misc/vignette.png");

	private static final class Vision {
		final Phantom phantom;
		/** Whose nightmare (entity id), and whether it is this player's own. */
		final int dreamer;
		final boolean own;
		final double angle0;
		final int circleFor;
		int age;
		boolean diving;
		Vec3 dive = Vec3.ZERO;

		Vision(Phantom phantom, int dreamer, boolean own, double angle0, int circleFor) {
			this.phantom = phantom;
			this.dreamer = dreamer;
			this.own = own;
			this.angle0 = angle0;
			this.circleFor = circleFor;
		}
	}

	/** A vision still to come: in how many ticks, whose, and where and how it comes (drawn from the nightmare's seed). */
	private static final class Coming {
		int left;
		final int dreamer, size, circleFor;
		final double angle, far, up;

		Coming(int left, int dreamer, RandomSource random) {
			this.left = left;
			this.dreamer = dreamer;
			this.angle = random.nextDouble() * Math.PI * 2.0;
			this.far = SPAWN_FAR + random.nextDouble() * SPAWN_MORE;
			this.up = SPAWN_UP + random.nextDouble() * SPAWN_UP_MORE;
			this.size = random.nextInt(3);
			this.circleFor = CIRCLE_TICKS + random.nextInt(CIRCLE_MORE);
		}
	}

	private static final List<Vision> VISIONS = new ArrayList<>();
	private static final List<Coming> COMING = new ArrayList<>();
	/** Ticks of the waking nightmare left, and the dark flash. */
	private static int waking, flash;
	private static int nextId = FIRST_ID;

	private NightmareClient() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(LivingNightmare.Visions.TYPE, (payload, context) -> begin(payload));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
		ClientTickEvents.END_CLIENT_TICK.register(NightmareClient::tick);
		HudElementRegistry.attachElementBefore(VanillaHudElements.MISC_OVERLAYS, JaymosKingdoms.id("nightmare"), NightmareClient::edges);
		// struck at, a vision is gone at once (and nothing is sent: there's nothing there)
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (world.isClientSide() && isVision(entity)) {
				for (Vision v : new ArrayList<>(VISIONS)) {
					if (v.phantom == entity) {
						vanish(v, false);
					}
				}
				return InteractionResult.FAIL;
			}
			return InteractionResult.PASS;
		});
	}

	/** Whether {@code e} is one of this client's visions. */
	public static boolean isVision(@Nullable Entity e) {
		return e instanceof Phantom && e.getId() <= FIRST_ID;
	}

	private static void begin(LivingNightmare.Visions payload) {
		int count = Mth.clamp(payload.count(), 1, 8);
		int ticks = Math.max(20, payload.ticks());
		RandomSource random = RandomSource.create(payload.seed());
		for (int i = 0; i < count; i++) {
			// spread over the time, the first soon
			int at = i * ticks / (count + 1) + random.nextInt(Math.max(1, ticks / (count + 1)));
			COMING.add(new Coming(i == 0 ? 20 + random.nextInt(40) : at, payload.dreamer(), random));
		}
		if (payload.waking()) {
			waking = Math.max(waking, ticks);
		}
	}

	private static void clear() {
		Minecraft minecraft = Minecraft.getInstance();
		for (Vision v : VISIONS) {
			if (minecraft.level != null) {
				minecraft.level.removeEntity(v.phantom.getId(), Entity.RemovalReason.DISCARDED);
			}
		}
		VISIONS.clear();
		COMING.clear();
		waking = flash = 0;
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer self = minecraft.player;
		ClientLevel level = minecraft.level;
		if (self == null || level == null) {
			if (!VISIONS.isEmpty() || !COMING.isEmpty()) {
				VISIONS.clear();
				COMING.clear();
			}
			waking = flash = 0;
			return;
		}
		if (minecraft.isPaused()) {
			return;
		}
		if (waking > 0) {
			waking--;
		}
		if (flash > 0) {
			flash--;
		}
		for (int i = COMING.size() - 1; i >= 0; i--) {
			Coming c = COMING.get(i);
			if (--c.left <= 0) {
				COMING.remove(i);
				if (level.getEntity(c.dreamer) instanceof LivingEntity dreamer && dreamer.isAlive()) {
					appear(level, dreamer, dreamer == self, c);
				}
			}
		}
		for (Vision v : new ArrayList<>(VISIONS)) {
			if (v.phantom.level() != level || !(level.getEntity(v.dreamer) instanceof LivingEntity dreamer) || !dreamer.isAlive()) {
				vanish(v, false);
				continue;
			}
			fly(v, dreamer);
		}
	}

	/** A vision comes out of the dark, some way off and above the dreamer. */
	private static void appear(ClientLevel level, LivingEntity dreamer, boolean own, Coming c) {
		RandomSource random = level.getRandom();
		Phantom phantom = new Phantom(EntityTypes.PHANTOM, level);
		phantom.setId(nextId--);
		phantom.noPhysics = true;
		phantom.setNoGravity(true);
		phantom.setPhantomSize(c.size);
		Vec3 eye = dreamer.getEyePosition();
		Vec3 at = eye.add(Math.cos(c.angle) * c.far, c.up, Math.sin(c.angle) * c.far);
		phantom.setPos(at.x, at.y, at.z);
		phantom.setOldPosAndRot();
		level.addEntity(phantom);
		VISIONS.add(new Vision(phantom, dreamer.getId(), own, c.angle, c.circleFor));
		for (int i = 0; i < 6; i++) {
			level.addParticle(ParticleTypes.LARGE_SMOKE, at.x + random.nextGaussian() * 0.4, at.y + random.nextGaussian() * 0.3, at.z + random.nextGaussian() * 0.4,
				0.0, 0.02, 0.0);
		}
		level.playLocalSound(at.x, at.y, at.z, SoundEvents.PHANTOM_AMBIENT, SoundSource.HOSTILE, 1.0F, 0.8F + random.nextFloat() * 0.2F, false);
	}

	/** One tick of a vision's flight: circling closer, then the dive at the dreamer's face. */
	private static void fly(Vision v, LivingEntity self) {
		Phantom p = v.phantom;
		v.age++;
		Vec3 eye = self.getEyePosition();
		Vec3 pos = p.position();
		Vec3 next;
		if (!v.diving) {
			double t = v.age / (double)v.circleFor;
			double radius = Mth.lerp(Math.min(1.0, t), Math.sqrt(Math.pow(pos.x - eye.x, 2) + Math.pow(pos.z - eye.z, 2)), CIRCLE_NEAR);
			double angle = v.angle0 + v.age * CIRCLE_SPEED / Math.max(4.0, radius);
			double height = Mth.lerp(Math.min(1.0, t), pos.y - eye.y, 3.0);
			Vec3 want = eye.add(Math.cos(angle) * radius, height, Math.sin(angle) * radius);
			Vec3 step = want.subtract(pos);
			next = pos.add(step.length() > CIRCLE_SPEED * 2.0 ? step.normalize().scale(CIRCLE_SPEED * 2.0) : step);
			if (v.age >= v.circleFor) {
				v.diving = true;
				self.level().playLocalSound(pos.x, pos.y, pos.z, SoundEvents.PHANTOM_SWOOP, SoundSource.HOSTILE, 1.2F, 0.9F + self.getRandom().nextFloat() * 0.2F, false);
			}
		} else {
			// it keeps on your eyes as you move
			v.dive = eye.subtract(pos).normalize().scale(DIVE_SPEED);
			next = pos.add(v.dive);
			if (next.distanceTo(eye) <= VANISH || pos.distanceTo(eye) <= VANISH) {
				p.setPos(next.x, next.y, next.z);
				vanish(v, true);
				return;
			}
		}
		Vec3 heading = next.subtract(pos);
		p.setPos(next.x, next.y, next.z);
		if (heading.lengthSqr() > 1.0E-6) {
			float yaw = (float)(Mth.atan2(heading.z, heading.x) * Mth.RAD_TO_DEG) - 90.0F;
			float pitch = (float)(-(Mth.atan2(heading.y, Math.sqrt(heading.x * heading.x + heading.z * heading.z)) * Mth.RAD_TO_DEG));
			p.setYRot(yaw);
			p.setXRot(pitch);
			p.yBodyRot = yaw;
			p.setYHeadRot(yaw);
		}
		p.clearFire();
		if (v.age > LONGEST) {
			vanish(v, false);
		}
	}

	/** A vision is gone: at your face (a bite in your ears, a jolt, a dark flash), or simply into smoke. */
	private static void vanish(Vision v, boolean atYou) {
		VISIONS.remove(v);
		Phantom p = v.phantom;
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level != null) {
			RandomSource random = level.getRandom();
			for (int i = 0; i < 10; i++) {
				level.addParticle(ParticleTypes.LARGE_SMOKE, p.getX() + random.nextGaussian() * 0.3, p.getY() + random.nextGaussian() * 0.3,
					p.getZ() + random.nextGaussian() * 0.3, 0.0, 0.03, 0.0);
			}
			if (atYou && v.own && minecraft.player != null) {
				LocalPlayer self = minecraft.player;
				level.playLocalSound(self.getX(), self.getEyeY(), self.getZ(), SoundEvents.PHANTOM_BITE, SoundSource.HOSTILE, 1.2F, 0.8F, false);
				level.playLocalSound(self.getX(), self.getEyeY(), self.getZ(), SoundEvents.PLAYER_BREATH, SoundSource.PLAYERS, 0.8F, 1.4F, false);
				self.setXRot(Mth.clamp(self.getXRot() + (random.nextFloat() - 0.6F) * JOLT * 2.0F, -90.0F, 90.0F));
				self.setYRot(self.getYRot() + (random.nextFloat() - 0.5F) * JOLT * 2.0F);
				flash = FLASH_TICKS;
			} else if (atYou) {
				// another's nightmare reaching them: only the bite, at them
				level.playLocalSound(p.getX(), p.getY(), p.getZ(), SoundEvents.PHANTOM_BITE, SoundSource.HOSTILE, 0.9F, 0.8F, false);
			} else {
				level.playLocalSound(p.getX(), p.getY(), p.getZ(), SoundEvents.PHANTOM_HURT, SoundSource.HOSTILE, 0.7F, 1.4F, false);
			}
			level.removeEntity(p.getId(), Entity.RemovalReason.DISCARDED);
		}
	}

	/** The waking nightmare's cold dark edges, and the dark flash of a vision at your face. */
	private static void edges(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null || waking <= 0 && flash <= 0) {
			return;
		}
		float partialTicks = delta.getGameTimeDeltaPartialTick(false);
		if (waking > 0) {
			float t = ((minecraft.level.getGameTime() % 10000L) + partialTicks) / BREATH_TICKS;
			float fade = Math.min(1.0F, waking / 40.0F);
			float e = (EDGE + EDGE_BREATH * (0.5F + 0.5F * Mth.sin(t * Mth.TWO_PI))) * fade;
			// the vignette takes away as much of each colour as given: less of the blue, so what's left is a cold dark blue
			graphics.blit(RenderPipelines.VIGNETTE, VIGNETTE, 0, 0, 0.0F, 0.0F, graphics.guiWidth(), graphics.guiHeight(), graphics.guiWidth(),
				graphics.guiHeight(), ARGB.colorFromFloat(1.0F, e, e, e * 0.55F));
		}
		if (flash > 0) {
			float k = (flash - partialTicks) / FLASH_TICKS;
			int alpha = (int)(Mth.clamp(k, 0.0F, 1.0F) * 170.0F);
			graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), ARGB.color(alpha, 5, 5, 12));
		}
	}
}
