package com.jaymoskingdoms.spirits;

import com.jaymoskingdoms.Arcana;
import com.jaymoskingdoms.Mana;
import com.jaymoskingdoms.PowersCompat;
import java.util.ArrayList;
import java.util.HashMap;
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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.241) Old Varro, the Siege-Mage: a gruff war veteran who barks orders and is proud when he is listened to. Andy: "a
 * collected energy javelin attack". Javelins and bombards; amber, a gauntlet as tall as you.
 * <ul>
 * <li><b>Mana javelin</b> (level 1, hold to charge): a piercing bolt, {@link SpiritJavelin}. <b>Mark</b> (2): the mob in
 * sight glows and takes a third more from the keeper. <b>Volley</b> (3): a fan of five small javelins. <b>Bombard</b> (5):
 * a slow heavy orb that arcs and bursts. <b>Spear wall</b> (7): a line of javelins rising from the ground ahead.</li>
 * <li>On its own (bond 20 and up): a javelin at whoever is drawing a bow on the keeper ({@link #aimedAt}). Advice in battle
 * comes from the core ({@link Spirits#advice}), barked.</li>
 * </ul>
 * The folk's spirits cast javelin and volley through {@link Move#castFrom}. The entity needs a no-op renderer on the client.
 */
public final class Varro extends Spirit {
	/** Javelin: damage is {@code BASE + charge ticks x PER_CHARGE}, up to {@code MAX}; how far past the eye it leaves; pierce count. */
	static final float JAVELIN_BASE = 8.0F, JAVELIN_PER_CHARGE = 0.25F, JAVELIN_MAX = 20.0F;
	static final int JAVELIN_PIERCE = 3;
	/** The charge (ticks) at which the throw gets a shake and a louder sound. */
	static final int FULL_CHARGE = 48;
	/** Volley: javelins in the fan, their damage, the fan's half-width (degrees), and their pierce. */
	static final int VOLLEY_COUNT = 5, VOLLEY_PIERCE = 2;
	static final float VOLLEY_DAMAGE = 4.0F, VOLLEY_SPREAD = 15.0F;
	/** Bombard: the orb's damage, and how much it is lobbed upward. */
	static final float BOMBARD_DAMAGE = 12.0F;
	static final double BOMBARD_LIFT = 0.18;
	/** Spear wall: javelins in the line (an odd number), how far ahead, damage, and the launch. */
	static final int WALL_WIDTH = 7;
	static final double WALL_AHEAD = 3.0, WALL_LAUNCH = 0.6;
	static final float WALL_DAMAGE = 8.0F;
	/** Mark: how long it lasts (ticks), and the extra share of every blow of the keeper's (a third of a half, plus: 1.3 times in all). */
	static final int MARK_TICKS = 200;
	static final float MARK_BONUS = 0.3F;
	static final double MARK_REACH = 32.0;
	/** The autonomous javelin: ticks between two, mana it costs, its damage and how far it will fire. */
	static final int JAVELIN_EVERY = 300;
	static final float JAVELIN_AUTO_COST = 12.0F, JAVELIN_AUTO_DAMAGE = 10.0F;
	static final double ARCHER_RANGE = 24.0;
	/** A folk's javelin hits this hard before the spirit's level counts. */
	static final float FOLK_JAVELIN = 10.0F;

	/** A mark: whose, and until when. */
	private record Mark(UUID owner, long until) {
	}

	/** Marked things by id. */
	private static final Map<UUID, Mark> MARKS = new HashMap<>();
	/** Set while the mark's extra blow is landing (so it does not mark again). */
	private static boolean bonusing;

	/** Its own moves. */
	public static final List<Move> MOVES = new ArrayList<>();

	static {
		MOVES.add(new JavelinMove());
		MOVES.add(new MarkMove());
		MOVES.add(new VolleyMove());
		MOVES.add(new BombardMove());
		MOVES.add(new SpearWallMove());
	}

	public Varro() {
		super(Spirits.VARRO, "varro", 0xFFFFB347, 2.4F);
	}

	@Override
	public List<Move> moves() {
		return MOVES;
	}

	/** Adds its moves to {@link Moves}, registers the javelin entity and hooks the game events its moves need. */
	public static void register() {
		SpiritJavelin.register();
		for (Move move : MOVES) {
			Moves.register(move);
		}
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 20 == 0) {
				MARKS.values().removeIf(m -> m.until() < server.overworld().getGameTime());
			}
		});
		// the keeper's blows on a marked thing land a third again
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (bonusing || taken <= 0.0F || !entity.isAlive() || MARKS.isEmpty()) {
				return;
			}
			Mark mark = MARKS.get(entity.getUUID());
			if (mark == null || !(source.getEntity() instanceof ServerPlayer keeper) || !keeper.getUUID().equals(mark.owner())
				|| !(entity.level() instanceof ServerLevel level)) {
				return;
			}
			if (level.getGameTime() > mark.until()) {
				MARKS.remove(entity.getUUID());
				return;
			}
			bonusing = true;
			try {
				// inside the hit window a harder blow lands only its excess (the window remembers the blow before armour,
				// so the bonus is measured from that), and the immunity the hit gave is left alone
				SpiritFoes.strike(level, keeper, entity, base + base * MARK_BONUS);
				level.sendParticles(ParticleTypes.CRIT, entity.getX(), entity.getY(0.6), entity.getZ(), 5, 0.25, 0.3, 0.25, 0.2);
			} finally {
				bonusing = false;
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> MARKS.clear());
	}

	// ---- on its own -------------------------------------------------------------------------------------------------

	/** An archer has the keeper in its sights: a javelin at it, once in {@link #JAVELIN_EVERY} ticks, from bond 20. */
	@Override
	public void aimedAt(ServerPlayer player, SpiritState state, LivingEntity archer) {
		long now = player.level().getGameTime();
		if (!cooled(player, "javelin", now) || archer.distanceTo(player) > ARCHER_RANGE || !player.hasLineOfSight(archer)) {
			return;
		}
		if (!Spirits.act(player, Spirits.VARRO, Spirits.FLASH_ACT, JAVELIN_AUTO_COST)) {
			return;
		}
		cool(player, "javelin", now, JAVELIN_EVERY);
		Spirits.show(player, Poses.JAVELIN);
		hurl(player, archer, JAVELIN_AUTO_DAMAGE * Spirits.damageScale(state), 0);
	}

	// ---- throwing ---------------------------------------------------------------------------------------------------

	/** A javelin of {@code damage} from {@code caster} at {@code target}. */
	private static void hurl(LivingEntity caster, LivingEntity target, float damage, int charge) {
		ServerLevel level = (ServerLevel)caster.level();
		Vec3 from = muzzle(caster);
		Vec3 dir = target.getBoundingBox().getCenter().subtract(from);
		SpiritJavelin.throwFrom(level, caster, from, dir, damage, JAVELIN_PIERCE, target);
		sound(level, caster, charge);
	}

	private static Vec3 muzzle(LivingEntity caster) {
		return caster.getEyePosition().add(caster.getLookAngle().scale(0.8)).add(0.0, -0.2, 0.0);
	}

	private static void sound(ServerLevel level, LivingEntity caster, int charge) {
		level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 1.0F, 1.3F - Math.min(charge, FULL_CHARGE) * 0.005F);
		Mana.sendSeen(level, Mana.MOTE, caster.getX(), caster.getEyeY(), caster.getZ(), 8, 0.3, 0.3, 0.3, 0.05);
	}

	/** A fan of small javelins from {@code caster} along {@code centre}. */
	private static void fan(LivingEntity caster, Vec3 centre, float damage, @Nullable LivingEntity aim) {
		ServerLevel level = (ServerLevel)caster.level();
		Vec3 from = muzzle(caster);
		for (int i = 0; i < VOLLEY_COUNT; i++) {
			double t = VOLLEY_COUNT == 1 ? 0.0 : (i / (double)(VOLLEY_COUNT - 1)) * 2.0 - 1.0;
			Vec3 dir = centre.yRot((float)Math.toRadians(t * VOLLEY_SPREAD));
			SpiritJavelin.throwFrom(level, caster, from, dir, damage, VOLLEY_PIERCE, aim);
		}
		sound(level, caster, 0);
	}

	// ---- the moves --------------------------------------------------------------------------------------------------

	private static void said(ServerPlayer player) {
		if (player.getRandom().nextFloat() < 0.15F) {
			SpiritVoice.say(player, "move");
		}
	}

	/** The mana javelin: gather it, hold, let go. */
	private static final class JavelinMove implements Move {
		@Override
		public String id() {
			return "javelin";
		}

		@Override
		public float cost() {
			return 12.0F;
		}

		@Override
		public int cooldown() {
			return 40;
		}

		@Override
		public int level() {
			return 1;
		}

		@Override
		public boolean charges() {
			return true;
		}

		@Override
		public int pose() {
			return Poses.JAVELIN;
		}

		@Override
		public boolean cast(ServerPlayer player, SpiritState state, int charge) {
			ServerLevel level = (ServerLevel)player.level();
			float damage = Math.min(JAVELIN_MAX, JAVELIN_BASE + Math.max(0, charge) * JAVELIN_PER_CHARGE) * Spirits.damageScale(state);
			LivingEntity aim = Arcana.sighted(player);
			Vec3 from = muzzle(player);
			Vec3 dir = aim != null && SpiritFoes.foe(player, aim, true) ? aim.getBoundingBox().getCenter().subtract(from) : player.getLookAngle();
			SpiritJavelin.throwFrom(level, player, from, dir, damage, JAVELIN_PIERCE, aim);
			sound(level, player, charge);
			if (charge >= FULL_CHARGE) {
				PowersCompat.shake(player, 0.3F);
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.6F, 1.8F);
			}
			said(player);
			return true;
		}

		@Override
		public boolean castFrom(LivingEntity caster, LivingEntity target, SpiritState state) {
			if (!target.isAlive()) {
				return false;
			}
			hurl(caster, target, FOLK_JAVELIN * Spirits.damageScale(state), 0);
			return true;
		}
	}

	/** Mark target: the mob in sight glows and takes a third more from its keeper. */
	private static final class MarkMove implements Move {
		@Override
		public String id() {
			return "mark";
		}

		@Override
		public float cost() {
			return 6.0F;
		}

		@Override
		public int cooldown() {
			return 60;
		}

		@Override
		public int level() {
			return 2;
		}

		@Override
		public int pose() {
			return Poses.MARK;
		}

		@Override
		public int intent() {
			return Mana.WILL;
		}

		@Override
		public boolean cast(ServerPlayer player, SpiritState state, int charge) {
			LivingEntity target = Arcana.sighted(player);
			if (target == null || target.distanceTo(player) > MARK_REACH || !SpiritFoes.foe(player, target, true)) {
				return false;
			}
			ServerLevel level = (ServerLevel)player.level();
			MARKS.values().removeIf(m -> m.owner().equals(player.getUUID()));
			MARKS.put(target.getUUID(), new Mark(player.getUUID(), level.getGameTime() + MARK_TICKS));
			target.addEffect(new MobEffectInstance(MobEffects.GLOWING, MARK_TICKS, 0, false, false), player);
			Vec3 at = target.getBoundingBox().getCenter();
			SpiritFoes.line(level, ParticleTypes.END_ROD, muzzle(player), at, 10);
			level.sendParticles(SpiritJavelin.AMBER, at.x, at.y + target.getBbHeight() * 0.6, at.z, 14, 0.3, 0.3, 0.3, 0.02);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 0.6F);
			said(player);
			return true;
		}
	}

	/** Volley: a fan of five small javelins. */
	private static final class VolleyMove implements Move {
		@Override
		public String id() {
			return "volley";
		}

		@Override
		public float cost() {
			return 18.0F;
		}

		@Override
		public int cooldown() {
			return 120;
		}

		@Override
		public int level() {
			return 3;
		}

		@Override
		public int pose() {
			return Poses.VOLLEY;
		}

		@Override
		public boolean cast(ServerPlayer player, SpiritState state, int charge) {
			fan(player, player.getLookAngle(), VOLLEY_DAMAGE * Spirits.damageScale(state), Arcana.sighted(player));
			said(player);
			return true;
		}

		@Override
		public boolean castFrom(LivingEntity caster, LivingEntity target, SpiritState state) {
			if (!target.isAlive()) {
				return false;
			}
			fan(caster, target.getBoundingBox().getCenter().subtract(muzzle(caster)), VOLLEY_DAMAGE * Spirits.damageScale(state), target);
			return true;
		}
	}

	/** Bombard: a slow heavy orb that arcs and bursts on impact. */
	private static final class BombardMove implements Move {
		@Override
		public String id() {
			return "bombard";
		}

		@Override
		public float cost() {
			return 24.0F;
		}

		@Override
		public int cooldown() {
			return 200;
		}

		@Override
		public int level() {
			return 5;
		}

		@Override
		public int pose() {
			return Poses.BOMBARD;
		}

		@Override
		public boolean cast(ServerPlayer player, SpiritState state, int charge) {
			ServerLevel level = (ServerLevel)player.level();
			SpiritJavelin.lob(level, player, muzzle(player), player.getLookAngle().add(0.0, BOMBARD_LIFT, 0.0), BOMBARD_DAMAGE * Spirits.damageScale(state));
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.0F, 0.6F);
			PowersCompat.shake(player, 0.2F);
			said(player);
			return true;
		}
	}

	/** Spear wall: javelins rise from the ground in a line across the look, three blocks ahead. */
	private static final class SpearWallMove implements Move {
		@Override
		public String id() {
			return "spear_wall";
		}

		@Override
		public float cost() {
			return 20.0F;
		}

		@Override
		public int cooldown() {
			return 240;
		}

		@Override
		public int level() {
			return 7;
		}

		@Override
		public int pose() {
			return Poses.WALL;
		}

		@Override
		public boolean cast(ServerPlayer player, SpiritState state, int charge) {
			ServerLevel level = (ServerLevel)player.level();
			Vec3 look = new Vec3(player.getLookAngle().x, 0.0, player.getLookAngle().z);
			look = look.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : look.normalize();
			Vec3 across = new Vec3(-look.z, 0.0, look.x);
			Vec3 centre = player.position().add(look.scale(WALL_AHEAD));
			float damage = WALL_DAMAGE * Spirits.damageScale(state);
			List<LivingEntity> hit = new ArrayList<>();
			for (int i = -(WALL_WIDTH / 2); i <= WALL_WIDTH / 2; i++) {
				Vec3 p = centre.add(across.scale(i));
				for (double h = 0.0; h <= 2.0; h += 0.5) {
					level.sendParticles(SpiritJavelin.AMBER, p.x, p.y + h, p.z, 2, 0.08, 0.05, 0.08, 0.0);
					level.sendParticles(ParticleTypes.END_ROD, p.x, p.y + h, p.z, 1, 0.03, 0.05, 0.03, 0.0);
				}
				Mana.sendSeen(level, Mana.MOTE, p.x, p.y + 1.0, p.z, 4, 0.1, 0.7, 0.1, 0.0);
				AABB column = new AABB(p.x - 0.7, p.y - 1.5, p.z - 0.7, p.x + 0.7, p.y + 2.5, p.z + 0.7);
				for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, column, e -> !hit.contains(e) && SpiritFoes.foe(player, e, false))) {
					hit.add(e);
					e.setInvulnerableTime(0);
					if (SpiritFoes.strike(level, player, e, damage)) {
						e.setDeltaMovement(e.getDeltaMovement().add(0.0, WALL_LAUNCH, 0.0));
						e.syncVelocity = true;
					}
				}
			}
			level.playSound(null, centre.x, centre.y, centre.z, SoundEvents.EVOKER_FANGS_ATTACK, SoundSource.PLAYERS, 1.0F, 1.1F);
			level.playSound(null, centre.x, centre.y, centre.z, SoundEvents.TRIDENT_HIT_GROUND, SoundSource.PLAYERS, 1.0F, 0.8F);
			PowersCompat.shake(player, 0.3F);
			said(player);
			return true;
		}
	}
}
