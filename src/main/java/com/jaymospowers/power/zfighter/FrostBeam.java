package com.jaymospowers.power.zfighter;

import com.jaymospowers.core.Dismember;
import com.jaymospowers.core.wounds.BodyPart;
import com.jaymospowers.core.wounds.Breaks;
import com.jaymospowers.core.wounds.Wounds;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * (0.15.70) A Frost-born's left-mouse hold once the day's ice prison is spent: a hair-thin white beam, drawn like a ki
 * wave's Frost Needle ({@code ZFighterBeams}, style {@link ZFighterKi#NEEDLE}). It hits for only {@link #DAMAGE}, but it
 * goes exactly where the crosshair is (a Z Fighter's starts weaker and grows with mastery, but fires faster), and a hit has {@link #WOUND_CHANCE} to wound the part it lands on: the head comes
 * off; an arm or leg is cut off, or crippled (broken) at {@link #CRIPPLE_SHARE}. A body that can't lose parts is crippled.
 * <p>
 * (0.16.7) Andy: "https://modrinth.com/mod/laser-eyes this has a good idea of what i want from the beam". Now two
 * hair-thin white beams from the eyes (style 3 in {@code ZFighterBeams}: no ki orbs), held for as long as the left mouse
 * is ({@link Keep}, every few ticks from the client), sweeping where the crosshair goes and meeting on what it hits. While
 * it touches something it hurts it once per {@link #COOLDOWN} ({@link #Z_COOLDOWN}), each with the wound roll. Water it
 * crosses freezes to frosted ice for a moment (it melts away by itself), and fire it touches goes out. No blocks broken.
 * <p>
 * Kingdoms keeps the day's charge: a hold on cooldown comes back to us as {@link #BEAM_TAG} (Andy: "will be usable after
 * the single day ice charge"). Without Kingdoms there's no charge, and the hold is always the beam.
 */
public final class FrostBeam {
	/** Damage (1.5 hearts), reach (blocks), the wait between beams (ticks), and the beam's width and life on screen. */
	public static final float DAMAGE = 3.0F;
	public static final double RANGE = 48.0;
	public static final int COOLDOWN = 60;
	/**
	 * (Andy) A Z Fighter's beam: half a heart, plus {@code Z_PER_MASTERY} for each ability his mastery has unlocked
	 * ({@link com.jaymospowers.core.Mastery#unlocked}, 0-4: 1.5 hearts at full), and every {@code Z_COOLDOWN} ticks (1.5 s).
	 */
	public static final float Z_DAMAGE = 1.0F, Z_PER_MASTERY = 0.5F;
	public static final int Z_COOLDOWN = 30;
	public static final float RADIUS = 0.02F;
	public static final int SHOWN_TICKS = 2;
	public static final int COLOR = 0xFFFFFF;
	/** How the beams are drawn (ZFighterBeams: thin eye beams, no orbs). */
	public static final int EYE_STYLE = 3;
	/** The eyes: half the gap between them, how far in front of the face the beams start, and how far below the eye line (blocks). */
	public static final double EYE_GAP = 0.09, EYE_OUT = 0.25, EYE_DOWN = 0.02;
	/** Unheard from this long (ticks), a held beam stops; the client says it's still held every {@code RaceHoldClient.FROST_KEEP}. */
	public static final int KEEP_TICKS = 8;
	/** The hum while it's held (every this many ticks), and how long water it crosses stays frozen (ticks, then it melts). */
	public static final int HUM_EVERY = 12, FROST_TICKS = 60;
	/** A hit's chance to take or cripple the part it lands on, and a limb's share of those that cripple (not sever). */
	public static final float WOUND_CHANCE = 0.4F, CRIPPLE_SHARE = 0.5F;
	/** Kingdoms puts this on a Frost-born whose hold found the ice prison on its daily cooldown; taken off here. */
	public static final String BEAM_TAG = "jaymospowers_frost_beam";

	private static final boolean KINGDOMS = FabricLoader.getInstance().isModLoaded("jaymoskingdoms");
	/** When each player last hurt something with it (game time). Not saved: a restart only clears a few seconds. */
	private static final Map<UUID, Long> LAST = new HashMap<>();
	/** Who has the beam out, and until when (game time) unless the client says it's still held. */
	private static final Map<UUID, Long> ON = new HashMap<>();

	/** Client → server: the Frost-born's left mouse is still held (the beam stays out). */
	public record Keep() implements CustomPacketPayload {
		public static final Type<Keep> TYPE = new Type<>(com.jaymospowers.JaymosPowers.id("frost_beam_keep"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Keep> CODEC = StreamCodec.unit(new Keep());

		@Override
		public Type<Keep> type() {
			return TYPE;
		}
	}

	private FrostBeam() {
	}

	static void init() {
		PayloadTypeRegistry.serverboundPlay().register(Keep.TYPE, Keep.CODEC);
		// (only a beam already out is kept: a hold that froze something in the prison never starts one)
		ServerPlayNetworking.registerGlobalReceiver(Keep.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			if (ON.containsKey(player.getUUID()) && player.getMainHandItem().isEmpty()) {
				ON.put(player.getUUID(), player.level().getGameTime() + KEEP_TICKS);
			}
		});
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
		for (Iterator<Map.Entry<UUID, Long>> it = ON.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Long> on = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(on.getKey());
			if (player == null || !player.isAlive() || player.isSpectator() || ZFighterLineage.of(player) != ZFighterLineage.FROST
				|| player.level().getGameTime() > on.getValue()) {
				it.remove();
				continue;
			}
			beam(player);
		}
	}

	/** The beam comes out (the hold, once the day's ice is spent): it stays while the client keeps saying it's held. */
	static void fire(ServerPlayer player) {
		if (!player.isAlive() || player.isSpectator() || ZFighterLineage.of(player) != ZFighterLineage.FROST) {
			return;
		}
		ServerLevel level = player.level();
		boolean fresh = ON.put(player.getUUID(), level.getGameTime() + KEEP_TICKS) == null;
		if (fresh) {
			level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.2F, 1.8F);
			beam(player);
		}
	}

	/** One tick of the beam: drawn from both eyes to where the look meets something; a hit when the last one's long enough ago. */
	private static void beam(ServerPlayer player) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
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
		// the eyes, either side of the look, just in front of the face
		Vec3 side = look.cross(new Vec3(0.0, 1.0, 0.0));
		side = side.lengthSqr() < 1.0E-6 ? Vec3.directionFromRotation(0.0F, player.getYRot() + 90.0F) : side.normalize();
		Vec3 front = eye.add(look.scale(EYE_OUT)).add(0.0, -EYE_DOWN, 0.0);
		show(level, front.add(side.scale(EYE_GAP)), end);
		show(level, front.add(side.scale(-EYE_GAP)), end);
		if (now % 2L == 0L) {
			level.sendParticles(ParticleTypes.SNOWFLAKE, end.x, end.y, end.z, 3, 0.08, 0.08, 0.08, 0.02);
		}
		if (now % HUM_EVERY == 0L) {
			level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.5F, 1.9F);
		}
		if (target == null) {
			frost(player, level, eye, end, wall);
			return;
		}
		boolean fighter = com.jaymospowers.core.Powers.has(player, ZFighterPower.INSTANCE);
		Long last = LAST.get(player.getUUID());
		if (last != null && now - last < (fighter ? Z_COOLDOWN : COOLDOWN) && now >= last) {
			return;
		}
		LAST.put(player.getUUID(), now);
		level.playSound(null, end.x, end.y, end.z, SoundEvents.PLAYER_HURT_FREEZE, SoundSource.PLAYERS, 0.8F, 1.4F);
		level.sendParticles(ParticleTypes.SNOWFLAKE, end.x, end.y, end.z, 12, 0.15, 0.15, 0.15, 0.05);
		strike(player, target, fighter ? Z_DAMAGE + Z_PER_MASTERY * com.jaymospowers.core.Mastery.unlocked(player) : DAMAGE);
	}

	/** Still water the beam crosses freezes for a moment (frosted ice, which melts away), and fire where it lands goes out. */
	private static void frost(ServerPlayer player, ServerLevel level, Vec3 eye, Vec3 end, HitResult wall) {
		BlockHitResult water = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.SOURCE_ONLY, player));
		if (water.getType() == HitResult.Type.BLOCK) {
			BlockPos pos = water.getBlockPos();
			if (level.getFluidState(pos).is(Fluids.WATER) && level.getFluidState(pos).isSource() && level.getBlockState(pos).is(Blocks.WATER)) {
				level.setBlockAndUpdate(pos, Blocks.FROSTED_ICE.defaultBlockState());
				level.scheduleTick(pos, Blocks.FROSTED_ICE, FROST_TICKS);
			}
		}
		if (wall instanceof BlockHitResult block && wall.getType() == HitResult.Type.BLOCK) {
			BlockPos fire = block.getBlockPos().relative(block.getDirection());
			if (level.getBlockState(fire).is(net.minecraft.tags.BlockTags.FIRE)) {
				level.removeBlock(fire, false);
				level.playSound(null, fire, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.6F, 1.6F);
			}
		}
	}

	/** The beam lands on the part the crosshair crosses: {@code damage} there, then the roll to wound it. */
	private static void strike(ServerPlayer player, LivingEntity target, float damage) {
		ServerLevel level = player.level();
		int part = Dismember.aimedPart(player, target);
		// (Wounds lands a power's cut on its part; this one never severs on overkill by itself)
		Dismember.cut(player, target, part, 0.0F, () -> target.hurtServer(level, ZFighterKi.source(player), damage));
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

	/** One eye's beam, drawn for everyone near for a couple of ticks (it's sent again each tick while held): hair-thin, all white. */
	private static void show(ServerLevel level, Vec3 from, Vec3 to) {
		ZFighterNet.BeamFx fx = new ZFighterNet.BeamFx(from.x, from.y, from.z, to.x, to.y, to.z, RADIUS, COLOR, EYE_STYLE, SHOWN_TICKS);
		Vec3 middle = from.lerp(to, 0.5);
		for (ServerPlayer viewer : level.players()) {
			if (viewer.position().distanceToSqr(middle) <= ZFighterKi.BEAM_SEEN * ZFighterKi.BEAM_SEEN) {
				ServerPlayNetworking.send(viewer, fx);
			}
		}
	}
}
