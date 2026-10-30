package com.jaymospowers.alchemy.infusion;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.alchemy.Alchemy;
import com.jaymospowers.alchemy.Essence;
import com.jaymospowers.alchemy.EssenceList;
import com.jaymospowers.alchemy.Knowledge;
import com.jaymospowers.alchemy.Research;
import com.jaymospowers.alchemy.flux.FluxEffects;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.AbstractSkullBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import org.jspecify.annotations.Nullable;

/**
 * The infusion altar. A {@link PedestalBlock} holds one item; a runed stone, the {@link MatrixBlock}, floats two blocks
 * above a central pedestal, and up to {@link #MAX_PEDESTALS} more pedestals stand around that one (within
 * {@link #REACH} blocks, on its level or one above or below) with the components. Right-click the stone with an essence
 * phial (or call {@link #start}) and the stone pays the recipe's essences out of the jars around it, takes the components
 * one by one and turns the centre item into the result ({@link MatrixEntity} runs it).
 * <p>
 * Recipes are data ({@code data/<ns>/alchemy/infusion/*.json}, {@link Recipe}). Every infusion carries an
 * <i>instability</i>: the recipe's own, plus a share for each component pedestal whose mirror across the centre holds
 * nothing, less what the {@link SaltCandleBlock salt candles}, skulls and ward stones near the stone steady. The higher it
 * is, the more often the altar throws something off ({@link MatrixEntity}).
 */
public final class Infusion {
	/** Component pedestals stand within this many blocks of the centre one, horizontally (a square around it). */
	public static final int REACH = 5;
	/** At most this many component pedestals count (the nearest). */
	public static final int MAX_PEDESTALS = 12;
	/** The stone and the centre pedestal: how far apart (blocks). */
	public static final int MATRIX_HEIGHT = 2;
	/** Jars this close to the stone (blocks) feed it. */
	public static final int JAR_RANGE = 8;
	/** Instability added for each component pedestal with no mirrored partner. */
	public static final int ASYMMETRY = 2;
	/** Stabilisers count within this many blocks of the stone (a cube). */
	public static final int STABILISER_RANGE = 6;
	/** What each steadying thing near the stone takes off the instability. */
	public static final int CANDLE_STEADY = 2, SKULL_STEADY = 3, WARD_STONE_STEADY = 5;
	/** The most the stabilisers together take off. */
	public static final int STEADY_CAP = 20;
	/** The research that opens the whole altar. */
	public static final String RESEARCH = "infusion";
	/** Special results a recipe can name instead of an item. */
	public static final String SPECIAL_ENCHANT = "infuse_enchantment";
	/** The centre of a recipe that takes any enchanted item. */
	public static final String ANY_ENCHANTED = "*enchanted";

	public static Block PEDESTAL, MATRIX, SALT_CANDLE;
	public static Item ICHOR, RUNIC_AMULET;
	public static BlockEntityType<PedestalEntity> PEDESTAL_ENTITY;
	public static BlockEntityType<MatrixEntity> MATRIX_ENTITY;

	/**
	 * What an infusion makes: an item, or a named special effect on the centre item.
	 *
	 * @param item    the item the centre becomes (its enchantments and damage carry over where sensible)
	 * @param special {@link #SPECIAL_ENCHANT}: the centre stays and one of its enchantments goes a level past the cap
	 */
	public record Result(Optional<Identifier> item, int count, Optional<String> special) {
		public static final Codec<Result> CODEC = RecordCodecBuilder.create(i -> i.group(
			Identifier.CODEC.optionalFieldOf("item").forGetter(Result::item),
			Codec.intRange(1, 64).optionalFieldOf("count", 1).forGetter(Result::count),
			Codec.STRING.optionalFieldOf("special").forGetter(Result::special)).apply(i, Result::new));
	}

	/**
	 * One infusion recipe.
	 *
	 * @param centre      an item id, {@code #tag}, or {@link #ANY_ENCHANTED}: what lies on the centre pedestal
	 * @param components  item ids or {@code #tags}, one per component pedestal that holds something (order doesn't matter)
	 * @param essences    paid out of the jars, one point at a time
	 * @param instability the recipe's own share of the altar's instability
	 * @param result      what comes of it
	 * @param research    the research the starter must know
	 * @param flux        flux left in the chunk when it succeeds
	 */
	public record Recipe(String centre, List<String> components, EssenceList essences, int instability, Result result, String research,
		int flux) {
		public static final Codec<Recipe> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("centre").forGetter(Recipe::centre),
			Codec.STRING.listOf().fieldOf("components").forGetter(Recipe::components),
			EssenceList.CODEC.fieldOf("essences").forGetter(Recipe::essences),
			Codec.INT.fieldOf("instability").forGetter(Recipe::instability),
			Result.CODEC.fieldOf("result").forGetter(Recipe::result),
			Codec.STRING.fieldOf("research").forGetter(Recipe::research),
			Codec.INT.optionalFieldOf("flux", 0).forGetter(Recipe::flux)).apply(i, Recipe::new));

		public boolean matchesCentre(ItemStack stack) {
			return ANY_ENCHANTED.equals(centre) ? stack.isEnchanted() : itemMatches(centre, stack);
		}
	}

	public record Loaded(Identifier id, Recipe recipe) {
	}

	private static List<Loaded> recipes = List.of();

	private Infusion() {
	}

	/** Whether {@code spec} (an item id or {@code #tag}) names this stack's item. */
	public static boolean itemMatches(String spec, ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		if (spec.startsWith("#")) {
			Identifier tag = Identifier.tryParse(spec.substring(1));
			return tag != null && stack.is(TagKey.create(Registries.ITEM, tag));
		}
		Identifier id = Identifier.tryParse(spec);
		return id != null && BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(id);
	}

	/**
	 * Which of {@code specs} each item is: {@code result[i]} is the index in {@code specs} item {@code i} fills, every spec
	 * used exactly once, or null when the two don't fit together (a different count, or an item no spec takes).
	 */
	public static int @Nullable [] assign(List<String> specs, List<ItemStack> items) {
		if (specs.size() != items.size()) {
			return null;
		}
		int[] out = new int[items.size()];
		return assign(specs, items, 0, out, new boolean[specs.size()]) ? out : null;
	}

	private static boolean assign(List<String> specs, List<ItemStack> items, int at, int[] out, boolean[] used) {
		if (at == items.size()) {
			return true;
		}
		for (int s = 0; s < specs.size(); s++) {
			if (!used[s] && itemMatches(specs.get(s), items.get(at))) {
				used[s] = true;
				out[at] = s;
				if (assign(specs, items, at + 1, out, used)) {
					return true;
				}
				used[s] = false;
			}
		}
		return false;
	}

	public static List<Recipe> recipes() {
		return recipes.stream().map(Loaded::recipe).toList();
	}

	/** Every recipe with its id, in id order. */
	public static List<Loaded> entries() {
		return recipes;
	}

	static @Nullable Recipe recipe(String id) {
		Identifier key = Identifier.tryParse(id);
		for (Loaded l : recipes) {
			if (l.id().equals(key)) {
				return l.recipe();
			}
		}
		return null;
	}

	/** The pedestal under the stone at {@code matrix}. */
	public static BlockPos centreOf(BlockPos matrix) {
		return matrix.below(MATRIX_HEIGHT);
	}

	/**
	 * The component pedestals around the centre one at {@code centre}, nearest first, at most {@link #MAX_PEDESTALS}
	 * (empty ones too: an empty pedestal is the mirror of nothing).
	 */
	public static List<PedestalEntity> pedestals(ServerLevel level, BlockPos centre) {
		List<PedestalEntity> found = new ArrayList<>();
		for (int dy = -1; dy <= 1; dy++) {
			for (int dx = -REACH; dx <= REACH; dx++) {
				for (int dz = -REACH; dz <= REACH; dz++) {
					BlockPos pos = centre.offset(dx, dy, dz);
					if ((dx != 0 || dz != 0 || dy != 0) && level.hasChunkAt(pos) && level.getBlockEntity(pos) instanceof PedestalEntity p) {
						found.add(p);
					}
				}
			}
		}
		found.sort(Comparator.comparingDouble((PedestalEntity p) -> p.getBlockPos().distSqr(centre)).thenComparingLong(p -> p.getBlockPos().asLong()));
		return found.size() > MAX_PEDESTALS ? new ArrayList<>(found.subList(0, MAX_PEDESTALS)) : found;
	}

	/** Pedestals among {@code all} that hold an item, in the same order. */
	public static List<PedestalEntity> loaded(List<PedestalEntity> all) {
		return all.stream().filter(p -> !p.stack().isEmpty()).toList();
	}

	/** How many loaded pedestals have no loaded mirror across {@code centre} (on the same level). */
	public static int asymmetry(ServerLevel level, BlockPos centre, List<PedestalEntity> all) {
		int lone = 0;
		for (PedestalEntity p : loaded(all)) {
			BlockPos at = p.getBlockPos();
			BlockPos mirror = new BlockPos(2 * centre.getX() - at.getX(), at.getY(), 2 * centre.getZ() - at.getZ());
			if (!level.hasChunkAt(mirror) || !(level.getBlockEntity(mirror) instanceof PedestalEntity m) || m.stack().isEmpty() || all.stream().noneMatch(o -> o == m)) {
				lone++;
			}
		}
		return lone;
	}

	/** What the nearby candles, skulls and ward stones take off the instability (at most {@link #STEADY_CAP}). */
	public static int steadiness(ServerLevel level, BlockPos matrix) {
		int steady = 0;
		int r = STABILISER_RANGE;
		for (BlockPos pos : BlockPos.betweenClosed(matrix.offset(-r, -r, -r), matrix.offset(r, r, r))) {
			if (!level.hasChunkAt(pos)) {
				continue;
			}
			BlockState state = level.getBlockState(pos);
			if (state.isAir()) {
				continue;
			}
			if (state.is(SALT_CANDLE)) {
				steady += CANDLE_STEADY;
			} else if (state.getBlock() instanceof AbstractSkullBlock) {
				steady += SKULL_STEADY;
			} else if (FluxEffects.WARD_STONE != null && state.is(FluxEffects.WARD_STONE)) {
				steady += WARD_STONE_STEADY;
			}
		}
		return Math.min(STEADY_CAP, steady);
	}

	/**
	 * The enchantment an enchant infusion would raise: one the book holds that the item already has at the table's cap.
	 * Null when there isn't one.
	 */
	static @Nullable Holder<Enchantment> chooseEnchantment(ItemStack item, List<ItemStack> components) {
		ItemEnchantments has = item.getEnchantments();
		for (ItemStack c : components) {
			ItemEnchantments stored = c.get(DataComponents.STORED_ENCHANTMENTS);
			if (stored == null) {
				continue;
			}
			for (Holder<Enchantment> e : stored.keySet()) {
				if (e.value().getMaxLevel() > 1 && has.getLevel(e) == e.value().getMaxLevel()) {
					return e;
				}
			}
		}
		return null;
	}

	/** The id an enchantment is saved under (for a run that outlasts a restart). */
	static @Nullable String enchantmentId(@Nullable Holder<Enchantment> e) {
		return e == null ? null : e.unwrapKey().map(k -> k.identifier().toString()).orElse(null);
	}

	static @Nullable Holder<Enchantment> enchantment(ServerLevel level, String id) {
		Identifier key = Identifier.tryParse(id);
		if (key == null) {
			return null;
		}
		return level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(ResourceKey.create(Registries.ENCHANTMENT, key)).orElse(null);
	}

	/**
	 * What the centre item becomes. An item result keeps the centre's enchantments, and its damage when both are
	 * damageable; the enchant infusion lifts {@code chosen} one past the cap.
	 */
	static ItemStack make(ServerLevel level, Recipe recipe, ItemStack centre, @Nullable String chosen) {
		if (recipe.result().special().isPresent()) {
			ItemStack out = centre.copy();
			Holder<Enchantment> e = chosen == null ? null : enchantment(level, chosen);
			if (e != null && SPECIAL_ENCHANT.equals(recipe.result().special().get()) && out.getEnchantments().getLevel(e) == e.value().getMaxLevel()) {
				EnchantmentHelper.updateEnchantments(out, m -> m.set(e, e.value().getMaxLevel() + 1));
			}
			return out;
		}
		Item item = recipe.result().item().flatMap(BuiltInRegistries.ITEM::getOptional).orElse(null);
		if (item == null) {
			return centre.copy();
		}
		ItemStack out = new ItemStack(item, recipe.result().count());
		if (centre.isEnchanted() && !out.isEmpty()) {
			out.set(DataComponents.ENCHANTMENTS, centre.getEnchantments());
		}
		if (centre.isDamageableItem() && out.isDamageableItem()) {
			out.setDamageValue(centre.getDamageValue());
		}
		return out;
	}

	/**
	 * Starts the infusion whose stone is at {@code at} (a pedestal with the stone two blocks above it also works): the
	 * centre pedestal's item and the components around it must match a recipe the player has the research for.
	 * Tells the player, in few words, why not. Returns whether it began.
	 */
	public static boolean start(ServerLevel level, BlockPos at, ServerPlayer player) {
		BlockPos matrix = at;
		if (level.getBlockState(at).is(PEDESTAL) && level.getBlockState(at.above(MATRIX_HEIGHT)).is(MATRIX)) {
			matrix = at.above(MATRIX_HEIGHT);
		}
		if (!(level.getBlockEntity(matrix) instanceof MatrixEntity stone)) {
			return false;
		}
		return stone.begin(level, player);
	}

	public static void init() {
		PEDESTAL = block("pedestal", PedestalBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(2.0F, 6.0F)
			.sound(SoundType.STONE).noOcclusion());
		MATRIX = block("infusion_matrix", MatrixBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(3.0F, 8.0F)
			.sound(SoundType.AMETHYST).noOcclusion().lightLevel(s -> MatrixBlock.LIGHT));
		SALT_CANDLE = block("salt_candle", SaltCandleBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).strength(0.1F)
			.sound(SoundType.CANDLE).noOcclusion().lightLevel(s -> SaltCandleBlock.LIGHT));
		ICHOR = Alchemy.item("ichor", Item::new);
		RUNIC_AMULET = Alchemy.item("runic_amulet", p -> new RunicAmuletItem(p.stacksTo(1)));
		PEDESTAL_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, JaymosPowers.id("pedestal"),
			new BlockEntityType<>(PedestalEntity::new, Set.of(PEDESTAL)));
		MATRIX_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, JaymosPowers.id("infusion_matrix"),
			new BlockEntityType<>(MatrixEntity::new, Set.of(MATRIX)));

		ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(JaymosPowers.id("alchemy_infusion"),
			new SimpleJsonResourceReloadListener<>(Recipe.CODEC, FileToIdConverter.json("alchemy/infusion")) {
				@Override
				protected void apply(Map<Identifier, Recipe> loaded, ResourceManager manager, ProfilerFiller profiler) {
					List<Identifier> ids = new ArrayList<>(loaded.keySet());
					ids.sort(null);
					List<Loaded> sorted = new ArrayList<>();
					for (Identifier id : ids) {
						sorted.add(new Loaded(id, loaded.get(id)));
					}
					recipes = List.copyOf(sorted);
				}
			});
		// (0.18.19) the recipe list goes to every client, so JEI's Infusion page shows what this server's altar makes
		PayloadTypeRegistry.clientboundPlay().register(Sync.TYPE, Sync.CODEC);
		ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS.register((player, joined) -> {
			if (joined) {
				send(player);
			}
		});
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> {
			if (success) {
				for (ServerPlayer player : PlayerLookup.all(server)) {
					send(player);
				}
			}
		});
		registerResearch();
	}

	private static void send(ServerPlayer player) {
		if (ServerPlayNetworking.canSend(player, Sync.TYPE)) {
			ServerPlayNetworking.send(player, new Sync(recipes));
		}
	}

	/** (0.18.19) Server to client: every infusion recipe with its id, for JEI. */
	public record Sync(List<Loaded> recipes) implements CustomPacketPayload {
		public static final Type<Sync> TYPE = new Type<>(JaymosPowers.id("infusions"));
		private static final StreamCodec<RegistryFriendlyByteBuf, Recipe> RECIPE = ByteBufCodecs.fromCodecWithRegistries(Recipe.CODEC);
		public static final StreamCodec<RegistryFriendlyByteBuf, Sync> CODEC = CustomPacketPayload.codec(Sync::write, Sync::read);

		private void write(RegistryFriendlyByteBuf buf) {
			buf.writeVarInt(recipes.size());
			for (Loaded loaded : recipes) {
				buf.writeIdentifier(loaded.id());
				RECIPE.encode(buf, loaded.recipe());
			}
		}

		private static Sync read(RegistryFriendlyByteBuf buf) {
			List<Loaded> list = new ArrayList<>();
			for (int i = buf.readVarInt(); i > 0; i--) {
				Identifier id = buf.readIdentifier();
				list.add(new Loaded(id, RECIPE.decode(buf)));
			}
			return new Sync(List.copyOf(list));
		}

		@Override
		public Type<Sync> type() {
			return TYPE;
		}
	}

	/** Client side: takes the server's recipe list. */
	public static void accept(Sync sync) {
		recipes = sync.recipes();
	}

	private static void registerResearch() {
		Research.Category tab = Research.Category.INFUSION;
		Research.register(new Research.Entry(RESEARCH, tab, () -> PEDESTAL.asItem(),
			EssenceList.of(Essence.SPIRIT, 10, Essence.ARCANA, 8).plus(Essence.METAL, 6), List.of("orichalc"), 0, 0, 1));
		Research.register(new Research.Entry("stability", tab, () -> SALT_CANDLE.asItem(),
			EssenceList.of(Essence.SALT, 12, Essence.LIGHT, 6).plus(Essence.SPIRIT, 5), List.of(RESEARCH), 0, 1, 0));
		Research.register(new Research.Entry("ichor", tab, () -> ICHOR,
			EssenceList.of(Essence.SPIRIT, 14, Essence.ARCANA, 10).plus(Essence.LIGHT, 10), List.of(RESEARCH), 0, 1, 1));
		Research.register(new Research.Entry("runic_amulet", tab, () -> RUNIC_AMULET,
			EssenceList.of(Essence.SPIRIT, 8, Essence.SALT, 10).plus(Essence.WEALTH, 5), List.of(RESEARCH), 0, 1, 2));
		Research.register(new Research.Entry("infused_enchanting", tab, () -> Items.ENCHANTED_BOOK,
			EssenceList.of(Essence.ARCANA, 20, Essence.MIND, 10).plus(Essence.CRYSTAL, 10), List.of("stability"), 0, 2, 0));
	}

	/** Whether {@code player} may use {@code recipe}. */
	static boolean allowed(ServerPlayer player, Recipe recipe) {
		return Knowledge.knows(player, RESEARCH) && Knowledge.knows(player, recipe.research());
	}

	/** Registers a block and its item (named with the block prefix, in the Alchemy tab). */
	private static Block block(String id, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosPowers.id(id));
		Block block = Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
		Alchemy.item(id, p -> new BlockItem(block, p.useBlockDescriptionPrefix()));
		return block;
	}

	/** A line of text for a message. */
	static Component text(String key, Object... args) {
		return Component.translatable("message.jaymospowers.infusion." + key, args);
	}
}
