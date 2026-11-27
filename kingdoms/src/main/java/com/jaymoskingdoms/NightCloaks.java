package com.jaymoskingdoms;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * The night-cloak (Night's Blood, patch 6), a deep-hooded cloak that only a tailor sells (Jaymo's Powers' {@code night_cloak},
 * sewn through {@link TailorHats}; Powers draws the cloak and keeps the hood up or down, {@code HoodedCloak}). Read here by id
 * and by Powers' own {@code HoodedCloak.up}, so Kingdoms never needs Powers. With the hood up:
 * <ul>
 * <li>The noon sun stings instead of burning ({@link #sting}): Weakness and wisps of smoke, no fire.</li>
 * <li>Folk see a hooded stranger rather than the ashen face: the vampire's looks are not drawn
 * ({@code VampireLooks}), and a witness who suspects them names them "a hooded figure" ({@link Suspicion}).</li>
 * </ul>
 * Holy dread ({@link HolyDread}) still finds them; the cloak does nothing against it.
 */
public final class NightCloaks {
	/** Powers' item id. */
	static final Identifier ID = Identifier.fromNamespaceAndPath("jaymospowers", "night_cloak");
	/** The sun's sting: Weakness level (amplifier) and how long (ticks), the smoke's puffs a second. */
	static final int STING_WEAKNESS = 1, STING_TICKS = 40, STING_SMOKE = 3;

	private static boolean looked;
	private static @Nullable MethodHandle up;

	private NightCloaks() {
	}

	/** Whether {@code e} has a night-cloak on its head (either side). */
	public static boolean wears(Entity e) {
		return e instanceof LivingEntity living && BuiltInRegistries.ITEM.getKey(living.getItemBySlot(EquipmentSlot.HEAD).getItem()).equals(ID);
	}

	/** Whether {@code e} wears a night-cloak with its hood up (either side). */
	public static boolean up(Entity e) {
		if (!(e instanceof Player player) || !wears(e)) {
			return false;
		}
		if (!looked) {
			looked = true;
			if (FabricLoader.getInstance().isModLoaded("jaymospowers")) {
				try {
					up = MethodHandles.publicLookup().findStatic(Class.forName("com.jaymospowers.costume.HoodedCloak"), "up",
						MethodType.methodType(boolean.class, Player.class));
				} catch (ReflectiveOperationException | LinkageError ex) {
					JaymosKingdoms.LOGGER.warn("Couldn't find Jaymo's Powers' hooded cloak", ex);
				}
			}
		}
		if (up == null) {
			return true;
		}
		try {
			return (boolean) up.invoke(player);
		} catch (Throwable t) {
			return true;
		}
	}

	/** The noon sun on a vampire in a hood: it stings. Called each second in the sun. */
	static void sting(ServerPlayer player) {
		player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, STING_TICKS, STING_WEAKNESS, true, false, true));
		ServerLevel level = player.level();
		level.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE, player.getX(), player.getY(0.7), player.getZ(), STING_SMOKE, 0.25, 0.4, 0.25, 0.01);
	}

	/** What a witness calls a hooded vampire. */
	static Component name() {
		return Component.translatable("identity.jaymoskingdoms.hooded");
	}
}
