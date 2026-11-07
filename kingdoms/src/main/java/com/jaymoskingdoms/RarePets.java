package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.chicken.ChickenVariants;
import net.minecraft.world.entity.animal.cow.MushroomCow;
import net.minecraft.world.entity.animal.fox.Fox;
import net.minecraft.world.entity.animal.frog.FrogVariants;
import net.minecraft.world.entity.animal.parrot.Parrot;
import net.minecraft.world.entity.animal.pig.PigVariants;
import net.minecraft.world.entity.animal.rabbit.Rabbit;
import net.minecraft.world.entity.animal.wolf.WolfVariants;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.436, Motion patch 7) Rare pets. Andy: "Some villagers will keep rare pets or animals as they found them from a
 * different biome far away and will brag about it as some sort of accomplishment." A realm's settlement of
 * {@link #GROWN_FOR} grown or more comes, in time, to have one villager keeping an animal from far off: a snow fox or a
 * snowy wolf where it's warm, a golden rabbit, an armadillo or a jungle parrot where it's cold, a brown mooshroom
 * anywhere. Dogs and parrots are tamed to their keeper and trail them; the rest keep about the spot where their
 * keeper took them in. A player who comes near the keeper with the animal about is bragged to (a bubble, once a day),
 * and now and then told an old tale instead (hints at hidden name-tag looks; no screen text says what they are). A
 * keeper who dies leaves it to a neighbour; one whose rare pet dies mourns it.
 */
final class RarePets {
	/** How often rare pets are thought about (ticks); how far round a seat they are looked for. */
	static final int EVERY = 40;
	static final double WATCHED = 96.0;
	/** A settlement this many grown or more keeps one rare pet. */
	static final int GROWN_FOR = 8;
	/** How often a settlement without one may come by one (ticks), and the chance it does. */
	static final long LOOK_TICKS = 12000L;
	static final float FIND_CHANCE = 0.35F;
	/** How far round its keeper's spot an animal that isn't tamed keeps; how far off it turns up. */
	static final int HOME_RADIUS = 6, TURNS_UP = 4;
	/** Bragging: how near the keeper a player must come, how near the animal must be, how often per player (ticks). */
	static final double BRAG_NEAR = 5.0, PET_NEAR = 16.0;
	static final long BRAG_EVERY = 24000L;
	/** Lines (lang {@code rarepet.jaymoskingdoms.brag.N}, {@code .hint.N}); one in this many talks after the first is a tale. */
	static final int BRAGS = 5, HINTS = 5, HINT_ODDS = 3;
	static final int JOY = 4, GRIEF = -20;
	static final double SPEECH_RANGE = 12.0, MOURN_RANGE = 48.0;

	enum Climate { COLD, MILD, HOT }

	/** A rare animal: what it's called, where it's from, where it's rare, and how it's made. */
	record Kind(String key, String from, List<Climate> rareIn, EntityType<? extends Mob> type, BiConsumer<ServerLevel, Mob> dress) {
	}

	static final List<Kind> KINDS = List.of(
		new Kind("snow_fox", "north", List.of(Climate.MILD, Climate.HOT), EntityTypes.FOX,
			(level, m) -> m.setComponent(DataComponents.FOX_VARIANT, Fox.Variant.SNOW)),
		new Kind("snowy_wolf", "north", List.of(Climate.MILD, Climate.HOT), EntityTypes.WOLF,
			(level, m) -> m.setComponent(DataComponents.WOLF_VARIANT, level.registryAccess().lookupOrThrow(Registries.WOLF_VARIANT).getOrThrow(WolfVariants.SNOWY))),
		new Kind("white_rabbit", "north", List.of(Climate.MILD, Climate.HOT), EntityTypes.RABBIT,
			(level, m) -> m.setComponent(DataComponents.RABBIT_VARIANT, Rabbit.Variant.WHITE)),
		new Kind("cold_frog", "north", List.of(Climate.MILD, Climate.HOT), EntityTypes.FROG,
			(level, m) -> m.setComponent(DataComponents.FROG_VARIANT, level.registryAccess().lookupOrThrow(Registries.FROG_VARIANT).getOrThrow(FrogVariants.COLD))),
		new Kind("cold_pig", "north", List.of(Climate.HOT), EntityTypes.PIG,
			(level, m) -> m.setComponent(DataComponents.PIG_VARIANT, level.registryAccess().lookupOrThrow(Registries.PIG_VARIANT).getOrThrow(PigVariants.COLD))),
		new Kind("gold_rabbit", "desert", List.of(Climate.COLD, Climate.MILD), EntityTypes.RABBIT,
			(level, m) -> m.setComponent(DataComponents.RABBIT_VARIANT, Rabbit.Variant.GOLD)),
		new Kind("armadillo", "savanna", List.of(Climate.COLD, Climate.MILD), EntityTypes.ARMADILLO, (level, m) -> { }),
		new Kind("spotted_wolf", "savanna", List.of(Climate.COLD, Climate.MILD), EntityTypes.WOLF,
			(level, m) -> m.setComponent(DataComponents.WOLF_VARIANT, level.registryAccess().lookupOrThrow(Registries.WOLF_VARIANT).getOrThrow(WolfVariants.SPOTTED))),
		new Kind("parrot", "jungle", List.of(Climate.COLD, Climate.MILD), EntityTypes.PARROT,
			(level, m) -> m.setComponent(DataComponents.PARROT_VARIANT, Parrot.Variant.values()[level.getRandom().nextInt(Parrot.Variant.values().length)])),
		new Kind("rusty_wolf", "jungle", List.of(Climate.COLD, Climate.MILD), EntityTypes.WOLF,
			(level, m) -> m.setComponent(DataComponents.WOLF_VARIANT, level.registryAccess().lookupOrThrow(Registries.WOLF_VARIANT).getOrThrow(WolfVariants.RUSTY))),
		new Kind("warm_frog", "mangroves", List.of(Climate.COLD, Climate.MILD), EntityTypes.FROG,
			(level, m) -> m.setComponent(DataComponents.FROG_VARIANT, level.registryAccess().lookupOrThrow(Registries.FROG_VARIANT).getOrThrow(FrogVariants.WARM))),
		new Kind("warm_chicken", "south", List.of(Climate.COLD), EntityTypes.CHICKEN,
			(level, m) -> m.setComponent(DataComponents.CHICKEN_VARIANT, level.registryAccess().lookupOrThrow(Registries.CHICKEN_VARIANT).getOrThrow(ChickenVariants.WARM))),
		new Kind("brown_mooshroom", "mushroom_isles", List.of(Climate.COLD, Climate.MILD, Climate.HOT), EntityTypes.MOOSHROOM,
			(level, m) -> m.setComponent(DataComponents.MOOSHROOM_VARIANT, MushroomCow.Variant.BROWN)));

	/** A rare pet: its keeper, its kind, the spot it keeps about, and its realm. */
	record Rare(UUID keeper, String kind, BlockPos home, int realm) {
		static final Codec<Rare> CODEC = RecordCodecBuilder.create(i -> i.group(
			UUIDUtil.CODEC.fieldOf("keeper").forGetter(Rare::keeper),
			Codec.STRING.fieldOf("kind").forGetter(Rare::kind),
			BlockPos.CODEC.fieldOf("home").forGetter(Rare::home),
			Codec.INT.fieldOf("realm").forGetter(Rare::realm)).apply(i, Rare::new));
	}

	static final AttachmentType<Rare> RARE = AttachmentRegistry.create(JaymosKingdoms.id("rare_pet"),
		builder -> builder.persistent(Rare.CODEC));

	/** Per realm: when it last looked for a rare pet. */
	private static final Map<Integer, Long> LOOKED = new HashMap<>();
	/** Per player and keeper: when they were last bragged to. */
	private static final Map<String, Long> BRAGGED = new HashMap<>();

	private RarePets() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(RarePets::tick);
		ServerLivingEntityEvents.AFTER_DEATH.register(RarePets::died);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			LOOKED.clear();
			BRAGGED.clear();
		});
	}

	static Climate climate(ServerLevel level, BlockPos at) {
		float t = level.getBiome(at).value().getBaseTemperature();
		return t < 0.3F ? Climate.COLD : t >= 0.95F ? Climate.HOT : Climate.MILD;
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		if (now % EVERY != 0) {
			return;
		}
		for (Faction f : Factions.all(server)) {
			if (f.fallen() || !f.type().isRealm() || !level.isPositionEntityTicking(f.seat())) {
				continue;
			}
			AABB box = new AABB(f.seat()).inflate(WATCHED);
			List<Mob> pets = level.getEntitiesOfClass(Mob.class, box, m -> m.isAlive() && m.hasAttached(RARE));
			if (now - LOOKED.getOrDefault(f.id(), -LOOK_TICKS) >= LOOK_TICKS) {
				LOOKED.put(f.id(), now);
				find(level, f, box, pets);
			}
			for (Mob pet : pets) {
				Rare rare = pet.getAttached(RARE);
				if (!(level.getEntity(rare.keeper()) instanceof Villager keeper) || !keeper.isAlive()) {
					continue;
				}
				if (!(pet instanceof TamableAnimal) && pet instanceof PathfinderMob walker && !walker.hasHome()) {
					walker.setHomeTo(rare.home(), HOME_RADIUS);
				}
				brag(level, keeper, pet, rare, now);
			}
		}
	}

	/** A big enough settlement without a rare pet may come by one; one whose keeper died goes to a neighbour. */
	private static void find(ServerLevel level, Faction f, AABB box, List<Mob> pets) {
		RandomSource random = level.getRandom();
		List<Villager> grown = level.getEntitiesOfClass(Villager.class, box, v -> v.isAlive() && !v.isBaby() && !(v instanceof Worker w && w.role() >= 0));
		for (Mob pet : pets) {
			Rare rare = pet.getAttached(RARE);
			if (!(level.getEntity(rare.keeper()) instanceof Villager keeper) || !keeper.isAlive()) {
				if (!grown.isEmpty()) {
					keep(level, f, grown.get(random.nextInt(grown.size())), pet, rare.kind());
				}
				return;
			}
		}
		if (!pets.isEmpty() || grown.size() < GROWN_FOR || random.nextFloat() >= FIND_CHANCE) {
			return;
		}
		Climate here = climate(level, f.seat());
		List<Kind> kinds = KINDS.stream().filter(k -> k.rareIn().contains(here)).toList();
		List<Villager> free = grown.stream().filter(v -> Routines.free(v, Routines.WORK_TO)).toList();
		if (kinds.isEmpty() || free.isEmpty()) {
			return;
		}
		Kind kind = kinds.get(random.nextInt(kinds.size()));
		Villager keeper = free.get(random.nextInt(free.size()));
		BlockPos at = Construction.standable(level, keeper.blockPosition().offset(random.nextInt(TURNS_UP * 2 + 1) - TURNS_UP, 0,
			random.nextInt(TURNS_UP * 2 + 1) - TURNS_UP), 3);
		Mob pet = kind.type().spawn(level, at, EntitySpawnReason.MOB_SUMMONED);
		if (pet == null) {
			return;
		}
		kind.dress().accept(level, pet);
		keep(level, f, keeper, pet, kind.key());
		say(level, keeper, "rarepet.jaymoskingdoms.found." + random.nextInt(3), kind);
	}

	private static void keep(ServerLevel level, Faction f, Villager keeper, Mob pet, String kind) {
		BlockPos home = keeper.blockPosition();
		pet.setAttached(RARE, new Rare(keeper.getUUID(), kind, home, f.id()));
		pet.setPersistenceRequired();
		if (pet instanceof TamableAnimal tame) {
			tame.setTame(true, true);
			tame.setOwner(keeper);
			tame.setOrderedToSit(false);
			tame.setInSittingPose(false);
		} else if (pet instanceof PathfinderMob walker) {
			walker.setHomeTo(home, HOME_RADIUS);
		}
		level.sendParticles(ParticleTypes.HEART, pet.getX(), pet.getEyeY() + 0.3, pet.getZ(), 5, 0.3, 0.2, 0.3, 0.0);
		McaCompat.cheer(keeper, JOY);
	}

	/** A player near the keeper with the animal about hears all about it (once a day each), or an old tale. */
	private static void brag(ServerLevel level, Villager keeper, Mob pet, Rare rare, long now) {
		if (keeper.distanceTo(pet) > PET_NEAR || Routines.busy(keeper) || keeper.isSleeping()) {
			return;
		}
		Kind kind = kind(rare.kind());
		if (kind == null) {
			return;
		}
		RandomSource random = level.getRandom();
		for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, keeper.getBoundingBox().inflate(BRAG_NEAR), p -> p.isAlive() && !p.isSpectator())) {
			String key = player.getUUID() + "|" + keeper.getUUID();
			Long last = BRAGGED.get(key);
			if (last != null && now - last < BRAG_EVERY) {
				continue;
			}
			BRAGGED.put(key, now);
			Component words = last != null && random.nextInt(HINT_ODDS) == 0
				? Component.translatable("rarepet.jaymoskingdoms.hint." + random.nextInt(HINTS))
				: Component.translatable("rarepet.jaymoskingdoms.brag." + random.nextInt(BRAGS), name(kind), from(kind));
			keeper.getLookControl().setLookAt(pet);
			Speech.sayTo(level, keeper, player, words, Speech.WHITE);
		}
	}

	/** A rare pet dies: its keeper mourns it. */
	private static void died(LivingEntity dead, DamageSource source) {
		Rare rare = dead.getAttached(RARE);
		if (rare == null || !(dead.level() instanceof ServerLevel level)) {
			return;
		}
		Entity keeper = level.getEntity(rare.keeper());
		if (keeper instanceof Villager v && v.isAlive() && v.distanceTo(dead) <= MOURN_RANGE) {
			McaCompat.cheer(v, GRIEF);
			Speech.say(level, v, Component.translatable("pet.jaymoskingdoms.mourn." + level.getRandom().nextInt(Pets.LINES)), Speech.WHITE, SPEECH_RANGE);
		}
	}

	private static void say(ServerLevel level, Villager v, String key, Kind kind) {
		Speech.say(level, v, Component.translatable(key, name(kind), from(kind)), Speech.WHITE, SPEECH_RANGE);
	}

	private static Component name(Kind kind) {
		return Component.translatable("rarepet.jaymoskingdoms.kind." + kind.key());
	}

	private static Component from(Kind kind) {
		return Component.translatable("rarepet.jaymoskingdoms.from." + kind.from());
	}

	private static @Nullable Kind kind(String key) {
		for (Kind k : KINDS) {
			if (k.key().equals(key)) {
				return k;
			}
		}
		return null;
	}

}
