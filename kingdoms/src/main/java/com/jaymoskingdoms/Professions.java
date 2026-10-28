package com.jaymoskingdoms;

import com.google.common.collect.ImmutableSet;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.21) The villager professions of a settlement's workers (builder, farmer, miner, woodcutter), so an MCA
 * villager promoted to the work shows it: in their name tag, their clothes, MCA's family tree. No job site takes
 * anyone on as one; only the settlement (or a player, through MCA's Professions menu) does.
 */
public final class Professions {
	/** The jobs, by name (MCA's Professions menu sends "profession.&lt;name&gt;"). */
	public static final List<String> JOBS = List.of("builder", "farmer", "miner", "woodcutter", "puller", "stablehand", "farrier");

	public static VillagerProfession BUILDER, FARMER, MINER, WOODCUTTER, PULLER, STABLEHAND, FARRIER;
	/** (0.5.53) A kingdom's court wizard: one of its elders, never a player's pick ({@link CourtWizards}). */
	public static VillagerProfession WIZARD;
	/** (0.7.291) A court's necromancer, a camp's shaman and blood witch: villagers in the office ({@link Casters}), never a player's pick. */
	public static VillagerProfession NECROMANCER, SHAMAN, BLOOD_WITCH;

	private Professions() {
	}

	static void register() {
		BUILDER = register("builder", SoundEvents.VILLAGER_WORK_MASON);
		FARMER = register("farmer", SoundEvents.VILLAGER_WORK_FARMER);
		MINER = register("miner", SoundEvents.VILLAGER_WORK_TOOLSMITH);
		WOODCUTTER = register("woodcutter", SoundEvents.VILLAGER_WORK_FLETCHER);
		// (0.5.26)
		PULLER = register("puller", SoundEvents.VILLAGER_WORK_LEATHERWORKER);
		// (0.5.47)
		STABLEHAND = register("stablehand", SoundEvents.VILLAGER_WORK_SHEPHERD);
		FARRIER = register("farrier", SoundEvents.VILLAGER_WORK_ARMORER);
		// (0.5.53)
		WIZARD = register("wizard", SoundEvents.VILLAGER_WORK_CLERIC);
		// (0.7.291)
		NECROMANCER = register("necromancer", SoundEvents.VILLAGER_WORK_CLERIC);
		SHAMAN = register("shaman", SoundEvents.VILLAGER_WORK_LEATHERWORKER);
		BLOOD_WITCH = register("blood_witch", SoundEvents.VILLAGER_WORK_CLERIC);
	}

	private static VillagerProfession register(String name, SoundEvent sound) {
		Identifier id = JaymosKingdoms.id(name);
		VillagerProfession profession = new VillagerProfession(Component.translatable("entity.minecraft.villager." + id.getNamespace() + "." + name),
			PoiType.NONE, PoiType.NONE, ImmutableSet.of(), ImmutableSet.of(), sound, Int2ObjectMap.ofEntries());
		return Registry.register(BuiltInRegistries.VILLAGER_PROFESSION, id, profession);
	}

	/** The profession for {@code role} (a builder's for the foreman too), or null if it isn't a worker's. */
	public static @Nullable VillagerProfession of(int role) {
		if (Folk.isBuilder(role)) {
			return BUILDER;
		}
		return role == Folk.FARMER ? FARMER : role == Folk.MINER ? MINER : role == Folk.WOODCUTTER ? WOODCUTTER : role == Folk.PULLER ? PULLER
			: role == Folk.STABLEHAND ? STABLEHAND : role == Folk.FARRIER ? FARRIER : role == Sorcery.WIZARD ? WIZARD
			: role == Sorcery.NECROMANCER ? NECROMANCER : role == Sorcery.SHAMAN || role == Orcs.SHAMAN ? SHAMAN : role == Sorcery.BLOOD_WITCH ? BLOOD_WITCH : null;
	}

	/** The role {@code profession} is (a builder's is {@link Folk#WORKER}), or -1 if it isn't one of these. */
	public static int role(Holder<VillagerProfession> profession) {
		return role(profession.value());
	}

	public static int role(VillagerProfession profession) {
		if (profession == BUILDER) {
			return Folk.WORKER;
		}
		return profession == FARMER ? Folk.FARMER : profession == MINER ? Folk.MINER : profession == WOODCUTTER ? Folk.WOODCUTTER
			: profession == PULLER ? Folk.PULLER : profession == STABLEHAND ? Folk.STABLEHAND : profession == FARRIER ? Folk.FARRIER
			: profession == WIZARD ? Sorcery.WIZARD : profession == NECROMANCER ? Sorcery.NECROMANCER : profession == SHAMAN ? Sorcery.SHAMAN
			: profession == BLOOD_WITCH ? Sorcery.BLOOD_WITCH : -1;
	}

	/** The role a job's name (one of {@link #JOBS}) is, or -1. */
	public static int role(String job) {
		return switch (job) {
			case "builder" -> Folk.WORKER;
			case "farmer" -> Folk.FARMER;
			case "miner" -> Folk.MINER;
			case "woodcutter" -> Folk.WOODCUTTER;
			case "puller" -> Folk.PULLER;
			case "stablehand" -> Folk.STABLEHAND;
			case "farrier" -> Folk.FARRIER;
			default -> -1;
		};
	}

	/** The look MCA dresses one of these in (the nearest of its own and vanilla's), or null if {@code profession} isn't one. */
	public static @Nullable String clothes(String profession) {
		return switch (profession) {
			case "jaymoskingdoms:builder" -> "minecraft:mason";
			case "jaymoskingdoms:farmer" -> "minecraft:farmer";
			case "jaymoskingdoms:miner" -> "mca:miner";
			case "jaymoskingdoms:woodcutter" -> "mca:lumberjack";
			case "jaymoskingdoms:puller" -> "minecraft:leatherworker";
			case "jaymoskingdoms:stablehand" -> "minecraft:shepherd";
			case "jaymoskingdoms:farrier" -> "minecraft:armorer";
			case "jaymoskingdoms:wizard" -> "minecraft:cleric";
			default -> null;
		};
	}
}
