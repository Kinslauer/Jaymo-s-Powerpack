package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.Level;

/**
 * (0.7.425) Havens patch 1: the <b>bestiary</b>, a greasy book the pedlar sells ({@link Pedlars}). One page for each kind of
 * goblin and orc, filled in by what you've done: a page is a smudge until you've met that kind; killing one writes a hint
 * of how they fight, and one of them talking under questioning ({@link Questioning}) writes a hint of their ways. Hints
 * only, never plain lore (Andy's rule), each line {@code bestiary.jaymoskingdoms.<kind>.<n>}.
 */
public final class Bestiary extends Item {
	/** What you've learned of a kind (bits): one slain, one talked. */
	public static final int SLAIN = 1, TOLD = 2;
	static final String TITLE = "Greasy Bestiary";

	/** The kinds, in page order, and the role each is for. */
	private static final String[] KINDS = {"goblin", "slinger", "goblin_king", "grunt", "ranger", "shaman", "warchief"};
	private static final int[] ROLES = {Goblins.GOBLIN, Goblins.SLINGER, Goblins.KING, Orcs.GRUNT, Orcs.RANGER, Orcs.SHAMAN, Orcs.WARCHIEF};

	public static Item ITEM;
	/** On a player: what they've learned, by kind. Saved, kept through death. */
	static final AttachmentType<Map<String, Integer>> NOTES = AttachmentRegistry.create(JaymosKingdoms.id("bestiary_notes"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.INT)).copyOnDeath());

	private Bestiary(Properties properties) {
		super(properties);
	}

	static void register() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("bestiary"));
		WrittenBookContent blank = new WrittenBookContent(Filterable.passThrough(TITLE), "", 0, List.of(), true);
		ITEM = Registry.register(BuiltInRegistries.ITEM, key, new Bestiary(new Item.Properties().setId(key).stacksTo(1).rarity(Rarity.UNCOMMON)
			.component(DataComponents.WRITTEN_BOOK_CONTENT, blank)
			.component(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT.withHidden(DataComponents.WRITTEN_BOOK_CONTENT, true))));
	}

	/** {@code player} learned {@code how} ({@link #SLAIN}, {@link #TOLD}) of the kind that has {@code role} (none for others). */
	public static void note(ServerPlayer player, int role, int how) {
		for (int i = 0; i < ROLES.length; i++) {
			if (ROLES[i] == role) {
				Map<String, Integer> notes = player.getAttachedOrElse(NOTES, Map.of());
				int was = notes.getOrDefault(KINDS[i], 0);
				if ((was & how) == 0) {
					Map<String, Integer> now = new HashMap<>(notes);
					now.put(KINDS[i], was | how);
					player.setAttached(NOTES, Map.copyOf(now));
					if (ITEM != null && player.getInventory().contains(new ItemStack(ITEM))) {
						player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.bestiary.noted").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
					}
				}
				return;
			}
		}
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (player instanceof ServerPlayer sp) {
			ItemStack held = sp.getItemInHand(hand);
			held.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough(TITLE), "", 0, pages(sp), true));
			sp.containerMenu.broadcastChanges();
			sp.openItemGui(held, hand);
			level.playSound(null, sp.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 0.8F);
		}
		return InteractionResult.SUCCESS;
	}

	/** A first page, then one for each kind as far as {@code player} has learned it. */
	static List<Filterable<Component>> pages(ServerPlayer player) {
		Map<String, Integer> notes = player.getAttachedOrElse(NOTES, Map.of());
		List<Filterable<Component>> pages = new ArrayList<>();
		pages.add(Filterable.passThrough(Component.translatable("bestiary.jaymoskingdoms.first").withStyle(ChatFormatting.BLACK)));
		for (String kind : KINDS) {
			int known = notes.getOrDefault(kind, 0);
			MutableComponent page = Component.empty();
			if (known == 0) {
				page.append(Component.translatable("bestiary.jaymoskingdoms.unknown").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
			} else {
				page.append(Component.translatable("bestiary.jaymoskingdoms." + kind).withStyle(ChatFormatting.BOLD)).append("\n\n");
				page.append(Component.translatable("bestiary.jaymoskingdoms." + kind + ".0").withStyle(ChatFormatting.BLACK));
				if ((known & SLAIN) != 0) {
					page.append("\n\n").append(Component.translatable("bestiary.jaymoskingdoms." + kind + ".1").withStyle(ChatFormatting.DARK_RED));
				}
				if ((known & TOLD) != 0) {
					page.append("\n\n").append(Component.translatable("bestiary.jaymoskingdoms." + kind + ".2").withStyle(ChatFormatting.DARK_BLUE));
				}
			}
			pages.add(Filterable.passThrough(page));
		}
		return pages;
	}
}
