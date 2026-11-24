package com.jaymoskingdoms.colossus;

import com.jaymoskingdoms.ClassTrees;
import com.jaymoskingdoms.Classes.PlayerClass;
import com.jaymoskingdoms.Commandments;
import com.jaymoskingdoms.Fx;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.Patron;
import com.jaymoskingdoms.Patrons;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Andy (2026-11-23): "I want that figure to appear, larger than any giant, as a single attack for any sufficiently progressed
 * paladin cleric or warlock. and the punishment as you break more and more commandments is seeing the figure, (replaced by
 * the respective patron) at varying distances, appearing periodically as a type of intimidation, culminating in a real hunt,
 * the player dying if the patron encounters them. it moves extremely slow. but starts closer every reappearance. only the
 * pious can see punishment patrons. the three classes' attacks are visible to all." The figure is {@link PatronColossus}.
 * <ul>
 * <li><b>The strike</b> ({@link #call}): a Paladin, Cleric or Warlock at class level {@link #STRIKE_LEVEL} with their patron's
 * favour at its fourth tier, sneaking and holding the class key, calls their patron's colossus once a day. It rises over the
 * spot they look at and stamps on it. A patron won't answer one already haunted for broken commandments.</li>
 * <li><b>Omens:</b> with {@link #OMEN_FROM} or more of their own patron's commandments broken, now and then the patron's
 * colossus stands far off and watches them, nearer and more often the more are broken.</li>
 * <li><b>The hunt:</b> from {@link #HUNT_FROM}, it walks at them instead, very slowly, for a minute, and comes back a few
 * minutes later, nearer each time, and on each walk brings a tornado, a quake or a tsunami down on them (Overworld only,
 * {@link com.jaymoskingdoms.disasters.Disasters#wrath}). If it reaches them it lifts its foot (the last chance to run) and
 * stamps, and they die.
 * The patron has had its due then: every broken commandment is forgiven.</li>
 * <li><b>Atonement:</b> an offering at the patron's shrine (once a day, as ever) forgives the last commandment broken and
 * sends the figure away.</li>
 * </ul>
 * Cheap: one entity per haunted player while it is out, moved from here; each player is looked at once a second.
 */
public final class Colossi {
	// ---- tuning ----
	/** The strike: class level and favour tier it takes, how far it reaches, how wide and hard it stamps, the shove. */
	public static final int STRIKE_LEVEL = 20, STRIKE_TIER = 4;
	static final double STRIKE_RANGE = 40.0, STRIKE_RADIUS = 6.0, STRIKE_SHOVE = 2.0;
	static final float STRIKE_DAMAGE = 40.0F;
	/** Ticks a strike lingers after the stamp before it goes. */
	static final int STRIKE_LINGER = 20;
	/** How far to the side of its middle its right foot comes down (blocks): the leg's 1.9 model pixels at its size. */
	static final double FOOT_SIDE = 1.9 / 16.0 * PatronColossus.HEIGHT / 2.0;
	/** Commandments broken (of ten) before the omens start, and before the hunt does. */
	public static final int OMEN_FROM = 3, HUNT_FROM = 7;
	/** How far off an omen stands: at {@link #OMEN_FROM} broken, and one short of the hunt (blocks). */
	static final double OMEN_FAR = 240.0, OMEN_NEAR = 90.0;
	/** Ticks between omens: at {@link #OMEN_FROM} broken (a day), and one short of the hunt; how long one stands. */
	static final int OMEN_GAP_MOST = 24000, OMEN_GAP_LEAST = 8000, OMEN_TICKS = 200;
	/** The hunt: how far off its first walk starts, how much nearer each next one, the nearest (blocks). */
	static final double HUNT_START = 160.0, HUNT_STEP = 25.0, HUNT_NEAREST = 40.0;
	/** Ticks between its walks, and how long each walk lasts. */
	static final int HUNT_GAP = 3600, HUNT_TICKS = 1200;
	/** How fast it walks (blocks a tick: a block a second), how near it stamps, how near the foot kills (blocks). */
	static final double HUNT_SPEED = 0.05, HUNT_REACH = 10.0, STAMP_KILL = 6.0;
	/** Ticks into each walk before it brings its disaster (a tornado, a quake or a tsunami), and the chance it does. */
	static final int DISASTER_AFTER = 200;
	static final float DISASTER_CHANCE = 1.0F;
	/** Ticks between its footfalls (heard only by the hunted). */
	static final int STRIDE_TICKS = 40;
	/** How far either side of where they look an omen or a walk begins (degrees). */
	static final float SIDE_SPREAD = 50.0F;
	/** Nearer than this, a start point isn't moved in to find loaded ground (blocks). */
	static final double LEAST_START = 32.0;

	/**
	 * On a player: the time of the next omen or walk (0 not yet set), how many walks so far (each starts nearer), and the
	 * day they last called the strike. Saved, kept through death.
	 */
	public record Haunt(long next, int walks, long struck) {
		public static final Haunt NONE = new Haunt(0L, 0, -1L);
		public static final Codec<Haunt> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.optionalFieldOf("next", 0L).forGetter(Haunt::next),
			Codec.INT.optionalFieldOf("walks", 0).forGetter(Haunt::walks),
			Codec.LONG.optionalFieldOf("struck", -1L).forGetter(Haunt::struck)).apply(i, Haunt::new));
	}

	public static final AttachmentType<Haunt> HAUNT = AttachmentRegistry.create(JaymosKingdoms.id("colossus_haunt"),
		b -> b.persistent(Haunt.CODEC).copyOnDeath());

	/** The figure out for each haunted player, and the strikes standing now. Not kept. */
	private static final Map<UUID, PatronColossus> OUT = new HashMap<>();
	private static final List<PatronColossus> STRIKES = new ArrayList<>();

	private Colossi() {
	}

	public static void register() {
		PatronColossus.register();
		JaymosKingdoms.LOGGER.debug("colossus attachment: {}", HAUNT);
		ServerTickEvents.END_SERVER_TICK.register(Colossi::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> send(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			OUT.clear();
			STRIKES.clear();
		});
	}

	// ---- the strike ----------------------------------------------------------------------------------------------------------

	/** Whether {@code player}, as {@code cls}, has come far enough to call the colossus (the client asks too, to wait for a hold). */
	public static boolean callable(Player player, @Nullable PlayerClass cls) {
		return (cls == PlayerClass.PALADIN || cls == PlayerClass.CLERIC || cls == PlayerClass.WARLOCK) && Patrons.of(player) != null
			&& Patrons.state(player).tier() >= STRIKE_TIER && ClassTrees.level(player, cls) >= STRIKE_LEVEL;
	}

	/** Sneak and the class key held: calls the colossus if {@code player} may. False if this isn't theirs to call at all. */
	public static boolean call(ServerPlayer player, @Nullable PlayerClass cls) {
		if (!callable(player, cls) || !(player.level() instanceof ServerLevel level)) {
			return false;
		}
		Patron patron = Patrons.of(player);
		if (Commandments.state(player).patron().equals(patron.key) && Commandments.state(player).count() >= OMEN_FROM) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.colossus.turned").withStyle(ChatFormatting.GRAY));
			return true;
		}
		long today = Patrons.day(level.getServer().overworld().getOverworldClockTime());
		Haunt h = haunt(player);
		if (h.struck == today) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.colossus.spent").withStyle(ChatFormatting.GRAY));
			return true;
		}
		player.setAttached(HAUNT, new Haunt(h.next, h.walks, today));
		HitResult hit = player.pick(STRIKE_RANGE, 1.0F, false);
		Vec3 spot = hit.getType() == HitResult.Type.MISS ? player.getEyePosition().add(player.getLookAngle().scale(STRIKE_RANGE)) : hit.getLocation();
		Vec3 forward = flat(player.getLookAngle());
		PatronColossus c = PatronColossus.make(level, patron, PatronColossus.STRIKE, player.getUUID(), spot, player.getYRot());
		c.spot = spot;
		Vec3 middle = spot.subtract(right(forward).scale(FOOT_SIDE));
		c.standAt(level, middle.x, middle.z);
		STRIKES.add(c);
		level.playSound(null, spot.x, spot.y, spot.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 2.0F, 0.4F);
		return true;
	}

	/** A strike's colossus, each tick: it rises, stamps, lingers and goes. */
	private static boolean strike(PatronColossus c) {
		if (!(c.level() instanceof ServerLevel level) || c.isRemoved()) {
			return false;
		}
		c.age++;
		if (c.leaving()) {
			return gone(c);
		}
		if (c.age >= PatronColossus.FADE_IN) {
			c.stamp();
		}
		if (c.stamping()) {
			c.stamp++;
			if (c.stamp == PatronColossus.LIFT_TICKS + PatronColossus.FALL_TICKS) {
				ServerPlayer caller = c.who == null ? null : level.getServer().getPlayerList().getPlayer(c.who);
				impact(level, c, caller);
			} else if (c.stamp >= PatronColossus.LIFT_TICKS + PatronColossus.FALL_TICKS + STRIKE_LINGER) {
				c.leave();
			}
		}
		return true;
	}

	/** The foot comes down on the strike's spot: its blow on every foe there. */
	private static void impact(ServerLevel level, PatronColossus c, @Nullable ServerPlayer caller) {
		Vec3 spot = c.spot;
		int colour = c.patron().colour;
		thud(level, spot, colour);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(spot, spot).inflate(STRIKE_RADIUS, 4.0, STRIKE_RADIUS),
			e -> e.isAlive() && e != caller && !friend(caller, e))) {
			Vec3 away = e.position().subtract(spot).multiply(1.0, 0.0, 1.0);
			if (away.lengthSqr() > STRIKE_RADIUS * STRIKE_RADIUS) {
				continue;
			}
			e.hurtServer(level, caller != null ? level.damageSources().indirectMagic(caller, caller) : level.damageSources().magic(), STRIKE_DAMAGE);
			away = away.lengthSqr() < 1.0E-4 ? flat(Vec3.directionFromRotation(0.0F, c.getYRot())) : away.normalize();
			e.push(away.x * STRIKE_SHOVE, 0.8, away.z * STRIKE_SHOVE);
			e.needsSync = true;
		}
	}

	/** The foot landing: the blast, the rings and the sound. */
	private static void thud(ServerLevel level, Vec3 spot, int colour) {
		Fx.blast(level, spot.add(0.0, 0.5, 0.0), (float) STRIKE_RADIUS, colour);
		Fx.ring(level, spot.add(0.0, 0.1, 0.0), (float) STRIKE_RADIUS, colour);
		Fx.ring(level, spot.add(0.0, 0.1, 0.0), (float) STRIKE_RADIUS * 2.5F, colour);
		level.playSound(null, spot.x, spot.y, spot.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 3.0F, 0.5F);
		level.playSound(null, spot.x, spot.y, spot.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 2.0F, 0.6F);
	}

	private static boolean friend(@Nullable ServerPlayer caller, LivingEntity e) {
		return e instanceof Player || caller != null && e instanceof TamableAnimal pet && pet.isOwnedBy(caller);
	}

	// ---- omens and the hunt ----------------------------------------------------------------------------------------------------

	public static Haunt haunt(Player player) {
		return player.getAttachedOrElse(HAUNT, Haunt.NONE);
	}

	/** Commandments of their own patron {@code player} has broken (0 if they've none, or have since sworn to another). */
	static int broken(Player player) {
		Patron p = Patrons.of(player);
		Commandments.Kept k = Commandments.state(player);
		return p != null && k.patron().equals(p.key) ? k.count() : 0;
	}

	private static void tick(MinecraftServer server) {
		for (Iterator<PatronColossus> it = STRIKES.iterator(); it.hasNext();) {
			if (!strike(it.next())) {
				it.remove();
			}
		}
		for (Iterator<Map.Entry<UUID, PatronColossus>> it = OUT.entrySet().iterator(); it.hasNext();) {
			Map.Entry<UUID, PatronColossus> e = it.next();
			ServerPlayer sp = server.getPlayerList().getPlayer(e.getKey());
			if (!step(e.getValue(), sp)) {
				e.getValue().discard();
				it.remove();
			}
		}
		if (server.getTickCount() % 20 != 0) {
			return;
		}
		for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
			schedule(sp);
		}
	}

	/** Once a second: whether it is time for {@code sp}'s patron to show itself. */
	private static void schedule(ServerPlayer sp) {
		if (OUT.containsKey(sp.getUUID()) || !(sp.level() instanceof ServerLevel level) || sp.isCreative() || sp.isSpectator() || !sp.isAlive()) {
			return;
		}
		int broken = broken(sp);
		Haunt h = haunt(sp);
		long now = level.getGameTime();
		if (broken < OMEN_FROM) {
			if (h.next != 0L || h.walks != 0) {
				sp.setAttached(HAUNT, new Haunt(0L, 0, h.struck));
			}
			return;
		}
		boolean hunt = broken >= HUNT_FROM;
		if (h.next == 0L) {
			// a first omen comes some while after the third is broken, not at once
			int gap = hunt ? HUNT_GAP : omenGap(broken);
			sp.setAttached(HAUNT, new Haunt(now + gap / 2 + sp.getRandom().nextInt(gap / 2 + 1), h.walks, h.struck));
			return;
		}
		// it shows itself only under an open sky's kind of world, and never to the sleeping
		if (now < h.next || level.dimensionType().hasCeiling() || !level.dimensionType().hasSkyLight() || sp.isSleeping()) {
			return;
		}
		Patron patron = Patrons.of(sp);
		double distance = hunt ? Math.max(HUNT_NEAREST, HUNT_START - HUNT_STEP * h.walks)
			: Mth.lerp((broken - OMEN_FROM) / (float) Math.max(1, HUNT_FROM - 1 - OMEN_FROM), OMEN_FAR, OMEN_NEAR);
		Vec3 at = start(level, sp, distance);
		if (at == null) {
			return;
		}
		PatronColossus c = PatronColossus.make(level, patron, hunt ? PatronColossus.HUNT : PatronColossus.OMEN, sp.getUUID(), at, 0.0F);
		c.standAt(level, at.x, at.z);
		c.face(sp.position());
		c.setOldPosAndRot();
		OUT.put(sp.getUUID(), c);
		sp.setAttached(HAUNT, new Haunt(now + (hunt ? HUNT_GAP : omenGap(broken)), hunt ? h.walks + 1 : h.walks, h.struck));
		sound(sp, SoundEvents.BELL_RESONATE, sp.position(), 1.0F, hunt ? 0.3F : 0.45F);
		if (hunt) {
			sound(sp, SoundEvents.WARDEN_EMERGE, at, 4.0F, 0.5F);
		}
	}

	private static int omenGap(int broken) {
		return Math.round(Mth.lerp((broken - OMEN_FROM) / (float) Math.max(1, HUNT_FROM - 1 - OMEN_FROM), OMEN_GAP_MOST, OMEN_GAP_LEAST));
	}

	/** Where it shows itself: {@code distance} off, ahead of {@code sp} give or take, on loaded ground (nearer if need be). */
	private static @Nullable Vec3 start(ServerLevel level, ServerPlayer sp, double distance) {
		float yaw = sp.getYRot() + (sp.getRandom().nextFloat() * 2.0F - 1.0F) * SIDE_SPREAD;
		Vec3 dir = flat(Vec3.directionFromRotation(0.0F, yaw));
		for (double d = distance; d >= Math.min(distance, LEAST_START); d -= 16.0) {
			Vec3 at = sp.position().add(dir.scale(d));
			if (level.isLoaded(net.minecraft.core.BlockPos.containing(at))) {
				return at;
			}
		}
		return null;
	}

	/** An omen or a walk, each tick; false when it is done (it is then discarded). */
	private static boolean step(PatronColossus c, @Nullable ServerPlayer sp) {
		if (c.isRemoved() || sp == null || !sp.isAlive() || sp.level() != c.level() || !(c.level() instanceof ServerLevel level)) {
			return false;
		}
		c.age++;
		if (c.leaving()) {
			return gone(c);
		}
		if (c.mode() == PatronColossus.OMEN) {
			c.face(sp.position());
			if (c.age >= OMEN_TICKS) {
				c.leave();
			}
			return true;
		}
		// the hunt
		if (c.stamping()) {
			c.stamp++;
			if (c.stamp == PatronColossus.LIFT_TICKS + PatronColossus.FALL_TICKS) {
				thud(level, c.spot, c.patron().colour);
				if (flat(sp.position().subtract(c.spot)).length() <= STAMP_KILL && !sp.isCreative() && !sp.isSpectator()) {
					caught(sp, level);
				}
			} else if (c.stamp >= PatronColossus.LIFT_TICKS + PatronColossus.FALL_TICKS + STRIKE_LINGER) {
				c.leave();
			}
			return true;
		}
		if (c.age < PatronColossus.FADE_IN) {
			c.face(sp.position());
			return true;
		}
		if (c.age >= PatronColossus.FADE_IN + HUNT_TICKS) {
			c.leave();
			return true;
		}
		// (Andy, 2026-11-24) it brings a natural disaster down on them as it comes
		if (c.age == PatronColossus.FADE_IN + DISASTER_AFTER && sp.getRandom().nextFloat() < DISASTER_CHANCE) {
			com.jaymoskingdoms.disasters.Disasters.wrath(sp);
		}
		Vec3 to = sp.position().subtract(c.position()).multiply(1.0, 0.0, 1.0);
		double distance = to.length();
		if (c.age % 20 == 0) {
			sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.colossus.hunt", (int) distance)
				.withStyle(style -> style.withColor(c.patron().colour & 0xFFFFFF)));
		}
		if (distance <= HUNT_REACH) {
			// it plants itself so its right foot is over them, and lifts it: run
			c.spot = sp.position();
			c.face(sp.position());
			Vec3 middle = c.spot.subtract(right(flat(Vec3.directionFromRotation(0.0F, c.getYRot()))).scale(FOOT_SIDE));
			c.standAt(level, middle.x, middle.z);
			c.stamp();
			return true;
		}
		Vec3 next = c.position().add(to.scale(HUNT_SPEED / distance));
		c.standAt(level, next.x, next.z);
		c.face(sp.position());
		c.walking(true);
		if (c.age % STRIDE_TICKS == 0) {
			sound(sp, SoundEvents.WARDEN_STEP, c.position(), 6.0F, 0.3F);
		}
		return true;
	}

	/** A going colossus, each tick; false once it has faded out. */
	private static boolean gone(PatronColossus c) {
		c.gone++;
		if (c.gone >= PatronColossus.FADE_OUT) {
			c.discard();
			return false;
		}
		return true;
	}

	/** The colossus reached {@code sp}: they die, and the patron has had its due. */
	private static void caught(ServerPlayer sp, ServerLevel level) {
		Commandments.forgive(sp, Commandments.COUNT);
		sp.setAttached(HAUNT, new Haunt(0L, 0, haunt(sp).struck));
		sp.kill(level);
	}

	/** (From {@link Patrons}) an offering made at {@code sp}'s own patron's shrine: a haunted one is forgiven one commandment. */
	public static void offered(ServerPlayer sp) {
		if (broken(sp) < OMEN_FROM) {
			return;
		}
		if (Commandments.forgive(sp, 1) > 0) {
			sp.sendSystemMessage(Component.translatable("message.jaymoskingdoms.colossus.forgiven").withStyle(ChatFormatting.GRAY));
		}
		PatronColossus c = OUT.get(sp.getUUID());
		if (c != null) {
			c.leave();
		}
		Haunt h = haunt(sp);
		long now = sp.level().getGameTime();
		// the walks start over from afar, and not straight away
		sp.setAttached(HAUNT, new Haunt(Math.max(h.next, now + HUNT_GAP), 0, h.struck));
	}

	/** Takes {@code player}'s figure away at once (they left). */
	private static void send(UUID player) {
		PatronColossus c = OUT.remove(player);
		if (c != null) {
			c.discard();
		}
	}

	// ---- small things ----

	private static Vec3 flat(Vec3 v) {
		Vec3 f = v.multiply(1.0, 0.0, 1.0);
		return f.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 0.0, 1.0) : f.normalize();
	}

	/** The right of something facing {@code forward} (flat). */
	private static Vec3 right(Vec3 forward) {
		return forward.cross(new Vec3(0.0, 1.0, 0.0)).normalize();
	}

	/** A sound only {@code sp} hears, from {@code at}. */
	private static void sound(ServerPlayer sp, SoundEvent sound, Vec3 at, float volume, float pitch) {
		sp.connection.send(new ClientboundSoundPacket(Holder.direct(sound), SoundSource.HOSTILE, at.x, at.y, at.z, volume, pitch, sp.getRandom().nextLong()));
	}
}
