package com.jaymoskingdoms.nightfall;

import com.jaymoskingdoms.money.Money;
import com.jaymoskingdoms.money.Coinage;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.Vampires;
import com.mojang.serialization.Codec;
import java.util.Locale;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import org.jspecify.annotations.Nullable;

/**
 * The vampires' callings (after Nightfall Plague's vampire villagers), on our own vampires ({@link Vampires}): hand one
 * a brewing stand, a smoker, a chiseled bookshelf or a note block and it takes up that calling for good (an ordinary
 * villager just looks puzzled). Then it trades from its hand: right-click it holding coins (or a purse) for its special ware,
 * or holding what it buys to sell it. And any vampire handed raw beef eats it and is quiet for the night, as if it had fed.
 * Sneaking passes, so the villager's own screen is still there.
 */
final class Callings {
	/** What a calling sells for (emeralds' worth in coin, (0.7.498)), and how many it gives. */
	static final int IMMUNITY_PRICE = 6, DRIED_MEAT_PRICE = 2, DRIED_MEAT_COUNT = 3, CARD_PRICE = 12, LUTE_PRICE = 10;
	/** What it pays (emeralds' worth in coin): the Slaughter for this many red foods, the Chronicler for a book, the Bard for a disc. */
	static final int RED_FOOD_PER_EMERALD = 4, BOOK_PAYS = 1, DISC_PAYS = 3;

	enum Calling {
		ALCHEMIST(Items.BREWING_STAND), SLAUGHTER(Items.SMOKER), CHRONICLER(Items.CHISELED_BOOKSHELF), BARD(Items.NOTE_BLOCK);

		final Item badge;

		Calling(Item badge) {
			this.badge = badge;
		}

		String id() {
			return this.name().toLowerCase(Locale.ROOT);
		}

		static @Nullable Calling of(String id) {
			for (Calling c : values()) {
				if (c.id().equals(id)) {
					return c;
				}
			}
			return null;
		}

		static @Nullable Calling badged(ItemStack stack) {
			for (Calling c : values()) {
				if (stack.is(c.badge)) {
					return c;
				}
			}
			return null;
		}
	}

	static final AttachmentType<String> CALLING = AttachmentRegistry.create(JaymosKingdoms.id("vampire_calling"),
		builder -> builder.persistent(Codec.STRING));

	private Callings() {
	}

	static void register() {
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp) || hand != InteractionHand.MAIN_HAND || sp.isShiftKeyDown()
				|| !(entity instanceof PathfinderMob mob) || !Vampires.isVampire(mob) || mob.isBaby()) {
				return InteractionResult.PASS;
			}
			return use(server, sp, mob, sp.getMainHandItem()) ? InteractionResult.SUCCESS : InteractionResult.PASS;
		});
	}

	private static boolean use(ServerLevel level, ServerPlayer player, PathfinderMob mob, ItemStack held) {
		if (held.is(Items.BEEF)) {
			if (!Vampires.feedBeef(mob)) {
				return false;
			}
			held.consume(1, player);
			level.sendParticles(Nightfall.blood(), mob.getX(), mob.getEyeY() - 0.2, mob.getZ(), 8, 0.15, 0.1, 0.15, 0.0);
			level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.GENERIC_EAT.value(), SoundSource.NEUTRAL, 1.0F, 0.7F);
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.nightfall.beef", mob.getDisplayName()));
			return true;
		}
		Calling badge = Calling.badged(held);
		Calling calling = Calling.of(mob.getAttachedOrElse(CALLING, ""));
		if (badge != null) {
			if (badge == calling) {
				return false;
			}
			mob.setAttached(CALLING, badge.id());
			held.consume(1, player);
			level.sendParticles(ParticleTypes.ENCHANT, mob.getX(), mob.getEyeY(), mob.getZ(), 20, 0.3, 0.3, 0.3, 0.5);
			level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.VILLAGER_WORK_CLERIC, SoundSource.NEUTRAL, 1.0F, 0.8F);
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.nightfall.calling." + badge.id(), mob.getDisplayName()));
			return true;
		}
		if (calling == null) {
			return false;
		}
		if (Coinage.held(held) > 0L) {
			return sell(level, player, mob, calling);
		}
		return buy(level, player, mob, calling, held);
	}

	/** Its special ware, for coin out of the coins and purses carried. */
	private static boolean sell(ServerLevel level, ServerPlayer player, PathfinderMob mob, Calling calling) {
		int price;
		ItemStack ware;
		switch (calling) {
			case ALCHEMIST -> {
				price = IMMUNITY_PRICE;
				ware = PotionContents.createItemStack(Items.POTION, Nightfall.IMMUNITY_POTION);
			}
			case SLAUGHTER -> {
				price = DRIED_MEAT_PRICE;
				ware = new ItemStack(Nightfall.DRIED_MEAT, DRIED_MEAT_COUNT);
			}
			case CHRONICLER -> {
				price = CARD_PRICE;
				ware = new ItemStack(Nightfall.CURSED_CARD);
			}
			default -> {
				price = LUTE_PRICE;
				ware = new ItemStack(Nightfall.LUTE);
			}
		}
		if (!Money.charge(player, Money.worth(price))) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.nightfall.price", mob.getDisplayName(), Money.text(player, Money.worth(price)), ware.getHoverName()));
			return true;
		}
		player.getInventory().placeItemBackInInventory(ware, Prediction.SERVER_ONLY);
		level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1.0F, 0.75F);
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.nightfall.sold." + calling.id(), mob.getDisplayName()));
		return true;
	}

	/** What its calling buys: red foods (the Slaughter), books (the Chronicler), music discs (the Bard). */
	private static boolean buy(ServerLevel level, ServerPlayer player, PathfinderMob mob, Calling calling, ItemStack held) {
		int take, pay;
		switch (calling) {
			case SLAUGHTER -> {
				if (!red(held) || held.getCount() < RED_FOOD_PER_EMERALD) {
					return false;
				}
				take = RED_FOOD_PER_EMERALD;
				pay = 1;
			}
			case CHRONICLER -> {
				if (!held.is(Items.BOOK) && !held.is(Items.WRITABLE_BOOK) && !held.is(Items.WRITTEN_BOOK) && !held.is(Items.ENCHANTED_BOOK)) {
					return false;
				}
				take = 1;
				pay = BOOK_PAYS;
			}
			case BARD -> {
				if (!held.has(net.minecraft.core.component.DataComponents.JUKEBOX_PLAYABLE)) {
					return false;
				}
				take = 1;
				pay = DISC_PAYS;
			}
			default -> {
				return false;
			}
		}
		held.consume(take, player);
		Money.pay(player, Money.worth(pay));
		level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1.0F, 0.75F);
		return true;
	}

	/** Red food: raw meat, berries, apples, beetroot. */
	private static boolean red(ItemStack stack) {
		return stack.is(Items.MUTTON) || stack.is(Items.PORKCHOP) || stack.is(Items.CHICKEN) || stack.is(Items.RABBIT) || stack.is(Items.SWEET_BERRIES)
			|| stack.is(Items.APPLE) || stack.is(Items.BEETROOT) || stack.is(ItemTags.MEAT) && !stack.is(Items.BEEF);
	}
}
