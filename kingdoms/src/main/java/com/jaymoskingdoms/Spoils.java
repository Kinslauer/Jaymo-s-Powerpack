package com.jaymoskingdoms;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * (0.7.435) Havens patch 5, spoils (ideas from King's Monsters, ARR: our own names, code and art). What a fallen
 * greenskin or ogre leaves the one who felled him.
 * <ul>
 * <li><b>Trophies worn as baubles</b> (Jaymo's Powers' bauble slots, by its {@code jaymospowers:baubles/<slot>} tags): a
 * captain who stays dead leaves his tusk (a charm, named for him); a goblin king his ring, an orc warchief his necklace of
 * tusks, the Ogre King his hide (a cloak). Each gives its bonus only while worn in its slot, and every one adds to your
 * Will in a staredown or a clash ({@link Staredowns#will}).</li>
 * <li><b>Weapons taken from kings:</b> a tribe's king (the warchief or goblin king at its seat) and the Ogre King carry
 * their own weapon; when he falls its iron head drops, to be put together at Powers' Tool Station (the Gutting Hook, the
 * Warchief's Cleaver, the Ogre King's Maul). No other route makes one.</li>
 * <li><b>Monster crowns:</b> the same kings drop their crowns. Worn on the head (right-click) they're drawn there and
 * add to your Will; placed, they sit on a shelf or a throne like any trophy.</li>
 * </ul>
 */
public final class Spoils {
	/** Will (staredowns, Will clashes): a worn captain's tusk (more from a high-ranked one), a worn king's trophy, a worn crown. */
	static final int TUSK_WILL = 1, TUSK_HIGH_RANK = 3, TUSK_HIGH_WILL = 1, KING_TROPHY_WILL = 1, CROWN_WILL = 2;
	/** Worn bonuses: the goblin king's ring (luck, speed), the necklace of tusks (attack), the Ogre King's hide (armour, footing). */
	static final double RING_LUCK = 1.0, RING_SPEED = 0.05, TUSKS_DAMAGE = 1.5, HIDE_ARMOR = 3.0, HIDE_KNOCKBACK = 0.15;
	/** A worn crown's armour. */
	static final double CROWN_ARMOR = 1.0;
	/** How often (ticks) worn trophies are looked at again; how often a king checks he still holds his weapon. */
	static final int REFRESH_TICKS = 20, ARM_TICKS = 100;
	/** Powers' king weapons and their heads, by id (namespace jaymospowers). */
	static final String HOOK = "gutting_hook", CLEAVER = "warchief_cleaver", MAUL = "ogre_kings_maul";
	static final String HOOK_HEAD = "gutting_hook_blade", CLEAVER_HEAD = "warchief_cleaver_blade", MAUL_HEAD = "ogre_maul_head";
	/** The key a captain's tusk keeps his rank under (in its custom data). */
	static final String RANK_KEY = "jaymoskingdoms_captain_rank";

	public static Item CAPTAINS_TUSK, GOBLIN_KINGS_RING, NECKLACE_OF_TUSKS, OGRE_KINGS_HIDE;
	public static Block GOBLIN_KINGS_CROWN, ORC_KINGS_CROWN, OGRE_KINGS_CROWN;
	public static Item GOBLIN_KINGS_CROWN_ITEM, ORC_KINGS_CROWN_ITEM, OGRE_KINGS_CROWN_ITEM;

	private static final Identifier RING_LUCK_ID = JaymosKingdoms.id("spoils_ring_luck"), RING_SPEED_ID = JaymosKingdoms.id("spoils_ring_speed"),
		TUSKS_ID = JaymosKingdoms.id("spoils_tusks"), HIDE_ARMOR_ID = JaymosKingdoms.id("spoils_hide_armor"),
		HIDE_KNOCKBACK_ID = JaymosKingdoms.id("spoils_hide_knockback");

	private Spoils() {
	}

	static void register() {
		CAPTAINS_TUSK = item("captains_tusk", p -> new Trophy(p, "captains_tusk"), new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
		GOBLIN_KINGS_RING = item("goblin_kings_ring", p -> new Trophy(p, "goblin_kings_ring"), new Item.Properties().stacksTo(1).rarity(Rarity.RARE));
		NECKLACE_OF_TUSKS = item("necklace_of_tusks", p -> new Trophy(p, "necklace_of_tusks"), new Item.Properties().stacksTo(1).rarity(Rarity.RARE));
		OGRE_KINGS_HIDE = item("ogre_kings_hide", p -> new Trophy(p, "ogre_kings_hide"), new Item.Properties().stacksTo(1).rarity(Rarity.RARE));
		GOBLIN_KINGS_CROWN = crown("goblin_kings_crown", Block.box(3, 0, 3, 13, 5, 13), MapColor.GOLD, SoundType.METAL);
		ORC_KINGS_CROWN = crown("orc_kings_crown", Block.box(3, 0, 3, 13, 7, 13), MapColor.COLOR_BLACK, SoundType.METAL);
		OGRE_KINGS_CROWN = crown("ogre_kings_crown", Block.box(2, 0, 2, 14, 6, 14), MapColor.STONE, SoundType.STONE);
		GOBLIN_KINGS_CROWN_ITEM = crownItem("goblin_kings_crown", GOBLIN_KINGS_CROWN);
		ORC_KINGS_CROWN_ITEM = crownItem("orc_kings_crown", ORC_KINGS_CROWN);
		OGRE_KINGS_CROWN_ITEM = crownItem("ogre_kings_crown", OGRE_KINGS_CROWN);
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> {
			for (Item item : List.of(CAPTAINS_TUSK, GOBLIN_KINGS_RING, NECKLACE_OF_TUSKS, OGRE_KINGS_HIDE, GOBLIN_KINGS_CROWN_ITEM,
				ORC_KINGS_CROWN_ITEM, OGRE_KINGS_CROWN_ITEM)) {
				output.accept(item);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % REFRESH_TICKS == 0) {
				for (ServerPlayer player : server.getPlayerList().getPlayers()) {
					refresh(player);
				}
			}
		});
	}

	private static Item item(String name, Function<Item.Properties, Item> make, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, make.apply(properties.setId(key)));
	}

	private static Block crown(String name, VoxelShape shape, MapColor color, SoundType sound) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosKingdoms.id(name));
		return Registry.register(BuiltInRegistries.BLOCK, key, new Crown(BlockBehaviour.Properties.of().setId(key).mapColor(color).sound(sound)
			.strength(0.8F).noOcclusion().pushReaction(PushReaction.POPPED), shape));
	}

	/** A crown's item: placed, it's the block; right-clicked in the air (or put in the helmet slot), it's worn and drawn on the head. */
	private static Item crownItem(String name, Block block) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id(name));
		Item.Properties properties = new Item.Properties().setId(key).useBlockDescriptionPrefix().stacksTo(1).rarity(Rarity.EPIC)
			.equippable(EquipmentSlot.HEAD)
			.attributes(ItemAttributeModifiers.builder()
				.add(Attributes.ARMOR, new AttributeModifier(JaymosKingdoms.id("crown_armor"), CROWN_ARMOR, AttributeModifier.Operation.ADD_VALUE),
					EquipmentSlotGroup.HEAD)
				.build());
		return Registry.register(BuiltInRegistries.ITEM, key, new CrownItem(block, properties));
	}

	// ---- what falls -------------------------------------------------------------------------------------------------

	/** Whether a player had the felling of {@code dead} (the blow, or the last hurt that counts). */
	private static boolean byPlayer(LivingEntity dead, DamageSource source) {
		return source.getEntity() instanceof Player || dead.getKillCredit() instanceof Player;
	}

	/**
	 * ({@code Camps.chiefSlain}) A goblin village's king or an orc camp's warchief fell: his trophy; and if his camp is
	 * his tribe's seat (he was its king), his crown and his weapon's head.
	 */
	static void kingFell(ServerLevel level, LivingEntity chief, int campId, DamageSource source) {
		MinecraftServer server = level.getServer();
		boolean goblin = Camps.goblin(server, campId), orc = Camps.orc(server, campId);
		if ((!goblin && !orc) || !byPlayer(chief, source)) {
			return;
		}
		drop(level, chief, new ItemStack(goblin ? GOBLIN_KINGS_RING : NECKLACE_OF_TUSKS));
		String tribe = Tribes.of(server, campId);
		Faction seat = tribe == null ? null : Tribes.seat(server, tribe);
		if (seat == null || seat.id() != campId) {
			return;
		}
		drop(level, chief, new ItemStack(goblin ? GOBLIN_KINGS_CROWN_ITEM : ORC_KINGS_CROWN_ITEM));
		ItemStack head = powers(goblin ? HOOK_HEAD : CLEAVER_HEAD);
		if (!head.isEmpty()) {
			drop(level, chief, head);
		}
		level.playSound(null, chief.getX(), chief.getY(), chief.getZ(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 0.6F, 0.6F);
	}

	/** ({@code OgreHolds}) The Ogre King fell: his hide, his crown, his maul's head. */
	static void ogreKingFell(ServerLevel level, LivingEntity king, DamageSource source) {
		if (!byPlayer(king, source)) {
			return;
		}
		drop(level, king, new ItemStack(OGRE_KINGS_HIDE));
		drop(level, king, new ItemStack(OGRE_KINGS_CROWN_ITEM));
		ItemStack head = powers(MAUL_HEAD);
		if (!head.isEmpty()) {
			drop(level, king, head);
		}
	}

	/** ({@code Captains.died}) A captain fell for good to a player's hand: his tusk, named for him. */
	static void captainFell(ServerLevel level, LivingEntity body, int rank, DamageSource source) {
		if (!byPlayer(body, source)) {
			return;
		}
		ItemStack tusk = new ItemStack(CAPTAINS_TUSK);
		tusk.set(DataComponents.ITEM_NAME, Component.translatable("item.jaymoskingdoms.captains_tusk.of", body.getDisplayName()));
		CompoundTag tag = new CompoundTag();
		tag.putInt(RANK_KEY, rank);
		tusk.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		drop(level, body, tusk);
	}

	private static void drop(ServerLevel level, LivingEntity from, ItemStack stack) {
		from.spawnAtLocation(level, stack, 0.5F);
	}

	/** One of Jaymo's Powers' items by id (a king's weapon head), or empty without Powers. */
	private static ItemStack powers(String id) {
		return BuiltInRegistries.ITEM.getOptional(Identifier.fromNamespaceAndPath("jaymospowers", id)).map(ItemStack::new).orElse(ItemStack.EMPTY);
	}

	// ---- kings carry their weapons ----------------------------------------------------------------------------------

	/**
	 * ({@code CampChiefs.step}) A tribe's king holds his own weapon (with Powers): the goblin king his Gutting Hook, the
	 * orc king his Warchief's Cleaver. It never drops whole; its head does ({@link #kingFell}).
	 */
	static void arm(ServerLevel level, Mob chief, int role, int campId) {
		if (chief.tickCount % ARM_TICKS != 0) {
			return;
		}
		String tribe = Tribes.of(level.getServer(), campId);
		Faction seat = tribe == null ? null : Tribes.seat(level.getServer(), tribe);
		if (seat == null || seat.id() != campId) {
			return;
		}
		ItemStack weapon = powers(role == Goblins.KING ? HOOK : CLEAVER);
		if (weapon.isEmpty() || chief.getMainHandItem().is(weapon.getItem())) {
			return;
		}
		chief.setItemSlot(EquipmentSlot.MAINHAND, weapon);
		chief.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
	}

	// ---- worn -------------------------------------------------------------------------------------------------------

	/** {@code player}'s Will from what they wear: a captain's tusk, a king's trophy, a crown on their head. */
	static int will(ServerPlayer player) {
		int will = 0;
		for (ItemStack stack : PowersCompat.baubles(player)) {
			if (stack.is(CAPTAINS_TUSK)) {
				will += TUSK_WILL + (rank(stack) >= TUSK_HIGH_RANK ? TUSK_HIGH_WILL : 0);
			} else if (stack.is(GOBLIN_KINGS_RING) || stack.is(NECKLACE_OF_TUSKS) || stack.is(OGRE_KINGS_HIDE)) {
				will += KING_TROPHY_WILL;
			}
		}
		if (crowned(player)) {
			will += CROWN_WILL;
		}
		return will;
	}

	/** Whether {@code e} wears one of the monster crowns. */
	public static boolean crowned(LivingEntity e) {
		ItemStack head = e.getItemBySlot(EquipmentSlot.HEAD);
		return head.is(GOBLIN_KINGS_CROWN_ITEM) || head.is(ORC_KINGS_CROWN_ITEM) || head.is(OGRE_KINGS_CROWN_ITEM);
	}

	private static int rank(ItemStack tusk) {
		return tusk.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getIntOr(RANK_KEY, 0);
	}

	/** Puts on the bonuses of the trophies {@code player} wears in their bauble slots and takes off the rest. */
	private static void refresh(ServerPlayer player) {
		boolean ring = false, tusks = false, hide = false;
		for (ItemStack stack : PowersCompat.baubles(player)) {
			ring |= stack.is(GOBLIN_KINGS_RING);
			tusks |= stack.is(NECKLACE_OF_TUSKS);
			hide |= stack.is(OGRE_KINGS_HIDE);
		}
		bonus(player, Attributes.LUCK, RING_LUCK_ID, ring, RING_LUCK, AttributeModifier.Operation.ADD_VALUE);
		bonus(player, Attributes.MOVEMENT_SPEED, RING_SPEED_ID, ring, RING_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		bonus(player, Attributes.ATTACK_DAMAGE, TUSKS_ID, tusks, TUSKS_DAMAGE, AttributeModifier.Operation.ADD_VALUE);
		bonus(player, Attributes.ARMOR, HIDE_ARMOR_ID, hide, HIDE_ARMOR, AttributeModifier.Operation.ADD_VALUE);
		bonus(player, Attributes.KNOCKBACK_RESISTANCE, HIDE_KNOCKBACK_ID, hide, HIDE_KNOCKBACK, AttributeModifier.Operation.ADD_VALUE);
	}

	private static void bonus(ServerPlayer player, Holder<Attribute> attribute, Identifier id, boolean on, double amount, AttributeModifier.Operation op) {
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance == null) {
			return;
		}
		if (on && !instance.hasModifier(id)) {
			instance.addTransientModifier(new AttributeModifier(id, amount, op));
		} else if (!on && instance.hasModifier(id)) {
			instance.removeModifier(id);
		}
	}

	// ---- the items --------------------------------------------------------------------------------------------------

	/** A trophy: what it is and what it does worn (Powers' bauble slots take it by tag). */
	static final class Trophy extends Item {
		private final String id;

		Trophy(Item.Properties properties, String id) {
			super(properties);
			this.id = id;
		}

		@Override
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
			builder.accept(Component.translatable("item.jaymoskingdoms." + this.id + ".worn").withStyle(ChatFormatting.BLUE));
			builder.accept(Component.translatable("item.jaymoskingdoms.trophy.will").withStyle(ChatFormatting.GRAY));
		}
	}

	/** A crown: placed, or worn. */
	static final class CrownItem extends BlockItem {
		CrownItem(Block block, Item.Properties properties) {
			super(block, properties);
		}

		@Override
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
			builder.accept(Component.translatable("item.jaymoskingdoms.monster_crown.hint").withStyle(ChatFormatting.GRAY));
		}
	}

	/** A crown set down: small, no collision worth the name. */
	static final class Crown extends Block {
		private final VoxelShape shape;

		Crown(BlockBehaviour.Properties properties, VoxelShape shape) {
			super(properties);
			this.shape = shape;
		}

		@Override
		protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
			return this.shape;
		}
	}
}
