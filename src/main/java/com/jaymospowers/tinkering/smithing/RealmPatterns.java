package com.jaymospowers.tinkering.smithing;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.tinkering.WeaponParts;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Medieval picks (me-sidesmith): a realm's own make. Every helm and weapon head is anyone's to forge; but each kingdom
 * keeps its own marked version of two of them, the plate helm its knights wear and the war arm they carry (which two is
 * Jaymo's Kingdoms' to say, {@link #keeps}). The marked piece is a little better made ({@link #TOUGHNESS},
 * {@link #EDGE}) and bears the realm's name. Only a smith sworn to that realm ({@link #sworn}) is offered it at the
 * anvil, or one who carries a <b>captured pattern</b> of it: a blueprint taken from a marked piece at the Drafting Table
 * ({@link Patterns}), a copy of one, or one off a slain realm knight (Kingdoms, {@link #patternOf}).
 * <p>
 * A realm is passed round as a tag, {@code "<faction id>|<name>"}.
 */
public final class RealmPatterns {
	// ---- tuning ----
	/** What a realm's make adds: toughness to a marked helm (or other armour), damage to a marked weapon. */
	public static final double TOUGHNESS = 1.0, EDGE = 0.5;
	/** The weapon heads a kingdom may keep as its war arm, of those whose weapons are in the game ({@link #warArms}). */
	public static final List<String> WAR_ARMS = List.of("halberd_head", "glaive_blade", "bec_de_corbin_head", "war_scythe_blade",
		"claymore_blade", "longsword_blade", "flanged_mace_head", "broadsword_blade");

	/** The forging choice of a realm's version: the option's id, this, and the realm's tag. */
	public static final String MARK = "@";
	static final Identifier TOUGHNESS_ID = JaymosPowers.id("realm_make_toughness"), EDGE_ID = JaymosPowers.id("realm_make_edge");

	/** The realm whose make a piece is, or whose design a captured pattern holds (a realm tag). */
	public static DataComponentType<String> REALM_DESIGN;

	/** The realm this smith is sworn to (a tag), or null. Kingdoms sets it; alone, nobody is. */
	public static Function<ServerPlayer, @Nullable String> sworn = smith -> null;
	/** Whether a realm (a tag) keeps a forging shape ({@link Blueprints#shape}) as its own. Kingdoms sets it. */
	public static BiPredicate<String, String> keeps = (realm, shape) -> false;

	private RealmPatterns() {
	}

	/** A shape a realm can call its own: a heraldic armour piece or a weapon head. */
	public static boolean claimable(String shape) {
		return shape.startsWith("heraldry/") || shape.startsWith("weapon/");
	}

	static void register() {
		REALM_DESIGN = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosPowers.id("realm_design"),
			DataComponentType.<String>builder().persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8).build());
		// a marked forging is finished only by a smith who may make it (another can't take it over at the anvil)
		Forging.CHECKS.add((smith, anvil, option) -> {
			String realm = anvil.work().get(REALM_DESIGN);
			return realm == null || smith.isCreative() || realms(smith, option).contains(realm) ? null : Forging.DULL;
		});
		Forging.FINISHES.add((smith, anvil, option, made) -> {
			String realm = anvil.work().get(REALM_DESIGN);
			if (realm != null) {
				mark(made, realm);
			}
		});
		// the head's make goes onto the weapon
		Grades.CARRIERS.add((head, made) -> {
			String realm = head.get(REALM_DESIGN);
			if (realm != null) {
				mark(made, realm);
			}
		});
		Grades.ATTRIBUTE_PASSES.add((stack, attributes) -> {
			if (stack.get(REALM_DESIGN) == null || stack.is(Blueprints.BLUEPRINT_ITEM)) {
				return attributes;
			}
			EquipmentSlotGroup slot = Grades.gearSlot(stack);
			if (slot == null) {
				return attributes;
			}
			return slot == EquipmentSlotGroup.MAINHAND
				? attributes.withModifierAdded(Attributes.ATTACK_DAMAGE, new AttributeModifier(EDGE_ID, EDGE, AttributeModifier.Operation.ADD_VALUE), slot)
				: attributes.withModifierAdded(Attributes.ARMOR_TOUGHNESS, new AttributeModifier(TOUGHNESS_ID, TOUGHNESS,
					AttributeModifier.Operation.ADD_VALUE), slot);
		});
	}

	/** The name in a realm tag. */
	public static String name(String realm) {
		int bar = realm.indexOf('|');
		return bar < 0 ? realm : realm.substring(bar + 1);
	}

	/** The realm versions of {@code option} this smith may forge: their own realm's, if it keeps the shape, and each captured pattern's they carry. */
	public static List<String> realms(ServerPlayer smith, Forging.Option option) {
		String shape = Blueprints.shape(option);
		List<String> out = new ArrayList<>();
		if (!claimable(shape)) {
			return out;
		}
		String own = sworn.apply(smith);
		if (own != null && keeps.test(own, shape)) {
			out.add(own);
		}
		Inventory inventory = smith.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			String realm = stack.is(Blueprints.BLUEPRINT_ITEM) ? stack.get(REALM_DESIGN) : null;
			if (realm != null && !out.contains(realm) && shape.equals(Blueprints.shapeOf(stack))) {
				out.add(realm);
			}
		}
		return out;
	}

	/** Makes {@code stack} the realm's make: its mark, its name, and its numbers rebaked. Kingdoms calls it by reflection for its knights' helms. */
	public static ItemStack mark(ItemStack stack, String realm) {
		if (stack.isEmpty() || realm.equals(stack.get(REALM_DESIGN))) {
			return stack;
		}
		Component name = stack.getHoverName();
		stack.set(REALM_DESIGN, realm);
		stack.set(DataComponents.ITEM_NAME, Component.translatable("item.jaymospowers.realm_design", name(realm), name));
		Grades.bake(stack);
		return stack;
	}

	/** Makes a blueprint a captured pattern of the realm's design. */
	static ItemStack capture(ItemStack blueprint, String realm) {
		blueprint.set(REALM_DESIGN, realm);
		blueprint.set(DataComponents.ITEM_NAME, Component.translatable("item.jaymospowers.blueprint.captured", name(realm),
			Blueprints.shapeName(Blueprints.shapeOf(blueprint))));
		return blueprint;
	}

	/** The war-arm heads whose weapons are in the game (Kingdoms picks each realm's from these). */
	public static List<String> warArms() {
		List<String> out = new ArrayList<>();
		for (String part : WAR_ARMS) {
			WeaponParts.Kind kind = WeaponParts.KINDS.get(part);
			if (kind != null && !WeaponParts.king(kind) && weapon(kind) != null) {
				out.add(part);
			}
		}
		return out;
	}

	/** A finished war arm with {@code part} for its head, in steel (iron if it has no steel), the realm's make; empty if there's none. */
	public static ItemStack warArm(String part, String realm) {
		WeaponParts.Kind kind = WeaponParts.KINDS.get(part);
		Item item = kind == null ? null : weapon(kind);
		return item == null ? ItemStack.EMPTY : mark(new ItemStack(item), realm);
	}

	private static @Nullable Item weapon(WeaponParts.Kind kind) {
		Item steel = kind.in(WeaponParts.Metal.STEEL);
		return steel != null ? steel : kind.in(WeaponParts.Metal.IRON);
	}

	/** The captured pattern a realm's marked piece gives (a slain knight's helm or arm), or empty if it isn't one. */
	public static ItemStack patternOf(ItemStack piece) {
		String shape = piece.get(REALM_DESIGN) == null ? null : Patterns.shapeOf(piece);
		return shape == null ? ItemStack.EMPTY : Patterns.pattern(piece, shape);
	}
}
