package com.jaymoskingdoms;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.57) Heritage, the idea of Origins remade as a step of character creation (MCA's destiny, then this, then the
 * class): where your blood comes from. Each heritage gives a point or two free in the attributes ({@link Stats}), a gift
 * and a flaw, all passive: powers stay the Powers mod's to find. Picked once, for life (an op can change it:
 * {@code /kingdom heritage set|clear|get}).
 *
 * <ul>
 * <li><b>Heartlander:</b> plain folk: two attribute points of your own choosing, no flaw.</li>
 * <li><b>Stoneborn:</b> mines faster; a little slower on foot.</li>
 * <li><b>Tideborn:</b> long breath, swims and mines well under water; (0.7.80) wet from touching water until 2 minutes
 * out of it, and hit harder while wet; hungrier once dry.</li>
 * <li><b>Sylvan:</b> lands soft, sneaks unseen; hits lighter.</li>
 * <li><b>Verdant:</b> green skin and antennae ({@link Verdants}), hunger drains slow in the sun; burns longer.</li>
 * <li><b>Emberborn:</b> (0.7.75) fire, lava and burning never hurt them, and they thrive in the heat; the cold (cold lands,
 * snow and freezing) saps their strength and speed and starves them. Embers glow under the skin of
 * their hands and in their eyes, and small flames rise off their hands (0.7.77, client EmberLook).</li>
 * <li><b>Frostborn:</b> never freezes, thrives in the cold; starves in the heat.</li>
 * <li><b>Nightborn:</b> stronger and quicker in the dark; weaker under the open sun.</li>
 * <li><b>Warrior-born:</b> starts far weaker than anyone (lighter blows, fewer hearts), and grows out of it as their
 * character level climbs; hits harder the worse they're hurt; always hungry.</li>
 * <li><b>Earthling:</b> (0.7.75) the Z Fighters' training-born race: the skill tree learns 25% faster; (0.7.92) near death
 * their ki flares with a random buff for a while ({@link EarthlingSurge}).</li>
 * <li><b>Stormborn:</b> (0.7.106) quicker under the open sky, and lightning never hurts them; in a thunderstorm their blows
 * throw a spark to a monster nearby. Deep underground they're sluggish. (0.7.154) Any rain sparks, the spark leaps on to a
 * second monster, and lightning charges them (Speed II, Strength I); Jaymo's Powers gives them Thunderclap on the left
 * hold, to call it down.</li>
 * <li><b>Dawnborn:</b> (0.7.106) Nightborn's mirror: stronger and slowly healing under the open sun, hitting the undead
 * harder; in the dark they hit lighter and hunger faster. (0.7.154) By day their blows set the undead alight; Jaymo's
 * Powers gives them Sunflare on the left hold.</li>
 * <li><b>Endborn:</b> (0.7.106, earned) now and then blink aside from an arrow or anything thrown; endermen don't mind
 * their stare. Water and rain hurt them a little, like an enderman.</li>
 * <li><b>Dragonblood:</b> (0.7.106, earned) fire and dragon's breath do them half harm; the hoard: the more gold they
 * carry the harder they hit, and carrying none they hit lighter.</li>
 * <li><b>Deepborn:</b> (0.7.106, earned) sneaking, they sense the creatures round them through walls (client); they never
 * set off sculk (the Warden can't hear them either). Blasts stun them, and the open sun dulls them.</li>
 * <li><b>Divine-born:</b> (0.7.262) the celestial blood: a halo and gold-touched skin, mends faster, smites the wicked, takes to
 * healing magic; (0.7.318) no need for food or sleep ({@link DivineBorn}).</li>
 * <li><b>Bio-built:</b> (0.7.262) a made being that absorbs downed foes to evolve through three forms, drains life from what
 * it hits and, at the last form, buds small copies ({@link BioBuilt}).</li>
 * <li><b>Rubber-folk:</b> (0.7.262) pink and stretchy: a longer reach, lost limbs regrown, weak foes swallowed for a heal, a
 * sweet tooth; rubber burns and pops ({@link RubberFolk}).</li>
 * </ul>
 * (0.7.106) Endborn, Dragonblood and Deepborn are earned, not picked at the start ({@link Kind#earned}): seeing the Ender
 * Dragon fall wakes the first two, slaying or taming any other dragon the Dragonblood, and killing a Warden or seeing
 * one dig away the Deepborn ({@link #unlockChecks}, {@link #died}). (0.7.140) The Stormborn is earned too: it wakes in a
 * Wind user whose power the storm corrupts (Jaymo's Powers' Stormbound). (0.7.184) Earning one ({@link #UNLOCKED}) no
 * longer changes your own blood (the Blood Rite is gone): your children are born to your heritage or one you've earned
 * ({@link VillagerHeritage#passed}), and you take it up by living on as one of them ({@link Lineage}).
 * (0.7.75) Earthling, Warrior-born, Frostborn and Verdant are the Z Fighter lineages too: Jaymo's Powers gives anyone of
 * that blood its race (a Warrior-born's tail, a Verdant's mending and stretch punch, a Frostborn's ice prison
 * ({@link FrostPrison}) on left mouse held), Z Fighter or not. The heritage key ({@code ;}) wraps a Warrior-born's tail
 * away or lets it out.
 * (0.7.79) Every heritage but Heartlander and Warrior-born has a look of its own too, like the Emberborn's: marks on
 * the hands and forearms that glow in the dark, glowing eyes for most, a Tideborn's gills and fins (client HeritageLooks).
 * Each player carries an entity tag {@code jaymoskingdoms_heritage_<key>} for their heritage, so Jaymo's Powers can read
 * it without Kingdoms classes (a Z Fighter's lineage comes from it).
 * Villagers are born to a heritage too, MCA's and the settlements' own folk, with the same attachment and tag
 * ({@link VillagerHeritage}: the weights, their looks, a Warrior-born villager's tail wrapped or cut, their talk).
 */
public final class Heritage {
	/** Stoneborn: block break speed (a share), and walking speed lost (a share). */
	static final double STONE_MINING = 0.15, STONE_SLOW = 0.06;
	/** Tideborn: breath, swimming and mining under water; hunger out of the wet, as a factor. */
	static final double TIDE_OXYGEN = 3.0, TIDE_SWIM = 0.5, TIDE_SUBMERGED_MINING = 0.6;
	static final float TIDE_DRY_HUNGER = 1.2F;
	/**
	 * (0.7.80) Tideborn are wet from touching water (or rain) until this long out of it (2 min): then they're dry, and
	 * the hunger flaw sets in. Wet, their blows land this much harder (a share).
	 */
	static final int TIDE_WET_TICKS = 2400;
	static final double TIDE_WET_DAMAGE = 0.15;
	/** Sylvan: blocks you can fall without harm, the share off how far sneaking is seen, and the share off your blows. */
	static final double SYLVAN_SAFE_FALL = 1.5, SYLVAN_SNEAK = 0.1, SYLVAN_WEAK = 0.1;
	/** Verdant: hunger in the sun, as a factor, and how much longer fire burns (a share). */
	static final float VERDANT_SUN_HUNGER = 0.65F;
	/** (0.7.123) Verdant hunger in the spring sun, as a factor (Kitchen's seasons). */
	static final float VERDANT_SPRING_HUNGER = 0.5F;
	static final double VERDANT_BURN = 0.5;
	/**
	 * Emberborn: fire never hurts them and never stays lit on them. In the cold their blows and their walking speed drop
	 * by these shares; freezing (powder snow) takes the bigger ones. Both Emberborn and Frostborn: hunger where they
	 * thrive and where they don't. Wool clothes take a share of an Emberborn's cold penalties off a piece
	 * ({@link WoolClothes#WOOL_COLD_SHARE}).
	 */
	static final double EMBER_COLD_WEAK = 0.15, EMBER_COLD_SLOW = 0.10, EMBER_FREEZING_WEAK = 0.30, EMBER_FREEZING_SLOW = 0.20;
	static final float THRIVE_HUNGER = 0.8F, WILT_HUNGER = 1.25F;
	/** A biome this hot (base temperature) or hotter is hot; this cold or colder is cold. */
	static final float HOT = 1.0F, COLD = 0.2F;
	/**
	 * (0.7.123) With Kitchen's seasons, how much warmer a temperate land is in summer and colder in winter for {@link #hot}
	 * and {@link #cold}: plains (0.8) are hot in summer and cold in winter.
	 */
	static final float SUMMER_WARMTH = 0.25F, WINTER_CHILL = 0.8F;
	/** Nightborn: blows and speed in the dark (shares), blows in the sun (a share off). Dark: night, or light this low. */
	static final double NIGHT_DAMAGE = 0.1, NIGHT_SPEED = 0.05, SUN_WEAK = 0.1;
	static final int DARK_LIGHT = 7;
	/**
	 * Warrior-born: below {@code WOUNDED} of their health their blows land harder, from {@code FURY} there up to
	 * {@code FURY_MAX} at {@code DESPERATE} of their health (shares), and they're knocked back less, up to
	 * {@code FURY_KNOCKBACK}; hunger, as a factor.
	 */
	public static final float WARRIOR_WOUNDED = 0.5F, WARRIOR_DESPERATE = 0.15F;
	static final double WARRIOR_FURY = 0.10, WARRIOR_FURY_MAX = 0.35, WARRIOR_FURY_KNOCKBACK = 0.3;
	static final float WARRIOR_HUNGER = 1.2F;
	/** Earthling: skill experience, as a factor. */
	static final float EARTHLING_XP = 1.25F;
	/**
	 * (0.7.106) Stormborn: walking speed under the open sky, and lost deep underground (no sky, (0.7.154) below
	 * {@code STORM_DEEP_Y}), as shares. In a thunderstorm under the sky a blow throws a spark of this much harm to the
	 * nearest monster this close to what they hit. (0.7.154) Andy's buffs: any rain will do, and the spark leaps on to
	 * {@code STORM_SPARK_CHAIN} monsters in all, each the nearest to the last.
	 */
	static final double STORM_SKY_SPEED = 0.12, STORM_DEEP_SLOW = 0.08, STORM_SPARK_RANGE = 4.0;
	static final float STORM_SPARK_DAMAGE = 4.0F;
	static final int STORM_SPARK_CHAIN = 2, STORM_DEEP_Y = 0;
	/**
	 * (0.7.154) Charged: lightning that strikes a Stormborn (it never hurts) gives them Speed II and Strength I for this
	 * long (ticks). Jaymo's Powers' Thunderclap calls it down on purpose.
	 */
	static final int STORM_CHARGED_TICKS = 600;
	/** (0.7.106) A Stormborn throws a spark at most this often (ticks), so a target's hurt-immunity doesn't let them spam it. */
	static final int STORM_SPARK_EVERY = 10;
	/**
	 * (0.7.106) Dawnborn: blows in the sun, the share more against the undead, blows lost in the dark (shares); in the sun
	 * they heal this much this often (ticks); hunger in the dark, as a factor. (0.7.154) Andy's buffs: the numbers up, and
	 * by day their blows set the undead alight for {@code DAWN_UNDEAD_BURN} seconds.
	 */
	static final double DAWN_SUN_DAMAGE = 0.15, DAWN_DARK_WEAK = 0.1;
	static final float DAWN_UNDEAD = 0.3F, DAWN_HEAL = 1.0F, DAWN_DARK_HUNGER = 1.2F;
	static final int DAWN_HEAL_EVERY = 60, DAWN_UNDEAD_BURN = 4;
	/**
	 * (0.7.106) Endborn: the chance a projectile misses as they blink aside, how far they may land, and how long before
	 * they can again (ticks); water and rain hurt this much this often (ticks).
	 */
	static final float END_BLINK_CHANCE = 0.25F, END_WET_DAMAGE = 1.0F;
	static final double END_BLINK_RANGE = 8.0;
	static final int END_BLINK_COOLDOWN = 60, END_WET_EVERY = 40;
	/**
	 * (0.7.106) Dragonblood: the share of fire and dragon's breath they take; the hoard: blows harder by up to
	 * {@code DRAGON_HOARD_MAX} (a share) as their gold rises to {@code DRAGON_HOARD_FULL} ingots' worth (a nugget a
	 * ninth, a block nine), and lighter by {@code DRAGON_POOR} carrying none.
	 */
	static final float DRAGON_FIRE_TAKEN = 0.5F;
	static final double DRAGON_HOARD_MAX = 0.15, DRAGON_POOR = 0.1, DRAGON_HOARD_FULL = 64.0;
	/** (0.7.106) Deepborn: how long a blast stuns them (ticks); blows lost in the open sun (a share). */
	static final int DEEP_STUN_TICKS = 60;
	static final double DEEP_SUN_WEAK = 0.1;
	/**
	 * (0.7.106) The unlocks: seeing the Ender Dragon die within this far; a tamed dragon of theirs, or a digging Warden,
	 * within this far; looked for this often (ticks).
	 */
	static final double UNLOCK_DRAGON_RANGE = 192.0, UNLOCK_NEAR_RANGE = 20.0;
	static final int UNLOCK_EVERY = 100;
	/** The tag Jaymo's Powers reads to wrap a Warrior-born's tail away or let it out (it takes the tag off). */
	static final String TAIL_TOGGLE_TAG = "jaymoskingdoms_tail_toggle";
	/**
	 * Warrior-born start far weaker, to balance their potential: blows this much lighter (a share) and this much less
	 * max health, both shrinking evenly to nothing by character level {@link #WARRIOR_GROWN_LEVEL}.
	 */
	static final double WARRIOR_GREEN_DAMAGE = 0.4, WARRIOR_GREEN_HEALTH = 8.0;
	static final int WARRIOR_GROWN_LEVEL = 20;
	/** The entity tag every player of a heritage carries: this, then the heritage's key. */
	public static final String TAG = "jaymoskingdoms_heritage_";
	/** How often the heritages that change with the light and the frost are looked at (ticks). */
	static final int TICK_EVERY = 20;
	/**
	 * (0.7.87) Andy: "max height should only be accessible to verdant". (0.7.290) Every heritage's MCA Size and Width
	 * are kept to its body ({@link BodySizes}), checked this often (ticks) and whenever they save their MCA look.
	 */
	static final int SIZE_CHECK_EVERY = 200;

	/**
	 * The heritages: free points to spend as you like, an icon, and the attributes each gives a free point in (one entry a
	 * point). (0.7.106) An earned one isn't offered at the start: it's woken in play ({@link #UNLOCKED}) and (0.7.184) passed to
	 * your children ({@link VillagerHeritage#passed}).
	 */
	public enum Kind {
		HEARTLANDER(2, Items.WHEAT),
		STONEBORN(0, Items.IRON_PICKAXE, Stats.Stat.GRIT, Stats.Stat.ENDURANCE),
		TIDEBORN(0, Items.NAUTILUS_SHELL, Stats.Stat.AGILITY, Stats.Stat.ENDURANCE),
		SYLVAN(0, Items.OAK_SAPLING, Stats.Stat.AGILITY, Stats.Stat.FINESSE),
		VERDANT(0, Items.MOSS_BLOCK, Stats.Stat.ENDURANCE, Stats.Stat.WITS),
		EMBERBORN(0, Items.BLAZE_POWDER, Stats.Stat.MIGHT, Stats.Stat.MIGHT),
		FROSTBORN(0, Items.SNOWBALL, Stats.Stat.GRIT, Stats.Stat.MIGHT),
		NIGHTBORN(0, Items.PHANTOM_MEMBRANE, Stats.Stat.FINESSE, Stats.Stat.WITS),
		WARRIORBORN(0, Items.IRON_SWORD, Stats.Stat.MIGHT, Stats.Stat.ENDURANCE),
		EARTHLING(0, Items.BOOK, Stats.Stat.WITS, Stats.Stat.ENDURANCE),
		STORMBORN(true, 0, Items.LIGHTNING_ROD.weathering().unaffected(), Stats.Stat.AGILITY, Stats.Stat.FINESSE),
		DAWNBORN(0, Items.SUNFLOWER, Stats.Stat.MIGHT, Stats.Stat.GRIT),
		ENDBORN(true, 0, Items.ENDER_PEARL, Stats.Stat.WITS, Stats.Stat.FINESSE),
		DRAGONBLOOD(true, 0, Items.DRAGON_BREATH, Stats.Stat.MIGHT, Stats.Stat.ENDURANCE),
		DEEPBORN(true, 0, Items.ECHO_SHARD, Stats.Stat.ENDURANCE, Stats.Stat.WITS),
		// (0.7.262, Andy's NOEA picks) three new bloods, picked at the start like the first ten
		DIVINE(0, Items.TOTEM_OF_UNDYING, Stats.Stat.ENDURANCE, Stats.Stat.WITS),
		BIOBUILT(0, Items.COPPER_INGOT, Stats.Stat.MIGHT, Stats.Stat.GRIT),
		RUBBERFOLK(0, Items.PINK_PETALS, Stats.Stat.AGILITY, Stats.Stat.ENDURANCE),
		// (0.7.292) Andy: goblins and orcs as MCA villagers, "still monstrous with their proportions, Orcs are bigger,
		// goblins smaller, both green and repulsive with variations" ({@link Greenskins}): broods, never a player's
		GOBLIN(true, true, 0, Items.ROTTEN_FLESH, Stats.Stat.AGILITY, Stats.Stat.FINESSE),
		ORC(true, true, 0, Items.BONE, Stats.Stat.MIGHT, Stats.Stat.GRIT);

		/** Attribute points to spend as you like. */
		public final int freePoints;
		public final Item icon;
		public final List<Stats.Stat> bonuses;
		/** (0.7.106) Woken in play, not picked at the start. */
		public final boolean earned;
		/** (0.7.292) A monstrous brood (goblins, orcs): only ever born to their own, never a player's, never woken. */
		public final boolean brood;

		Kind(int freePoints, Item icon, Stats.Stat... bonuses) {
			this(false, freePoints, icon, bonuses);
		}

		Kind(boolean earned, int freePoints, Item icon, Stats.Stat... bonuses) {
			this(earned, false, freePoints, icon, bonuses);
		}

		Kind(boolean earned, boolean brood, int freePoints, Item icon, Stats.Stat... bonuses) {
			this.earned = earned;
			this.brood = brood;
			this.freePoints = freePoints;
			this.icon = icon;
			this.bonuses = List.of(bonuses);
		}

		public String key() {
			return name().toLowerCase(Locale.ROOT);
		}

		/** (0.7.292) Every heritage a player could ever have: all but the broods. */
		public static Kind[] playable() {
			return java.util.Arrays.stream(values()).filter(k -> !k.brood).toArray(Kind[]::new);
		}

		/** Free points in {@code stat}. */
		public int bonus(Stats.Stat stat) {
			int n = 0;
			for (Stats.Stat s : this.bonuses) {
				if (s == stat) {
					n++;
				}
			}
			return n;
		}

		public static @Nullable Kind byKey(String key) {
			for (Kind k : values()) {
				if (k.key().equals(key)) {
					return k;
				}
			}
			return null;
		}

		/** What the screen lists for it: the free points, the gift, the flaw. */
		public List<Component> card() {
			List<Component> out = new ArrayList<>();
			if (this.freePoints > 0) {
				out.add(Component.translatable("heritage.jaymoskingdoms.free", this.freePoints));
			}
			for (Stats.Stat stat : Stats.Stat.values()) {
				int n = bonus(stat);
				if (n > 0) {
					out.add(Component.translatable("heritage.jaymoskingdoms.bonus", n, Component.translatable("stat.jaymoskingdoms." + stat.key())));
				}
			}
			out.add(Component.translatable("heritage.jaymoskingdoms." + key() + ".gift"));
			out.add(Component.translatable("heritage.jaymoskingdoms." + key() + ".flaw"));
			// (0.7.221) and how far from human folk think it ({@link Prejudice})
			int shunned = Prejudice.degree(this);
			if (shunned > 0) {
				out.add(Component.translatable("heritage.jaymoskingdoms.shunned." + shunned));
			}
			return out;
		}
	}

	/**
	 * Each player's heritage (its key). Saved, kept through death, synced to everyone (its own player's screens, and since
	 * 0.7.75 everyone's view of an Emberborn's glow, client EmberLook).
	 */
	public static final AttachmentType<String> HERITAGE = AttachmentRegistry.create(JaymosKingdoms.id("heritage"),
		builder -> builder.persistent(Codec.STRING).copyOnDeath().syncWith(ByteBufCodecs.STRING_UTF8, AttachmentSyncPredicate.all()));

	/**
	 * (0.7.80) What every client needs for a heritage's look ({@code client/HeritageLooks}): {@link #LOOK_WET} a Tideborn
	 * who's wet, {@link #LOOK_UNSEEN} a Sylvan whose glow goes out entirely when they sneak (a Thief, or with a Stealth
	 * perk). Synced to everyone, not saved.
	 */
	public static final AttachmentType<Integer> LOOK = AttachmentRegistry.create(JaymosKingdoms.id("heritage_look"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));
	public static final int LOOK_WET = 1, LOOK_UNSEEN = 2;
	/** (0.7.80) A Tideborn's ticks left wet. Saved. */
	static final AttachmentType<Integer> WET = AttachmentRegistry.create(JaymosKingdoms.id("heritage_wet"), builder -> builder.persistent(Codec.INT));

	/**
	 * (0.7.106) The earned heritages a player has woken (their keys). Saved, kept through death, synced to its own player
	 * (the screen shows them).
	 */
	public static final AttachmentType<List<String>> UNLOCKED = AttachmentRegistry.create(JaymosKingdoms.id("heritage_unlocked"),
		builder -> builder.persistent(Codec.STRING.listOf()).copyOnDeath()
			.syncWith(ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), AttachmentSyncPredicate.targetOnly()));

	/**
	 * (0.7.106) The Blood Rite. (0.7.184) Gone, replaced by {@link Lineage}: still registered so the ones players carry
	 * load, and read, it crumbles and tells them where their blood went.
	 */
	public static Item RITE_ITEM;
	/** (0.7.106) When each Endborn may next blink aside (game time). */
	private static final Map<UUID, Long> BLINK_READY = new HashMap<>();
	/** (0.7.106) When each Stormborn last threw a spark (game time). */
	private static final Map<UUID, Long> SPARKED = new HashMap<>();

	/**
	 * Server → player: pick a heritage (the screen opens; {@code forced}: it can't be shut without picking).
	 */
	public record Open(boolean forced) implements CustomPacketPayload {
		public static final Type<Open> TYPE = new Type<>(JaymosKingdoms.id("heritage_open"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Open> CODEC = CustomPacketPayload.codec(
			(p, buf) -> buf.writeBoolean(p.forced), buf -> new Open(buf.readBoolean()));

		@Override
		public Type<Open> type() {
			return TYPE;
		}
	}

	/** Player → server: they picked heritage {@code kind} (its ordinal), (0.7.214) in its look {@code variant} (0: the first). */
	public record Choose(int kind, int variant) implements CustomPacketPayload {
		public static final Type<Choose> TYPE = new Type<>(JaymosKingdoms.id("heritage_choose"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Choose> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeVarInt(p.kind);
				buf.writeVarInt(p.variant);
			}, buf -> new Choose(buf.readVarInt(), buf.readVarInt()));

		@Override
		public Type<Choose> type() {
			return TYPE;
		}
	}

	private Heritage() {
	}

	static void register() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("blood_rite"));
		RITE_ITEM = Registry.register(BuiltInRegistries.ITEM, key, new Rite(new Item.Properties().setId(key).stacksTo(16).rarity(Rarity.RARE)));
		PayloadTypeRegistry.clientboundPlay().register(Open.TYPE, Open.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Choose.TYPE, Choose.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Choose.TYPE, (payload, context) -> choose(context.player(), payload.kind(), payload.variant()));
		ServerTickEvents.END_SERVER_TICK.register(Heritage::tick);
		// Emberborn: fire, lava, hot floors and burning never hurt
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !(entity instanceof ServerPlayer player
			&& source.is(DamageTypeTags.IS_FIRE) && is(player, Kind.EMBERBORN)));
		// (0.7.106) Stormborn: lightning never hurts ((0.7.154) it charges them); Endborn: now and then blink aside from
		// what's thrown or shot
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !(entity instanceof ServerPlayer player)
			|| !(source.is(DamageTypeTags.IS_LIGHTNING) && is(player, Kind.STORMBORN) && charged(player)) && !blink(player, source));
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (entity instanceof ServerPlayer player && taken > 0.0F) {
				stunned(player, source);
			}
			if (taken > 0.0F) {
				struck(entity, source);
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register(Heritage::died);
		net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			BLINK_READY.remove(handler.player.getUUID());
			SPARKED.remove(handler.player.getUUID());
		});
	}

	public static @Nullable Kind of(Player player) {
		String key = player.getAttached(HERITAGE);
		return key == null ? null : Kind.byKey(key);
	}

	public static boolean is(Player player, Kind kind) {
		return of(player) == kind;
	}

	/** Opens the heritage screen for {@code player} (if their client has this mod). */
	static void ask(ServerPlayer player, boolean forced) {
		if (ServerPlayNetworking.canSend(player, Open.TYPE)) {
			ServerPlayNetworking.send(player, new Open(forced));
		}
	}

	/** Whether the heritage screen can be put up for {@code player} (their client has this mod). */
	static boolean canAsk(ServerPlayer player) {
		return ServerPlayNetworking.canSend(player, Open.TYPE);
	}

	private static void choose(ServerPlayer player, int ordinal, int variant) {
		Kind[] all = Kind.values();
		if (ordinal < 0 || ordinal >= all.length) {
			return;
		}
		Kind kind = all[ordinal];
		if (of(player) != null) {
			return;
		}
		if (kind.earned && !unlocked(player, kind)) {
			return;
		}
		set(player, kind);
		HeritageVariants.choose(player, kind, variant);
		// (character creation: the class comes next)
		if (Classes.classOf(player) == null) {
			Classes.ask(player, true);
		}
	}

	/** Makes {@code player} of heritage {@code kind}: its look (a Verdant's green), its attributes, the word of it. */
	static void set(ServerPlayer player, Kind kind) {
		Kind was = of(player);
		player.setAttached(HERITAGE, kind.key());
		tag(player, kind);
		look(player, was, kind);
		Stats.settle(player);
		Skills.refresh(player);
		if (McaCompat.LOADED) {
			McaBridge.keepBody(player);
		}
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.8F, 1.0F);
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.heritage.chosen", Component.translatable("heritage.jaymoskingdoms." + kind.key()))
			.withStyle(ChatFormatting.GOLD));
	}

	/** Takes {@code player}'s heritage away (and asks again). */
	static void clear(ServerPlayer player) {
		Kind was = of(player);
		player.removeAttached(HERITAGE);
		tag(player, null);
		look(player, was, null);
		Stats.settle(player);
		Skills.refresh(player);
	}

	/** Puts the tag of heritage {@code kind} on {@code player}, and takes every other heritage's off. */
	private static void tag(ServerPlayer player, @Nullable Kind kind) {
		for (Kind k : Kind.values()) {
			if (k == kind) {
				player.addTag(TAG + k.key());
			} else {
				player.removeTag(TAG + k.key());
			}
		}
	}

	/** A Verdant is green ({@link Verdants#SHADE}, seen by everyone); leaving it behind takes the green away. */
	private static void look(ServerPlayer player, @Nullable Kind was, @Nullable Kind now) {
		if (now == Kind.VERDANT && !Verdants.is(player)) {
			player.setAttached(Verdants.SHADE, (player.getRandom().nextFloat() + player.getRandom().nextFloat()) / 2.0F);
		} else if (was == Kind.VERDANT && now != Kind.VERDANT) {
			player.removeAttached(Verdants.SHADE);
		}
	}

	// ---- effects -----------------------------------------------------------------------------------------------

	/** Puts {@code player}'s heritage on them as they stand (the attributes that don't change with the light). */
	static void attributes(ServerPlayer player) {
		Kind kind = of(player);
		if (kind != null && !player.entityTags().contains(TAG + kind.key())) {
			// (picked before the tags were kept)
			tag(player, kind);
		}
		if (kind == Kind.VERDANT && !Verdants.is(player)) {
			// (the green isn't kept through death on its own)
			look(player, null, kind);
		}
		Skills.modifier(player, Attributes.BLOCK_BREAK_SPEED, "heritage_mining", kind == Kind.STONEBORN ? STONE_MINING : 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		Skills.modifier(player, Attributes.MOVEMENT_SPEED, "heritage_slow", kind == Kind.STONEBORN ? -STONE_SLOW : 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		Skills.modifier(player, Attributes.OXYGEN_BONUS, "heritage_breath", kind == Kind.TIDEBORN ? TIDE_OXYGEN : 0.0, AttributeModifier.Operation.ADD_VALUE);
		Skills.modifier(player, Attributes.WATER_MOVEMENT_EFFICIENCY, "heritage_swim", kind == Kind.TIDEBORN ? TIDE_SWIM : 0.0, AttributeModifier.Operation.ADD_VALUE);
		Skills.modifier(player, Attributes.SUBMERGED_MINING_SPEED, "heritage_submerged", kind == Kind.TIDEBORN ? TIDE_SUBMERGED_MINING : 0.0, AttributeModifier.Operation.ADD_VALUE);
		Skills.modifier(player, Attributes.SAFE_FALL_DISTANCE, "heritage_soft", kind == Kind.SYLVAN ? SYLVAN_SAFE_FALL : 0.0, AttributeModifier.Operation.ADD_VALUE);
		Skills.modifier(player, Attributes.ATTACK_DAMAGE, "heritage_weak", kind == Kind.SYLVAN ? -SYLVAN_WEAK : 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		Skills.modifier(player, Attributes.BURNING_TIME, "heritage_burn",
			kind == Kind.VERDANT ? VERDANT_BURN : kind == Kind.EMBERBORN ? -1.0 : 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		if (kind != Kind.EMBERBORN) {
			Skills.modifier(player, Attributes.ATTACK_DAMAGE, "heritage_chill", 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
			Skills.modifier(player, Attributes.MOVEMENT_SPEED, "heritage_chill", 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		}
		if (kind != Kind.NIGHTBORN) {
			Skills.modifier(player, Attributes.ATTACK_DAMAGE, "heritage_night", 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
			Skills.modifier(player, Attributes.MOVEMENT_SPEED, "heritage_night", 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		}
		if (kind != Kind.WARRIORBORN) {
			Skills.modifier(player, Attributes.ATTACK_DAMAGE, "heritage_fury", 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
			Skills.modifier(player, Attributes.KNOCKBACK_RESISTANCE, "heritage_fury", 0.0, AttributeModifier.Operation.ADD_VALUE);
		}
		if (kind != Kind.TIDEBORN) {
			Skills.modifier(player, Attributes.ATTACK_DAMAGE, "heritage_tide", 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		}
		// (0.7.106) the new bloods' modifiers that change with where they stand, taken off everyone else
		if (kind != Kind.STORMBORN) {
			Skills.modifier(player, Attributes.MOVEMENT_SPEED, "heritage_sky", 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		}
		if (kind != Kind.DAWNBORN && kind != Kind.DEEPBORN) {
			Skills.modifier(player, Attributes.ATTACK_DAMAGE, "heritage_sun", 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		}
		if (kind != Kind.DRAGONBLOOD) {
			Skills.modifier(player, Attributes.ATTACK_DAMAGE, "heritage_hoard", 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		}
		green(player, kind);
		// (0.7.262) the Bio-built's form, the Rubber-folk's stretch and burn (taken off everyone else)
		BioBuilt.attributes(player, kind);
		RubberFolk.attributes(player, kind);
		// (0.7.214) the variant's trait
		HeritageVariants.traits(player);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % TICK_EVERY != 0) {
			return;
		}
		boolean sizes = McaCompat.LOADED && server.getTickCount() % SIZE_CHECK_EVERY == 0;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Kind kind = of(player);
			look(player, kind);
			if (sizes) {
				McaBridge.keepBody(player);
			}
			if (kind == Kind.NIGHTBORN) {
				// stronger and quicker in the dark, weaker under the open sun
				double damage = 0.0, speed = 0.0;
				if (dark(player)) {
					// (0.7.88) a Nightborn vampire's grows under a Blood Moon
					double bonus = NightbornBlood.darkBonus(player);
					damage = NIGHT_DAMAGE * bonus;
					speed = NIGHT_SPEED * bonus;
				} else if (inSun(player)) {
					damage = -SUN_WEAK;
				}
				Skills.modifier(player, Attributes.ATTACK_DAMAGE, "heritage_night", damage, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
				Skills.modifier(player, Attributes.MOVEMENT_SPEED, "heritage_night", speed, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
			} else if (kind == Kind.WARRIORBORN) {
				// the worse they're hurt, the harder they fight
				float rage = rage(player);
				Skills.modifier(player, Attributes.ATTACK_DAMAGE, "heritage_fury", fury(rage), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
				Skills.modifier(player, Attributes.KNOCKBACK_RESISTANCE, "heritage_fury", WARRIOR_FURY_KNOCKBACK * rage, AttributeModifier.Operation.ADD_VALUE);
				green(player, kind);
			} else if (kind == Kind.EMBERBORN) {
				// flame won't take on them; the cold saps them, freezing worst of all
				if (player.isOnFire()) {
					player.clearFire();
				}
				double weak = 0.0, slow = 0.0;
				if (player.getTicksFrozen() > 0) {
					weak = EMBER_FREEZING_WEAK;
					slow = EMBER_FREEZING_SLOW;
				} else if (chilled(player)) {
					weak = EMBER_COLD_WEAK;
					slow = EMBER_COLD_SLOW;
				}
				// knitted wool keeps some of it off, a share a piece
				double bare = 1.0 - WoolClothes.warmth(player);
				Skills.modifier(player, Attributes.ATTACK_DAMAGE, "heritage_chill", -weak * bare, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
				Skills.modifier(player, Attributes.MOVEMENT_SPEED, "heritage_chill", -slow * bare, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
			} else if (kind == Kind.FROSTBORN && player.getTicksFrozen() > 0) {
				player.setTicksFrozen(0);
			} else if (kind == Kind.STORMBORN) {
				// quicker under the open sky, sluggish deep underground
				double speed = openSky(player) ? STORM_SKY_SPEED : deep(player) ? -STORM_DEEP_SLOW : 0.0;
				Skills.modifier(player, Attributes.MOVEMENT_SPEED, "heritage_sky", speed, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
			} else if (kind == Kind.DAWNBORN) {
				// the sun strengthens and mends them; the dark dulls them
				boolean sun = inSun(player);
				Skills.modifier(player, Attributes.ATTACK_DAMAGE, "heritage_sun", sun ? DAWN_SUN_DAMAGE : dark(player) ? -DAWN_DARK_WEAK : 0.0,
					AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
				if (sun && server.getTickCount() % DAWN_HEAL_EVERY == 0 && player.getHealth() < player.getMaxHealth()) {
					player.heal(DAWN_HEAL);
				}
			} else if (kind == Kind.ENDBORN) {
				// water burns them like an enderman
				if (player.isInWaterOrRain() && server.getTickCount() % END_WET_EVERY == 0 && !player.isCreative() && !player.isSpectator()) {
					player.hurtServer(player.level(), player.damageSources().drown(), END_WET_DAMAGE);
				}
			} else if (kind == Kind.DRAGONBLOOD) {
				Skills.modifier(player, Attributes.ATTACK_DAMAGE, "heritage_hoard", hoard(player), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
			} else if (kind == Kind.DEEPBORN) {
				Skills.modifier(player, Attributes.ATTACK_DAMAGE, "heritage_sun", inSun(player) ? -DEEP_SUN_WEAK : 0.0,
					AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
			}
			if (server.getTickCount() % UNLOCK_EVERY == 0) {
				unlockChecks(player);
			}
		}
	}

	/**
	 * (0.7.80) A Tideborn gets wet touching water (or rain) and dries {@link #TIDE_WET_TICKS} after; wet, they hit harder.
	 * A Sylvan who's a Thief or has a Stealth perk can't be seen glowing while sneaking. Both go on {@link #LOOK}.
	 */
	private static void look(ServerPlayer player, @Nullable Kind kind) {
		int look = 0;
		if (kind == Kind.TIDEBORN) {
			int left = player.isInWaterOrRain() ? TIDE_WET_TICKS : Math.max(0, player.getAttachedOrElse(WET, 0) - TICK_EVERY);
			player.setAttached(WET, left);
			boolean wet = left > 0;
			Skills.modifier(player, Attributes.ATTACK_DAMAGE, "heritage_tide", wet ? TIDE_WET_DAMAGE : 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
			look = wet ? LOOK_WET : 0;
		} else if (kind == Kind.SYLVAN && unseen(player)) {
			look = LOOK_UNSEEN;
		}
		if (player.getAttachedOrElse(LOOK, 0) != look) {
			if (look == 0) {
				player.removeAttached(LOOK);
			} else {
				player.setAttached(LOOK, look);
			}
		}
	}

	/** A Thief, or anyone who's learned a Stealth perk. */
	static boolean unseen(ServerPlayer player) {
		if (Classes.is(player, Classes.PlayerClass.THIEF)) {
			return true;
		}
		Skills.Progress progress = Skills.of(player);
		for (Skills.Perk perk : Skills.Perk.values()) {
			if (perk.pillar == Skills.Pillar.STEALTH && progress.rank(perk) > 0) {
				return true;
			}
		}
		return false;
	}

	/** A Tideborn who's still wet (touched water within {@link #TIDE_WET_TICKS}). */
	static boolean wet(ServerPlayer player) {
		return player.isInWaterOrRain() || player.getAttachedOrElse(WET, 0) > 0;
	}

	/** How much of a Warrior-born's early weakness is left, 1 (none grown) to 0 (character level {@link #WARRIOR_GROWN_LEVEL}). */
	static double greenness(ServerPlayer player) {
		return Math.clamp(1.0 - Stats.characterLevel(Skills.of(player)) / (double)WARRIOR_GROWN_LEVEL, 0.0, 1.0);
	}

	/** A Warrior-born's early weakness, as it stands now (taken off everyone else). */
	private static void green(ServerPlayer player, @Nullable Kind kind) {
		double left = kind == Kind.WARRIORBORN ? greenness(player) : 0.0;
		Skills.modifier(player, Attributes.ATTACK_DAMAGE, "heritage_green", -WARRIOR_GREEN_DAMAGE * left, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		// whole hearts, so the bar doesn't flicker by halves as they grow
		Skills.modifier(player, Attributes.MAX_HEALTH, "heritage_green_health", -2.0 * Math.ceil(WARRIOR_GREEN_HEALTH / 2.0 * left),
			AttributeModifier.Operation.ADD_VALUE);
		if (player.getHealth() > player.getMaxHealth()) {
			player.setHealth(player.getMaxHealth());
		}
	}

	/** Night where they stand, or a dark place. */
	static boolean dark(ServerPlayer player) {
		Level level = player.level();
		BlockPos pos = player.blockPosition();
		return level.getMaxLocalRawBrightness(pos) <= DARK_LIGHT || level.isDarkOutside() && level.canSeeSky(pos);
	}

	/** Day, under the open sky, no rain. */
	static boolean inSun(ServerPlayer player) {
		Level level = player.level();
		BlockPos pos = player.blockPosition().above();
		return level.isBrightOutside() && level.canSeeSky(pos) && !level.isRainingAt(pos);
	}

	/** The factor on {@code player}'s hunger drain from their heritage, where they are now. */
	static float exhaustion(ServerPlayer player) {
		Kind kind = of(player);
		if (kind == null) {
			return 1.0F;
		}
		return switch (kind) {
			case TIDEBORN -> wet(player) ? 1.0F : TIDE_DRY_HUNGER;
			case VERDANT -> inSun(player) ? (spring(player) ? VERDANT_SPRING_HUNGER : VERDANT_SUN_HUNGER) : 1.0F;
			case EMBERBORN -> hot(player) ? THRIVE_HUNGER
				: chilled(player) ? 1.0F + (WILT_HUNGER - 1.0F) * (float) (1.0 - WoolClothes.warmth(player)) : 1.0F;
			case FROSTBORN -> cold(player) ? THRIVE_HUNGER : hot(player) ? WILT_HUNGER : 1.0F;
			case WARRIORBORN -> WARRIOR_HUNGER;
			case DAWNBORN -> dark(player) ? DAWN_DARK_HUNGER : 1.0F;
			default -> 1.0F;
		};
	}

	/**
	 * (0.7.123) The land's warmth where {@code player} stands: its biome's, and with Kitchen's seasons a temperate land
	 * (plains, forests) is warmer in summer and colder in winter, enough to be hot or cold for an Emberborn or Frostborn.
	 */
	private static float temperature(ServerPlayer player) {
		float base = player.level().getBiome(player.blockPosition()).value().getBaseTemperature();
		if (!KitchenCompat.temperate(base)) {
			return base;
		}
		String season = KitchenCompat.season(player.level());
		return "summer".equals(season) ? base + SUMMER_WARMTH : "winter".equals(season) ? base - WINTER_CHILL : base;
	}

	/** (0.7.123) Verdant in spring: everything's growing, so the sun feeds them even more. */
	static boolean spring(ServerPlayer player) {
		return "spring".equals(KitchenCompat.season(player.level()));
	}

	static boolean hot(ServerPlayer player) {
		return temperature(player) >= HOT;
	}

	static boolean cold(ServerPlayer player) {
		return temperature(player) <= COLD;
	}

	/** In the cold, for an Emberborn: a cold land (snow, ice, the frozen peaks, a temperate land in winter), or freezing. */
	static boolean chilled(ServerPlayer player) {
		return cold(player) || player.getTicksFrozen() > 0;
	}

	/**
	 * (0.7.96) How furious a wounded Warrior-born (or anyone, on the client's side: health is synced) is: 0 above
	 * {@link #WARRIOR_WOUNDED} of their health, rising to 1 at {@link #WARRIOR_DESPERATE}.
	 */
	public static float rage(LivingEntity entity) {
		float share = entity.getHealth() / Math.max(1.0F, entity.getMaxHealth());
		return share >= WARRIOR_WOUNDED ? 0.0F : Math.clamp((WARRIOR_WOUNDED - share) / (WARRIOR_WOUNDED - WARRIOR_DESPERATE), 0.0F, 1.0F);
	}

	/** The extra blow (a share) at {@code rage}: none at 0, {@link #WARRIOR_FURY} as it starts, {@link #WARRIOR_FURY_MAX} at 1. */
	static double fury(float rage) {
		return rage <= 0.0F ? 0.0 : WARRIOR_FURY + (WARRIOR_FURY_MAX - WARRIOR_FURY) * rage;
	}

	/** Earthling: the factor on skill experience. */
	static float xpFactor(Player player) {
		return is(player, Kind.EARTHLING) ? EARTHLING_XP : 1.0F;
	}

	/** The heritage key: a Warrior-born's tail (a Frostborn's ice prison is their left mouse held, through Jaymo's Powers). */
	static void gift(ServerPlayer player) {
		Kind kind = of(player);
		if (kind == Kind.WARRIORBORN) {
			player.addTag(TAIL_TOGGLE_TAG);
		} else if (kind == Kind.BIOBUILT) {
			// (0.7.288) on a downed foe it starts (or stops) absorbing it; else, in the last form, it buds copies
			BioBuilt.press(player);
		} else if (kind == Kind.RUBBERFOLK) {
			// (0.7.288) starts (or stops) swallowing a weak foe
			RubberFolk.press(player);
		}
	}

	/** Sylvan: the share off how far sneaking is seen. */
	static double sneak(Player player) {
		return is(player, Kind.SYLVAN) ? SYLVAN_SNEAK : 0.0;
	}

	// ---- (0.7.106) the new bloods -----------------------------------------------------------------------------------

	/** Under the open sky (nothing overhead), in the Overworld's light: a Stormborn's home. */
	static boolean openSky(ServerPlayer player) {
		return player.level().canSeeSky(player.blockPosition().above());
	}

	/** Deep underground: no sky overhead and (0.7.154, was below the sea) below {@link #STORM_DEEP_Y}. */
	static boolean deep(ServerPlayer player) {
		return !player.level().canSeeSky(player.blockPosition().above()) && player.getY() < STORM_DEEP_Y;
	}

	/** A Dragonblood's hoard: their blows' share more (or less, carrying no gold at all) for the gold they carry. */
	static double hoard(ServerPlayer player) {
		double gold = 0.0;
		var inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.is(Items.GOLD_INGOT) || stack.is(Items.RAW_GOLD)) {
				gold += stack.getCount();
			} else if (stack.is(Items.GOLD_NUGGET)) {
				gold += stack.getCount() / 9.0;
			} else if (stack.is(Items.GOLD_BLOCK)) {
				gold += stack.getCount() * 9.0;
			}
		}
		return gold <= 0.0 ? -DRAGON_POOR : DRAGON_HOARD_MAX * Math.min(1.0, gold / DRAGON_HOARD_FULL);
	}

	/** An Endborn shot at or hit by something thrown: now and then they blink aside, and it misses (true). */
	private static boolean blink(ServerPlayer player, DamageSource source) {
		if (!source.is(DamageTypeTags.IS_PROJECTILE) || !is(player, Kind.ENDBORN) || player.isDeadOrDying()
			|| !(player.level() instanceof ServerLevel level) || player.isInvulnerableTo(level, source)) {
			return false;
		}
		long now = player.level().getGameTime();
		Long ready = BLINK_READY.get(player.getUUID());
		if (ready != null && now < ready || player.getRandom().nextFloat() >= END_BLINK_CHANCE) {
			return false;
		}
		for (int tries = 0; tries < 16; tries++) {
			double x = player.getX() + (player.getRandom().nextDouble() - 0.5) * 2.0 * END_BLINK_RANGE;
			double y = player.getY() + player.getRandom().nextInt(5) - 2;
			double z = player.getZ() + (player.getRandom().nextDouble() - 0.5) * 2.0 * END_BLINK_RANGE;
			double fromX = player.getX(), fromY = player.getY(), fromZ = player.getZ();
			if (player.randomTeleport(x, y, z, true, state -> false)) {
				BLINK_READY.put(player.getUUID(), now + END_BLINK_COOLDOWN);
				player.level().playSound(null, fromX, fromY, fromZ, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8F, 1.2F);
				player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8F, 1.2F);
				return true;
			}
		}
		return false;
	}

	/** A Deepborn caught in a blast: the noise stuns them for a moment. */
	private static void stunned(ServerPlayer player, DamageSource source) {
		if (source.is(DamageTypeTags.IS_EXPLOSION) && is(player, Kind.DEEPBORN)) {
			player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, DEEP_STUN_TICKS + 40, 0, false, false, true));
			player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, DEEP_STUN_TICKS, 2, false, false, true));
		}
	}

	/**
	 * Called from the hurt mixin ({@code LivingEntityHurtMixin}) for every blow to {@code target}: a Dragonblood (player or
	 * villager) takes half from fire and dragon's breath; a Dawnborn hits the undead harder.
	 */
	public static float dealt(LivingEntity target, DamageSource source, float amount) {
		Kind mine = VillagerHeritage.of(target);
		if (mine == Kind.DRAGONBLOOD && (source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypes.DRAGON_BREATH))) {
			amount *= DRAGON_FIRE_TAKEN;
		} else if (mine == Kind.BIOBUILT && (source.is(DamageTypeTags.IS_LIGHTNING) || source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC))) {
			// (0.7.262) a made body is brittle to lightning and magic
			amount *= BioBuilt.BRITTLE;
		} else if (mine == Kind.RUBBERFOLK) {
			// (0.7.262) rubber pops
			amount *= RubberFolk.taken(source);
		}
		Entity by = source.getEntity();
		if (!(by instanceof LivingEntity attacker) || by != source.getDirectEntity() || target.level().isClientSide()) {
			return amount;
		}
		Kind kind = VillagerHeritage.of(attacker);
		if (kind == Kind.DAWNBORN && target.is(EntityTypeTags.UNDEAD)) {
			amount *= 1.0F + DAWN_UNDEAD;
			// (0.7.154) by day the sun in their blows sets the dead alight
			if (target.level().isBrightOutside()) {
				target.igniteForSeconds(DAWN_UNDEAD_BURN);
			}
		} else if (kind == Kind.DIVINE) {
			// (0.7.262) the Divine-born smite the wicked
			amount *= DivineBorn.smite(attacker, target);
		}
		return amount;
	}

	/**
	 * (0.7.154) Lightning struck a Stormborn: charged, Speed II and Strength I for a while, and the crackle of it. Always
	 * true (the strike still does them no harm).
	 */
	private static boolean charged(ServerPlayer player) {
		player.addEffect(new MobEffectInstance(MobEffects.SPEED, STORM_CHARGED_TICKS, 1));
		player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, STORM_CHARGED_TICKS, 0));
		player.level().sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY(0.5), player.getZ(), 12, 0.3, 0.6, 0.3, 0.1);
		return true;
	}

	/**
	 * A blow that landed on {@code target}: a Stormborn player's, in a thunderstorm ((0.7.154) or any rain) under open
	 * sky, throws a spark.
	 */
	private static void struck(LivingEntity target, DamageSource source) {
		if (source.getEntity() instanceof Player attacker && source.getEntity() == source.getDirectEntity() && is(attacker, Kind.STORMBORN)
			&& attacker.level().isRaining() && attacker.level().canSeeSky(attacker.blockPosition().above())
			&& attacker.level() instanceof ServerLevel level) {
			spark(level, attacker, target);
		}
	}

	/** A Stormborn's spark: from what they hit to the nearest monster close by (not them, not it); one a swing at most. */
	private static void spark(ServerLevel level, LivingEntity attacker, LivingEntity target) {
		long now = level.getGameTime();
		Long last = SPARKED.get(attacker.getUUID());
		if (last != null && now - last < STORM_SPARK_EVERY && now >= last) {
			return;
		}
		SPARKED.put(attacker.getUUID(), now);
		// (0.7.154) it leaps on from each it strikes to the nearest monster not yet struck
		List<LivingEntity> struck = new ArrayList<>();
		LivingEntity from = target;
		for (int n = 0; n < STORM_SPARK_CHAIN; n++) {
			LivingEntity at = from;
			LivingEntity nearest = null;
			double best = STORM_SPARK_RANGE * STORM_SPARK_RANGE;
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, at.getBoundingBox().inflate(STORM_SPARK_RANGE),
				e -> e != attacker && e != target && !struck.contains(e) && e.isAlive() && e instanceof Enemy)) {
				double d = e.distanceToSqr(at);
				if (d < best) {
					best = d;
					nearest = e;
				}
			}
			if (nearest == null) {
				return;
			}
			// a line of sparks between them
			double fx = at.getX(), fy = at.getY(0.6), fz = at.getZ();
			double tx = nearest.getX(), ty = nearest.getY(0.6), tz = nearest.getZ();
			for (int i = 0; i <= 8; i++) {
				double t = i / 8.0;
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, fx + (tx - fx) * t, fy + (ty - fy) * t, fz + (tz - fz) * t, 2, 0.05, 0.05, 0.05, 0.0);
			}
			level.playSound(null, tx, ty, tz, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.4F, 1.6F);
			nearest.hurtServer(level, level.damageSources().lightningBolt(), STORM_SPARK_DAMAGE);
			struck.add(nearest);
			from = nearest;
		}
	}

	// ---- (0.7.106) earned heritages ------------------------------------------------------------------------------

	/** Whether {@code player} has woken earned heritage {@code kind} (any heritage that isn't earned is always open). */
	public static boolean unlocked(Player player, Kind kind) {
		if (kind.brood) {
			// (0.7.292) a goblin's or an orc's blood is never a player's
			return false;
		}
		if (!kind.earned) {
			return true;
		}
		List<String> keys = player.getAttached(UNLOCKED);
		return keys != null && keys.contains(kind.key());
	}

	/** Wakes earned heritage {@code kind} in {@code player}, once: the word of it ((0.7.184) their children can carry it). */
	static void unlock(ServerPlayer player, Kind kind) {
		if (!kind.earned || kind.brood || unlocked(player, kind)) {
			return;
		}
		List<String> keys = new ArrayList<>(player.getAttachedOrElse(UNLOCKED, List.of()));
		keys.add(kind.key());
		player.setAttached(UNLOCKED, List.copyOf(keys));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 0.6F);
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.heritage.unlocked",
			Component.translatable("heritage.jaymoskingdoms." + kind.key())).withStyle(ChatFormatting.DARK_RED));
	}

	/**
	 * Every {@link #UNLOCK_EVERY} ticks, what's round {@code player} that wakes an earned heritage: the Ender Dragon dying
	 * near them (Endborn and Dragonblood), a dragon of any mod tamed by them close by (Dragonblood), a Warden digging
	 * away close by (Deepborn).
	 */
	static void unlockChecks(ServerPlayer player) {
		if (!(player.level() instanceof ServerLevel level) || player.isSpectator()) {
			return;
		}
		// (getDragons() leaves out a dying dragon: its health is already 0)
		for (EnderDragon dragon : level.getEntities(EntityTypes.ENDER_DRAGON, d -> d.dragonDeathTime > 0)) {
			if (dragon.distanceTo(player) < UNLOCK_DRAGON_RANGE) {
				unlock(player, Kind.ENDBORN);
				unlock(player, Kind.DRAGONBLOOD);
			}
		}
		// (0.7.140) a wind the storm has taken (Jaymo's Powers' Stormbound) wakes the Stormborn
		if (!unlocked(player, Kind.STORMBORN) && "wind".equals(PowersCompat.power(player)) && PowersCompat.corrupted(player)) {
			unlock(player, Kind.STORMBORN);
		}
		var near = player.getBoundingBox().inflate(UNLOCK_NEAR_RANGE);
		if (!unlocked(player, Kind.DEEPBORN)) {
			if (!level.getEntitiesOfClass(Warden.class, near, w -> w.getPose() == Pose.DIGGING).isEmpty()) {
				unlock(player, Kind.DEEPBORN);
			}
		}
		if (!unlocked(player, Kind.DRAGONBLOOD)) {
			for (LivingEntity tame : level.getEntitiesOfClass(LivingEntity.class, near, e -> e instanceof OwnableEntity && dragon(e))) {
				if (((OwnableEntity)tame).getOwner() == player) {
					unlock(player, Kind.DRAGONBLOOD);
				}
			}
		}
	}

	/** A dragon of any mod (its id says so), the Ender Dragon too. */
	static boolean dragon(Entity entity) {
		return entity instanceof EnderDragon || BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath().contains("dragon");
	}

	/** Something died: a dragon slain by a player wakes their Dragonblood, a Warden their Deepborn. */
	private static void died(LivingEntity entity, DamageSource source) {
		if (!(source.getEntity() instanceof ServerPlayer player)) {
			return;
		}
		if (dragon(entity)) {
			unlock(player, Kind.DRAGONBLOOD);
		} else if (entity instanceof Warden) {
			unlock(player, Kind.DEEPBORN);
		}
	}

	/** (0.7.106) The Blood Rite. (0.7.184) Read, it crumbles: an earned blood is taken up through your children now. */
	static final class Rite extends Item {
		Rite(Item.Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (player instanceof ServerPlayer sp) {
				sp.getItemInHand(hand).consume(1, sp);
				level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.SAND_BREAK, SoundSource.PLAYERS, 0.8F, 0.8F);
				sp.sendSystemMessage(Component.translatable("message.jaymoskingdoms.heritage.rite_crumbles").withStyle(ChatFormatting.GRAY));
			}
			return InteractionResult.SUCCESS;
		}
	}

	// ---- for ops ------------------------------------------------------------------------------------------------

	/** {@code /kingdom heritage set|clear|get|unlock}, for ops. */
	static LiteralArgumentBuilder<CommandSourceStack> command() {
		var players = Commands.argument("players", EntityArgument.players());
		// (0.7.106) wake an earned heritage in them, as if they'd earned it
		var unlockPlayers = Commands.argument("players", EntityArgument.players());
		for (Kind kind : Kind.values()) {
			if (!kind.earned || kind.brood) {
				continue;
			}
			unlockPlayers.then(Commands.literal(kind.key()).executes(c -> {
				int n = 0;
				for (ServerPlayer p : EntityArgument.getPlayers(c, "players")) {
					unlock(p, kind);
					n++;
				}
				int count = n;
				c.getSource().sendSuccess(() -> Component.translatable("command.jaymoskingdoms.heritage.unlocked", count,
					Component.translatable("heritage.jaymoskingdoms." + kind.key())), true);
				return n;
			}));
		}
		for (Kind kind : Kind.values()) {
			if (kind.brood) {
				// (0.7.292) goblins and orcs are villagers only
				continue;
			}
			players.then(Commands.literal(kind.key()).executes(c -> {
				int n = 0;
				for (ServerPlayer p : EntityArgument.getPlayers(c, "players")) {
					set(p, kind);
					n++;
				}
				int count = n;
				c.getSource().sendSuccess(() -> Component.translatable("command.jaymoskingdoms.heritage.set", count,
					Component.translatable("heritage.jaymoskingdoms." + kind.key())), true);
				return n;
			}));
		}
		return Commands.literal("heritage").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.then(Commands.literal("set").then(players))
			.then(Commands.literal("unlock").then(unlockPlayers))
			.then(Commands.literal("clear").then(Commands.argument("players", EntityArgument.players()).executes(c -> {
				int n = 0;
				for (ServerPlayer p : EntityArgument.getPlayers(c, "players")) {
					clear(p);
					ask(p, true);
					n++;
				}
				int count = n;
				c.getSource().sendSuccess(() -> Component.translatable("command.jaymoskingdoms.heritage.cleared", count), true);
				return n;
			})))
			.then(Commands.literal("get").then(Commands.argument("player", EntityArgument.player()).executes(c -> {
				ServerPlayer p = EntityArgument.getPlayer(c, "player");
				Kind kind = of(p);
				c.getSource().sendSuccess(() -> kind == null ? Component.translatable("command.jaymoskingdoms.heritage.none", p.getDisplayName())
					: Component.translatable("command.jaymoskingdoms.heritage.is", p.getDisplayName(), Component.translatable("heritage.jaymoskingdoms." + kind.key())), false);
				return 1;
			})));
	}
}
