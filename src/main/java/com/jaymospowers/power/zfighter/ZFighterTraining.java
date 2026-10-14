package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Fate;
import com.jaymospowers.core.Powers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * Training, our own take on a ki-fighter's growth: <b>Training Points</b> (TP) come from fighting (every hit, every
 * kill, more for bigger foes), from time spent with the power, from the road (walking) and from shadowboxing (the
 * Meditate key; under heavy gravity, many times over). They buy:
 * <ul>
 * <li>five <b>stats</b> ({@link #STRENGTH} fists, {@link #TOUGHNESS} less damage taken, {@link #VITALITY} health,
 * {@link #KI_POWER} ki techniques, {@link #ENERGY} more ki), each up to {@link #STAT_CAP}; every level bought makes the
 * next one dearer;</li>
 * <li>the <b>forms</b> past Super: Super 2 and Super 3, each needing TP, a quarter of the form below mastered, and
 * hardship lived through;</li>
 * <li>the tiers of <b>Limit Break</b>, the form that stacks on top of the others and burns your health.</li>
 * </ul>
 * <b>Mastery</b> is per form (0-100%), earned only while in it: every blow landed (four times as much for a heavy one)
 * and time spent in it. A quarter opens the next form; half lets you go into it for free; and it lifts the form's
 * power while easing its drain. Saved, kept through death, synced to the owner (the training screen, the HUD).
 */
public final class ZFighterTraining {
	public static final int STRENGTH = 0;
	public static final int TOUGHNESS = 1;
	public static final int VITALITY = 2;
	public static final int KI_POWER = 3;
	public static final int ENERGY = 4;
	public static final int STATS = 5;
	public static final int STAT_CAP = 50;
	/** A stat level costs this, plus {@link #STAT_COST_STEP} for every level already bought (any stat). */
	public static final int STAT_BASE_COST = 20;
	public static final int STAT_COST_STEP = 10;
	/** What one level gives: fist damage, share of damage taken away, max health, ki technique power, max ki. */
	public static final double STRENGTH_PER_LEVEL = 0.2;
	public static final float TOUGHNESS_PER_LEVEL = 0.006F;
	public static final double VITALITY_PER_LEVEL = 0.4;
	public static final float KI_POWER_PER_LEVEL = 0.015F;
	public static final int ENERGY_PER_LEVEL = 3;

	/** Forms: 1 Super (the breakthrough), 2 Super 2, 3 Super 3. Index 0 is base. */
	public static final int FORMS = 3;
	/** TP and hardship to buy Super 2 and Super 3 (index by form); each also needs {@link #NEXT_FORM_MASTERY} of the one below. */
	public static final int[] FORM_TP = {0, 0, 4000, 12000};
	public static final int[] FORM_HARDSHIP = {0, 0, 80, 150};
	/** Mastery, in hundredths of a percent (10000 = 100%). */
	public static final int MASTERY_MAX = 10000;
	public static final int NEXT_FORM_MASTERY = 2500;
	public static final int FREE_ENTRY_MASTERY = 5000;
	/** Mastery per blow landed in a form (0.25%); a heavy blow (a fifth of the target's health or more) counts four times. */
	public static final int MASTERY_PER_HIT = 25;
	/** Mastery for every 5 s spent in a form (0.04%). */
	public static final int MASTERY_PER_TIME = 4;

	// ---- techniques: G (quick) and V (charged) each hold one; the Spirit Bomb is C in Super 3 ----
	public static final int KI_BLAST = 0;
	public static final int KI_DISC = 1;
	public static final int HOMING_VOLLEY = 2;
	public static final int SOLAR_FLASH = 3;
	public static final int KI_BARRIER = 4;
	public static final int KI_WAVE = 5;
	public static final int KI_BOMB = 6;
	public static final int SPIRIT_BOMB = 7;
	public static final int TECHNIQUES = 8;
	public static final String[] TECHNIQUE_KEYS = {"ki_blast", "ki_disc", "homing_volley", "solar_flash", "ki_barrier", "ki_wave", "ki_bomb",
		"spirit_bomb"};
	/** Known from the start: Ki Blast and Ki Wave. */
	public static final int STARTER_TECHNIQUES = 1 << KI_BLAST | 1 << KI_WAVE;
	/** TP to learn each (index by technique). */
	public static final int[] TECHNIQUE_TP = {0, 800, 1200, 600, 1500, 0, 2000, 20000};
	/** The Spirit Bomb also needs Super 3 a quarter mastered. */
	public static final int SPIRIT_BOMB_MASTERY = 2500;

	/** Limit Break's tiers: TP each (index by tier, 1-3). */
	public static final int[] LIMIT_TP = {0, 1000, 3000, 8000};

	// ---- earning TP ----
	public static final int TP_PER_HIT = 2;
	/** A kill: this, plus a quarter of the victim's max health. */
	public static final int TP_PER_KILL = 2;
	public static final int TP_PASSIVE_TICKS = 100;
	public static final double TP_TRAVEL_BLOCKS = 20.0;
	/**
	 * Shadowboxing (his meditation, the Meditate key; see {@link com.jaymospowers.core.Meditate}): once settled, every
	 * second this much TP, this much ki, form mastery while in a form, and a little hunger. Under heavy gravity (an
	 * Immense Field, Gravity's weight) it's how he trains: the TP and mastery count {@link #SHADOWBOX_HEAVY} times over
	 * (on top of heavy gravity's own {@link #HEAVY_GRAVITY_RATE}).
	 */
	public static final float SHADOWBOX_TP = 1.0F;
	public static final float SHADOWBOX_KI = 2.0F;
	public static final int SHADOWBOX_MASTERY = 4;
	public static final float SHADOWBOX_HEAVY = 3.0F;
	public static final float SHADOWBOX_EXHAUSTION = 0.2F;
	/** Hungry, a fighter trains at half the rate. */
	public static final float HUNGRY_TP = 0.5F;
	/** Z Fighters within this many blocks get {@link #SHARE_TP} of the TP he earns (training together). */
	public static final double SHARE_RANGE = 32.0;
	public static final float SHARE_TP = 0.25F;

	public record Data(int tp, List<Integer> stats, int form, List<Integer> mastery, int limit, int techniques, int quick, int charged) {
		public static final Data NONE = new Data(0, List.of(0, 0, 0, 0, 0), 0, List.of(0, 0, 0), 0, STARTER_TECHNIQUES, KI_BLAST, KI_WAVE);
		public static final Codec<Data> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.INT.optionalFieldOf("tp", 0).forGetter(Data::tp),
				Codec.INT.listOf().optionalFieldOf("stats", NONE.stats).forGetter(Data::stats),
				Codec.INT.optionalFieldOf("form", 0).forGetter(Data::form),
				Codec.INT.listOf().optionalFieldOf("mastery", NONE.mastery).forGetter(Data::mastery),
				Codec.INT.optionalFieldOf("limit", 0).forGetter(Data::limit),
				Codec.INT.optionalFieldOf("techniques", STARTER_TECHNIQUES).forGetter(Data::techniques),
				Codec.INT.optionalFieldOf("quick", KI_BLAST).forGetter(Data::quick),
				Codec.INT.optionalFieldOf("charged", KI_WAVE).forGetter(Data::charged))
			.apply(i, Data::new));
		public static final StreamCodec<ByteBuf, Data> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Data::tp,
			ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), Data::stats,
			ByteBufCodecs.VAR_INT, Data::form,
			ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), Data::mastery,
			ByteBufCodecs.VAR_INT, Data::limit,
			ByteBufCodecs.VAR_INT, Data::techniques,
			ByteBufCodecs.VAR_INT, Data::quick,
			ByteBufCodecs.VAR_INT, Data::charged,
			Data::new);

		public int stat(int index) {
			return index >= 0 && index < stats.size() ? stats.get(index) : 0;
		}

		/** Mastery of {@code form} (1-3), 0-{@link #MASTERY_MAX}. */
		public int mastery(int form) {
			return form >= 1 && form <= mastery.size() ? mastery.get(form - 1) : 0;
		}

		public int totalLevels() {
			int total = 0;
			for (int level : stats) {
				total += level;
			}
			return total;
		}

		Data withTp(int value) {
			return new Data(value, stats, form, mastery, limit, techniques, quick, charged);
		}

		Data withStat(int index, int level) {
			List<Integer> next = new ArrayList<>(stats);
			while (next.size() < STATS) {
				next.add(0);
			}
			next.set(index, level);
			return new Data(tp, List.copyOf(next), form, mastery, limit, techniques, quick, charged);
		}

		Data withForm(int value) {
			return new Data(tp, stats, value, mastery, limit, techniques, quick, charged);
		}

		Data withMastery(int form, int value) {
			List<Integer> next = new ArrayList<>(mastery);
			while (next.size() < FORMS) {
				next.add(0);
			}
			next.set(form - 1, value);
			return new Data(tp, stats, this.form, List.copyOf(next), limit, techniques, quick, charged);
		}

		Data withLimit(int value) {
			return new Data(tp, stats, form, mastery, value, techniques, quick, charged);
		}

		/** Whether technique {@code index} ({@link #KI_BLAST} etc.) has been learned. */
		public boolean knows(int index) {
			return (techniques & 1 << index) != 0;
		}

		Data withTechnique(int index) {
			return new Data(tp, stats, form, mastery, limit, techniques | 1 << index, quick, charged);
		}

		Data withQuick(int index) {
			return new Data(tp, stats, form, mastery, limit, techniques, index, charged);
		}

		Data withCharged(int index) {
			return new Data(tp, stats, form, mastery, limit, techniques, quick, index);
		}
	}

	public static final AttachmentType<Data> DATA = AttachmentRegistry.create(JaymosPowers.id("zfighter_training"),
		builder -> builder.persistent(Data.CODEC).copyOnDeath().syncWith(Data.STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));

	private ZFighterTraining() {
	}

	public static Data of(Player player) {
		Data data = player.getAttached(DATA);
		return data == null ? Data.NONE : data;
	}

	/** The cost of the next level of any stat. */
	public static int statCost(Data data) {
		return STAT_BASE_COST + STAT_COST_STEP * data.totalLevels();
	}

	/**
	 * The highest form he can take: 0 before the breakthrough, then the highest bought (Super comes with the breakthrough,
	 * which ascends the power).
	 */
	public static int formsOpen(Player player) {
		return Fate.isAscended(player) ? Mth.clamp(Math.max(1, of(player).form()), 1, FORMS) : 0;
	}

	/** Whether {@code form} (2-3) can be bought now; if not, why (a lang key), or null when it can. */
	public static String formBlocker(Player player, int form) {
		Data data = of(player);
		if (form < 2 || form > FORMS || form != formsOpen(player) + 1) {
			return form <= formsOpen(player) ? "screen.jaymospowers.zfighter.owned" : "screen.jaymospowers.zfighter.locked";
		}
		if (data.mastery(form - 1) < NEXT_FORM_MASTERY) {
			return "screen.jaymospowers.zfighter.need_mastery";
		}
		if (ZFighterPower.hardship(player) < FORM_HARDSHIP[form]) {
			return "screen.jaymospowers.zfighter.need_hardship";
		}
		if (data.tp() < FORM_TP[form]) {
			return "screen.jaymospowers.zfighter.need_tp";
		}
		return null;
	}

	// ---------------------------------------------------------------- server: earning

	static void earn(ServerPlayer player, float amount) {
		if (amount <= 0.0F || player.isCreative()) {
			return;
		}
		if (player.getFoodData().getFoodLevel() <= ZFighterPower.HUNGRY_FOOD) {
			amount *= HUNGRY_TP;
		}
		amount *= rate(player);
		add(player, amount);
		// training together: friends who are Z Fighters nearby learn from watching
		for (ServerPlayer friend : player.level().players()) {
			if (friend != player && friend.isAlive() && !friend.isCreative() && friend.distanceToSqr(player) <= SHARE_RANGE * SHARE_RANGE
				&& Powers.has(friend, ZFighterPower.INSTANCE)) {
				add(friend, amount * SHARE_TP);
			}
		}
	}

	/** A gift of TP (the Keeper, a wish): straight in, no rate. */
	static void grant(ServerPlayer player, int amount) {
		Data data = of(player);
		player.setAttached(DATA, data.withTp(data.tp() + amount));
	}

	private static void add(ServerPlayer player, float amount) {
		ZFighterPower.Live live = ZFighterPower.live(player);
		live.tpFraction += amount;
		int whole = (int)live.tpFraction;
		if (whole > 0) {
			live.tpFraction -= whole;
			Data data = of(player);
			player.setAttached(DATA, data.withTp(data.tp() + whole));
		}
	}

	/** In a Gravity user's Immense Field, or under Gravity's weight (a Well's pull, Crush's pin), he trains this much faster. */
	public static final float HEAVY_GRAVITY_RATE = 2.0F;

	/** How fast he trains right now: Training Weights worn, times heavy gravity. */
	static float rate(ServerPlayer player) {
		return ZFighterItems.weightRate(player) * (heavyGravity(player) ? HEAVY_GRAVITY_RATE : 1.0F) * ZFighterLineage.trainingFactor(player)
			* (player.level().dimension() == ZFighterSites.TIME_CHAMBER ? ZFighterSites.CHAMBER_TRAINING : 1.0F);
	}

	/** Under heavy gravity: someone's Immense Field, or Gravity's weight on him. */
	public static boolean heavyGravity(ServerPlayer player) {
		return com.jaymospowers.power.gravity.GravityTrainingField.inField(player)
			|| player.getAttached(com.jaymospowers.power.gravity.GravityPower.KNEEL) != null;
	}

	/** Mastery for the form he's in. */
	static void master(ServerPlayer player, int form, int amount) {
		if (form < 1 || form > FORMS || amount <= 0) {
			return;
		}
		Data data = of(player);
		int before = data.mastery(form);
		int after = Math.min(MASTERY_MAX, before + Math.round(amount * rate(player)));
		if (after == before) {
			return;
		}
		player.setAttached(DATA, data.withMastery(form, after));
		for (int threshold : new int[] {NEXT_FORM_MASTERY, FREE_ENTRY_MASTERY, MASTERY_MAX}) {
			if (before < threshold && after >= threshold) {
				player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_mastery_" + threshold / 100,
					Component.translatable(ZFighterLineage.formKey(player, form))).withStyle(ChatFormatting.GOLD));
				player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7F, 1.4F);
			}
		}
	}

	/** Every tick: time with the power, the road walked, meditation, time spent in a form. */
	static void tick(ServerPlayer player, ZFighterPower.Live live) {
		long now = player.level().getGameTime();
		if (now % TP_PASSIVE_TICKS == 0) {
			earn(player, 1.0F);
			if (live.form > 0) {
				master(player, live.form, MASTERY_PER_TIME);
			}
		}
		if (live.lastX != null && player.onGround() && player.getVehicle() == null && !live.flying) {
			double dx = player.getX() - live.lastX;
			double dz = player.getZ() - live.lastZ;
			double step = Math.sqrt(dx * dx + dz * dz);
			if (step < 1.0) {
				live.walked += step;
			}
			if (live.walked >= TP_TRAVEL_BLOCKS) {
				live.walked -= TP_TRAVEL_BLOCKS;
				earn(player, 1.0F);
			}
		}
		live.lastX = player.getX();
		live.lastZ = player.getZ();
		// shadowboxing: his meditation (the Meditate key, standing still), and how he trains under heavy gravity
		boolean boxing = com.jaymospowers.core.Meditate.shadowboxing(player) && !live.charging && live.waveFrom < 0 && live.barrageFrom < 0;
		live.meditating = boxing ? live.meditating + 1 : 0;
		if (live.meditating > 0 && live.meditating % 20 == 0) {
			float heavy = heavyGravity(player) ? SHADOWBOX_HEAVY : 1.0F;
			earn(player, SHADOWBOX_TP * heavy);
			live.ki += SHADOWBOX_KI;
			if (live.form > 0) {
				master(player, live.form, Math.round(SHADOWBOX_MASTERY * heavy));
			}
			player.causeFoodExhaustion(SHADOWBOX_EXHAUSTION * heavy);
		}
	}

	/** A blow he landed (fist, ki): TP, and mastery of the form he's in. */
	static void landed(ServerPlayer player, ZFighterPower.Live live, float damage, float targetMaxHealth) {
		if (damage < 1.0F) {
			return;
		}
		earn(player, TP_PER_HIT);
		if (live.form > 0) {
			// a blow under heavy gravity counts as a heavy one
			boolean heavy = damage >= targetMaxHealth * 0.2F || heavyGravity(player);
			master(player, live.form, heavy ? MASTERY_PER_HIT * 4 : MASTERY_PER_HIT);
		}
	}

	static void killed(ServerPlayer player, float victimMaxHealth) {
		earn(player, TP_PER_KILL + victimMaxHealth * 0.25F);
	}

	// ---------------------------------------------------------------- server: spending (from the training screen)

	static void buyStat(ServerPlayer player, int index) {
		Data data = of(player);
		if (index < 0 || index >= STATS || data.stat(index) >= STAT_CAP) {
			return;
		}
		int cost = statCost(data);
		if (data.tp() < cost && !player.isCreative()) {
			return;
		}
		player.setAttached(DATA, data.withStat(index, data.stat(index) + 1).withTp(player.isCreative() ? data.tp() : data.tp() - cost));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5F, 1.6F);
		ZFighterPower.refresh(player, ZFighterPower.live(player));
	}

	static void buyForm(ServerPlayer player, int form) {
		if (formBlocker(player, form) != null && !(player.isCreative() && form == formsOpen(player) + 1 && form <= FORMS && Fate.isAscended(player))) {
			return;
		}
		Data data = of(player);
		player.setAttached(DATA, data.withForm(form).withTp(player.isCreative() ? data.tp() : data.tp() - FORM_TP[form]));
		player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_form_learned",
			Component.translatable(ZFighterLineage.formKey(player, form))).withStyle(ChatFormatting.GOLD));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.8F,
			1.2F);
	}

	static void buyLimit(ServerPlayer player) {
		Data data = of(player);
		int tier = data.limit() + 1;
		if (tier >= LIMIT_TP.length) {
			return;
		}
		int cost = limitCost(player, tier);
		if (data.tp() < cost && !player.isCreative()) {
			return;
		}
		player.setAttached(DATA, data.withLimit(tier).withTp(player.isCreative() ? data.tp() : data.tp() - cost));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6F, 0.8F);
	}

	/** What Limit Break tier {@code tier} costs him (Earthlings pay half). */
	public static int limitCost(Player player, int tier) {
		return Math.round(LIMIT_TP[tier] * ZFighterLineage.limitFactor(player));
	}

	/** Whether technique {@code index} can be learned now; if not, why (a lang key), or null when it can. */
	public static String techniqueBlocker(Player player, int index) {
		Data data = of(player);
		if (index < 0 || index >= TECHNIQUES || data.knows(index)) {
			return "screen.jaymospowers.zfighter.owned";
		}
		if (index == SPIRIT_BOMB && (formsOpen(player) < FORMS || data.mastery(FORMS) < SPIRIT_BOMB_MASTERY)) {
			return "screen.jaymospowers.zfighter.need_super3";
		}
		if (data.tp() < TECHNIQUE_TP[index]) {
			return "screen.jaymospowers.zfighter.need_tp";
		}
		return null;
	}

	static void buyTechnique(ServerPlayer player, int index) {
		String blocker = techniqueBlocker(player, index);
		if (blocker != null && !(player.isCreative() && "screen.jaymospowers.zfighter.need_tp".equals(blocker))) {
			return;
		}
		Data data = of(player);
		player.setAttached(DATA, data.withTechnique(index).withTp(player.isCreative() ? data.tp() : data.tp() - TECHNIQUE_TP[index]));
		player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_technique_learned",
			Component.translatable(ZFighterLineage.techniqueKey(player, index))).withStyle(ChatFormatting.GOLD));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.8F,
			1.4F);
	}

	/** Puts a learned technique on its key: G for the quick ones, V for the charged ones. */
	static void equipTechnique(ServerPlayer player, int index) {
		Data data = of(player);
		if (index < 0 || index >= SPIRIT_BOMB || !data.knows(index)) {
			return;
		}
		player.setAttached(DATA, charged(index) ? data.withCharged(index) : data.withQuick(index));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.5F, 1.2F);
	}

	/** Whether technique {@code index} goes on V (held and let go) rather than G. */
	public static boolean charged(int index) {
		return index == KI_WAVE || index == KI_BOMB;
	}

	// ---------------------------------------------------------------- what the stats and mastery give

	/** 0-1 mastery of the form he's in (0 in base). */
	static float mastery(Player player, int form) {
		return form <= 0 ? 0.0F : of(player).mastery(form) / (float)MASTERY_MAX;
	}

	static float toughness(Player player) {
		return of(player).stat(TOUGHNESS) * TOUGHNESS_PER_LEVEL;
	}
}
