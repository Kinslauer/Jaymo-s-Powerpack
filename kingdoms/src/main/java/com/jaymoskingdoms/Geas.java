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
 * freed, one held fast or downed, a sworn captain) or who fails a Wits save, it binds them for three days to the caster:
 * <ul>
 * <li>they never raise a hand to the caster, and a blow tried is lost and hurts them;</li>
 * <li>they stay within {@link #LEASH} blocks of the caster, and each moment past it burns ({@link #PAIN}), never to death;</li>
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

	record Oath(String lord, long until) {
		static final Codec<Oath> CODEC = RecordCodecBuilder.create(i -> i.group(Codec.STRING.fieldOf("lord").forGetter(Oath::lord),
			Codec.LONG.fieldOf("until").forGetter(Oath::until)).apply(i, Oath::new));
	}

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
		target.setAttached(SWORN, new Oath(caster.getUUID().toString(), until));
		BOUND.add(target);
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
			if (lord != null && lord.level() == level && lord.distanceTo(who) > LEASH) {
				hurt(who, PAIN);
				if (who instanceof Villager v) {
					BehaviorUtils.setWalkAndLookTargetMemories(v, new EntityTracker(lord, false), 1.0F, 2);
				} else if (who instanceof Mob mob) {
					mob.getNavigation().moveTo(lord, 1.0);
				}
			}
		}
	}
}
