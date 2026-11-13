package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Bosses shrug off the first stuns (fifth edition's legendary resistance, Andy's pick D1). A boss has a few resistances
 * (three for the great ones, two for the rest). When a hold, a charm, a net, a fright or a stun (ours, or Powers' Chrono and
 * Darktide stuns, the Chrono's Time Stop and a Gravity user's Lift, which ask through {@code PowersCompat}) would take, one
 * is spent instead and the effect fails: a flake of hide comes off it, it staggers back, and the sound is lower each time.
 * Once they are all gone the effects work as they do on anyone. A spent resistance comes back one a day.
 * <p>
 * One volley counts once: tries within {@link #GRACE} ticks of a spent resistance are shrugged too, without spending more.
 */
public final class Legendary {
	/** How many a great boss (a dragon, a necromancer, the vanilla bosses) holds, and how many any other boss. */
	static final int GREAT = 3, LESSER = 2;
	/** A spent resistance comes back after this long (ticks): a day. */
	static final long RETURN = 24000L;
	/** Tries this soon (ticks) after a spent resistance count as the same try. */
	static final int GRACE = 30;
	/** How hard the stagger shoves it back (blocks a tick). */
	static final double STAGGER = 0.25;

	/** How many are spent and when the last was. */
	public record Spent(int count, long last) {
		static final Codec<Spent> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.INT.fieldOf("count").forGetter(Spent::count),
				Codec.LONG.fieldOf("last").forGetter(Spent::last))
			.apply(i, Spent::new));
	}

	static final AttachmentType<Spent> SPENT = AttachmentRegistry.create(JaymosKingdoms.id("legendary_spent"), b -> b.persistent(Spent.CODEC));

	private Legendary() {
	}

	/** Whether {@code e} is a boss that holds resistances. */
	public static boolean boss(Entity e) {
		return e instanceof LivingEntity && (e instanceof Mob m && Senses.boss(m) || BossBars.boss(e));
	}

	/** How many resistances {@code boss} holds in all. */
	static int holds(LivingEntity boss) {
		return boss.getType() == net.minecraft.world.entity.EntityTypes.ENDER_DRAGON || boss.getType() == net.minecraft.world.entity.EntityTypes.WITHER
			|| boss instanceof com.jaymoskingdoms.dragons.WindDragon || boss instanceof GreatNecromancer || boss instanceof PatronChampion ? GREAT : LESSER;
	}

	/** How many {@code boss} has left just now (resistances that have come back counted in). */
	public static int left(LivingEntity boss) {
		Spent s = boss.getAttached(SPENT);
		if (s == null) {
			return holds(boss);
		}
		long back = (boss.level().getGameTime() - s.last()) / RETURN;
		return Math.min(holds(boss), holds(boss) - s.count() + (int)Math.min(back, 100L));
	}

	/**
	 * {@code who}, if a boss, shrugs off what would take hold of it: true if a resistance was spent (or was spent a moment
	 * ago on the same try), false if it is no boss or has none left. Shows the one spent.
	 */
	public static boolean shrugs(LivingEntity who) {
		if (!boss(who) || !(who.level() instanceof ServerLevel level) || !who.isAlive()) {
			return false;
		}
		long now = level.getGameTime();
		Spent s = who.getAttached(SPENT);
		if (s != null && now - s.last() < GRACE && now >= s.last()) {
			return true;
		}
		int left = left(who);
		if (left <= 0) {
			return false;
		}
		int total = holds(who);
		who.setAttached(SPENT, new Spent(total - left + 1, now));
		show(level, who, left - 1);
		return true;
	}

	/** A flake off its hide, a stagger back; the last one goes with a crack. */
	private static void show(ServerLevel level, LivingEntity who, int remaining) {
		Vec3 at = new Vec3(who.getX(), who.getY(0.6), who.getZ());
		float w = Math.max(0.4F, who.getBbWidth());
		level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, Items.TURTLE_SCUTE), at.x, at.y, at.z, 10 + (int)(w * 4), w * 0.4, who.getBbHeight() * 0.25, w * 0.4, 0.12);
		level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 8, w * 0.4, who.getBbHeight() * 0.2, w * 0.4, 0.3);
		float pitch = 0.6F + 0.25F * remaining;
		level.playSound(null, who.getX(), who.getY(), who.getZ(), SoundEvents.SHIELD_BREAK, SoundSource.HOSTILE, 0.9F, pitch);
		if (remaining == 0) {
			level.playSound(null, who.getX(), who.getY(), who.getZ(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 0.8F, 0.5F);
			Fx.ring(level, who.position(), 1.0F + w, 0xC8C0B0);
		}
		Vec3 back = who.getLookAngle().multiply(1.0, 0.0, 1.0);
		if (back.lengthSqr() > 1.0E-4) {
			back = back.normalize().scale(-STAGGER);
			who.setDeltaMovement(who.getDeltaMovement().add(back.x, 0.05, back.z));
			who.syncVelocity = true;
		}
		who.animateHurt(who.getYRot());
		if (who instanceof Mob mob) {
			mob.getNavigation().stop();
		}
	}
}
