package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Carry;
import com.jaymospowers.core.Meditate;
import com.jaymospowers.core.Powers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;

/**
 * Stats that train themselves: besides the TP shop, each of the five stats fills its own bar from the kind of training
 * that suits it, and a full bar raises it a level (to the same {@link ZFighterTraining#STAT_CAP}, and trained levels
 * don't make the shop dearer). Every method tires for the day: each {@link #DAILY_HALF} points it gives in an in-game day
 * halve what it gives next (down to {@link #DAILY_FLOOR}), so varied training grows him fastest.
 * <ul>
 * <li>Strength: heavy gravity, heavy blows, breaking hard blocks (twice bare-handed), carrying, heavy shadowboxing.</li>
 * <li>Toughness: hits taken and stood (above {@link #SURVIVE_SHARE} health), blows guarded or parried, heat and cold.</li>
 * <li>Vitality: sprinting, swimming, climbing, the road walked.</li>
 * <li>Ki Power: ki techniques that hit, holding a beam or barrage, shadowboxing (his meditation).</li>
 * <li>Energy: charging ki, spending it down low and filling back up, flying on ki.</li>
 * </ul>
 * Weights, the Time Chamber and an Earthling's blood speed it up like TP; hungry, half. Saved, kept through death,
 * synced to the owner (the training screen).
 */
public final class ZFighterGrowth {
	// ---- the methods, in stat order ----
	public static final int HEAVY_GRAVITY = 0, HEAVY_BLOWS = 1, MINING = 2, CARRYING = 3, HEAVY_BOXING = 4;
	public static final int HITS_TAKEN = 5, GUARDING = 6, ELEMENTS = 7;
	public static final int SPRINTING = 8, SWIMMING = 9, CLIMBING = 10, TRAVEL = 11;
	public static final int KI_HITS = 12, BEAM_HOLD = 13, MEDITATION = 14;
	public static final int CHARGING = 15, LOW_RECOVERY = 16, KI_FLIGHT = 17;
	public static final int METHODS = 18;
	public static final String[] METHOD_KEYS = {"heavy_gravity", "heavy_blows", "mining", "carrying", "heavy_boxing", "hits_taken", "guarding",
		"elements", "sprinting", "swimming", "climbing", "travel", "ki_hits", "beam_hold", "meditation", "charging", "low_recovery", "ki_flight"};
	/** Which stat each method trains. */
	public static final int[] METHOD_STAT = {0, 0, 0, 0, 0, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 4, 4, 4};

	// ---- the bar: points for the next level, more for each level the stat already has (bought or trained) ----
	public static final float BAR_BASE = 600.0F;
	public static final float BAR_STEP = 60.0F;
	/** Each method's day: every this many points it gave today halve the next, never below the floor. */
	public static final float DAILY_HALF = 12.0F;
	public static final float DAILY_FLOOR = 0.05F;
	public static final long DAY_TICKS = 24000L;
	/** Hungry, training gives this share. */
	static final float HUNGRY_SHARE = 0.5F;

	// ---- what each method gives (points) ----
	/** Strength: every this many ticks under heavy gravity, carrying something, or shadowboxing heavy. */
	static final int HEAVY_GRAVITY_TICKS = 100;
	static final float HEAVY_GRAVITY_POINTS = 1.0F;
	static final int CARRY_TICKS = 100;
	static final float CARRY_POINTS = 1.0F;
	static final int HEAVY_BOXING_TICKS = 40;
	static final float HEAVY_BOXING_POINTS = 1.0F;
	/** A heavy blow: a punch for this much or more, or a fifth of a foe's health (a foe of at least {@link #HEAVY_FOE_HEALTH}). */
	static final float HEAVY_BLOW_DAMAGE = 6.0F;
	static final float HEAVY_BLOW_SHARE = 0.2F;
	static final float HEAVY_FOE_HEALTH = 10.0F;
	static final float HEAVY_BLOW_POINTS = 2.0F;
	/** A block at least this hard: its hardness times this (at most {@link #MINING_MAX}), twice with bare hands. */
	static final float MINING_MIN_HARDNESS = 1.5F;
	static final float MINING_PER_HARDNESS = 0.5F;
	static final float MINING_MAX = 4.0F;
	static final float MINING_BARE = 2.0F;
	/** Toughness: per point of damage taken from a creature while still above this share of his health (at most this much a hit). */
	static final float SURVIVE_SHARE = 0.3F;
	static final float HIT_POINTS_PER_DAMAGE = 1.0F;
	static final float HIT_POINTS_MAX = 6.0F;
	static final float GUARD_POINTS = 2.0F;
	static final float PARRY_POINTS = 4.0F;
	/** Out in the snow-cold or a hot land (base temperature this or more): every this many ticks; burning or freezing, faster. */
	static final float HOT_TEMPERATURE = 1.5F;
	static final int ELEMENTS_TICKS = 200;
	static final int ELEMENTS_HARSH_TICKS = 60;
	static final float ELEMENTS_POINTS = 1.0F;
	/** Vitality: blocks sprinted, swum, climbed and walked for a point each. */
	static final double SPRINT_BLOCKS = 30.0;
	static final double SWIM_BLOCKS = 12.0;
	static final double CLIMB_BLOCKS = 5.0;
	static final double TRAVEL_BLOCKS = 80.0;
	/** A step longer than this (blocks a tick) is a teleport, not training. */
	static final double MAX_STEP = 1.0;
	/** Ki Power: a ki technique that lands; every this many ticks holding a beam or barrage, or shadowboxing. */
	static final float KI_HIT_POINTS = 1.5F;
	static final int BEAM_TICKS = 20;
	static final float BEAM_POINTS = 1.0F;
	static final int MEDITATION_TICKS = 60;
	static final float MEDITATION_POINTS = 1.0F;
	/** Energy: every this many ticks charging or flying; spent below the low share and filled back to the high one. */
	static final int CHARGE_TICKS = 60;
	static final float CHARGE_POINTS = 1.0F;
	static final int FLIGHT_TICKS = 100;
	static final float FLIGHT_POINTS = 1.0F;
	static final float LOW_KI_SHARE = 0.2F;
	static final float RECOVERED_KI_SHARE = 0.8F;
	static final float LOW_RECOVERY_POINTS = 6.0F;

	public record Data(List<Float> progress, List<Integer> trained, long day, List<Float> today) {
		public static final Data NONE = new Data(List.of(0.0F, 0.0F, 0.0F, 0.0F, 0.0F), List.of(0, 0, 0, 0, 0), 0L, List.of());
		public static final Codec<Data> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.FLOAT.listOf().optionalFieldOf("progress", NONE.progress).forGetter(Data::progress),
				Codec.INT.listOf().optionalFieldOf("trained", NONE.trained).forGetter(Data::trained),
				Codec.LONG.optionalFieldOf("day", 0L).forGetter(Data::day),
				Codec.FLOAT.listOf().optionalFieldOf("today", List.of()).forGetter(Data::today))
			.apply(i, Data::new));
		public static final StreamCodec<ByteBuf, Data> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.FLOAT.apply(ByteBufCodecs.list()), Data::progress,
			ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), Data::trained,
			ByteBufCodecs.VAR_LONG, Data::day,
			ByteBufCodecs.FLOAT.apply(ByteBufCodecs.list()), Data::today,
			Data::new);

		public float progress(int stat) {
			return stat >= 0 && stat < progress.size() ? progress.get(stat) : 0.0F;
		}

		public int trained(int stat) {
			return stat >= 0 && stat < trained.size() ? trained.get(stat) : 0;
		}

		public int totalTrained() {
			int total = 0;
			for (int level : trained) {
				total += level;
			}
			return total;
		}

		/** Points method {@code method} has given on {@code day} (0 on any other day). */
		public float today(int method, long day) {
			return day == this.day && method >= 0 && method < today.size() ? today.get(method) : 0.0F;
		}

		/** What method {@code method} gives now, as a share of its full rate (it tires through the day). */
		public float factor(int method, long day) {
			return Math.max(DAILY_FLOOR, (float)Math.pow(0.5, today(method, day) / DAILY_HALF));
		}

		Data with(int stat, float value, int trainedLevels, long day, int method, float given) {
			List<Float> p = new ArrayList<>(progress);
			while (p.size() < ZFighterTraining.STATS) {
				p.add(0.0F);
			}
			p.set(stat, value);
			List<Integer> t = new ArrayList<>(trained);
			while (t.size() < ZFighterTraining.STATS) {
				t.add(0);
			}
			t.set(stat, trainedLevels);
			List<Float> d = day == this.day ? new ArrayList<>(today) : new ArrayList<>();
			while (d.size() < METHODS) {
				d.add(0.0F);
			}
			d.set(method, d.get(method) + given);
			return new Data(List.copyOf(p), List.copyOf(t), day, List.copyOf(d));
		}
	}

	public static final AttachmentType<Data> DATA = AttachmentRegistry.create(JaymosPowers.id("zfighter_growth"),
		builder -> builder.persistent(Data.CODEC).copyOnDeath().syncWith(Data.STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));

	/** Between ticks: where he was, the distances piling up, and whether his ki is down low. Not saved. */
	private static final class Track {
		double x, y, z;
		boolean placed;
		double sprinted, swum, climbed, walked;
		float prevKi = -1.0F;
		boolean low;
	}

	private static final Map<UUID, Track> TRACKS = new HashMap<>();
	/** A Ki Disc's cuts call the block-break event too: those aren't his hands at work. */
	static boolean cutting;

	private ZFighterGrowth() {
	}

	static void init() {
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			if (cutting || !(player instanceof ServerPlayer server) || !Powers.has(server, ZFighterPower.INSTANCE)) {
				return;
			}
			float hardness = state.getDestroySpeed(level, pos);
			if (hardness < MINING_MIN_HARDNESS) {
				return;
			}
			float points = Math.min(MINING_MAX, hardness * MINING_PER_HARDNESS) * (server.getMainHandItem().isEmpty() ? MINING_BARE : 1.0F);
			train(server, MINING, points);
		});
	}

	static void clear() {
		TRACKS.clear();
	}

	static void forget(ServerPlayer player) {
		TRACKS.remove(player.getUUID());
	}

	public static Data of(Player player) {
		Data data = player.getAttached(DATA);
		return data == null ? Data.NONE : data;
	}

	/** Points for stat {@code stat}'s next level, at {@code level}. */
	public static float bar(int level) {
		return BAR_BASE + BAR_STEP * level;
	}

	public static long day(Player player) {
		return player.level().getGameTime() / DAY_TICKS;
	}

	/** Training by {@code method}: {@code points} at its full rate, tired by today's use of it, into its stat's bar. */
	static void train(ServerPlayer player, int method, float points) {
		if (points <= 0.0F || player.isCreative() || !player.isAlive()) {
			return;
		}
		int stat = METHOD_STAT[method];
		ZFighterTraining.Data training = ZFighterTraining.of(player);
		int level = training.stat(stat);
		if (level >= ZFighterTraining.STAT_CAP) {
			return;
		}
		if (player.getFoodData().getFoodLevel() <= ZFighterPower.HUNGRY_FOOD) {
			points *= HUNGRY_SHARE;
		}
		points *= ZFighterItems.weightRate(player) * ZFighterLineage.trainingFactor(player)
			* (player.level().dimension() == ZFighterSites.TIME_CHAMBER ? ZFighterSites.CHAMBER_TRAINING : 1.0F)
			* ZFighterAfterlife.trainingBonus(player) * ZFighterSpace.trainingRate(player);
		Data data = of(player);
		long day = day(player);
		float given = points * data.factor(method, day);
		float progress = data.progress(stat) + given;
		int trained = data.trained(stat);
		boolean grew = false;
		while (progress >= bar(level) && level < ZFighterTraining.STAT_CAP) {
			progress -= bar(level);
			level++;
			trained++;
			grew = true;
		}
		if (level >= ZFighterTraining.STAT_CAP) {
			progress = 0.0F;
		}
		player.setAttached(DATA, data.with(stat, progress, trained, day, method, given));
		if (grew) {
			player.setAttached(ZFighterTraining.DATA, training.withStat(stat, level));
			player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_stat_trained",
				Component.translatable("stat.jaymospowers.zfighter." + ZFighterTraining.STAT_KEYS[stat]), level).withStyle(ChatFormatting.GOLD));
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5F, 1.2F);
			ZFighterPower.refresh(player, ZFighterPower.live(player));
		}
	}

	// ---------------------------------------------------------------- every tick (from the power's tick)

	static void tick(ServerPlayer player, ZFighterPower.Live live) {
		if (player.isCreative() || player.isSpectator()) {
			return;
		}
		long now = player.level().getGameTime();
		Track track = TRACKS.computeIfAbsent(player.getUUID(), id -> new Track());

		// Strength
		if (now % HEAVY_GRAVITY_TICKS == 0 && ZFighterTraining.heavyGravity(player)) {
			train(player, HEAVY_GRAVITY, HEAVY_GRAVITY_POINTS);
		}
		if (now % CARRY_TICKS == 0 && (Carry.isCarrying(player) || com.jaymospowers.power.web.KitchenCompat.isCarryingCorpse(player))) {
			train(player, CARRYING, CARRY_POINTS);
		}
		Meditate.State meditation = Meditate.of(player);
		boolean boxing = Meditate.shadowboxing(player) && meditation != null;
		if (boxing && meditation.kind() == Meditate.SHADOWBOXING_HEAVY && now % HEAVY_BOXING_TICKS == 0) {
			train(player, HEAVY_BOXING, HEAVY_BOXING_POINTS);
		}

		// Toughness: the elements
		boolean harsh = player.isOnFire() || player.getTicksFrozen() > 0;
		if (harsh ? now % ELEMENTS_HARSH_TICKS == 0 : now % ELEMENTS_TICKS == 0 && harshLand(player)) {
			train(player, ELEMENTS, ELEMENTS_POINTS);
		}

		// Vitality: the body moving
		if (track.placed && player.getVehicle() == null && !live.flying) {
			double dx = player.getX() - track.x;
			double dy = player.getY() - track.y;
			double dz = player.getZ() - track.z;
			double flat = Math.sqrt(dx * dx + dz * dz);
			if (flat < MAX_STEP && Math.abs(dy) < MAX_STEP) {
				if (player.isSwimming() || player.isInWater() && !player.onGround()) {
					track.swum += Math.sqrt(flat * flat + dy * dy);
				} else if (player.onClimbable() && dy > 0.0) {
					track.climbed += dy;
				} else if (player.onGround() && player.isSprinting()) {
					track.sprinted += flat;
				} else if (player.onGround()) {
					track.walked += flat;
				}
			}
			track.swum = pay(player, SWIMMING, track.swum, SWIM_BLOCKS);
			track.climbed = pay(player, CLIMBING, track.climbed, CLIMB_BLOCKS);
			track.sprinted = pay(player, SPRINTING, track.sprinted, SPRINT_BLOCKS);
			track.walked = pay(player, TRAVEL, track.walked, TRAVEL_BLOCKS);
		}
		track.x = player.getX();
		track.y = player.getY();
		track.z = player.getZ();
		track.placed = true;

		// Ki Power
		if (now % BEAM_TICKS == 0 && (live.waveFrom >= 0 || live.barrageFrom >= 0 || live.spiritFrom >= 0)) {
			train(player, BEAM_HOLD, BEAM_POINTS);
		}
		if (boxing && meditation.kind() == Meditate.SHADOWBOXING && now % MEDITATION_TICKS == 0) {
			train(player, MEDITATION, MEDITATION_POINTS);
		}

		// Energy
		if (live.charging && now % CHARGE_TICKS == 0) {
			train(player, CHARGING, CHARGE_POINTS);
		}
		if (live.flying && now % FLIGHT_TICKS == 0) {
			train(player, KI_FLIGHT, FLIGHT_POINTS);
		}
		int max = ZFighterPower.maxKi(player);
		if (track.prevKi >= LOW_KI_SHARE * max && live.ki < LOW_KI_SHARE * max) {
			// spent down low (not merely starting a life with none)
			track.low = true;
		} else if (track.low && live.ki >= RECOVERED_KI_SHARE * max) {
			track.low = false;
			train(player, LOW_RECOVERY, LOW_RECOVERY_POINTS);
		}
		track.prevKi = live.ki;
	}

	/** Pays method {@code method} a point for every {@code per} of {@code piled}; what's left over. */
	private static double pay(ServerPlayer player, int method, double piled, double per) {
		if (piled >= per) {
			int whole = (int)(piled / per);
			train(player, method, whole);
			return piled - whole * per;
		}
		return piled;
	}

	/** Out where it snows, or in a hot land. */
	private static boolean harshLand(ServerPlayer player) {
		BlockPos pos = BlockPos.containing(player.getEyePosition());
		return player.level().getBiome(pos).value().getBaseTemperature() >= HOT_TEMPERATURE
			|| com.jaymospowers.power.wind.WindCold.isCold(player);
	}

	// ---------------------------------------------------------------- events

	/** A blow of his landed (after the damage): heavy punches train Strength, ki techniques that hit train Ki Power. */
	static void dealt(ServerPlayer player, LivingEntity target, DamageSource source, float damage) {
		if (damage < 1.0F || target instanceof ArmorStand) {
			return;
		}
		if (source.is(ZFighterKi.KI)) {
			train(player, KI_HITS, KI_HIT_POINTS);
		} else if (source.getDirectEntity() == player && source.is(DamageTypes.PLAYER_ATTACK)
			&& (damage >= HEAVY_BLOW_DAMAGE || target.getMaxHealth() >= HEAVY_FOE_HEALTH && damage >= target.getMaxHealth() * HEAVY_BLOW_SHARE)) {
			train(player, HEAVY_BLOWS, HEAVY_BLOW_POINTS);
		}
	}

	/** He took a hit from a creature and is still standing well: Toughness. */
	static void took(ServerPlayer player, DamageSource source, float damage) {
		if (damage <= 0.0F || !(source.getEntity() instanceof LivingEntity attacker) || attacker == player || !player.isAlive()
			|| player.getHealth() < player.getMaxHealth() * SURVIVE_SHARE) {
			return;
		}
		train(player, HITS_TAKEN, Math.min(HIT_POINTS_MAX, damage * HIT_POINTS_PER_DAMAGE));
	}

	/** A blow caught on his guard (or parried): Toughness. */
	static void guarded(ServerPlayer player, boolean parried) {
		train(player, GUARDING, parried ? PARRY_POINTS : GUARD_POINTS);
	}
}
