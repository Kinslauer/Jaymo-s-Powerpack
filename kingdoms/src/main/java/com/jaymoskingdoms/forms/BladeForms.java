package com.jaymoskingdoms.forms;

import com.jaymoskingdoms.CombatArts;
import com.jaymoskingdoms.PowersCompat;
import com.jaymoskingdoms.Senses;
import com.jaymoskingdoms.forms.Forms.Family;
import com.jaymoskingdoms.forms.Forms.Form;
import com.jaymoskingdoms.forms.Forms.Kind;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * (0.7.381) The blade family's forms (swords, and the katana of Balkon's WeaponMod through the {@code form_blades} tag).
 * <ul>
 * <li>Sheath Draw: a fast dash of {@value #DASH} blocks that cuts everything it passes on the draw; half again as hard on a
 * foe that is not after you or cannot see you ({@link Senses#unaware}).
 * <li>Crescent Sweep: a low, wide arc in front that trips: foes are slowed, knocked off their feet and, with Powers, have
 * their poise worn down hard (without it, they are thrown back further instead).
 * <li>(0.7.382) Rain of Cuts (masterstroke): on a fallen foe, a flurry of {@value #CUTS} cuts in ink-dark trails across it,
 * the last one a heavy blow with Powers' impact frames, under a hard-cut film ({@link PowersCompat#FILM_CUTS}).
 * </ul>
 */
final class BladeForms {
	static final Form SHEATH_DRAW = new Form("sheath_draw", Family.BLADE, Kind.ART, 70, 6, 1.5F, 6);
	static final Form CRESCENT_SWEEP = new Form("crescent_sweep", Family.BLADE, Kind.ART, 110, 8, 2.0F, 6);
	static final Form RAIN_OF_CUTS = new Form("rain_of_cuts", Family.BLADE, Kind.MASTERSTROKE, 400, 8, 3.0F, 4);

	/** Sheath Draw: how far the dash goes (blocks), the cut's width either side of the line and the reach past its end, what it deals (times your attack), the bonus on an unaware foe. */
	static final double DASH = 4.0, DRAW_WIDTH = 1.2, DRAW_REACH = 1.2;
	static final float DRAW_DAMAGE = 1.25F, UNAWARE_BONUS = 1.5F;
	/** Crescent Sweep: how far the arc reaches (blocks), how wide it is (cosine of the half-angle; -0.2 is a little over a half circle), what it deals (times your attack). */
	static final double SWEEP_REACH = 3.2, SWEEP_ARC = -0.2;
	static final float SWEEP_DAMAGE = 0.9F;
	/** Crescent Sweep's trip: slowness (ticks, level), the lift and shove of the fall, the poise worn off (Powers), or the shove when there is none. */
	static final int TRIP_TICKS = 40, TRIP_SLOW = 3;
	static final double TRIP_LIFT = 0.3, TRIP_SHOVE = 0.35, TRIP_SHOVE_NO_POISE = 0.9;
	static final float TRIP_POISE = 14.0F;
	/** The screen shake of each (about 0.3 a nudge, 1 a blow). */
	static final float DRAW_SHAKE = 0.25F, SWEEP_SHAKE = 0.35F, RAIN_SHAKE = 0.5F;
	/** Rain of Cuts: how many cuts, the ticks between them, what each deals (times your attack) and the last one (a heavy blow). */
	static final int CUTS = 9, CUT_GAP = 2;
	static final float CUT_DAMAGE = 0.3F, LAST_CUT_DAMAGE = 1.4F;
	/** Rain of Cuts: how far the foe may get from you before the flurry stops (blocks), and the film's length (ticks) and how far bars show to witnesses (blocks). */
	static final double RAIN_LEASH = 8.0, FILM_WITNESSES = 24.0;
	static final int FILM_TICKS = 34;

	private BladeForms() {
	}

	static void addTo(Map<String, Form> forms) {
		forms.put(SHEATH_DRAW.id(), SHEATH_DRAW);
		forms.put(CRESCENT_SWEEP.id(), CRESCENT_SWEEP);
		forms.put(RAIN_OF_CUTS.id(), RAIN_OF_CUTS);
	}

	/** A flurry under way: whose, on whom, the cuts left, when the next falls, the form's rank, and the cuts that landed. */
	private static final class Flurry {
		final UUID who, foe;
		final int rank;
		int left = CUTS;
		long next;
		final List<LivingEntity> landed = new ArrayList<>();

		Flurry(UUID who, UUID foe, int rank, long next) {
			this.who = who;
			this.foe = foe;
			this.rank = rank;
			this.next = next;
		}
	}

	private static final Map<UUID, Flurry> FLURRIES = new HashMap<>();

	/** Called from {@link Forms#register}. */
	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(BladeForms::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> FLURRIES.remove(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> FLURRIES.clear());
	}

	static void perform(ServerPlayer player, Form form, int rank) {
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}
		if (form == SHEATH_DRAW) {
			sheathDraw(level, player, form, rank);
		} else if (form == CRESCENT_SWEEP) {
			crescentSweep(level, player, form, rank);
		} else if (form == RAIN_OF_CUTS) {
			rainOfCuts(level, player, rank);
		}
	}

	private static void sheathDraw(ServerLevel level, ServerPlayer player, Form form, int rank) {
		Vec3 look = player.getLookAngle().multiply(1.0, 0.0, 1.0);
		if (look.lengthSqr() < 1.0E-4) {
			return;
		}
		look = look.normalize();
		Vec3 start = player.position();
		Vec3 end = start;
		double dash = Forms.reach(DASH, rank);
		// as far along the look as there is room to stand
		for (double d = 0.5; d <= dash; d += 0.5) {
			Vec3 step = start.add(look.scale(d));
			if (!level.noCollision(player, player.getBoundingBox().move(step.subtract(start)))) {
				break;
			}
			end = step;
		}
		Vec3 ahead = end.add(look.scale(DRAW_REACH));
		List<LivingEntity> hits = new ArrayList<>(Forms.targets(level, player,
			new AABB(start, ahead).inflate(DRAW_WIDTH, 1.0, DRAW_WIDTH).expandTowards(0.0, player.getBbHeight(), 0.0)));
		for (double d = 0.0; d < start.distanceTo(end); d += 0.7) {
			Vec3 at = start.add(look.scale(d));
			level.sendParticles(ParticleTypes.CLOUD, at.x, at.y + 0.2, at.z, 1, 0.1, 0.05, 0.1, 0.0);
		}
		Trails.blade(level, start.add(0.0, 1.0, 0.0), end.add(look.scale(DRAW_REACH)).add(0.0, 1.0, 0.0));
		if (end.distanceToSqr(start) > 1.0E-4) {
			player.teleportTo(level, end.x, end.y, end.z, Set.of(), player.getYRot(), player.getXRot(), false);
		}
		level.playSound(null, end.x, end.y, end.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 1.5F);
		level.playSound(null, end.x, end.y, end.z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 0.8F, 1.2F);
		level.sendParticles(ParticleTypes.SWEEP_ATTACK, end.x + look.x, end.y + 1.0, end.z + look.z, 1, 0.0, 0.0, 0.0, 0.0);
		for (LivingEntity hit : hits) {
			float amount = Forms.damage(player, DRAW_DAMAGE, rank);
			if (Senses.unaware(hit, player)) {
				amount *= UNAWARE_BONUS;
				level.sendParticles(ParticleTypes.CRIT, hit.getX(), hit.getY(0.6), hit.getZ(), 10, 0.3, 0.3, 0.3, 0.2);
			}
			CombatArts.strike(level, player, hit, amount);
			level.sendParticles(ParticleTypes.SWEEP_ATTACK, hit.getX(), hit.getY(0.5), hit.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
		}
		PowersCompat.shake(player, DRAW_SHAKE);
		Forms.landed(player, form, hits);
	}

	private static void crescentSweep(ServerLevel level, ServerPlayer player, Form form, int rank) {
		Vec3 look = player.getLookAngle().multiply(1.0, 0.0, 1.0);
		if (look.lengthSqr() < 1.0E-4) {
			return;
		}
		look = look.normalize();
		double reach = Forms.reach(SWEEP_REACH, rank);
		// low: foes whose feet are near yours
		List<LivingEntity> hits = new ArrayList<>();
		for (LivingEntity foe : Forms.targets(level, player, player.getBoundingBox().inflate(reach, 0.5, reach))) {
			Vec3 to = foe.position().subtract(player.position()).multiply(1.0, 0.0, 1.0);
			double d = to.length();
			if (foe.getBoundingBox().minY <= player.getY() + 1.0 && d <= reach + foe.getBbWidth() / 2.0
				&& (d < 1.0E-3 || to.scale(1.0 / d).dot(look) > SWEEP_ARC)) {
				hits.add(foe);
			}
		}
		// the arc, low and wide
		Vec3 before = null;
		for (int i = -6; i <= 6; i++) {
			double a = i / 6.0 * Math.acos(Math.max(-1.0, SWEEP_ARC));
			Vec3 dir = new Vec3(look.x * Math.cos(a) - look.z * Math.sin(a), 0.0, look.x * Math.sin(a) + look.z * Math.cos(a));
			Vec3 arcAt = new Vec3(player.getX() + dir.x * reach * 0.8, player.getY() + 0.3, player.getZ() + dir.z * reach * 0.8);
			if (before != null) {
				Trails.blade(level, before, arcAt);
			}
			before = arcAt;
			level.sendParticles(ParticleTypes.SWEEP_ATTACK, player.getX() + dir.x * reach * 0.8, player.getY() + 0.3, player.getZ() + dir.z * reach * 0.8, 1, 0.0, 0.0, 0.0, 0.0);
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 0.7F);
		for (LivingEntity foe : hits) {
			CombatArts.strike(level, player, foe, Forms.damage(player, SWEEP_DAMAGE, rank));
			if (!foe.isAlive()) {
				continue;
			}
			foe.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, TRIP_TICKS, TRIP_SLOW), player);
			boolean poise = PowersCompat.wearPoise(foe, TRIP_POISE * (1.0F + 0.2F * (rank - 1)));
			Vec3 out = foe.position().subtract(player.position()).multiply(1.0, 0.0, 1.0);
			Vec3 push = out.lengthSqr() < 1.0E-4 ? look : out.normalize();
			double shove = poise ? TRIP_SHOVE : TRIP_SHOVE_NO_POISE;
			foe.setDeltaMovement(foe.getDeltaMovement().add(push.x * shove, TRIP_LIFT, push.z * shove));
			foe.needsSync = true;
		}
		PowersCompat.shake(player, SWEEP_SHAKE);
		Forms.landed(player, form, hits);
	}

	// ---- Rain of Cuts (0.7.382) ----------------------------------------------------------------------------------------

	private static void rainOfCuts(ServerLevel level, ServerPlayer player, int rank) {
		LivingEntity foe = Forms.masterTarget(player);
		if (foe == null || FLURRIES.containsKey(player.getUUID())) {
			return;
		}
		FLURRIES.put(player.getUUID(), new Flurry(player.getUUID(), foe.getUUID(), rank, level.getGameTime()));
		// the film: hard cuts between angles round the foe (bars for witnesses; an older Powers, or none, shows nothing)
		PowersCompat.film(player, player, PowersCompat.FILM_CUTS, FILM_TICKS, foe.getBoundingBox().getCenter(), foe, FILM_WITNESSES);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 0.6F);
	}

	private static void tick(MinecraftServer server) {
		if (FLURRIES.isEmpty()) {
			return;
		}
		for (Iterator<Flurry> it = FLURRIES.values().iterator(); it.hasNext(); ) {
			Flurry flurry = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(flurry.who);
			if (player == null || !player.isAlive() || !(player.level() instanceof ServerLevel level)) {
				it.remove();
				continue;
			}
			Entity found = level.getEntity(flurry.foe);
			if (!(found instanceof LivingEntity foe) || !foe.isAlive() || foe.distanceTo(player) > RAIN_LEASH) {
				finish(player, flurry);
				it.remove();
				continue;
			}
			if (level.getGameTime() < flurry.next) {
				continue;
			}
			flurry.next = level.getGameTime() + CUT_GAP;
			flurry.left--;
			boolean last = flurry.left <= 0;
			cut(level, player, foe, flurry, last);
			if (last || !foe.isAlive()) {
				finish(player, flurry);
				it.remove();
			}
		}
	}

	/** One cut across the foe: a slash from one side of it to another, the last a heavy blow and a flash of impact frames. */
	private static void cut(ServerLevel level, ServerPlayer player, LivingEntity foe, Flurry flurry, boolean last) {
		double angle = player.getRandom().nextDouble() * Math.PI;
		double half = foe.getBbWidth() * 0.9 + 0.3;
		Vec3 centre = foe.getBoundingBox().getCenter().add(0.0, (player.getRandom().nextDouble() - 0.5) * foe.getBbHeight() * 0.5, 0.0);
		Vec3 across = new Vec3(Math.cos(angle) * half, (player.getRandom().nextDouble() - 0.5) * 0.8, Math.sin(angle) * half);
		Trails.blade(level, centre.add(across), centre.subtract(across));
		level.sendParticles(ParticleTypes.SWEEP_ATTACK, centre.x, centre.y, centre.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.playSound(null, foe.getX(), foe.getY(), foe.getZ(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 0.7F, 0.9F + 0.5F * player.getRandom().nextFloat());
		CombatArts.strike(level, player, foe, Forms.damage(player, last ? LAST_CUT_DAMAGE : CUT_DAMAGE, flurry.rank));
		if (!flurry.landed.contains(foe)) {
			flurry.landed.add(foe);
		}
		if (last) {
			level.sendParticles(ParticleTypes.CRIT, centre.x, centre.y, centre.z, 24, 0.5, 0.5, 0.5, 0.4);
			level.playSound(null, foe.getX(), foe.getY(), foe.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.2F, 0.7F);
			PowersCompat.impactFrame(level, centre);
			PowersCompat.shake(player, RAIN_SHAKE);
		}
	}

	private static void finish(ServerPlayer player, Flurry flurry) {
		Forms.landed(player, RAIN_OF_CUTS, flurry.landed);
	}
}
