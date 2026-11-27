package com.jaymospowers.power.chrono;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.AbilityPower;
import com.jaymospowers.core.PickKit;
import com.jaymospowers.core.Powers;
import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.core.wounds.Downed;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Time skip (the skip key, in Overclock): the moment shatters. For {@link #TICKS} ticks every foe within {@link #RADIUS}
 * falls out of step: slowed hard ({@link #SLOW_AMPLIFIER}), and a creature's aim wobbles (its head and body are jolted a
 * few degrees every tick, and now and then it loses sight of who it was after). Foes wear {@link #OUT_OF_STEP}, which every
 * client draws with {@code ChronoSlowLook}'s trail of gold motes. The runner wears {@link #SKIPPING}: their own client pushes
 * them forward at speed and warps the screen ({@code ChronoSkipClient}, the {@code chrono_warp} post effect). Player foes
 * are slowed but their aim is their own, so it is not wobbled.
 */
public final class ChronoSkip {
	// ---- tuning ----
	/** How long the skip lasts (ticks), its recharge, and the reach (blocks, times the caster's size). */
	public static final int TICKS = 50, COOLDOWN = 280;
	static final double RADIUS = 12.0;
	/** The slow on foes (Slowness amplifier) and how long each refresh lasts (ticks). */
	static final int SLOW_AMPLIFIER = 4, SLOW_TICKS = 8;
	/** A creature's aim wobble: the most it is turned a tick (degrees), and the chance in this many ticks that it forgets its target. */
	static final float WOBBLE = 22.0F;
	static final int FORGET_ONE_IN = 12;
	static final int COLOR = 0xFFD27A;

	/** On the runner while the skip lasts, for their own client (the push and the screen warp). */
	public static final AttachmentType<Boolean> SKIPPING = AttachmentRegistry.create(JaymosPowers.id("chrono_skipping"),
		builder -> builder.syncWith(net.minecraft.network.codec.ByteBufCodecs.BOOL, AttachmentSyncPredicate.targetOnly()));
	/** On a foe fallen out of step, for every client (the trail of motes). */
	public static final AttachmentType<Boolean> OUT_OF_STEP = AttachmentRegistry.create(JaymosPowers.id("chrono_out_of_step"),
		builder -> builder.syncWith(net.minecraft.network.codec.ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));

	/** Client to server: the skip key was pressed. */
	public record Press() implements CustomPacketPayload {
		public static final Type<Press> TYPE = new Type<>(JaymosPowers.id("chrono_skip"));
		public static final StreamCodec<ByteBuf, Press> CODEC = StreamCodec.unit(new Press());

		@Override
		public Type<Press> type() {
			return TYPE;
		}
	}

	private static final class Skip {
		int left = TICKS;
		final List<LivingEntity> foes = new ArrayList<>();
	}

	private static final Map<UUID, Skip> SKIPS = new HashMap<>();
	private static final Map<UUID, Long> READY_AT = new HashMap<>();

	private ChronoSkip() {
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Press.TYPE, Press.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Press.TYPE, (payload, ctx) -> start(ctx.player()));
		ServerTickEvents.END_SERVER_TICK.register(ChronoSkip::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (UUID id : new ArrayList<>(SKIPS.keySet())) {
				ServerPlayer player = server.getPlayerList().getPlayer(id);
				end(id, player);
			}
			SKIPS.clear();
			READY_AT.clear();
		});
	}

	/** A runner leaves or loses the power: the skip ends and its recharge is forgotten. */
	static void forget(ServerPlayer player) {
		end(player.getUUID(), player);
		READY_AT.remove(player.getUUID());
	}

	private static void start(ServerPlayer player) {
		if (!player.isAlive() || player.isSpectator() || Downed.isDowned(player) || !Powers.has(player, ChronoPower.INSTANCE)
			|| !ChronoSpeed.overclocked(player) || SKIPS.containsKey(player.getUUID())) {
			return;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		if (now < READY_AT.getOrDefault(player.getUUID(), 0L)) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.chrono_skip.recharging"));
			return;
		}
		READY_AT.put(player.getUUID(), now + COOLDOWN);
		Skip skip = new Skip();
		double reach = AbilityPower.size(player, RADIUS);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(reach),
			e -> e != player && e.isAlive() && !e.isSpectator() && PickKit.enemy(player, e))) {
			if (e.distanceToSqr(player) <= reach * reach) {
				skip.foes.add(e);
				e.setAttached(OUT_OF_STEP, Boolean.TRUE);
			}
		}
		SKIPS.put(player.getUUID(), skip);
		player.setAttached(SKIPPING, Boolean.TRUE);
		com.jaymospowers.core.trials.PowerTrials.count(player, "chrono.skip", 1);
		Vec3 at = player.position().add(0.0, 0.2, 0.0);
		MagicFx.ring(level, at, 4.0F, COLOR);
		MagicFx.blast(level, player.position().add(0.0, 1.0, 0.0), 1.6F, 0xBFE8FF);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.0F, 1.8F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 0.9F, 0.6F);
	}

	private static void tick(MinecraftServer server) {
		if (SKIPS.isEmpty()) {
			return;
		}
		for (Iterator<Map.Entry<UUID, Skip>> it = SKIPS.entrySet().iterator(); it.hasNext();) {
			Map.Entry<UUID, Skip> entry = it.next();
			Skip skip = entry.getValue();
			ServerPlayer owner = server.getPlayerList().getPlayer(entry.getKey());
			if (owner == null || !owner.isAlive() || --skip.left <= 0 || !ChronoSpeed.overclocked(owner)) {
				it.remove();
				end(entry.getKey(), owner, skip);
				continue;
			}
			for (LivingEntity foe : skip.foes) {
				if (!foe.isAlive() || foe.isRemoved()) {
					continue;
				}
				foe.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SLOW_TICKS, SLOW_AMPLIFIER, false, false, false), owner);
				if (foe instanceof Mob mob && !(foe instanceof Player)) {
					wobble(mob, owner);
				}
			}
		}
	}

	/** A creature's aim goes astray: its head and body are jolted, and now and then it forgets whom it was after. */
	private static void wobble(Mob mob, ServerPlayer owner) {
		float jolt = (owner.getRandom().nextFloat() * 2.0F - 1.0F) * WOBBLE;
		mob.setYRot(mob.getYRot() + jolt);
		mob.setYHeadRot(mob.getYHeadRot() + jolt * 1.5F);
		mob.setXRot(mob.getXRot() + (owner.getRandom().nextFloat() - 0.5F) * WOBBLE);
		if (owner.getRandom().nextInt(FORGET_ONE_IN) == 0 && mob.getTarget() != null) {
			mob.setTarget(null);
		}
	}

	private static void end(UUID id, ServerPlayer owner) {
		Skip skip = SKIPS.remove(id);
		end(id, owner, skip);
	}

	private static void end(UUID id, ServerPlayer owner, Skip skip) {
		if (owner != null) {
			owner.removeAttached(SKIPPING);
		}
		if (skip != null) {
			for (LivingEntity foe : skip.foes) {
				foe.removeAttached(OUT_OF_STEP);
			}
		}
	}
}
