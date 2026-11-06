package com.jaymospowers.combat;

import com.jaymospowers.core.wounds.Downed;
import com.jaymospowers.freerun.Freerun;
import com.jaymospowers.power.darktide.DarktideStatus;
import com.jaymospowers.power.web.WebParkour;
import io.netty.buffer.ByteBuf;
import com.jaymospowers.JaymosPowers;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * (0.21.21, Kingdoms Rivals patch 8) Vault over a shield (the Arkham games' idea; ideas only). Andy: "the input should be
 * jump plus alt key or roll bind in mid air". Jump, then press Dodge (Left Alt) in the air, with a foe who has his shield
 * up ({@link Shields#raised}) close and in front of you, and you go up and over him (the client pushes you at once,
 * {@code DodgeClient}, and tells the server, {@link Ask}), a hand on his shoulder, and
 * land behind him; he reels for a beat ({@link #STUN_TICKS}) with his back to you. Not one much bigger than you
 * ({@link #TOO_TALL}, {@link #TOO_WIDE}): an ogre's shield is a wall, and you're told so. The vault's look is Freerun's
 * ({@code WebParkourPose}).
 */
public final class ShieldVault {
	// ================================================================================================================
	// Tuning
	/** How near (blocks, flat) and how nearly ahead (degrees either side) the shield must be when you jump. */
	public static final double REACH = 3.0;
	public static final float AHEAD = 40.0F;
	/** Too big to vault: taller than you times this, or wider than you times this. */
	public static final float TOO_TALL = 1.35F, TOO_WIDE = 2.2F;
	/** Where you come down: this far past him (blocks); the flat distance an air run covers per unit of push. */
	public static final double LAND_BEYOND = 1.6, AIR_SPAN = 8.0;
	/** The upward push: this plus this much per block of his height, kept between the two. */
	public static final double UP_BASE = 0.35, UP_PER_HEIGHT = 0.15, UP_MIN = 0.55, UP_MAX = 0.78;
	/** He reels this long when you land behind him (ticks). */
	public static final int STUN_TICKS = 20;
	/** A vault ends when you land, or after this long (ticks); and none again for this long. */
	public static final int MAX_TICKS = 30, COOLDOWN = 20;
	/** The server allows this much more reach (blocks) than the client looked in (it sees you a tick or two late). */
	public static final double SERVER_SLACK = 1.0;
	// ================================================================================================================

	/** Client → server: Dodge pressed in the air over the shield of the entity {@code foe}. */
	public record Ask(int foe) implements CustomPacketPayload {
		public static final Type<Ask> TYPE = new Type<>(JaymosPowers.id("shield_vault"));
		public static final StreamCodec<ByteBuf, Ask> CODEC = ByteBufCodecs.VAR_INT.map(Ask::new, Ask::foe);

		@Override
		public Type<Ask> type() {
			return TYPE;
		}
	}

	/** A vault going on: over whom, since when, the pose it set. */
	private record Vault(LivingEntity foe, long since, int move) {
	}

	private static final Map<UUID, Vault> VAULTS = new HashMap<>();
	private static final Map<UUID, Long> NEXT = new HashMap<>();

	private ShieldVault() {
	}

	public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(Ask.TYPE, Ask.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Ask.TYPE, (payload, ctx) -> asked(ctx.player(), payload.foe()));
		ServerTickEvents.END_SERVER_TICK.register(ShieldVault::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			VAULTS.clear();
			NEXT.clear();
		});
	}

	private static void tick(MinecraftServer server) {
		if (VAULTS.isEmpty()) {
			return;
		}
		for (UUID id : List.copyOf(VAULTS.keySet())) {
			ServerPlayer player = server.getPlayerList().getPlayer(id);
			Vault vault = VAULTS.get(id);
			if (player == null) {
				VAULTS.remove(id);
				continue;
			}
			long ran = player.level().getGameTime() - vault.since();
			if (ran > 2 && player.onGround() || ran > MAX_TICKS || !player.isAlive()) {
				land(player, vault);
			}
		}
	}

	/** Whether {@code player} may vault now: in the air, not riding, gliding, downed or mid-freerun. Either side. */
	public static boolean canVault(Player player) {
		return !player.onGround() && !player.isSpectator() && !player.isPassenger() && !player.isFallFlying() && !player.isInWater()
			&& !player.getAbilities().flying && !Downed.isDowned(player) && !player.hasAttached(Freerun.MOVE);
	}

	/** The nearest foe with his shield up, close and ahead of {@code player} ({@code slack}: extra reach allowed), or null. Either side. */
	public static @Nullable LivingEntity target(Player player, double slack) {
		Vec3 ahead = Vec3.directionFromRotation(0.0F, player.getYRot());
		LivingEntity foe = null;
		double best = Double.MAX_VALUE;
		for (LivingEntity e : player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(REACH + 1.0 + slack),
			e -> e != player && e.isAlive() && !e.isSpectator() && Shields.raised(e))) {
			Vec3 to = e.position().subtract(player.position()).multiply(1.0, 0.0, 1.0);
			double d = to.length();
			if (d > REACH + slack + e.getBbWidth() * 0.5 || d < 0.01) {
				continue;
			}
			double angle = Math.toDegrees(Math.acos(Mth.clamp(to.normalize().dot(ahead), -1.0, 1.0)));
			if (angle <= AHEAD + slack * 10.0 && d < best) {
				foe = e;
				best = d;
			}
		}
		return foe;
	}

	/** Whether {@code foe} is too big for {@code player} to vault. Either side. */
	public static boolean tooBig(Player player, LivingEntity foe) {
		return foe.getBbHeight() > player.getBbHeight() * TOO_TALL || foe.getBbWidth() > player.getBbWidth() * TOO_WIDE;
	}

	/** The push that carries {@code player} up and over {@code foe} to land past him. Either side (the client moves itself). */
	public static Vec3 push(Player player, LivingEntity foe) {
		Vec3 to = foe.position().subtract(player.position()).multiply(1.0, 0.0, 1.0);
		Vec3 flat = to.normalize().scale((to.length() + LAND_BEYOND) / AIR_SPAN);
		return new Vec3(flat.x, Mth.clamp(UP_BASE + UP_PER_HEIGHT * foe.getBbHeight(), UP_MIN, UP_MAX), flat.z);
	}

	/** The client vaulted (or tried) over {@code id}'s shield: checked, then the look, his guard broken, and the landing watched. */
	private static void asked(ServerPlayer player, int id) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		if (!(level.getEntity(id) instanceof LivingEntity foe) || now < NEXT.getOrDefault(player.getUUID(), 0L) || VAULTS.containsKey(player.getUUID())
			|| player.isSpectator() || Downed.isDowned(player) || player.isPassenger() || foe == player || !foe.isAlive() || !Shields.raised(foe)
			|| foe.distanceTo(player) > REACH + SERVER_SLACK + foe.getBbWidth() + 1.0) {
			return;
		}
		NEXT.put(player.getUUID(), now + COOLDOWN);
		if (tooBig(player, foe)) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.shield_vault.too_big", foe.getDisplayName()).withStyle(ChatFormatting.GRAY));
			return;
		}
		player.resetFallDistance();
		int move = (player.getRandom().nextInt(WebParkour.VARIANTS) * 8 + Freerun.VAULT) * 8;
		player.setAttached(Freerun.MOVE, move);
		VAULTS.put(player.getUUID(), new Vault(foe, now, move));
		// (his guard's no use to him now)
		foe.stopUsingItem();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.5F, 1.4F);
		level.playSound(null, foe.getX(), foe.getY(), foe.getZ(), SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.PLAYERS, 0.8F, 0.8F);
	}

	/** Down again: if behind him, he reels. */
	private static void land(ServerPlayer player, Vault vault) {
		VAULTS.remove(player.getUUID());
		Integer move = player.getAttached(Freerun.MOVE);
		if (move != null && move == vault.move()) {
			player.removeAttached(Freerun.MOVE);
		}
		LivingEntity foe = vault.foe();
		if (!player.isAlive() || !foe.isAlive() || foe.level() != player.level() || foe.distanceTo(player) > REACH + 2.0) {
			return;
		}
		Vec3 facing = Vec3.directionFromRotation(0.0F, foe.getYRot());
		Vec3 toPlayer = player.position().subtract(foe.position()).multiply(1.0, 0.0, 1.0);
		if (toPlayer.lengthSqr() < 0.01 || facing.dot(toPlayer.normalize()) > 0.0) {
			// (came down in front of him after all)
			return;
		}
		DarktideStatus.stun(foe, STUN_TICKS);
		foe.stopUsingItem();
		ServerLevel level = player.level();
		level.sendParticles(ParticleTypes.CRIT, foe.getX(), foe.getEyeY(), foe.getZ(), 8, 0.3, 0.2, 0.3, 0.1);
		level.playSound(null, foe.getX(), foe.getY(), foe.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 0.8F, 0.9F);
	}
}
