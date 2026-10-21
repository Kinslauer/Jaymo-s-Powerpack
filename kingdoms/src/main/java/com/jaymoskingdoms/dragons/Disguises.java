package com.jaymoskingdoms.dragons;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.McaCompat;
import com.jaymoskingdoms.TalkNet;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.139) Eurus and Aeolus walk the world as villagers. Andy: "Eurus and Aeolus are actually MCA villagers, they can
 * spawn anywhere and will transform after questioning you if you have already beaten two other dragons"; if you haven't
 * killed the past three "they simply shoo you off unless you have wind control, a tamed dragon, an unlocked heritage,
 * arsenal, chrono or darktide" ({@link DragonRecords}).
 * <ul>
 * <li>Now and then, near a random player in the Overworld, one turns up on the surface out of sight, a short way off:
 * an MCA villager (a plain one without MCA) carrying the {@link #DISGUISE} attachment, which the client reads to dress
 * it in the dragon's own skin ({@code McaDisguiseSkinMixin}) with no clothes or hair on top ({@code McaDisguiseLayerMixin}).
 * Never more than one of each loaded, and never Aeolus with an Aeolus dragon about.</li>
 * <li>Right-clicking one has it question you ({@link TalkNet}): who are you, and what have you slain. Then it
 * {@linkplain #reveal reveals itself}: the villager goes in a burst of wind (and, for Eurus, thunder) and a wild dragon
 * stands there, after you; or it {@linkplain #shoo shoos you off}, with a shove and a wait before it'll talk again. MCA's
 * own talk screen never opens for them.</li>
 * <li>They can't be hurt (a blow does nothing: they don't get angry, they don't reveal), stay while anyone is near,
 * and wander off after {@link #UNVISITED_TICKS} with no one near.</li>
 * </ul>
 */
public final class Disguises {
	/** How often (ticks) a spawn is weighed for each kind, and the chance it comes (Eurus about once in 2.5 hours, Aeolus in 6, of one player's play). */
	static final int SPAWN_CHECK_TICKS = 1200;
	static final float EURUS_CHANCE = 1.0F / 150.0F, AEOLUS_CHANCE = 1.0F / 360.0F;
	/** How far (blocks) off a stranger turns up, and how high or low of the player's feet the ground may be. */
	static final int SPAWN_MIN = 24, SPAWN_MAX = 44, SPAWN_HEIGHT = 20;
	/** How near (blocks) an Aeolus dragon rules out Aeolus turning up as a villager. */
	static final double AEOLUS_SPACE = 192.0;
	/** How often (ticks) the loaded ones are looked after; how near (blocks) a player counts as a visit; how long (ticks, three days) with none and they leave. */
	static final int UPKEEP_TICKS = 200;
	static final double NEAR_RANGE = 48.0;
	static final long UNVISITED_TICKS = 72000L;
	/** How near (blocks) the player must stay for the questions, and how long (ticks) a shooed player waits before they'll talk again. */
	static final double TALK_RANGE = 8.0;
	static final long SHOO_TICKS = 6000L;
	/** The shove (blocks per tick, then up) that sends a player off, and how high (blocks) a dragon may be lifted to find room when it appears. */
	static final double SHOO_PUSH = 0.9, SHOO_UP = 0.35;
	static final int REVEAL_LIFT_MAX = 12;
	/** The colours of their words (RGB): Eurus' storm blue, Aeolus' old gold. */
	static final int EURUS_COLOR = 0x9FD3FF, AEOLUS_COLOR = 0xE8D9A0;

	/** The disguised villager's kind ("eurus" or "aeolus"); synced to everyone who sees it, for the skin. */
	public static final AttachmentType<String> DISGUISE = AttachmentRegistry.create(JaymosKingdoms.id("disguise"),
		builder -> builder.persistent(Codec.STRING).syncWith(ByteBufCodecs.STRING_UTF8, AttachmentSyncPredicate.all()));
	/** The game time a player was last near it (so an unvisited one leaves). */
	static final AttachmentType<Long> VISITED = AttachmentRegistry.create(JaymosKingdoms.id("disguise_visited"),
		builder -> builder.persistent(Codec.LONG));

	/** Their skins (a 64x64 player skin each, from Andy's friend): worn by the villager instead of its own look. */
	private static final Identifier EURUS_SKIN = JaymosKingdoms.id("textures/entity/dragon/eurus/villager.png");
	private static final Identifier AEOLUS_SKIN = JaymosKingdoms.id("textures/entity/dragon/aeolus/villager.png");

	/** The two of them. */
	private enum Kind {
		EURUS("eurus", DragonRecords.EURUS_BEATEN, true, EURUS_CHANCE, EURUS_COLOR, EURUS_SKIN),
		AEOLUS("aeolus", DragonRecords.AEOLUS_BEATEN, false, AEOLUS_CHANCE, AEOLUS_COLOR, AEOLUS_SKIN);

		final String id;
		/** How many kinds of wind dragon they want beaten before they show themselves. */
		final int beaten;
		/** Which MCA body they wear: Eurus' skin is the slim-armed (female) layout, Aeolus' the classic. */
		final boolean female;
		final float chance;
		final int color;
		final Identifier skin;

		Kind(String id, int beaten, boolean female, float chance, int color, Identifier skin) {
			this.id = id;
			this.beaten = beaten;
			this.female = female;
			this.chance = chance;
			this.color = color;
			this.skin = skin;
		}

		static @Nullable Kind of(@Nullable String id) {
			for (Kind k : values()) {
				if (k.id.equals(id)) {
					return k;
				}
			}
			return null;
		}

		EntityType<? extends WindDragon> dragon() {
			return this == EURUS ? Dragons.EURUS_DRAGON : Dragons.AEOLUS_DRAGON;
		}
	}

	/** A player being questioned: by whom, which kind, and which question they're on (1: who are you, 2: what have you slain). */
	private record Asking(int entityId, Kind kind, int stage) {
	}

	/** Those loaded now, by kind. */
	private static final Map<Kind, Set<Villager>> LOADED = new EnumMap<>(Kind.class);
	/** Who's being questioned. */
	private static final Map<UUID, Asking> ASKED = new HashMap<>();
	/** Who's been shooed off, by player and kind: until when (game time). */
	private static final Map<String, Long> SHOOED = new HashMap<>();

	private Disguises() {
	}

	public static void register() {
		JaymosKingdoms.LOGGER.debug("disguise attachments: {} {}", DISGUISE, VISITED);
		for (Kind k : Kind.values()) {
			LOADED.put(k, new HashSet<>());
		}
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			Kind kind = entity instanceof Villager ? kind(entity) : null;
			if (kind != null) {
				LOADED.get(kind).add((Villager)entity);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof Villager v) {
				for (Set<Villager> set : LOADED.values()) {
					set.remove(v);
				}
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(Disguises::tick);
		// not a scratch on them: a blow does nothing (they don't reveal, and they don't turn angry)
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
			entity.getAttached(DISGUISE) == null || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY));
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (world.isClientSide() || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer sp)
				|| !(entity instanceof Villager villager) || !(world instanceof ServerLevel level)) {
				return InteractionResult.PASS;
			}
			Kind kind = kind(villager);
			if (kind == null) {
				return InteractionResult.PASS;
			}
			talk(level, sp, villager, kind);
			// (SUCCESS: MCA's own talk screen stays shut)
			return InteractionResult.SUCCESS;
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> ASKED.remove(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			LOADED.values().forEach(Set::clear);
			ASKED.clear();
			SHOOED.clear();
		});
	}

	/** Which of them {@code entity} is, if it's one in disguise. */
	private static @Nullable Kind kind(Entity entity) {
		return Kind.of(entity.getAttached(DISGUISE));
	}

	/** The skin a disguised villager wears (client side, for the MCA mixins), or null if {@code entity} isn't one. */
	public static @Nullable Identifier skin(@Nullable Entity entity) {
		Kind kind = entity == null ? null : kind(entity);
		return kind == null ? null : kind.skin;
	}

	// ---- coming and going ---------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		int ticks = server.getTickCount();
		if (ticks % SPAWN_CHECK_TICKS == 0) {
			spawns(server);
		}
		if (ticks % UPKEEP_TICKS == 0) {
			upkeep(server);
		}
	}

	/** Now and then one of them turns up near a player in the Overworld. */
	private static void spawns(MinecraftServer server) {
		ServerLevel level = server.getLevel(Level.OVERWORLD);
		if (level == null) {
			return;
		}
		List<ServerPlayer> players = level.players().stream().filter(p -> !p.isSpectator()).toList();
		if (players.isEmpty()) {
			return;
		}
		RandomSource random = level.getRandom();
		for (Kind kind : Kind.values()) {
			if (random.nextFloat() >= kind.chance || !LOADED.get(kind).isEmpty()) {
				continue;
			}
			ServerPlayer player = players.get(random.nextInt(players.size()));
			if (kind == Kind.AEOLUS && !level.getEntitiesOfClass(AeolusDragon.class, player.getBoundingBox().inflate(AEOLUS_SPACE)).isEmpty()) {
				continue;
			}
			spawn(level, player, kind, random);
		}
	}

	/** On the surface {@link #SPAWN_MIN} to {@link #SPAWN_MAX} blocks off, where {@code player} can't see. */
	private static void spawn(ServerLevel level, ServerPlayer player, Kind kind, RandomSource random) {
		for (int i = 0; i < 10; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double r = SPAWN_MIN + random.nextDouble() * (SPAWN_MAX - SPAWN_MIN);
			BlockPos column = BlockPos.containing(player.getX() + Math.cos(angle) * r, player.getY(), player.getZ() + Math.sin(angle) * r);
			if (!level.isLoaded(column)) {
				continue;
			}
			BlockPos spot = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
			BlockPos floor = spot.below();
			if (Math.abs(spot.getY() - player.getY()) > SPAWN_HEIGHT || !level.getBlockState(floor).isSolid()
				|| !level.getFluidState(spot).isEmpty() || !level.getFluidState(floor).isEmpty()
				|| !level.getBlockState(spot).getCollisionShape(level, spot).isEmpty()
				|| !level.getBlockState(spot.above()).getCollisionShape(level, spot.above()).isEmpty()) {
				continue;
			}
			Villager villager = newVillager(level, kind);
			if (villager == null) {
				return;
			}
			villager.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
			if (player.hasLineOfSight(villager)) {
				villager.discard();
				continue;
			}
			villager.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), EntitySpawnReason.EVENT, null);
			if (villager.isBaby()) {
				villager.setBaby(false);
			}
			villager.setAttached(DISGUISE, kind.id);
			villager.setAttached(VISITED, level.getGameTime());
			villager.setPersistenceRequired();
			level.addFreshEntity(villager);
			JaymosKingdoms.LOGGER.info("{} walks the world as a villager, at {}", kind.id, spot);
			return;
		}
	}

	/** A new grown villager: MCA's (male or female body, for the skin's arms) if MCA is loaded, else a plain one. */
	private static @Nullable Villager newVillager(ServerLevel level, Kind kind) {
		EntityType<?> type = null;
		if (McaCompat.LOADED) {
			type = BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.fromNamespaceAndPath(McaCompat.MOD, kind.female ? "female_villager" : "male_villager")).orElse(null);
		}
		if (type == null) {
			type = EntityTypes.VILLAGER;
		}
		return type.create(level, EntitySpawnReason.EVENT) instanceof Villager v ? v : null;
	}

	/** Each loaded one: someone near is a visit; with none for {@link #UNVISITED_TICKS} it wanders off. */
	private static void upkeep(MinecraftServer server) {
		for (Set<Villager> set : LOADED.values()) {
			for (Villager v : new ArrayList<>(set)) {
				if (!v.isAlive() || !(v.level() instanceof ServerLevel level)) {
					set.remove(v);
					continue;
				}
				long now = level.getGameTime();
				if (!level.getEntitiesOfClass(ServerPlayer.class, v.getBoundingBox().inflate(NEAR_RANGE), p -> !p.isSpectator()).isEmpty()) {
					v.setAttached(VISITED, now);
					continue;
				}
				Long seen = v.getAttached(VISITED);
				if (seen == null) {
					v.setAttached(VISITED, now);
				} else if (now - seen > UNVISITED_TICKS) {
					level.sendParticles(ParticleTypes.CLOUD, v.getX(), v.getY() + 1.0, v.getZ(), 12, 0.3, 0.5, 0.3, 0.05);
					JaymosKingdoms.LOGGER.debug("{} went on its way", kind(v) == null ? "a disguise" : kind(v).id);
					v.discard();
				}
			}
		}
	}

	// ---- questioning --------------------------------------------------------------------------------------------------

	private static String key(ServerPlayer player, Kind kind) {
		return player.getUUID() + "/" + kind.id;
	}

	private static String lang(Kind kind, String what) {
		return "disguise.jaymoskingdoms." + kind.id + "." + what;
	}

	/** {@code villager} says {@code line} to {@code player}, in chat, in the speaker's colour (their own words, not a village's tongue). */
	private static void say(ServerPlayer player, Villager villager, Kind kind, Component line) {
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.says", villager.getDisplayName(), line).withColor(kind.color));
	}

	/** The player right-clicked one: the first question, unless they were just shooed off. */
	private static void talk(ServerLevel level, ServerPlayer player, Villager villager, Kind kind) {
		Long until = SHOOED.get(key(player, kind));
		if (until != null && level.getGameTime() < until) {
			say(player, villager, kind, Component.translatable(lang(kind, "busy")));
			return;
		}
		villager.getNavigation().stop();
		villager.getLookControl().setLookAt(player);
		if (!ServerPlayNetworking.canSend(player, TalkNet.Ask.TYPE)) {
			// (no screen to put it on: they decide at once)
			verdict(level, player, villager, kind);
			return;
		}
		ask(player, villager, kind, 1);
	}

	/** Question {@code stage} (1 or 2) and its three answers, which are only ever flavour: what's decided is what the player has done. */
	private static void ask(ServerPlayer player, Villager villager, Kind kind, int stage) {
		String q = stage == 1 ? "who" : "slain";
		ASKED.put(player.getUUID(), new Asking(villager.getId(), kind, stage));
		List<Component> options = List.of(Component.translatable(lang(kind, q + ".a")), Component.translatable(lang(kind, q + ".b")),
			Component.translatable(lang(kind, q + ".c")));
		ServerPlayNetworking.send(player, new TalkNet.Ask(villager.getId(), villager.getDisplayName(), Component.translatable(lang(kind, q)).withColor(kind.color),
			options, List.of(true, true, true)));
	}

	/**
	 * {@link com.jaymoskingdoms.Approaches#answer} offers every answer here first: true if it was to one of their questions
	 * (so no one else handles it).
	 */
	public static boolean answer(ServerPlayer player, TalkNet.Answer answer) {
		Asking asked = ASKED.get(player.getUUID());
		if (asked == null || asked.entityId() != answer.entityId()) {
			return false;
		}
		ASKED.remove(player.getUUID());
		if (answer.option() < 0 || answer.option() > 2 || !(player.level() instanceof ServerLevel level)
			|| !(level.getEntity(asked.entityId()) instanceof Villager villager) || !villager.isAlive()
			|| kind(villager) != asked.kind() || villager.distanceTo(player) > TALK_RANGE) {
			// (they shut the screen, or walked off: they lose interest)
			return true;
		}
		if (asked.stage() == 1) {
			ask(player, villager, asked.kind(), 2);
		} else {
			verdict(level, player, villager, asked.kind());
		}
		return true;
	}

	/** Having heard enough: if the player has the right, it shows itself; if not, it shoos them off. */
	private static void verdict(ServerLevel level, ServerPlayer player, Villager villager, Kind kind) {
		if (DragonRecords.trusted(player) || DragonRecords.beaten(player) >= kind.beaten) {
			reveal(level, player, villager, kind);
		} else {
			shoo(level, player, villager, kind);
		}
	}

	/** A dismissive line, a shove away, and a wait before it'll talk to them again. */
	private static void shoo(ServerLevel level, ServerPlayer player, Villager villager, Kind kind) {
		say(player, villager, kind, Component.translatable(lang(kind, "shoo." + (1 + level.getRandom().nextInt(3)))));
		Vec3 away = player.position().subtract(villager.position()).multiply(1.0, 0.0, 1.0);
		Vec3 push = away.lengthSqr() < 1.0E-4 ? Vec3.ZERO : away.normalize().scale(SHOO_PUSH);
		player.setDeltaMovement(push.x, SHOO_UP, push.z);
		player.syncVelocity = true;
		level.playSound(null, villager.getX(), villager.getY(), villager.getZ(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 1.0F, kind == Kind.EURUS ? 1.4F : 0.6F);
		level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.3, player.getZ(), 8, 0.3, 0.1, 0.3, 0.05);
		SHOOED.put(key(player, kind), level.getGameTime() + SHOO_TICKS);
	}

	/**
	 * The villager is gone in a burst of wind (Eurus brings thunder), and a wild dragon stands in its place, after
	 * {@code player}. A giant (Aeolus) is lifted until it fits; with no room within {@link #REVEAL_LIFT_MAX} blocks it asks
	 * the player to come into the open and the villager stays.
	 */
	private static void reveal(ServerLevel level, ServerPlayer player, Villager villager, Kind kind) {
		WindDragon dragon = kind.dragon().create(level, EntitySpawnReason.EVENT);
		if (dragon == null) {
			return;
		}
		boolean room = false;
		for (int up = 0; up <= REVEAL_LIFT_MAX && !room; up++) {
			dragon.snapTo(villager.getX(), villager.getY() + up, villager.getZ(), villager.getYRot(), 0.0F);
			room = level.noCollision(dragon);
		}
		if (!room) {
			dragon.discard();
			say(player, villager, kind, Component.translatable(lang(kind, "cramped")));
			return;
		}
		say(player, villager, kind, Component.translatable(lang(kind, "reveal")));
		double x = villager.getX(), y = villager.getY() + 1.0, z = villager.getZ();
		villager.discard();
		dragon.finalizeSpawn(level, level.getCurrentDifficultyAt(dragon.blockPosition()), EntitySpawnReason.EVENT, null);
		level.addFreshEntity(dragon);
		// (a wild dragon after this player: its own AI picks the target up from getTarget)
		dragon.setTarget(player);
		dragon.takeOff();
		level.sendParticles(ParticleTypes.CLOUD, x, y, z, 60, 1.0, 1.0, 1.0, 0.15);
		level.sendParticles(ParticleTypes.POOF, x, y, z, 30, 0.8, 0.8, 0.8, 0.1);
		level.playSound(null, x, y, z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 4.0F, kind == Kind.EURUS ? 1.0F : 0.5F);
		if (kind == Kind.EURUS) {
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 30, 1.0, 1.0, 1.0, 0.3);
			LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
			if (bolt != null) {
				// (the light and thunder only: no fires wherever it turns up)
				bolt.setVisualOnly(true);
				bolt.snapTo(x, y - 1.0, z);
				level.addFreshEntity(bolt);
			}
		}
		JaymosKingdoms.LOGGER.info("{} showed itself to {}", kind.id, player.getName().getString());
	}
}
