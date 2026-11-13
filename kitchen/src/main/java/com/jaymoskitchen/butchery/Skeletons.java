package com.jaymoskitchen.butchery;

import com.jaymoskitchen.JaymosKitchen;
import com.jaymoskitchen.corpse.Hanging;
import com.jaymoskitchen.delight.CookwareBlock;
import com.jaymoskitchen.delight.Delight;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import org.jspecify.annotations.Nullable;

/**
 * Skeleton displays (the plan PR #864 left out; Andy, 2026-11-13: "start with the six, include deer, and if we need more
 * we can make them as required", and on cleaning "add both").
 *
 * <ol>
 * <li><b>Frame bones.</b> A carcass butchered all the way on the meat hook leaves its frame bones, one set per animal,
 * remembering what it was ({@link #BONES_OF}: "Bones of a cow").</li>
 * <li><b>Clean them</b>, two ways. Boiled in a cooking pot over a fire for an in-game hour ({@link #BOIL_TICKS}): quick,
 * but the grease leaves them ivory. Or left to a beetle box's colony for a day ({@link BeetleBoxEntity}): slow, and they
 * come out white, as a museum's are. The colony lives on the scraps; left empty for days it dies off, and rotten flesh
 * starts another.</li>
 * <li><b>Stand them up</b> on a bone stand ({@link BoneStandBlock}): the whole skeleton, facing the way the stand was set.
 * Sneak-use with an empty hand cycles its poses. Uncleaned bones on a stand go green and draw flies.</li>
 * </ol>
 *
 * <p>Only the animals with a skeleton model have frame bones ({@link #ANIMALS}): cow, pig, sheep, horse, wolf, chicken
 * and Naturalist's deer. A mooshroom is a cow under the skin, and a donkey or mule a horse, so they use those models.
 * More are made as they're wanted.
 */
public final class Skeletons {
	/** Boiling clean in a cooking pot over a fire: an in-game hour. */
	public static final int BOIL_TICKS = 1000;

	/** An animal's skeleton: its model (geckolib/models/block/skeleton_{@code model}) and its poses (animations, in order). */
	public record Kind(String model, List<String> poses) {
	}

	/** The animals that leave frame bones, by entity type id. */
	public static final Map<Identifier, Kind> ANIMALS = new HashMap<>();

	static {
		Kind cow = new Kind("cow", List.of("standing", "grazing", "lowing"));
		Kind horse = new Kind("horse", List.of("standing", "grazing", "rearing"));
		ANIMALS.put(mc("cow"), cow);
		ANIMALS.put(mc("mooshroom"), cow);
		ANIMALS.put(mc("pig"), new Kind("pig", List.of("standing", "rooting")));
		ANIMALS.put(mc("sheep"), new Kind("sheep", List.of("standing", "grazing")));
		ANIMALS.put(mc("horse"), horse);
		ANIMALS.put(mc("donkey"), horse);
		ANIMALS.put(mc("mule"), horse);
		ANIMALS.put(mc("wolf"), new Kind("wolf", List.of("standing", "howling", "sitting")));
		ANIMALS.put(mc("chicken"), new Kind("chicken", List.of("standing", "pecking", "flapping")));
		ANIMALS.put(Identifier.fromNamespaceAndPath("naturalist", "deer"), new Kind("deer", List.of("standing", "grazing", "alert")));
	}

	/** How the bones were cleaned: not yet, boiled (ivory), or by beetles (white). */
	public enum Clean { DIRTY, BOILED, BEETLES }

	public static DataComponentType<Identifier> BONES_OF;
	public static DataComponentType<Integer> BONES_CLEAN;
	public static Item FRAME_BONES, CLEANED_BONES;
	public static BoneStandBlock BONE_STAND;
	public static BeetleBoxBlock BEETLE_BOX;
	public static BlockEntityType<BoneStandEntity> BONE_STAND_ENTITY;
	public static BlockEntityType<BeetleBoxEntity> BEETLE_BOX_ENTITY;

	/** A set of bones boiling in a cooking pot: whose, and how long it has boiled (ticks). */
	record Boil(String animal, int ticks) {
		static final Codec<Boil> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("animal").forGetter(Boil::animal),
			Codec.INT.fieldOf("ticks").forGetter(Boil::ticks)
		).apply(i, Boil::new));
	}

	/** Every pot with bones in it, in a dimension, by packed block position. Server only. */
	static final AttachmentType<Map<String, Boil>> BOILING = AttachmentRegistry.create(JaymosKitchen.id("boiling_bones"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Boil.CODEC)));

	private Skeletons() {
	}

	private static Identifier mc(String path) {
		return Identifier.withDefaultNamespace(path);
	}

	public static void register() {
		BONES_OF = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosKitchen.id("bones_of"),
			DataComponentType.<Identifier>builder().persistent(Identifier.CODEC).networkSynchronized(Identifier.STREAM_CODEC).build());
		BONES_CLEAN = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosKitchen.id("bones_clean"),
			DataComponentType.<Integer>builder().persistent(Codec.INT).networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.VAR_INT).build());
		FRAME_BONES = item("frame_bones", BonesItem::new, new Item.Properties().stacksTo(1));
		CLEANED_BONES = item("cleaned_bones", BonesItem::new, new Item.Properties().stacksTo(1));

		BONE_STAND = block("bone_stand", p -> new BoneStandBlock(p), BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(1.5F)
			.sound(SoundType.WOOD).noOcclusion());
		BEETLE_BOX = block("beetle_box", p -> new BeetleBoxBlock(p), BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(1.5F)
			.sound(SoundType.WOOD).ignitedByLava());
		BONE_STAND_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, JaymosKitchen.id("bone_stand"),
			new BlockEntityType<>(BoneStandEntity::new, java.util.Set.of(BONE_STAND)));
		BEETLE_BOX_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, JaymosKitchen.id("beetle_box"),
			new BlockEntityType<>(BeetleBoxEntity::new, java.util.Set.of(BEETLE_BOX)));

		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> {
			output.accept(new ItemStack(BONE_STAND));
			output.accept(new ItemStack(BEETLE_BOX));
		});

		// the last cut on the hook leaves the frame
		Hanging.addOnDone(Skeletons::frameLeft);
		// boiling in the cooking pot
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (player.isSpectator() || !(level.getBlockState(hit.getBlockPos()).getBlock() instanceof CookwareBlock)
				|| level.getBlockState(hit.getBlockPos()).getBlock() != Delight.COOKING_POT_BLOCK) {
				return InteractionResult.PASS;
			}
			return pot(level instanceof ServerLevel server ? server : null, hit.getBlockPos(), player, player.getItemInHand(hand));
		});
		ServerTickEvents.END_SERVER_TICK.register(Skeletons::boil);
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, entity) -> {
			if (level instanceof ServerLevel server) {
				spill(server, pos);
			}
		});
	}

	private static Item item(String id, Function<Item.Properties, Item> make, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKitchen.id(id));
		return Registry.register(BuiltInRegistries.ITEM, key, make.apply(properties.setId(key)));
	}

	private static <B extends Block> B block(String id, Function<BlockBehaviour.Properties, B> make, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosKitchen.id(id));
		B block = Registry.register(BuiltInRegistries.BLOCK, key, make.apply(properties.setId(key)));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, JaymosKitchen.id(id));
		Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
		return block;
	}

	// ---- the bones ------------------------------------------------------------------------------------------------------

	/** The animal these bones were, or null. */
	public static @Nullable Identifier of(ItemStack bones) {
		return BONES_OF == null ? null : bones.get(BONES_OF);
	}

	public static @Nullable Kind kind(ItemStack bones) {
		Identifier animal = of(bones);
		return animal == null ? null : ANIMALS.get(animal);
	}

	public static Clean clean(ItemStack bones) {
		if (!bones.is(CLEANED_BONES)) {
			return Clean.DIRTY;
		}
		Integer how = bones.get(BONES_CLEAN);
		return how != null && how == Clean.BEETLES.ordinal() ? Clean.BEETLES : Clean.BOILED;
	}

	public static boolean isBones(ItemStack stack) {
		return (stack.is(FRAME_BONES) || stack.is(CLEANED_BONES)) && kind(stack) != null;
	}

	public static ItemStack frame(Identifier animal) {
		ItemStack bones = new ItemStack(FRAME_BONES);
		bones.set(BONES_OF, animal);
		return bones;
	}

	/** Dirty bones, cleaned this way. */
	public static ItemStack cleaned(ItemStack dirty, Clean how) {
		ItemStack bones = new ItemStack(CLEANED_BONES);
		Identifier animal = of(dirty);
		if (animal != null) {
			bones.set(BONES_OF, animal);
		}
		bones.set(BONES_CLEAN, how.ordinal());
		return bones;
	}

	/** A carcass finished on the hook leaves its frame, if it's an animal with a skeleton. */
	private static void frameLeft(Player player, Entity carcass) {
		if (!(carcass instanceof com.jaymoskitchen.corpse.CorpseEntity corpse) || !(carcass.level() instanceof ServerLevel level)) {
			return;
		}
		net.minecraft.world.entity.EntityType<?> type = corpse.sourceType();
		Identifier animal = type == null ? null : BuiltInRegistries.ENTITY_TYPE.getKey(type);
		if (animal != null && ANIMALS.containsKey(animal)) {
			carcass.spawnAtLocation(level, frame(animal), 0.3F);
		}
	}

	// ---- boiling in the pot ---------------------------------------------------------------------------------------------

	private static String key(BlockPos pos) {
		return Long.toString(pos.asLong());
	}

	/** Bones into a hot pot; with an empty hand, out again once they're clean (or how long is left). */
	private static InteractionResult pot(@Nullable ServerLevel level, BlockPos pos, Player player, ItemStack held) {
		boolean bones = held.is(FRAME_BONES) && kind(held) != null;
		if (level == null) {
			return bones ? InteractionResult.SUCCESS : InteractionResult.PASS;
		}
		Map<String, Boil> boiling = level.getAttachedOrElse(BOILING, Map.of());
		Boil in = boiling.get(key(pos));
		if (bones) {
			if (in != null) {
				player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.bones.pot_full"));
				return InteractionResult.SUCCESS;
			}
			if (!CookwareBlock.heated(level, pos)) {
				player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.bones.pot_cold"));
				return InteractionResult.SUCCESS;
			}
			Map<String, Boil> more = new HashMap<>(boiling);
			more.put(key(pos), new Boil(String.valueOf(of(held)), 0));
			level.setAttached(BOILING, more);
			if (!player.hasInfiniteMaterials()) {
				held.shrink(1);
			}
			level.playSound(null, pos, SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 0.6F, 1.0F);
			player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.bones.boiling", (BOIL_TICKS + 1199) / 1200));
			return InteractionResult.SUCCESS;
		}
		if (in == null || !held.isEmpty() || player.isShiftKeyDown()) {
			return InteractionResult.PASS;
		}
		if (in.ticks() < BOIL_TICKS) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.bones.boiling_left", (BOIL_TICKS - in.ticks() + 1199) / 1200));
			return InteractionResult.SUCCESS;
		}
		Map<String, Boil> less = new HashMap<>(boiling);
		less.remove(key(pos));
		level.setAttached(BOILING, less);
		player.getInventory().placeItemBackInInventory(cleaned(frame(Identifier.parse(in.animal())), Clean.BOILED), Prediction.SERVER_ONLY);
		level.playSound(null, pos, SoundEvents.BONE_BLOCK_BREAK, SoundSource.BLOCKS, 0.8F, 1.2F);
		return InteractionResult.SUCCESS;
	}

	/** Bones boil on while their pot is over a fire. */
	private static void boil(MinecraftServer server) {
		if (server.getTickCount() % 20 != 0) {
			return;
		}
		for (ServerLevel level : server.getAllLevels()) {
			Map<String, Boil> boiling = level.getAttached(BOILING);
			if (boiling == null || boiling.isEmpty()) {
				continue;
			}
			Map<String, Boil> next = new HashMap<>();
			for (Map.Entry<String, Boil> entry : boiling.entrySet()) {
				BlockPos pos = BlockPos.of(Long.parseLong(entry.getKey()));
				if (!level.isLoaded(pos)) {
					next.put(entry.getKey(), entry.getValue());
					continue;
				}
				if (level.getBlockState(pos).getBlock() != Delight.COOKING_POT_BLOCK) {
					continue;
				}
				Boil boil = entry.getValue();
				if (CookwareBlock.heated(level, pos) && boil.ticks() < BOIL_TICKS) {
					boil = new Boil(boil.animal(), boil.ticks() + 20);
					if (level.getRandom().nextInt(4) == 0) {
						level.sendParticles(ParticleTypes.BUBBLE_POP, pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5, 3, 0.2, 0.0, 0.2, 0.0);
					}
				}
				next.put(entry.getKey(), boil);
			}
			level.setAttached(BOILING, next);
		}
	}

	/** A pot broken with bones in it drops them, still dirty unless they were done. */
	private static void spill(ServerLevel level, BlockPos pos) {
		Map<String, Boil> boiling = level.getAttached(BOILING);
		Boil in = boiling == null ? null : boiling.get(key(pos));
		if (in == null) {
			return;
		}
		Map<String, Boil> less = new HashMap<>(boiling);
		less.remove(key(pos));
		level.setAttached(BOILING, less);
		ItemStack bones = frame(Identifier.parse(in.animal()));
		Block.popResource(level, pos, in.ticks() >= BOIL_TICKS ? cleaned(bones, Clean.BOILED) : bones);
	}
}
