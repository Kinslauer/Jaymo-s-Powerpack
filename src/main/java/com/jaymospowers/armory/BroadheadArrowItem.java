package com.jaymospowers.armory;

import com.jaymospowers.core.wounds.Bleeding;
import com.jaymospowers.core.wounds.BodyPart;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * (Smithing) A steel broadhead: a wide, barbed head (steel nuggets, a stick and a feather) that cuts deeper than a plain
 * arrow and always opens a bleeding cut ({@link Bleeding}). It's made for the recurve bow's heavy draw: loosed from
 * anything else (a plain bow, a crossbow, a dispenser) it hits for only {@link #BROADHEAD_OFF_BOW_SCALE} of that.
 */
public class BroadheadArrowItem extends ArrowItem {
	/** Damage over a plain arrow's base (2), from the recurve bow. */
	static final double BROADHEAD_DAMAGE = 2.0;
	/** The share of that base it keeps from any other bow. */
	static final double BROADHEAD_OFF_BOW_SCALE = 0.6;
	/** A plain arrow's base damage. */
	static final double ARROW_BASE = 2.0;
	/** Marks a loosed broadhead (an entity tag, so it lasts through saves). */
	static final String BROADHEAD_TAG = "jaymospowers_broadhead";

	public BroadheadArrowItem(Properties properties) {
		super(properties);
	}

	/** A broadhead's hit opens a cut on the body. */
	static void init() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			Entity direct = source.getDirectEntity();
			if (damageTaken > 0.0F && !blocked && direct != null && direct.entityTags().contains(BROADHEAD_TAG)) {
				Bleeding.open(entity, BodyPart.BODY, false);
			}
		});
	}

	@Override
	public AbstractArrow createArrow(Level level, ItemStack ammo, LivingEntity shooter, @Nullable ItemStack weapon) {
		AbstractArrow arrow = super.createArrow(level, ammo, shooter, weapon);
		return broadhead(arrow, weapon != null && weapon.getItem() instanceof RecurveBowItem);
	}

	@Override
	public Projectile asProjectile(Level level, Position position, ItemStack stack, Direction direction) {
		Projectile projectile = super.asProjectile(level, position, stack, direction);
		return projectile instanceof AbstractArrow arrow ? broadhead(arrow, false) : projectile;
	}

	private static AbstractArrow broadhead(AbstractArrow arrow, boolean recurve) {
		double full = ARROW_BASE + BROADHEAD_DAMAGE;
		arrow.setBaseDamage(recurve ? full : full * BROADHEAD_OFF_BOW_SCALE);
		arrow.addTag(BROADHEAD_TAG);
		return arrow;
	}
}
