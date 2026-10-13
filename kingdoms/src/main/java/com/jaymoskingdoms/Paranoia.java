package com.jaymoskingdoms;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.59) A paranoid ruler's guards ({@link Tempers.Temper#PARANOID}) suspect anyone who hangs about him of plotting
 * an attack. Suspicion builds while a stranger stays near him ({@link #CLOSE_GAIN} a second within
 * {@link #SPACE}, {@link #LINGER_GAIN} within {@link #WATCH_RANGE}), jumps when they lay hands on him (right-click,
 * {@link #HARASS_GAIN}), and cools off ({@link #COOL} a second) once they keep away. It's a ladder:
 * <ol>
 * <li>Too close: shoved back by his guard and told to keep their distance.</li>
 * <li>{@link #WARY}: watched. Town guards ({@link #HARASSERS} at most) come over, dog their steps, shove them away from
 * the king and threaten them.</li>
 * <li>{@link #DRIVE_OUT}: driven out. The guards walk them to the edge of the city ({@link #CITY_RADIUS} from the
 * seat), shoving them away from the seat, and keep them out for {@link #BAN_TICKS}.</li>
 * </ol>
 * None of it is a fight: the guards never draw on them. Only an actual attack (hitting the king or a guard) makes
 * them hostile, as anywhere. His realm's own people and his sworn wardens ({@link Wardens}) are trusted.
 */
final class Paranoia {
	/** Closer than this to him is too close: shoved back; strangers within {@link #WATCH_RANGE} are lingering. */
	static final double SPACE = 5.0, WATCH_RANGE = 12.0;
	/** Suspicion a second: within {@link #SPACE}, within {@link #WATCH_RANGE}, cooling off further away; and for laying hands on him. */
	static final int CLOSE_GAIN = 3, LINGER_GAIN = 1, COOL = 1, HARASS_GAIN = 10;
	/** Suspicion at which the guards start harassing you, and at which they drive you out of the city. */
	static final int WARY = 25, DRIVE_OUT = 60;
	/** How long the ban lasts (ticks: 5 minutes), and how far from the seat the city reaches. */
	static final int BAN_TICKS = 6000;
	static final double CITY_RADIUS = 80.0;
	/** Guards that come for one stranger at most, from how far, how close they get to shove, and how hard. */
	static final int HARASSERS = 2;
	static final double CALL_RANGE = 40.0, REACH = 2.8, SHOVE = 0.8, THROW = 1.1;
	/** A guard shoves at most this often (ticks); a warning or threat to the same player at most this often. */
	static final int SHOVE_TICKS = 30, WARN_TICKS = 200, THREAT_TICKS = 100;
	/** How fast a guard walks over (watched), and when seeing you out. */
	static final double WALK_SPEED = 0.9, DRIVE_SPEED = 1.1;
	/** How far guards' words carry. */
	static final double SPEECH_RANGE = 16.0;

	private static final class Watch {
		int suspicion;
		long bannedUntil = -1L;
		long touched;
		long warned = Long.MIN_VALUE / 2;
		long threatened = Long.MIN_VALUE / 2;

		boolean banned(long now) {
			return this.bannedUntil > now;
		}
	}

	/** "faction/player" → what his guards think of them. */
	private static final Map<String, Watch> WATCHED = new HashMap<>();
	/** Guard → the player he's been sent after. */
	private static final Map<UUID, UUID> SENT = new HashMap<>();
	/** Guard → the faction he's from (to find the player's watch). */
	private static final Map<UUID, Integer> SENT_BY = new HashMap<>();

	private Paranoia() {
	}

	static void register() {
		UseEntityCallback.EVENT.register(Paranoia::use);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			WATCHED.clear();
			SENT.clear();
			SENT_BY.clear();
		});
	}

	private static String key(int faction, UUID player) {
		return faction + "/" + player;
	}

	/** {@code p} is no stranger to him: his own people, his wardens, or not really there. */
	private static boolean trusted(ServerLevel level, Faction f, ServerPlayer p) {
		return p.isCreative() || p.isSpectator() || f.members().containsKey(p.getUUID()) || Wardens.of(level.getServer(), f.id()).contains(p.getUUID());
	}

	private static double flatDistance(Vec3 a, Vec3 b) {
		double dx = a.x - b.x, dz = a.z - b.z;
		return Math.sqrt(dx * dx + dz * dz);
	}

	private static boolean inCity(Faction f, Entity e) {
		return flatDistance(e.position(), Vec3.atBottomCenterOf(f.seat())) <= CITY_RADIUS;
	}

	/** Laying hands on him (right-clicking him) is harassment. */
	private static InteractionResult use(net.minecraft.world.entity.player.Player player, net.minecraft.world.level.Level world, InteractionHand hand,
		Entity entity, net.minecraft.world.phys.@Nullable EntityHitResult hit) {
		if (world.isClientSide() || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer p) || !(entity instanceof Folk king)
			|| !(world instanceof ServerLevel level)) {
			return InteractionResult.PASS;
		}
		Faction f = Factions.get(level.getServer(), king.factionId());
		if (f == null || Tempers.of(f) != Tempers.Temper.PARANOID || !f.leader().map(king.getUUID()::equals).orElse(false) || trusted(level, f, p)) {
			return InteractionResult.PASS;
		}
		long now = level.getGameTime();
		Watch w = WATCHED.computeIfAbsent(key(f.id(), p.getUUID()), k -> new Watch());
		w.suspicion = Math.min(DRIVE_OUT, w.suspicion + HARASS_GAIN);
		w.touched = now;
		Folk guard = nearestGuard(level, f, p, SPACE * 2);
		Speech.say(level, guard != null ? guard : king, Component.translatable("speech.jaymoskingdoms.paranoid.hands"), Speech.RED, SPEECH_RANGE);
		shove(level, guard, p, p.position().subtract(king.position()), SHOVE);
		return InteractionResult.PASS;
	}

	/** Every {@link Tempers#TICK} for a paranoid realm whose seat is loaded; {@code king} is null while he's not about. */
	static void tick(ServerLevel level, Faction f, @Nullable Folk king, long now) {
		if (king != null) {
			for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, king.getBoundingBox().inflate(WATCH_RANGE))) {
				if (trusted(level, f, p)) {
					continue;
				}
				double d = p.distanceTo(king);
				if (d > WATCH_RANGE) {
					continue;
				}
				Watch w = WATCHED.computeIfAbsent(key(f.id(), p.getUUID()), k -> new Watch());
				w.touched = now;
				w.suspicion = Math.min(DRIVE_OUT, w.suspicion + (d <= SPACE ? CLOSE_GAIN : LINGER_GAIN));
				if (d <= SPACE) {
					// too close: shoved back by whichever of his guards is nearest
					Folk guard = nearestGuard(level, f, p, SPACE);
					shove(level, guard, p, p.position().subtract(king.position()), SHOVE);
					if (now - w.warned >= WARN_TICKS) {
						w.warned = now;
						p.sendSystemMessage(Component.translatable("message.jaymoskingdoms.paranoid_warning", king.getDisplayName()).withStyle(ChatFormatting.RED));
					}
				}
				if (!w.banned(now) && w.suspicion >= DRIVE_OUT) {
					banish(level, f, king, p, w, now);
				} else if (!w.banned(now) && w.suspicion >= WARY && w.suspicion - (d <= SPACE ? CLOSE_GAIN : LINGER_GAIN) < WARY) {
					p.sendSystemMessage(Component.translatable("message.jaymoskingdoms.paranoid.watched", king.getDisplayName()).withStyle(ChatFormatting.RED));
				}
			}
		}
		String prefix = f.id() + "/";
		for (Iterator<Map.Entry<String, Watch>> it = WATCHED.entrySet().iterator(); it.hasNext();) {
			Map.Entry<String, Watch> e = it.next();
			if (!e.getKey().startsWith(prefix)) {
				continue;
			}
			Watch w = e.getValue();
			UUID id = UUID.fromString(e.getKey().substring(prefix.length()));
			ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
			if (w.bannedUntil >= 0 && !w.banned(now)) {
				// the ban's run out: a clean slate
				it.remove();
				if (p != null) {
					p.sendSystemMessage(Component.translatable("message.jaymoskingdoms.paranoid.forgiven", f.name()).withStyle(ChatFormatting.GRAY));
				}
				continue;
			}
			if (!w.banned(now) && w.touched != now) {
				w.suspicion -= COOL;
				if (w.suspicion <= 0) {
					it.remove();
					continue;
				}
			}
			if (p == null || p.level() != level || trusted(level, f, p)) {
				continue;
			}
			if (w.banned(now) ? inCity(f, p) : w.suspicion >= WARY && king != null && p.distanceTo(king) <= WATCH_RANGE * 2) {
				send(level, f, p);
			}
		}
		// guards whose errand is over go back to their posts
		for (Iterator<Map.Entry<UUID, UUID>> it = SENT.entrySet().iterator(); it.hasNext();) {
			Map.Entry<UUID, UUID> e = it.next();
			Integer from = SENT_BY.get(e.getKey());
			if (from == null || from != f.id()) {
				continue;
			}
			if (!(level.getEntity(e.getKey()) instanceof Folk g) || !g.isAlive() || !wanted(level, f, g, e.getValue(), now)) {
				SENT_BY.remove(e.getKey());
				it.remove();
			}
		}
	}

	/** {@code player} is still someone {@code guard} should be dogging. */
	private static boolean wanted(ServerLevel level, Faction f, Folk guard, UUID player, long now) {
		Watch w = WATCHED.get(key(f.id(), player));
		ServerPlayer p = level.getServer().getPlayerList().getPlayer(player);
		if (w == null || p == null || p.level() != level || !p.isAlive() || trusted(level, f, p)) {
			return false;
		}
		return w.banned(now) ? inCity(f, p) : w.suspicion >= WARY;
	}

	private static void banish(ServerLevel level, Faction f, Folk king, ServerPlayer p, Watch w, long now) {
		w.bannedUntil = now + BAN_TICKS;
		w.threatened = now;
		p.sendSystemMessage(Component.translatable("message.jaymoskingdoms.paranoid.banished", king.getDisplayName(), f.name(), BAN_TICKS / 1200)
			.withStyle(ChatFormatting.DARK_RED));
		Speech.say(level, king, Component.translatable("speech.jaymoskingdoms.paranoid.seize"), Speech.RED, SPEECH_RANGE);
		level.playSound(null, king.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 1.0F, 0.8F);
	}

	/** Sends up to {@link #HARASSERS} of the realm's town guards after {@code p} (the nearest, not at his back). */
	private static void send(ServerLevel level, Faction f, ServerPlayer p) {
		int already = 0;
		for (UUID target : SENT.values()) {
			if (target.equals(p.getUUID())) {
				already++;
			}
		}
		if (already >= HARASSERS) {
			return;
		}
		List<Folk> guards = level.getEntitiesOfClass(Folk.class, p.getBoundingBox().inflate(CALL_RANGE),
			g -> g.isAlive() && g.factionId() == f.id() && g.role() == Folk.GUARD && g.escort() == null && g.getTarget() == null
				&& !SENT.containsKey(g.getUUID()));
		guards.sort((a, b) -> Double.compare(a.distanceToSqr(p), b.distanceToSqr(p)));
		for (Folk g : guards) {
			if (already >= HARASSERS) {
				break;
			}
			SENT.put(g.getUUID(), p.getUUID());
			SENT_BY.put(g.getUUID(), f.id());
			already++;
		}
	}

	private static @Nullable Folk nearestGuard(ServerLevel level, Faction f, ServerPlayer p, double range) {
		Folk best = null;
		for (Folk g : level.getEntitiesOfClass(Folk.class, p.getBoundingBox().inflate(range),
			g -> g.isAlive() && g.factionId() == f.id() && g.role() == Folk.GUARD && g.getTarget() == null)) {
			if (best == null || g.distanceToSqr(p) < best.distanceToSqr(p)) {
				best = g;
			}
		}
		return best;
	}

	/** {@code p} is shoved along {@code away} (flat), by {@code guard} if one's at hand. */
	private static void shove(ServerLevel level, @Nullable Folk guard, ServerPlayer p, Vec3 away, double strength) {
		Vec3 flat = away.multiply(1.0, 0.0, 1.0);
		if (flat.lengthSqr() < 1.0E-4) {
			flat = new Vec3(1.0, 0.0, 0.0);
		}
		flat = flat.normalize();
		if (guard != null) {
			guard.getLookControl().setLookAt(p, 30.0F, 30.0F);
			guard.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		}
		p.push(flat.x * strength, 0.25, flat.z * strength);
		p.syncVelocity = true;
		level.playSound(null, p.blockPosition(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.NEUTRAL, 0.7F, 1.0F);
	}

	/**
	 * A town guard sent after a suspect ({@link #send}): he walks up to them and shoves them along, away from the king
	 * (watched) or away from the seat (driven out), with a word now and then. He never draws on them.
	 */
	static final class Harass extends Goal {
		private final Folk guard;
		private long shoved = Long.MIN_VALUE / 2;

		Harass(Folk guard) {
			this.guard = guard;
			this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		private @Nullable ServerPlayer suspect() {
			UUID id = SENT.get(this.guard.getUUID());
			if (id == null || !(this.guard.level() instanceof ServerLevel level)) {
				return null;
			}
			ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
			return p != null && p.level() == level && p.isAlive() ? p : null;
		}

		@Override
		public boolean canUse() {
			return this.guard.getTarget() == null && this.guard.escort() == null && this.suspect() != null;
		}

		@Override
		public boolean canContinueToUse() {
			return this.canUse();
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void stop() {
			this.guard.getNavigation().stop();
		}

		@Override
		public void tick() {
			ServerPlayer p = this.suspect();
			Integer from = SENT_BY.get(this.guard.getUUID());
			if (p == null || from == null || !(this.guard.level() instanceof ServerLevel level)) {
				return;
			}
			Faction f = Factions.get(level.getServer(), from);
			Watch w = WATCHED.get(key(from, p.getUUID()));
			if (f == null || w == null) {
				return;
			}
			long now = level.getGameTime();
			boolean out = w.banned(now);
			this.guard.getLookControl().setLookAt(p, 30.0F, 30.0F);
			double d = this.guard.distanceTo(p);
			if (d > REACH) {
				if (this.guard.getNavigation().isDone() || this.guard.tickCount % 10 == 0) {
					this.guard.getNavigation().moveTo(p, out ? DRIVE_SPEED : WALK_SPEED);
				}
				return;
			}
			this.guard.getNavigation().stop();
			if (now - this.shoved < SHOVE_TICKS) {
				return;
			}
			this.shoved = now;
			Vec3 away;
			if (out) {
				away = p.position().subtract(Vec3.atBottomCenterOf(f.seat()));
			} else {
				Entity king = f.leader().map(level::getEntity).orElse(null);
				away = p.position().subtract(king != null ? king.position() : this.guard.position());
			}
			shove(level, this.guard, p, away, out ? THROW : SHOVE);
			level.sendParticles(ParticleTypes.ANGRY_VILLAGER, this.guard.getX(), this.guard.getY() + 2.1, this.guard.getZ(), 1, 0.2, 0.1, 0.2, 0.0);
			if (now - w.threatened >= THREAT_TICKS) {
				w.threatened = now;
				String line = (out ? "speech.jaymoskingdoms.paranoid.out" : "speech.jaymoskingdoms.paranoid.watch") + level.getRandom().nextInt(4);
				Speech.say(level, this.guard, Component.translatable(line), Speech.RED, SPEECH_RANGE);
			}
		}
	}
}
