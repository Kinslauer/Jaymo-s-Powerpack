package com.jaymospowers.power.zfighter;

import com.jaymospowers.core.Powers;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The sparring partner: a Z Fighter sets down his Sparring Dummy and it stands up in his own shape (a vanilla mannequin
 * wearing his skin) and fights him: it closes in and throws punches, now and then a Ki Blast from range or a Flash
 * Step behind him, all at his own power. Every blow he lands trains him as in a real fight (TP, form mastery). It never
 * kills him: a blow that would leave him under a heart ends the bout, and he's lost. Beat it down and it yields: a TP
 * prize. Only he can hurt it, and it hurts only him. A bout lasts at most {@link #BOUT_TICKS}.
 */
final class ZFighterSparring {
	static final int BOUT_TICKS = 6000;
	static final int ITEM_COOLDOWN = 600;
	static final double LEAVE_RANGE = 32.0;
	static final double REACH = 2.6;
	static final double STEP_SPEED = 0.32;
	static final int PUNCH_EVERY = 14;
	/** Its punches hit for this share of his own fists. */
	static final float PUNCH_SHARE = 0.5F;
	static final int BLAST_EVERY = 70;
	static final double BLAST_RANGE = 5.0;
	static final double BLAST_SPEED = 1.0;
	static final float BLAST_SHARE = 0.6F;
	static final int STEP_EVERY = 160;
	/** It has this many times his health. */
	static final float HEALTH_SHARE = 1.5F;
	/** Beating it: this much TP, plus this much for each form he's learned. */
	static final int WIN_TP = 15;
	static final int WIN_TP_PER_FORM = 8;
	static final String TAG = "jaymospowers_sparring";

	private static final class Bout {
		final UUID owner;
		final UUID partner;
		final ServerLevel level;
		final long until;
		int age;
		final List<Vec3[]> blasts = new ArrayList<>();

		Bout(UUID owner, UUID partner, ServerLevel level, long until) {
			this.owner = owner;
			this.partner = partner;
			this.level = level;
			this.until = until;
		}
	}

	/** Partner → bout. */
	private static final Map<UUID, Bout> BOUTS = new HashMap<>();

	private ZFighterSparring() {
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
		ServerTickEvents.END_SERVER_TICK.register(ZFighterSparring::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (Bout bout : BOUTS.values()) {
				var entity = bout.level.getEntity(bout.partner);
				if (entity != null) {
					entity.discard();
				}
			}
			BOUTS.clear();
		});
		// a partner left over from a restart has no bout: it goes
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Mannequin && entity.entityTags().contains(TAG) && !BOUTS.containsKey(entity.getUUID())) {
				entity.discard();
			}
		});
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			Bout bout = BOUTS.get(entity.getUUID());
			if (bout != null) {
				// only its fighter can hurt it
				return source.getEntity() != null && source.getEntity().getUUID().equals(bout.owner);
			}
			return true;
		});
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			Bout bout = BOUTS.get(entity.getUUID());
			if (bout == null) {
				return true;
			}
			entity.setHealth(1.0F);
			ServerPlayer owner = bout.level.getServer().getPlayerList().getPlayer(bout.owner);
			if (owner != null) {
				win(owner);
			}
			end(bout, entity.getUUID());
			return false;
		});
	}

	/** The Sparring Dummy used on a block: his partner stands up there. */
	static InteractionResult use(UseOnContext context) {
		if (!(context.getPlayer() instanceof ServerPlayer player) || !(context.getLevel() instanceof ServerLevel level)) {
			return InteractionResult.SUCCESS;
		}
		if (!Powers.has(player, ZFighterPower.INSTANCE)) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_spar_not_fighter").withStyle(ChatFormatting.GRAY));
			return InteractionResult.FAIL;
		}
		for (Iterator<Map.Entry<UUID, Bout>> it = BOUTS.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Bout> entry = it.next();
			if (entry.getValue().owner.equals(player.getUUID())) {
				// one partner at a time: the old one goes
				var old = entry.getValue().level.getEntity(entry.getKey());
				if (old != null) {
					old.discard();
				}
				it.remove();
			}
		}
		BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
		Mannequin partner = EntityTypes.MANNEQUIN.create(level, EntitySpawnReason.MOB_SUMMONED);
		if (partner == null) {
			return InteractionResult.FAIL;
		}
		partner.getEntityData().set(Profile.key(), ResolvableProfile.createResolved(player.getGameProfile()));
		partner.addTag(TAG);
		partner.setCustomName(Component.translatable("entity.jaymospowers.sparring_partner", player.getDisplayName()));
		partner.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, player.getYRot() + 180.0F, 0.0F);
		var health = partner.getAttribute(Attributes.MAX_HEALTH);
		if (health != null) {
			health.setBaseValue(Math.max(20.0, player.getMaxHealth() * HEALTH_SHARE));
		}
		partner.setHealth(partner.getMaxHealth());
		// (0.17.91) known before it is added: the load sweep would otherwise take it for a leftover
		BOUTS.put(partner.getUUID(), new Bout(player.getUUID(), partner.getUUID(), level, level.getGameTime() + BOUT_TICKS));
		if (!level.addFreshEntity(partner)) {
			BOUTS.remove(partner.getUUID());
			return InteractionResult.FAIL;
		}
		level.sendParticles(ParticleTypes.POOF, partner.getX(), partner.getY() + 1.0, partner.getZ(), 20, 0.3, 0.6, 0.3, 0.02);
		level.playSound(null, partner.getX(), partner.getY(), partner.getZ(), SoundEvents.ARMOR_STAND_PLACE, SoundSource.PLAYERS, 1.0F, 0.8F);
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_spar_start").withStyle(ChatFormatting.GOLD));
		player.getCooldowns().addCooldown(context.getItemInHand(), ITEM_COOLDOWN);
		return InteractionResult.SUCCESS;
	}

	private static void tick(MinecraftServer server) {
		if (BOUTS.isEmpty()) {
			return;
		}
		for (Iterator<Map.Entry<UUID, Bout>> it = BOUTS.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Bout> entry = it.next();
			Bout bout = entry.getValue();
			var entity = bout.level.getEntity(entry.getKey());
			ServerPlayer owner = server.getPlayerList().getPlayer(bout.owner);
			if (!(entity instanceof Mannequin partner) || !partner.isAlive() || owner == null || !owner.isAlive() || owner.level() != bout.level
				|| owner.distanceToSqr(partner) > LEAVE_RANGE * LEAVE_RANGE || bout.level.getGameTime() >= bout.until
				|| !Powers.has(owner, ZFighterPower.INSTANCE)) {
				if (entity != null) {
					vanish(bout.level, entity.position());
					entity.discard();
				}
				it.remove();
				continue;
			}
			fight(owner, partner, bout);
		}
	}

	private static void fight(ServerPlayer owner, Mannequin partner, Bout bout) {
		ServerLevel level = bout.level;
		bout.age++;
		Vec3 toOwner = owner.position().subtract(partner.position());
		double distance = toOwner.length();
		float yaw = (float)(Mth.atan2(toOwner.z, toOwner.x) * Mth.RAD_TO_DEG) - 90.0F;
		partner.setYRot(yaw);
		partner.setYHeadRot(yaw);
		partner.setYBodyRot(yaw);
		Vec3 flat = new Vec3(toOwner.x, 0.0, toOwner.z);
		if (distance > REACH * 0.8 && flat.lengthSqr() > 1.0E-4) {
			Vec3 step = flat.normalize().scale(STEP_SPEED);
			partner.setDeltaMovement(step.x, partner.getDeltaMovement().y, step.z);
			if (partner.horizontalCollision && partner.onGround()) {
				partner.setDeltaMovement(step.x, 0.42, step.z);
			}
		}
		float fists = (float)ZFighterPower.fistBonus(owner) + 1.0F;
		if (distance <= REACH && bout.age % PUNCH_EVERY == 0) {
			partner.swing(bout.age % (PUNCH_EVERY * 2) == 0 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND, SwingAnimation.DEFAULT, true);
			strike(owner, partner, fists * PUNCH_SHARE, bout);
			level.playSound(null, partner.getX(), partner.getY(), partner.getZ(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 0.7F, 1.2F);
		}
		if (distance > BLAST_RANGE && bout.age % BLAST_EVERY == 0 && partner.hasLineOfSight(owner)) {
			Vec3 from = partner.getEyePosition();
			bout.blasts.add(new Vec3[] {from, owner.getEyePosition().subtract(from).normalize().scale(BLAST_SPEED)});
			partner.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
			level.playSound(null, partner.getX(), partner.getY(), partner.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.6F, 1.8F);
		}
		if (bout.age % STEP_EVERY == STEP_EVERY / 2 && distance > 1.5) {
			// a Flash Step: behind him
			float facing = owner.getYRot() * Mth.DEG_TO_RAD;
			Vec3 behind = owner.position().subtract(new Vec3(-Mth.sin(facing), 0.0, Mth.cos(facing)).scale(1.6));
			if (level.noCollision(partner, partner.getBoundingBox().move(behind.subtract(partner.position())))) {
				vanish(level, partner.position());
				partner.teleportTo(behind.x, behind.y, behind.z);
				level.playSound(null, behind.x, behind.y, behind.z, SoundEvents.BREEZE_JUMP, SoundSource.PLAYERS, 0.6F, 1.8F);
			}
		}
		DustParticleOptions dust = new DustParticleOptions(ZFighterPower.COLOR, 1.6F);
		for (Iterator<Vec3[]> it = bout.blasts.iterator(); it.hasNext(); ) {
			Vec3[] blast = it.next();
			blast[0] = blast[0].add(blast[1]);
			// an orb like a real Ki Blast, and one mote
			ZFighterKi.orb(level, System.identityHashCode(blast) | 0x40000000, blast[0], 0.4, ZFighterPower.COLOR);
			level.sendParticles(dust, true, false, blast[0].x, blast[0].y, blast[0].z, 1, 0.05, 0.05, 0.05, 0.0);
			if (owner.getBoundingBox().inflate(0.4).contains(blast[0])) {
				strike(owner, partner, fists * BLAST_SHARE, bout);
				it.remove();
			} else if (blast[0].distanceToSqr(partner.position()) > LEAVE_RANGE * LEAVE_RANGE || !level.isEmptyBlock(BlockPos.containing(blast[0]))) {
				it.remove();
			}
		}
	}

	/** A blow on its fighter; one that would leave him under a heart ends the bout instead. */
	private static void strike(ServerPlayer owner, Mannequin partner, float damage, Bout bout) {
		if (owner.isCreative() || owner.isBlocking()) {
			return;
		}
		if (owner.getHealth() - damage < 1.0F) {
			owner.setHealth(1.0F);
			owner.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_spar_lost").withStyle(ChatFormatting.GRAY));
			end(bout, partner.getUUID());
			return;
		}
		owner.hurtServer(bout.level, owner.damageSources().mobAttack(partner), damage);
		ZFighterKi.push(owner, owner.position().subtract(partner.position()), 0.3, 0.1);
	}

	private static void win(ServerPlayer owner) {
		int prize = WIN_TP + WIN_TP_PER_FORM * ZFighterTraining.formsOpen(owner);
		ZFighterTraining.earn(owner, prize);
		// (0.17.61) a side quest may ask for bouts won
		ZFighterSideQuests.sparWon(owner);
		owner.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_spar_won", prize).withStyle(ChatFormatting.GOLD));
		owner.level().playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8F, 1.2F);
	}

	private static void end(Bout bout, UUID partner) {
		var entity = bout.level.getEntity(partner);
		if (entity != null) {
			vanish(bout.level, entity.position());
			entity.discard();
		}
		// the tick drops the bout once its partner is gone
	}

	private static void vanish(ServerLevel level, Vec3 at) {
		level.sendParticles(ParticleTypes.POOF, at.x, at.y + 1.0, at.z, 12, 0.3, 0.6, 0.3, 0.02);
	}

	/** The Sparring Dummy: a straw fighter that takes your shape when you set it down. */
	static final class DummyItem extends Item {
		DummyItem(Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult useOn(UseOnContext context) {
			return ZFighterSparring.use(context);
		}
	}
}
