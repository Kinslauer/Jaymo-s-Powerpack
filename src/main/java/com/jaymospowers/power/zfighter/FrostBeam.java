package com.jaymospowers.power.zfighter;

import com.jaymospowers.core.Dismember;
import com.jaymospowers.core.wounds.BodyPart;
import com.jaymospowers.core.wounds.Breaks;
import com.jaymospowers.core.wounds.Wounds;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * (0.15.70) A Frost-born's left-mouse hold once the day's ice prison is spent: a hair-thin white beam, drawn like a ki
 * wave's Frost Needle ({@code ZFighterBeams}, style {@link ZFighterKi#NEEDLE}). It hits for only {@link #DAMAGE}, but it
 * goes exactly where the crosshair is, and a hit has {@link #WOUND_CHANCE} to wound the part it lands on: the head comes
 * off; an arm or leg is cut off, or crippled (broken) at {@link #CRIPPLE_SHARE}. A body that can't lose parts is crippled.
 * <p>
 * Kingdoms keeps the day's charge: a hold on cooldown comes back to us as {@link #BEAM_TAG} (Andy: "will be usable after
 * the single day ice charge"). Without Kingdoms there's no charge, and the hold is always the beam.
 */
public final class FrostBeam {
	/** Damage (1.5 hearts), reach (blocks), the wait between beams (ticks), and the beam's width and life on screen. */
	public static final float DAMAGE = 3.0F;
	public static final double RANGE = 48.0;
	public static final int COOLDOWN = 60;
	public static final float RADIUS = 0.035F;
	public static final int SHOWN_TICKS = 10;
	public static final int COLOR = 0xFFFFFF;
	/** A hit's chance to take or cripple the part it lands on, and a limb's share of those that cripple (not sever). */
	public static final float WOUND_CHANCE = 0.4F, CRIPPLE_SHARE = 0.5F;
	/** Kingdoms puts this on a Frost-born whose hold found the ice prison on its daily cooldown; taken off here. */
	public static final String BEAM_TAG = "jaymospowers_frost_beam";

	private static final boolean KINGDOMS = FabricLoader.getInstance().isModLoaded("jaymoskingdoms");
	/** When each player last fired (game time). Not saved: a restart only clears a few seconds. */
	private static final Map<UUID, Long> LAST = new HashMap<>();

	private FrostBeam() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(FrostBeam::tick);
	}

	/** A Frost-born's hold: Kingdoms tries its ice prison first (and sends the beam back if the day's is spent). */
	static void hold(ServerPlayer player) {
		if (KINGDOMS) {
			player.addTag(RaceMoves.FROST_TAG);
		} else {
			fire(player);
		}
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.entityTags().contains(BEAM_TAG)) {
				player.removeTag(BEAM_TAG);
				fire(player);
			}
		}
	}

	static void fire(ServerPlayer player) {
		if (!player.isAlive() || player.isSpectator() || ZFighterLineage.of(player) != ZFighterLineage.FROST) {
			return;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		Long last = LAST.get(player.getUUID());
		if (last != null && now - last < COOLDOWN && now >= last) {
			return;
		}
		LAST.put(player.getUUID(), now);
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0F);
		HitResult wall = level.clip(new ClipContext(eye, eye.add(look.scale(RANGE)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		double reach = wall.getType() == HitResult.Type.MISS ? RANGE : wall.getLocation().distanceTo(eye);
		Vec3 end = eye.add(look.scale(reach));
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, player.getBoundingBox().expandTowards(look.scale(reach)).inflate(1.0),
			e -> e != player && !e.isSpectator() && e instanceof LivingEntity living && living.isAlive() && e.isPickable(), reach * reach);
		LivingEntity target = hit == null ? null : (LivingEntity)hit.getEntity();
		if (hit != null) {
			end = hit.getLocation();
		}
		Vec3 from = ZFighterKi.waveHands(player, look);
		show(level, from, end);
		player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.2F, 1.8F);
		level.playSound(null, end.x, end.y, end.z, SoundEvents.PLAYER_HURT_FREEZE, SoundSource.PLAYERS, 0.8F, 1.4F);
		level.sendParticles(ParticleTypes.SNOWFLAKE, end.x, end.y, end.z, 12, 0.15, 0.15, 0.15, 0.05);
		if (target != null) {
			strike(player, target);
		}
	}

	/** The beam lands on the part the crosshair crosses: 1.5 hearts there, then the roll to wound it. */
	private static void strike(ServerPlayer player, LivingEntity target) {
		ServerLevel level = player.level();
		int part = Dismember.aimedPart(player, target);
		// (Wounds lands a power's cut on its part; this one never severs on overkill by itself)
		Dismember.cut(player, target, part, 0.0F, () -> target.hurtServer(level, ZFighterKi.source(player), DAMAGE));
		if (!target.isAlive() || target instanceof Player p && (p.isCreative() || p.isSpectator())
			|| target.getRandom().nextFloat() >= WOUND_CHANCE) {
			return;
		}
		boolean parts = Dismember.canLoseParts(target) && (Dismember.missing(target) & part) == 0;
		if (parts && (part == Dismember.HEAD || target.getRandom().nextFloat() >= CRIPPLE_SHARE)) {
			Dismember.sever(target, part, player);
		} else {
			cripple(target, part, player);
		}
	}

	/** Breaks the limb it hit, or for a head (or a body without that limb) a leg, whichever the body has. */
	private static void cripple(LivingEntity target, int part, ServerPlayer by) {
		BodyPart aimed = part == Dismember.HEAD ? null : Breaks.part(part);
		BodyPart[] tries = {aimed, target.getRandom().nextBoolean() ? BodyPart.LEFT_LEG : BodyPart.RIGHT_LEG, BodyPart.LEGS, BodyPart.HIND_LEGS};
		for (BodyPart limb : tries) {
			if (limb != null && Wounds.breakPart(target, limb, by)) {
				return;
			}
		}
	}

	/** Drawn for everyone near: the Frost Needle's hair-thin core, all white. */
	private static void show(ServerLevel level, Vec3 from, Vec3 to) {
		ZFighterNet.BeamFx fx = new ZFighterNet.BeamFx(from.x, from.y, from.z, to.x, to.y, to.z, RADIUS, COLOR, ZFighterKi.NEEDLE, SHOWN_TICKS);
		Vec3 middle = from.lerp(to, 0.5);
		for (ServerPlayer viewer : level.players()) {
			if (viewer.position().distanceToSqr(middle) <= ZFighterKi.BEAM_SEEN * ZFighterKi.BEAM_SEEN) {
				ServerPlayNetworking.send(viewer, fx);
			}
		}
	}
}
