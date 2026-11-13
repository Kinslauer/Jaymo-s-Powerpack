package com.jaymoskingdoms;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Held fast (fifth edition's paralysed, Andy's pick B4), and the two short stillnesses that work the same way. A body held
 * can't move or act, only look about: a player's keys are locked on their own screen (their eyes stay free), a mob stands
 * rooted and its blows don't land. Players and everyone else alike.
 * <ul>
 * <li><b>Held</b>: a Hold spell, a ghoul-like claw, a spider's venom, a stiffening venom. Every melee blow on a held body
 * lands as a crit, and a Grit save ({@link Saves.Kind#HOLD}) every {@link #SAVE_EVERY} ticks may break it.</li>
 * <li><b>Startled</b> ({@link Surprise}): a heartbeat of stillness. No crit, no save; it just passes.</li>
 * <li><b>Asleep</b> (a sleep draught, {@link Poisons}): out cold until it wears off or they are hurt. The first blow is a crit.</li>
 * </ul>
 * Held and asleep bodies also give the one who strikes the upper hand ({@link UpperHand}).
 */
public final class HeldFast {
	/** A held body's save comes round this often (ticks). */
	static final int SAVE_EVERY = 60;
	/** A melee blow lands as a crit: this many times harder. */
	static final float CRIT = 1.5F;
	/** A claw (the paralysing claws tag) holds the one it rakes this often, for this long (ticks). */
	static final float CLAW_CHANCE = 0.25F;
	static final int CLAW_TICKS = 20 * 3;
	/** A spider's venom, and a cave spider's, hold this often, for this long (ticks). */
	static final float VENOM_CHANCE = 0.12F, CAVE_VENOM_CHANCE = 0.2F;
	static final int VENOM_TICKS = 20 * 4;
	/** Creatures whose claws hold: wraiths, husks, and whatever ghouls the pack adds. */
	public static final net.minecraft.tags.TagKey<net.minecraft.world.entity.EntityType<?>> PARALYSING_CLAWS =
		net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE, JaymosKingdoms.id("paralysing_claws"));
	/** A blow from within this many blocks counts as close by. */
	static final double CLOSE = 4.5;

	/** Why a body is still. */
	public enum Kind {
		HELD, STARTLED, ASLEEP
	}

	private static final class Hold {
		Kind kind;
		long until, nextSave;

		Hold(Kind kind, long until, long nextSave) {
			this.kind = kind;
			this.until = until;
			this.nextSave = nextSave;
		}
	}

	private static final Map<LivingEntity, Hold> HOLDS = new WeakHashMap<>();

	private HeldFast() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(HeldFast::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> HOLDS.clear());
		// a body that can't act deals no damage (a mob's blow, a bolt from its bow, a player's swing)
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((victim, source, amount) ->
			!(source.getEntity() instanceof LivingEntity doer && doer != victim && source.getDirectEntity() == doer && locked(doer)));
		// nor does a held archer's shot leave the string
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			if (entity instanceof Projectile p && p.getOwner() instanceof LivingEntity owner && locked(owner)) {
				p.discard();
			}
		});
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> locked(player) ? InteractionResult.FAIL : InteractionResult.PASS);
		AttackBlockCallback.EVENT.register((player, world, hand, pos, dir) -> locked(player) ? InteractionResult.FAIL : InteractionResult.PASS);
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> locked(player) ? InteractionResult.FAIL : InteractionResult.PASS);
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> locked(player) ? InteractionResult.FAIL : InteractionResult.PASS);
		UseItemCallback.EVENT.register((player, world, hand) -> locked(player) ? InteractionResult.FAIL : InteractionResult.PASS);
		PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, be) -> !locked(player));
		UpperHand.add((attacker, target) -> locked(target) ? 1 : 0);
		// a ghoul-like claw, a spider's venom: sometimes a bite or a rake holds the one it lands on
		ServerLivingEntityEvents.AFTER_DAMAGE.register((victim, source, base, taken, blocked) -> {
			if (!(source.getEntity() instanceof Mob attacker) || source.getDirectEntity() != attacker || !victim.isAlive() || taken <= 0.0F || victim == attacker) {
				return;
			}
			if (attacker.is(PARALYSING_CLAWS)) {
				if (attacker.getRandom().nextFloat() < CLAW_CHANCE) {
					afflict(victim, CLAW_TICKS);
				}
			} else if (attacker instanceof net.minecraft.world.entity.monster.spider.Spider) {
				if (attacker.getRandom().nextFloat() < (attacker instanceof net.minecraft.world.entity.monster.spider.CaveSpider ? CAVE_VENOM_CHANCE : VENOM_CHANCE)) {
					afflict(victim, VENOM_TICKS);
				}
			}
		});
	}

	/** Whether {@code who} is held, startled or asleep from the draught just now. */
	public static boolean locked(LivingEntity who) {
		Hold h = HOLDS.get(who);
		return h != null && who.level().getGameTime() < h.until;
	}

	/** Whether {@code who} is held fast (a hold proper: it can be saved against and every blow is a crit). */
	public static boolean held(LivingEntity who) {
		Hold h = HOLDS.get(who);
		return h != null && h.kind == Kind.HELD && who.level().getGameTime() < h.until;
	}

	/** Whether {@code who} is out cold from a sleep draught. */
	public static boolean asleep(LivingEntity who) {
		Hold h = HOLDS.get(who);
		return h != null && h.kind == Kind.ASLEEP && who.level().getGameTime() < h.until;
	}

	/** Whether nothing can hold {@code who}: spectators and the mindless dead who feel nothing in their limbs (a boss shrugs off the first holds, {@link Legendary}). */
	public static boolean immune(LivingEntity who) {
		return who instanceof Player p && (p.isSpectator() || p.isCreative()) || who.is(EntityTypeTags.SKELETONS);
	}

	/** {@code who} is held for {@code ticks}, with no save to resist it first. Returns false if nothing can hold them. */
	public static boolean hold(LivingEntity who, int ticks) {
		return !Legendary.shrugs(who) && set(who, Kind.HELD, ticks);
	}

	/** {@code who} is held for {@code ticks} unless their Grit save turns it (rolled once, now). Returns whether it took. */
	public static boolean afflict(LivingEntity who, int ticks) {
		if (immune(who) || Saves.resist(who, Saves.Kind.HOLD)) {
			return false;
		}
		return hold(who, ticks);
	}

	/** {@code who} is still for a heartbeat ({@link Surprise}). */
	static boolean startle(LivingEntity who, int ticks) {
		return set(who, Kind.STARTLED, ticks);
	}

	/** {@code who} is out cold for {@code ticks} ({@link Poisons}). */
	static boolean sleep(LivingEntity who, int ticks) {
		return !immune(who) && !Legendary.shrugs(who) && set(who, Kind.ASLEEP, ticks);
	}

	private static boolean set(LivingEntity who, Kind kind, int ticks) {
		if (immune(who) || !(who.level() instanceof ServerLevel level) || !who.isAlive()) {
			return false;
		}
		long now = level.getGameTime();
		Hold was = HOLDS.get(who);
		// a longer or deeper stillness wins over a shorter one already on
		if (was != null && now < was.until && was.until >= now + ticks) {
			return false;
		}
		HOLDS.put(who, new Hold(kind, now + ticks, now + SAVE_EVERY));
		stand(who);
		return true;
	}

	/** Lets {@code who} go (a save, a cure, a blow that woke them). */
	public static void release(LivingEntity who) {
		HOLDS.remove(who);
	}

	/** A body rooted to its spot: no walking, no sprint, no path. */
	private static void stand(LivingEntity who) {
		if (who instanceof Mob mob) {
			mob.getNavigation().stop();
		}
		if (who instanceof Player p) {
			p.setSprinting(false);
		}
		Vec3 v = who.getDeltaMovement();
		who.setDeltaMovement(0.0, Math.min(0.0, v.y), 0.0);
		who.syncVelocity = true;
	}

	private static void tick(MinecraftServer server) {
		if (HOLDS.isEmpty()) {
			return;
		}
		List<Map.Entry<LivingEntity, Hold>> all = new ArrayList<>(HOLDS.entrySet());
		for (Map.Entry<LivingEntity, Hold> e : all) {
			LivingEntity who = e.getKey();
			Hold h = e.getValue();
			if (who == null || !who.isAlive() || who.isRemoved() || !(who.level() instanceof ServerLevel level)) {
				HOLDS.remove(who);
				continue;
			}
			long now = level.getGameTime();
			if (now >= h.until) {
				HOLDS.remove(who);
				continue;
			}
			if (h.kind == Kind.HELD && now >= h.nextSave) {
				h.nextSave = now + SAVE_EVERY;
				if (Saves.resist(who, Saves.Kind.HOLD)) {
					HOLDS.remove(who);
					level.playSound(null, who.getX(), who.getY(), who.getZ(), SoundEvents.PLAYER_BREATH, SoundSource.PLAYERS, 0.8F, 1.5F);
					level.sendParticles(ParticleTypes.CLOUD, who.getX(), who.getY(0.5), who.getZ(), 6, 0.3, 0.4, 0.3, 0.02);
					continue;
				}
			}
			if (who instanceof Mob || who instanceof ServerPlayer) {
				Vec3 v = who.getDeltaMovement();
				if (who.onGround() || who instanceof Mob) {
					who.setDeltaMovement(0.0, v.y, 0.0);
				}
				if (who instanceof Mob mob) {
					mob.getNavigation().stop();
				}
			}
		}
	}

	/** {@code target} taking {@code amount}: a melee blow on a held or sleeping body lands as a crit; a hurt sleeper wakes. */
	public static float dealt(LivingEntity target, DamageSource source, float amount) {
		Hold h = HOLDS.get(target);
		if (h == null || amount <= 0.0F || !(target.level() instanceof ServerLevel level) || level.getGameTime() >= h.until) {
			return amount;
		}
		boolean close = source.getDirectEntity() == source.getEntity() && source.getEntity() != null && source.getEntity().distanceToSqr(target) <= CLOSE * CLOSE;
		if (h.kind == Kind.ASLEEP) {
			// any hurt wakes them; a first blow close by is a crit
			HOLDS.remove(target);
			return close ? crit(level, target, amount) : amount;
		}
		return h.kind == Kind.HELD && close && !Senses.cooling(target, source) ? crit(level, target, amount) : amount;
	}

	private static float crit(ServerLevel level, LivingEntity target, float amount) {
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 0.9F, 0.9F);
		level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY(0.6), target.getZ(), 12, 0.3, 0.3, 0.3, 0.25);
		return amount * CRIT;
	}
}
