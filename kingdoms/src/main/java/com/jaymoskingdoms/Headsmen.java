package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Crowned Sun's headsmen (Lore.md, "The dawnwatch and the headsmen"; Andy asked for them, 2026-11-15). They carry out
 * the crown's sentences in black plate edged with gold (Fantasy Armor's Golden Execution, {@link FantasyArmorCompat#HEADSMAN}),
 * and a headsman's plate goes dull if he goes too long without his work.
 * <ul>
 * <li><b>Who.</b> Each realm whose state patron is the Crowned Sun keeps one at its seat: the guard nearest the seat is
 * named ({@link #TAG}) when a player is about and it has none, and another when he dies. Where Fantasy Armor is loaded he
 * wears the whole set on duty and off ({@link FantasyArmorCompat#issue}, {@link FantasyArmorCompat#keeps}); without it he is a guard like the rest.</li>
 * <li><b>His work.</b> In his realm a sentence of death at an audience ({@link Arrests}) is his when he is within
 * {@link #CALL} blocks: he walks to the one sentenced and the blow is his ({@link #strike}).</li>
 * <li><b>The dull plate.</b> {@link #DULL_DAYS} days after his last execution his plate is dull: its Strength is gone and he
 * is slower. A player in the whole set is the same unless they have killed a person (a villager, an illager, a player,
 * or one of the kingdoms' own folk) in the last {@link #DULL_DAYS} days. Nothing on screen says so.</li>
 * <li><b>The thief's faith.</b> One sworn to the Ash Whisperer who comes near a headsman is spat at
 * ({@link #SPIT_RANGE}): the Crowned Sun's people hang hers when they are caught.</li>
 * <li><b>Never sold.</b> A headsman lets one piece of his plate fall when he dies.</li>
 * </ul>
 */
public final class Headsmen {
	public static final String TAG = "jaymoskingdoms.headsman";
	/** How often the Sun's seats near players are looked over (ticks), how near a player has to be, and how near the seat a guard must be to be named. */
	static final int SWEEP = 200;
	static final double PLAYER_NEAR = 96.0, SEAT_RANGE = 48.0;
	/** How far from the seat a named headsman is still looked for (out on rounds), so a second one isn't named. */
	static final double HEADSMAN_RANGE = 128.0;
	/** How near the sentenced one he has to be to be called to it, how near he strikes from, and how long he's waited for (ticks). */
	static final double CALL = 32.0, STRIKE_REACH = 2.5;
	static final int CALL_WAIT = 200, CALL_STEP = 10;
	static final double WALK_SPEED = 0.9;
	/** Days without an execution before the plate is dull. */
	static final int DULL_DAYS = 3;
	/** How near a sworn of the Ash Whisperer comes before a headsman spits, and how long before he spits at them again (ticks). */
	static final double SPIT_RANGE = 6.0;
	static final long SPIT_EVERY = 2400;
	/** The Strength Fantasy Armor's set gives lasts at most this long (ticks): what a dull plate takes away. */
	static final int SET_STRENGTH = 300;
	static final long DAY = 24000L;

	/** On a headsman, and on a player: the game time of their last execution (or kill). Saved. */
	static final AttachmentType<Long> LAST_WORK = AttachmentRegistry.create(JaymosKingdoms.id("headsman_last_work"),
		builder -> builder.persistent(Codec.LONG).copyOnDeath());
	/** On a headsman: when he last spat at the Ash Whisperer's own. */
	static final AttachmentType<Long> SPAT = AttachmentRegistry.create(JaymosKingdoms.id("headsman_spat"));

	private Headsmen() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % SWEEP == 0) {
				sweep(server);
			}
			if (server.getTickCount() % 20 == 0) {
				for (ServerPlayer player : server.getPlayerList().getPlayers()) {
					if (FantasyArmorCompat.whole(player, FantasyArmorCompat.HEADSMAN) && dull(player)) {
						blunt(player);
					}
				}
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (!(entity.level() instanceof ServerLevel level)) {
				return;
			}
			if (source.getEntity() instanceof Player killer && person(entity)) {
				killer.setAttached(LAST_WORK, level.getGameTime());
			}
			if (entity.entityTags().contains(TAG)) {
				// never sold: one piece of his plate falls with him
				int slot = entity.getRandom().nextInt(4);
				FantasyArmorCompat.piece(FantasyArmorCompat.HEADSMAN, slot).ifPresent(item -> entity.spawnAtLocation(level, new ItemStack(item)));
			}
		});
	}

	/** A person, as a headsman counts his work: a villager, an illager or a player. */
	private static boolean person(LivingEntity e) {
		return e instanceof AbstractVillager || e instanceof AbstractIllager || e instanceof Player || e instanceof Folk;
	}

	/** {@code e}'s last work was more than {@link #DULL_DAYS} days ago (one who never did any is dull). */
	static boolean dull(LivingEntity e) {
		Long last = e.getAttached(LAST_WORK);
		return last == null || e.level().getGameTime() - last > DULL_DAYS * DAY;
	}

	/** A dull plate: the Strength its set gives is gone. */
	private static void blunt(LivingEntity e) {
		MobEffectInstance strength = e.getEffect(MobEffects.STRENGTH);
		if (strength != null && strength.getAmplifier() == 0 && !strength.isInfiniteDuration() && strength.getDuration() <= SET_STRENGTH) {
			e.removeEffect(MobEffects.STRENGTH);
		}
	}

	/** Each of the Crowned Sun's seats with a player near: its headsman named if it has none, dulled if idle, spitting at the Ash's own. */
	private static void sweep(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		for (Faction f : Factions.all(server)) {
			if (!f.type().isRealm() || RealmFaiths.patron(server, f) != Patron.CROWNED_SUN) {
				continue;
			}
			BlockPos seat = f.seat();
			Vec3 at = Vec3.atBottomCenterOf(seat);
			if (level.getNearestPlayer(at.x, at.y, at.z, PLAYER_NEAR, false) == null) {
				continue;
			}
			PathfinderMob headsman = null;
			for (Worker g : Workers.near(level, new AABB(seat).inflate(HEADSMAN_RANGE),
					g -> g.mob().isAlive() && g.factionId() == f.id() && g.mob().entityTags().contains(TAG))) {
				if (headsman == null) {
					headsman = g.mob();
				} else {
					g.mob().removeTag(TAG); // one headsman to a seat
				}
			}
			List<Worker> guards = Workers.near(level, new AABB(seat).inflate(SEAT_RANGE),
				g -> g.mob().isAlive() && g.factionId() == f.id() && g.role() == Folk.GUARD);
			if (headsman == null && !guards.isEmpty()) {
				guards.sort(java.util.Comparator.comparingDouble(g -> g.mob().distanceToSqr(at)));
				headsman = guards.get(0).mob();
				headsman.addTag(TAG);
				headsman.setPersistenceRequired();
				// (a new man is keen: his plate isn't dull the day he's named)
				headsman.setAttached(LAST_WORK, level.getGameTime());
				FantasyArmorCompat.dress(headsman, FantasyArmorCompat.HEADSMAN);
			}
			if (headsman == null) {
				continue;
			}
			// (without the whole plate on he is a guard like the rest)
			if (FantasyArmorCompat.whole(headsman, FantasyArmorCompat.HEADSMAN) && dull(headsman)) {
				blunt(headsman);
				headsman.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SWEEP + 20, 0, true, false));
			}
			spit(level, headsman);
		}
	}

	/** The headsman spits at a sworn of the Ash Whisperer near him (now and then). */
	private static void spit(ServerLevel level, PathfinderMob headsman) {
		Long last = headsman.getAttached(SPAT);
		if (last != null && level.getGameTime() - last < SPIT_EVERY) {
			return;
		}
		for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, headsman.getBoundingBox().inflate(SPIT_RANGE))) {
			if (Patrons.of(p) == Patron.ASH_WHISPERER) {
				headsman.getLookControl().setLookAt(p, 30.0F, 30.0F);
				level.playSound(null, headsman.getX(), headsman.getY(), headsman.getZ(), SoundEvents.LLAMA_SPIT, SoundSource.NEUTRAL, 0.8F, 0.8F);
				level.playSound(null, headsman.getX(), headsman.getY(), headsman.getZ(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 0.8F, 0.7F);
				headsman.setAttached(SPAT, level.getGameTime());
				return;
			}
		}
	}

	/** {@code f}'s headsman within {@link #CALL} blocks of {@code crook}, or null (outside the Crowned Sun's realms, or none near). */
	static @Nullable PathfinderMob of(ServerLevel level, Faction f, LivingEntity crook) {
		if (RealmFaiths.patron(level.getServer(), f) != Patron.CROWNED_SUN) {
			return null;
		}
		List<Worker> near = Workers.near(level, crook.getBoundingBox().inflate(CALL),
			g -> g.mob().isAlive() && g.factionId() == f.id() && g.mob().entityTags().contains(TAG));
		return near.isEmpty() ? null : near.get(0).mob();
	}

	/**
	 * (from {@link Arrests}, a sentence of death) The headsman comes to {@code crook}: true while he is still on his way
	 * (call again after {@link #CALL_STEP} ticks), false once he is there to strike, or has been waited for long enough.
	 */
	static boolean coming(PathfinderMob headsman, LivingEntity crook, int waited) {
		if (headsman.distanceToSqr(crook) <= STRIKE_REACH * STRIKE_REACH || waited >= CALL_WAIT) {
			headsman.getNavigation().stop();
			return false;
		}
		headsman.getNavigation().moveTo(crook, WALK_SPEED);
		return true;
	}

	/** The headsman has carried out a sentence: his plate is bright again. */
	static void strike(ServerLevel level, PathfinderMob headsman) {
		headsman.setAttached(LAST_WORK, level.getGameTime());
	}
}
