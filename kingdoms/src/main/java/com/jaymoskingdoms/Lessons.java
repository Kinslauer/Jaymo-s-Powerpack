package com.jaymoskingdoms;

import com.jaymoskingdoms.money.Money;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import org.jspecify.annotations.Nullable;

/**
 * Teachers: pay a villager to teach you a tongue, a craft or a fighting style over a few days. The course is a number
 * of lessons ({@link #DAYS}); each is a day's, bought in person (you turn up, ask again, pay the day's fee) and only
 * one a day. Miss a whole day ({@link #LAPSE_DAYS}) and the course lapses, and what was paid is gone. The teacher is
 * whoever you started with; a different one starts a fresh course.
 * <ul>
 * <li>A <b>tongue</b> ({@link Languages}): any grown villager teaches their own people's language you cannot yet speak.
 * Each lesson adds a share of fluency.</li>
 * <li>A <b>craft</b>: a villager in a trade teaches it; each lesson is skill experience in the pillar the trade belongs to
 * ({@link #pillar}), and finishing the course gives more.</li>
 * <li>A <b>fighting style</b>: a guard, a veteran or a fist master teaches; with Jaymo's Powers' boxing the finished
 * course is a fist style lesson ({@link BoxingTeachers}), and always Strength experience.</li>
 * </ul>
 * Fees are a teacher's own rate ({@link Tailors#price}): friends pay less.
 */
public final class Lessons {
	/** Lessons to a course by kind: tongue, craft, fight. */
	static final int[] DAYS = {4, 3, 3};
	/** Fee per lesson (emeralds' worth, before the teacher's rate) by kind. */
	static final int[] FEE = {3, 4, 5};
	/** Whole days missed before a course lapses. */
	static final int LAPSE_DAYS = 2;
	/** Experience per lesson and for finishing a craft or fighting course. */
	static final int LESSON_XP = 40, FINISH_XP = 120;
	static final double TALK_RANGE = 6.0;

	/** The course under way: what ({@code tongue:northern}, {@code craft}, {@code fight}), by whom, lessons done and needed, the last day. */
	public record Course(String subject, UUID teacher, String name, int done, int needed, long lastDay) {
		public static final Codec<Course> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("subject").forGetter(Course::subject),
			net.minecraft.core.UUIDUtil.CODEC.fieldOf("teacher").forGetter(Course::teacher),
			Codec.STRING.fieldOf("name").forGetter(Course::name),
			Codec.INT.fieldOf("done").forGetter(Course::done),
			Codec.INT.fieldOf("needed").forGetter(Course::needed),
			Codec.LONG.fieldOf("last").forGetter(Course::lastDay)).apply(i, Course::new));
		public static final StreamCodec<RegistryFriendlyByteBuf, Course> STREAM = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, Course::subject, net.minecraft.core.UUIDUtil.STREAM_CODEC, Course::teacher, ByteBufCodecs.STRING_UTF8, Course::name,
			ByteBufCodecs.VAR_INT, Course::done, ByteBufCodecs.VAR_INT, Course::needed, ByteBufCodecs.VAR_LONG, Course::lastDay, Course::new);
	}

	public static final AttachmentType<Course> COURSE = AttachmentRegistry.create(JaymosKingdoms.id("lesson_course"),
		builder -> builder.persistent(Course.CODEC).copyOnDeath().syncWith(Course.STREAM, AttachmentSyncPredicate.targetOnly()));

	private Lessons() {
	}

	/** What a villager's trade teaches: the pillar its craft trains. */
	static Skills.@Nullable Pillar pillar(Villager v) {
		var p = v.getVillagerData().profession();
		if (p.is(VillagerProfession.ARMORER) || p.is(VillagerProfession.WEAPONSMITH) || p.is(VillagerProfession.TOOLSMITH) || p.is(VillagerProfession.MASON)) {
			return Skills.Pillar.STRENGTH;
		}
		if (p.is(VillagerProfession.FARMER) || p.is(VillagerProfession.FISHERMAN) || p.is(VillagerProfession.BUTCHER) || p.is(VillagerProfession.SHEPHERD)
			|| p.is(VillagerProfession.CLERIC)) {
			return Skills.Pillar.SURVIVAL;
		}
		if (p.is(VillagerProfession.LIBRARIAN) || p.is(VillagerProfession.CARTOGRAPHER)) {
			return Skills.Pillar.SCHOLAR;
		}
		if (p.is(VillagerProfession.FLETCHER) || p.is(VillagerProfession.LEATHERWORKER)) {
			return Skills.Pillar.STEALTH;
		}
		return null;
	}

	private static Languages.@Nullable Tongue tongue(ServerPlayer player, LivingEntity v) {
		Languages.Tongue t = Languages.of(v);
		return t != null && !Languages.speaks(player, t) ? t : null;
	}

	private static boolean fighter(LivingEntity v) {
		return Downed.isGuard(v) || BoxingTeachers.styleOf(v) != null;
	}

	/** Whether {@code v} teaches {@code player} anything. */
	static boolean offers(ServerPlayer player, LivingEntity v) {
		if (!(v instanceof Villager vil) || vil.isBaby() || !v.isAlive() || Rulers.is(v)) {
			return false;
		}
		return tongue(player, v) != null || pillar(vil) != null || fighter(v);
	}

	/** The menu of what {@code v} teaches (with the course under way, if it is theirs, first). */
	static void ask(ServerPlayer player, LivingEntity v) {
		if (!offers(player, v) || !(v.level() instanceof ServerLevel level)) {
			return;
		}
		Villager vil = (Villager)v;
		long day = level.getGameTime() / 24000L;
		List<Menus.Choice> choices = new ArrayList<>();
		Course current = player.getAttached(COURSE);
		if (current != null && current.teacher().equals(v.getUUID()) && day - current.lastDay() <= LAPSE_DAYS) {
			boolean today = current.lastDay() >= day && current.done() > 0;
			choices.add(Menus.Choice.of(Component.translatable("lessons.jaymoskingdoms.continue", subjectName(current.subject()), current.done(), current.needed(),
				fee(vil, player, kind(current.subject()))), !today && Money.canPay(player, Money.worth(fee(vil, player, kind(current.subject())))),
				p -> lesson(p, vil, current.subject())));
		}
		Languages.Tongue t = tongue(player, v);
		if (t != null && !(current != null && current.subject().equals("tongue:" + t.id()))) {
			choices.add(Menus.Choice.of(Component.translatable("lessons.jaymoskingdoms.start.tongue", t.title(), DAYS[0], fee(vil, player, 0)),
				Money.canPay(player, Money.worth(fee(vil, player, 0))), p -> lesson(p, vil, "tongue:" + t.id())));
		}
		if (pillar(vil) != null && !(current != null && current.subject().equals("craft"))) {
			choices.add(Menus.Choice.of(Component.translatable("lessons.jaymoskingdoms.start.craft", DAYS[1], fee(vil, player, 1)),
				Money.canPay(player, Money.worth(fee(vil, player, 1))), p -> lesson(p, vil, "craft")));
		}
		if (fighter(v) && !(current != null && current.subject().equals("fight"))) {
			choices.add(Menus.Choice.of(Component.translatable("lessons.jaymoskingdoms.start.fight", DAYS[2], fee(vil, player, 2)),
				Money.canPay(player, Money.worth(fee(vil, player, 2))), p -> lesson(p, vil, "fight")));
		}
		Menus.ask(player, v, Component.translatable("lessons.jaymoskingdoms.ask"), choices);
	}

	private static int kind(String subject) {
		return subject.startsWith("tongue") ? 0 : subject.equals("craft") ? 1 : 2;
	}

	private static Component subjectName(String subject) {
		if (subject.startsWith("tongue:")) {
			Languages.Tongue t = Languages.Tongue.parse(subject.substring(7));
			return t == null ? Component.empty() : t.title();
		}
		return Component.translatable("lessons.jaymoskingdoms.subject." + subject);
	}

	private static int fee(Villager teacher, ServerPlayer player, int kind) {
		return Tailors.price(teacher, player, FEE[kind]);
	}

	/** One lesson of {@code subject} from {@code teacher}: paid for, learned, and the course moved on or finished. */
	static void lesson(ServerPlayer player, Villager teacher, String subject) {
		if (!offers(player, teacher) || !(teacher.level() instanceof ServerLevel level) || teacher.distanceTo(player) > TALK_RANGE) {
			return;
		}
		int kind = kind(subject);
		long day = level.getGameTime() / 24000L;
		Course c = player.getAttached(COURSE);
		boolean same = c != null && c.subject().equals(subject) && c.teacher().equals(teacher.getUUID()) && day - c.lastDay() <= LAPSE_DAYS;
		if (same && c.lastDay() >= day && c.done() > 0) {
			Speech.tell(level, teacher, player, Component.translatable("lessons.jaymoskingdoms.tomorrow"), Speech.WHITE);
			return;
		}
		if (!Money.charge(player, Money.worth(fee(teacher, player, kind)))) {
			return;
		}
		int done = (same ? c.done() : 0) + 1;
		Course next = new Course(subject, teacher.getUUID(), teacher.getName().getString(), done, DAYS[kind], day);
		teach(player, teacher, subject, kind, done);
		McaCompat.hearts(teacher, player, 1);
		Skills.grant(player, Skills.Pillar.SCHOLAR, 5);
		if (done >= DAYS[kind]) {
			player.removeAttached(COURSE);
			finish(player, teacher, subject, kind);
			Sparks.act(player, Sparks.Act.LEARNED, false);
		} else {
			player.setAttached(COURSE, next);
			Speech.tell(level, teacher, player, Component.translatable("lessons.jaymoskingdoms.taught." + level.getRandom().nextInt(3), done, DAYS[kind]), Speech.GOLD);
		}
	}

	private static void teach(ServerPlayer player, Villager teacher, String subject, int kind, int done) {
		switch (kind) {
			case 0 -> {
				Languages.Tongue t = Languages.Tongue.parse(subject.substring(7));
				if (t != null) {
					Map<String, Integer> learned = new HashMap<>(player.getAttachedOrElse(Languages.LEARNED, Map.<String, Integer>of()));
					int step = (Languages.FLUENT + DAYS[0] - 1) / DAYS[0];
					learned.merge(t.id(), step, Integer::sum);
					player.setAttached(Languages.LEARNED, learned);
				}
			}
			case 1 -> {
				Skills.Pillar pillar = pillar(teacher);
				if (pillar != null) {
					Skills.grant(player, pillar, LESSON_XP);
				}
			}
			default -> Skills.grant(player, Skills.Pillar.STRENGTH, LESSON_XP);
		}
	}

	private static void finish(ServerPlayer player, Villager teacher, String subject, int kind) {
		ServerLevel level = player.level();
		Component done = Component.translatable("lessons.jaymoskingdoms.finished." + kind, subjectName(subject));
		if (kind == 1) {
			Skills.Pillar pillar = pillar(teacher);
			if (pillar != null) {
				Skills.grant(player, pillar, FINISH_XP);
			}
		} else if (kind == 2) {
			Skills.grant(player, Skills.Pillar.STRENGTH, FINISH_XP);
			String style = BoxingTeachers.styleOf(teacher);
			if (style != null) {
				String outcome = PowersCompat.boxingLesson(player, style);
				if (outcome != null) {
					Speech.tell(level, teacher, player, Component.translatable("boxing.jaymoskingdoms." + outcome + "." + level.getRandom().nextInt(3),
						player.getDisplayName(), PowersCompat.boxingCount(player)), Speech.GOLD);
				}
			}
		}
		Speech.tell(level, teacher, player, done, Speech.GOLD);
	}
}
