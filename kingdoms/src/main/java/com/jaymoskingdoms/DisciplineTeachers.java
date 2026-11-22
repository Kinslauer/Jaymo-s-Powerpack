package com.jaymoskingdoms;

import com.jaymoskingdoms.money.Money;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Who opens a discipline of defence you have no inclination or lean for ({@link Disciplines}): the drill yard's
 * master-at-arms (a veteran guard, {@code GuardDrills#isVeteran}) for Arms, the Fist school's master for Ki, a court wizard
 * for Arcana. Each teaches it once, for {@link #FEE} silver; after that it trains at a quarter of the speed. "Teach me to
 * defend myself" ({@link #ANSWER}) shows on a teacher's MCA menu only when they have something you lack, and the answer is
 * said in bubbles, never chat.
 */
public final class DisciplineTeachers {
	/** MCA's answer for "Teach me to defend myself". */
	public static final String ANSWER = "jaymoskingdoms_discipline";
	/** What a lesson costs (silver), and how near the teacher has to be. */
	static final long FEE = 30L;
	static final double TALK_RANGE = 8.0;

	private DisciplineTeachers() {
	}

	/** The discipline {@code who} teaches, or -1. */
	static int teaches(LivingEntity who) {
		if (!who.isAlive()) {
			return -1;
		}
		if (CourtWizards.is(who)) {
			return Disciplines.ARCANA;
		}
		if (com.jaymoskingdoms.forms.Schools.masterOf(who) == com.jaymoskingdoms.forms.Schools.School.FIST) {
			return Disciplines.KI;
		}
		return com.jaymoskingdoms.forms.GuardDrills.isVeteran(who) ? Disciplines.ARMS : -1;
	}

	/** Whether the answer shows on {@code who}: a teacher of a discipline {@code player} hasn't got. */
	public static boolean canOffer(ServerPlayer player, LivingEntity who) {
		int d = teaches(who);
		return d >= 0 && Disciplines.locked(player, d);
	}

	/** "Teach me to defend myself": paid, and the discipline opened. */
	public static void teach(ServerPlayer player, LivingEntity who) {
		int d = teaches(who);
		if (d < 0 || !(player.level() instanceof ServerLevel level) || who.distanceTo(player) > TALK_RANGE) {
			return;
		}
		String key = "discipline.jaymoskingdoms.teach." + Disciplines.KEYS[d];
		if (!Disciplines.locked(player, d)) {
			Speech.tell(level, who, player, Component.translatable(key + ".known"), Speech.GOLD);
			return;
		}
		if (!player.isCreative() && !Money.charge(player, Money.worth(FEE))) {
			Speech.tell(level, who, player, Component.translatable(key + ".poor", FEE), Speech.GOLD);
			return;
		}
		Disciplines.teach(player, d);
		Speech.tell(level, who, player, Component.translatable(key + ".taught"), Speech.GOLD);
	}
}
