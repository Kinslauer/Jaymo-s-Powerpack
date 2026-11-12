package com.jaymospowers.power.ember;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Flames;
import com.jaymospowers.core.Shakes;
import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.power.xenoform.XenoformState;
import com.jaymospowers.power.xenoform.forms.Pyre;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.ChatFormatting;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Running hot, the fire users' fight gauge (Emberborn of any power, and Pyre). Landing blows and taking them builds heat
 * (the HUD bar over the right dock); it cools once the fight is over, and fast in water or rain.
 * <ul>
 * <li>From {@link #HOT_FROM} the hands are white-hot: a melee blow wears the target's armour down faster
 * ({@link #WEAR_HOT}, more at the top). Armour is only worn to one point of durability short of broken, never deleted.</li>
 * <li>Double-tap sneak while at least {@link #VENT_MIN} hot: a vent spends the heat as a burst of fire and steam round you
 * (scaled by how much was spent).</li>
 * <li>At the top you scald (a little self damage every {@link #SCALD_EVERY} ticks, never to death) and every blow sets
 * the target alight; after {@link #MELT_TICKS} at the top the heat bursts: a blast that empties the gauge and leaves you
 * weakened and slowed for {@link #WEAK_TICKS}.</li>
 * </ul>
 * Kindle's own heat bar ({@link EmberKindle}) is the held flame's; this one is the whole fight's.
 */
public final class RunningHot {
	// ---- tuning ----
	public static final int MAX = 100;
	/** Heat above which the hands are white-hot. */
	public static final int HOT_FROM = 60;
	/** Heat gained per point of damage dealt (capped per blow) and taken. */
	static final float GAIN_DEALT = 2.0F, GAIN_CAP = 8.0F, GAIN_TAKEN = 1.5F;
	/** Ticks after the last blow before the heat starts to cool, and how much it cools a tick; in water or rain a tick. */
	static final int CALM_TICKS = 100;
	static final float CALM_DRAIN = 0.15F, WET_DRAIN = 1.5F;
	/** Durability added to each armour piece by a white-hot blow, and at the top. */
	static final int WEAR_HOT = 2, WEAR_TOP = 5;
	/** Seconds a blow at the top sets alight. */
	static final int TOP_BURN = 4;
	/** The vent: least heat, how close two sneaks must be (ticks), reach, damage at a full gauge, burning seconds, cooldown, the share left. */
	static final int VENT_MIN = 30, DOUBLE_TAP = 8, VENT_BURN = 4, VENT_COOLDOWN = 60;
	static final double VENT_RADIUS = 5.0;
	static final float VENT_DAMAGE = 8.0F, VENT_LEFT = 0.15F;
	/** At the top: self damage every this many ticks (and the health it will not go under), ticks before it bursts. */
	static final int SCALD_EVERY = 20, MELT_TICKS = 120;
	static final float SCALD_DAMAGE = 1.0F, SCALD_SPARE = 4.0F;
	/** The burst: reach, damage, burning seconds, the weakness after (ticks), the push. */
	static final double BURST_RADIUS = 6.0;
	static final float BURST_DAMAGE = 10.0F;
	static final int BURST_BURN = 6, WEAK_TICKS = 160;
	static final double BURST_PUSH = 1.0;
	static final int COLOR = 0xFF8A1E, WHITE = 0xFFE9B0, STEAM = 0xD8D8D8;

	/** The heat, 0 to {@link #MAX}, synced to its owner for the HUD; absent when cold. */
	public static final AttachmentType<Integer> HEAT = AttachmentRegistry.create(JaymosPowers.id("running_hot"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.targetOnly()));

	private static final class Live {
		float heat;
		long lastFight, lastEdge = -100, ventReady;
		boolean sneak;
		int top;
		int shown;
	}

	private static final Map<UUID, Live> LIVE = new HashMap<>();
	/** Set while our own damage is dealt (scald, vent, burst), so it does not feed the gauge again. */
	static boolean internal;

	private RunningHot() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (user(player) && player.isAlive() && !player.isSpectator()) {
					tick(player, LIVE.computeIfAbsent(player.getUUID(), k -> new Live()));
				} else if (LIVE.remove(player.getUUID()) != null) {
					player.removeAttached(HEAT);
				}
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> LIVE.clear());
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (internal || taken <= 0.0F) {
				return;
			}
			if (entity instanceof ServerPlayer player && user(player)) {
				add(player, taken * GAIN_TAKEN);
			}
			if (source.getEntity() instanceof ServerPlayer attacker && attacker != entity && user(attacker)) {
				add(attacker, Math.min(taken * GAIN_DEALT, GAIN_CAP));
				FireKit.credit(entity, attacker);
				if (source.getDirectEntity() == attacker) {
					hands(attacker, entity);
				}
			}
		});
	}

	/** A fire user: Emberborn blood (any power or none), or Pyre. Both sides. */
	public static boolean user(Player player) {
		return EmberKindle.ember(player) || XenoformState.form(player) instanceof Pyre;
	}

	public static int heat(Player player) {
		Integer value = player.getAttached(HEAT);
		return value == null ? 0 : value;
	}

	public static boolean hot(Player player) {
		return heat(player) >= HOT_FROM;
	}

	/** Heat for a fire user (the soak feeds it too). */
	public static void add(ServerPlayer player, float amount) {
		if (!user(player)) {
			return;
		}
		Live live = LIVE.computeIfAbsent(player.getUUID(), k -> new Live());
		live.heat = Math.min(MAX, live.heat + amount);
		live.lastFight = player.level().getGameTime();
	}

	/** A melee blow from white-hot hands: the armour wears faster, and at the top the target is set alight. */
	private static void hands(ServerPlayer attacker, LivingEntity target) {
		float heat = heat(attacker);
		if (heat < HOT_FROM) {
			return;
		}
		boolean top = heat >= MAX;
		int wear = top ? WEAR_TOP : WEAR_HOT;
		for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			ItemStack stack = target.getItemBySlot(slot);
			if (!stack.isEmpty() && stack.isDamageableItem()) {
				stack.setDamageValue(Math.min(stack.getMaxDamage() - 1, stack.getDamageValue() + wear));
			}
		}
		ServerLevel level = attacker.level();
		if (top) {
			Flames.touch(level, target, TOP_BURN, attacker);
		}
		Vec3 at = target.getBoundingBox().getCenter();
		level.sendParticles(MagicFx.spark(WHITE, 0.7F), at.x, at.y, at.z, 6, 0.3, 0.4, 0.3, 0.05);
	}

	private static void tick(ServerPlayer player, Live live) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		boolean sneak = player.isShiftKeyDown();
		if (sneak && !live.sneak) {
			if (now - live.lastEdge <= DOUBLE_TAP && live.heat >= VENT_MIN && now >= live.ventReady) {
				vent(player, live, now);
			}
			live.lastEdge = now;
		}
		live.sneak = sneak;
		if (player.isInWaterOrRain() && live.heat > 0.0F) {
			live.heat = Math.max(0.0F, live.heat - WET_DRAIN);
			if (player.tickCount % 6 == 0) {
				level.sendParticles(MagicFx.miasma(STEAM, 1.0F), player.getX(), player.getY() + 1.0, player.getZ(), 2, 0.3, 0.5, 0.3, 0.01);
			}
		} else if (now - live.lastFight > CALM_TICKS) {
			live.heat = Math.max(0.0F, live.heat - CALM_DRAIN);
		}
		if (live.heat >= MAX) {
			live.top++;
			if (live.top % SCALD_EVERY == 0 && !player.isCreative() && player.getHealth() > SCALD_SPARE) {
				internal = true;
				player.hurtServer(level, level.damageSources().generic(), SCALD_DAMAGE);
				internal = false;
				level.sendParticles(ParticleTypes.LARGE_SMOKE, player.getX(), player.getY() + 1.0, player.getZ(), 3, 0.3, 0.5, 0.3, 0.02);
			}
			if (live.top >= MELT_TICKS) {
				burst(player, live, now);
			}
		} else {
			live.top = Math.max(0, live.top - 2);
		}
		if (live.heat >= HOT_FROM && player.tickCount % 3 == 0) {
			Vec3 hand = player.getEyePosition().add(player.getLookAngle().scale(0.7)).subtract(0.0, 0.5, 0.0);
			level.sendParticles(MagicFx.spark(live.heat >= MAX ? WHITE : COLOR, 0.6F), hand.x, hand.y, hand.z, 1, 0.1, 0.1, 0.1, 0.02);
		}
		int shown = Math.round(live.heat);
		if (shown != live.shown) {
			live.shown = shown;
			if (shown <= 0) {
				player.removeAttached(HEAT);
			} else {
				player.setAttached(HEAT, shown);
			}
		}
	}

	/** The vent: the heat goes out as fire and steam. */
	private static void vent(ServerPlayer player, Live live, long now) {
		ServerLevel level = player.level();
		float share = live.heat / MAX;
		live.heat *= VENT_LEFT;
		live.ventReady = now + VENT_COOLDOWN;
		Vec3 at = player.position().add(0.0, 0.5, 0.0);
		double reach = 2.5 + (VENT_RADIUS - 2.5) * share;
		MagicFx.ring(level, player.position(), (float)reach, COLOR);
		for (int i = 0; i < 6; i++) {
			double angle = Math.PI * 2.0 * i / 6.0;
			MagicFx.wave(level, at, new Vec3(Math.cos(angle), 0.0, Math.sin(angle)), reach, 0.8F, COLOR, 10);
		}
		level.sendParticles(MagicFx.miasma(STEAM, 1.6F), at.x, at.y + 0.6, at.z, 30, reach * 0.4, 0.5, reach * 0.4, 0.04);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 1.4F, 0.6F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.0F, 0.7F);
		for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(reach), e -> FireKit.foe(player, e))) {
			FireKit.hurt(player, living, VENT_DAMAGE * share);
			Flames.touch(level, living, VENT_BURN, player);
		}
	}

	/** The top lets go: a blast that empties the gauge and leaves the user weakened. */
	private static void burst(ServerPlayer player, Live live, long now) {
		ServerLevel level = player.level();
		live.heat = 0.0F;
		live.top = 0;
		live.ventReady = now + VENT_COOLDOWN;
		Vec3 at = player.position().add(0.0, 0.8, 0.0);
		MagicFx.blast(level, at, 4.0F, WHITE);
		MagicFx.ring(level, player.position(), (float)BURST_RADIUS, COLOR);
		level.sendParticles(MagicFx.miasma(STEAM, 2.0F), at.x, at.y, at.z, 24, 1.5, 0.8, 1.5, 0.05);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.2F, 1.1F);
		Shakes.heavy(player, at, 16.0, 1.0F);
		for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(BURST_RADIUS), e -> FireKit.foe(player, e))) {
			FireKit.hurt(player, living, BURST_DAMAGE);
			Flames.touch(level, living, BURST_BURN, player);
			Vec3 away = living.position().subtract(player.position()).multiply(1.0, 0.0, 1.0);
			if (away.lengthSqr() > 1.0E-4) {
				living.push(away.normalize().scale(BURST_PUSH).add(0.0, 0.3, 0.0));
				living.syncVelocity = true;
			}
		}
		player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, WEAK_TICKS, 0));
		player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, WEAK_TICKS, 0));
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.hot.burst").withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC));
	}
}
