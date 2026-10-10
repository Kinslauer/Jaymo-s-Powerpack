package com.jaymospowers.power.chrono;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Fate;
import com.jaymospowers.core.Powers;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.Vec3;

/**
 * Time Flow, the mode (Z) of a Chrono that is both Timeless (ascended) and corrupted: free-flowing combat, the way a
 * brawler fights a crowd. Time bends around the fight.
 * <ul>
 * <li><b>Strikes:</b> attack toward an enemy (within {@link #RANGE}, roughly where you look) and you're carried to it:
 * a jab, hook or uppercut up close, a lunging punch from further, a flying kick from far. Each landed blow raises the
 * combo, and the combo raises the damage.</li>
 * <li><b>Counters:</b> an enemy's blow doesn't land at once. Time holds it for {@link #PARRY_WINDOW} ticks, a white
 * flash over the attacker; right click inside that window (or just before the blow) and you counter it: the blow is
 * turned aside and they're struck and staggered. Every blow held at that moment is countered together.</li>
 * <li><b>Sets of four:</b> blows 1-3 of every four landed don't shove: they stun ({@link #STUN_TICKS}, refreshed each
 * blow: a mob's AI and movement stop, a player's movement keys go dead). Blow 4 is the finisher, the style's full push
 * x{@link #FINISHER_PUSH} with its lift.</li>
 * <li><b>Counter variants:</b> against a single attacker the counter is one of three longer moves, the caster untouchable
 * throughout: {@link #CIRCLE} (round to its back, then the blow), {@link #SLIP} (sidestep, then a body blow) or
 * {@link #DEFLECT} (one of {@link #DEFLECTS} arm parries, the attacker staggers, then the riposte).</li>
 * <li>Getting hit for real, or {@link #COMBO_TIMEOUT} without a blow, ends the combo.</li>
 * </ul>
 * The client finds the target and carries you there (movement is the client's); the server checks it and lands the
 * blow when it's due.
 */
public final class ChronoFlow {
	/** How far a strike reaches for its target (blocks), and how far the blow lands from (with slack for lag). */
	public static final double RANGE = 9.0, HIT_REACH = 3.8;
	/** Distance bands: up close a plain blow; then a lunge; beyond that a flying kick. */
	public static final double CLOSE = 2.4, MID = 5.0;
	/** Styles (animations): close blows, the lunge, the flying kick, the counter, and Chrono Punch's quick jabs. */
	public static final int JAB = 0, HOOK = 1, UPPERCUT = 2, LUNGE = 3, KICK = 4, COUNTER = 5, RAPID = 6;
	/** How long each style's animation runs, and when its blow lands (ticks). */
	public static final int[] DURATION = {6, 7, 8, 9, 12, 10, 3};
	public static final int[] HIT_AT = {2, 3, 4, 5, 8, 4, 1};
	/** Each style's damage (before the combo). */
	static final float[] DAMAGE = {4.0F, 4.5F, 5.0F, 5.0F, 6.0F, 6.0F, 0.0F};
	/** The combo adds this share per blow, up to {@code COMBO_MAX_BONUS}. */
	static final float COMBO_BONUS = 0.08F, COMBO_MAX_BONUS = 1.0F;
	static final int COMBO_TIMEOUT = 80;
	/** How long a held blow waits for the counter, and how early a right click still counts. */
	public static final int PARRY_WINDOW = 14, PARRY_EARLY = 5;
	/** A countered attacker is staggered: Slowness and Weakness this long. */
	static final int STAGGER_TICKS = 40;
	/** Blows 1-3 of each set stun for this long (refreshed each blow); blow {@code SET_SIZE} is the finisher, its push scaled up. */
	public static final int STUN_TICKS = 15, SET_SIZE = 4;
	static final double FINISHER_PUSH = 1.3;
	/** Counter variants (single attacker): the old quick counter (a crowd), circle to the back, slip aside, deflect. */
	public static final int CROWD = 0, CIRCLE = 1, SLIP = 2, DEFLECT = 3;
	/** Deflects: high forearm block, outside sweep, slap-down, cross block, elbow guard. */
	public static final int HIGH_BLOCK = 0, OUTSIDE_SWEEP = 1, SLAP_DOWN = 2, CROSS_BLOCK = 3, ELBOW_GUARD = 4, DEFLECTS = 5;
	/** Each counter variant's length and when its blow lands (ticks); the crowd counter matches {@code COUNTER}'s. */
	public static final int[] COUNTER_DURATION = {10, 24, 20, 26};
	public static final int[] COUNTER_HIT_AT = {4, 17, 12, 18};
	/** The countered attacker stays stunned this many ticks past the blow; a deflect knocks it back this hard. */
	static final int COUNTER_STUN_SLACK = 3;
	static final double DEFLECT_SHOVE = 0.25;
	/** The mode's hunger, per tick. */
	static final float EXHAUSTION = 0.006F;

	private static final DustParticleOptions GOLD = new DustParticleOptions(0xFFE08A, 1.0F);
	private static final ColorParticleOption FLASH = ColorParticleOption.create(ParticleTypes.FLASH, 0xFFFFFFFF);

	/** On a player in Time Flow (synced to everyone). */
	public static final AttachmentType<Boolean> ON = AttachmentRegistry.create(JaymosPowers.id("chrono_flow"),
		builder -> builder.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));

	/**
	 * A blow under way: its style, when it began (game time), at whom, and with which hand (1 right, -1 left). A counter
	 * also has its variant ({@link #CROWD}..{@link #DEFLECT}) and, for a deflect, which parry. Synced to all, who see it.
	 */
	public record Move(int style, long start, int target, int side, int variant, int deflect) {
		public static final StreamCodec<ByteBuf, Move> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Move::style,
			ByteBufCodecs.VAR_LONG, Move::start, ByteBufCodecs.VAR_INT, Move::target, ByteBufCodecs.VAR_INT, Move::side,
			ByteBufCodecs.VAR_INT, Move::variant, ByteBufCodecs.VAR_INT, Move::deflect, Move::new);

		public Move(int style, long start, int target, int side) {
			this(style, start, target, side, CROWD, 0);
		}

		/** How long this move's animation runs (ticks). */
		public int duration() {
			return style == COUNTER ? COUNTER_DURATION[Math.clamp(variant, CROWD, DEFLECT)] : DURATION[style];
		}

		/** When its blow lands (ticks after the start). */
		public int hitAt() {
			return style == COUNTER ? COUNTER_HIT_AT[Math.clamp(variant, CROWD, DEFLECT)] : HIT_AT[style];
		}
	}

	/** Stunned by a Time Flow blow until this game time (synced to all: a stunned player's own client locks their keys). */
	public static final AttachmentType<Long> STUN = AttachmentRegistry.create(JaymosPowers.id("chrono_flow_stun"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.all()));

	public static final AttachmentType<Move> MOVE = AttachmentRegistry.create(JaymosPowers.id("chrono_flow_move"),
		builder -> builder.syncWith(Move.STREAM_CODEC, AttachmentSyncPredicate.all()));
	/** The combo so far (synced to its owner, for the HUD). */
	public static final AttachmentType<Integer> COMBO = AttachmentRegistry.create(JaymosPowers.id("chrono_flow_combo"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.targetOnly()));

	/** Client → server: strike {@code target} in {@code style}. */
	public record Strike(int target, int style) implements CustomPacketPayload {
		public static final Type<Strike> TYPE = new Type<>(JaymosPowers.id("chrono_strike"));
		public static final StreamCodec<ByteBuf, Strike> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Strike::target,
			ByteBufCodecs.VAR_INT, Strike::style, Strike::new);

		@Override
		public Type<Strike> type() {
			return TYPE;
		}
	}

	/** Client → server: right click, the counter. */
	public record Parry() implements CustomPacketPayload {
		public static final Type<Parry> TYPE = new Type<>(JaymosPowers.id("chrono_parry"));
		public static final StreamCodec<ByteBuf, Parry> CODEC = StreamCodec.unit(new Parry());

		@Override
		public Type<Parry> type() {
			return TYPE;
		}
	}

	/** A blow held in time, waiting for the counter. */
	private record Held(UUID victim, int attacker, DamageSource source, float amount, long due) {
	}

	/** A blow on its way: lands at {@code due} if the target's still in reach. */
	private record Blow(UUID player, int target, int style, long due) {
	}

	private static final class Fighter {
		int combo;
		long lastBlow;
		long parryAt = -100;
		/** Untouchable until then: a counter's animation. */
		long counterUntil;
	}

	private static final List<Held> HELD = new ArrayList<>();
	private static final List<Blow> BLOWS = new ArrayList<>();
	private static final java.util.Map<UUID, Fighter> FIGHTERS = new java.util.HashMap<>();
	/** Everything carrying a stun, so it's taken off when it runs out. */
	private static final List<LivingEntity> STUNNED = new ArrayList<>();
	/** True while a held blow is let through (so it isn't held again). */
	private static boolean landing;

	private ChronoFlow() {
	}

	static void init() {
		PayloadTypeRegistry.serverboundPlay().register(Strike.TYPE, Strike.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Parry.TYPE, Parry.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Strike.TYPE, (payload, ctx) -> strike(ctx.player(), payload.target(), payload.style()));
		ServerPlayNetworking.registerGlobalReceiver(Parry.TYPE, (payload, ctx) -> parry(ctx.player()));
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(ChronoFlow::allowDamage);
		ServerTickEvents.END_SERVER_TICK.register(ChronoFlow::tick);
	}

	public static boolean on(net.minecraft.world.entity.player.Player player) {
		return player.getAttached(ON) != null;
	}

	/** Whether a Time Flow blow has this entity stunned (either side: the stun is synced). */
	public static boolean stunned(LivingEntity entity) {
		Long until = entity.getAttached(STUN);
		return until != null && entity.level().getGameTime() < until;
	}

	/** Stuns {@code target} for {@code ticks} (or longer, if it's already stunned longer): it stops where it stands. */
	static void stun(LivingEntity target, int ticks) {
		long until = target.level().getGameTime() + ticks;
		Long now = target.getAttached(STUN);
		if (now != null && now >= until) {
			return;
		}
		if (now == null) {
			STUNNED.add(target);
		}
		target.setAttached(STUN, until);
		if (target instanceof net.minecraft.world.entity.Mob mob) {
			mob.getNavigation().stop();
		}
	}

	private static void unstun(LivingEntity target) {
		if (target.removeAttached(STUN) != null) {
			STUNNED.remove(target);
		}
	}

	/** Whether the power can enter Time Flow: Timeless and corrupted both. */
	static boolean available(ServerPlayer player) {
		return Fate.isAscended(player) && Fate.isCorrupted(player);
	}

	/** Z: in or out. */
	static void toggle(ServerPlayer player) {
		ServerLevel level = player.level();
		if (on(player)) {
			stop(player);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.7F, 1.6F);
			return;
		}
		if (!available(player)) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.chrono_flow_unavailable").withStyle(ChatFormatting.GOLD));
			return;
		}
		player.setAttached(ON, true);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 1.0F, 0.5F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8F, 1.8F);
		for (int i = 0; i < 24; i++) {
			double a = i / 24.0 * Math.PI * 2.0;
			level.sendParticles(GOLD, player.getX() + Math.cos(a) * 1.2, player.getY() + 0.1, player.getZ() + Math.sin(a) * 1.2, 1, 0.0, 0.0, 0.0, 0.0);
		}
	}

	static void stop(ServerPlayer player) {
		player.removeAttached(ON);
		player.removeAttached(MOVE);
		player.removeAttached(COMBO);
		FIGHTERS.remove(player.getUUID());
		// anything held on them lands now
		release(player);
	}

	/** Per tick for a chrono player: the mode's upkeep. */
	static void tickPlayer(ServerPlayer player) {
		if (!on(player)) {
			return;
		}
		if (!available(player) || !player.isAlive() || player.isSpectator()) {
			stop(player);
			return;
		}
		if (!player.isCreative()) {
			player.causeFoodExhaustion(EXHAUSTION);
		}
		Fighter fighter = fighter(player);
		long now = player.level().getGameTime();
		if (fighter.combo > 0 && now - fighter.lastBlow > COMBO_TIMEOUT) {
			setCombo(player, fighter, 0);
		}
		if (player.tickCount % 8 == 0) {
			// a faint golden shimmer: time bending around them
			player.level().sendParticles(GOLD, player.getX(), player.getY() + 1.0, player.getZ(), 1, 0.35, 0.6, 0.35, 0.0);
		}
	}

	private static Fighter fighter(ServerPlayer player) {
		return FIGHTERS.computeIfAbsent(player.getUUID(), id -> new Fighter());
	}

	private static void setCombo(ServerPlayer player, Fighter fighter, int combo) {
		fighter.combo = combo;
		if (combo <= 0) {
			player.removeAttached(COMBO);
		} else {
			player.setAttached(COMBO, combo);
		}
	}

	// ---------------------------------------------------------------- strikes

	private static void strike(ServerPlayer player, int targetId, int style) {
		if (!on(player) || !player.isAlive() || style < JAB || style > KICK) {
			return;
		}
		ServerLevel level = player.level();
		Entity target = level.getEntity(targetId);
		if (!(target instanceof LivingEntity living) || !living.isAlive() || living.distanceTo(player) > RANGE + 2.0) {
			return;
		}
		long now = level.getGameTime();
		int side = level.getRandom().nextBoolean() ? 1 : -1;
		player.setAttached(MOVE, new Move(style, now, targetId, side));
		BLOWS.add(new Blow(player.getUUID(), targetId, style, now + HIT_AT[style]));
		if (style >= LUNGE) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.5F, 1.6F);
			level.sendParticles(GOLD, player.getX(), player.getY() + 1.0, player.getZ(), 8, 0.3, 0.5, 0.3, 0.0);
		}
	}

	/** A blow comes due: if the target's still in reach it lands, harder with the combo. */
	private static void land(ServerPlayer player, LivingEntity target, int style) {
		ServerLevel level = player.level();
		if (!target.isAlive() || target.distanceTo(player) - target.getBbWidth() * 0.5 > HIT_REACH) {
			return;
		}
		Fighter fighter = fighter(player);
		float bonus = Math.min(COMBO_MAX_BONUS, fighter.combo * COMBO_BONUS);
		float damage = Math.max(DAMAGE[style], (float)player.getAttributeValue(Attributes.ATTACK_DAMAGE)) * (1.0F + bonus);
		// blows 1-3 of each four stun; the fourth (and every counter's answer) throws
		boolean finisher = (fighter.combo + 1) % SET_SIZE == 0;
		boolean pushes = finisher || style == COUNTER;
		player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		target.setInvulnerableTime(0);
		Vec3 before = target.getDeltaMovement();
		if (!target.hurtServer(level, player.damageSources().playerAttack(player), damage)) {
			return;
		}
		if (pushes) {
			Vec3 away = target.position().subtract(player.position()).horizontal();
			away = away.lengthSqr() < 1.0E-4 ? player.getLookAngle().horizontal() : away.normalize();
			double push = switch (style) {
				case KICK -> 1.1;
				case LUNGE, COUNTER -> 0.7;
				default -> 0.35;
			};
			if (finisher && style != COUNTER) {
				push *= FINISHER_PUSH;
			}
			double lift = style == UPPERCUT ? 0.55 : style == KICK ? 0.3 : 0.12;
			unstun(target);
			target.setDeltaMovement(before.add(away.scale(push)).add(0.0, lift, 0.0));
		} else {
			// no shove, no lift (the hit's own knockback undone too): they're stopped where they stand
			target.setDeltaMovement(0.0, Math.min(0.0, before.y), 0.0);
			stun(target, STUN_TICKS);
		}
		target.syncVelocity = true;
		fighter.lastBlow = level.getGameTime();
		setCombo(player, fighter, fighter.combo + 1);
		Vec3 at = target.getBoundingBox().getCenter();
		level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 8, 0.25, 0.3, 0.25, 0.3);
		level.sendParticles(GOLD, at.x, at.y, at.z, 6, 0.2, 0.3, 0.2, 0.0);
		level.playSound(null, at.x, at.y, at.z, pushes ? SoundEvents.PLAYER_ATTACK_KNOCKBACK : SoundEvents.PLAYER_ATTACK_STRONG,
			SoundSource.PLAYERS, 1.0F, 0.8F + Math.min(0.6F, fighter.combo * 0.04F));
		if (finisher && style != COUNTER) {
			level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0F, 0.7F);
			level.sendParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		}
		if (fighter.combo % 5 == 0) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.9F,
				1.0F + Math.min(0.8F, fighter.combo * 0.03F));
		}
	}

	// ---------------------------------------------------------------- counters

	/** A blow on someone in Time Flow is held for the counter (or countered at once, if they pressed just before). */
	private static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (!landing && entity instanceof ServerPlayer countering) {
			Fighter f = FIGHTERS.get(countering.getUUID());
			if (f != null && countering.level().getGameTime() < f.counterUntil) {
				return false; // mid-counter: nothing lands
			}
		}
		if (landing || !(entity instanceof ServerPlayer player) || !on(player) || !(source.getEntity() instanceof LivingEntity attacker)
			|| source.getDirectEntity() != attacker || attacker == player) {
			return true;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		Fighter fighter = fighter(player);
		if (now - fighter.parryAt <= PARRY_EARLY) {
			// they saw it coming
			counter(player, List.of(attacker));
			return false;
		}
		for (Held held : HELD) {
			if (held.victim().equals(player.getUUID()) && held.attacker() == attacker.getId()) {
				return false; // already held: one blow at a time from each
			}
		}
		HELD.add(new Held(player.getUUID(), attacker.getId(), source, amount, now + PARRY_WINDOW));
		flash(level, attacker);
		player.connection.send(new net.minecraft.network.protocol.game.ClientboundSoundPacket(
			SoundEvents.NOTE_BLOCK_BELL, SoundSource.PLAYERS, player.getX(), player.getY(), player.getZ(), 0.8F, 1.9F,
			level.getRandom().nextLong()));
		return false;
	}

	/** Right click: every blow held on you is countered; with none yet, it's ready for one about to come. */
	private static void parry(ServerPlayer player) {
		if (!on(player) || !player.isAlive()) {
			return;
		}
		Fighter fighter = fighter(player);
		ServerLevel level = player.level();
		if (level.getGameTime() < fighter.counterUntil) {
			return; // already countering
		}
		fighter.parryAt = level.getGameTime();
		List<LivingEntity> attackers = new ArrayList<>();
		for (Iterator<Held> it = HELD.iterator(); it.hasNext(); ) {
			Held held = it.next();
			if (held.victim().equals(player.getUUID())) {
				it.remove();
				if (level.getEntity(held.attacker()) instanceof LivingEntity attacker && attacker.isAlive()) {
					attackers.add(attacker);
				}
			}
		}
		if (!attackers.isEmpty()) {
			counter(player, attackers);
		} else {
			// a guard raised against nothing (yet)
			player.setAttached(MOVE, new Move(COUNTER, level.getGameTime(), -1, 1));
		}
	}

	/** Turned aside: each attacker is struck and staggered, the first with the counter's blow. One attacker gets a longer counter. */
	private static void counter(ServerPlayer player, List<LivingEntity> attackers) {
		if (attackers.size() == 1) {
			counterOne(player, attackers.get(0));
			return;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		player.setAttached(MOVE, new Move(COUNTER, now, attackers.get(0).getId(), 1));
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SHIELD_BLOCK.value(), SoundSource.PLAYERS, 1.0F, 1.4F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 0.7F, 1.8F);
		for (LivingEntity attacker : attackers) {
			attacker.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, STAGGER_TICKS, 3, false, false, false), player);
			attacker.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, STAGGER_TICKS, 1, false, false, false), player);
			BLOWS.add(new Blow(player.getUUID(), attacker.getId(), COUNTER, now + HIT_AT[COUNTER]));
			Vec3 at = attacker.getEyePosition();
			level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 0.3, at.z, 8, 0.2, 0.2, 0.2, 0.05);
		}
	}

	/**
	 * A single attacker's counter: circle to its back, slip aside or deflect (picked at random), then the blow. The caster
	 * can't be touched for the whole move, and the attacker is stunned until the blow lands. The client moves the caster
	 * (circle, slip) from the synced move.
	 */
	private static void counterOne(ServerPlayer player, LivingEntity attacker) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		var random = level.getRandom();
		int variant = CIRCLE + random.nextInt(3);
		int deflect = random.nextInt(DEFLECTS);
		int side = random.nextBoolean() ? 1 : -1;
		Move move = new Move(COUNTER, now, attacker.getId(), side, variant, deflect);
		player.setAttached(MOVE, move);
		fighter(player).counterUntil = now + move.duration();
		BLOWS.add(new Blow(player.getUUID(), attacker.getId(), COUNTER, now + move.hitAt()));
		attacker.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, STAGGER_TICKS, 3, false, false, false), player);
		attacker.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, STAGGER_TICKS, 1, false, false, false), player);
		stun(attacker, move.hitAt() + COUNTER_STUN_SLACK);
		Vec3 eye = attacker.getEyePosition();
		level.sendParticles(ParticleTypes.END_ROD, eye.x, eye.y + 0.3, eye.z, 8, 0.2, 0.2, 0.2, 0.05);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 0.7F, 1.8F);
		switch (variant) {
			case CIRCLE -> {
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.8F, 0.7F);
				level.sendParticles(GOLD, player.getX(), player.getY() + 1.0, player.getZ(), 10, 0.4, 0.5, 0.4, 0.0);
			}
			case SLIP -> level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_NODAMAGE, SoundSource.PLAYERS, 1.0F, 0.8F);
			default -> {
				// the blow knocked aside: the attacker reels back a step
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SHIELD_BLOCK.value(), SoundSource.PLAYERS, 1.0F, 1.2F);
				Vec3 away = attacker.position().subtract(player.position()).horizontal();
				if (away.lengthSqr() > 1.0E-4) {
					attacker.setDeltaMovement(attacker.getDeltaMovement().add(away.normalize().scale(DEFLECT_SHOVE)));
					attacker.syncVelocity = true;
				}
				Vec3 at = player.getEyePosition().add(player.getLookAngle().scale(0.6));
				level.sendParticles(ParticleTypes.CRIT, at.x, at.y - 0.3, at.z, 6, 0.15, 0.15, 0.15, 0.2);
			}
		}
	}

	private static void flash(ServerLevel level, LivingEntity attacker) {
		level.sendParticles(FLASH, attacker.getX(), attacker.getY() + attacker.getBbHeight() + 0.6, attacker.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
		level.sendParticles(ParticleTypes.END_ROD, attacker.getX(), attacker.getY() + attacker.getBbHeight() + 0.6, attacker.getZ(), 3, 0.1, 0.1, 0.1, 0.01);
	}

	/** Everything held on {@code player} lands now (the mode ended). */
	private static void release(ServerPlayer player) {
		List<Held> due = new ArrayList<>();
		HELD.removeIf(h -> h.victim().equals(player.getUUID()) && due.add(h));
		for (Held held : due) {
			hit(player, held);
		}
	}

	private static void hit(ServerPlayer player, Held held) {
		landing = true;
		try {
			player.hurtServer(player.level(), held.source(), held.amount());
		} finally {
			landing = false;
		}
	}

	private static void tick(MinecraftServer server) {
		if (!STUNNED.isEmpty()) {
			for (Iterator<LivingEntity> it = STUNNED.iterator(); it.hasNext(); ) {
				LivingEntity entity = it.next();
				Long until = entity.getAttached(STUN);
				if (until == null || entity.isRemoved() || !entity.isAlive() || entity.level().getGameTime() >= until) {
					it.remove();
					entity.removeAttached(STUN);
				}
			}
		}
		if (!HELD.isEmpty()) {
			for (Iterator<Held> it = HELD.iterator(); it.hasNext(); ) {
				Held held = it.next();
				ServerPlayer player = server.getPlayerList().getPlayer(held.victim());
				if (player == null || !player.isAlive()) {
					it.remove();
					continue;
				}
				ServerLevel level = player.level();
				Entity attacker = level.getEntity(held.attacker());
				long now = level.getGameTime();
				if (attacker == null || !attacker.isAlive()) {
					it.remove();
					continue;
				}
				if (now < held.due()) {
					if ((held.due() - now) % 3 == 0 && attacker instanceof LivingEntity living) {
						flash(level, living);
					}
					continue;
				}
				// too late: it lands, and the combo breaks
				it.remove();
				hit(player, held);
				Fighter fighter = FIGHTERS.get(player.getUUID());
				if (fighter != null) {
					setCombo(player, fighter, 0);
				}
			}
		}
		if (!BLOWS.isEmpty()) {
			for (Iterator<Blow> it = BLOWS.iterator(); it.hasNext(); ) {
				Blow blow = it.next();
				ServerPlayer player = server.getPlayerList().getPlayer(blow.player());
				if (player == null) {
					it.remove();
					continue;
				}
				if (player.level().getGameTime() < blow.due()) {
					continue;
				}
				it.remove();
				if (player.level().getEntity(blow.target()) instanceof LivingEntity target) {
					land(player, target, blow.style());
				}
			}
		}
	}

	static void clear() {
		HELD.clear();
		BLOWS.clear();
		FIGHTERS.clear();
		STUNNED.clear();
	}

	/** A strike's style for a target this far away (and the n-th close blow of the combo). */
	public static int styleFor(double distance, int n) {
		if (distance > MID) {
			return KICK;
		}
		if (distance > CLOSE) {
			return LUNGE;
		}
		return n % 3 == 0 ? JAB : n % 3 == 1 ? HOOK : UPPERCUT;
	}
}
