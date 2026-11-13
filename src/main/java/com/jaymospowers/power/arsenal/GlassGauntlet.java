package com.jaymospowers.power.arsenal;

import com.jaymospowers.JaymosPowers;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.BlockTransformer;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.BlockTransformers;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.Nullable;

/**
 * The glass gauntlet: a gauntlet of volcanic glass that is every tool and a weapon besides. Nobody makes one and no
 * chest holds one: now and then ({@link #CHANCE}) an Arsenal's Reach closes on it, and from then on the rift keeps it
 * for that hand ({@link #KEPT}): it comes first on every Reach, as an ascended Arsenal's pinned weapon does (a pin of
 * theirs comes before it). It is a summon like any other pull and goes back into the rift when its time is up.
 * <ul>
 * <li><b>Claws out</b> (how it comes): a claw of the claw family (Kingdoms' claw forms work with it, and its own
 * seven-blow form), quick and sharp, and it guards like any weapon.</li>
 * <li><b>Open hand</b>: it digs, chops, shovels and tills as a diamond tool of each kind; right click strips a log or
 * tills the ground. It hits like a tool and does not guard.</li>
 * </ul>
 * Sneak and right click turns it from one to the other. Sneak and Reach with an empty hand leaves it in the rift (the
 * usual pulls come back), and the same again calls it back.
 */
public final class GlassGauntlet {
	/** The chance a Reach closes on the gauntlet for an Arsenal who has never held it (1 pull in 300). */
	static final float CHANCE = 1.0F / 300.0F;
	/** Claws out: what it adds to a blow over diamond (6 in all, as Rimeclaw) and its swing speed (a dagger's quickness). */
	static final float FIGHT_DAMAGE = 2.0F, FIGHT_SPEED = -1.6F;
	/** Open hand: what it adds to a blow, its swing speed, and how fast it works (a diamond tool's 8). */
	static final double WORK_DAMAGE = 2.0, WORK_SWING = -2.6;
	static final float WORK_SPEED = 8.0F;
	/** The model's name for each way of wearing it (custom model data, string 0); Kingdoms reads {@link #FIGHT} by name. */
	public static final String FIGHT = "fight", WORK = "work";

	/** True: the rift keeps the gauntlet for this player and brings it first. False: kept, but left in the rift for now. Saved, kept through death. */
	public static final AttachmentType<Boolean> KEPT = AttachmentRegistry.create(JaymosPowers.id("glass_gauntlet"),
		builder -> builder.persistent(Codec.BOOL).copyOnDeath().syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.targetOnly()));

	public static Item ITEM;

	private GlassGauntlet() {
	}

	static void register() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id("glass_gauntlet"));
		ITEM = Registry.register(BuiltInRegistries.ITEM, key, new GauntletItem(new Item.Properties().setId(key).rarity(Rarity.EPIC)
			.sword(ToolMaterial.DIAMOND, FIGHT_DAMAGE, FIGHT_SPEED).fireResistant()
			.component(DataComponents.CUSTOM_MODEL_DATA, look(FIGHT))
			.component(DataComponents.LORE, new ItemLore(List.of(
				Component.translatable("item.jaymospowers.glass_gauntlet.lore1").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC),
				Component.translatable("item.jaymospowers.glass_gauntlet.lore2").withStyle(ChatFormatting.DARK_PURPLE),
				Component.translatable("item.jaymospowers.glass_gauntlet.lore3").withStyle(ChatFormatting.DARK_PURPLE))))));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> output.accept(ITEM));
	}

	private static CustomModelData look(String mode) {
		return new CustomModelData(List.of(), List.of(), List.of(mode), List.of());
	}

	public static boolean is(ItemStack stack) {
		return ITEM != null && stack.is(ITEM);
	}

	/** Whether {@code stack} is the gauntlet with its claws out. */
	public static boolean fight(ItemStack stack) {
		CustomModelData data = stack.get(DataComponents.CUSTOM_MODEL_DATA);
		return is(stack) && (data == null || !data.strings().contains(WORK));
	}

	/** A claw held up as a guard: the gauntlet, or the snow giant's Rimeclaw (they share the clawed guard). */
	public static boolean clawed(ItemStack stack) {
		return fight(stack) || RelicWeapons.kind(stack) == RelicWeapons.Kind.RIMECLAW;
	}

	// ---------------------------------------------------------------- the rift keeps it

	/** Whether the rift brings {@code player} the gauntlet first. */
	static boolean favoured(Player player) {
		return Boolean.TRUE.equals(player.getAttached(KEPT));
	}

	/** Rolled when a Reach opens: the gauntlet, for an Arsenal who has never held it, or null (nearly always). */
	static @Nullable List<ItemStack> roll(ServerPlayer player, RandomSource random) {
		if (ITEM == null || player.getAttached(KEPT) != null || random.nextFloat() >= CHANCE) {
			return null;
		}
		player.setAttached(KEPT, true);
		return new ArrayList<>(List.of(new ItemStack(ITEM)));
	}

	/**
	 * Sneak + Reach with an empty hand, for an Arsenal the rift keeps the gauntlet for: leaves it in the rift, or calls
	 * it back. Returns true when it handled the key (no Reach this time).
	 */
	static boolean trySetAside(ServerPlayer player) {
		Boolean kept = player.getAttached(KEPT);
		if (kept == null || !player.isShiftKeyDown() || !player.getMainHandItem().isEmpty()) {
			return false;
		}
		player.setAttached(KEPT, !kept);
		player.sendOverlayMessage(Component.translatable(kept ? "message.jaymospowers.glass_gauntlet.left" : "message.jaymospowers.glass_gauntlet.called")
			.withStyle(kept ? ChatFormatting.GRAY : ChatFormatting.LIGHT_PURPLE));
		ServerLevel level = player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.9F, kept ? 0.6F : 1.3F);
		return true;
	}

	static void onLost(ServerPlayer player) {
		player.removeAttached(KEPT);
	}

	// ---------------------------------------------------------------- claws out, open hand

	/** Turns {@code stack} to the other way of wearing it. */
	static void turn(ServerLevel level, Player player, ItemStack stack) {
		boolean toWork = fight(stack);
		if (toWork) {
			Registry<Block> blocks = level.registryAccess().lookupOrThrow(Registries.BLOCK);
			stack.set(DataComponents.TOOL, new Tool(List.of(
				Tool.Rule.deniesDrops(blocks.getOrThrow(BlockTags.INCORRECT_FOR_DIAMOND_TOOL)),
				Tool.Rule.minesAndDrops(blocks.getOrThrow(BlockTags.MINEABLE_WITH_PICKAXE), WORK_SPEED),
				Tool.Rule.minesAndDrops(blocks.getOrThrow(BlockTags.MINEABLE_WITH_AXE), WORK_SPEED),
				Tool.Rule.minesAndDrops(blocks.getOrThrow(BlockTags.MINEABLE_WITH_SHOVEL), WORK_SPEED),
				Tool.Rule.minesAndDrops(blocks.getOrThrow(BlockTags.MINEABLE_WITH_HOE), WORK_SPEED)), 1.0F, 1, true));
			stack.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.builder()
				.add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, WORK_DAMAGE, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
				.add(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, WORK_SWING, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
				.build());
			stack.remove(DataComponents.WEAPON);
			stack.set(DataComponents.CUSTOM_MODEL_DATA, look(WORK));
		} else {
			// back to what the item is made as
			for (DataComponentType<?> type : List.of(DataComponents.TOOL, DataComponents.ATTRIBUTE_MODIFIERS, DataComponents.WEAPON, DataComponents.CUSTOM_MODEL_DATA)) {
				reset(stack, type);
			}
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_CLUSTER_STEP, SoundSource.PLAYERS, 1.0F, toWork ? 0.7F : 1.4F);
		if (player instanceof ServerPlayer serverPlayer) {
			serverPlayer.sendOverlayMessage(Component.translatable(toWork ? "message.jaymospowers.glass_gauntlet.work" : "message.jaymospowers.glass_gauntlet.fight")
				.withStyle(ChatFormatting.LIGHT_PURPLE));
		}
	}

	private static <T> void reset(ItemStack stack, DataComponentType<T> type) {
		stack.set(type, stack.getItem().components().get(type));
	}

	/** The gauntlet itself. */
	public static final class GauntletItem extends Item {
		GauntletItem(Properties properties) {
			super(properties);
		}

		/** Sneak and right click in the air turns it (a raised guard, claws out, is {@code combat.Guard}'s and comes before this). */
		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (!player.isShiftKeyDown()) {
				return InteractionResult.PASS;
			}
			if (level instanceof ServerLevel server) {
				turn(server, player, player.getItemInHand(hand));
			}
			return InteractionResult.SUCCESS;
		}

		/** At a block: sneaking turns it; the open hand strips a log as an axe does, else tills as a hoe does. */
		@Override
		public InteractionResult useOn(UseOnContext context) {
			Player player = context.getPlayer();
			ItemStack stack = context.getItemInHand();
			Level level = context.getLevel();
			if (player != null && player.isShiftKeyDown()) {
				if (level instanceof ServerLevel server) {
					turn(server, player, stack);
				}
				return InteractionResult.SUCCESS;
			}
			if (fight(stack)) {
				return InteractionResult.PASS;
			}
			Optional<Registry<BlockTransformer>> transformers = level.registryAccess().lookup(Registries.BLOCK_TRANSFORMER);
			if (transformers.isEmpty()) {
				return InteractionResult.PASS;
			}
			for (ResourceKey<BlockTransformer> key : List.of(BlockTransformers.AXE, BlockTransformers.HOE)) {
				Optional<Holder.Reference<BlockTransformer>> transformer = transformers.get().get(key);
				if (transformer.isPresent()) {
					InteractionResult result = transformer.get().value().transformBlock(context);
					if (result != InteractionResult.PASS) {
						return result;
					}
				}
			}
			return InteractionResult.PASS;
		}
	}
}
