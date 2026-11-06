package com.jaymoskingdoms;

import com.jaymoskingdoms.Captains.Captain;
import java.util.List;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantments;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.621, Rivals patch 6b) A Bane's own weapon ({@link Banes}): each kind carries its own (Jaymo's Powers' Gravesworn,
 * Grudge and Hornscythe, {@code armory/BaneWeapons}), steeped in one of the Sorcerer's elements ({@link Spellcraft#strike}),
 * keener each rank. Its blows burst with the element now and then, in its hand or yours. End it with its own marks and its
 * head is yours, element and all, to be put together at the Tool Station (found only, like the kings' heads). Without Powers
 * it fights with the plain weapons it always had, the element still on its blows, and it's those you take.
 */
public final class BaneArms {
	/** One blow in this many bursts with the element (the rest only spark). */
	static final int BURST_ONE_IN = 6;
	/** Each kind's elements, one picked for each Bane for good (by its id). */
	static final List<String> KNIGHT = List.of("death", "ice", "earth", "water");
	static final List<String> WARLORD = List.of("fire", "earth", "air", "lightning", "ice", "water");
	static final List<String> DEMON = List.of("fire", "blood", "lightning", "death");

	private static final Identifier ELEMENT_ID = Identifier.fromNamespaceAndPath("jaymospowers", "bane_element");
	/** (A burst's own harm is a blow too: no bursts off it.) */
	private static boolean striking;

	private BaneArms() {
	}

	static void register() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((victim, source, base, taken, blocked) -> {
			if (striking || taken <= 0.0F || !(source.getEntity() instanceof LivingEntity by) || source.getDirectEntity() != by
				|| !(victim.level() instanceof ServerLevel level) || !victim.isAlive()) {
				return;
			}
			String element = element(by);
			if (element == null) {
				return;
			}
			if (level.getRandom().nextInt(BURST_ONE_IN) != 0) {
				level.sendParticles(Spellcraft.particle(element), victim.getX(), victim.getY(0.6), victim.getZ(), 3, 0.2, 0.2, 0.2, 0.02);
				return;
			}
			striking = true;
			try {
				Spellcraft.strike(level, by, victim, element, false);
			} finally {
				striking = false;
			}
		});
	}

	/** The element {@code by}'s blows carry: a Bane's own, or the one steeped in the weapon in their hand. */
	static @Nullable String element(LivingEntity by) {
		if (Banes.is(by)) {
			Captain c = Captains.of(by);
			if (c != null && Banes.bane(c.tribe())) {
				return element(c);
			}
		}
		DataComponentType<String> type = component();
		return type == null ? null : by.getMainHandItem().get(type);
	}

	/** {@code c}'s element, the same for it always. */
	static String element(Captain c) {
		List<String> all = switch (Rivals.side(c.tribe())) {
			case BANE_WARLORD -> WARLORD;
			case BANE_DEMON -> DEMON;
			default -> KNIGHT;
		};
		return all.get(Math.floorMod(c.id().hashCode(), all.size()));
	}

	/** Its weapon at {@code rank}: its own (Powers), keener each rank, or {@code plain} without Powers. */
	static ItemStack weapon(ServerLevel level, Captain c, int rank, Item plain) {
		Item own = item(Rivals.side(c.tribe()), false);
		if (own == null) {
			return new ItemStack(plain);
		}
		ItemStack stack = new ItemStack(own);
		steep(stack, element(c));
		if (rank > 1) {
			Relics.enchantment(level, Enchantments.SHARPNESS).ifPresent(h -> stack.enchant(h, rank - 1));
		}
		return stack;
	}

	/** What its owner takes when it's ended: its weapon's head (Powers), steeped and named for it; or {@code held} without Powers. */
	static ItemStack spoils(Captain c, ItemStack held) {
		Item head = item(Rivals.side(c.tribe()), true);
		if (head == null) {
			return held.copy();
		}
		ItemStack stack = new ItemStack(head);
		steep(stack, element(c));
		stack.set(DataComponents.LORE, new ItemLore(List.of(Component.translatable("tooltip.jaymoskingdoms.bane_spoils", c.name())
			.withStyle(net.minecraft.ChatFormatting.DARK_GRAY, net.minecraft.ChatFormatting.ITALIC))));
		return stack;
	}

	/** Powers' weapon (or its head) for a kind, or null without Powers. */
	private static @Nullable Item item(Rivals.Side kind, boolean head) {
		String id = switch (kind) {
			case BANE_WARLORD -> head ? "grudge_head" : "grudge";
			case BANE_DEMON -> head ? "hornscythe_blade" : "hornscythe";
			default -> head ? "gravesworn_blade" : "gravesworn";
		};
		return BuiltInRegistries.ITEM.getOptional(Identifier.fromNamespaceAndPath("jaymospowers", id)).orElse(null);
	}

	private static void steep(ItemStack stack, String element) {
		DataComponentType<String> type = component();
		if (type != null) {
			stack.set(type, element);
		}
	}

	@SuppressWarnings("unchecked")
	private static @Nullable DataComponentType<String> component() {
		return (DataComponentType<String>)BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(ELEMENT_ID);
	}
}
