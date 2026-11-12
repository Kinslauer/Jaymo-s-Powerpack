package com.jaymoskingdoms;

import com.jaymoskingdoms.forms.GuardDrills;
import com.jaymoskingdoms.forms.Schools;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * (Jung) Who teaches boxing (Jaymo's Powers' fist styles: its {@code boxing/BoxingTraining} decides everything; this
 * class only knows who the teachers are and puts the lesson into their mouths).
 * <ul>
 * <li><b>Teachers</b>: the Fist school's master ({@link Schools}), village elders ({@link CourtWizards#isElder}) and
 * veteran guards ({@link GuardDrills#isVeteran}) of the four peoples who box. A teacher teaches their people's style:
 * the Emberborn the Arrow Slit, the Stoneborn the Long Guard, Earthlings the Bastion; the Warriorborn, and the school's
 * master whoever he is, teach all three.</li>
 * <li><b>"Speak of the fist"</b> ({@link #ANSWER}) on a teacher's main menu: {@link #teach} asks Powers what the visit
 * earns (a task set, a style learned, a task for the next levels, or only encouragement) and the teacher says it, in
 * bubbles, never chat.</li>
 * <li><b>Kills</b> of our own folk that Powers can't tell apart (an orc, a camp's chief, a bandit) are reported to it
 * ({@link #slain}), and so is a blow that leaves one downed, since they go down rather than die; it counts them only
 * with fist gear in hand.</li>
 * </ul>
 * Without Jaymo's Powers nobody offers it.
 */
public final class BoxingTeachers {
	/** MCA's answer for "Speak of the fist". */
	public static final String ANSWER = "jaymoskingdoms_boxing";
	/** How near (blocks) the teacher has to be. */
	static final double TALK_RANGE = 8.0;
	/** Points toward a boxer's style for a guard's drill done (Powers gives a point a punch that counts). */
	public static final int DRILL_POINTS = 12;

	private BoxingTeachers() {
	}

	public static void register() {
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (source.getEntity() instanceof ServerPlayer player && source.getDirectEntity() == player) {
				slain(player, entity);
			}
		});
		// our folk go down rather than die (Powers' Downed): the blow that leaves one on the ground counts too
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (damageTaken > 0.0F && entity.isAlive() && source.getEntity() instanceof ServerPlayer player && source.getDirectEntity() == player
				&& PowersCompat.downedState(entity) > 0) {
				slain(player, entity);
			}
		});
	}

	/** The style {@code who} teaches (Powers' style id, or "any"), or null if they aren't a teacher. */
	static @Nullable String styleOf(LivingEntity who) {
		if (!who.isAlive() || !(who.level() instanceof ServerLevel level)) {
			return null;
		}
		if (Schools.masterOf(who) == Schools.School.FIST) {
			return "any";
		}
		boolean elder = who instanceof Aging aging && CourtWizards.isElder(level, aging);
		if (!elder && !GuardDrills.isVeteran(who)) {
			return null;
		}
		Heritage.Kind people = VillagerHeritage.of(who);
		if (people == null) {
			return null;
		}
		return switch (people) {
			case EMBERBORN -> "arrow_slit";
			case STONEBORN -> "long_guard";
			case EARTHLING -> "bastion";
			case WARRIORBORN -> "any";
			default -> null;
		};
	}

	/** Whether "Speak of the fist" shows on {@code who}: a teacher, with Powers' boxing there. */
	public static boolean canOffer(ServerPlayer player, LivingEntity who) {
		return PowersCompat.boxing() && styleOf(who) != null;
	}

	/** "Speak of the fist" ({@link #ANSWER}): the lesson, and the teacher's words for what came of it. */
	public static void teach(ServerPlayer player, LivingEntity who) {
		String style = styleOf(who);
		if (style == null || !(player.level() instanceof ServerLevel level) || who.distanceTo(player) > TALK_RANGE) {
			return;
		}
		String outcome = PowersCompat.boxingLesson(player, style);
		if (outcome == null) {
			return;
		}
		// three ways of saying each thing; the count is what is left of a task
		Speech.tell(level, who, player, Component.translatable("boxing.jaymoskingdoms." + outcome + "." + level.getRandom().nextInt(3),
			player.getDisplayName(), PowersCompat.boxingCount(player)), Speech.GOLD);
	}

	/** {@code player} killed {@code victim}: if it is one of ours that a boxer's task hunts, Powers hears of it. */
	private static void slain(ServerPlayer player, LivingEntity victim) {
		if (!PowersCompat.boxing()) {
			return;
		}
		if (victim instanceof Worker worker && (Greenskins.chief(worker.role()) || worker.role() == Folk.CHIEF)) {
			PowersCompat.boxingKill(player, victim, "chief");
		} else if (Orcs.isOrc(victim)) {
			PowersCompat.boxingKill(player, victim, "orc");
		} else if (Outlaws.is(victim)) {
			PowersCompat.boxingKill(player, victim, "bandit");
		}
	}
}
