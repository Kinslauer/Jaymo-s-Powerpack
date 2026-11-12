package com.jaymospowers.gear.arrow;

import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.core.wounds.Bleeding;
import com.jaymospowers.core.wounds.BodyPart;
import com.jaymospowers.core.wounds.Tripped;
import com.jaymospowers.gear.GroundFx;
import com.jaymospowers.gear.arrow.mixin.ArrowPierceInvoker;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * What each {@link ArrowHead} does. {@link #arm} marks a fresh arrow with its head (and sets what a head changes about the
 * flight); the mixin on {@code AbstractArrow} calls {@link #hitEntity} and {@link #hitBlock} when it lands. The bola trips
 * through the bolas' own code ({@link Tripped#bind}), the barbed head opens a cut ({@link Bleeding#open}).
 */
public final class HeadFx {
	// ---- tuning ----
	/** Broadhead: damage over a plain arrow's base (2), and the share of the launch speed it keeps. */
	public static final double BROADHEAD_DAMAGE = 1.6, BROADHEAD_SPEED = 0.8;
	/** Bola: ticks a hit foe stays down, and the slowing for one that can't be tripped (ticks). */
	public static final int BOLA_TRIP = 60, BOLA_TANGLE = 80;
	/** Net: how far (blocks) it spreads where it lands, and how long it roots (ticks). */
	public static final double NET_RADIUS = 2.6;
	public static final int NET_ROOT = 80;
	/** Harpoon: how hard it drags its catch to the archer (blocks/tick), and the lift. */
	public static final double HARPOON_PULL = 1.1, HARPOON_LIFT = 0.25;
	/** Grapple: reach (blocks) it will pull from, the pull per block of distance (capped), and the lift. */
	public static final double GRAPPLE_REACH = 40.0, GRAPPLE_PER_BLOCK = 0.1, GRAPPLE_MIN = 0.7, GRAPPLE_MAX = 2.2, GRAPPLE_LIFT = 0.25;
	/** Whistle: how far (blocks) it carries. */
	public static final double WHISTLE_RANGE = 28.0;
	/** Flash: how far (blocks) it dazzles, and for how long (ticks). */
	public static final double FLASH_RADIUS = 9.0;
	public static final int FLASH_BLIND = 70;
	/** Drill: seconds a shield it goes through stays down. */
	public static final float DRILL_DISABLE = 3.0F;
	/** Caltrop head: how many caltrops it scatters. */
	public static final int CALTROPS_FROM_ARROW = 7;
	/** A plain arrow's base damage. */
	public static final double ARROW_BASE = 2.0;
	/** Boomerang: speed it flies home (blocks/tick), how near the archer it is caught (blocks), and how long it tries (ticks). */
	public static final double BOOMERANG_SPEED = 1.5, BOOMERANG_CATCH = 1.8;
	public static final int BOOMERANG_TICKS = 120;

	private static final String SLOWED = "jaymospowers.head_slowed", HOMING = "jaymospowers.head_homing";
	private static final List<Homing> HOMEWARD = new ArrayList<>();

	private record Homing(AbstractArrow arrow, long since) {
	}

	private HeadFx() {
	}

	public static void init() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof AbstractArrow arrow && arrow.entityTags().contains(ArrowHead.BROADHEAD.tag()) && !arrow.entityTags().contains(SLOWED)) {
				arrow.addTag(SLOWED);
				arrow.setDeltaMovement(arrow.getDeltaMovement().scale(BROADHEAD_SPEED));
			}
		});
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(HeadFx::allowDamage);
		ServerTickEvents.END_SERVER_TICK.register(server -> flyHome());
	}

	/** Marks a fresh arrow with its head and applies what the head changes about the arrow. */
	public static AbstractArrow arm(AbstractArrow arrow, @Nullable ArrowHead head) {
		if (head == null) {
			return arrow;
		}
		arrow.addTag(head.tag());
		switch (head) {
			case BROADHEAD -> arrow.setBaseDamage(ARROW_BASE * BROADHEAD_DAMAGE);
			case FIRE -> arrow.igniteForSeconds(100.0F);
			case DRILL -> ((ArrowPierceInvoker) arrow).jaymospowers$setPierceLevel((byte) 1);
			default -> {
			}
		}
		return arrow;
	}

	public static @Nullable ArrowHead headOf(Entity arrow) {
		for (ArrowHead head : ArrowHead.values()) {
			if (arrow.entityTags().contains(head.tag())) {
				return head;
			}
		}
		return null;
	}

	// ---- landing ----

	/** The arrow struck {@code hit}: its head does its work (called after the arrow's own damage). */
	public static void hitEntity(AbstractArrow arrow, EntityHitResult hit) {
		ArrowHead head = headOf(arrow);
		if (head == null || !(arrow.level() instanceof ServerLevel level)) {
			return;
		}
		Entity owner = arrow.getOwner();
		Entity struck = hit.getEntity();
		Vec3 at = hit.getLocation();
		LivingEntity living = struck instanceof LivingEntity l ? l : null;
		switch (head) {
			case BOLA -> {
				if (living != null && living.isAlive() && !Tripped.bind(living, owner, BOLA_TRIP)) {
					living.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, BOLA_TANGLE, 2));
				}
			}
			case NET -> net(level, at, owner);
			case CALTROP -> GroundFx.caltrops(level, at, owner, CALTROPS_FROM_ARROW);
			case HARPOON -> {
				if (living != null && owner != null && living.isAlive()) {
					Vec3 pull = owner.position().subtract(living.position());
					Vec3 flat = new Vec3(pull.x, 0.0, pull.z).normalize();
					living.setDeltaMovement(flat.x * HARPOON_PULL, HARPOON_LIFT, flat.z * HARPOON_PULL);
					living.syncVelocity = true;
					MagicFx.beam(level, owner.getEyePosition(), living.getBoundingBox().getCenter(), 0.05F, 0xC8CCD4, 10);
					level.playSound(null, living.getX(), living.getY(), living.getZ(), SoundEvents.CHAIN_HIT, SoundSource.PLAYERS, 1.0F, 0.9F);
				}
			}
			case GRAPPLE -> grapple(level, owner, at);
			case SMOKE -> GroundFx.cloud(level, at, owner, GroundFx.Kind.SMOKE, GroundFx.POT_RADIUS, GroundFx.ARROW_SMOKE_TICKS);
			case FLARE -> GroundFx.flare(level, at, head.color);
			case WHISTLE -> GroundFx.lure(level, at, WHISTLE_RANGE);
			case BARBED -> {
				if (living != null) {
					Bleeding.open(living, BodyPart.BODY, false);
				}
			}
			case FLASH -> flash(level, at, owner);
			default -> {
			}
		}
		if (head.spent) {
			arrow.discard();
		}
	}

	/** The arrow struck a block. True when the head took the arrow over (vanilla's sticking is skipped). */
	public static boolean hitBlock(AbstractArrow arrow, BlockHitResult hit) {
		ArrowHead head = headOf(arrow);
		if (head == null || !(arrow.level() instanceof ServerLevel level)) {
			return false;
		}
		Entity owner = arrow.getOwner();
		Vec3 at = hit.getLocation().add(Vec3.atLowerCornerOf(hit.getDirection().getUnitVec3i()).scale(0.1));
		switch (head) {
			case NET -> net(level, at, owner);
			case CALTROP -> GroundFx.caltrops(level, at, owner, CALTROPS_FROM_ARROW);
			case GRAPPLE -> grapple(level, owner, hit.getLocation());
			case SMOKE -> GroundFx.cloud(level, at, owner, GroundFx.Kind.SMOKE, GroundFx.POT_RADIUS, GroundFx.ARROW_SMOKE_TICKS);
			case FLARE -> GroundFx.flare(level, at, head.color);
			case WHISTLE -> GroundFx.lure(level, at, WHISTLE_RANGE);
			case FLASH -> flash(level, at, owner);
			case BOOMERANG -> {
				if (owner instanceof Player player && player.isAlive() && player.level() == level && !arrow.entityTags().contains(HOMING)) {
					arrow.addTag(HOMING);
					arrow.setNoPhysics(true);
					HOMEWARD.add(new Homing(arrow, level.getGameTime()));
					level.playSound(null, arrow.getX(), arrow.getY(), arrow.getZ(), SoundEvents.TRIDENT_RETURN, SoundSource.PLAYERS, 0.6F, 1.5F);
					return true;
				}
				return arrow.entityTags().contains(HOMING);
			}
			default -> {
			}
		}
		if (head.spent) {
			arrow.discard();
			return true;
		}
		return false;
	}

	private static void net(ServerLevel level, Vec3 at, @Nullable Entity owner) {
		MagicFx.ring(level, at, (float) NET_RADIUS, ArrowHead.NET.color);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.COBWEB_PLACE, SoundSource.PLAYERS, 1.0F, 0.8F);
		AABB box = new AABB(at, at).inflate(NET_RADIUS, 1.5, NET_RADIUS);
		for (LivingEntity caught : level.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && !e.isSpectator() && e != owner)) {
			if (caught.position().distanceTo(at) <= NET_RADIUS + 0.6) {
				caught.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, NET_ROOT, 6));
				if (caught instanceof Mob mob) {
					mob.getNavigation().stop();
				}
			}
		}
	}

	private static void grapple(ServerLevel level, @Nullable Entity owner, Vec3 to) {
		if (owner == null || owner.position().distanceTo(to) > GRAPPLE_REACH) {
			return;
		}
		Vec3 pull = to.subtract(owner.getEyePosition());
		double speed = Math.clamp(pull.length() * GRAPPLE_PER_BLOCK, GRAPPLE_MIN, GRAPPLE_MAX);
		Vec3 dir = pull.normalize();
		owner.setDeltaMovement(dir.x * speed, dir.y * speed + GRAPPLE_LIFT, dir.z * speed);
		owner.syncVelocity = true;
		MagicFx.beam(level, owner.getEyePosition().add(0.0, -0.3, 0.0), to, 0.05F, 0xB8C4D0, 14);
		level.playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.CHAIN_HIT, SoundSource.PLAYERS, 1.0F, 1.2F);
	}

	/** A flash at {@code at}: whoever is near and not shielded from it is blinded, and mobs lose track of who they were after. */
	public static void flash(ServerLevel level, Vec3 at, @Nullable Entity owner) {
		MagicFx.blast(level, at, 3.0F, 0xFFFFF0);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 1.2F, 1.6F);
		AABB box = new AABB(at, at).inflate(FLASH_RADIUS);
		for (LivingEntity dazzled : level.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && !e.isSpectator() && e != owner)) {
			Vec3 eyes = dazzled.getEyePosition();
			double distance = eyes.distanceTo(at);
			if (distance > FLASH_RADIUS) {
				continue;
			}
			Vec3 toFlash = at.subtract(eyes).normalize();
			// those turned away from it only catch the edge
			double facing = dazzled.getLookAngle().dot(toFlash);
			int ticks = (int) (FLASH_BLIND * (facing > 0.0 ? 1.0 : 0.4) * (1.0 - distance / (FLASH_RADIUS * 1.4)));
			if (ticks > 8) {
				dazzled.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, ticks, 0));
				if (dazzled instanceof Mob mob) {
					mob.setTarget(null);
				}
			}
		}
	}

	// ---- drill through a shield ----

	private static boolean allowDamage(LivingEntity victim, DamageSource source, float amount) {
		if (source.getDirectEntity() instanceof AbstractArrow arrow && arrow.entityTags().contains(ArrowHead.DRILL.tag()) && victim.isBlocking()
			&& victim.level() instanceof ServerLevel level) {
			ItemStack shield = victim.getUseItem();
			BlocksAttacks blocks = shield.get(DataComponents.BLOCKS_ATTACKS);
			if (blocks != null) {
				blocks.disable(level, victim, DRILL_DISABLE, shield);
			}
			victim.stopUsingItem();
		}
		return true;
	}

	// ---- the boomerang's way home ----

	private static void flyHome() {
		for (int i = HOMEWARD.size() - 1; i >= 0; i--) {
			Homing homing = HOMEWARD.get(i);
			AbstractArrow arrow = homing.arrow();
			if (arrow.isRemoved() || !(arrow.level() instanceof ServerLevel level)) {
				HOMEWARD.remove(i);
				continue;
			}
			if (!(arrow.getOwner() instanceof Player owner) || !owner.isAlive() || owner.level() != level
				|| level.getGameTime() - homing.since() > BOOMERANG_TICKS) {
				// nobody to come back to: it drops where it is
				arrow.setNoPhysics(false);
				HOMEWARD.remove(i);
				continue;
			}
			Vec3 to = owner.getEyePosition().add(0.0, -0.4, 0.0).subtract(arrow.position());
			if (to.length() < BOOMERANG_CATCH) {
				ItemStack caught = arrow.getPickupItemStackOrigin().copy();
				if (arrow.pickup == AbstractArrow.Pickup.ALLOWED && !owner.getInventory().add(caught)) {
					owner.drop(caught, false, net.minecraft.util.Prediction.SERVER_ONLY);
				}
				level.playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.4F, 1.6F);
				arrow.discard();
				HOMEWARD.remove(i);
				continue;
			}
			arrow.setDeltaMovement(to.normalize().scale(BOOMERANG_SPEED));
		}
	}
}
