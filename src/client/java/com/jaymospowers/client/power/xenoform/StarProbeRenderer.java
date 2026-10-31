package com.jaymospowers.client.power.xenoform;

import com.jaymospowers.JaymosPowers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.VexRenderer;
import net.minecraft.client.renderer.entity.state.VexRenderState;
import net.minecraft.resources.Identifier;

/** (0.18.122, Xeno-Band patch 6) A star wreck's probe: the vex's body and flight, in our own grey metal skin with a green visor. */
public final class StarProbeRenderer extends VexRenderer {
	static final Identifier TEXTURE = JaymosPowers.id("textures/entity/star_probe.png");

	public StarProbeRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public Identifier getTextureLocation(VexRenderState state) {
		return TEXTURE;
	}
}
