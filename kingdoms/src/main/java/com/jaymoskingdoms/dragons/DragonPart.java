package com.jaymoskingdoms.dragons;

import java.util.List;
import java.util.function.Predicate;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * One part of a dragon you can aim at beyond its body's box (after Dragonforge's part hitboxes, RochStudio): its head,
 * neck, the two halves of its tail and, in flight, its wings. A blow on a part hurts the dragon by the part's share: the
 * head a little more, the wings and tail less ({@link Kind}). Made as the Nehemoth's parts are ({@code NehemothPart}):
 * never added to the world, saved or sent, only handed to whoever asks the level for entities near it
 * ({@code mixin.fts}'s part mixins), so a crosshair, an arrow or a blow can land on it. Its riders never see their own
 * dragon's parts. Using a part (a saddle, mounting up) is using the dragon.
 */
public class DragonPart extends Entity {
	/** The parts and what a blow on each counts for. */
	public enum Kind {
		HEAD(1.15F), NECK(1.0F), TAIL_BASE(0.85F), TAIL_TIP(0.75F), LEFT_WING(0.9F), RIGHT_WING(0.9F);

		public final float damage;

		Kind(float damage) {
			this.damage = damage;
		}
	}

	/** How far from a dragon's middle any part can be (blocks, the greatest dragon's wing tip), for finding dragons near a box. */
	static final double REACH = 16.0;

	public final WindDragon parent;
	public final Kind kind;
	private EntityDimensions size = EntityDimensions.fixed(0.5F, 0.5F);
	/** Whether it is out now (wings only while flying); a part that isn't is handed to no one. */
	boolean out;

	DragonPart(WindDragon parent, Kind kind) {
		super(parent.getType(), parent.level());
		this.parent = parent;
		this.kind = kind;
		this.refreshDimensions();
	}

	/** Puts its middle at {@code at}, {@code width} wide and {@code height} high. */
	void place(Vec3 at, float width, float height) {
		if (Math.abs(this.size.width() - width) > 0.05F || Math.abs(this.size.height() - height) > 0.05F) {
			this.size = EntityDimensions.fixed(width, height);
			this.refreshDimensions();
		}
		this.setPos(at.x, at.y - height / 2.0, at.z);
	}

	/** Adds the parts of the dragons near {@code box} that overlap it to {@code into} (what the level hands anyone asking). */
	public static void addNear(Level level, @Nullable Entity except, AABB box, Predicate<? super Entity> selector, List<Entity> into) {
		Entity asker = except instanceof Projectile shot && shot.getOwner() != null ? shot.getOwner() : except;
		for (WindDragon dragon : level.getEntitiesOfClass(WindDragon.class, box.inflate(REACH), Entity::isAlive)) {
			if (dragon == except || dragon.getHealth() <= 0.0F || asker != null && asker.getRootVehicle() == dragon) {
				continue;
			}
			for (DragonPart part : dragon.parts()) {
				if (part.out && part != except && selector.test(part) && box.intersects(part.getBoundingBox())) {
					into.add(part);
				}
			}
		}
	}

	/** The part whose id is {@code id}: parts take the ids just after their dragon's. */
	public static @Nullable DragonPart byId(Level level, int id) {
		int count = Kind.values().length;
		for (int back = 1; back <= count; back++) {
			if (level.getEntity(id - back) instanceof WindDragon dragon && dragon.parts()[back - 1].getId() == id) {
				return dragon.parts()[back - 1];
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
		return this.out;
	}

	@Override
	public @Nullable ItemStack getPickResult() {
		return this.parent.getPickResult();
	}

	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
		return this.parent.interact(player, hand, location);
	}

	@Override
	public final boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		return !this.isInvulnerableToBase(source) && this.parent.hurtFromPart(this, level, source, damage);
	}

	/** A blast hurts the dragon once, through its body. */
	@Override
	public boolean ignoreExplosion(Explosion explosion) {
		return true;
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
