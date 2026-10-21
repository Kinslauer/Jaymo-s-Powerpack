package com.jaymospowers.power.wind;

import com.jaymospowers.core.Fate;
import com.jaymospowers.core.Powers;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.Vec3;

/**
 * (0.10.17) The quarterstaff in a Wind power user's hand (whatever their class) or a wind sorcerer's (a Kingdoms Sorcerer of the air). Its five Better Combat swings (front twirl, sweep, whirlwind, thrust,
 * overhead slam) each carry a gale of their own, counted by our own chain of landed swings (Better Combat's combo index
 * never reaches the server):
 * <ol>
 * <li><b>Gust</b> (the twirl): the struck foe is blown back.</li>
 * <li><b>Wind Blade</b> (the sweep): a blade of air runs on ahead and cuts everything in a cone in front of you.</li>
 * <li><b>Vortex</b> (the whirlwind): everyone around is dragged in toward the spinning staff.</li>
 * <li><b>Piercing Gale</b> (the thrust): the foe is hurled far straight back.</li>
 * <li><b>Downdraft</b> (the slam): a burst that throws everything near up into the air; slam from the air and the
 * burst bounces you back up too.</li>
 * </ol>
 * Right click (or sneak + right click, standing) is the <b>Staff Vault</b>: plant the staff and ride a gust forward
 * and up (straight up when sneaking), landing soft. Every hit with a staff also leaves a puff of air. In anyone else's
 * hand (anyone without the Wind power) it's just a staff. The Tempest mode (Z) and the ascension make every gale hit harder.
 */
public final class WindStaff {
	/** A chain breaks if no swing lands for this long (ticks); a swing is counted once however many it hits. */
	static final int CHAIN_TIMEOUT = 40;
	/** Every hit: how hard the puff pushes the foe away. */
	static final double PUFF_PUSH = 0.25;
	/** Gust: push away and lift. */
	static final double GUST_PUSH = 0.9, GUST_LIFT = 0.25;
	/** Wind Blade: reach (blocks), how wide (cosine of the half-angle), damage (x your attack damage), push. */
	static final double BLADE_REACH = 6.0, BLADE_CONE = 0.6, BLADE_PUSH = 0.45;
	static final float BLADE_DAMAGE = 0.5F;
	/** Vortex: radius (blocks) and pull strength (blocks/tick at the edge). */
	static final double VORTEX_RADIUS = 6.0, VORTEX_PULL = 0.85;
	/** Piercing Gale: push and lift. */
	static final double PIERCE_PUSH = 2.2, PIERCE_LIFT = 0.3;
	/** Downdraft: radius, launch, sideways push, damage (x your attack damage), and how high it bounces a slam from the air. */
	static final double DOWNDRAFT_RADIUS = 4.5, DOWNDRAFT_LIFT = 0.9, DOWNDRAFT_PUSH = 0.5, DOWNDRAFT_BOUNCE = 0.8;
	static final float DOWNDRAFT_DAMAGE = 0.4F;
	/** The Tempest mode (Z) and the ascension: every gale's push and damage times this. */
	static final double TEMPEST_BONUS = 1.3;
	/** Staff Vault: forward and up (blocks/tick), straight up when sneaking, cooldown (ticks), soft landing window. */
	static final double VAULT_FORWARD = 1.25, VAULT_UP = 0.75, VAULT_HIGH_UP = 1.35;
	static final int VAULT_COOLDOWN = 50, VAULT_SOFT_TICKS = 80;

	private record Chain(int step, long lastTick) {
	}

	private static final Map<UUID, Chain> CHAINS = new HashMap<>();
	/** Vaulting players and the tick their soft landing runs out. */
	private static final Map<UUID, Long> VAULTS = new HashMap<>();
	/** Our own gale hits fire the damage event too: don't count those as swings. */
	private static boolean inGale;

	private WindStaff() {
	}

	static void register() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (!inGale && damageTaken > 0.0F && source.is(DamageTypes.PLAYER_ATTACK) && source.getDirectEntity() instanceof ServerPlayer attacker
				&& entity != attacker && isStaff(attacker.getMainHandItem()) && wielder(attacker)) {
				hit(attacker, entity);
			}
		});
		UseItemCallback.EVENT.register((player, world, hand) -> {
			if (hand != InteractionHand.MAIN_HAND || !isStaff(player.getItemInHand(hand)) || !wielder(player)) {
				return InteractionResult.PASS;
			}
			if (player instanceof ServerPlayer serverPlayer) {
				vault(serverPlayer, player.getItemInHand(hand));
			}
			return InteractionResult.SUCCESS;
		});
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
			!(source.is(DamageTypes.FALL) && entity instanceof ServerPlayer player && VAULTS.containsKey(player.getUUID())));
		ServerTickEvents.END_SERVER_TICK.register(WindStaff::tick);
	}

	/** Any quarterstaff (the Armory's, or one another mod names so). */
	public static boolean isStaff(ItemStack stack) {
		return !stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().endsWith("quarterstaff");
	}

	/**
	 * (0.15.17, Andy: "wind should keep the quarterstaff abilities regardless of class") Who the staff's gales answer:
	 * anyone with the Wind power, whatever their class, and still an air Sorcerer without it.
	 */
	static boolean wielder(Player player) {
		return Powers.has(player, WindPower.INSTANCE) || wind(player);
	}

	/**
	 * (Andy: "only wind sorcerers") A Jaymo's Kingdoms Sorcerer who picked the air element. Read by name, so
	 * without Kingdoms nobody is one.
	 */
	static boolean wind(Player player) {
		if (CLASS_OF == null) {
			return false;
		}
		try {
			Object chosen = CLASS_OF.invoke(player);
			return "sorcerer".equals(CLASS_KEY.invoke(chosen)) && "air".equals(CLASS_OPTION.invoke(chosen));
		} catch (Throwable t) {
			return false;
		}
	}

	private static final MethodHandle CLASS_OF, CLASS_KEY, CLASS_OPTION;

	static {
		MethodHandle of = null, key = null, option = null;
		try {
			Class<?> classes = Class.forName("com.jaymoskingdoms.Classes");
			Class<?> chosen = Class.forName("com.jaymoskingdoms.Classes$Chosen");
			MethodHandles.Lookup lookup = MethodHandles.publicLookup();
			of = lookup.findStatic(classes, "of", MethodType.methodType(chosen, Player.class));
			key = lookup.findVirtual(chosen, "cls", MethodType.methodType(String.class));
			option = lookup.findVirtual(chosen, "option", MethodType.methodType(String.class));
		} catch (ReflectiveOperationException | LinkageError e) {
			of = null;
		}
		CLASS_OF = of;
		CLASS_KEY = key;
		CLASS_OPTION = option;
	}

	static double bonus(ServerPlayer player) {
		return WindModes.tempest(player) || Fate.isAscended(player) ? TEMPEST_BONUS : 1.0;
	}

	static boolean foe(ServerPlayer player, LivingEntity e) {
		return e != player && e.isAlive() && !e.isSpectator() && !player.isAlliedTo(e) && !(e instanceof TamableAnimal pet && pet.isOwnedBy(player));
	}

	// ---------------------------------------------------------------- the combo

	private static void hit(ServerPlayer player, LivingEntity target) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		Vec3 away = target.position().subtract(player.position()).horizontal();
		Vec3 dir = away.lengthSqr() > 1.0E-4 ? away.normalize() : player.getLookAngle().horizontal().normalize();
		double bonus = bonus(player);
		// the puff on every hit
		push(target, dir.scale(PUFF_PUSH * bonus), 0.0);
		Vec3 at = target.position().add(0.0, target.getBbHeight() * 0.6, 0.0);
		level.sendParticles(ParticleTypes.SMALL_GUST, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);

		Chain chain = CHAINS.get(player.getUUID());
		if (chain != null && chain.lastTick() == now) {
			return; // the same swing hitting a second foe
		}
		int step = chain == null || now - chain.lastTick() > CHAIN_TIMEOUT ? 0 : (chain.step() + 1) % 5;
		CHAINS.put(player.getUUID(), new Chain(step, now));
		inGale = true;
		try {
			switch (step) {
				case 0 -> gust(player, target, dir, bonus);
				case 1 -> blade(player, bonus);
				case 2 -> vortex(player, bonus);
				case 3 -> pierce(player, target, dir, bonus);
				default -> downdraft(player, bonus);
			}
		} finally {
			inGale = false;
		}
	}

	private static void gust(ServerPlayer player, LivingEntity target, Vec3 dir, double bonus) {
		push(target, dir.scale(GUST_PUSH * bonus), GUST_LIFT);
		ServerLevel level = player.level();
		Vec3 at = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);
		level.sendParticles(ParticleTypes.GUST, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 0.5F, 1.4F);
	}

	private static void blade(ServerPlayer player, double bonus) {
		ServerLevel level = player.level();
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		// the blade's path: a line of gusts running out ahead
		for (double d = 1.5; d <= BLADE_REACH; d += 1.5) {
			Vec3 p = eye.add(look.scale(d)).add(0.0, -0.4, 0.0);
			level.sendParticles(ParticleTypes.SWEEP_ATTACK, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
			level.sendParticles(ParticleTypes.CLOUD, p.x, p.y, p.z, 3, 0.5, 0.1, 0.5, 0.02);
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BREEZE_SHOOT, SoundSource.PLAYERS, 0.8F, 1.5F);
		float damage = (float)(player.getAttributeValue(Attributes.ATTACK_DAMAGE) * BLADE_DAMAGE * bonus);
		Vec3 flat = look.horizontal().normalize();
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(BLADE_REACH), e -> foe(player, e))) {
			Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
			double distance = to.length();
			if (distance > BLADE_REACH + e.getBbWidth() * 0.5 || distance > 1.0E-3 && to.scale(1.0 / distance).dot(look) < BLADE_CONE) {
				continue;
			}
			e.setInvulnerableTime(0);
			if (e.hurtServer(level, player.damageSources().playerAttack(player), damage)) {
				push(e, flat.scale(BLADE_PUSH * bonus), 0.1);
				level.sendParticles(ParticleTypes.CRIT, e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(), 4, 0.2, 0.2, 0.2, 0.2);
			}
		}
	}

	private static void vortex(ServerPlayer player, double bonus) {
		ServerLevel level = player.level();
		Vec3 center = player.position().add(0.0, 1.0, 0.0);
		level.sendParticles(ParticleTypes.GUST_EMITTER_SMALL, center.x, center.y, center.z, 1, 0.0, 0.0, 0.0, 0.0);
		for (int i = 0; i < 16; i++) {
			double a = Math.PI * 2.0 * i / 16.0;
			double r = VORTEX_RADIUS * 0.7;
			// clouds on a ring, drifting round and in
			level.sendParticles(ParticleTypes.CLOUD, center.x + Math.cos(a) * r, center.y - 0.5, center.z + Math.sin(a) * r, 0,
				-Math.sin(a) - Math.cos(a) * 0.6, 0.05, Math.cos(a) - Math.sin(a) * 0.6, 0.3);
		}
		level.playSound(null, center.x, center.y, center.z, SoundEvents.BREEZE_WHIRL, SoundSource.PLAYERS, 1.0F, 0.9F);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(VORTEX_RADIUS), e -> foe(player, e))) {
			Vec3 in = player.position().subtract(e.position()).horizontal();
			double distance = in.length();
			if (distance > VORTEX_RADIUS || distance < 1.5) {
				continue; // too far, or already at the staff
			}
			double strength = VORTEX_PULL * bonus * Math.min(1.0, distance / VORTEX_RADIUS + 0.3);
			push(e, in.normalize().scale(strength), 0.15);
		}
	}

	private static void pierce(ServerPlayer player, LivingEntity target, Vec3 dir, double bonus) {
		Vec3 line = player.getLookAngle().horizontal().normalize();
		push(target, line.lengthSqr() > 0.5 ? line.scale(PIERCE_PUSH * bonus) : dir.scale(PIERCE_PUSH * bonus), PIERCE_LIFT);
		ServerLevel level = player.level();
		Vec3 at = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);
		level.sendParticles(ParticleTypes.GUST_EMITTER_SMALL, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 0.9F, 1.2F);
	}

	private static void downdraft(ServerPlayer player, double bonus) {
		ServerLevel level = player.level();
		Vec3 center = player.position();
		level.sendParticles(ParticleTypes.GUST_EMITTER_LARGE, center.x, center.y + 0.3, center.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.sendParticles(ParticleTypes.CLOUD, center.x, center.y + 0.1, center.z, 30, DOWNDRAFT_RADIUS * 0.4, 0.1, DOWNDRAFT_RADIUS * 0.4, 0.15);
		level.playSound(null, center.x, center.y, center.z, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 1.3F, 0.7F);
		com.jaymospowers.core.Shakes.heavy(player, center, DOWNDRAFT_RADIUS * 2.0, 1.0F);
		float damage = (float)(player.getAttributeValue(Attributes.ATTACK_DAMAGE) * DOWNDRAFT_DAMAGE * bonus);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(DOWNDRAFT_RADIUS), e -> foe(player, e))) {
			Vec3 away = e.position().subtract(center).horizontal();
			double distance = away.length();
			if (distance > DOWNDRAFT_RADIUS) {
				continue;
			}
			double share = 1.0 - distance / (DOWNDRAFT_RADIUS + 1.0);
			e.setInvulnerableTime(0);
			e.hurtServer(level, player.damageSources().playerAttack(player), damage);
			Vec3 out = distance > 1.0E-3 ? away.scale(1.0 / distance) : Vec3.ZERO;
			push(e, out.scale(DOWNDRAFT_PUSH * share * bonus), DOWNDRAFT_LIFT * share * bonus);
		}
		// slammed from the air: the burst throws you back up, landing soft
		if (!player.onGround()) {
			Vec3 v = player.getDeltaMovement();
			player.setDeltaMovement(v.x, Math.max(v.y, DOWNDRAFT_BOUNCE), v.z);
			player.syncVelocity = true;
			player.fallDistance = 0.0;
			VAULTS.put(player.getUUID(), level.getGameTime() + VAULT_SOFT_TICKS);
		}
	}

	static void push(LivingEntity e, Vec3 horizontal, double lift) {
		Vec3 v = e.getDeltaMovement().add(horizontal);
		e.setDeltaMovement(v.x, Math.max(v.y, 0.0) + lift, v.z);
		e.syncVelocity = true;
	}

	// ---------------------------------------------------------------- the vault

	private static void vault(ServerPlayer player, ItemStack stack) {
		if (player.getCooldowns().isOnCooldown(stack) || player.isPassenger()) {
			return;
		}
		ServerLevel level = player.level();
		boolean high = player.isShiftKeyDown();
		Vec3 flat = player.getLookAngle().horizontal();
		flat = flat.lengthSqr() > 1.0E-4 ? flat.normalize() : Vec3.ZERO;
		Vec3 v = high ? new Vec3(0.0, VAULT_HIGH_UP, 0.0) : flat.scale(VAULT_FORWARD).add(0.0, VAULT_UP, 0.0);
		player.setDeltaMovement(v);
		player.syncVelocity = true;
		player.fallDistance = 0.0;
		player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		VAULTS.put(player.getUUID(), level.getGameTime() + VAULT_SOFT_TICKS);
		player.getCooldowns().addCooldown(stack, WindCold.cooldown(player, VAULT_COOLDOWN));
		Vec3 at = player.position();
		level.sendParticles(ParticleTypes.GUST_EMITTER_SMALL, at.x, at.y + 0.1, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.sendParticles(ParticleTypes.CLOUD, at.x, at.y + 0.1, at.z, 12, 0.4, 0.05, 0.4, 0.05);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.BREEZE_JUMP, SoundSource.PLAYERS, 1.0F, 1.1F);
	}

	private static void tick(MinecraftServer server) {
		for (Iterator<Map.Entry<UUID, Long>> it = VAULTS.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Long> entry = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player == null || !player.isAlive()) {
				it.remove();
				continue;
			}
			long now = player.level().getGameTime();
			// the window ends a moment after you touch down (so the landing tick itself is covered), or when it runs out
			if (now > entry.getValue() || player.onGround() && entry.getValue() - now < VAULT_SOFT_TICKS - 5) {
				player.fallDistance = 0.0;
				it.remove();
			}
		}
		CHAINS.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
	}
}
