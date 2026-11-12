package com.jaymospowers.client.costume;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.gifted.GiftedCostume;
import com.jaymospowers.costume.CostumeDesign;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * A gifted villager in their costume ({@link GiftedCostume}) is drawn as a player body wearing it, the way a dead
 * player's costumed body is ({@link CorpseCostumeClient}): the costume was made for that body, and the mask and suit hide
 * whoever is under it (MCA's look, the villager's size and blood). From the dispatcher's extract, last
 * ({@code GiftedCostumeEntityRenderDispatcherMixin}): the villager's humanoid render state is copied onto a player's
 * (every field the game and Fabric's data give it, never MCA's own), the costume put on it, and the player renderer draws
 * it. Over their head goes the costume's name, not theirs.
 */
public final class GiftedCostumeClient {
	/** The fields copied off a villager's state, from {@link EntityRenderState} down to {@link HumanoidRenderState}. */
	private static @Nullable List<Field> fields;
	private static boolean broken;

	private GiftedCostumeClient() {
	}

	/** {@code state}, or a player's in its place when {@code entity} is a gifted villager in their costume. */
	public static EntityRenderState swap(Entity entity, EntityRenderState state) {
		if (broken || entity instanceof Player || !(state instanceof HumanoidRenderState humanoid) || state instanceof AvatarRenderState) {
			return state;
		}
		CostumeDesign design = GiftedCostume.shown(entity);
		if (design == null) {
			return state;
		}
		AvatarRenderState avatar = new AvatarRenderState();
		List<Field> copied = fields();
		for (int i = 0; i < copied.size(); i++) {
			Field f = copied.get(i);
			try {
				f.set(avatar, f.get(humanoid));
			} catch (ReflectiveOperationException | RuntimeException e) {
				// (a field that can't be set, a final one a newer Java won't let go of: left as the new state has it)
				JaymosPowers.LOGGER.debug("A gifted villager's costumed body can't take {}", f.getName(), e);
				copied.remove(i--);
				if (copied.isEmpty()) {
					broken = true;
					return state;
				}
			}
		}
		avatar.id = entity.getId();
		avatar.skin = DefaultPlayerSkin.get(entity.getUUID());
		avatar.showHat = avatar.showJacket = avatar.showLeftSleeve = avatar.showRightSleeve = avatar.showLeftPants = avatar.showRightPants = false;
		avatar.showCape = false;
		avatar.setData(CostumeClient.KEY, design);
		avatar.setData(CostumeClient.SHAPE, null);
		avatar.setData(CostumeBody.BODY, null);
		avatar.setData(CostumeBody.PARENT, null);
		avatar.setData(CostumeCover.KEY, null);
		// the armour under it isn't drawn, as on a costumed player
		avatar.headEquipment = ItemStack.EMPTY;
		avatar.headItem.clear();
		avatar.wornHeadType = null;
		avatar.wornHeadProfile = null;
		if (!avatar.chestEquipment.has(DataComponents.GLIDER)) {
			avatar.chestEquipment = ItemStack.EMPTY;
		}
		avatar.legsEquipment = ItemStack.EMPTY;
		avatar.feetEquipment = ItemStack.EMPTY;
		if (avatar.nameTag != null) {
			avatar.nameTag = Component.literal(design.name());
			avatar.scoreText = null;
		}
		return avatar;
	}

	private static List<Field> fields() {
		if (fields == null) {
			List<Field> all = new ArrayList<>();
			for (Class<?> c = HumanoidRenderState.class; c != null && EntityRenderState.class.isAssignableFrom(c); c = c.getSuperclass()) {
				for (Field f : c.getDeclaredFields()) {
					// (MCA's own fields, mca$..., say MCA is drawing the body: they stay off the player's state)
					if (Modifier.isStatic(f.getModifiers()) || f.getName().contains("$")) {
						continue;
					}
					f.setAccessible(true);
					all.add(f);
				}
			}
			fields = all;
		}
		return fields;
	}
}
