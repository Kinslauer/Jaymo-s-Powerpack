package com.jaymospowers.bonebond;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.compat.KingdomsCompat;
import com.jaymospowers.core.PickKit;
import com.jaymospowers.core.fx.MagicFx;
import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.material.MapColor;

/**
 * (Awakening pick) Metal bonded to bone. <b>Boneiron</b> is a rare deep ore (deepslate, far down); smelted, its ingots go into
 * a <b>Bonding Vat</b>'s brine. Someone whose body heals fast (a Verdant, a vampire or a werewolf; a Z Fighter of the Verdant
 * line) climbs in and settles (sneaking) for {@link #BOND_TICKS}, hurting all the while, and the metal goes into their
 * bones: one tier more, up to {@link #TIERS}, each tier {@link #TIER_ARMOR} armour, {@link #TIER_TOUGHNESS} toughness,
 * {@link #TIER_ATTACK} attack and {@link #TIER_KNOCKBACK} knockback, kept through death. Anyone else can't take it in.
 * Or plate armour with it: a boneiron plate and a piece of armour in a Tool Forge
 * ({@link com.jaymospowers.tinkering.ToolStationMenu}), up to {@link #PLATINGS} a piece, each a smaller share of the same
 * four while worn.
 * <p>Lore: in the deepest deepslate, where the world's oldest bones lie pressed into the stone, the iron has taken something of
 * them, and it takes to living bone the way other metals take to fire, if the body can mend round it fast enough.
 */
public final class Boneiron {
	// ---- tuning ----
	/** Tiers in all; ingots each tier takes from the vat (tier n: n + 1); the most ingots a vat holds. */
	public static final int TIERS = 5, VAT_MOST = 6;
	/** What each bonded tier gives. */
	static final double TIER_ARMOR = 2.0, TIER_TOUGHNESS = 1.0, TIER_ATTACK = 1.0, TIER_KNOCKBACK = 0.2;
	/** Platings a piece of armour takes, and what each worn plating gives. */
	public static final int PLATINGS = 3;
	static final double PLATE_ARMOR = 0.5, PLATE_TOUGHNESS = 0.25, PLATE_ATTACK = 0.25, PLATE_KNOCKBACK = 0.05;
	/** How long the bonding takes (ticks), and the harm it does each second. */
	static final int BOND_TICKS = 200;
	static final float BOND_HARM = 1.0F;
	/** How often the bones' and platings' bonuses are looked at again (ticks). */
	static final int CHECK_TICKS = 20;
	static final int COLOR = 0xE8DCC0;

	/** Tiers bonded to {@code player}'s bones. Saved, kept through death, synced to its player (the vat tells them). */
	public static final AttachmentType<Integer> TIER = AttachmentRegistry.create(JaymosPowers.id("boneiron_tier"),
		builder -> builder.persistent(Codec.INT).copyOnDeath().syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.targetOnly()));

	public static final ResourceKey<PlacedFeature> ORE_FEATURE = ResourceKey.create(Registries.PLACED_FEATURE, JaymosPowers.id("ore_boneiron"));

	/** Platings on a piece of armour. */
	public static DataComponentType<Integer> PLATED;
	public static Block ORE, VAT;
	public static Item RAW, INGOT, PLATE;

	private static final Map<UUID, Integer> BONDING = new HashMap<>();

	private Boneiron() {
	}

	public static void init() {
		PLATED = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosPowers.id("boneiron_plated"),
			DataComponentType.<Integer>builder().persistent(Codec.intRange(0, PLATINGS)).networkSynchronized(ByteBufCodecs.VAR_INT).build());
		ORE = block("deepslate_boneiron_ore", Block::new, BlockBehaviour.Properties.of().mapColor(MapColor.DEEPSLATE)
			.instrument(NoteBlockInstrument.BASEDRUM).strength(5.0F, 3.0F).sound(SoundType.DEEPSLATE).requiresCorrectToolForDrops());
		VAT = block("bonding_vat", BondingVatBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.0F)
			.sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion());
		RAW = item("raw_boneiron", Item::new);
		INGOT = item("boneiron_ingot", Item::new);
		PLATE = item("boneiron_plate", BoneironPlateItem::new);
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(output -> {
			output.accept(ORE);
			output.accept(RAW);
			output.accept(INGOT);
			output.accept(PLATE);
			output.accept(VAT);
		});
		BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), GenerationStep.Decoration.UNDERGROUND_ORES, ORE_FEATURE);
		ServerTickEvents.END_SERVER_TICK.register(Boneiron::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> BONDING.remove(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> BONDING.clear());
	}

	private static Block block(String id, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosPowers.id(id));
		Block block = Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, JaymosPowers.id(id));
		Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
		return block;
	}

	private static Item item(String id, Function<Item.Properties, Item> factory) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id(id));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(new Item.Properties().setId(key).rarity(Rarity.UNCOMMON)));
	}

	/** Tiers bonded to {@code player}'s bones (either side). */
	public static int tier(Player player) {
		return player.getAttachedOrElse(TIER, 0);
	}

	/** Ingots tier {@code tier} takes from the vat. */
	public static int cost(int tier) {
		return tier + 1;
	}

	/** Whether {@code player}'s body heals fast enough to take metal into the bone: Kingdoms' Verdant, vampires and werewolves; a Verdant Z Fighter. */
	public static boolean healsFast(ServerPlayer player) {
		if (com.jaymospowers.core.Powers.has(player, com.jaymospowers.power.zfighter.ZFighterPower.INSTANCE)
			&& com.jaymospowers.power.zfighter.ZFighterLineage.of(player) == com.jaymospowers.power.zfighter.ZFighterLineage.VERDANT) {
			return true;
		}
		return Boolean.TRUE.equals(KingdomsCompat.call("Heritage", "healsFast", new Class<?>[] {Player.class}, Boolean.FALSE, player));
	}

	private static void tick(MinecraftServer server) {
		boolean check = server.getTickCount() % CHECK_TICKS == 0;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			bond(player);
			if (check) {
				apply(player);
			}
		}
	}

	/** Settled in a vat: the bonding goes on, hurting; leaving it, or standing up, stops it. */
	private static void bond(ServerPlayer player) {
		ServerLevel level = player.level();
		BlockPos pos = player.blockPosition();
		BlockState state = level.getBlockState(pos);
		boolean in = state.is(VAT) && player.isShiftKeyDown() && player.isAlive() && !player.isSpectator();
		if (!in) {
			BONDING.remove(player.getUUID());
			return;
		}
		int next = tier(player) + 1;
		if (next > TIERS) {
			hint(player, "message.jaymospowers.bonebond.full");
			return;
		}
		if (state.getValue(BondingVatBlock.METAL) < cost(next)) {
			hint(player, "message.jaymospowers.bonebond.short");
			return;
		}
		if (!healsFast(player)) {
			hint(player, "message.jaymospowers.bonebond.refused");
			return;
		}
		int t = BONDING.merge(player.getUUID(), 1, Integer::sum);
		if (t % 20 == 0) {
			player.hurtServer(level, level.damageSources().magic(), BOND_HARM);
			MagicFx.orb(level, player.position().add(0.0, 0.6, 0.0), 0.8F, COLOR, 10);
			level.playSound(null, pos, SoundEvents.BONE_BLOCK_BREAK, SoundSource.PLAYERS, 0.8F, 0.6F);
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.bonebond.bonding", PickKit.gauge(t / (float)BOND_TICKS, 10))
				.withStyle(ChatFormatting.WHITE));
		}
		if (t >= BOND_TICKS) {
			BONDING.remove(player.getUUID());
			level.setBlock(pos, state.setValue(BondingVatBlock.METAL, state.getValue(BondingVatBlock.METAL) - cost(next)), Block.UPDATE_ALL);
			player.setAttached(TIER, next);
			apply(player);
			MagicFx.ring(level, player.position().add(0.0, 0.2, 0.0), 1.6F, COLOR);
			level.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 1.0F, 0.6F);
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.bonebond.done", next, TIERS).withStyle(ChatFormatting.GOLD));
		}
	}

	private static void hint(ServerPlayer player, String key) {
		if (player.tickCount % 40 == 0) {
			player.sendOverlayMessage(Component.translatable(key).withStyle(ChatFormatting.GRAY));
		}
	}

	/** The bones' tiers and the worn platings, as attribute modifiers. */
	private static void apply(ServerPlayer player) {
		int tier = tier(player), plates = 0;
		for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			ItemStack worn = player.getItemBySlot(slot);
			plates += BoneironPlateItem.armour(worn) ? worn.getOrDefault(PLATED, 0) : 0;
		}
		set(player, Attributes.ARMOR, "boneiron_armor", tier * TIER_ARMOR + plates * PLATE_ARMOR);
		set(player, Attributes.ARMOR_TOUGHNESS, "boneiron_toughness", tier * TIER_TOUGHNESS + plates * PLATE_TOUGHNESS);
		set(player, Attributes.ATTACK_DAMAGE, "boneiron_attack", tier * TIER_ATTACK + plates * PLATE_ATTACK);
		set(player, Attributes.ATTACK_KNOCKBACK, "boneiron_knockback", tier * TIER_KNOCKBACK + plates * PLATE_KNOCKBACK);
	}

	private static void set(ServerPlayer player, Holder<Attribute> attribute, String id, double amount) {
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance == null) {
			return;
		}
		Identifier key = JaymosPowers.id(id);
		AttributeModifier had = instance.getModifier(key);
		if (had != null && had.amount() == amount) {
			return;
		}
		instance.removeModifier(key);
		if (amount != 0.0) {
			instance.addTransientModifier(new AttributeModifier(key, amount, AttributeModifier.Operation.ADD_VALUE));
		}
	}
}
