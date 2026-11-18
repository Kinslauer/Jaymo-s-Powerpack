package com.jaymospowers.power.arsenal;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.api.PowerKey;
import com.jaymospowers.core.AbilityPower;
import com.jaymospowers.core.Fate;
import com.jaymospowers.core.Mastery;
import com.jaymospowers.core.PickKit;
import com.jaymospowers.core.fx.MagicFx;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Void Arsenal's blinks: the End's step, the one the endermen take. Every blink goes through a real rift pair, one
 * torn open where he leaves (facing the way he goes) and one where he comes out (facing back), drawn like his other rifts
 * ({@link #rifts}; {@code ArsenalBlinkRenderer} on the client). Only for a corrupted Arsenal past its starter
 * ({@link #voided}):
 * <ul>
 * <li>Sprint + R, <b>Blink</b>: up to {@link #BLINK_RANGE} blocks along the look, short of any wall, and untouchable for
 * {@link #UNTOUCHABLE} ticks.</li>
 * <li>Sprint + B, <b>Blink Strike</b>: behind the foe looked at, up to {@link #STRIKE_RANGE} blocks off, and a blow.</li>
 * <li>Sprint + Z, <b>Vanish</b>: blinding smoke; everyone round loses him, and he blinks a few blocks back unseen.</li>
 * <li>Sprint + X, <b>Sky Drop</b>: a foe close by and he go {@link #DROP_HEIGHT} blocks straight up; he lets go, and
 * blinks home while it falls.</li>
 * <li>Ascended, Sneak + C, <b>Flicker</b>: for {@link #FLICKER_TICKS} ticks he flickers from foe to foe round him, a blow
 * every {@link #FLICKER_EVERY} ticks, untouchable throughout.</li>
 * </ul>
 * The heavy hit that blinks a corrupted user away ({@link ArsenalVoid#blink}) opens the same pair.
 */
public final class ArsenalBlinks {
	// ---- tuning ----
	private static final String TABLE = "arsenal_blinks";
	private static final int BLINK = 0, STRIKE = 1, VANISH = 2, DROP = 3, FLICKER = 4;
	/** Blink: reach (blocks), untouchable (ticks), wait. */
	static final double BLINK_RANGE = 12.0;
	static final int UNTOUCHABLE = 10, BLINK_COOLDOWN = 30;
	/** Blink Strike: reach, the blow (a share of his attack damage, plus), wait. */
	static final double STRIKE_RANGE = 18.0;
	static final float STRIKE_SHARE = 1.5F, STRIKE_PLUS = 2.0F;
	static final int STRIKE_COOLDOWN = 80;
	/** Vanish: the smoke's reach, the blinding and the unseen time (ticks), how far back, wait. */
	static final double VANISH_RADIUS = 5.0, VANISH_BACK = 5.0;
	static final int VANISH_BLIND = 80, VANISH_UNSEEN = 100, VANISH_COOLDOWN = 200;
	/** Sky Drop: how close a foe, how high, the ticks up there before letting go, wait. */
	static final double DROP_REACH = 6.0;
	static final int DROP_HEIGHT = 14, DROP_HANG = 8, DROP_COOLDOWN = 300;
	/** Flicker: how long, a blow every, how far it looks for foes, the blow's share of his attack damage, wait. */
	static final int FLICKER_TICKS = 40, FLICKER_EVERY = 4, FLICKER_COOLDOWN = 20 * 60;
	static final double FLICKER_RANGE = 12.0;
	static final float FLICKER_SHARE = 0.7F;
	/** How far in front of where he leaves (and behind where he comes out) each rift opens. */
	static final double RIFT_OFFSET = 0.4;
	/** The motes at each end: how many (the rift itself is drawn on the client). */
	static final int MOTES = 2;
	static final int SMOKE = 0x2B2433, FLASH = 0xF0D8FF;

	/**
	 * Server → client: one blink's rift pair, where he left ({@code from}, facing {@code fromYaw}) and where he came out
	 * ({@code to}, facing {@code toYaw}), sized to the body that went through; the End's starfield ({@code voidRift}) or
	 * the nether-portal swirl, as his own rifts look right now.
	 */
	public record Tear(double fx, double fy, double fz, float fromYaw, double tx, double ty, double tz, float toYaw, float width, float height,
		boolean voidRift) implements CustomPacketPayload {
		public static final Type<Tear> TYPE = new Type<>(JaymosPowers.id("arsenal_blink"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Tear> CODEC = CustomPacketPayload.codec(
			(t, buf) -> {
				buf.writeDouble(t.fx);
				buf.writeDouble(t.fy);
				buf.writeDouble(t.fz);
				buf.writeFloat(t.fromYaw);
				buf.writeDouble(t.tx);
				buf.writeDouble(t.ty);
				buf.writeDouble(t.tz);
				buf.writeFloat(t.toYaw);
				buf.writeFloat(t.width);
				buf.writeFloat(t.height);
				buf.writeBoolean(t.voidRift);
			},
			buf -> new Tear(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readDouble(), buf.readDouble(),
				buf.readDouble(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readBoolean()));

		@Override
		public Type<Tear> type() {
			return TYPE;
		}
	}

	/** Untouchable until (game time). */
	private static final Map<UUID, Long> UNTOUCHED = new HashMap<>();

	private static final class Drop {
		LivingEntity foe;
		Vec3 home;
		long at;
	}

	private static final Map<UUID, Drop> DROPS = new HashMap<>();
	/** Flicker running until (game time). */
	private static final Map<UUID, Long> FLICKERING = new HashMap<>();

	private ArsenalBlinks() {
	}

	static void init() {
		PayloadTypeRegistry.clientboundPlay().register(Tear.TYPE, Tear.CODEC);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
			!untouchable(entity) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forget(handler.player));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			UNTOUCHED.clear();
			DROPS.clear();
			FLICKERING.clear();
		});
	}

	/** The power went, he went down, or the void left him: whatever blink was running ends. */
	static void forget(ServerPlayer player) {
		UNTOUCHED.remove(player.getUUID());
		DROPS.remove(player.getUUID());
		FLICKERING.remove(player.getUUID());
	}

	/** In the instant between here and there: nothing touches him. */
	static boolean untouchable(Entity e) {
		Long until = UNTOUCHED.get(e.getUUID());
		return until != null && e.level().getGameTime() <= until;
	}

	/** The blinks are the Void Arsenal's, once past its starter (the same test as its other keys). */
	static boolean voided(ServerPlayer player) {
		return Fate.isCorrupted(player) && !Mastery.starterOnly(player);
	}

	private static boolean sprinting(ServerPlayer player) {
		return player.isSprinting() && !player.isShiftKeyDown() && voided(player);
	}

	// ---- the keys (each true when the press was a blink's) ----

	/** Sprint + R: Blink; sprint + B: Blink Strike. */
	static boolean key(ServerPlayer player, PowerKey key) {
		if (!sprinting(player)) {
			return false;
		}
		switch (key) {
			case ABILITY_1 -> blink(player);
			case ABILITY_4 -> strike(player);
			default -> {
				return false;
			}
		}
		return true;
	}

	/** Sprint + Z: Vanish. */
	static boolean mode(ServerPlayer player) {
		if (!sprinting(player)) {
			return false;
		}
		vanish(player);
		return true;
	}

	/** Sprint + X: Sky Drop (so the press never starts the Cannon Battery). */
	static boolean special(ServerPlayer player) {
		if (!sprinting(player)) {
			return false;
		}
		drop(player);
		return true;
	}

	/** Ascended, Sneak + C: Flicker. */
	static boolean ultimate(ServerPlayer player) {
		if (!player.isShiftKeyDown() || !Fate.isAscended(player) || !voided(player)) {
			return false;
		}
		if (ready(player, FLICKER)) {
			PickKit.spend(TABLE, player, FLICKER, FLICKER_COOLDOWN);
			long until = player.level().getGameTime() + FLICKER_TICKS;
			FLICKERING.put(player.getUUID(), until);
			UNTOUCHED.put(player.getUUID(), until);
		}
		return true;
	}

	private static boolean ready(ServerPlayer player, int slot) {
		if (PickKit.ready(TABLE, player, slot) || player.getAbilities().instabuild) {
			return true;
		}
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.arsenal.blink_wait", (PickKit.left(TABLE, player, slot) + 19) / 20)
			.withStyle(ChatFormatting.DARK_PURPLE));
		return false;
	}

	// ---- the step ----

	/** Steps {@code e} to {@code to} (a spot it fits) through a rift pair opened by {@code opener}. */
	static void step(ServerLevel level, ServerPlayer opener, LivingEntity e, Vec3 to) {
		Vec3 from = e.position();
		if (e.isPassenger()) {
			e.stopRiding();
		}
		if (e instanceof ServerPlayer p) {
			p.teleportTo(level, to.x, to.y, to.z, Set.of(), p.getYRot(), p.getXRot(), false);
		} else {
			e.teleportTo(to.x, to.y, to.z);
		}
		e.resetFallDistance();
		rifts(level, e, from, to, ArsenalVoid.isVoid(opener));
		level.playSound(null, to.x, to.y, to.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.7F, 1.4F);
	}

	/**
	 * Opens the rift pair for {@code e}'s step from {@code from} to {@code to} on every client near either end: the one
	 * it left by faces the way it went, the one it came out of faces back. A mote or two drifts off each.
	 */
	public static void rifts(ServerLevel level, LivingEntity e, Vec3 from, Vec3 to, boolean voidRift) {
		Vec3 d = to.subtract(from).multiply(1.0, 0.0, 1.0);
		boolean vertical = d.lengthSqr() < 1.0E-4;
		float yaw = vertical ? e.getYRot() : (float)Math.toDegrees(Math.atan2(-d.x, d.z));
		Vec3 ahead = vertical ? Vec3.ZERO : d.normalize().scale(RIFT_OFFSET);
		Vec3 out = from.add(ahead);
		Vec3 in = to.subtract(ahead);
		Tear tear = new Tear(out.x, out.y, out.z, yaw, in.x, in.y, in.z, yaw + 180.0F, e.getBbWidth(), e.getBbHeight(), voidRift);
		double view = ArsenalPocket.VIEW_DISTANCE * ArsenalPocket.VIEW_DISTANCE;
		for (ServerPlayer viewer : level.players()) {
			if ((viewer.position().distanceToSqr(out) < view || viewer.position().distanceToSqr(in) < view) && ServerPlayNetworking.canSend(viewer, Tear.TYPE)) {
				ServerPlayNetworking.send(viewer, tear);
			}
		}
		double half = e.getBbHeight() * 0.5;
		level.sendParticles(MagicFx.VOID, out.x, out.y + half, out.z, MOTES, 0.2, half * 0.5, 0.2, 0.02);
		level.sendParticles(MagicFx.VOID, in.x, in.y + half, in.z, MOTES, 0.2, half * 0.5, 0.2, 0.02);
	}

	/** The nearest spot {@code e} fits at {@code at}: there, a little above or below. */
	static @Nullable Vec3 fit(ServerLevel level, LivingEntity e, Vec3 at) {
		for (double dy : new double[] {0.0, 0.5, 1.0, -0.5, 1.5, -1.0, 2.0}) {
			Vec3 spot = at.add(0.0, dy, 0.0);
			if (level.noCollision(e, e.getDimensions(e.getPose()).makeBoundingBox(spot))) {
				return spot;
			}
		}
		return null;
	}

	/** Sprint + R: along the look, short of any wall, untouchable a moment. */
	static void blink(ServerPlayer player) {
		if (!ready(player, BLINK)) {
			return;
		}
		ServerLevel level = player.level();
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		BlockHitResult wall = level.clip(new ClipContext(eye, eye.add(look.scale(BLINK_RANGE)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		double reach = wall.getType() == HitResult.Type.MISS ? BLINK_RANGE : Math.max(0.0, wall.getLocation().distanceTo(eye) - 0.8);
		Vec3 to = null;
		for (double d = reach; d >= 1.0 && to == null; d -= 0.5) {
			to = fit(level, player, player.position().add(look.scale(d)));
		}
		if (to == null) {
			return;
		}
		PickKit.spend(TABLE, player, BLINK, BLINK_COOLDOWN);
		UNTOUCHED.put(player.getUUID(), level.getGameTime() + UNTOUCHABLE);
		Vec3 keep = player.getDeltaMovement();
		step(level, player, player, to);
		player.setDeltaMovement(keep.multiply(1.0, 0.0, 1.0));
		player.syncVelocity = true;
	}

	/** Sprint + B: behind the foe looked at, and a blow. */
	static void strike(ServerPlayer player) {
		LivingEntity foe = PickKit.aimed(player, STRIKE_RANGE);
		if (foe == null) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.arsenal.blink_no_foe").withStyle(ChatFormatting.GRAY));
			return;
		}
		if (!ready(player, STRIKE)) {
			return;
		}
		ServerLevel level = player.level();
		Vec3 behind = foe.position().subtract(player.position()).multiply(1.0, 0.0, 1.0);
		behind = behind.lengthSqr() < 1.0E-4 ? player.getLookAngle().multiply(1.0, 0.0, 1.0) : behind.normalize();
		Vec3 to = fit(level, player, foe.position().add(behind.scale(foe.getBbWidth() * 0.5 + 0.8)));
		if (to == null) {
			to = fit(level, player, foe.position().subtract(behind.scale(foe.getBbWidth() * 0.5 + 0.8)));
		}
		if (to == null) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.arsenal.blink_no_room").withStyle(ChatFormatting.GRAY));
			return;
		}
		PickKit.spend(TABLE, player, STRIKE, STRIKE_COOLDOWN);
		UNTOUCHED.put(player.getUUID(), level.getGameTime() + UNTOUCHABLE);
		step(level, player, player, to);
		face(player, foe);
		hit(level, player, foe, STRIKE_SHARE, STRIKE_PLUS);
	}

	private static void face(ServerPlayer player, LivingEntity foe) {
		Vec3 d = foe.getEyePosition().subtract(player.getEyePosition());
		float yaw = (float)(Math.atan2(d.z, d.x) * (180.0 / Math.PI)) - 90.0F;
		float pitch = (float)(-Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * (180.0 / Math.PI));
		player.teleportTo(player.level(), player.getX(), player.getY(), player.getZ(), Set.of(), yaw, pitch, false);
	}

	private static void hit(ServerLevel level, ServerPlayer player, LivingEntity foe, float share, float plus) {
		float harm = (float)player.getAttributeValue(Attributes.ATTACK_DAMAGE) * share + plus;
		foe.setInvulnerableTime(0);
		foe.hurtServer(level, player.damageSources().playerAttack(player), harm);
		player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		Vec3 c = foe.getBoundingBox().getCenter();
		MagicFx.stroke(level, c.add(-0.6, 0.5, 0.0), c.add(0.6, -0.5, 0.0), 0.1F, FLASH);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.9F, 1.4F);
	}

	/** Sprint + Z: blinding smoke, and gone a few steps back. */
	static void vanish(ServerPlayer player) {
		if (!ready(player, VANISH)) {
			return;
		}
		ServerLevel level = player.level();
		PickKit.spend(TABLE, player, VANISH, VANISH_COOLDOWN);
		Vec3 at = player.position();
		double radius = AbilityPower.size(player, VANISH_RADIUS);
		level.sendParticles(MagicFx.miasma(SMOKE, 2.0F), at.x, at.y + 1.0, at.z, 60, radius * 0.4, 1.0, radius * 0.4, 0.02);
		MagicFx.blast(level, at.add(0.0, 1.0, 0.0), (float)radius * 0.6F, SMOKE);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 1.0F, 0.8F);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius), e -> e != player && e.isAlive())) {
			e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, VANISH_BLIND, 0), player);
			if (e instanceof Mob mob && mob.getTarget() == player) {
				mob.setTarget(null);
				mob.getNavigation().stop();
			}
		}
		player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, VANISH_UNSEEN, 0, false, false));
		Vec3 back = player.getLookAngle().multiply(-1.0, 0.0, -1.0);
		back = back.lengthSqr() < 1.0E-4 ? Vec3.ZERO : back.normalize();
		for (double d = VANISH_BACK; d >= 1.0; d -= 0.5) {
			Vec3 to = fit(level, player, at.add(back.scale(d)));
			if (to != null && level.clip(new ClipContext(player.getEyePosition(), to.add(0.0, player.getEyeHeight(), 0.0), ClipContext.Block.COLLIDER,
				ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.MISS) {
				UNTOUCHED.put(player.getUUID(), level.getGameTime() + UNTOUCHABLE);
				step(level, player, player, to);
				break;
			}
		}
	}

	/** Sprint + X: up with a foe, and let go. */
	static void drop(ServerPlayer player) {
		LivingEntity foe = PickKit.aimed(player, DROP_REACH);
		if (foe == null) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.arsenal.blink_no_foe").withStyle(ChatFormatting.GRAY));
			return;
		}
		if (!foe.canUsePortal(false)) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.arsenal.blink_too_much").withStyle(ChatFormatting.GRAY));
			return;
		}
		ServerLevel level = player.level();
		Vec3 foeUp = foe.position().add(0.0, DROP_HEIGHT, 0.0);
		Vec3 apart = player.position().subtract(foe.position()).multiply(1.0, 0.0, 1.0);
		apart = apart.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : apart.normalize();
		Vec3 meUp = foeUp.add(apart.scale(foe.getBbWidth() * 0.5 + 0.8));
		if (!level.noCollision(foe, foe.getDimensions(foe.getPose()).makeBoundingBox(foeUp))
			|| !level.noCollision(player, player.getDimensions(player.getPose()).makeBoundingBox(meUp))
			|| roofed(level, player, foe.getEyePosition(), foeUp) || roofed(level, player, player.getEyePosition(), meUp)) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.arsenal.blink_no_sky").withStyle(ChatFormatting.GRAY));
			return;
		}
		if (!ready(player, DROP)) {
			return;
		}
		PickKit.spend(TABLE, player, DROP, DROP_COOLDOWN);
		Drop d = new Drop();
		d.foe = foe;
		d.home = player.position();
		d.at = level.getGameTime();
		DROPS.put(player.getUUID(), d);
		UNTOUCHED.put(player.getUUID(), level.getGameTime() + DROP_HANG + UNTOUCHABLE);
		step(level, player, foe, foeUp);
		step(level, player, player, meUp);
		foe.setDeltaMovement(Vec3.ZERO);
		foe.syncVelocity = true;
	}

	/** Something solid between {@code from} and the top of the column at {@code up}: a roof. */
	private static boolean roofed(ServerLevel level, Entity viewer, Vec3 from, Vec3 up) {
		return level.clip(new ClipContext(from, up.add(0.0, 2.0, 0.0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, viewer))
			.getType() != HitResult.Type.MISS;
	}

	/** Every tick, for an Arsenal user (from {@link ArsenalPower#tick}). */
	static void tick(ServerPlayer player) {
		Drop d = DROPS.get(player.getUUID());
		Long flicker = FLICKERING.get(player.getUUID());
		if (d == null && flicker == null) {
			return;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		if (d != null) {
			if (now - d.at < DROP_HANG && d.foe.isAlive()) {
				// held up there a moment, then let go
				d.foe.setDeltaMovement(Vec3.ZERO);
				d.foe.syncVelocity = true;
				player.setDeltaMovement(Vec3.ZERO);
				player.syncVelocity = true;
				player.resetFallDistance();
			} else {
				DROPS.remove(player.getUUID());
				Vec3 home = fit(level, player, d.home);
				if (home != null && player.isAlive()) {
					UNTOUCHED.put(player.getUUID(), now + UNTOUCHABLE);
					step(level, player, player, home);
				}
			}
		}
		if (flicker != null) {
			if (now > flicker || !player.isAlive()) {
				FLICKERING.remove(player.getUUID());
			} else if (now % FLICKER_EVERY == 0) {
				flick(level, player);
			}
		}
	}

	/** One flicker: through a rift to beside a foe round him, and a blow. */
	private static void flick(ServerLevel level, ServerPlayer player) {
		List<LivingEntity> foes = new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(FLICKER_RANGE),
			e -> PickKit.foe(player, e) && e.distanceTo(player) <= FLICKER_RANGE));
		if (foes.isEmpty()) {
			return;
		}
		LivingEntity foe = foes.get(level.getRandom().nextInt(foes.size()));
		double angle = level.getRandom().nextDouble() * Math.PI * 2.0;
		Vec3 side = new Vec3(Math.cos(angle), 0.0, Math.sin(angle)).scale(foe.getBbWidth() * 0.5 + 0.8);
		Vec3 to = fit(level, player, foe.position().add(side));
		if (to == null) {
			return;
		}
		step(level, player, player, to);
		face(player, foe);
		hit(level, player, foe, FLICKER_SHARE, 0.0F);
	}
}
