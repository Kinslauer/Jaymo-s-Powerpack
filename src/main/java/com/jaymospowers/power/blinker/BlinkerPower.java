package com.jaymospowers.power.blinker;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.api.Power;
import com.jaymospowers.api.PowerKey;
import com.jaymospowers.core.PickKit;
import com.jaymospowers.core.Powers;
import com.jaymospowers.core.fx.MagicFx;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Blinker (Andy's Awakening pick: "a new power, or the End-born's gift"). The End's step, the one the endermen take, woken in a
 * person: they are here, and then they are there, and in the instant between nothing can touch them.
 * <ul>
 * <li>R, <b>Blink</b>: up to {@link #BLINK_RANGE} blocks along the look, short of any wall, and untouchable for
 * {@link #UNTOUCHABLE} ticks. (The starter, at half the reach.)</li>
 * <li>G, <b>Blink Strike</b>: to the foe you look at, up to {@link #STRIKE_RANGE} blocks off, behind it, and a blow.</li>
 * <li>V, <b>Vanish</b>: a burst of blinding smoke; everyone round you loses you, and you blink a few blocks back unseen.</li>
 * <li>B, <b>Sky Drop</b>: take hold of a foe close by and blink with it {@link #DROP_HEIGHT} blocks straight up; let go, and
 * blink back down while it falls.</li>
 * <li>C (ascended), <b>Flicker</b>: for {@link #FLICKER_TICKS} ticks you flicker from foe to foe round you, a blow every
 * {@link #FLICKER_EVERY} ticks, untouchable throughout.</li>
 * </ul>
 */
public final class BlinkerPower implements Power {
	public static final BlinkerPower INSTANCE = new BlinkerPower();
	public static final Identifier ID = JaymosPowers.id("blinker");
	public static final int COLOR = 0xC07CFF;
	static final int SMOKE = 0x2B2433, FLASH = 0xF0D8FF;

	// ---- tuning ----
	private static final String TABLE = "blinker";
	private static final int BLINK = 0, STRIKE = 1, VANISH = 2, DROP = 3, FLICKER = 4;
	/** Blink: reach (blocks), untouchable (ticks), wait. */
	static final double BLINK_RANGE = 12.0;
	static final int UNTOUCHABLE = 10, BLINK_COOLDOWN = 30;
	/** Blink Strike: reach, the blow (a share of the fists, plus), wait. */
	static final double STRIKE_RANGE = 18.0;
	static final float STRIKE_SHARE = 1.5F, STRIKE_PLUS = 2.0F;
	static final int STRIKE_COOLDOWN = 80;
	/** Vanish: the smoke's reach, the blinding and the unseen time (ticks), how far back, wait. */
	static final double VANISH_RADIUS = 5.0, VANISH_BACK = 5.0;
	static final int VANISH_BLIND = 80, VANISH_UNSEEN = 100, VANISH_COOLDOWN = 200;
	/** Sky Drop: how close a foe, how high, the ticks up there before letting go, wait. */
	static final double DROP_REACH = 6.0;
	static final int DROP_HEIGHT = 14, DROP_HANG = 8, DROP_COOLDOWN = 300;
	/** Flicker: how long, a blow every, how far it looks for foes, the blow's share of the fists, wait. */
	static final int FLICKER_TICKS = 40, FLICKER_EVERY = 4, FLICKER_COOLDOWN = 20 * 60;
	static final double FLICKER_RANGE = 12.0;
	static final float FLICKER_SHARE = 0.7F;

	/** Untouchable until (game time). */
	private static final Map<UUID, Long> UNTOUCHED = new HashMap<>();

	private static final class Drop {
		LivingEntity foe;
		Vec3 home;
		long at;
	}

	private static final Map<UUID, Drop> DROPS = new HashMap<>();
	/** Flicker running until (game time). */
	private static final Map<UUID, Long> FLICKERING = new HashMap<>();

	private BlinkerPower() {
	}

	public static void init() {
		Powers.register(INSTANCE);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
			!untouchable(entity) || source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forget(handler.player));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			UNTOUCHED.clear();
			DROPS.clear();
			FLICKERING.clear();
		});
	}

	private static void forget(ServerPlayer player) {
		UNTOUCHED.remove(player.getUUID());
		DROPS.remove(player.getUUID());
		FLICKERING.remove(player.getUUID());
	}

	/** In the instant between here and there: nothing touches them. */
	static boolean untouchable(net.minecraft.world.entity.Entity e) {
		Long until = UNTOUCHED.get(e.getUUID());
		return until != null && e.level().getGameTime() <= until;
	}

	@Override
	public Identifier id() {
		return ID;
	}

	@Override
	public int color() {
		return COLOR;
	}

	@Override
	public ItemStack icon() {
		return new ItemStack(Items.ENDER_PEARL);
	}

	@Override
	public PowerKey starterKey() {
		return PowerKey.ABILITY_1;
	}

	@Override
	public PowerKey[] unlockOrder() {
		return new PowerKey[] {PowerKey.ABILITY_1, PowerKey.ABILITY_2, PowerKey.ABILITY_3, PowerKey.ABILITY_4};
	}

	@Override
	public boolean slotUnlocked(net.minecraft.world.entity.player.Player player, String slot) {
		return false;
	}

	@Override
	public void onLost(ServerPlayer player) {
		forget(player);
	}

	@Override
	public void onDowned(ServerPlayer player) {
		DROPS.remove(player.getUUID());
		FLICKERING.remove(player.getUUID());
	}

	private static boolean ready(ServerPlayer player, int slot) {
		if (PickKit.ready(TABLE, player, slot) || player.getAbilities().instabuild) {
			return true;
		}
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.blinker.wait", (PickKit.left(TABLE, player, slot) + 19) / 20)
			.withStyle(ChatFormatting.DARK_PURPLE));
		return false;
	}

	@Override
	public void onKey(ServerPlayer player, PowerKey key, boolean down) {
		if (!down) {
			return;
		}
		switch (key) {
			case ABILITY_1 -> blink(player, com.jaymospowers.core.Mastery.starterOnly(player));
			case ABILITY_2 -> strike(player);
			case ABILITY_3 -> vanish(player);
			case ABILITY_4 -> drop(player);
		}
	}

	@Override
	public void onUltimate(ServerPlayer player, boolean down) {
		if (down && ready(player, FLICKER)) {
			PickKit.spend(TABLE, player, FLICKER, FLICKER_COOLDOWN);
			long until = player.level().getGameTime() + FLICKER_TICKS;
			FLICKERING.put(player.getUUID(), until);
			UNTOUCHED.put(player.getUUID(), until);
		}
	}

	// ---- the step ----

	/** Steps {@code e} to {@code to} (a spot it fits), with the flash at both ends. */
	static void step(ServerLevel level, LivingEntity e, Vec3 to) {
		Vec3 from = e.position();
		puff(level, from, e.getBbHeight());
		if (e.isPassenger()) {
			e.stopRiding();
		}
		if (e instanceof ServerPlayer p) {
			p.teleportTo(level, to.x, to.y, to.z, Set.of(), p.getYRot(), p.getXRot(), false);
		} else {
			e.teleportTo(to.x, to.y, to.z);
		}
		e.resetFallDistance();
		puff(level, to, e.getBbHeight());
		level.playSound(null, to.x, to.y, to.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.7F, 1.4F);
	}

	private static void puff(ServerLevel level, Vec3 at, float height) {
		level.sendParticles(MagicFx.wisp(COLOR, 1.0F), at.x, at.y + height * 0.5, at.z, 14, 0.25, height * 0.35, 0.25, 0.05);
		level.sendParticles(MagicFx.spark(FLASH, 0.6F), at.x, at.y + height * 0.5, at.z, 4, 0.2, height * 0.3, 0.2, 0.02);
	}

	/** The nearest spot {@code e} fits at {@code at}: there, a little above or below. */
	static @Nullable Vec3 fit(ServerLevel level, LivingEntity e, Vec3 at) {
		for (double dy : new double[] {0.0, 0.5, 1.0, -0.5, 1.5, -1.0, 2.0}) {
			Vec3 spot = at.add(0.0, dy, 0.0);
			if (level.noCollision(e, e.getDimensions(e.getPose()).makeBoundingBox(spot))) {
				return spot;
			}
		}
		return null;
	}

	/** R: along the look, short of any wall, untouchable a moment. */
	static void blink(ServerPlayer player, boolean starter) {
		if (!ready(player, BLINK)) {
			return;
		}
		ServerLevel level = player.level();
		double range = starter ? BLINK_RANGE * 0.5 : BLINK_RANGE;
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		BlockHitResult wall = level.clip(new ClipContext(eye, eye.add(look.scale(range)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		double reach = wall.getType() == HitResult.Type.MISS ? range : Math.max(0.0, wall.getLocation().distanceTo(eye) - 0.8);
		Vec3 to = null;
		for (double d = reach; d >= 1.0 && to == null; d -= 0.5) {
			to = fit(level, player, player.position().add(look.scale(d)));
		}
		if (to == null) {
			return;
		}
		PickKit.spend(TABLE, player, BLINK, BLINK_COOLDOWN);
		UNTOUCHED.put(player.getUUID(), level.getGameTime() + UNTOUCHABLE);
		Vec3 keep = player.getDeltaMovement();
		step(level, player, to);
		player.setDeltaMovement(keep.multiply(1.0, 0.0, 1.0));
		player.syncVelocity = true;
	}

	/** G: behind the foe looked at, and a blow. */
	static void strike(ServerPlayer player) {
		LivingEntity foe = PickKit.aimed(player, STRIKE_RANGE);
		if (foe == null) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.blinker.no_foe").withStyle(ChatFormatting.GRAY));
			return;
		}
		if (!ready(player, STRIKE)) {
			return;
		}
		ServerLevel level = player.level();
		Vec3 behind = foe.position().subtract(player.position()).multiply(1.0, 0.0, 1.0);
		behind = behind.lengthSqr() < 1.0E-4 ? player.getLookAngle().multiply(1.0, 0.0, 1.0) : behind.normalize();
		Vec3 to = fit(level, player, foe.position().add(behind.scale(foe.getBbWidth() * 0.5 + 0.8)));
		if (to == null) {
			to = fit(level, player, foe.position().subtract(behind.scale(foe.getBbWidth() * 0.5 + 0.8)));
		}
		if (to == null) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.blinker.no_room").withStyle(ChatFormatting.GRAY));
			return;
		}
		PickKit.spend(TABLE, player, STRIKE, STRIKE_COOLDOWN);
		UNTOUCHED.put(player.getUUID(), level.getGameTime() + UNTOUCHABLE);
		step(level, player, to);
		face(player, foe);
		hit(level, player, foe, STRIKE_SHARE, STRIKE_PLUS);
	}

	private static void face(ServerPlayer player, LivingEntity foe) {
		Vec3 d = foe.getEyePosition().subtract(player.getEyePosition());
		float yaw = (float)(Math.atan2(d.z, d.x) * (180.0 / Math.PI)) - 90.0F;
		float pitch = (float)(-Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * (180.0 / Math.PI));
		player.teleportTo(player.level(), player.getX(), player.getY(), player.getZ(), Set.of(), yaw, pitch, false);
	}

	private static void hit(ServerLevel level, ServerPlayer player, LivingEntity foe, float share, float plus) {
		float harm = (float)player.getAttributeValue(Attributes.ATTACK_DAMAGE) * share + plus;
		foe.setInvulnerableTime(0);
		foe.hurtServer(level, player.damageSources().playerAttack(player), harm);
		player.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		Vec3 c = foe.getBoundingBox().getCenter();
		MagicFx.stroke(level, c.add(-0.6, 0.5, 0.0), c.add(0.6, -0.5, 0.0), 0.1F, FLASH);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.9F, 1.4F);
	}

	/** V: blinding smoke, and gone a few steps back. */
	static void vanish(ServerPlayer player) {
		if (!ready(player, VANISH)) {
			return;
		}
		ServerLevel level = player.level();
		PickKit.spend(TABLE, player, VANISH, VANISH_COOLDOWN);
		Vec3 at = player.position();
		level.sendParticles(MagicFx.miasma(SMOKE, 2.0F), at.x, at.y + 1.0, at.z, 60, VANISH_RADIUS * 0.4, 1.0, VANISH_RADIUS * 0.4, 0.02);
		MagicFx.blast(level, at.add(0.0, 1.0, 0.0), (float)VANISH_RADIUS * 0.6F, SMOKE);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 1.0F, 0.8F);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(VANISH_RADIUS), e -> e != player && e.isAlive())) {
			e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, VANISH_BLIND, 0), player);
			if (e instanceof Mob mob && mob.getTarget() == player) {
				mob.setTarget(null);
				mob.getNavigation().stop();
			}
		}
		player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, VANISH_UNSEEN, 0, false, false));
		Vec3 back = player.getLookAngle().multiply(-1.0, 0.0, -1.0);
		back = back.lengthSqr() < 1.0E-4 ? Vec3.ZERO : back.normalize();
		for (double d = VANISH_BACK; d >= 1.0; d -= 0.5) {
			Vec3 to = fit(level, player, at.add(back.scale(d)));
			if (to != null && level.clip(new ClipContext(player.getEyePosition(), to.add(0.0, player.getEyeHeight(), 0.0), ClipContext.Block.COLLIDER,
				ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.MISS) {
				UNTOUCHED.put(player.getUUID(), level.getGameTime() + UNTOUCHABLE);
				step(level, player, to);
				break;
			}
		}
	}

	/** B: up with a foe, and let go. */
	static void drop(ServerPlayer player) {
		LivingEntity foe = PickKit.aimed(player, DROP_REACH);
		if (foe == null) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.blinker.no_foe").withStyle(ChatFormatting.GRAY));
			return;
		}
		if (!foe.canUsePortal(false)) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.blinker.too_much").withStyle(ChatFormatting.GRAY));
			return;
		}
		if (!ready(player, DROP)) {
			return;
		}
		ServerLevel level = player.level();
		Vec3 foeUp = foe.position().add(0.0, DROP_HEIGHT, 0.0);
		Vec3 apart = player.position().subtract(foe.position()).multiply(1.0, 0.0, 1.0);
		apart = apart.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : apart.normalize();
		Vec3 meUp = foeUp.add(apart.scale(foe.getBbWidth() * 0.5 + 0.8));
		if (!level.noCollision(foe, foe.getDimensions(foe.getPose()).makeBoundingBox(foeUp))
			|| !level.noCollision(player, player.getDimensions(player.getPose()).makeBoundingBox(meUp))) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.blinker.no_sky").withStyle(ChatFormatting.GRAY));
			return;
		}
		PickKit.spend(TABLE, player, DROP, DROP_COOLDOWN);
		Drop d = new Drop();
		d.foe = foe;
		d.home = player.position();
		d.at = level.getGameTime();
		DROPS.put(player.getUUID(), d);
		UNTOUCHED.put(player.getUUID(), level.getGameTime() + DROP_HANG + UNTOUCHABLE);
		step(level, foe, foeUp);
		step(level, player, meUp);
		foe.setDeltaMovement(Vec3.ZERO);
		foe.syncVelocity = true;
	}

	@Override
	public void tick(ServerPlayer player) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		Drop d = DROPS.get(player.getUUID());
		if (d != null) {
			if (now - d.at < DROP_HANG && d.foe.isAlive()) {
				// held up there a moment, then let go
				d.foe.setDeltaMovement(Vec3.ZERO);
				d.foe.syncVelocity = true;
				player.setDeltaMovement(Vec3.ZERO);
				player.syncVelocity = true;
				player.resetFallDistance();
			} else {
				DROPS.remove(player.getUUID());
				Vec3 home = fit(level, player, d.home);
				if (home != null && player.isAlive()) {
					UNTOUCHED.put(player.getUUID(), now + UNTOUCHABLE);
					step(level, player, home);
				}
			}
		}
		Long flicker = FLICKERING.get(player.getUUID());
		if (flicker != null) {
			if (now > flicker || !player.isAlive()) {
				FLICKERING.remove(player.getUUID());
			} else if (now % FLICKER_EVERY == 0) {
				flick(level, player);
			}
		}
	}

	/** One flicker: beside a foe round you, and a blow. */
	private static void flick(ServerLevel level, ServerPlayer player) {
		List<LivingEntity> foes = new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(FLICKER_RANGE),
			e -> PickKit.foe(player, e) && e.distanceTo(player) <= FLICKER_RANGE));
		if (foes.isEmpty()) {
			return;
		}
		LivingEntity foe = foes.get(level.getRandom().nextInt(foes.size()));
		double angle = level.getRandom().nextDouble() * Math.PI * 2.0;
		Vec3 side = new Vec3(Math.cos(angle), 0.0, Math.sin(angle)).scale(foe.getBbWidth() * 0.5 + 0.8);
		Vec3 to = fit(level, player, foe.position().add(side));
		if (to == null) {
			return;
		}
		step(level, player, to);
		face(player, foe);
		hit(level, player, foe, FLICKER_SHARE, 0.0F);
	}
}
