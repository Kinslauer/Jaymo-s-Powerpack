package com.jaymoskingdoms;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.361) Valor: a fighter's own strength, woken in a moment of defiance and grown only by feats, that lets a coated
 * fist or blade reach whatever magic hides behind. Jungxvv's idea and name, merged with Andy's (2026-10-29): "a way to
 * fight magic users", "unique to everyone that possesses one", grown by "the feats they accomplish". Andy ticked the whole
 * design list (Claude Doc "Valor design list"), every suggested answer, and Senses reveal a disguised vampire at Unbroken.
 *
 * <p><b>Waking</b> ({@link #canWake}, {@link #wake}): Strength level {@value #WAKE_LEVEL}, a Will perk, and no Mage or Sorcerer.
 * Then beating a foe stronger than you, rising from downed and winning that fight, Refuse to Fall saving you, or killing what
 * was after someone else wakes it: a heartbeat and a pulse of colour, no chat text. The feat that woke it is its
 * {@link Origin} (a quirk for life); its look ({@link Look}), soul colour and name are rolled with it.
 *
 * <p><b>Growth</b>: every {@link Feat} adds Valor experience, less each time against the same kind of foe; a little more
 * each day you live with it; half as much for a player with a power. Five ranks ({@link #RANKS}): Spark, Kindled,
 * Tempered (a second quirk from the feats done most, {@link Bent}), Steeled, Unbroken.
 *
 * <p><b>The arts</b>: Reinforcement (Spark: a dark sheen up the forearms, less harm, block bare-armed while sneaking),
 * Armament (Kindled: down the weapon too, harder blows that stagger and stun, and land on what magic makes untouchable),
 * Senses (always on: the hidden living as outlines, auto-dodges from Kindled, a shot's warning from Steeled, a disguised
 * vampire's cold heartbeat at Unbroken). The Valor key (backslash) puts the coatings up and down ({@link Use}); they drain the {@link #pool} and
 * running dry leaves you Spent. Brutes and Warlords are proficient in Reinforcement, those and Thieves in Armament,
 * Archers, Marksmen and Survivalists in Senses: a third less drain and the art's full strength (others top out at Steeled).
 *
 * <p><b>Against magic</b> ({@link #pierces}): a coated blow lands through Chrono's intangibility, Darktide's flurry,
 * Smoke and a Flame Avatar (Jaymo's Powers asks, {@code ValorSkill}), a spirit's parry, and shatters a ward's absorption;
 * a full-strength one breaks a caster's spell (Will's Spellbreaker, every time) and unmakes a risen thrall.
 */
public final class Valor {
	// ---- tuning ----------------------------------------------------------------------------------------------------
	/** Strength level needed before Valor can wake; for the child of a Valor parent (Lineage). */
	static final int WAKE_LEVEL = 5, HEIR_WAKE_LEVEL = 3;
	/** On a player who took over the child of a Valor holder. */
	static final String HEIR_TAG = "jaymoskingdoms_valor_heir";
	/** Valor experience at each rank (Spark, Kindled, Tempered, Steeled, Unbroken). */
	public static final float[] RANKS = {0.0F, 40.0F, 120.0F, 300.0F, 700.0F};
	public static final int SPARK = 0, KINDLED = 1, TEMPERED = 2, STEELED = 3, UNBROKEN = 4;
	/** The highest rank an art reaches for a class not proficient in it. */
	static final int UNTRAINED_CAP = STEELED;
	/** Each repeat of a feat against the same kind of foe pays this much less (1 / (1 + this x times)). */
	static final float REPEAT_FALLOFF = 0.5F;
	/** A player with a power grows Valor at this share. */
	static final float POWERED_GROWTH = 0.5F;
	/** Valor experience a day lived with it. */
	static final float DAILY = 1.0F;
	/** How long after rising from downed a kill still counts as winning that fight (ticks). */
	static final int RISE_WINDOW = 1200;

	/** The pool: its size at Spark and per rank; drain per second (Reinforcement, Armament); refill per second, the wait before it. */
	static final float POOL_BASE = 40.0F, POOL_PER_RANK = 30.0F, DRAIN_REINFORCE = 1.0F, DRAIN_ARMAMENT = 1.5F, REFILL = 2.0F;
	static final int REFILL_WAIT = 100;
	/** A proficient class drains this share; a well-fed one refills this much faster (food at or over FED), a sleeper this much. */
	static final float PROFICIENT_DRAIN = 0.67F, FED_REFILL = 1.5F, SLEEP_REFILL = 3.0F;
	static final int FED = 18;
	/** Spent: how long (ticks). */
	static final int SPENT_TICKS = 1200;

	/** Reinforcement: harm taken off by rank; more off spells and powers (on top, with Will's Grounded, to the cap). */
	static final float[] REINFORCE = {0.15F, 0.22F, 0.29F, 0.35F, 0.40F};
	static final float REINFORCE_MAGIC = 0.10F, MAGIC_CAP = 0.70F;
	/** Bare-armed blocks (sneaking, empty main hand, a blow from in front): their cost, how often, how frontal (cosine). */
	static final float BLOCK_COST = 4.0F;
	static final int BLOCK_EVERY = 10;
	static final double BLOCK_COS = 0.3;
	/** Armament: extra harm by rank (none at Spark: it opens at Kindled); stagger odds and how long; stun odds by rank and how long. */
	static final float[] ARMAMENT = {0.0F, 0.15F, 0.22F, 0.30F, 0.40F};
	static final float STAGGER_CHANCE = 0.25F;
	static final int STAGGER_TICKS = 10;
	static final float[] STUN = {0.0F, 0.08F, 0.10F, 0.12F, 0.15F};
	static final int STUN_TICKS = 20;
	/** Senses: auto-dodge odds by rank (from Kindled), proficient this many times; the wait (ticks) and cost; how hard the sidestep. */
	static final float[] DODGE = {0.0F, 0.10F, 0.15F, 0.20F, 0.25F};
	static final float DODGE_PROFICIENT = 1.5F, DODGE_COST = 6.0F;
	static final int DODGE_WAIT = 120, DODGE_WAIT_PROFICIENT = 80;
	static final double SIDESTEP = 0.55;

	/** Origins' quirks: Ember's fire taken, Bulwark's reach and share, Hollow's mana drained, Undying's drain under three hearts, Duelist's stagger. */
	static final float EMBER_FIRE = 0.4F, BULWARK_SHARE = 0.5F, HOLLOW_MANA = 10.0F, UNDYING_DRAIN = 0.5F, DUELIST_STAGGER = 2.0F;
	static final double BULWARK_REACH = 2.5;
	/** Rooted: knockback resistance while coated. */
	static final double ROOTED_RESIST = 1.0;
	/** Bents' second quirks: a gate-holder's Reinforcement drain, a duellist's extra Armament, a mage-hunter's harm on casters, a survivor's Spent. */
	static final float GATE_DRAIN = 0.7F, DUEL_ARMAMENT = 0.10F, MAGEBANE = 0.15F, SURVIVOR_SPENT = 0.5F;
	/** Will's Second Breath: drain under three hearts. */
	static final float SECOND_BREATH_DRAIN = 0.8F;
	/** Classes: a Brute's coated fist (extra harm), a Warlord's steadying reach. */
	static final float BRUTE_FIST = 4.0F;
	static final double WARLORD_REACH = 8.0;

	// ---- what a Valor is --------------------------------------------------------------------------------------------

	/** The moment that woke it: a quirk for life. */
	public enum Origin {
		/** Woke while burning: the coating shrugs off fire. */
		EMBER,
		/** Woke when Refuse to Fall held them up: no knockback while coated. */
		ROOTED,
		/** Woke killing what was after someone else: Reinforcement covers a groupmate beside you. */
		BULWARK,
		/** Woke killing a caster: coated blows drain a caster's mana. */
		HOLLOW,
		/** Woke rising from downed: the coating drains slower under three hearts. */
		UNDYING,
		/** Woke against a beast: wider Senses. */
		HUNTER,
		/** Woke against a stronger fighter: coated blows stagger twice as often. */
		DUELIST;

		public String key() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	/** How the coating looks on the forearms (a dark dust in this colour). */
	public enum Look {
		IRON(0x3A3F47), STONE(0x5A544C), SCALE(0x1F3A2E), ASH(0x6E6E6E), HAZE(0x8A6A4A), LACQUER(0x0D0D12);

		public final int color;

		Look(int color) {
			this.color = color;
		}

		public String key() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	/** What a Valor's feats lean to; at Tempered the most-done one is its second quirk. */
	public enum Bent {
		GATE, DUEL, HUNT, MAGEBANE, SURVIVOR;

		public String key() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	/** The deeds that grow Valor, what each is worth and what it leans to. */
	public enum Feat {
		STRONGER(6.0F, Bent.DUEL), BEAST(5.0F, Bent.HUNT), GREAT_FOE(20.0F, Bent.DUEL), CASTER(5.0F, Bent.MAGEBANE),
		SPELLBREAK(2.0F, Bent.MAGEBANE), PROTECT(4.0F, Bent.GATE), RISE(5.0F, Bent.SURVIVOR), REFUSE(5.0F, Bent.SURVIVOR),
		/** Lasting a minute against a Valor villager, or beating them ({@link ValorFolk}): once per teacher. */
		SPAR(8.0F, Bent.DUEL);

		public final float worth;
		public final Bent bent;

		Feat(float worth, Bent bent) {
			this.worth = worth;
			this.bent = bent;
		}

		public String key() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	/** The soul colours (King's Valor shows it; Jaymo's: green, red, gold, blue, violet, white, black-red). */
	public static final int[] SOUL = {0x3FD46A, 0xE0302A, 0xF2C230, 0x3A7BF0, 0x9A4AE0, 0xF0F0F0, 0x6A0A10};
	public static final String[] SOUL_KEYS = {"green", "red", "gold", "blue", "violet", "white", "blackred"};

	/**
	 * Someone's Valor: its experience, origin, look, second quirk ({@link Bent}, -1 before Tempered), soul colour, name (and
	 * whether it was renamed), when it woke, the last day it grew by itself, its feats (by feat and kind of foe) and the
	 * great foes felled since Tempered.
	 */
	public record Data(float exp, int origin, int look, int bent, int soul, String name, boolean renamed, long woke, long day,
		Map<String, Integer> feats, int greatFoes) {
		public static final Codec<Data> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.FLOAT.fieldOf("exp").forGetter(Data::exp),
			Codec.INT.fieldOf("origin").forGetter(Data::origin),
			Codec.INT.fieldOf("look").forGetter(Data::look),
			Codec.INT.optionalFieldOf("bent", -1).forGetter(Data::bent),
			Codec.INT.fieldOf("soul").forGetter(Data::soul),
			Codec.STRING.fieldOf("name").forGetter(Data::name),
			Codec.BOOL.optionalFieldOf("renamed", false).forGetter(Data::renamed),
			Codec.LONG.fieldOf("woke").forGetter(Data::woke),
			Codec.LONG.optionalFieldOf("day", 0L).forGetter(Data::day),
			Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("feats", Map.of()).forGetter(Data::feats),
			Codec.INT.optionalFieldOf("great_foes", 0).forGetter(Data::greatFoes)).apply(i, Data::new));

		public int rank() {
			return rankFor(exp);
		}

		public Origin originKind() {
			return Origin.values()[Mth.clamp(origin, 0, Origin.values().length - 1)];
		}

		public Look lookKind() {
			return Look.values()[Mth.clamp(look, 0, Look.values().length - 1)];
		}

		public @Nullable Bent bentKind() {
			return bent < 0 || bent >= Bent.values().length ? null : Bent.values()[bent];
		}

		public int soulColor() {
			return SOUL[Mth.clamp(soul, 0, SOUL.length - 1)];
		}

		/** How many times each feat has been done, all kinds of foe together. */
		public Map<Feat, Integer> featCounts() {
			Map<Feat, Integer> out = new EnumMap<>(Feat.class);
			for (Map.Entry<String, Integer> e : feats.entrySet()) {
				String key = e.getKey();
				int colon = key.indexOf(':');
				Feat feat = byKey(colon < 0 ? key : key.substring(0, colon));
				if (feat != null) {
					out.merge(feat, e.getValue(), Integer::sum);
				}
			}
			return out;
		}

		Data withExp(float exp) {
			return new Data(exp, origin, look, bent, soul, name, renamed, woke, day, feats, greatFoes);
		}

		Data withBent(int bent) {
			return new Data(exp, origin, look, bent, soul, name, renamed, woke, day, feats, greatFoes);
		}

		Data withName(String name, boolean renamed) {
			return new Data(exp, origin, look, bent, soul, name, renamed, woke, day, feats, greatFoes);
		}

		Data withDay(long day, float exp) {
			return new Data(exp, origin, look, bent, soul, name, renamed, woke, day, feats, greatFoes);
		}

		Data withFeat(String key, float exp) {
			Map<String, Integer> more = new HashMap<>(feats);
			more.merge(key, 1, Integer::sum);
			return new Data(exp, origin, look, bent, soul, name, renamed, woke, day, Map.copyOf(more), greatFoes);
		}

		Data withGreatFoes(int greatFoes) {
			return new Data(exp, origin, look, bent, soul, name, renamed, woke, day, feats, greatFoes);
		}
	}

	static @Nullable Feat byKey(String key) {
		for (Feat feat : Feat.values()) {
			if (feat.key().equals(key)) {
				return feat;
			}
		}
		return null;
	}

	public static int rankFor(float exp) {
		int rank = 0;
		for (int i = 0; i < RANKS.length; i++) {
			if (exp >= RANKS[i]) {
				rank = i;
			}
		}
		return rank;
	}

	/** On anyone with Valor (players and villagers): saved, kept through death, synced to all. */
	public static final AttachmentType<Data> VALOR = AttachmentRegistry.create(JaymosKingdoms.id("valor"),
		builder -> builder.persistent(Data.CODEC).copyOnDeath().syncWith(ByteBufCodecs.fromCodec(Data.CODEC), AttachmentSyncPredicate.all()));

	/**
	 * What's up right now, packed (synced to all, not saved): {@link #REINFORCED}, {@link #ARMED}, {@link #KINGS} (King's
	 * coating), {@link #SPENT}; the pool's share in bits 8-15.
	 */
	public static final AttachmentType<Integer> STATE = AttachmentRegistry.create(JaymosKingdoms.id("valor_state"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));
	public static final int REINFORCED = 1, ARMED = 2, KINGS = 4, SPENT = 8;

	/** The tag on a shot loosed while its archer or marksman was coated: it lands as a coated blow. */
	public static final String COATED_SHOT = "jaymoskingdoms_valor_shot";
	private static final Identifier ROOTED_ID = JaymosKingdoms.id("valor_rooted");

	/** The Valor key: the coatings up or down (0); held, King's Valor released (1). */
	public record Use(int what) implements CustomPacketPayload {
		public static final Type<Use> TYPE = new Type<>(JaymosKingdoms.id("valor_use"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Use> CODEC = CustomPacketPayload.codec(
			(p, buf) -> buf.writeVarInt(p.what), buf -> new Use(buf.readVarInt()));

		@Override
		public Type<Use> type() {
			return TYPE;
		}
	}

	/** A Valor holder's moment-to-moment state (not saved: the pool comes back full on joining). */
	static final class Live {
		float pool = -1.0F;
		boolean reinforced, armed;
		long spentUntil, lastCombat, dodgeReady, blockReady, roseAt = -1;
		boolean wasDowned;
	}

	private static final Map<UUID, Live> LIVE = new HashMap<>();

	private Valor() {
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Use.TYPE, Use.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Use.TYPE, (payload, context) -> use(context.player(), payload.what()));
		ServerTickEvents.END_SERVER_TICK.register(Valor::tick);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(Valor::allowDamage);
		ServerLivingEntityEvents.AFTER_DAMAGE.register(Valor::afterDamage);
		ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register((level, killer, killed, source) -> {
			if (killer instanceof ServerPlayer sp && !sp.isCreative() && killed instanceof LivingEntity dead) {
				killed(sp, dead);
			}
		});
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Projectile shot && shot.tickCount == 0 && shot.getOwner() instanceof ServerPlayer owner && coatsShots(owner)) {
				shot.addTag(COATED_SHOT);
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> LIVE.remove(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> LIVE.clear());
		CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> dispatcher.register(Commands.literal("valor")
			.then(Commands.literal("name").then(Commands.argument("name", StringArgumentType.greedyString())
				.executes(c -> rename(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "name")))))
			.then(Commands.literal("wake").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.argument("player", EntityArgument.player()).executes(c -> {
					ServerPlayer target = EntityArgument.getPlayer(c, "player");
					if (!has(target)) {
						wake(target, Origin.DUELIST);
					}
					return 1;
				})))
			.then(Commands.literal("grant").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.argument("player", EntityArgument.player()).then(Commands.argument("exp", FloatArgumentType.floatArg(0.0F))
					.executes(c -> {
						ServerPlayer target = EntityArgument.getPlayer(c, "player");
						Data data = target.getAttached(VALOR);
						if (data != null) {
							grow(target, data, data.exp() + FloatArgumentType.getFloat(c, "exp"));
						}
						return 1;
					}))))));
	}

	// ---- who has what ----------------------------------------------------------------------------------------------

	public static boolean has(Entity e) {
		return e.hasAttached(VALOR);
	}

	public static @Nullable Data of(Entity e) {
		return e.getAttached(VALOR);
	}

	public static int rank(Entity e) {
		Data data = e.getAttached(VALOR);
		return data == null ? -1 : data.rank();
	}

	public static int state(Entity e) {
		return e.getAttachedOrElse(STATE, 0);
	}

	/** Reinforcement is up on {@code e} (either side). */
	public static boolean reinforced(Entity e) {
		return (state(e) & REINFORCED) != 0;
	}

	/** Armament (or King's coating) is up on {@code e} (either side). */
	public static boolean armed(Entity e) {
		return (state(e) & (ARMED | KINGS)) != 0;
	}

	/** The pool's share, 0-1 (either side). */
	public static float poolShare(Entity e) {
		return ((state(e) >> 8) & 0xFF) / 255.0F;
	}

	public static boolean spent(Entity e) {
		return (state(e) & SPENT) != 0;
	}

	/**
	 * Whether {@code source} is a coated blow: from someone with Armament up, by their own hand, or a shot they loosed coated.
	 * Magic doesn't turn it aside (Jaymo's Powers asks, by reflection, before a power lets a blow pass through).
	 */
	public static boolean pierces(DamageSource source) {
		Entity direct = source.getDirectEntity();
		if (direct != null && direct.entityTags().contains(COATED_SHOT)) {
			return true;
		}
		Entity by = source.getEntity();
		return by != null && direct == by && armed(by);
	}

	/** The classes proficient in each art (Jung's). */
	static boolean proficientReinforce(Classes.@Nullable PlayerClass c) {
		return c == Classes.PlayerClass.BRUTE || c == Classes.PlayerClass.WARLORD;
	}

	static boolean proficientArmament(Classes.@Nullable PlayerClass c) {
		return c == Classes.PlayerClass.BRUTE || c == Classes.PlayerClass.WARLORD || c == Classes.PlayerClass.THIEF;
	}

	public static boolean proficientSenses(Classes.@Nullable PlayerClass c) {
		return c == Classes.PlayerClass.ARCHER || c == Classes.PlayerClass.MARKSMAN || c == Classes.PlayerClass.SURVIVALIST;
	}

	static Classes.@Nullable PlayerClass classOf(Entity e) {
		return e instanceof Player p ? Classes.classOf(p) : null;
	}

	/** An art's rank for {@code e}: their rank, capped at Steeled without the class for it. */
	static int artRank(Entity e, Data data, boolean proficient) {
		int rank = data.rank();
		return proficient ? rank : Math.min(rank, UNTRAINED_CAP);
	}

	/** The rank Senses work at for {@code e} (-1 without Valor); both sides (the client draws them). */
	public static int sensesRank(Entity e) {
		Data data = e.getAttached(VALOR);
		return data == null ? -1 : artRank(e, data, proficientSenses(classOf(e)));
	}

	/** How far Senses reach (blocks) for {@code e}: by rank, half again for a Hunter's origin or bent. */
	public static double sensesReach(Entity e) {
		Data data = e.getAttached(VALOR);
		if (data == null) {
			return 0.0;
		}
		double reach = 8.0 + 4.0 * artRank(e, data, proficientSenses(classOf(e)));
		if (data.originKind() == Origin.HUNTER) {
			reach *= 1.5;
		}
		if (data.bentKind() == Bent.HUNT) {
			reach += 6.0;
		}
		return reach;
	}

	static float poolMax(Data data) {
		return POOL_BASE + POOL_PER_RANK * data.rank();
	}

	static Live live(ServerPlayer sp) {
		return LIVE.computeIfAbsent(sp.getUUID(), id -> new Live());
	}

	// ---- waking ----------------------------------------------------------------------------------------------------

	/** The Will perks: any one of them, and Strength at {@value #WAKE_LEVEL}, readies Valor to wake. */
	static final List<Skills.Perk> WILL_PERKS = List.of(Skills.Perk.DREAD_PRESENCE, Skills.Perk.GROUNDED, Skills.Perk.STARE_DOWN,
		Skills.Perk.NO_GETTING_UP, Skills.Perk.SPELLBREAKER, Skills.Perk.BROKEN_MORALE, Skills.Perk.SNAP_THE_THREAD, Skills.Perk.SHRUG_IT_OFF,
		Skills.Perk.NAME_THAT_CARRIES, Skills.Perk.WITCH_HUNTER, Skills.Perk.WILL_OVER_FLESH, Skills.Perk.GRIM_HAND, Skills.Perk.NO_MERCY,
		Skills.Perk.LAST_LOOK, Skills.Perk.REFUSE_TO_FALL, Skills.Perk.SECOND_BREATH, Skills.Perk.UNBROKEN_ROAR, Skills.Perk.UNFLINCHING);

	/** Whether {@code sp}'s Valor could wake now: Strength {@value #WAKE_LEVEL}, a Will perk, and no Mage or Sorcerer (Andy). */
	public static boolean canWake(Player sp) {
		if (has(sp) || sp.isCreative() || sp.isSpectator()) {
			return false;
		}
		Classes.PlayerClass c = Classes.classOf(sp);
		if (c == Classes.PlayerClass.MAGE || c == Classes.PlayerClass.SORCERER) {
			return false;
		}
		Skills.Progress progress = Skills.of(sp);
		if (progress.level(Skills.Pillar.STRENGTH) < (sp.entityTags().contains(HEIR_TAG) ? HEIR_WAKE_LEVEL : WAKE_LEVEL)) {
			return false;
		}
		for (Skills.Perk perk : WILL_PERKS) {
			if (progress.rank(perk) > 0) {
				return true;
			}
		}
		return false;
	}

	/** {@code sp}'s Valor wakes from {@code origin}: its look, soul colour and name rolled; a heartbeat and a pulse, no words. */
	static void wake(ServerPlayer sp, Origin origin) {
		ServerLevel level = sp.level();
		RandomSource r = level.getRandom();
		Look look = favouredLook(origin, r);
		int soul = r.nextInt(SOUL.length);
		long now = level.getGameTime();
		Data data = new Data(0.0F, origin.ordinal(), look.ordinal(), -1, soul, name(origin, look, r), false, now, now / Will.DAY, Map.of(), 0);
		sp.setAttached(VALOR, data);
		Live live = live(sp);
		live.pool = poolMax(data);
		level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.6F, 0.7F);
		level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8F, 0.6F);
		coatBurst(level, sp, look.color, 40);
		PowersCompat.shake(sp, 0.6F);
	}

	/** Half the time the look an origin leans to, else any. */
	static Look favouredLook(Origin origin, RandomSource r) {
		if (r.nextBoolean()) {
			return Look.values()[r.nextInt(Look.values().length)];
		}
		return switch (origin) {
			case EMBER -> Look.HAZE;
			case ROOTED -> Look.STONE;
			case BULWARK -> Look.IRON;
			case HOLLOW -> Look.LACQUER;
			case UNDYING -> Look.ASH;
			case HUNTER -> Look.SCALE;
			case DUELIST -> Look.IRON;
		};
	}

	private static final Map<Origin, String[]> ORIGIN_WORDS = Map.of(
		Origin.EMBER, new String[] {"Unburnt", "Smouldering", "Ember"},
		Origin.ROOTED, new String[] {"Unmoved", "Rooted", "Standing"},
		Origin.BULWARK, new String[] {"Unbowed", "Shielding", "Warden's"},
		Origin.HOLLOW, new String[] {"Spellbane", "Hollow", "Hushing"},
		Origin.UNDYING, new String[] {"Undying", "Risen", "Stubborn"},
		Origin.HUNTER, new String[] {"Keen", "Stalking", "Wild"},
		Origin.DUELIST, new String[] {"Iron-willed", "Answering", "Proud"});
	private static final Map<Look, String[]> LOOK_WORDS = Map.of(
		Look.IRON, new String[] {"Hide", "Grip", "Gauntlet"},
		Look.STONE, new String[] {"Stone", "Crag", "Bulwark"},
		Look.SCALE, new String[] {"Scale", "Coil", "Fang"},
		Look.ASH, new String[] {"Ash", "Cinder", "Grey"},
		Look.HAZE, new String[] {"Haze", "Shimmer", "Heat"},
		Look.LACQUER, new String[] {"Shell", "Lacquer", "Night"});

	/** A name from its origin and look: "the Unbowed Hide", "the Spellbane Shell". */
	static String name(Origin origin, Look look, RandomSource r) {
		String[] a = ORIGIN_WORDS.get(origin), b = LOOK_WORDS.get(look);
		return "the " + a[r.nextInt(a.length)] + " " + b[r.nextInt(b.length)];
	}

	/** /valor name: once, a Valor of yours takes the name you give it. */
	private static int rename(ServerPlayer sp, String name) {
		Data data = sp.getAttached(VALOR);
		if (data == null) {
			sp.sendSystemMessage(Component.translatable("message.jaymoskingdoms.valor.none").withStyle(ChatFormatting.GRAY));
			return 0;
		}
		if (data.renamed()) {
			sp.sendSystemMessage(Component.translatable("message.jaymoskingdoms.valor.named_once", data.name()).withStyle(ChatFormatting.GRAY));
			return 0;
		}
		String clean = name.strip();
		if (clean.isEmpty() || clean.length() > 32) {
			sp.sendSystemMessage(Component.translatable("message.jaymoskingdoms.valor.name_length").withStyle(ChatFormatting.GRAY));
			return 0;
		}
		sp.setAttached(VALOR, data.withName(clean, true));
		sp.sendSystemMessage(Component.translatable("message.jaymoskingdoms.valor.named", clean).withStyle(ChatFormatting.GOLD));
		return 1;
	}

	/** (Lineage) {@code player} becomes their child: their Valor stays with the elder they leave behind. */
	static void leaveWith(ServerPlayer player, LivingEntity elder) {
		Data data = player.getAttached(VALOR);
		if (data == null) {
			return;
		}
		Live live = live(player);
		coatsDown(player, live);
		player.removeAttached(VALOR);
		player.removeAttached(STATE);
		LIVE.remove(player.getUUID());
		elder.setAttached(VALOR, data);
		elder.addTag(ValorFolk.ROLLED);
		// (a Valor parent's child wakes theirs a little more easily)
		player.addTag(HEIR_TAG);
		ValorFolk.track(elder);
	}

	// ---- feats -----------------------------------------------------------------------------------------------------

	/** A foe stronger than {@code player}: a boss, a kind from a tier above theirs, more health (or power level), or a player they're weaker than. */
	static boolean stronger(Player player, LivingEntity e) {
		if (BossBars.boss(e)) {
			return true;
		}
		if (e instanceof Player other) {
			return Will.weaker(other, player);
		}
		if (Will.kindTier(e) > WorldTiers.of(player).ordinal()) {
			return true;
		}
		int ours = PowersCompat.powerLevel(player), them = PowersCompat.powerLevel(e);
		if (ours > 0 && them > 0) {
			return them > ours;
		}
		return e.getMaxHealth() > player.getMaxHealth();
	}

	/** A beast: an animal, or a monster that isn't a person (no illager, raider or folk). */
	static boolean beast(LivingEntity e) {
		return e instanceof Animal || e instanceof Enemy && !(e instanceof Folk) && !(e instanceof Worker) && !Will.caster(e)
			&& e.getType().getCategory() == net.minecraft.world.entity.MobCategory.MONSTER && !(e instanceof net.minecraft.world.entity.monster.illager.AbstractIllager);
	}

	/** {@code sp} killed {@code dead}: a feat, or Valor's waking. */
	private static void killed(ServerPlayer sp, LivingEntity dead) {
		Live live = live(sp);
		long now = sp.level().getGameTime();
		boolean rose = live.roseAt >= 0 && now - live.roseAt <= RISE_WINDOW && (dead instanceof Enemy || dead instanceof Mob m && m.getTarget() == sp || dead instanceof Player);
		boolean protect = dead instanceof Mob mob && mob.getTarget() != null && mob.getTarget() != sp && mob.getTarget().isAlive()
			&& !(mob.getTarget() instanceof Enemy) && (mob.getTarget() instanceof Player || !(mob.getTarget() instanceof Mob target) || !Will.foe(sp, target));
		// (waking takes more: the one you stood for was in real danger, under half their health)
		boolean saved = protect && ((Mob) dead).getTarget().getHealth() < ((Mob) dead).getTarget().getMaxHealth() * 0.5F;
		boolean caster = Will.caster(dead) && !(dead instanceof Player) || Will.castAt(sp, dead);
		boolean great = BossBars.boss(dead);
		boolean stronger = stronger(sp, dead);
		if (!has(sp)) {
			if (!canWake(sp)) {
				return;
			}
			Origin origin = null;
			if (rose) {
				origin = Origin.UNDYING;
			} else if (saved) {
				origin = Origin.BULWARK;
			} else if (stronger) {
				origin = caster ? Origin.HOLLOW : beast(dead) ? Origin.HUNTER : Origin.DUELIST;
			}
			if (origin != null) {
				wake(sp, sp.isOnFire() ? Origin.EMBER : origin);
			}
			return;
		}
		if (great) {
			feat(sp, Feat.GREAT_FOE, dead);
			greatFoe(sp, dead);
		} else if (stronger) {
			feat(sp, beast(dead) ? Feat.BEAST : Feat.STRONGER, dead);
		}
		if (caster) {
			feat(sp, Feat.CASTER, dead);
		}
		if (protect) {
			feat(sp, Feat.PROTECT, dead);
		}
		if (rose) {
			feat(sp, Feat.RISE, dead);
			live.roseAt = -1;
		}
	}

	/** A great foe felled: from Tempered on it wakes and grows King's Valor ({@link ValorKings#felled}). */
	static void greatFoe(LivingEntity holder, LivingEntity dead) {
		ValorKings.felled(holder, dead);
	}

	/** Refuse to Fall held {@code sp} up (Will): a feat, or Valor's waking (Rooted). */
	static void refused(ServerPlayer sp) {
		if (!has(sp)) {
			if (canWake(sp)) {
				wake(sp, sp.isOnFire() ? Origin.EMBER : Origin.ROOTED);
			}
			return;
		}
		feat(sp, Feat.REFUSE, null);
	}

	/** A Spellbreaker Blow landed (Will). */
	static void spellbroke(ServerPlayer sp, LivingEntity caster) {
		if (has(sp)) {
			feat(sp, Feat.SPELLBREAK, caster);
		}
	}

	/** {@code feat} done against {@code foe}: worth less each time against its kind, half for a power's holder. */
	static void feat(LivingEntity holder, Feat feat, @Nullable LivingEntity foe) {
		featKeyed(holder, feat, feat.key() + (foe == null ? "" : ":" + net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(foe.getType())));
	}

	/** {@code feat} under its own key (a spar's teacher, a foe's kind): worth less each time it's done again. */
	static void featKeyed(LivingEntity holder, Feat feat, String key) {
		Data data = holder.getAttached(VALOR);
		if (data == null || holder instanceof Player p && p.isCreative()) {
			return;
		}
		int done = data.feats().getOrDefault(key, 0);
		float worth = feat.worth / (1.0F + REPEAT_FALLOFF * done);
		if (holder instanceof Player p && PowersCompat.power(p) != null) {
			worth *= POWERED_GROWTH;
		}
		Data next = data.withFeat(key, data.exp());
		grow(holder, next, data.exp() + worth);
	}

	/** Sets {@code holder}'s Valor experience; a new rank is felt (and at Tempered the second quirk grows). */
	static void grow(LivingEntity holder, Data data, float exp) {
		int was = data.rank();
		Data next = data.withExp(exp);
		int now = next.rank();
		if (now >= TEMPERED && next.bent() < 0) {
			next = next.withBent(mostDone(next).ordinal());
		}
		holder.setAttached(VALOR, next);
		if (now > was && holder.level() instanceof ServerLevel level) {
			LivingEntity sp = holder;
			level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.4F, 0.8F);
			level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 0.5F);
			coatBurst(level, sp, next.lookKind().color, 30);
			if (holder instanceof ServerPlayer player) {
				player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.valor.rank", Component.translatable("valor.jaymoskingdoms.rank." + now))
					.withStyle(ChatFormatting.GOLD));
			}
		}
	}

	/** The bent its feats lean to most (a duellist's by default). */
	static Bent mostDone(Data data) {
		Map<Bent, Integer> by = new EnumMap<>(Bent.class);
		for (Map.Entry<Feat, Integer> e : data.featCounts().entrySet()) {
			by.merge(e.getKey().bent, e.getValue(), Integer::sum);
		}
		Bent best = Bent.DUEL;
		int most = 0;
		for (Map.Entry<Bent, Integer> e : by.entrySet()) {
			if (e.getValue() > most) {
				most = e.getValue();
				best = e.getKey();
			}
		}
		return best;
	}

	// ---- the coatings ----------------------------------------------------------------------------------------------

	/** From the Valor key: the coatings up (every art opened) or down. */
	static void use(ServerPlayer sp, int what) {
		Data data = sp.getAttached(VALOR);
		if (data == null || !sp.isAlive() || sp.isSpectator() || Downed.isDowned(sp)) {
			return;
		}
		if (what == 1) {
			ValorKings.release(sp, data);
			return;
		}
		Live live = live(sp);
		long now = sp.level().getGameTime();
		if (live.reinforced || live.armed) {
			coatsDown(sp, live);
			return;
		}
		if (now < live.spentUntil) {
			sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.valor.spent").withStyle(ChatFormatting.GRAY));
			return;
		}
		live.reinforced = true;
		live.armed = data.rank() >= KINDLED;
		sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.ARMOR_EQUIP_NETHERITE.value(), SoundSource.PLAYERS, 0.8F, 0.6F);
		coatBurst(sp.level(), sp, data.lookKind().color, 12);
		sync(sp, data, live);
	}

	static void coatsDown(ServerPlayer sp, Live live) {
		live.reinforced = live.armed = false;
		AttributeInstance kb = sp.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
		if (kb != null && kb.getModifier(ROOTED_ID) != null) {
			kb.removeModifier(ROOTED_ID);
		}
		Data data = sp.getAttached(VALOR);
		if (data != null) {
			sync(sp, data, live);
		}
	}

	/** Whether {@code owner}'s shots go out coated: Armament up, and an Archer at Steeled or a Marksman. */
	static boolean coatsShots(ServerPlayer owner) {
		if (!armed(owner)) {
			return false;
		}
		Classes.PlayerClass c = Classes.classOf(owner);
		return c == Classes.PlayerClass.MARKSMAN || c == Classes.PlayerClass.ARCHER && rank(owner) >= STEELED;
	}

	/** Packs what's up into {@link #STATE}, setting it only when it changed. */
	static void sync(ServerPlayer sp, Data data, Live live) {
		long now = sp.level().getGameTime();
		int share = Mth.clamp(Math.round(live.pool / poolMax(data) * 255.0F), 0, 255);
		int packed = (live.reinforced ? REINFORCED : 0) | (live.armed ? ARMED : 0) | (live.armed && ValorKings.coatOpen(data) ? KINGS : 0)
			| (now < live.spentUntil ? SPENT : 0) | share << 8;
		if (sp.getAttachedOrElse(STATE, 0) != packed) {
			sp.setAttached(STATE, packed);
		}
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
			Data data = sp.getAttached(VALOR);
			if (data == null) {
				continue;
			}
			ServerLevel level = sp.level();
			long now = level.getGameTime();
			Live live = live(sp);
			float max = poolMax(data);
			if (live.pool < 0.0F) {
				live.pool = max;
			}
			// rising from downed: a kill in the next minute is winning that fight
			boolean downed = Downed.isDowned(sp);
			if (live.wasDowned && !downed && sp.isAlive()) {
				live.roseAt = now;
			}
			live.wasDowned = downed;
			if ((live.reinforced || live.armed) && (!sp.isAlive() || downed || sp.isSpectator())) {
				coatsDown(sp, live);
			}
			// a day lived with it
			long day = now / Will.DAY;
			if (day > data.day()) {
				data = data.withDay(day, data.exp());
				grow(sp, data, data.exp() + DAILY * (PowersCompat.power(sp) != null ? POWERED_GROWTH : 1.0F));
				data = sp.getAttached(VALOR);
			}
			if (live.reinforced || live.armed) {
				float drain = 0.0F;
				Classes.PlayerClass c = Classes.classOf(sp);
				if (live.reinforced) {
					drain += DRAIN_REINFORCE * (proficientReinforce(c) ? PROFICIENT_DRAIN : 1.0F) * (data.bentKind() == Bent.GATE ? GATE_DRAIN : 1.0F);
				}
				if (live.armed) {
					drain += DRAIN_ARMAMENT * (proficientArmament(c) ? PROFICIENT_DRAIN : 1.0F);
				}
				if (sp.getHealth() <= Will.SECOND_BREATH_HEALTH) {
					if (data.originKind() == Origin.UNDYING) {
						drain *= UNDYING_DRAIN;
					}
					if (Will.surging(sp)) {
						drain *= SECOND_BREATH_DRAIN;
					}
				}
				live.pool -= drain / 20.0F;
				if (live.pool <= 0.0F) {
					live.pool = 0.0F;
					coatsDown(sp, live);
					float spent = SPENT_TICKS * (data.bentKind() == Bent.SURVIVOR ? SURVIVOR_SPENT : 1.0F);
					live.spentUntil = now + (long) spent;
					sp.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, (int) spent, 0), sp);
					sp.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, (int) spent, 0), sp);
					level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.PLAYER_ATTACK_NODAMAGE, SoundSource.PLAYERS, 1.0F, 0.5F);
					sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.valor.spent").withStyle(ChatFormatting.GRAY));
				}
				if (now % 4 == 0) {
					coatParticles(level, sp, live.armed, data.lookKind().color);
				}
				if (data.originKind() == Origin.ROOTED) {
					AttributeInstance kb = sp.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
					if (kb != null && kb.getModifier(ROOTED_ID) == null) {
						kb.addOrUpdateTransientModifier(new AttributeModifier(ROOTED_ID, ROOTED_RESIST, AttributeModifier.Operation.ADD_VALUE));
					}
				}
			} else if (now - live.lastCombat >= REFILL_WAIT && live.pool < max) {
				float refill = REFILL * (sp.getFoodData().getFoodLevel() >= FED ? FED_REFILL : 1.0F) * (sp.isSleeping() ? SLEEP_REFILL : 1.0F);
				live.pool = Math.min(max, live.pool + refill / 20.0F);
			}
			if (data != null) {
				sync(sp, data, live);
			}
		}
	}

	/** The coating's dust on the forearms (and down the weapon when armed). */
	static void coatParticles(ServerLevel level, LivingEntity e, boolean armed, int color) {
		DustParticleOptions dust = new DustParticleOptions(color, 0.8F);
		float yaw = e.yBodyRot * Mth.DEG_TO_RAD;
		double fx = -Mth.sin(yaw), fz = Mth.cos(yaw), sx = Mth.cos(yaw), sz = Mth.sin(yaw);
		double y = e.getY() + e.getBbHeight() * 0.5;
		for (int side = -1; side <= 1; side += 2) {
			double x = e.getX() + sx * 0.38 * side + fx * 0.15, z = e.getZ() + sz * 0.38 * side + fz * 0.15;
			level.sendParticles(dust, x, y, z, 2, 0.06, 0.12, 0.06, 0.0);
		}
		if (armed) {
			double x = e.getX() - sx * 0.38 + fx * 0.6, z = e.getZ() - sz * 0.38 + fz * 0.6;
			level.sendParticles(dust, x, y + 0.2, z, 2, 0.08, 0.25, 0.08, 0.0);
		}
	}

	/** A burst of the coating round {@code e} (waking, a rank, the coatings going up). */
	static void coatBurst(ServerLevel level, LivingEntity e, int color, int count) {
		level.sendParticles(new DustParticleOptions(color, 1.4F), e.getX(), e.getY(0.5), e.getZ(), count, 0.5, 0.7, 0.5, 0.05);
	}

	// ---- blows -----------------------------------------------------------------------------------------------------

	/**
	 * What a blow deals (from {@code LivingEntity.hurtServer}, after Will's): Reinforcement takes harm off (more off magic),
	 * an Ember's coating fire; a Bulwark covers a groupmate beside them; a coated rider's horse too. Armament adds to a coated
	 * blow (a Brute's coated fist hits like a club), and a coated blow shatters a ward's absorption first.
	 */
	public static float dealt(LivingEntity target, DamageSource source, float amount) {
		if (amount <= 0.0F || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return amount;
		}
		// the struck: Reinforcement
		LivingEntity covered = target;
		float share = 1.0F;
		if (!reinforced(target)) {
			covered = coverer(target);
			share = covered == null ? 0.0F : BULWARK_SHARE;
		}
		if (covered != null && share > 0.0F) {
			Data data = covered.getAttached(VALOR);
			if (data != null) {
				float off = REINFORCE[artRank(covered, data, proficientReinforce(classOf(covered)))] * share;
				if (covered == target && Will.magical(source, target)) {
					off = Math.min(MAGIC_CAP, off + REINFORCE_MAGIC);
				}
				if (covered == target && data.originKind() == Origin.EMBER && source.is(DamageTypeTags.IS_FIRE)) {
					off = Math.max(off, 1.0F - EMBER_FIRE);
				}
				amount *= 1.0F - off;
				if (covered instanceof ServerPlayer sp) {
					live(sp).lastCombat = sp.level().getGameTime();
				}
			}
		}
		// the striker: Armament
		if (!pierces(source)) {
			return amount;
		}
		Entity by = source.getEntity();
		Data data = by == null ? null : by.getAttached(VALOR);
		if (data == null) {
			return amount;
		}
		if (target.getAbsorptionAmount() > 0.0F) {
			// a ward shatters under it
			target.removeEffect(MobEffects.ABSORPTION);
			target.setAbsorptionAmount(0.0F);
			if (target.level() instanceof ServerLevel level) {
				level.sendParticles(ParticleTypes.ENCHANTED_HIT, target.getX(), target.getY(0.6), target.getZ(), 16, 0.3, 0.4, 0.3, 0.2);
				level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 1.0F, 1.2F);
			}
		}
		int rank = artRank(by, data, proficientArmament(classOf(by)));
		float more = ARMAMENT[rank] + (data.bentKind() == Bent.DUEL ? DUEL_ARMAMENT : 0.0F)
			+ (data.bentKind() == Bent.MAGEBANE && Will.caster(target) ? MAGEBANE : 0.0F);
		amount *= 1.0F + more;
		if (by instanceof Player p && Classes.classOf(p) == Classes.PlayerClass.BRUTE && source.getDirectEntity() == p && p.getMainHandItem().isEmpty()) {
			amount += BRUTE_FIST;
		}
		if (by instanceof ServerPlayer sp) {
			live(sp).lastCombat = sp.level().getGameTime();
		}
		// King's coating: the weaker struck die outright (No Mercy's rule: sneaking, fists and clubs only down)
		return ValorKings.coatedBlow(target, source, by, amount);
	}

	/** Who covers {@code target}: a Bulwark-born groupmate beside them with Reinforcement up, or a rider on the horse. */
	private static @Nullable LivingEntity coverer(LivingEntity target) {
		for (Entity rider : target.getPassengers()) {
			if (rider instanceof Player p && Classes.classOf(p) == Classes.PlayerClass.RIDER && reinforced(p)) {
				return p;
			}
		}
		if (!(target instanceof Player hurt) || !(target.level() instanceof ServerLevel level)) {
			return null;
		}
		for (ServerPlayer other : level.players()) {
			if (other != hurt && reinforced(other) && other.distanceToSqr(hurt) <= BULWARK_REACH * BULWARK_REACH && Groups.together(other, hurt)) {
				Data data = other.getAttached(VALOR);
				if (data != null && data.originKind() == Origin.BULWARK) {
					return other;
				}
			}
		}
		return null;
	}

	/** Bare-armed blocks (Reinforcement) and Senses' auto-dodges. */
	private static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (!(entity instanceof ServerPlayer sp) || amount <= 0.0F || !(source.getEntity() instanceof LivingEntity attacker) || attacker == sp
			|| source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || source.is(DamageTypeTags.IS_EXPLOSION) || source.is(DamageTypeTags.IS_FIRE)) {
			return true;
		}
		Data data = sp.getAttached(VALOR);
		if (data == null || Downed.isDowned(sp)) {
			return true;
		}
		ServerLevel level = sp.level();
		long now = level.getGameTime();
		Live live = live(sp);
		live.lastCombat = now;
		boolean melee = source.getDirectEntity() == attacker;
		// Reinforcement: sneaking, empty-handed, a blow from in front is taken on the forearms
		if (melee && live.reinforced && sp.isShiftKeyDown() && sp.getMainHandItem().isEmpty() && now >= live.blockReady && live.pool >= BLOCK_COST) {
			Vec3 to = attacker.position().subtract(sp.position()).multiply(1.0, 0.0, 1.0);
			Vec3 look = sp.getLookAngle().multiply(1.0, 0.0, 1.0);
			if (to.lengthSqr() > 1.0E-4 && look.lengthSqr() > 1.0E-4 && to.normalize().dot(look.normalize()) >= BLOCK_COS) {
				live.blockReady = now + BLOCK_EVERY;
				live.pool -= BLOCK_COST;
				level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.SHIELD_BLOCK.value(), SoundSource.PLAYERS, 1.0F, 0.7F);
				coatBurst(level, sp, data.lookKind().color, 8);
				attacker.knockback(0.4, sp.getX() - attacker.getX(), sp.getZ() - attacker.getZ(), source, 0.0F);
				return false;
			}
		}
		// Senses: a blow seen coming is sidestepped
		int senses = sensesRank(sp);
		float odds = DODGE[Math.max(0, senses)] * (proficientSenses(Classes.classOf(sp)) ? DODGE_PROFICIENT : 1.0F);
		if (odds <= 0.0F || now < live.dodgeReady || now < live.spentUntil || live.pool < DODGE_COST || level.getRandom().nextFloat() >= odds) {
			return true;
		}
		live.dodgeReady = now + (proficientSenses(Classes.classOf(sp)) ? DODGE_WAIT_PROFICIENT : DODGE_WAIT);
		live.pool -= DODGE_COST;
		Vec3 from = (melee ? attacker.position() : source.getDirectEntity() != null ? source.getDirectEntity().position() : attacker.position())
			.subtract(sp.position()).multiply(1.0, 0.0, 1.0);
		Vec3 side = from.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(-from.z, 0.0, from.x).normalize();
		if (level.getRandom().nextBoolean()) {
			side = side.scale(-1.0);
		}
		sp.setDeltaMovement(sp.getDeltaMovement().add(side.scale(SIDESTEP)).add(0.0, 0.1, 0.0));
		sp.needsSync = true;
		level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.PLAYER_ATTACK_NODAMAGE, SoundSource.PLAYERS, 0.9F, 1.4F);
		level.sendParticles(ParticleTypes.CLOUD, sp.getX(), sp.getY(0.3), sp.getZ(), 6, 0.2, 0.1, 0.2, 0.02);
		return false;
	}

	/** A coated blow landed: a stagger, sometimes a stun; on a caster, its spell breaks; a risen thrall falls apart. */
	private static void afterDamage(LivingEntity target, DamageSource source, float baseDamage, float damageTaken, boolean blocked) {
		if (damageTaken <= 0.0F || !pierces(source) || !(target.level() instanceof ServerLevel level)) {
			return;
		}
		Entity by = source.getEntity();
		Data data = by == null ? null : by.getAttached(VALOR);
		if (data == null) {
			return;
		}
		RandomSource r = level.getRandom();
		int rank = artRank(by, data, proficientArmament(classOf(by)));
		float stagger = STAGGER_CHANCE * (data.originKind() == Origin.DUELIST ? DUELIST_STAGGER : 1.0F);
		if (target instanceof Mob mob && target.isAlive()) {
			if (r.nextFloat() < STUN[rank]) {
				Will.daze(mob, STUN_TICKS);
				level.sendParticles(ParticleTypes.CRIT, mob.getX(), mob.getY(1.0), mob.getZ(), 10, 0.3, 0.2, 0.3, 0.1);
			} else if (r.nextFloat() < stagger) {
				mob.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, STAGGER_TICKS, 4), by instanceof LivingEntity l ? l : null);
				mob.getNavigation().stop();
			}
		} else if (target instanceof ServerPlayer other && r.nextFloat() < stagger) {
			other.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, STAGGER_TICKS, 3), by instanceof LivingEntity l ? l : null);
		}
		if (!(by instanceof ServerPlayer sp) || source.getDirectEntity() != sp) {
			return;
		}
		boolean full = CombatArts.fullStrength(sp, level.getGameTime()) || Classes.classOf(sp) == Classes.PlayerClass.THIEF;
		if (!full) {
			return;
		}
		if (Will.caster(target)) {
			Will.spellbreak(level, sp, target);
			if (data.originKind() == Origin.HOLLOW && target instanceof ServerPlayer caster) {
				Mana.drain(caster, HOLLOW_MANA);
			}
			for (Mob thrall : Thralls.of(target.getUUID())) {
				thrall.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, Will.REEL_TICKS, 3), sp);
				thrall.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, Will.REEL_TICKS, 1), sp);
			}
		}
		if (target instanceof Mob thrall && target.isAlive() && Thralls.isThrall(thrall)) {
			Thralls.crumble(level, thrall);
		}
	}

	/**
	 * A Warlord's steadiness: whether {@code e} (a player) stands within a coated Warlord's reach and is theirs (themself or a
	 * groupmate). Will's Dread Presence and roar pass them by.
	 */
	public static boolean steadied(Entity e) {
		if (!(e instanceof Player p) || !(e.level() instanceof ServerLevel level)) {
			return false;
		}
		for (ServerPlayer warlord : level.players()) {
			if ((reinforced(warlord) || armed(warlord)) && Classes.classOf(warlord) == Classes.PlayerClass.WARLORD
				&& warlord.distanceToSqr(p) <= WARLORD_REACH * WARLORD_REACH && (warlord == p || Groups.together(warlord, p))) {
				return true;
			}
		}
		return false;
	}
}
