package com.jaymoskingdoms.client.illagers;

import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;

public class FlyingMagmaRenderState extends EntityRenderState {
   public final BlockModelRenderState blockModel = new BlockModelRenderState();
   public float yRot;
   public float xRot;
}
