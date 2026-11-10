package com.jaymoskingdoms.client;

import com.jaymoskingdoms.JaymosKingdoms;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * The Overworld's old dead. Andy: "Zombies and husks should have villager like skins on the overworld too, as they are
 * meant to be dead villagers from time past. Like the mca zombies already in the game, except these are explained as
 * being much older and much more deteriorated." Each grown zombie and husk in the Overworld wears one of our old-villager
 * skins (kingdoms/tools/gen_old_dead.py: faded robes of the villagers' lands and trades, bone and parchment, earth), picked
 * by its id so it keeps it. MCA's zombie villagers (the recently dead) keep their own looks, as do babies, drowned, other
 * mods' zombies, and every zombie outside the Overworld (Jaymo's Powers dresses the future's own).
 * Read by mixin.OldDeadSkinMixin and mixin.OldDeadHuskSkinMixin.
 */
public final class OldDeadSkins {
	/** Skins of each kind (old_dead/zombie_n, old_dead/husk_n). */
	public static final int ZOMBIES = 32, HUSKS = 16;
	private static final Identifier[] ZOMBIE_SKINS = new Identifier[ZOMBIES];
	private static final Identifier[] HUSK_SKINS = new Identifier[HUSKS];
	static final RenderStateDataKey<Identifier> SKIN = RenderStateDataKey.create(() -> "jaymoskingdoms:old_dead_skin");

	static {
		for (int i = 0; i < ZOMBIES; i++) {
			ZOMBIE_SKINS[i] = JaymosKingdoms.id("textures/entity/old_dead/zombie_" + i + ".png");
		}
		for (int i = 0; i < HUSKS; i++) {
			HUSK_SKINS[i] = JaymosKingdoms.id("textures/entity/old_dead/husk_" + i + ".png");
		}
	}

	private OldDeadSkins() {
	}

	/** From the zombie renderers' extractRenderState: a grown zombie or husk in the Overworld gets its skin. */
	public static void extract(LivingEntity zombie, LivingEntityRenderState state) {
		if (zombie.isBaby() || zombie.level().dimension() != Level.OVERWORLD) {
			return;
		}
		int pick = zombie.getUUID().hashCode();
		EntityType<?> type = zombie.getType();
		if (type == EntityTypes.ZOMBIE) {
			state.setData(SKIN, ZOMBIE_SKINS[Math.floorMod(pick, ZOMBIES)]);
		} else if (type == EntityTypes.HUSK) {
			state.setData(SKIN, HUSK_SKINS[Math.floorMod(pick, HUSKS)]);
		}
	}

	/** Its skin, if it's one of the old dead. */
	public static @Nullable Identifier skin(LivingEntityRenderState state) {
		return state.getData(SKIN);
	}
}
