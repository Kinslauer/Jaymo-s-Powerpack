package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jspecify.annotations.Nullable;

/**
 * Salvage (Andy, 2026-11-19: "they should salvage gear and add it to their stores, so kingdoms low on funds can be seen
 * wearing the spoils. or some guards will wear it and brag that they like the color or how they ripped it from a cold
 * dead enemy"). When a settlement's bearers take up a body to bury or burn ({@link Bearers}), they strip it first: what
 * it wore and carried goes into the settlement's stores ({@link Storage}), each piece of gear marked with whose it was.
 * Without Jaymo's Kitchen's corpses, a fallen folk's gear goes into the stores of the realm whose land they fell on.
 * <p>
 * A guard who wears a piece of the spoils brags about it now and then to a player close by: about its colour, or, when it
 * came off an enemy, about the dead enemy they took it from.
 */
public final class Salvage {
	/** The mark on a salvaged piece: whose it was ({@code from}), and whether they were the settlement's enemy ({@code foe}). */
	static final String TAG = "jaymoskingdoms_salvage";
	/** How often the guards round each player are looked over for a brag (ticks), how near a player must be, and how often one guard may brag. */
	static final int BRAG_SWEEP = 200;
	static final double BRAG_RANGE = 6.0;
	static final long BRAG_EVERY = 6000L;
	/** A guard close by brags on a sweep with this chance; lines per kind ({@code speech.jaymoskingdoms.salvage.<kind>.<n>}). */
	static final float BRAG_CHANCE = 0.25F;
	static final int LINES = 3;
	static final double SPEECH_RANGE = 10.0;

	private static final Map<UUID, Long> BRAGGED = new HashMap<>();

	private Salvage() {
	}

	static void register() {
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity.level() instanceof ServerLevel level && entity instanceof Folk folk && !KitchenCompat.loaded()) {
				fallen(level, folk);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % BRAG_SWEEP == 0) {
				brag(server);
			}
		});
	}

	/** {@code stack} is a piece of salvage. */
	static boolean is(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data != null && data.copyTag().contains(TAG);
	}

	/** Marks {@code stack} as salvaged from {@code from}, an enemy of the settlement ({@code foe}) or one of its own. */
	static void mark(ItemStack stack, String from, boolean foe) {
		if (is(stack)) {
			return;
		}
		CustomData.update(DataComponents.CUSTOM_DATA, stack, root -> {
			CompoundTag t = new CompoundTag();
			t.putString("from", from);
			t.putBoolean("foe", foe);
			root.put(TAG, t);
		});
	}

	private static CompoundTag mark(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? new CompoundTag() : data.copyTag().getCompoundOrEmpty(TAG);
	}

	/** Gear worth marking: armour, a weapon, a shield. */
	private static boolean gear(ItemStack stack) {
		return stack.has(DataComponents.EQUIPPABLE) || stack.has(DataComponents.WEAPON) || stack.has(DataComponents.BLOCKS_ATTACKS);
	}

	/**
	 * (Bearers) {@code bearer} of {@code f} has taken up {@code corpse} ({@code name}, of {@code realm} or -1): what it wore and
	 * carried goes into {@code f}'s stores, its gear marked; what doesn't fit is put down at the bearer's feet.
	 */
	static void salvage(ServerLevel level, Faction f, Entity corpse, PathfinderMob bearer, Component name, int realm) {
		if (f.fallen()) {
			return;
		}
		List<ItemStack> taken = KitchenCompat.robBy(corpse, bearer);
		store(level, f, taken, from(level.getServer(), name, realm), realm != f.id(), bearer);
	}

	/** Without Kitchen's corpses: a folk fallen on a standing realm's land leaves its gear to that realm's stores. */
	private static void fallen(ServerLevel level, Folk folk) {
		Faction f = Theft.lawAt(level, folk.blockPosition());
		if (f == null || f.fallen() || !f.type().isRealm()) {
			return;
		}
		List<ItemStack> worn = new java.util.ArrayList<>();
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			ItemStack stack = folk.getItemBySlot(slot);
			if (!stack.isEmpty() && gear(stack)) {
				worn.add(stack.copy());
				folk.setItemSlot(slot, ItemStack.EMPTY);
			}
		}
		store(level, f, worn, from(level.getServer(), folk.getName(), folk.factionId()), folk.factionId() != f.id(), folk);
	}

	private static String from(MinecraftServer server, Component name, int realm) {
		Faction of = realm >= 0 ? Factions.get(server, realm) : null;
		return of != null ? of.name() : name.getString();
	}

	private static void store(ServerLevel level, Faction f, List<ItemStack> taken, String from, boolean foe, Entity at) {
		for (ItemStack stack : taken) {
			if (stack.isEmpty()) {
				continue;
			}
			if (gear(stack)) {
				mark(stack, from, foe);
			}
			ItemStack left = Storage.put(level, f, stack);
			if (!left.isEmpty()) {
				at.spawnAtLocation(level, left);
			}
		}
	}

	/** The guards round each player who wear a piece of the spoils may brag about it. */
	private static void brag(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		BRAGGED.values().removeIf(next -> next < now);
		for (ServerLevel level : server.getAllLevels()) {
			for (ServerPlayer player : level.players()) {
				for (Worker w : Workers.near(level, player.getBoundingBox().inflate(BRAG_RANGE), x -> x.role() == Folk.GUARD || x.role() == Folk.ARCHER)) {
					PathfinderMob mob = w.mob();
					if (BRAGGED.containsKey(mob.getUUID()) || mob.getTarget() != null || mob.isSleeping() || level.getRandom().nextFloat() >= BRAG_CHANCE) {
						continue;
					}
					ItemStack piece = worn(mob);
					if (piece == null) {
						continue;
					}
					BRAGGED.put(mob.getUUID(), now + BRAG_EVERY);
					CompoundTag t = mark(piece);
					boolean foe = t.getBooleanOr("foe", false);
					String key = "speech.jaymoskingdoms.salvage." + (foe && level.getRandom().nextBoolean() ? "foe" : "colour") + "." + level.getRandom().nextInt(LINES);
					Speech.say(level, mob, Component.translatable(key, piece.getHoverName(), t.getStringOr("from", "")), Speech.WHITE, SPEECH_RANGE);
				}
			}
		}
	}

	/** A piece of an enemy's spoils {@code mob} has on, or null. */
	private static @Nullable ItemStack worn(LivingEntity mob) {
		for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.CHEST, EquipmentSlot.HEAD, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) {
			ItemStack stack = mob.getItemBySlot(slot);
			// (what came off the settlement's own dead is nothing to brag of)
			if (!stack.isEmpty() && is(stack) && mark(stack).getBooleanOr("foe", false)) {
				return stack;
			}
		}
		return null;
	}
}
