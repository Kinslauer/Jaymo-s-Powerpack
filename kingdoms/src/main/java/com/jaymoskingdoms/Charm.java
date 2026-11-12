package com.jaymoskingdoms;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Charmed (fifth edition's charmed, Andy's pick B2). Players and everyone else alike. A charmed body's swings turn aside
 * from whoever charmed it, and a charmed person agrees to more: the charmer gets better prices and better odds when
 * they haggle or tell a tale ({@link #sway}, {@link #prices}). A Wits save ({@link Saves.Kind#CHARM}) resists it as it
 * is cast. It breaks when the charmer or their friends (their pets, their group, others they've charmed) hurt the
 * charmed, and times out.
 * <p>
 * What charms: a vampire's gaze (a vampire that has you in its sight and means to feed, {@link #gaze}), a hag's song (a
 * witch singing at one who stands near her), and the Charm spell, which calls {@link #charm} when it is built.
 */
public final class Charm {
	/** How long a charm lasts (ticks), at the most. */
	static final int MOST = 20 * 120;
	/** A vampire's gaze and a hag's song reach this far (blocks), try once this often (ticks), and last this long (ticks). */
	static final double GAZE_RANGE = 9.0;
	static final int GAZE_EVERY = 100, GAZE_TICKS = 20 * 45, SONG_TICKS = 20 * 30;
	/** A charmed person's better odds when haggling or telling a tale, and the share their prices come down by. */
	static final float SWAY = 0.3F, DISCOUNT = 0.3F;
	/** Charmers may not be charmed back by their own thrall inside this long (ticks) of the charm. */
	static final int ARMS_LENGTH = 40;

	private record Spell(UUID charmer, long until) {
	}

	private static final Map<LivingEntity, Spell> SPELLS = new WeakHashMap<>();

	private Charm() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Charm::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> SPELLS.clear());
		// swings turn aside from the charmer; a blow from the charmer or their friends breaks the charm
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((victim, source, amount) -> {
			Entity doer = source.getEntity();
			if (doer instanceof LivingEntity attacker && charmedBy(attacker, victim)) {
				if (victim.level() instanceof ServerLevel level) {
					level.playSound(null, attacker.getX(), attacker.getY(), attacker.getZ(), SoundEvents.PLAYER_ATTACK_NODAMAGE, SoundSource.PLAYERS, 0.6F, 1.4F);
				}
				return false;
			}
			if (doer != null && charmed(victim) && friendsOf(victim, doer, source)) {
				SPELLS.remove(victim);
				if (victim.level() instanceof ServerLevel level) {
					level.playSound(null, victim.getX(), victim.getEyeY(), victim.getZ(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.6F, 1.6F);
				}
			}
			return true;
		});
	}

	/** Whether {@code who} is charmed just now. */
	public static boolean charmed(LivingEntity who) {
		Spell s = SPELLS.get(who);
		return s != null && who.level().getGameTime() < s.until;
	}

	/** Whether {@code who} is charmed by {@code charmer} in particular. */
	public static boolean charmedBy(LivingEntity who, Entity charmer) {
		Spell s = SPELLS.get(who);
		return s != null && who.level().getGameTime() < s.until && s.charmer.equals(charmer.getUUID());
	}

	/**
	 * {@code charmer} charms {@code who} for {@code ticks} after a Wits save (rolled once, now). Returns whether it took.
	 * Bosses and those who feel nothing (the dead who aren't minds) can't be charmed.
	 */
	public static boolean charm(LivingEntity who, LivingEntity charmer, int ticks) {
		if (who == charmer || who instanceof Mob m && Senses.boss(m) || who instanceof Player p && (p.isSpectator() || p.isCreative())
			|| !(who.level() instanceof ServerLevel level)) {
			return false;
		}
		if (Saves.resist(who, Saves.Kind.CHARM)) {
			return false;
		}
		SPELLS.put(who, new Spell(charmer.getUUID(), level.getGameTime() + Math.min(ticks, MOST)));
		if (who instanceof Mob mob) {
			if (mob.getTarget() == charmer) {
				mob.setTarget(null);
			}
			mob.getNavigation().stop();
		}
		Fx.ring(level, new Vec3(who.getX(), who.getY(0.5), who.getZ()), 0.9F, 0xE0609A);
		level.playSound(null, who.getX(), who.getEyeY(), who.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.7F, 1.5F);
		return true;
	}

	/** Frees {@code who} of any charm (a cure). */
	public static void free(LivingEntity who) {
		SPELLS.remove(who);
	}

	/** {@code doer}'s blow (by {@code source}) is the charmer's or a friend's: their own, their pet's, their group's, another thrall's. */
	private static boolean friendsOf(LivingEntity victim, Entity doer, DamageSource source) {
		Spell s = SPELLS.get(victim);
		if (s == null) {
			return false;
		}
		Entity owner = doer instanceof Projectile p && p.getOwner() != null ? p.getOwner() : doer;
		if (owner.getUUID().equals(s.charmer)) {
			return true;
		}
		if (owner instanceof TamableAnimal pet && pet.getOwnerReference() != null && s.charmer.equals(pet.getOwnerReference().getUUID())) {
			return true;
		}
		Entity charmer = victim.level() instanceof ServerLevel level ? level.getEntity(s.charmer) : null;
		if (charmer instanceof Player p && Groups.together(p, owner)) {
			return true;
		}
		return owner instanceof LivingEntity thrall && owner != victim && charmedBy(thrall, charmer == null ? owner : charmer);
	}

	/** The better odds {@code charmer} has with {@code npc} when haggling or telling a tale: more if they're charmed by them. */
	public static float sway(@Nullable Entity npc, Entity charmer) {
		return npc instanceof LivingEntity who && charmedBy(who, charmer) ? SWAY : 0.0F;
	}

	/** A charmed trader charges their charmer less. */
	public static void prices(net.minecraft.world.entity.npc.villager.Villager villager, Player player) {
		if (!charmedBy(villager, player)) {
			return;
		}
		for (net.minecraft.world.item.trading.MerchantOffer offer : villager.getOffers()) {
			int by = Math.max(1, Math.round(offer.getBaseCostA().getCount() * DISCOUNT));
			offer.addToSpecialPriceDiff(-by);
		}
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 5 == 0 && !SPELLS.isEmpty()) {
			for (Map.Entry<LivingEntity, Spell> e : new ArrayList<>(SPELLS.entrySet())) {
				LivingEntity who = e.getKey();
				if (who == null || !who.isAlive() || who.isRemoved() || who.level().getGameTime() >= e.getValue().until) {
					SPELLS.remove(who);
					continue;
				}
				if (who instanceof Mob mob && mob.getTarget() != null && mob.getTarget().getUUID().equals(e.getValue().charmer)) {
					mob.setTarget(null);
					mob.getNavigation().stop();
				}
			}
		}
		if (server.getTickCount() % GAZE_EVERY == 0) {
			for (ServerLevel level : server.getAllLevels()) {
				for (Entity e : level.getAllEntities()) {
					if (e instanceof Mob m && m.isAlive() && !m.isSleeping() && m.getTarget() instanceof LivingEntity prey) {
						gaze(m, prey);
					}
				}
			}
		}
	}

	/** A vampire's gaze, or a witch's song, on the one it has set its mind on. */
	private static void gaze(Mob m, LivingEntity prey) {
		boolean vampire = Vampires.isVampire(m);
		if (!(vampire || m instanceof Witch) || m.distanceTo(prey) > GAZE_RANGE || !m.hasLineOfSight(prey) || !prey.isAlive() || charmed(prey)) {
			return;
		}
		if (m.getRandom().nextFloat() < 0.5F) {
			charm(prey, m, vampire ? GAZE_TICKS : SONG_TICKS);
		}
	}
}
