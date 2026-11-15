package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import org.jspecify.annotations.Nullable;

/**
 * (gb-haggle) Asking for more before you take work: a giver's offer, board work just pulled off the board (asked of its
 * poster before you've set out), or a tale its asker is putting to you. Once a job. The one asked decides by their nature
 * ({@link Temperaments}, and an MCA villager's own temper) and how well you put it ({@link Presence#talk}): they agree
 * to the full ask, meet you halfway, or take offence (the offer's gone, and they and their town think less of you).
 * What they say is over their heads; nothing is told in chat.
 */
public final class Haggle {
	/** What you ask for: this share more pay. Meeting you halfway gives half of it. */
	static final float ASK = 0.3F;
	/** The chances, before nature and talk: they agree, or take offence (the rest meet you halfway). */
	static final float AGREE = 0.3F, OFFENCE = 0.2F;
	/** Per step of their nature's warmth (and of an MCA villager's temper): this much likelier to agree, and less to take offence. */
	static final float WARMTH_AGREE = 0.1F, WARMTH_OFFENCE = 0.06F;
	/** The proud and the hotheaded take it worse; the greedy don't give easily. */
	static final float TOUCHY = 0.15F, TIGHT = 0.15F;
	/** Board work can be haggled over with its poster this long after it was pulled off the board (ticks). */
	static final long BOARD_TICKS = 20L * 60 * 5;
	/** What offence costs: hearts with them, and the town's regard. */
	static final int OFFENCE_HEARTS = 5, OFFENCE_REP = 1;
	/** How many jobs haggled over are remembered (once each). */
	static final int REMEMBERED = 48;
	static final int LINES = 3;
	static final double SPEECH_RANGE = 12.0;
	/** A tale's run var: the pay's multiplier, once haggled. */
	static final String TALE_VAR = "~haggle";

	enum Outcome {
		AGREE, HALFWAY, OFFENCE
	}

	/** The jobs a player has haggled over (quest ids, newest last). Saved, kept through death. */
	static final AttachmentType<List<String>> HAGGLED = AttachmentRegistry.create(JaymosKingdoms.id("haggled"),
		builder -> builder.persistent(Codec.STRING.listOf()).copyOnDeath());

	private Haggle() {
	}

	// ---- whether -------------------------------------------------------------------------------------------------

	static boolean haggled(ServerPlayer player, String id) {
		return player.getAttachedOrElse(HAGGLED, List.of()).contains(id);
	}

	private static void remember(ServerPlayer player, String id) {
		List<String> list = new ArrayList<>(player.getAttachedOrElse(HAGGLED, List.of()));
		list.add(id);
		while (list.size() > REMEMBERED) {
			list.removeFirst();
		}
		player.setAttached(HAGGLED, List.copyOf(list));
	}

	/** {@code q}, on {@code giver}'s screen, may be haggled over: on offer, or board work of theirs just taken. */
	static boolean may(ServerLevel level, ServerPlayer player, Quest q, boolean offered) {
		if (haggled(player, q.id()) || Tales.ofTale(q) || q.reward().milli() <= 0) {
			return false;
		}
		return offered || q.kind() >= Quest.PACK && level.getGameTime() - q.givenAt() <= BOARD_TICKS;
	}

	// ---- asking --------------------------------------------------------------------------------------------------

	/** (From {@link Quests}) {@code player} asked {@code giver} for more for quest {@code id}. */
	static void quest(ServerLevel level, ServerPlayer player, LivingEntity giver, Faction f, String id) {
		Quests.Offer offer = giver.getAttachedOrElse(Quests.OFFER, new Quests.Offer(-1L, false, Optional.empty()));
		Quest on = offer.has() ? offer.quest().filter(q -> q.id().equals(id)).orElse(null) : null;
		Quest taken = on == null ? Quests.quest(player, id) : null;
		Quest q = on != null ? on : taken;
		if (q == null || !may(level, player, q, on != null) || on == null && !q.giver().uuid().equals(giver.getUUID())) {
			return;
		}
		remember(player, id);
		Outcome o = judge(level, player, giver);
		if (o == Outcome.OFFENCE) {
			offended(level, player, giver, f);
			if (on != null) {
				// the offer's off for today
				Quests.withdraw(giver, "");
			}
			return;
		}
		Quest better = q.withReward(raised(q.reward(), multiplier(o)));
		if (on != null) {
			giver.setAttached(Quests.OFFER, new Quests.Offer(offer.nextDay(), true, Optional.of(better)));
		} else {
			Quests.set(player, better);
			Quests.sync(player, true);
		}
		pleased(level, giver, o);
	}

	/**
	 * (From {@link Tales}) {@code player} asked {@code giver}, who is putting a tale to them, for more. The tale's pay is
	 * raised in its {@code vars}; null if they took offence (the tale is off).
	 */
	static @Nullable Outcome tale(ServerPlayer player, LivingEntity giver, Map<String, String> vars, String id) {
		if (!(player.level() instanceof ServerLevel level) || vars.containsKey(TALE_VAR)) {
			return null;
		}
		remember(player, id);
		Outcome o = judge(level, player, giver);
		if (o == Outcome.OFFENCE) {
			Faction f = Theft.lawAt(level, giver.blockPosition());
			offended(level, player, giver, f);
			return o;
		}
		vars.put(TALE_VAR, Float.toString(multiplier(o)));
		pleased(level, giver, o);
		return o;
	}

	/** A tale's {@code pay} as haggled in its {@code vars}. */
	static int talePay(int pay, Map<String, String> vars) {
		String m = vars.get(TALE_VAR);
		if (m == null || pay <= 0) {
			return pay;
		}
		try {
			return Math.round(pay * Float.parseFloat(m));
		} catch (NumberFormatException e) {
			return pay;
		}
	}

	// ---- the answer ----------------------------------------------------------------------------------------------

	/** How {@code giver} takes being asked for more by {@code player}. */
	static Outcome judge(ServerLevel level, ServerPlayer player, LivingEntity giver) {
		int warmth = Temperaments.warmth(giver) + (giver instanceof Villager v ? McaCompat.temper(v) : 0);
		Temperaments.Temperament t = Temperaments.of(giver);
		float talk = Presence.talk(player);
		float agree = AGREE + WARMTH_AGREE * warmth + talk - (t == Temperaments.Temperament.GREEDY ? TIGHT : 0.0F);
		float offence = OFFENCE - WARMTH_OFFENCE * warmth - talk * 0.5F
			+ (t == Temperaments.Temperament.PROUD || t == Temperaments.Temperament.HOTHEADED ? TOUCHY : 0.0F);
		agree = Math.clamp(agree, 0.02F, 0.9F);
		offence = Math.clamp(offence, 0.02F, 0.9F - agree + 0.02F);
		float roll = level.getRandom().nextFloat();
		return roll < agree ? Outcome.AGREE : roll < agree + offence ? Outcome.OFFENCE : Outcome.HALFWAY;
	}

	private static float multiplier(Outcome o) {
		return 1.0F + (o == Outcome.AGREE ? ASK : ASK / 2.0F);
	}

	/** {@code r} with its coin raised by {@code by}. */
	private static Quest.Reward raised(Quest.Reward r, float by) {
		long milli = Math.round(r.milli() * (double)by);
		int emeralds = (int)(milli / com.jaymoskingdoms.money.Money.UNIT);
		int copper = (int)((milli % com.jaymoskingdoms.money.Money.UNIT) / Currency.BIT.base);
		return new Quest.Reward(emeralds, r.items(), r.book(), r.relic(), r.hearts(), r.reputation(), r.standing(), copper);
	}

	private static void pleased(ServerLevel level, LivingEntity giver, Outcome o) {
		say(level, giver, o == Outcome.AGREE ? "agree" : "half", o == Outcome.AGREE ? Speech.GOLD : Speech.WHITE);
		level.playSound(null, giver.getX(), giver.getY(), giver.getZ(), o == Outcome.AGREE ? SoundEvents.VILLAGER_YES : SoundEvents.VILLAGER_AMBIENT,
			SoundSource.NEUTRAL, 1.0F, 1.0F);
	}

	private static void offended(ServerLevel level, ServerPlayer player, LivingEntity giver, @Nullable Faction f) {
		say(level, giver, "offence", Speech.RED);
		level.playSound(null, giver.getX(), giver.getY(), giver.getZ(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 1.0F, 0.9F);
		level.sendParticles(ParticleTypes.ANGRY_VILLAGER, giver.getX(), giver.getY(1.0), giver.getZ(), 3, 0.25, 0.25, 0.25, 0.0);
		McaCompat.hearts(giver, player, -OFFENCE_HEARTS);
		if (f != null) {
			Reputation.change(player, f, -OFFENCE_REP);
		}
	}

	private static void say(ServerLevel level, LivingEntity giver, String what, int colour) {
		Speech.say(level, giver, Component.translatable("message.jaymoskingdoms.haggle." + what + "." + level.getRandom().nextInt(LINES)), colour, SPEECH_RANGE);
	}
}
