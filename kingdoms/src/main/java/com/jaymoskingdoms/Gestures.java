package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Leanings you can see (docs/Pawn-Plan.md row C2; after the pawns whose idle stance and victory pose show what they are
 * in Dragon's Dogma, a commercial game: ideas only).
 * <ul>
 * <li><b>Idle</b>: a companion standing about out of a fight shows its main leaning every so often ({@link #IDLE_EVERY}):
 * a guard watches outward, away from you; a hunter of bowmen scans the far distance; a medic looks over whoever near is
 * hurt; a biggest-taker shadow-boxes; a first-in looks the way you're going; a weak-clearer looks at the ground about;
 * a spoils-seeker eyes the nearest thing lying about; a backer-up keeps an eye on you.</li>
 * <li><b>A win</b>: as the fight goes quiet each one standing takes it its way ({@link TableTalk#STYLE}, else its voice):
 * loud ones leap and shake a fist, warm ones turn to you with a heart or two, quiet ones nod and kneel a moment.</li>
 * <li><b>Pointing</b> (foe and friend): one picking a new foe stops, turns to it and thrusts an arm at it for
 * {@link #POINT_TICKS} before going in, so you see it coming.</li>
 * </ul>
 */
public final class Gestures {
	/** How often an idle companion shows its leaning (ticks); how long a pointing stop lasts (ticks). */
	static final int IDLE_EVERY = 160, POINT_TICKS = 10;
	/** Not pointing again for this long (ticks). */
	static final int POINT_GAP = 100;

	private record Point(LivingEntity at, long until) {
	}

	private static final Map<Mob, Point> POINTING = new IdentityHashMap<>();
	private static final Map<UUID, Long> POINTED = new HashMap<>();
	/** A quiet one's kneel ends (game time). */
	private static final Map<Mob, Long> KNEELING = new IdentityHashMap<>();
	/** Looks held a while (a look control alone lets go after a tick or two). */
	private static final Map<Mob, Look> LOOKS = new IdentityHashMap<>();

	private record Look(@Nullable LivingEntity at, Vec3 to, long until) {
	}

	/** How long an idle or winning look is held (ticks). */
	static final int LOOK_TICKS = 40;

	private Gestures() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Gestures::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			POINTING.clear();
			POINTED.clear();
			KNEELING.clear();
			LOOKS.clear();
		});
	}

	/** (Comrades) {@code m} has picked {@code foe}: it points first. */
	static void point(Mob m, LivingEntity foe) {
		if (!(m.level() instanceof ServerLevel level)) {
			return;
		}
		long now = level.getGameTime();
		Long last = POINTED.get(m.getUUID());
		if (last != null && now - last < POINT_GAP || m.distanceTo(foe) < 4.0) {
			return;
		}
		POINTED.put(m.getUUID(), now);
		POINTING.put(m, new Point(foe, now + POINT_TICKS));
		m.getNavigation().stop();
		m.getLookControl().setLookAt(foe, 60.0F, 60.0F);
		m.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
	}

	/** (Comrades) the fight's over and {@code m} is still standing: it takes the win its way. */
	static void won(Mob m) {
		if (!(m.level() instanceof ServerLevel level) || !Temperaments.humanoid(m)) {
			return;
		}
		ServerPlayer lord = lordNear(m);
		Callouts.Voice voice = TableTalk.voice(m, "won", Callouts.voice(m));
		switch (voice) {
			case BOLD -> {
				m.getJumpControl().jump();
				m.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
			}
			case WARM -> {
				if (lord != null) {
					look(m, lord);
				}
				level.sendParticles(ParticleTypes.HEART, m.getX(), m.getY() + m.getBbHeight() + 0.3, m.getZ(), 2, 0.3, 0.2, 0.3, 0.0);
			}
			default -> {
				if (lord != null) {
					look(m, new Vec3(lord.getX(), lord.getEyeY() - 1.0, lord.getZ()));
				}
				m.setShiftKeyDown(true);
				KNEELING.put(m, level.getGameTime() + 30);
			}
		}
	}

	private static @Nullable ServerPlayer lordNear(Mob m) {
		UUID lord = Bonds.lordOf(m);
		return lord != null && m.level().getPlayerByUUID(lord) instanceof ServerPlayer p && p.distanceTo(m) < 24.0 ? p : null;
	}

	private static void tick(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		if (!POINTING.isEmpty()) {
			for (var it = POINTING.entrySet().iterator(); it.hasNext();) {
				Map.Entry<Mob, Point> e = it.next();
				Mob m = e.getKey();
				Point p = e.getValue();
				if (now >= p.until() || !m.isAlive() || !p.at().isAlive()) {
					it.remove();
					continue;
				}
				// held a moment: arm out, eyes on it
				m.getNavigation().stop();
				m.setDeltaMovement(m.getDeltaMovement().multiply(0.0, 1.0, 0.0));
				m.getLookControl().setLookAt(p.at(), 60.0F, 60.0F);
			}
		}
		if (!LOOKS.isEmpty()) {
			LOOKS.entrySet().removeIf(e -> {
				Mob m = e.getKey();
				Look l = e.getValue();
				if (now >= l.until() || !m.isAlive() || Comrades.target(m) != null || POINTING.containsKey(m)) {
					return true;
				}
				if (l.at() != null) {
					m.getLookControl().setLookAt(l.at(), 30.0F, 30.0F);
				} else {
					m.getLookControl().setLookAt(l.to().x, l.to().y, l.to().z);
				}
				return false;
			});
		}
		if (!KNEELING.isEmpty()) {
			KNEELING.entrySet().removeIf(e -> {
				if (now >= e.getValue() || !e.getKey().isAlive()) {
					if (Comrades.target(e.getKey()) == null) {
						e.getKey().setShiftKeyDown(false);
					}
					return true;
				}
				return false;
			});
		}
		if (server.getTickCount() % 20 != 0) {
			return;
		}
		POINTED.values().removeIf(t -> now - t > POINT_GAP * 4L);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			for (Mob m : Bonds.companions(player, 24.0)) {
				if ((m.getId() * 7L + now) % IDLE_EVERY < 20 && Comrades.target(m) == null && !Comrades.busy(m) && m.getNavigation().isDone()
					&& !CampSuppers.eating(m) && !m.isSleeping() && !m.isPassenger()) {
					idle(player, m);
				}
			}
		}
	}

	/** A scout ranges this far ahead of you (blocks). */
	static final double SCOUT_AHEAD = 8.0;

	private static void look(Mob m, LivingEntity at) {
		LOOKS.put(m, new Look(at, at.getEyePosition(), m.level().getGameTime() + LOOK_TICKS));
	}

	static void look(Mob m, Vec3 to) {
		LOOKS.put(m, new Look(null, to, m.level().getGameTime() + LOOK_TICKS));
	}

	/** {@code m} shows its main leaning. */
	private static void idle(ServerPlayer player, Mob m) {
		ServerLevel level = player.level();
		Vec3 eye = m.getEyePosition();
		Vec3 away = m.position().subtract(player.position()).multiply(1.0, 0.0, 1.0);
		if (away.lengthSqr() < 1.0E-4) {
			away = m.getLookAngle().multiply(1.0, 0.0, 1.0);
		}
		away = away.normalize();
		switch (Comrades.leanings(m).main()) {
			case GUARD -> {
				// watching outward, turning a little side to side
				double turn = (m.getRandom().nextDouble() - 0.5) * 1.2;
				Vec3 out = new Vec3(away.x * Math.cos(turn) - away.z * Math.sin(turn), 0.0, away.x * Math.sin(turn) + away.z * Math.cos(turn));
				look(m, new Vec3(eye.x + out.x * 8.0, eye.y, eye.z + out.z * 8.0));
			}
			case SHOOTERS -> {
				// scanning far off and a little up, where bowmen stand
				double a = m.getRandom().nextDouble() * Math.PI * 2.0;
				look(m, new Vec3(eye.x + Math.cos(a) * 30.0, eye.y + 4.0, eye.z + Math.sin(a) * 30.0));
			}
			case MEDIC -> {
				LivingEntity hurt = player.getHealth() < player.getMaxHealth() ? player : null;
				for (Mob o : Bonds.companions(player, 12.0)) {
					if (o != m && o.getHealth() < o.getMaxHealth() * 0.8F && (hurt == null || o.getHealth() / o.getMaxHealth() < hurt.getHealth() / hurt.getMaxHealth())) {
						hurt = o;
					}
				}
				if (hurt != null) {
					look(m, hurt);
				}
			}
			case BIGGEST -> {
				// shadow-boxing
				m.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
				m.swing(InteractionHand.OFF_HAND, SwingAnimation.DEFAULT, true);
			}
			case FIRST -> {
				Vec3 ahead = player.getLookAngle().multiply(1.0, 0.0, 1.0);
				look(m, new Vec3(eye.x + ahead.x * 16.0, eye.y, eye.z + ahead.z * 16.0));
			}
			case WEAK -> look(m, new Vec3(eye.x + away.x * 3.0, m.getY(), eye.z + away.z * 3.0));
			case SPOILS -> {
				ItemEntity item = null;
				for (ItemEntity i : level.getEntitiesOfClass(ItemEntity.class, m.getBoundingBox().inflate(8.0))) {
					item = i;
					break;
				}
				if (item != null) {
					look(m, item.position());
				} else {
					look(m, new Vec3(eye.x + away.x * 3.0, m.getY(), eye.z + away.z * 3.0));
				}
			}
			case BACKUP -> look(m, player);
			case SCOUT -> {
				// to the skyline, and ranging a little ahead of you when you're on the move
				Vec3 ahead = player.getLookAngle().multiply(1.0, 0.0, 1.0);
				if (ahead.lengthSqr() > 1.0E-4) {
					ahead = ahead.normalize();
					look(m, new Vec3(eye.x + ahead.x * 40.0, eye.y + 6.0, eye.z + ahead.z * 40.0));
					if (player.getDeltaMovement().horizontalDistanceSqr() > 0.004 && QuickOrders.stance(m) == null) {
						Vec3 to = player.position().add(ahead.scale(SCOUT_AHEAD));
						m.getNavigation().moveTo(to.x, to.y, to.z, 0.7);
					}
				}
			}
		}
	}
}
