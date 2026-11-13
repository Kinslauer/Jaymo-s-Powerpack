package com.jaymoskingdoms;

import com.jaymoskingdoms.monsters.Werewolf;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Occult exposure. Andy (2026-11-13): "kind of like how Bloodborne has insight, you unlock more understanding of the occult
 * and ethereal, villagers and players with low exposure have a chance to faint upon seeing an especially strong
 * supernatural mob" (Bloodborne is a commercial game: the idea only, our own name, numbers and effects).
 * <p>
 * Everyone who thinks for themselves carries a count of what they have seen of the other side ({@link #EXPOSURE}): players
 * start at nothing and gain it, the world's folk are born with a little ({@link #FOLK_ROLL}, more for priests and guards)
 * and gain it the same way. It never goes down; only a new life (Powers' reincarnation wipe) clears it.
 * <ul>
 * <li><b>What counts.</b> The supernatural ({@link #supernatural}): the {@link #SUPERNATURAL} tag (the restless dead
 * beyond the common zombie and skeleton, ghosts, shades, vampires' kind, the werewolf as a beast, the things of the
 * End), a vampire that has shown itself, a Bane, a blood witch. The Z Fighters' demons are named vanilla mobs and don't count. The especially strong ({@link #overwhelming}): the {@link #OVERWHELMING}
 * tag, any supernatural boss, anything supernatural of {@link #STRONG} health or more, and the dreadful ({@link Fear#FRIGHTFUL})
 * that are also supernatural.</li>
 * <li><b>How it grows</b> (players): a kind seen for the first time ({@link #FIRST_SIGHT}); a kind seen again, once a day
 * ({@link #SIGHT}, {@link #OVERWHELMING_SIGHT}); a supernatural kill, once a day each kind, or every overwhelming one
 * ({@link #KILL}, {@link #OVERWHELMING_KILL}); a wound from one, once a day each kind ({@link #WOUNDED}); a living
 * nightmare lived through ({@link LivingNightmare}); coming round from a faint ({@link Overcome}). Seeing means in front
 * of you, in sight, within {@link #SIGHT_RANGE}. Each gain is a low tone in your own ears, and a rank gained a bell: no
 * text.</li>
 * <li><b>What it opens</b> ({@link #rank}): <b>Unsettled</b> ({@link #UNSETTLED}): you hear the unseen: a whisper from
 * the way a supernatural thing is that you can't see. <b>Seasoned</b>: unease comes slower and you no longer faint.
 * <b>Initiated</b>: the ethereal: what walks unseen (invisible creatures, a hidden beast) shows to you as a pale shape
 * ({@code EtherealSightMixin}). <b>Unveiled</b>: the hidden cursed show themselves to you: a vampire, a werewolf in their
 * own skin, the blighted, a stolen life's copy, outlined faintly in violet when near ({@link #UNVEIL_RANGE}); and nothing
 * unnerves you any more. The other side notices you too: the more you know, the likelier a cursed sleep brings its
 * nightmare ({@link LivingNightmare#chance}).</li>
 * </ul>
 * The character menu shows the rank and the count (MenuCompat).
 */
public final class Exposure {
	// ================================================================================================================
	// Tuning
	/** Where each rank starts (points): Untouched, Unsettled, Seasoned, Initiated, Unveiled. */
	static final int[] RANK_FROM = {0, 5, 15, 30, 60};
	public static final int UNTOUCHED = 0, UNSETTLED = 1, SEASONED = 2, INITIATED = 3, UNVEILED = 4;
	/** Points: a kind seen for the first time; seen again (once a day each kind); an overwhelming one seen (once a day each kind). */
	static final int FIRST_SIGHT = 3, SIGHT = 1, OVERWHELMING_SIGHT = 2;
	/** Points: a supernatural kill (once a day each kind); an overwhelming one killed (every time); a wound from one (once a day each kind). */
	static final int KILL = 1, OVERWHELMING_KILL = 4, WOUNDED = 1;
	/** The most anyone holds. */
	static final int MOST = 99;
	/** Health from which anything supernatural is overwhelming. */
	static final float STRONG = 100.0F;
	/** Seeing: how far, how wide (the cosine of half the cone), and how often players look (ticks). */
	static final double SIGHT_RANGE = 24.0, SIGHT_CONE = 0.5;
	static final int LOOK_EVERY = 20;
	/** The world's folk are born with up to this; a priest (cleric) has this more, a guard this more. */
	static final int FOLK_ROLL = 10, CLERIC = 20, GUARD = 6;
	/** Unsettled: a whisper from an unseen supernatural thing this near, listened for this often (ticks), this likely. */
	static final double WHISPER_RANGE = 20.0;
	static final int WHISPER_EVERY = 100;
	static final float WHISPER_CHANCE = 0.35F;
	/** Unveiled: the hidden cursed show within this far, looked for this often (ticks). */
	static final double UNVEIL_RANGE = 16.0;
	static final int UNVEIL_EVERY = 20;
	// ================================================================================================================

	public static final TagKey<EntityType<?>> SUPERNATURAL = TagKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id("supernatural"));
	public static final TagKey<EntityType<?>> OVERWHELMING = TagKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id("overwhelming"));

	/** How much of the other side someone has seen. Saved, kept through a death, synced to a player themselves. */
	public static final AttachmentType<Integer> EXPOSURE = AttachmentRegistry.create(JaymosKingdoms.id("exposure"),
		builder -> builder.persistent(Codec.INT).copyOnDeath().syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.targetOnly()));
	/** The supernatural kinds a player has ever seen (entity type ids, or "vampire", "bane"). Saved. */
	static final AttachmentType<List<String>> SEEN = AttachmentRegistry.create(JaymosKingdoms.id("occult_seen"),
		builder -> builder.persistent(Codec.STRING.listOf()).copyOnDeath());

	/** Server to an Unveiled player: the hidden cursed near them (entity ids). Sent when it changes. */
	public record Unveiled(List<Integer> ids) implements CustomPacketPayload {
		public static final Type<Unveiled> TYPE = new Type<>(JaymosKingdoms.id("unveiled"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Unveiled> CODEC = CustomPacketPayload.codec(
			(p, buf) -> buf.writeVarIntArray(p.ids.stream().mapToInt(Integer::intValue).toArray()),
			buf -> new Unveiled(java.util.Arrays.stream(buf.readVarIntArray(4096)).boxed().toList()));

		@Override
		public Type<Unveiled> type() {
			return TYPE;
		}
	}

	/** The client's copy: the hidden cursed it was last told of. */
	public static volatile java.util.Set<Integer> clientUnveiled = java.util.Set.of();

	/** By player, then kind (with "!" before for a kill, "~" for a wound): the day (overworld clock) it last counted. */
	private static final Map<UUID, Map<String, Long>> COUNTED = new HashMap<>();
	/** What each Unveiled player was last sent. */
	private static final Map<UUID, List<Integer>> SENT = new HashMap<>();

	private Exposure() {
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Unveiled.TYPE, Unveiled.CODEC);
		ServerTickEvents.END_SERVER_TICK.register(Exposure::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			COUNTED.clear();
			SENT.clear();
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((victim, source) -> {
			if (source.getEntity() instanceof ServerPlayer killer && victim != killer && supernatural(victim)) {
				if (overwhelming(victim)) {
					add(killer, OVERWHELMING_KILL);
				} else {
					once(killer, "!" + kind(victim), KILL);
				}
			}
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((victim, source, base, taken, blocked) -> {
			if (victim instanceof ServerPlayer player && !blocked && taken > 0.0F && source.getEntity() instanceof LivingEntity attacker
				&& attacker != player && supernatural(attacker)) {
				once(player, "~" + kind(attacker), WOUNDED);
				LivingNightmare.wounded(player, attacker);
			}
		});
	}

	// ---- who counts ----------------------------------------------------------------------------------------------------

	/** Whether {@code e} is a thing of the other side (see the class notes). Server side. */
	public static boolean supernatural(Entity e) {
		if (!(e instanceof LivingEntity living) || !living.isAlive()) {
			return false;
		}
		if (e instanceof Werewolf w) {
			// by day it is only a man
			return !w.isHuman();
		}
		// (a blood witch is a Folk role, not a creature of its own: the face of the forbidden arts)
		return e.is(SUPERNATURAL) || Vampires.exposed(e) || Banes.is(e) || e instanceof Folk f && f.role() == Sorcery.BLOOD_WITCH;
	}

	/** Whether {@code e} is supernatural and especially strong: the sight that can overcome the unready ({@link Overcome}). */
	public static boolean overwhelming(Entity e) {
		if (!supernatural(e)) {
			return false;
		}
		LivingEntity living = (LivingEntity)e;
		return e.is(OVERWHELMING) || e.is(Fear.FRIGHTFUL) || Legendary.boss(e) || Banes.is(e) || living.getMaxHealth() >= STRONG;
	}

	/** The kind {@code e} counts as for first sights and the day's tally. */
	static String kind(Entity e) {
		if (Banes.is(e)) {
			return "bane";
		}
		if (Vampires.isVampire(e) && !(e instanceof Werewolf)) {
			return "vampire";
		}
		return BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString();
	}

	/** Whether {@code e} is one of the hidden cursed an Unveiled eye sees through. */
	static boolean hiddenCursed(Entity e) {
		if (!(e instanceof LivingEntity) || !e.isAlive()) {
			return false;
		}
		if (e instanceof Werewolf w) {
			return w.isHuman();
		}
		return Vampires.isVampire(e) && !Vampires.exposed(e) || Lycans.cursed(e) && !Lycans.changed(e) || Doubles.isCopy(e)
			|| e instanceof ServerPlayer p && HollowBlight.blighted(p);
	}

	// ---- the count -----------------------------------------------------------------------------------------------------

	/** {@code e}'s exposure. A villager or the world's folk are given theirs now (server) if they have none yet. */
	public static int of(LivingEntity e) {
		Integer had = e.getAttached(EXPOSURE);
		if (had != null) {
			return had;
		}
		if (e.level().isClientSide() || !Fear.fearsAtAll(e) || e instanceof net.minecraft.world.entity.player.Player) {
			return 0;
		}
		int born = e.getRandom().nextInt(FOLK_ROLL + 1);
		if (e instanceof Villager v && v.getVillagerData().profession().is(VillagerProfession.CLERIC)) {
			born += CLERIC;
		}
		if (Downed.isGuard(e)) {
			born += GUARD;
		}
		e.setAttached(EXPOSURE, born);
		return born;
	}

	/** The rank {@code points} make (0 Untouched to 4 Unveiled). */
	public static int rank(int points) {
		int r = 0;
		for (int i = 0; i < RANK_FROM.length; i++) {
			if (points >= RANK_FROM[i]) {
				r = i;
			}
		}
		return r;
	}

	public static int rankOf(LivingEntity e) {
		return rank(of(e));
	}

	/** The rank's lang key part. */
	public static String rankKey(int rank) {
		return switch (Mth.clamp(rank, 0, 4)) {
			case 0 -> "untouched";
			case 1 -> "unsettled";
			case 2 -> "seasoned";
			case 3 -> "initiated";
			default -> "unveiled";
		};
	}

	/** {@code e} gains {@code n}: a low tone in a player's own ears, and a bell when a rank comes with it. */
	public static void add(LivingEntity e, int n) {
		if (n <= 0 || e.level().isClientSide()) {
			return;
		}
		int was = of(e);
		int now = Math.min(MOST, was + n);
		if (now == was) {
			return;
		}
		e.setAttached(EXPOSURE, now);
		if (e instanceof ServerPlayer player) {
			if (rank(now) > rank(was)) {
				hear(player, SoundEvents.BELL_RESONATE, 0.7F, 0.6F);
			} else {
				hear(player, SoundEvents.SOUL_ESCAPE.value(), 0.8F, 0.5F);
			}
		}
	}

	/** {@code player} gains {@code n} for {@code what} at most once a day. */
	static void once(ServerPlayer player, String what, int n) {
		long day = player.level().getOverworldClockTime() / 24000L;
		Map<String, Long> counted = COUNTED.computeIfAbsent(player.getUUID(), k -> new HashMap<>());
		Long last = counted.get(what);
		if (last != null && last == day) {
			return;
		}
		counted.put(what, day);
		add(player, n);
	}

	/** A sound only {@code player} hears, at their own head. */
	static void hear(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
		hearAt(player, sound, player.getEyePosition(), volume, pitch);
	}

	static void hearAt(ServerPlayer player, SoundEvent sound, Vec3 at, float volume, float pitch) {
		Holder<SoundEvent> holder = BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound);
		player.connection.send(new ClientboundSoundPacket(holder, SoundSource.AMBIENT, at.x, at.y, at.z, volume, pitch, player.getRandom().nextLong()));
	}

	// ---- seeing ---------------------------------------------------------------------------------------------------------

	/** Whether {@code who} has {@code e} before their eyes: in sight, in front, near enough. */
	static boolean sees(LivingEntity who, Entity e, double range) {
		if (who.distanceToSqr(e) > range * range || !who.hasLineOfSight(e)) {
			return false;
		}
		Vec3 to = e.getEyePosition().subtract(who.getEyePosition());
		double len = to.length();
		return len < 2.0 || who.getViewVector(1.0F).dot(to.scale(1.0 / len)) >= SIGHT_CONE;
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.isSpectator() || !player.isAlive()) {
				continue;
			}
			int age = player.tickCount;
			if (age % LOOK_EVERY == 0) {
				look(player);
			}
			int rank = rankOf(player);
			if (rank >= UNSETTLED && age % WHISPER_EVERY == 7) {
				whisper(player);
			}
			if (age % UNVEIL_EVERY == 3) {
				unveil(player, rank >= UNVEILED);
			}
		}
	}

	/** What {@code player} has before them: first sights, the day's sights, and the chance of a cursed sleep. */
	private static void look(ServerPlayer player) {
		ServerLevel level = player.level();
		List<String> seen = player.getAttached(SEEN);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(SIGHT_RANGE),
			x -> x != player && supernatural(x))) {
			if (!sees(player, e, SIGHT_RANGE)) {
				continue;
			}
			String kind = kind(e);
			boolean overwhelming = overwhelming(e);
			if (seen == null || !seen.contains(kind)) {
				List<String> next = seen == null ? new ArrayList<>() : new ArrayList<>(seen);
				next.add(kind);
				player.setAttached(SEEN, List.copyOf(next));
				seen = next;
				add(player, FIRST_SIGHT);
			}
			long day = level.getOverworldClockTime() / 24000L;
			Map<String, Long> counted = COUNTED.computeIfAbsent(player.getUUID(), k -> new HashMap<>());
			Long last = counted.get(kind);
			if (last == null || last != day) {
				counted.put(kind, day);
				add(player, overwhelming ? OVERWHELMING_SIGHT : SIGHT);
				if (overwhelming) {
					LivingNightmare.sighted(player, e);
				}
			}
		}
	}

	/** Unsettled and up: now and then, a whisper from the way an unseen supernatural thing is. */
	private static void whisper(ServerPlayer player) {
		if (player.getRandom().nextFloat() >= WHISPER_CHANCE) {
			return;
		}
		ServerLevel level = player.level();
		LivingEntity near = null;
		double best = WHISPER_RANGE * WHISPER_RANGE;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(WHISPER_RANGE),
			x -> x != player && supernatural(x))) {
			double d = e.distanceToSqr(player);
			if (d < best && !player.hasLineOfSight(e)) {
				best = d;
				near = e;
			}
		}
		if (near == null) {
			return;
		}
		// from a few blocks off, the way it is
		Vec3 way = near.position().subtract(player.position());
		Vec3 at = player.getEyePosition().add(way.normalize().scale(Math.min(4.0, way.length())));
		hearAt(player, SoundEvents.AMBIENT_SOUL_SAND_VALLEY_MOOD.value(), at, 0.5F, 0.6F + player.getRandom().nextFloat() * 0.2F);
	}

	/** Unveiled: tells the player which of the hidden cursed are near (and an empty list when that ends). */
	private static void unveil(ServerPlayer player, boolean unveiled) {
		List<Integer> ids = new ArrayList<>();
		if (unveiled) {
			for (LivingEntity e : player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(UNVEIL_RANGE),
				x -> x != player && hiddenCursed(x))) {
				ids.add(e.getId());
			}
		}
		List<Integer> was = SENT.get(player.getUUID());
		if ((was == null ? List.of() : was).equals(ids) || !ServerPlayNetworking.canSend(player, Unveiled.TYPE)) {
			return;
		}
		SENT.put(player.getUUID(), ids);
		ServerPlayNetworking.send(player, new Unveiled(ids));
	}

	/** A player's character menu row value: "Seasoned (17)". */
	public static net.minecraft.network.chat.Component describe(int points) {
		return net.minecraft.network.chat.Component.translatable("exposure.jaymoskingdoms.value",
			net.minecraft.network.chat.Component.translatable("exposure.jaymoskingdoms.rank." + rankKey(rank(points))), points);
	}
}
