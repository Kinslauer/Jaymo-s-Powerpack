package com.jaymospowers.power.arsenal;

import com.jaymospowers.JaymosPowers;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * <b>Rift Chains</b>, the Arsenal's B (uncorrupted; the Void Arsenal's B is the Sword of Damocles): three small rifts
 * tear open around the creature you're aiming at and chains shoot out of them and hold it where it stands, in the air
 * if that's where it was. It can't walk, jump or fly off until the chains let go ({@link #HOLD} ticks, {@link
 * #HOLD_PLAYER} for a player). Press B again while it's held and the chains drag it through the air to your feet.
 *
 * <p>Only the bind is synced ({@link #CHAINED}, on the bound creature, to everyone); every client draws the rifts and
 * chains from it ({@code ArsenalChainsRenderer}).
 */
public final class ArsenalChains {
	// tuning (ticks, blocks)
	public static final int COOLDOWN = 400;
	static final double RANGE = 24.0;
	/** How long the chains hold a creature, and a player (shorter: they have to be able to fight back). */
	static final int HOLD = 80;
	static final int HOLD_PLAYER = 40;
	/** The rifts open this far out from the creature's middle, and the chains take this long to reach it. */
	public static final double RIFT_DISTANCE = 2.4;
	public static final int SHOOT = 4;
	/** The yank: blocks per tick, the longest it pulls, and how close in front of you it stops. */
	static final double YANK_SPEED = 1.4;
	static final int YANK_MAX = 20;
	static final double YANK_STOP = 2.0;
	/** Held players are let back to where they were bound when they drift further than this. */
	static final double PLAYER_SLACK = 0.4;

	/**
	 * A bind: who cast it (entity id, for drawing the yank), when it started, where it holds (x, y, z: the creature's
	 * feet), which way the rifts are turned (radians), and when the yank started (0 if it hasn't).
	 */
	public record Chained(int owner, long start, double x, double y, double z, float angle, long yank) {
		public static final StreamCodec<ByteBuf, Chained> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Chained::owner,
			ByteBufCodecs.VAR_LONG, Chained::start,
			ByteBufCodecs.DOUBLE, Chained::x,
			ByteBufCodecs.DOUBLE, Chained::y,
			ByteBufCodecs.DOUBLE, Chained::z,
			ByteBufCodecs.FLOAT, Chained::angle,
			ByteBufCodecs.VAR_LONG, Chained::yank,
			Chained::new);

		public Vec3 anchor() {
			return new Vec3(x, y, z);
		}

		Chained yanked(long now) {
			return new Chained(owner, start, x, y, z, angle, now);
		}

		/**
		 * Rift {@code i} of 3, around a creature this tall: two up and to either side, one low behind, all a little above
		 * the middle so the chains come down onto it.
		 */
		public Vec3 rift(int i, double height) {
			double a = angle + i * Mth.TWO_PI / 3.0;
			double up = i == 0 ? -0.2 : 0.9;
			return new Vec3(x + Math.cos(a) * RIFT_DISTANCE, y + height * 0.5 + up + height * 0.25, z + Math.sin(a) * RIFT_DISTANCE);
		}
	}

	public static final AttachmentType<Chained> CHAINED = AttachmentRegistry.create(JaymosPowers.id("arsenal_chained"),
		builder -> builder.syncWith(Chained.STREAM_CODEC, AttachmentSyncPredicate.all()));

	private static final Identifier SPEED_ID = JaymosPowers.id("arsenal_chains_speed");
	private static final Identifier JUMP_ID = JaymosPowers.id("arsenal_chains_jump");
	private static final Identifier FLY_ID = JaymosPowers.id("arsenal_chains_fly");

	/** The server's side of a bind: the caster → what they hold and until when. */
	private static final class Bind {
		final ServerLevel level;
		final Entity target;
		final long until;

		Bind(ServerLevel level, Entity target, long until) {
			this.level = level;
			this.target = target;
			this.until = until;
		}
	}

	private static final Map<UUID, Bind> BINDS = new HashMap<>();

	private ArsenalChains() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(ArsenalChains::serverTick);
	}

	/** B: bind what you're aiming at, or, with something already bound, yank it to you. */
	static void key(ServerPlayer player) {
		Bind bind = BINDS.get(player.getUUID());
		if (bind != null) {
			yank(player, bind);
			return;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		ArsenalPower.Cooldowns cd = ArsenalPower.cooldowns(player);
		if (now < cd.chains()) {
			denied(player, Component.translatable("message.jaymospowers.arsenal.chains_recharging", String.format("%.1f", (cd.chains() - now) / 20.0)));
			return;
		}
		Entity target = aimed(player);
		if (target == null) {
			denied(player, Component.translatable("message.jaymospowers.arsenal.chains_nothing"));
			return;
		}
		player.setAttached(ArsenalPower.COOLDOWNS, cd.withChains(now + COOLDOWN));
		// the chains pull it off whatever it rides
		target.stopRiding();
		Vec3 from = player.position().subtract(target.position());
		// one rift behind the creature (from where you stand), the other two up either side of it
		float angle = (float) Mth.atan2(-from.z, -from.x);
		target.setAttached(CHAINED, new Chained(player.getId(), now, target.getX(), target.getY(), target.getZ(), angle, 0L));
		BINDS.put(player.getUUID(), new Bind(level, target, now + (target instanceof ServerPlayer ? HOLD_PLAYER : HOLD)));
		if (target instanceof LivingEntity living) {
			slow(living, true);
		}
		Chained chained = target.getAttached(CHAINED);
		for (int i = 0; i < 3; i++) {
			Vec3 r = chained.rift(i, target.getBbHeight());
			level.sendParticles(ParticleTypes.REVERSE_PORTAL, r.x, r.y, r.z, 12, 0.15, 0.15, 0.15, 0.04);
		}
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.6F, 0.7F);
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1.4F, 0.6F);
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.ARMOR_EQUIP_CHAIN.value(), SoundSource.PLAYERS, 1.2F, 0.5F);
		if (target instanceof ServerPlayer bound) {
			bound.sendOverlayMessage(Component.translatable("message.jaymospowers.arsenal.chains_bound").withStyle(ChatFormatting.DARK_PURPLE));
		}
	}

	/** The creature in your sights, within {@link #RANGE} and not behind a wall. */
	private static @Nullable Entity aimed(ServerPlayer player) {
		ServerLevel level = player.level();
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		Vec3 end = eye.add(look.scale(RANGE));
		BlockHitResult blockHit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		if (blockHit.getType() != HitResult.Type.MISS) {
			end = blockHit.getLocation();
		}
		AABB search = player.getBoundingBox().expandTowards(look.scale(RANGE)).inflate(1.5);
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, search,
			e -> e instanceof LivingEntity && !(e instanceof EnderDragon) && e.isAlive() && !e.isSpectator() && e != player
				&& !e.hasAttached(CHAINED) && !e.isPassengerOfSameVehicle(player), eye.distanceToSqr(end));
		return hit == null ? null : hit.getEntity();
	}

	/** B again: the chains haul it to you. */
	private static void yank(ServerPlayer player, Bind bind) {
		Chained chained = bind.target.getAttached(CHAINED);
		if (chained == null || chained.yank() != 0L) {
			return;
		}
		long now = bind.level.getGameTime();
		bind.target.setAttached(CHAINED, chained.yanked(now));
		if (bind.target instanceof LivingEntity living) {
			slow(living, false);
		}
		BINDS.put(player.getUUID(), new Bind(bind.level, bind.target, now + YANK_MAX));
		bind.level.playSound(null, bind.target.getX(), bind.target.getY(), bind.target.getZ(), SoundEvents.CHAIN_BREAK, SoundSource.PLAYERS, 1.2F, 0.6F);
		bind.level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.5F, 0.6F);
	}

	private static void serverTick(MinecraftServer server) {
		for (Iterator<Map.Entry<UUID, Bind>> it = BINDS.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Bind> entry = it.next();
			Bind bind = entry.getValue();
			ServerPlayer owner = server.getPlayerList().getPlayer(entry.getKey());
			Entity target = bind.target;
			Chained chained = target.getAttached(CHAINED);
			long now = bind.level.getGameTime();
			boolean gone = owner == null || !owner.isAlive() || owner.level() != bind.level || !target.isAlive() || target.isRemoved()
				|| target.level() != bind.level || chained == null;
			if (gone || now >= bind.until || chained.yank() != 0L && !pull(owner, target)) {
				it.remove();
				release(bind.level, target);
				continue;
			}
			if (chained.yank() == 0L) {
				hold(target, chained.anchor());
			}
		}
	}

	/** Held where the chains caught it: back to the spot, no momentum, no path to follow. */
	private static void hold(Entity target, Vec3 anchor) {
		if (target instanceof ServerPlayer player) {
			// the client moves its player: put them back only when they've drifted
			if (player.position().distanceToSqr(anchor) > PLAYER_SLACK * PLAYER_SLACK) {
				player.teleportTo(anchor.x, anchor.y, anchor.z);
			}
			player.setDeltaMovement(Vec3.ZERO);
			player.resetFallDistance();
			return;
		}
		if (target instanceof Mob mob) {
			mob.getNavigation().stop();
		}
		target.setPos(anchor);
		target.setDeltaMovement(Vec3.ZERO);
		target.resetFallDistance();
		target.needsSync = true;
	}

	/** One tick of the yank: toward the spot in front of you. False once it's there. */
	private static boolean pull(ServerPlayer owner, Entity target) {
		Vec3 look = owner.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0.0, look.z);
		flat = flat.lengthSqr() < 1.0E-4 ? Vec3.ZERO : flat.normalize();
		Vec3 stop = owner.position().add(flat.scale(YANK_STOP));
		Vec3 to = stop.subtract(target.position());
		double d = to.length();
		if (d < 0.8) {
			target.setDeltaMovement(target.getDeltaMovement().scale(0.2));
			target.needsSync = true;
			return false;
		}
		// a little lift so it clears the ground on the way
		Vec3 v = to.scale(Math.min(YANK_SPEED, d) / d).add(0.0, d > 3.0 ? 0.12 : 0.0, 0.0);
		target.setDeltaMovement(v);
		target.needsSync = true;
		target.resetFallDistance();
		return true;
	}

	/** The chains let go and slip back into their rifts. */
	private static void release(ServerLevel level, Entity target) {
		Chained chained = target.getAttached(CHAINED);
		target.removeAttached(CHAINED);
		if (target instanceof LivingEntity living) {
			slow(living, false);
		}
		target.resetFallDistance();
		if (chained != null && chained.yank() == 0L) {
			for (int i = 0; i < 3; i++) {
				Vec3 r = chained.rift(i, target.getBbHeight());
				level.sendParticles(ParticleTypes.REVERSE_PORTAL, r.x, r.y, r.z, 8, 0.1, 0.1, 0.1, 0.06);
			}
		}
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.CHAIN_BREAK, SoundSource.PLAYERS, 0.8F, 1.1F);
	}

	/** Bound: no walking, jumping or flying (transient, so nothing is saved if the world closes mid-bind). */
	private static void slow(LivingEntity living, boolean on) {
		modifier(living.getAttribute(Attributes.MOVEMENT_SPEED), SPEED_ID, on);
		modifier(living.getAttribute(Attributes.JUMP_STRENGTH), JUMP_ID, on);
		modifier(living.getAttribute(Attributes.FLYING_SPEED), FLY_ID, on);
	}

	private static void modifier(@Nullable AttributeInstance attribute, Identifier id, boolean on) {
		if (attribute == null) {
			return;
		}
		attribute.removeModifier(id);
		if (on) {
			attribute.addTransientModifier(new AttributeModifier(id, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
	}

	/** The power is lost or the caster dies: whatever they hold goes free. */
	static void onLost(ServerPlayer player) {
		Bind bind = BINDS.remove(player.getUUID());
		if (bind != null) {
			release(bind.level, bind.target);
		}
	}

	private static void denied(ServerPlayer player, Component message) {
		player.sendOverlayMessage(message.copy().withStyle(ChatFormatting.GRAY));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.3F, 1.6F);
	}
}
