package com.jaymoskingdoms.spirits;

import com.jaymoskingdoms.Arcana;
import com.jaymoskingdoms.Mana;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * (0.7.517) Three spirit moves from Andy's wizard ideas (The Four Houses, MIT, ideas only; Andy: "spellcraft perks or moves
 * for the ki spirits"). Disarming and a shield were already the spirits' (Thessaly's Unseen Hand, Ambrose's Aegis), so these
 * are the new ones:
 * <ul>
 * <li><b>Petrify</b> (the Quiet One, level 4): the foe in sight turns to stone for a few seconds, rooted, slow-handed, grey dust
 * falling off it. No bosses.</li>
 * <li><b>Guardian Light</b> (Ambrose, level 5): a burst of pale light; the undead near you burn, are hurt and thrown back.</li>
 * <li><b>Turn Back</b> (Thessaly, level 6): for a few seconds her hands catch arrows and bolts flying at you and send them back
 * the way they came.</li>
 * </ul>
 */
final class SpiritCharms {
	static final float PETRIFY_COST = 22.0F, GUARDIAN_COST = 26.0F, TURN_BACK_COST = 18.0F;
	static final int PETRIFY_COOLDOWN = 20 * 20, GUARDIAN_COOLDOWN = 20 * 45, TURN_BACK_COOLDOWN = 20 * 25;
	/** Petrify: reach (blocks) and how long the stone holds (ticks). */
	static final double PETRIFY_RANGE = 16.0;
	static final int PETRIFY_TICKS = 20 * 4;
	/** Guardian Light: radius (blocks), the hurt it does to each undead, how long they burn (s) and how hard they're thrown. */
	static final double GUARDIAN_RADIUS = 12.0, GUARDIAN_PUSH = 1.2;
	static final float GUARDIAN_DAMAGE = 6.0F;
	static final int GUARDIAN_BURN = 4;
	/** Turn Back: how long (ticks) and how near (blocks) a missile is caught. */
	static final int TURN_BACK_TICKS = 20 * 5;
	static final double TURN_BACK_RADIUS = 3.5;

	/** Game time each player's Turn Back ends. */
	private static final Map<UUID, Long> TURNING = new HashMap<>();
	/** Petrified foes: the game time their stone wears off (to shed dust while it holds). */
	private static final Map<LivingEntity, Long> STONE = new HashMap<>();

	private SpiritCharms() {
	}

	static Move petrify() {
		return new Ambrose.Def("petrify", PETRIFY_COST, PETRIFY_COOLDOWN, 4, Poses.PIN, Mana.HARM, SpiritCharms::petrify);
	}

	static Move guardian() {
		return new Ambrose.Def("guardian", GUARDIAN_COST, GUARDIAN_COOLDOWN, 5, Poses.BLESS, Mana.WARD, SpiritCharms::guardian);
	}

	static Move turnBack() {
		return new Ambrose.Def("turn_back", TURN_BACK_COST, TURN_BACK_COOLDOWN, 6, Poses.CATCH, Mana.WARD, SpiritCharms::turnBack);
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(SpiritCharms::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			TURNING.clear();
			STONE.clear();
		});
	}

	private static boolean petrify(ServerPlayer player, SpiritState state, int charge) {
		LivingEntity foe = Arcana.sighted(player);
		if (foe == null || player.distanceTo(foe) > PETRIFY_RANGE || !SpiritFoes.foe(player, foe, true) || QuietOne.boss(foe)
			|| !(player.level() instanceof ServerLevel level)) {
			return false;
		}
		foe.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, PETRIFY_TICKS, 9, false, false), player);
		foe.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, PETRIFY_TICKS, 4, false, false), player);
		foe.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, PETRIFY_TICKS, 2, false, false), player);
		foe.setDeltaMovement(Vec3.ZERO);
		STONE.put(foe, level.getGameTime() + PETRIFY_TICKS);
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()), foe.getX(), foe.getY(0.5),
			foe.getZ(), 40, foe.getBbWidth() * 0.4, foe.getBbHeight() * 0.4, foe.getBbWidth() * 0.4, 0.1);
		level.playSound(null, foe.getX(), foe.getY(), foe.getZ(), SoundEvents.STONE_PLACE, SoundSource.PLAYERS, 1.2F, 0.6F);
		level.playSound(null, foe.getX(), foe.getY(), foe.getZ(), SoundEvents.DEEPSLATE_BREAK, SoundSource.PLAYERS, 0.8F, 0.5F);
		return true;
	}

	private static boolean guardian(ServerPlayer player, SpiritState state, int charge) {
		if (!(player.level() instanceof ServerLevel level)) {
			return false;
		}
		AABB box = player.getBoundingBox().inflate(GUARDIAN_RADIUS);
		var undead = level.getEntitiesOfClass(LivingEntity.class, box,
			e -> e.isAlive() && e.is(EntityTypeTags.UNDEAD) && e.distanceTo(player) <= GUARDIAN_RADIUS && !SpiritFoes.ally(player, e));
		Mana.sendSeen(level, Mana.MOTE, player.getX(), player.getY() + 1.2, player.getZ(), 60, 2.5, 1.2, 2.5, 0.05);
		level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.2, player.getZ(), 50, 0.4, 0.6, 0.4, 0.35);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F, 1.6F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 0.8F);
		for (LivingEntity e : undead) {
			e.hurtServer(level, player.damageSources().indirectMagic(player, player), GUARDIAN_DAMAGE);
			e.igniteForSeconds(GUARDIAN_BURN);
			Vec3 away = e.position().subtract(player.position()).multiply(1.0, 0.0, 1.0);
			if (away.lengthSqr() > 1.0E-4) {
				away = away.normalize().scale(GUARDIAN_PUSH);
				e.push(away.x, 0.35, away.z);
				e.syncVelocity = true;
			}
			level.sendParticles(ParticleTypes.END_ROD, e.getX(), e.getY(0.6), e.getZ(), 6, 0.2, 0.3, 0.2, 0.05);
		}
		return true;
	}

	private static boolean turnBack(ServerPlayer player, SpiritState state, int charge) {
		TURNING.put(player.getUUID(), player.level().getGameTime() + TURN_BACK_TICKS);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.4F);
		return true;
	}

	private static void tick(MinecraftServer server) {
		if (!STONE.isEmpty()) {
			STONE.entrySet().removeIf(e -> {
				LivingEntity foe = e.getKey();
				if (!foe.isAlive() || !(foe.level() instanceof ServerLevel level) || level.getGameTime() >= e.getValue()) {
					return true;
				}
				foe.setDeltaMovement(0.0, Math.min(0.0, foe.getDeltaMovement().y), 0.0);
				if (level.getGameTime() % 5 == 0) {
					level.sendParticles(new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.GRAVEL.defaultBlockState()), foe.getX(),
						foe.getY(0.7), foe.getZ(), 3, foe.getBbWidth() * 0.4, foe.getBbHeight() * 0.3, foe.getBbWidth() * 0.4, 0.0);
				}
				return false;
			});
		}
		if (TURNING.isEmpty()) {
			return;
		}
		TURNING.entrySet().removeIf(e -> {
			ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
			if (player == null || !player.isAlive() || player.level().getGameTime() >= e.getValue()) {
				return true;
			}
			ServerLevel level = player.level();
			for (Projectile shot : level.getEntitiesOfClass(Projectile.class, player.getBoundingBox().inflate(TURN_BACK_RADIUS),
				p -> p.getOwner() != player && p.getDeltaMovement().lengthSqr() > 0.04)) {
				Vec3 to = player.getBoundingBox().getCenter().subtract(shot.position());
				if (shot.getDeltaMovement().dot(to) <= 0.0) {
					continue;
				}
				shot.setDeltaMovement(shot.getDeltaMovement().scale(-0.9));
				shot.setOwner(player);
				shot.syncVelocity = true;
				level.sendParticles(ParticleTypes.ENCHANTED_HIT, shot.getX(), shot.getY(), shot.getZ(), 8, 0.1, 0.1, 0.1, 0.1);
				level.playSound(null, shot.getX(), shot.getY(), shot.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.7F, 1.6F);
			}
			return false;
		});
	}
}
