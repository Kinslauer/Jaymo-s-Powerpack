package com.jaymoskingdoms;

import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.illager.SpellcasterIllager;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * The defence disciplines for enemies (Andy, 2026-11-22: "only armed enemies get wards, more as world teir increases, and a
 * max of 5 on experienced enemies"; and "add the same capabilities to MCA"). A mob with a weapon or shield in hand gets
 * passive blocks under a player's rules: a player's blow on its front is caught, {@link #SHARE} of it stopped, one block
 * used. With none left the next blow lands clean and it flinches (its swing cut short). Hit from behind or the side and
 * none are used.
 * <ul>
 * <li><b>How many</b> ({@link #ARMED}, {@link #EXPERIENCED}): by the world tier of the player striking, more for
 * {@link WeaponForms#experienced} fighters, 5 at most.</li>
 * <li><b>Coming back</b>: one every {@link #RETURN_TICKS} once it hasn't been hit for {@link #CALM_TICKS}; all of them when it
 * lands its own parry ({@link MobParry}, rolled before a block). A parry of yours or a guard break takes all of them.</li>
 * <li><b>Discipline</b>, by what it is ({@link #discipline}): casters (evokers, illusioners, witches) Arcana; an MCA villager
 * (guards, bandits, orcs, realm soldiers) its heritage's lean ({@link Disciplines#leans}), Arms first; anyone else Arms. Only
 * the look differs: steel's sparks, a flare of ki, a ring of runes.</li>
 * <li>Unarmed mobs, a caster with nothing in hand, and bosses ({@link #BOSS_HEALTH} or more) get none.</li>
 * </ul>
 */
public final class MobBlocks {
	/** Blocks by the striking player's world tier (Hearth to Legend): an armed enemy, an experienced one. */
	static final int[] ARMED = {1, 1, 2, 3, 4}, EXPERIENCED = {2, 2, 3, 4, 5};
	static final float SHARE = 0.6F;
	static final int CALM_TICKS = 60, RETURN_TICKS = 80;
	/** A clean hit: it reels this long (ticks), its swing cut. */
	static final int FLINCH = 8;
	static final float BOSS_HEALTH = 100.0F;
	/** After a parry or a broken guard, no blocks at all for this long (ticks). */
	static final int BROKEN_TICKS = 40;
	/** Only blows from this far round its front (degrees each side), as its parry. */
	static final double FRONT_DEGREES = 90.0;

	/** Per mob: blocks used, last hit, last given back, no blocks until (game time), and how many it had at the last blow. */
	private static final Map<Mob, long[]> STATE = new WeakHashMap<>();
	private static final int USED = 0, HIT = 1, BACK = 2, BROKEN = 3, TOTAL = 4;

	/**
	 * What a locked-on player's pips show (Kingdoms' client, {@code MobBlocksHud}): blocks left (3 bits), how many it has
	 * (3 bits), its discipline (2 bits). Synced to everyone near; missing until it's first struck.
	 */
	public static final net.fabricmc.fabric.api.attachment.v1.AttachmentType<Integer> SHOWN = net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry.create(
		JaymosKingdoms.id("mob_blocks"), builder -> builder.syncWith(net.minecraft.network.codec.ByteBufCodecs.VAR_INT,
			net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate.all()));

	private MobBlocks() {
	}

	/** From {@link MobParry#register}: the calm gives blocks back, and the pips follow. */
	static void register() {
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 20 != 0 || STATE.isEmpty()) {
				return;
			}
			for (Map.Entry<Mob, long[]> e : new java.util.ArrayList<>(STATE.entrySet())) {
				Mob mob = e.getKey();
				if (mob.isAlive() && mob.level() instanceof ServerLevel level) {
					settle(e.getValue(), level.getGameTime());
					sync(mob, e.getValue());
				}
			}
		});
	}

	/** The calm since its last hit gives blocks back, one every {@link #RETURN_TICKS}. */
	private static void settle(long[] s, long now) {
		if (s[USED] <= 0) {
			return;
		}
		long since = Math.max(s[HIT] + CALM_TICKS, s[BACK]);
		long back = Math.max(0L, (now - since) / RETURN_TICKS);
		if (back > 0) {
			s[USED] = Math.max(0L, s[USED] - back);
			s[BACK] = now;
		}
	}

	private static void sync(Mob mob, long[] s) {
		int total = (int)Math.min(7L, s[TOTAL]);
		int left = (int)Math.clamp(s[TOTAL] - s[USED], 0L, 7L);
		int packed = left | total << 3 | discipline(mob) << 6;
		Integer was = mob.getAttached(SHOWN);
		if (was == null || was != packed) {
			mob.setAttached(SHOWN, packed);
		}
	}

	/** Whether {@code mob} gets blocks at all: armed, not a boss. */
	static boolean armed(Mob mob) {
		if (mob.getMaxHealth() >= BOSS_HEALTH) {
			return false;
		}
		ItemStack main = mob.getMainHandItem(), off = mob.getOffhandItem();
		return main.has(DataComponents.WEAPON) || main.has(DataComponents.BLOCKS_ATTACKS) || off.has(DataComponents.BLOCKS_ATTACKS);
	}

	/** Its discipline: what it is, not what it holds. */
	static int discipline(Mob mob) {
		if (mob instanceof SpellcasterIllager || mob instanceof Witch) {
			return Disciplines.ARCANA;
		}
		Heritage.Kind kind = VillagerHeritage.of(mob);
		if (kind != null) {
			Set<Integer> leans = Disciplines.leans(kind, HeritageVariants.of(mob));
			if (!leans.isEmpty() && !leans.contains(Disciplines.ARMS)) {
				return leans.iterator().next();
			}
		}
		return Disciplines.ARMS;
	}

	/** How many blocks {@code mob} has against {@code player}. */
	static int count(Mob mob, Player player) {
		int tier = Math.clamp(WorldTiers.of(player).ordinal(), 0, ARMED.length - 1);
		return (WeaponForms.experienced(mob) ? EXPERIENCED : ARMED)[tier];
	}

	private static long[] state(Mob mob) {
		return STATE.computeIfAbsent(mob, m -> new long[] {0L, Long.MIN_VALUE / 2, Long.MIN_VALUE / 2, Long.MIN_VALUE / 2, ARMED[0]});
	}

	/** Last of the hurt chain ({@code LivingEntityHurtMixin}): a player's blow on an armed mob's front, caught or clean. */
	public static float dealt(LivingEntity victim, DamageSource source, float amount) {
		if (!(victim instanceof Mob mob) || !(victim.level() instanceof ServerLevel level) || !(source.getEntity() instanceof Player player)
			|| amount <= 0.0F || !mob.isAlive() || Downed.isDowned(mob) || !armed(mob)) {
			return amount;
		}
		if (!inFront(mob, player)) {
			return amount;
		}
		long now = level.getGameTime();
		long[] s = state(mob);
		settle(s, now);
		s[HIT] = now;
		s[TOTAL] = count(mob, player);
		s[USED] = Math.min(s[USED], s[TOTAL]);
		if (now >= s[BROKEN] && s[USED] < s[TOTAL]) {
			s[USED]++;
			sync(mob, s);
			show(level, mob, player, discipline(mob));
			return amount * (1.0F - SHARE);
		}
		sync(mob, s);
		flinch(level, mob);
		return amount;
	}

	/** Its own parry landed: every block back. */
	static void parried(Mob mob) {
		long[] s = STATE.get(mob);
		if (s != null) {
			s[USED] = 0L;
			sync(mob, s);
		}
	}

	/** A player's parry (or a broken guard): all its blocks gone until it's left alone. */
	public static void broken(Mob mob) {
		long[] s = state(mob);
		s[USED] = s[TOTAL];
		// none caught till its guard mends (as a player's broken guard)
		s[BROKEN] = mob.level().getGameTime() + BROKEN_TICKS;
		s[HIT] = mob.level().getGameTime();
		sync(mob, s);
	}

	private static boolean inFront(Mob mob, Player player) {
		Vec3 to = new Vec3(player.getX() - mob.getX(), 0.0, player.getZ() - mob.getZ());
		if (to.lengthSqr() < 1.0E-4) {
			return true;
		}
		Vec3 facing = Vec3.directionFromRotation(0.0F, mob.getYHeadRot());
		return to.normalize().dot(facing) >= Math.cos(Math.toRadians(FRONT_DEGREES));
	}

	private static void show(ServerLevel level, Mob mob, Player player, int discipline) {
		Vec3 at = mob.getEyePosition().add(player.getEyePosition().subtract(mob.getEyePosition()).normalize().scale(0.6)).add(0.0, -0.3, 0.0);
		switch (discipline) {
			case Disciplines.KI -> {
				level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 8, 0.15, 0.2, 0.15, 0.02);
				level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 0.4F, 1.8F);
			}
			case Disciplines.ARCANA -> {
				level.sendParticles(ParticleTypes.ENCHANT, at.x, at.y, at.z, 24, 0.25, 0.25, 0.25, 0.6);
				level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.HOSTILE, 0.6F, 1.4F);
			}
			default -> {
				net.minecraft.world.InteractionHand hand = MobShields.guardHand(mob);
				ItemStack guard = hand != null ? mob.getItemInHand(hand) : mob.getMainHandItem();
				if (!PowersCompat.guardMet(level, mob, guard, player, 6, 0.8F, 2)) {
					level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 8, 0.15, 0.15, 0.15, 0.3);
				}
				level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 0.6F, 1.3F);
			}
		}
	}

	/** A clean hit: its swing cut short, a moment's reel. */
	private static void flinch(ServerLevel level, Mob mob) {
		MobShields.drop(mob);
		mob.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOWNESS, FLINCH, 2, false, false));
	}
}
