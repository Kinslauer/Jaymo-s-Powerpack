package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Powers;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * The fusion dance. Two Z Fighters face each other a few blocks apart; each taps Z, then presses C within two seconds,
 * and dances (arms swept out to one side, over, then fingertips pointed at each other). When the second one's dance
 * ends, they become one for {@link #FUSION_TICKS}: the first to dance leads and steers; the other watches from inside
 * (a spectator riding his camera). The fused fighter has both their fists, health, ki and ki power added together, and
 * {@link #FUSION_BONUS} more. Either may then not fuse again for {@link #COOLDOWN}.
 */
public final class ZFighterFusion {
	/** Z, then C within this long, starts the dance; the partner's dance must start within this long of yours. */
	static final int DANCE_WINDOW = 40;
	public static final int DANCE_TICKS = 40;
	static final double DANCE_RANGE = 6.0;
	/** How squarely they must face each other (the dot of their flat looks, at most). */
	static final double FACING = -0.5;
	static final int FUSION_TICKS = 1200;
	static final float FUSION_BONUS = 0.1F;
	static final long COOLDOWN = 12000L;

	/** Which way the dancer sweeps (1 right, -1 left), while dancing; everyone's client poses it. */
	public static final AttachmentType<Integer> DANCE = AttachmentRegistry.create(JaymosPowers.id("zfighter_dance"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));
	/** On the one watching from inside: the game mode to give back (kept across a log-out or a crash). */
	public static final AttachmentType<Integer> RESTORE = AttachmentRegistry.create(JaymosPowers.id("zfighter_fusion_restore"),
		builder -> builder.persistent(Codec.INT));
	/** When he last fused (game time). */
	public static final AttachmentType<Long> LAST = AttachmentRegistry.create(JaymosPowers.id("zfighter_fusion_last"),
		builder -> builder.persistent(Codec.LONG));

	private record Dance(long started, int side, long ends) {
	}

	private record Pending(UUID leader, UUID partner, long at) {
	}

	/** What the partner adds to the leader, taken when they fuse. */
	record Fused(UUID partner, long until, double fists, double health, int ki, float power, float toughness) {
	}

	private static final Map<UUID, Long> Z_TAPS = new HashMap<>();
	private static final Map<UUID, Dance> DANCES = new HashMap<>();
	private static final List<Pending> PENDING = new ArrayList<>();
	private static final Map<UUID, Fused> FUSED = new HashMap<>();

	private ZFighterFusion() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(ZFighterFusion::tick);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> giveBack(handler.player, null)));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			UUID id = handler.player.getUUID();
			if (FUSED.containsKey(id)) {
				defuse(server, id);
			}
			for (Map.Entry<UUID, Fused> entry : new ArrayList<>(FUSED.entrySet())) {
				if (entry.getValue().partner().equals(id)) {
					defuse(server, entry.getKey());
				}
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (UUID leader : new ArrayList<>(FUSED.keySet())) {
				defuse(server, leader);
			}
			Z_TAPS.clear();
			DANCES.clear();
			PENDING.clear();
		});
	}

	/** Z pressed: remembered, in case C follows. */
	static void tappedZ(ServerPlayer player) {
		Z_TAPS.put(player.getUUID(), player.level().getGameTime());
	}

	/** C pressed: if Z came just before, he dances (and C does nothing else). */
	static boolean tryDance(ServerPlayer player, ZFighterPower.Live live) {
		long now = player.level().getGameTime();
		Long z = Z_TAPS.remove(player.getUUID());
		if (z == null || now - z > DANCE_WINDOW || live.flying || !player.onGround() || FUSED.containsKey(player.getUUID())) {
			return false;
		}
		Long last = player.getAttached(LAST);
		if (last != null && now - last < COOLDOWN && !player.isCreative()) {
			long minutes = (COOLDOWN - (now - last) + 1199L) / 1200L;
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_fusion_tired", minutes).withStyle(ChatFormatting.GRAY));
			return true;
		}
		ServerPlayer partner = partner(player, now);
		int side = partner == null ? -1 : -DANCES.get(partner.getUUID()).side();
		DANCES.put(player.getUUID(), new Dance(now, side, now + DANCE_TICKS));
		player.setAttached(DANCE, side);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.2F);
		if (partner == null) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_fusion_waiting").withStyle(ChatFormatting.AQUA));
		} else {
			PENDING.add(new Pending(partner.getUUID(), player.getUUID(), now + DANCE_TICKS));
		}
		return true;
	}

	/** Another Z Fighter dancing near, facing him, who started within the window. */
	private static ServerPlayer partner(ServerPlayer player, long now) {
		for (ServerPlayer other : player.level().players()) {
			Dance dance = DANCES.get(other.getUUID());
			if (other == player || dance == null || now - dance.started() > DANCE_WINDOW || !Powers.has(other, ZFighterPower.INSTANCE)
				|| other.distanceTo(player) > DANCE_RANGE || FUSED.containsKey(other.getUUID())) {
				continue;
			}
			Vec3 a = player.getLookAngle().multiply(1.0, 0.0, 1.0).normalize();
			Vec3 b = other.getLookAngle().multiply(1.0, 0.0, 1.0).normalize();
			if (a.dot(b) <= FACING) {
				return other;
			}
		}
		return null;
	}

	private static void tick(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		for (Iterator<Map.Entry<UUID, Dance>> it = DANCES.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Dance> entry = it.next();
			if (now >= entry.getValue().ends()) {
				ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
				if (player != null) {
					player.removeAttached(DANCE);
				}
				it.remove();
			}
		}
		for (Iterator<Pending> it = PENDING.iterator(); it.hasNext(); ) {
			Pending pending = it.next();
			if (now < pending.at()) {
				continue;
			}
			it.remove();
			ServerPlayer leader = server.getPlayerList().getPlayer(pending.leader());
			ServerPlayer partner = server.getPlayerList().getPlayer(pending.partner());
			if (leader != null && partner != null && leader.isAlive() && partner.isAlive() && leader.level() == partner.level()
				&& leader.distanceTo(partner) <= DANCE_RANGE + 2.0 && !FUSED.containsKey(leader.getUUID()) && !FUSED.containsKey(partner.getUUID())
				&& !ZFighterLineage.ape(leader) && !ZFighterLineage.ape(partner)) {
				fuse(leader, partner);
			}
		}
		for (UUID id : new ArrayList<>(FUSED.keySet())) {
			Fused fused = FUSED.get(id);
			ServerPlayer leader = server.getPlayerList().getPlayer(id);
			ServerPlayer partner = server.getPlayerList().getPlayer(fused.partner());
			if (leader == null || partner == null || !leader.isAlive() || now >= fused.until()) {
				defuse(server, id);
				continue;
			}
			// the partner rides along inside, seeing what the leader sees
			if (partner.level() != leader.level() || partner.distanceToSqr(leader) > 4.0) {
				partner.teleportTo(leader.level(), leader.getX(), leader.getY(), leader.getZ(), Set.of(), leader.getYRot(), leader.getXRot(), false);
			}
			if (partner.getCamera() != leader) {
				partner.setCamera(leader);
			}
			if (now % 10 == 0) {
				ServerLevel level = leader.level();
				level.sendParticles(new DustParticleOptions(0xFFD040, 1.6F), leader.getX(), leader.getY() + 1.0, leader.getZ(), 6, 0.4, 0.8, 0.4, 0.0);
				level.sendParticles(new DustParticleOptions(0x40E0D0, 1.6F), leader.getX(), leader.getY() + 1.0, leader.getZ(), 6, 0.4, 0.8, 0.4, 0.0);
			}
			if (fused.until() - now == 200) {
				leader.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_fusion_ending").withStyle(ChatFormatting.GOLD));
			}
		}
	}

	private static void fuse(ServerPlayer leader, ServerPlayer partner) {
		long now = leader.level().getGameTime();
		ZFighterPower.Live partnerLive = ZFighterPower.live(partner);
		Fused fused = new Fused(partner.getUUID(), now + FUSION_TICKS, ZFighterPower.fistBonus(partner), partner.getMaxHealth(),
			ZFighterPower.maxKi(partner), ZFighterPower.kiPower(partner, partnerLive), ZFighterTraining.toughness(partner));
		FUSED.put(leader.getUUID(), fused);
		leader.setAttached(LAST, now);
		partner.setAttached(LAST, now);
		ZFighterPower.Live live = ZFighterPower.live(leader);
		live.ki = Math.min(ZFighterPower.maxKi(leader), live.ki + partnerLive.ki);
		partnerLive.ki = 0.0F;
		partnerLive.charging = false;
		partner.setAttached(RESTORE, partner.gameMode().getId());
		partner.setGameMode(GameType.SPECTATOR);
		partner.setCamera(leader);
		ZFighterPower.refresh(leader, live);
		leader.setHealth(leader.getMaxHealth());
		ServerLevel level = leader.level();
		level.sendParticles(net.minecraft.core.particles.ColorParticleOption.create(ParticleTypes.FLASH, 0xFFFFFFFF), leader.getX(), leader.getY() + 1.0, leader.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
		level.sendParticles(ParticleTypes.END_ROD, true, false, leader.getX(), leader.getY() + 1.0, leader.getZ(), 80, 1.5, 1.5, 1.5, 0.2);
		level.playSound(null, leader.getX(), leader.getY(), leader.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 2.0F, 0.7F);
		level.playSound(null, leader.getX(), leader.getY(), leader.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.5F, 1.6F);
		Component message = Component.translatable("message.jaymospowers.zfighter_fused", leader.getDisplayName(), partner.getDisplayName())
			.withStyle(ChatFormatting.GOLD);
		for (ServerPlayer near : level.players()) {
			if (near.distanceToSqr(leader) < 64.0 * 64.0) {
				near.sendSystemMessage(message);
			}
		}
	}

	/** The fusion ends: the leader loses what the partner gave, and the partner steps out beside him. */
	private static void defuse(MinecraftServer server, UUID leaderId) {
		Fused fused = FUSED.remove(leaderId);
		if (fused == null) {
			return;
		}
		ServerPlayer leader = server.getPlayerList().getPlayer(leaderId);
		ServerPlayer partner = server.getPlayerList().getPlayer(fused.partner());
		if (leader != null) {
			ZFighterPower.Live live = ZFighterPower.live(leader);
			ZFighterPower.refresh(leader, live);
			live.ki = Math.min(live.ki, ZFighterPower.maxKi(leader));
			leader.setHealth(Math.min(leader.getHealth(), leader.getMaxHealth()));
			leader.level().sendParticles(net.minecraft.core.particles.ColorParticleOption.create(ParticleTypes.FLASH, 0xFFFFFFFF), leader.getX(), leader.getY() + 1.0, leader.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
			leader.level().playSound(null, leader.getX(), leader.getY(), leader.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.5F, 1.0F);
			leader.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_unfused").withStyle(ChatFormatting.GOLD));
		}
		if (partner != null) {
			giveBack(partner, leader);
		}
	}

	/** The partner back in his own body (on joining, too, if a fusion was cut short by a log-out or a crash). */
	private static void giveBack(ServerPlayer partner, ServerPlayer leader) {
		Integer mode = partner.getAttached(RESTORE);
		if (mode == null) {
			return;
		}
		partner.removeAttached(RESTORE);
		partner.setCamera(partner);
		if (leader != null) {
			Vec3 side = leader.getLookAngle().multiply(1.0, 0.0, 1.0).normalize().cross(new Vec3(0.0, 1.0, 0.0));
			partner.teleportTo(leader.level(), leader.getX() + side.x * 1.5, leader.getY(), leader.getZ() + side.z * 1.5, Set.of(), leader.getYRot(), 0.0F,
				false);
		}
		partner.setGameMode(GameType.byId(mode));
	}

	static Fused of(ServerPlayer player) {
		return FUSED.get(player.getUUID());
	}

	// ---------------------------------------------------------------- what fusing adds (read by ZFighterPower and ZFighterBattle)

	static double fists(ServerPlayer player) {
		Fused fused = of(player);
		return fused == null ? 0.0 : fused.fists();
	}

	static double health(ServerPlayer player) {
		Fused fused = of(player);
		return fused == null ? 0.0 : fused.health();
	}

	static double bonus(ServerPlayer player) {
		return of(player) == null ? 0.0 : FUSION_BONUS;
	}

	static int ki(ServerPlayer player) {
		Fused fused = of(player);
		return fused == null ? 0 : fused.ki();
	}

	static float power(ServerPlayer player, float own) {
		Fused fused = of(player);
		return fused == null ? own : (own + fused.power()) * (1.0F + FUSION_BONUS);
	}

	/** Damage taken, after the partner's toughness too. */
	static float toughness(ServerPlayer player, float damage) {
		Fused fused = of(player);
		return fused == null ? damage : damage * (1.0F - fused.toughness());
	}

	/** Attribute ids for {@link ZFighterPower#refresh}. */
	static final net.minecraft.resources.Identifier FIST_ID = JaymosPowers.id("zfighter_fusion_fists");
	static final net.minecraft.resources.Identifier BONUS_ID = JaymosPowers.id("zfighter_fusion_bonus");
	static final net.minecraft.resources.Identifier HEALTH_ID = JaymosPowers.id("zfighter_fusion_health");
}
