package com.jaymoskingdoms.client;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.Valor;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * (0.7.410) The coat's raised plates (Andy: "a growing scale system ... modeled off the player like bio built"): small
 * boxes laid on the arms the way BioForms lays its steel plates, posed with the arm as it's drawn. They cover the arm as far
 * as the rank's coat does (the hand first, the shoulder last; a pauldron at Unbroken), and when the coat goes up they grow
 * in one after another from the hand ({@link #GROW_TICKS}).
 * <ul>
 * <li>Scale: rows of small overlapping scales, their lower edges lifted, staggered;</li>
 * <li>Iron: bands of plate round the forearm, with a rivet on each;</li>
 * <li>Stone: rough lumps of rock here and there;</li>
 * <li>Ash: thin flakes curling off;</li>
 * <li>Lacquer: overlapping shell segments, like a beetle's;</li>
 * <li>Haze has none: it's a shimmer, not a shell.</li>
 * </ul>
 * Under King's coating the plates glint in the soul colour. Textures from {@code tools/gen_valor_coats.py}.
 */
public final class ValorPlates {
	/** How long the plates take to grow in, all of them (ticks). */
	static final float GROW_TICKS = 16.0F;
	/** How far off the arm the plates sit: bare (over the sleeves), over armour (pixels). */
	public static final float OUT_BARE = 0.3F, OUT_ARMOURED = 1.05F;
	/** Rows of the arm a rank's coat covers, up from the hand (as gen_valor_coats.py's COVER). */
	static final int[] COVER = {3, 6, 8, 12, 12};
	/** The right arm (classic): its centre across, its top and bottom (pixels); a slim arm is this much as wide, its centre. */
	static final float CENTRE = -1.0F, TOP = -2.0F, HAND = 10.0F, SLIM = 0.75F, SLIM_CENTRE = -0.5F;

	/** The plate box's size (pixels): across and down, and its thickness. */
	static final float BOX_W = 8.0F, BOX_T = 2.0F;

	/** Faces of the arm: front, outer, back (the inner one is against the body). */
	enum Face {
		FRONT(Mth.PI), OUTER(-Mth.HALF_PI), BACK(0.0F);

		/** Its turn about the arm's length, from facing back (+z). */
		final float yRot;

		Face(float yRot) {
			this.yRot = yRot;
		}
	}

	/** One plate: its face, how far across it (from the middle), the top edge's height, its size, how far its lower edge lifts. */
	record Plate(Face face, float u, float y, float w, float h, float t, float tilt) {
	}

	/** A look's plates, from the hand up, and its box. */
	record Set(List<Plate> plates, ModelPart box, Identifier texture, Identifier glow) {
	}

	private static final Map<Integer, Set> SETS = new HashMap<>();
	/** When each one's coat went up (their tickCount), while it stays up. */
	private static final Map<Integer, Integer> SINCE = new HashMap<>();

	private ValorPlates() {
	}

	/** How far {@code entity}'s plates have grown in (0-1); forgets them once the coat is down. */
	static float grow(LivingEntity entity, boolean up) {
		if (!up) {
			SINCE.remove(entity.getId());
			return 0.0F;
		}
		int since = SINCE.computeIfAbsent(entity.getId(), id -> entity.tickCount);
		if (since > entity.tickCount) {
			// (the id taken by someone else since)
			SINCE.put(entity.getId(), entity.tickCount);
			since = entity.tickCount;
		}
		float partial = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
		return Mth.clamp((entity.tickCount - since + partial) / GROW_TICKS, 0.0F, 1.0F);
	}

	/**
	 * The plates on one arm ({@code arm} posed as it's drawn now, {@code right} which), {@code out} off it; {@code glow} the
	 * King's glint's colour (0: none).
	 */
	static void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, ModelPart arm, boolean right, boolean slim, ValorLook.Coat coat,
		float out, int glow) {
		Set set = set(coat.look());
		if (set == null || coat.grow() <= 0.0F) {
			return;
		}
		float covered = HAND - COVER[Mth.clamp(coat.rank(), 0, COVER.length - 1)];
		List<Plate> plates = set.plates();
		int shown = 0;
		for (Plate p : plates) {
			if (p.y() >= covered - 0.01F || p.y() < TOP && coat.rank() >= Valor.UNBROKEN) {
				shown++;
			}
		}
		poseStack.pushPose();
		arm.translateAndRotate(poseStack);
		// the left arm is the right one mirrored (worked out here: a mirrored pose would turn the boxes inside out)
		float side = right ? 1.0F : -1.0F;
		if (slim) {
			poseStack.translate(side * SLIM_CENTRE / 16.0F, 0.0F, 0.0F);
			poseStack.scale(SLIM, 1.0F, 1.0F);
			poseStack.translate(-side * CENTRE / 16.0F, 0.0F, 0.0F);
		}
		int i = 0;
		for (Plate p : plates) {
			boolean pauldron = p.y() < TOP;
			if (pauldron ? coat.rank() < Valor.UNBROKEN : p.y() < covered - 0.01F) {
				continue;
			}
			// one after another from the hand
			float s = Mth.clamp(coat.grow() * shown - i, 0.0F, 1.0F);
			i++;
			if (s <= 0.0F) {
				continue;
			}
			float x, z;
			switch (p.face()) {
				case FRONT -> {
					x = CENTRE + p.u();
					z = -2.0F - out;
				}
				case BACK -> {
					x = CENTRE - p.u();
					z = 2.0F + out;
				}
				default -> {
					x = CENTRE - 2.0F - out;
					z = p.u();
				}
			}
			poseStack.pushPose();
			poseStack.translate(side * x / 16.0F, p.y() / 16.0F, z / 16.0F);
			poseStack.rotate(Axis.YP, p.face() == Face.OUTER ? side * p.face().yRot : p.face().yRot);
			poseStack.rotate(Axis.XP, p.tilt());
			poseStack.scale(p.w() / BOX_W * s, p.h() / BOX_W * s, p.t() / BOX_T * s);
			collector.order(1).submitModelPart(set.box(), poseStack, RenderTypes.entityCutout(set.texture()), light, OverlayTexture.NO_OVERLAY, null, -1);
			if (glow != 0) {
				collector.order(2).submitModelPart(set.box(), poseStack, RenderTypes.eyes(set.glow()), light, OverlayTexture.NO_OVERLAY, null, glow);
			}
			poseStack.popPose();
		}
		poseStack.popPose();
	}

	/** {@code look}'s plates (null for Haze), made once. */
	private static Set set(int look) {
		Valor.Look kind = Valor.Look.values()[Mth.clamp(look, 0, Valor.Look.values().length - 1)];
		if (kind == Valor.Look.HAZE) {
			return null;
		}
		return SETS.computeIfAbsent(kind.ordinal(), k -> new Set(plates(kind), box(),
			JaymosKingdoms.id("textures/entity/valor/plate_" + kind.key() + ".png"), JaymosKingdoms.id("textures/entity/valor/plate_" + kind.key() + "_glow.png")));
	}

	/** The plate box: {@link #BOX_W} across and down, {@link #BOX_T} out from its top edge's middle (each plate is this, scaled), so its faces show the texture's 8x8 pattern. */
	private static ModelPart box() {
		MeshDefinition mesh = new MeshDefinition();
		mesh.getRoot().addOrReplaceChild("plate", CubeListBuilder.create().texOffs(0, 0).addBox(-BOX_W / 2.0F, 0.0F, 0.0F, BOX_W, BOX_W, BOX_T), PartPose.ZERO);
		return LayerDefinition.create(mesh, 32, 16).bakeRoot();
	}

	/** Where a look's plates go, the hand's first. */
	static List<Plate> plates(Valor.Look look) {
		List<Plate> out = new ArrayList<>();
		Random r = new Random(41L + look.ordinal());
		switch (look) {
			case SCALE -> {
				// rows of three a face, staggered, each overlapping the one below
				int row = 0;
				for (float y = HAND - 1.6F; y >= TOP - 0.2F; y -= 1.1F, row++) {
					float off = row % 2 == 0 ? 0.0F : 0.65F;
					for (Face f : Face.values()) {
						for (float u = -1.3F + off; u <= 1.35F; u += 1.3F) {
							out.add(new Plate(f, u, y, 1.6F, 1.6F, 0.35F, 0.45F));
						}
					}
				}
			}
			case IRON -> {
				for (float y = HAND - 1.4F; y >= TOP; y -= 2.6F) {
					for (Face f : Face.values()) {
						out.add(new Plate(f, 0.0F, y, 4.4F, 1.3F, 0.45F, 0.04F));
						out.add(new Plate(f, 0.0F, y + 0.35F, 0.7F, 0.7F, 0.8F, 0.0F));
					}
				}
			}
			case STONE -> {
				for (float y = HAND - 1.8F; y >= TOP; y -= 1.7F) {
					int n = 1 + r.nextInt(2);
					for (int k = 0; k < n; k++) {
						Face f = Face.values()[r.nextInt(Face.values().length)];
						float size = 1.4F + r.nextFloat() * 0.8F;
						out.add(new Plate(f, (r.nextFloat() - 0.5F) * 2.4F, y, size, size * (0.8F + r.nextFloat() * 0.3F), 0.7F + r.nextFloat() * 0.5F,
							(r.nextFloat() - 0.3F) * 0.3F));
					}
				}
			}
			case ASH -> {
				for (float y = HAND - 1.2F; y >= TOP; y -= 1.9F) {
					Face f = Face.values()[r.nextInt(Face.values().length)];
					out.add(new Plate(f, (r.nextFloat() - 0.5F) * 2.6F, y, 1.2F + r.nextFloat() * 0.6F, 0.9F, 0.2F, 0.6F + r.nextFloat() * 0.3F));
				}
			}
			case LACQUER -> {
				for (float y = HAND - 2.4F; y >= TOP - 0.4F; y -= 1.9F) {
					for (Face f : Face.values()) {
						out.add(new Plate(f, 0.0F, y, 4.5F, 2.4F, 0.5F, 0.22F));
					}
				}
			}
			default -> {
				return out;
			}
		}
		// Unbroken: a pauldron over the shoulder's outer face, in two tiers
		out.add(new Plate(Face.OUTER, 0.0F, TOP - 0.6F, 4.8F, 2.6F, 0.6F, 0.7F));
		out.add(new Plate(Face.OUTER, 0.0F, TOP - 1.6F, 4.2F, 2.0F, 0.6F, 0.9F));
		return out;
	}
}
