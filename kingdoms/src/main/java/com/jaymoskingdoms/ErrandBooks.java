package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.WrittenBookContent;
import org.jspecify.annotations.Nullable;

/**
 * The book a town errand comes with, and the rare errand that follows many of them. Taking an errand off the board
 * ({@link TownNotices#pull}) gives you the quest as ever, and with it a written book in the giver's hand: who they are, where
 * they were (a bearing and a distance from where you stood, and their coordinates), and that it is to them it goes back.
 * After {@link #RARE_AFTER} errands finished, the next one asked of you may instead be a rare one: the same giver trusts you
 * with the way into a ruin their people kept ({@link QuestBoard#dig}: a buried trail ruin, a fetch quest, a dig), told only
 * in the book. The words are in the book and the log, never on a screen of their own.
 */
final class ErrandBooks {
	// ---- tuning ----
	/** Town errands a player has finished before the next one asked may be the rare one. */
	static final int RARE_AFTER = 8;
	/** The chance that next one is the rare one. */
	static final float RARE_CHANCE = 0.25F;
	/** The cover's title (a written book's is capped); the name carries the errand. */
	static final String TITLE = "An Errand";
	/** Item data: the quest id the book was written for (not {@link Quests#KEY}, or a fetch would count it as its keepsake). */
	static final String BOOK_KEY = "jaymoskingdoms_errand_book";

	/** Per player: town errands finished since the last rare one. */
	static final AttachmentType<Integer> DONE = AttachmentRegistry.create(JaymosKingdoms.id("errands_done"),
		builder -> builder.persistent(Codec.INT).copyOnDeath());

	private ErrandBooks() {
	}

	/** A quest {@code q} was handed in and paid: if it was a town errand, it counts toward the rare one. */
	static void finished(ServerPlayer player, Quest q) {
		if (q.template().startsWith("town.") && (q.kind() == Quest.GATHER || q.kind() == Quest.LEAD)) {
			player.setAttached(DONE, player.getAttachedOrElse(DONE, 0) + 1);
		}
	}

	/**
	 * {@code player} pulled an errand off a board from {@code giver} of {@code f}: after enough of them, this may be the rare one
	 * instead. Null if it isn't (the ordinary errand goes on); true if the rare errand is now theirs; false if it would be but
	 * their log is full.
	 */
	static @Nullable Boolean rare(ServerLevel level, ServerPlayer player, LivingEntity giver, Faction f, RandomSource random) {
		if (player.getAttachedOrElse(DONE, 0) < RARE_AFTER || random.nextFloat() >= RARE_CHANCE) {
			return null;
		}
		Quest q = QuestBoard.dig(level, giver, f, random);
		if (q == null) {
			// (no ruin near enough, or their trade has nothing to dig for: the ordinary errand)
			return null;
		}
		if (!Quests.takeFromBoard(level, player, q)) {
			return false;
		}
		player.setAttached(DONE, 0);
		give(player, q, giver, f, true);
		return true;
	}

	/** Gives {@code player} the book for {@code q}, asked by {@code giver} of {@code f}; {@code rare} for the way into a ruin. */
	static void give(ServerPlayer player, Quest q, LivingEntity giver, Faction f, boolean rare) {
		BlockPos at = giver.blockPosition();
		String name = q.giver().name();
		Component where = Component.translatable("errand_book.page.where", at.getX(), at.getZ(),
			Math.round(Math.sqrt(at.distSqr(player.blockPosition()))), bearing(player.blockPosition(), at), name);
		List<Filterable<Component>> pages = new ArrayList<>();
		if (rare) {
			BlockPos ruin = q.objective().pos().orElse(at);
			pages.add(Filterable.passThrough(Component.translatable("errand_book.page.rare", name, f.name(), ruin.getX(), ruin.getZ())));
		} else {
			pages.add(Filterable.passThrough(Component.translatable("errand_book.page.ask", name, f.name(), Quests.title(q))));
		}
		pages.add(Filterable.passThrough(where));
		ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
		book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough(TITLE), name, 0, pages, true));
		book.set(DataComponents.CUSTOM_NAME, Component.translatable(rare ? "item.jaymoskingdoms.errand_book.rare" : "item.jaymoskingdoms.errand_book",
			Quests.title(q)).withStyle(style -> style.withItalic(false)));
		CompoundTag tag = new CompoundTag();
		tag.putString(BOOK_KEY, q.id());
		book.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		if (!player.getInventory().add(book)) {
			player.drop(book, false, net.minecraft.util.Prediction.SERVER_ONLY);
		}
	}

	/** Quest {@code id} is off {@code player}'s log: its book goes with it. */
	static void forget(ServerPlayer player, String id) {
		Inventory inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			CustomData data = s.is(Items.WRITTEN_BOOK) ? s.get(DataComponents.CUSTOM_DATA) : null;
			if (data != null && id.equals(data.copyTag().getString(BOOK_KEY).orElse(null))) {
				inv.setItem(i, ItemStack.EMPTY);
			}
		}
	}

	/** Which way {@code to} lies from {@code from}, in words (north is toward negative z). */
	private static Component bearing(BlockPos from, BlockPos to) {
		double angle = Math.toDegrees(Math.atan2(to.getX() - from.getX(), -(to.getZ() - from.getZ())));
		int point = Math.floorMod(Math.round((float) (angle / 45.0)), 8);
		return Component.translatable("errand_book.dir." + point);
	}
}
