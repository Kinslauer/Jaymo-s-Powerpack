package com.jaymoskingdoms.spirits;

import com.jaymoskingdoms.Fx;
import com.jaymoskingdoms.Mana;
import com.jaymoskingdoms.PowersCompat;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Two as One: Grimsby's and Varro's last art (Andy, 2026-11-14: "the capstone being a mode in which you play as if you had
 * celebrimbor's power, and you can use the abilities"; the idea only, our own name and look). For {@value #TICKS} ticks the
 * spirit is not behind you but in you, and fights with your hands:
 * <ul>
 * <li>its figure stands full and bright over you the whole time, for the attuned;</li>
 * <li>every art costs half its mana and is ready again in a third of the time;</li>
 * <li>each blow of your own lands again as the spirit's ({@link #ECHO} of it), and every {@value #CHAIN_EVERY}th sets off
 * an art for nothing: Grimsby's Pale Glaive, or a javelin from Varro at each of the three nearest;</li>
 * <li>a blow at you is answered ({@value #COUNTER_EVERY} ticks apart): Grimsby turns it aside and cuts back, Varro throws
 * the striker off its feet;</li>
 * <li>each kill adds {@value #PER_KILL} ticks (to {@value #MOST} in all) and leads to the next: Grimsby puts you on the
 * nearest foe by the Short Road, Varro hauls it to your feet;</li>
 * <li>you run and leap as it did, and no fall hurts.</li>
 * </ul>
 * It ends when its time is up or the spirit is put away, and cannot be called again for {@value #COOLDOWN} ticks.
 */
public final class AsOne {
	static final int LEVEL = 18, TICKS = 20 * 30, MOST = 20 * 60, PER_KILL = 60, COOLDOWN = 20 * 240;
	static final float COST = 40.0F, COST_TIMES = 0.5F, COOLDOWN_TIMES = 1.0F / 3.0F;
	/** The share of each of your own blows that lands again as the spirit's, and every which blow sets off a free art. */
	static final float ECHO = 0.5F;
	static final int CHAIN_EVERY = 5;
	/** The answer to a blow: ticks between two, Grimsby's cut back, how long Varro's throw leaves the striker off its feet. */
	static final int COUNTER_EVERY = 40, COUNTER_STAGGER = 25;
	static final float COUNTER_DAMAGE = 8.0F;
	/** After a kill: how far the next foe may be, and the ticks before the spirit takes you (or it) there. */
	static final double NEXT_RANGE = 14.0;
	static final int NEXT_DELAY = 6;

	public static final Move MOVE = new Art("as_one", COST, COOLDOWN, LEVEL, Poses.SPEAK, Mana.WILL, AsOne::begin);

	private static final class Run {
		final ServerPlayer player;
		final long began;
		long until, counterAt, nextAt;
		int blows;

		Run(ServerPlayer player, long now) {
			this.player = player;
			this.began = now;
			this.until = now + TICKS;
		}
	}

	private static final Map<UUID, Run> RUNS = new HashMap<>();
	private static boolean echoing;

	private AsOne() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(AsOne::tick);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !answered(entity, source, amount));
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> echo(entity, source, base, taken));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			Run run = source.getEntity() instanceof ServerPlayer killer ? RUNS.get(killer.getUUID()) : null;
			if (run != null) {
				long now = run.player.level().getGameTime();
				run.until = Math.min(run.began + MOST, run.until + PER_KILL);
				run.nextAt = now + NEXT_DELAY;
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> RUNS.clear());
	}

	public static boolean on(@Nullable LivingEntity player) {
		return player != null && RUNS.containsKey(player.getUUID());
	}

	/** What {@code move}'s mana cost is multiplied by for {@code player} just now. */
	static float costTimes(ServerPlayer player) {
		return on(player) ? COST_TIMES : 1.0F;
	}

	/** What {@code move}'s cooldown is multiplied by for {@code player} just now (never its own). */
	static float cooldownTimes(ServerPlayer player, Move move) {
		return move != MOVE && on(player) ? COOLDOWN_TIMES : 1.0F;
	}

	private static boolean begin(ServerPlayer player, SpiritState state) {
		if (on(player) || state.kind() != Spirits.GRIMSBY && state.kind() != Spirits.VARRO) {
			return false;
		}
		ServerLevel level = (ServerLevel)player.level();
		RUNS.put(player.getUUID(), new Run(player, level.getGameTime()));
		Spirits.toggle(player, SpiritState.AS_ONE, true);
		int color = Spirits.of(state.kind()).color() & 0xFFFFFF;
		Fx.blast(level, player.position().add(0.0, 1.0, 0.0), 3.0F, color);
		Fx.sigil(level, player.position().add(0.0, 0.05, 0.0), 2.0F, color, 40);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0F, 0.7F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 0.5F, 1.4F);
		PowersCompat.shake(player, 0.6F);
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.spirit.as_one." + Spirits.of(state.kind()).id())
			.withStyle(s -> s.withColor(color)));
		return true;
	}

	private static void end(Run run) {
		Spirits.toggle(run.player, SpiritState.AS_ONE, false);
		if (run.player.level() instanceof ServerLevel level && !run.player.isRemoved()) {
			level.playSound(null, run.player.getX(), run.player.getY(), run.player.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.8F, 0.9F);
			run.player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.spirit.as_one.over").withStyle(ChatFormatting.GRAY));
		}
	}

	private static void tick(MinecraftServer server) {
		for (Iterator<Run> it = RUNS.values().iterator(); it.hasNext();) {
			Run run = it.next();
			ServerPlayer player = run.player;
			SpiritState state = player.isRemoved() ? null : Spirits.state(player);
			if (state == null || !player.isAlive() || !state.out() || !state.toggled(SpiritState.AS_ONE) || !(player.level() instanceof ServerLevel level)
				|| level.getGameTime() >= run.until) {
				it.remove();
				end(run);
				continue;
			}
			long now = level.getGameTime();
			if (now % 20L == 0L) {
				player.addEffect(new MobEffectInstance(MobEffects.SPEED, 30, 0, false, false));
				player.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, 30, 1, false, false));
			}
			if (now % 4L == 0L) {
				Mana.sendSeen(level, Fx.wisp(Spirits.of(state.kind()).color() & 0xFFFFFF, 1.1F), player.getX(), player.getY() + 1.0, player.getZ(), 2, 0.3, 0.6, 0.3, 0.01);
			}
			if (run.nextAt > 0L && now >= run.nextAt) {
				run.nextAt = 0L;
				next(level, player, state);
			}
		}
	}

	/** After a kill: on to the nearest foe in sight. */
	private static void next(ServerLevel level, ServerPlayer player, SpiritState state) {
		LivingEntity best = null;
		double bestD = NEXT_RANGE * NEXT_RANGE;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(NEXT_RANGE), e -> SpiritFoes.foe(player, e, false))) {
			double d = e.distanceToSqr(player);
			if (d < bestD && d > 9.0 && player.hasLineOfSight(e)) {
				best = e;
				bestD = d;
			}
		}
		if (best == null) {
			return;
		}
		float scale = Spirits.damageScale(state);
		if (state.kind() == Spirits.GRIMSBY ? GrimsbyArts.stepTo(player, best, scale) : VarroArts.haulIn(player, best, scale)) {
			Spirits.show(player, state.kind() == Spirits.GRIMSBY ? Poses.SWORD : Poses.PULL);
		}
	}

	/** A blow of the keeper's own landed: it lands again as the spirit's, and every few sets off an art. */
	private static void echo(LivingEntity entity, DamageSource source, float base, float taken) {
		if (echoing || taken <= 0.0F || !(source.getEntity() instanceof ServerPlayer player) || source.getDirectEntity() != player
			|| source.is(DamageTypeTags.BYPASSES_ARMOR) || !(entity.level() instanceof ServerLevel level)) {
			return;
		}
		Run run = RUNS.get(player.getUUID());
		SpiritState state = run == null ? null : Spirits.state(player);
		if (state == null || !SpiritFoes.foe(player, entity, true)) {
			return;
		}
		echoing = true;
		try {
			if (entity.isAlive()) {
				// (inside the hit window a harder blow lands only its excess, as Varro's mark does)
				SpiritFoes.strike(level, player, entity, base + base * ECHO);
			}
			if (++run.blows % CHAIN_EVERY == 0) {
				float scale = Spirits.damageScale(state);
				if (state.kind() == Spirits.GRIMSBY) {
					GrimsbyArts.glaiveNow(player, state, scale);
					Spirits.show(player, Poses.GREATSWORD);
				} else {
					volley(level, player, entity, scale);
					Spirits.show(player, Poses.VOLLEY);
				}
			}
		} finally {
			echoing = false;
		}
	}

	/** A javelin from Varro at each of the three nearest foes. */
	private static void volley(ServerLevel level, ServerPlayer player, LivingEntity first, float scale) {
		Vec3 from = player.getEyePosition().add(0.0, 0.6, 0.0);
		level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(NEXT_RANGE), e -> SpiritFoes.foe(player, e, e == first)).stream()
			.sorted(java.util.Comparator.comparingDouble(player::distanceToSqr)).limit(3)
			.forEach(e -> SpiritJavelin.throwFrom(level, player, from, e.getBoundingBox().getCenter().subtract(from), Varro.VOLLEY_DAMAGE * 2.0F * scale, 1, e));
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 1.0F, 0.8F);
	}

	/** A blow is about to land on a keeper who is as one with their spirit. True cancels it. */
	private static boolean answered(LivingEntity entity, DamageSource source, float amount) {
		Run run = RUNS.get(entity.getUUID());
		if (run == null || amount <= 0.0F || !(entity.level() instanceof ServerLevel level)) {
			return false;
		}
		if (source.is(DamageTypeTags.IS_FALL)) {
			return true;
		}
		SpiritState state = Spirits.state(run.player);
		long now = level.getGameTime();
		if (state == null || now < run.counterAt || !(source.getDirectEntity() instanceof LivingEntity attacker) || attacker != source.getEntity()
			|| attacker == entity || SpiritFoes.ally(entity, attacker)) {
			return false;
		}
		run.counterAt = now + COUNTER_EVERY;
		Spirits.show(run.player, Poses.PARRY);
		level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0F, 1.2F);
		if (state.kind() == Spirits.GRIMSBY) {
			attacker.setInvulnerableTime(0);
			SpiritFoes.strike(level, entity, attacker, COUNTER_DAMAGE * Spirits.damageScale(state));
			return true;
		}
		SpiritFoes.shove(level, entity, attacker, 1.4);
		Staggered.stagger(entity, attacker, COUNTER_STAGGER);
		return false;
	}
}
