package com.jaymospowers.power.darktide;

import com.jaymospowers.api.PowerKey;
import com.jaymospowers.core.Mastery;
import com.jaymospowers.core.PickKit;
import com.jaymospowers.core.Shakes;
import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.core.wounds.BodyPart;
import com.jaymospowers.core.wounds.Bleeding;
import com.jaymospowers.power.xenoform.SeaCalls;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * What the sea answers a Darktide user with, every one a variant of a key (the keys keep their own moves):
 * <ul>
 * <li>sneak + R with a trident in hand: <b>Trident Jab</b>, three quick jabs that open a bleeding cut.</li>
 * <li>sneak + G, held: <b>Water Beam</b> on a gauge in the action bar that drains while it runs and refills when it stops.</li>
 * <li>sneak + V: <b>Water Dome</b> that stops shots (the sea aliens' dome, {@link SeaCalls#dome}).</li>
 * <li>sneak + B: <b>Whirlpool</b> that drags creatures and items in ({@link SeaCalls#whirlpool}).</li>
 * <li>sneak + Z in water: <b>the shoal</b>, fish and dolphins fight for you ({@link SeaCalls#shoal}).</li>
 * <li>passive with a trident in hand: the drowned nearby turn on your foes ({@link SeaCalls#answerDrowned}).</li>
 * <li>passive: a trident you throw that lands under open sky calls lightning down on it.</li>
 * </ul>
 */
final class DarktidePicks {
	private static final String TABLE = "darktide_picks";
	private static final int JAB = 0, BEAM = 1, STRIKE = 2;

	/** Trident Jab: jabs, ticks between them, reach, damage, the cooldown, Flow it costs. */
	static final int JABS = 3, JAB_EVERY = 4, JAB_COOLDOWN = 70;
	static final double JAB_REACH = 3.8;
	static final float JAB_DAMAGE = 3.0F, JAB_FLOW = 6.0F;
	/** Water Beam: the gauge's size, drained and refilled per tick, the least to start with, reach, damage and its beat, the push. */
	static final float BEAM_GAUGE = 100.0F, BEAM_DRAIN = 1.6F, BEAM_REFILL = 0.7F, BEAM_START = 25.0F;
	static final double BEAM_REACH = 18.0, BEAM_PUSH = 0.12;
	static final float BEAM_DAMAGE = 1.5F;
	static final int BEAM_BEAT = 5, BEAM_COOLDOWN = 20;
	/** Trident lightning: how slow a stuck trident is, the bolt's reach and damage, the cooldown. */
	static final double STUCK_SPEED = 0.06;
	static final double STRIKE_REACH = 3.0;
	static final float STRIKE_DAMAGE = 7.0F;
	static final int STRIKE_COOLDOWN = 80, STRIKE_AGE = 4;
	static final int GAUGE_WIDTH = 20;

	private record Jabs(long next, int left) {
	}

	private static final Map<UUID, Jabs> JABBING = new HashMap<>();
	private static final Map<UUID, Float> GAUGE = new HashMap<>();
	private static final Set<UUID> BEAMING = new HashSet<>();
	private static final Map<UUID, Set<Integer>> STRUCK = new HashMap<>();

	private DarktidePicks() {
	}

	static void forget(ServerPlayer player) {
		UUID id = player.getUUID();
		JABBING.remove(id);
		GAUGE.remove(id);
		BEAMING.remove(id);
		STRUCK.remove(id);
		PickKit.forget(player);
		SeaCalls.clear(player);
	}

	private static void say(ServerPlayer player, String key) {
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.darktide_picks." + key).withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.ITALIC));
	}

	/** A key, down or up. True if it was one of the sea's moves (the key then does nothing else). */
	static boolean key(ServerPlayer player, PowerKey key, boolean down) {
		if (!down) {
			if (key == PowerKey.ABILITY_2 && BEAMING.remove(player.getUUID())) {
				PickKit.spend(TABLE, player, BEAM, BEAM_COOLDOWN);
				return true;
			}
			return false;
		}
		if (!player.isShiftKeyDown() || player.isSpectator() || !Mastery.isUnlocked(player, key)) {
			return false;
		}
		switch (key) {
			case ABILITY_1 -> {
				if (!player.getMainHandItem().is(Items.TRIDENT)) {
					return false;
				}
				if (!PickKit.ready(TABLE, player, JAB) || JABBING.containsKey(player.getUUID())) {
					say(player, "cooling");
					return true;
				}
				DarktideLive live = DarktidePower.live(player);
				if (live.flow < JAB_FLOW) {
					say(player, "no_flow");
					return true;
				}
				live.flow -= JAB_FLOW;
				JABBING.put(player.getUUID(), new Jabs(player.level().getGameTime(), JABS));
				PickKit.spend(TABLE, player, JAB, JAB_COOLDOWN);
				return true;
			}
			case ABILITY_2 -> {
				if (!PickKit.ready(TABLE, player, BEAM)) {
					say(player, "cooling");
					return true;
				}
				if (GAUGE.getOrDefault(player.getUUID(), BEAM_GAUGE) < BEAM_START) {
					say(player, "gauge_low");
					return true;
				}
				BEAMING.add(player.getUUID());
				player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, SoundSource.PLAYERS, 1.2F, 0.9F);
				return true;
			}
			case ABILITY_3 -> {
				SeaCalls.dome(player);
				return true;
			}
			default -> {
				SeaCalls.whirlpool(player, PickKit.aimed(player, SeaCalls.WHIRL_REACH));
				return true;
			}
		}
	}

	/** sneak + Z: the shoal answers, in water. */
	static boolean shoal(ServerPlayer player) {
		if (!player.isInWater()) {
			return false;
		}
		SeaCalls.shoal(player);
		return true;
	}

	private static void jabTick(ServerPlayer player, ServerLevel level, long now) {
		Jabs jabs = JABBING.get(player.getUUID());
		if (jabs == null || now < jabs.next) {
			return;
		}
		if (!player.getMainHandItem().is(Items.TRIDENT)) {
			JABBING.remove(player.getUUID());
			return;
		}
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		Vec3 tip = eye.add(look.scale(JAB_REACH));
		Vec3 side = new Vec3(-look.z, 0.0, look.x).normalize().scale((jabs.left % 2 == 0 ? 0.25 : -0.25));
		MagicFx.stroke(level, eye.add(look.scale(0.6)).add(side).add(0.0, -0.3, 0.0), tip, 0.28F, DarktideLooks.PALE);
		LivingEntity target = DarktideMoves.pick(player, JAB_REACH);
		if (target != null) {
			DarktideMoves.hit(player, target, JAB_DAMAGE + DarktideMoves.weapon(player) * 0.25F);
			Bleeding.open(target, BodyPart.BODY, false);
			DarktideLooks.spray(level, target.getBoundingBox().getCenter(), 0.8F);
		}
		player.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 0.5F, 1.8F);
		if (jabs.left <= 1) {
			JABBING.remove(player.getUUID());
		} else {
			JABBING.put(player.getUUID(), new Jabs(now + JAB_EVERY, jabs.left - 1));
		}
	}

	private static void beamTick(ServerPlayer player, ServerLevel level, long now) {
		UUID id = player.getUUID();
		float gauge = GAUGE.getOrDefault(id, BEAM_GAUGE);
		if (BEAMING.contains(id)) {
			gauge -= BEAM_DRAIN;
			if (gauge <= 0.0F || player.isUsingItem() || !player.isAlive()) {
				gauge = Math.max(0.0F, gauge);
				BEAMING.remove(id);
				PickKit.spend(TABLE, player, BEAM, BEAM_COOLDOWN * 2);
			} else {
				Vec3 eye = player.getEyePosition();
				Vec3 look = player.getLookAngle();
				Vec3 end = eye.add(look.scale(BEAM_REACH));
				var wall = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
				if (wall.getType() != HitResult.Type.MISS) {
					end = wall.getLocation();
				}
				Vec3 from = eye.add(look.scale(0.7)).add(0.0, -0.3, 0.0);
				MagicFx.beam(level, from, end, 0.22F, DarktideLooks.DROP, 3);
				if (now % 3 == 0) {
					DarktideLooks.spray(level, end, 0.6F);
				}
				if (now % BEAM_BEAT == 0) {
					Vec3 line = end.subtract(from);
					for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(from, end).inflate(1.2), c -> PickKit.foe(player, c))) {
						Vec3 p = e.getBoundingBox().getCenter().subtract(from);
						double t = Math.max(0.0, Math.min(1.0, p.dot(line) / Math.max(1.0E-4, line.lengthSqr())));
						if (p.subtract(line.scale(t)).length() <= 0.9 + e.getBbWidth() * 0.5) {
							DarktideMoves.hit(player, e, BEAM_DAMAGE);
							e.setDeltaMovement(e.getDeltaMovement().add(look.scale(BEAM_PUSH)));
							e.syncVelocity = true;
							e.clearFire();
						}
					}
				}
				if (now % 8 == 0) {
					level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 0.6F, 1.5F);
				}
			}
		} else if (gauge < BEAM_GAUGE) {
			gauge = Math.min(BEAM_GAUGE, gauge + BEAM_REFILL);
		}
		GAUGE.put(id, gauge);
		if (BEAMING.contains(id) || gauge < BEAM_GAUGE && now % 10 == 0) {
			player.sendOverlayMessage(Component.literal("Water " + PickKit.gauge(gauge / BEAM_GAUGE, GAUGE_WIDTH)).withStyle(ChatFormatting.AQUA));
		}
	}

	/** A trident you threw that has landed under open sky calls a bolt onto itself. */
	private static void strikeTick(ServerPlayer player, ServerLevel level, long now) {
		if (now % 2 != 0 || !Mastery.isUnlocked(player, PowerKey.ABILITY_1)) {
			return;
		}
		Set<Integer> done = STRUCK.get(player.getUUID());
		for (ThrownTrident trident : level.getEntitiesOfClass(ThrownTrident.class, PickKit.around(player.position(), 96.0), t -> t.getOwner() == player && t.isAlive())) {
			if (trident.tickCount < STRIKE_AGE || trident.getDeltaMovement().lengthSqr() > STUCK_SPEED * STUCK_SPEED
				|| done != null && done.contains(trident.getId()) || !level.canSeeSky(trident.blockPosition()) || !PickKit.ready(TABLE, player, STRIKE)) {
				continue;
			}
			PickKit.spend(TABLE, player, STRIKE, STRIKE_COOLDOWN);
			STRUCK.computeIfAbsent(player.getUUID(), k -> new HashSet<>()).add(trident.getId());
			Vec3 at = trident.position();
			LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
			if (bolt != null) {
				bolt.snapTo(at);
				bolt.setVisualOnly(true);
				bolt.setCause(player);
				level.addFreshEntity(bolt);
			}
			DamageSource source = player.damageSources().source(DamageTypes.LIGHTNING_BOLT, player);
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, PickKit.around(at, STRIKE_REACH), c -> PickKit.foe(player, c))) {
				e.hurtServer(level, source, STRIKE_DAMAGE * (e.isInWaterOrRain() ? 1.3F : 1.0F));
			}
			MagicFx.blast(level, at.add(0.0, 0.5, 0.0), 2.0F, DarktideLooks.PALE);
			Shakes.around(level, at, 20.0, 1.0F);
		}
		if (done != null) {
			done.removeIf(id -> level.getEntity(id) == null);
		}
	}

	static void tick(ServerPlayer player, DarktideLive live) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		if (!player.isAlive()) {
			BEAMING.remove(player.getUUID());
			JABBING.remove(player.getUUID());
			return;
		}
		jabTick(player, level, now);
		beamTick(player, level, now);
		strikeTick(player, level, now);
		SeaCalls.tick(player);
		SeaCalls.answerDrowned(player);
	}
}
