package com.jaymospowers.compat;

import java.lang.reflect.Method;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * (0.20.9) Jaymo's Kingdoms is optional for Powers: this is the only place that touches it, by reflection, and only when it is
 * loaded. A missing jar (or a changed method) answers "no" instead of crashing.
 */
public final class KingdomsCompat {
	private static final boolean LOADED = FabricLoader.getInstance().isModLoaded("jaymoskingdoms");
	private static Method boss;
	private static boolean looked;
	private static Method blessing;
	private static boolean blessingLooked;
	private static Method grabbed;
	private static boolean grabbedLooked;
	private static Method fished;
	private static boolean fishedLooked;
	private static Method trainMight;
	private static boolean trainMightLooked;

	private static Method formsKnown, formsPerk;
	private static boolean formsLooked;

	private KingdomsCompat() {
	}

	/**
	 * Whether {@code player} has Weapon Forms: a form learnt from any teacher (Kingdoms' {@code forms.Forms}), or the
	 * Strength perk that opens every weapon's full combo ({@code WeaponForms}). Both are synced to their owner, so this
	 * answers alike on the client (for yourself) and the server. True without Kingdoms: there is nothing to learn it from.
	 */
	public static boolean knowsForms(Player player) {
		if (!LOADED || player == null) {
			return true;
		}
		if (!formsLooked) {
			formsLooked = true;
			try {
				formsKnown = Class.forName("com.jaymoskingdoms.forms.Forms").getMethod("known", Player.class);
			} catch (ReflectiveOperationException | LinkageError e) {
				formsKnown = null;
			}
			try {
				formsPerk = Class.forName("com.jaymoskingdoms.WeaponForms").getMethod("knows", Player.class);
			} catch (ReflectiveOperationException | LinkageError e) {
				formsPerk = null;
			}
		}
		try {
			if (formsKnown != null && formsKnown.invoke(null, player) instanceof java.util.Collection<?> known && !known.isEmpty()) {
				return true;
			}
			return formsPerk != null && Boolean.TRUE.equals(formsPerk.invoke(null, player));
		} catch (ReflectiveOperationException | RuntimeException e) {
			return false;
		}
	}

	/** Whether Kingdoms calls {@code entity} a boss (one with a boss bar: a king, a lord, a warlord, an Aether Giant, a wild dragon). */
	public static boolean boss(Entity entity) {
		if (!LOADED) {
			return false;
		}
		if (!looked) {
			looked = true;
			try {
				boss = Class.forName("com.jaymoskingdoms.BossBars").getMethod("boss", Entity.class);
			} catch (ReflectiveOperationException | LinkageError e) {
				boss = null;
			}
		}
		if (boss == null) {
			return false;
		}
		try {
			return Boolean.TRUE.equals(boss.invoke(null, entity));
		} catch (ReflectiveOperationException | RuntimeException e) {
			return false;
		}
	}

	/** (0.20.18) {@code player}'s sworn value for a Kingdoms key (negative: turned from that patron), 0 when unknown or Kingdoms is absent. */
	public static float blessing(Player player, String key) {
		if (!LOADED || player == null) {
			return 0.0F;
		}
		if (!blessingLooked) {
			blessingLooked = true;
			try {
				blessing = Class.forName("com.jaymoskingdoms.Patrons").getMethod("blessing", Player.class, String.class);
			} catch (ReflectiveOperationException | LinkageError e) {
				blessing = null;
			}
		}
		if (blessing == null) {
			return 0.0F;
		}
		try {
			return blessing.invoke(null, player, key) instanceof Number n ? n.floatValue() : 0.0F;
		} catch (ReflectiveOperationException | RuntimeException e) {
			return 0.0F;
		}
	}

	/**
	 * (0.21.33) Whether a vampire holds {@code entity} up by the throat now (Kingdoms' {@code BloodGrab}), on the client or the
	 * server: a held body that goes down stays in the hand, not laid on the ground ({@code DownedPose}). False without Kingdoms.
	 */
	public static boolean grabbed(Entity entity) {
		if (!LOADED || entity == null) {
			return false;
		}
		if (!grabbedLooked) {
			grabbedLooked = true;
			try {
				grabbed = Class.forName("com.jaymoskingdoms.BloodGrab").getMethod("grabbed", Entity.class);
			} catch (ReflectiveOperationException | LinkageError e) {
				grabbed = null;
			}
		}
		if (grabbed == null) {
			return false;
		}
		try {
			return Boolean.TRUE.equals(grabbed.invoke(null, entity));
		} catch (ReflectiveOperationException | RuntimeException e) {
			return false;
		}
	}

	/**
	 * (0.21.53) A great white shark (the Naturalist entity Kingdoms' whaling sharks use, see {@code Sharks}), brought up at
	 * {@code (x, y, z)} to be fought on the line; null without Kingdoms, without the shark, or in shallow water.
	 */
	public static @Nullable LivingEntity sharkDrawnIn(net.minecraft.server.level.ServerLevel level, double x, double y, double z) {
		if (!LOADED) {
			return null;
		}
		net.minecraft.world.entity.EntityType<?> sharkType =
				net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
						.getOptional(net.minecraft.resources.Identifier.fromNamespaceAndPath("naturalist", "great_white_shark"))
						.orElse(null);
		if (sharkType == null) {
			return null;
		}
		// Needs water deep enough for a great white: the hook block and two below must be water.
		net.minecraft.core.BlockPos pos = net.minecraft.core.BlockPos.containing(x, y, z);
		if (!level.getFluidState(pos).is(net.minecraft.tags.FluidTags.WATER)
				|| !level.getFluidState(pos.below()).is(net.minecraft.tags.FluidTags.WATER)
				|| !level.getFluidState(pos.below().below()).is(net.minecraft.tags.FluidTags.WATER)) {
			return null;
		}
		net.minecraft.world.entity.Entity shark = sharkType.spawn(level, pos, net.minecraft.world.entity.EntitySpawnReason.EVENT);
		if (!(shark instanceof LivingEntity living)) {
			return null;
		}
		// held on the line, not hunting: no AI, no gravity, and no one else can kill it
		living.setPermanentlyInvulnerable(true);
		living.setNoGravity(true);
		if (living instanceof net.minecraft.world.entity.Mob mob) {
			mob.setNoAi(true);
			mob.setPersistenceRequired();
		}
		return living;
	}

	/**
	 * (0.21.61, Andy: "tideborn will see fishing as an attack, and doing in their havens is seen as a crime") A fish took
	 * {@code player}'s hook at {@code hook}: Kingdoms' {@code TideFishing} has any Tideborn who see it fight them, and calls it
	 * poaching in a Tideborn haven. Nothing without Kingdoms.
	 */
	public static void fished(net.minecraft.server.level.ServerPlayer player, net.minecraft.core.BlockPos hook) {
		if (!LOADED || player == null) {
			return;
		}
		if (!fishedLooked) {
			fishedLooked = true;
			try {
				fished = Class.forName("com.jaymoskingdoms.TideFishing").getMethod("fished",
					net.minecraft.server.level.ServerPlayer.class, net.minecraft.core.BlockPos.class);
			} catch (ReflectiveOperationException | LinkageError e) {
				fished = null;
			}
		}
		if (fished == null) {
			return;
		}
		try {
			fished.invoke(null, player, hook);
		} catch (ReflectiveOperationException | RuntimeException e) {
			// (an older Kingdoms, or one that threw: the fishing goes on)
		}
	}

	/**
	 * {@code points} of Might trained by {@code player} (shadowboxing in Training Weights, {@code core.Training}): Kingdoms'
	 * {@code Stats.trainMight} keeps them toward its Might attribute. Nothing without Kingdoms.
	 */
	public static void trainMight(net.minecraft.server.level.ServerPlayer player, double points) {
		if (!LOADED || player == null) {
			return;
		}
		if (!trainMightLooked) {
			trainMightLooked = true;
			try {
				trainMight = Class.forName("com.jaymoskingdoms.Stats").getMethod("trainMight", net.minecraft.server.level.ServerPlayer.class, double.class);
			} catch (ReflectiveOperationException | LinkageError e) {
				trainMight = null;
			}
		}
		if (trainMight == null) {
			return;
		}
		try {
			trainMight.invoke(null, player, points);
		} catch (ReflectiveOperationException | RuntimeException e) {
			// (an older Kingdoms, or one that threw: the shadowboxing goes on)
		}
	}

	private static Method temperOf, temperKey;
	private static boolean temperLooked;

	/**
	 * {@code entity}'s temperament key in Kingdoms ("timid", "brave", "hotheaded"...), or null without Kingdoms, for a
	 * creature that has none, or if the call fails. Kingdoms draws one for a humanoid of its own that has none yet.
	 */
	public static @Nullable String temperament(Entity entity) {
		if (!LOADED) {
			return null;
		}
		if (!temperLooked) {
			temperLooked = true;
			try {
				temperOf = Class.forName("com.jaymoskingdoms.Temperaments").getMethod("of", Entity.class);
				temperKey = Class.forName("com.jaymoskingdoms.Temperaments$Temperament").getMethod("key");
			} catch (ReflectiveOperationException | LinkageError e) {
				temperOf = temperKey = null;
			}
		}
		if (temperOf == null || temperKey == null) {
			return null;
		}
		try {
			Object t = temperOf.invoke(null, entity);
			return t == null ? null : (String)temperKey.invoke(t);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return null;
		}
	}
}
