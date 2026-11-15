package com.jaymospowers.enchanting;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.jewellery.CutGem;
import com.jaymospowers.jewellery.Gems;
import com.jaymospowers.jewellery.Jewellery;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Medieval picks (me-inscribe): the Inscription Table. Lay a piece of gear on it and set a gem beside it, then take a
 * chisel to it and choose a word of power to carve: the word enchants the gear one level. The gem decides which words
 * are open ({@link #WORDS}: an emerald's are of luck, a diamond's of guarding, lapis's of water and so on), and each gem
 * carries three carvings before it is spent ({@link #GEM_CARVINGS}). A raw gem or a cut one both serve. Carving keeps the
 * Enchanting overhaul's rules: a word goes only on gear it suits, never past its top level, never beside a word it
 * clashes with, and never past what the item holds ({@link Enchanting#limit}). Jaymo's Kingdoms gates the table behind
 * the Knowledge (as it does the enchanting table). Art, data and lang: tools/gen_medieval_smithing.py.
 */
public final class Inscription {
	// ---- tuning ----
	/** Carvings one gem gives before it's spent. */
	public static final int GEM_CARVINGS = 3;
	/** Uses a chisel lasts (one per carving). */
	public static final int CHISEL_USES = 64;
	/** How far from the table a carver may stand (blocks, squared) for a choice to count. */
	static final double REACH_SQ = 8 * 8;

	/** The words each gem opens (enchantment ids), by {@link Gems.Gem#id}. */
	public static final Map<String, List<String>> WORDS = Map.of(
		"emerald", List.of("minecraft:fortune", "minecraft:looting", "minecraft:luck_of_the_sea", "minecraft:lure"),
		"diamond", List.of("minecraft:protection", "minecraft:sharpness", "minecraft:unbreaking", "minecraft:projectile_protection",
			"minecraft:blast_protection", "minecraft:feather_falling"),
		"amethyst", List.of("minecraft:power", "minecraft:punch", "minecraft:piercing", "minecraft:multishot", "minecraft:quick_charge"),
		"lapis", List.of("minecraft:aqua_affinity", "minecraft:depth_strider", "minecraft:respiration", "minecraft:riptide",
			"minecraft:loyalty", "minecraft:impaling", "minecraft:channeling"),
		"quartz", List.of("minecraft:fire_protection", "minecraft:fire_aspect", "minecraft:flame", "minecraft:smite"),
		"zanite", List.of("minecraft:efficiency", "minecraft:silk_touch", "minecraft:unbreaking"),
		"glint", List.of("minecraft:swift_sneak", "minecraft:soul_speed", "minecraft:sweeping_edge", "minecraft:lunge"),
		"corrobonite", List.of("minecraft:knockback", "jaymospowers:pummelling", "jaymospowers:barbed", "minecraft:density",
			"minecraft:breach"),
		"golden_amber", List.of("minecraft:bane_of_arthropods", "minecraft:frost_walker", "minecraft:wind_burst"));

	/** Carvings made with a gem so far. */
	public static DataComponentType<Integer> CARVINGS;
	public static Block INSCRIPTION_TABLE;
	public static BlockEntityType<InscriptionTableEntity> ENTITY;
	public static Item CHISEL;

	/** One word the carver may choose: the enchantment and the level it would take. */
	public record Word(String id, int level) {
		static final StreamCodec<ByteBuf, Word> CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, Word::id, ByteBufCodecs.VAR_INT,
			Word::level, Word::new);
	}

	/** Server → client: the words open on this table. */
	public record Open(BlockPos pos, ItemStack gear, int gemColour, List<Word> words) implements CustomPacketPayload {
		public static final Type<Open> TYPE = new Type<>(JaymosPowers.id("inscription_open"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Open> CODEC = StreamCodec.composite(BlockPos.STREAM_CODEC, Open::pos,
			ItemStack.STREAM_CODEC, Open::gear, ByteBufCodecs.INT, Open::gemColour, Word.CODEC.apply(ByteBufCodecs.list()), Open::words, Open::new);

		@Override
		public Type<Open> type() {
			return TYPE;
		}
	}

	/** Client → server: carve this word. */
	public record Carve(BlockPos pos, String id) implements CustomPacketPayload {
		public static final Type<Carve> TYPE = new Type<>(JaymosPowers.id("inscription_carve"));
		public static final StreamCodec<ByteBuf, Carve> CODEC = StreamCodec.composite(BlockPos.STREAM_CODEC, Carve::pos, ByteBufCodecs.STRING_UTF8,
			Carve::id, Carve::new);

		@Override
		public Type<Carve> type() {
			return TYPE;
		}
	}

	private Inscription() {
	}

	/** From {@link Enchanting#init}. */
	static void init() {
		CARVINGS = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosPowers.id("gem_carvings"),
			DataComponentType.<Integer>builder().persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT).build());
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosPowers.id("inscription_table"));
		INSCRIPTION_TABLE = Registry.register(BuiltInRegistries.BLOCK, key, new InscriptionTableBlock(BlockBehaviour.Properties.of()
			.mapColor(MapColor.STONE).strength(2.5F, 6.0F).sound(SoundType.STONE).requiresCorrectToolForDrops().noOcclusion().setId(key)));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, key.identifier());
		Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(INSCRIPTION_TABLE, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
		ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, key.identifier(),
			new BlockEntityType<>(InscriptionTableEntity::new, Set.of(INSCRIPTION_TABLE)));
		ResourceKey<Item> chiselKey = ResourceKey.create(Registries.ITEM, JaymosPowers.id("chisel"));
		CHISEL = Registry.register(BuiltInRegistries.ITEM, chiselKey, new Item(new Item.Properties().setId(chiselKey).durability(CHISEL_USES)));

		PayloadTypeRegistry.clientboundPlay().register(Open.TYPE, Open.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Carve.TYPE, Carve.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Carve.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			if (player.blockPosition().distSqr(payload.pos()) <= REACH_SQ && com.jaymospowers.core.Loaded.made(player.level(), payload.pos())
				&& player.level().getBlockEntity(payload.pos()) instanceof InscriptionTableEntity table) {
				carve(player, table, payload.id());
			}
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> output.insertAfter(Items.ENCHANTING_TABLE,
			INSCRIPTION_TABLE));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> output.accept(CHISEL));
	}

	/** The gem this stack is (a raw gem or a cut one), or null. */
	public static Gems.@Nullable Gem gem(ItemStack stack) {
		CutGem cut = stack.get(Jewellery.CUT_GEM);
		return cut != null ? cut.gemType() : Gems.of(stack);
	}

	/** Whether gear can go on the table: anything that takes enchantments, books aside. */
	static boolean gear(ItemStack stack) {
		return !stack.is(Items.BOOK) && !stack.is(Items.ENCHANTED_BOOK) && Enchanting.enchantable(stack);
	}

	/** The words this gem can carve into this gear now. */
	static List<Word> words(ServerLevel level, ItemStack gear, ItemStack gemStack) {
		List<Word> out = new ArrayList<>();
		Gems.Gem gem = gem(gemStack);
		if (gem == null || !gear(gear)) {
			return out;
		}
		Registry<Enchantment> registry = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		ItemEnchantments on = EnchantmentHelper.getEnchantmentsForCrafting(gear);
		int curses = Enchanting.curses(on);
		for (String id : WORDS.getOrDefault(gem.id(), List.of())) {
			Identifier key = Identifier.tryParse(id);
			Holder<Enchantment> word = key == null ? null : registry.get(key).orElse(null);
			if (word == null || word.is(EnchantmentTags.CURSE) || !word.value().canEnchant(gear)) {
				continue;
			}
			int current = on.getLevel(word);
			if (current >= EnchantRecipes.maxLevel(word) || Enchanting.clashes(on, word)
				|| current == 0 && on.size() - curses >= Enchanting.limit(gear, curses)) {
				continue;
			}
			out.add(new Word(id, current + 1));
		}
		return out;
	}

	/** The carver chose a word: it goes into the gear, the chisel wears, and the gem spends one carving. */
	static void carve(ServerPlayer player, InscriptionTableEntity table, String id) {
		ServerLevel level = (ServerLevel) player.level();
		InteractionHand hand = chiselHand(player);
		if (hand == null) {
			return;
		}
		Word chosen = null;
		for (Word word : words(level, table.gear(), table.gem())) {
			if (word.id().equals(id)) {
				chosen = word;
			}
		}
		if (chosen == null) {
			return;
		}
		Holder<Enchantment> word = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(Identifier.parse(chosen.id())).orElseThrow();
		int target = chosen.level();
		ItemStack gear = table.gear().copy();
		EnchantmentHelper.updateEnchantments(gear, enchantments -> enchantments.set(word, target));
		ItemStack gemStack = table.gem().copy();
		Gems.Gem gem = gem(gemStack);
		int carved = gemStack.getOrDefault(CARVINGS, 0) + 1;
		BlockPos pos = table.getBlockPos();
		Vec3 top = Vec3.atCenterOf(pos).add(0.0, 0.55, 0.0);
		int colour = gem == null ? MagicFx.RUNE_COLOR : gem.colour();
		if (carved >= GEM_CARVINGS) {
			gemStack = ItemStack.EMPTY;
			level.playSound(null, pos, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.BLOCKS, 1.0F, 0.8F);
		} else {
			gemStack.set(CARVINGS, carved);
		}
		table.set(gear, gemStack);
		player.getItemInHand(hand).hurtAndBreak(1, player, hand);
		player.swing(hand, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		level.playSound(null, pos, SoundEvents.STONE_HIT, SoundSource.BLOCKS, 1.0F, 1.3F);
		level.playSound(null, pos, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 0.8F, 1.1F);
		level.sendParticles(MagicFx.rune(colour, 0.8F), top.x, top.y, top.z, 10, 0.2, 0.05, 0.2, 0.02);
		MagicFx.sigil(level, top, new Vec3(0.0, 1.0, 0.0), 0.45F, colour, 20);
	}

	static @Nullable InteractionHand chiselHand(ServerPlayer player) {
		for (InteractionHand hand : InteractionHand.values()) {
			if (player.getItemInHand(hand).is(CHISEL)) {
				return hand;
			}
		}
		return null;
	}

	/** A chisel was taken to the table: the carver sees the words open, or the table rings dull if there are none. */
	static void open(ServerPlayer player, InscriptionTableEntity table) {
		ServerLevel level = (ServerLevel) player.level();
		List<Word> words = words(level, table.gear(), table.gem());
		if (words.isEmpty()) {
			level.playSound(null, table.getBlockPos(), SoundEvents.STONE_HIT, SoundSource.BLOCKS, 0.6F, 0.6F);
			return;
		}
		Gems.Gem gem = gem(table.gem());
		ServerPlayNetworking.send(player, new Open(table.getBlockPos(), table.gear().copy(), gem == null ? MagicFx.RUNE_COLOR : gem.colour(), words));
	}
}
