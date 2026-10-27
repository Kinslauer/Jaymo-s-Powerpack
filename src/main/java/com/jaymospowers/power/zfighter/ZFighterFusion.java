package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Powers;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * The fusion dance. Two Z Fighters face each other a few blocks apart; each taps Z, then presses C within two seconds,
 * and dances (arms swept out to one side, over, then fingertips pointed at each other). When the second one's dance
 * ends, they become one for {@link #FUSION_TICKS}, and longer the more often the pair has fused and the more they've trained
 * between them ({@link #length}): the first to dance leads and steers; the other watches from inside (a spectator riding
 * his camera); halfway through they change places. The fused fighter has both their fists, health, ki and ki power added together, and
 * {@link #FUSION_BONUS} more. Either may then not fuse again for {@link #COOLDOWN}.
 * <p>
 * (0.17.53, Andy's NOEA picks) Patch 4: the same machinery now carries four more ways in, each a {@link Kind}: the
 * earrings and the arm bands ({@link ZFighterFusionItems}: no dance, for 30 minutes or 3), the five-way ring
 * ({@link ZFighterFiveFusion}: one huge body, four watching) and a master's agreement ({@link ZFighterMasterFusion}: the
 * master is held out of the world meanwhile). While fused, every step of form above base burns the timer faster
 * ({@link #FORM_BURN} more per step).
 */
public final class ZFighterFusion {
	/** Z, then C within this long, starts the dance; the partner's dance must start within this long of yours. */
	static final int DANCE_WINDOW = 40;
	public static final int DANCE_TICKS = 40;
	static final double DANCE_RANGE = 6.0;
	/** How squarely they must face each other (the dot of their flat looks, at most). */
	static final double FACING = -0.5;
	static final int FUSION_TICKS = 1200;
	/**
	 * (0.16.16) Andy: "the more they fuse, and the more experience they have collectively the longer the fusion, so you can
	 * kind of train your fusion". Each fusion the pair has done before adds this much (up to {@link #PRACTICE_CAP} of them),
	 * and each stat level they've trained between them ({@link ZFighterTraining.Data#totalLevels}) this much more.
	 */
	static final int PER_FUSION_TICKS = 120;
	static final int PRACTICE_CAP = 15;
	static final int PER_LEVEL_TICKS = 2;
	static final float FUSION_BONUS = 0.1F;
	static final long COOLDOWN = 12000L;
	/** (0.17.53) Each form step above base (see {@link ZFighterForms#tier}) drains the fusion timer this much faster (0.5: +50% a step). */
	static final double FORM_BURN = 0.5;
	/** How often (ticks) the fused are told the timer is burning faster, and what it says. */
	static final int BURN_SHOWN_EVERY = 100;
	/** The partners' toughness (damage turned aside) added together never goes past this. */
	static final float TOUGHNESS_CAP = 0.75F;

	/** Which way the dancer sweeps (1 right, -1 left), while dancing; everyone's client poses it. */
	public static final AttachmentType<Integer> DANCE = AttachmentRegistry.create(JaymosPowers.id("zfighter_dance"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));
	/** On the one watching from inside: the game mode to give back (kept across a log-out or a crash). */
	public static final AttachmentType<Integer> RESTORE = AttachmentRegistry.create(JaymosPowers.id("zfighter_fusion_restore"),
		builder -> builder.persistent(Codec.INT));
	/**
	 * (0.16.16) On the fused body, for everyone's client: who the two are (the first to dance, then the other, the same
	 * after they change places) and the one inside's lineage and tail, so the body is drawn as both of them
	 * ({@code ZFighterFusionLook}).
	 */
	public static final AttachmentType<Look> LOOK = AttachmentRegistry.create(JaymosPowers.id("zfighter_fusion_look"),
		builder -> builder.syncWith(Look.STREAM_CODEC, AttachmentSyncPredicate.all()));
	/** (0.16.16) How many times he's fused with each partner (their UUID), for the length of the next fusion. */
	public static final AttachmentType<Map<String, Integer>> PRACTICE = AttachmentRegistry.create(JaymosPowers.id("zfighter_fusion_practice"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.INT)).copyOnDeath());
	/** When he last fused (game time). */
	public static final AttachmentType<Long> LAST = AttachmentRegistry.create(JaymosPowers.id("zfighter_fusion_last"),
		builder -> builder.persistent(Codec.LONG));

	private record Dance(long started, int side, long ends) {
	}

	/** The fused body's two (first and second dancer), and the lineage and tail (0 none, 1 out, 2 wrapped) of the one inside. */
	public record Look(UUID first, UUID second, int innerLineage, int innerTail) {
		public static final StreamCodec<ByteBuf, Look> STREAM_CODEC = StreamCodec.composite(
			UUIDUtil.STREAM_CODEC, Look::first,
			UUIDUtil.STREAM_CODEC, Look::second,
			ByteBufCodecs.VAR_INT, Look::innerLineage,
			ByteBufCodecs.VAR_INT, Look::innerTail,
			Look::new);
	}

	private record Pending(UUID leader, UUID partner, long at) {
	}

	/** (0.17.53) How a fusion came about. */
	enum Kind {
		DANCE, EARRING, BAND, FIVE, MASTER
	}

	/** What a partner adds to the leader (or, for several, what they add together). */
	record Stats(double fists, double health, int ki, float power, float toughness) {
		static final Stats NONE = new Stats(0.0, 0.0, 0, 0.0F, 0.0F);

		/** What {@code player} brings, as he is now. */
		static Stats of(ServerPlayer player) {
			return new Stats(ZFighterPower.fistBonus(player), player.getMaxHealth(), ZFighterPower.maxKi(player),
				ZFighterPower.kiPower(player, ZFighterPower.live(player)), ZFighterTraining.toughness(player));
		}

		Stats plus(Stats other) {
			return new Stats(fists + other.fists, health + other.health, ki + other.ki, power + other.power,
				Math.min(TOUGHNESS_CAP, toughness + other.toughness));
		}
	}

	/**
	 * A fusion under way, kept under the one steering. {@code inside} are the players watching from inside (none for a
	 * master's: {@code master} is the NPC held out of the world instead). {@code until} moves up as forms burn it.
	 */
	static final class Fused {
		final Kind kind;
		final List<UUID> inside;
		final UUID master;
		long until;
		final long length;
		final Stats stats;
		final float bonus;
		final boolean swapped;
		/** The part of a tick the forms have burned and not yet taken off {@code until}. */
		double burned;
		boolean warned;

		Fused(Kind kind, List<UUID> inside, UUID master, long until, long length, Stats stats, float bonus, boolean swapped) {
			this.kind = kind;
			this.inside = inside;
			this.master = master;
			this.until = until;
			this.length = length;
			this.stats = stats;
			this.bonus = bonus;
			this.swapped = swapped;
		}

		UUID partner() {
			return inside.isEmpty() ? null : inside.get(0);
		}

		long until() {
			return until;
		}

		long length() {
			return length;
		}

		boolean swapped() {
			return swapped;
		}

		/** The same fusion with the other one steering (halfway through a two-way fusion). */
		Fused swappedTo(UUID newPartner, Stats newStats) {
			Fused next = new Fused(kind, List.of(newPartner), null, until, length, newStats, bonus, true);
			next.burned = burned;
			next.warned = warned;
			return next;
		}
	}

	private static final Map<UUID, Long> Z_TAPS = new HashMap<>();
	private static final Map<UUID, Dance> DANCES = new HashMap<>();
	private static final List<Pending> PENDING = new ArrayList<>();
	private static final Map<UUID, Fused> FUSED = new HashMap<>();

	private ZFighterFusion() {
	}

	static void init() {
		// (0.17.53) the other ways in
		ZFighterFusionItems.init();
		ZFighterFiveFusion.init();
		ZFighterMasterFusion.init();
		ServerTickEvents.END_SERVER_TICK.register(ZFighterFusion::tick);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> giveBack(handler.player, null)));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			UUID id = handler.player.getUUID();
			if (FUSED.containsKey(id)) {
				defuse(server, id);
			}
			for (Map.Entry<UUID, Fused> entry : new ArrayList<>(FUSED.entrySet())) {
				if (entry.getValue().inside.contains(id)) {
					defuse(server, entry.getKey());
				}
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (UUID leader : new ArrayList<>(FUSED.keySet())) {
				defuse(server, leader);
			}
			Z_TAPS.clear();
			DANCES.clear();
			PENDING.clear();
		});
	}

	/** Z pressed: remembered, in case C follows. */
	static void tappedZ(ServerPlayer player) {
		Z_TAPS.put(player.getUUID(), player.level().getGameTime());
	}

	/** C pressed: if Z came just before, he dances (and C does nothing else). */
	static boolean tryDance(ServerPlayer player, ZFighterPower.Live live) {
		long now = player.level().getGameTime();
		Long z = Z_TAPS.remove(player.getUUID());
		if (z == null || now - z > DANCE_WINDOW || live.flying || !player.onGround() || FUSED.containsKey(player.getUUID())) {
			return false;
		}
		Long last = player.getAttached(LAST);
		if (last != null && now - last < COOLDOWN && !player.isCreative()) {
			long minutes = (COOLDOWN - (now - last) + 1199L) / 1200L;
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_fusion_tired", minutes).withStyle(ChatFormatting.GRAY));
			return true;
		}
		// (0.17.53) five of them in a ring, all past Super 2, all pressing Z then C: the five-way
		if (ZFighterFiveFusion.agree(player, now)) {
			return true;
		}
		ServerPlayer partner = partner(player, now);
		int side = partner == null ? -1 : -DANCES.get(partner.getUUID()).side();
		DANCES.put(player.getUUID(), new Dance(now, side, now + DANCE_TICKS));
		player.setAttached(DANCE, side);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.2F);
		if (partner == null) {
			// (0.17.53) a master who has taught him enough answers the dance instead
			if (!ZFighterMasterFusion.begin(player, now)) {
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_fusion_waiting").withStyle(ChatFormatting.AQUA));
			}
		} else {
			PENDING.add(new Pending(partner.getUUID(), player.getUUID(), now + DANCE_TICKS));
		}
		return true;
	}

	/** Another Z Fighter dancing near, facing him, who started within the window. */
	private static ServerPlayer partner(ServerPlayer player, long now) {
		for (ServerPlayer other : player.level().players()) {
			Dance dance = DANCES.get(other.getUUID());
			if (other == player || dance == null || now - dance.started() > DANCE_WINDOW || !Powers.has(other, ZFighterPower.INSTANCE)
				|| other.distanceTo(player) > DANCE_RANGE || FUSED.containsKey(other.getUUID())) {
				continue;
			}
			Vec3 a = player.getLookAngle().multiply(1.0, 0.0, 1.0).normalize();
			Vec3 b = other.getLookAngle().multiply(1.0, 0.0, 1.0).normalize();
			if (a.dot(b) <= FACING) {
				return other;
			}
		}
		return null;
	}

	private static void tick(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		for (Iterator<Map.Entry<UUID, Dance>> it = DANCES.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Dance> entry = it.next();
			if (now >= entry.getValue().ends()) {
				ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
				if (player != null) {
					player.removeAttached(DANCE);
				}
				it.remove();
			}
		}
		for (Iterator<Pending> it = PENDING.iterator(); it.hasNext(); ) {
			Pending pending = it.next();
			if (now < pending.at()) {
				continue;
			}
			it.remove();
			ServerPlayer leader = server.getPlayerList().getPlayer(pending.leader());
			ServerPlayer partner = server.getPlayerList().getPlayer(pending.partner());
			if (leader != null && partner != null && leader.isAlive() && partner.isAlive() && leader.level() == partner.level()
				&& leader.distanceTo(partner) <= DANCE_RANGE + 2.0 && !FUSED.containsKey(leader.getUUID()) && !FUSED.containsKey(partner.getUUID())
				&& !ZFighterLineage.ape(leader) && !ZFighterLineage.ape(partner)) {
				fuse(leader, partner);
			}
		}
		for (UUID id : new ArrayList<>(FUSED.keySet())) {
			Fused fused = FUSED.get(id);
			ServerPlayer leader = server.getPlayerList().getPlayer(id);
			if (leader == null || !leader.isAlive() || now >= fused.until() || !insideHere(server, fused)) {
				defuse(server, id);
				continue;
			}
			// (0.17.53) each form step above base burns the timer faster
			int steps = ZFighterForms.tier(ZFighterPower.live(leader).form);
			if (steps > 0) {
				fused.burned += steps * FORM_BURN;
				long whole = (long)fused.burned;
				fused.until -= whole;
				fused.burned -= whole;
				if (now % BURN_SHOWN_EVERY == 0) {
					Component burning = Component.translatable("message.jaymospowers.zfighter_fusion_burning", Math.max(0L, fused.until() - now) / 20L,
						Math.round(steps * FORM_BURN * 100.0)).withStyle(ChatFormatting.GOLD);
					tell(server, leader, fused, burning);
				}
			}
			// halfway through, the one watching from inside takes over and the leader goes inside
			if (!fused.swapped() && fused.until() - now <= fused.length() / 2) {
				swap(leader, server.getPlayerList().getPlayer(fused.partner()), fused);
				continue;
			}
			// the partners ride along inside, seeing what the leader sees
			for (UUID inner : fused.inside) {
				ServerPlayer partner = server.getPlayerList().getPlayer(inner);
				if (partner.level() != leader.level() || partner.distanceToSqr(leader) > 4.0) {
					partner.teleportTo(leader.level(), leader.getX(), leader.getY(), leader.getZ(), Set.of(), leader.getYRot(), leader.getXRot(), false);
				}
				if (partner.getCamera() != leader) {
					partner.setCamera(leader);
				}
			}
			if (now % 10 == 0) {
				ServerLevel level = leader.level();
				level.sendParticles(new DustParticleOptions(0xFFD040, 1.6F), leader.getX(), leader.getY() + 1.0, leader.getZ(), 6, 0.4, 0.8, 0.4, 0.0);
				level.sendParticles(new DustParticleOptions(0x40E0D0, 1.6F), leader.getX(), leader.getY() + 1.0, leader.getZ(), 6, 0.4, 0.8, 0.4, 0.0);
			}
			if (!fused.warned && fused.until() - now <= 200) {
				fused.warned = true;
				leader.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_fusion_ending").withStyle(ChatFormatting.GOLD));
			}
		}
	}

	/** Whether everyone who should be inside is still on the server. */
	private static boolean insideHere(MinecraftServer server, Fused fused) {
		for (UUID inner : fused.inside) {
			if (server.getPlayerList().getPlayer(inner) == null) {
				return false;
			}
		}
		return true;
	}

	/** An overlay message for the one steering and everyone inside. */
	private static void tell(MinecraftServer server, ServerPlayer leader, Fused fused, Component message) {
		leader.sendOverlayMessage(message);
		for (UUID inner : fused.inside) {
			ServerPlayer partner = server.getPlayerList().getPlayer(inner);
			if (partner != null) {
				partner.sendOverlayMessage(message);
			}
		}
	}

	/** The dance is done: they fuse for as long as their practice and training allow. */
	private static void fuse(ServerPlayer leader, ServerPlayer partner) {
		int practice = practice(leader, partner);
		int length = length(practice, ZFighterTraining.of(leader).totalLevels() + ZFighterTraining.of(partner).totalLevels());
		begin(leader, List.of(partner), Kind.DANCE, length, FUSION_BONUS, null);
		practised(leader, partner, practice + 1);
		practised(partner, leader, practice + 1);
		announce(leader, partner);
		Component holds = Component.translatable("message.jaymospowers.zfighter_fusion_length", practice + 1, (length + 19) / 20)
			.withStyle(ChatFormatting.AQUA);
		leader.sendOverlayMessage(holds);
		partner.sendOverlayMessage(holds);
	}

	/**
	 * (0.17.53) Two fighters become one without the dance ({@link ZFighterFusionItems}): {@code leader} steers, {@code partner}
	 * watches from inside, for {@code length} ticks with {@code bonus} on top of the two added together.
	 */
	static void fuseWith(ServerPlayer leader, ServerPlayer partner, Kind kind, int length, float bonus) {
		begin(leader, List.of(partner), kind, length, bonus, null);
		announce(leader, partner);
		Component holds = Component.translatable("message.jaymospowers.zfighter_fusion_timed", (length + 1199) / 1200).withStyle(ChatFormatting.AQUA);
		leader.sendOverlayMessage(holds);
		partner.sendOverlayMessage(holds);
	}

	/** Whether {@code player} could fuse right now (a Z Fighter, whole and free, not an ape, not already fused or watching). */
	static boolean fusable(ServerPlayer player) {
		return player.isAlive() && !player.isSpectator() && Powers.has(player, ZFighterPower.INSTANCE) && !ZFighterLineage.ape(player)
			&& !FUSED.containsKey(player.getUUID()) && !player.hasAttached(RESTORE);
	}

	/** Whether he is worn out from his last fusion (and says so); creative players never are. */
	static boolean worn(ServerPlayer player, long now) {
		Long last = player.getAttached(LAST);
		if (last == null || now - last >= COOLDOWN || player.isCreative()) {
			return false;
		}
		long minutes = (COOLDOWN - (now - last) + 1199L) / 1200L;
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_fusion_tired", minutes).withStyle(ChatFormatting.GRAY));
		return true;
	}

	/** Puts {@code player} through the dance pose now (the five-way's agreement), turned {@code side}. */
	static void pose(ServerPlayer player, int side, long now) {
		DANCES.put(player.getUUID(), new Dance(now, side, now + DANCE_TICKS));
		player.setAttached(DANCE, side);
	}

	private static void announce(ServerPlayer leader, ServerPlayer partner) {
		Component message = Component.translatable("message.jaymospowers.zfighter_fused", leader.getDisplayName(), partner.getDisplayName())
			.withStyle(ChatFormatting.GOLD);
		for (ServerPlayer near : leader.level().players()) {
			if (near.distanceToSqr(leader) < 64.0 * 64.0) {
				near.sendSystemMessage(message);
			}
		}
	}

	/**
	 * Every way in ends here: {@code leader} gets what {@code inside} bring added to his own (and {@code extra}, a master's),
	 * the ones inside become spectators riding his camera, and the clock starts. Returns the fusion.
	 */
	static Fused begin(ServerPlayer leader, List<ServerPlayer> inside, Kind kind, long length, float bonus, Stats extra) {
		long now = leader.level().getGameTime();
		Stats stats = extra == null ? Stats.NONE : extra;
		List<UUID> ids = new ArrayList<>();
		for (ServerPlayer partner : inside) {
			stats = stats.plus(Stats.of(partner));
			ids.add(partner.getUUID());
		}
		Fused fused = new Fused(kind, ids, null, now + length, length, stats, bonus, kind != Kind.DANCE && kind != Kind.EARRING && kind != Kind.BAND);
		FUSED.put(leader.getUUID(), fused);
		leader.setAttached(LAST, now);
		ZFighterPower.Live live = ZFighterPower.live(leader);
		for (ServerPlayer partner : inside) {
			ZFighterPower.Live partnerLive = ZFighterPower.live(partner);
			partner.setAttached(LAST, now);
			live.ki = Math.min(ZFighterPower.maxKi(leader), live.ki + partnerLive.ki);
			partnerLive.ki = 0.0F;
			partnerLive.charging = false;
			partner.setAttached(RESTORE, partner.gameMode().getId());
			partner.setGameMode(GameType.SPECTATOR);
			partner.setCamera(leader);
		}
		if (!inside.isEmpty()) {
			look(leader, inside.get(0), leader, inside.get(0));
		}
		ZFighterPower.refresh(leader, live);
		leader.setHealth(leader.getMaxHealth());
		ServerLevel level = leader.level();
		level.sendParticles(net.minecraft.core.particles.ColorParticleOption.create(ParticleTypes.FLASH, 0xFFFFFFFF), leader.getX(), leader.getY() + 1.0, leader.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
		level.sendParticles(ParticleTypes.END_ROD, true, false, leader.getX(), leader.getY() + 1.0, leader.getZ(), 80, 1.5, 1.5, 1.5, 0.2);
		level.playSound(null, leader.getX(), leader.getY(), leader.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 2.0F, 0.7F);
		level.playSound(null, leader.getX(), leader.getY(), leader.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.5F, 1.6F);
		com.jaymospowers.core.Shakes.around(level, leader.position(), 20.0, 1.5F);
		return fused;
	}

	/** (0.17.53) Marks a started fusion as a master's: {@code master} is the NPC held out of the world, {@code look} how the body is drawn. */
	static void withMaster(ServerPlayer leader, UUID master, Look look) {
		Fused fused = FUSED.get(leader.getUUID());
		if (fused != null) {
			FUSED.put(leader.getUUID(), new Fused(fused.kind, fused.inside, master, fused.until, fused.length, fused.stats, fused.bonus, true));
			leader.setAttached(LOOK, look);
		}
	}

	/** How long a fusion lasts (ticks): after {@code practice} fusions together, with {@code levels} stat levels between them. */
	static int length(int practice, int levels) {
		return FUSION_TICKS + Math.min(practice, PRACTICE_CAP) * PER_FUSION_TICKS + Math.max(0, levels) * PER_LEVEL_TICKS;
	}

	/** How many times the two have fused before (the fewer of what each remembers). */
	static int practice(ServerPlayer a, ServerPlayer b) {
		return Math.min(practiced(a, b), practiced(b, a));
	}

	private static int practiced(ServerPlayer player, ServerPlayer partner) {
		Map<String, Integer> practice = player.getAttached(PRACTICE);
		return practice == null ? 0 : practice.getOrDefault(partner.getUUID().toString(), 0);
	}

	private static void practised(ServerPlayer player, ServerPlayer partner, int times) {
		Map<String, Integer> practice = player.getAttached(PRACTICE);
		Map<String, Integer> next = practice == null ? new HashMap<>() : new HashMap<>(practice);
		next.put(partner.getUUID().toString(), times);
		player.setAttached(PRACTICE, next);
	}

	/**
	 * Halfway: the partner steps into the body where it stands and steers, and the leader goes inside to watch. The fused
	 * strength stays the same; only who adds to whom changes.
	 */
	private static void swap(ServerPlayer leader, ServerPlayer partner, Fused fused) {
		FUSED.remove(leader.getUUID());
		ZFighterPower.Live live = ZFighterPower.live(leader);
		float healthShare = leader.getHealth() / leader.getMaxHealth();
		boolean airborne = !leader.onGround();
		int flightPose = live.flying ? ZFighterPower.flightPose(leader) : 0;
		if (live.flying) {
			ZFighterPower.endFlight(leader, live);
		}
		live.charging = false;
		// what the old leader brings, on his own
		ZFighterPower.refresh(leader, live);
		ZFighterPower.Live partnerLive = ZFighterPower.live(partner);
		Fused swapped = fused.swappedTo(leader.getUUID(), Stats.of(leader));
		float ki = live.ki;
		live.ki = 0.0F;
		// the partner out, in the body's place
		Vec3 motion = leader.getDeltaMovement();
		Integer mode = partner.getAttached(RESTORE);
		partner.removeAttached(RESTORE);
		partner.setCamera(partner);
		partner.teleportTo(leader.level(), leader.getX(), leader.getY(), leader.getZ(), Set.of(), leader.getYRot(), leader.getXRot(), false);
		partner.setGameMode(GameType.byId(mode == null ? GameType.SURVIVAL.getId() : mode));
		partner.setDeltaMovement(motion);
		partner.resetFallDistance();
		if (airborne) {
			// just in case the hover doesn't take: a moment to take off again before the fall is a real one
			partner.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOW_FALLING, 60, 0, false, false));
		}
		FUSED.put(partner.getUUID(), swapped);
		// the old leader in
		leader.setAttached(RESTORE, leader.gameMode().getId());
		leader.setGameMode(GameType.SPECTATOR);
		leader.setCamera(partner);
		leader.removeAttached(LOOK);
		// the body looks the same after they change places: the first dancer stays first
		look(partner, leader, leader, partner);
		ZFighterPower.refresh(partner, partnerLive);
		partnerLive.ki = Math.min(ZFighterPower.maxKi(partner), ki);
		partner.setHealth(Math.max(1.0F, healthShare * partner.getMaxHealth()));
		if (flightPose > 0) {
			// mid-flight: he takes over hovering where the body was
			partner.setDeltaMovement(Vec3.ZERO);
			ZFighterPower.hoverNow(partner, flightPose);
		}
		ServerLevel level = partner.level();
		level.sendParticles(ParticleTypes.END_ROD, true, false, partner.getX(), partner.getY() + 1.0, partner.getZ(), 40, 0.8, 1.0, 0.8, 0.1);
		level.playSound(null, partner.getX(), partner.getY(), partner.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.5F, 0.8F);
		Component message = Component.translatable("message.jaymospowers.zfighter_fusion_swap", partner.getDisplayName()).withStyle(ChatFormatting.GOLD);
		partner.sendOverlayMessage(message);
		leader.sendOverlayMessage(message);
	}

	/** The fusion ends: the leader loses what the partner gave, and the partner steps out beside him. */
	static void defuse(MinecraftServer server, UUID leaderId) {
		Fused fused = FUSED.remove(leaderId);
		if (fused == null) {
			return;
		}
		ServerPlayer leader = server.getPlayerList().getPlayer(leaderId);
		if (leader != null) {
			leader.removeAttached(LOOK);
			ZFighterPower.Live live = ZFighterPower.live(leader);
			ZFighterPower.refresh(leader, live);
			live.ki = Math.min(live.ki, ZFighterPower.maxKi(leader));
			leader.setHealth(Math.min(leader.getHealth(), leader.getMaxHealth()));
			leader.level().sendParticles(net.minecraft.core.particles.ColorParticleOption.create(ParticleTypes.FLASH, 0xFFFFFFFF), leader.getX(), leader.getY() + 1.0, leader.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
			leader.level().playSound(null, leader.getX(), leader.getY(), leader.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.5F, 1.0F);
			leader.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_unfused").withStyle(ChatFormatting.GOLD));
		}
		for (UUID inner : fused.inside) {
			ServerPlayer partner = server.getPlayerList().getPlayer(inner);
			if (partner != null) {
				giveBack(partner, leader);
			}
		}
		// (0.17.53) the master comes out of hiding; the earrings and bands crack
		if (fused.master != null) {
			ZFighterMasterFusion.release(server, fused.master, leader);
		}
		if (leader != null && (fused.kind == Kind.EARRING || fused.kind == Kind.BAND)) {
			ZFighterFusionItems.crack(leader, fused.kind);
		}
	}

	/** Marks {@code body} as the two fused, {@code inner} watching from inside. */
	private static void look(ServerPlayer body, ServerPlayer inner, ServerPlayer first, ServerPlayer second) {
		int tail = ZFighterLineage.of(inner) != ZFighterLineage.WARRIOR || ZFighterLineage.tailCut(inner) ? 0 : ZFighterLineage.tailOut(inner) ? 1 : 2;
		body.setAttached(LOOK, new Look(first.getUUID(), second.getUUID(), ZFighterLineage.of(inner), tail));
	}

	/** The partner back in his own body (on joining, too, if a fusion was cut short by a log-out or a crash). */
	private static void giveBack(ServerPlayer partner, ServerPlayer leader) {
		Integer mode = partner.getAttached(RESTORE);
		if (mode == null) {
			return;
		}
		partner.removeAttached(RESTORE);
		partner.removeAttached(LOOK);
		partner.setCamera(partner);
		if (leader != null) {
			Vec3 side = leader.getLookAngle().multiply(1.0, 0.0, 1.0).normalize().cross(new Vec3(0.0, 1.0, 0.0));
			partner.teleportTo(leader.level(), leader.getX() + side.x * 1.5, leader.getY(), leader.getZ() + side.z * 1.5, Set.of(), leader.getYRot(), 0.0F,
				false);
		}
		partner.setGameMode(GameType.byId(mode));
	}

	static Fused of(ServerPlayer player) {
		return FUSED.get(player.getUUID());
	}

	// ---------------------------------------------------------------- what fusing adds (read by ZFighterPower and ZFighterBattle)

	static double fists(ServerPlayer player) {
		Fused fused = of(player);
		return fused == null ? 0.0 : fused.stats.fists();
	}

	static double health(ServerPlayer player) {
		Fused fused = of(player);
		return fused == null ? 0.0 : fused.stats.health();
	}

	static double bonus(ServerPlayer player) {
		Fused fused = of(player);
		return fused == null ? 0.0 : fused.bonus;
	}

	/** (0.17.53) How much bigger the fused body is (the five-way's giant), as a multiplier of its base size; 0 for none. */
	static double scale(ServerPlayer player) {
		Fused fused = of(player);
		return fused != null && fused.kind == Kind.FIVE ? ZFighterFiveFusion.SCALE : 0.0;
	}

	static int ki(ServerPlayer player) {
		Fused fused = of(player);
		return fused == null ? 0 : fused.stats.ki();
	}

	static float power(ServerPlayer player, float own) {
		Fused fused = of(player);
		return fused == null ? own : (own + fused.stats.power()) * (1.0F + fused.bonus);
	}

	/** Damage taken, after the partner's toughness too. */
	static float toughness(ServerPlayer player, float damage) {
		Fused fused = of(player);
		return fused == null ? damage : damage * (1.0F - fused.stats.toughness());
	}

	/** (0.17.53) How the fusion {@code player} steers came about (null: he steers none). */
	static Kind kind(ServerPlayer player) {
		Fused fused = of(player);
		return fused == null ? null : fused.kind;
	}

	/** Attribute ids for {@link ZFighterPower#refresh}. */
	static final net.minecraft.resources.Identifier FIST_ID = JaymosPowers.id("zfighter_fusion_fists");
	static final net.minecraft.resources.Identifier BONUS_ID = JaymosPowers.id("zfighter_fusion_bonus");
	static final net.minecraft.resources.Identifier HEALTH_ID = JaymosPowers.id("zfighter_fusion_health");
	static final net.minecraft.resources.Identifier SCALE_ID = JaymosPowers.id("zfighter_fusion_scale");
}
