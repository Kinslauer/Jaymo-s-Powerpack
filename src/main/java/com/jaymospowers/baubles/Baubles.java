package com.jaymospowers.baubles;

import com.jaymospowers.JaymosPowers;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.gamerules.GameRules;

/**
 * (0.17.184) Baubles (Andy: "need this", the CurseForge Baubles mod). Baubles itself is a 1.12 Forge mod and no
 * accessory-slot mod (Trinkets, Accessories, Curios) has a Fabric 26.3 build, so this is our own, laid out as Baubles
 * was: seven slots, an amulet, two rings, a belt, a head piece, a body piece and a charm.
 * <ul>
 * <li>The slots have their own screen, opened from a tab on the inventory's left edge (or the unbound Baubles key).</li>
 * <li>What fits each slot is an item tag ({@code jaymospowers:baubles/<slot>}), so any item can be made wearable by
 * data. Our own {@link BaubleItem}s give their bonuses while worn there, and only there.</li>
 * <li>Worn baubles drop where you die unless keepInventory holds.</li>
 * </ul>
 */
public final class Baubles {
	/** The seven slots, in the order they sit (and are saved). */
	public enum Kind {
		AMULET("amulet"), RING("ring"), BELT("belt"), HEAD("head"), BODY("body"), CHARM("charm");

		public final String id;
		public final TagKey<Item> tag;

		Kind(String id) {
			this.id = id;
			this.tag = TagKey.create(Registries.ITEM, JaymosPowers.id("baubles/" + id));
		}
	}

	/** Which kind each of the seven slots takes. */
	public static final Kind[] SLOTS = {Kind.AMULET, Kind.RING, Kind.RING, Kind.BELT, Kind.HEAD, Kind.BODY, Kind.CHARM};
	public static final int SIZE = SLOTS.length;
	/** How often (ticks) worn bonuses are checked again, besides whenever the slots change. */
	static final int REFRESH_TICKS = 20;

	/** On a player: what they wear in the seven slots (server; the screen syncs what it shows). */
	public static final AttachmentType<List<ItemStack>> WORN = AttachmentRegistry.create(JaymosPowers.id("baubles"),
		builder -> builder.persistent(ItemStack.OPTIONAL_CODEC.listOf()).copyOnDeath());

	public static MenuType<BaublesMenu> MENU;
	public static Item HEARTSTONE_AMULET, SWIFTFOOT_RING, DELVERS_RING, BRAWLERS_BELT, DIVERS_CIRCLET, FEATHERFALL_CLOAK, RABBIT_FOOT_CHARM;
	/** Every attribute any bauble touches, so stale bonuses can be taken off. */
	private static final Set<Holder<Attribute>> TOUCHED = new LinkedHashSet<>();
	private static final List<Item> ITEMS = new ArrayList<>();

	private Baubles() {
	}

	public static void init() {
		MENU = Registry.register(BuiltInRegistries.MENU, JaymosPowers.id("baubles"), new MenuType<>(BaublesMenu::new, FeatureFlags.VANILLA_SET));
		HEARTSTONE_AMULET = bauble("heartstone_amulet", Kind.AMULET, Rarity.UNCOMMON,
			new BaubleItem.Bonus(Attributes.MAX_HEALTH, 4.0, AttributeModifier.Operation.ADD_VALUE));
		SWIFTFOOT_RING = bauble("swiftfoot_ring", Kind.RING, Rarity.UNCOMMON,
			new BaubleItem.Bonus(Attributes.MOVEMENT_SPEED, 0.08, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		DELVERS_RING = bauble("delvers_ring", Kind.RING, Rarity.UNCOMMON,
			new BaubleItem.Bonus(Attributes.BLOCK_BREAK_SPEED, 0.2, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		BRAWLERS_BELT = bauble("brawlers_belt", Kind.BELT, Rarity.COMMON,
			new BaubleItem.Bonus(Attributes.KNOCKBACK_RESISTANCE, 0.25, AttributeModifier.Operation.ADD_VALUE));
		DIVERS_CIRCLET = bauble("divers_circlet", Kind.HEAD, Rarity.UNCOMMON,
			new BaubleItem.Bonus(Attributes.OXYGEN_BONUS, 1.0, AttributeModifier.Operation.ADD_VALUE),
			new BaubleItem.Bonus(Attributes.SUBMERGED_MINING_SPEED, 0.5, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		FEATHERFALL_CLOAK = bauble("featherfall_cloak", Kind.BODY, Rarity.UNCOMMON,
			new BaubleItem.Bonus(Attributes.SAFE_FALL_DISTANCE, 2.0, AttributeModifier.Operation.ADD_VALUE));
		RABBIT_FOOT_CHARM = bauble("rabbit_foot_charm", Kind.CHARM, Rarity.COMMON,
			new BaubleItem.Bonus(Attributes.LUCK, 1.0, AttributeModifier.Operation.ADD_VALUE));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> ITEMS.forEach(output::accept));
		BaublesNet.init();
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % REFRESH_TICKS == 0) {
				for (ServerPlayer player : server.getPlayerList().getPlayers()) {
					refresh(player);
				}
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player && !player.level().getGameRules().get(GameRules.KEEP_INVENTORY)) {
				dropAll(player);
			}
		});
	}

	private static Item bauble(String id, Kind kind, Rarity rarity, BaubleItem.Bonus... bonuses) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id(id));
		Item item = Registry.register(BuiltInRegistries.ITEM, key, new BaubleItem(new Item.Properties().setId(key).stacksTo(1).rarity(rarity), kind, List.of(bonuses)));
		for (BaubleItem.Bonus bonus : bonuses) {
			TOUCHED.add(bonus.attribute());
		}
		ITEMS.add(item);
		return item;
	}

	/** A copy of what this player wears, always {@link #SIZE} long (server). */
	public static List<ItemStack> worn(Player player) {
		List<ItemStack> saved = player.getAttached(WORN);
		List<ItemStack> out = new ArrayList<>(SIZE);
		for (int i = 0; i < SIZE; i++) {
			out.add(saved != null && i < saved.size() ? saved.get(i) : ItemStack.EMPTY);
		}
		return out;
	}

	/** Saves what this player wears and puts its bonuses on (server). */
	static void setWorn(ServerPlayer player, List<ItemStack> stacks) {
		List<ItemStack> copy = new ArrayList<>(SIZE);
		for (int i = 0; i < SIZE; i++) {
			copy.add(i < stacks.size() ? stacks.get(i).copy() : ItemStack.EMPTY);
		}
		if (copy.stream().allMatch(ItemStack::isEmpty)) {
			player.removeAttached(WORN);
		} else {
			player.setAttached(WORN, copy);
		}
		refresh(player);
	}

	/** Whether {@code stack} may go in slot {@code slot}. */
	public static boolean fits(int slot, ItemStack stack) {
		return slot >= 0 && slot < SIZE && stack.is(SLOTS[slot].tag);
	}

	/** Whether this player wears {@code item} in any slot (server). */
	public static boolean wearing(Player player, Item item) {
		return worn(player).stream().anyMatch(stack -> stack.is(item));
	}

	/** The id of the modifier the {@code bonus}th bonus of the bauble in slot {@code slot} puts on. */
	static Identifier modifierId(int slot, int bonus) {
		return JaymosPowers.id("bauble_" + slot + "_" + bonus);
	}

	/**
	 * Puts on the bonuses of what this player wears and takes off any that no longer apply. The modifiers are permanent
	 * (saved with the player's attributes) so a heartstone's extra health is there when they log back in, before their
	 * health is read; this takes them off again as soon as the bauble comes off.
	 */
	static void refresh(ServerPlayer player) {
		List<ItemStack> worn = worn(player);
		for (Holder<Attribute> attribute : TOUCHED) {
			AttributeInstance instance = player.getAttribute(attribute);
			if (instance == null) {
				continue;
			}
			for (int slot = 0; slot < SIZE; slot++) {
				List<BaubleItem.Bonus> bonuses = worn.get(slot).getItem() instanceof BaubleItem bauble && fits(slot, worn.get(slot))
					? bauble.bonuses() : List.of();
				for (int i = 0; i < BaubleItem.MAX_BONUSES; i++) {
					Identifier id = modifierId(slot, i);
					BaubleItem.Bonus bonus = i < bonuses.size() ? bonuses.get(i) : null;
					if (bonus != null && bonus.attribute().equals(attribute)) {
						AttributeModifier modifier = new AttributeModifier(id, bonus.amount(), bonus.operation());
						if (!modifier.equals(instance.getModifier(id))) {
							instance.addOrReplacePermanentModifier(modifier);
						}
					} else if (instance.hasModifier(id)) {
						instance.removeModifier(id);
					}
				}
			}
		}
		if (player.getHealth() > player.getMaxHealth()) {
			player.setHealth(player.getMaxHealth());
		}
	}

	/** Drops everything worn where the player fell (on death, without keepInventory). */
	private static void dropAll(ServerPlayer player) {
		List<ItemStack> worn = worn(player);
		player.removeAttached(WORN);
		for (ItemStack stack : worn) {
			if (!stack.isEmpty()) {
				Containers.dropItemStack(player.level(), player.getX(), player.getY(), player.getZ(), stack);
			}
		}
		refresh(player);
	}

	/** Opens this player's bauble slots (the inventory tab or the Baubles key). */
	static void open(ServerPlayer player) {
		if (!player.isAlive() || player.isSpectator()) {
			return;
		}
		player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new BaublesMenu(id, inventory, new BaublesContainer(player)),
			Component.translatable("container.jaymospowers.baubles")));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_GENERIC.value(), SoundSource.PLAYERS, 0.5F, 1.2F);
	}

	/** Every bauble of ours, for the creative tab and the screen's empty-slot pictures. */
	public static List<Item> items() {
		return ITEMS;
	}

	/** Our own bauble for a slot kind, to show faintly in an empty slot. */
	public static Item picture(Kind kind) {
		for (Item item : ITEMS) {
			if (item instanceof BaubleItem bauble && bauble.kind() == kind) {
				return item;
			}
		}
		return HEARTSTONE_AMULET;
	}

	/** Sets a bauble down in an empty, matching slot from the hand (right-clicking with one). False if none fits. */
	static boolean equipFromHand(ServerPlayer player, ItemStack stack) {
		List<ItemStack> worn = worn(player);
		for (int i = 0; i < SIZE; i++) {
			if (worn.get(i).isEmpty() && fits(i, stack)) {
				worn.set(i, stack.copyWithCount(1));
				setWorn(player, worn);
				stack.shrink(1);
				player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_GENERIC.value(), SoundSource.PLAYERS, 0.8F, 1.2F);
				return true;
			}
		}
		return false;
	}
}
