package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;

/**
 * (0.7.389) A clash of wills between players. Andy (2026-10-30): Stare Down "can be done between players, and when one
 * initiates a stare, the other is notified, then it starts a clash". A player with Stare Down who sneaks and holds their
 * gaze on another player warns them at once ({@link #warn}: a line, a heartbeat, a shiver); held for {@link #LOCK_TICKS},
 * the two lock eyes. Both are rooted where they stand, turned to face each other, and press jump as fast as they can for
 * {@link #TICKS}: a press adds {@link #TAP} and a share of your Will ({@link Staredowns#will}), and the greater Will gains
 * {@link #WILL_RATE} a tick besides. Each sees the contest on their action bar. It ends at the time, or at a {@link #DECIDE}
 * lead. The one who breaks looks away: shaken, slowed and weakened, their knees buckling, their powers and spells hushed for a
 * moment ({@link Will#hush}); the winner grows in Strength. A draw throws both back a step.
 */
final class WillClash {
	/** How long the gaze must be held before the two lock eyes (ticks), before Second Breath. */
	static final int LOCK_TICKS = 40;
	/** How long the contest runs (ticks). */
	static final int TICKS = 100;
	/** A jump press's points, and one more for every this much Will. */
	static final float TAP = 1.0F, WILL_PER_POINT = 4.0F;
	/** What each point of Will over the other's is worth every tick. */
	static final float WILL_RATE = 0.03F;
	/** The lead that decides it early (points). */
	static final float DECIDE = 30.0F;
	/** The one who breaks: shaken this long (ticks), their knees giving this long; the winner's Strength experience. */
	static final int BROKEN_TICKS = 200, BUCKLE_TICKS = 40, XP_WIN = 4;
	/** A draw's shove (blocks/tick), and how far a rooted player may drift (blocks). */
	static final double PUSH = 0.5, DRIFT = 0.15;
	/** The warning's shiver, and the contest's least and most shake. */
	static final float WARN_SHAKE = 0.2F, SHAKE_MIN = 0.06F, SHAKE_MAX = 0.3F;
	/** Two players may lock eyes again this long after a clash (ticks). */
	static final int COOLDOWN_TICKS = 600;
	/** The bar's length on the action bar (characters). */
	static final int BAR = 30;

	private static final class Session {
		final UUID a, b;
		final Vec3 spotA, spotB;
		final long start;
		final int willA, willB;
		float score;
		boolean jumpA, jumpB;

		Session(ServerPlayer a, ServerPlayer b, long start) {
			this.a = a.getUUID();
			this.b = b.getUUID();
			this.spotA = a.position();
			this.spotB = b.position();
			this.start = start;
			this.willA = Staredowns.will(a);
			this.willB = Staredowns.will(b);
		}
	}

	private static final Map<UUID, Session> SESSIONS = new HashMap<>();
	/** Player → game time they may clash again. */
	private static final Map<UUID, Long> READY = new HashMap<>();

	private WillClash() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(WillClash::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			Session s = SESSIONS.get(handler.player.getUUID());
			if (s != null) {
				end(s);
			}
			READY.remove(handler.player.getUUID());
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			SESSIONS.clear();
			READY.clear();
		});
	}

	static boolean in(ServerPlayer player) {
		return SESSIONS.containsKey(player.getUUID());
	}

	/** Whether {@code sp} may lock eyes with {@code other} now. */
	static boolean may(ServerPlayer sp, ServerPlayer other, long now) {
		return other != sp && other.isAlive() && !other.isSpectator() && !other.isCreative() && !Downed.isDowned(other) && !in(sp) && !in(other)
			&& !com.jaymoskingdoms.spirits.SpiritClash.inClash(sp) && !com.jaymoskingdoms.spirits.SpiritClash.inClash(other)
			&& READY.getOrDefault(sp.getUUID(), 0L) <= now && READY.getOrDefault(other.getUUID(), 0L) <= now;
	}

	/** {@code sp} has begun to stare {@code other} down: they know it. */
	static void warn(ServerLevel level, ServerPlayer sp, ServerPlayer other) {
		other.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.will_clash.warn", sp.getDisplayName()).withStyle(ChatFormatting.DARK_RED));
		PowersCompat.shake(other, WARN_SHAKE);
		level.playSound(null, other.getX(), other.getY(), other.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 0.8F, 1.0F);
		sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.will_clash.staring", other.getDisplayName()).withStyle(ChatFormatting.GRAY));
	}

	/** The gaze held long enough: the two lock eyes. */
	static void begin(ServerLevel level, ServerPlayer a, ServerPlayer b, long now) {
		Session s = new Session(a, b, now);
		SESSIONS.put(s.a, s);
		SESSIONS.put(s.b, s);
		face(a, b);
		face(b, a);
		Vec3 mid = a.position().add(b.position()).scale(0.5);
		level.playSound(null, mid.x, mid.y + 1.0, mid.z, SoundEvents.WARDEN_ROAR, SoundSource.PLAYERS, 0.6F, 1.4F);
		for (ServerPlayer p : new ServerPlayer[] {a, b}) {
			p.sendSystemMessage(Component.translatable("message.jaymoskingdoms.will_clash.begin", (p == a ? b : a).getDisplayName()).withStyle(ChatFormatting.GOLD));
			PowersCompat.shake(p, SHAKE_MAX);
		}
	}

	private static void tick(MinecraftServer server) {
		if (SESSIONS.isEmpty()) {
			return;
		}
		List<Session> running = new ArrayList<>();
		for (Session s : SESSIONS.values()) {
			if (!running.contains(s)) {
				running.add(s);
			}
		}
		for (Session s : running) {
			ServerPlayer a = server.getPlayerList().getPlayer(s.a), b = server.getPlayerList().getPlayer(s.b);
			if (a == null || b == null || !a.isAlive() || !b.isAlive() || a.isSpectator() || b.isSpectator() || a.level() != b.level()
				|| Downed.isDowned(a) || Downed.isDowned(b) || !(a.level() instanceof ServerLevel level)) {
				end(s);
				continue;
			}
			long t = level.getGameTime() - s.start;
			boolean ja = a.getLastClientInput().jump(), jb = b.getLastClientInput().jump();
			if (ja && !s.jumpA) {
				s.score += TAP + s.willA / WILL_PER_POINT;
			}
			if (jb && !s.jumpB) {
				s.score -= TAP + s.willB / WILL_PER_POINT;
			}
			s.jumpA = ja;
			s.jumpB = jb;
			s.score += WILL_RATE * (s.willA - s.willB);
			root(a, s.spotA);
			root(b, s.spotB);
			if (t % 2 == 0) {
				a.sendOverlayMessage(bar(s.score, b));
				b.sendOverlayMessage(bar(-s.score, a));
			}
			if (t % 5 == 0) {
				float shake = SHAKE_MIN + (SHAKE_MAX - SHAKE_MIN) * Math.min(1.0F, Math.abs(s.score) / DECIDE);
				PowersCompat.shake(a, shake);
				PowersCompat.shake(b, shake);
				Vec3 mid = a.getEyePosition().add(b.getEyePosition()).scale(0.5);
				level.sendParticles(ParticleTypes.CRIT, mid.x, mid.y, mid.z, 3, 0.2, 0.2, 0.2, 0.05);
			}
			if (Math.abs(s.score) >= DECIDE || t >= TICKS) {
				finish(level, s, a, b);
			}
		}
	}

	/** The contest as {@code me} sees it ({@code score} is my lead): my side gold, theirs red. */
	private static Component bar(float score, ServerPlayer them) {
		float lead = Math.clamp(score / DECIDE, -1.0F, 1.0F);
		int mine = Math.round((lead + 1.0F) * 0.5F * BAR);
		MutableComponent line = Component.translatable("message.jaymoskingdoms.will_clash.you").withStyle(ChatFormatting.GOLD).append(" ");
		line.append(Component.literal("|".repeat(mine)).withStyle(ChatFormatting.GOLD));
		line.append(Component.literal("|".repeat(BAR - mine)).withStyle(ChatFormatting.DARK_RED));
		return line.append(" ").append(them.getDisplayName().copy().withStyle(ChatFormatting.DARK_RED));
	}

	private static void finish(ServerLevel level, Session s, ServerPlayer a, ServerPlayer b) {
		end(s);
		long now = level.getGameTime();
		READY.put(s.a, now + COOLDOWN_TICKS);
		READY.put(s.b, now + COOLDOWN_TICKS);
		if (Math.abs(s.score) < 1.0F) {
			push(a, b);
			push(b, a);
			level.playSound(null, a.getX(), a.getY(), a.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0F, 0.7F);
			for (ServerPlayer p : new ServerPlayer[] {a, b}) {
				p.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.will_clash.draw").withStyle(ChatFormatting.YELLOW));
			}
			return;
		}
		ServerPlayer win = s.score > 0.0F ? a : b, lose = win == a ? b : a;
		lose.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, BROKEN_TICKS, 0), win);
		lose.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, BROKEN_TICKS, 0), win);
		lose.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, BUCKLE_TICKS, 4), win);
		Will.hush(level, lose);
		PowersCompat.shake(lose, 0.9F);
		PowersCompat.shake(win, 0.3F);
		Skills.grant(win, Skills.Pillar.STRENGTH, XP_WIN);
		level.sendParticles(ParticleTypes.FALLING_WATER, lose.getX(), lose.getY(0.9), lose.getZ(), 10, 0.25, 0.1, 0.25, 0.0);
		level.playSound(null, lose.getX(), lose.getY(), lose.getZ(), SoundEvents.ARMOR_EQUIP_GENERIC.value(), SoundSource.PLAYERS, 0.8F, 0.6F);
		win.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.will_clash.won", lose.getDisplayName()).withStyle(ChatFormatting.GOLD));
		lose.sendSystemMessage(Component.translatable("message.jaymoskingdoms.will_clash.lost", win.getDisplayName()).withStyle(ChatFormatting.DARK_RED));
	}

	private static void end(Session s) {
		SESSIONS.remove(s.a);
		SESSIONS.remove(s.b);
	}

	private static void face(ServerPlayer who, ServerPlayer at) {
		Vec3 to = at.getEyePosition().subtract(who.getEyePosition());
		float yaw = (float) Math.toDegrees(Math.atan2(to.z, to.x)) - 90.0F;
		float pitch = (float) -Math.toDegrees(Math.atan2(to.y, to.horizontalDistance()));
		who.connection.teleport(who.getX(), who.getY(), who.getZ(), yaw, pitch);
	}

	/** Holds {@code p} where the clash found them. */
	private static void root(ServerPlayer p, Vec3 spot) {
		p.setDeltaMovement(Vec3.ZERO);
		if (p.position().distanceToSqr(spot) > DRIFT * DRIFT) {
			p.connection.teleport(spot.x, spot.y, spot.z, p.getYRot(), p.getXRot());
		}
		p.fallDistance = 0.0;
	}

	private static void push(ServerPlayer who, ServerPlayer from) {
		Vec3 away = new Vec3(who.getX() - from.getX(), 0.0, who.getZ() - from.getZ());
		away = away.lengthSqr() > 1.0E-4 ? away.normalize() : Vec3.directionFromRotation(0.0F, who.getYRot() + 180.0F);
		who.push(away.x * PUSH, 0.2, away.z * PUSH);
		who.syncVelocity = true;
	}
}
