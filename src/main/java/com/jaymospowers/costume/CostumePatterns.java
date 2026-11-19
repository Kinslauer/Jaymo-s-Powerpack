package com.jaymospowers.costume;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.gifted.GiftedCostume;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetComponentsFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import org.jspecify.annotations.Nullable;

/**
 * Costume patterns found in the world (Andy's Powerborne picks): a few cuts, masks and capes in the designer start locked
 * ({@link CostumeSets.Piece#pattern}), and each is unlocked, for good and for that player alone, by using a Costume Pattern
 * that names it ({@link Costume#LEARNED} is what a player knows; right-click the pattern and it is learned and used up).
 * Every piece that was in the designer before stays open to everyone.
 * <p>
 * Where patterns turn up: a boss's fighters (each who did a tenth of the harm, {@link #bossReward}, called by
 * {@code BossShare}), buried treasure and the bastion's treasure chests (loot injection below), the wandering trader (data:
 * {@code villager_trade/costume/wandering_trader}) and costumed gifted folk, who drop the pattern of a patterned piece they
 * wore when a player kills them ({@link #giftedDrop}; Powers keeps the costume ({@code GiftedCostume#DESIGN}), so Jaymo's
 * Kingdoms needs no change).
 */
public final class CostumePatterns {
	/** On a pattern: the piece it unlocks, as {@link CostumeSets.Piece#key} ({@code mask:domino}). */
	public static DataComponentType<String> PIECE;
	public static Item PATTERN;

	/** The share of a boss's harm (0-1) a fighter needs to be given a pattern, and the chance they are. */
	static final float BOSS_SHARE = 0.1F, BOSS_CHANCE = 0.5F;
	/** The chance a buried treasure chest, and a bastion's treasure chest, holds one. */
	static final float BURIED_CHANCE = 0.3F, BASTION_CHANCE = 0.4F;
	/** The chance a costumed gifted villager killed by a player drops the pattern of a patterned piece they wore. */
	static final float GIFTED_DROP_CHANCE = 0.7F;

	private CostumePatterns() {
	}

	static void init() {
		PIECE = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosPowers.id("costume_pattern_piece"),
			DataComponentType.<String>builder().persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8).build());
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id("costume_pattern"));
		PATTERN = Registry.register(BuiltInRegistries.ITEM, key, new PatternItem(new Item.Properties().setId(key).stacksTo(16).rarity(Rarity.UNCOMMON)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> {
			for (CostumeSets.Piece piece : all()) {
				output.accept(stack(piece));
			}
		});
		LootTableEvents.MODIFY.register((table, builder, source, registries) -> {
			float chance = table.equals(BuiltInLootTables.BURIED_TREASURE) ? BURIED_CHANCE
				: table.equals(BuiltInLootTables.BASTION_TREASURE) ? BASTION_CHANCE : 0.0F;
			if (chance > 0.0F && source.isBuiltin()) {
				LootPool.Builder pool = LootPool.lootPool().when(LootItemRandomChanceCondition.randomChance(chance));
				for (CostumeSets.Piece piece : all()) {
					pool.add(LootItem.lootTableItem(PATTERN).apply(SetComponentsFunction.setComponent(PIECE, piece.key())));
				}
				builder.withPool(pool);
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof PathfinderMob mob && source.getEntity() instanceof ServerPlayer && mob.level() instanceof ServerLevel level) {
				giftedDrop(level, mob);
			}
		});
	}

	/** Every pattern piece in the designer. */
	public static List<CostumeSets.Piece> all() {
		List<CostumeSets.Piece> out = new ArrayList<>();
		pieces(CostumeDesign.Cut.values(), out::add);
		pieces(CostumeDesign.Mask.values(), out::add);
		pieces(CostumeDesign.Cape.values(), out::add);
		return out;
	}

	private static void pieces(CostumeSets.Piece[] values, Consumer<CostumeSets.Piece> into) {
		for (CostumeSets.Piece piece : values) {
			if (piece.pattern()) {
				into.accept(piece);
			}
		}
	}

	/** The piece a pattern key names, or null. */
	public static CostumeSets.@Nullable Piece find(String key) {
		for (CostumeSets.Piece piece : all()) {
			if (piece.key().equals(key)) {
				return piece;
			}
		}
		return null;
	}

	/** A pattern for {@code piece}. */
	public static ItemStack stack(CostumeSets.Piece piece) {
		ItemStack stack = new ItemStack(PATTERN);
		stack.set(PIECE, piece.key());
		return stack;
	}

	/** The piece's name, as the designer shows it. */
	public static Component name(CostumeSets.Piece piece) {
		return Component.translatable("costume.jaymospowers." + piece.kind() + "." + piece.getSerializedName());
	}

	/** What {@code player} has learned (either side: it is synced to its owner). */
	private static List<String> learned(Player player) {
		return player.getAttachedOrElse(Costume.LEARNED, List.of());
	}

	/** Whether {@code player} may use {@code piece} as far as patterns go: not a pattern piece, learned, or in creative. Either side. */
	public static boolean known(Player player, CostumeSets.Piece piece) {
		return !piece.pattern() || player.isCreative() || learned(player).contains(piece.key());
	}

	/** Teaches {@code player} {@code piece} (server). False if they already knew it. */
	public static boolean learn(ServerPlayer player, CostumeSets.Piece piece) {
		List<String> now = new ArrayList<>(learned(player));
		if (now.contains(piece.key())) {
			return false;
		}
		now.add(piece.key());
		player.setAttached(Costume.LEARNED, List.copyOf(now));
		return true;
	}

	/** A pattern for a piece {@code player} hasn't learned, picked at random, or empty if they have learned them all. */
	private static ItemStack unlearned(ServerPlayer player, RandomSource random) {
		List<CostumeSets.Piece> left = new ArrayList<>();
		for (CostumeSets.Piece piece : all()) {
			if (!learned(player).contains(piece.key())) {
				left.add(piece);
			}
		}
		return left.isEmpty() ? ItemStack.EMPTY : stack(left.get(random.nextInt(left.size())));
	}

	/** A boss fell and {@code player} did {@code share} (0-1) of the harm to it: maybe a pattern for them (called by BossShare). */
	public static void bossReward(ServerPlayer player, float share) {
		if (share < BOSS_SHARE || player.getRandom().nextFloat() >= BOSS_CHANCE) {
			return;
		}
		ItemStack pattern = unlearned(player, player.getRandom());
		if (!pattern.isEmpty()) {
			player.getInventory().placeItemBackInInventory(pattern, Prediction.SERVER_ONLY);
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.costume.pattern_found"));
		}
	}

	/** A costumed gifted villager a player killed: they may drop the pattern of a patterned piece of the costume they wore. */
	private static void giftedDrop(ServerLevel level, PathfinderMob mob) {
		CostumeDesign design = mob.getAttached(GiftedCostume.DESIGN);
		if (design == null || mob.getRandom().nextFloat() >= GIFTED_DROP_CHANCE) {
			return;
		}
		List<CostumeSets.Piece> worn = new ArrayList<>();
		for (CostumeSets.Piece piece : new CostumeSets.Piece[] {design.cut(), design.mask(), design.cape()}) {
			if (piece.pattern()) {
				worn.add(piece);
			}
		}
		if (!worn.isEmpty()) {
			mob.spawnAtLocation(level, stack(worn.get(mob.getRandom().nextInt(worn.size()))));
		}
	}

	/** A Costume Pattern: use it to learn the piece it names. */
	static final class PatternItem extends Item {
		PatternItem(Item.Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			ItemStack stack = player.getItemInHand(hand);
			String key = stack.get(PIECE);
			CostumeSets.Piece piece = key == null ? null : find(key);
			if (piece == null) {
				return InteractionResult.PASS;
			}
			if (player instanceof ServerPlayer server) {
				if (!learn(server, piece)) {
					server.sendOverlayMessage(Component.translatable("message.jaymospowers.costume.pattern_known", name(piece)));
					return InteractionResult.FAIL;
				}
				stack.shrink(1);
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.UI_LOOM_SELECT_PATTERN, SoundSource.PLAYERS, 1.0F, 1.0F);
				server.sendOverlayMessage(Component.translatable("message.jaymospowers.costume.pattern_learned", name(piece)));
			}
			return InteractionResult.SUCCESS;
		}

		@Override
		@SuppressWarnings("deprecation")
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
			String key = stack.get(PIECE);
			CostumeSets.Piece piece = key == null ? null : find(key);
			if (piece != null) {
				builder.accept(Component.translatable("tooltip.jaymospowers.costume_pattern." + piece.kind(), name(piece)).withStyle(ChatFormatting.GRAY));
				builder.accept(Component.translatable("tooltip.jaymospowers.costume_pattern.use").withStyle(ChatFormatting.DARK_GRAY));
			}
		}
	}
}
