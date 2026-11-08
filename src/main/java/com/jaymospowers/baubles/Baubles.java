package com.jaymospowers.baubles;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.compat.ToolBeltCompat;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
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
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.gamerules.GameRules;

/**
 * (0.17.184) Baubles (Andy: "need this", the CurseForge Baubles mod). Baubles itself is a 1.12 Forge mod and no
 * accessory-slot mod (Trinkets, Accessories, Curios) has a Fabric 26.3 build, so this is our own, laid out as Baubles
 * was: seven slots, an amulet, two rings, a belt, a head piece, a body piece and a charm, and a wrist (0.19.87) for bracelets.
 * <ul>
 * <li>The slots have their own screen, opened from a tab on the inventory's left edge (or the unbound Baubles key).</li>
 * <li>What fits each slot is an item tag ({@code jaymospowers:baubles/<slot>}), so any item can be made wearable by
 * data. Our own {@link BaubleItem}s give their bonuses while worn there, and only there; any worn item ticks as if
 * carried, so Alchemy's Runic Amulet (tagged an amulet) works from its slot.</li>
 * <li>Worn baubles drop where you die unless keepInventory holds.</li>
 * </ul>
 */
public final class Baubles {
	/** The eight slots, in the order they sit (and are saved). */
	public enum Kind {
		AMULET("amulet"), RING("ring"), BELT("belt"), HEAD("head"), BODY("body"), CHARM("charm"),
		/** (0.19.87) Bracelets. Added last so saved lists of seven still load. */
		WRIST("wrist"),
		/** (Artifacts) Shoes, slippers and flippers. Added after the wrist so saved lists of eight still load. */
		FEET("feet");

		public final String id;
		public final TagKey<Item> tag;

		Kind(String id) {
			this.id = id;
			this.tag = TagKey.create(Registries.ITEM, JaymosPowers.id("baubles/" + id));
		}
	}

	/** Which kind each of the nine slots takes. */
	public static final Kind[] SLOTS = {Kind.AMULET, Kind.RING, Kind.RING, Kind.BELT, Kind.HEAD, Kind.BODY, Kind.CHARM, Kind.WRIST, Kind.FEET};
	public static final int SIZE = SLOTS.length;
	/** How often (ticks) worn bonuses are checked again, besides whenever the slots change. */
	static final int REFRESH_TICKS = 20;

	/** On a player: what they wear in the slots (saved on the server, synced to the wearer). */
	public static final AttachmentType<List<ItemStack>> WORN = AttachmentRegistry.create(JaymosPowers.id("baubles"),
		builder -> builder.persistent(ItemStack.OPTIONAL_CODEC.listOf()).copyOnDeath()
			// (RPG Series) synced to the wearer too: Spell Engine reads worn quivers on the client as well
			.syncWith(ItemStack.OPTIONAL_LIST_STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));

	/**
	 * (Motion patch 4) On a player: the worn things others see, synced to everyone for drawing them: the lantern hung
	 * at the hip (a lantern in the belt slot) and the lantern pole on the back, each empty when not worn.
	 */
	public static final AttachmentType<List<ItemStack>> SHOWN = AttachmentRegistry.create(JaymosPowers.id("baubles_shown"),
		builder -> builder.syncWith(ItemStack.OPTIONAL_LIST_STREAM_CODEC, AttachmentSyncPredicate.all()));
	/**
	 * (0.21.37) On a player: what's in each of the nine slots, by item id ({@code minecraft:air} for empty), synced to
	 * everyone, so every worn bauble with a worn look is drawn on the body ({@code client/baubles/WornLooks}). Not saved.
	 */
	public static final AttachmentType<List<Identifier>> LOOKS = AttachmentRegistry.create(JaymosPowers.id("baubles_looks"),
		builder -> builder.syncWith(Identifier.STREAM_CODEC.apply(net.minecraft.network.codec.ByteBufCodecs.list()),
			AttachmentSyncPredicate.all()));
	/** Vanilla's lanterns tag (plain, soul, every copper one): what hangs at the hip from the belt slot. */
	static final TagKey<Item> LANTERNS = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("minecraft", "lanterns"));
	/**
	 * (0.20.0) Items besides lanterns that hang at the hip from the belt slot: the tag {@code jaymospowers:hip_shown}, which
	 * Jaymo's Kingdoms fills with its coin purse. The client draws a non-lantern hip item with the item renderer.
	 */
	static final TagKey<Item> HIP_SHOWN = TagKey.create(Registries.ITEM, JaymosPowers.id("hip_shown"));
	/** The belt and body slots, for {@link #SHOWN}. */
	public static final int BELT_SLOT = 3;
	static final int BODY_SLOT = 5;

	public static MenuType<BaublesMenu> MENU;
	public static Item LANTERN_POLE;
	/** (0.20.90) A breathing mask for the Hestveil's gas caves (seven-mods plan, patch 10): worn in the head slot. */
	public static Item SKY_MASK;
	public static Item HEARTSTONE_AMULET, SWIFTFOOT_RING, DELVERS_RING, BRAWLERS_BELT, DIVERS_CIRCLET, FEATHERFALL_CLOAK, RABBIT_FOOT_CHARM;
	/** Every attribute any bauble touches, so stale bonuses can be taken off. */
	private static final Set<Holder<Attribute>> TOUCHED = new LinkedHashSet<>();
	private static final List<Item> ITEMS = new ArrayList<>();
	/** (0.19.87) Slot pictures for kinds that have no bauble of the seven (the wrist's bracelet). */
	private static final Map<Kind, Item> PICTURES = new EnumMap<>(Kind.class);

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
		// (Motion patch 4) a lantern on a stick, worn on the back with the lantern hung out over the head
		LANTERN_POLE = bauble("lantern_pole", Kind.BODY, Rarity.COMMON);
		SKY_MASK = bauble("sky_mask", Kind.HEAD, Rarity.COMMON);
		// (0.20.12) the pink fairy, caught in a bottle
		Fairies.init();
		// (Artifacts) the Artifacts mod's 45 baubles, ported (MIT), and where they are found
		com.jaymospowers.baubles.artifacts.ArtifactBaubles.init();
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> ITEMS.forEach(output::accept));
		BaublesNet.init();
		BaublesQuickEquip.init();
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				tickWorn(player);
				if (server.getTickCount() % REFRESH_TICKS == 0) {
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
		return add(Registry.register(BuiltInRegistries.ITEM, key, new BaubleItem(new Item.Properties().setId(key).stacksTo(1).rarity(rarity), kind, List.of(bonuses))));
	}

	/** (0.20.12) Lists a registered bauble of ours: its bonuses taken off when it comes off, and in the creative tab. */
	static <T extends BaubleItem> T add(T item) {
		for (BaubleItem.Bonus bonus : item.bonuses()) {
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

	/**
	 * Saves what this player wears and puts its bonuses on (server). The stacks themselves are kept, not copies, so the
	 * open bauble screen and the saved slots stay the same objects: a worn totem used up while the screen is open
	 * (FriendsAndFoesCompat) can't come back when it closes.
	 */
	public static void setWorn(ServerPlayer player, List<ItemStack> stacks) {
		List<ItemStack> copy = new ArrayList<>(SIZE);
		for (int i = 0; i < SIZE; i++) {
			copy.add(i < stacks.size() ? stacks.get(i) : ItemStack.EMPTY);
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

	/**
	 * (Artifacts) Whether this player wears {@code item} in a slot that takes it, without copying the list. Works on the
	 * server, and on the client for the local player (the worn list syncs to its wearer).
	 */
	public static boolean wears(Player player, Item item) {
		List<ItemStack> saved = player.getAttached(WORN);
		if (saved == null) {
			return false;
		}
		for (int i = 0; i < saved.size() && i < SIZE; i++) {
			ItemStack stack = saved.get(i);
			if (stack.is(item) && fits(i, stack)) {
				return true;
			}
		}
		return false;
	}

	/** The worn stack of {@code item} (live), or empty: for cooldowns, which are keyed by the stack. */
	public static ItemStack wornStack(Player player, Item item) {
		List<ItemStack> saved = player.getAttached(WORN);
		if (saved != null) {
			for (int i = 0; i < saved.size() && i < SIZE; i++) {
				if (saved.get(i).is(item) && fits(i, saved.get(i))) {
					return saved.get(i);
				}
			}
		}
		return ItemStack.EMPTY;
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
					? bauble.bonuses(worn.get(slot)) : List.of();
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
		show(player, worn);
	}

	/** A hip lantern: any lantern ({@code #minecraft:lanterns}) in the belt slot. */
	public static boolean hipLantern(ItemStack stack) {
		return stack.is(LANTERNS);
	}

	/** (0.20.0) A hip item: a lantern, or anything in {@code #jaymospowers:hip_shown} (a coin purse) in the belt slot. */
	public static boolean hipItem(ItemStack stack) {
		return stack.is(LANTERNS) || stack.is(HIP_SHOWN);
	}

	/** What sits in the belt slot itself (the live stack), or empty: a tool belt stays a tool belt here. */
	public static ItemStack beltSlot(Player player) {
		List<ItemStack> saved = player.getAttached(WORN);
		return saved != null && BELT_SLOT < saved.size() ? saved.get(BELT_SLOT) : ItemStack.EMPTY;
	}

	/**
	 * (0.20.0) What {@code player} wears in the belt slot: the live stack (changing a purse's contents in place changes it),
	 * or empty. Jaymo's Kingdoms reads its coin purse from here ({@code PowersCompat.belt}).
	 * <p>(0.21.43, Andy: "lanterns and coin purses can work from inside the belt") With a Traveler Tool Belt in the slot,
	 * the hip item carried in it instead (a copy: write it back with {@link #setBelt}), a purse before a lantern, or empty.
	 */
	public static ItemStack belt(Player player) {
		ItemStack slot = beltSlot(player);
		if (!ToolBeltCompat.isBelt(slot)) {
			return slot;
		}
		int at = carriedHip(slot);
		return at < 0 ? ItemStack.EMPTY : carried(slot).get(at);
	}

	/** (0.21.43) Copies of what a tool belt carries, in its slot order (empties kept). */
	private static List<ItemStack> carried(ItemStack toolBelt) {
		return toolBelt.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).itemCopies().toList();
	}

	/** (0.21.43) Where in a tool belt the hip item {@link #belt} hands out is: the first purse, else the first lantern, or -1. */
	private static int carriedHip(ItemStack toolBelt) {
		List<ItemStack> items = carried(toolBelt);
		int lantern = -1;
		for (int i = 0; i < items.size(); i++) {
			if (items.get(i).is(HIP_SHOWN)) {
				return i;
			}
			if (lantern < 0 && items.get(i).is(LANTERNS)) {
				lantern = i;
			}
		}
		return lantern;
	}

	/**
	 * (0.20.0) Puts {@code stack} in the belt slot (if it fits there, or is empty), redoing the bonuses and the hip. Kingdoms
	 * calls it after changing a worn purse, or when a pickpocket lifts the whole thing ({@code PowersCompat.setBelt}).
	 * <p>(0.21.43) With a tool belt in the slot, {@code stack} replaces the hip item carried in it ({@link #belt}): an
	 * empty stack takes it out (a lifted purse), and the tool belt stays on. Nothing happens if it carries none.
	 */
	public static void setBelt(ServerPlayer player, ItemStack stack) {
		if (!stack.isEmpty() && !fits(BELT_SLOT, stack)) {
			return;
		}
		List<ItemStack> worn = worn(player);
		ItemStack slot = worn.get(BELT_SLOT);
		if (ToolBeltCompat.isBelt(slot) && (stack.isEmpty() || hipItem(stack))) {
			int at = carriedHip(slot);
			if (at < 0) {
				return;
			}
			List<ItemStack> items = new ArrayList<>(carried(slot));
			items.set(at, stack.copy());
			ItemStack toolBelt = slot.copy();
			toolBelt.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
			worn.set(BELT_SLOT, toolBelt);
			setWorn(player, worn);
			return;
		}
		worn.set(BELT_SLOT, stack);
		setWorn(player, worn);
	}

	/** Syncs the hip lantern and lantern pole others should see, only when they change. */
	private static void show(ServerPlayer player, List<ItemStack> worn) {
		// (0.21.37) the worn looks: only items in a slot that takes them
		List<Identifier> looks = new ArrayList<>(SIZE);
		boolean any = false;
		for (int i = 0; i < SIZE; i++) {
			ItemStack stack = worn.get(i);
			boolean shows = !stack.isEmpty() && fits(i, stack);
			looks.add(shows ? BuiltInRegistries.ITEM.getKey(stack.getItem()) : BuiltInRegistries.ITEM.getDefaultKey());
			any |= shows;
		}
		if (!any) {
			if (player.getAttached(LOOKS) != null) {
				player.removeAttached(LOOKS);
			}
		} else if (!looks.equals(player.getAttached(LOOKS))) {
			player.setAttached(LOOKS, List.copyOf(looks));
		}
		ItemStack hip = hipItem(worn.get(BELT_SLOT)) ? worn.get(BELT_SLOT).copyWithCount(1) : ItemStack.EMPTY;
		// (0.21.43) a lantern or purse carried in a worn tool belt hangs at the hip too
		if (hip.isEmpty() && ToolBeltCompat.isBelt(worn.get(BELT_SLOT))) {
			int at = carriedHip(worn.get(BELT_SLOT));
			hip = at < 0 ? ItemStack.EMPTY : carried(worn.get(BELT_SLOT)).get(at).copyWithCount(1);
		}
		ItemStack back = worn.get(BODY_SLOT).is(LANTERN_POLE) ? worn.get(BODY_SLOT).copyWithCount(1) : ItemStack.EMPTY;
		List<ItemStack> now = player.getAttached(SHOWN);
		if (hip.isEmpty() && back.isEmpty()) {
			if (now != null) {
				player.removeAttached(SHOWN);
			}
			return;
		}
		// (0.20.0) same components too: a purse's fullness (its model data) changes what is drawn
		if (now == null || now.size() != 2 || !ItemStack.isSameItemSameComponents(now.get(0), hip) || !ItemStack.isSameItem(now.get(1), back)
			|| now.get(0).isEmpty() != hip.isEmpty() || now.get(1).isEmpty() != back.isEmpty()) {
			player.setAttached(SHOWN, List.of(hip, back));
		}
	}

	/** (0.21.37) The item id worn in each slot, as everyone sees it (empty when nothing is worn; client and server). */
	public static List<Identifier> looks(Player player) {
		List<Identifier> now = player.getAttached(LOOKS);
		return now == null ? List.of() : now;
	}

	/** What a player shows hung on them: {hip item (a lantern, or a coin purse), lantern pole}, either empty (client and server). */
	public static ItemStack shown(Player player, int which) {
		List<ItemStack> now = player.getAttached(SHOWN);
		return now != null && which < now.size() ? now.get(which) : ItemStack.EMPTY;
	}

	/**
	 * Worn baubles tick as if carried, so an item that works from the inventory (the Runic Amulet) works from its slot
	 * too. No equipment slot is passed: to the item it is just carried.
	 */
	private static void tickWorn(ServerPlayer player) {
		List<ItemStack> saved = player.getAttached(WORN);
		if (saved == null) {
			return;
		}
		for (ItemStack stack : saved) {
			if (!stack.isEmpty()) {
				stack.getItem().inventoryTick(stack, player.level(), player, null);
			}
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
		Item picture = PICTURES.get(kind);
		return picture != null ? picture : HEARTSTONE_AMULET;
	}

	/** (0.19.87) Sets the picture an empty slot of this kind shows when no bauble of the seven has that kind. */
	public static void picture(Kind kind, Item item) {
		PICTURES.put(kind, item);
	}

	/** (0.19.87) Lets bonuses on this attribute be taken off again when the piece comes off (jewellery). */
	public static void touch(Holder<Attribute> attribute) {
		TOUCHED.add(attribute);
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
