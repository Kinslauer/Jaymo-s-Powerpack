package com.jaymoskingdoms.forms;

import com.jaymoskingdoms.CombatArts;
import com.jaymoskingdoms.PowersCompat;
import com.jaymoskingdoms.forms.Forms.Family;
import com.jaymoskingdoms.forms.Forms.Form;
import com.jaymoskingdoms.forms.Forms.Kind;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * (0.7.383) The claw family's forms (the Fang Gauntlets of Jaymo's Powers and any other weapon in the {@code form_claws} tag).
 * <ul>
 * <li>Rending Claw: a rake across the front, {@value #RAKE_REACH} blocks out: every foe in the arc is cut and left bleeding as
 * Rend does it ({@link CombatArts#bleed}), for longer at higher ranks.
 * <li>Feral Pounce: a leap onto the foe ahead (the nearest in a narrow cone within {@value #POUNCE_RANGE} blocks, else just
 * a long leap); landing on it, you mark it with a savage blow and a short bleed and knock it flat.
 * </ul>
 */
final class ClawForms {
	static final Form RENDING_CLAW = new Form("rending_claw", Family.CLAW, Kind.ART, 100, 6, 1.5F, 6);
	static final Form FERAL_POUNCE = new Form("feral_pounce", Family.CLAW, Kind.ART, 140, 4, 2.0F, 6);

	/** Rending Claw: how far the rake reaches (blocks), how wide it is (cosine of the half-angle), what it deals (times your attack), how long the bleed lasts (seconds, and more each rank). */
	static final double RAKE_REACH = 2.8, RAKE_ARC = 0.1;
	static final float RAKE_DAMAGE = 0.8F;
	static final int RAKE_BLEED = 5, RAKE_BLEED_PER_RANK = 2;
	/** Feral Pounce: how far it looks for a foe (blocks) and how narrow (cosine), the leap's speed (blocks a tick) and lift, how near counts as landed on it, the longest the leap lasts (ticks), what the blow deals (times your attack), the bleed after (seconds). */
	static final double POUNCE_RANGE = 8.0, POUNCE_FACING = 0.75, POUNCE_SPEED = 1.0, POUNCE_LIFT = 0.3, POUNCE_LANDED = 1.9;
	static final int POUNCE_TICKS = 16, POUNCE_BLEED = 3;
	static final float POUNCE_DAMAGE = 1.4F;
	/** The screen shake of each (about 0.3 a nudge, 1 a blow). */
	static final float RAKE_SHAKE = 0.3F, POUNCE_SHAKE = 0.45F;

	/** A pounce under way: on whom (null for a bare leap), until when. */
	private record Pounce(UUID foe, long until, int rank) {
	}

	private static final Map<UUID, Pounce> POUNCES = new HashMap<>();

	private ClawForms() {
	}

	static void addTo(Map<String, Form> forms) {
		forms.put(RENDING_CLAW.id(), RENDING_CLAW);
		forms.put(FERAL_POUNCE.id(), FERAL_POUNCE);
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(ClawForms::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> POUNCES.remove(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> POUNCES.clear());
	}

	static void perform(ServerPlayer player, Form form, int rank) {
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}
		if (form == RENDING_CLAW) {
			rake(level, player, rank);
		} else if (form == FERAL_POUNCE) {
			pounce(level, player, rank);
		}
	}

	private static void rake(ServerLevel level, ServerPlayer player, int rank) {
		Vec3 look = player.getLookAngle().multiply(1.0, 0.0, 1.0);
		if (look.lengthSqr() < 1.0E-4) {
			return;
		}
		look = look.normalize();
		double reach = Forms.reach(RAKE_REACH, rank);
		List<LivingEntity> hits = new ArrayList<>();
		for (LivingEntity foe : Forms.targets(level, player, player.getBoundingBox().inflate(reach, 0.5, reach))) {
			Vec3 to = foe.position().subtract(player.position()).multiply(1.0, 0.0, 1.0);
			double d = to.length();
			if (d <= reach + foe.getBbWidth() / 2.0 && (d < 1.0E-3 || to.scale(1.0 / d).dot(look) > RAKE_ARC)) {
				hits.add(foe);
			}
		}
		// three raking lines across the arc
		Vec3 side = new Vec3(-look.z, 0.0, look.x);
		for (int claw = -1; claw <= 1; claw++) {
			for (double d = 0.6; d <= reach; d += 0.4) {
				Vec3 at = player.position().add(look.scale(d)).add(side.scale(claw * 0.35 + (d - reach / 2) * 0.12 * claw));
				level.sendParticles(ParticleTypes.CRIT, at.x, player.getY(0.6), at.z, 1, 0.0, 0.0, 0.0, 0.0);
			}
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 0.6F);
		for (LivingEntity foe : hits) {
			CombatArts.strike(level, player, foe, Forms.damage(player, RAKE_DAMAGE, rank));
			if (foe.isAlive() && !(foe instanceof net.minecraft.world.entity.decoration.Mannequin)) {
				CombatArts.bleed(level, player, foe, RAKE_BLEED + RAKE_BLEED_PER_RANK * (rank - 1));
			}
		}
		PowersCompat.shake(player, RAKE_SHAKE);
		Forms.landed(player, RENDING_CLAW, hits);
	}

	private static void pounce(ServerLevel level, ServerPlayer player, int rank) {
		Vec3 look = player.getLookAngle();
		LivingEntity best = null;
		double bestScore = Double.MAX_VALUE;
		for (LivingEntity foe : Forms.targets(level, player, player.getBoundingBox().inflate(POUNCE_RANGE))) {
			Vec3 to = foe.getBoundingBox().getCenter().subtract(player.getEyePosition());
			double d = to.length();
			if (d > 1.0E-3 && d <= POUNCE_RANGE && to.scale(1.0 / d).dot(look) > POUNCE_FACING && d < bestScore) {
				best = foe;
				bestScore = d;
			}
		}
		Vec3 dir = best == null ? look.multiply(1.0, 0.0, 1.0) : best.getBoundingBox().getCenter().subtract(player.position().add(0.0, 0.9, 0.0));
		if (dir.lengthSqr() < 1.0E-4) {
			return;
		}
		dir = dir.normalize();
		player.setDeltaMovement(dir.x * POUNCE_SPEED, dir.y * POUNCE_SPEED + POUNCE_LIFT, dir.z * POUNCE_SPEED);
		player.needsSync = true;
		player.resetFallDistance();
		POUNCES.put(player.getUUID(), new Pounce(best == null ? null : best.getUUID(), level.getGameTime() + POUNCE_TICKS, rank));
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 0.9F, 1.3F);
		level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.2, player.getZ(), 6, 0.2, 0.05, 0.2, 0.03);
	}

	private static void tick(MinecraftServer server) {
		if (POUNCES.isEmpty()) {
			return;
		}
		for (Iterator<Map.Entry<UUID, Pounce>> it = POUNCES.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Pounce> entry = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			Pounce pounce = entry.getValue();
			if (player == null || !player.isAlive() || !(player.level() instanceof ServerLevel level) || level.getGameTime() > pounce.until()) {
				it.remove();
				continue;
			}
			if (pounce.foe() == null) {
				if (player.onGround() && level.getGameTime() > pounce.until() - POUNCE_TICKS + 2) {
					it.remove();
				}
				continue;
			}
			if (!(level.getEntity(pounce.foe()) instanceof LivingEntity foe) || !foe.isAlive()) {
				it.remove();
				continue;
			}
			if (player.getBoundingBox().inflate(0.3).intersects(foe.getBoundingBox()) || player.distanceToSqr(foe) <= POUNCE_LANDED * POUNCE_LANDED) {
				it.remove();
				land(level, player, foe, pounce.rank());
			}
		}
	}

	/** On the foe: a savage blow, a short bleed, and it is knocked flat. */
	private static void land(ServerLevel level, ServerPlayer player, LivingEntity foe, int rank) {
		CombatArts.strike(level, player, foe, Forms.damage(player, POUNCE_DAMAGE, rank));
		if (foe.isAlive()) {
			if (!(foe instanceof net.minecraft.world.entity.decoration.Mannequin)) {
				CombatArts.bleed(level, player, foe, POUNCE_BLEED + (rank - 1));
			}
			Vec3 push = foe.position().subtract(player.position()).multiply(1.0, 0.0, 1.0);
			push = push.lengthSqr() < 1.0E-4 ? Vec3.ZERO : push.normalize().scale(0.3);
			foe.setDeltaMovement(foe.getDeltaMovement().add(push.x, -0.1, push.z));
			foe.needsSync = true;
		}
		// the leap ends on the foe
		player.setDeltaMovement(player.getDeltaMovement().scale(0.2));
		player.needsSync = true;
		level.sendParticles(ParticleTypes.CRIT, foe.getX(), foe.getY(0.7), foe.getZ(), 14, 0.3, 0.3, 0.3, 0.3);
		level.playSound(null, foe.getX(), foe.getY(), foe.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0F, 0.8F);
		PowersCompat.shake(player, POUNCE_SHAKE);
		List<LivingEntity> hits = new ArrayList<>();
		hits.add(foe);
		Forms.landed(player, FERAL_POUNCE, hits);
	}
}
