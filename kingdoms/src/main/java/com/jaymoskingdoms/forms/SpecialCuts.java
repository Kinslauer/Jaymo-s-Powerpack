package com.jaymoskingdoms.forms;

import com.jaymoskingdoms.CombatArts;
import com.jaymoskingdoms.Downed;
import com.jaymoskingdoms.PowersCompat;
import com.jaymoskingdoms.forms.Forms.Family;
import com.jaymoskingdoms.forms.Forms.Form;
import com.jaymoskingdoms.forms.Forms.Kind;
import com.jaymoskingdoms.spirits.Spirits;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * The blade school's later arts (Andy's picks from Star Wars: Galaxy in a Cube, ideas only: its heavy attack, its
 * flourish chain and its returning throw, rebuilt as Weapon Forms of our own). The Quiet Tide teaches them after
 * Sheath Draw and Crescent Sweep.
 * <ul>
 * <li><b>Wheel Cut</b>: the blade circles overhead through a long wind-up ({@code WHEEL_CUT.windUp()} ticks), then one
 * blow across {@link #WHEEL_ARC a third of a circle} for {@value #WHEEL_DAMAGE} times your attack. It costs
 * {@value #WHEEL_FEATHERS} feathers. A guard can only block it, never parry it (the red glint says so as it winds up),
 * and a raised shield is knocked out of use for {@value #SHIELD_OUT_TICKS} ticks. Being hurt during the wind-up breaks
 * it off, and it can be stepped out of.</li>
 * <li><b>Figure Eight</b>: the blade spins in continuous eights. Every loop ({@value #LOOP_TICKS} ticks) makes three
 * light cuts (right, behind on the right, ahead) and costs {@value #LOOP_FEATHERS} feathers; it runs until they give
 * out, the blade leaves the hand, or the key is pressed again. While it spins, blows and shots from the front are
 * turned aside for {@value #TURN_FEATHERS} feather each.</li>
 * <li><b>Hurled Blade</b>: the blade is thrown tumbling. It strikes once and sticks where it lands, as a dagger does. It
 * flies back to the hand only for one whose power can call it ({@link #callsBack}): Gravity, a Z Fighter's ki, a mana
 * spirit's Far Hand. Thrown through Powers ({@link PowersCompat#hurlBlade}); without Powers there is no throw.</li>
 * </ul>
 */
final class SpecialCuts {
	static final Form WHEEL_CUT = new Form("wheel_cut", Family.BLADE, Kind.ART, 160, 25, 2.5F, 6);
	static final Form FIGURE_EIGHT = new Form("figure_eight", Family.BLADE, Kind.ART, 120, 0, 0.5F, 5);
	static final Form HURLED_BLADE = new Form("hurled_blade", Family.BLADE, Kind.ART, 100, 8, 1.0F, 5);

	/** Wheel Cut: feathers it costs, how far it reaches (blocks), how wide (cosine of the half-angle: 0.5 is 120 degrees in all), what it deals (times your attack). */
	static final int WHEEL_FEATHERS = 6;
	static final double WHEEL_REACH = 3.4, WHEEL_ARC = 0.5;
	static final float WHEEL_DAMAGE = 3.0F;
	/** Wheel Cut: how long a shield it lands on is out of use (ticks), the poise it wears off (Powers), its shove, its screen shake, and how long the blow itself is drawn (ticks; Powers' {@code ArmedPose.WHEEL_SWEEP_TICKS}). */
	static final int SHIELD_OUT_TICKS = 100;
	static final float WHEEL_POISE = 12.0F, WHEEL_SHAKE = 0.6F;
	static final double WHEEL_SHOVE = 0.6;
	static final int WHEEL_SWEEP_TICKS = 8;
	/** Figure Eight: a loop's length (ticks), the ticks in it its three cuts fall on, the most loops, feathers a loop, and food exhaustion a loop instead when there are no feathers to spend. */
	static final int LOOP_TICKS = 14, MAX_LOOPS = 12, LOOP_FEATHERS = 2;
	static final int[] CUT_AT = {0, 5, 10};
	static final float LOOP_EXHAUSTION = 1.2F;
	/** Figure Eight: the way each cut goes (degrees right of where you look), its reach (blocks), its width (cosine of the half-angle) and what it deals (times your attack). */
	static final float[] CUT_TURN = {75.0F, 150.0F, 0.0F};
	static final double EIGHT_REACH = 2.8, EIGHT_ARC = 0.55;
	static final float EIGHT_DAMAGE = 0.4F;
	/** Figure Eight: how much of the front it guards (cosine of the half-angle) and the feathers each blow turned aside costs. */
	static final double TURN_ARC = 0.5;
	static final int TURN_FEATHERS = 1;
	/** Hurled Blade: what it deals (times your attack), the speed it leaves at (blocks/tick) and each rank adds, and how long the throw is drawn after it leaves (ticks; Powers' {@code ArmedPose.HURL_SNAP_TICKS}). */
	static final float HURL_DAMAGE = 1.1F, HURL_SPEED = 1.7F, HURL_RANK_SPEED = 0.15F;
	static final int HURL_SNAP_TICKS = 6;
	/** The powers that can call a hurled blade back to the hand. */
	static final Set<String> CALLING_POWERS = Set.of("gravity", "zfighter");

	/** A Figure Eight spinning: when it began, the form's rank, and who it has cut. */
	private static final class Spin {
		final long start;
		final int rank;
		final List<LivingEntity> landed = new ArrayList<>();

		Spin(long start, int rank) {
			this.start = start;
			this.rank = rank;
		}
	}

	private static final Map<UUID, Spin> SPINS = new HashMap<>();

	private SpecialCuts() {
	}

	static void addTo(Map<String, Form> forms) {
		forms.put(WHEEL_CUT.id(), WHEEL_CUT);
		forms.put(FIGURE_EIGHT.id(), FIGURE_EIGHT);
		forms.put(HURLED_BLADE.id(), HURLED_BLADE);
	}

	static boolean owns(Form form) {
		return form == WHEEL_CUT || form == FIGURE_EIGHT || form == HURLED_BLADE;
	}

	/** Called from {@link Forms#register}. */
	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(SpecialCuts::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> SPINS.remove(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> SPINS.clear());
		// a spinning blade turns aside what comes at its front
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !(entity instanceof ServerPlayer player && turnedAside(player, source, amount)));
		// a Wheel Cut still winding up is broken off by a blow that lands
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (entity instanceof ServerPlayer player && taken > 0.0F && Forms.interrupt(player, WHEEL_CUT)) {
				PowersCompat.endCutPose(player);
				player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.form.broken", WHEEL_CUT.name()).withStyle(ChatFormatting.GRAY));
			}
		});
	}

	/** Before a form is spent: whether {@code player} has the wind for it (the feathers are taken here). */
	static boolean ready(ServerPlayer player, Form form) {
		if (form == WHEEL_CUT) {
			return PowersCompat.spendFeathers(player, WHEEL_FEATHERS);
		}
		if (form == FIGURE_EIGHT) {
			return PowersCompat.spendFeathers(player, LOOP_FEATHERS);
		}
		return true;
	}

	/** A form's wind-up has begun: the body is seen at it, and a Wheel Cut warns whoever it is aimed at. */
	static void windingUp(ServerLevel level, ServerPlayer player, Form form) {
		if (form == WHEEL_CUT) {
			PowersCompat.cutPose(player, PowersCompat.CUT_WHEEL, form.windUp() + WHEEL_SWEEP_TICKS, 0);
			Vec3 at = player.getEyePosition().add(player.getLookAngle().scale(0.8)).add(0.0, 0.5, 0.0);
			if (!PowersCompat.tellGlint(level, at, true)) {
				level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 8, 0.2, 0.2, 0.2, 0.05);
			}
			for (LivingEntity foe : inArc(level, player, Forms.reach(WHEEL_REACH, 1) + 2.0, WHEEL_ARC, flatLook(player))) {
				if (foe instanceof Player) {
					PowersCompat.tellIncoming(player, foe, form.windUp(), true);
				}
			}
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.8F, 0.5F);
		} else if (form == HURLED_BLADE) {
			PowersCompat.cutPose(player, PowersCompat.CUT_HURL, form.windUp() + HURL_SNAP_TICKS, 0);
		}
	}

	/** A wind-up of ours ended before its blow (the stance was dropped, the blade put away): the body stops showing it. */
	static void cutShort(ServerPlayer player, Form form) {
		if (owns(form)) {
			PowersCompat.endCutPose(player);
		}
	}

	static void perform(ServerPlayer player, Form form, int rank) {
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}
		if (form == WHEEL_CUT) {
			wheelCut(level, player, form, rank);
		} else if (form == FIGURE_EIGHT) {
			figureEight(level, player, rank);
		} else if (form == HURLED_BLADE) {
			hurledBlade(level, player, form, rank);
		}
	}

	/** Where {@code player} looks, along the ground (the way they face if they look straight up or down). */
	private static Vec3 flatLook(ServerPlayer player) {
		Vec3 look = player.getLookAngle().multiply(1.0, 0.0, 1.0);
		return look.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0.0F, player.getYRot()) : look.normalize();
	}

	/** {@code dir} turned {@code degrees} to the right, on the ground. */
	private static Vec3 turned(Vec3 dir, float degrees) {
		double a = Math.toRadians(degrees);
		return new Vec3(dir.x * Math.cos(a) - dir.z * Math.sin(a), 0.0, dir.x * Math.sin(a) + dir.z * Math.cos(a));
	}

	/** The foes a cut along {@code dir} reaches: within {@code reach} of {@code player}, at their height, inside the arc. */
	private static List<LivingEntity> inArc(ServerLevel level, ServerPlayer player, double reach, double arc, Vec3 dir) {
		List<LivingEntity> hits = new ArrayList<>();
		for (LivingEntity foe : Forms.targets(level, player, player.getBoundingBox().inflate(reach, 1.0, reach))) {
			Vec3 to = foe.position().subtract(player.position()).multiply(1.0, 0.0, 1.0);
			double d = to.length();
			if (d <= reach + foe.getBbWidth() / 2.0 && (d < 1.0E-3 || to.scale(1.0 / d).dot(dir) > arc)) {
				hits.add(foe);
			}
		}
		return hits;
	}

	/** The trail of a cut: an arc at chest height round {@code player}, {@code half} radians either side of {@code dir}. */
	private static void arcTrail(ServerLevel level, ServerPlayer player, Vec3 dir, double half, double radius, double height) {
		Vec3 before = null;
		for (int i = -4; i <= 4; i++) {
			Vec3 d = turned(dir, (float)Math.toDegrees(i / 4.0 * half));
			Vec3 at = new Vec3(player.getX() + d.x * radius, player.getY() + height, player.getZ() + d.z * radius);
			if (before != null) {
				Trails.blade(level, before, at);
			}
			before = at;
		}
	}

	// ---- Wheel Cut -----------------------------------------------------------------------------------------------------

	private static void wheelCut(ServerLevel level, ServerPlayer player, Form form, int rank) {
		Vec3 look = flatLook(player);
		double reach = Forms.reach(WHEEL_REACH, rank);
		// (the blow itself: also where a wind-up skipped by a Form Cancel begins)
		PowersCompat.cutPose(player, PowersCompat.CUT_WHEEL, form.windUp() + WHEEL_SWEEP_TICKS, form.windUp());
		List<LivingEntity> hits = inArc(level, player, reach, WHEEL_ARC, look);
		arcTrail(level, player, look, Math.acos(WHEEL_ARC), reach * 0.8, 1.2);
		arcTrail(level, player, look, Math.acos(WHEEL_ARC), reach * 0.55, 1.1);
		Vec3 front = player.position().add(look.scale(reach * 0.7));
		level.sendParticles(ParticleTypes.SWEEP_ATTACK, front.x, player.getY() + 1.2, front.z, 3, 0.6, 0.1, 0.6, 0.0);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.2F, 0.55F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.0F, 0.7F);
		// no guard parries this: Powers reads the tag off the one who strikes
		boolean tagged = player.addTag(PowersCompat.UNPARRYABLE_TAG);
		try {
			for (LivingEntity foe : hits) {
				shieldDown(level, foe);
				CombatArts.strike(level, player, foe, Forms.damage(player, WHEEL_DAMAGE, rank));
				if (!foe.isAlive()) {
					continue;
				}
				PowersCompat.wearPoise(foe, WHEEL_POISE);
				Vec3 out = foe.position().subtract(player.position()).multiply(1.0, 0.0, 1.0);
				Vec3 push = out.lengthSqr() < 1.0E-4 ? look : out.normalize();
				foe.setDeltaMovement(foe.getDeltaMovement().add(push.x * WHEEL_SHOVE, 0.15, push.z * WHEEL_SHOVE));
				foe.needsSync = true;
			}
		} finally {
			if (tagged) {
				player.removeTag(PowersCompat.UNPARRYABLE_TAG);
			}
		}
		if (!hits.isEmpty()) {
			PowersCompat.impactFrame(level, hits.get(0).getBoundingBox().getCenter());
		}
		PowersCompat.shake(player, WHEEL_SHAKE);
		Forms.landed(player, form, hits);
	}

	/** A shield raised against a Wheel Cut is beaten down (a blade's guard is not a shield: it blocks, and pays for it). */
	private static void shieldDown(ServerLevel level, LivingEntity foe) {
		if (!foe.isBlocking()) {
			return;
		}
		ItemStack with = foe.getItemBlockingWith();
		if (with == null || with.isEmpty() || with.has(DataComponents.WEAPON)) {
			return;
		}
		if (foe instanceof Player p) {
			p.getCooldowns().addCooldown(with, SHIELD_OUT_TICKS);
		}
		foe.stopUsingItem();
		level.playSound(null, foe.getX(), foe.getY(), foe.getZ(), SoundEvents.SHIELD_BREAK, SoundSource.PLAYERS, 0.9F, 0.9F);
	}

	// ---- Figure Eight --------------------------------------------------------------------------------------------------

	private static void figureEight(ServerLevel level, ServerPlayer player, int rank) {
		SPINS.put(player.getUUID(), new Spin(level.getGameTime(), rank));
		PowersCompat.cutPose(player, PowersCompat.CUT_EIGHT, MAX_LOOPS * LOOP_TICKS, 0);
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.form.figure_eight").withStyle(ChatFormatting.GOLD));
	}

	/** A press while a Figure Eight spins lets it drop. True if one was spinning (so {@link Forms} treats the press as spent). */
	static boolean stop(ServerPlayer player) {
		Spin spin = SPINS.remove(player.getUUID());
		if (spin == null) {
			return false;
		}
		end(player, spin);
		return true;
	}

	private static void end(ServerPlayer player, Spin spin) {
		PowersCompat.endCutPose(player);
		Forms.landed(player, FIGURE_EIGHT, spin.landed);
	}

	private static void tick(MinecraftServer server) {
		if (SPINS.isEmpty()) {
			return;
		}
		for (Iterator<Map.Entry<UUID, Spin>> it = SPINS.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Spin> entry = it.next();
			Spin spin = entry.getValue();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player == null || !(player.level() instanceof ServerLevel level)) {
				it.remove();
				continue;
			}
			long t = level.getGameTime() - spin.start;
			int at = (int)(t % LOOP_TICKS);
			boolean over = !player.isAlive() || player.isSpectator() || Downed.isDowned(player) || !Family.BLADE.holds(player.getMainHandItem())
				|| player.isPassenger() || t >= (long)MAX_LOOPS * LOOP_TICKS;
			// each new loop is paid for as it starts (the first was, as the form was spent)
			if (!over && t > 0 && at == 0) {
				if (PowersCompat.hasFeathers()) {
					over = !PowersCompat.spendFeathers(player, LOOP_FEATHERS);
				} else {
					player.causeFoodExhaustion(LOOP_EXHAUSTION);
					over = !player.isCreative() && player.getFoodData().getFoodLevel() < Forms.MIN_FOOD;
				}
			}
			if (over) {
				it.remove();
				end(player, spin);
				continue;
			}
			for (int i = 0; i < CUT_AT.length; i++) {
				if (CUT_AT[i] == at) {
					eightCut(level, player, spin, i);
				}
			}
		}
	}

	/** One cut of the eight: a light arc the way that cut goes. */
	private static void eightCut(ServerLevel level, ServerPlayer player, Spin spin, int which) {
		Vec3 dir = turned(flatLook(player), CUT_TURN[which]);
		double reach = Forms.reach(EIGHT_REACH, spin.rank);
		arcTrail(level, player, dir, Math.acos(EIGHT_ARC), reach * 0.75, which == 1 ? 0.9 : 1.3);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.6F,
			1.3F + 0.15F * which);
		for (LivingEntity foe : inArc(level, player, reach, EIGHT_ARC, dir)) {
			CombatArts.strike(level, player, foe, Forms.damage(player, EIGHT_DAMAGE, spin.rank));
			level.sendParticles(ParticleTypes.SWEEP_ATTACK, foe.getX(), foe.getY(0.6), foe.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
			if (!spin.landed.contains(foe)) {
				spin.landed.add(foe);
			}
		}
	}

	/** Damage on its way to someone spinning a Figure Eight: true when the blade turned it aside (none of it lands). */
	private static boolean turnedAside(ServerPlayer player, DamageSource source, float amount) {
		if (amount <= 0.0F || !SPINS.containsKey(player.getUUID()) || source.is(DamageTypeTags.BYPASSES_SHIELD) || source.is(DamageTypeTags.IS_EXPLOSION)) {
			return false;
		}
		Entity direct = source.getDirectEntity();
		boolean shot = direct instanceof Projectile || source.is(DamageTypeTags.IS_PROJECTILE);
		boolean melee = !shot && direct instanceof LivingEntity && direct == source.getEntity();
		Vec3 from = source.getSourcePosition();
		if (!shot && !melee || from == null) {
			return false;
		}
		// an unparryable blow (an ogre's overhead, a ball from a gun, another's Wheel Cut) goes straight through the spin
		if (direct != null && direct.entityTags().contains(PowersCompat.UNPARRYABLE_TAG)
			|| source.getEntity() != null && source.getEntity().entityTags().contains(PowersCompat.UNPARRYABLE_TAG)) {
			return false;
		}
		Vec3 to = from.subtract(player.position()).multiply(1.0, 0.0, 1.0);
		if (to.lengthSqr() > 1.0E-4 && to.normalize().dot(flatLook(player)) < TURN_ARC) {
			return false;
		}
		if (!PowersCompat.spendFeathers(player, TURN_FEATHERS)) {
			return false;
		}
		ServerLevel level = player.level();
		LivingEntity attacker = source.getEntity() instanceof LivingEntity living && living != player ? living : null;
		if (!PowersCompat.guardMet(level, player, player.getMainHandItem(), attacker, 8, 0.9F, 0)) {
			level.sendParticles(ParticleTypes.CRIT, player.getX(), player.getY() + 1.2, player.getZ(), 6, 0.3, 0.3, 0.3, 0.1);
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.6F, 1.5F);
		return true;
	}

	// ---- Hurled Blade --------------------------------------------------------------------------------------------------

	/** Whether a blade {@code player} hurls flies back to the hand: Gravity pulls it, a Z Fighter's ki carries it, a mana spirit's Far Hand fetches it. */
	static boolean callsBack(ServerPlayer player) {
		String power = PowersCompat.power(player);
		return power != null && CALLING_POWERS.contains(power) || Spirits.state(player) != null;
	}

	private static void hurledBlade(ServerLevel level, ServerPlayer player, Form form, int rank) {
		ItemStack held = player.getMainHandItem();
		// (re-checked: the hand may have been swapped during the wind-up)
		if (held.isEmpty() || !Family.BLADE.holds(held)) {
			return;
		}
		PowersCompat.cutPose(player, PowersCompat.CUT_HURL, form.windUp() + HURL_SNAP_TICKS, form.windUp());
		ItemStack blade = held.copyWithCount(1);
		if (!PowersCompat.hurlBlade(player, blade, Forms.damage(player, HURL_DAMAGE, rank), HURL_SPEED + HURL_RANK_SPEED * (rank - 1), callsBack(player))) {
			// (no Powers, or an older one: the blade stays in the hand)
			return;
		}
		if (!player.hasInfiniteMaterials()) {
			held.shrink(1);
			if (held.isEmpty()) {
				player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			}
		}
		Forms.practice(player, form.id(), Forms.DUMMY_PRACTICE);
	}
}
