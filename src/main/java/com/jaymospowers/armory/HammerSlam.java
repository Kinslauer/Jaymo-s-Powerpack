package com.jaymospowers.armory;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.UltimateFilm;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * (0.17.94) Balkon's warhammer smash (hold use to charge, let go) is swung down from overhead. Andy: "warhammer
 * rightclick should animate swinging down from overhead like the better combat swing".
 * <ul>
 * <li>While charging, the hammer is held up over your head (the trident's raised arm, not the bow's draw).</li>
 * <li>Let go with a full charge and Better Combat's own two-handed slam plays (the same swing as the warhammer's
 * left-click), for you in first and third person and for everyone who can see you. The smash itself (WeaponMod's
 * blast, knockback, cooldown) lands when the hammer comes down, {@link #IMPACT} ticks later, not on the release.</li>
 * <li>Andy: "it should gain the parry while on cooldown." While the smash is cooling down, right click raises the
 * weapon guard instead ({@code combat/Guard}: block, parry windows, feathers), drawn as a guard, and letting go does
 * nothing. Which one a raise is gets decided by the server as it starts ({@link #GUARD}, synced to everyone), so
 * the cooldown running out mid-guard doesn't turn it into a smash.</li>
 * </ul>
 * Without Better Combat the smash is WeaponMod's own, at once. Players only: a mob's smash is left as it was.
 * Hooked in by mixin/WeaponModWarhammerMixin (the smash), WeaponModMeleeUseMixin (use, pose, release),
 * HammerChargeBlockMixin (no block while charging) and client HammerPoseMixin/GuardFirstPersonMixin (the poses).
 */
public final class HammerSlam {
	/** Better Combat's swing, the one its hammer preset (and so the warhammer's left-click) uses. */
	public static final String ANIMATION = "bettercombat:two_handed_slam";
	/** How long the slam lasts (ticks) and how far through it the head comes down. */
	public static final float LENGTH = 14.0F, UPSWING = 0.5F;
	/** Ticks from letting go to the smash: the downswing's hit. */
	public static final int IMPACT = Math.round(LENGTH * UPSWING);
	/** Played on release, before the blast's own boom. */
	private static final Identifier WHOOSH = Identifier.fromNamespaceAndPath("bettercombat", "hammer_slam");

	/** WeaponMod's smash cooldown (ticks from the last smash), as its MeleeCompWarhammer counts it. */
	public static final int SMASH_COOLDOWN = 400;

	/** Whether this player's current (or last) raise of a warhammer is the guard rather than the smash's charge. */
	public static final AttachmentType<Boolean> GUARD = AttachmentRegistry.create(JaymosPowers.id("hammer_guard"),
		builder -> builder.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));

	private static final @Nullable MethodHandle LAST_SMASH = lastSmash();

	private static final boolean BETTER_COMBAT = FabricLoader.getInstance().isModLoaded("bettercombat")
		&& FabricLoader.getInstance().isModLoaded("player_animation_library");

	/** Plays the slam on the local player (set by the client; Better Combat never echoes your own swing back to you). */
	public static @Nullable Consumer<Player> localSwing;

	private HammerSlam() {
	}

	public static boolean isWarhammer(Item item) {
		Identifier id = BuiltInRegistries.ITEM.getKey(item);
		return id.getNamespace().equals("weaponmod") && id.getPath().startsWith("warhammer");
	}

	/** The charge: held overhead. */
	public static ItemUseAnimation chargePose() {
		return ItemUseAnimation.TRIDENT;
	}

	/** {@code entity} has a warhammer raised as a guard (the smash cooling down when it went up). */
	public static boolean guarding(LivingEntity entity) {
		return raised(entity) && entity.getAttachedOrElse(GUARD, false);
	}

	/** {@code entity} is charging a warhammer smash: held up, not a guard. */
	public static boolean charging(LivingEntity entity) {
		return raised(entity) && !entity.getAttachedOrElse(GUARD, false);
	}

	private static boolean raised(LivingEntity entity) {
		return entity instanceof Player && entity.isUsingItem() && isWarhammer(entity.getUseItem().getItem());
	}

	/** The pose a warhammer held in use is drawn with, for {@code player}. */
	public static ItemUseAnimation pose(LivingEntity player) {
		return guarding(player) ? ItemUseAnimation.BLOCK : chargePose();
	}

	/**
	 * Right click with a warhammer (both sides; replaces WeaponMod's, which did nothing while the smash cooled down): it
	 * always goes up, the server deciding whether as the smash's charge or as a guard.
	 */
	public static InteractionResult use(Player player, InteractionHand hand) {
		if (!player.level().isClientSide()) {
			player.setAttached(GUARD, !smashReady(player));
		}
		player.startUsingItem(hand);
		return InteractionResult.CONSUME;
	}

	/** Letting go of a guard: nothing happens (WeaponMod would have smashed after a long enough hold). */
	public static boolean releasesAsGuard(LivingEntity entity) {
		return entity instanceof Player && entity.getAttachedOrElse(GUARD, false);
	}

	/** Server: WeaponMod's own test, as MeleeCompWarhammer.isCharged counts it (its last smash is on the player). */
	private static boolean smashReady(Player player) {
		if (LAST_SMASH == null) {
			return true;
		}
		try {
			return (int)LAST_SMASH.invokeExact(player) + SMASH_COOLDOWN - player.tickCount <= 0;
		} catch (Throwable e) {
			return true;
		}
	}

	private static @Nullable MethodHandle lastSmash() {
		try {
			Class<?> data = Class.forName("ckathode.weaponmod.PlayerWeaponData");
			return MethodHandles.publicLookup().findStatic(data, "getLastWarhammerSmashTicks", MethodType.methodType(int.class, Player.class));
		} catch (ReflectiveOperationException | LinkageError e) {
			return null;
		}
	}

	/**
	 * WeaponMod's smash is about to go off on {@code entity}. Returns true if it's taken over here: the slam swings, and
	 * {@code smash} (WeaponMod's own) runs on the server when the hammer lands.
	 */
	public static boolean slam(ItemStack stack, Level level, LivingEntity entity, Runnable smash) {
		if (!BETTER_COMBAT || !(entity instanceof Player player)) {
			return false;
		}
		if (level.isClientSide()) {
			Consumer<Player> swing = localSwing;
			if (swing != null) {
				swing.accept(player);
			}
			return true;
		}
		if (!(player instanceof ServerPlayer server)) {
			return false;
		}
		HammerSlamBc.broadcast(server);
		BuiltInRegistries.SOUND_EVENT.getOptional(WHOOSH).ifPresent(sound ->
			level.playSound(null, player.getX(), player.getY(), player.getZ(), sound, player.getSoundSource(), 1.0F, 0.9F));
		UltimateFilm.later(server, IMPACT, p -> {
			// still there, still holding the hammer that was swung
			if (p.isAlive() && p.level() == level && p.getMainHandItem() == stack && !stack.isEmpty()) {
				smash.run();
			}
		});
		return true;
	}
}
