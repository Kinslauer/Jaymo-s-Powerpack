package com.jaymoskingdoms;

import com.jaymoskingdoms.mixin.MobAccessor;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.entity.monster.zombie.Zombie;

/**
 * (0.7.103) Small changes to the game's own monsters, after Better With Mods' (MIT; its ideas, our own code):
 * <ul>
 * <li>Baby zombies (any kind) are slower and hit softer: {@link #BABY_SPEED} and {@link #BABY_DAMAGE}, shares of their
 * own, which add up with a world tier's ({@link WorldTiers}).</li>
 * <li>Endermen stand by each other: one hurt by someone calls the endermen round it onto them, once it has taken a world
 * tier (Borderlands or higher; a Hearth enderman fights alone, as ever).</li>
 * <li>An enderman's teleport rumbles with far-off thunder ({@code mixin.EndermanThunderMixin}).</li>
 * </ul>
 */
public final class MobTweaks {
	/** A baby zombie's speed and attack damage (shares of its own, added as WorldTiers' are, so they sum with a tier's). */
	static final double BABY_SPEED = -0.25, BABY_DAMAGE = -0.25;
	/** The enderman's call for help: a target goal beside the game's own look-for-player (1), ahead of its hurt-by (2). */
	static final int ENDER_ALERT_PRIORITY = 1;
	/** The least world tier at which endermen answer each other's call. */
	static final int ENDER_ALERT_TIER = 1;
	/** The thunder on an enderman's teleport: how loud, and its pitch (low and far). */
	static final float THUNDER_VOLUME = 0.6F, THUNDER_PITCH = 1.6F;

	private static final Identifier BABY_SPEED_ID = JaymosKingdoms.id("baby_zombie_speed"), BABY_DAMAGE_ID = JaymosKingdoms.id("baby_zombie_damage");

	private MobTweaks() {
	}

	static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> loaded(entity));
	}

	private static void loaded(Entity entity) {
		if (entity instanceof Zombie zombie && zombie.isBaby()) {
			weaken(zombie);
		} else if (entity instanceof Enderman enderman) {
			((MobAccessor)enderman).jaymoskingdoms$targets().addGoal(ENDER_ALERT_PRIORITY, new CallForHelp(enderman));
		}
	}

	/** A baby zombie's lesser speed and bite (fixed ids: replaced, never stacked, however often it loads). */
	private static void weaken(Zombie zombie) {
		modify(zombie, Attributes.MOVEMENT_SPEED, BABY_SPEED_ID, BABY_SPEED);
		modify(zombie, Attributes.ATTACK_DAMAGE, BABY_DAMAGE_ID, BABY_DAMAGE);
	}

	private static void modify(Zombie zombie, Holder<Attribute> attribute, Identifier id, double amount) {
		AttributeInstance instance = zombie.getAttribute(attribute);
		if (instance != null) {
			instance.addOrReplacePermanentModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		}
	}

	/** Far-off thunder where an enderman lands (from its teleport, server side). */
	public static void teleported(Enderman enderman) {
		if (enderman.level() instanceof ServerLevel level && !enderman.isSilent()) {
			level.playSound(null, enderman.getX(), enderman.getY(), enderman.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE,
				THUNDER_VOLUME, THUNDER_PITCH + level.getRandom().nextFloat() * 0.2F);
		}
	}

	/**
	 * The game's hurt-by-target, calling the endermen near by onto the attacker too; only once this enderman has a world
	 * tier, read when it's hurt (tiers come after loading, from players near by).
	 */
	private static final class CallForHelp extends HurtByTargetGoal {
		private final Enderman enderman;

		CallForHelp(Enderman enderman) {
			super(enderman);
			this.enderman = enderman;
			this.setAlertOthers();
		}

		@Override
		protected void alertOthers() {
			if (this.enderman.getAttachedOrElse(WorldTiers.MOB_TIER, 0) >= ENDER_ALERT_TIER) {
				super.alertOthers();
			}
		}
	}
}
