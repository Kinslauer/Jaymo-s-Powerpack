package com.jaymospowers.power.gravity;

import com.jaymospowers.api.PowerKey;
import com.jaymospowers.core.Mastery;
import com.jaymospowers.core.Powers;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Z (Power Mode), the Immense Field: a dome of heavy gravity set down where you aim (or around you), for a while.
 * Everyone inside but you is heavy: slowed, low-jumping, falling fast. It's for training: anyone with a power inside
 * grows into it faster than a day an ability allows ({@link Mastery#fieldBoosted}); a Z Fighter inside also trains at
 * double the rate (the Z Fighter power asks {@link #inField}), and shadowboxing in it (the Meditate key) far more.
 * Your own field's weight isn't enough for you: you get that boost only while sparring inside it (landing blows on
 * someone or something in it, or taking them there), and every exchange is practice.
 * Z again takes it down. Once the Gravity Well (G) is yours.
 */
public final class GravityTrainingField {
	static final double RADIUS = 5.0;
	static final double PLACE_RANGE = 24.0;
	static final int TICKS = 1200;
	static final int COOLDOWN = 1800;
	/** Extra pull down on anything in the air inside (blocks a tick, each tick). */
	static final double PULL = 0.05;
	/** After a blow traded inside his own field, the owner counts as sparring this long (ticks). */
	static final int SPAR_WINDOW = 100;
	/** One practice point per this many ticks of sparring in his own field, at most. */
	static final int SPAR_CREDIT_SPACING = 200;
	static final int COLOR = 0x8A6CFF;
	/** Hunger the field costs its owner each tick it stands, fresh (4 exhaustion = half a shank): about 5 shanks a full field. */
	static final float EXHAUSTION = 0.035F;
	/** What's left of that cost once the power is fully mastered; it eases down as you level. */
	static final float MASTERED_EXHAUSTION = 0.25F;

	private record Field(ResourceKey<Level> dimension, Vec3 centre, UUID owner, long until) {
	}

	private static final List<Field> FIELDS = new ArrayList<>();
	/** Owner → when they may set down another (game time). */
	private static final Map<UUID, Long> READY = new HashMap<>();
	/** Owner → when he last traded a blow inside his own field (game time); and when that last earned practice. */
	private static final Map<UUID, Long> SPARRED = new HashMap<>();
	private static final Map<UUID, Long> SPAR_CREDIT = new HashMap<>();

	private GravityTrainingField() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(GravityTrainingField::tick);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (FIELDS.isEmpty() || !(source.getEntity() instanceof LivingEntity attacker) || source.getDirectEntity() != attacker) {
				return;
			}
			// a blow traded hand to hand, inside the owner's own field: sparring
			if (attacker instanceof ServerPlayer owner && sparsWith(owner, entity)) {
				spar(owner);
			}
			if (entity instanceof ServerPlayer owner && sparsWith(owner, attacker)) {
				spar(owner);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			FIELDS.clear();
			READY.clear();
			SPARRED.clear();
			SPAR_CREDIT.clear();
		});
	}

	/** Z pressed: set a field down, or take yours down. */
	static void toggle(ServerPlayer player) {
		if (!player.isAlive() || Powers.silenced(player)) {
			return;
		}
		if (!Mastery.isUnlocked(player, PowerKey.ABILITY_2)) {
			return;
		}
		if (FIELDS.removeIf(field -> field.owner().equals(player.getUUID()))) {
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.8F, 0.6F);
			return;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		Long ready = READY.get(player.getUUID());
		if (ready != null && now < ready && !player.isCreative()) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.gravity.field_wait", (ready - now + 19) / 20)
				.withStyle(ChatFormatting.GRAY));
			return;
		}
		Vec3 eye = player.getEyePosition();
		BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(player.getLookAngle().scale(PLACE_RANGE)), ClipContext.Block.COLLIDER,
			ClipContext.Fluid.NONE, player));
		Vec3 centre = hit.getType() == BlockHitResult.Type.MISS ? player.position() : hit.getLocation();
		FIELDS.add(new Field(level.dimension(), centre, player.getUUID(), now + TICKS));
		READY.put(player.getUUID(), now + COOLDOWN);
		level.sendParticles(new DustParticleOptions(COLOR, 2.0F), true, false, centre.x, centre.y + 0.5, centre.z, 60, RADIUS * 0.4, 0.4,
			RADIUS * 0.4, 0.0);
		level.playSound(null, centre.x, centre.y, centre.z, SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 0.8F, 0.6F);
		level.playSound(null, centre.x, centre.y, centre.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8F, 0.5F);
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.gravity.field").withStyle(ChatFormatting.LIGHT_PURPLE));
	}

	/** Holding the field up is hard work: it eats the owner's hunger, less as they level. Starving, they drop it. */
	private static boolean feed(ServerPlayer owner) {
		if (owner.isCreative() || owner.isSpectator()) {
			return true;
		}
		if (owner.getFoodData().getFoodLevel() <= 0) {
			owner.sendOverlayMessage(Component.translatable("message.jaymospowers.gravity.field_starved").withStyle(ChatFormatting.GRAY));
			owner.level().playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.8F, 0.6F);
			return false;
		}
		float level = Mastery.unlocked(owner) >= Mastery.ALL ? 1.0F
			: Math.min(1.0F, Mastery.points(owner) / (float)Mastery.UNLOCK_AT[Mastery.ALL]);
		owner.causeFoodExhaustion(EXHAUSTION * (1.0F - level * (1.0F - MASTERED_EXHAUSTION)));
		return true;
	}

	/** The owner's own field, if he stands in it. */
	private static Field ownField(Entity owner) {
		for (Field field : FIELDS) {
			if (field.owner().equals(owner.getUUID()) && field.dimension() == owner.level().dimension()
				&& owner.position().distanceToSqr(field.centre()) <= RADIUS * RADIUS) {
				return field;
			}
		}
		return null;
	}

	/** Whether {@code owner} stands in his own field with {@code other} in it too (a partner, not an armour stand). */
	private static boolean sparsWith(ServerPlayer owner, Entity other) {
		Field field = ownField(owner);
		return field != null && other != owner && other instanceof LivingEntity && !(other instanceof ArmorStand) && other.isAlive()
			&& other.position().distanceToSqr(field.centre()) <= RADIUS * RADIUS && Powers.has(owner, GravityPower.INSTANCE);
	}

	private static void spar(ServerPlayer owner) {
		long now = owner.level().getGameTime();
		Long last = SPARRED.put(owner.getUUID(), now);
		if (last == null || now - last > SPAR_WINDOW) {
			owner.sendOverlayMessage(Component.translatable("message.jaymospowers.gravity.field_spar").withStyle(ChatFormatting.LIGHT_PURPLE));
		}
		Long credit = SPAR_CREDIT.get(owner.getUUID());
		if (credit == null || now - credit >= SPAR_CREDIT_SPACING) {
			SPAR_CREDIT.put(owner.getUUID(), now);
			Mastery.practice(owner, 1);
		}
	}

	/**
	 * Whether {@code player} trains faster right now (see {@link Mastery#fieldBoosted}): in someone else's field, or in
	 * his own while sparring there.
	 */
	public static boolean boosts(ServerPlayer player) {
		if (FIELDS.isEmpty()) {
			return false;
		}
		if (inField(player)) {
			return true;
		}
		Long sparred = SPARRED.get(player.getUUID());
		return sparred != null && player.level().getGameTime() - sparred <= SPAR_WINDOW && ownField(player) != null;
	}

	/** Whether {@code entity} stands in someone else's Immense Field. */
	public static boolean inField(Entity entity) {
		return owner(entity) != null;
	}

	private static UUID owner(Entity entity) {
		for (Field field : FIELDS) {
			if (field.dimension() == entity.level().dimension() && !field.owner().equals(entity.getUUID())
				&& entity.position().distanceToSqr(field.centre()) <= RADIUS * RADIUS) {
				return field.owner();
			}
		}
		return null;
	}

	private static void tick(MinecraftServer server) {
		if (FIELDS.isEmpty()) {
			return;
		}
		for (Iterator<Field> it = FIELDS.iterator(); it.hasNext(); ) {
			Field field = it.next();
			ServerLevel level = server.getLevel(field.dimension());
			if (level == null || level.getGameTime() >= field.until()) {
				it.remove();
				continue;
			}
			ServerPlayer owner = server.getPlayerList().getPlayer(field.owner());
			if (owner == null || !Powers.has(owner, GravityPower.INSTANCE) || !feed(owner)) {
				it.remove();
				continue;
			}
			weigh(level, field, owner);
		}
	}

	private static void weigh(ServerLevel level, Field field, ServerPlayer owner) {
		long now = level.getGameTime();
		Vec3 c = field.centre();
		for (LivingEntity inside : level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(RADIUS),
			e -> e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand) && !e.getUUID().equals(field.owner()))) {
			if (inside.position().distanceToSqr(c) > RADIUS * RADIUS) {
				continue;
			}
			if (now % 10 == 0) {
				inside.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 15, 1, true, false), owner);
			}
			if (!inside.onGround()) {
				inside.addDeltaMovement(new Vec3(0.0, -PULL, 0.0));
				inside.syncVelocity = true;
			}
		}
		if (now % 3 == 0) {
			// the dome's skin: purple motes on its surface, and a dark ring on the ground
			DustParticleOptions dust = new DustParticleOptions(COLOR, 1.3F);
			var random = level.getRandom();
			for (int i = 0; i < 16; i++) {
				Vec3 dir = new Vec3(random.nextGaussian(), Math.abs(random.nextGaussian()), random.nextGaussian()).normalize();
				Vec3 at = c.add(dir.scale(RADIUS));
				level.sendParticles(dust, true, false, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
			}
			double a = now * 0.15;
			for (int i = 0; i < 8; i++) {
				double t = a + i * Math.PI / 4.0;
				level.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x + Math.cos(t) * RADIUS, c.y + 0.1, c.z + Math.sin(t) * RADIUS, 1, 0.0, 0.0, 0.0,
					0.0);
			}
		}
		if (now % 40 == 0) {
			level.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 0.8F, 0.4F);
		}
	}
}
