package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.gamerules.GameRules;

/**
 * The living nightmare. Andy (2026-11-13): "phantoms now spawn as hallucinations lunging at you and disappearing, and
 * appearing for only the player. they have a chance to appear after you sleep, as a 'living nightmare' a curse that can be
 * afflicted upon you by encounters with supernatural mobs."
 * <ul>
 * <li><b>No phantoms in the world.</b> The sleepless night's spawner ({@code PhantomSpawnerMixin}) never puts a real one
 * in the sky. Where it would have (the same rule: the game rule on, night, under open sky above the sea, three days and more
 * without rest, the same odds), the sleepless player alone sees them instead ({@link #sleepless}): one to three, coming out
 * of the dark at them.</li>
 * <li><b>The curse.</b> A supernatural thing can leave it on you ({@link #NIGHTMARE}, a count of sleeps): a wound from one
 * ({@link #WOUND_CURSE}, {@link #OVERWHELMING_WOUND_CURSE} from an overwhelming one), the day's first sight of an
 * overwhelming one ({@link #SIGHT_CURSE}), and always a faint at one ({@link Overcome}). It lasts {@link #NIGHTS} sleeps
 * and up to {@link #NIGHTS_MORE} more; a fresh encounter while it is on tops it up to at least that many again.</li>
 * <li><b>After you sleep.</b> Each sleep through the night spends one, and may bring the nightmare with you into the
 * morning ({@link #chance}: likelier the more you know of the other side, {@link Exposure}): for a minute after you wake,
 * phantoms come at you that nobody else can see, circling, lunging at your face and gone the instant before they strike.
 * Striking one makes it gone sooner. Living it through adds to your exposure.</li>
 * <li><b>Cures.</b> Whatever lifts a curse ({@code PowersCompat.cureCurses}: a priest's or a Cleric's restoration) lifts
 * this too; otherwise it runs out with its sleeps.</li>
 * </ul>
 * The visions themselves are drawn by the client ({@code NightmareClient}) from {@link Visions}; the server never makes an
 * entity. The character menu says "Sleep: Haunted" while it is on (MenuCompat).
 */
public final class LivingNightmare {
	// ================================================================================================================
	// Tuning
	/** How many sleeps the curse lasts at the least, and up to how many more. */
	static final int NIGHTS = 3, NIGHTS_MORE = 4;
	/** The chance a cursed sleep brings the nightmare: at the least, more for each point of exposure, at the most. */
	static final float CHANCE = 0.4F, PER_POINT = 0.004F, MOST = 0.8F;
	/** The chance of being cursed: a wound from the supernatural, from an overwhelming one; the day's first sight of an overwhelming one. */
	static final float WOUND_CURSE = 0.06F, OVERWHELMING_WOUND_CURSE = 0.25F, SIGHT_CURSE = 0.1F;
	/** A waking nightmare: this many phantoms and up to this many more, over this long (ticks). */
	static final int WAKING = 2, WAKING_MORE = 3, WAKING_TICKS = 20 * 60;
	/** A sleepless night's visions: over this long (ticks). */
	static final int SLEEPLESS_TICKS = 20 * 40;
	/** Vanilla's sleepless rules: rest this long (ticks) before any; looked at every this much and up to this much more (ticks). */
	static final int RESTLESS = 72000, SLEEPLESS_EVERY = 60 * 20, SLEEPLESS_MORE = 60 * 20;
	/** Exposure for a nightmare lived through. */
	static final int LIVED_THROUGH = 1;
	// ================================================================================================================

	/** Sleeps left under the curse. Saved, kept through a death, synced to the cursed player (their menu). */
	public static final AttachmentType<Integer> NIGHTMARE = AttachmentRegistry.create(JaymosKingdoms.id("living_nightmare"),
		builder -> builder.persistent(Codec.INT).copyOnDeath().syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.targetOnly()));

	/** Server to one player: phantoms only they will see. How many, over how long (ticks), and whether it's a waking nightmare. */
	public record Visions(int count, int ticks, boolean waking) implements CustomPacketPayload {
		public static final Type<Visions> TYPE = new Type<>(JaymosKingdoms.id("visions"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Visions> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeVarInt(p.count);
				buf.writeVarInt(p.ticks);
				buf.writeBoolean(p.waking);
			}, buf -> new Visions(buf.readVarInt(), buf.readVarInt(), buf.readBoolean()));

		@Override
		public Type<Visions> type() {
			return TYPE;
		}
	}

	/** When each player's nightmare ends (game time), so it's counted as lived through once it does. */
	private static final Map<UUID, Long> HAUNTED = new HashMap<>();
	/** When the sleepless spawner looks next, by level (game time). */
	private static final Map<ServerLevel, Long> NEXT_SLEEPLESS = new java.util.WeakHashMap<>();

	private LivingNightmare() {
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Visions.TYPE, Visions.CODEC);
		EntitySleepEvents.STOP_SLEEPING.register((entity, pos) -> {
			if (entity instanceof ServerPlayer player && player.isSleepingLongEnough()) {
				slept(player);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(LivingNightmare::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			HAUNTED.clear();
			NEXT_SLEEPLESS.clear();
		});
	}

	/** Sleeps left under the curse (0: none). */
	public static int nights(LivingEntity e) {
		Integer n = e.getAttached(NIGHTMARE);
		return n == null ? 0 : n;
	}

	/** {@code player} is cursed (or the curse is topped up) for the least and a few more sleeps. */
	static void curse(ServerPlayer player) {
		if (player.isCreative() || player.isSpectator()) {
			return;
		}
		int n = NIGHTS + player.getRandom().nextInt(NIGHTS_MORE + 1);
		if (n > nights(player)) {
			player.setAttached(NIGHTMARE, n);
		}
	}

	/** Lifts the curse; whether there was one. */
	public static boolean cure(ServerPlayer player) {
		if (!player.hasAttached(NIGHTMARE)) {
			return false;
		}
		player.removeAttached(NIGHTMARE);
		return true;
	}

	/** A supernatural {@code attacker} wounded {@code player}: it may leave the curse. */
	static void wounded(ServerPlayer player, LivingEntity attacker) {
		float chance = Exposure.overwhelming(attacker) ? OVERWHELMING_WOUND_CURSE : WOUND_CURSE;
		if (player.getRandom().nextFloat() < chance) {
			curse(player);
		}
	}

	/** The day's first sight of an overwhelming {@code thing}: it may leave the curse. */
	static void sighted(ServerPlayer player, LivingEntity thing) {
		if (player.getRandom().nextFloat() < SIGHT_CURSE) {
			curse(player);
		}
	}

	/** A faint at the sight of one ({@link Overcome}) always leaves it. */
	static void overcome(ServerPlayer player) {
		curse(player);
	}

	/** The chance one cursed sleep brings the nightmare for {@code player}. */
	static float chance(ServerPlayer player) {
		return Math.min(MOST, CHANCE + PER_POINT * Exposure.of(player));
	}

	/** {@code player} slept the night through: a sleep is spent, and the nightmare may follow them out of it. */
	private static void slept(ServerPlayer player) {
		int n = nights(player);
		if (n <= 0) {
			return;
		}
		if (n <= 1) {
			player.removeAttached(NIGHTMARE);
		} else {
			player.setAttached(NIGHTMARE, n - 1);
		}
		if (player.getRandom().nextFloat() < chance(player)) {
			send(player, WAKING + player.getRandom().nextInt(WAKING_MORE + 1), WAKING_TICKS, true);
		}
	}

	/** The visions go to {@code player}'s client alone. */
	private static void send(ServerPlayer player, int count, int ticks, boolean waking) {
		if (!ServerPlayNetworking.canSend(player, Visions.TYPE)) {
			return;
		}
		ServerPlayNetworking.send(player, new Visions(count, ticks, waking));
		HAUNTED.put(player.getUUID(), player.level().getGameTime() + ticks);
		// the first breath of it: a sharp intake, only they hear
		Exposure.hear(player, SoundEvents.PHANTOM_AMBIENT, 0.6F, 0.6F);
	}

	private static void tick(MinecraftServer server) {
		if (HAUNTED.isEmpty() || server.getTickCount() % 20 != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Long until = HAUNTED.get(player.getUUID());
			if (until != null && player.level().getGameTime() >= until) {
				HAUNTED.remove(player.getUUID());
				if (player.isAlive()) {
					Exposure.add(player, LIVED_THROUGH);
				}
			}
		}
	}

	/**
	 * In place of the vanilla phantom spawner ({@code PhantomSpawnerMixin}): the same rules, but the sleepless see their
	 * phantoms alone, and nothing real is spawned.
	 */
	public static void sleepless(ServerLevel level) {
		if (!level.getGameRules().get(GameRules.SPAWN_PHANTOMS)) {
			return;
		}
		long now = level.getGameTime();
		Long next = NEXT_SLEEPLESS.get(level);
		if (next == null) {
			NEXT_SLEEPLESS.put(level, now + SLEEPLESS_EVERY + level.getRandom().nextInt(SLEEPLESS_MORE));
			return;
		}
		if (now < next) {
			return;
		}
		NEXT_SLEEPLESS.put(level, now + SLEEPLESS_EVERY + level.getRandom().nextInt(SLEEPLESS_MORE));
		if (level.getSkyDarken() < 5 && level.dimensionType().hasSkyLight()) {
			return;
		}
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator() || player.isCreative()) {
				continue;
			}
			BlockPos at = player.blockPosition();
			if (level.dimensionType().hasSkyLight() && (at.getY() < level.getSeaLevel() || !level.canSeeSky(at))) {
				continue;
			}
			if (!level.getCurrentDifficultyAt(at).isHarderThan(level.getRandom().nextFloat() * 3.0F)) {
				continue;
			}
			int rest = Mth.clamp(player.getStats().getValue(Stats.CUSTOM.get(Stats.TIME_SINCE_REST)), 1, Integer.MAX_VALUE);
			if (level.getRandom().nextInt(rest) < RESTLESS) {
				continue;
			}
			int count = 1 + level.getRandom().nextInt(level.getCurrentDifficultyAt(at).getDifficulty().getId() + 1);
			send(player, Math.min(3, count), SLEEPLESS_TICKS, false);
		}
	}
}
