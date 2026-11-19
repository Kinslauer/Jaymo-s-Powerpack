package com.jaymospowers.core.brawl;

import com.jaymospowers.core.BeamClash;
import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.power.zfighter.ZFighterNpcKi;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * NPC beams in beam struggles (NPC fights patch 3, row F10): takes every beam a fighting NPC fires through
 * {@link ZFighterNpcKi#fireBeam} (the tyrant's, the patrolman's, a Z Fighter gifted villager's) and holds it out for {@link #HOLD}
 * ticks as a {@link BeamClash} beam. Meeting another beam head-on, a player's or another NPC's, it locks into a struggle (the NPC
 * pushes on its own by rank, with surges, {@link BeamClash#npcBeam}); not meeting one, it hits the line in {@link #PULSES} pulses
 * that add up to the damage the beam always did. Aimed at players as at anyone.
 */
public final class NpcBeams {
	/** How long the beam is held out (ticks), how many pulses it hits in, and the beam's width and kick (blocks, blocks a tick). */
	static final int HOLD = 24, PULSES = 2, FX_EVERY = 3;
	static final double WIDTH = 1.2, PUSH = 0.9, LIFT = 0.3;
	static final float RADIUS = 0.8F;
	/** Its power in a struggle: this much, plus this times the rank of the NPC (a player's beam is about 1). */
	static final float POWER = 1.0F, POWER_RANK = 0.8F;

	private record Held(ServerLevel level, ZFighterNpcKi.NpcBeam beam, Vec3 dir, double length, int[] tick) {
	}

	private static final List<Held> HELD = new ArrayList<>();
	/** NPCs whose beam a struggle has ended. */
	private static final Set<UUID> OFF = new HashSet<>();

	private NpcBeams() {
	}

	/** From {@link Brawl#init}. */
	static void init() {
		ZFighterNpcKi.taker = NpcBeams::take;
		ServerTickEvents.END_SERVER_TICK.register(NpcBeams::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			HELD.clear();
			OFF.clear();
		});
	}

	private static boolean take(ServerLevel level, ZFighterNpcKi.NpcBeam beam) {
		Vec3 line = beam.to().subtract(beam.from());
		if (line.lengthSqr() < 1.0E-4) {
			return false;
		}
		OFF.remove(beam.caster().getUUID());
		HELD.add(new Held(level, beam, line.normalize(), line.length(), new int[] {0}));
		level.playSound(null, beam.caster().getX(), beam.caster().getY(), beam.caster().getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 1.2F, 1.4F);
		return true;
	}

	private static void tick(MinecraftServer server) {
		for (Iterator<Held> it = new ArrayList<>(HELD).iterator(); it.hasNext();) {
			Held h = it.next();
			int tick = h.tick()[0]++;
			Mob caster = h.beam().caster();
			if (!caster.isAlive() || caster.level() != h.level() || OFF.contains(caster.getUUID()) || tick > HOLD && !BeamClash.clashing(caster)
				|| tick > HOLD + BeamClash.MAX_TICKS) {
				HELD.remove(h);
				continue;
			}
			hold(h, tick);
		}
	}

	/** One tick of a held beam: reported to the struggles, drawn up to the knot, hitting on a pulse. */
	private static void hold(Held h, int tick) {
		ServerLevel level = h.level();
		ZFighterNpcKi.NpcBeam beam = h.beam();
		Mob caster = beam.caster();
		float rank = Brawl.rank(caster);
		BeamClash.npcBeam(caster, beam.from(), h.dir(), h.length(), POWER + POWER_RANK * rank, rank, BeamClash.KIND_KI, m -> OFF.add(m.getUUID()));
		double length = BeamClash.clampNpc(caster, beam.from(), h.length());
		Vec3 end = beam.from().add(h.dir().scale(length));
		if (tick % FX_EVERY == 0) {
			MagicFx.beam(level, beam.from(), end, RADIUS, beam.color(), FX_EVERY + 2);
			MagicFx.burst(level, end.x, end.y, end.z, beam.color(), 1.0F, 6, 0.25, 0.25, 0.25);
		}
		int gap = HOLD / PULSES;
		if (tick > 0 && tick <= HOLD && tick % gap == 0) {
			AABB box = new AABB(beam.from(), end).inflate(WIDTH);
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != caster && e.isAlive() && hittable(e, beam.foe()))) {
				if (distanceToSegment(e.getBoundingBox().getCenter(), beam.from(), end) <= WIDTH && caster.hasLineOfSight(e)) {
					e.setInvulnerableTime(0);
					if (e.hurtServer(level, level.damageSources().mobAttack(caster), beam.damage() / PULSES)) {
						Vec3 push = h.dir().multiply(1.0, 0.0, 1.0);
						e.setDeltaMovement(e.getDeltaMovement().add(push.scale(PUSH / PULSES)).add(0.0, LIFT / PULSES, 0.0));
						e.syncVelocity = true;
					}
				}
			}
		}
	}

	/** Who a beam hurts: its foe, and any player in the way (not one in creative or spectator). */
	private static boolean hittable(LivingEntity e, LivingEntity foe) {
		return e == foe || e instanceof Player p && !p.isCreative() && !p.isSpectator();
	}

	private static double distanceToSegment(Vec3 p, Vec3 a, Vec3 b) {
		Vec3 ab = b.subtract(a);
		double len = ab.lengthSqr();
		double t = len < 1.0E-6 ? 0.0 : Math.clamp(p.subtract(a).dot(ab) / len, 0.0, 1.0);
		return p.distanceTo(a.add(ab.scale(t)));
	}
}
