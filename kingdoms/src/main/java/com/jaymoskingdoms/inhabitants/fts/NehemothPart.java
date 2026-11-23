package com.jaymoskingdoms.inhabitants.fts;

import java.util.List;
import java.util.function.Predicate;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * One targetable part of a {@link Nehemoth} (the original's NehemothPart and NehemothPartEntity; From the Shadows Reborn,
 * Sonmok14 and L_Ender, AGPL v3; sizes and damage multipliers are theirs). Made the way vanilla makes the ender dragon's
 * parts: a plain entity that is never added to the world, saved or sent, only handed to whoever asks the level for
 * entities near it (the mixins in {@code mixin.fts}), so a crosshair, an arrow or a blow can land on it and then hurts the
 * Nehemoth through it, with the part's own multiplier (the head twice, the neck one and a half, the rest half).
 *
 * <p>Where each part sits is the geo model's hitbox locator, turned with the body and scaled as it is drawn; unlike the
 * original, it does not follow the arms and legs through their animations.
 */
public class NehemothPart extends Entity {
	/** The parts: their offset from its feet (blocks; right, up, forward at rest), width, height and damage multiplier. */
	public enum Kind {
		HEAD(0.007F, 3.018F, 0.576F, 0.55F, 0.5F, 2.0F),
		NECK(0.000F, 2.947F, 0.098F, 0.5F, 0.4F, 1.5F),
		BODY(0.000F, 2.489F, -0.139F, 0.7F, 0.6F, 0.5F),
		BOTTOM(0.000F, 1.950F, 0.000F, 0.65F, 0.6F, 0.5F),
		RIGHT_ARM_UPPER(0.758F, 2.659F, -0.046F, 0.4F, 0.5F, 0.5F),
		RIGHT_ARM_LOWER(0.758F, 2.209F, -0.046F, 0.4F, 0.5F, 0.5F),
		RIGHT_FOREARM_UPPER(0.693F, 1.745F, -0.010F, 0.35F, 0.4F, 0.5F),
		RIGHT_FOREARM_LOWER(0.693F, 1.445F, -0.010F, 0.35F, 0.4F, 0.5F),
		LEFT_ARM_UPPER(-0.758F, 2.659F, -0.046F, 0.4F, 0.5F, 0.5F),
		LEFT_ARM_LOWER(-0.758F, 2.209F, -0.046F, 0.4F, 0.5F, 0.5F),
		LEFT_FOREARM_UPPER(-0.693F, 1.745F, -0.010F, 0.35F, 0.4F, 0.5F),
		LEFT_FOREARM_LOWER(-0.693F, 1.445F, -0.010F, 0.35F, 0.4F, 0.5F),
		RIGHT_THIGH_UPPER(-0.300F, 1.575F, -0.075F, 0.45F, 0.55F, 0.5F),
		RIGHT_THIGH_LOWER(-0.300F, 1.200F, -0.075F, 0.45F, 0.55F, 0.5F),
		RIGHT_SHIN_UPPER(-0.450F, 0.900F, 0.075F, 0.5F, 0.35F, 0.5F),
		RIGHT_SHIN_LOWER(-0.450F, 0.825F, 0.450F, 0.5F, 0.35F, 0.5F),
		LEFT_THIGH_UPPER(0.300F, 1.575F, -0.075F, 0.45F, 0.55F, 0.5F),
		LEFT_THIGH_LOWER(0.300F, 1.200F, -0.075F, 0.45F, 0.55F, 0.5F),
		LEFT_SHIN_UPPER(0.450F, 0.900F, 0.075F, 0.5F, 0.35F, 0.5F),
		LEFT_SHIN_LOWER(0.450F, 0.825F, 0.450F, 0.5F, 0.35F, 0.5F);

		final float right, up, forward, width, height, damageMultiplier;

		Kind(float right, float up, float forward, float width, float height, float damageMultiplier) {
			this.right = right;
			this.up = up;
			this.forward = forward;
			this.width = width;
			this.height = height;
			this.damageMultiplier = damageMultiplier;
		}
	}

	/** How far from a Nehemoth's feet any part can be (blocks), for finding the Nehemoths near a box. */
	static final double REACH = 5.0;

	public final Nehemoth parent;
	public final Kind kind;
	private final EntityDimensions size;

	NehemothPart(Nehemoth parent, Kind kind) {
		super(parent.getType(), parent.level());
		this.parent = parent;
		this.kind = kind;
		this.size = EntityDimensions.fixed(kind.width, kind.height);
		this.refreshDimensions();
	}

	/** Puts it where its locator is on a body standing at the parent's feet and turned {@code yawDegrees}. */
	void place(double x, double y, double z, float yawDegrees) {
		double yaw = Math.toRadians(yawDegrees);
		double sin = Math.sin(yaw), cos = Math.cos(yaw);
		double wx = this.kind.right * cos - this.kind.forward * sin;
		double wz = this.kind.right * sin + this.kind.forward * cos;
		this.setPos(x + wx, y + this.kind.up - this.kind.height / 2.0, z + wz);
	}

	/** Adds the parts of the Nehemoths near {@code box} that overlap it to {@code into} (what the level hands anyone asking). */
	public static void addNear(Level level, @Nullable Entity except, AABB box, Predicate<? super Entity> selector, List<Entity> into) {
		for (Nehemoth n : level.getEntitiesOfClass(Nehemoth.class, box.inflate(REACH), Entity::isAlive)) {
			if (n == except || n.getHealth() <= 0.0F) {
				continue;
			}
			for (NehemothPart part : n.parts()) {
				if (part != except && selector.test(part) && box.intersects(part.getBoundingBox())) {
					into.add(part);
				}
			}
		}
	}

	/** The part whose id is {@code id}: parts take the ids just after their Nehemoth's. */
	public static @Nullable NehemothPart byId(Level level, int id) {
		for (int back = 1; back <= Kind.values().length; back++) {
			if (level.getEntity(id - back) instanceof Nehemoth n && n.parts()[back - 1].getId() == id) {
				return n.parts()[back - 1];
			}
		}
		return null;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
	}

	@Override
	public boolean isPickable() {
		return true;
	}

	@Override
	public @Nullable ItemStack getPickResult() {
		return this.parent.getPickResult();
	}

	@Override
	public final boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		return !this.isInvulnerableToBase(source) && this.parent.hurtFromPart(this, level, source, damage);
	}

	@Override
	public boolean is(Entity other) {
		return this == other || this.parent == other;
	}

	@Override
	public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity serverEntity) {
		throw new UnsupportedOperationException();
	}

	@Override
	public EntityDimensions getDimensions(Pose pose) {
		return this.size;
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}
}
