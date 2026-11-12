package com.jaymoskingdoms.forms;

import com.jaymoskingdoms.Downed;
import com.jaymoskingdoms.Folk;
import com.jaymoskingdoms.Fx;
import com.jaymoskingdoms.Parties;
import com.jaymoskingdoms.Speech;
import com.jaymoskingdoms.Temperaments;
import com.jaymoskingdoms.Temperaments.Temperament;
import com.jaymoskingdoms.Will;
import com.jaymoskingdoms.forms.Forms.Family;
import com.jaymoskingdoms.forms.Forms.Form;
import com.jaymoskingdoms.forms.Forms.Kind;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * The Thief's two knife forms. Both leave a weapon on the ground, never in the thief's hands: a thief takes nothing off a
 * player's body here, only makes them drop what they hold.
 * <ul>
 * <li><b>Disarm Rush</b> ({@link Skills.Perk#DISARM_RUSH}): rush through up to {@link #RUSH_FOES} armed foes ahead of you,
 * each knocked slow for a moment with the weapon out of its hand. A mob fights bare-handed and goes back for its weapon
 * after {@link #REARM_TICKS} ticks if it is still lying there; a player (only where fighting players is allowed, and never a
 * party member) drops the held item with a pickup delay of {@link #PLAYER_DELAY} ticks.</li>
 * <li><b>Dry Remark</b> ({@link Skills.Perk#DRY_REMARK}): a line said aloud in a speech bubble. Anyone of a weak temperament
 * within {@link #REMARK_RANGE} blocks who holds something lets it fall: the gentle always, a plain one if you are the stronger
 * ({@link Will#weaker}); the bold and the grasping never.</li>
 * </ul>
 * Lifting a weapon out of a pocket is the existing pickpocket ({@code Pickpocket}, {@link Skills.Perk#LIFT_WEAPON}).
 */
public final class RogueForms {
	static final Form DISARM_RUSH = new Form("disarm_rush", Family.KNIFE, Kind.ART, 300, 3, 1.5F, 6);
	static final Form DRY_REMARK = new Form("dry_remark", Family.KNIFE, Kind.ART, 400, 2, 0.5F, 6);

	// ---- tuning ----
	/** Disarm Rush: how far ahead it looks for foes (blocks), how straight ahead they must be (cosine), how many it takes, how far past each you land, and the stagger (ticks). */
	static final double RUSH_RANGE = 12.0, RUSH_FACING = 0.2, RUSH_PAST = 1.3;
	static final int RUSH_FOES = 3, STAGGER_TICKS = 30;
	/** A weapon knocked from a mob is taken up again after this long (ticks) if it is still lying where it fell near it (blocks). */
	static final int REARM_TICKS = 300;
	static final double REARM_RANGE = 16.0;
	/** A player's dropped item: ticks before anyone may pick it up. A mob's: the same. */
	static final int PLAYER_DELAY = 60, MOB_DELAY = 40;
	/** The speed a dropped weapon is flung (blocks per tick). */
	static final double FLING = 0.25;
	/** Dry Remark: how far the line carries (blocks), how loud it is heard (blocks), and the number of lines. */
	static final double REMARK_RANGE = 8.0, REMARK_HEARD = 24.0;
	static final int REMARK_LINES = 4;
	/** The temperaments that always lose their grip, and the ones that never do. */
	private static final Set<Temperament> GENTLE = Set.of(Temperament.TIMID, Temperament.GLOOMY, Temperament.DUTIFUL, Temperament.DEVOUT,
		Temperament.KINDHEARTED, Temperament.CHEERFUL, Temperament.SUSPICIOUS);
	private static final Set<Temperament> STUBBORN = Set.of(Temperament.BRAVE, Temperament.PROUD, Temperament.HOTHEADED, Temperament.SPITEFUL,
		Temperament.GREEDY);

	/** A weapon waiting to go back into a mob's hand. */
	private record Rearm(Mob mob, ServerLevel level, UUID item, ItemStack stack, long at) {
	}

	private static final List<Rearm> REARMS = new ArrayList<>();

	private RogueForms() {
	}

	static void addTo(Map<String, Form> forms) {
		forms.put(DISARM_RUSH.id(), DISARM_RUSH);
		forms.put(DRY_REMARK.id(), DRY_REMARK);
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(RogueForms::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			// (shutting down: every mob keeps its weapon, so none is lost)
			for (Rearm r : REARMS) {
				rearm(r);
			}
			REARMS.clear();
		});
	}

	/** Runs {@code form} if it is one of this class's; false if not. */
	static boolean perform(ServerPlayer player, Form form, int rank) {
		if (!(player.level() instanceof ServerLevel level)) {
			return false;
		}
		if (form == DISARM_RUSH) {
			rush(level, player);
			return true;
		}
		if (form == DRY_REMARK) {
			remark(level, player);
			return true;
		}
		return false;
	}

	// ---- disarm rush ----------------------------------------------------------------------------------------------------

	private static boolean armed(ServerPlayer player, LivingEntity e) {
		if (e == player || !e.isAlive() || e.isSpectator() || Downed.isDowned(e) || e.getMainHandItem().isEmpty()) {
			return false;
		}
		if (e instanceof ServerPlayer other) {
			return player.level().isPvpAllowed() && !other.isCreative() && !Parties.allied(player, other);
		}
		return e instanceof Mob mob && (mob instanceof Enemy || mob.getTarget() == player);
	}

	private static void rush(ServerLevel level, ServerPlayer player) {
		Vec3 look = player.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0.0, look.z);
		if (flat.lengthSqr() < 1.0E-6) {
			return;
		}
		Vec3 ahead = flat.normalize();
		List<LivingEntity> foes = new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(RUSH_RANGE), e -> {
			if (!armed(player, e)) {
				return false;
			}
			Vec3 to = e.position().subtract(player.position()).multiply(1.0, 0.0, 1.0);
			return to.lengthSqr() > 1.0E-6 && to.normalize().dot(ahead) > RUSH_FACING;
		}));
		foes.sort(Comparator.comparingDouble(player::distanceToSqr));
		if (foes.size() > RUSH_FOES) {
			foes = foes.subList(0, RUSH_FOES);
		}
		if (foes.isEmpty()) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.form.disarm_rush.none").withStyle(ChatFormatting.GRAY));
			return;
		}
		List<LivingEntity> hit = new ArrayList<>();
		for (LivingEntity foe : foes) {
			Vec3 start = player.position();
			Vec3 dir = foe.position().subtract(start).multiply(1.0, 0.0, 1.0);
			if (dir.lengthSqr() < 1.0E-6) {
				dir = ahead;
			}
			dir = dir.normalize();
			Vec3 dest = null;
			for (double past : new double[] {RUSH_PAST, 0.8, -1.0}) {
				Vec3 candidate = foe.position().add(dir.scale(past));
				if (level.noCollision(player, player.getBoundingBox().move(candidate.subtract(start)))) {
					dest = candidate;
					break;
				}
			}
			if (dest == null) {
				continue;
			}
			Fx.beam(level, start.add(0.0, 1.0, 0.0), dest.add(0.0, 1.0, 0.0), 0.12F, 0xB9B4D6, 6);
			float yaw = net.minecraft.util.Mth.wrapDegrees((float)(net.minecraft.util.Mth.atan2(dir.z, dir.x) * 57.2957763671875) - 90.0F);
			player.teleportTo(level, dest.x, dest.y, dest.z, Set.of(), yaw, 0.0F, false);
			if (knock(level, player, foe)) {
				hit.add(foe);
			}
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 1.8F);
		Forms.landed(player, DISARM_RUSH, hit);
	}

	/** {@code foe}'s weapon is knocked from its hand and lands near it. True if it was. */
	private static boolean knock(ServerLevel level, ServerPlayer thief, LivingEntity foe) {
		ItemStack held = foe.getMainHandItem();
		if (held.isEmpty()) {
			return false;
		}
		Vec3 fling = foe.position().subtract(thief.position()).multiply(1.0, 0.0, 1.0);
		fling = fling.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 0.0, 0.0) : fling.normalize().scale(FLING);
		ItemEntity dropped;
		if (foe instanceof ServerPlayer player) {
			ItemStack taken = held.copy();
			player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			dropped = player.drop(taken, false, Prediction.SERVER_ONLY);
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.disarmed", thief.getDisplayName()).withStyle(ChatFormatting.RED));
		} else if (foe instanceof Mob mob) {
			ItemStack taken = held.copy();
			mob.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
			dropped = mob.spawnAtLocation(level, taken, 0.8F);
			if (dropped != null) {
				REARMS.add(new Rearm(mob, level, dropped.getUUID(), taken, level.getGameTime() + REARM_TICKS));
			} else {
				mob.setItemSlot(EquipmentSlot.MAINHAND, taken);
				return false;
			}
		} else {
			return false;
		}
		if (dropped != null) {
			dropped.setPickUpDelay(foe instanceof Player ? PLAYER_DELAY : MOB_DELAY);
			dropped.setDeltaMovement(fling.x, 0.2, fling.z);
		}
		foe.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, STAGGER_TICKS, 1, false, false));
		Fx.blast(level, foe.getEyePosition(), 0.7F, 0xFFE27A);
		level.playSound(null, foe.getX(), foe.getY(), foe.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 0.8F, 1.3F);
		return true;
	}

	private static void tick(MinecraftServer server) {
		if (REARMS.isEmpty()) {
			return;
		}
		for (Iterator<Rearm> it = REARMS.iterator(); it.hasNext();) {
			Rearm r = it.next();
			if (r.mob().isRemoved() || !r.mob().isAlive() || r.level().getGameTime() >= r.at()) {
				it.remove();
				if (r.mob().isAlive() && !r.mob().isRemoved()) {
					rearm(r);
				}
			}
		}
	}

	/** The weapon goes back into the mob's hand if it is still on the ground near it (someone who took it has it, and the mob stays bare-handed). */
	private static void rearm(Rearm r) {
		if (r.level().getEntity(r.item()) instanceof ItemEntity item && !item.isRemoved() && item.distanceTo(r.mob()) <= REARM_RANGE
			&& r.mob().getMainHandItem().isEmpty()) {
			item.discard();
			r.mob().setItemSlot(EquipmentSlot.MAINHAND, r.stack());
		}
	}

	// ---- dry remark -------------------------------------------------------------------------------------------------------

	/** Whether {@code e}, humanoid and holding something, lets go at a remark from {@code player}. */
	private static boolean rattled(ServerPlayer player, Mob e) {
		if (!Temperaments.humanoid(e) || !e.isAlive() || e.getMainHandItem().isEmpty() || Downed.isDowned(e) || e instanceof Folk folk && folk.leader()) {
			return false;
		}
		Temperament t = Temperaments.of(e);
		if (t != null && GENTLE.contains(t)) {
			return true;
		}
		return (t == null || !STUBBORN.contains(t)) && Will.weaker(player, e);
	}

	private static void remark(ServerLevel level, ServerPlayer player) {
		Speech.say(level, player, Component.translatable("message.jaymoskingdoms.remark." + level.getRandom().nextInt(REMARK_LINES)), Speech.YELLOW, REMARK_HEARD);
		List<LivingEntity> hit = new ArrayList<>();
		for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(REMARK_RANGE), e -> rattled(player, e))) {
			if (knock(level, player, mob)) {
				hit.add(mob);
			}
		}
		if (hit.isEmpty()) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.form.dry_remark.none").withStyle(ChatFormatting.GRAY));
		}
		Forms.landed(player, DRY_REMARK, hit);
	}
}
