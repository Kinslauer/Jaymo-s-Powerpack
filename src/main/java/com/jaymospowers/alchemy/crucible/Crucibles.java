package com.jaymospowers.alchemy.crucible;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.alchemy.Alchemy;
import com.jaymospowers.alchemy.Essence;
import com.jaymospowers.alchemy.EssenceList;
import com.jaymospowers.alchemy.Phials;
import com.jaymospowers.alchemy.Research;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import org.jspecify.annotations.Nullable;

/**
 * The crucible and what grows around it: the pot itself ({@link CrucibleBlock}), the alembic that stands on it, essence
 * jars ({@link JarBlock}), and the things a catalyst makes ({@link Recipe}, data in
 * {@code data/<ns>/alchemy/crucible/*.json}). The pot dissolves what is thrown in into essence; a catalyst thrown in
 * while the pot holds the right essences, by someone who has the research, turns into something new.
 */
public final class Crucibles {
	/** Most essence (all kinds together) a crucible holds; what goes over boils away as flux. */
	public static final int CAPACITY = 500;
	/** The share of a dissolved thing's essence that is lost as flux in its chunk. */
	public static final double LOSS = 0.1;
	/** Ticks of heat (water over a fire) before the pot is hot and bubbling (5 s). */
	public static final int HEAT_TICKS = 100;
	/** Ticks between dissolving one thrown item and the next. */
	public static final int DISSOLVE_TICKS = 8;
	/** Ticks between the alembic moving one essence on into a jar. */
	public static final int DISTIL_TICKS = 40;
	/** Most one jar holds, of a single essence. */
	public static final int JAR_SIZE = 250;
	/** What the pot or a jar gives an empty phial, at most (a phial's size). */
	public static final int DRAW = Phials.SIZE;
	/** Colour of the plain water in a pot with no essence yet. */
	public static final int WATER_COLOUR = 0x3F76E4;
	/** Tag put on an item the pot has turned away or made, so it isn't dissolved again. */
	public static final String SPENT_TAG = "jaymospowers_alchemy_spent";

	public static Block CRUCIBLE, ALEMBIC, ESSENCE_JAR, ORICHALC_BLOCK, EVERFLAME;
	public static Item ORICHALC_INGOT, ORICHALC_NUGGET, QUICKSILVER, BRIMSTONE, ALCHEMICAL_SALT;
	public static BlockEntityType<CrucibleEntity> CRUCIBLE_ENTITY;
	public static BlockEntityType<JarEntity> JAR_ENTITY;

	/**
	 * One catalyst recipe.
	 *
	 * @param catalyst an item id, or {@code #tag}
	 * @param essences what the pot must hold; all of it is used up
	 * @param result   what the catalyst turns into
	 * @param research the research the thrower must know ({@link com.jaymospowers.alchemy.Knowledge#knows})
	 */
	public record Recipe(String catalyst, EssenceList essences, Result result, String research) {
		public record Result(Identifier item, int count) {
			public static final Codec<Result> CODEC = RecordCodecBuilder.create(i -> i.group(
				Identifier.CODEC.fieldOf("item").forGetter(Result::item),
				Codec.intRange(1, 64).optionalFieldOf("count", 1).forGetter(Result::count)).apply(i, Result::new));
		}

		public static final Codec<Recipe> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("catalyst").forGetter(Recipe::catalyst),
			EssenceList.CODEC.fieldOf("essences").forGetter(Recipe::essences),
			Result.CODEC.fieldOf("result").forGetter(Recipe::result),
			Codec.STRING.fieldOf("research").forGetter(Recipe::research)).apply(i, Recipe::new));

		public boolean matches(ItemStack stack) {
			if (catalyst.startsWith("#")) {
				Identifier tag = Identifier.tryParse(catalyst.substring(1));
				return tag != null && stack.is(TagKey.create(Registries.ITEM, tag));
			}
			Identifier id = Identifier.tryParse(catalyst);
			return id != null && BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(id);
		}

		/** What it makes (null if the result's item doesn't exist: a recipe for a mod that isn't loaded). */
		public @Nullable ItemStack make() {
			return BuiltInRegistries.ITEM.getOptional(result.item()).map(item -> new ItemStack(item, result.count())).orElse(null);
		}
	}

	private static List<Recipe> recipes = List.of();

	private Crucibles() {
	}

	/** Every catalyst recipe loaded, in id order. */
	public static List<Recipe> recipes() {
		return recipes;
	}

	/** The colour of a pot's liquid: the essences' colours mixed by how much of each there is (water-blue when none). */
	public static int mix(EssenceList essences) {
		int total = essences.total();
		if (total <= 0) {
			return WATER_COLOUR;
		}
		double r = 0, g = 0, b = 0;
		for (Map.Entry<Essence, Integer> en : essences.amounts().entrySet()) {
			double share = en.getValue() / (double) total;
			int c = en.getKey().colour;
			r += (c >> 16 & 0xFF) * share;
			g += (c >> 8 & 0xFF) * share;
			b += (c & 0xFF) * share;
		}
		return (int) Math.round(r) << 16 | (int) Math.round(g) << 8 | (int) Math.round(b);
	}

	public static void init() {
		CRUCIBLE = block("crucible", CrucibleBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(2.5F)
			.sound(SoundType.COPPER).noOcclusion(), false);
		ALEMBIC = block("alembic", AlembicBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(2.0F)
			.sound(SoundType.COPPER).noOcclusion(), false);
		ESSENCE_JAR = block("essence_jar", JarBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(0.6F)
			.sound(SoundType.GLASS).noOcclusion(), true);
		ORICHALC_BLOCK = block("orichalc_block", Block::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PINK).strength(5.0F, 6.0F)
			.sound(SoundType.METAL), false);
		EVERFLAME = block("everflame", EverflameBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.GOLD).instabreak().noCollision()
			.noOcclusion().lightLevel(state -> EverflameBlock.LIGHT).sound(SoundType.WOOL).pushReaction(PushReaction.POPPED), false);
		ORICHALC_INGOT = Alchemy.item("orichalc_ingot", Item::new);
		ORICHALC_NUGGET = Alchemy.item("orichalc_nugget", Item::new);
		QUICKSILVER = Alchemy.item("quicksilver", Item::new);
		BRIMSTONE = Alchemy.item("brimstone", Item::new);
		ALCHEMICAL_SALT = Alchemy.item("alchemical_salt", Item::new);
		CRUCIBLE_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, JaymosPowers.id("crucible"),
			new BlockEntityType<>(CrucibleEntity::new, Set.of(CRUCIBLE)));
		JAR_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, JaymosPowers.id("essence_jar"),
			new BlockEntityType<>(JarEntity::new, Set.of(ESSENCE_JAR)));

		ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(JaymosPowers.id("alchemy_crucible"),
			new SimpleJsonResourceReloadListener<>(Recipe.CODEC, FileToIdConverter.json("alchemy/crucible")) {
				@Override
				protected void apply(Map<Identifier, Recipe> loaded, ResourceManager manager, ProfilerFiller profiler) {
					List<Identifier> ids = new ArrayList<>(loaded.keySet());
					ids.sort(null);
					List<Recipe> sorted = new ArrayList<>();
					for (Identifier id : ids) {
						sorted.add(loaded.get(id));
					}
					recipes = List.copyOf(sorted);
				}
			});
		// (0.18.50) the recipe list goes to every client, so JEI's Crucible page shows what this server's pot makes
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

	/** (0.18.50) Server to client: every crucible recipe, for JEI. */
	public record Sync(List<Recipe> recipes) implements CustomPacketPayload {
		public static final Type<Sync> TYPE = new Type<>(JaymosPowers.id("crucible_recipes"));
		private static final StreamCodec<RegistryFriendlyByteBuf, Recipe> RECIPE = ByteBufCodecs.fromCodecWithRegistries(Recipe.CODEC);
		public static final StreamCodec<RegistryFriendlyByteBuf, Sync> CODEC = CustomPacketPayload.codec(Sync::write, Sync::read);

		private void write(RegistryFriendlyByteBuf buf) {
			buf.writeVarInt(recipes.size());
			for (Recipe recipe : recipes) {
				RECIPE.encode(buf, recipe);
			}
		}

		private static Sync read(RegistryFriendlyByteBuf buf) {
			List<Recipe> list = new ArrayList<>();
			for (int i = buf.readVarInt(); i > 0; i--) {
				list.add(RECIPE.decode(buf));
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
		Research.Category tab = Research.Category.CRUCIBLE;
		Research.register(new Research.Entry("crucible", tab, () -> CRUCIBLE.asItem(),
			EssenceList.of(Essence.FIRE, 5, Essence.WATER, 5), List.of("alchemy_basics"), 0, 0, 1));
		Research.register(new Research.Entry("orichalc", tab, () -> ORICHALC_INGOT,
			EssenceList.of(Essence.METAL, 15, Essence.SALT, 10), List.of("crucible"), 0, 1, 0));
		Research.register(new Research.Entry("principles", tab, () -> QUICKSILVER,
			EssenceList.of(Essence.SALT, 10, Essence.SULPHUR, 10).plus(Essence.MERCURY, 10), List.of("crucible"), 0, 1, 1));
		Research.register(new Research.Entry("alembic", tab, () -> ALEMBIC.asItem(),
			EssenceList.of(Essence.WATER, 8, Essence.FIRE, 8).plus(Essence.CRYSTAL, 5), List.of("crucible"), 0, 1, 2));
		Research.register(new Research.Entry("transmutation", tab, () -> Items.GOLD_NUGGET,
			EssenceList.of(Essence.METAL, 15, Essence.MERCURY, 15).plus(Essence.WEALTH, 5), List.of("orichalc"), 0, 2, 0));
		Research.register(new Research.Entry("everflame", tab, () -> EVERFLAME.asItem(),
			EssenceList.of(Essence.LIGHT, 12, Essence.FIRE, 8), List.of("principles"), 0, 2, 1));
	}

	/** Registers a block and its item (named with the block prefix, in the Alchemy tab). */
	private static Block block(String id, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties,
		boolean jarItem) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosPowers.id(id));
		Block block = Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
		Alchemy.item(id, p -> jarItem ? new JarBlock.JarItem(block, p.useBlockDescriptionPrefix()) : new BlockItem(block, p.useBlockDescriptionPrefix()));
		return block;
	}
}
