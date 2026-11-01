package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Shakes;
import com.jaymospowers.core.Soundscape;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.18, Andy's NOEA pick, added later: self-destruct; "bio built, android, and rubber should be able to learn the self
 * destruct") Self-Destruct, a charged technique (sneak+V, hold and let go) only the Bio-built, the Rubber-folk (Kingdoms
 * heritages) and androids ({@link ZFighterAndroid}) can learn ({@link #FIT}). Andy: "the self destruct should involve
 * grabbing and tethering to another player or mob, for a large explosion that usually kills both and deals extreme
 * terrain damage depending on power". Pressed on a foe within reach, they seize it ({@link #start}) and hold it to them,
 * tethered by a cord of ki, while all their ki swells their body till it glows white ({@link #gather}); let go, it goes
 * off in one great blast that takes them both and tears up the ground, harder and wider the longer it gathered and the
 * stronger their ki ({@link #fire}). Nothing saves the one who set it off: no totem, no going down first.
 */
final class ZFighterSelfDestruct {
	/** Ki to begin, and the most a full charge costs (it takes whatever they have past that too, {@link #fire}). */
	static final float MIN_KI = 40.0F;
	static final float MAX_KI = 120.0F;
	static final int FULL_TICKS = 80;
	/** The blast's reach (blocks) and its hurt at the centre (times Ki Power), charge 0 to full; it falls off to the edge. */
	static final double RADIUS_MIN = 5.0, RADIUS_MAX = 11.0;
	static final float DAMAGE_MIN = 24.0F, DAMAGE_MAX = 60.0F;
	/** How hard it throws what it hurts. */
	static final double THROW = 2.2;
	/** How near the foe must be to seize (blocks), where it's held (blocks in front), and how far it may be dragged off. */
	static final double GRAB_REACH = 3.5, HOLD = 0.9, SLIP = 6.0;
	/** The one held takes this much more at the heart of it: it usually dies with them. */
	static final float HELD_SHARE = 1.6F;
	/** The ground it tears up: explosion power, charge 0 to full, times Ki Power, and no more than this. */
	static final float TERRAIN_MIN = 4.0F, TERRAIN_MAX = 10.0F, TERRAIN_CAP = 18.0F;
	/** The white glow it swells to. */
	static final int GLOW = 0xFFF4E0;

	/** Whether this body can learn it (the Bio-built, the Rubber-folk, an android), synced so the training screen knows too. */
	static final AttachmentType<Boolean> FIT = AttachmentRegistry.create(JaymosPowers.id("zfighter_self_destruct_fit"),
		builder -> builder.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.targetOnly()));

	/**
	 * (0.18.94, Andy: "rubber uses the skin colored blob animation to kind of melt onto the grab target") The foe they've
	 * seized, by entity id, synced to everyone: Jaymo's Kingdoms' {@code RubberEngulf} draws a Rubber-folk's pink rubber
	 * stretching onto it and sealing it in (the swallow's rope and skin, held there and never gulped) in place of the cord
	 * of ki. Not saved.
	 */
	public static final AttachmentType<Integer> HOLDING = AttachmentRegistry.create(JaymosPowers.id("zfighter_self_destruct_holding"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));

	/** Player → the foe they've seized. Not saved. */
	private static final java.util.Map<java.util.UUID, LivingEntity> HELD = new java.util.HashMap<>();

	private ZFighterSelfDestruct() {
	}

	static void clear() {
		HELD.clear();
	}

	/** Sneak+V down: they seize the foe in front of them, or it can't begin. */
	static boolean start(ServerPlayer player) {
		LivingEntity foe = ZFighterArts.find(player, GRAB_REACH);
		if (foe == null) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_self_destruct_nofoe").withStyle(ChatFormatting.GRAY));
			return false;
		}
		HELD.put(player.getUUID(), foe);
		player.setAttached(HOLDING, foe.getId());
		ServerLevel level = player.level();
		level.playSound(null, foe.getX(), foe.getY(), foe.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.0F, 0.6F);
		if (foe instanceof ServerPlayer held) {
			held.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_self_destruct_held", player.getDisplayName())
				.withStyle(ChatFormatting.RED));
		}
		return true;
	}

	/** The foe they hold, while it's still in their grip. */
	private static @Nullable LivingEntity held(ServerPlayer player) {
		LivingEntity foe = HELD.get(player.getUUID());
		if (foe == null || !foe.isAlive() || foe.isRemoved() || foe.level() != player.level() || player.distanceTo(foe) > SLIP) {
			let(player);
			return null;
		}
		return foe;
	}

	/** Their grip is gone. */
	private static void let(ServerPlayer player) {
		HELD.remove(player.getUUID());
		if (player.hasAttached(HOLDING)) {
			player.removeAttached(HOLDING);
		}
	}

	/** Whether {@code player} can learn Self-Destruct. Both sides (the server keeps {@link #FIT} up to date). */
	static boolean fit(Player player) {
		return player.getAttachedOrElse(FIT, false);
	}

	/** Every second: whether their body is one that can blow itself up. */
	static void tick(ServerPlayer player, ZFighterPower.Live live) {
		// stopped gathering without letting it off (stunned, knocked out of it): they let go
		if (live.waveFrom < 0 && HELD.containsKey(player.getUUID())) {
			let(player);
		}
		if (player.tickCount % 20 != 0) {
			return;
		}
		boolean fit = ZFighterAndroid.android(player) || player.entityTags().contains("jaymoskingdoms_heritage_biobuilt")
			|| ZFighterRubberBody.is(player);
		if (fit != fit(player)) {
			if (fit) {
				player.setAttached(FIT, true);
			} else {
				player.removeAttached(FIT);
			}
		}
	}

	/** Why it can't be gathered now (a lang key), or null. */
	static @Nullable String blocker(ServerPlayer player) {
		return fit(player) ? null : "screen.jaymospowers.zfighter.need_self_destruct_body";
	}

	/** The foe held fast to them on a cord of ki; their body swells with light, the ground shakes, and a warning hum rises. */
	static void gather(ServerPlayer player, ZFighterPower.Live live, float charge) {
		ServerLevel level = player.level();
		Vec3 at = player.position().add(0.0, player.getBbHeight() * 0.5, 0.0);
		LivingEntity foe = held(player);
		if (foe != null) {
			Vec3 front = player.getLookAngle().multiply(1.0, 0.0, 1.0);
			front = front.lengthSqr() < 1.0E-4 ? Vec3.ZERO : front.normalize();
			Vec3 spot = player.position().add(front.scale(HOLD + foe.getBbWidth() * 0.5));
			Vec3 pull = spot.subtract(foe.position());
			foe.setDeltaMovement(pull.x * 0.5, Math.min(0.0, foe.getDeltaMovement().y), pull.z * 0.5);
			foe.needsSync = true;
			foe.resetFallDistance();
			if (!ZFighterRubberBody.is(player)) {
				// a Rubber-folk melts onto them instead (drawn by Jaymo's Kingdoms, from HOLDING)
				HeldCord.draw(level, player, foe, charge >= 0.75F ? GLOW : ZFighterArts.color(live));
			}
		}
		int color = charge >= 0.75F ? GLOW : ZFighterArts.color(live);
		double spread = 0.4 + charge * 0.6;
		level.sendParticles(new DustParticleOptions(color, 1.2F + charge * 1.4F), true, false, at.x, at.y, at.z, 3 + (int)(charge * 8),
			spread, player.getBbHeight() * 0.4, spread, 0.0);
		if (level.getGameTime() % 4 == 0) {
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, true, false, at.x, at.y, at.z, 2 + (int)(charge * 6), spread, spread, spread, 0.3);
		}
		if (level.getGameTime() % 10 == 0) {
			Shakes.send(player, 0.2F + charge * 0.5F, at);
		}
		if (Soundscape.due(player, "ki.self_destruct", 6, 9)) {
			Soundscape.play(level, player.position(), SoundSource.PLAYERS, ZFighterKi.KI_HUM.louder(1.2F).pitched(0.6F + charge * 1.2F), ZFighterKi.KI_SURGE);
		}
		if (charge >= 1.0F && level.getGameTime() % 20 == 0) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TNT_PRIMED, SoundSource.PLAYERS, 1.0F, 0.7F);
		}
	}

	/** Let go: one great blast round them and the one they hold, and they are gone with it. */
	static void fire(ServerPlayer player, ZFighterPower.Live live, float charge) {
		ServerLevel level = player.level();
		Vec3 at = player.position().add(0.0, player.getBbHeight() * 0.5, 0.0);
		LivingEntity foe = held(player);
		let(player);
		// whatever ki is left goes into it too
		float rest = Math.max(0.0F, live.ki);
		live.ki = 0.0F;
		float boost = 1.0F + Math.min(1.0F, rest / MAX_KI) * 0.5F;
		double radius = Mth.lerp(charge, RADIUS_MIN, RADIUS_MAX) * boost;
		float damage = Mth.lerp(charge, DAMAGE_MIN, DAMAGE_MAX) * boost * ZFighterPower.kiPower(player, live);
		for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(radius),
			e -> ZFighterArts.valid(player, e))) {
			Vec3 to = near.getBoundingBox().getCenter().subtract(at);
			double distance = to.length();
			if (distance > radius) {
				continue;
			}
			float share = near == foe ? HELD_SHARE : (float)(1.0 - distance / radius * 0.6);
			near.hurtServer(level, ZFighterKi.source(player), damage * share);
			ZFighterKi.push(near, to, THROW * share, 0.6 * share);
		}
		// the ground torn up, by how strong it was (it always takes the ground: there's no holding back a self-destruct)
		float terrain = Math.min(TERRAIN_CAP, Mth.lerp(charge, TERRAIN_MIN, TERRAIN_MAX) * boost * ZFighterPower.kiPower(player, live));
		level.explode(player, ZFighterKi.source(player), ZFighterKi.GROUND_ONLY, at.x, at.y, at.z, terrain, false, Level.ExplosionInteraction.BLOCK);
		level.sendParticles(new DustParticleOptions(GLOW, 4.0F), true, false, at.x, at.y, at.z, 120, radius * 0.4, radius * 0.4, radius * 0.4, 0.0);
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, true, false, at.x, at.y, at.z, 3, radius * 0.2, radius * 0.2, radius * 0.2, 0.0);
		level.sendParticles(net.minecraft.core.particles.ColorParticleOption.create(ParticleTypes.FLASH, 0xFFFFF4E0), true, false, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 4.0F, 0.5F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 3.0F, 0.6F);
		Shakes.send(player, 1.5F + charge, at);
		player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_self_destruct").withStyle(ChatFormatting.GOLD));
		// nothing saves them: past totems, and they don't go down first (Downed lets a bypassing death through)
		if (!player.isCreative()) {
			player.hurtServer(level, level.damageSources().genericKill(), Float.MAX_VALUE);
		}
	}

	/** The cord of ki between them and the one they hold. */
	private static final class HeldCord {
		static void draw(ServerLevel level, ServerPlayer player, LivingEntity foe, int color) {
			Vec3 from = player.position().add(0.0, player.getBbHeight() * 0.6, 0.0), to = foe.getBoundingBox().getCenter();
			Vec3 step = to.subtract(from);
			int steps = Math.max(2, (int)(step.length() * 3.0));
			for (int i = 0; i <= steps; i++) {
				Vec3 p = from.add(step.scale((double)i / steps));
				level.sendParticles(new DustParticleOptions(color, 0.9F), true, false, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
			}
		}
	}
}
