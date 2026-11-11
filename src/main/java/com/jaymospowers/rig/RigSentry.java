package com.jaymospowers.rig;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.fx.MagicFx;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Sentry: step out and the Mind stands the suit where you leave it (Sneak + the call key, or the Rig menu; the Core
 * at Mind level 3, as for parking). The suit is parked as ever ({@link RigCall}); what stands there is an armour
 * stand wearing a copy of the pieces, which can't be hurt or stripped. While you are on the server and in the same
 * dimension it guards its spot: it shoots the nearest hostile creature within {@link #GUARD_RADIUS}, and whatever
 * last hurt you within {@link #ASSIST_RADIUS} of it. Calling the suit (the call key) takes the stand away and
 * the plates fly in from where it stood. The stand carries its owner ({@link #OWNER}); one whose owner has called
 * the suit since (it was in an unloaded chunk or another dimension then) is removed when it loads.
 */
public final class RigSentry {
	/** On the player: the standing suit's armour stand. Kept through death (the suit is parked, not worn). */
	public static final AttachmentType<UUID> POST = AttachmentRegistry.create(JaymosPowers.id("rig_sentry_post"),
		builder -> builder.persistent(UUIDUtil.CODEC).copyOnDeath());
	/** On the armour stand: whose suit it is. */
	public static final AttachmentType<UUID> OWNER = AttachmentRegistry.create(JaymosPowers.id("rig_sentry_owner"),
		builder -> builder.persistent(UUIDUtil.CODEC));

	/** How far it sees a hostile creature, and how far from it the thing that hurt you still draws its fire (blocks). */
	static final double GUARD_RADIUS = 14.0, ASSIST_RADIUS = 40.0;
	/** The shot: damage, ticks between shots, how often it looks for a target (ticks), how long you count as just hurt (ticks). */
	static final float SHOT_DAMAGE = 5.0F;
	static final int SHOT_COOLDOWN = 20, LOOK_EVERY = 5, HURT_MEMORY = 200;
	/** The shot's look: colour, thickness (blocks), life (ticks), and how high on the stand it leaves from. */
	static final int SHOT_COLOR = 0x5FE6FF;
	static final float SHOT_RADIUS = 0.07F;
	static final int SHOT_TICKS = 6;
	static final double MUZZLE_HEIGHT = 1.4;

	private RigSentry() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(RigSentry::tick);
		// nobody else strips it, breaks it, or poses it
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> entity.hasAttached(OWNER) ? InteractionResult.FAIL : InteractionResult.PASS);
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> entity.hasAttached(OWNER) && !player.isCreative()
			? InteractionResult.FAIL : InteractionResult.PASS);
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			UUID owner = entity.getAttached(OWNER);
			if (owner == null) {
				return;
			}
			ServerPlayer player = level.getServer().getPlayerList().getPlayer(owner);
			if (player != null && !entity.getUUID().equals(player.getAttached(POST))) {
				// its owner called the suit while it was away
				level.getServer().execute(entity::discard);
			}
		});
	}

	/** The Sentry key: stand the worn suit where you are, or (it already stands there) call it. */
	static void use(ServerPlayer player) {
		if (player.hasAttached(POST) || RigCall.parked(player) && !Rig.wearsAny(player)) {
			RigCall.use(player);
			return;
		}
		if (!Rig.wearsAny(player)) {
			return;
		}
		List<ItemStack> wore = wornCopies(player);
		if (!RigCall.park(player)) {
			return;
		}
		ServerLevel level = player.level();
		ArmorStand stand = EntityTypes.ARMOR_STAND.create(level, EntitySpawnReason.TRIGGERED);
		if (stand == null) {
			return;
		}
		stand.snapTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0.0F);
		stand.setShowArms(true);
		stand.setNoBasePlate(true);
		stand.setPermanentlyInvulnerable(true);
		for (Rig.Piece piece : Rig.Piece.values()) {
			ItemStack copy = wore.get(piece.ordinal());
			if (!copy.isEmpty()) {
				stand.setItemSlot(piece.slot, copy);
			}
		}
		stand.setAttached(OWNER, player.getUUID());
		level.addFreshEntity(stand);
		player.setAttached(POST, stand.getUUID());
		MagicFx.ring(level, stand.position().add(0.0, 0.1, 0.0), 1.4F, SHOT_COLOR);
		level.playSound(null, stand.getX(), stand.getY(), stand.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8F, 1.3F);
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.rig.sentry_set"));
	}

	private static List<ItemStack> wornCopies(ServerPlayer player) {
		return java.util.Arrays.stream(Rig.Piece.values()).map(piece -> {
			ItemStack worn = player.getItemBySlot(piece.slot);
			return piece.item() != null && worn.is(piece.item()) ? worn.copy() : ItemStack.EMPTY;
		}).toList();
	}

	/** Takes the owner's standing suit away (if it stands in a loaded place); where it stood, or null if nowhere. */
	static @Nullable Vec3 recall(ServerPlayer player) {
		UUID id = player.getAttached(POST);
		if (id == null) {
			return null;
		}
		player.removeAttached(POST);
		Entity stand = player.level().getServer().overworld().getEntityInAnyDimension(id);
		if (stand == null) {
			return null;
		}
		Vec3 at = stand.position();
		stand.discard();
		return at;
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			UUID id = player.getAttached(POST);
			if (id == null || player.isSpectator() || player.tickCount % LOOK_EVERY != 0) {
				continue;
			}
			if (!(server.overworld().getEntityInAnyDimension(id) instanceof ArmorStand stand) || stand.level() != player.level()) {
				continue;
			}
			guard(player, stand);
		}
	}

	/** One look about: face and shoot the target, if the last shot is done with. */
	private static void guard(ServerPlayer owner, ArmorStand stand) {
		ServerLevel level = owner.level();
		LivingEntity target = target(owner, stand);
		if (target == null) {
			return;
		}
		Vec3 from = stand.position().add(0.0, MUZZLE_HEIGHT, 0.0);
		Vec3 to = target.getBoundingBox().getCenter();
		Vec3 d = to.subtract(from);
		stand.setYRot((float)(Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90.0F);
		long now = level.getGameTime();
		// the stand's own clock: the last shot is kept in its age
		if (now - stand.getAttachedOrElse(LAST_SHOT, 0L) < SHOT_COOLDOWN) {
			return;
		}
		stand.setAttached(LAST_SHOT, now);
		MagicFx.beam(level, from, to, SHOT_RADIUS, SHOT_COLOR, SHOT_TICKS);
		MagicFx.blast(level, to, 0.5F, SHOT_COLOR);
		target.hurtServer(level, level.damageSources().playerAttack(owner), SHOT_DAMAGE);
		level.playSound(null, stand.getX(), stand.getY(), stand.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.7F, 1.9F);
	}

	/** Not saved: when the stand last fired. */
	private static final AttachmentType<Long> LAST_SHOT = AttachmentRegistry.create(JaymosPowers.id("rig_sentry_shot"));

	/** Whatever last hurt the owner if it is near, else the nearest hostile creature in sight. */
	private static @Nullable LivingEntity target(ServerPlayer owner, ArmorStand stand) {
		LivingEntity attacker = owner.getLastHurtByMob();
		if (attacker != null && attacker.isAlive() && attacker != owner && owner.tickCount - owner.getLastHurtByMobTimestamp() < HURT_MEMORY
			&& attacker.level() == stand.level() && attacker.distanceTo(stand) <= ASSIST_RADIUS && stand.hasLineOfSight(attacker)) {
			return attacker;
		}
		LivingEntity best = null;
		double bestDistance = Double.MAX_VALUE;
		for (LivingEntity entity : stand.level().getEntitiesOfClass(LivingEntity.class, stand.getBoundingBox().inflate(GUARD_RADIUS),
			e -> e instanceof Enemy && e.isAlive() && !e.isSpectator())) {
			double distance = entity.distanceToSqr(stand);
			if (distance < bestDistance && stand.hasLineOfSight(entity)) {
				bestDistance = distance;
				best = entity;
			}
		}
		return best;
	}
}
