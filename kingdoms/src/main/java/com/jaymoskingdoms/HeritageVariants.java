package com.jaymoskingdoms;

import com.jaymoskingdoms.remnants.Element;
import com.jaymoskingdoms.remnants.Monoliths;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.214) Heritage variants. Andy: "give each heritage 2 additional variants bringing them up to 3 total", "like
 * Emberborn's avatars but more ubiquitous": every heritage is born in three looks, its first (the look it always had)
 * and two more, each a people of its own with a name, a skin tint, its own marks (client {@code HeritageLooks},
 * textures from {@code tools/gen_heritage_looks.py}) and one small trait. Players and villagers alike can be any of them.
 * <ul>
 * <li><b>Players</b> pick one on the heritage screen with their heritage (the Choose packet carries it). One who picked a
 * heritage before variants came is asked once in chat ({@link #offer}) and keeps the first look until they answer.</li>
 * <li><b>Villagers</b> are born to one ({@link #roll}): mostly the first, leaned by the land the way heritages are, and
 * by the Aether's remnants: a monolith of a variant's element near where they're born makes it far likelier (the
 * Highfields' sky-touched, the Arctic's rime, the Irradiated lands' Ambrosium glow, the Magnetic lands' lodestone). A
 * child often takes a parent's variant, and some variants are hybrids: a child of a Stoneborn and an Emberborn who comes
 * out Stoneborn is likely Magmavein ({@link Variant#hybrid}).</li>
 * <li><b>Stoneborn</b> are all greyer than other folk (Andy: "to further signify them as their own at a glance"): the
 * first look's tint is {@link #STONE_GREY}.</li>
 * </ul>
 * Kept in {@link #VARIANT} ({@code "base"} for the first look, so a choice is told apart from none), synced to
 * everyone, saved, kept through death; an MCA villager's own saved data carries it too ({@link #SAVE_KEY}).
 */
public final class HeritageVariants {
	/** The variant of a heritage's first look, as kept. */
	public static final String BASE = "base";
	/** How likely a villager is born to the first look, against each other variant's weight. */
	static final int W_BASE = 50, W_VARIANT = 25;
	/** A variant whose land it is counts this many times over; one whose element's monolith stands near, this many. */
	static final int LEAN = 3, REMNANT_LEAN = 4;
	/** How near a monolith must stand for its element to lean a birth (blocks). */
	static final double REMNANT_RANGE = 800.0;
	/** A child of the same heritage as a parent takes that parent's variant this often; a hybrid's two bloods make it this often. */
	static final float INHERIT_CHANCE = 0.5F, HYBRID_CHANCE = 0.6F;
	/** The Stoneborn's grey (Andy: "stoneborn should all have a greyer hue"): over the skin by this much. */
	static final int STONE_GREY = 0x8A8C8E;
	static final float STONE_GREY_AMOUNT = 0.6F;
	/**
	 * (0.7.261) The new bloods' skin: every Rubber-folk is pink and every Divine-born touched with gold, over the skin by these
	 * amounts; a Bio-built's drab made skin grows more so with each form ({@code BIO_AMOUNT}, first to third).
	 */
	static final int RUBBER_PINK = 0xF09AC0, DIVINE_GOLD = 0xF4DDA0, BIO_GREY = 0x8AA39A;
	static final float RUBBER_PINK_AMOUNT = 0.55F, DIVINE_GOLD_AMOUNT = 0.3F;
	static final float[] BIO_AMOUNT = {0.25F, 0.35F, 0.5F};
	/** Thornrose: what a melee blow costs whoever lands it on one. */
	static final float THORN_DAMAGE = 1.0F;
	/** Thornrose: only blows from this near count as melee (blocks). */
	static final double THORN_REACH = 4.5;
	/** An MCA villager's own saved data: its variant (a baby item carries it). */
	public static final String SAVE_KEY = "jaymoskingdoms_heritage_variant";

	/** Each entity's variant ({@link Variant#key}, or {@link #BASE}). Saved, kept through death, synced to everyone. */
	public static final AttachmentType<String> VARIANT = AttachmentRegistry.create(JaymosKingdoms.id("heritage_variant"),
		builder -> builder.persistent(Codec.STRING).copyOnDeath().syncWith(ByteBufCodecs.STRING_UTF8, AttachmentSyncPredicate.all()));

	/** Where a variant is born more often. */
	enum Land { NONE, PLAINS, HIGH, DEEP, HOT, WARM_SEA, DEEP_SEA, FLOWERS, CHERRY, SWAMP, JUNGLE, SOUL, COLD, DARK, MOUNTAIN, BADLANDS, END }

	/** One small trait: {@code amount} on {@code attribute}. */
	record Trait(Holder<Attribute> attribute, double amount, AttributeModifier.Operation operation) {
		static Trait add(Holder<Attribute> attribute, double amount) {
			return new Trait(attribute, amount, AttributeModifier.Operation.ADD_VALUE);
		}

		static Trait share(Holder<Attribute> attribute, double amount) {
			return new Trait(attribute, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		}
	}

	/**
	 * The variants past each heritage's first look, two each, in order. {@code tint}: the colour laid over their skin
	 * (MCA's skin layer; a Verdant's green), by {@code tintAmount} (0: none); {@code element}: a monolith of it near
	 * leans births to it; {@code hybrid}: a child with a parent of that heritage leans to it.
	 */
	public enum Variant {
		// ---- Heartlander: the plain folk, but even they carry the land
		SUNFRECKLED(Heritage.Kind.HEARTLANDER, 0xB0784E, 0.18F, Land.PLAINS, null, null, Trait.share(Attributes.MOVEMENT_SPEED, 0.03)),
		SKYMARKED(Heritage.Kind.HEARTLANDER, 0, 0.0F, Land.HIGH, Element.HIGHFIELD, null, Trait.add(Attributes.SAFE_FALL_DISTANCE, 1.0)),
		// ---- Stoneborn: Andy's two, the obsidian-hided and the ember hybrid
		OBSIDIAN(Heritage.Kind.STONEBORN, 0x2A2234, 0.8F, Land.DEEP, null, null, Trait.add(Attributes.ARMOR, 3.0),
			Trait.add(Attributes.KNOCKBACK_RESISTANCE, 0.2), Trait.share(Attributes.MOVEMENT_SPEED, -0.04)),
		MAGMAVEIN(Heritage.Kind.STONEBORN, 0x4C4746, 0.7F, Land.HOT, null, Heritage.Kind.EMBERBORN, Trait.share(Attributes.BURNING_TIME, -0.6),
			Trait.share(Attributes.ATTACK_DAMAGE, 0.04)),
		// ---- Tideborn
		REEFCROWNED(Heritage.Kind.TIDEBORN, 0xE2A486, 0.15F, Land.WARM_SEA, null, null, Trait.add(Attributes.ARMOR, 1.5)),
		ABYSSAL(Heritage.Kind.TIDEBORN, 0x30506E, 0.4F, Land.DEEP_SEA, null, Heritage.Kind.DEEPBORN, Trait.add(Attributes.OXYGEN_BONUS, 2.0)),
		// ---- Sylvan: Andy's thorny rose, and the Highfields' blossom
		THORNROSE(Heritage.Kind.SYLVAN, 0, 0.0F, Land.FLOWERS, null, null),
		BLOSSOMKIN(Heritage.Kind.SYLVAN, 0xF4D6DC, 0.14F, Land.CHERRY, Element.HIGHFIELD, null, Trait.add(Attributes.SAFE_FALL_DISTANCE, 2.0)),
		// ---- Verdant (the tint shifts their green)
		GLOWCAP(Heritage.Kind.VERDANT, 0xB4CC3C, 0.45F, Land.SWAMP, Element.IRRADIATED, null, Trait.add(Attributes.MAX_HEALTH, 2.0)),
		MOSSHIDE(Heritage.Kind.VERDANT, 0x46582A, 0.5F, Land.JUNGLE, null, Heritage.Kind.SYLVAN, Trait.add(Attributes.ARMOR, 1.5)),
		// ---- Emberborn
		SOULFLAME(Heritage.Kind.EMBERBORN, 0, 0.0F, Land.SOUL, null, null, Trait.share(Attributes.ATTACK_DAMAGE, 0.05)),
		ASHEN(Heritage.Kind.EMBERBORN, 0x787270, 0.45F, Land.COLD, null, Heritage.Kind.FROSTBORN, Trait.add(Attributes.ARMOR, 1.0)),
		// ---- Frostborn
		RIMEGLASS(Heritage.Kind.FROSTBORN, 0xCCE4F4, 0.35F, Land.COLD, Element.ARCTIC, null, Trait.add(Attributes.ARMOR, 2.0),
			Trait.share(Attributes.MOVEMENT_SPEED, -0.02)),
		AURORA(Heritage.Kind.FROSTBORN, 0, 0.0F, Land.NONE, Element.ARCTIC, null, Trait.share(Attributes.MOVEMENT_SPEED, 0.03)),
		// ---- Nightborn
		MOONPALE(Heritage.Kind.NIGHTBORN, 0xDCE0EC, 0.4F, Land.HIGH, null, null, Trait.add(Attributes.MAX_HEALTH, 2.0)),
		UMBRAL(Heritage.Kind.NIGHTBORN, 0x483E5E, 0.35F, Land.DARK, null, Heritage.Kind.ENDBORN, Trait.share(Attributes.SNEAKING_SPEED, 0.15)),
		// ---- Warrior-born
		BATTLESCARRED(Heritage.Kind.WARRIORBORN, 0, 0.0F, Land.NONE, null, null, Trait.add(Attributes.ARMOR, 1.5)),
		WARPAINTED(Heritage.Kind.WARRIORBORN, 0, 0.0F, Land.NONE, null, null, Trait.share(Attributes.ATTACK_DAMAGE, 0.05)),
		// ---- Earthling
		IRONKNUCKLE(Heritage.Kind.EARTHLING, 0, 0.0F, Land.NONE, null, null, Trait.share(Attributes.ATTACK_DAMAGE, 0.04)),
		INKMARKED(Heritage.Kind.EARTHLING, 0, 0.0F, Land.NONE, null, null, Trait.add(Attributes.LUCK, 1.0)),
		// ---- Stormborn
		LODEBORN(Heritage.Kind.STORMBORN, 0x7A7A88, 0.25F, Land.MOUNTAIN, Element.MAGNETIC, null, Trait.add(Attributes.KNOCKBACK_RESISTANCE, 0.15)),
		GALEBORN(Heritage.Kind.STORMBORN, 0xF0F0F4, 0.15F, Land.HIGH, Element.HIGHFIELD, null, Trait.add(Attributes.SAFE_FALL_DISTANCE, 2.0)),
		// ---- Dawnborn
		TWILIGHT(Heritage.Kind.DAWNBORN, 0, 0.0F, Land.NONE, null, Heritage.Kind.NIGHTBORN, Trait.share(Attributes.MOVEMENT_SPEED, 0.02)),
		GILDED(Heritage.Kind.DAWNBORN, 0xE0C080, 0.15F, Land.BADLANDS, null, null, Trait.add(Attributes.LUCK, 1.0)),
		// ---- Endborn
		CHORUSGROWN(Heritage.Kind.ENDBORN, 0, 0.0F, Land.END, null, null, Trait.add(Attributes.MAX_HEALTH, 2.0)),
		VOIDPALE(Heritage.Kind.ENDBORN, 0xD0C8DC, 0.45F, Land.END, null, null, Trait.share(Attributes.MOVEMENT_SPEED, 0.03)),
		// ---- Dragonblood
		GOLDSCALE(Heritage.Kind.DRAGONBLOOD, 0xD8B060, 0.2F, Land.BADLANDS, null, null, Trait.add(Attributes.LUCK, 1.0)),
		RIMEWYRM(Heritage.Kind.DRAGONBLOOD, 0xC8DCEC, 0.25F, Land.COLD, Element.ARCTIC, Heritage.Kind.FROSTBORN, Trait.add(Attributes.ARMOR, 1.5)),
		// ---- Deepborn
		HOLLOW(Heritage.Kind.DEEPBORN, 0x9AA8A8, 0.4F, Land.DEEP, null, null, Trait.share(Attributes.SNEAKING_SPEED, 0.15)),
		GEODE(Heritage.Kind.DEEPBORN, 0x8C8C94, 0.3F, Land.DEEP, null, Heritage.Kind.STONEBORN, Trait.add(Attributes.ARMOR, 2.0)),
		// ---- (0.7.261, Andy's NOEA picks) Divine-born: the starlit and the feathered
		STARLIT(Heritage.Kind.DIVINE, 0xCFE0FF, 0.3F, Land.HIGH, null, null, Trait.add(Attributes.LUCK, 1.0)),
		SERAPHIC(Heritage.Kind.DIVINE, 0xFFF4DC, 0.35F, Land.NONE, null, null, Trait.add(Attributes.SAFE_FALL_DISTANCE, 2.0)),
		// ---- Bio-built: brass-bound and pearly
		BRASSBOUND(Heritage.Kind.BIOBUILT, 0xC0865A, 0.3F, Land.NONE, null, null, Trait.add(Attributes.ARMOR, 1.5)),
		OPALINE(Heritage.Kind.BIOBUILT, 0xD8E4E0, 0.35F, Land.NONE, null, null, Trait.share(Attributes.MOVEMENT_SPEED, 0.03)),
		// ---- Rubber-folk: candy-striped and berry-glossed (over the pink every one of them wears)
		CANDYSTRIPE(Heritage.Kind.RUBBERFOLK, 0xFFD0DC, 0.35F, Land.PLAINS, null, null, Trait.share(Attributes.MOVEMENT_SPEED, 0.03)),
		BERRYGLOSS(Heritage.Kind.RUBBERFOLK, 0xB04C88, 0.45F, Land.SWAMP, null, null, Trait.share(Attributes.ATTACK_DAMAGE, 0.04));

		public final Heritage.Kind heritage;
		/** Over the skin (RGB), by {@code tintAmount} (0: none). */
		public final int tint;
		public final float tintAmount;
		final Land land;
		final @Nullable Element element;
		final Heritage.@Nullable Kind hybrid;
		final List<Trait> traits;

		Variant(Heritage.Kind heritage, int tint, float tintAmount, Land land, @Nullable Element element, Heritage.@Nullable Kind hybrid, Trait... traits) {
			this.heritage = heritage;
			this.tint = tint;
			this.tintAmount = tintAmount;
			this.land = land;
			this.element = element;
			this.hybrid = hybrid;
			this.traits = List.of(traits);
		}

		public String key() {
			return name().toLowerCase(Locale.ROOT);
		}

		/** 1 or 2: which of its heritage's looks this is (0 is the first). */
		public int index() {
			return of(this.heritage).indexOf(this) + 1;
		}

		public Component title() {
			return Component.translatable("variant.jaymoskingdoms." + key());
		}

		public static @Nullable Variant byKey(String key) {
			for (Variant v : values()) {
				if (v.key().equals(key)) {
					return v;
				}
			}
			return null;
		}
	}

	/** Every attribute a variant's trait touches (so a trait no longer had is taken off). */
	private static final Set<Holder<Attribute>> TRAITED = new LinkedHashSet<>();

	static {
		for (Variant v : Variant.values()) {
			for (Trait t : v.traits) {
				TRAITED.add(t.attribute());
			}
		}
	}

	private HeritageVariants() {
	}

	static void register() {
		// Thornrose: whoever lands a blow on one up close is pricked
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (taken > 0.0F) {
				thorns(entity, source);
			}
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> offer(handler.player));
		CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> dispatcher.register(Commands.literal("kingdom")
			.then(Commands.literal("blood")
				.then(Commands.argument("variant", StringArgumentType.word())
					.executes(c -> claim(c.getSource(), StringArgumentType.getString(c, "variant")))))));
	}

	// ---- who is what ----------------------------------------------------------------------------------------------

	/** {@code kind}'s two variants past its first look. */
	public static List<Variant> of(Heritage.Kind kind) {
		List<Variant> out = new ArrayList<>(2);
		for (Variant v : Variant.values()) {
			if (v.heritage == kind) {
				out.add(v);
			}
		}
		return out;
	}

	/** {@code kind}'s look number {@code index} (0: the first, null), or null past its last. */
	public static @Nullable Variant at(Heritage.Kind kind, int index) {
		List<Variant> all = of(kind);
		return index >= 1 && index <= all.size() ? all.get(index - 1) : null;
	}

	/** {@code entity}'s variant, or null for the first look (or none, or one that isn't its heritage's). */
	public static @Nullable Variant of(Entity entity) {
		String key = entity.getAttached(VARIANT);
		Variant v = key == null ? null : Variant.byKey(key);
		return v != null && v.heritage == VillagerHeritage.of(entity) ? v : null;
	}

	/** Whether {@code entity} has a variant settled yet ({@link #BASE} counts). */
	static boolean settled(Entity entity) {
		return entity.hasAttached(VARIANT);
	}

	/** Puts variant {@code v} (null: the first look) on {@code entity}, and its trait. */
	public static void set(LivingEntity entity, @Nullable Variant v) {
		entity.setAttached(VARIANT, v == null ? BASE : v.key());
		traits(entity);
	}

	/** The look name of {@code kind}'s first. */
	public static Component baseTitle(Heritage.Kind kind) {
		return Component.translatable("variant.jaymoskingdoms." + kind.key() + "_base");
	}

	/** The name of {@code kind}'s look number {@code index}. */
	public static Component title(Heritage.Kind kind, int index) {
		Variant v = at(kind, index);
		return v == null ? baseTitle(kind) : v.title();
	}

	/** The lang key root of {@code kind}'s look {@code index} ({@code .desc}, {@code .trait} under it). */
	public static String langKey(Heritage.Kind kind, int index) {
		Variant v = at(kind, index);
		return "variant.jaymoskingdoms." + (v == null ? kind.key() + "_base" : v.key());
	}

	// ---- skin ------------------------------------------------------------------------------------------------------

	/**
	 * {@code color} (MCA's skin colour, opaque ARGB) as {@code entity}'s heritage wears it: a Stoneborn's greyed, a
	 * variant's tinted. As it was for everyone else. (A Verdant's green is tinted in {@link Verdants#skin}.)
	 */
	public static int skin(Entity entity, int color) {
		Heritage.Kind kind = VillagerHeritage.of(entity);
		if (kind == null || kind == Heritage.Kind.VERDANT) {
			return color;
		}
		Variant v = of(entity);
		if (kind == Heritage.Kind.STONEBORN) {
			color = blend(color, STONE_GREY, STONE_GREY_AMOUNT);
		} else if (kind == Heritage.Kind.RUBBERFOLK) {
			color = blend(color, RUBBER_PINK, RUBBER_PINK_AMOUNT);
		} else if (kind == Heritage.Kind.DIVINE) {
			color = blend(color, DIVINE_GOLD, DIVINE_GOLD_AMOUNT);
		} else if (kind == Heritage.Kind.BIOBUILT) {
			color = blend(color, BIO_GREY, BIO_AMOUNT[BioBuilt.form(entity)]);
		}
		return v == null || v.tintAmount <= 0.0F ? color : blend(color, v.tint, v.tintAmount);
	}

	/** {@code color} (ARGB) {@code amount} of the way to {@code toward} (RGB); its alpha kept. */
	public static int blend(int color, int toward, float amount) {
		return ARGB.color(ARGB.alpha(color), Math.round(Mth.lerp(amount, ARGB.red(color), ARGB.red(toward))),
			Math.round(Mth.lerp(amount, ARGB.green(color), ARGB.green(toward))), Math.round(Mth.lerp(amount, ARGB.blue(color), ARGB.blue(toward))));
	}

	// ---- births ----------------------------------------------------------------------------------------------------

	/**
	 * A variant for one of {@code kind} born at {@code pos}: by the weights, leaned by the land and by any monolith of a
	 * variant's element standing near. Null: the first look.
	 */
	static @Nullable Variant roll(Level level, BlockPos pos, Heritage.Kind kind, RandomSource random) {
		List<Variant> all = of(kind);
		float[] weights = new float[all.size()];
		float total = W_BASE;
		for (int i = 0; i < all.size(); i++) {
			Variant v = all.get(i);
			float w = W_VARIANT;
			if (fits(level, pos, v.land)) {
				w *= LEAN;
			}
			if (v.element != null && level instanceof ServerLevel server && Monoliths.near(server, pos, v.element, REMNANT_RANGE)) {
				w *= REMNANT_LEAN;
			}
			weights[i] = w;
			total += w;
		}
		float pick = random.nextFloat() * total - W_BASE;
		for (int i = 0; i < all.size(); i++) {
			if (pick < 0.0F) {
				return null;
			}
			pick -= weights[i];
			if (pick < 0.0F) {
				return all.get(i);
			}
		}
		return null;
	}

	/** Whether {@code pos} is land {@code land}. */
	static boolean fits(Level level, BlockPos pos, Land land) {
		Holder<Biome> biome = level.getBiome(pos);
		float heat = biome.value().getBaseTemperature();
		return switch (land) {
			case NONE -> false;
			case PLAINS -> biome.is(Biomes.PLAINS) || biome.is(Biomes.SUNFLOWER_PLAINS) || biome.is(BiomeTags.IS_SAVANNA);
			case HIGH -> pos.getY() >= VillagerHeritage.HIGH_Y;
			case DEEP -> pos.getY() < 0 || biome.is(Biomes.BASALT_DELTAS);
			case HOT -> heat >= Heritage.HOT || biome.is(BiomeTags.IS_BADLANDS) || biome.is(BiomeTags.IS_NETHER);
			case WARM_SEA -> biome.is(Biomes.WARM_OCEAN) || biome.is(Biomes.LUKEWARM_OCEAN) || biome.is(BiomeTags.IS_BEACH) && heat >= 0.8F;
			case DEEP_SEA -> biome.is(BiomeTags.IS_DEEP_OCEAN);
			case FLOWERS -> biome.is(Biomes.FLOWER_FOREST) || biome.is(Biomes.MEADOW) || biome.is(Biomes.SUNFLOWER_PLAINS);
			case CHERRY -> biome.is(Biomes.CHERRY_GROVE);
			case SWAMP -> biome.is(Biomes.SWAMP) || biome.is(Biomes.MANGROVE_SWAMP) || biome.is(Biomes.MUSHROOM_FIELDS) || biome.is(Biomes.LUSH_CAVES);
			case JUNGLE -> biome.is(BiomeTags.IS_JUNGLE);
			case SOUL -> biome.is(Biomes.SOUL_SAND_VALLEY) || biome.is(BiomeTags.IS_NETHER);
			case COLD -> heat <= Heritage.COLD;
			case DARK -> biome.is(Biomes.DARK_FOREST) || biome.is(Biomes.PALE_GARDEN);
			case MOUNTAIN -> biome.is(BiomeTags.IS_MOUNTAIN) || biome.is(BiomeTags.IS_HILL);
			case BADLANDS -> biome.is(BiomeTags.IS_BADLANDS);
			case END -> biome.is(BiomeTags.IS_END);
		};
	}

	/**
	 * {@code villager} is born (or was before variants came): one that has its heritage's variant keeps it; any other is
	 * born to one here ({@link #roll}).
	 */
	static void settle(LivingEntity villager) {
		Heritage.Kind kind = VillagerHeritage.of(villager);
		if (kind == null) {
			villager.removeAttached(VARIANT);
			traits(villager);
			return;
		}
		String key = villager.getAttached(VARIANT);
		Variant had = key == null ? null : Variant.byKey(key);
		if (BASE.equals(key) || had != null && had.heritage == kind) {
			traits(villager);
			return;
		}
		set(villager, roll(villager.level(), villager.blockPosition(), kind, villager.getRandom()));
	}

	/**
	 * A child of {@code kind}'s variant from its parents: a parent of the same heritage passes theirs on
	 * {@link #INHERIT_CHANCE} of the time; with a parent of a hybrid variant's other blood it's that hybrid
	 * {@link #HYBRID_CHANCE} of the time; else a roll where it's born.
	 */
	public static @Nullable Variant child(Heritage.Kind kind, @Nullable Entity mother, @Nullable Entity father, Entity at, RandomSource random) {
		for (Variant v : of(kind)) {
			if (v.hybrid != null && (is(mother, v.hybrid) || is(father, v.hybrid)) && random.nextFloat() < HYBRID_CHANCE) {
				return v;
			}
		}
		List<Entity> same = new ArrayList<>(2);
		for (Entity parent : new Entity[] {mother, father}) {
			if (parent != null && VillagerHeritage.of(parent) == kind && settled(parent)) {
				same.add(parent);
			}
		}
		if (!same.isEmpty() && random.nextFloat() < INHERIT_CHANCE) {
			return of(same.get(random.nextInt(same.size())));
		}
		return roll(at.level(), at.blockPosition(), kind, random);
	}

	private static boolean is(@Nullable Entity parent, Heritage.Kind kind) {
		return parent != null && VillagerHeritage.of(parent) == kind;
	}

	// ---- traits ----------------------------------------------------------------------------------------------------

	/** Puts {@code entity}'s variant's trait on it, and takes any other's off. */
	public static void traits(LivingEntity entity) {
		Variant v = of(entity);
		for (Holder<Attribute> attribute : TRAITED) {
			double amount = 0.0;
			AttributeModifier.Operation operation = AttributeModifier.Operation.ADD_VALUE;
			if (v != null) {
				for (Trait t : v.traits) {
					if (t.attribute().equals(attribute)) {
						amount += t.amount();
						operation = t.operation();
					}
				}
			}
			AttributeInstance instance = entity.getAttribute(attribute);
			if (instance == null) {
				continue;
			}
			Identifier id = JaymosKingdoms.id("heritage_variant_" + attribute.unwrapKey().map(k -> k.identifier().getPath()).orElse("x").replace('.', '_'));
			if (amount == 0.0) {
				instance.removeModifier(id);
			} else {
				instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, operation));
			}
		}
	}

	/** Thornrose: a melee blow on one pricks whoever struck it (never thorns on thorns). */
	private static void thorns(LivingEntity target, DamageSource source) {
		if (of(target) != Variant.THORNROSE || source.is(DamageTypes.THORNS) || !(target.level() instanceof ServerLevel level)) {
			return;
		}
		Entity direct = source.getDirectEntity();
		if (direct instanceof LivingEntity attacker && direct == source.getEntity() && attacker != target && attacker.distanceTo(target) <= THORN_REACH) {
			attacker.hurtServer(level, level.damageSources().thorns(target), THORN_DAMAGE);
		}
	}

	// ---- players ---------------------------------------------------------------------------------------------------

	/** A player picks look {@code index} of their heritage (on the heritage screen). */
	static void choose(ServerPlayer player, Heritage.Kind kind, int index) {
		set(player, at(kind, index));
	}

	/**
	 * (joining) A player who chose a heritage before variants came is asked once, in chat, which of its three looks they
	 * are; until they answer they wear the first.
	 */
	static void offer(ServerPlayer player) {
		Heritage.Kind kind = Heritage.of(player);
		if (kind == null) {
			return;
		}
		if (settled(player)) {
			traits(player);
			return;
		}
		MutableComponent line = Component.translatable("message.jaymoskingdoms.variant.offer", Component.translatable("heritage.jaymoskingdoms." + kind.key()))
			.withStyle(ChatFormatting.GOLD);
		for (int i = 0; i <= of(kind).size(); i++) {
			int index = i;
			Variant v = at(kind, i);
			String command = "/kingdom blood " + (v == null ? BASE : v.key());
			line.append(" ").append(title(kind, index).copy().withStyle(style -> style.withColor(ChatFormatting.GREEN).withUnderlined(true)
				.withClickEvent(new ClickEvent.RunCommand(command)).withHoverEvent(new HoverEvent.ShowText(Component.translatable(langKey(kind, index) + ".desc")))));
		}
		player.sendSystemMessage(line);
	}

	private static int indexOf(Heritage.Kind kind, @Nullable Variant v) {
		return v == null ? 0 : v.index();
	}

	/** {@code /kingdom blood <variant>}: a player's answer to {@link #offer}, once only. */
	private static int claim(CommandSourceStack source, String key) {
		if (!(source.getEntity() instanceof ServerPlayer player)) {
			return 0;
		}
		Heritage.Kind kind = Heritage.of(player);
		if (kind == null || settled(player)) {
			source.sendFailure(Component.translatable("message.jaymoskingdoms.variant.settled"));
			return 0;
		}
		Variant v = Variant.byKey(key);
		if (!BASE.equals(key) && (v == null || v.heritage != kind)) {
			source.sendFailure(Component.translatable("message.jaymoskingdoms.variant.unknown"));
			return 0;
		}
		set(player, v);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.8F, 1.2F);
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.variant.chosen", title(kind, indexOf(kind, v))).withStyle(ChatFormatting.GOLD));
		return 1;
	}
}
