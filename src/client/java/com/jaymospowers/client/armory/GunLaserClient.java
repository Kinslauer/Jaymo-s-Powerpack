package com.jaymospowers.client.armory;

import com.jaymospowers.armory.GunLasers;
import com.jaymospowers.armory.TaczGuns;
import com.jaymospowers.client.JaymosPowersClient;
import com.jaymospowers.core.fx.MagicFx;
import com.mojang.blaze3d.platform.InputConstants;
import java.lang.reflect.Method;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Your side of the laser sights ({@link GunLasers}, row ad_laser): the key that switches the laser on the gun in your hand,
 * and the dot every switched-on laser puts where it points, yours and everyone's in sight (the future's gunners' too: a
 * dot on the wall before they fire). TaCZ draws the beam itself; {@code TaczLaserBeamMixin} stops it while the laser's off.
 */
public final class GunLaserClient {
	/** Lasers are looked for within this far of you, and a laser reaches this far (blocks). */
	static final double LOOK_RANGE = 48.0, REACH = 64.0;
	/** The dot: a glint this big (on redstone dust's scale), sat this far off what it lands on, toward the gun. */
	static final float DOT_SIZE = 0.35F;
	static final double DOT_LIFT = 0.03;
	/** A laser whose colour can't be read is this red. */
	static final int RED = 0xFF2A1E;

	/** The gun whose attachment TaCZ is drawing just now (empty otherwise), set by {@code TaczAttachmentLaserMixin}. */
	public static ItemStack drawing = ItemStack.EMPTY;
	/** TaCZ's one gun item (every gun is it, told apart by its id). */
	private static net.minecraft.world.item.@Nullable Item gunItem;

	private static @Nullable KeyMapping key;
	private static boolean looked;
	private static @Nullable Method colorOf;

	private GunLaserClient() {
	}

	public static void init() {
		if (!TaczGuns.LOADED) {
			return;
		}
		key = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymospowers.laser", InputConstants.KEY_END, JaymosPowersClient.CATEGORY));
		ClientTickEvents.END_CLIENT_TICK.register(GunLaserClient::tick);
	}

	private static void tick(Minecraft minecraft) {
		if (minecraft.player == null || minecraft.level == null) {
			return;
		}
		while (key != null && key.consumeClick()) {
			if (GunLasers.hasLaser(minecraft.player.getMainHandItem())) {
				ClientPlayNetworking.send(new GunLasers.Toggle());
			}
		}
		if (minecraft.isPaused()) {
			return;
		}
		ClientLevel level = minecraft.level;
		for (LivingEntity who : level.getEntitiesOfClass(LivingEntity.class, minecraft.player.getBoundingBox().inflate(LOOK_RANGE))) {
			ItemStack gun = who.getMainHandItem();
			if (gunItem == null) {
				gunItem = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse("tacz:modern_kinetic_gun"));
			}
			// (the item first: no copying of every mob's data each tick)
			if (!gun.is(gunItem) || !who.isAlive() || GunLasers.off(gun) || !GunLasers.hasLaser(gun)) {
				continue;
			}
			Vec3 eye = who.getEyePosition();
			Vec3 aim = who.getViewVector(1.0F);
			Vec3 end = eye.add(aim.scale(REACH));
			HitResult wall = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, who));
			if (wall.getType() != HitResult.Type.MISS) {
				end = wall.getLocation();
			}
			EntityHitResult body = ProjectileUtil.getEntityHitResult(who, eye, end, new AABB(eye, end).inflate(1.0),
				e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && e != who, eye.distanceToSqr(end));
			if (body != null) {
				end = body.getLocation();
			} else if (wall.getType() == HitResult.Type.MISS) {
				// pointing at the sky: nothing for it to land on
				continue;
			}
			Vec3 dot = end.subtract(aim.scale(DOT_LIFT));
			level.addParticle(MagicFx.mote(color(gun), DOT_SIZE), dot.x, dot.y, dot.z, 0.0, 0.0, 0.0);
		}
	}

	/** The colour TaCZ draws this gun's laser in (its own, or the one set at the gun smith's), by reflection. */
	private static int color(ItemStack gun) {
		if (!looked) {
			looked = true;
			try {
				colorOf = Class.forName("com.tacz.guns.util.LaserColorUtil").getMethod("getLaserColor", ItemStack.class);
			} catch (ReflectiveOperationException | LinkageError e) {
				colorOf = null;
			}
		}
		if (colorOf != null) {
			try {
				return ((Number) colorOf.invoke(null, gun)).intValue() & 0xFFFFFF;
			} catch (ReflectiveOperationException | RuntimeException e) {
				colorOf = null;
			}
		}
		return RED;
	}
}
