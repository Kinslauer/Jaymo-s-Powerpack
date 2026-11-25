package com.jaymoskingdoms.client.glowwood;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Mob;

/** Base renderer of the creatures: fills a {@link CreatureRenderState} and picks the texture from the entity. */
public abstract class CreatureRenderer<E extends Mob, M extends RigModel<E>> extends MobRenderer<E, CreatureRenderState<E>, M> {

	protected CreatureRenderer(EntityRendererProvider.Context context, M model, float shadowRadius) {
		super(context, model, shadowRadius);
	}

	protected abstract Identifier texture(E entity);

	@Override
	public CreatureRenderState<E> createRenderState() {
		return new CreatureRenderState<>();
	}

	@Override
	public void extractRenderState(E entity, CreatureRenderState<E> state, float partialTick) {
		super.extractRenderState(entity, state, partialTick);
		state.entity = entity;
		state.partialTick = partialTick;
	}

	@Override
	public Identifier getTextureLocation(CreatureRenderState<E> state) {
		return texture(state.entity);
	}
}
