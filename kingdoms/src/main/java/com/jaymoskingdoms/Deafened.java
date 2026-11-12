package com.jaymoskingdoms;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Deafened by blasts (fifth edition's deafened, Andy's pick B5). Dynamite, a cannon, any explosion or a thunderbolt close by
 * rings a player's ears: for a while the whole world goes muffled under a high whine, they can't hear the footsteps and
 * callouts about them (no chance of noticing by ear, no bubbles from speakers who aren't close), and spells that need
 * speaking go wrong. The nearer the blast the longer it lasts.
 * <p>
 * The server keeps the time and tells the player's own client ({@link Afflictions}); the muffling and whine are made
 * there ({@code FifthClient}, {@code SoundEngineMixin}).
 */
public final class Deafened {
	/** A blast of strength 1 is heard to ring this far (blocks), plus this much for each point of its strength. */
	static final double BASE_RANGE = 6.0, PER_POWER = 2.5;
	/** The longest and shortest ring (ticks): at the blast, and at the edge of its range. */
	static final int LONGEST = 20 * 30, SHORTEST = 20 * 4;
	/** A thunderbolt rings as an explosion of this strength; a cannon's muzzle as this. */
	public static final float BOLT = 4.0F, MUZZLE = 4.0F, BLAST = 4.0F;
	/** Spells that need speaking go wrong this often when the caster can't hear themselves. */
	static final float SPELL_GOES_WRONG = 0.55F;
	/** A speaker closer than this (blocks) is lip-read, and still seen to speak. */
	static final double LIP_READ = 2.5;

	private static final Map<LivingEntity, Long> UNTIL = new WeakHashMap<>();

	private Deafened() {
	}

	static void register() {
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> UNTIL.clear());
		ServerTickEvents.END_SERVER_TICK.register(Deafened::tick);
	}

	/** An explosion or bolt of {@code strength} at {@code at}: rings the ears of every player near enough. */
	public static void blast(ServerLevel level, Vec3 at, float strength) {
		double range = BASE_RANGE + PER_POWER * strength;
		for (ServerPlayer p : level.players()) {
			if (p.isSpectator()) {
				continue;
			}
			double d = Math.sqrt(p.distanceToSqr(at));
			if (d > range) {
				continue;
			}
			int ticks = (int) (SHORTEST + (LONGEST - SHORTEST) * (1.0 - d / range));
			ring(p, ticks);
		}
	}

	/** Rings {@code who}'s ears for {@code ticks} (or longer, if they already ring longer). */
	public static void ring(LivingEntity who, int ticks) {
		long until = who.level().getGameTime() + ticks;
		Long was = UNTIL.get(who);
		if (was == null || was < until) {
			UNTIL.put(who, until);
		}
	}

	/** Until when {@code who}'s ears ring (game time), or 0. */
	public static long until(LivingEntity who) {
		Long u = UNTIL.get(who);
		return u == null ? 0L : u;
	}

	/** Whether {@code who} can't hear just now. */
	public static boolean is(LivingEntity who) {
		Long u = UNTIL.get(who);
		return u != null && who.level().getGameTime() < u;
	}

	/** Whether {@code speaker}'s words don't reach {@code listener}: they are deaf and the speaker isn't close. */
	public static boolean unheard(ServerPlayer listener, net.minecraft.world.entity.Entity speaker) {
		return is(listener) && listener.distanceTo(speaker) > LIP_READ;
	}

	/** A spell that needs speaking: whether it goes wrong because {@code caster} can't hear themselves (and fizzles, seen and heard). */
	public static boolean spoils(LivingEntity caster) {
		if (!is(caster) || caster.getRandom().nextFloat() >= SPELL_GOES_WRONG || !(caster.level() instanceof ServerLevel level)) {
			return false;
		}
		level.playSound(null, caster.getX(), caster.getEyeY(), caster.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.8F, 1.3F);
		Fx.burst(level, caster.getX(), caster.getY(1.0), caster.getZ(), 0x7A7A8A, 0.6F, 8, 0.3, 0.3, 0.3);
		return true;
	}

	private static void tick(MinecraftServer server) {
		if (!UNTIL.isEmpty() && server.getTickCount() % 200 == 0) {
			UNTIL.entrySet().removeIf(e -> e.getKey() == null || e.getKey().isRemoved() || e.getKey().level().getGameTime() >= e.getValue());
		}
	}
}
