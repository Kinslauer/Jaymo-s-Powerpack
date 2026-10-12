package com.jaymoskingdoms;

import com.jaymoskingdoms.mixin.BrushableBlockEntityAccessor;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BrushableBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.29) What waits at a quest's ruin, set out when the player who took it comes near: for a fetch, a chest with the
 * keepsake in it and a few of the ruin's own kind standing over it; for a king's quest, a <b>champion</b> (a boss far
 * beyond a camp's chief: hundreds of health, plated, unshoveable, a boss bar) and its escort, which calls more to its side
 * when hurt, rages near its end, and throws back whoever crowds it.
 */
final class QuestFoes {
	/** How near (blocks, flat) the quest's player must come for its cache or champion to be set out. */
	static final double SET_OUT_RANGE = 48.0;
	/** A champion: health (a beast's more), its blows on top of its own, armour, toughness. */
	static final double CHAMPION_HEALTH = 500.0, BEAST_HEALTH = 700.0, CHAMPION_DAMAGE = 10.0, CHAMPION_ARMOR = 20.0, CHAMPION_TOUGHNESS = 10.0;
	/** How far it keeps from its lair, and how far it sees. */
	static final int CHAMPION_HOME_RADIUS = 20;
	static final double CHAMPION_SIGHT = 40.0;
	/** Its escort: how many at first, how many more it calls at {@link #CALL_AT} of its health, and their health. */
	static final int ESCORT = 5, CALLED = 4;
	static final double ESCORT_HEALTH = 60.0;
	/** It calls its escort at this share of its health, and rages at this share (for {@link #RAGE_TICKS}). */
	static final float CALL_AT = 0.6F, RAGE_AT = 0.3F;
	static final int RAGE_TICKS = 20 * 60;
	/** Its shockwave: how often (ticks), how near a foe sets it off, how far it reaches, its damage, its push. */
	static final int SHOCK_TICKS = 160;
	static final double SHOCK_TRIGGER = 4.0, SHOCK_RANGE = 6.0;
	static final float SHOCK_DAMAGE = 8.0F;
	static final double SHOCK_PUSH = 1.4;
	/** With nobody within this of it, it mends this share of its health each second. */
	static final double REST_RANGE = 40.0;
	static final float REST_HEAL = 0.02F;
	/** A cache's guards (and their health). */
	static final int CACHE_GUARDS = 3;
	static final double CACHE_GUARD_HEALTH = 32.0;
	/** A spot in a structure must open onto at least this much free space, so nothing is set out in a sealed pocket. */
	static final int OPEN_SPACE = 40;
	/** How far (blocks, each way) to look about a spot for room when the spot itself is no good. */
	static final int NEAR_REACH = 6, NEAR_TRIES = 40;
	/** Ruins that lie buried: a fetch there is a dig (the keepsake is in their suspicious gravel, to brush out), and no guards. */
	static final Set<String> DIGS = Set.of("minecraft:trail_ruins");
	/** How many of a dig ruin's pieces, and blocks in each, to look through for its suspicious gravel. */
	static final int DIG_PIECES = 32, DIG_CHECKS = 20000;
	/** After a search of a structure finds no room, it's searched again only after this many ticks (it's costly). */
	static final int RETRY_TICKS = 200;

	/** On a champion or one of the quest's guards: the player and quest it's for, whether it's the champion, how far its fight has gone (0, called, raged). */
	record Tag(UUID owner, String quest, boolean champion, int phase) {
		static final Codec<Tag> CODEC = RecordCodecBuilder.create(i -> i.group(
				UUIDUtil.CODEC.fieldOf("owner").forGetter(Tag::owner),
				Codec.STRING.fieldOf("quest").forGetter(Tag::quest),
				Codec.BOOL.optionalFieldOf("champion", false).forGetter(Tag::champion),
				Codec.INT.optionalFieldOf("phase", 0).forGetter(Tag::phase))
			.apply(i, Tag::new));

		Tag withPhase(int phase) {
			return new Tag(owner, quest, champion, phase);
		}
	}

	static final AttachmentType<Tag> FOE = AttachmentRegistry.create(JaymosKingdoms.id("quest_foe"), builder -> builder.persistent(Tag.CODEC));

	/** The champions loaded now, and their bars. */
	private static final Set<Mob> CHAMPIONS = new HashSet<>();
	private static final Map<UUID, ServerBossEvent> BARS = new HashMap<>();
	/** Quests whose structure had no room yet: the tick to look again. */
	private static final Map<String, Long> RETRY = new HashMap<>();

	private QuestFoes() {
	}

	static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			Tag tag = entity.getAttached(FOE);
			if (tag != null && tag.champion() && entity instanceof Mob mob) {
				CHAMPIONS.add(mob);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> forget(entity));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> forget(entity));
		ServerTickEvents.END_SERVER_TICK.register(QuestFoes::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			CHAMPIONS.clear();
			BARS.clear();
		});
	}

	private static void forget(Entity entity) {
		if (entity instanceof Mob mob && CHAMPIONS.remove(mob)) {
			ServerBossEvent bar = BARS.remove(mob.getUUID());
			if (bar != null) {
				bar.removeAllPlayers();
			}
		}
	}

	/** A champion (a boss bar's boss, {@link BossBars}). */
	static boolean champion(Entity entity) {
		Tag tag = entity.getAttached(FOE);
		return tag != null && tag.champion();
	}

	static @Nullable Tag tag(Entity entity) {
		return entity.getAttached(FOE);
	}

	// ---- which champion lairs where ------------------------------------------------------------------------------------

	/** The champion that lairs in the structure {@code id}. */
	static String championFor(String id, RandomSource random) {
		String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
		if (path.equals("pillager_outpost") || path.equals("mansion")) {
			return "warchief";
		} else if (path.equals("desert_pyramid")) {
			return "tomb_lord";
		} else if (path.equals("jungle_pyramid") || path.equals("swamp_hut")) {
			return "stalker";
		} else if (path.equals("igloo")) {
			return "frost";
		} else if (path.equals("stronghold") || path.startsWith("mineshaft") || path.equals("ancient_city") || path.equals("trial_chambers")) {
			return "bone_lord";
		} else if (path.startsWith("ruined_portal")) {
			return "brute";
		}
		return random.nextBoolean() ? "beast" : "plague_knight";
	}

	private static EntityType<? extends Mob> championType(String foe) {
		return switch (foe) {
			case "warchief" -> EntityTypes.VINDICATOR;
			case "tomb_lord" -> EntityTypes.HUSK;
			case "stalker" -> EntityTypes.BOGGED;
			case "frost" -> EntityTypes.STRAY;
			case "bone_lord" -> EntityTypes.WITHER_SKELETON;
			case "brute" -> EntityTypes.PIGLIN_BRUTE;
			case "beast" -> EntityTypes.RAVAGER;
			default -> EntityTypes.ZOMBIE;
		};
	}

	private static EntityType<? extends Mob> escortType(String foe, RandomSource random) {
		return switch (foe) {
			case "warchief" -> random.nextBoolean() ? EntityTypes.PILLAGER : EntityTypes.VINDICATOR;
			case "tomb_lord" -> EntityTypes.HUSK;
			case "stalker" -> random.nextBoolean() ? EntityTypes.BOGGED : EntityTypes.SKELETON;
			case "frost" -> EntityTypes.STRAY;
			case "bone_lord" -> random.nextInt(3) == 0 ? EntityTypes.WITHER_SKELETON : EntityTypes.SKELETON;
			case "brute" -> EntityTypes.PIGLIN_BRUTE;
			case "beast" -> EntityTypes.VINDICATOR;
			default -> EntityTypes.ZOMBIE;
		};
	}

	/** A fetch cache's guards: the ruin's own kind. */
	private static EntityType<? extends Mob> guardType(String structure, RandomSource random) {
		String path = structure.contains(":") ? structure.substring(structure.indexOf(':') + 1) : structure;
		if (path.equals("desert_pyramid")) {
			return EntityTypes.HUSK;
		} else if (path.equals("igloo") || path.contains("snowy")) {
			return EntityTypes.STRAY;
		} else if (path.equals("jungle_pyramid") || path.contains("swamp")) {
			return EntityTypes.BOGGED;
		} else if (path.startsWith("shipwreck") || path.startsWith("ocean_ruin")) {
			return EntityTypes.DROWNED;
		} else if (path.startsWith("mineshaft")) {
			return random.nextBoolean() ? EntityTypes.CAVE_SPIDER : EntityTypes.SKELETON;
		} else if (path.equals("pillager_outpost")) {
			return EntityTypes.PILLAGER;
		} else if (path.startsWith("ruined_portal")) {
			return EntityTypes.ZOMBIFIED_PIGLIN;
		}
		return random.nextBoolean() ? EntityTypes.ZOMBIE : EntityTypes.SKELETON;
	}

	// ---- setting them out ----------------------------------------------------------------------------------------------

	/**
	 * A fetch quest's cache, set out in its ruin: a chest with {@code keepsake} (and a little more), and its guards.
	 * Returns where the chest stands, or null if no room could be found in what's loaded yet.
	 */
	static @Nullable BlockPos cache(ServerLevel level, ServerPlayer player, Quest q, ItemStack keepsake) {
		BlockPos located = q.objective().pos().orElse(null);
		if (located == null) {
			return null;
		}
		if (dig(q)) {
			return bury(level, q, located, keepsake);
		}
		RandomSource random = level.getRandom();
		EntityType<? extends Mob> type = guardType(q.objective().target(), random);
		BlockPos spot = search(level, q, located, type);
		if (spot == null) {
			return null;
		}
		level.setBlock(spot, Blocks.CHEST.defaultBlockState(), 3);
		BlockEntity be = level.getBlockEntity(spot);
		if (be instanceof ChestBlockEntity chest) {
			chest.setItem(13, keepsake);
			chest.setItem(random.nextInt(9), new ItemStack(Items.EMERALD, 1 + random.nextInt(4)));
			chest.setItem(18 + random.nextInt(9), new ItemStack(random.nextBoolean() ? Items.BREAD : Items.BONE, 2 + random.nextInt(4)));
		}
		for (int i = 0; i < CACHE_GUARDS; i++) {
			// (not on the chest itself)
			Mob guard = spawn(level, type, near(level, spot.above(), type, random), q, player, false);
			if (guard != null) {
				health(guard, CACHE_GUARD_HEALTH);
			}
		}
		return spot;
	}

	/** Whether fetch quest {@code q} is a dig: its keepsake buried in a ruin's suspicious gravel. */
	static boolean dig(Quest q) {
		return q.kind() == Quest.FETCH && DIGS.contains(q.objective().target());
	}

	/** A dig's keepsake, put in the suspicious gravel (or sand) nearest the buried ruin's middle; where, or null till some is loaded. */
	private static @Nullable BlockPos bury(ServerLevel level, Quest q, BlockPos located, ItemStack keepsake) {
		long now = level.getGameTime();
		Long next = RETRY.get(q.id());
		if (next != null && now < next) {
			return null;
		}
		BlockPos found = null;
		Identifier id = Identifier.tryParse(q.objective().target());
		Structure structure = id == null ? null : level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValue(id);
		if (structure != null && level.hasChunk(located.getX() >> 4, located.getZ() >> 4)) {
			StructureStart start = level.structureManager().getStartForStructure(structure, level.getChunk(located.getX() >> 4, located.getZ() >> 4));
			if (start != null && start.isValid()) {
				BlockPos middle = start.getBoundingBox().getCenter();
				double best = Double.MAX_VALUE;
				int looked = 0;
				for (StructurePiece piece : start.getPieces()) {
					BoundingBox box = piece.getBoundingBox();
					int checks = 0;
					for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
						if (++checks > DIG_CHECKS) {
							break;
						}
						double d = p.distSqr(middle);
						if (d < best && level.isLoaded(p) && level.getBlockState(p).getBlock() instanceof BrushableBlock
							&& level.getBlockEntity(p) instanceof BrushableBlockEntity) {
							found = p.immutable();
							best = d;
						}
					}
					if (++looked >= DIG_PIECES) {
						break;
					}
				}
			}
		}
		if (found == null || !(level.getBlockEntity(found) instanceof BrushableBlockEntity gravel)) {
			RETRY.put(q.id(), now + RETRY_TICKS);
			return null;
		}
		RETRY.remove(q.id());
		BrushableBlockEntityAccessor hold = (BrushableBlockEntityAccessor)gravel;
		hold.jaymoskingdoms$setLootTable(null);
		hold.jaymoskingdoms$setItem(keepsake);
		gravel.setChanged();
		return found;
	}

	/** A king's champion and its escort, set out in its lair; the champion, or null if no room could be found yet. */
	static @Nullable Mob champion(ServerLevel level, ServerPlayer player, Quest q) {
		BlockPos located = q.objective().pos().orElse(null);
		if (located == null) {
			return null;
		}
		String foe = q.objective().otherName();
		BlockPos spot = search(level, q, located, championType(foe));
		if (spot == null) {
			return null;
		}
		Mob champion = spawn(level, championType(foe), spot, q, player, true);
		if (champion == null) {
			return null;
		}
		outfit(level, champion, foe);
		champion.setHomeTo(spot, CHAMPION_HOME_RADIUS);
		RandomSource random = level.getRandom();
		for (int i = 0; i < ESCORT; i++) {
			escort(level, champion, foe, q, player, random);
		}
		level.playSound(null, spot, SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 3.0F, 0.6F);
		return champion;
	}

	private static void escort(ServerLevel level, Mob champion, String foe, Quest q, ServerPlayer player, RandomSource random) {
		EntityType<? extends Mob> type = escortType(foe, random);
		Mob escort = spawn(level, type, near(level, champion.blockPosition(), type, random), q, player, false);
		if (escort == null) {
			return;
		}
		health(escort, ESCORT_HEALTH);
		attribute(escort, Attributes.ARMOR, 8.0);
		escort.setHomeTo(champion.blockPosition(), CHAMPION_HOME_RADIUS + 8);
		if (escort.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
			// (a helm, so the undead don't burn by day)
			escort.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
			escort.setDropChance(EquipmentSlot.HEAD, 0.0F);
		}
	}

	private static @Nullable Mob spawn(ServerLevel level, EntityType<? extends Mob> type, BlockPos at, Quest q, ServerPlayer player, boolean champion) {
		Mob mob = type.create(level, EntitySpawnReason.EVENT);
		if (mob == null) {
			return null;
		}
		mob.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
		mob.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.EVENT, null);
		mob.setPersistenceRequired();
		mob.setCanPickUpLoot(false);
		if (mob instanceof Raider raider) {
			raider.setCanJoinRaid(false);
			raider.setPatrolLeader(false);
		}
		if (mob instanceof AbstractPiglin piglin) {
			piglin.setImmuneToZombification(true);
		}
		attribute(mob, Attributes.FOLLOW_RANGE, CHAMPION_SIGHT);
		mob.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, MobEffectInstance.INFINITE_DURATION, 0, false, false));
		mob.setAttached(FOE, new Tag(player.getUUID(), q.id(), champion, 0));
		level.addFreshEntity(mob);
		return mob;
	}

	/** A champion's make: its health, blows, plate, a name, and gear that doesn't drop. */
	private static void outfit(ServerLevel level, Mob mob, String foe) {
		health(mob, foe.equals("beast") ? BEAST_HEALTH : CHAMPION_HEALTH);
		AttributeInstance damage = mob.getAttribute(Attributes.ATTACK_DAMAGE);
		if (damage != null) {
			damage.setBaseValue(damage.getBaseValue() + CHAMPION_DAMAGE);
		}
		attribute(mob, Attributes.ARMOR, CHAMPION_ARMOR);
		attribute(mob, Attributes.ARMOR_TOUGHNESS, CHAMPION_TOUGHNESS);
		attribute(mob, Attributes.KNOCKBACK_RESISTANCE, 1.0);
		AttributeInstance speed = mob.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null) {
			speed.setBaseValue(speed.getBaseValue() * 1.15);
		}
		switch (foe) {
			case "warchief" -> {
				gear(mob, EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_AXE));
				gear(mob, EquipmentSlot.HEAD, Raid.getOminousBannerInstance(level.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN)));
			}
			case "tomb_lord" -> {
				gear(mob, EquipmentSlot.MAINHAND, new ItemStack(Items.GOLDEN_SWORD));
				gear(mob, EquipmentSlot.HEAD, new ItemStack(Items.GOLDEN_HELMET));
				gear(mob, EquipmentSlot.CHEST, new ItemStack(Items.GOLDEN_CHESTPLATE));
			}
			case "stalker", "frost" -> {
				ItemStack bow = new ItemStack(Items.BOW);
				Relics.enchantment(level, net.minecraft.world.item.enchantment.Enchantments.POWER).ifPresent(h -> bow.enchant(h, 5));
				gear(mob, EquipmentSlot.MAINHAND, bow);
				gear(mob, EquipmentSlot.HEAD, new ItemStack(Items.CHAINMAIL_HELMET));
			}
			case "bone_lord" -> {
				gear(mob, EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_SWORD));
				gear(mob, EquipmentSlot.HEAD, new ItemStack(Items.NETHERITE_HELMET));
				gear(mob, EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
			}
			case "brute" -> gear(mob, EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_AXE));
			case "plague_knight" -> {
				gear(mob, EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_SWORD));
				gear(mob, EquipmentSlot.HEAD, new ItemStack(Items.NETHERITE_HELMET));
				gear(mob, EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
				gear(mob, EquipmentSlot.LEGS, new ItemStack(Items.NETHERITE_LEGGINGS));
				gear(mob, EquipmentSlot.FEET, new ItemStack(Items.NETHERITE_BOOTS));
			}
			default -> {
			}
		}
		mob.setCustomName(Component.translatable("foe.jaymoskingdoms." + foe));
		mob.setCustomNameVisible(true);
	}

	private static void gear(Mob mob, EquipmentSlot slot, ItemStack stack) {
		mob.setItemSlot(slot, stack);
		mob.setDropChance(slot, 0.0F);
	}

	private static void health(Mob mob, double health) {
		attribute(mob, Attributes.MAX_HEALTH, health);
		mob.setHealth(mob.getMaxHealth());
	}

	private static void attribute(Mob mob, Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, double value) {
		AttributeInstance instance = mob.getAttribute(attribute);
		if (instance != null) {
			instance.setBaseValue(value);
		}
	}

	/** A spot a few blocks from {@code at} with room for a {@code type}, or {@code at} itself. */
	private static BlockPos near(ServerLevel level, BlockPos at, EntityType<?> type, RandomSource random) {
		BlockPos found = around(level, at, type, random);
		return found != null ? found : at;
	}

	private static @Nullable BlockPos around(ServerLevel level, BlockPos at, EntityType<?> type, RandomSource random) {
		for (int tries = 0; tries < NEAR_TRIES; tries++) {
			int reach = tries < NEAR_TRIES / 2 ? NEAR_REACH / 2 : NEAR_REACH;
			BlockPos p = at.offset(random.nextInt(reach * 2 + 1) - reach, 0, random.nextInt(reach * 2 + 1) - reach);
			for (int dy = 3; dy >= -3; dy--) {
				BlockPos q = p.above(dy);
				if (standable(level, q, type)) {
					return q;
				}
			}
		}
		return null;
	}

	/** {@link #spot}, but not again for a while after it found no room. */
	private static @Nullable BlockPos search(ServerLevel level, Quest q, BlockPos located, EntityType<?> type) {
		long now = level.getGameTime();
		Long next = RETRY.get(q.id());
		if (next != null && now < next) {
			return null;
		}
		BlockPos spot = spot(level, located, q.objective().target(), type);
		if (spot == null) {
			RETRY.put(q.id(), now + RETRY_TICKS);
		} else {
			RETRY.remove(q.id());
		}
		return spot;
	}

	/**
	 * Room for a {@code type} in the structure that starts at {@code located}: in its pieces nearest its middle, in
	 * what's loaded (null till a room is found); on the ground there only for a structure with no pieces to search.
	 */
	static @Nullable BlockPos spot(ServerLevel level, BlockPos located, String structureId, EntityType<?> type) {
		if (!level.hasChunk(located.getX() >> 4, located.getZ() >> 4)) {
			return null;
		}
		Identifier id = Identifier.tryParse(structureId);
		Structure structure = id == null ? null : level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValue(id);
		BlockPos over = located.offset(8, 0, 8);
		if (structure != null) {
			StructureStart start = level.structureManager().getStartForStructure(structure, level.getChunk(located.getX() >> 4, located.getZ() >> 4));
			if (start != null && start.isValid()) {
				BoundingBox all = start.getBoundingBox();
				BlockPos middle = all.getCenter();
				over = new BlockPos(middle.getX(), 0, middle.getZ());
				List<StructurePiece> pieces = new ArrayList<>(start.getPieces());
				pieces.sort((a, b) -> Double.compare(a.getBoundingBox().getCenter().distSqr(middle), b.getBoundingBox().getCenter().distSqr(middle)));
				int looked = 0;
				for (StructurePiece piece : pieces) {
					BlockPos found = room(level, piece.getBoundingBox(), type);
					if (found != null) {
						return found;
					}
					if (++looked >= 24) {
						break;
					}
				}
				if (!pieces.isEmpty()) {
					// (in the dungeon, not over it: wait till more of it is loaded)
					return null;
				}
			}
		}
		if (!level.hasChunk(over.getX() >> 4, over.getZ() >> 4)) {
			over = located.offset(8, 0, 8);
		}
		BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, over);
		if (standable(level, top, type)) {
			return top;
		}
		BlockPos found = around(level, top, type, level.getRandom());
		return found != null ? found : top;
	}

	/** The open spot with room for a {@code type} nearest the middle of {@code box}, or null. */
	private static @Nullable BlockPos room(ServerLevel level, BoundingBox box, EntityType<?> type) {
		BlockPos middle = box.getCenter();
		List<BlockPos> free = new ArrayList<>();
		int checks = 0;
		for (int y = box.minY(); y <= box.maxY() && checks < 6000; y++) {
			for (int x = box.minX(); x <= box.maxX() && checks < 6000; x++) {
				for (int z = box.minZ(); z <= box.maxZ() && checks < 6000; z++) {
					checks++;
					BlockPos p = new BlockPos(x, y, z);
					if (standable(level, p, type)) {
						free.add(p);
					}
				}
			}
		}
		free.sort((a, b) -> Double.compare(a.distSqr(middle), b.distSqr(middle)));
		int tried = 0;
		for (BlockPos p : free) {
			if (open(level, p)) {
				return p;
			}
			if (++tried >= 32) {
				break;
			}
		}
		return null;
	}

	/** Whether a {@code type} fits at {@code p}: a sturdy floor under it, and its whole body clear of blocks and water. */
	private static boolean standable(ServerLevel level, BlockPos p, EntityType<?> type) {
		AABB body = type.getDimensions().makeBoundingBox(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
		if (!level.isLoaded(BlockPos.containing(body.minX, body.minY, body.minZ)) || !level.isLoaded(BlockPos.containing(body.maxX, body.maxY, body.maxZ))) {
			return false;
		}
		BlockPos below = p.below();
		if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
			return false;
		}
		return level.noCollision(body) && !level.containsAnyLiquid(body);
	}

	/** Whether {@code p} opens onto at least {@link #OPEN_SPACE} free blocks (walkable, two high), not a sealed pocket in the earth. */
	private static boolean open(ServerLevel level, BlockPos p) {
		Set<BlockPos> seen = new HashSet<>();
		java.util.ArrayDeque<BlockPos> next = new java.util.ArrayDeque<>();
		next.add(p);
		seen.add(p);
		while (!next.isEmpty() && seen.size() < OPEN_SPACE * 4) {
			BlockPos at = next.poll();
			for (Direction d : Direction.values()) {
				BlockPos to = at.relative(d);
				if (seen.contains(to) || !level.isLoaded(to)) {
					continue;
				}
				BlockState s = level.getBlockState(to);
				if (s.getCollisionShape(level, to).isEmpty() && s.getFluidState().isEmpty()) {
					seen.add(to);
					if (seen.size() >= OPEN_SPACE) {
						return true;
					}
					next.add(to);
				}
			}
		}
		return seen.size() >= OPEN_SPACE;
	}

	// ---- the champions' fight ------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		if (CHAMPIONS.isEmpty()) {
			return;
		}
		boolean second = server.getTickCount() % 20 == 0;
		for (Mob champion : List.copyOf(CHAMPIONS)) {
			if (!champion.isAlive() || !(champion.level() instanceof ServerLevel level)) {
				forget(champion);
				continue;
			}
			ServerBossEvent bar = BARS.computeIfAbsent(champion.getUUID(), id -> new ServerBossEvent(id, champion.getDisplayName(),
				BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10));
			bar.setProgress(champion.getHealth() / champion.getMaxHealth());
			BossBars.show(level, champion, bar);
			fight(level, champion, server.getTickCount(), second);
		}
	}

	private static void fight(ServerLevel level, Mob champion, int tick, boolean second) {
		Tag tag = tag(champion);
		if (tag == null) {
			return;
		}
		float share = champion.getHealth() / champion.getMaxHealth();
		RandomSource random = level.getRandom();
		if (tag.phase() < 1 && share <= CALL_AT) {
			// the first turn: it calls its escort
			champion.setAttached(FOE, tag = tag.withPhase(1));
			ServerPlayer owner = level.getServer().getPlayerList().getPlayer(tag.owner());
			String foe = foeOf(champion);
			if (owner != null) {
				Quest q = Quests.quest(owner, tag.quest());
				if (q != null) {
					for (int i = 0; i < CALLED; i++) {
						escort(level, champion, foe, q, owner, random);
					}
				}
			}
			level.playSound(null, champion.blockPosition(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 4.0F, 1.0F);
			Speech.say(level, champion, Component.translatable("foe.jaymoskingdoms.call"), Speech.RED, 48.0);
		}
		if (tag.phase() < 2 && share <= RAGE_AT) {
			// the last: it rages
			champion.setAttached(FOE, tag = tag.withPhase(2));
			champion.addEffect(new MobEffectInstance(MobEffects.STRENGTH, RAGE_TICKS, 1));
			champion.addEffect(new MobEffectInstance(MobEffects.SPEED, RAGE_TICKS, 0));
			champion.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, RAGE_TICKS, 0));
			level.playSound(null, champion.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 4.0F, 0.5F);
			Speech.say(level, champion, Component.translatable("foe.jaymoskingdoms.rage"), Speech.RED, 48.0);
		}
		if ((tick + champion.getId()) % SHOCK_TICKS == 0 && !level.getEntitiesOfClass(ServerPlayer.class, champion.getBoundingBox().inflate(SHOCK_TRIGGER),
			p -> !p.isCreative() && !p.isSpectator()).isEmpty()) {
			shockwave(level, champion);
		}
		if (second && level.getEntitiesOfClass(ServerPlayer.class, champion.getBoundingBox().inflate(REST_RANGE), p -> !p.isSpectator()).isEmpty()
			&& champion.getHealth() < champion.getMaxHealth()) {
			champion.heal(champion.getMaxHealth() * REST_HEAL);
		}
	}

	/** It slams the ground: everyone close is hurt and thrown back. */
	private static void shockwave(ServerLevel level, Mob champion) {
		Vec3 at = champion.position();
		level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.5, at.z, 6, 1.5, 0.2, 1.5, 0.0);
		level.playSound(null, champion.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.5F, 0.7F);
		for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, champion.getBoundingBox().inflate(SHOCK_RANGE), p -> !p.isCreative() && !p.isSpectator())) {
			Vec3 push = p.position().subtract(at).multiply(1.0, 0.0, 1.0);
			push = push.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : push.normalize();
			p.hurtServer(level, level.damageSources().mobAttack(champion), SHOCK_DAMAGE);
			p.setDeltaMovement(p.getDeltaMovement().add(push.x * SHOCK_PUSH, 0.6, push.z * SHOCK_PUSH));
			p.syncVelocity = true;
		}
	}

	/** Which champion a mob is, from its type. */
	private static String foeOf(Mob mob) {
		for (String foe : List.of("warchief", "tomb_lord", "stalker", "frost", "bone_lord", "brute", "beast")) {
			if (mob.getType() == championType(foe)) {
				return foe;
			}
		}
		return "plague_knight";
	}
}
