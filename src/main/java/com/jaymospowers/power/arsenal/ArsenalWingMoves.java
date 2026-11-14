package com.jaymospowers.power.arsenal;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Fate;
import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.ground.FirstTools;
import com.jaymospowers.power.arsenal.entity.RiftBlade;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Attendant Blades' big moves ({@link ArsenalWing} holds the wing itself):
 * <ul>
 * <li><b>Closing Ring</b> (order wheel, every blade in its place): the blades ring what their holder looks at and hold
 * it where it stands for {@link #RING_TICKS} (longer if Rift Chains already bind it), then all drive in at once.</li>
 * <li><b>Step to a blade</b> (order wheel): the holder steps through a blade's rift to where the wing is at work: the
 * thing it is ringing or set on, or the spot it is being steered to.</li>
 * <li><b>Wing combos</b>: hits landed one after another (no more than {@link #COMBO_WINDOW} apart) walk the wing
 * through a chain of five: thrusts, crossing pairs, a sweep, and every blade at once. The Fan has one chain, the Halo
 * and the Flank another ({@link #CHAINS}).</li>
 * <li><b>Joining the great casts</b>: when their holder calls Hundred Blades or drops the Sword of Damocles, every
 * blade in its place rises and goes in with it.</li>
 * </ul>
 */
public final class ArsenalWingMoves {
	// tuning (ticks)
	public static final int RING_TICKS = 60;
	/** The hold on something Rift Chains already bind. */
	public static final int RING_CHAINED_TICKS = 100;
	/** The blades take this long to fly out to the ring, and this long to drive in at the end. */
	public static final int RING_OUT_TICKS = 8;
	public static final int RING_CLOSE_TICKS = 6;
	public static final double RING_RADIUS = 1.7;
	public static final double RING_RANGE = 24.0;
	/** Each blade, as the ring closes. */
	public static final float RING_DAMAGE = 4.0F;
	public static final int RING_COOLDOWN = 400;

	public static final int STEP_COOLDOWN = 100;
	/** The holder arrives this far short of what the wing is on. */
	public static final double STEP_SHORT = 1.6;

	public static final int COMBO_WINDOW = 30;
	public static final double SWEEP_RADIUS = 2.5;
	public static final float SWEEP_DAMAGE = 3.0F;
	/** A blade joining a great cast hangs this long, and this much longer for each one before it, then flies. */
	public static final int JOIN_HOVER = 10;
	public static final int JOIN_GAP = 3;
	public static final double JOIN_RADIUS = 6.0;

	private static final int THRUST = 0, CROSS = 1, SWEEP = 2, VOLLEY = 3;
	/** What each landed hit in a row asks of the wing: the Fan's chain, then the Halo's and the Flank's. */
	private static final int[][] CHAINS = {
		{THRUST, THRUST, CROSS, CROSS, VOLLEY},
		{THRUST, SWEEP, CROSS, SWEEP, VOLLEY},
	};
	private static final String[] MOVE_NAMES = {"thrust", "cross", "sweep", "volley"};

	/** A Closing Ring: when it began, when it closes, and what it holds (an entity id). */
	public record Ring(long start, long end, int target) {
		public static final StreamCodec<ByteBuf, Ring> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_LONG, Ring::start,
			ByteBufCodecs.VAR_LONG, Ring::end,
			ByteBufCodecs.VAR_INT, Ring::target,
			Ring::new);
	}

	public static final AttachmentType<Ring> RING = AttachmentRegistry.create(JaymosPowers.id("arsenal_wing_ring"),
		builder -> builder.syncWith(Ring.STREAM_CODEC, AttachmentSyncPredicate.all()));

	private static final Map<UUID, Long> RING_READY = new HashMap<>();
	private static final Map<UUID, Long> STEP_READY = new HashMap<>();
	/** How many hits in a row the wing has answered, and when the last one was. */
	private static final Map<UUID, Integer> COMBO = new HashMap<>();
	private static final Map<UUID, Long> COMBO_AT = new HashMap<>();

	private ArsenalWingMoves() {
	}

	// ---------------------------------------------------------------- Closing Ring

	/** The wheel's Closing Ring: every blade must be hanging in its place. */
	static void ring(ServerPlayer player, ArsenalWing.Wing wing) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		if (wing.mode() != ArsenalWing.DOCKED) {
			return;
		}
		long ready = RING_READY.getOrDefault(player.getUUID(), 0L);
		if (now < ready) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.arsenal.wing_recharging",
				String.format("%.1f", (ready - now) / 20.0)).withStyle(ChatFormatting.GRAY));
			return;
		}
		for (int i = 0; i < wing.blades().size(); i++) {
			if (wing.out(i, now) < 1.0F) {
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.arsenal.wing_ring_not_ready").withStyle(ChatFormatting.GRAY));
				return;
			}
		}
		LivingEntity target = ArsenalWing.looked(player, RING_RANGE);
		if (target == null || !ArsenalWing.fair(player, target)) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.arsenal.wing_no_target").withStyle(ChatFormatting.GRAY));
			return;
		}
		RING_READY.put(player.getUUID(), now + RING_COOLDOWN);
		player.setAttached(RING, new Ring(now, now + (target.hasAttached(ArsenalChains.CHAINED) ? RING_CHAINED_TICKS : RING_TICKS), target.getId()));
		player.setAttached(ArsenalWing.WING, wing.doing(ArsenalWing.RING, now));
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.arsenal.wing_ring", target.getDisplayName()).withStyle(ChatFormatting.LIGHT_PURPLE));
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.TRIDENT_RIPTIDE_2.value(), SoundSource.PLAYERS, 0.8F, 0.8F);
	}

	/** Every tick while the wing is ringing something: it is held, and at the end every blade drives in. */
	static void tickRing(ServerPlayer player, ArsenalWing.Wing wing) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		Ring ring = player.getAttached(RING);
		LivingEntity target = ring != null && level.getEntity(ring.target()) instanceof LivingEntity living ? living : null;
		if (target == null || !ArsenalWing.fair(player, target) || target.distanceToSqr(player) > RING_RANGE * RING_RANGE * 2.25) {
			release(player, wing, now + ArsenalWing.EMERGE_TICKS);
			return;
		}
		boolean corrupted = Fate.isCorrupted(player);
		Vec3 center = target.getBoundingBox().getCenter();
		if (now < ring.end()) {
			if (now - ring.start() >= RING_OUT_TICKS) {
				// held where it stands
				Vec3 v = target.getDeltaMovement();
				target.setDeltaMovement(0.0, Math.min(0.0, v.y), 0.0);
				target.needsSync = true;
				target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 10, 6, true, false, false));
				if (target instanceof Mob mob) {
					mob.getNavigation().stop();
				}
				if ((now - ring.start()) % 10 == 0) {
					MagicFx.ring(level, center, null, (float) ringRadius(target), corrupted ? 0x2A1840 : MagicFx.VOID_COLOR, 10);
					level.playSound(null, center.x, center.y, center.z, SoundEvents.CHAIN_STEP, SoundSource.PLAYERS, 0.5F, 0.7F);
				}
			}
			return;
		}
		// closed: every blade that is there goes in
		DamageSource source = player.damageSources().playerAttack(player);
		for (ArsenalWing.Blade blade : wing.blades()) {
			if (blade.back() > now) {
				continue;
			}
			float damage = (FirstTools.rock(blade.item()) ? Math.min(RING_DAMAGE, ArsenalWing.ROCK_DAMAGE) : RING_DAMAGE)
				* (corrupted ? ArsenalVoid.DAMAGE_MULTIPLIER : 1.0F);
			target.setInvulnerableTime(0);
			if (target.hurtServer(level, source, damage) && corrupted) {
				ArsenalVoid.voidHit(target, player, false);
			}
		}
		MagicFx.ring(level, center, 0.6F, corrupted ? 0x2A1840 : MagicFx.VOID_COLOR);
		level.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT, center.x, center.y, center.z, 16, 0.3, 0.4, 0.3, 0.3);
		level.playSound(null, center.x, center.y, center.z, SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, 1.2F, 0.7F);
		level.playSound(null, center.x, center.y, center.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0F, 0.8F);
		release(player, wing, now + ArsenalWing.RETURN_TICKS);
	}

	/** The ring is over: the blades are back in their places at {@code back}. */
	private static void release(ServerPlayer player, ArsenalWing.Wing wing, long back) {
		long now = player.level().getGameTime();
		List<ArsenalWing.Blade> blades = new ArrayList<>();
		for (ArsenalWing.Blade blade : wing.blades()) {
			blades.add(blade.back() > now ? blade : blade.away(back));
		}
		player.setAttached(ArsenalWing.WING, wing.doing(ArsenalWing.DOCKED, now).with(blades));
		player.removeAttached(RING);
	}

	private static double ringRadius(Entity target) {
		return Math.max(RING_RADIUS, target.getBbWidth() * 0.5 + 1.0);
	}

	/**
	 * Where blade {@code i} is in a Closing Ring (both sides): flown out from its place, circling what is held, and
	 * driving in over the last {@link #RING_CLOSE_TICKS}. Null when there is no ring to draw (the wing's own places then).
	 */
	static @Nullable Vec3 ringSlot(Player player, ArsenalWing.Wing wing, int i, double now, float partialTick) {
		Ring ring = player.getAttached(RING);
		Entity target = ring == null ? null : player.level().getEntity(ring.target());
		if (target == null) {
			return null;
		}
		Vec3 center = target.getPosition(partialTick).add(0.0, target.getBbHeight() * 0.5, 0.0);
		double close = Mth.clamp((now - (ring.end() - RING_CLOSE_TICKS)) / RING_CLOSE_TICKS, 0.0, 1.0);
		double r = Mth.lerp(close * close, ringRadius(target), 0.2);
		double angle = (now - ring.start()) * 0.25 + i * Mth.TWO_PI / wing.blades().size();
		Vec3 there = center.add(Math.cos(angle) * r, Math.sin(angle * 0.5 + i) * 0.3 * (1.0 - close), Math.sin(angle) * r);
		double mix = Mth.clamp((now - ring.start()) / RING_OUT_TICKS, 0.0, 1.0);
		return mix >= 1.0 ? there : ArsenalWing.slot(player, wing.doing(ArsenalWing.DOCKED, 0), i, now, partialTick).lerp(there, mix * mix * (3.0 - 2.0 * mix));
	}

	// ---------------------------------------------------------------- Step to a blade

	/** The wheel's Step: through a blade's rift to where the wing is at work. */
	static void step(ServerPlayer player, ArsenalWing.Wing wing) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		long ready = STEP_READY.getOrDefault(player.getUUID(), 0L);
		if (now < ready) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.arsenal.wing_recharging",
				String.format("%.1f", (ready - now) / 20.0)).withStyle(ChatFormatting.GRAY));
			return;
		}
		Ring ring = player.getAttached(RING);
		LivingEntity target = wing.mode() == ArsenalWing.RING && ring != null && level.getEntity(ring.target()) instanceof LivingEntity living
			? living : ArsenalWing.harried(player);
		Vec3 to;
		if (target != null) {
			Vec3 back = player.position().subtract(target.position()).multiply(1.0, 0.0, 1.0);
			back = back.lengthSqr() < 1.0E-4 ? player.getLookAngle().multiply(-1.0, 0.0, -1.0) : back;
			to = target.position().add(back.normalize().scale(target.getBbWidth() * 0.5 + STEP_SHORT));
		} else if (wing.mode() == ArsenalWing.STEER) {
			to = ArsenalWing.steerPoint(player, 1.0F).subtract(0.0, 1.0, 0.0);
		} else {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.arsenal.wing_step_nowhere").withStyle(ChatFormatting.GRAY));
			return;
		}
		Vec3 from = player.position();
		// room to stand there, or a block higher
		if (!level.noCollision(player, player.getBoundingBox().move(to.subtract(from)))) {
			to = to.add(0.0, 1.0, 0.0);
			if (!level.noCollision(player, player.getBoundingBox().move(to.subtract(from)))) {
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.arsenal.wing_step_no_room").withStyle(ChatFormatting.GRAY));
				return;
			}
		}
		STEP_READY.put(player.getUUID(), now + STEP_COOLDOWN);
		int color = Fate.isCorrupted(player) ? 0x2A1840 : MagicFx.VOID_COLOR;
		MagicFx.ring(level, from.add(0.0, 1.0, 0.0), 0.8F, color);
		level.playSound(null, from.x, from.y, from.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.7F, 1.4F);
		player.teleportTo(to.x, to.y, to.z);
		player.resetFallDistance();
		if (target != null) {
			player.lookAt(EntityAnchorArgument.Anchor.EYES, target.getBoundingBox().getCenter());
		}
		MagicFx.ring(level, to.add(0.0, 1.0, 0.0), 0.8F, color);
		level.playSound(null, to.x, to.y, to.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.7F, 1.4F);
	}

	// ---------------------------------------------------------------- combos

	/** The holder landed a hit: the wing's answer, by how many it has answered in a row. */
	static void swing(ServerPlayer player, ArsenalWing.Wing wing, LivingEntity target) {
		UUID id = player.getUUID();
		ServerLevel level = player.level();
		long now = level.getGameTime();
		int[] chain = CHAINS[wing.shape() == ArsenalWing.FAN ? 0 : 1];
		int step = now - COMBO_AT.getOrDefault(id, -1000L) <= COMBO_WINDOW ? COMBO.getOrDefault(id, 0) % chain.length : 0;
		int move = chain[step];
		int blades = switch (move) {
			case CROSS -> 2;
			case VOLLEY -> wing.blades().size();
			default -> 1;
		};
		int sent = 0;
		while (sent < blades && ArsenalWing.strike(player, player.getAttached(ArsenalWing.WING), target, ArsenalWing.STRIKE_DAMAGE)) {
			sent++;
		}
		if (sent == 0) {
			// none ready: the chain waits where it is
			return;
		}
		COMBO.put(id, step + 1);
		COMBO_AT.put(id, now);
		if (move == SWEEP) {
			sweep(player, target);
		}
		if (move != THRUST) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.arsenal.wing_combo." + MOVE_NAMES[move]).withStyle(ChatFormatting.LIGHT_PURPLE));
		}
	}

	/** The blade goes round what it struck: everything else close to it is cut. */
	private static void sweep(ServerPlayer player, LivingEntity target) {
		ServerLevel level = player.level();
		boolean corrupted = Fate.isCorrupted(player);
		Vec3 center = target.getBoundingBox().getCenter();
		DamageSource source = player.damageSources().playerAttack(player);
		for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, AABB.ofSize(center, SWEEP_RADIUS * 2.0, 3.0, SWEEP_RADIUS * 2.0),
			e -> e != target && ArsenalWing.fair(player, e) && ArsenalWing.foe(player, e) && e.distanceToSqr(target) <= SWEEP_RADIUS * SWEEP_RADIUS)) {
			living.hurtServer(level, source, SWEEP_DAMAGE * (corrupted ? ArsenalVoid.DAMAGE_MULTIPLIER : 1.0F));
		}
		MagicFx.ring(level, center, null, (float) SWEEP_RADIUS, corrupted ? 0x2A1840 : MagicFx.VOID_COLOR, 8);
		level.playSound(null, center.x, center.y, center.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.9F, 1.1F);
	}

	// ---------------------------------------------------------------- the great casts

	/**
	 * Hundred Blades was called, or the Sword of Damocles struck, at {@code point}: every blade hanging in its place
	 * rises a moment and flies in, at what is within {@link #JOIN_RADIUS} of it, or at the spot itself.
	 */
	static void join(ServerPlayer player, Vec3 point) {
		ArsenalWing.Wing wing = player.getAttached(ArsenalWing.WING);
		if (wing == null || wing.mode() != ArsenalWing.DOCKED) {
			return;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		boolean corrupted = Fate.isCorrupted(player);
		List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, AABB.ofSize(point, JOIN_RADIUS * 2.0, JOIN_RADIUS * 2.0, JOIN_RADIUS * 2.0),
			e -> ArsenalWing.fair(player, e));
		List<ArsenalWing.Blade> blades = new ArrayList<>(wing.blades());
		int sent = 0;
		for (int i = 0; i < blades.size(); i++) {
			ArsenalWing.Blade blade = blades.get(i);
			int hover = JOIN_HOVER + sent * JOIN_GAP;
			if (wing.out(i, now) < 1.0F || !blade.own() && now + hover + ArsenalWing.RETURN_TICKS >= blade.expires()) {
				continue;
			}
			LivingEntity target = targets.isEmpty() ? null : targets.get(sent % targets.size());
			Vec3 from = ArsenalWing.slot(player, wing, i, now, 1.0F).add(0.0, 1.5, 0.0);
			RiftBlade flying = RiftBlade.create(level, player, blade.item(), from, target != null ? target.getBoundingBox().getCenter() : point, target, hover,
				(FirstTools.rock(blade.item()) ? ArsenalWing.ROCK_DAMAGE : ArsenalWing.STRIKE_DAMAGE) * (corrupted ? ArsenalVoid.DAMAGE_MULTIPLIER : 1.0F));
			flying.makeSkyBlade(corrupted);
			level.addFreshEntity(flying);
			blades.set(i, blade.away(now + hover + ArsenalWing.RETURN_TICKS));
			sent++;
		}
		if (sent > 0) {
			player.setAttached(ArsenalWing.WING, wing.with(blades));
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TRIDENT_RIPTIDE_1.value(), SoundSource.PLAYERS, 0.7F, 1.2F);
		}
	}

	/** The wing is gone: nothing stays ringed. */
	static void clear(ServerPlayer player) {
		if (player.getAttached(RING) != null) {
			player.removeAttached(RING);
		}
	}

	static void onLost(ServerPlayer player) {
		UUID id = player.getUUID();
		RING_READY.remove(id);
		STEP_READY.remove(id);
		COMBO.remove(id);
		COMBO_AT.remove(id);
		player.removeAttached(RING);
	}
}
