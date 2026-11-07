package com.jaymospowers.compat;

import java.lang.reflect.Method;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

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

	private KingdomsCompat() {
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

	private static Method hunt;
	private static boolean huntLooked;

	/**
	 * (0.21.53) A great white shark is drawn up from the deep near {@code (x, y, z)} (Kingdoms' whaling sharks, see
	 * {@code com.jaymoskingdoms.whaling.Sharks#hunt}): false without Kingdoms, without Naturalist's shark, or when
	 * there is no water deep enough there. Like everything else here, reached by reflection and fail-soft.
	 */
	public static boolean sharkDrawnIn(net.minecraft.server.level.ServerLevel level, double x, double y, double z) {
		if (!LOADED) {
			return false;
		}
		if (!huntLooked) {
			huntLooked = true;
			try {
				hunt = Class.forName("com.jaymoskingdoms.whaling.Sharks")
						.getMethod("hunt", net.minecraft.server.level.ServerLevel.class,
								net.minecraft.world.phys.Vec3.class, net.minecraft.world.entity.LivingEntity.class);
			} catch (ReflectiveOperationException | LinkageError e) {
				hunt = null;
			}
		}
		if (hunt == null) {
			return false;
		}
		try {
			return Boolean.TRUE.equals(hunt.invoke(null, level, new net.minecraft.world.phys.Vec3(x, y, z), null));
		} catch (ReflectiveOperationException | RuntimeException e) {
			return false;
		}
	}
}
