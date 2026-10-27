package com.jaymospowers.power.zfighter;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * (0.17.51, Andy's NOEA picks) A fusion partner for solo play. A good master ({@link ZFighterMasters}) who has taught him
 * {@link #LESSONS_NEEDED} lessons (the count the god form's ring keeps, {@link ZFighterGodRitual#lessons}) agrees to fuse. Stand
 * within {@link #RANGE} blocks of the master and tap Z, then C, with no partner dancing: the master answers the dance, and
 * when it ends the two are one for {@link #BASE_TICKS} (and {@link #PER_LESSON_TICKS} more for every lesson past the
 * first {@link #LESSONS_NEEDED}, up to {@link #LESSON_CAP}), with {@link #BONUS} on top of what the master adds: fists,
 * health, ki, ki power and toughness, each growing with the lessons ({@link #PER_LESSON}). The player steers the whole time;
 * the master has no player to watch from inside. For the length of the fusion the master NPC is <b>held</b>: invisible,
 * silent, unhurtable, unthinking and carried along with him (tagged {@link #HELD_TAG}), then set down beside him and restored
 * when it ends, or when the server stops. A held master found after a crash is restored as soon as he is loaded again.
 * The body is drawn as both of them where it can be (the master's looks are blended in on the client).
 */
public final class ZFighterMasterFusion {
	/** Good-master lessons needed before one agrees. */
	static final int LESSONS_NEEDED = 8;
	/** How close the master must be, and how far he may be when the dance ends. */
	static final double RANGE = 5.0;
	/** The fusion lasts this long (5 minutes), plus this much for each lesson past the first {@link #LESSONS_NEEDED}, counting no more than {@link #LESSON_CAP} of them. */
	static final int BASE_TICKS = 6000;
	static final int PER_LESSON_TICKS = 60;
	static final int LESSON_CAP = 40;
	/** What the master brings at the least, and how much more of it for each lesson taught (a fraction of the base). */
	static final double FISTS = 3.0, HEALTH = 20.0;
	static final int KI = 40;
	static final float POWER = 0.6F, TOUGHNESS = 0.08F;
	static final double PER_LESSON = 0.03;
	/** What the fused body has on top of that. */
	static final float BONUS = 0.08F;
	/** How often (ticks) the held master is carried along to the player. */
	static final int CARRY_EVERY = 2;
	static final String HELD_TAG = "jaymospowers_held_master";

	/** A master called to the dance: who, and when the dance ends. */
	private record Waiting(UUID master, long at) {
	}

	/** A master held out of the world for a fusion: who is carrying him. */
	private record Held(UUID leader) {
	}

	private static final Map<UUID, Waiting> WAITING = new HashMap<>();
	private static final Map<UUID, Held> HELD = new HashMap<>();

	private ZFighterMasterFusion() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(ZFighterMasterFusion::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			WAITING.clear();
			HELD.clear();
		});
		// a master still held from before a crash or a stop is set free as soon as he loads
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity.entityTags().contains(HELD_TAG) && !HELD.containsKey(entity.getUUID())) {
				free(entity);
			}
		});
	}

	/** Whether a master is held now (the masters' own upkeep waits for it). */
	static boolean holding() {
		return !HELD.isEmpty();
	}

	/**
	 * He danced alone (Z, then C): a good master within {@link #RANGE} answers if he has taught enough. Returns whether there was
	 * a master to answer (so the "waiting for a partner" hint is not shown).
	 */
	static boolean begin(ServerPlayer player, long now) {
		LivingEntity master = nearest(player);
		if (master == null) {
			return false;
		}
		if (!ZFighterGodRitual.goodHearted(player) || ZFighterKingdoms.rogue(player)) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_master_fuse_refuses", master.getDisplayName())
				.withStyle(ChatFormatting.AQUA));
			return true;
		}
		int lessons = ZFighterGodRitual.lessons(player);
		if (lessons < LESSONS_NEEDED) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_master_fuse_not_yet", master.getDisplayName(), lessons,
				LESSONS_NEEDED).withStyle(ChatFormatting.AQUA));
			return true;
		}
		WAITING.put(player.getUUID(), new Waiting(master.getUUID(), now + ZFighterFusion.DANCE_TICKS));
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_master_fuse_joins", master.getDisplayName())
			.withStyle(ChatFormatting.AQUA));
		return true;
	}

	/** The nearest good master within range that is not already held. */
	private static LivingEntity nearest(ServerPlayer player) {
		List<LivingEntity> masters = player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(RANGE),
			e -> e.isAlive() && e.entityTags().contains(ZFighterMasters.TAG) && !HELD.containsKey(e.getUUID()));
		LivingEntity best = null;
		for (LivingEntity master : masters) {
			if (best == null || master.distanceToSqr(player) < best.distanceToSqr(player)) {
				best = master;
			}
		}
		return best;
	}

	private static void tick(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		for (Iterator<Map.Entry<UUID, Waiting>> it = WAITING.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Waiting> entry = it.next();
			if (now < entry.getValue().at()) {
				continue;
			}
			it.remove();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			Entity master = player == null ? null : player.level().getEntity(entry.getValue().master());
			if (player != null && master instanceof LivingEntity living && living.isAlive() && ZFighterFusion.fusable(player)
				&& player.distanceTo(master) <= RANGE + 2.0 && !HELD.containsKey(master.getUUID())) {
				fuse(player, living);
			}
		}
		if (HELD.isEmpty() || now % CARRY_EVERY != 0) {
			return;
		}
		for (Iterator<Map.Entry<UUID, Held>> it = HELD.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Held> entry = it.next();
			ServerPlayer leader = server.getPlayerList().getPlayer(entry.getValue().leader());
			Entity master = find(server, entry.getKey(), leader);
			if (leader == null || master == null || ZFighterFusion.of(leader) == null) {
				// the fusion is gone (or the master is): set him free where he is
				it.remove();
				if (master != null) {
					free(master);
				}
				continue;
			}
			carry(master, leader);
		}
	}

	/** The held master by his id: in the player's world first, then anywhere. */
	private static Entity find(MinecraftServer server, UUID id, ServerPlayer leader) {
		if (leader != null) {
			Entity here = leader.level().getEntity(id);
			if (here != null) {
				return here;
			}
		}
		for (ServerLevel level : server.getAllLevels()) {
			Entity there = level.getEntity(id);
			if (there != null) {
				return there;
			}
		}
		return null;
	}

	/** He rides along inside the player's body, out of the way. */
	private static void carry(Entity master, ServerPlayer leader) {
		if (master.level() != leader.level() || master.distanceToSqr(leader) > 1.0) {
			master.teleportTo(leader.level(), leader.getX(), leader.getY(), leader.getZ(), Set.of(), leader.getYRot(), 0.0F, false);
		}
	}

	/** The dance is done: he holds the master, and the two are one. */
	private static void fuse(ServerPlayer player, LivingEntity master) {
		int lessons = ZFighterGodRitual.lessons(player);
		double grown = 1.0 + PER_LESSON * Math.min(lessons, LESSON_CAP);
		ZFighterFusion.Stats stats = new ZFighterFusion.Stats(FISTS * grown, HEALTH * grown, (int)(KI * grown), (float)(POWER * grown), (float)(TOUGHNESS * grown));
		int length = BASE_TICKS + PER_LESSON_TICKS * Mth.clamp(lessons - LESSONS_NEEDED, 0, LESSON_CAP);
		ZFighterFusion.begin(player, List.of(), ZFighterFusion.Kind.MASTER, length, BONUS, stats);
		hold(master, player);
		ZFighterFusion.withMaster(player, master.getUUID(), new ZFighterFusion.Look(player.getUUID(), master.getUUID(), 0, 0));
		ZFighterPower.Live live = ZFighterPower.live(player);
		live.ki = Math.min(ZFighterPower.maxKi(player), live.ki + stats.ki() * 0.5F);
		ZFighterPower.refresh(player, live);
		Component message = Component.translatable("message.jaymospowers.zfighter_fused", player.getDisplayName(), master.getDisplayName())
			.withStyle(ChatFormatting.GOLD);
		for (ServerPlayer near : player.level().players()) {
			if (near.distanceToSqr(player) < 64.0 * 64.0) {
				near.sendSystemMessage(message);
			}
		}
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_fusion_timed", (length + 1199) / 1200).withStyle(ChatFormatting.AQUA));
	}

	/** Hidden, silent, unhurtable, unthinking, with no body to bump into: carried along. */
	private static void hold(LivingEntity master, ServerPlayer leader) {
		HELD.put(master.getUUID(), new Held(leader.getUUID()));
		master.addTag(HELD_TAG);
		master.setInvisible(true);
		master.setSilent(true);
		master.setPermanentlyInvulnerable(true);
		master.setNoGravity(true);
		master.noPhysics = true;
		if (master instanceof Mob mob) {
			mob.setNoAi(true);
			mob.getNavigation().stop();
		}
		carry(master, leader);
	}

	/** Back as he was (a plain master: visible, loud, thinking, hurtable, falling). */
	private static void free(Entity master) {
		master.removeTag(HELD_TAG);
		master.setInvisible(false);
		master.setSilent(false);
		master.setPermanentlyInvulnerable(false);
		master.setNoGravity(false);
		master.noPhysics = false;
		if (master instanceof Mob mob) {
			mob.setNoAi(false);
		}
	}

	/** The fusion ended: the master is set down beside the player (or where he is, if the player is gone) and restored. */
	static void release(MinecraftServer server, UUID masterId, ServerPlayer leader) {
		HELD.remove(masterId);
		Entity master = find(server, masterId, leader);
		if (master == null) {
			return;
		}
		free(master);
		if (leader != null) {
			Vec3 side = leader.getLookAngle().multiply(1.0, 0.0, 1.0).normalize().cross(new Vec3(0.0, 1.0, 0.0));
			master.teleportTo(leader.level(), leader.getX() + side.x * 1.5, leader.getY(), leader.getZ() + side.z * 1.5, Set.of(), leader.getYRot() + 180.0F,
				0.0F, false);
			leader.level().sendParticles(ParticleTypes.END_ROD, true, false, master.getX(), master.getY() + 1.0, master.getZ(), 30, 0.4, 0.8, 0.4, 0.1);
			leader.level().playSound(null, master.getX(), master.getY(), master.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.NEUTRAL, 1.0F, 0.9F);
		}
	}
}
