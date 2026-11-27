package com.jaymoskingdoms.client.glowwood.creature;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.client.glowwood.CreatureRenderer;
import com.jaymoskingdoms.client.glowwood.GlowLayer;
import com.jaymoskingdoms.glowwood.creatures.MimicapEntity;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

public class MimicapRenderer extends CreatureRenderer<MimicapEntity, MimicapModel> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(JaymosKingdoms.id("mimicap"), "main");
	private static final Identifier RED = JaymosKingdoms.id("textures/entity/glowwood/mimicap/mimicap_red.png");
	private static final Identifier BROWN = JaymosKingdoms.id("textures/entity/glowwood/mimicap/mimicap_brown.png");
	private static final Identifier GLOW = JaymosKingdoms.id("textures/entity/glowwood/mimicap/mimicap_glow.png");

	public MimicapRenderer(EntityRendererProvider.Context context) {
		super(context, new MimicapModel(context.bakeLayer(LAYER)), 0.6F);
		// The eyes only light up once it is awake (and flicker as it wakes).
		this.addLayer(new GlowLayer<>(this, e -> GLOW, e -> switch (e.getPhase()) {
			case MimicapEntity.AWAKE -> 0.9 + 0.1 * Mth.sin(e.tickCount * 0.3F);
			case MimicapEntity.WAKING -> (e.tickCount / 2) % 2 == 0 ? 1.0 : 0.3;
			case MimicapEntity.SETTLING -> 0.3;
			default -> 0.0;
		}));
	}

	@Override
	protected Identifier texture(MimicapEntity entity) {
		return entity.getVariant() == 0 ? RED : BROWN;
	}

	/** Topples onto its back when it dies. */
	public float getFlipDegrees() {
		return 180.0F;
	}
}
