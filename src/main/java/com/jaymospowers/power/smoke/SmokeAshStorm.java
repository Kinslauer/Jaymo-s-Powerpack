package com.jaymospowers.power.smoke;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Fate;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Smoke with both fates, Ashen Sky (ascended and corrupted). C is Ash Storm; Harness the Sky's holds still work
 * ({@link SmokeCloudshaper}), and a few things change:
 * <ul>
 * <li><b>Tap C, Ash Storm:</b> a black storm cell of your own forms over you and follows you {@link #STORM_TICKS}
 * (30 s), {@link #RADIUS} blocks out. Ash falls under it (darkness at everyone else's edges), and lightning strikes
 * hostiles and whoever hurt you (a visual-only bolt and our own damage, so no fires). 3 minute cooldown.</li>
 * <li><b>Sneak + tap C:</b> a raincloud that rains soot: it blinds, and puts no fire out.</li>
 * <li><b>Hold C at a tornado:</b> Unwind can finish it off. Doing so drains your corruption (you stay ascended) and
 * it can come back, but the meter fills {@link #HARDER}x slower for each tornado you've killed (Andy).</li>
 * <li><b>Sneak + hold C under a supercell, or under your own Ash Storm once it's built up:</b> you feed the storm
 * instead; {@link #FEED_SECONDS} s puts a tornado down where you aim. Through Jaymo's Kingdoms ({@code SkyCalm}, found
 * by reflection); without it there are no tornadoes to make.</li>
 * </ul>
 */
final class SmokeAshStorm {
	static final int STORM_TICKS = 600, COOLDOWN = 3600;
	/** How far out the storm reaches (40 blocks across), and how high its cloud hangs over you. */
	static final double RADIUS = 20.0;
	static final double CLOUD_HEIGHT = 16.0;
	/** It builds up over this long; feeding it a tornado needs it full. */
	static final int BUILD_TICKS = 100;
	/** (0.16.24) The film (ticks): a crane up round you as it gathers; bars for everyone in twice its radius. */
	static final int FILM_TICKS = 50;
	/** One strike this often (ticks), on a hostile or on whoever hurt you lately ({@link #GRUDGE_TICKS}). */
	static final int STRIKE_EVERY = 30, GRUDGE_TICKS = 400;
	static final float LIGHTNING_DAMAGE = 6.0F;
	static final double LIGHTNING_REACH = 2.5;
	/** Smog puffed into the cloud overhead every 10 ticks. */
	static final float CLOUD_PUFF = 4.0F;
	static final int DARK_TICKS = 50;
	/** Seconds of feeding to put a tornado down, and how far you can aim it. */
	static final int FEED_SECONDS = 10;
	static final double AIM_RANGE = 64.0;
	/** The corruption meter fills this many times slower for each tornado killed. */
	static final double HARDER = 1.5;

	/** How many tornadoes this Smoke user has killed. Saved, kept through death. */
	public static final AttachmentType<Integer> KILLS = AttachmentRegistry.create(JaymosPowers.id("smoke_tornado_kills"),
		builder -> builder.persistent(com.mojang.serialization.Codec.INT).copyOnDeath());

	private static final MethodHandle SUPERCELL = find("supercellOver", MethodType.methodType(boolean.class, Level.class, double.class, double.class));
	private static final MethodHandle TOUCHDOWN = find("touchdown",
		MethodType.methodType(boolean.class, Level.class, double.class, double.class, double.class, double.class, boolean.class));

	private static final Map<UUID, Long> STORMS = new HashMap<>();

	private SmokeAshStorm() {
	}

	private static @Nullable MethodHandle find(String name, MethodType type) {
		try {
			return MethodHandles.publicLookup().findStatic(Class.forName("com.jaymoskingdoms.disasters.SkyCalm"), name, type);
		} catch (ReflectiveOperationException | LinkageError e) {
			return null;
		}
	}

	/** Both fates: Ascended and corrupted. */
	static boolean both(ServerPlayer player) {
		return Fate.isAscended(player) && Fate.isCorrupted(player);
	}

	// ---------------------------------------------------------------- the storm

	/** Tap C: the storm forms over you (or the cooldown says no). */
	static void summon(ServerPlayer player) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		long left = SmokePower.state(player).left(SmokeState.ASH, now);
		if (left > 0) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.smoke_ash_storm_wait", (left + 19) / 20).withStyle(ChatFormatting.GRAY));
			return;
		}
		STORMS.put(player.getUUID(), now);
		com.jaymospowers.core.UltimateFilm.play(player, com.jaymospowers.core.UltimateFilm.CRANE, FILM_TICKS, RADIUS * 2.0);
		SmokePower.cooldown(player, SmokeState.ASH, COOLDOWN);
		SmokeMoves.begin(player, SmokeMoves.ASH);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 1.5F, 0.5F);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, player.getX(), player.getY() + 1.0, player.getZ(), 60, 1.0, 1.0, 1.0, 0.2);
	}

	/** Game ticks since this user's storm formed, or -1 if they have none. */
	private static long age(ServerPlayer player) {
		Long from = STORMS.get(player.getUUID());
		return from == null ? -1 : player.level().getGameTime() - from;
	}

	/** Whether this user's own storm is up and fully built. */
	static boolean builtUp(ServerPlayer player) {
		return age(player) >= BUILD_TICKS;
	}

	/** From the power's tick. */
	static void tick(ServerPlayer player) {
		long age = age(player);
		if (age < 0) {
			return;
		}
		if (age >= STORM_TICKS || !player.isAlive() || !both(player)) {
			STORMS.remove(player.getUUID());
			return;
		}
		ServerLevel level = player.level();
		float strength = Math.min(1.0F, age / (float)BUILD_TICKS);
		double r = RADIUS * (0.4 + 0.6 * strength);
		double base = cloudBase(level, player);
		// the cloud: smog high overhead, kept topped up, and ash falling out of it
		if (age % 10 == 0) {
			for (int i = 0; i < 3; i++) {
				double a = level.getRandom().nextDouble() * Math.PI * 2.0, d = Math.sqrt(level.getRandom().nextDouble()) * r;
				SmokeField.puff(level, new Vec3(player.getX() + Math.cos(a) * d, base, player.getZ() + Math.sin(a) * d), CLOUD_PUFF * strength, player);
			}
		}
		level.sendParticles(ParticleTypes.LARGE_SMOKE, player.getX(), base, player.getZ(), 6, r * 0.5, 1.0, r * 0.5, 0.01);
		level.sendParticles(ParticleTypes.ASH, player.getX(), player.getY() + 6.0, player.getZ(), (int)(30 * strength), r * 0.5, 5.0, r * 0.5, 0.0);
		level.sendParticles(ParticleTypes.WHITE_ASH, player.getX(), player.getY() + 6.0, player.getZ(), (int)(10 * strength), r * 0.5, 5.0, r * 0.5, 0.0);
		if (age % 80 == 0) {
			level.playSound(null, player.getX(), base, player.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 0.6F, 0.5F);
		}
		AABB under = new AABB(player.getX() - r, player.getY() - 8.0, player.getZ() - r, player.getX() + r, base, player.getZ() + r);
		// darkness at the edges of everyone else's screen
		if (age % 20 == 0) {
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, under, e -> e != player && e.isAlive() && near(e, player, r))) {
				e.addEffect(new MobEffectInstance(MobEffects.DARKNESS, DARK_TICKS, 0, false, false, true));
			}
		}
		if (strength >= 1.0F && age % STRIKE_EVERY == 0) {
			LivingEntity target = target(level, player, under, r);
			if (target != null) {
				strike(level, player, target.position());
			}
		}
	}

	/** The storm's cloud hangs over you, lower under a roof. */
	private static double cloudBase(ServerLevel level, ServerPlayer player) {
		double top = player.getY() + CLOUD_HEIGHT;
		BlockPos at = player.blockPosition();
		for (int y = 2; y <= CLOUD_HEIGHT; y++) {
			if (!level.getBlockState(at.above(y)).getCollisionShape(level, at).isEmpty()) {
				return player.getY() + y - 1;
			}
		}
		return top;
	}

	private static boolean near(LivingEntity e, ServerPlayer player, double r) {
		double dx = e.getX() - player.getX(), dz = e.getZ() - player.getZ();
		return dx * dx + dz * dz <= r * r;
	}

	/** Whoever hurt you lately (if under the storm), else the nearest hostile under it. */
	private static @Nullable LivingEntity target(ServerLevel level, ServerPlayer player, AABB under, double r) {
		LivingEntity grudge = player.getLastHurtByMob();
		if (grudge != null && grudge.isAlive() && player.tickCount - player.getLastHurtByMobTimestamp() <= GRUDGE_TICKS && near(grudge, player, r)
			&& under.contains(grudge.position())) {
			return grudge;
		}
		LivingEntity best = null;
		double nearest = Double.MAX_VALUE;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, under, e -> e instanceof Enemy && e.isAlive() && near(e, player, r))) {
			double d = e.distanceToSqr(player);
			if (d < nearest && level.canSeeSky(e.blockPosition())) {
				nearest = d;
				best = e;
			}
		}
		return best;
	}

	/** A visual-only bolt (no fire, no vanilla damage) and our own lightning damage round it, sparing you. */
	private static void strike(ServerLevel level, ServerPlayer caster, Vec3 at) {
		LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
		if (bolt != null) {
			bolt.snapTo(at);
			bolt.setVisualOnly(true);
			bolt.setCause(caster);
			level.addFreshEntity(bolt);
		}
		AABB area = new AABB(at.x - LIGHTNING_REACH, at.y - LIGHTNING_REACH, at.z - LIGHTNING_REACH,
			at.x + LIGHTNING_REACH, at.y + 4.0 + LIGHTNING_REACH, at.z + LIGHTNING_REACH);
		DamageSource source = caster.damageSources().source(DamageTypes.LIGHTNING_BOLT, caster);
		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area, e -> e.isAlive() && e != caster && !e.isSpectator())) {
			target.hurtServer(level, source, LIGHTNING_DAMAGE);
		}
		com.jaymospowers.core.Shakes.around(level, at, 20.0, 1.0F);
	}

	// ---------------------------------------------------------------- feeding a storm

	/** Whether there's a storm here this user can feed: a supercell overhead, or their own built-up Ash Storm. */
	static boolean canFeed(ServerPlayer player) {
		return builtUp(player) || supercellOver(player);
	}

	private static boolean supercellOver(ServerPlayer player) {
		if (SUPERCELL == null) {
			return false;
		}
		try {
			return (boolean)SUPERCELL.invokeExact((Level)player.level(), player.getX(), player.getZ());
		} catch (Throwable t) {
			return false;
		}
	}

	/** A second of feeding the storm; {@link #FEED_SECONDS} of them put a tornado down where you aim. Returns true once it's down. */
	static boolean feed(ServerPlayer player, int seconds) {
		ServerLevel level = player.level();
		boolean supercell = supercellOver(player);
		if (TOUCHDOWN == null || !supercell && !builtUp(player)) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.smoke_feed_nothing").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
			return true;
		}
		// the cloud pulled down into a turning column where you aim
		Vec3 aim = aim(player);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, aim.x, aim.y + 12.0, aim.z, 10 + seconds * 3, 1.0 + seconds * 0.3, 2.0, 1.0 + seconds * 0.3, 0.05);
		level.playSound(null, aim.x, aim.y, aim.z, SoundEvents.ELYTRA_FLYING, SoundSource.WEATHER, 0.3F + seconds * 0.05F, 0.5F);
		if (seconds < FEED_SECONDS) {
			return false;
		}
		try {
			if ((boolean)TOUCHDOWN.invokeExact((Level)level, player.getX(), player.getZ(), aim.x, aim.z, supercell)) {
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.smoke_feed_touchdown").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
			}
		} catch (Throwable ignored) {
		}
		return true;
	}

	/** Where the player aims on the ground, up to {@link #AIM_RANGE}. */
	private static Vec3 aim(ServerPlayer player) {
		ServerLevel level = player.level();
		Vec3 eye = player.getEyePosition();
		Vec3 at = level.clip(new ClipContext(eye, eye.add(player.getLookAngle().scale(AIM_RANGE)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
			player)).getLocation();
		BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(at));
		return new Vec3(at.x, ground.getY(), at.z);
	}

	// ---------------------------------------------------------------- killing a tornado

	/** A tornado killed: the corruption burns away (still ascended), and it will be harder to come back. */
	static void killed(ServerPlayer player) {
		int kills = kills(player) + 1;
		player.setAttached(KILLS, kills);
		STORMS.remove(player.getUUID());
		Fate.purify(player);
		// unlike the other cures it can come back (Andy), only harder
		Fate.State state = Fate.state(player);
		Fate.set(player, new Fate.State(state.ascended(), false, false, 0));
		SmokeSmog.forget(player);
		player.removeAttached(SmokeSmog.SINCE);
		player.sendSystemMessage(Component.translatable("message.jaymospowers.smoke_tornado_killed").withStyle(ChatFormatting.AQUA, ChatFormatting.ITALIC));
	}

	static int kills(ServerPlayer player) {
		Integer kills = player.getAttached(KILLS);
		return kills == null ? 0 : kills;
	}

	/** How many times slower the corruption meter fills for this user. */
	static double harder(ServerPlayer player) {
		return Math.pow(HARDER, kills(player));
	}

	static void forget(ServerPlayer player) {
		STORMS.remove(player.getUUID());
	}

	static void clear() {
		STORMS.clear();
	}
}
