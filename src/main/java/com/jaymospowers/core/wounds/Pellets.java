package com.jaymospowers.core.wounds;

import com.jaymospowers.core.BladeBlood;
import com.jaymospowers.core.HitReactions;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The pellets of one shotgun blast (TaCZ's shotguns fire each as its own bullet; a blunderbuss, any mod's spread) land
 * on the same body in the same tick from the same shooter. They join into one wound and one shove: the marks and the
 * flinch are laid once at the end of the tick, sized by all the damage the blast did, and the
 * knockback after the first pellet is taken back off. The damage, the bleeding and the breaks stay the pellets' own, each
 * on the part it struck, so a spread still tears wide.
 */
final class Pellets {
	/** One blast on one body this tick. */
	private static final class Blast {
		final LivingEntity victim;
		final BodyPlan plan;
		final DamageSource source;
		final BodyPart landed;
		final Vec3 velocity;
		float total;

		Blast(LivingEntity victim, BodyPlan plan, DamageSource source, @Nullable BodyPart landed, float loss) {
			this.victim = victim;
			this.plan = plan;
			this.source = source;
			this.landed = landed;
			this.total = loss;
			this.velocity = victim.getDeltaMovement();
		}
	}

	private record Key(int victim, UUID shooter) {
	}

	private static final Map<Key, Blast> BLASTS = new HashMap<>();

	private Pellets() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> flush());
	}

	/**
	 * A hit by {@code source} landed on {@code landed}. If it was a bullet, it's gathered into its blast, to be laid once at
	 * the end of the tick, and true is returned; the caller then leaves the marks and the flinch to it.
	 */
	static boolean gather(LivingEntity victim, BodyPlan plan, DamageSource source, @Nullable BodyPart landed, float loss) {
		if (!(source.getDirectEntity() instanceof Projectile bullet) || source.getEntity() == null || !BladeBlood.bullet(bullet)) {
			return false;
		}
		Key key = new Key(victim.getId(), source.getEntity().getUUID());
		Blast blast = BLASTS.get(key);
		if (blast == null) {
			BLASTS.put(key, new Blast(victim, plan, source, landed, loss));
			return true;
		}
		blast.total += loss;
		// one shove: this pellet's knockback comes off again
		victim.setDeltaMovement(blast.velocity);
		victim.needsSync = true;
		return true;
	}

	private static void flush() {
		if (BLASTS.isEmpty()) {
			return;
		}
		List<Blast> blasts = new ArrayList<>(BLASTS.values());
		BLASTS.clear();
		for (Blast blast : blasts) {
			if (blast.victim.isAlive() && !blast.victim.isRemoved()) {
				WoundMarks.onHit(blast.victim, blast.plan, blast.source, blast.landed, blast.total);
				HitReactions.onHit(blast.victim, blast.source, blast.landed, blast.total);
			}
		}
	}
}
