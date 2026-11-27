package com.jaymospowers.power.web;

import com.jaymospowers.core.fx.MagicFx;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Web storm (Back + Jump + C, once every ability is unlocked, the pull-in partner of {@link WebBlossom}, which throws outward):
 * spin a dome of web lines out from both wrists to the blocks around you ({@link WebPose#WEB_STORM}), drawing the creatures that are
 * a fight for you in toward you for {@link #PULL_TICKS}, then burst the dome and web them all
 * ({@link WebStrength#cocoon}). It needs {@link #LEAST_ANCHORS} blocks within {@link #REACH} to hold the dome from, and
 * (like a blossom) a shot of a shooter's fluid unless the suit's own mass does it. Bosses and
 * anything too big to haul ({@link WebHaul#haulable}) are not drawn in.
 */
public final class WebStorm {
	/** How far the dome reaches (blocks), how many lines it casts to find its blocks, and the fewest it must find. */
	static final double REACH = 12.0;
	static final int CASTS = 20, LEAST_ANCHORS = 4;
	/** How far it sees foes (blocks), how many it draws in, how long it pulls (ticks), the wait after (ticks). */
	static final double SEES = 12.0;
	static final int MAX_FOES = 12, PULL_TICKS = 24, COOLDOWN = 240;
	/** The pull: its top speed (blocks/tick), the share of the gap it asks for, and how near a foe is held (blocks). */
	static final double PULL_SPEED = 0.5, PULL_SHARE = 0.2, HOLD = 2.0;
	/** The burst: how far it webs (blocks), for how long (ticks), the damage, and the strays of cobweb left. */
	static final double BURST = 6.0;
	static final int BURST_TICKS = 60, STRAYS = 10;
	static final float BURST_DAMAGE = 3.0F;

	private record Live(List<Vec3> anchors, List<Integer> foes, long start, boolean dark) {
	}

	private static final Map<UUID, Live> LIVE = new HashMap<>();
	private static final Map<UUID, Long> READY = new HashMap<>();

	private WebStorm() {
	}

	/** The dome: true if it was cast; false (nothing used) when there is no shot, no anchors or nobody to draw in, so the key goes on to what it did before. */
	static boolean use(ServerPlayer player) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		if (LIVE.containsKey(player.getUUID()) || now < READY.getOrDefault(player.getUUID(), 0L) || WebPower.ragdolled(player)) {
			return false;
		}
		boolean dark = WebSymbiote.suitShown(player) || WebSymbiote.isToggled(player, WebSymbioteState.ARMOR);
		if (!dark && WebShooters.shotHand(player) < 0) {
			return false;
		}
		Vec3 centre = player.position().add(0.0, player.getBbHeight() * 0.6, 0.0);
		List<Vec3> anchors = new ArrayList<>();
		for (int i = 0; i < CASTS; i++) {
			double a = i * Math.PI * 2.0 / CASTS + level.getRandom().nextDouble() * 0.2;
			double e = 0.15 + level.getRandom().nextDouble() * 0.9;
			Vec3 dir = new Vec3(Math.cos(a) * Math.cos(e), Math.sin(e), Math.sin(a) * Math.cos(e));
			BlockHitResult hit = WebAnchors.clip(player, centre, centre.add(dir.scale(REACH)));
			if (hit.getType() == HitResult.Type.BLOCK) {
				anchors.add(hit.getLocation());
			}
		}
		List<LivingEntity> found = new ArrayList<>();
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(centre, centre).inflate(SEES), e -> WebBlossom.fight(player, e) && WebHaul.haulable(e))) {
			if (e.distanceToSqr(player) <= SEES * SEES && player.hasLineOfSight(e)) {
				found.add(e);
			}
		}
		if (anchors.size() < LEAST_ANCHORS || found.isEmpty()) {
			return false;
		}
		if (!dark) {
			WebShooters.fire(player, WebShooters.shotHand(player), false);
		}
		found.sort(Comparator.comparingDouble(e -> e.distanceToSqr(player)));
		List<Integer> ids = new ArrayList<>();
		for (LivingEntity e : found.subList(0, Math.min(MAX_FOES, found.size()))) {
			ids.add(e.getId());
		}
		READY.put(player.getUUID(), now + COOLDOWN);
		LIVE.put(player.getUUID(), new Live(anchors, ids, now, dark));
		WebPower.pose(player, WebPose.WEB_STORM, 1);
		level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.PLAYERS, 1.0F, 0.9F);
		return true;
	}

	/** Every tick, per web slinger. */
	static void tick(ServerPlayer player) {
		Live live = LIVE.get(player.getUUID());
		if (live == null) {
			return;
		}
		ServerLevel level = player.level();
		long t = level.getGameTime() - live.start();
		if (!player.isAlive() || player.isSpectator()) {
			LIVE.remove(player.getUUID());
			WebStrikeLines.clear(player);
			return;
		}
		Vec3 hand = player.position().add(0.0, player.getBbHeight() * 0.65, 0.0);
		if (t >= PULL_TICKS) {
			burst(level, player, live);
			return;
		}
		// the dome spins out: two lines a tick, going round the blocks, and a few strands drawn
		int n = live.anchors().size();
		Vec3 a = live.anchors().get((int)((t * 2) % n)), b = live.anchors().get((int)((t * 2 + 1) % n));
		WebStrikeLines.show(player, a, -1, b, -1);
		for (int k = 0; k < 4; k++) {
			Vec3 end = live.anchors().get((int)((t * 3 + k * 5) % n));
			for (int i = 1; i <= 6; i++) {
				Vec3 p = hand.lerp(end, i / 6.0);
				level.sendParticles(live.dark() ? WebSymbiote.dust(1.0F) : ParticleTypes.ITEM_COBWEB, p.x, p.y, p.z, 1, 0.03, 0.03, 0.03, 0.0);
			}
		}
		// and everything it has caught is drawn in
		for (int id : live.foes()) {
			if (!(level.getEntity(id) instanceof LivingEntity foe) || !foe.isAlive()) {
				continue;
			}
			Vec3 gap = player.position().subtract(foe.position());
			double d = gap.length();
			if (d > HOLD) {
				WebHaul.push(foe, gap.scale(Math.min(PULL_SPEED, (d - HOLD) * PULL_SHARE) / d));
				foe.resetFallDistance();
			}
		}
		if (t % 5 == 0) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.PLAYERS, 0.6F, 0.8F + 0.04F * t);
		}
	}

	/** The dome bursts: every foe near is webbed, hurt and left with strays of cobweb about. */
	private static void burst(ServerLevel level, ServerPlayer player, Live live) {
		LIVE.remove(player.getUUID());
		WebStrikeLines.clear(player);
		Vec3 at = player.position().add(0.0, player.getBbHeight() * 0.5, 0.0);
		for (int id : live.foes()) {
			if (level.getEntity(id) instanceof LivingEntity foe && foe.isAlive() && foe.distanceToSqr(player) <= BURST * BURST) {
				foe.setInvulnerableTime(0);
				foe.hurtServer(level, player.damageSources().mobAttack(player), BURST_DAMAGE);
				if (foe.isAlive()) {
					WebStrength.cocoon(level, foe, BURST_TICKS, live.dark());
				}
			}
		}
		WebTraps.scatter(level, player.position(), BURST, STRAYS, live.dark());
		level.sendParticles(live.dark() ? WebSymbiote.dust(1.4F) : ParticleTypes.ITEM_COBWEB, at.x, at.y, at.z, 70, BURST * 0.3, 0.8, BURST * 0.3, 0.2);
		MagicFx.ring(level, player.position(), (float)BURST, 0xF2F0E4);
		MagicFx.blast(level, at, 1.6F, 0xF2F0E4);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.6F, 1.6F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.COBWEB_PLACE, SoundSource.PLAYERS, 1.2F, 0.6F);
		com.jaymospowers.core.Shakes.around(level, at, 12.0, 0.7F);
	}

	static void forget(ServerPlayer player) {
		LIVE.remove(player.getUUID());
		READY.remove(player.getUUID());
	}

	static void clearAll() {
		LIVE.clear();
		READY.clear();
	}
}
