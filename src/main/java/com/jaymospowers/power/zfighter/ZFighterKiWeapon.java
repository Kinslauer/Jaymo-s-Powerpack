package com.jaymospowers.power.zfighter;

import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Powers;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/**
 * (0.17.45, Andy's NOEA picks) Ki weapons: one technique on G that shapes his ki into a blade, a scythe or a clawed lance
 * (picked on the training screen). While it is out ki drains by the second, his bare-handed blows (the punch, the hold
 * combo) hit {@link #BONUS} harder, scaled by Ki Power, and reach {@link #REACH} further. His hand stays empty, so they
 * are fists for "fists first" ({@link ZFighterBattle}). Put an item in the hand and it rests, costing nothing. The
 * client draws it in his ki colour on the right hand.
 */
public final class ZFighterKiWeapon {
	public static final int BLADE = 0, SCYTHE = 1, LANCE = 2, SHAPES = 3;
	public static final String[] SHAPE_KEYS = {"blade", "scythe", "lance"};
	/** Ki to shape it, and ki a second while it is out. */
	static final float DRAW_KI = 8.0F;
	static final float DRAIN_PER_SECOND = 3.0F;
	/** Extra damage on a bare-handed blow (before Ki Power), by shape. */
	static final float[] BONUS = {1.5F, 2.0F, 1.75F};
	/** Extra reach in blocks, by shape. */
	static final double[] REACH = {1.0, 1.5, 2.0};
	static final int TOGGLE_COOLDOWN = 20;
	private static final Identifier REACH_ID = JaymosPowers.id("zfighter_ki_weapon_reach");
	/**
	 * The ki weapon as an energy blade (Andy's pick from Star Wars: Galaxy in a Cube, ideas only; asked who should carry
	 * one, Andy: "z fighters"). Its own sounds ({@code tools/gen_ki_blade_sounds.py}): it flares out and draws in, hums
	 * every {@link #HUM_EVERY} ticks while it is out, and sings on each blow that lands. The body stands armed with it
	 * (the client's {@code ArmedPose}). A parry made with it out strikes an arrow back at whoever loosed it, at
	 * {@link #DEFLECT_SPEED} of its speed (never slower than {@link #DEFLECT_MIN} blocks a tick), scattered by
	 * {@link #DEFLECT_SCATTER}.
	 */
	static final int HUM_EVERY = 36;
	static final float HUM_VOLUME = 0.22F;
	static final double DEFLECT_SPEED = 0.6, DEFLECT_MIN = 0.8;
	static final float DEFLECT_SCATTER = 3.0F;
	private static final net.minecraft.sounds.SoundEvent IGNITE = sound("ignite"), RETRACT = sound("retract"), HUM = sound("hum"),
		SWING = sound("swing"), DEFLECT = sound("deflect");

	private static net.minecraft.sounds.SoundEvent sound(String name) {
		return net.minecraft.sounds.SoundEvent.createVariableRangeEvent(JaymosPowers.id("ki_blade." + name));
	}

	/** The shape he has chosen (kept for good; his own client's training screen shows it). */
	public static final AttachmentType<Integer> SHAPE = AttachmentRegistry.create(JaymosPowers.id("zfighter_ki_weapon_shape"),
		builder -> builder.persistent(Codec.INT).copyOnDeath().syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.targetOnly()));
	/** While it is out: its shape plus one, for everyone's client. Not saved. */
	public static final AttachmentType<Integer> OUT = AttachmentRegistry.create(JaymosPowers.id("zfighter_ki_weapon_out"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));

	private ZFighterKiWeapon() {
	}

	public static int shape(net.minecraft.world.entity.player.Player player) {
		return Math.floorMod(player.getAttachedOrElse(SHAPE, BLADE), SHAPES);
	}

	/** The shape out now, or -1. Both sides. */
	public static int out(net.minecraft.world.entity.player.Player player) {
		Integer out = player.getAttached(OUT);
		return out == null ? -1 : out - 1;
	}

	/** G: draw it, or put it away. */
	static void toggle(ServerPlayer player, ZFighterPower.Live live) {
		if (out(player) >= 0) {
			sheathe(player, true);
			return;
		}
		if (!ZFighterPower.spend(player, live, DRAW_KI)) {
			return;
		}
		int shape = shape(player);
		player.setAttached(OUT, shape + 1);
		reach(player, shape);
		ServerLevel level = player.level();
		Vec3 hand = player.position().add(0.0, player.getBbHeight() * 0.5, 0.0);
		level.sendParticles(ZFighterPower.dust(live), true, false, hand.x, hand.y, hand.z, 12, 0.3, 0.4, 0.3, 0.05);
		level.sendParticles(MagicFx.LIGHT, true, false, hand.x, hand.y, hand.z, 6, 0.2, 0.3, 0.2, 0.05);
		level.playSound(null, hand.x, hand.y, hand.z, IGNITE, SoundSource.PLAYERS, 0.8F, 0.95F + player.getRandom().nextFloat() * 0.1F);
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_ki_weapon_out",
			Component.translatable("technique.jaymospowers.zfighter.ki_weapon." + SHAPE_KEYS[shape])).withStyle(ChatFormatting.AQUA));
	}

	static void sheathe(ServerPlayer player, boolean sound) {
		boolean was = out(player) >= 0;
		player.removeAttached(OUT);
		AttributeInstance range = player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE);
		if (range != null) {
			range.removeModifier(REACH_ID);
		}
		if (was && sound) {
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), RETRACT, SoundSource.PLAYERS, 0.7F, 0.95F + player.getRandom().nextFloat() * 0.1F);
		}
	}

	/** The training screen's button: the next shape (and the weapon on G); a weapon already out takes the new shape. */
	static void cycle(ServerPlayer player) {
		if (!Powers.has(player, ZFighterPower.INSTANCE) || !ZFighterTraining.of(player).knows(ZFighterTraining.KI_WEAPON)) {
			return;
		}
		int shape = (shape(player) + 1) % SHAPES;
		player.setAttached(SHAPE, shape);
		ZFighterTraining.equipTechnique(player, ZFighterTraining.KI_WEAPON);
		if (out(player) >= 0) {
			player.setAttached(OUT, shape + 1);
			reach(player, shape);
		}
	}

	private static void reach(ServerPlayer player, int shape) {
		AttributeInstance range = player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE);
		if (range != null) {
			range.addOrUpdateTransientModifier(new AttributeModifier(REACH_ID, REACH[shape], AttributeModifier.Operation.ADD_VALUE));
		}
	}

	/** Every tick, per Z Fighter: the ki drains; an empty hand is needed for its reach; no ki, no weapon. */
	static void tick(ServerPlayer player, ZFighterPower.Live live) {
		int shape = out(player);
		if (shape < 0) {
			return;
		}
		if (!player.isAlive() || ZFighterLineage.ape(player) || Powers.silenced(player)) {
			sheathe(player, false);
			return;
		}
		boolean bare = player.getMainHandItem().isEmpty();
		AttributeInstance range = player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE);
		if (range != null && (range.getModifier(REACH_ID) != null) != bare) {
			if (bare) {
				reach(player, shape);
			} else {
				range.removeModifier(REACH_ID);
			}
		}
		if (bare && player.tickCount % HUM_EVERY == 0) {
			player.level().playSound(null, player.getX(), player.getY() + 1.0, player.getZ(), HUM, SoundSource.PLAYERS, HUM_VOLUME, 1.0F);
		}
		if (bare && !player.isCreative() && !live.raging()) {
			live.ki -= DRAIN_PER_SECOND / 20.0F * ZFighterAndroid.costFactor(player);
			if (live.ki <= 0.0F) {
				live.ki = 0.0F;
				sheathe(player, true);
			}
		}
	}

	/** The damage of a blow of his, plus the weapon's: only a bare-handed punch or combo blow, only while it is out. */
	static float boost(ServerPlayer player, DamageSource source, float damage) {
		int shape = out(player);
		if (shape < 0 || !player.getMainHandItem().isEmpty() || source.getDirectEntity() != player || !source.is(DamageTypes.PLAYER_ATTACK)) {
			return damage;
		}
		player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), SWING, SoundSource.PLAYERS, 0.7F, 0.9F + player.getRandom().nextFloat() * 0.2F);
		return damage + BONUS[shape] * ZFighterPower.kiPower(player, ZFighterPower.live(player));
	}

	/** Whether his ki weapon is out and in use (an empty hand). Both sides. */
	public static boolean wielded(net.minecraft.world.entity.player.Player player) {
		return out(player) >= 0 && player.getMainHandItem().isEmpty();
	}

	/**
	 * His guard parried {@code shot} with the ki weapon out: an arrow like it flies back at whoever loosed it (the one
	 * that struck is spent on the guard). It can't be picked up.
	 */
	static void deflect(ServerPlayer player, net.minecraft.world.entity.projectile.arrow.AbstractArrow shot) {
		if (!wielded(player)) {
			return;
		}
		ServerLevel level = player.level();
		net.minecraft.world.entity.Entity shooter = shot.getOwner();
		Vec3 from = player.getEyePosition().add(player.getLookAngle().scale(0.8));
		Vec3 dir = shooter != null && shooter != player && shooter.isAlive()
			? shooter.position().add(0.0, shooter.getBbHeight() * 0.6, 0.0).subtract(from).normalize() : player.getLookAngle();
		net.minecraft.world.entity.projectile.arrow.Arrow back = new net.minecraft.world.entity.projectile.arrow.Arrow(level, player,
			new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ARROW), null);
		back.setPos(from);
		back.pickup = net.minecraft.world.entity.projectile.arrow.AbstractArrow.Pickup.DISALLOWED;
		back.shoot(dir.x, dir.y + 0.05, dir.z, (float)Math.max(DEFLECT_MIN, shot.getDeltaMovement().length() * DEFLECT_SPEED), DEFLECT_SCATTER);
		level.addFreshEntity(back);
		player.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		level.playSound(null, from.x, from.y, from.z, DEFLECT, SoundSource.PLAYERS, 0.9F, 0.95F + player.getRandom().nextFloat() * 0.1F);
		level.sendParticles(MagicFx.LIGHT, true, false, from.x, from.y, from.z, 5, 0.15, 0.15, 0.15, 0.05);
	}
}
