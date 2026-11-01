package com.jaymoskitchen.client.corpse;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Entity;

/**
 * (0.5.46) What other mods dress a dead player's body in (Jaymo's Powers: the costume they died in). Only plain Java and
 * Minecraft types, so a mod adds to these lists by reflection without compiling against Kitchen:
 * <ul>
 * <li>{@link #PLAIN}: true for a body that wears something: it's drawn in the dead player's own player body (where
 * the dressing is drawn), never as MCA's villager look;</li>
 * <li>{@link #EXTRACT}: as the body's state is made each frame: (corpse, the body's state), to put what it wears on
 * the state (the skin-layer flags are reset to shown just before);</li>
 * <li>{@link #SUBMIT}: just after the body is submitted, in its pose: (the body's state, {@code {PlayerModel body,
 * PoseStack, SubmitNodeCollector, Integer light}}).</li>
 * </ul>
 */
public final class CorpseDressing {
	public static final List<Predicate<Entity>> PLAIN = new CopyOnWriteArrayList<>();
	public static final List<BiConsumer<Entity, AvatarRenderState>> EXTRACT = new CopyOnWriteArrayList<>();
	public static final List<BiConsumer<AvatarRenderState, Object[]>> SUBMIT = new CopyOnWriteArrayList<>();

	private CorpseDressing() {
	}

	/** Whether {@code corpse} is to be drawn in the plain player body (something is dressed on it). */
	static boolean plain(Entity corpse) {
		for (Predicate<Entity> p : PLAIN) {
			try {
				if (p.test(corpse)) {
					return true;
				}
			} catch (RuntimeException e) {
				// another mod's hook failing never stops the body being drawn
			}
		}
		return false;
	}

	static void extract(Entity corpse, AvatarRenderState avatar) {
		avatar.showHat = avatar.showJacket = avatar.showLeftPants = avatar.showRightPants = avatar.showLeftSleeve = avatar.showRightSleeve = true;
		for (BiConsumer<Entity, AvatarRenderState> c : EXTRACT) {
			try {
				c.accept(corpse, avatar);
			} catch (RuntimeException e) {
				// as above
			}
		}
	}

	static void submit(AvatarRenderState avatar, PlayerModel body, PoseStack poseStack, SubmitNodeCollector collector, int light) {
		if (SUBMIT.isEmpty()) {
			return;
		}
		Object[] args = {body, poseStack, collector, light};
		for (BiConsumer<AvatarRenderState, Object[]> c : SUBMIT) {
			try {
				c.accept(avatar, args);
			} catch (RuntimeException e) {
				// as above
			}
		}
	}
}
