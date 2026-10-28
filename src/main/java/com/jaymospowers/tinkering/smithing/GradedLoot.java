package com.jaymospowers.tinkering.smithing;

import com.jaymospowers.tinkering.TinkerTool;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.server.level.ServerLevel;

/**
 * (Smithing) Weapons, tools and armour found in loot chests and sold by villagers and wandering traders come with a
 * random grade: mostly Well Forged, some Poorly and Expertly, rarely Perfectly, very rarely a Masterwork. Jaymo's
 * Kingdoms grades its own towns' smiths' wares by the buyer's standing first (it sets the grade id, which is baked
 * the first time the stack is in a pack, see {@link Wear}); this runs after it and leaves what is already graded.
 */
public final class GradedLoot {
	/** Weights by grade, in {@link Grade} order: poor, well, expert, perfect, master. */
	public static final int[] LOOT_WEIGHTS = {20, 60, 14, 5, 1};
	public static final int[] TRADE_WEIGHTS = {12, 62, 19, 6, 1};

	/** The phase this runs in, after the default one (Kingdoms' standing-based grades). */
	public static final Identifier LATE = Identifier.fromNamespaceAndPath("jaymospowers", "graded_trades");

	private GradedLoot() {
	}

	static void register() {
		LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> {
			var key = table.unwrapKey();
			if (key.isEmpty() || !key.get().identifier().getPath().startsWith("chests/")) {
				return;
			}
			RandomSource random = context.getRandom();
			for (ItemStack drop : drops) {
				if (gradable(drop)) {
					Grades.set(drop, roll(random, LOOT_WEIGHTS));
				}
			}
		});
		Event<UseEntityCallback> events = UseEntityCallback.EVENT;
		events.addPhaseOrdering(Event.DEFAULT_PHASE, LATE);
		events.register(LATE, (player, level, hand, entity, hit) -> {
			if (level instanceof ServerLevel server && entity instanceof AbstractVillager villager && villager.getTradingPlayer() == null) {
				grade(villager, server.getRandom());
			}
			return InteractionResult.PASS;
		});
	}

	/** Grades what the villager sells that is gear and not yet graded (an offer is graded once and keeps it). */
	private static void grade(AbstractVillager villager, RandomSource random) {
		for (MerchantOffer offer : villager.getOffers()) {
			ItemStack result = offer.getResult();
			if (offer.getUses() == 0 && gradable(result)) {
				Grades.set(result, roll(random, TRADE_WEIGHTS));
			}
		}
	}

	/** Whether this is gear that takes a grade: a damageable tool, weapon or armour that has none. */
	public static boolean gradable(ItemStack stack) {
		if (stack.isEmpty() || stack.has(Smithing.GRADE) || stack.getItem() instanceof TinkerTool || stack.getItem() instanceof SmithingHammerItem
			|| stack.getItem() instanceof TongsItem || !stack.isDamageableItem()) {
			return false;
		}
		return stack.has(DataComponents.TOOL) || stack.has(DataComponents.WEAPON) || stack.has(DataComponents.EQUIPPABLE)
			|| stack.getPrototype().getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY)
				.modifiers().stream().anyMatch(e -> e.attribute().equals(Attributes.ATTACK_DAMAGE));
	}

	/** A grade drawn by these weights (poor, well, expert, perfect, master). */
	public static Grade roll(RandomSource random, int[] weights) {
		int total = 0;
		for (int weight : weights) {
			total += weight;
		}
		int pick = random.nextInt(Math.max(1, total));
		Grade[] grades = Grade.values();
		for (int i = 0; i < grades.length && i < weights.length; i++) {
			pick -= weights[i];
			if (pick < 0) {
				return grades[i];
			}
		}
		return Grade.WELL;
	}
}
