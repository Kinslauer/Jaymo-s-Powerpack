package com.jaymoskingdoms.client.glowwood;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.Entity;

/**
 * Render state of the Spore &amp; Stem creatures. Besides the usual living-entity fields it keeps the entity itself, so
 * the models can read the creature's animation states and synced values (phase, swelling, variant) while posing.
 */
public class CreatureRenderState<E extends Entity> extends LivingEntityRenderState {
	public E entity;
	public float partialTick;
}
