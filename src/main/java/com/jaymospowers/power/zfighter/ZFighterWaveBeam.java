package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.BeamClash;
import com.jaymospowers.core.Powers;
import com.jaymospowers.core.Soundscape;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A fired Ki Wave (every lineage's signature, and the Great Ape's mouth beam) as a travelling beam: let go, its head
 * shoots out from the hands at the signature's speed, drawn from the hands to the head all the way. What the head
 * reaches is hurt, pushed and chilled once; where it stops (its reach, a block unless it pierces, or a clash) it bursts.
 * The fighter holds the firing pose, slowed, while it's out. On its way it's a {@link BeamClash} beam: it locks with
 * Wind's and Smoke's streams and any other fighter's beam, V pushes it, and its charge and the ki behind it are its
 * strength (ki drains while it clashes).
 */
public final class ZFighterWaveBeam {
	/**
	 * Strength in a clash, times the steady push: by charge (none to full), and this share of the fighter's ki power on
	 * top. (0.17.1) Andy: "factor the player's power levels in ... allow transformations mid battle to count": the ki
	 * power is read live, every tick of the clash, so going Super while it grinds pushes harder at once, and all of it
	 * counts now (was 0.3, fixed when the wave left his hands).
	 */
	static final float CLASH_CHARGE_EMPTY = 0.6F, CLASH_CHARGE_FULL = 1.2F, CLASH_KI_POWER = 1.0F;
	/** And by the ki left behind it (empty to full); narrower than before (0.7-1.2) so a form's ki cost doesn't undo its power. */
	static final float CLASH_KI_EMPTY = 0.85F, CLASH_KI_FULL = 1.1F;
	/** Ki a tick while the beam clashes (not in creative or Rage). */
	static final float CLASH_KI_DRAIN = 0.3F;
	/** The Great Ape's beam has no ki behind it: the moon's strength. */
	static final float APE_CLASH_STRENGTH = 1.3F;
	/** How far apart two clashing beams may be pulled before they part (blocks; BeamClash adds its slack). */
	static final double CLASH_RANGE = 56.0;
	/** A beam stuck this long past its flight (and a full clash) ends anyway. */
	static final int LIFE_SLACK = 40;
	/** The head hums every this many ticks. */
	static final int HUM_EVERY = 6;
	/** The Spiral Lance's coil and the wave's crackle are drawn every this many ticks, this finely (blocks). */
	static final int SPARK_EVERY = 2;
	static final double COIL_STEP = 0.45, CRACKLE_STEP = 0.6;
	/** A beam stopped by a clash bursts with this share of its blast (the clash itself does the hurting). */
	static final float CLASH_BURST = 0.6F;

	/** He's holding the firing pose, for everyone's client. Not saved. */
	public static final AttachmentType<Boolean> FIRING = AttachmentRegistry.create(JaymosPowers.id("zfighter_wave_firing"),
		builder -> builder.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));

	/**
	 * What a fired beam is: its reach and speed (blocks, blocks a tick), how thick, the hurt and push of what it reaches,
	 * how long it chills (ticks), whether it bores through blocks, its burst (power, how it treats the ground, whether
	 * the burst only shakes the ground), its colour and style, its strength in a clash (by charge; his ki power and
	 * the ki left are added live), whether it's the Great Ape's, and its hum's pitch.
	 */
	record Spec(double range, double speed, double radius, float damage, double push, int chill, boolean pierces, float blast,
		Level.ExplosionInteraction interaction, boolean groundOnly, int color, int style, float strength, boolean ape, float pitch) {
	}

	private static final class Wave {
		final ServerLevel level;
		final UUID owner;
		final int id;
		final Vec3 start;
		final Vec3 dir;
		final Spec spec;
		final int maxAge;
		final Set<Integer> struck = new HashSet<>();
		double length;
		int age;

		Wave(ServerLevel level, UUID owner, int id, Vec3 start, Vec3 dir, Spec spec) {
			this.level = level;
			this.owner = owner;
			this.id = id;
			this.start = start;
			this.dir = dir;
			this.spec = spec;
			this.maxAge = (int)Math.ceil(spec.range() / spec.speed()) + BeamClash.MAX_TICKS + LIFE_SLACK;
		}

		Vec3 head() {
			return this.start.add(this.dir.scale(this.length));
		}
	}

	private static final Map<UUID, Wave> WAVES = new HashMap<>();

	private ZFighterWaveBeam() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(ZFighterWaveBeam::tick);
		BeamClash.register(new BeamClash.Beam(p -> BeamClash.KIND_KI, p -> WAVES.containsKey(p.getUUID()), CLASH_RANGE, ZFighterWaveBeam::stop,
			new BeamClash.Shape() {
				@Override
				public Vec3 from(ServerPlayer player) {
					Wave wave = WAVES.get(player.getUUID());
					return wave == null ? player.getEyePosition() : wave.start;
				}

				@Override
				public Vec3 dir(ServerPlayer player) {
					Wave wave = WAVES.get(player.getUUID());
					return wave == null ? player.getLookAngle() : wave.dir;
				}

				@Override
				public double reach(ServerPlayer player) {
					Wave wave = WAVES.get(player.getUUID());
					return wave == null ? 0.0 : wave.length;
				}

				@Override
				public float strength(ServerPlayer player) {
					return ZFighterWaveBeam.strength(player);
				}

				@Override
				public String pushKey() {
					return "key.jaymospowers.ability_3";
				}
			}));
	}

	static void clear() {
		WAVES.clear();
	}

	/** Whether his beam is out (both sides: the pose, the slow, V held back). */
	public static boolean out(Player player) {
		return Boolean.TRUE.equals(player.getAttached(FIRING));
	}

	/** A wave's strength in a clash from how full it was (his ki power is added live, {@link #strength(ServerPlayer)}). */
	static float strength(float charge) {
		return Mth.lerp(charge, CLASH_CHARGE_EMPTY, CLASH_CHARGE_FULL);
	}

	/** Lets the beam go from {@code start} along {@code dir}. */
	static void fire(ServerPlayer player, Vec3 start, Vec3 dir, Spec spec) {
		Wave old = WAVES.remove(player.getUUID());
		if (old != null) {
			burst(player, old, old.head(), false);
		}
		WAVES.put(player.getUUID(), new Wave(player.level(), player.getUUID(), player.getId(), start, dir.normalize(), spec));
		player.setAttached(FIRING, true);
		ZFighterPower.refresh(player, ZFighterPower.live(player));
	}

	private static float strength(ServerPlayer player) {
		Wave wave = WAVES.get(player.getUUID());
		if (wave == null) {
			return 1.0F;
		}
		if (wave.spec.ape()) {
			return APE_CLASH_STRENGTH;
		}
		ZFighterPower.Live live = ZFighterPower.live(player);
		float share = player.isCreative() || live.raging() ? 1.0F : Mth.clamp(live.ki / Math.max(1, ZFighterPower.maxKi(player)), 0.0F, 1.0F);
		float power = 1.0F + CLASH_KI_POWER * (ZFighterPower.kiPower(player, live) - 1.0F);
		return wave.spec.strength() * Math.max(0.1F, power) * Mth.lerp(share, CLASH_KI_EMPTY, CLASH_KI_FULL);
	}

	/** From BeamClash: overwhelmed, let go or detonated, the beam ends at the knot. */
	private static void stop(ServerPlayer player) {
		Wave wave = WAVES.remove(player.getUUID());
		if (wave != null) {
			burst(player, wave, wave.head(), true);
		}
	}

	private static void tick(MinecraftServer server) {
		if (WAVES.isEmpty()) {
			return;
		}
		List<Wave> waves = new ArrayList<>(WAVES.values());
		for (Wave wave : waves) {
			if (WAVES.get(wave.owner) != wave) {
				continue;
			}
			ServerPlayer player = server.getPlayerList().getPlayer(wave.owner);
			if (player == null || !player.isAlive() || player.isSpectator() || player.level() != wave.level || !Powers.has(player, ZFighterPower.INSTANCE)) {
				// he's gone: it fizzles
				WAVES.remove(wave.owner);
				send(wave, wave.head(), true);
				if (player != null) {
					end(player);
				}
				continue;
			}
			if (!advance(player, wave)) {
				WAVES.remove(wave.owner);
				burst(player, wave, wave.head(), false);
			}
		}
	}

	/** One tick of flight; false once it has stopped. */
	private static boolean advance(ServerPlayer player, Wave wave) {
		ServerLevel level = wave.level;
		Spec spec = wave.spec;
		wave.age++;
		boolean done = false;
		if (BeamClash.isClashing(player)) {
			// held at the knot, following it either way; the ki behind it burns
			wave.length = Math.max(0.0, BeamClash.clamp(player, wave.start, wave.length + spec.speed()));
			ZFighterPower.Live live = ZFighterPower.live(player);
			if (!spec.ape() && !player.isCreative() && !live.raging()) {
				live.ki = Math.max(0.0F, live.ki - CLASH_KI_DRAIN);
			}
		} else {
			double next = Math.min(spec.range(), wave.length + spec.speed());
			if (!spec.pierces()) {
				Vec3 a = wave.head();
				Vec3 b = wave.start.add(wave.dir.scale(next));
				BlockHitResult hit = level.clip(new ClipContext(a, b, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
				if (hit.getType() != HitResult.Type.MISS) {
					next = wave.start.distanceTo(hit.getLocation());
					done = true;
				}
			}
			wave.length = next;
			done |= wave.length >= spec.range() - 1.0E-3 || wave.age >= wave.maxAge;
		}
		Vec3 head = wave.head();
		strike(player, wave, head);
		sparks(wave, head);
		if (wave.age % HUM_EVERY == 0) {
			Soundscape.play(level, head, SoundSource.PLAYERS, ZFighterKi.KI_HUM.louder(1.4F).pitched(spec.pitch()), ZFighterKi.KI_SURGE);
		}
		if (!done) {
			send(wave, head, false);
		}
		return !done;
	}

	/** Whatever the beam's body now reaches is hurt, pushed and chilled, once. */
	private static void strike(ServerPlayer player, Wave wave, Vec3 head) {
		Spec spec = wave.spec;
		ServerLevel level = wave.level;
		DamageSource source = ZFighterKi.source(player);
		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, new AABB(wave.start, head).inflate(spec.radius() + 1.0),
			e -> e != player && e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand) && !wave.struck.contains(e.getId()))) {
			if (ZFighterKi.distanceToSegment(target.getBoundingBox().getCenter(), wave.start, head) > spec.radius() + target.getBbWidth() * 0.5) {
				continue;
			}
			wave.struck.add(target.getId());
			target.setInvulnerableTime(0);
			target.hurtServer(level, source, spec.damage());
			ZFighterKi.push(target, wave.dir, spec.push(), 0.3);
			if (spec.chill() > 0) {
				target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, spec.chill(), 2), player);
				target.setTicksFrozen(Math.max(target.getTicksFrozen(), target.getTicksRequiredToFreeze() + spec.chill()));
			}
		}
	}

	/** The server's particles round the beam: the Spiral Lance's coil, a crackle round a wave, motes off the head. */
	private static void sparks(Wave wave, Vec3 head) {
		ServerLevel level = wave.level;
		Spec spec = wave.spec;
		if (wave.age % SPARK_EVERY != 0 || wave.length < 0.5) {
			return;
		}
		DustParticleOptions glow = new DustParticleOptions(spec.color(), spec.style() == ZFighterKi.WAVE ? 3.0F : 1.2F);
		Vec3 side = wave.dir.cross(Math.abs(wave.dir.y) < 0.95 ? new Vec3(0.0, 1.0, 0.0) : new Vec3(1.0, 0.0, 0.0)).normalize();
		Vec3 up = side.cross(wave.dir).normalize();
		if (spec.style() == ZFighterKi.SPIRAL) {
			for (double d = 0.0; d <= wave.length; d += COIL_STEP) {
				double t = d * 1.6 - wave.age * 0.8;
				Vec3 coil = wave.start.add(wave.dir.scale(d)).add(side.scale(Math.cos(t) * 0.5)).add(up.scale(Math.sin(t) * 0.5));
				level.sendParticles(glow, true, false, coil.x, coil.y, coil.z, 1, 0.0, 0.0, 0.0, 0.0);
			}
		} else if (spec.style() == ZFighterKi.WAVE) {
			double r = spec.radius() * 0.6;
			for (double d = 0.0; d <= wave.length; d += CRACKLE_STEP) {
				if (level.getRandom().nextInt(6) == 0) {
					Vec3 at = wave.start.add(wave.dir.scale(d));
					level.sendParticles(glow, true, false, at.x, at.y, at.z, 1, r, r, r, 0.0);
				}
			}
		}
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, true, false, head.x, head.y, head.z, 2, spec.radius() * 0.4, spec.radius() * 0.4,
			spec.radius() * 0.4, 0.15);
	}

	/** Where the head stops, it bursts (the beam's own blast); a clash's end only shakes the ground with it. */
	private static void burst(ServerPlayer player, Wave wave, Vec3 at, boolean byClash) {
		ServerLevel level = wave.level;
		Spec spec = wave.spec;
		send(wave, at, true);
		if (spec.blast() > 0.0F) {
			level.explode(player, ZFighterKi.source(player), byClash || spec.groundOnly() ? ZFighterKi.GROUND_ONLY : null, at.x, at.y, at.z,
				spec.blast() * (byClash ? CLASH_BURST : 1.0F), false, spec.interaction());
		} else {
			// the lance and the needle don't burst: they shatter into motes
			level.sendParticles(new DustParticleOptions(spec.color(), 1.5F), true, false, at.x, at.y, at.z, 24, 0.4, 0.4, 0.4, 0.2);
			level.sendParticles(ParticleTypes.END_ROD, true, false, at.x, at.y, at.z, 10, 0.2, 0.2, 0.2, 0.15);
			level.playSound(null, at.x, at.y, at.z, spec.style() == ZFighterKi.NEEDLE ? SoundEvents.AMETHYST_BLOCK_BREAK : SoundEvents.BEACON_DEACTIVATE,
				SoundSource.PLAYERS, 1.2F, spec.style() == ZFighterKi.NEEDLE ? 1.6F : 1.2F);
		}
		end(player);
	}

	/** Out of the pose, no longer slowed. */
	private static void end(ServerPlayer player) {
		if (WAVES.containsKey(player.getUUID())) {
			return;
		}
		player.removeAttached(FIRING);
		if (Powers.has(player, ZFighterPower.INSTANCE)) {
			ZFighterPower.refresh(player, ZFighterPower.live(player));
		}
	}

	/** Everyone near sees the beam from his hands to its head (or, {@code done}, fading where it stopped). */
	private static void send(Wave wave, Vec3 head, boolean done) {
		ServerLevel level = wave.level;
		ServerPlayer owner = level.getServer().getPlayerList().getPlayer(wave.owner);
		Vec3 from = owner != null && owner.level() == level ? hands(owner, wave) : wave.start;
		ZFighterNet.BeamHead fx = new ZFighterNet.BeamHead(wave.id, from.x, from.y, from.z, head.x, head.y, head.z, (float)wave.spec.radius(),
			wave.spec.color(), wave.spec.style(), done ? ZFighterKi.BEAM_TICKS : 0);
		Vec3 middle = from.lerp(head, 0.5);
		double seen = ZFighterKi.BEAM_SEEN + wave.length * 0.5;
		for (ServerPlayer viewer : level.players()) {
			if (viewer == owner || viewer.position().distanceToSqr(middle) <= seen * seen) {
				ServerPlayNetworking.send(viewer, fx);
			}
		}
	}

	/** Where the beam leaves him now: the cupped hands (the Great Ape's mouth), never ahead of where it started. */
	private static Vec3 hands(ServerPlayer player, Wave wave) {
		Vec3 look = player.getViewVector(1.0F);
		return wave.spec.ape() ? ZFighterKi.apeMouth(player, look) : ZFighterKi.waveHands(player, look);
	}

	/** For debug and other powers: where this player's beam's head is, or null. */
	public static @Nullable Vec3 head(Player player) {
		Wave wave = WAVES.get(player.getUUID());
		return wave == null ? null : wave.head();
	}
}
