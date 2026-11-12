package com.jaymospowers.rig;

import com.jaymospowers.core.PickKit;
import com.jaymospowers.core.fx.MagicFx;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The decoy: a false image of the wearer, a vanilla mannequin in their skin (drawn by the mannequin's own renderer, the way
 * the Z Fighter's afterimage and Gravity's copy are), wound up out of brass and glass. Two kinds, picked at the wrist table:
 * <ul>
 * <li><b>Lure</b>: one image stands {@link #AHEAD} blocks off and every enemy within {@link #LURE_RANGE} turns on it for
 * {@link #LURE_TICKS}, while the wearer is unseen (invisible, so the armour goes too). The first blow to the image pops it.</li>
 * <li><b>Trio</b>: three images hold the wearer's pose a few blocks off, move as they move, swing when they swing, and
 * every blow the wearer lands is struck again by each image for {@link #TRIO_SHARE} of its damage. They last
 * {@link #TRIO_TICKS}; a blow to one pops it.</li>
 * </ul>
 */
public final class RigDecoy {
	/** The Core's charge, and the cooldown (ticks). */
	public static final int COST = 90, COOLDOWN = 300;
	/** Lure: how far ahead the image stands, how far its pull reaches, how long it all lasts, how often the pull is renewed. */
	public static final double AHEAD = 5.0, LURE_RANGE = 32.0;
	public static final int LURE_TICKS = 100, LURE_EVERY = 5;
	/** Trio: how long, how far off each image holds, the share of the wearer's damage each strikes for, and how far from its target an image still strikes. */
	public static final int TRIO_TICKS = 160;
	public static final double TRIO_OFFSET = 2.2, TRIO_REACH = 14.0;
	public static final float TRIO_SHARE = 0.2F;
	/** The mannequin's tag, so one left over from a restart can be swept away. */
	public static final String TAG = "jaymospowers_rig_decoy";
	public static final int GOLD = 0xE8B84A;
	private static final String TABLE = "rig_decoy";

	private record Set3(ServerLevel level, List<UUID> copies, List<Vec3> offsets, boolean trio, long until) {
	}

	private static final Map<UUID, Set3> ACTIVE = new HashMap<>();
	/** Wearers who picked the trio (the lure is the default). */
	private static final Set<UUID> TRIO = new HashSet<>();
	private static boolean striking;

	private RigDecoy() {
	}

	/** The mannequin's skin, which only a mannequin's own code may set: read through a subclass that's never made. */
	private abstract static class Profile extends Mannequin {
		private Profile(Level level) {
			super(level);
		}

		static EntityDataAccessor<ResolvableProfile> key() {
			return DATA_PROFILE;
		}
	}

	static void init() {
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(RigDecoy::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> clearAll());
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			Set3 set = ACTIVE.remove(handler.player.getUUID());
			if (set != null) {
				popAll(set);
			}
			TRIO.remove(handler.player.getUUID());
		});
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Mannequin && entity.entityTags().contains(TAG) && !isCopy(entity.getUUID())) {
				entity.discard();
			}
		});
		// a blow to an image pops it and does nothing else
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (entity instanceof Mannequin && entity.entityTags().contains(TAG) && entity.level() instanceof ServerLevel) {
				pop(entity);
				return false;
			}
			return true;
		});
		// the trio strikes again whatever the wearer strikes
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (striking || blocked || taken <= 0.0F || !(source.getEntity() instanceof ServerPlayer owner) || source.getDirectEntity() != owner) {
				return;
			}
			Set3 set = ACTIVE.get(owner.getUUID());
			if (set == null || !set.trio || entity instanceof Mannequin || !PickKit.foe(owner, entity)) {
				return;
			}
			striking = true;
			try {
				for (UUID id : set.copies) {
					Entity copy = set.level.getEntity(id);
					if (copy instanceof Mannequin && copy.isAlive() && copy.distanceToSqr(entity) <= TRIO_REACH * TRIO_REACH) {
						entity.setInvulnerableTime(0);
						entity.hurtServer(set.level, owner.damageSources().playerAttack(owner), taken * TRIO_SHARE);
						MagicFx.stroke(set.level, copy.position().add(0.0, 1.0, 0.0), entity.getBoundingBox().getCenter(), 0.1F, GOLD);
					}
				}
			} finally {
				striking = false;
			}
		});
	}

	static boolean trio(ServerPlayer player) {
		return TRIO.contains(player.getUUID());
	}

	/** The table's Decoy kind: lure or trio. */
	static void toggleKind(ServerPlayer player) {
		boolean now = TRIO.add(player.getUUID());
		if (!now) {
			TRIO.remove(player.getUUID());
		}
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.rig_picks.decoy.kind_" + (now ? "trio" : "lure")).withStyle(ChatFormatting.GOLD));
	}

	private static boolean isCopy(UUID id) {
		for (Set3 set : ACTIVE.values()) {
			if (set.copies.contains(id)) {
				return true;
			}
		}
		return false;
	}

	private static void clearAll() {
		for (Set3 set : ACTIVE.values()) {
			for (UUID id : set.copies) {
				Entity entity = set.level.getEntity(id);
				if (entity != null) {
					entity.discard();
				}
			}
		}
		ACTIVE.clear();
	}

	// ---- the Decoy entry ------------------------------------------------------------------------------------------------

	static void use(ServerPlayer player) {
		if (Rig.core(player).isEmpty()) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.rig_picks.needs_core").withStyle(ChatFormatting.GRAY));
			return;
		}
		if (!PickKit.ready(TABLE, player, 0)) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.rig_picks.decoy.cooling").withStyle(ChatFormatting.GRAY));
			return;
		}
		if (!Rig.spend(player, COST)) {
			RigMind.empty(player);
			return;
		}
		PickKit.spend(TABLE, player, 0, COOLDOWN);
		Set3 old = ACTIVE.remove(player.getUUID());
		if (old != null) {
			popAll(old);
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		boolean trio = trio(player);
		List<UUID> copies = new ArrayList<>();
		List<Vec3> offsets = new ArrayList<>();
		Vec3 here = player.position();
		Vec3 look = player.getLookAngle().multiply(1.0, 0.0, 1.0);
		look = look.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : look.normalize();
		Vec3 right = new Vec3(-look.z, 0.0, look.x);
		if (trio) {
			offsets.add(right.scale(TRIO_OFFSET));
			offsets.add(right.scale(-TRIO_OFFSET));
			offsets.add(look.scale(-TRIO_OFFSET));
		} else {
			offsets.add(look.scale(AHEAD));
		}
		ServerPlayer owner = player;
		Set3 set = new Set3(level, copies, offsets, trio, now + (trio ? TRIO_TICKS : LURE_TICKS));
		ACTIVE.put(player.getUUID(), set);
		for (Vec3 offset : offsets) {
			Mannequin copy = EntityTypes.MANNEQUIN.create(level, EntitySpawnReason.MOB_SUMMONED);
			if (copy == null) {
				continue;
			}
			Vec3 at = place(level, copy, here, offset);
			copy.getEntityData().set(Profile.key(), ResolvableProfile.createResolved(owner.getGameProfile()));
			copy.addTag(TAG);
			copy.snapTo(at.x, at.y, at.z, owner.getYRot(), owner.getXRot());
			copy.setYHeadRot(owner.getYHeadRot());
			copy.setYBodyRot(owner.yBodyRot);
			copy.setNoGravity(trio);
			copies.add(copy.getUUID());
			if (level.addFreshEntity(copy)) {
				wind(level, at);
			} else {
				copies.remove(copy.getUUID());
			}
		}
		if (copies.isEmpty()) {
			ACTIVE.remove(player.getUUID());
			return;
		}
		if (!trio) {
			player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, LURE_TICKS, 0, true, false, false));
			lure(player, set);
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.COPPER_GOLEM_SPIN, SoundSource.PLAYERS, 0.9F, 1.5F);
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.rig_picks.decoy." + (trio ? "trio" : "lure")).withStyle(ChatFormatting.GOLD));
	}

	/** Somewhere near {@code here + offset} the image fits, else beside the wearer. */
	private static Vec3 place(ServerLevel level, Mannequin copy, Vec3 here, Vec3 offset) {
		for (double lift : new double[] {0.0, 1.0, 2.0, -1.0}) {
			Vec3 at = here.add(offset).add(0.0, lift, 0.0);
			if (level.noCollision(copy, copy.getBoundingBox().move(at.subtract(copy.position())))) {
				return at;
			}
		}
		return here.add(offset.scale(0.4));
	}

	private static void wind(ServerLevel level, Vec3 at) {
		level.sendParticles(ParticleTypes.POOF, true, false, at.x, at.y + 1.0, at.z, 8, 0.25, 0.5, 0.25, 0.02);
		MagicFx.ring(level, at.add(0.0, 0.1, 0.0), null, 0.9F, GOLD, 10);
	}

	// ---- every tick -----------------------------------------------------------------------------------------------------

	/** Every enemy in range, and whatever hunts the wearer, turns on the image. */
	private static void lure(ServerPlayer owner, Set3 set) {
		Entity copy = set.copies.isEmpty() ? null : set.level.getEntity(set.copies.get(0));
		if (!(copy instanceof LivingEntity image)) {
			return;
		}
		for (Mob mob : set.level.getEntitiesOfClass(Mob.class, new AABB(owner.position(), owner.position()).inflate(LURE_RANGE),
			m -> m.isAlive() && (m instanceof Enemy || m.getTarget() == owner) && PickKit.foe(owner, m))) {
			mob.setTarget(image);
		}
	}

	private static void tick(MinecraftServer server) {
		if (ACTIVE.isEmpty()) {
			return;
		}
		for (Iterator<Map.Entry<UUID, Set3>> it = ACTIVE.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Set3> entry = it.next();
			Set3 set = entry.getValue();
			ServerPlayer owner = server.getPlayerList().getPlayer(entry.getKey());
			long now = set.level.getGameTime();
			set.copies.removeIf(id -> !(set.level.getEntity(id) instanceof Mannequin m) || !m.isAlive());
			if (owner == null || !owner.isAlive() || owner.level() != set.level || now >= set.until || set.copies.isEmpty() && set.trio) {
				popAll(set);
				it.remove();
				continue;
			}
			if (set.trio) {
				mirror(owner, set);
			} else if (set.copies.isEmpty()) {
				// the lure image was struck: the pull is over (the wearer stays unseen until it wears off)
				it.remove();
			} else if (now % LURE_EVERY == 0) {
				lure(owner, set);
			}
		}
	}

	/** The trio holds the wearer's pose and moves as they move. */
	private static void mirror(ServerPlayer owner, Set3 set) {
		for (int k = 0; k < set.copies.size(); k++) {
			if (!(set.level.getEntity(set.copies.get(k)) instanceof Mannequin copy)) {
				continue;
			}
			Vec3 offset = set.offsets.get(Math.min(k, set.offsets.size() - 1));
			Vec3 want = place(set.level, copy, owner.position(), offset);
			copy.snapTo(want.x, want.y, want.z, owner.getYRot(), owner.getXRot());
			copy.setYHeadRot(owner.getYHeadRot());
			copy.setYBodyRot(owner.yBodyRot);
			copy.setPose(owner.getPose());
			copy.setShiftKeyDown(owner.isShiftKeyDown());
			copy.setSprinting(owner.isSprinting());
			copy.setDeltaMovement(owner.getDeltaMovement());
			if (owner.isSwinging() && !copy.isSwinging()) {
				copy.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
			}
		}
	}

	private static void popAll(Set3 set) {
		for (UUID id : set.copies) {
			Entity entity = set.level.getEntity(id);
			if (entity != null) {
				pop(entity);
			}
		}
		set.copies.clear();
	}

	/** An image pops: a puff of smoke and brass, and gone. */
	private static void pop(Entity entity) {
		if (!(entity.level() instanceof ServerLevel level) || entity.isRemoved()) {
			return;
		}
		Vec3 at = entity.position();
		level.sendParticles(ParticleTypes.POOF, true, false, at.x, at.y + 1.0, at.z, 12, 0.3, 0.6, 0.3, 0.03);
		MagicFx.burst(level, at.x, at.y + 1.0, at.z, GOLD, 0.6F, 8, 0.3, 0.6, 0.3);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.COPPER_GOLEM_ITEM_DROP, SoundSource.PLAYERS, 0.8F, 1.4F);
		entity.discard();
	}
}
