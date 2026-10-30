package com.jaymoskingdoms.journal;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.WorldTiers;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
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
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.Level;

/**
 * (0.7.383, Andy's SevTech picks: "Rung Journal") A small book with one page for each world tier ({@link WorldTiers}), written
 * as a hermit's or a patrolman's odd short hints and never as plain lore. Each page is about the step into its tier; a line
 * with a feat behind it ticks itself when that feat lands (the great foes, the Nether, the Wither, the Dragon, and the four
 * exit deeds), read from the player's own {@link WorldTiers.Progress} each time the book is opened. It is a book to look at:
 * using it opens the pages the way a written book's open, rebuilt for whoever holds it.
 * <p>
 * A player is given one the first time they enter Borderlands ({@link #GIVEN_AT}); anyone can bind another from a book and some
 * string.
 */
public final class RungJournal extends Item {
	// ---- tuning ----
	/** The tier (an ordinal) that gives a player their journal when they first enter it: Borderlands. */
	public static final int GIVEN_AT = 1;
	/** The marks in front of a line that is done and a line still to do. */
	static final String DONE = "[x] ", TO_DO = "[ ] ";
	/** The title the book carries. */
	static final String TITLE = "Rung Journal";

	/** What a line asks for: nothing (a hint only), the tier's great foes, or one feat (bit). */
	private record Line(int feat, boolean counts) {
		static Line hint() {
			return new Line(0, false);
		}

		static Line foes() {
			return new Line(0, true);
		}

		static Line feat(int bit) {
			return new Line(bit, false);
		}
	}

	/**
	 * Per tier (Hearth to Legend), its lines in the order they're written; line {@code n} of tier {@code t} is the lang key
	 * {@code journal.jaymoskingdoms.<tier>.<n>}. The step into Wildlands carries the Aether test; the pod and the wreck come
	 * with Dread and Legend.
	 */
	private static final Line[][] PAGES = {
		{Line.hint(), Line.hint(), Line.hint()},
		{Line.foes(), Line.feat(WorldTiers.Progress.FORGED), Line.hint()},
		{Line.foes(), Line.feat(WorldTiers.Progress.NETHER), Line.feat(WorldTiers.Progress.AETHER_TROPHY), Line.hint()},
		{Line.foes(), Line.feat(WorldTiers.Progress.WITHER), Line.feat(WorldTiers.Progress.POD_FLIGHT), Line.hint()},
		{Line.foes(), Line.feat(WorldTiers.Progress.DRAGON), Line.feat(WorldTiers.Progress.MAW_SALVAGE), Line.hint()}};

	public static Item ITEM;
	/** Whether a player has been given their journal (so a lost one is not replaced by every tier change). Saved, kept through death. */
	static final AttachmentType<Boolean> GIVEN = AttachmentRegistry.create(JaymosKingdoms.id("rung_journal_given"),
		builder -> builder.persistent(Codec.BOOL).copyOnDeath());

	private RungJournal(Properties properties) {
		super(properties);
	}

	public static void register() {
		JaymosKingdoms.LOGGER.debug("rung journal attachment: {}", GIVEN);
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("rung_journal"));
		WrittenBookContent blank = new WrittenBookContent(Filterable.passThrough(TITLE), "", 0, List.of(), true);
		ITEM = Registry.register(BuiltInRegistries.ITEM, key, new RungJournal(new Item.Properties().setId(key).stacksTo(1).rarity(Rarity.UNCOMMON)
			.component(DataComponents.WRITTEN_BOOK_CONTENT, blank)
			.component(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT.withHidden(DataComponents.WRITTEN_BOOK_CONTENT, true))));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> output.insertAfter(Items.MAP, ITEM));
	}

	/** Puts a journal in {@code player}'s pack (or at their feet) the first time only. */
	public static void give(ServerPlayer player) {
		if (ITEM == null || Boolean.TRUE.equals(player.getAttached(GIVEN))) {
			return;
		}
		player.setAttached(GIVEN, true);
		player.getInventory().placeItemBackInInventory(new ItemStack(ITEM), net.minecraft.util.Prediction.SERVER_ONLY);
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.journal.given").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (player instanceof ServerPlayer sp) {
			ItemStack held = sp.getItemInHand(hand);
			held.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough(TITLE), "", 0, pages(sp), true));
			sp.containerMenu.broadcastChanges();
			sp.openItemGui(held, hand);
			level.playSound(null, sp.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 1.0F);
		}
		return InteractionResult.SUCCESS;
	}

	/** One page for each tier, ticked for {@code player}'s progress. */
	public static List<Filterable<Component>> pages(ServerPlayer player) {
		WorldTiers.Progress progress = WorldTiers.progress(player);
		int open = progress.open();
		List<Filterable<Component>> pages = new ArrayList<>();
		WorldTiers.Tier[] tiers = WorldTiers.Tier.values();
		for (int t = 0; t < tiers.length; t++) {
			MutableComponent page = Component.empty().append(tiers[t].title().copy().withStyle(ChatFormatting.BOLD)).append("\n");
			for (int n = 0; n < PAGES[t].length; n++) {
				Line line = PAGES[t][n];
				page.append("\n");
				if (line.counts() || line.feat() != 0) {
					boolean done = open >= t || (line.counts() ? progress.foes() >= WorldTiers.foesNeeded(t) : progress.has(line.feat()));
					page.append(Component.literal(done ? DONE : TO_DO).withStyle(done ? ChatFormatting.DARK_GREEN : ChatFormatting.DARK_GRAY));
				}
				page.append(Component.translatable("journal.jaymoskingdoms." + tiers[t].key() + "." + n).withStyle(ChatFormatting.BLACK));
			}
			pages.add(Filterable.passThrough(page));
		}
		return pages;
	}
}
