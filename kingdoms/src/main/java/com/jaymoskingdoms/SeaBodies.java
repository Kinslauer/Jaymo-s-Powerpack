package com.jaymoskingdoms;

import com.jaymoskingdoms.HeritageVariants.Variant;
import java.util.EnumSet;
import java.util.Set;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.entity.EntityTypeTest;

/**
 * (0.7.486, Andy: "the ones with nonstandard heads or bodies cannot wear traditional armor") The sea-blooded Tideborn whose
 * heads or backs are their own shape (client SeaParts) can't wear a helmet or a chestplate: a piece put on slides off into
 * the pack (or drops when the pack is full), with a word on the action bar. "Traditional armour" is any piece that gives
 * armour in that slot, and on the back an elytra too; crowns, pumpkins and other headwear that give no armour still fit.
 * <p>
 * (Andy: "this applies period, the villagers will comment on it, and find alternatives ... It would make sense if every
 * tide born village relegated these types to archery and mounted combat.") Villagers too: a piece they're wearing, or
 * carrying for MCA's armour routine to put on, is shed at their feet with a word about it ({@code talk.jaymoskingdoms.sea_body.shed.N});
 * a guard of such a shape takes up the bow (MCA's archer, a bow put in the pack if they have none,
 * {@code .bow.N}), and they're first in line for their realm's horses ({@link Cavalry}).
 */
public final class SeaBodies {
	/** How often each player's head and chest are checked (ticks), and each villager's. */
	static final int EVERY = 10, VILLAGERS_EVERY = 100;
	/** How many lines there are of each (lang {@code talk.jaymoskingdoms.sea_body.shed.N}, {@code .bow.N}), and how far they carry. */
	static final int SHED_LINES = 5, BOW_LINES = 4;
	static final double SPEECH_RANGE = 16.0;

	/** Heads too strange for a helmet: mantles, lures, bells, eyestalks, bills, manes, spines, frills and fronds. */
	public static final Set<Variant> NO_HELMET = EnumSet.of(Variant.EIGHTARM, Variant.LANTERNJAW, Variant.DRIFTBELL, Variant.SHELLCLAW,
		Variant.SWORDBILL, Variant.LIONMANE, Variant.SPINEPUFF, Variant.FRILLBACK, Variant.WEEDFROND);
	/** Backs too strange for a chestplate: dorsal fins, wings, shells, sails, spines, frills and fronds. */
	public static final Set<Variant> NO_CHEST = EnumSet.of(Variant.GREYFIN, Variant.MANTAWING, Variant.SHELLCLAW, Variant.OLDSHELL,
		Variant.LIONMANE, Variant.SWORDBILL, Variant.SPINEPUFF, Variant.FRILLBACK, Variant.WEEDFROND, Variant.MORAY);

	private SeaBodies() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(SeaBodies::tick);
	}

	/** Whether {@code look} can't wear a helmet, or a chestplate (a villager kept off the front line for it). */
	public static boolean unarmoured(HeritageVariants.@org.jspecify.annotations.Nullable Variant look) {
		return look != null && (NO_HELMET.contains(look) || NO_CHEST.contains(look));
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % VILLAGERS_EVERY == 0) {
			for (ServerLevel level : server.getAllLevels()) {
				for (Mob mob : level.getEntities(EntityTypeTest.forClass(Mob.class), m -> m.isAlive() && unarmoured(HeritageVariants.of(m)))) {
					villager(level, mob);
				}
			}
		}
		if (server.getTickCount() % EVERY != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.isSpectator()) {
				continue;
			}
			Variant look = HeritageVariants.of(player);
			if (look == null) {
				continue;
			}
			if (NO_HELMET.contains(look)) {
				shed(player, EquipmentSlot.HEAD, "message.jaymoskingdoms.sea_body.head");
			}
			if (NO_CHEST.contains(look)) {
				shed(player, EquipmentSlot.CHEST, "message.jaymoskingdoms.sea_body.back");
			}
		}
	}

	private static void shed(ServerPlayer player, EquipmentSlot slot, String message) {
		ItemStack stack = player.getItemBySlot(slot);
		if (stack.isEmpty() || !armour(stack, slot)) {
			return;
		}
		player.setItemSlot(slot, ItemStack.EMPTY);
		player.getInventory().placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
		player.sendOverlayMessage(Component.translatable(message).withStyle(ChatFormatting.GRAY));
	}

	/** A villager (or any humanoid mob) of a shape that takes no helmet or chestplate. */
	private static void villager(ServerLevel level, Mob mob) {
		Variant look = HeritageVariants.of(mob);
		boolean shed = false;
		for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST}) {
			if (!(slot == EquipmentSlot.HEAD ? NO_HELMET : NO_CHEST).contains(look)) {
				continue;
			}
			ItemStack worn = mob.getItemBySlot(slot);
			if (!worn.isEmpty() && armour(worn, slot)) {
				mob.setItemSlot(slot, ItemStack.EMPTY);
				mob.spawnAtLocation(level, worn);
				shed = true;
			}
			// what MCA's armour routine would put on them next
			shed |= McaCompat.shedPacked(mob, slot, level);
		}
		if (shed) {
			say(level, mob, "shed", SHED_LINES);
		}
		if (McaCompat.toBow(mob)) {
			say(level, mob, "bow", BOW_LINES);
		}
	}

	private static void say(ServerLevel level, Mob mob, String kind, int lines) {
		Speech.say(level, mob, Component.translatable("talk.jaymoskingdoms.sea_body." + kind + "." + level.getRandom().nextInt(lines)), Speech.WHITE,
			SPEECH_RANGE);
	}

	/** Whether {@code stack} is traditional armour in {@code slot}: it gives armour there (or, on the back, it's an elytra). */
	public static boolean armour(ItemStack stack, EquipmentSlot slot) {
		if (slot == EquipmentSlot.CHEST && stack.has(DataComponents.GLIDER)) {
			return true;
		}
		boolean[] gives = {false};
		stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).forEach(slot, (attribute, modifier) -> {
			if ((attribute.is(Attributes.ARMOR) || attribute.is(Attributes.ARMOR_TOUGHNESS)) && modifier.amount() > 0.0) {
				gives[0] = true;
			}
		});
		return gives[0];
	}
}
