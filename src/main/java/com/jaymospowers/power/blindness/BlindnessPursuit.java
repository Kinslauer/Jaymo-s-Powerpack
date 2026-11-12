package com.jaymospowers.power.blindness;

import com.jaymospowers.core.wounds.Bleeding;
import com.jaymospowers.core.wounds.BodyPart;
import com.jaymospowers.core.wounds.BodyPlan;
import com.jaymospowers.core.wounds.Wounds;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The pursuit of someone who can't see ({@link Blind#sightless}), done by the radar and the smell of blood
 * ({@link BlindnessRadar}): a blow in melee marks the creature it lands on and opens a cut on it, and for as long as the
 * mark lasts ({@link #MARK_TICKS}, longer while the creature still bleeds) you run faster toward it when you sprint
 * its way ({@link #AMPLIFIER}, one more while it bleeds). The mark shows in gold on the radar.
 */
public final class BlindnessPursuit {
	// ---- tuning ----
	/** How long a mark lasts after the last blow, and how much is added to it while the creature bleeds (ticks). */
	static final int MARK_TICKS = 300, BLEEDING_EXTRA = 100;
	/** How far the pursuit reaches (blocks), and how closely you must face the creature (cosine) while sprinting. */
	static final double RANGE = 32.0, FACING_COS = 0.7;
	/** The Speed amplifier it grants, and one more while the marked creature bleeds; it is refreshed to last this many ticks. */
	static final int AMPLIFIER = 1, SPEED_TICKS = 8;

	private record Mark(int target, long until) {
	}

	private static final Map<UUID, Mark> MARKS = new HashMap<>();

	private BlindnessPursuit() {
	}

	static void init() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register(BlindnessPursuit::afterDamage);
		ServerTickEvents.END_SERVER_TICK.register(BlindnessPursuit::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> MARKS.remove(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> MARKS.clear());
	}

	/** The creature {@code player}'s pursuit is on, or null. */
	static @Nullable Mob target(ServerPlayer player) {
		Mark mark = MARKS.get(player.getUUID());
		return mark != null && player.level().getEntity(mark.target) instanceof Mob mob && mob.isAlive() ? mob : null;
	}

	private static void afterDamage(LivingEntity victim, DamageSource source, float baseDamage, float damageTaken, boolean blocked) {
		if (blocked || damageTaken <= 0.0F || !(source.getEntity() instanceof ServerPlayer player) || source.getDirectEntity() != player
			|| victim == player || !victim.isAlive() || player.isSpectator() || !Blind.sightless(player) || !(victim.level() instanceof ServerLevel level)) {
			return;
		}
		MARKS.put(player.getUUID(), new Mark(victim.getId(), level.getGameTime() + MARK_TICKS));
		BodyPlan plan = Wounds.plan(victim);
		if (plan != null) {
			Bleeding.open(victim, plan.resolve(BodyPart.BODY), false);
		}
	}

	private static void tick(MinecraftServer server) {
		if (MARKS.isEmpty()) {
			return;
		}
		for (var it = MARKS.entrySet().iterator(); it.hasNext();) {
			var entry = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			Mark mark = entry.getValue();
			if (player == null || !player.isAlive() || !Blind.sightless(player)
				|| !(player.level().getEntity(mark.target) instanceof LivingEntity target) || !target.isAlive()) {
				it.remove();
				continue;
			}
			long now = player.level().getGameTime();
			boolean bleeding = Bleeding.bleeding(target) != 0;
			if (now > mark.until + (bleeding ? BLEEDING_EXTRA : 0)) {
				it.remove();
				continue;
			}
			Vec3 to = target.position().subtract(player.position());
			double distance = to.length();
			if (distance > RANGE || distance < 2.0 || !player.isSprinting() || player.tickCount % 4 != 0) {
				continue;
			}
			Vec3 flat = new Vec3(to.x, 0.0, to.z);
			Vec3 look = new Vec3(player.getLookAngle().x, 0.0, player.getLookAngle().z);
			if (flat.lengthSqr() < 1.0E-4 || look.lengthSqr() < 1.0E-4 || flat.normalize().dot(look.normalize()) < FACING_COS) {
				continue;
			}
			player.addEffect(new MobEffectInstance(MobEffects.SPEED, SPEED_TICKS, bleeding ? AMPLIFIER + 1 : AMPLIFIER, true, false, false));
		}
	}
}
