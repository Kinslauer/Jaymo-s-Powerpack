package com.jaymoskingdoms.spirits;

import com.jaymoskingdoms.Arcana;
import com.jaymoskingdoms.Mana;
import com.jaymoskingdoms.PowersCompat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.241) Grimsby the Weary: a grumpy old wizard of four hundred years' study who has been made a fly swatter for a child,
 * and is secretly fond of them. Andy: "grumpy old wizards that are tired of looking after said young child ... a stand that
 * forms into different weapons". Blade and parry; steel-blue-white, long and bony.
 * <ul>
 * <li><b>Blade forms</b> ({@link GrimsbyBlades}): sword, greatsword, spear, axe, hammer, each a three-tap combo.</li>
 * <li><b>Flurry</b> (level 3): six fast blows from both hands over twelve ticks. <b>Riposte</b> (5): for a moment the next
 * melee blow is turned aside and answered. <b>Guard break</b> (7): a heavy slam that knocks a shield aside and stuns.
 * <b>Finisher</b> (9): one cut for a helpless foe.</li>
 * <li>On its own (bond 20 and up): when a blow would land from behind, a parry the keeper never saw, and a complaint about
 * it ({@link #hurt}). Advice in battle comes from the core ({@link Spirits#advice}).</li>
 * </ul>
 * The folk's spirits cast flurry and riposte through {@link Move#castFrom}.
 */
public final class Grimsby extends Spirit {
	/** Flurry: this many blows, this many ticks apart (twelve ticks in all), of this much damage each; the last one knocks back. */
	static final int FLURRY_BLOWS = 6, FLURRY_GAP = 2;
	static final float FLURRY_DAMAGE = 3.0F, FLURRY_KNOCK = 0.9F;
	static final double FLURRY_REACH = 4.5, FLURRY_LOSE = 6.5;
	/** Riposte: the window (ticks) in which the next melee blow is turned aside, and the cut it is answered with. */
	static final int RIPOSTE_TICKS = 20;
	static final float RIPOSTE_DAMAGE = 8.0F;
	/** Guard break: damage, reach, and how long the foe is stunned (ticks; slowness 5 and no target). */
	static final float BREAK_DAMAGE = 10.0F;
	static final double BREAK_REACH = 4.0;
	static final int STUN_TICKS = 30;
	/** Finisher: damage, and how near the helpless foe must be. */
	static final float FINISH_DAMAGE = 30.0F;
	static final double FINISH_REACH = 4.0;
	/** The unseen parry: ticks between two, mana it costs, and how far to the side of the keeper's look a blow must come (dot below this). */
	static final int PARRY_EVERY = 400;
	static final float PARRY_COST = 8.0F;
	static final double UNSEEN_DOT = 0.3;
	/** How hard a parry throws the attacker back. */
	static final double PARRY_SHOVE = 0.5;

	/** A flurry under way: who strikes, who is struck, blows left and when the next lands. */
	private static final class Flurrying {
		final LivingEntity caster;
		final UUID target;
		final float scale;
		int left = FLURRY_BLOWS;
		long next;

		Flurrying(LivingEntity caster, UUID target, float scale, long next) {
			this.caster = caster;
			this.target = target;
			this.scale = scale;
			this.next = next;
		}
	}

	/** A riposte waiting: until when, and how hard the answer is. */
	private record Riposting(LivingEntity holder, long until, float scale) {
	}

	/** A foe stunned by a guard break: until when. */
	private record Stunned(Mob mob, long until) {
	}

	private static final Map<UUID, Flurrying> FLURRIES = new HashMap<>();
	private static final Map<UUID, Riposting> RIPOSTES = new HashMap<>();
	private static final Map<UUID, Stunned> STUNS = new HashMap<>();

	/** Its own moves. */
	public static final List<Move> MOVES = new ArrayList<>();

	static {
		MOVES.addAll(GrimsbyBlades.moves());
		MOVES.add(new FlurryMove());
		MOVES.add(new RiposteMove());
		MOVES.add(new GuardBreakMove());
		MOVES.add(new FinishMove());
	}

	public Grimsby() {
		super(Spirits.GRIMSBY, "grimsby", 0xFF9AB8FF, 1.0F);
	}

	@Override
	public List<Move> moves() {
		return MOVES;
	}

	/** Adds its moves to {@link Moves} and hooks the game events its moves need. */
	public static void register() {
		for (Move move : MOVES) {
			Moves.register(move);
		}
		GrimsbyBlades.register();
		ServerTickEvents.END_SERVER_TICK.register(server -> tickAll());
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !riposte(entity, source, amount));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			FLURRIES.clear();
			RIPOSTES.clear();
			STUNS.clear();
		});
	}

	// ---- on its own -------------------------------------------------------------------------------------------------

	/**
	 * A blow from behind (the keeper is not looking at the attacker) is parried without being asked, once in
	 * {@link #PARRY_EVERY} ticks, from bond 20: the hit is cancelled, the spirit complains.
	 */
	@Override
	public boolean hurt(ServerPlayer player, SpiritState state, DamageSource source, float amount) {
		if (amount <= 0.0F || state.bond() < Spirits.ACT_BOND || RIPOSTES.containsKey(player.getUUID())) {
			return false;
		}
		if (!(source.getDirectEntity() instanceof LivingEntity attacker) || attacker != source.getEntity() || attacker == player) {
			return false;
		}
		long now = player.level().getGameTime();
		if (!cooled(player, "parry", now)) {
			return false;
		}
		Vec3 to = new Vec3(attacker.getX() - player.getX(), 0.0, attacker.getZ() - player.getZ());
		Vec3 look = new Vec3(player.getLookAngle().x, 0.0, player.getLookAngle().z);
		if (to.lengthSqr() > 1.0E-4 && look.lengthSqr() > 1.0E-4 && to.normalize().dot(look.normalize()) >= UNSEEN_DOT) {
			return false;
		}
		if (!Spirits.act(player, Spirits.GRIMSBY, Spirits.FLASH_PARRY, PARRY_COST)) {
			return false;
		}
		cool(player, "parry", now, PARRY_EVERY);
		Spirits.show(player, Poses.PARRY);
		clang((ServerLevel)player.level(), player, attacker);
		if (SpiritFoes.foe(player, attacker, false)) {
			SpiritFoes.shove((ServerLevel)player.level(), player, attacker, PARRY_SHOVE);
		}
		return true;
	}

	private static void clang(ServerLevel level, LivingEntity holder, @Nullable LivingEntity attacker) {
		Vec3 at = attacker == null ? holder.getEyePosition() : holder.position().add(attacker.position()).scale(0.5).add(0.0, 1.0, 0.0);
		level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 12, 0.3, 0.3, 0.3, 0.3);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 8, 0.2, 0.2, 0.2, 0.2);
		Mana.sendSeen(level, GrimsbyBlades.EDGE, at.x, at.y, at.z, 10, 0.3, 0.3, 0.3, 0.02);
		level.playSound(null, holder.getX(), holder.getY(), holder.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0F, 1.3F);
		level.playSound(null, holder.getX(), holder.getY(), holder.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.25F, 1.8F);
	}

	// ---- riposte ----------------------------------------------------------------------------------------------------

	/** A blow is about to land on {@code entity}: turn it aside if a riposte is waiting. True cancels it. */
	private static boolean riposte(LivingEntity entity, DamageSource source, float amount) {
		Riposting waiting = RIPOSTES.get(entity.getUUID());
		if (waiting == null || amount <= 0.0F || !(entity.level() instanceof ServerLevel level)) {
			return false;
		}
		if (level.getGameTime() > waiting.until()) {
			RIPOSTES.remove(entity.getUUID());
			return false;
		}
		if (!(source.getDirectEntity() instanceof LivingEntity attacker) || attacker != source.getEntity() || attacker == entity) {
			return false;
		}
		RIPOSTES.remove(entity.getUUID());
		if (entity instanceof ServerPlayer player) {
			Spirits.show(player, Poses.PARRY);
		}
		clang(level, entity, attacker);
		if (!SpiritFoes.ally(entity, attacker)) {
			attacker.setInvulnerableTime(0);
			SpiritFoes.strike(level, entity, attacker, RIPOSTE_DAMAGE * waiting.scale());
		}
		return true;
	}

	// ---- every tick -------------------------------------------------------------------------------------------------

	private static void tickAll() {
		for (Iterator<Flurrying> it = FLURRIES.values().iterator(); it.hasNext();) {
			Flurrying f = it.next();
			if (!f.caster.isAlive() || !(f.caster.level() instanceof ServerLevel level)) {
				it.remove();
				continue;
			}
			if (level.getGameTime() < f.next) {
				continue;
			}
			if (!(level.getEntity(f.target) instanceof LivingEntity target) || !target.isAlive() || target.distanceTo(f.caster) > FLURRY_LOSE) {
				it.remove();
				continue;
			}
			blow(level, f, target);
			f.left--;
			f.next += FLURRY_GAP;
			if (f.left <= 0) {
				it.remove();
			}
		}
		for (Iterator<Riposting> it = RIPOSTES.values().iterator(); it.hasNext();) {
			Riposting r = it.next();
			if (!r.holder().isAlive() || r.holder().level().getGameTime() > r.until()) {
				it.remove();
			}
		}
		for (Iterator<Stunned> it = STUNS.values().iterator(); it.hasNext();) {
			Stunned s = it.next();
			if (!s.mob().isAlive() || s.mob().level().getGameTime() > s.until()) {
				it.remove();
			} else {
				s.mob().setTarget(null);
			}
		}
	}

	/** One of the flurry's blows, from the left hand or the right. */
	private static void blow(ServerLevel level, Flurrying f, LivingEntity target) {
		boolean last = f.left == 1;
		boolean left = (f.left & 1) == 0;
		Vec3 centre = target.getBoundingBox().getCenter();
		Vec3 side = new Vec3(-(centre.z - f.caster.getZ()), 0.0, centre.x - f.caster.getX());
		side = side.lengthSqr() < 1.0E-4 ? Vec3.ZERO : side.normalize().scale(left ? 0.6 : -0.6);
		Vec3 from = f.caster.getEyePosition().add(side).add(0.0, -0.4, 0.0);
		SpiritFoes.line(level, ParticleTypes.CRIT, from, centre.add(side.scale(-0.4)), 5);
		target.setInvulnerableTime(0);
		SpiritFoes.strike(level, f.caster, target, FLURRY_DAMAGE * f.scale);
		if (last) {
			SpiritFoes.shove(level, f.caster, target, FLURRY_KNOCK);
			level.sendParticles(ParticleTypes.SWEEP_ATTACK, centre.x, centre.y, centre.z, 1, 0.0, 0.0, 0.0, 0.0);
		}
		level.playSound(null, f.caster.getX(), f.caster.getY(), f.caster.getZ(), last ? SoundEvents.PLAYER_ATTACK_STRONG : SoundEvents.PLAYER_ATTACK_SWEEP,
			SoundSource.PLAYERS, 0.7F, 1.0F + (left ? 0.0F : 0.15F));
	}

	/** Starts a flurry of {@code caster}'s at {@code target}; false if one is already under way. */
	private static boolean startFlurry(LivingEntity caster, LivingEntity target, float scale) {
		if (FLURRIES.containsKey(caster.getUUID()) || !target.isAlive()) {
			return false;
		}
		FLURRIES.put(caster.getUUID(), new Flurrying(caster, target.getUUID(), scale, caster.level().getGameTime()));
		return true;
	}

	private static boolean startRiposte(LivingEntity holder, float scale) {
		RIPOSTES.put(holder.getUUID(), new Riposting(holder, holder.level().getGameTime() + RIPOSTE_TICKS, scale));
		if (holder.level() instanceof ServerLevel level) {
			Vec3 p = holder.position();
			Mana.sendSeen(level, GrimsbyBlades.EDGE, p.x, p.y + 1.1, p.z, 10, 0.5, 0.6, 0.5, 0.01);
			level.playSound(null, p.x, p.y, p.z, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.6F, 1.6F);
		}
		return true;
	}

	private static void said(ServerPlayer player) {
		if (player.getRandom().nextFloat() < 0.15F) {
			SpiritVoice.say(player, "move");
		}
	}

	// ---- the moves --------------------------------------------------------------------------------------------------

	/** Flurry: six fast blows from both hands at the foe in front. */
	private static final class FlurryMove implements Move {
		@Override
		public String id() {
			return "flurry";
		}

		@Override
		public float cost() {
			return 14.0F;
		}

		@Override
		public int cooldown() {
			return 100;
		}

		@Override
		public int level() {
			return 3;
		}

		@Override
		public int pose() {
			return Poses.FLURRY;
		}

		@Override
		public boolean cast(ServerPlayer player, SpiritState state, int charge) {
			List<LivingEntity> foes = SpiritFoes.cone(player, FLURRY_REACH, 45.0, true);
			if (foes.isEmpty() || !startFlurry(player, foes.get(0), Spirits.damageScale(state))) {
				return false;
			}
			said(player);
			return true;
		}

		@Override
		public boolean castFrom(LivingEntity caster, LivingEntity target, SpiritState state) {
			return startFlurry(caster, target, Spirits.damageScale(state));
		}
	}

	/** Riposte: for a moment the next melee blow is turned aside and answered with a cut. */
	private static final class RiposteMove implements Move {
		@Override
		public String id() {
			return "riposte";
		}

		@Override
		public float cost() {
			return 10.0F;
		}

		@Override
		public int cooldown() {
			return 160;
		}

		@Override
		public int level() {
			return 5;
		}

		@Override
		public int pose() {
			return Poses.PARRY;
		}

		@Override
		public int intent() {
			return Mana.WARD;
		}

		@Override
		public boolean cast(ServerPlayer player, SpiritState state, int charge) {
			said(player);
			return startRiposte(player, Spirits.damageScale(state));
		}

		@Override
		public boolean castFrom(LivingEntity caster, LivingEntity target, SpiritState state) {
			return startRiposte(caster, Spirits.damageScale(state));
		}
	}

	/** Guard break: a heavy two-handed slam that knocks a shield aside and stuns. */
	private static final class GuardBreakMove implements Move {
		@Override
		public String id() {
			return "guard_break";
		}

		@Override
		public float cost() {
			return 16.0F;
		}

		@Override
		public int cooldown() {
			return 200;
		}

		@Override
		public int level() {
			return 7;
		}

		@Override
		public int pose() {
			return Poses.SLAM;
		}

		@Override
		public boolean cast(ServerPlayer player, SpiritState state, int charge) {
			List<LivingEntity> foes = SpiritFoes.cone(player, BREAK_REACH, 40.0, true);
			if (foes.isEmpty()) {
				return false;
			}
			ServerLevel level = (ServerLevel)player.level();
			LivingEntity foe = foes.get(0);
			SpiritFoes.breakShield(level, foe, GrimsbyBlades.SHIELD_SECONDS);
			foe.setInvulnerableTime(0);
			SpiritFoes.strike(level, player, foe, BREAK_DAMAGE * Spirits.damageScale(state));
			foe.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, STUN_TICKS, 4), player);
			if (foe instanceof Mob mob) {
				mob.setTarget(null);
				STUNS.put(mob.getUUID(), new Stunned(mob, level.getGameTime() + STUN_TICKS));
			}
			Vec3 at = foe.position();
			level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.6, at.z, 1, 0.0, 0.0, 0.0, 0.0);
			Mana.sendSeen(level, GrimsbyBlades.EDGE, at.x, at.y + 1.0, at.z, 20, 0.5, 0.6, 0.5, 0.05);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 1.0F, 0.7F);
			PowersCompat.shake(player, 0.5F);
			said(player);
			return true;
		}
	}

	/** Finisher: one cut for a helpless foe. */
	private static final class FinishMove implements Move {
		@Override
		public String id() {
			return "finish";
		}

		@Override
		public float cost() {
			return 20.0F;
		}

		@Override
		public int cooldown() {
			return 300;
		}

		@Override
		public int level() {
			return 9;
		}

		@Override
		public int pose() {
			return Poses.FINISH;
		}

		@Override
		public boolean cast(ServerPlayer player, SpiritState state, int charge) {
			LivingEntity foe = Arcana.sighted(player);
			if (foe == null || foe.distanceTo(player) - foe.getBbWidth() * 0.5 > FINISH_REACH || !SpiritFoes.foe(player, foe, true) || !PowersCompat.helpless(foe)) {
				return false;
			}
			ServerLevel level = (ServerLevel)player.level();
			foe.setInvulnerableTime(0);
			SpiritFoes.strike(level, player, foe, FINISH_DAMAGE * Spirits.damageScale(state));
			Vec3 at = foe.getBoundingBox().getCenter();
			SpiritFoes.line(level, ParticleTypes.CRIT, at.add(-0.8, 0.8, 0.0), at.add(0.8, -0.8, 0.0), 8);
			level.sendParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0F, 0.7F);
			said(player);
			return true;
		}
	}
}
