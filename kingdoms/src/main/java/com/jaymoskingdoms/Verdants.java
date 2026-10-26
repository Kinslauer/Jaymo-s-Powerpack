package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * (0.7.23) Andy: every villager in Jaymo's Powers' Verdant World (the Z Fighter's green world behind the Lookout's door)
 * is a Verdant, and every Verdant has antennae and green skin. A villager there (MCA's or vanilla's) is marked the first
 * time it loads in that world, and stays a Verdant wherever it goes. The mark is its shade ({@link #SHADE}, 0 pale ..
 * 1 deep green), seen by every client: {@code mixin/mca/McaVerdantSkinMixin} paints MCA's skin layer that green and
 * {@code client/VerdantLook} puts the antennae on. Read by dimension id, so Kingdoms needs no Powers classes.
 */
public final class Verdants {
	/** Jaymo's Powers' Verdant World. */
	public static final Identifier WORLD = Identifier.fromNamespaceAndPath("jaymospowers", "verdant_world");
	/** Greens from the palest (shade 0) to the deepest (shade 1), and how far a shade leans yellow or blue. */
	static final int PALE = 0xA6DE86, DEEP = 0x2F6E2A;
	static final int LEAN = 26;

	/** A Verdant's shade of green (0 pale .. 1 deep); none: not a Verdant. */
	public static final AttachmentType<Float> SHADE = AttachmentRegistry.create(JaymosKingdoms.id("verdant"),
		builder -> builder.persistent(Codec.FLOAT).syncWith(ByteBufCodecs.FLOAT, AttachmentSyncPredicate.all()));

	private Verdants() {
	}

	static void register() {
		ServerEntityEvents.ENTITY_LOAD.register(Verdants::loaded);
	}

	private static void loaded(Entity entity, ServerLevel level) {
		if (entity instanceof Villager && !entity.hasAttached(SHADE) && level.dimension().identifier().equals(WORLD)) {
			// most a middling green, a few very pale or very deep
			float shade = (entity.getRandom().nextFloat() + entity.getRandom().nextFloat()) / 2.0F;
			entity.setAttached(SHADE, shade);
		}
	}

	/** Whether {@code entity} is a Verdant. */
	public static boolean is(Entity entity) {
		return entity.hasAttached(SHADE);
	}

	/** {@code entity}'s skin colour (opaque ARGB), or 0 when it's no Verdant. */
	public static int skin(Entity entity) {
		Float shade = entity.getAttached(SHADE);
		if (shade == null) {
			return 0;
		}
		// (0.7.214) a Glowcap's or a Mosshide's green leans their own way
		HeritageVariants.Variant v = HeritageVariants.of(entity);
		int green = green(shade);
		return v == null || v.heritage != Heritage.Kind.VERDANT ? green : HeritageVariants.blend(green, v.tint, v.tintAmount);
	}

	/** The green of {@code shade}: pale to deep, some leaning yellow and some blue. */
	public static int green(float shade) {
		float s = Mth.clamp(shade, 0.0F, 1.0F);
		float lean = Mth.frac(s * 13.37F) - 0.5F;
		int r = Mth.clamp(Math.round(Mth.lerp(s, ARGB.red(PALE), ARGB.red(DEEP)) + lean * LEAN), 0, 255);
		int g = Math.round(Mth.lerp(s, ARGB.green(PALE), ARGB.green(DEEP)));
		int b = Mth.clamp(Math.round(Mth.lerp(s, ARGB.blue(PALE), ARGB.blue(DEEP)) - lean * LEAN), 0, 255);
		return ARGB.color(255, r, g, b);
	}
}
