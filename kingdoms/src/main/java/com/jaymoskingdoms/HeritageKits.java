package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import com.jaymoskingdoms.mixin.MobAccessor;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.490) Heritage kits (Andy: "using the weakness/abilities"; docs/Races-Plan.md). Every heritage and look has a Runic Races
 * style kit: one active ability on the heritage key ({@code ;}), one passive (a gift) and one weakness (a flaw), shown as
 * Ability / Gift / Flaw on the heritage card. A kit sits on top of the heritage's own gifts and flaws (they stay as they were,
 * and a kit never repeats one: where the heritage already has the passive or the weakness, the kit's slot is {@link Perk#NONE}).
 * Where a heritage already has an ability (the Frostborn's ice prison, the Stormborn's Thunderclap, the Dawnborn's Sunflare,
 * the Verdant's stretch: Jaymo's Powers' left mouse holds; the Bio-built's and Rubber-folk's engulfing on the heritage key) the
 * kit points at it. A look with a race of its own (the Beastfolk, the Hollow, the Sylvan fae and elves, the Stoneborn,
 * Dragonblood, Heartlander and Earthling looks) takes that race's kit; every other look has its heritage's.
 * <p>
 * Runic Races (Otectus, MIT) gave the ideas, names and rough numbers; the code is ours. Villagers of a heritage get its kit's
 * passives and weaknesses ({@link #attributes}, {@link #tick}, {@link #taken}, {@link #dealt}) but never its ability.
 */
public final class HeritageKits {
	// ---- tuning: the moves ----
	/** Cooldowns are on each {@link Move}. Durations in seconds, amplifiers from 0. */
	static final int SHADOWMELD_SECONDS = 8, TREMOR_SECONDS = 10, TREMOR_RANGE = 24, FORGE_SECONDS = 12, WALL_SECONDS = 6, WARD_SECONDS = 15;
	static final float FORGE_MEND = 0.25F;
	static final double LEAP_UP = 0.9, LEAP_FORWARD = 1.0, DASH = 1.7, POUNCE_FORWARD = 1.3, POUNCE_UP = 0.45, WIND_UP = 1.4, STEP_RANGE = 12.0;
	/** Breaths: reach, how wide the cone (dot of look and the line to the foe), harm, seconds alight or chilled. */
	static final double BREATH_RANGE = 8.0, BREATH_CONE = 0.7;
	static final float FIRE_BREATH_DAMAGE = 5.0F, FROST_BREATH_DAMAGE = 4.0F;
	static final int FIRE_BREATH_BURN = 6, FROST_BREATH_SECONDS = 5;
	static final double QUAKE_RANGE = 5.0;
	static final float QUAKE_DAMAGE = 4.0F;
	static final float TIDAL_HEAL = 4.0F;
	static final int TIDAL_SECONDS = 10, SNARE_SECONDS = 4, WEB_SECONDS = 5;
	static final double SNARE_RANGE = 8.0, WEB_RANGE = 14.0;
	static final int CRY_SECONDS = 10, WIND_SECONDS = 8;
	static final double CRY_RANGE = 8.0, HOWL_RANGE = 12.0, WOLF_RANGE = 16.0;
	static final float WIND_HEAL = 6.0F, HALO_HEAL = 4.0F, HALO_SMITE = 6.0F, REAP_DAMAGE = 3.0F, REAP_HEAL = 1.5F, REAP_HEAL_MAX = 6.0F, BLOOM_HEAL = 4.0F;
	static final double HALO_RANGE = 8.0, HALO_SMITE_RANGE = 6.0, REAP_RANGE = 6.0, BLOOM_RANGE = 6.0, CHILL_RANGE = 6.0, FOXFIRE_RANGE = 12.0, CHARM_RANGE = 12.0;
	static final int BLOOM_GROWS = 12, BLOOM_REACH = 4;
	/** Lifesteal (Undying Hunger, Blood Frenzy): how long (ticks) and the share of a blow's harm that comes back. */
	static final int HUNGER_TICKS = 200, FRENZY_TICKS = 200;
	static final float HUNGER_STEAL = 0.3F, FRENZY_STEAL = 0.25F;
	/** Pounce: the next blow within this long lands this much harder (a factor). */
	static final int POUNCE_TICKS = 40;
	static final float POUNCE_DAMAGE = 1.5F;
	/** Conscript the Dead: how many rise, how long they stand (ticks), how near they are called, how far they look for a foe. */
	static final int CONSCRIPT_COUNT = 2, CONSCRIPT_TICKS = 600;
	static final double CONSCRIPT_REACH = 16.0;
	/** Phasing (Spectral Phase, Phase Shift): how long (ticks) nothing can touch them. */
	static final int SPECTRAL_TICKS = 80, PHASE_TICKS = 40;
	/** Arcane Reflex: how long (ticks) blows land at {@code REFLEX_TAKEN}. */
	static final int REFLEX_TICKS = 60;
	static final float REFLEX_TAKEN = 0.3F;
	/** (0.7.490) Arcane Reflex: how hard a blow that lands throws its striker back, and up. */
	static final double REFLEX_SHOVE = 0.6, REFLEX_LIFT = 0.2;
	/** Faerie Bargain: the health paid (it needs more than {@code BARGAIN_FLOOR} to strike) and the seconds of strength bought. */
	static final float BARGAIN_COST = 4.0F, BARGAIN_FLOOR = 8.0F;
	static final int BARGAIN_SECONDS = 15, MIRROR_RANGE = 16, VEIL_SECONDS = 8, VEIL_NIGHT_SECONDS = 12;
	// ---- tuning: the gifts and flaws ----
	static final float FIRE_FRAIL_TAKEN = 1.5F, TRIPLE_FIRE_TAKEN = 3.0F, COLD_IRON_TAKEN = 1.35F, WARDED_TAKEN = 0.7F, SPIRIT_MAGIC_TAKEN = 1.4F;
	static final float GLACIAL_TAKEN = 0.85F, BONES_TAKEN = 1.1F, GLASS_TAKEN = 1.2F, GLASS_DEALT = 1.2F, BRITTLE_TAKEN = 1.5F, HOLY_TAKEN = 1.3F;
	static final float BLOODSCENT_DEALT = 1.1F, ARCHER_DEALT = 1.15F;
	static final int VENOM_TICKS = 60;
	/** Healing taken, as a factor, for the half-healed, the less-healed, and the Hollow. */
	static final float HALF_HEALING = 0.5F, LESS_HEALING = 0.75F;
	/** Hunger drain, as a factor. */
	static final float RAVENOUS_HUNGER = 1.3F, HUNGRY_HUNGER = 1.15F, STOMACH_HUNGER = 0.6F, DRY_HUNGER = 1.3F;
	/** Sun decay: the harm and how often (ticks). Sun, wet, cold and the rest: the shares off blows and speed. */
	static final float SUN_DECAY_DAMAGE = 1.0F;
	static final int SUN_DECAY_EVERY = 40, PHOTO_EVERY = 60, DRY_LIMIT_TICKS = 6000;
	static final float PHOTO_HEAL = 1.0F, SOUL_TAKER_HEAL = 3.0F;
	static final double SUN_WEAK_DAMAGE = 0.12, SUN_HOLY_DAMAGE = 0.10, SUN_HOLY_SPEED = 0.05, WET_DAMAGE = 0.15, WET_SPEED = 0.15;
	static final double COLD_BLOOD_DAMAGE = 0.15, COLD_BLOOD_SPEED = 0.20, DRY_DAMAGE = 0.15, DRY_SPEED = 0.10, LAND_SPEED = 0.05;
	static final double HEAVY_SPEED = 0.15, GROUNDED_DAMAGE = 0.12, MOON_SPEED = 0.05;
	/**
	 * (0.7.490) Heavy in water: each tick in water, the swim's sideways speed is kept at this factor (about 15% slower, sprinting
	 * or not) and they're pulled down this much (blocks per tick), as a stone sinks. Swimming ignores speed attributes.
	 */
	public static final double HEAVY_DRAG = 0.97, HEAVY_SINK = 0.012;
	/** Nine Lives: they live again, at 1 health, at most this often (ticks). */
	static final int NINE_LIVES_COOLDOWN = 6000;
	/** Night vision lasts this long each time it's given (ticks), over the 1 s between looks. */
	static final int EYES_TICKS = 300;
	/** Gossamer flight: while sneaking in the air they fall like a feather, refreshed this often (ticks). */
	static final int GOSSAMER_EVERY = 4, GOSSAMER_TICKS = 12;

	/** The tracked static attributes' modifier ids start with this. */
	private static final String KIT_ID = "heritage_kit_";

	/** One tuned attribute change. */
	record Mod(Holder<Attribute> attribute, double amount, AttributeModifier.Operation operation) {
		static Mod add(Holder<Attribute> attribute, double amount) {
			return new Mod(attribute, amount, AttributeModifier.Operation.ADD_VALUE);
		}

		static Mod share(Holder<Attribute> attribute, double amount) {
			return new Mod(attribute, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		}
	}

	/** The abilities, each with its cooldown (seconds). The first few are existing left-mouse holds in Jaymo's Powers, or the engulfing key. */
	public enum Move {
		NONE(0), FROSTBIND(0), THUNDERCLAP(0), SUNFLARE(0), STRETCH(0), ENGULF(0),
		SHADOWMELD(45), TREMORSENSE(30), FORGE_BLESSING(60), SHIELD_WALL(40), RUNE_OF_WARDING(60), MOUNTAIN_LEAP(20), ENDER_STEP(20),
		FIRE_BREATH(30), FROST_BREATH(30), TERRA_QUAKE(30), TIDAL_BREATH(40), ROOT_SNARE(35), WAR_CRY(60), SECOND_WIND(60),
		ARCANE_OVERFLOW(75), STROKE_OF_FORTUNE(120), MESSENGERS_DASH(15), UNBREAKABLE_STAND(60), HALO_PULSE(45),
		FERAL_SPRINT(30), WEB_SNARE(25), WIND_BURST(25), HOWL(60), POUNCE(15), FOXFIRE(40), SHED_SKIN(60),
		GRAVE_CHILL(35), UNDYING_HUNGER(40), CONSCRIPT_THE_DEAD(60), SPECTRAL_PHASE(60), SOUL_HARVEST(30),
		VERDANT_BLOOM(45), MIRROR_SHIFT(20), PHASE_SHIFT(30), SIRENS_CHARM(45), FAERIE_BARGAIN(45),
		ARCANE_REFLEX(25), MOONLIT_VEIL(40), BLOOD_FRENZY(60);

		public final int cooldown;

		Move(int cooldown) {
			this.cooldown = cooldown;
		}

		public String key() {
			return name().toLowerCase(Locale.ROOT);
		}

		/** Held on left mouse in Jaymo's Powers (or the heritage key's own engulfing), not cast on the heritage key here. */
		public boolean existing() {
			return this == FROSTBIND || this == THUNDERCLAP || this == SUNFLARE || this == STRETCH || this == ENGULF;
		}
	}

	/** The passives and the weaknesses. {@code mods}: the plain attribute ones; the rest are done in the hooks below. */
	public enum Perk {
		NONE,
		// attributes (gifts, then flaws)
		LUCKY(Mod.add(Attributes.LUCK, 1.0)), SWIFT(Mod.share(Attributes.MOVEMENT_SPEED, 0.04)), STURDY(Mod.add(Attributes.KNOCKBACK_RESISTANCE, 0.25)),
		TOUGH(Mod.add(Attributes.ARMOR, 2.0)), FEATHERFALL(Mod.add(Attributes.SAFE_FALL_DISTANCE, 6.0)), GHOSTLY(Mod.add(Attributes.SAFE_FALL_DISTANCE, 20.0)),
		QUICK_HANDS(Mod.share(Attributes.ATTACK_SPEED, 0.10)), SNEAKY(Mod.share(Attributes.SNEAKING_SPEED, 0.3)),
		SOFT(Mod.add(Attributes.ARMOR, -1.0)), HEAVY_FOOTED(Mod.share(Attributes.MOVEMENT_SPEED, -0.04)), FRAIL(Mod.add(Attributes.MAX_HEALTH, -4.0)),
		HOLLOW_BONES(Mod.add(Attributes.MAX_HEALTH, -2.0)), GLASS_CANNON(Mod.add(Attributes.MAX_HEALTH, -6.0)), HOLLOW_SELF(Mod.add(Attributes.ARMOR, -3.0)),
		BRITTLE(Mod.add(Attributes.MAX_HEALTH, -2.0)), SPIRIT_FRAIL(Mod.add(Attributes.MAX_HEALTH, -4.0)),
		// hooks
		NIGHT_EYES, DEEP_EYES, MOONSIGHT, GLACIAL, ICE_SCALES, POISON_PROOF, VENOM, ARCHER, BLOODSCENT, SOUL_TAKER, NINE_LIVES, PHOTOSYNTH, AQUATIC,
		GOSSAMER, WARDED, UNDEAD_STOMACH,
		FIRE_FRAIL, TRIPLE_FIRE, COLD_IRON, SUN_WEAK, SUN_HOLY, SUN_DECAY, HATES_WATER, COLD_BLOODED, BOUND_TO_WATER, LANDBOUND, HEAVY_WATER,
		WET_GROUNDED, HALF_HEALING, LESS_HEALING, RAVENOUS, HUNGRY;

		public final Mod[] mods;

		Perk(Mod... mods) {
			this.mods = mods;
		}

		public String key() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	/** A kit: its ability, its gift, its flaw. */
	public record Kit(Move move, Perk gift, Perk flaw) {
		boolean has(Perk perk) {
			return this.gift == perk || this.flaw == perk;
		}
	}

	private static final Map<Heritage.Kind, Kit> KINDS = new EnumMap<>(Heritage.Kind.class);
	private static final Map<HeritageVariants.Variant, Kit> LOOKS = new EnumMap<>(HeritageVariants.Variant.class);

	private static void kind(Heritage.Kind kind, Move move, Perk gift, Perk flaw) {
		KINDS.put(kind, new Kit(move, gift, flaw));
	}

	private static void look(HeritageVariants.Variant look, Move move, Perk gift, Perk flaw) {
		LOOKS.put(look, new Kit(move, gift, flaw));
	}

	static {
		// the heritages (their nearest Runic race, docs/Races-Plan.md)
		kind(Heritage.Kind.HEARTLANDER, Move.STROKE_OF_FORTUNE, Perk.LUCKY, Perk.SOFT);
		kind(Heritage.Kind.STONEBORN, Move.SHIELD_WALL, Perk.NONE, Perk.NONE);
		kind(Heritage.Kind.TIDEBORN, Move.TIDAL_BREATH, Perk.NONE, Perk.LANDBOUND);
		kind(Heritage.Kind.SYLVAN, Move.ROOT_SNARE, Perk.NONE, Perk.NONE);
		kind(Heritage.Kind.VERDANT, Move.STRETCH, Perk.NONE, Perk.NONE);
		kind(Heritage.Kind.EMBERBORN, Move.FORGE_BLESSING, Perk.NONE, Perk.HEAVY_WATER);
		kind(Heritage.Kind.FROSTBORN, Move.FROSTBIND, Perk.GLACIAL, Perk.FIRE_FRAIL);
		kind(Heritage.Kind.NIGHTBORN, Move.SHADOWMELD, Perk.NIGHT_EYES, Perk.NONE);
		kind(Heritage.Kind.WARRIORBORN, Move.WAR_CRY, Perk.NONE, Perk.NONE);
		// (0.7.490) every heritage has a flaw: the Earthling's body needs more feeding than a blooded one's
		kind(Heritage.Kind.EARTHLING, Move.SECOND_WIND, Perk.NONE, Perk.HUNGRY);
		kind(Heritage.Kind.STORMBORN, Move.THUNDERCLAP, Perk.NONE, Perk.WET_GROUNDED);
		kind(Heritage.Kind.DAWNBORN, Move.SUNFLARE, Perk.NONE, Perk.NONE);
		kind(Heritage.Kind.ENDBORN, Move.ENDER_STEP, Perk.NONE, Perk.NONE);
		kind(Heritage.Kind.DRAGONBLOOD, Move.FIRE_BREATH, Perk.NONE, Perk.NONE);
		kind(Heritage.Kind.DEEPBORN, Move.TREMORSENSE, Perk.DEEP_EYES, Perk.NONE);
		// (0.7.490) the Divine-born never hunger, so theirs is a softer skin
		kind(Heritage.Kind.DIVINE, Move.HALO_PULSE, Perk.NONE, Perk.SOFT);
		kind(Heritage.Kind.BIOBUILT, Move.ENGULF, Perk.NONE, Perk.NONE);
		kind(Heritage.Kind.RUBBERFOLK, Move.ENGULF, Perk.NONE, Perk.NONE);
		kind(Heritage.Kind.BEASTFOLK, Move.FERAL_SPRINT, Perk.SWIFT, Perk.HUNGRY);
		kind(Heritage.Kind.HOLLOW, Move.GRAVE_CHILL, Perk.UNDEAD_STOMACH, Perk.LESS_HEALING);
		// the looks with a race of their own
		// (0.7.490) the look's own +3% speed is its gift: no second SWIFT on top
		look(HeritageVariants.Variant.SUNFRECKLED, Move.MESSENGERS_DASH, Perk.NONE, Perk.HUNGRY);
		look(HeritageVariants.Variant.SKYMARKED, Move.UNBREAKABLE_STAND, Perk.STURDY, Perk.HEAVY_FOOTED);
		look(HeritageVariants.Variant.OBSIDIAN, Move.RUNE_OF_WARDING, Perk.WARDED, Perk.NONE);
		look(HeritageVariants.Variant.MAGMAVEIN, Move.MOUNTAIN_LEAP, Perk.FEATHERFALL, Perk.NONE);
		// (0.7.490) the Terra drake's matching weakness: earth-heavy, slow on its feet
		look(HeritageVariants.Variant.GOLDSCALE, Move.TERRA_QUAKE, Perk.STURDY, Perk.HEAVY_FOOTED);
		look(HeritageVariants.Variant.RIMEWYRM, Move.FROST_BREATH, Perk.ICE_SCALES, Perk.FIRE_FRAIL);
		look(HeritageVariants.Variant.INKMARKED, Move.ARCANE_OVERFLOW, Perk.WARDED, Perk.FRAIL);
		look(HeritageVariants.Variant.DRYAD, Move.VERDANT_BLOOM, Perk.PHOTOSYNTH, Perk.TRIPLE_FIRE);
		look(HeritageVariants.Variant.CHANGELING, Move.MIRROR_SHIFT, Perk.SNEAKY, Perk.HOLLOW_SELF);
		look(HeritageVariants.Variant.SPRITE, Move.PHASE_SHIFT, Perk.GOSSAMER, Perk.GLASS_CANNON);
		look(HeritageVariants.Variant.NYMPH, Move.SIRENS_CHARM, Perk.AQUATIC, Perk.BOUND_TO_WATER);
		look(HeritageVariants.Variant.FAERIE, Move.FAERIE_BARGAIN, Perk.LUCKY, Perk.COLD_IRON);
		look(HeritageVariants.Variant.HIGHELF, Move.ARCANE_REFLEX, Perk.QUICK_HANDS, Perk.FRAIL);
		look(HeritageVariants.Variant.MOONELF, Move.MOONLIT_VEIL, Perk.MOONSIGHT, Perk.SUN_WEAK);
		look(HeritageVariants.Variant.BLOODELF, Move.BLOOD_FRENZY, Perk.BLOODSCENT, Perk.LESS_HEALING);
		look(HeritageVariants.Variant.ARACHNID, Move.WEB_SNARE, Perk.VENOM, Perk.FIRE_FRAIL);
		look(HeritageVariants.Variant.AVIAN, Move.WIND_BURST, Perk.FEATHERFALL, Perk.HOLLOW_BONES);
		look(HeritageVariants.Variant.CANINE, Move.HOWL, Perk.SWIFT, Perk.RAVENOUS);
		look(HeritageVariants.Variant.FELINE, Move.POUNCE, Perk.NINE_LIVES, Perk.HATES_WATER);
		look(HeritageVariants.Variant.KITSUNE, Move.FOXFIRE, Perk.LUCKY, Perk.SPIRIT_FRAIL);
		look(HeritageVariants.Variant.SERPEN, Move.SHED_SKIN, Perk.POISON_PROOF, Perk.COLD_BLOODED);
		look(HeritageVariants.Variant.ZOMBIE, Move.UNDYING_HUNGER, Perk.TOUGH, Perk.SUN_DECAY);
		look(HeritageVariants.Variant.SKELETON, Move.CONSCRIPT_THE_DEAD, Perk.ARCHER, Perk.BRITTLE);
		look(HeritageVariants.Variant.WRAITH, Move.SPECTRAL_PHASE, Perk.GHOSTLY, Perk.SUN_HOLY);
		look(HeritageVariants.Variant.REAPER, Move.SOUL_HARVEST, Perk.SOUL_TAKER, Perk.HALF_HEALING);
	}

	/** Static attributes the kits touch (each gets a modifier per operation, set or taken off together). */
	private static final List<Holder<Attribute>> TRACKED = List.of(Attributes.LUCK, Attributes.MOVEMENT_SPEED, Attributes.KNOCKBACK_RESISTANCE,
		Attributes.ARMOR, Attributes.SAFE_FALL_DISTANCE, Attributes.ATTACK_SPEED, Attributes.SNEAKING_SPEED, Attributes.MAX_HEALTH);

	/**
	 * Per player: when the ability is ready again (game time), and the windows the abilities open. (0.7.490) {@code READY} and
	 * {@code LIVED} outlast a logout, so a relog doesn't reset a cooldown.
	 */
	private static final Map<UUID, Long> READY = new HashMap<>();
	private static final Map<UUID, Long> PHASED = new HashMap<>(), REFLEX = new HashMap<>(), POUNCED = new HashMap<>(), LIVED = new HashMap<>(), LAST_WET = new HashMap<>();
	private static final Map<UUID, long[]> STEAL = new HashMap<>();
	private static final Map<UUID, Float> HEALTH = new HashMap<>();
	/** The risen skeletons (id → its caller and the game time it falls) and where to find them. */
	private record Conscript(ServerLevel level, UUID owner, long until) {
	}

	private static final Map<UUID, Conscript> CONSCRIPTS = new HashMap<>();
	/** (0.7.490) The scoreboard tag on a risen skeleton, so one that outlives its entry is found and dropped when it loads. */
	private static final String CONSCRIPT_TAG = "jaymoskingdoms_conscript";

	private HeritageKits() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(HeritageKits::tickAll);
		// phased: nothing touches them; a risen skeleton never hurts a player
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (entity instanceof ServerPlayer player && phased(player) && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
				return false;
			}
			Entity by = source.getEntity();
			return !(entity instanceof Player && by != null && CONSCRIPTS.containsKey(by.getUUID()));
		});
		// (0.7.490) venom, the pounce and lifesteal: only for a blow that got through
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			landed(entity, source, taken, blocked);
			reflexed(entity, source, taken, blocked);
		});
		// Nine Lives: the Feline gets up at one health, now and then
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			// (0.7.490) a risen skeleton cut down crumbles: no bones, arrows or experience for its caller to farm
			if (entity.entityTags().contains(CONSCRIPT_TAG)) {
				dismiss(entity.getUUID());
				CONSCRIPTS.remove(entity.getUUID());
				entity.discard();
				return false;
			}
			return !(entity instanceof ServerPlayer player) || !nineLives(player, source);
		});
		// Soul Taker: a kill mends the Reaper
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer dead) {
				// (a new life starts at full health: that is not a mend to cut)
				HEALTH.remove(dead.getUUID());
			} else {
				// (0.7.490) a dead villager's dry count goes with it
				LAST_WET.remove(entity.getUUID());
			}
			if (source.getEntity() instanceof LivingEntity killer && killer != entity && killer.isAlive()) {
				Kit kit = kit(killer);
				if (kit != null && kit.has(Perk.SOUL_TAKER)) {
					killer.heal(SOUL_TAKER_HEAL);
					if (killer.level() instanceof ServerLevel level) {
						level.sendParticles(ParticleTypes.SOUL, killer.getX(), killer.getY(0.6), killer.getZ(), 6, 0.3, 0.4, 0.3, 0.02);
					}
				}
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			UUID id = handler.player.getUUID();
			// (0.7.490) READY and LIVED stay: cooldowns run on through a relog
			PHASED.remove(id);
			REFLEX.remove(id);
			POUNCED.remove(id);
			LAST_WET.remove(id);
			STEAL.remove(id);
			HEALTH.remove(id);
		});
		// (0.7.490) an unloaded villager's dry count is dropped (a player's is kept until they log off)
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (!(entity instanceof ServerPlayer)) {
				LAST_WET.remove(entity.getUUID());
			}
		});
		// (0.7.490) a risen skeleton left behind in an unloaded chunk (or saved over a restart) falls when it loads again
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity.entityTags().contains(CONSCRIPT_TAG) && !CONSCRIPTS.containsKey(entity.getUUID())) {
				entity.discard();
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (UUID id : new ArrayList<>(CONSCRIPTS.keySet())) {
				dismiss(id);
			}
			CONSCRIPTS.clear();
		});
	}

	// ---- whose kit ---------------------------------------------------------------------------------------------------

	/** The kit of {@code entity} (a player's or a villager's heritage and look), or null: no heritage, or a brood. */
	public static @Nullable Kit kit(Entity entity) {
		Heritage.Kind kind = VillagerHeritage.of(entity);
		if (kind == null) {
			return null;
		}
		HeritageVariants.Variant look = HeritageVariants.of(entity);
		Kit own = look == null || look.heritage != kind ? null : LOOKS.get(look);
		return own != null ? own : KINDS.get(kind);
	}

	/** The kit of {@code kind}'s look {@code look} (null: the first look), for the heritage card. */
	public static @Nullable Kit kit(Heritage.Kind kind, HeritageVariants.@Nullable Variant look) {
		Kit own = look == null || look.heritage != kind ? null : LOOKS.get(look);
		return own != null ? own : KINDS.get(kind);
	}

	/** The card rows of a kit: its ability, its gift, its flaw (keys end in {@code .move}, {@code .gift}, {@code .flaw}). */
	public static List<Component> card(@Nullable Kit kit) {
		List<Component> out = new ArrayList<>();
		if (kit == null) {
			return out;
		}
		if (kit.move != Move.NONE) {
			String how = kit.move == Move.ENGULF ? "engulf" : kit.move == Move.WAR_CRY ? "sneak" : kit.move.existing() ? "hold" : "key";
			out.add(Component.translatable("kit.jaymoskingdoms.card.move." + how, Component.translatable("kit.jaymoskingdoms.move." + kit.move.key()),
				Component.translatable("kit.jaymoskingdoms.move." + kit.move.key() + ".desc")));
		}
		if (kit.gift != Perk.NONE) {
			out.add(Component.translatable("kit.jaymoskingdoms.card.gift", Component.translatable("kit.jaymoskingdoms.perk." + kit.gift.key())));
		}
		if (kit.flaw != Perk.NONE) {
			out.add(Component.translatable("kit.jaymoskingdoms.card.flaw", Component.translatable("kit.jaymoskingdoms.perk." + kit.flaw.key())));
		}
		return out;
	}

	// ---- attributes (players and villagers) -------------------------------------------------------------------------

	/** Puts the kit's plain attribute changes on {@code entity}, and takes any other's off. */
	public static void attributes(LivingEntity entity) {
		Kit kit = kit(entity);
		for (Holder<Attribute> attribute : TRACKED) {
			for (AttributeModifier.Operation op : new AttributeModifier.Operation[] {AttributeModifier.Operation.ADD_VALUE, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL}) {
				double amount = 0.0;
				if (kit != null) {
					for (Perk perk : new Perk[] {kit.gift, kit.flaw}) {
						for (Mod m : perk.mods) {
							if (m.attribute().equals(attribute) && m.operation() == op) {
								amount += m.amount();
							}
						}
					}
				}
				put(entity, attribute, KIT_ID + path(attribute) + (op == AttributeModifier.Operation.ADD_VALUE ? "_add" : "_share"), amount, op);
			}
		}
		if (entity.getHealth() > entity.getMaxHealth()) {
			entity.setHealth(entity.getMaxHealth());
		}
	}

	private static String path(Holder<Attribute> attribute) {
		return attribute.unwrapKey().map(k -> k.identifier().getPath()).orElse("x").replace('.', '_');
	}

	private static void put(LivingEntity entity, Holder<Attribute> attribute, String id, double amount, AttributeModifier.Operation op) {
		AttributeInstance instance = entity.getAttribute(attribute);
		if (instance == null) {
			return;
		}
		Identifier key = JaymosKingdoms.id(id);
		if (amount == 0.0) {
			instance.removeModifier(key);
		} else {
			instance.addOrUpdateTransientModifier(new AttributeModifier(key, amount, op));
		}
	}

	// ---- places ---------------------------------------------------------------------------------------------------------

	private static boolean sun(Entity e) {
		Level level = e.level();
		BlockPos pos = e.blockPosition().above();
		return level.isBrightOutside() && level.canSeeSky(pos) && !level.isRainingAt(pos);
	}

	private static boolean dark(Entity e) {
		Level level = e.level();
		BlockPos pos = e.blockPosition();
		return level.getMaxLocalRawBrightness(pos) <= Heritage.DARK_LIGHT || level.isDarkOutside() && level.canSeeSky(pos);
	}

	private static boolean night(Entity e) {
		return e.level().isDarkOutside() && e.level().canSeeSky(e.blockPosition());
	}

	private static boolean cold(Entity e) {
		return e.level().getBiome(e.blockPosition()).value().getBaseTemperature() <= Heritage.COLD;
	}

	private static boolean wet(Entity e) {
		return e instanceof ServerPlayer player ? Heritage.wet(player) : e.isInWaterOrRain();
	}

	private static boolean phased(ServerPlayer player) {
		Long until = PHASED.get(player.getUUID());
		return until != null && player.level().getGameTime() < until;
	}

	// ---- every second ---------------------------------------------------------------------------------------------------

	private static void tickAll(MinecraftServer server) {
		int tick = server.getTickCount();
		if (tick % GOSSAMER_EVERY == 0) {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				Kit kit = kit(player);
				if (kit != null && kit.has(Perk.GOSSAMER) && !player.onGround() && player.isShiftKeyDown() && !player.isInWater()) {
					player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, GOSSAMER_TICKS, 0, true, false, false));
				}
			}
		}
		if (tick % Heritage.TICK_EVERY == 0 && !CONSCRIPTS.isEmpty()) {
			conscripts(server);
		}
	}

	/**
	 * Called every second from {@code Heritage.tick} for each player and from {@code VillagerHeritage.tick} for each villager:
	 * the weaknesses and passives that depend on where they stand, and (a player's) mending and sight.
	 */
	public static void tick(LivingEntity entity, int serverTick) {
		Kit kit = kit(entity);
		dynamics(entity, kit);
		if (kit == null) {
			if (entity instanceof ServerPlayer player) {
				HEALTH.remove(player.getUUID());
			}
			return;
		}
		if (kit.has(Perk.ICE_SCALES) && entity.getTicksFrozen() > 0) {
			entity.setTicksFrozen(0);
		}
		if (kit.has(Perk.POISON_PROOF) || kit.has(Perk.UNDEAD_STOMACH)) {
			entity.removeEffect(MobEffects.POISON);
			if (kit.has(Perk.UNDEAD_STOMACH)) {
				entity.removeEffect(MobEffects.HUNGER);
			}
		}
		if (!(entity instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
			return;
		}
		// (the eyes' gifts, night eyes, deep eyes and moonsight, are darkvision now: grey shapes out to 16 blocks, seen on the player's
		// own screen alone, see Darkvision; no Night Vision is given)
		if (kit.has(Perk.AQUATIC) && player.isInWater()) {
			player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, Heritage.TICK_EVERY * 3, 0, true, false, false));
			player.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, Heritage.TICK_EVERY * 3, 0, true, false, false));
		}
		if (kit.has(Perk.PHOTOSYNTH) && serverTick % PHOTO_EVERY == 0 && sun(player) && player.getHealth() < player.getMaxHealth()) {
			player.heal(PHOTO_HEAL);
		}
		if (kit.has(Perk.SUN_DECAY) && serverTick % SUN_DECAY_EVERY == 0 && sun(player) && player.getItemBySlot(EquipmentSlot.HEAD).isEmpty()
			&& !player.isCreative() && !player.isSpectator()) {
			level.sendParticles(ParticleTypes.SMOKE, player.getX(), player.getY(1.0), player.getZ(), 6, 0.3, 0.5, 0.3, 0.02);
			player.hurtServer(level, level.damageSources().generic(), SUN_DECAY_DAMAGE);
		}
		if (player.isInWaterOrRain()) {
			LAST_WET.put(player.getUUID(), level.getGameTime());
		}
		healing(player, kit);
	}

	/** The shares off blows and speed, and off swimming, that change with the weather and the land: set, or taken off, together. */
	private static void dynamics(LivingEntity entity, @Nullable Kit kit) {
		double damage = 0.0, speed = 0.0;
		if (kit != null) {
			boolean sunny = kit.has(Perk.SUN_WEAK) || kit.has(Perk.SUN_HOLY) ? sun(entity) : false;
			if (kit.has(Perk.SUN_WEAK) && sunny) {
				damage -= SUN_WEAK_DAMAGE;
			}
			if (kit.has(Perk.SUN_HOLY) && sunny) {
				damage -= SUN_HOLY_DAMAGE;
				speed -= SUN_HOLY_SPEED;
			}
			if (kit.has(Perk.HATES_WATER) && entity.isInWaterOrRain()) {
				damage -= WET_DAMAGE;
				speed -= WET_SPEED;
			}
			if (kit.has(Perk.COLD_BLOODED) && cold(entity)) {
				damage -= COLD_BLOOD_DAMAGE;
				speed -= COLD_BLOOD_SPEED;
			}
			if (kit.has(Perk.BOUND_TO_WATER) && dry(entity)) {
				damage -= DRY_DAMAGE;
				speed -= DRY_SPEED;
			}
			if (kit.has(Perk.LANDBOUND) && !wet(entity)) {
				speed -= LAND_SPEED;
			}
			if (kit.has(Perk.HEAVY_WATER) && entity.isInWater()) {
				// (0.7.490) wading; the swim itself is slowed in travelInWater ({@link #heavyInWater})
				speed -= HEAVY_SPEED;
			}
			if (kit.has(Perk.WET_GROUNDED) && entity.isInWater()) {
				damage -= GROUNDED_DAMAGE;
			}
			if (kit.has(Perk.MOONSIGHT) && night(entity)) {
				speed += MOON_SPEED;
			}
		}
		put(entity, Attributes.ATTACK_DAMAGE, KIT_ID + "place_damage", damage, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		put(entity, Attributes.MOVEMENT_SPEED, KIT_ID + "place_speed", speed, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
	}

	/** (0.7.500) Whether {@code entity} is cold-blooded (the Serpen): Kitchen's body heat never overheats them ({@link KitchenCompat}). */
	public static boolean coldBlooded(LivingEntity entity) {
		Kit kit = kit(entity);
		return kit != null && kit.has(Perk.COLD_BLOODED);
	}

	/**
	 * (0.7.490) Whether {@code entity} is heavy in water (the Emberborn's flaw): read on both sides, from the synced heritage, by
	 * {@code mixin.heritage.LivingEntityHeavyWaterMixin} after each tick's swim.
	 */
	public static boolean heavyInWater(LivingEntity entity) {
		Kit kit = kit(entity);
		return kit != null && kit.has(Perk.HEAVY_WATER);
	}

	/** A bound-to-water one who's been out of water and rain for {@link #DRY_LIMIT_TICKS} (a villager's count starts when it's loaded). */
	private static boolean dry(LivingEntity entity) {
		if (entity.isInWaterOrRain()) {
			// (0.7.490) wetting restarts the count for villagers too, not only players
			LAST_WET.put(entity.getUUID(), entity.level().getGameTime());
			return false;
		}
		Long last = LAST_WET.get(entity.getUUID());
		if (last == null) {
			LAST_WET.put(entity.getUUID(), entity.level().getGameTime());
			return false;
		}
		return entity.level().getGameTime() - last > DRY_LIMIT_TICKS;
	}

	/** Healing taken at less than its worth (the Reaper's, the Blood elf's, the Hollow's): the gain since last second is cut. */
	private static void healing(ServerPlayer player, Kit kit) {
		float factor = kit.has(Perk.HALF_HEALING) ? HALF_HEALING : kit.has(Perk.LESS_HEALING) ? LESS_HEALING : 1.0F;
		Float last = HEALTH.get(player.getUUID());
		float now = player.getHealth();
		if (factor < 1.0F && last != null && player.isAlive() && now > last + 0.05F && now < player.getMaxHealth() + 0.001F) {
			now = last + (now - last) * factor;
			player.setHealth(now);
		}
		HEALTH.put(player.getUUID(), now);
	}

	/** After a blow, so the damage taken isn't read as healing's loss next second. */
	static void settle(ServerPlayer player) {
		if (HEALTH.containsKey(player.getUUID())) {
			HEALTH.put(player.getUUID(), player.getHealth());
		}
	}

	// ---- hunger -----------------------------------------------------------------------------------------------------------

	/** The factor on {@code player}'s hunger drain from their kit. */
	static float hunger(ServerPlayer player) {
		Kit kit = kit(player);
		if (kit == null) {
			return 1.0F;
		}
		float f = 1.0F;
		if (kit.has(Perk.RAVENOUS)) {
			f *= RAVENOUS_HUNGER;
		}
		if (kit.has(Perk.HUNGRY)) {
			f *= HUNGRY_HUNGER;
		}
		if (kit.has(Perk.UNDEAD_STOMACH)) {
			f *= STOMACH_HUNGER;
		}
		if (kit.has(Perk.BOUND_TO_WATER) && dry(player)) {
			f *= DRY_HUNGER;
		}
		return f;
	}

	// ---- blows -------------------------------------------------------------------------------------------------------------

	/** What {@code target} takes from {@code source} given its kit's flaws and gifts (from {@code Heritage.dealt}). */
	static float taken(LivingEntity target, DamageSource source, float amount) {
		Kit kit = kit(target);
		if (kit == null) {
			return amount;
		}
		boolean fire = source.is(DamageTypeTags.IS_FIRE);
		boolean magic = source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC);
		if (fire && kit.has(Perk.FIRE_FRAIL)) {
			amount *= FIRE_FRAIL_TAKEN;
		}
		if (fire && kit.has(Perk.TRIPLE_FIRE)) {
			amount *= TRIPLE_FIRE_TAKEN;
		}
		if (kit.has(Perk.COLD_IRON) && ironBlow(source)) {
			amount *= COLD_IRON_TAKEN;
		}
		if (magic && kit.has(Perk.WARDED)) {
			amount *= WARDED_TAKEN;
		}
		if (magic && kit.has(Perk.SPIRIT_FRAIL)) {
			amount *= SPIRIT_MAGIC_TAKEN;
		}
		if (kit.has(Perk.GLACIAL) && (cold(target) || target.getTicksFrozen() > 0)) {
			amount *= GLACIAL_TAKEN;
		}
		if (kit.has(Perk.HOLLOW_BONES)) {
			amount *= BONES_TAKEN;
		}
		if (kit.has(Perk.GLASS_CANNON)) {
			amount *= GLASS_TAKEN;
		}
		if (kit.has(Perk.BRITTLE) && (source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypeTags.IS_EXPLOSION))) {
			amount *= BRITTLE_TAKEN;
		}
		if (kit.has(Perk.SUN_HOLY) && source.getEntity() != null && holy(source.getEntity())) {
			amount *= HOLY_TAKEN;
		}
		if (target instanceof ServerPlayer player) {
			Long until = REFLEX.get(player.getUUID());
			if (until != null && player.level().getGameTime() < until) {
				// (0.7.490) only the factor here: the shove is for a blow that lands (reflexed)
				amount *= REFLEX_TAKEN;
			}
		}
		return amount;
	}

	/** A blow struck by {@code striker} at {@code target}: its kit's gifts and flaws, and the windows its abilities opened. */
	static float dealt(LivingEntity striker, LivingEntity target, DamageSource source, float amount) {
		Kit kit = kit(striker);
		if (kit == null) {
			return amount;
		}
		if (kit.has(Perk.GLASS_CANNON)) {
			amount *= GLASS_DEALT;
		}
		if (kit.has(Perk.BLOODSCENT) && target.getHealth() < target.getMaxHealth() * 0.5F) {
			amount *= BLOODSCENT_DEALT;
		}
		if (kit.has(Perk.ARCHER) && source.is(DamageTypeTags.IS_PROJECTILE)) {
			amount *= ARCHER_DEALT;
		}
		if (melee(striker, target, source)) {
			// (0.7.490) only the factor here: the pounce is spent by a blow that lands (landed)
			Long pounce = POUNCED.get(striker.getUUID());
			if (pounce != null && striker.level().getGameTime() < pounce) {
				amount *= POUNCE_DAMAGE;
			}
		}
		return amount;
	}

	private static boolean melee(LivingEntity striker, LivingEntity target, DamageSource source) {
		return striker != target && source.getEntity() == source.getDirectEntity() && source.getDirectEntity() == striker;
	}

	/**
	 * (0.7.490) A blow by {@code striker} that {@code target} has actually taken ({@code taken} after armour, past the
	 * invulnerable ticks and the shield): the venom, the pounce spent, and the lifesteal on what got through.
	 */
	private static void landed(LivingEntity target, DamageSource source, float taken, boolean blocked) {
		if (blocked || taken <= 0.0F || !(source.getEntity() instanceof LivingEntity striker) || !melee(striker, target, source)) {
			return;
		}
		Kit kit = kit(striker);
		if (kit == null) {
			return;
		}
		if (kit.has(Perk.VENOM) && target.isAlive()) {
			target.addEffect(new MobEffectInstance(MobEffects.POISON, VENOM_TICKS, 0), striker);
		}
		POUNCED.remove(striker.getUUID());
		long[] steal = STEAL.get(striker.getUUID());
		if (steal != null && striker.level().getGameTime() < steal[0] && striker.isAlive()) {
			striker.heal(taken * steal[1] / 100.0F);
		}
	}

	/** (0.7.490) Arcane Reflex: a blow that got through inside the window throws its striker back. */
	private static void reflexed(LivingEntity target, DamageSource source, float taken, boolean blocked) {
		if (blocked || taken <= 0.0F || !(target instanceof ServerPlayer player)) {
			return;
		}
		Long until = REFLEX.get(player.getUUID());
		if (until == null || player.level().getGameTime() >= until || !(source.getEntity() instanceof LivingEntity striker) || striker == player) {
			return;
		}
		Vec3 away = striker.position().subtract(player.position()).multiply(1.0, 0.0, 1.0);
		away = away.lengthSqr() < 1.0E-4 ? Vec3.ZERO : away.normalize().scale(REFLEX_SHOVE);
		striker.setDeltaMovement(striker.getDeltaMovement().add(away.x, REFLEX_LIFT, away.z));
		striker.syncVelocity = true;
	}

	private static boolean ironBlow(DamageSource source) {
		Entity by = source.getEntity();
		if (by instanceof IronGolem) {
			return true;
		}
		if (by instanceof LivingEntity living) {
			Identifier id = BuiltInRegistries.ITEM.getKey(living.getMainHandItem().getItem());
			return id.getPath().startsWith("iron_");
		}
		return false;
	}

	/** The holy: the Dawnborn and the Divine-born (a blow in their hand, or a Dawnborn's burning sun). */
	private static boolean holy(Entity by) {
		Heritage.Kind kind = VillagerHeritage.of(by);
		return kind == Heritage.Kind.DAWNBORN || kind == Heritage.Kind.DIVINE;
	}

	private static boolean nineLives(ServerPlayer player, DamageSource source) {
		Kit kit = kit(player);
		if (kit == null || !kit.has(Perk.NINE_LIVES) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return false;
		}
		long now = player.level().getGameTime();
		Long lived = LIVED.get(player.getUUID());
		if (lived != null && now - lived < NINE_LIVES_COOLDOWN && now >= lived) {
			return false;
		}
		LIVED.put(player.getUUID(), now);
		player.setHealth(1.0F);
		player.removeAllEffects();
		fx(player, MobEffects.REGENERATION, 6, 1);
		fx(player, MobEffects.ABSORPTION, 10, 1);
		fx(player, MobEffects.RESISTANCE, 4, 1);
		if (player.level() instanceof ServerLevel level) {
			level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY(0.5), player.getZ(), 24, 0.4, 0.6, 0.4, 0.3);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.6F, 1.4F);
		}
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.kit.nine_lives").withStyle(ChatFormatting.GOLD), true);
		return true;
	}

	// ---- the ability key ------------------------------------------------------------------------------------------------

	/** The heritage key was pressed: cast {@code player}'s ability (if it has one on this key and is ready). */
	static void press(ServerPlayer player) {
		Kit kit = kit(player);
		if (kit == null || kit.move == Move.NONE || !player.isAlive() || player.isSpectator() || !(player.level() instanceof ServerLevel level)) {
			return;
		}
		Move move = kit.move;
		if (move.existing()) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.kit.held", Component.translatable("kit.jaymoskingdoms.move." + move.key()))
				.withStyle(ChatFormatting.GRAY), true);
			return;
		}
		long now = level.getGameTime();
		Long ready = READY.get(player.getUUID());
		if (ready != null && now < ready && ready - now <= move.cooldown * 20L) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.kit.cooldown", Component.translatable("kit.jaymoskingdoms.move." + move.key()),
				(ready - now + 19) / 20).withStyle(ChatFormatting.GRAY), true);
			return;
		}
		if (cast(player, level, move, now)) {
			READY.put(player.getUUID(), now + move.cooldown * 20L);
			settle(player);
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.kit.cast", Component.translatable("kit.jaymoskingdoms.move." + move.key()))
				.withStyle(ChatFormatting.GOLD), true);
		}
	}

	private static boolean cast(ServerPlayer p, ServerLevel level, Move move, long now) {
		switch (move) {
			case SHADOWMELD -> {
				fx(p, MobEffects.INVISIBILITY, SHADOWMELD_SECONDS, 0);
				fx(p, MobEffects.SPEED, SHADOWMELD_SECONDS, 0);
				calm(p, 16.0);
				burst(p, ParticleTypes.SMOKE, 24);
				sound(p, SoundEvents.ENDERMAN_TELEPORT, 0.5F, 0.6F);
			}
			case TREMORSENSE -> {
				for (LivingEntity e : around(p, TREMOR_RANGE, e -> true)) {
					fx(e, MobEffects.GLOWING, TREMOR_SECONDS, 0);
				}
				ring(p, ParticleTypes.SCULK_SOUL, 20);
				sound(p, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.8F, 0.5F);
			}
			case FORGE_BLESSING -> {
				ItemStack held = p.getMainHandItem();
				if (held.isDamageableItem() && held.getDamageValue() > 0) {
					held.setDamageValue(Math.max(0, held.getDamageValue() - Math.round(held.getMaxDamage() * FORGE_MEND)));
				}
				fx(p, MobEffects.HASTE, FORGE_SECONDS, 1);
				fx(p, MobEffects.STRENGTH, FORGE_SECONDS, 0);
				burst(p, ParticleTypes.FLAME, 24);
				sound(p, SoundEvents.FIRECHARGE_USE, 0.8F, 0.8F);
			}
			case SHIELD_WALL -> {
				fx(p, MobEffects.RESISTANCE, WALL_SECONDS, 1);
				fx(p, MobEffects.SLOWNESS, WALL_SECONDS, 0);
				burst(p, ParticleTypes.CRIT, 20);
				sound(p, SoundEvents.SHIELD_BLOCK.value(), 1.0F, 0.7F);
			}
			case RUNE_OF_WARDING -> {
				fx(p, MobEffects.ABSORPTION, WARD_SECONDS, 2);
				fx(p, MobEffects.RESISTANCE, WARD_SECONDS, 0);
				burst(p, Fx.GLYPH, 40);
				sound(p, SoundEvents.EVOKER_CAST_SPELL, 0.8F, 1.2F);
			}
			case MOUNTAIN_LEAP -> {
				Vec3 look = flat(p);
				launch(p, look.x * LEAP_FORWARD, LEAP_UP, look.z * LEAP_FORWARD);
				fx(p, MobEffects.SLOW_FALLING, 4, 0);
				burst(p, ParticleTypes.POOF, 16);
			}
			case ENDER_STEP -> {
				if (!step(p, level)) {
					return false;
				}
			}
			case FIRE_BREATH -> {
				breath(p, level, ParticleTypes.FLAME, FIRE_BREATH_DAMAGE, e -> e.igniteForSeconds(FIRE_BREATH_BURN));
				sound(p, SoundEvents.FIRECHARGE_USE, 1.0F, 0.6F);
			}
			case FROST_BREATH -> {
				breath(p, level, ParticleTypes.SNOWFLAKE, FROST_BREATH_DAMAGE, e -> {
					fx(e, MobEffects.SLOWNESS, FROST_BREATH_SECONDS, 2);
					e.setTicksFrozen(e.getTicksRequiredToFreeze() + 40);
				});
				sound(p, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), 1.0F, 0.6F);
			}
			case TERRA_QUAKE -> {
				for (LivingEntity e : around(p, QUAKE_RANGE, e -> foe(p, e))) {
					e.hurtServer(level, level.damageSources().playerAttack(p), QUAKE_DAMAGE);
					e.setDeltaMovement(e.getDeltaMovement().add(0.0, 0.5, 0.0));
					e.syncVelocity = true;
					fx(e, MobEffects.SLOWNESS, 3, 1);
				}
				ring(p, ParticleTypes.POOF, 24);
				sound(p, SoundEvents.GENERIC_EXPLODE.value(), 0.7F, 0.6F);
			}
			case TIDAL_BREATH -> {
				p.setAirSupply(p.getMaxAirSupply());
				p.heal(TIDAL_HEAL);
				p.clearFire();
				fx(p, MobEffects.DOLPHINS_GRACE, TIDAL_SECONDS, 0);
				fx(p, MobEffects.WATER_BREATHING, TIDAL_SECONDS, 0);
				burst(p, ParticleTypes.BUBBLE_POP, 30);
				sound(p, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.8F, 1.6F);
			}
			case ROOT_SNARE -> {
				for (LivingEntity e : around(p, SNARE_RANGE, e -> foe(p, e))) {
					fx(e, MobEffects.SLOWNESS, SNARE_SECONDS, 4);
					burst(e, ParticleTypes.HAPPY_VILLAGER, 8);
				}
				ring(p, ParticleTypes.HAPPY_VILLAGER, 24);
			}
			case WAR_CRY -> {
				for (LivingEntity e : around(p, CRY_RANGE, e -> e instanceof Player)) {
					fx(e, MobEffects.STRENGTH, CRY_SECONDS, 0);
					fx(e, MobEffects.SPEED, CRY_SECONDS, 0);
				}
				fx(p, MobEffects.STRENGTH, CRY_SECONDS, 0);
				fx(p, MobEffects.SPEED, CRY_SECONDS, 0);
				ring(p, ParticleTypes.ANGRY_VILLAGER, 16);
				sound(p, SoundEvents.RAVAGER_ROAR, 1.0F, 1.0F);
			}
			case SECOND_WIND -> {
				p.heal(WIND_HEAL);
				fx(p, MobEffects.REGENERATION, 5, 0);
				p.removeEffect(MobEffects.SLOWNESS);
				p.removeEffect(MobEffects.WEAKNESS);
				burst(p, ParticleTypes.HAPPY_VILLAGER, 16);
				sound(p, SoundEvents.PLAYER_LEVELUP, 0.6F, 1.2F);
			}
			case ARCANE_OVERFLOW -> {
				fx(p, MobEffects.HASTE, 15, 1);
				fx(p, MobEffects.REGENERATION, 10, 0);
				fx(p, MobEffects.LUCK, 30, 0);
				burst(p, Fx.GLYPH, 50);
				sound(p, SoundEvents.EVOKER_CAST_SPELL, 0.8F, 1.0F);
			}
			case STROKE_OF_FORTUNE -> {
				fx(p, MobEffects.LUCK, 90, 2);
				fx(p, MobEffects.HASTE, 30, 0);
				burst(p, ParticleTypes.HAPPY_VILLAGER, 24);
				sound(p, SoundEvents.PLAYER_LEVELUP, 0.7F, 1.6F);
			}
			case MESSENGERS_DASH -> {
				Vec3 look = flat(p);
				launch(p, look.x * DASH, 0.15, look.z * DASH);
				fx(p, MobEffects.SPEED, 4, 1);
				burst(p, ParticleTypes.POOF, 12);
			}
			case UNBREAKABLE_STAND -> {
				fx(p, MobEffects.RESISTANCE, 5, 2);
				fx(p, MobEffects.SLOWNESS, 5, 0);
				p.setDeltaMovement(Vec3.ZERO);
				burst(p, ParticleTypes.CRIT, 24);
				sound(p, SoundEvents.SHIELD_BLOCK.value(), 1.0F, 0.5F);
			}
			case HALO_PULSE -> {
				for (LivingEntity e : around(p, HALO_RANGE, e -> e instanceof Player)) {
					e.heal(HALO_HEAL);
				}
				p.heal(HALO_HEAL);
				for (LivingEntity e : around(p, HALO_SMITE_RANGE, e -> e.is(EntityTypeTags.UNDEAD))) {
					e.hurtServer(level, level.damageSources().playerAttack(p), HALO_SMITE);
				}
				ring(p, Fx.LIGHT, 28);
				sound(p, SoundEvents.BEACON_ACTIVATE, 0.8F, 1.4F);
			}
			case FERAL_SPRINT -> {
				fx(p, MobEffects.SPEED, 6, 2);
				fx(p, MobEffects.JUMP_BOOST, 6, 0);
				burst(p, ParticleTypes.POOF, 14);
			}
			case WEB_SNARE -> {
				LivingEntity target = Arcana.sighted(p);
				if (target == null || target.distanceTo(p) > WEB_RANGE) {
					target = nearest(p, WEB_RANGE, e -> foe(p, e));
				}
				if (target == null) {
					p.sendSystemMessage(Component.translatable("message.jaymoskingdoms.kit.nothing").withStyle(ChatFormatting.GRAY), true);
					return false;
				}
				fx(target, MobEffects.SLOWNESS, WEB_SECONDS, 5);
				fx(target, MobEffects.WEAKNESS, WEB_SECONDS, 0);
				burst(target, ParticleTypes.ITEM_COBWEB, 30);
				sound(p, SoundEvents.SPIDER_AMBIENT, 0.8F, 1.4F);
			}
			case WIND_BURST -> {
				launch(p, p.getDeltaMovement().x, WIND_UP, p.getDeltaMovement().z);
				fx(p, MobEffects.SLOW_FALLING, WIND_SECONDS, 0);
				ring(p, ParticleTypes.POOF, 16);
				sound(p, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), 0.9F, 1.2F);
			}
			case HOWL -> {
				for (LivingEntity e : around(p, HOWL_RANGE, e -> e instanceof Player)) {
					fx(e, MobEffects.STRENGTH, CRY_SECONDS, 0);
					fx(e, MobEffects.SPEED, CRY_SECONDS, 0);
				}
				for (LivingEntity e : around(p, WOLF_RANGE, e -> e instanceof Wolf)) {
					fx(e, MobEffects.STRENGTH, CRY_SECONDS + 5, 1);
					fx(e, MobEffects.SPEED, CRY_SECONDS + 5, 1);
				}
				fx(p, MobEffects.STRENGTH, CRY_SECONDS, 0);
				fx(p, MobEffects.SPEED, CRY_SECONDS, 0);
				ring(p, ParticleTypes.NOTE, 12);
				sound(p, SoundEvents.ENDER_DRAGON_GROWL, 0.8F, 1.7F);
			}
			case POUNCE -> {
				Vec3 look = p.getLookAngle();
				launch(p, look.x * POUNCE_FORWARD, POUNCE_UP, look.z * POUNCE_FORWARD);
				POUNCED.put(p.getUUID(), now + POUNCE_TICKS);
				burst(p, ParticleTypes.CRIT, 10);
			}
			case FOXFIRE -> {
				for (LivingEntity e : around(p, FOXFIRE_RANGE, e -> e instanceof Mob)) {
					if (e instanceof Mob mob && mob.getTarget() == p) {
						mob.setTarget(null);
					}
					if (foe(p, e)) {
						fx(e, MobEffects.BLINDNESS, 3, 0);
					}
				}
				fx(p, MobEffects.SPEED, 5, 0);
				ring(p, ParticleTypes.SOUL_FIRE_FLAME, 24);
				sound(p, SoundEvents.EVOKER_CAST_SPELL, 0.7F, 1.6F);
			}
			case SHED_SKIN -> {
				for (MobEffectInstance e : new ArrayList<>(p.getActiveEffects())) {
					if (!e.getEffect().value().isBeneficial()) {
						p.removeEffect(e.getEffect());
					}
				}
				p.heal(4.0F);
				p.clearFire();
				fx(p, MobEffects.RESISTANCE, 4, 0);
				burst(p, ParticleTypes.POOF, 20);
			}
			case GRAVE_CHILL -> {
				for (LivingEntity e : around(p, CHILL_RANGE, e -> foe(p, e))) {
					fx(e, MobEffects.SLOWNESS, 5, 1);
					fx(e, MobEffects.WEAKNESS, 5, 0);
				}
				ring(p, ParticleTypes.SNOWFLAKE, 24);
				sound(p, SoundEvents.SOUL_ESCAPE.value(), 0.8F, 0.6F);
			}
			case UNDYING_HUNGER -> {
				STEAL.put(p.getUUID(), new long[] {now + HUNGER_TICKS, Math.round(HUNGER_STEAL * 100.0F)});
				p.heal(2.0F);
				burst(p, ParticleTypes.SOUL, 14);
				sound(p, SoundEvents.ZOMBIE_VILLAGER_CURE, 0.7F, 0.6F);
			}
			case CONSCRIPT_THE_DEAD -> {
				if (!conscript(p, level, now)) {
					return false;
				}
			}
			case SPECTRAL_PHASE -> {
				PHASED.put(p.getUUID(), now + SPECTRAL_TICKS);
				fx(p, MobEffects.INVISIBILITY, 4, 0);
				fx(p, MobEffects.SPEED, 4, 1);
				calm(p, 20.0);
				ring(p, ParticleTypes.SOUL, 20);
				sound(p, SoundEvents.SOUL_ESCAPE.value(), 0.9F, 0.8F);
			}
			case SOUL_HARVEST -> {
				float healed = 0.0F;
				for (LivingEntity e : around(p, REAP_RANGE, e -> foe(p, e))) {
					e.hurtServer(level, level.damageSources().magic(), REAP_DAMAGE);
					burst(e, ParticleTypes.SOUL, 6);
					healed = Math.min(REAP_HEAL_MAX, healed + REAP_HEAL);
				}
				if (healed <= 0.0F) {
					p.sendSystemMessage(Component.translatable("message.jaymoskingdoms.kit.nothing").withStyle(ChatFormatting.GRAY), true);
					return false;
				}
				p.heal(healed);
				ring(p, ParticleTypes.SCULK_SOUL, 20);
				sound(p, SoundEvents.SOUL_ESCAPE.value(), 0.9F, 1.2F);
			}
			case VERDANT_BLOOM -> {
				for (LivingEntity e : around(p, BLOOM_RANGE, e -> e instanceof Player)) {
					e.heal(BLOOM_HEAL);
					fx(e, MobEffects.REGENERATION, 8, 0);
				}
				p.heal(BLOOM_HEAL);
				fx(p, MobEffects.REGENERATION, 8, 0);
				grow(p, level);
				ring(p, ParticleTypes.HAPPY_VILLAGER, 30);
				sound(p, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.8F, 1.3F);
			}
			case MIRROR_SHIFT -> {
				LivingEntity target = Arcana.sighted(p);
				Vec3 mine = p.position();
				// (0.7.490) only a mob, never a player or a great one, and only where each fits in the other's place
				if (!(target instanceof Mob) || unmirrorable(target) || target.distanceTo(p) > MIRROR_RANGE
					|| !level.noCollision(target, target.getBoundingBox().move(mine.subtract(target.position())))
					|| !level.noCollision(p, p.getBoundingBox().move(target.position().subtract(mine)))) {
					p.sendSystemMessage(Component.translatable("message.jaymoskingdoms.kit.nothing").withStyle(ChatFormatting.GRAY), true);
					return false;
				}
				Vec3 theirs = target.position();
				target.teleportTo(level, mine.x, mine.y, mine.z, Set.<Relative>of(), target.getYRot(), target.getXRot(), false);
				p.teleportTo(level, theirs.x, theirs.y, theirs.z, Set.<Relative>of(), p.getYRot(), p.getXRot(), false);
				fx(p, MobEffects.INVISIBILITY, 2, 0);
				fx(p, MobEffects.SPEED, 4, 1);
				if (target instanceof Mob mob) {
					mob.setTarget(null);
				}
				burst(p, Fx.HEX, 20);
				sound(p, SoundEvents.ENDERMAN_TELEPORT, 0.6F, 1.5F);
			}
			case PHASE_SHIFT -> {
				PHASED.put(p.getUUID(), now + PHASE_TICKS);
				Vec3 look = flat(p);
				launch(p, look.x * 1.2, 0.2, look.z * 1.2);
				fx(p, MobEffects.INVISIBILITY, 3, 0);
				fx(p, MobEffects.SLOW_FALLING, 6, 0);
				burst(p, Fx.LIGHT, 20);
				sound(p, SoundEvents.EVOKER_CAST_SPELL, 0.6F, 1.8F);
			}
			case SIRENS_CHARM -> {
				List<LivingEntity> foes = around(p, CHARM_RANGE, e -> foe(p, e));
				if (foes.isEmpty()) {
					p.sendSystemMessage(Component.translatable("message.jaymoskingdoms.kit.nothing").withStyle(ChatFormatting.GRAY), true);
					return false;
				}
				for (LivingEntity e : foes) {
					if (e instanceof Mob mob) {
						mob.setTarget(null);
					}
					fx(e, MobEffects.SLOWNESS, 6, 1);
					Vec3 pull = p.position().subtract(e.position()).multiply(1.0, 0.0, 1.0).normalize().scale(0.5);
					e.setDeltaMovement(e.getDeltaMovement().add(pull.x, 0.1, pull.z));
					e.syncVelocity = true;
					burst(e, ParticleTypes.HEART, 5);
				}
				ring(p, ParticleTypes.NOTE, 16);
				sound(p, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0F, 1.9F);
			}
			case FAERIE_BARGAIN -> {
				if (p.getHealth() <= BARGAIN_FLOOR) {
					p.sendSystemMessage(Component.translatable("message.jaymoskingdoms.kit.too_weak").withStyle(ChatFormatting.GRAY), true);
					return false;
				}
				p.setHealth(p.getHealth() - BARGAIN_COST);
				fx(p, MobEffects.STRENGTH, BARGAIN_SECONDS, 1);
				fx(p, MobEffects.SPEED, BARGAIN_SECONDS, 1);
				fx(p, MobEffects.LUCK, BARGAIN_SECONDS * 4, 1);
				burst(p, Fx.HEX, 30);
				sound(p, SoundEvents.EVOKER_CAST_SPELL, 0.8F, 0.8F);
			}
			case ARCANE_REFLEX -> {
				REFLEX.put(p.getUUID(), now + REFLEX_TICKS);
				burst(p, Fx.GLYPH, 30);
				sound(p, SoundEvents.EVOKER_CAST_SPELL, 0.6F, 1.7F);
			}
			case MOONLIT_VEIL -> {
				fx(p, MobEffects.INVISIBILITY, night(p) ? VEIL_NIGHT_SECONDS : VEIL_SECONDS, 0);
				fx(p, MobEffects.SPEED, night(p) ? VEIL_NIGHT_SECONDS : VEIL_SECONDS, 0);
				calm(p, 16.0);
				burst(p, Fx.LIGHT, 20);
				sound(p, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.7F, 0.7F);
			}
			case BLOOD_FRENZY -> {
				STEAL.put(p.getUUID(), new long[] {now + FRENZY_TICKS, Math.round(FRENZY_STEAL * 100.0F)});
				fx(p, MobEffects.SPEED, 10, 0);
				burst(p, ParticleTypes.ANGRY_VILLAGER, 12);
				sound(p, SoundEvents.RAVAGER_ROAR, 0.7F, 1.5F);
			}
			default -> {
				return false;
			}
		}
		return true;
	}

	// ---- the helpers of the abilities ----------------------------------------------------------------------------------------

	private static void fx(LivingEntity e, Holder<MobEffect> effect, int seconds, int amplifier) {
		e.addEffect(new MobEffectInstance(effect, seconds * 20, amplifier));
	}

	private static void sound(Entity e, SoundEvent sound, float volume, float pitch) {
		e.level().playSound(null, e.getX(), e.getY(), e.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
	}

	private static void burst(Entity e, ParticleOptions particle, int count) {
		if (e.level() instanceof ServerLevel level) {
			level.sendParticles(particle, e.getX(), e.getY(0.5), e.getZ(), count, 0.4, 0.6, 0.4, 0.05);
		}
	}

	private static void ring(Entity e, ParticleOptions particle, int count) {
		if (e.level() instanceof ServerLevel level) {
			for (int i = 0; i < count; i++) {
				double a = i * Math.PI * 2.0 / count;
				level.sendParticles(particle, e.getX() + Math.cos(a) * 1.4, e.getY(0.3), e.getZ() + Math.sin(a) * 1.4, 1, 0.0, 0.1, 0.0, 0.02);
			}
		}
	}

	/** Their look, flat (no up or down), normalised; straight ahead if they stare at the sky. */
	private static Vec3 flat(Entity e) {
		Vec3 look = e.getLookAngle().multiply(1.0, 0.0, 1.0);
		return look.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0.0F, e.getYRot()) : look.normalize();
	}

	private static void launch(ServerPlayer p, double x, double y, double z) {
		p.setDeltaMovement(x, y, z);
		p.syncVelocity = true;
		p.resetFallDistance();
	}

	/** The living within {@code range} of {@code p} (not them) that pass {@code filter}. */
	private static List<LivingEntity> around(ServerPlayer p, double range, Predicate<LivingEntity> filter) {
		return p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(range),
			e -> e != p && e.isAlive() && e.distanceToSqr(p) <= range * range && !CONSCRIPTS.containsKey(e.getUUID()) && filter.test(e));
	}

	private static @Nullable LivingEntity nearest(ServerPlayer p, double range, Predicate<LivingEntity> filter) {
		LivingEntity best = null;
		double d = Double.MAX_VALUE;
		for (LivingEntity e : around(p, range, filter)) {
			if (e.distanceToSqr(p) < d) {
				d = e.distanceToSqr(p);
				best = e;
			}
		}
		return best;
	}

	/** A monster, not tamed by or kin to {@code p}. */
	private static boolean foe(ServerPlayer p, LivingEntity e) {
		return e instanceof Enemy && !(e instanceof Player);
	}

	/** Monsters within {@code range} forget {@code p}. */
	private static void calm(ServerPlayer p, double range) {
		for (LivingEntity e : around(p, range, e -> e instanceof Mob)) {
			if (e instanceof Mob mob && mob.getTarget() == p) {
				mob.setTarget(null);
			}
		}
	}

	/** A cone of {@code particle} in front of {@code p}; each foe in it takes {@code damage} and {@code then}. */
	private static void breath(ServerPlayer p, ServerLevel level, ParticleOptions particle, float damage, java.util.function.Consumer<LivingEntity> then) {
		Vec3 look = p.getLookAngle();
		Vec3 eye = p.getEyePosition();
		for (int i = 1; i <= 16; i++) {
			double d = i * BREATH_RANGE / 16.0;
			level.sendParticles(particle, eye.x + look.x * d, eye.y + look.y * d - 0.2, eye.z + look.z * d, 3, d * 0.08, d * 0.08, d * 0.08, 0.02);
		}
		for (LivingEntity e : around(p, BREATH_RANGE, e -> true)) {
			Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
			if (to.lengthSqr() > 1.0E-4 && to.normalize().dot(look) >= BREATH_CONE && foe(p, e)) {
				e.hurtServer(level, level.damageSources().playerAttack(p), damage);
				then.accept(e);
			}
		}
	}

	/** Ender Step: along the look to the last free spot within range. */
	private static boolean step(ServerPlayer p, ServerLevel level) {
		Vec3 look = p.getLookAngle();
		Vec3 at = null;
		for (double d = 1.0; d <= STEP_RANGE; d += 1.0) {
			AABB box = p.getBoundingBox().move(look.x * d, look.y * d, look.z * d);
			if (!level.noCollision(p, box)) {
				break;
			}
			at = p.position().add(look.x * d, look.y * d, look.z * d);
		}
		if (at == null) {
			p.sendSystemMessage(Component.translatable("message.jaymoskingdoms.kit.nothing").withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		sound(p, SoundEvents.ENDERMAN_TELEPORT, 0.8F, 1.2F);
		p.teleportTo(level, at.x, at.y, at.z, Set.<Relative>of(), p.getYRot(), p.getXRot(), false);
		p.resetFallDistance();
		sound(p, SoundEvents.ENDERMAN_TELEPORT, 0.8F, 1.2F);
		burst(p, Fx.VOID, 30);
		return true;
	}

	/** Verdant Bloom: the crops round {@code p} grow a stage, a few at a time. */
	private static void grow(ServerPlayer p, ServerLevel level) {
		int grown = 0;
		BlockPos centre = p.blockPosition();
		for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-BLOOM_REACH, -1, -BLOOM_REACH), centre.offset(BLOOM_REACH, 1, BLOOM_REACH))) {
			if (grown < BLOOM_GROWS && BoneMealItem.growCrop(new ItemStack(Items.BONE_MEAL), level, pos)) {
				grown++;
			}
		}
	}

	/** (0.7.490) Mirror Shift: the great ones (and anything a player rides or is riding) are not to be swapped. */
	private static boolean unmirrorable(LivingEntity target) {
		return target instanceof WitherBoss || target instanceof EnderDragon || target instanceof ElderGuardian || target instanceof Warden
			|| target.isVehicle() || target.isPassenger();
	}

	// ---- conscripts ---------------------------------------------------------------------------------------------------------------

	private static boolean conscript(ServerPlayer p, ServerLevel level, long now) {
		// (0.7.490) on Peaceful a skeleton is gone the moment it rises: the call fails and costs nothing
		if (level.getDifficulty() == Difficulty.PEACEFUL) {
			p.sendSystemMessage(Component.translatable("message.jaymoskingdoms.kit.nothing").withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		int risen = 0;
		for (int i = 0; i < CONSCRIPT_COUNT; i++) {
			if (!(EntityTypes.SKELETON.create(level, EntitySpawnReason.MOB_SUMMONED) instanceof Mob mob)) {
				continue;
			}
			double a = p.getRandom().nextDouble() * Math.PI * 2.0;
			BlockPos at = BlockPos.containing(p.getX() + Math.cos(a) * 2.0, p.getY(), p.getZ() + Math.sin(a) * 2.0);
			mob.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, p.getYRot(), 0.0F);
			mob.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.MOB_SUMMONED, null);
			mob.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
			mob.setDropChance(EquipmentSlot.HEAD, 0.0F);
			// (0.7.490) the risen keep their 30 seconds in daylight: they don't burn
			mob.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, CONSCRIPT_TICKS + 40, 0, false, false));
			// (0.7.490) not kept for good, tagged (so a stray one is dropped on load), and blind to players: they pick foes only by conscripts()
			mob.addTag(CONSCRIPT_TAG);
			((MobAccessor) mob).jaymoskingdoms$targets().removeAllGoals(g -> g instanceof NearestAttackableTargetGoal<?>);
			CONSCRIPTS.put(mob.getUUID(), new Conscript(level, p.getUUID(), now + CONSCRIPT_TICKS));
			if (!level.addFreshEntity(mob)) {
				CONSCRIPTS.remove(mob.getUUID());
				continue;
			}
			level.sendParticles(ParticleTypes.SOUL, mob.getX(), mob.getY(0.5), mob.getZ(), 10, 0.3, 0.5, 0.3, 0.02);
			risen++;
		}
		if (risen == 0) {
			return false;
		}
		sound(p, SoundEvents.SOUL_ESCAPE.value(), 0.9F, 0.5F);
		return true;
	}

	/** Every second: the risen go after the nearest monster and fall when their time is up (or their caller is gone). */
	private static void conscripts(MinecraftServer server) {
		for (UUID id : new ArrayList<>(CONSCRIPTS.keySet())) {
			Conscript c = CONSCRIPTS.get(id);
			Entity e = c.level().getEntity(id);
			if (!(e instanceof Mob mob) || !mob.isAlive() || c.level().getGameTime() >= c.until() || server.getPlayerList().getPlayer(c.owner()) == null) {
				dismiss(id);
				CONSCRIPTS.remove(id);
				continue;
			}
			// (0.7.490) the sun sets them alight each tick; the fire resistance keeps it harmless, and this puts it out
			if (mob.isOnFire()) {
				mob.clearFire();
			}
			LivingEntity prey = null;
			double d = CONSCRIPT_REACH * CONSCRIPT_REACH;
			for (LivingEntity near : c.level().getEntitiesOfClass(LivingEntity.class, mob.getBoundingBox().inflate(CONSCRIPT_REACH),
				x -> x instanceof Enemy && !(x instanceof Player) && x.isAlive() && !CONSCRIPTS.containsKey(x.getUUID()))) {
				if (near.distanceToSqr(mob) < d) {
					d = near.distanceToSqr(mob);
					prey = near;
				}
			}
			if (prey != null || mob.getTarget() instanceof Player) {
				mob.setTarget(prey);
			}
		}
	}

	private static void dismiss(UUID id) {
		Conscript c = CONSCRIPTS.get(id);
		if (c != null && c.level().getEntity(id) instanceof Mob mob) {
			c.level().sendParticles(ParticleTypes.SOUL, mob.getX(), mob.getY(0.5), mob.getZ(), 8, 0.3, 0.5, 0.3, 0.02);
			mob.discard();
		}
	}
}
