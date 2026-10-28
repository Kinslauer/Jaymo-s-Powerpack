package com.jaymospowers.armory;

import com.jaymospowers.core.UltimateFilm;
import java.util.function.Consumer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * (0.17.93) Balkon's warhammer smash (hold use to charge, let go) is swung down from overhead. Andy: "warhammer
 * rightclick should animate swinging down from overhead like the better combat swing".
 * <ul>
 * <li>While charging, the hammer is held up over your head (the trident's raised arm, not the bow's draw).</li>
 * <li>Let go with a full charge and Better Combat's own two-handed slam plays (the same swing as the warhammer's
 * left-click), for you in first and third person and for everyone who can see you. The smash itself (WeaponMod's
 * blast, knockback, cooldown) lands when the hammer comes down, {@link #IMPACT} ticks later, not on the release.</li>
 * </ul>
 * Without Better Combat the smash is WeaponMod's own, at once. Players only: a mob's smash is left as it was.
 * Hooked in by mixin/WeaponModWarhammerMixin (the smash) and WeaponModMeleeUseMixin (the charge pose).
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
