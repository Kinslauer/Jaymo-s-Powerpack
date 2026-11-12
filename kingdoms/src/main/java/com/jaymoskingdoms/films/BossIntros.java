package com.jaymoskingdoms.films;

import com.jaymoskingdoms.BossBars;
import com.jaymoskingdoms.Folk;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.PatronChampion;
import com.jaymoskingdoms.Rulers;
import com.jaymoskingdoms.dragons.WindDragon;
import com.jaymoskingdoms.remnants.Monoliths;
import com.jaymoskingdoms.wild.Grizzly;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A great foe's entrance. Andy: "can bosses get intro cutscenes? ... for the first encounter with ignovorius maybe, even
 * passive boss mobs can get them. just to further illustrate the majesty of the encounter." The first time a player
 * lays eyes on each kind of great creature, their own game plays a short film of it ({@code BossIntroClient}): bars, a
 * low look up from behind their shoulder, the creature alone, its roar, then the two of them in one frame. No words.
 * <ul>
 * <li><b>Who gets one:</b> whatever carries a boss bar ({@link BossBars#boss}, less the crowned villagers and quest
 * champions, who are people of a town or a job and not an encounter), anything in the {@code c:bosses} tag, and the
 * {@link #GREAT} tag (vanilla's bosses, Powers' Aether bosses, and the great gentle things: whales in the sea and in the
 * sky).</li>
 * <li><b>Once each:</b> by kind ({@link #key}), kept on the player through death ({@link #SEEN}). It is marked seen only
 * when their game says the film began, so one that couldn't play (a screen open, another film) comes again.</li>
 * <li><b>No harm while it plays</b> (their keys don't answer), in the way of {@link LoreFilms}: a blow they strike or
 * a step off the spot ends that, and the film can be skipped.</li>
 * <li>A foe that is after them shows its bar as the film ends ({@link BossBars#met}); a calm one doesn't.</li>
 * <li>Ops: {@code /kingdom bossintro} replays the nearest one's, {@code /kingdom bossintro reset} forgets them all.</li>
 * </ul>
 */
public final class BossIntros {
	/** How long the film runs (ticks), and when in it the creature roars. */
	public static final int FILM_TICKS = 130, ROAR_AT = 62;
	/** How near (blocks) and in plain sight a great creature must be for its film to begin; how often players are looked over (ticks). */
	static final double SIGHT = 40.0;
	static final int LOOK_EVERY = 20;
	/** After a film is offered: how long the game has to say it began (ticks), and how long until another is offered. */
	static final int ANSWER_TICKS = 40, OFFER_GAP = 200;
	/** Stepping this far (blocks) from where the film began ends its protection. */
	static final double LEFT_SPOT = 1.5;
	/** The roar: how loud and how deep. */
	static final float ROAR_VOLUME = 3.0F, ROAR_PITCH = 0.55F;

	/** Creatures that get an entrance without carrying a boss bar of ours (a data pack can add to it). */
	public static final TagKey<EntityType<?>> GREAT = TagKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id("boss_intro"));
	static final TagKey<EntityType<?>> BOSSES = TagKey.create(Registries.ENTITY_TYPE, net.minecraft.resources.Identifier.fromNamespaceAndPath("c", "bosses"));

	/** The Ogre King's mark ({@code OgreHolds}). */
	static final String OGRE_KING_TAG = "jaymoskingdoms.ogre_king";

	/** On a player: the kinds whose entrance they have seen. Saved, kept through death. */
	static final AttachmentType<List<String>> SEEN = AttachmentRegistry.create(JaymosKingdoms.id("boss_intros_seen"),
		builder -> builder.persistent(Codec.STRING.listOf()).copyOnDeath());

	/** The kinds of great foe {@code player} has seen make their entrance, as {@link #key} names them (for the journal). */
	public static List<String> seen(net.minecraft.world.entity.player.Player player) {
		return player.getAttachedOrElse(SEEN, List.of());
	}

	/** Tells a player's game to play {@code entity}'s entrance; {@code calm} for one that isn't after them (no shake). */
	public record Play(int entity, int ticks, int roarAt, boolean calm) implements CustomPacketPayload {
		public static final Type<Play> TYPE = new Type<>(JaymosKingdoms.id("boss_intro"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Play> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Play::entity,
			ByteBufCodecs.VAR_INT, Play::ticks, ByteBufCodecs.VAR_INT, Play::roarAt, ByteBufCodecs.BOOL, Play::calm, Play::new);

		@Override
		public Type<Play> type() {
			return TYPE;
		}
	}

	/** The player's game answers: the film {@link #BEGAN}, {@link #ENDED} (played out or skipped), or {@link #REFUSED} (it couldn't play now). */
	public record State(int entity, int what) implements CustomPacketPayload {
		public static final int BEGAN = 0, ENDED = 1, REFUSED = 2;
		public static final Type<State> TYPE = new Type<>(JaymosKingdoms.id("boss_intro_state"));
		public static final StreamCodec<RegistryFriendlyByteBuf, State> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, State::entity,
			ByteBufCodecs.VAR_INT, State::what, State::new);

		@Override
		public Type<State> type() {
			return TYPE;
		}
	}

	/** A film offered or playing: the creature, its kind, when the offer lapses or the film ends, where the player stood, whether it has begun and roared. */
	private static final class Watch {
		final int entity;
		final @Nullable String key;
		final Vec3 at;
		long until, roarAt;
		boolean began, roared;

		Watch(int entity, @Nullable String key, Vec3 at, long until) {
			this.entity = entity;
			this.key = key;
			this.at = at;
			this.until = until;
		}
	}

	private static final Map<UUID, Watch> WATCHING = new HashMap<>();
	/** When each player may next be offered a film (game time). */
	private static final Map<UUID, Long> NEXT_OFFER = new HashMap<>();

	private BossIntros() {
	}

	public static void register() {
		JaymosKingdoms.LOGGER.debug("boss intro attachment: {}", SEEN);
		PayloadTypeRegistry.clientboundPlay().register(Play.TYPE, Play.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(State.TYPE, State.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(State.TYPE, (payload, context) -> answer(context.player(), payload));
		ServerTickEvents.END_SERVER_TICK.register(BossIntros::tick);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(BossIntros::harm);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			WATCHING.remove(handler.player.getUUID());
			NEXT_OFFER.remove(handler.player.getUUID());
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			WATCHING.clear();
			NEXT_OFFER.clear();
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> dispatcher.register(Commands.literal("kingdom")
			.then(Commands.literal("bossintro").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.executes(c -> replay(c.getSource().getPlayerOrException()))
				.then(Commands.literal("reset").executes(c -> {
					c.getSource().getPlayerOrException().removeAttached(SEEN);
					return 1;
				})))));
	}

	/**
	 * The kind {@code entity} is for its entrance (one film a kind), or null if it gets none. A type that stands for
	 * several great foes is told apart: each patron's champion, a chief from a warlord.
	 */
	public static @Nullable String key(Entity entity) {
		return entity instanceof LivingEntity living && living.isAlive() ? kind(entity) : null;
	}

	/** {@link #key}, for a creature alive or fallen (the trophy it leaves, {@code Trophies}). */
	public static @Nullable String kind(Entity entity) {
		if (!(entity instanceof LivingEntity) || entity instanceof ServerPlayer) {
			return null;
		}
		String type = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
		if (entity instanceof WindDragon dragon) {
			return dragon.isTamed() ? null : type;
		}
		if (entity instanceof PatronChampion champion) {
			return champion.decoy() ? null : type + "/" + champion.patron().name().toLowerCase(java.util.Locale.ROOT);
		}
		if (entity instanceof Folk folk) {
			return folk.leader() ? type + "/leader" : null;
		}
		if (entity.entityTags().contains(OGRE_KING_TAG)) {
			return "jaymoskingdoms:ogre_king";
		}
		if (Rulers.is(entity)) {
			// (a king or a lord is met in his hall, by talking)
			return null;
		}
		if (Monoliths.giant(entity) || entity instanceof Grizzly || entity.is(BOSSES) || entity.is(GREAT)) {
			return type;
		}
		if (com.jaymoskingdoms.nether.NetherForts.warlord(entity)
			|| entity instanceof com.jaymoskingdoms.nether.Hornkin hornkin && hornkin.kind() == com.jaymoskingdoms.nether.Hornkin.Kind.WARLORD) {
			return type + "/warlord";
		}
		return null;
	}

	private static void tick(MinecraftServer server) {
		if (!WATCHING.isEmpty()) {
			watch(server);
		}
		if (server.getTickCount() % LOOK_EVERY != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.isSpectator() || !player.isAlive() || player.isSleeping() || WATCHING.containsKey(player.getUUID())
				|| !ServerPlayNetworking.canSend(player, Play.TYPE)) {
				continue;
			}
			long now = player.level().getGameTime();
			Long next = NEXT_OFFER.get(player.getUUID());
			if (next != null && now < next) {
				continue;
			}
			LivingEntity met = unmet(player);
			if (met != null) {
				offer(player, met, key(met));
			}
		}
	}

	/** The nearest great creature in plain sight of {@code player} whose entrance they haven't seen. */
	private static @Nullable LivingEntity unmet(ServerPlayer player) {
		List<String> seen = player.getAttachedOrElse(SEEN, List.of());
		LivingEntity best = null;
		double bestD = SIGHT * SIGHT;
		for (LivingEntity e : player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(SIGHT), e -> e != player)) {
			double d = e.distanceToSqr(player);
			if (d >= bestD) {
				continue;
			}
			String key = key(e);
			if (key != null && !seen.contains(key) && !e.isInvisible() && player.hasLineOfSight(e)) {
				best = e;
				bestD = d;
			}
		}
		return best;
	}

	private static void offer(ServerPlayer player, LivingEntity boss, @Nullable String key) {
		long now = player.level().getGameTime();
		boolean calm = !(boss instanceof Enemy) && !(boss instanceof Mob mob && mob.getTarget() == player);
		ServerPlayNetworking.send(player, new Play(boss.getId(), FILM_TICKS, ROAR_AT, calm));
		WATCHING.put(player.getUUID(), new Watch(boss.getId(), key, player.position(), now + ANSWER_TICKS));
		NEXT_OFFER.put(player.getUUID(), now + OFFER_GAP);
	}

	/** {@code /kingdom bossintro}: the nearest great creature's film again, seen or not. */
	private static int replay(ServerPlayer player) {
		if (!ServerPlayNetworking.canSend(player, Play.TYPE)) {
			return 0;
		}
		LivingEntity best = null;
		double bestD = Double.MAX_VALUE;
		for (LivingEntity e : player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(SIGHT * 1.5), e -> e != player && key(e) != null)) {
			double d = e.distanceToSqr(player);
			if (d < bestD) {
				best = e;
				bestD = d;
			}
		}
		if (best == null) {
			return 0;
		}
		// (no kind: a replay is never written down as seen)
		offer(player, best, null);
		return 1;
	}

	private static void answer(ServerPlayer player, State state) {
		Watch watch = WATCHING.get(player.getUUID());
		if (watch == null || watch.entity != state.entity()) {
			return;
		}
		long now = player.level().getGameTime();
		if (state.what() == State.BEGAN && !watch.began) {
			watch.began = true;
			watch.until = now + FILM_TICKS;
			watch.roarAt = now + ROAR_AT;
			if (watch.key != null) {
				List<String> seen = new ArrayList<>(player.getAttachedOrElse(SEEN, List.of()));
				if (!seen.contains(watch.key)) {
					seen.add(watch.key);
					player.setAttached(SEEN, List.copyOf(seen));
				}
			}
		} else if (state.what() == State.ENDED && watch.began) {
			end(player, watch);
		} else if (state.what() == State.REFUSED && !watch.began) {
			WATCHING.remove(player.getUUID());
		}
	}

	/** Each tick a film is on: the creature turns to its watcher and, at the moment, roars; a film run out ends. */
	private static void watch(MinecraftServer server) {
		for (Map.Entry<UUID, Watch> entry : List.copyOf(WATCHING.entrySet())) {
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			Watch watch = entry.getValue();
			if (player == null) {
				WATCHING.remove(entry.getKey());
				continue;
			}
			long now = player.level().getGameTime();
			if (now >= watch.until) {
				if (watch.began) {
					end(player, watch);
				} else {
					WATCHING.remove(entry.getKey());
				}
				continue;
			}
			if (!watch.began) {
				continue;
			}
			Entity boss = player.level().getEntity(watch.entity);
			if (boss instanceof Mob mob && mob.isAlive()) {
				mob.getLookControl().setLookAt(player, 30.0F, 30.0F);
			}
			if (!watch.roared && now >= watch.roarAt && boss instanceof LivingEntity living && living.isAlive()) {
				watch.roared = true;
				roar((ServerLevel)player.level(), living);
			}
		}
	}

	/** Its voice: a dragon, a Giant or the grizzly roars deep; anything else gives its own call. */
	private static void roar(ServerLevel level, LivingEntity boss) {
		if (boss instanceof WindDragon || boss instanceof Grizzly || Monoliths.giant(boss)) {
			level.playSound(null, boss.getX(), boss.getY(), boss.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, ROAR_VOLUME, ROAR_PITCH);
		} else if (boss instanceof Mob mob) {
			mob.playAmbientSound();
		}
	}

	private static void end(ServerPlayer player, Watch watch) {
		WATCHING.remove(player.getUUID());
		Entity boss = player.level().getEntity(watch.entity);
		if (boss instanceof Mob mob && mob.isAlive() && mob.getTarget() == player) {
			// it is after them: its bar comes up as the bars go
			BossBars.met(mob, player);
		}
	}

	/** No harm to a player while the film plays; a blow they strike, or leaving the spot, ends that. */
	private static boolean harm(LivingEntity victim, DamageSource source, float amount) {
		if (WATCHING.isEmpty()) {
			return true;
		}
		if (source.getEntity() instanceof ServerPlayer striker) {
			Watch own = WATCHING.get(striker.getUUID());
			if (own != null && own.began) {
				WATCHING.remove(striker.getUUID());
			}
		}
		Watch watch = WATCHING.get(victim.getUUID());
		if (watch == null || !watch.began) {
			return true;
		}
		if (victim.level().getGameTime() >= watch.until || victim.position().distanceTo(watch.at) > LEFT_SPOT) {
			WATCHING.remove(victim.getUUID());
			return true;
		}
		return false;
	}
}
