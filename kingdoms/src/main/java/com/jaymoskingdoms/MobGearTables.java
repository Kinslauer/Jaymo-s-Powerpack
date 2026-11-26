package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.jspecify.annotations.Nullable;

/**
 * Gear from loot tables (after MobGear by lare, CC0): a mob spawns in what the loot table {@code <its namespace>:mobgear/<its
 * name>} gives, where a datapack has one ({@code data/minecraft/loot_table/mobgear/zombie.json} dresses the zombies). The
 * table's luck is the local difficulty where it spawned (0 in peace, up to about 6.75 on hard), so {@code quality} on an
 * entry makes it likelier the harder the place. While it is rolled, the mob's own custom data holds
 * {@code mobgear:spawnreason} (natural, structure, reinforcement...) for an {@code entity_properties} condition to test.
 * A table that gives anything replaces the gear the mob was given; one that gives nothing leaves it. Spawners and
 * conversions keep their own gear. Each item goes to the slot it's worn in (anything else to the main hand), the first
 * to come taking a slot; an item's custom data can say {@code mobgear:slot} (mainhand, offhand, head, chest, legs, feet,
 * body, saddle), {@code mobgear:priority} (the higher put on first) and {@code mobgear:dropchance} (0 to 1). The same
 * keys as MobGear's, so its datapacks work here; to give a whole set at once, give each piece its own pool.
 * <p>
 * Our own monsters' tiered gear is drawn from tables too ({@link MonsterGear}, {@code loot_table/monster_gear/}).
 */
public final class MobGearTables {
	/** The folder of a mob's table, under its own namespace. */
	static final String FOLDER = "mobgear/";
	/** The custom data keys (MobGear's own). */
	static final String SPAWN_REASON = "mobgear:spawnreason", SLOT = "mobgear:slot", PRIORITY = "mobgear:priority", DROP_CHANCE = "mobgear:dropchance";
	/** The slots a table's gear replaces. */
	private static final EquipmentSlot[] CLEARED = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST,
		EquipmentSlot.LEGS, EquipmentSlot.FEET};

	private record Spawned(EntitySpawnReason reason, float luck) {
	}

	/**
	 * Mobs set up this tick, dressed at the end of it, once the rest of their setup (a zombie's own gear, which comes after
	 * the mob's) is done. Mobs of new chunks are set up off the server thread, so it's locked.
	 */
	private static final Map<Mob, Spawned> WAITING = Collections.synchronizedMap(new WeakHashMap<>());

	private MobGearTables() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(MobGearTables::tick);
	}

	/** From {@code MobGearSpawnMixin}: {@code mob} was set up for {@code reason}, with this local difficulty. */
	public static void spawned(Mob mob, EntitySpawnReason reason, float difficulty) {
		if (reason == EntitySpawnReason.CONVERSION || EntitySpawnReason.isSpawner(reason) || mob.level().getServer() == null
			|| table(mob.level().getServer(), mob) == LootTable.EMPTY) {
			return;
		}
		WAITING.put(mob, new Spawned(reason, difficulty));
	}

	private static LootTable table(MinecraftServer server, Mob mob) {
		Identifier type = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
		return server.reloadableRegistries().getLootTable(key(Identifier.fromNamespaceAndPath(type.getNamespace(), FOLDER + type.getPath())));
	}

	static ResourceKey<LootTable> key(Identifier id) {
		return ResourceKey.create(Registries.LOOT_TABLE, id);
	}

	private static void tick(MinecraftServer server) {
		if (WAITING.isEmpty()) {
			return;
		}
		List<Map.Entry<Mob, Spawned>> due;
		synchronized (WAITING) {
			due = new ArrayList<>(WAITING.entrySet());
			WAITING.clear();
		}
		for (Map.Entry<Mob, Spawned> entry : due) {
			Mob mob = entry.getKey();
			if (!mob.isRemoved() && mob.level() instanceof ServerLevel level) {
				dress(level, mob, entry.getValue());
			}
		}
	}

	private static void dress(ServerLevel level, Mob mob, Spawned spawned) {
		LootTable table = table(level.getServer(), mob);
		if (table == LootTable.EMPTY) {
			return;
		}
		CustomData before = mob.get(DataComponents.CUSTOM_DATA);
		CustomData own = before == null ? CustomData.EMPTY : before;
		mob.setComponent(DataComponents.CUSTOM_DATA, own.update(tag -> tag.putString(SPAWN_REASON, spawned.reason().name())));
		List<ItemStack> gear = roll(level, mob, table, spawned.luck());
		mob.setComponent(DataComponents.CUSTOM_DATA, own);
		if (gear.isEmpty()) {
			return;
		}
		for (EquipmentSlot slot : CLEARED) {
			mob.setItemSlot(slot, ItemStack.EMPTY);
		}
		gear.sort((a, b) -> Integer.compare(priority(b), priority(a)));
		List<EquipmentSlot> taken = new ArrayList<>();
		for (ItemStack stack : gear) {
			CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
			EquipmentSlot slot = data.getString(SLOT).map(MobGearTables::slot).orElse(null);
			if (slot == null) {
				slot = mob.resolveSlot(stack, taken);
			} else if (taken.contains(slot)) {
				slot = null;
			}
			Float chance = data.getFloat(DROP_CHANCE).orElse(null);
			data.remove(SLOT);
			data.remove(PRIORITY);
			data.remove(DROP_CHANCE);
			if (data.isEmpty()) {
				stack.remove(DataComponents.CUSTOM_DATA);
			} else {
				stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
			}
			if (slot == null) {
				continue;
			}
			mob.setItemSlot(slot, slot.limit(stack));
			if (chance != null) {
				mob.setDropChance(slot, chance);
			}
			taken.add(slot);
		}
	}

	/** What {@code table} gives {@code mob} (the equipment context: the mob, where it stands), with {@code luck}. */
	static List<ItemStack> roll(ServerLevel level, Mob mob, LootTable table, float luck) {
		LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, mob.position())
			.withParameter(LootContextParams.THIS_ENTITY, mob).withLuck(luck).create(LootContextParamSets.EQUIPMENT);
		return new ArrayList<>(table.getRandomItems(params));
	}

	private static int priority(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? 0 : data.copyTag().getInt(PRIORITY).orElse(0);
	}

	private static @Nullable EquipmentSlot slot(String name) {
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			if (slot.getName().equalsIgnoreCase(name)) {
				return slot;
			}
		}
		return null;
	}
}
