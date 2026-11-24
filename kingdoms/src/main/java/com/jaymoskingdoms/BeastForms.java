package com.jaymoskingdoms;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.Npc;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Any beast a Druid has studied is a shape (the Druid's tree, {@link WildShapes}): its stand-in is the beast itself, in its own
 * size, and what the real one has it lends, and what it lacks it takes.
 * <ul>
 * <li><b>Which.</b> Animals only: the creature, ambient and water categories, never a monster, a person or a Kingdoms
 * {@link Folk}. A form is {@code "mob:" + entity id}; the four of old, the fox and the stag keep their hand-made ones.</li>
 * <li><b>What it gives</b> ({@link Trait}): a table of name fragments (so a modpack's animals fall into the same kinds by their
 * names) beside the category: wings, gills, a leap, a soft landing and night eyes, a climber's feet, a butt, a digger's
 * claws, a gallop, a wall-climb, a walk on lava, a long body. Weaknesses come with them: a fish out of water flops, a bat
 * in the light is weak.</li>
 * <li><b>How great.</b> By its size ({@link Size}): small beasts from the start, middling beasts with Fox and Stag (the middle
 * of the tree), the great ones (elephant, orca, bison) with the Circle of the Moon.</li>
 * <li><b>Long bodies.</b> A snake, crocodile or eel has a tail of stand-ins that bends along the path walked, and what it
 * strikes is squeezed.</li>
 * </ul>
 */
public final class BeastForms {
	public static final String PREFIX = "mob:";
	/** What a shape lends. */
	enum Trait {
		FLY, SWIM, LEAP, SOFT, NIGHT, CLIMB, BUTT, DIG, GALLOP, WALLCLIMB, LAVA, LONG, FLOP, DARK
	}

	/** How great a beast is. */
	public enum Size {
		SMALL, MID, GREAT
	}

	/** The share of a standard body's height a shape's player is drawn at, at least and at most. */
	static final double SCALE_MIN = 0.35, SCALE_MAX = 2.0, PLAYER_HEIGHT = 1.8;
	/** A beast's volume (width x width x height) under which it is small, and from which it is great; and the names that are always great. */
	static final double SMALL_VOLUME = 0.5, GREAT_VOLUME = 4.0;
	static final List<String> GREAT_NAMES = List.of("elephant", "orca", "bison", "whale", "mammoth", "mastodon", "rhino", "hippo", "buffalo", "moose");
	/** The great beasts' extra health and a small one's loss. */
	static final double GREAT_HEALTH = 8.0, SMALL_HEALTH = -2.0;
	/** Name fragment tables: a beast whose entity path holds one has the trait. */
	private static final Map<Trait, List<String>> NAMES = new EnumMap<>(Trait.class);
	/** Climbing and galloping step up this much; a butt knocks back this much more; a soft landing is this far safe. */
	static final double STEP_UP = 1.0, GALLOP_STEP = 0.5, BUTT_KNOCKBACK = 1.5, SOFT_FALL = 8.0;
	/** Wall-climb and lava-float speeds (blocks a tick). */
	static final double CLIMB_SPEED = 0.22, LAVA_RISE = 0.18;
	/** Bats in the light: the light level from which they are weak. */
	static final int DARK_LIGHT = 11;
	/** A long body: the stand-ins behind, the ticks of the path between them, the path kept, and each one's loss of size. */
	static final int TAIL_LENGTH = 5, TAIL_GAP = 4, TAIL_KEEP = 40;
	static final double TAIL_THINNING = -0.1;
	/** The squeeze: how long (ticks) a struck foe stays held, how near, how often it is hurt (ticks) and for how much. */
	static final int SQUEEZE_WINDOW = 60, SQUEEZE_EVERY = 20;
	static final double SQUEEZE_RANGE = 3.0;
	static final float SQUEEZE_DAMAGE = 2.0F;
	/** What is left out (people, spirits). */
	private static final Set<String> LEFT_OUT = Set.of("allay", "villager", "wandering_trader", "zombie_villager", "copper_golem", "player");
	private static final Set<MobCategory> BEASTS = Set.of(MobCategory.CREATURE, MobCategory.AMBIENT, MobCategory.WATER_CREATURE,
		MobCategory.WATER_AMBIENT, MobCategory.UNDERGROUND_WATER_CREATURE, MobCategory.AXOLOTLS);
	/** Entities that were put in the world for a shape or a call and must not outlast the game that made them. */
	static final String TEMP_PREFIX = "jaymoskingdoms_druid_temp:";
	/** This run's mark: what was marked in an earlier run is a leftover. */
	static final String TEMP_TAG = TEMP_PREFIX + Long.toHexString(System.nanoTime());

	private static final Identifier SCALE_ID = JaymosKingdoms.id("druid_beast_scale"), STEP_ID = JaymosKingdoms.id("druid_beast_step"),
		KNOCK_ID = JaymosKingdoms.id("druid_beast_butt"), FALL_ID = JaymosKingdoms.id("druid_beast_fall");

	static {
		NAMES.put(Trait.FLY, List.of("bat", "bee", "parrot", "bird", "eagle", "hawk", "owl", "falcon", "crow", "raven", "pigeon", "gull", "butterfly",
			"dragonfly", "moth", "heron", "stork", "vulture", "condor", "bluejay", "finch"));
		NAMES.put(Trait.SWIM, List.of("fish", "cod", "salmon", "tropical", "puffer", "eel", "shark", "dolphin", "orca", "whale", "squid", "axolotl",
			"seal", "otter", "piranha", "ray", "crocodile", "gator", "alligator", "turtle", "trout", "bass", "tuna", "carp", "lamprey", "walrus"));
		NAMES.put(Trait.LEAP, List.of("frog", "toad", "rabbit", "hare", "kangaroo", "grasshopper", "cricket", "flea", "gazelle", "springbok"));
		NAMES.put(Trait.SOFT, List.of("cat", "ocelot", "lynx", "lion", "tiger", "panther", "leopard", "cougar", "puma", "cheetah", "bobcat", "jaguar"));
		NAMES.put(Trait.NIGHT, List.of("cat", "ocelot", "lynx", "lion", "tiger", "panther", "leopard", "cougar", "puma", "bobcat", "jaguar", "owl",
			"bat", "fox", "wolf"));
		NAMES.put(Trait.CLIMB, List.of("goat", "ibex", "bighorn", "chamois", "mountain", "squirrel", "monkey", "ape", "lemur", "sloth", "raccoon"));
		NAMES.put(Trait.BUTT, List.of("goat", "ibex", "bighorn", "ram", "bison", "buffalo", "rhino", "moose", "elk", "boar", "hog", "bull", "yak"));
		NAMES.put(Trait.DIG, List.of("fox", "mole", "badger", "armadillo", "wolverine", "meerkat", "groundhog", "aardvark", "gopher", "prairie", "vole", "mouse"));
		NAMES.put(Trait.GALLOP, List.of("horse", "donkey", "mule", "zebra", "camel", "gazelle", "antelope", "pony", "ostrich", "wildebeest", "okapi", "llama"));
		NAMES.put(Trait.WALLCLIMB, List.of("spider", "tarantula", "gecko", "lizard", "ant", "beetle", "snail", "centipede", "salamander", "iguana", "chameleon"));
		NAMES.put(Trait.LAVA, List.of("strider", "magma", "lava", "salamander"));
		NAMES.put(Trait.LONG, List.of("snake", "serpent", "crocodile", "gator", "alligator", "eel", "python", "cobra", "viper", "anaconda", "adder", "worm",
			"lamprey", "centipede", "moray", "slug"));
		NAMES.put(Trait.FLOP, List.of("fish", "cod", "salmon", "tropical", "puffer", "eel", "shark", "piranha", "trout", "bass", "tuna", "carp", "lamprey",
			"ray", "squid"));
		NAMES.put(Trait.DARK, List.of("bat"));
	}

	/** A long body's stand-ins behind its head, and the path they follow. */
	private static final class Tail {
		final List<Mob> parts = new ArrayList<>();
		final ArrayDeque<Vec3> path = new ArrayDeque<>();
	}

	private static final Map<UUID, Tail> TAILS = new HashMap<>();
	private static final Map<EntityType<?>, Set<Trait>> TRAITS = new HashMap<>();

	private BeastForms() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(BeastForms::tick);
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			// (what a crash left behind: a shape's tail, a called beast)
			for (String tag : entity.entityTags()) {
				if (tag.startsWith(TEMP_PREFIX) && !tag.equals(TEMP_TAG)) {
					entity.discard();
					break;
				}
			}
		});
	}

	static void clear() {
		TAILS.clear();
	}

	// ---- which beasts ---------------------------------------------------------------------------------------------------------

	public static boolean isMob(String form) {
		return form.startsWith(PREFIX);
	}

	public static String form(EntityType<?> type) {
		return PREFIX + BuiltInRegistries.ENTITY_TYPE.getKey(type);
	}

	/** The entity type of a {@code "mob:"} form, or null. */
	public static @Nullable EntityType<?> typeOf(String form) {
		Identifier id = isMob(form) ? Identifier.tryParse(form.substring(PREFIX.length())) : null;
		return id == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
	}

	/** Whether {@code type} is a beast a Druid could wear: an animal that has no shape of its own already. */
	static boolean eligible(EntityType<?> type) {
		Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
		if (!BEASTS.contains(type.getCategory()) || LEFT_OUT.contains(id.getPath())) {
			return false;
		}
		for (List<String> kin : Druids.KIN.values()) {
			if (kin.contains(id.getPath())) {
				return false;
			}
		}
		return true;
	}

	/** Whether {@code e} is a living beast of an eligible kind (never a monster, a person or a Folk). */
	static boolean eligible(Entity e) {
		return e instanceof Mob && !(e instanceof Enemy) && !(e instanceof Npc) && !(e instanceof Folk) && eligible(e.getType());
	}

	/** The form watching {@code e} counts toward, or null if it is not a beast to wear. */
	static @Nullable String watchForm(Entity e) {
		return eligible(e) ? form(e.getType()) : null;
	}

	/** The stand-in for a {@code "mob:"} form, or null (not a beast, or its mod is gone). */
	static @Nullable Mob standIn(ServerLevel level, String form) {
		EntityType<?> type = typeOf(form);
		return type != null && eligible(type) && type.create(level, EntitySpawnReason.EVENT) instanceof Mob mob && eligible(mob) ? mob : null;
	}

	/** The beasts {@code player} has studied (watched through, or one star of foe knowledge), by name. */
	static List<String> studied(Player player) {
		Map<String, Integer> watched = player.getAttachedOrElse(Druids.WATCHED, Map.<String, Integer>of());
		List<String> out = new ArrayList<>();
		for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
			if (!eligible(type)) {
				continue;
			}
			String form = form(type);
			if (player.isCreative() || watched.getOrDefault(form, 0) >= Druids.STUDY_TICKS
				|| FoeStudy.stars(player, BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath()) >= 1) {
				out.add(form);
			}
		}
		out.sort(Comparator.comparing(f -> f));
		return out;
	}

	// ---- how great ------------------------------------------------------------------------------------------------------------

	public static Size size(String form) {
		EntityType<?> type = typeOf(form);
		if (type == null) {
			return Size.SMALL;
		}
		String path = BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath();
		double volume = type.getWidth() * type.getWidth() * type.getHeight();
		if (volume >= GREAT_VOLUME || GREAT_NAMES.stream().anyMatch(path::contains)) {
			return Size.GREAT;
		}
		return volume < SMALL_VOLUME ? Size.SMALL : Size.MID;
	}

	/** Whether {@code player}'s rank in the tree lets them wear {@code form}: small ones always, middling ones with Fox and Stag, great ones with the Circle of the Moon. */
	static boolean allowed(Player player, String form) {
		if (player.isCreative() || !isMob(form)) {
			return true;
		}
		return switch (size(form)) {
			case SMALL -> true;
			case MID -> WildShapes.has(player, WildShapes.FOXSTAG) || WildShapes.has(player, WildShapes.MOON) || WildShapes.has(player, WildShapes.LAND)
				|| WildShapes.has(player, Spores.KEY);
			case GREAT -> WildShapes.has(player, WildShapes.MOON);
		};
	}

	// ---- what it gives --------------------------------------------------------------------------------------------------------

	static Set<Trait> traits(String form) {
		EntityType<?> type = typeOf(form);
		return type == null ? Set.of() : TRAITS.computeIfAbsent(type, BeastForms::work);
	}

	static boolean has(String form, Trait trait) {
		return isMob(form) && traits(form).contains(trait);
	}

	private static Set<Trait> work(EntityType<?> type) {
		String path = BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath();
		Set<Trait> out = EnumSet.noneOf(Trait.class);
		for (Map.Entry<Trait, List<String>> e : NAMES.entrySet()) {
			if (e.getValue().stream().anyMatch(path::contains)) {
				out.add(e.getKey());
			}
		}
		if (type.getCategory() == MobCategory.WATER_CREATURE || type.getCategory() == MobCategory.WATER_AMBIENT
			|| type.getCategory() == MobCategory.UNDERGROUND_WATER_CREATURE) {
			out.add(Trait.SWIM);
		}
		if (type.getCategory() == MobCategory.WATER_AMBIENT) {
			out.add(Trait.FLOP);
		}
		return out;
	}

	/** The extra health a size gives (the great are tough, the small frail). */
	static double health(String form) {
		return switch (size(form)) {
			case GREAT -> GREAT_HEALTH;
			case SMALL -> SMALL_HEALTH;
			default -> 0.0;
		};
	}

	/** Whether the form's wings lift: the hawk's way ({@link Druids}). */
	static boolean flies(String form) {
		return has(form, Trait.FLY);
	}

	/** A shape is taken: the beast's own height is the Druid's, a long body grows its tail, and what it has in its feet and head. */
	static void taken(ServerPlayer player, ServerLevel level, String form, Mob beast) {
		Set<Trait> traits = traits(form);
		double scale = Math.max(SCALE_MIN, Math.min(SCALE_MAX, typeOf(form).getHeight() / PLAYER_HEIGHT));
		modifier(player, Attributes.SCALE, SCALE_ID, scale - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		if (traits.contains(Trait.CLIMB)) {
			modifier(player, Attributes.STEP_HEIGHT, STEP_ID, STEP_UP, AttributeModifier.Operation.ADD_VALUE);
		} else if (traits.contains(Trait.GALLOP)) {
			modifier(player, Attributes.STEP_HEIGHT, STEP_ID, GALLOP_STEP, AttributeModifier.Operation.ADD_VALUE);
		}
		if (traits.contains(Trait.BUTT)) {
			modifier(player, Attributes.ATTACK_KNOCKBACK, KNOCK_ID, BUTT_KNOCKBACK, AttributeModifier.Operation.ADD_VALUE);
		}
		if (traits.contains(Trait.SOFT)) {
			modifier(player, Attributes.SAFE_FALL_DISTANCE, FALL_ID, SOFT_FALL, AttributeModifier.Operation.ADD_VALUE);
		}
		if (traits.contains(Trait.LONG)) {
			Tail tail = new Tail();
			for (int i = 0; i < TAIL_LENGTH; i++) {
				Mob part = Druids.stand(level, form);
				if (part == null) {
					break;
				}
				part.addTag(TEMP_TAG);
				modifier(part, Attributes.SCALE, SCALE_ID, TAIL_THINNING * (i + 1), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
				part.setPos(player.getX(), player.getY(), player.getZ());
				level.addFreshEntity(part);
				tail.parts.add(part);
			}
			TAILS.put(player.getUUID(), tail);
		}
	}

	/** The shape is shed: the body's own size and strides back, the tail gone. */
	static void shed(ServerPlayer player) {
		for (var e : List.of(Map.entry(Attributes.SCALE, SCALE_ID), Map.entry(Attributes.STEP_HEIGHT, STEP_ID), Map.entry(Attributes.ATTACK_KNOCKBACK, KNOCK_ID),
			Map.entry(Attributes.SAFE_FALL_DISTANCE, FALL_ID))) {
			AttributeInstance a = player.getAttribute(e.getKey());
			if (a != null) {
				a.removeModifier(e.getValue());
			}
		}
		Tail tail = TAILS.remove(player.getUUID());
		if (tail != null) {
			tail.parts.forEach(Entity::discard);
		}
	}

	private static void modifier(LivingEntity e, Holder<Attribute> attribute, Identifier id, double amount, AttributeModifier.Operation op) {
		AttributeInstance a = e.getAttribute(attribute);
		if (a != null) {
			a.removeModifier(id);
			a.addTransientModifier(new AttributeModifier(id, amount, op));
		}
	}

	/** A shape's strengths and weaknesses, put on for a moment more (so they lapse by themselves). */
	static void buffs(ServerPlayer player, String form) {
		Set<Trait> traits = traits(form);
		if (traits.contains(Trait.FLY) || traits.contains(Trait.SOFT) && !traits.contains(Trait.NIGHT)) {
			player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 60, 0, true, false, false));
		}
		if (traits.contains(Trait.SWIM)) {
			player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 60, 0, true, false, false));
			if (player.isInWater()) {
				player.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 60, 1, true, false, false));
			} else if (traits.contains(Trait.FLOP)) {
				// a fish out of water flops
				player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 3, true, false, false));
				player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 1, true, false, false));
			}
		}
		if (traits.contains(Trait.LEAP)) {
			player.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, 60, 2, true, false, false));
		}
		if (traits.contains(Trait.NIGHT)) {
			player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 260, 0, true, false, false));
		}
		if (traits.contains(Trait.CLIMB)) {
			player.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, 60, 1, true, false, false));
		}
		if (traits.contains(Trait.BUTT)) {
			player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 60, 0, true, false, false));
		}
		if (traits.contains(Trait.DIG)) {
			player.addEffect(new MobEffectInstance(MobEffects.HASTE, 60, 1, true, false, false));
		}
		if (traits.contains(Trait.GALLOP)) {
			player.addEffect(new MobEffectInstance(MobEffects.SPEED, 60, 1, true, false, false));
		}
		if (traits.contains(Trait.LAVA)) {
			player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 60, 0, true, false, false));
		}
		if (traits.contains(Trait.DARK) && player.level().getMaxLocalRawBrightness(player.blockPosition()) >= DARK_LIGHT) {
			// a bat in the light
			player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 1, true, false, false));
			player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 0, true, false, false));
		}
		switch (size(form)) {
			case GREAT -> {
				player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 60, 1, true, false, false));
				player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 60, 0, true, false, false));
				player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 0, true, false, false));
			}
			case SMALL -> player.addEffect(new MobEffectInstance(MobEffects.SPEED, 60, 0, true, false, false));
			default -> {
			}
		}
	}

	/** Whether {@code e} is a part of some Druid's long body. */
	static boolean isTail(UUID owner, Entity e) {
		Tail tail = TAILS.get(owner);
		return tail != null && tail.parts.contains(e);
	}

	static boolean isTail(Entity e) {
		for (Tail tail : TAILS.values()) {
			if (tail.parts.contains(e)) {
				return true;
			}
		}
		return false;
	}

	// ---- every tick -----------------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			String form = player.getAttachedOrElse(Druids.FORM, "");
			if (!isMob(form) || !player.isAlive()) {
				continue;
			}
			Set<Trait> traits = traits(form);
			if (traits.contains(Trait.WALLCLIMB) && player.horizontalCollision && !player.isShiftKeyDown()) {
				Vec3 v = player.getDeltaMovement();
				player.setDeltaMovement(v.x, Math.max(v.y, CLIMB_SPEED), v.z);
				player.needsSync = true;
				player.fallDistance = 0.0;
			}
			if (traits.contains(Trait.LAVA) && player.isInLava()) {
				Vec3 v = player.getDeltaMovement();
				player.setDeltaMovement(v.x, Math.max(v.y, LAVA_RISE), v.z);
				player.needsSync = true;
			}
			Tail tail = TAILS.get(player.getUUID());
			if (tail != null) {
				bend(player, tail);
				if ((server.getTickCount() + player.getId()) % SQUEEZE_EVERY == 0) {
					squeeze(player);
				}
			}
		}
	}

	/** The tail follows the path the head has walked, each part a little further back, turned along it. */
	private static void bend(ServerPlayer player, Tail tail) {
		tail.path.addFirst(player.position());
		while (tail.path.size() > TAIL_KEEP) {
			tail.path.removeLast();
		}
		Vec3[] path = tail.path.toArray(new Vec3[0]);
		for (int i = 0; i < tail.parts.size(); i++) {
			Mob part = tail.parts.get(i);
			if (part.isRemoved()) {
				continue;
			}
			int at = Math.min(path.length - 1, (i + 1) * TAIL_GAP);
			Vec3 here = path[at], ahead = path[Math.max(0, at - TAIL_GAP)];
			part.setPos(here.x, here.y, here.z);
			Vec3 d = ahead.subtract(here);
			float yaw = d.horizontalDistanceSqr() > 1.0E-4 ? (float) (Math.toDegrees(Math.atan2(-d.x, d.z))) : player.getYRot();
			part.setYRot(yaw);
			part.setYBodyRot(yaw);
			part.setYHeadRot(yaw);
			part.setOnGround(player.onGround());
		}
	}

	/** What a long body strikes it holds and squeezes: slowed to a crawl and hurt every second while it is near. */
	private static void squeeze(ServerPlayer player) {
		LivingEntity foe = player.getLastHurtMob();
		if (foe == null || !foe.isAlive() || player.tickCount - player.getLastHurtMobTimestamp() > SQUEEZE_WINDOW || foe.distanceTo(player) > SQUEEZE_RANGE
			|| !(player.level() instanceof ServerLevel level)) {
			return;
		}
		foe.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 5, false, false, false));
		DamageSource source = player.damageSources().playerAttack(player);
		foe.hurtServer(level, source, SQUEEZE_DAMAGE);
		level.sendParticles(ParticleTypes.CRIT, foe.getX(), foe.getY(0.5), foe.getZ(), 6, 0.3, 0.4, 0.3, 0.05);
		level.playSound(null, foe.blockPosition(), SoundEvents.BONE_BLOCK_HIT, SoundSource.PLAYERS, 0.8F, 0.6F);
	}
}
