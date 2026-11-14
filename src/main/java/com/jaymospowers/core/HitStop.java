package com.jaymospowers.core;

import com.jaymospowers.JaymosPowers;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Hit-stop (Andy picked it from The Awakening's feel, and noted "yes bettercombat heavies need this"): on a heavy blow
 * the attacker and the one struck hold still for a few ticks, then the knockback goes. Everyone near sees both bodies
 * hold their pose (client {@code HitStopClient}); here the struck mob's knockback waits for it.
 * <ul>
 * <li><b>Heavy melee:</b> a player's own melee blow with a slow weapon (its attack speed at or under
 * {@link #HEAVY_SPEED} swings a second: greatswords, axes, hammers, Better Combat's heavies among them) that takes
 * {@link #HEAVY_DAMAGE} or more: {@link #BASE_TICKS}, one more for each {@link #DAMAGE_PER_TICK} past that, up to
 * {@link #MAX_TICKS}.</li>
 * <li>Anything else asks for it with {@link #hit} (the Rig's punches and its dash).</li>
 * </ul>
 * A struck player keeps their own movement (it is theirs to run); they still see the hold.
 */
public final class HitStop {
	/** A melee blow is heavy from this much damage taken, with a weapon swinging this slowly or slower (swings a second). */
	public static final float HEAVY_DAMAGE = 7.0F;
	public static final double HEAVY_SPEED = 1.3;
	/** The hold: ticks for a heavy blow, the damage past {@link #HEAVY_DAMAGE} that adds each further tick, and the most. */
	public static final int BASE_TICKS = 2, MAX_TICKS = 4;
	public static final float DAMAGE_PER_TICK = 6.0F;
	/** Who sees it: players within this of the one struck (blocks). */
	public static final double RANGE = 48.0;

	/** The hold, for everyone watching: who struck (-1: nobody to hold), who was struck, for how many ticks. */
	public record Stop(int attacker, int target, int ticks) implements CustomPacketPayload {
		public static final Type<Stop> TYPE = new Type<>(JaymosPowers.id("hit_stop"));
		public static final StreamCodec<ByteBuf, Stop> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Stop::attacker, ByteBufCodecs.VAR_INT,
			Stop::target, ByteBufCodecs.VAR_INT, Stop::ticks, Stop::new);

		@Override
		public Type<Stop> type() {
			return TYPE;
		}
	}

	/** A struck mob held in place: the knockback it gets back, and the game time it gets it. */
	private static final class Held {
		Vec3 velocity = Vec3.ZERO;
		long until;
		boolean caught;
	}

	private static final Map<LivingEntity, Held> HELD = new HashMap<>();

	private HitStop() {
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Stop.TYPE, Stop.CODEC);
		ServerLivingEntityEvents.AFTER_DAMAGE.register(HitStop::afterDamage);
		ServerTickEvents.END_SERVER_TICK.register(server -> tick());
	}

	private static void afterDamage(LivingEntity victim, DamageSource source, float base, float taken, boolean blocked) {
		if (blocked || taken < HEAVY_DAMAGE || source.is(DamageTypeTags.IS_PROJECTILE) || source.is(DamageTypeTags.IS_EXPLOSION)
			|| !(source.getEntity() instanceof Player attacker) || source.getDirectEntity() != attacker || attacker == victim
			|| attacker.getAttributeValue(Attributes.ATTACK_SPEED) > HEAVY_SPEED) {
			return;
		}
		hit(attacker, victim, Math.min(MAX_TICKS, BASE_TICKS + (int)((taken - HEAVY_DAMAGE) / DAMAGE_PER_TICK)));
	}

	/** Holds {@code attacker} (may be null) and {@code target} for {@code ticks}; the target's knockback waits for it. */
	public static void hit(Entity attacker, LivingEntity target, int ticks) {
		if (!(target.level() instanceof ServerLevel level) || ticks <= 0) {
			return;
		}
		ticks = Math.min(ticks, MAX_TICKS);
		if (!(target instanceof Player)) {
			Held held = HELD.computeIfAbsent(target, t -> new Held());
			held.until = Math.max(held.until, level.getGameTime() + ticks);
		}
		Stop stop = new Stop(attacker == null ? -1 : attacker.getId(), target.getId(), ticks);
		for (ServerPlayer player : PlayerLookup.around(level, target.position(), RANGE)) {
			ServerPlayNetworking.send(player, stop);
		}
	}

	/** End of each tick: held mobs stay put (whatever knockback came this tick is kept for them), and are let go on time. */
	private static void tick() {
		for (Iterator<Map.Entry<LivingEntity, Held>> it = HELD.entrySet().iterator(); it.hasNext();) {
			Map.Entry<LivingEntity, Held> entry = it.next();
			LivingEntity mob = entry.getKey();
			Held held = entry.getValue();
			if (mob.isRemoved()) {
				it.remove();
				continue;
			}
			if (mob.level().getGameTime() >= held.until || !mob.isAlive()) {
				it.remove();
				mob.setDeltaMovement(mob.getDeltaMovement().add(held.velocity));
				mob.syncVelocity = true;
				continue;
			}
			// the first tick catches the blow's knockback; after that anything new is added to it
			Vec3 now = mob.getDeltaMovement();
			held.velocity = held.caught ? held.velocity.add(now.x, Math.max(0.0, now.y), now.z) : now;
			held.caught = true;
			mob.setDeltaMovement(0.0, Math.min(0.0, now.y), 0.0);
			mob.syncVelocity = true;
		}
	}
}
