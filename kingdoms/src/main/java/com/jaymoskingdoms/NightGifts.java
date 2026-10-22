package com.jaymoskingdoms;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.wolf.WolfSoundVariants;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.66) The night's gifts: the moves and senses vampire and werewolf players get on top of {@link VampirePlayers} and
 * {@link WerewolfPlayers}. Ideas from the Vampires Strike Back modpack's own origins (All Rights Reserved: nothing of
 * theirs is used, only the ideas, remade here).
 *
 * <p><b>Vampires</b> (and {@link Vampires.Kind#DHAMPIR dhampirs}, half-turned, without a vampire's weaknesses or its
 * night strength):
 * <ul>
 * <li>{@code O} Blood Dash: thrown the way they look (one more in the air before they land); looking up, a great leap.
 * No fall damage till they've landed.</li>
 * <li>{@code P} in the air: Groundpound. Down like a stone; where they land, everyone near is thrown back and hurt, the
 * fall does nothing to them, and every person near shows through walls for a while ({@link Reveal}).</li>
 * <li>Kin-sense: always the way (and how far) to the nearest of their kind ({@link Sense}, drawn on the client).</li>
 * <li>Lifeleech: a kill lends them extra hearts for a while (Absorption, more with each kill close on the last).</li>
 * <li>Bat wings: Space in the air glides like an elytra, no elytra needed. The wings tire while they glide
 * ({@link #WINGS_MAX}) and heal with experience.</li>
 * <li>Under the earth, out of the sun's reach ({@link #DEEP_Y}), they're quick.</li>
 * <li>(vampires only) The sun drains their hunger.</li>
 * </ul>
 *
 * <p><b>Werewolves</b> (and {@link Lycans.Curse#half() wolfbloods}, half-cursed, who never change):
 * <ul>
 * <li>{@code O} Leap: a bound the way they look; looking up, straight up. Stronger as the beast.</li>
 * <li>{@code P} Howl: every person near shows through walls; as the beast it throws back everyone near and sends
 * villagers running. Every other werewolf hears where it came from.</li>
 * <li>Pack-sense: the way to the nearest of their kind, like kin-sense.</li>
 * <li>Frenzy: a kill heals them and drives them on (Strength, Speed).</li>
 * <li>The beast runs up walls (walk into one), and falls further before it hurts; in human form they fall a little
 * better than most too.</li>
 * <li>At night in the woods (forest, taiga, jungle) they're quick.</li>
 * </ul>
 *
 * <p>What a client needs (which gift, the beast, the wings) is mirrored in {@link #GIFT}, sent to every client.
 */
public final class NightGifts {
	/** Blood Dash: speed along the look; a great leap's up and along; the cooldown (ticks). */
	static final double DASH_SPEED = 1.7, DASH_LEAP_UP = 1.45, DASH_LEAP_ALONG = 0.35;
	static final int DASH_COOLDOWN = 30;
	/** Pitch past which they're looking up (a leap) or down. */
	static final float LOOK_UP = -50.0F;
	/** Leap: speed along and up; straight up; the cooldown (ticks); human form's share of it. */
	static final double LEAP_ALONG = 1.35, LEAP_UP = 0.75, LEAP_HIGH = 1.35, HUMAN_SHARE = 0.65;
	static final int LEAP_COOLDOWN = 40;
	/** Groundpound: speed down; the shockwave's reach, push and damage; the grace after landing (ticks); the cooldown. */
	static final double POUND_DOWN = 3.0, POUND_RADIUS = 5.0, POUND_PUSH = 1.3;
	static final float POUND_DAMAGE = 5.0F;
	static final int POUND_GRACE = 20, POUND_COOLDOWN = 100;
	/** Howl: reach, push, damage (the beast only), and cooldown (ticks). */
	static final double HOWL_RADIUS = 8.0, HOWL_PUSH = 1.5;
	static final int HOWL_COOLDOWN = 300;
	/** How far a groundpound or howl shows people, how long they show (ticks); how far other werewolves hear a howl. */
	static final double REVEAL_RANGE = 32.0, HOWL_HEARD = 256.0;
	static final int REVEAL_TICKS = 200;
	/** Kin-sense: how far it finds kin who aren't players (players: anywhere in the same world); how many it shows. */
	static final double SENSE_NPC_RANGE = 128.0;
	static final int SENSE_SHOWN = 3;
	/** Lifeleech: how long the borrowed hearts last (ticks), the most Absorption it builds to (amplifier), the heal. */
	static final int LEECH_TICKS = 600, LEECH_MAX = 2;
	static final float KILL_HEAL = 2.0F;
	/** Frenzy after a werewolf's kill (ticks); Strength amplifier in human form and as the beast. */
	static final int FRENZY_TICKS = 100, FRENZY_STRENGTH = 0, FRENZY_BEAST_STRENGTH = 2;
	/** Bat wings: seconds of gliding when whole; how much each point of experience mends. */
	public static final int WINGS_MAX = 120;
	static final int WINGS_PER_XP = 2;
	/** Below this height, out of the sky's sight, a vampire's quick. */
	static final int DEEP_Y = 60;
	/** The sun's drain on a vampire's hunger (exhaustion a second). */
	static final float SUN_EXHAUSTION = 0.05F;
	/** Safe fall distance added (blocks): human-form werewolves, the beast; the beast's fall damage share taken off. */
	static final double WOLF_SAFE_FALL = 3.0, BEAST_SAFE_FALL = 10.0, BEAST_FALL_CUT = -0.5;

	/** Old wives' talk of the night's gifts ({@link #gossip}): how often a villager's gossip turns to it, for most and
	 * for someone cursed or half-cursed (they need it more); how many lines there are. */
	static final float GOSSIP_CHANCE = 0.12F, GOSSIP_CURSED_CHANCE = 0.4F;
	static final int GOSSIP_LINES = 18;

	/** What gift a player has. */
	public static final int NONE = 0, VAMPIRE = 1, DHAMPIR = 2, WEREWOLF = 3, WOLFBLOOD = 4;

	/** The mirror a client reads: which gift, whether they're the beast now, what's left of their wings. */
	public record Gift(int kind, boolean beast, int wings) {
		static final Codec<Gift> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("kind").forGetter(Gift::kind),
			Codec.BOOL.fieldOf("beast").forGetter(Gift::beast),
			Codec.INT.fieldOf("wings").forGetter(Gift::wings)).apply(i, Gift::new));
		static final StreamCodec<ByteBuf, Gift> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Gift::kind, ByteBufCodecs.BOOL, Gift::beast, ByteBufCodecs.VAR_INT, Gift::wings, Gift::new);

		public boolean blood() {
			return this.kind == VAMPIRE || this.kind == DHAMPIR;
		}

		public boolean wolf() {
			return this.kind == WEREWOLF || this.kind == WOLFBLOOD;
		}
	}

	public static final AttachmentType<Gift> GIFT = AttachmentRegistry.create(JaymosKingdoms.id("night_gift"),
		builder -> builder.persistent(Gift.CODEC).copyOnDeath().syncWith(Gift.STREAM_CODEC.cast(), (target, viewer) -> target instanceof Player));

	/** Player → server: {@code O} (0) or {@code P} (1). */
	public record Move(int which) implements CustomPacketPayload {
		public static final Type<Move> TYPE = new Type<>(JaymosKingdoms.id("night_move"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Move> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Move::which, Move::new);

		@Override
		public Type<Move> type() {
			return TYPE;
		}
	}

	/**
	 * Server → player, every second: the nearest kin (world bearing in degrees, as yaw; distance in blocks), and how many
	 * there are; (0.7.88) and what's felt only faintly, the same way (a vampire's unturned Nightborn, a cured Nightborn's
	 * nearest vampire: {@link NightbornBlood}), distances rough.
	 */
	public record Sense(List<Integer> bearings, List<Integer> distances, int count, List<Integer> faintBearings, List<Integer> faintDistances)
		implements CustomPacketPayload {
		public static final Type<Sense> TYPE = new Type<>(JaymosKingdoms.id("night_sense"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Sense> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(8)), Sense::bearings, ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(8)), Sense::distances,
			ByteBufCodecs.VAR_INT, Sense::count, ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(8)), Sense::faintBearings,
			ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(8)), Sense::faintDistances, Sense::new);

		@Override
		public Type<Sense> type() {
			return TYPE;
		}
	}

	/** Server → player: these people show through walls for {@code ticks}. */
	public record Reveal(List<Integer> ids, int ticks) implements CustomPacketPayload {
		public static final Type<Reveal> TYPE = new Type<>(JaymosKingdoms.id("night_reveal"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Reveal> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(512)), Reveal::ids, ByteBufCodecs.VAR_INT, Reveal::ticks, Reveal::new);

		@Override
		public Type<Reveal> type() {
			return TYPE;
		}
	}

	private static final Identifier SAFE_FALL = JaymosKingdoms.id("night_safe_fall");
	private static final Identifier FALL_CUT = JaymosKingdoms.id("night_fall_cut");

	/** Cooldowns by player: O and P (game time they're ready). */
	private static final Map<UUID, long[]> READY = new HashMap<>();
	/** Thrown by a dash or leap and not landed yet (no fall damage); the air dash spent. */
	private static final Map<UUID, Long> AIRBORNE = new HashMap<>();
	private static final Set<UUID> AIR_DASHED = new HashSet<>();
	/** Mid-groundpound; and the grace after one lands (game time it ends). */
	private static final Set<UUID> POUNDING = new HashSet<>();
	private static final Map<UUID, Long> GRACE = new HashMap<>();
	/** Last known total experience (to mend wings with what they gain). */
	private static final Map<UUID, Integer> LAST_XP = new HashMap<>();
	/** The last kill (lifeleech builds when kills come close together). */
	private static final Map<UUID, Long> LAST_KILL = new HashMap<>();

	private NightGifts() {
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Move.TYPE, Move.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Sense.TYPE, Sense.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Reveal.TYPE, Reveal.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Move.TYPE, (payload, context) -> move(context.player(), payload.which()));
		ServerTickEvents.END_SERVER_TICK.register(NightGifts::tick);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (!(entity instanceof ServerPlayer player)) {
				return true;
			}
			UUID id = player.getUUID();
			Long grace = GRACE.get(id);
			if (grace != null && player.level().getGameTime() < grace && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
				return false;
			}
			if (source.is(DamageTypeTags.IS_FALL) && (AIRBORNE.remove(id) != null | POUNDING.contains(id))) {
				return false;
			}
			return true;
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (source.getEntity() instanceof ServerPlayer killer && killer != entity) {
				killed(killer, entity);
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forget(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			READY.clear();
			AIRBORNE.clear();
			AIR_DASHED.clear();
			POUNDING.clear();
			GRACE.clear();
			LAST_XP.clear();
			LAST_KILL.clear();
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> dispatcher.register(Commands.literal("kingdom")
			.then(Commands.literal("nightgift").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.argument("what", StringArgumentType.word())
					.suggests((c, b) -> {
						for (String s : new String[] {"vampire", "dhampir", "werewolf", "wolfblood", "crypt", "den", "none"}) {
							b.suggest(s);
						}
						return b.buildFuture();
					})
					.executes(c -> give(c.getSource(), StringArgumentType.getString(c, "what")))))));
	}

	private static void forget(UUID id) {
		READY.remove(id);
		AIRBORNE.remove(id);
		AIR_DASHED.remove(id);
		POUNDING.remove(id);
		GRACE.remove(id);
		LAST_XP.remove(id);
		LAST_KILL.remove(id);
	}

	// ---- who has what --------------------------------------------------------------------------------------------------

	/** The gift {@code player} has, worked out on the server. */
	static int kindOf(ServerPlayer player) {
		if (Vampires.isVampire(player)) {
			return Vampires.kind(player) == Vampires.Kind.DHAMPIR ? DHAMPIR : VAMPIRE;
		}
		Lycans.Curse curse = player.getAttached(Lycans.CURSE);
		if (curse != null) {
			return curse.half() ? WOLFBLOOD : WEREWOLF;
		}
		return NONE;
	}

	/**
	 * (0.7.66) Now and then a villager's gossip is old wives' talk of vampires and werewolves that hints at a gift and
	 * how to come by it ({@link Approaches}): the dash, the pound, the wings and XP, the kin-sense, the golden apple, the
	 * crypt; the leap, the howl, the climb, the ritual's moon, the den. Oftener for someone cursed or half-cursed.
	 */
	static @Nullable Component gossip(ServerLevel level, ServerPlayer player) {
		float chance = gift(player) != null ? GOSSIP_CURSED_CHANCE : GOSSIP_CHANCE;
		if (level.getRandom().nextFloat() >= chance) {
			return null;
		}
		return Component.translatable("talk.jaymoskingdoms.gossip.night." + level.getRandom().nextInt(GOSSIP_LINES));
	}

	/** What a client sees of {@code player}'s gift (either side), or null. */
	public static Gift gift(Entity player) {
		return player.getAttached(GIFT);
	}

	/** Bat wings to glide on now (read on both sides, from {@link #GIFT}). */
	public static boolean batWings(Player player) {
		Gift g = player.getAttached(GIFT);
		return g != null && g.blood() && g.wings() > 0 && !player.onGround() && !player.isPassenger()
			&& !player.hasEffect(MobEffects.LEVITATION) && !player.getAbilities().flying;
	}

	/** The beast climbs whatever it runs into (read on both sides). */
	public static boolean wallRun(Player player) {
		Gift g = player.getAttached(GIFT);
		return g != null && g.beast() && player.horizontalCollision && !player.isFallFlying() && !player.isSpectator();
	}

	/** Gliding on the wings and not on an elytra (or anything else that glides). */
	public static boolean onBatWings(Player player) {
		if (!player.isFallFlying()) {
			return false;
		}
		for (EquipmentSlot slot : EquipmentSlot.VALUES) {
			if (LivingEntity.canGlideUsing(player.getItemBySlot(slot), slot)) {
				return false;
			}
		}
		Gift g = player.getAttached(GIFT);
		return g != null && g.blood();
	}

	/** Someone a groundpound or howl shows: the world's people, players, illagers and witches. */
	static boolean human(LivingEntity e) {
		return e.isAlive() && (e instanceof Villager || e instanceof Folk || e instanceof Player p && !p.isSpectator() || e instanceof Raider);
	}

	// ---- every tick ----------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		ServerLevel overworld = server.overworld();
		long now = overworld.getGameTime();
		boolean night = Vampires.night(overworld.getOverworldClockTime());
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			UUID id = player.getUUID();
			if (POUNDING.contains(id) && (player.onGround() || player.isInWater())) {
				POUNDING.remove(id);
				land(player);
			}
			Long thrown = AIRBORNE.get(id);
			// (a few ticks' grace: the server hears they've left the ground a little after it threw them)
			if (thrown != null && now - thrown > 10 && (player.onGround() || player.isInWater() || player.isFallFlying()) && player.fallDistance <= 0.0) {
				AIRBORNE.remove(id);
			}
			if (player.onGround()) {
				AIR_DASHED.remove(id);
			}
			if ((now + player.getId()) % 20 != 0) {
				continue;
			}
			second(server, player, night);
		}
	}

	private static void second(MinecraftServer server, ServerPlayer player, boolean night) {
		int kind = kindOf(player);
		Gift old = player.getAttached(GIFT);
		boolean beast = WerewolfPlayers.beast(player);
		int wings = old == null || !old.blood() ? WINGS_MAX : old.wings();
		if (kind == VAMPIRE || kind == DHAMPIR) {
			if (onBatWings(player)) {
				wings = Math.max(0, wings - 1);
			}
			int xp = player.totalExperience;
			Integer last = LAST_XP.put(player.getUUID(), xp);
			if (last != null && xp > last) {
				wings = Math.min(WINGS_MAX, wings + (xp - last) * WINGS_PER_XP);
			}
		}
		Gift now = kind == NONE ? null : new Gift(kind, beast, wings);
		if (now == null) {
			if (old != null) {
				player.removeAttached(GIFT);
				ServerPlayNetworking.send(player, new Sense(List.of(), List.of(), 0, List.of(), List.of()));
			}
			falls(player, NONE, false);
			return;
		}
		if (!now.equals(old)) {
			player.setAttached(GIFT, now);
		}
		falls(player, kind, beast);
		if (player.isSpectator() || !player.isAlive()) {
			return;
		}
		ServerLevel level = player.level();
		if (now.blood()) {
			if (level.dimension() == Level.OVERWORLD && player.getY() < DEEP_Y && !level.canSeeSky(player.blockPosition().above())) {
				player.addEffect(new MobEffectInstance(MobEffects.SPEED, 40, 0, true, false, true));
			}
			if (kind == VAMPIRE && !night && !player.isCreative() && level.dimension() == Level.OVERWORLD
				&& !level.isRainingAt(player.blockPosition()) && level.canSeeSky(BlockPos.containing(player.getEyePosition()))) {
				player.causeFoodExhaustion(SUN_EXHAUSTION);
			}
		} else if (!beast && night && level.dimension() == Level.OVERWORLD) {
			Holder<net.minecraft.world.level.biome.Biome> biome = level.getBiome(player.blockPosition());
			if (biome.is(BiomeTags.IS_FOREST) || biome.is(BiomeTags.IS_TAIGA) || biome.is(BiomeTags.IS_JUNGLE)) {
				player.addEffect(new MobEffectInstance(MobEffects.SPEED, 40, 0, true, false, true));
			}
		}
		sense(server, player, now);
	}

	/** Werewolves fall better (the beast much better); everyone else as they were. */
	private static void falls(ServerPlayer player, int kind, boolean beast) {
		boolean wolf = kind == WEREWOLF || kind == WOLFBLOOD;
		modifier(player, Attributes.SAFE_FALL_DISTANCE, SAFE_FALL, wolf ? beast ? BEAST_SAFE_FALL : WOLF_SAFE_FALL : 0.0,
			AttributeModifier.Operation.ADD_VALUE);
		modifier(player, Attributes.FALL_DAMAGE_MULTIPLIER, FALL_CUT, wolf && beast ? BEAST_FALL_CUT : 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
	}

	private static void modifier(ServerPlayer player, Holder<Attribute> attribute, Identifier id, double amount, AttributeModifier.Operation op) {
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance == null) {
			return;
		}
		AttributeModifier current = instance.getModifier(id);
		if (amount == 0.0) {
			if (current != null) {
				instance.removeModifier(id);
			}
		} else if (current == null || current.amount() != amount) {
			instance.removeModifier(id);
			instance.addTransientModifier(new AttributeModifier(id, amount, op));
		}
	}

	/** Kin-sense / pack-sense: the nearest of their kind. */
	private static void sense(MinecraftServer server, ServerPlayer player, Gift gift) {
		ServerLevel level = player.level();
		List<Entity> kin = new ArrayList<>();
		for (ServerPlayer other : server.getPlayerList().getPlayers()) {
			Gift g = other.getAttached(GIFT);
			if (other != player && other.level() == level && !other.isSpectator() && g != null && g.blood() == gift.blood()) {
				kin.add(other);
			}
		}
		AABB box = player.getBoundingBox().inflate(SENSE_NPC_RANGE);
		if (gift.blood()) {
			kin.addAll(level.getEntitiesOfClass(PathfinderMob.class, box, m -> m.isAlive() && Vampires.isVampire(m)));
		} else {
			kin.addAll(level.getEntitiesOfClass(PathfinderMob.class, box, m -> m.isAlive() && !m.isInvisible()
				&& (Lycans.cursed(m) || m instanceof com.jaymoskingdoms.monsters.Werewolf w && !WerewolfPlayers.puppet(w))));
		}
		kin.sort(Comparator.comparingDouble(e -> e.distanceToSqr(player)));
		List<Integer> bearings = new ArrayList<>(), distances = new ArrayList<>();
		for (int i = 0; i < kin.size() && i < SENSE_SHOWN; i++) {
			Entity e = kin.get(i);
			double dx = e.getX() - player.getX(), dz = e.getZ() - player.getZ();
			bearings.add(Math.round((float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F));
			distances.add((int) Math.round(Math.sqrt(dx * dx + dz * dz)));
		}
		List<Integer> faintBearings = new ArrayList<>(), faintDistances = new ArrayList<>();
		if (gift.blood()) {
			// (0.7.88) and, faintly, the Nightborn not yet turned
			NightbornBlood.faint(player, faintBearings, faintDistances);
		}
		ServerPlayNetworking.send(player, new Sense(bearings, distances, kin.size(), faintBearings, faintDistances));
	}

	// ---- the moves -----------------------------------------------------------------------------------------------------

	private static void move(ServerPlayer player, int which) {
		Gift g = player.getAttached(GIFT);
		if (g == null || !player.isAlive() || player.isSpectator() || player.isPassenger() || player.isSleeping()) {
			return;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		long[] ready = READY.computeIfAbsent(player.getUUID(), k -> new long[2]);
		if (which < 0 || which > 1 || now < ready[which]) {
			return;
		}
		if (g.blood()) {
			if (which == 0 && dash(level, player)) {
				ready[0] = now + DASH_COOLDOWN;
			} else if (which == 1 && pound(level, player)) {
				ready[1] = now + POUND_COOLDOWN;
			}
		} else if (g.wolf()) {
			boolean beast = WerewolfPlayers.beast(player);
			if (which == 0 && leap(level, player, beast)) {
				ready[0] = now + LEAP_COOLDOWN;
			} else if (which == 1) {
				howl(level, player, beast);
				ready[1] = now + HOWL_COOLDOWN;
			}
		}
	}

	private static void push(ServerPlayer player, Vec3 v) {
		player.setDeltaMovement(v);
		player.needsSync = true;
		player.connection.send(new ClientboundSetEntityMotionPacket(player));
	}

	/** {@code O}, a vampire: Blood Dash. */
	private static boolean dash(ServerLevel level, ServerPlayer player) {
		boolean air = !player.onGround() && !player.isInWater();
		if (air && !AIR_DASHED.add(player.getUUID())) {
			return false;
		}
		Vec3 look = player.getLookAngle();
		// (0.7.88) night-deep: a Nightborn in the dark goes further
		double deep = NightbornBlood.deep(player) ? NightbornBlood.DEEP_DASH : 1.0;
		Vec3 v;
		if (player.getXRot() < LOOK_UP) {
			v = new Vec3(look.x * DASH_LEAP_ALONG * deep, DASH_LEAP_UP, look.z * DASH_LEAP_ALONG * deep);
		} else {
			v = look.scale(DASH_SPEED * deep);
			if (!air) {
				v = new Vec3(v.x, Math.max(v.y, 0.25), v.z);
			}
		}
		if (player.isFallFlying()) {
			player.stopFallFlying();
		}
		push(player, v);
		AIRBORNE.put(player.getUUID(), level.getGameTime());
		player.fallDistance = 0.0;
		level.sendParticles(PowersCompat.blood(0x8A0303, 1.4F), player.getX(), player.getY(0.5), player.getZ(), 24, 0.3, 0.5, 0.3, 0.0);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, player.getX(), player.getY(0.3), player.getZ(), 10, 0.25, 0.3, 0.25, 0.02);
		level.playSound(null, player.blockPosition(), SoundEvents.BAT_TAKEOFF, SoundSource.PLAYERS, 0.9F, 0.7F);
		return true;
	}

	/** {@code P} in the air, a vampire: Groundpound. */
	private static boolean pound(ServerLevel level, ServerPlayer player) {
		if (player.onGround() || player.isInWater() || POUNDING.contains(player.getUUID())) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.night.pound_air").withStyle(ChatFormatting.DARK_RED));
			return false;
		}
		if (player.isFallFlying()) {
			player.stopFallFlying();
		}
		POUNDING.add(player.getUUID());
		push(player, new Vec3(0.0, -POUND_DOWN, 0.0));
		level.playSound(null, player.blockPosition(), SoundEvents.MACE_SMASH_AIR, SoundSource.PLAYERS, 1.0F, 0.6F);
		return true;
	}

	/** The groundpound lands. */
	private static void land(ServerPlayer player) {
		ServerLevel level = player.level();
		GRACE.put(player.getUUID(), level.getGameTime() + POUND_GRACE);
		player.fallDistance = 0.0;
		AIRBORNE.remove(player.getUUID());
		// (0.7.88) night-deep: a Nightborn in the dark throws wider and harder, and sees further
		boolean deep = NightbornBlood.deep(player);
		double radius = deep ? POUND_RADIUS * NightbornBlood.DEEP_POUND_RADIUS : POUND_RADIUS;
		double push = deep ? POUND_PUSH * NightbornBlood.DEEP_POUND_PUSH : POUND_PUSH;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(radius), e -> e != player && e.isAlive()
			&& !(e instanceof ArmorStand) && !(e instanceof Player p && (p.isCreative() || p.isSpectator())))) {
			double dx = e.getX() - player.getX(), dz = e.getZ() - player.getZ();
			double d = Math.sqrt(dx * dx + dz * dz);
			if (d > radius) {
				continue;
			}
			double share = 1.0 - d / (radius + 1.0);
			e.hurtServer(level, level.damageSources().playerAttack(player), (float) (POUND_DAMAGE * share));
			e.knockback(push * share, -dx, -dz, level.damageSources().playerAttack(player), 0.0F);
			e.setDeltaMovement(e.getDeltaMovement().add(0.0, 0.35 * share, 0.0));
			e.needsSync = true;
		}
		level.sendParticles(ParticleTypes.EXPLOSION, player.getX(), player.getY(), player.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
		for (int i = 0; i < 32; i++) {
			double a = i * Mth.TWO_PI / 32.0;
			level.sendParticles(ParticleTypes.CLOUD, player.getX() + Math.cos(a) * 1.5, player.getY() + 0.1, player.getZ() + Math.sin(a) * 1.5, 0,
				Math.cos(a), 0.05, Math.sin(a), 0.35);
		}
		level.sendParticles(PowersCompat.blood(0x8A0303, 1.6F), player.getX(), player.getY() + 0.2, player.getZ(), deep ? 64 : 40, radius * 0.4, 0.1,
			radius * 0.4, 0.0);
		level.playSound(null, player.blockPosition(), SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.2F, deep ? 0.55F : 0.7F);
		reveal(level, player, deep ? REVEAL_RANGE * NightbornBlood.DEEP_REVEAL : REVEAL_RANGE);
	}

	/** {@code O}, a werewolf: Leap. */
	private static boolean leap(ServerLevel level, ServerPlayer player, boolean beast) {
		if (!player.onGround() && !player.isInWater() && !player.onClimbable()) {
			return false;
		}
		double share = beast ? 1.0 : HUMAN_SHARE;
		Vec3 look = player.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0.0, look.z);
		flat = flat.lengthSqr() < 1.0E-4 ? Vec3.ZERO : flat.normalize();
		Vec3 v = player.getXRot() < LOOK_UP ? new Vec3(flat.x * 0.2, LEAP_HIGH * share, flat.z * 0.2)
			: new Vec3(flat.x * LEAP_ALONG * share, LEAP_UP * share, flat.z * LEAP_ALONG * share);
		push(player, v);
		AIRBORNE.put(player.getUUID(), level.getGameTime());
		level.sendParticles(ParticleTypes.POOF, player.getX(), player.getY(), player.getZ(), 12, 0.3, 0.05, 0.3, 0.02);
		level.playSound(null, player.blockPosition(), beast ? SoundEvents.RAVAGER_STEP : SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.8F,
			beast ? 0.8F : 1.4F);
		return true;
	}

	/** {@code P}, a werewolf: Howl. */
	private static void howl(ServerLevel level, ServerPlayer player, boolean beast) {
		level.playSound(null, player.blockPosition(), SoundEvents.WOLF_SOUNDS.get(WolfSoundVariants.SoundSet.BIG).adultSounds().ambientSound().value(),
			SoundSource.PLAYERS, beast ? 4.0F : 2.0F, beast ? 0.45F : 0.75F);
		level.playSound(null, player.blockPosition(), SoundEvents.WOLF_SOUNDS.get(WolfSoundVariants.SoundSet.ANGRY).adultSounds().growlSound().value(),
			SoundSource.PLAYERS, beast ? 2.5F : 1.0F, 0.5F);
		level.sendParticles(ParticleTypes.SONIC_BOOM, player.getX(), player.getEyeY(), player.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
		if (beast) {
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(HOWL_RADIUS), e -> e != player && e.isAlive()
				&& !(e instanceof ArmorStand) && !(e instanceof Player p && (p.isCreative() || p.isSpectator()))
				&& !(e instanceof com.jaymoskingdoms.monsters.Werewolf w && WerewolfPlayers.current(w)))) {
				double dx = e.getX() - player.getX(), dz = e.getZ() - player.getZ();
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d > HOWL_RADIUS) {
					continue;
				}
				e.knockback(HOWL_PUSH * (1.0 - d / (HOWL_RADIUS + 1.0)), -dx, -dz, level.damageSources().playerAttack(player), 0.0F);
				e.needsSync = true;
				if (e instanceof Villager v) {
					Frights.scare(level, v, player.position());
				}
			}
		}
		reveal(level, player, REVEAL_RANGE);
		// (every other werewolf hears it, and where it came from)
		for (ServerPlayer other : level.players()) {
			Gift g = other.getAttached(GIFT);
			if (other == player || g == null || !g.wolf() || other.distanceToSqr(player) > HOWL_HEARD * HOWL_HEARD) {
				continue;
			}
			int d = (int) Math.round(other.distanceTo(player));
			other.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.night.howl_heard", d, Sightings.direction(other.blockPosition(), player.blockPosition()))
				.withStyle(ChatFormatting.GOLD));
		}
	}

	/** Every person near shows through walls, for this player only. */
	private static void reveal(ServerLevel level, ServerPlayer player, double range) {
		List<Integer> ids = new ArrayList<>();
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(range), e -> e != player && human(e))) {
			if (e.distanceToSqr(player) <= range * range && ids.size() < 512) {
				ids.add(e.getId());
			}
		}
		ServerPlayNetworking.send(player, new Reveal(ids, REVEAL_TICKS));
	}

	// ---- kills ---------------------------------------------------------------------------------------------------------

	private static void killed(ServerPlayer killer, LivingEntity victim) {
		Gift g = killer.getAttached(GIFT);
		if (g == null || victim instanceof ArmorStand) {
			return;
		}
		long now = killer.level().getGameTime();
		if (g.blood()) {
			if (victim.is(EntityTypeTags.UNDEAD)) {
				return;
			}
			// Lifeleech
			killer.heal(KILL_HEAL);
			Long last = LAST_KILL.put(killer.getUUID(), now);
			MobEffectInstance had = killer.getEffect(MobEffects.ABSORPTION);
			int amp = had != null && last != null && now - last < LEECH_TICKS ? Math.min(LEECH_MAX, had.getAmplifier() + 1) : 0;
			killer.removeEffect(MobEffects.ABSORPTION);
			killer.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, LEECH_TICKS, amp, false, false, true));
			killer.level().sendParticles(PowersCompat.blood(0x8A0303, 1.0F), victim.getX(), victim.getY(0.6), victim.getZ(), 16, 0.3, 0.4, 0.3, 0.0);
		} else if (g.wolf()) {
			// Frenzy
			killer.heal(KILL_HEAL);
			boolean beast = WerewolfPlayers.beast(killer);
			killer.addEffect(new MobEffectInstance(MobEffects.STRENGTH, FRENZY_TICKS, beast ? FRENZY_BEAST_STRENGTH : FRENZY_STRENGTH, false, false, true));
			killer.addEffect(new MobEffectInstance(MobEffects.SPEED, FRENZY_TICKS, beast ? 1 : 0, false, false, true));
		}
	}

	// ---- operators -----------------------------------------------------------------------------------------------------

	/** {@code /kingdom nightgift <what>}: make yourself one (or build a crypt or den where you stand), for trying it out. */
	private static int give(CommandSourceStack source, String what) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		ServerLevel level = player.level();
		long nightNo = Vampires.nightIndex(level.getOverworldClockTime());
		switch (what.toLowerCase(Locale.ROOT)) {
			case "vampire", "dhampir" -> {
				WerewolfPlayers.cure(player);
				player.removeAttached(Vampires.BITTEN);
				Vampires.Kind kind = what.equalsIgnoreCase("dhampir") ? Vampires.Kind.DHAMPIR : Vampires.Kind.PLAYER;
				player.setAttached(Vampires.VAMPIRE, new Vampires.Vampire(kind.name().toLowerCase(Locale.ROOT), nightNo, 0L));
			}
			case "werewolf", "wolfblood" -> {
				player.removeAttached(Vampires.VAMPIRE);
				player.removeAttached(Vampires.BITTEN);
				Lycans.Curse c = Lycans.roll(level, player);
				player.setAttached(Lycans.CURSE, what.equalsIgnoreCase("wolfblood") ? c.halved() : c.withMoons(WerewolfPlayers.MASTERY_MOONS));
			}
			case "crypt" -> {
				if (!Lairs.crypt(level, player)) {
					source.sendFailure(Component.literal("No room for a crypt here (it must be dug into natural ground)."));
					return 0;
				}
			}
			case "den" -> {
				if (!Lairs.den(level, player)) {
					source.sendFailure(Component.literal("No room for a den here (it must be dug into natural ground)."));
					return 0;
				}
			}
			case "none" -> {
				WerewolfPlayers.cure(player);
				player.removeAttached(Vampires.VAMPIRE);
				player.removeAttached(Vampires.BITTEN);
			}
			default -> {
				source.sendFailure(Component.literal("vampire, dhampir, werewolf, wolfblood, crypt, den or none"));
				return 0;
			}
		}
		second(level.getServer(), player, Vampires.night(level.getOverworldClockTime()));
		source.sendSuccess(() -> Component.literal("Night gift: " + what), true);
		return 1;
	}
}
