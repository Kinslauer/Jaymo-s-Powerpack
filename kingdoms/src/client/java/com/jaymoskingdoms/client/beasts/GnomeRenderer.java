package com.jaymoskingdoms.client.beasts;

import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.renderer.base.RenderPassInfo;
import com.jaymoskingdoms.beasts.Gnome;
import com.jaymoskingdoms.client.inhabitants.InhabitantRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/**
 * Draws the gnome (com.jaymoskingdoms.beasts.Gnome) with GeckoLib (geckolib/models/entity/gnome.geo.json, our own,
 * kingdoms/tools/gen_gnome.py). The plank shield on its left arm shows only on the gnomes that carry one.
 */
public final class GnomeRenderer extends InhabitantRenderer<Gnome> {
	static final float SHADOW = 0.3F;
	static final DataTicket<Boolean> SHIELD = DataTicket.create("jaymoskingdoms_gnome_shield", Boolean.class);

	GnomeRenderer(EntityRendererProvider.Context context) {
		super(context, "gnome", 1.0F, SHADOW, "head");
	}

	@Override
	public void addRenderData(Gnome gnome, Void related, LivingEntityRenderState state, float partialTick) {
		super.addRenderData(gnome, related, state, partialTick);
		state.addGeckolibData(SHIELD, gnome.shield());
	}

	@Override
	public void adjustModelBonesForRender(RenderPassInfo<LivingEntityRenderState> info, BoneSnapshots snapshots) {
		super.adjustModelBonesForRender(info, snapshots);
		boolean shield = info.getOrDefaultGeckolibData(SHIELD, false);
		snapshots.ifPresent("shield", bone -> bone.skipRender(!shield));
	}
}
