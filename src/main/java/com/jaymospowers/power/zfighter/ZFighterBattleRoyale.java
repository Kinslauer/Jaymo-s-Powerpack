package com.jaymospowers.power.zfighter;

import com.jaymospowers.core.gifted.Gifted;
import com.jaymospowers.core.wounds.Downed;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.17.60, Andy's NOEA picks) The last-one-standing tournament. It opens like the bracket ({@link ZFighterTournaments}: the
 * ring beside the kingdom's seat, a sign-up while you stand in it), but when sign-up ends everyone who entered, any gifted
 * Z Fighter villager standing in the ring ({@link #GIFTED_MAX}) and champions of the kingdom to fill the field up to
 * {@link #FIELD_MIN} ({@link #FIELD_MAX} at most) are set round the ring and, after a {@link #COUNTDOWN} countdown, all fight at
 * once. A fighter is out when he steps or is knocked out of the ring, is beaten down (his death is turned into half a heart,
 * as in the bracket), is downed, or has his poise broken twice running ({@link ZFighterPoise#knockedOut}); an eliminated
 * player is healed and set down outside the ring. After {@link #SHRINK_FROM} ticks the ring closes in, down to
 * {@link #MIN_RADIUS}, and at {@link #FIGHT_TICKS} whoever is least hurt wins. Only the fighters can hurt each other. The last
 * one in wins the bracket's prize ({@link ZFighterTournaments#WIN_TP}), the runner-up a bout's, and every fighter who put
 * another out ({@link #KO_TP} each).
 */
final class ZFighterBattleRoyale {
	/** Champions fill the field up to this many fighters; no more than {@code FIELD_MAX} in all (sign-up caps players at 8). */
	static final int FIELD_MIN = 6;
	static final int FIELD_MAX = 10;
	/** Gifted Z Fighter villagers in the ring who may be entered. */
	static final int GIFTED_MAX = 2;
	/** Ticks of countdown before the fight (nobody can be hurt), and the fight's length. */
	static final int COUNTDOWN = 100;
	static final int FIGHT_TICKS = 6000;
	/** The ring starts closing this long into the fight (ticks), down to {@code MIN_RADIUS} blocks at its end. */
	static final int SHRINK_FROM = 2400;
	static final double MIN_RADIUS = 3.5;
	/** How far past the ring's line counts as out (blocks). */
	static final double MARGIN = 1.5;
	/** Fighters start this far in from the line (blocks). */
	static final double START_INSET = 1.5;
	/** A blow counts as the cause of an elimination for this long after it (ticks). */
	static final int CREDIT_TICKS = 100;
	/** What each fighter who put another out earns. */
	static final int KO_TP = 60;
	static final int KO_EMERALDS = 1;
	/** Eliminated players are set down this far from the middle (blocks). */
	static final double SIDELINE = ZFighterTournaments.RADIUS + 3.0;
	/** Champions and villagers pick a new foe this often (ticks). */
	static final int RETARGET = 10;
	static final int RING_COLOR = ZFighterTournaments.RING_COLOR;
	static final int CLOSING_COLOR = 0xFF6050;

	private static final class Fighter {
		final UUID id;
		final Component name;
		final boolean player;
		/** 0 while in; otherwise the place he finished (1 the winner). */
		int place;
		String how = "";
		Component by = Component.empty();
		int knockouts;

		Fighter(UUID id, Component name, boolean player) {
			this.id = id;
			this.name = name;
			this.player = player;
		}
	}

	private record Hit(UUID by, long at) {
	}

	private static final class Royale {
		final ServerLevel level;
		final String kingdom;
		final BlockPos center;
		final List<Fighter> fighters = new ArrayList<>();
		final Map<UUID, Hit> hits = new HashMap<>();
		long startAt;
		long until;

		Royale(ServerLevel level, String kingdom, BlockPos center) {
			this.level = level;
			this.kingdom = kingdom;
			this.center = center;
		}

		int alive() {
			int n = 0;
			for (Fighter f : fighters) {
				if (f.place == 0) {
					n++;
				}
			}
			return n;
		}

		@Nullable Fighter fighter(UUID id) {
			for (Fighter f : fighters) {
				if (f.id.equals(id)) {
					return f;
				}
			}
			return null;
		}

		@Nullable Fighter inFight(UUID id) {
			Fighter f = fighter(id);
			return f != null && f.place == 0 ? f : null;
		}
	}

	private static @Nullable Royale current;
	private static ZFighterBracketNet.Board lastBoard = ZFighterBracketNet.Board.NONE;

	private ZFighterBattleRoyale() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(ZFighterBattleRoyale::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			if (current != null) {
				release(current, null);
			}
			current = null;
		});
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			Royale r = current;
			if (r == null) {
				return true;
			}
			Entity attacker = source.getEntity();
			Fighter victim = r.inFight(entity.getUUID());
			Fighter hitter = attacker == null ? null : r.inFight(attacker.getUUID());
			if (victim == null) {
				// a bystander in the ring's way is not to be hurt by a fighter's ki
				return hitter == null || !(entity instanceof ServerPlayer);
			}
			if (r.level.getGameTime() < r.startAt) {
				return false;
			}
			return attacker == null || hitter != null && hitter != victim;
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			Royale r = current;
			Entity attacker = source.getEntity();
			if (r != null && attacker != null && damageTaken > 0.0F && r.fighter(entity.getUUID()) != null && r.fighter(attacker.getUUID()) != null) {
				r.hits.put(entity.getUUID(), new Hit(attacker.getUUID(), r.level.getGameTime()));
			}
		});
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			Royale r = current;
			Fighter f = r == null ? null : r.inFight(entity.getUUID());
			if (f == null) {
				return true;
			}
			// beaten down, never killed
			entity.setHealth(1.0F);
			Entity attacker = source.getEntity();
			Fighter by = attacker == null ? credit(r, f) : r.fighter(attacker.getUUID());
			eliminate(r, f, "beaten", by == f ? null : by);
			return false;
		});
	}

	/** Whether a fight is on (or counting down). */
	static boolean active() {
		return current != null;
	}

	/** Whether this entity is a fighter in it (so a champion of the field is not sent away). */
	static boolean fighting(UUID id) {
		Royale r = current;
		return r != null && r.inFight(id) != null;
	}

	/** The final board of the last fight to end. */
	static ZFighterBracketNet.Board lastBoard() {
		return lastBoard;
	}

	/** Sign-up is over: the field is set round the ring and the countdown begins. Cancelled if no player is left to fight. */
	static void start(ServerLevel level, String kingdom, BlockPos center, List<UUID> players) {
		Royale r = new Royale(level, kingdom, center);
		List<Entity> bodies = new ArrayList<>();
		for (UUID id : players) {
			ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
			if (player != null && player.isAlive() && player.level() == level && !player.isSpectator()
				&& player.distanceToSqr(Vec3.atCenterOf(center)) <= ZFighterTournaments.NEAR * ZFighterTournaments.NEAR) {
				r.fighters.add(new Fighter(player.getUUID(), player.getName(), true));
				bodies.add(player);
			}
		}
		if (r.fighters.isEmpty()) {
			level.getServer().getPlayerList().broadcastSystemMessage(Component.translatable("message.jaymospowers.zfighter_tournament_off", kingdom)
				.withStyle(ChatFormatting.GRAY), false);
			return;
		}
		// gifted Z Fighter villagers standing in the ring
		AABB box = new AABB(Vec3.atBottomCenterOf(center), Vec3.atBottomCenterOf(center)).inflate(ZFighterTournaments.RADIUS + 3.0, ZFighterTournaments.RING_LOW, ZFighterTournaments.RADIUS + 3.0);
		int gifted = 0;
		for (PathfinderMob mob : level.getEntitiesOfClass(PathfinderMob.class, box, m -> m.isAlive() && !m.isBaby() && "zfighter".equals(Gifted.power(m)))) {
			if (gifted++ >= GIFTED_MAX) {
				break;
			}
			r.fighters.add(new Fighter(mob.getUUID(), mob.getDisplayName(), false));
			bodies.add(mob);
		}
		int field = Math.min(FIELD_MAX, Math.max(FIELD_MIN, r.fighters.size()));
		List<String> names = new ArrayList<>(List.of(ZFighterTournaments.CHAMPIONS));
		Collections.shuffle(names, new java.util.Random(level.getRandom().nextLong()));
		for (int i = 0; r.fighters.size() < field; i++) {
			Component name = Component.translatable("entity.jaymospowers.rogue_fighter.champion." + names.get(i % names.size()));
			ZFighterRogue champion = ZFighterRaids.ROGUE.create(level, EntitySpawnReason.EVENT);
			if (champion == null) {
				break;
			}
			champion.become(ZFighterRogue.CHAMPION, name);
			r.fighters.add(new Fighter(champion.getUUID(), name, false));
			bodies.add(champion);
		}
		// set them round the ring, in a shuffled order so the players are not all side by side
		List<Integer> slots = new ArrayList<>();
		for (int i = 0; i < bodies.size(); i++) {
			slots.add(i);
		}
		Collections.shuffle(slots, new java.util.Random(level.getRandom().nextLong()));
		for (int i = 0; i < bodies.size(); i++) {
			double angle = slots.get(i) * Math.PI * 2.0 / bodies.size();
			Vec3 spot = Vec3.atBottomCenterOf(center).add(Math.cos(angle) * (ZFighterTournaments.RADIUS - START_INSET), 0.0,
				Math.sin(angle) * (ZFighterTournaments.RADIUS - START_INSET));
			float yaw = (float)(Math.toDegrees(Math.atan2(-(spot.x - center.getX() - 0.5), spot.z - center.getZ() - 0.5)));
			Entity body = bodies.get(i);
			if (body instanceof ServerPlayer player) {
				player.teleportTo(level, spot.x, spot.y, spot.z, Set.of(), yaw, 0.0F, false);
				player.setHealth(player.getMaxHealth());
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_royale_ready").withStyle(ChatFormatting.GOLD));
			} else if (body instanceof ZFighterRogue champion) {
				double health = ZFighterRogue.CHAMPION_HEALTH;
				champion.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
				champion.setHealth((float)health);
				champion.snapTo(spot.x, spot.y, spot.z, yaw, 0.0F);
				level.addFreshEntity(champion);
				level.sendParticles(ParticleTypes.CLOUD, spot.x, spot.y + 1.0, spot.z, 12, 0.3, 0.6, 0.3, 0.05);
			} else if (body instanceof LivingEntity living) {
				// a gifted villager steps in whole
				living.setHealth(living.getMaxHealth());
				living.snapTo(spot.x, spot.y, spot.z, yaw, 0.0F);
				if (living instanceof Mob mob) {
					mob.getNavigation().stop();
				}
			}
		}
		long now = level.getGameTime();
		r.startAt = now + COUNTDOWN;
		r.until = r.startAt + FIGHT_TICKS;
		current = r;
		say(r, Component.translatable("message.jaymospowers.zfighter_royale_begins", kingdom, r.fighters.size()), ChatFormatting.GOLD);
		level.playSound(null, center, SoundEvents.BELL_BLOCK, SoundSource.NEUTRAL, 3.0F, 0.8F);
		push(r);
		for (Fighter f : r.fighters) {
			if (f.player) {
				ServerPlayer player = level.getServer().getPlayerList().getPlayer(f.id);
				if (player != null) {
					ZFighterBracketNet.open(player, board(r));
				}
			}
		}
	}

	private static void tick(MinecraftServer server) {
		Royale r = current;
		if (r == null) {
			return;
		}
		ServerLevel level = r.level;
		long now = level.getGameTime();
		if (now % 5 == 0) {
			ring(r, now);
		}
		if (now < r.startAt) {
			if ((r.startAt - now) % 20 == 0) {
				int seconds = (int)((r.startAt - now) / 20);
				for (Fighter f : r.fighters) {
					ServerPlayer player = f.player ? server.getPlayerList().getPlayer(f.id) : null;
					if (player != null) {
						player.sendOverlayMessage(Component.literal(Integer.toString(seconds)).withStyle(ChatFormatting.YELLOW));
					}
				}
				level.playSound(null, r.center, SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.NEUTRAL, 2.0F, 1.0F);
			}
			return;
		}
		if (now == r.startAt) {
			say(r, Component.translatable("message.jaymospowers.zfighter_royale_fight"), ChatFormatting.RED);
			level.playSound(null, r.center, SoundEvents.RAID_HORN.value(), SoundSource.NEUTRAL, 6.0F, 1.4F);
			push(r);
		}
		double radius = radius(r, now);
		Vec3 c = Vec3.atBottomCenterOf(r.center);
		for (Fighter f : new ArrayList<>(r.fighters)) {
			if (f.place != 0) {
				continue;
			}
			if (r.alive() <= 1) {
				break;
			}
			LivingEntity body = body(r, f);
			if (body == null) {
				eliminate(r, f, "gone", credit(r, f));
				continue;
			}
			double dx = body.getX() - c.x, dz = body.getZ() - c.z;
			if (dx * dx + dz * dz > (radius + MARGIN) * (radius + MARGIN) || body.getY() > c.y + ZFighterTournaments.RING_HIGH
				|| body.getY() < c.y - ZFighterTournaments.RING_LOW) {
				eliminate(r, f, "ring_out", credit(r, f));
			} else if (Downed.isDowned(body)) {
				eliminate(r, f, "downed", credit(r, f));
			} else if (ZFighterPoise.knockedOut(body)) {
				eliminate(r, f, "ko", credit(r, f));
			}
		}
		if (r.alive() <= 1) {
			end(r, null);
			return;
		}
		if (now >= r.until) {
			Fighter best = null;
			float most = -1.0F;
			for (Fighter f : r.fighters) {
				LivingEntity body = f.place == 0 ? body(r, f) : null;
				if (body != null && body.getHealth() / body.getMaxHealth() > most) {
					most = body.getHealth() / body.getMaxHealth();
					best = f;
				}
			}
			end(r, best);
			return;
		}
		if (now % RETARGET == 0) {
			retarget(r);
		}
		if (now % 100 == 0) {
			push(r);
		}
	}

	// ---------------------------------------------------------------- the ring

	/** The ring's line: the arena's, closing in over the second half. */
	private static double radius(Royale r, long now) {
		long from = r.startAt + SHRINK_FROM;
		if (now <= from) {
			return ZFighterTournaments.RADIUS;
		}
		double t = Math.min(1.0, (now - from) / (double)Math.max(1L, r.until - from));
		return ZFighterTournaments.RADIUS + (MIN_RADIUS - ZFighterTournaments.RADIUS) * t;
	}

	private static void ring(Royale r, long now) {
		Vec3 c = Vec3.atBottomCenterOf(r.center);
		double radius = radius(r, now);
		boolean closing = radius < ZFighterTournaments.RADIUS - 0.01;
		DustParticleOptions dust = new DustParticleOptions(closing ? CLOSING_COLOR : RING_COLOR, 1.6F);
		int points = (int)Math.max(24, radius * 7);
		for (int i = 0; i < points; i++) {
			double a = i * Math.PI * 2.0 / points;
			r.level.sendParticles(dust, true, false, c.x + Math.cos(a) * radius, c.y + 0.15, c.z + Math.sin(a) * radius, 1, 0.0, 0.0, 0.0, 0.0);
		}
	}

	// ---------------------------------------------------------------- the fighters

	private static @Nullable LivingEntity body(Royale r, Fighter f) {
		ServerPlayer player = r.level.getServer().getPlayerList().getPlayer(f.id);
		if (player != null) {
			return player.isAlive() && player.level() == r.level && !player.isSpectator() ? player : null;
		}
		return !f.player && r.level.getEntity(f.id) instanceof LivingEntity living && living.isAlive() ? living : null;
	}

	/** Champions and gifted villagers go for the nearest fighter still in. */
	private static void retarget(Royale r) {
		for (Fighter f : r.fighters) {
			if (f.place != 0 || f.player || !(body(r, f) instanceof Mob mob)) {
				continue;
			}
			LivingEntity target = mob.getTarget();
			Fighter held = target == null ? null : r.inFight(target.getUUID());
			if (held != null && target.isAlive() && !Downed.isDowned(target) && !ZFighterPoise.stunned(target)) {
				continue;
			}
			LivingEntity nearest = null;
			double best = Double.MAX_VALUE;
			for (Fighter other : r.fighters) {
				if (other == f || other.place != 0) {
					continue;
				}
				LivingEntity foe = body(r, other);
				if (foe != null && !Downed.isDowned(foe)) {
					// one who is staggered is a poorer target than one on his feet
					double d = mob.distanceToSqr(foe) + (ZFighterPoise.stunned(foe) ? 100.0 : 0.0);
					if (d < best) {
						best = d;
						nearest = foe;
					}
				}
			}
			mob.setTarget(nearest);
			if (nearest != null && !(mob instanceof ZFighterRogue)) {
				mob.getNavigation().moveTo(nearest, 1.0);
			}
		}
	}

	/** Who put {@code victim} out: whoever of the field hit him last, lately. */
	private static @Nullable Fighter credit(Royale r, Fighter victim) {
		Hit hit = r.hits.get(victim.id);
		return hit != null && r.level.getGameTime() - hit.at() <= CREDIT_TICKS ? r.fighter(hit.by()) : null;
	}

	private static void eliminate(Royale r, Fighter f, String how, @Nullable Fighter by) {
		if (f.place != 0) {
			return;
		}
		f.place = r.alive();
		f.how = how;
		if (by != null && by != f) {
			f.by = by.name;
			by.knockouts++;
		}
		LivingEntity body = body(r, f);
		if (body != null) {
			if (Downed.isDowned(body)) {
				Downed.getUp(r.level, body);
			}
			body.setHealth(body.getMaxHealth());
			if (body instanceof Mob mob) {
				mob.setTarget(null);
				mob.getNavigation().stop();
			}
			r.level.sendParticles(ParticleTypes.POOF, body.getX(), body.getY() + 0.5, body.getZ(), 12, 0.3, 0.4, 0.3, 0.05);
			if (body instanceof ZFighterRogue) {
				body.discard();
			} else {
				sideline(r, body);
			}
		}
		Component message = by != null && by != f
			? Component.translatable("message.jaymospowers.zfighter_royale_out_by", f.name, Component.translatable("screen.jaymospowers.zfighter_bracket.how." + how), by.name, r.alive())
			: Component.translatable("message.jaymospowers.zfighter_royale_out", f.name, Component.translatable("screen.jaymospowers.zfighter_bracket.how." + how), r.alive());
		say(r, message, ChatFormatting.YELLOW);
		r.level.playSound(null, r.center, SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, 1.5F, 0.6F);
		push(r);
	}

	/** Set down just outside the ring, on the side he was on. */
	private static void sideline(Royale r, LivingEntity body) {
		Vec3 c = Vec3.atBottomCenterOf(r.center);
		double dx = body.getX() - c.x, dz = body.getZ() - c.z;
		double length = Math.sqrt(dx * dx + dz * dz);
		if (length < 1.0E-3) {
			dx = 1.0;
			dz = 0.0;
			length = 1.0;
		}
		double x = c.x + dx / length * SIDELINE;
		double z = c.z + dz / length * SIDELINE;
		double y = r.level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int)Math.floor(x), (int)Math.floor(z));
		body.setDeltaMovement(Vec3.ZERO);
		if (body instanceof ServerPlayer player) {
			player.teleportTo(r.level, x, Math.max(y, c.y), z, Set.of(), player.getYRot(), 0.0F, false);
		} else {
			body.snapTo(x, Math.max(y, c.y), z, body.getYRot(), 0.0F);
		}
	}

	// ---------------------------------------------------------------- the end

	/** The fight is over: {@code winner} (or the one left standing) is crowned and paid. */
	private static void end(Royale r, @Nullable Fighter winner) {
		if (winner == null) {
			for (Fighter f : r.fighters) {
				if (f.place == 0) {
					winner = f;
				}
			}
		}
		if (winner == null) {
			// everyone went out in the same breath: the last to go wins
			winner = r.fighters.stream().min(Comparator.comparingInt(f -> f.place)).orElse(null);
		}
		// the clock ran out on the rest: the least hurt takes second place
		List<Fighter> left = new ArrayList<>();
		for (Fighter f : r.fighters) {
			if (f != winner && f.place == 0) {
				left.add(f);
			}
		}
		left.sort(Comparator.comparingDouble(f -> {
			LivingEntity body = body(r, f);
			return body == null ? 0.0 : -body.getHealth() / body.getMaxHealth();
		}));
		for (int i = 0; i < left.size(); i++) {
			left.get(i).place = i + 2;
			left.get(i).how = "time";
		}
		if (winner != null) {
			winner.place = 1;
		}
		current = null;
		ServerLevel level = r.level;
		Component champion = winner == null ? Component.empty() : winner.name;
		level.getServer().getPlayerList().broadcastSystemMessage(Component.translatable("message.jaymospowers.zfighter_royale_champion", champion, r.kingdom)
			.withStyle(ChatFormatting.GOLD), false);
		level.playSound(null, r.center, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.NEUTRAL, 4.0F, 1.0F);
		// the prizes: the winner's, the runner-up's, and a K.O. each
		for (Fighter f : r.fighters) {
			ServerPlayer player = f.player ? level.getServer().getPlayerList().getPlayer(f.id) : null;
			if (player == null) {
				continue;
			}
			if (f == winner) {
				ZFighterTournaments.pay(player, ZFighterTournaments.WIN_TP, ZFighterTournaments.WIN_EMERALDS);
				level.sendParticles(ParticleTypes.FIREWORK, player.getX(), player.getY() + 1.5, player.getZ(), 60, 0.6, 1.0, 0.6, 0.15);
			} else if (f.place == 2) {
				ZFighterTournaments.pay(player, ZFighterTournaments.BOUT_TP, ZFighterTournaments.BOUT_EMERALDS);
			}
			if (f.knockouts > 0) {
				ZFighterTournaments.pay(player, KO_TP * f.knockouts, KO_EMERALDS * f.knockouts);
			}
		}
		release(r, winner);
		lastBoard = board(r, ZFighterBracketNet.OVER, champion.getString());
		ZFighterTournaments.remember(lastBoard);
		push(r, lastBoard);
	}

	/** The champions leave the ring, and anyone still in it is made whole. */
	private static void release(Royale r, @Nullable Fighter winner) {
		for (Fighter f : r.fighters) {
			LivingEntity body = body(r, f);
			if (body == null) {
				continue;
			}
			if (body instanceof ZFighterRogue) {
				r.level.sendParticles(ParticleTypes.CLOUD, body.getX(), body.getY() + 1.0, body.getZ(), 12, 0.3, 0.6, 0.3, 0.05);
				body.discard();
				continue;
			}
			if (Downed.isDowned(body)) {
				Downed.getUp(r.level, body);
			}
			body.setHealth(body.getMaxHealth());
			if (body instanceof Mob mob) {
				mob.setTarget(null);
				mob.getNavigation().stop();
			}
		}
	}

	// ---------------------------------------------------------------- the board

	/** The board as it stands (while a fight is on). */
	static ZFighterBracketNet.Board board() {
		return current != null ? board(current) : lastBoard;
	}

	private static ZFighterBracketNet.Board board(Royale r) {
		long now = r.level.getGameTime();
		return board(r, now < r.until ? ZFighterBracketNet.FIGHTING : ZFighterBracketNet.OVER, "");
	}

	private static ZFighterBracketNet.Board board(Royale r, int phase, String champion) {
		List<Fighter> sorted = new ArrayList<>(r.fighters);
		// those still in first, then the winner and the latest out
		sorted.sort(Comparator.comparingInt(f -> f.place == 0 ? -1 : f.place));
		List<ZFighterBracketNet.Fighter> out = new ArrayList<>();
		for (Fighter f : sorted) {
			out.add(new ZFighterBracketNet.Fighter(ZFighterBracketNet.name(f.name), f.player, f.place, f.how,
				f.by.getString().isEmpty() ? "" : ZFighterBracketNet.name(f.by)));
		}
		long left = Math.max(0L, r.until - r.level.getGameTime());
		return new ZFighterBracketNet.Board(true, true, phase, r.kingdom, 0, 0, phase == ZFighterBracketNet.OVER ? 0 : (int)left,
			champion.isEmpty() ? "" : champion, List.of(), out);
	}

	private static void push(Royale r) {
		push(r, board(r));
	}

	private static void push(Royale r, ZFighterBracketNet.Board board) {
		for (ServerPlayer player : r.level.players()) {
			if (player.distanceToSqr(Vec3.atCenterOf(r.center)) <= ZFighterTournaments.NEAR * ZFighterTournaments.NEAR) {
				ZFighterBracketNet.update(player, board);
			}
		}
	}

	/** Told to everyone within sight of the ring. */
	private static void say(Royale r, Component message, ChatFormatting style) {
		for (ServerPlayer player : r.level.players()) {
			if (player.distanceToSqr(Vec3.atCenterOf(r.center)) <= ZFighterTournaments.NEAR * ZFighterTournaments.NEAR) {
				player.sendSystemMessage(message.copy().withStyle(style));
			}
		}
	}
}
