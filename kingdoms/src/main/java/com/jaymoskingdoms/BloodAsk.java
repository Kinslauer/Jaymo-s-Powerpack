package com.jaymoskingdoms;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import com.mojang.serialization.Codec;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Asking and paying (Night's Blood, patch 3). A new MCA talk option for vampires, "Ask to drink" ({@link #ANSWER}), with a
 * villager who knows or half-knows what you are ({@link #knowledge}: a strong suspicion ({@link Suspicion}) or a great
 * exposure ({@link Exposure}) is knowing, any at all is half). It is tied to what they want ({@link OwnWants}): they take
 * payment in coin (emeralds, as many as they'd pay for their own want) or in the thing itself, a favour; with neither, a
 * villager who thinks well of you ({@link Recollections#leaning}) does it as a friend's favour.
 * <p>
 * A willing drink raises no alarm and makes no struggle: the cup is taken without a blow, no one is told
 * ({@link Suspicion} and {@link VampireAlarm} are never asked, no theft is noted), and it gives what a bite would, scaled by
 * whose blood it is and with its after-taste ({@link BloodTaste}). Who agrees depends on how they feel about you, what you
 * paid, and their nature: a devout or timid villager needs more persuading, a greedy one is bought, a kind one gives. A
 * villager who knows what you are is warier than one who only half-knows.
 * <p>
 * A refusal is remembered ({@link Recollections.Kind#REFUSED_DRINK}), and a timid or devout villager who refuses may report
 * it ({@link #REPORT_CHANCE}): the folk near them come to suspect you ({@link Suspicion#VAMPIRE}). A drink given is
 * remembered too ({@link Recollections.Kind#LET_DRINK}); they give no more than one a day.
 */
public final class BloodAsk {
	/** "Ask to drink" (added to MCA's {@code main} menu by our dialogue data). */
	public static final String ANSWER = "jaymoskingdoms_ask_drink";
	/** How many ways each answer is said. */
	static final int LINES = 3;

	/** Suspicion that counts as knowing, and exposure ({@link Exposure#of} points) that does; any suspicion or this much exposure is half-knowing. */
	static final int KNOWS_SUSPICION = Suspicion.STRONG, KNOWS_EXPOSURE = 30, HALF_EXPOSURE = 5;
	/** What a willing drink gives (old food points: a bite gives four) and costs the giver (health). */
	static final int ASK_FOOD = 3;
	static final float ASK_COST = 2.0F;
	/** How long between two willing drinks from one person (ticks). */
	static final long GAP = 24000L;
	/** Agreeing: the leaning needed, what knowing and half-knowing add to it, what payment takes off it, and the random roll over it. */
	static final int NEED = 3, KNOWS_WARY = 4, HALF_WARY = 2, COIN_EASE = 3, WANT_EASE = 5, ROLL = 4;
	/** A devout or timid one needs this much more; a greedy one takes this much less for coin, a kind one for nothing. */
	static final int DEVOUT_WARY = 4, TIMID_WARY = 3, GREEDY_EASE = 3, KIND_EASE = 2;
	/** The leaning at which a villager does it as a friend's favour, with nothing paid. */
	static final int FRIEND = Recollections.WARM;
	/** Most emeralds asked for coin, and the least. */
	static final int COIN_MOST = 8, COIN_LEAST = 1;
	/** A refusing timid or devout villager reports you: the chance; and how far the report carries (blocks). */
	static final float REPORT_CHANCE = 0.5F;
	static final double REPORT_RANGE = 24.0;

	/** On a villager: when they last gave a drink (game time). */
	static final AttachmentType<Long> GAVE_AT = AttachmentRegistry.create(JaymosKingdoms.id("gave_blood_at"), builder -> builder.persistent(Codec.LONG));

	private BloodAsk() {
	}

	/** 2 for a villager who knows what {@code player} is, 1 for one who half-knows, 0 for one who has no idea. */
	static int knowledge(Villager villager, ServerPlayer player) {
		int suspicion = Suspicion.strength(villager, player);
		int exposure = Exposure.of(villager);
		if (suspicion >= KNOWS_SUSPICION || exposure >= KNOWS_EXPOSURE) {
			return 2;
		}
		return suspicion > 0 || exposure >= HALF_EXPOSURE ? 1 : 0;
	}

	/** Whether "Ask to drink" shows for {@code player} with {@code villager}: a vampire, a grown villager who knows or half-knows. */
	public static boolean canAsk(ServerPlayer player, Villager villager) {
		return Vampires.isVampire(player) && !Downed.isDowned(player) && Vampires.person(villager) && !Downed.isDowned(villager)
			&& Recollections.rememberer(villager) && knowledge(villager, player) > 0;
	}

	/** {@code player} asked {@code villager} to drink (MCA's menu): what they say; the rest happens as they say it. */
	public static Component ask(ServerPlayer player, Villager villager) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		Long gave = villager.getAttached(GAVE_AT);
		if (gave != null && now - gave < GAP) {
			return Component.translatable("dialogue.chat.jaymoskingdoms.ask_drink.again." + villager.getRandom().nextInt(LINES));
		}
		if (!Vows.allows(player, "feeding")) {
			return Component.translatable("dialogue.chat.jaymoskingdoms.ask_drink.no." + villager.getRandom().nextInt(LINES));
		}
		OwnWants.Want want = OwnWants.of(villager);
		int coin = Math.max(COIN_LEAST, Math.min(COIN_MOST, want.emeralds()));
		boolean hasWant = count(player, want) >= want.count();
		boolean hasCoin = count(player, Items.EMERALD) >= coin;
		int lean = Recollections.leaning(villager, player);
		boolean friend = lean >= FRIEND;
		boolean greedy = Temperaments.is(villager, Temperaments.Temperament.GREEDY);
		boolean kind = Temperaments.is(villager, Temperaments.Temperament.KINDHEARTED);
		int need = NEED + (knowledge(villager, player) >= 2 ? KNOWS_WARY : HALF_WARY);
		if (CastOut.devout(villager)) {
			need += DEVOUT_WARY;
		}
		if (Temperaments.is(villager, Temperaments.Temperament.TIMID)) {
			need += TIMID_WARY;
		}
		String paid = "";
		if (hasWant) {
			need -= WANT_EASE;
			paid = "want";
		} else if (hasCoin) {
			need -= COIN_EASE + (greedy ? GREEDY_EASE : 0);
			paid = "coin";
		} else if (friend) {
			paid = "favour";
		}
		if (kind) {
			need -= KIND_EASE;
		}
		boolean willing = !Recollections.refuses(villager, player) && !paid.isEmpty() && lean + villager.getRandom().nextInt(ROLL + 1) >= need;
		if (!willing) {
			return refused(level, player, villager, paid.isEmpty());
		}
		if (paid.equals("want")) {
			take(player, want);
		} else if (paid.equals("coin")) {
			take(player, Items.EMERALD, coin);
		}
		drink(level, player, villager);
		villager.setAttached(GAVE_AT, now);
		Recollections.remember(villager, player, Recollections.Kind.LET_DRINK, Component.empty());
		return Component.translatable("dialogue.chat.jaymoskingdoms.ask_drink.yes." + paid + "." + villager.getRandom().nextInt(LINES));
	}

	/** They won't: remembered; and a timid or devout one may tell the folk what you are. */
	private static Component refused(ServerLevel level, ServerPlayer player, Villager villager, boolean nothingPaid) {
		Recollections.remember(villager, player, Recollections.Kind.REFUSED_DRINK, Component.empty());
		level.playSound(null, villager.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 1.0F, villager.getVoicePitch());
		boolean nervous = CastOut.devout(villager) || Temperaments.is(villager, Temperaments.Temperament.TIMID);
		if (nervous && villager.getRandom().nextFloat() < REPORT_CHANCE) {
			Suspicion.witnessed(level, player, Suspicion.VAMPIRE, Suspicion.ADD_FEEDING, REPORT_RANGE, true);
			return Component.translatable("dialogue.chat.jaymoskingdoms.ask_drink.report." + villager.getRandom().nextInt(LINES));
		}
		return Component.translatable("dialogue.chat.jaymoskingdoms.ask_drink." + (nothingPaid ? "nothing" : "no") + "." + villager.getRandom().nextInt(LINES));
	}

	/**
	 * The cup, given: no blow, no struggle, no alarm. The giver is a little the weaker for it; the vampire is fed as a bite
	 * feeds them and tastes whose blood it is.
	 */
	private static void drink(ServerLevel level, ServerPlayer player, Villager villager) {
		villager.setHealth(Math.max(1.0F, villager.getHealth() - ASK_COST));
		player.heal(VampirePlayers.DRINK_HEAL);
		Heritage.Kind kind = BloodTaste.blood(villager);
		if (VampireBlood.full(player)) {
			if (!BloodPage.palateRefuses(player, villager)) {
				BloodTaste.drank(player, BloodTaste.scaled(ASK_FOOD * VampireBlood.PER_FOOD, kind, BloodTaste.great(villager)), kind, false);
			}
		} else {
			VampireBlood.feed(player, ASK_FOOD, VampirePlayers.DRINK_SATURATION);
		}
		BloodPage.fedOn(player, villager);
		KitchenCompat.drink(player, VampirePlayers.DRINK_WATER);
		VampirePlayers.fedFromBottle(player);
		BloodRequests.fed(player, villager);
		Commandments.act(player, "blood");
		VampireMask.fed(player);
		BloodFever.bit(player);
		level.sendParticles(PowersCompat.blood(0x8A0303, 0.8F), villager.getX(), villager.getEyeY() - 0.25, villager.getZ(), 4, 0.15, 0.15, 0.15, 0.0);
		level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_DRINK.value(), SoundSource.PLAYERS, 0.6F, 0.55F);
	}

	// ---- what they're paid with ------------------------------------------------------------------------------------------

	private static int count(ServerPlayer player, OwnWants.Want want) {
		int n = 0;
		for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
			if (stack.is(want.item()) && want.components().test(stack)) {
				n += stack.getCount();
			}
		}
		return n;
	}

	private static int count(ServerPlayer player, net.minecraft.world.item.Item item) {
		int n = 0;
		for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
			if (stack.is(item)) {
				n += stack.getCount();
			}
		}
		return n;
	}

	private static void take(ServerPlayer player, OwnWants.Want want) {
		int left = want.count();
		for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
			if (left > 0 && stack.is(want.item()) && want.components().test(stack)) {
				int n = Math.min(left, stack.getCount());
				stack.shrink(n);
				left -= n;
			}
		}
	}

	private static void take(ServerPlayer player, net.minecraft.world.item.Item item, int amount) {
		int left = amount;
		for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
			if (left > 0 && stack.is(item)) {
				int n = Math.min(left, stack.getCount());
				stack.shrink(n);
				left -= n;
			}
		}
	}
}
