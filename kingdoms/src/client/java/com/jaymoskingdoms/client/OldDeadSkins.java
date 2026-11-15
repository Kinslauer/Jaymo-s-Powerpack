package com.jaymoskingdoms.client;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.RisenLooks;
import java.util.UUID;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * The old dead of the present day. Andy: "Zombies and husks should have villager like skins on the overworld too, as they
 * are meant to be dead villagers from time past. Like the mca zombies already in the game, except these are explained as
 * being much older and much more deteriorated." Each grown zombie and husk in every world but Jaymo's Powers' future wears
 * one of our old-villager skins (kingdoms/tools/gen_old_dead.py), picked by its id so it keeps it. A zombie's is drawn
 * by where it rose ({@link RisenLooks}: plain, dried, bloated, pale or frostbitten), in its land's villager clothes, at
 * one of {@link #STAGES} stages of rot. MCA's zombie villagers (the recently dead) keep their own looks, as do babies,
 * drowned and other mods' zombies; the future's dead are dressed by Jaymo's Powers, but for a share of its grown zombies
 * left to Tissou's Zombie Pack ({@link TissouPack}). None of these skins is the game's own zombie texture, so nothing a
 * resource pack hangs on that path reaches the present's dead.
 * Read by mixin.OldDeadSkinMixin and mixin.OldDeadHuskSkinMixin; the eyes that glow are {@link DeadEyes}.
 */
public final class OldDeadSkins {
	/** Husk skins (old_dead/husk_n). */
	public static final int HUSKS = 16;
	/** A zombie's: stages of rot, and skins of each (old_dead/risen/place/land_stage_variant; gen_old_dead.py's STAGES, VARIANTS). */
	public static final int STAGES = 3, VARIANTS = 3;
	/** Of the future's grown zombies, this share are drawn with the game's own zombie texture for Tissou's pack to dress. */
	static final float TISSOU_SHARE = 0.5F;
	static final Identifier GAME_ZOMBIE = Identifier.withDefaultNamespace("textures/entity/zombie/zombie.png");
	private static final Identifier[] HUSK_SKINS = new Identifier[HUSKS];
	private static final Identifier[][][] RISEN = new Identifier[RisenLooks.Place.values().length][RisenLooks.Land.values().length][];
	static final RenderStateDataKey<Identifier> SKIN = RenderStateDataKey.create(() -> "jaymoskingdoms:old_dead_skin");
	/** On a future zombie left to Tissou's pack. */
	static final RenderStateDataKey<Boolean> TISSOU = RenderStateDataKey.create(() -> "jaymoskingdoms:tissou_dead");

	static {
		for (int i = 0; i < HUSKS; i++) {
			HUSK_SKINS[i] = JaymosKingdoms.id("textures/entity/old_dead/husk_" + i + ".png");
		}
		for (RisenLooks.Place place : RisenLooks.Place.values()) {
			for (RisenLooks.Land land : place.lands) {
				Identifier[] skins = new Identifier[STAGES * VARIANTS];
				for (int i = 0; i < skins.length; i++) {
					skins[i] = JaymosKingdoms.id("textures/entity/old_dead/risen/" + place.id + "/" + land.id + "_" + i / VARIANTS + "_" + i % VARIANTS + ".png");
				}
				RISEN[place.ordinal()][land.ordinal()] = skins;
			}
		}
	}

	private OldDeadSkins() {
	}

	/** From the zombie renderers' extractRenderState: a grown zombie or husk gets its skin, and every one its eyes. */
	public static void extract(LivingEntity zombie, LivingEntityRenderState state) {
		EntityType<?> type = zombie.getType();
		if (type != EntityTypes.ZOMBIE && type != EntityTypes.HUSK) {
			return;
		}
		boolean future = zombie.level().dimension() == RisenLooks.FUTURE;
		boolean tissou = future && !zombie.isBaby() && type == EntityTypes.ZOMBIE && TissouPack.dresses() && share(zombie.getUUID()) < TISSOU_SHARE;
		if (tissou) {
			state.setData(TISSOU, true);
		}
		DeadEyes.extract(zombie, state, future, tissou);
		if (zombie.isBaby() || future) {
			return;
		}
		int pick = zombie.getUUID().hashCode();
		if (type == EntityTypes.ZOMBIE) {
			Integer look = zombie.getAttached(RisenLooks.ROSE);
			RisenLooks.Place place = look == null ? RisenLooks.Place.PLAIN : RisenLooks.place(look);
			RisenLooks.Land land = place.drawn(look == null ? RisenLooks.Land.PLAINS : RisenLooks.land(look));
			state.setData(SKIN, RISEN[place.ordinal()][land.ordinal()][Math.floorMod(pick, STAGES * VARIANTS)]);
		} else {
			state.setData(SKIN, HUSK_SKINS[Math.floorMod(pick, HUSKS)]);
		}
	}

	/** Its skin, if it's one of the old dead. */
	public static @Nullable Identifier skin(LivingEntityRenderState state) {
		return state.getData(SKIN);
	}

	/** (mixin.TissouFutureDeadMixin) The texture it's drawn with: the game's own zombie's for a future zombie left to Tissou's pack. */
	public static Identifier texture(LivingEntityRenderState state, Identifier texture) {
		return Boolean.TRUE.equals(state.getData(TISSOU)) ? GAME_ZOMBIE : texture;
	}

	/** Its place (0..1) for the Tissou share: its own roll, apart from the skin Jaymo's Powers picks from its id's hash. */
	static float share(UUID id) {
		long mixed = (id.getLeastSignificantBits() ^ id.getMostSignificantBits() >>> 7) * 0x9E3779B97F4A7C15L;
		return (mixed >>> 40) / (float) (1 << 24);
	}
}
