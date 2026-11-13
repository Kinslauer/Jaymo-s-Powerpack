package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * Geas (fifth edition): a sworn task that hurts to break. Laid on someone who cannot refuse it (a captive in chains or just
 * freed, one held fast or downed, a sworn captain) or who fails a Wits save, it binds them for three days to the caster,
 * who names the task as it takes hold ({@link #TASKS}):
 * <ul>
 * <li><b>serve</b> (what it is until another is named): stay within {@link #LEASH} blocks of the caster and fight whoever
 * the caster fights or is set upon by;</li>
 * <li><b>guard</b>: keep to within {@link #POST} blocks of the spot they were sworn on, a watchman who cannot leave it;</li>
 * <li><b>begone</b>: leave, and stay {@link #AWAY} blocks from the spot they were sworn on (they are given {@link #GRACE}
 * ticks to get clear).</li>
 * </ul>
 * Breaking the task burns ({@link #PAIN}, never to death). And whatever the task:
 * <ul>
 * <li>they never raise a hand to the caster, and a blow tried is lost and hurts them;</li>
 * <li>a rescued captive does not go their own way, and a sworn captain does not turn back on his lord at dawn.</li>
 * </ul>
 * It ends with the days, with the caster's death, or with a dispel. Held by the bound one, so it outlasts a restart.
 */
public final class Geas {
	/** An oath lasts this long (ticks), binds to within this many blocks, and burns this much a time it is broken. */
	static final long TICKS = 3L * 24000L;
	static final double LEASH = 40.0;
	static final float PAIN = 1.0F, PAIN_LEAST = 4.0F, BLOW_PAIN = 2.0F;
	/** How often (ticks) the oath is checked. */
	static final int EVERY = 40;
	static final int COLOUR = 0x7A3AA8;
	/** The tasks, by the name saved. */
	static final List<String> TASKS = List.of("serve", "guard", "begone");
	/** Guard: how far from the post; begone: how far to stay from it, and the time (ticks) given to get there. */
	static final double POST = 10.0, AWAY = 48.0;
	static final int GRACE = 20 * 60;

	record Oath(String lord, long until, String task, long post) {
		static final Codec<Oath> CODEC = RecordCodecBuilder.create(i -> i.group(Codec.STRING.fieldOf("lord").forGetter(Oath::lord),
			Codec.LONG.fieldOf("until").forGetter(Oath::until), Codec.STRING.optionalFieldOf("task", "serve").forGetter(Oath::task),
			Codec.LONG.optionalFieldOf("post", 0L).forGetter(Oath::post)).apply(i, Oath::new));
	}

	/** When each one told to be gone must be clear by (game time). */
	private static final Map<LivingEntity, Long> CLEAR_BY = new WeakHashMap<>();

	static final AttachmentType<Oath> SWORN = AttachmentRegistry.create(JaymosKingdoms.id("geas"), b -> b.persistent(Oath.CODEC));
	/** On the overworld: sworn captains' ids, with the time their oath ends. */
	static final AttachmentType<Map<String, Long>> CAPTAINS = AttachmentRegistry.create(JaymosKingdoms.id("geas_captains"),
		b -> b.persistent(Codec.unboundedMap(Codec.STRING, Codec.LONG)));
	private static final Set<LivingEntity> BOUND = Collections.newSetFromMap(new WeakHashMap<>());

	private Geas() {
	}

	static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof LivingEntity living && living.getAttached(SWORN) != null) {
				BOUND.add(living);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(Geas::tick);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((victim, source, amount) -> {
			Entity doer = source.getEntity();
			if (doer instanceof LivingEntity bound && doer != victim && lordIs(bound, victim)) {
				hurt(bound, BLOW_PAIN);
				return false;
			}
			return true;
		});
	}

	private static boolean lordIs(LivingEntity bound, Entity other) {
		Oath o = bound.getAttached(SWORN);
		return o != null && o.lord().equals(other.getUUID().toString()) && bound.level().getGameTime() < o.until();
	}

	/** Whether {@code who} lies under a geas just now. */
	public static boolean sworn(LivingEntity who) {
		Oath o = who.getAttached(SWORN);
		return o != null && who.level().getGameTime() < o.until();
	}

	/** Whether the sworn captain {@code id} is bound by a geas (he will not turn on his lord). */
	static boolean bindsCaptain(MinecraftServer server, String id, long now) {
		Long until = Factions.level(server).getAttachedOrElse(CAPTAINS, Map.of()).get(id);
		return until != null && now < until;
	}

	/** Lifts a geas from {@code who} (a dispel); true if there was one. */
	public static boolean lift(LivingEntity who) {
		boolean had = who.getAttached(SWORN) != null;
		who.removeAttached(SWORN);
		BOUND.remove(who);
		return had;
	}

	/** Whether {@code who} cannot refuse: bound in chains, held fast, down, a captive {@code caster} freed, or a captain already sworn to them. */
	private static boolean helpless(LivingEntity who, ServerPlayer caster) {
		Captives.Captive c = who.getAttached(Captives.CAPTIVE);
		boolean freed = c != null && c.rescuer().map(caster.getUUID()::equals).orElse(false);
		return Captives.bound(who) || HeldFast.locked(who) || Downed.isDowned(who) || freed
			|| SwornCaptains.free(who) && caster.getUUID().equals(SwornCaptains.lordOf(who));
	}

	static boolean cast(ServerLevel level, ServerPlayer caster, float power) {
		LivingEntity target = MindSpells.target(caster);
		if (target == null || target == caster || !(MindSpells.person(target) || target instanceof Villager) || !SpellParts.canSpeak(caster)) {
			return false;
		}
		Fx.beam(level, caster.getEyePosition().add(0.0, -0.3, 0.0), target.getBoundingBox().getCenter(), 0.14F, COLOUR, 10);
		if (!helpless(target, caster) && (Legendary.shrugs(target) || Saves.resist(target, Saves.Kind.CHARM))) {
			MindSpells.shrugged(level, target);
			return true;
		}
		long until = level.getGameTime() + Math.round(TICKS * power);
		target.setAttached(SWORN, new Oath(caster.getUUID().toString(), until, "serve", target.blockPosition().asLong()));
		BOUND.add(target);
		// the caster names the task (left unnamed, it is to serve)
		List<Menus.Choice> tasks = new java.util.ArrayList<>();
		for (String task : TASKS) {
			tasks.add(Menus.Choice.of(Component.translatable("geas.jaymoskingdoms.task." + task), p -> charge(p, target, task)));
		}
		Menus.ask(caster, target, Component.translatable("geas.jaymoskingdoms.sworn"), tasks);
		if (SwornCaptains.free(target) && caster.getUUID().equals(SwornCaptains.lordOf(target))) {
			String id = Captains.idOf(target);
			if (id != null) {
				Map<String, Long> all = new HashMap<>(Factions.level(level.getServer()).getAttachedOrElse(CAPTAINS, Map.of()));
				all.put(id, until);
				Factions.level(level.getServer()).setAttached(CAPTAINS, Map.copyOf(all));
			}
		}
		Fx.sigil(level, target.position().add(0.0, 0.05, 0.0), 1.4F, COLOUR, 60);
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 0.8F, 0.6F);
		return true;
	}

	/** {@code caster} names the task of the one they bound. */
	private static void charge(ServerPlayer caster, LivingEntity bound, String task) {
		Oath o = bound.getAttached(SWORN);
		if (o == null || !o.lord().equals(caster.getUUID().toString()) || !bound.isAlive() || !(bound.level() instanceof ServerLevel level)) {
			return;
		}
		bound.setAttached(SWORN, new Oath(o.lord(), o.until(), task, bound.blockPosition().asLong()));
		if (task.equals("begone")) {
			CLEAR_BY.put(bound, level.getGameTime() + GRACE);
		}
		Speech.tell(level, bound, caster, Component.translatable("geas.jaymoskingdoms.answer." + task), COLOUR);
	}

	private static void walk(LivingEntity who, BlockPos to) {
		if (who instanceof Villager v) {
			BehaviorUtils.setWalkAndLookTargetMemories(v, to, 1.0F, 2);
		} else if (who instanceof Mob mob) {
			mob.getNavigation().moveTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5, 1.0);
		}
	}

	/** Pain for a broken oath: a burn and a cry, never enough to kill. */
	private static void hurt(LivingEntity who, float amount) {
		if (!(who.level() instanceof ServerLevel level) || who.getHealth() <= PAIN_LEAST) {
			return;
		}
		who.hurtServer(level, level.damageSources().magic(), Math.min(amount, who.getHealth() - PAIN_LEAST));
		level.sendParticles(Fx.rune(COLOUR, 0.9F), who.getX(), who.getY(1.0), who.getZ(), 5, 0.3, 0.4, 0.3, 0.02);
		level.playSound(null, who.getX(), who.getY(), who.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.NEUTRAL, 0.8F, 0.6F);
	}

	private static void tick(MinecraftServer server) {
		if (BOUND.isEmpty() || server.getTickCount() % EVERY != 0) {
			return;
		}
		for (LivingEntity who : List.copyOf(BOUND)) {
			Oath o = who.getAttached(SWORN);
			if (o == null || !who.isAlive() || !(who.level() instanceof ServerLevel level)) {
				BOUND.remove(who);
				continue;
			}
			UUID lordId;
			try {
				lordId = UUID.fromString(o.lord());
			} catch (IllegalArgumentException e) {
				lift(who);
				continue;
			}
			if (level.getGameTime() >= o.until()) {
				lift(who);
				continue;
			}
			Entity lord = level.getEntity(lordId);
			ServerPlayer online = server.getPlayerList().getPlayer(lordId);
			if (online != null && !online.isAlive()) {
				lift(who);
				continue;
			}
			if (who instanceof Mob mob && mob.getTarget() == lord) {
				mob.setTarget(null);
			}
			BlockPos post = BlockPos.of(o.post());
			double fromPost = Math.sqrt(who.blockPosition().distSqr(post));
			switch (o.task()) {
				case "guard" -> {
					if (fromPost > POST) {
						hurt(who, PAIN);
						walk(who, post);
					}
				}
				case "begone" -> {
					if (fromPost < AWAY) {
						// away from the spot, the way they already stand from it
						double dx = who.getX() - (post.getX() + 0.5), dz = who.getZ() - (post.getZ() + 0.5);
						double d = Math.max(0.01, Math.sqrt(dx * dx + dz * dz));
						walk(who, BlockPos.containing(who.getX() + dx / d * 16.0, who.getY(), who.getZ() + dz / d * 16.0));
						Long clearBy = CLEAR_BY.get(who);
						if (clearBy == null) {
							CLEAR_BY.put(who, level.getGameTime() + GRACE);
						} else if (level.getGameTime() > clearBy) {
							hurt(who, PAIN);
						}
					}
				}
				default -> {
					if (lord != null && lord.level() == level && lord.distanceTo(who) > LEASH) {
						hurt(who, PAIN);
						if (who instanceof Villager v) {
							BehaviorUtils.setWalkAndLookTargetMemories(v, new EntityTracker(lord, false), 1.0F, 2);
						} else if (who instanceof Mob mob) {
							mob.getNavigation().moveTo(lord, 1.0);
						}
					}
					// their lord's fight is theirs: whoever set on the lord, else whoever the lord last struck
					if (who instanceof Mob mob && lord instanceof LivingEntity master && (mob.getTarget() == null || !mob.getTarget().isAlive())) {
						LivingEntity foe = master.getLastHurtByMob() != null && master.getLastHurtByMob().isAlive() ? master.getLastHurtByMob() : master.getLastHurtMob();
						if (foe != null && foe.isAlive() && foe != who && foe != master && foe.distanceToSqr(who) < LEASH * LEASH && !sworn(foe)) {
							mob.setTarget(foe);
						}
					}
				}
			}
		}
	}
}
