package com.jaymospowers.power.arsenal;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.api.PowerKey;
import com.jaymospowers.core.Fate;
import com.jaymospowers.core.LockOn;
import com.jaymospowers.core.Mastery;
import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.power.arsenal.entity.RiftBlade;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * <b>Attendant Blades</b> (a short hold of Sneak + V, once V is yours; Sneak + X for the Void Arsenal, whose Sneak + V
 * is its gates): weapons come out of small rifts and stay,
 * hanging round their holder in a formation that follows them, instead of firing once as Barrage does. Two blades at
 * first, four once every ability is unlocked, six for the Armory and the Void Arsenal. Each goes home on its own timer
 * ({@link #BLADE_TICKS}), as everything the rift gives does. A tap of Sneak + V with the blades out changes the
 * formation ({@link #FAN}, {@link #HALO}, {@link #FLANK}).
 *
 * <p>What they do unasked: when their holder hits something, the next ready blade strikes the same thing (the lock-on
 * target comes first) and is back in its place {@link #RETURN_TICKS} later; and they go for whatever hurt their holder.
 * Until the power is mastered they also pick fights of their own with monsters close by.
 *
 * <p>The whole wing is one attachment ({@link #WING}, synced to everyone); the places come from one formula both sides
 * use ({@link #slot}). A blade on its way to a target is a {@link RiftBlade}; docked ones are drawn by
 * {@code ArsenalWingRenderer}.
 */
public final class ArsenalWing {
	// tuning (ticks)
	/** Sneak + V held this long calls the blades (or sends them home); let go sooner and the formation changes. */
	public static final int HOLD_TICKS = 8;
	public static final int BLADE_TICKS = 1200;
	/** From calling the blades until they can be called again. */
	public static final int COOLDOWN = 400;
	/** The blades come out one after another, this far apart; each takes {@link #EMERGE_TICKS} to slide out. */
	public static final int EMERGE_GAP = 6;
	public static final int EMERGE_TICKS = 9;
	/** A blade that struck is back in its place this much later. */
	public static final int RETURN_TICKS = 30;
	public static final float STRIKE_DAMAGE = 5.0F;
	/** Guarding: one blade this often at whatever hurt the holder in the last {@link #GRUDGE_TICKS}, within {@link #GUARD_RANGE}. */
	public static final int GUARD_GAP = 15;
	public static final int GRUDGE_TICKS = 100;
	public static final double GUARD_RANGE = 16.0;
	/** Unmastered: a blade flies at the nearest monster in {@link #WILD_RANGE} this often, asked or not. */
	public static final int WILD_GAP = 40;
	public static final double WILD_RANGE = 10.0;

	public static final int FAN = 0, HALO = 1, FLANK = 2, SHAPES = 3;

	/** One blade: what it is, when it goes home, and when it is back in its place ({@code 0}: it never left). */
	public record Blade(ItemStack item, long expires, long back) {
		public static final StreamCodec<RegistryFriendlyByteBuf, Blade> STREAM_CODEC = StreamCodec.composite(
			ItemStack.OPTIONAL_STREAM_CODEC, Blade::item,
			ByteBufCodecs.VAR_LONG, Blade::expires,
			ByteBufCodecs.VAR_LONG, Blade::back,
			Blade::new);
	}

	/** The blades round one holder: when they were called, the formation, and each blade in its place's order. */
	public record Wing(long start, int shape, List<Blade> blades) {
		public static final StreamCodec<RegistryFriendlyByteBuf, Wing> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_LONG, Wing::start,
			ByteBufCodecs.VAR_INT, Wing::shape,
			Blade.STREAM_CODEC.apply(ByteBufCodecs.list()), Wing::blades,
			Wing::new);

		/** How far out of its rift blade {@code i} is at game time {@code now}: 0 not yet, 1 hanging in its place. */
		public float out(int i, double now) {
			Blade blade = blades.get(i);
			double first = (now - start - i * EMERGE_GAP) / EMERGE_TICKS;
			double again = blade.back() == 0 ? 1.0 : (now - blade.back()) / EMERGE_TICKS + 1.0;
			return (float) Mth.clamp(Math.min(first, again), 0.0, 1.0);
		}
	}

	public static final AttachmentType<Wing> WING = AttachmentRegistry.create(JaymosPowers.id("arsenal_wing"),
		builder -> builder.syncWith(Wing.STREAM_CODEC, AttachmentSyncPredicate.all()));

	/** When Sneak + V went down ({@code -1}: this hold has done its work already). */
	private static final Map<UUID, Long> PRESSED = new HashMap<>();
	private static final Map<UUID, Long> READY = new HashMap<>();
	/** When the next unasked blade may go, and the swing the last one answered. */
	private static final Map<UUID, Long> NEXT = new HashMap<>();
	private static final Map<UUID, Integer> ANSWERED = new HashMap<>();

	private ArsenalWing() {
	}

	/** How many blades answer: two, four once every ability is unlocked, six for the Armory and the Void Arsenal. */
	public static int count(Player player) {
		if (Fate.isAscended(player) || Fate.isCorrupted(player)) {
			return 6;
		}
		return Mastery.isUnlocked(player, PowerKey.ABILITY_4) ? 4 : 2;
	}

	/** The wing's key went down or up. True when it was the wing's (Sneak held as it went down), so Barrage and the cannons leave it be. */
	static boolean key(ServerPlayer player, boolean down) {
		UUID id = player.getUUID();
		if (down) {
			if (!player.isShiftKeyDown() || Mastery.starterOnly(player) || !Mastery.isUnlocked(player, PowerKey.ABILITY_3)) {
				return false;
			}
			PRESSED.put(id, player.level().getGameTime());
			return true;
		}
		Long pressed = PRESSED.remove(id);
		if (pressed == null) {
			return false;
		}
		if (pressed >= 0) {
			// let go early: the next formation, or a word on how to call them
			Wing wing = player.getAttached(WING);
			if (wing == null) {
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.arsenal.wing_hold" + (Fate.isCorrupted(player) ? ".corrupted" : "")).withStyle(ChatFormatting.GRAY));
			} else {
				int shape = (wing.shape() + 1) % SHAPES;
				player.setAttached(WING, new Wing(wing.start(), shape, wing.blades()));
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.arsenal.wing_shape." + shape).withStyle(ChatFormatting.LIGHT_PURPLE));
				player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CHAIN_STEP, SoundSource.PLAYERS, 0.6F, 1.4F);
			}
		}
		return true;
	}

	private static void toggle(ServerPlayer player) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		Wing wing = player.getAttached(WING);
		if (wing != null) {
			dismiss(player, wing);
			return;
		}
		long ready = READY.getOrDefault(player.getUUID(), 0L);
		if (now < ready) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.arsenal.wing_recharging",
				String.format("%.1f", (ready - now) / 20.0)).withStyle(ChatFormatting.GRAY));
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.3F, 1.6F);
			return;
		}
		boolean corrupted = Fate.isCorrupted(player);
		List<Blade> blades = new ArrayList<>();
		int count = count(player);
		for (int i = 0; i < count; i++) {
			ItemStack item = ArsenalPool.randomBlade(player.getRandom());
			blades.add(new Blade(corrupted ? ArsenalVoid.voidTouched(item) : item, now + BLADE_TICKS + (long) i * EMERGE_GAP, 0));
		}
		player.setAttached(WING, new Wing(now, FAN, List.copyOf(blades)));
		READY.put(player.getUUID(), now + COOLDOWN);
		Mastery.used(player);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.PLAYERS, 0.9F, 1.1F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.5F, 0.6F);
	}

	private static void dismiss(ServerPlayer player, Wing wing) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		for (int i = 0; i < wing.blades().size(); i++) {
			if (wing.out(i, now) > 0.0F) {
				Summoned.dissolveEffects(level, slot(player, wing, i, now, 1.0F));
			}
		}
		player.removeAttached(WING);
	}

	/** Every tick for the holder: the hold on Sneak + V, blades going home, and the ones that strike unasked. */
	static void tick(ServerPlayer player) {
		UUID id = player.getUUID();
		ServerLevel level = player.level();
		long now = level.getGameTime();
		Long pressed = PRESSED.get(id);
		if (pressed != null && pressed >= 0 && now - pressed >= HOLD_TICKS) {
			PRESSED.put(id, -1L);
			if (player.isAlive() && !player.isSpectator()) {
				toggle(player);
			}
		}
		Wing wing = player.getAttached(WING);
		if (wing == null) {
			return;
		}
		if (!player.isAlive()) {
			dismiss(player, wing);
			return;
		}
		List<Blade> kept = new ArrayList<>();
		for (int i = 0; i < wing.blades().size(); i++) {
			Blade blade = wing.blades().get(i);
			if (now < blade.expires() || now < blade.back()) {
				kept.add(blade);
			} else {
				Summoned.dissolveEffects(level, slot(player, wing, i, now, 1.0F));
			}
		}
		if (kept.isEmpty()) {
			player.removeAttached(WING);
			return;
		}
		if (kept.size() != wing.blades().size()) {
			wing = new Wing(wing.start(), wing.shape(), List.copyOf(kept));
			player.setAttached(WING, wing);
		}

		// my swing: one blade for each hit I land, at my lock-on target if I have one
		LivingEntity struck = player.getLastHurtMob();
		int swing = player.getLastHurtMobTimestamp();
		if (struck != null && player.tickCount - swing <= 1 && ANSWERED.getOrDefault(id, -1) != swing) {
			ANSWERED.put(id, swing);
			LivingEntity locked = LockOn.target(player);
			strike(player, wing, locked != null ? locked : struck);
			return;
		}
		if (now < NEXT.getOrDefault(id, 0L)) {
			return;
		}
		LivingEntity foe = grudge(player);
		int gap = GUARD_GAP;
		if (foe == null && count(player) < 4) {
			foe = nearestMonster(player);
			gap = WILD_GAP;
		}
		if (foe != null && strike(player, wing, foe)) {
			NEXT.put(id, now + gap);
		}
	}

	/** Whatever hurt the holder lately and is still close. */
	private static @Nullable LivingEntity grudge(ServerPlayer player) {
		LivingEntity attacker = player.getLastHurtByMob();
		return attacker != null && player.tickCount - player.getLastHurtByMobTimestamp() < GRUDGE_TICKS
			&& attacker.distanceToSqr(player) < GUARD_RANGE * GUARD_RANGE && fair(player, attacker) ? attacker : null;
	}

	private static @Nullable LivingEntity nearestMonster(ServerPlayer player) {
		LivingEntity best = null;
		double bestDistance = WILD_RANGE * WILD_RANGE;
		for (LivingEntity living : player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(WILD_RANGE),
			e -> e instanceof Enemy && fair(player, e) && player.hasLineOfSight(e))) {
			double distance = living.distanceToSqr(player);
			if (distance < bestDistance) {
				bestDistance = distance;
				best = living;
			}
		}
		return best;
	}

	/** A thing the blades may strike: alive, in this world, not their holder and not the holder's own animal. */
	private static boolean fair(ServerPlayer player, LivingEntity target) {
		return target != player && target.isAlive() && target.level() == player.level()
			&& !(target instanceof OwnableEntity owned && owned.getOwner() == player);
	}

	/** The first blade hanging in its place flies at {@code target}. False when none is ready or the target isn't fair. */
	private static boolean strike(ServerPlayer player, Wing wing, LivingEntity target) {
		if (!fair(player, target)) {
			return false;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		for (int i = 0; i < wing.blades().size(); i++) {
			Blade blade = wing.blades().get(i);
			if (wing.out(i, now) < 1.0F || now + RETURN_TICKS >= blade.expires()) {
				continue;
			}
			boolean corrupted = Fate.isCorrupted(player);
			Vec3 from = slot(player, wing, i, now, 1.0F);
			Vec3 to = target.getBoundingBox().getCenter();
			RiftBlade flying = RiftBlade.create(level, player, blade.item(), from, to, target, 1,
				STRIKE_DAMAGE * (corrupted ? ArsenalVoid.DAMAGE_MULTIPLIER : 1.0F));
			// like one of the Hundred Blades: it lands whatever my own hit left of the target's guard, and is gone after
			flying.makeSkyBlade(corrupted);
			level.addFreshEntity(flying);
			MagicFx.stroke(level, from, to, 0.1F, corrupted ? 0x2A1840 : MagicFx.VOID_COLOR);
			level.playSound(null, from.x, from.y, from.z, SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, 0.5F, 1.6F);
			List<Blade> blades = new ArrayList<>(wing.blades());
			blades.set(i, new Blade(blade.item(), blade.expires(), now + RETURN_TICKS));
			player.setAttached(WING, new Wing(wing.start(), wing.shape(), List.copyOf(blades)));
			return true;
		}
		return false;
	}

	/**
	 * Where blade {@code i} hangs at game time {@code now}, turning with its holder's view: a fan over the shoulders,
	 * a slow ring at chest height, or a column down each side.
	 */
	public static Vec3 slot(Player player, Wing wing, int i, double now, float partialTick) {
		int n = wing.blades().size();
		float yaw = player.getViewYRot(partialTick) * Mth.DEG_TO_RAD;
		Vec3 forward = new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
		Vec3 right = new Vec3(-forward.z, 0.0, forward.x);
		double across, up, ahead;
		switch (wing.shape()) {
			case HALO -> {
				double angle = now * 0.04 + i * Mth.TWO_PI / n;
				across = Math.cos(angle) * 1.5;
				ahead = Math.sin(angle) * 1.5;
				up = 1.3;
			}
			case FLANK -> {
				int row = i / 2;
				across = (i % 2 == 0 ? -1.1 : 1.1) - (i % 2 == 0 ? 1 : -1) * row * 0.15;
				ahead = 0.3 - row * 0.6;
				up = 1.1 + row * 0.35;
			}
			default -> {
				double t = n == 1 ? 0.0 : i / (n - 1.0) * 2.0 - 1.0;
				across = t * (0.6 + 0.2 * n);
				ahead = -0.8 + 0.25 * Math.abs(t);
				up = 2.2 - 0.55 * Math.abs(t);
			}
		}
		up += Math.sin(now * 0.08 + i * 1.7) * 0.06;
		return player.getPosition(partialTick).add(right.scale(across)).add(forward.scale(ahead)).add(0.0, up, 0.0);
	}

	/** The power went away, or its holder left: the blades with it. */
	static void onLost(ServerPlayer player) {
		UUID id = player.getUUID();
		PRESSED.remove(id);
		READY.remove(id);
		NEXT.remove(id);
		ANSWERED.remove(id);
		player.removeAttached(WING);
	}
}
