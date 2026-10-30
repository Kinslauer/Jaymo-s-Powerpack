package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.361) King's Valor, Valor's own crown (Jung's): "only gained and grown from defeating great foes aka bosses and kings",
 * and "most kings should spawn with valor and a rare few can have kings valor".
 * <ul>
 * <li><b>Waking it</b> ({@link #felled}): the first great foe (anything with a boss bar) felled at Tempered or past wakes
 * King's Valor, the soul colour shown in a burst; the {@value #COAT_FOES}rd opens King's Valor Coating. Each one grows it; one
 * who had King's Valor themselves counts {@value #KINGS_COUNT} times.</li>
 * <li><b>King's Valor</b> ({@link #release}, holding the Valor key): every weaker foe round you passes out (downed) for 10 seconds,
 * equals and the stronger are shaken a moment, casters caught in it hushed. The whole pool, and a long wait.</li>
 * <li><b>King's Valor Coating</b> ({@link #coatedBlow}): Armament in the soul colour. The weaker struck die outright, but
 * sneaking, fists and clubs only down them (No Mercy's rule). Two King's coatings meeting ({@link #clash}): a crack of
 * thunder, and every weaker one near faints or freezes, trembling.</li>
 * <li><b>Rulers</b> ({@link #rulerRoll}): about {@value #RULER_VALOR} of crowned rulers have Valor, about
 * {@value #RULER_KINGS} King's Valor. One with King's Valor doesn't run ({@link #standsAndFights}): they coat up and fight,
 * and at court they release King's Valor once when the throne room is attacked.</li>
 * </ul>
 */
public final class ValorKings {
	/** Great foes for King's Valor Coating; how many a King's Valor holder counts as. */
	public static final int COAT_FOES = 3, KINGS_COUNT = 3;
	/** King's Valor: its reach (base, per great foe, most), how long the weaker are out, how long the rest are shaken (ticks). */
	static final double REACH = 8.0, REACH_PER_FOE = 2.0, REACH_MAX = 24.0;
	static final int FAINT_TICKS = 200, SHAKEN_TICKS = 30;
	/** Its wait (ticks), and the share of the pool it needs (it takes all of it). */
	static final int WAIT = 6000;
	static final float POOL_NEEDED = 0.5F;
	/** A clash: how far the weaker feel it, how long they're out or frozen (ticks), how often between the same two (ticks). */
	static final double CLASH_REACH = 12.0;
	static final int CLASH_TICKS = 100, CLASH_EVERY = 60;
	/** Rulers: the share with Valor, the share with King's Valor, their Valor experience (least, most) and great foes (most). */
	static final float RULER_VALOR = 0.7F, RULER_KINGS = 0.1F, RULER_EXP_MIN = 120.0F, RULER_EXP_MAX = 520.0F;
	static final int RULER_FOES_MAX = 5;
	/** A ruler's court: how near the throne a blow is "in the throne room" (blocks); how far they look for whoever attacked. */
	static final double COURT_RANGE = 16.0, THREAT_REACH = 16.0;
	/** How long after a blow a ruler still fights (ticks). */
	static final int FIGHT_TICKS = 200;
	static final String RULER_ROLLED = "jaymoskingdoms_valor_ruler_rolled";

	/** When each holder's King's Valor is next ready (game time). */
	private static final Map<UUID, Long> READY = new HashMap<>();
	/** A clash's last moment for a pair (the smaller id first). */
	private static final Map<String, Long> CLASHED = new HashMap<>();
	/** Who was just struck dead by a King's coating, and the tick (Will's No Mercy lets it kill). */
	private static final Map<LivingEntity, Long> KILLED = new WeakHashMap<>();

	private ValorKings() {
	}

	static void register() {
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			READY.clear();
			CLASHED.clear();
			KILLED.clear();
		});
	}

	/** King's Valor is open to whoever has felled a great foe since Tempered. */
	public static boolean open(Valor.Data data) {
		return data.greatFoes() >= 1;
	}

	/** King's Valor Coating opens with the {@value #COAT_FOES}rd. */
	public static boolean coatOpen(Valor.Data data) {
		return data.greatFoes() >= COAT_FOES;
	}

	public static boolean hasKings(Entity e) {
		Valor.Data data = Valor.of(e);
		return data != null && open(data);
	}

	/** {@code holder} felled the great foe {@code dead}: from Tempered on, King's Valor wakes and grows. */
	static void felled(LivingEntity holder, LivingEntity dead) {
		Valor.Data data = Valor.of(holder);
		if (data == null || data.rank() < Valor.TEMPERED) {
			return;
		}
		int before = data.greatFoes(), after = before + (hasKings(dead) ? KINGS_COUNT : 1);
		holder.setAttached(Valor.VALOR, data.withGreatFoes(after));
		if (holder.level() instanceof ServerLevel level && (before < 1 || before < COAT_FOES && after >= COAT_FOES)) {
			reveal(level, holder, data.soulColor(), before >= 1);
		}
	}

	/** The soul colour shown: a ring and a column of it, thunder far off, a heartbeat. */
	static void reveal(ServerLevel level, LivingEntity holder, int color, boolean coat) {
		DustParticleOptions dust = new DustParticleOptions(color, 2.0F);
		for (int i = 0; i < 48; i++) {
			double a = Math.PI * 2 * i / 48;
			level.sendParticles(dust, holder.getX() + Math.cos(a) * 3.0, holder.getY(0.2), holder.getZ() + Math.sin(a) * 3.0, 2, 0.1, 0.1, 0.1, 0.0);
		}
		level.sendParticles(dust, holder.getX(), holder.getY(0.5), holder.getZ(), 80, 0.3, 1.6, 0.3, 0.0);
		level.playSound(null, holder.getX(), holder.getY(), holder.getZ(), SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 1.2F, coat ? 0.8F : 0.6F);
		level.playSound(null, holder.getX(), holder.getY(), holder.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.6F, 0.6F);
		if (holder instanceof ServerPlayer sp) {
			PowersCompat.shake(sp, 0.8F);
		}
	}

	// ---- King's Valor -----------------------------------------------------------------------------------------------

	/** Holding the Valor key: King's Valor released, if it's open, ready and the pool is deep enough; it takes the whole pool. */
	static void release(ServerPlayer sp, Valor.Data data) {
		if (!open(data)) {
			sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.valor.kings_closed").withStyle(ChatFormatting.GRAY));
			return;
		}
		ServerLevel level = sp.level();
		long now = level.getGameTime();
		long ready = READY.getOrDefault(sp.getUUID(), 0L);
		if (ready > now) {
			sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.valor.kings_wait", (ready - now + 19) / 20).withStyle(ChatFormatting.GRAY));
			return;
		}
		Valor.Live live = Valor.live(sp);
		if (now < live.spentUntil || live.pool < Valor.poolMax(data) * POOL_NEEDED) {
			sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.valor.kings_pool").withStyle(ChatFormatting.GRAY));
			return;
		}
		live.pool = 0.0F;
		READY.put(sp.getUUID(), now + WAIT);
		unleash(level, sp, data, e -> Will.foe(sp, e) || Will.rival(sp, e));
		Valor.sync(sp, data, live);
	}

	/** King's Valor from {@code source}: the weaker among {@code hits} round them pass out, the rest are shaken. */
	static void unleash(ServerLevel level, LivingEntity source, Valor.Data data, Predicate<LivingEntity> hits) {
		double reach = Math.min(REACH_MAX, REACH + REACH_PER_FOE * data.greatFoes());
		DustParticleOptions dust = new DustParticleOptions(data.soulColor(), 1.6F);
		for (double r = 1.5; r <= reach; r += 1.5) {
			int n = (int) (r * 6);
			for (int i = 0; i < n; i++) {
				double a = Math.PI * 2 * i / n;
				level.sendParticles(dust, source.getX() + Math.cos(a) * r, source.getY(0.3), source.getZ() + Math.sin(a) * r, 1, 0.05, 0.2, 0.05, 0.0);
			}
		}
		level.playSound(null, source.getX(), source.getY(), source.getZ(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 2.0F, 0.5F);
		level.playSound(null, source.getX(), source.getY(), source.getZ(), SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 1.5F, 0.7F);
		level.playSound(null, source.getX(), source.getY(), source.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 2.0F, 0.5F);
		AABB box = source.getBoundingBox().inflate(reach, 4.0, reach);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != source && e.isAlive() && e.distanceToSqr(source) <= reach * reach
			&& hits.test(e))) {
			if (Will.caster(e)) {
				Will.hush(level, e);
			}
			if (e instanceof ServerPlayer other) {
				PowersCompat.shake(other, 0.9F);
			}
			if (weakerThan(source, e) && PowersCompat.knockOut(level, e, FAINT_TICKS)) {
				continue;
			}
			if (e instanceof Mob mob) {
				Will.daze(mob, SHAKEN_TICKS);
			} else {
				e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SHAKEN_TICKS, 4), source);
			}
		}
		if (source instanceof ServerPlayer sp) {
			PowersCompat.shake(sp, 0.5F);
		}
	}

	/** Whether {@code e} is weaker than {@code by}: Will's rule for a player; for a villager, less health or a lowly kind. */
	static boolean weakerThan(LivingEntity by, LivingEntity e) {
		if (by instanceof Player p) {
			return Will.weaker(p, e);
		}
		if (BossBars.boss(e) || e == by) {
			return false;
		}
		return e.getMaxHealth() < by.getMaxHealth() || !(e instanceof Player) && Will.kindTier(e) < 2;
	}

	// ---- King's Valor Coating ----------------------------------------------------------------------------------------

	/**
	 * A coated blow from {@code by} on {@code target} ({@link Valor#dealt}): with the King's coating up, two coatings clash;
	 * a weaker one struck dies outright, unless {@code by} sneaks or strikes with fists or a club.
	 */
	static float coatedBlow(LivingEntity target, DamageSource source, @Nullable Entity by, float amount) {
		if (by == null || (Valor.state(by) & Valor.KINGS) == 0 || !(by instanceof LivingEntity striker) || !(target.level() instanceof ServerLevel level)) {
			return amount;
		}
		if ((Valor.state(target) & Valor.KINGS) != 0) {
			clash(level, striker, target);
			return amount;
		}
		if (!weakerThan(striker, target)) {
			return amount;
		}
		if (striker instanceof Player p && (p.isShiftKeyDown() || source.getDirectEntity() == p && Will.gentle(p.getMainHandItem()))) {
			return amount;
		}
		KILLED.put(target, level.getGameTime());
		Valor.Data data = Valor.of(striker);
		if (data != null) {
			level.sendParticles(new DustParticleOptions(data.soulColor(), 1.5F), target.getX(), target.getY(0.6), target.getZ(), 20, 0.3, 0.4, 0.3, 0.0);
		}
		return Math.max(amount, target.getHealth() + target.getAbsorptionAmount() + 20.0F);
	}

	/** (for Will's No Mercy, which Jaymo's Powers asks) Whether {@code victim} was just struck dead by a King's coating: it dies, not downed. */
	public static boolean killed(LivingEntity victim) {
		Long at = KILLED.get(victim);
		return at != null && at == victim.level().getGameTime();
	}

	/** Two King's coatings meet: thunder, sparks of both colours, and the weaker round them faint or freeze, trembling. */
	static void clash(ServerLevel level, LivingEntity a, LivingEntity b) {
		long now = level.getGameTime();
		String key = a.getUUID().compareTo(b.getUUID()) < 0 ? a.getUUID() + "|" + b.getUUID() : b.getUUID() + "|" + a.getUUID();
		if (now - CLASHED.getOrDefault(key, -CLASH_EVERY * 2L) < CLASH_EVERY) {
			return;
		}
		CLASHED.put(key, now);
		Valor.Data da = Valor.of(a), db = Valor.of(b);
		Vec3 mid = a.position().add(b.position()).scale(0.5).add(0.0, 1.2, 0.0);
		if (da != null) {
			level.sendParticles(new DustParticleOptions(da.soulColor(), 1.8F), mid.x, mid.y, mid.z, 30, 0.4, 0.4, 0.4, 0.0);
		}
		if (db != null) {
			level.sendParticles(new DustParticleOptions(db.soulColor(), 1.8F), mid.x, mid.y, mid.z, 30, 0.4, 0.4, 0.4, 0.0);
		}
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, mid.x, mid.y, mid.z, 40, 0.5, 0.5, 0.5, 0.4);
		level.playSound(null, mid.x, mid.y, mid.z, SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 2.0F, 1.1F);
		level.playSound(null, mid.x, mid.y, mid.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 1.0F, 0.5F);
		RandomSource r = level.getRandom();
		AABB box = new AABB(mid, mid).inflate(CLASH_REACH, 4.0, CLASH_REACH);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != a && e != b && e.isAlive()
			&& weakerThan(a, e) && weakerThan(b, e))) {
			if (e instanceof ServerPlayer p) {
				PowersCompat.shake(p, 1.0F);
			}
			if (r.nextBoolean() && PowersCompat.knockOut(level, e, CLASH_TICKS)) {
				continue;
			}
			if (e instanceof Mob mob) {
				Will.daze(mob, CLASH_TICKS);
			} else {
				e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, CLASH_TICKS, 9), a);
				e.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, CLASH_TICKS, 2), a);
			}
		}
	}

	// ---- rulers ------------------------------------------------------------------------------------------------------

	/** A crowned ruler is weighed once: most have Valor, a rare few King's Valor. */
	static void rulerRoll(ServerLevel level, PathfinderMob ruler) {
		if (ruler.entityTags().contains(RULER_ROLLED)) {
			return;
		}
		ruler.addTag(RULER_ROLLED);
		RandomSource r = level.getRandom();
		float roll = r.nextFloat();
		if (roll >= RULER_VALOR) {
			return;
		}
		Valor.Data data = Valor.of(ruler);
		float exp = RULER_EXP_MIN + r.nextFloat() * (RULER_EXP_MAX - RULER_EXP_MIN);
		if (data == null) {
			data = ValorFolk.born(level, ruler, exp);
		} else if (data.exp() < exp) {
			data = data.withExp(exp);
		}
		if (roll < RULER_KINGS) {
			data = data.withGreatFoes(1 + r.nextInt(RULER_FOES_MAX));
		}
		ruler.setAttached(Valor.VALOR, data);
		ValorFolk.track(ruler);
	}

	/**
	 * (from {@link Rulers}) A ruler with King's Valor, threatened, doesn't run: they coat up and fight whoever came at them,
	 * and in their throne room they release King's Valor once. True while they're fighting.
	 */
	static boolean standsAndFights(ServerLevel level, PathfinderMob ruler, Faction f) {
		Valor.Data data = Valor.of(ruler);
		if (data == null || !open(data) || Downed.isDowned(ruler)) {
			return false;
		}
		LivingEntity foe = ruler.getLastHurtByMob();
		if (foe == null || !foe.isAlive() || ruler.tickCount - ruler.getLastHurtByMobTimestamp() > FIGHT_TICKS || foe.distanceTo(ruler) > THREAT_REACH) {
			foe = null;
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, ruler.getBoundingBox().inflate(8.0), e -> e.isAlive() && e instanceof Enemy)) {
				foe = e;
				break;
			}
		}
		if (foe == null) {
			return false;
		}
		ValorFolk.fighting(ruler);
		Castle.Keep keep = Factions.keep(level.getServer(), f.id());
		long now = level.getGameTime();
		if (keep != null && ruler.blockPosition().closerThan(keep.throne(), COURT_RANGE) && READY.getOrDefault(ruler.getUUID(), 0L) <= now) {
			READY.put(ruler.getUUID(), now + WAIT);
			LivingEntity attacker = foe;
			unleash(level, ruler, data, e -> e == attacker || e instanceof Enemy
				|| e instanceof ServerPlayer p && (Reputation.get(p, f.id()) <= Reputation.HOSTILE || p == attacker));
		}
		ValorFolk.strike(level, ruler, foe);
		return true;
	}
}
