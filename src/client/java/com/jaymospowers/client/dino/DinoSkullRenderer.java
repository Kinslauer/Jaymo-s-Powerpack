package com.jaymospowers.client.dino;

import com.jaymospowers.dino.block.DinoSkullBlock;
import com.jaymospowers.dino.block.DinoSkullBlockEntity;
import com.jaymospowers.dino.entity.Species;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class DinoSkullRenderer implements BlockEntityRenderer<DinoSkullBlockEntity, DinoSkullRenderer.State> {
   public DinoSkullRenderer(Context context) {
   }

   public DinoSkullRenderer.State createRenderState() {
      return new DinoSkullRenderer.State();
   }

   public void extractRenderState(DinoSkullBlockEntity be, DinoSkullRenderer.State state, float partial, Vec3 camera, @Nullable CrumblingOverlay breakProgress) {
      BlockEntityRenderer.super.extractRenderState(be, state, partial, camera, breakProgress);
      BlockState bs = be.getBlockState();
      if (bs.getBlock() instanceof DinoSkullBlock block) {
         state.species = block.species();
         state.yaw = DinoSkullBlock.yaw(bs);
         state.wall = (Boolean)bs.getValue(DinoSkullBlock.WALL);
      }
   }

   public void submit(DinoSkullRenderer.State state, PoseStack ps, SubmitNodeCollector collector, CameraRenderState camera) {
      DinoModels.Entry entry = DinoModels.get(state.species);
      DinoModels.SkullMesh mesh = entry.skull();
      if (mesh.quads != 0) {
         ps.pushPose();
         ps.translate(0.5F, 0.0F, 0.5F);
         ps.rotateDegrees(Axis.YP, -state.yaw);
         if (state.wall) {
            ps.translate(0.0F, 0.5F - mesh.height * 0.5F, -0.5F + mesh.length * 0.5F);
         }

         int light = state.lightCoords;
         collector.submitCustomGeometry(ps, RenderTypes.entityCutout(entry.skeleton), (pose, buffer) -> mesh.draw(pose, buffer, light));
         ps.popPose();
      }
   }

   public boolean shouldRenderOffScreen() {
      return true;
   }

   public int getViewDistance() {
      return 96;
   }

   public static class State extends BlockEntityRenderState {
      public Species species = Species.VELOCIRAPTOR;
      public float yaw;
      public boolean wall;
   }
}
