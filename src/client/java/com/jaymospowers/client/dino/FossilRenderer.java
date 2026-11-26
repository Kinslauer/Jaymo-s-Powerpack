package com.jaymospowers.client.dino;

import com.jaymospowers.dino.entity.FossilSkeleton;
import com.jaymospowers.dino.entity.Species;
import com.jaymospowers.dino.rig.DinoAnimIn;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.AABB;

public class FossilRenderer extends EntityRenderer<FossilSkeleton, FossilRenderer.State> {
   private final DinoAnimIn in = new DinoAnimIn();

   public FossilRenderer(Context ctx) {
      super(ctx);
      this.shadowRadius = 0.5F;
   }

   public FossilRenderer.State createRenderState() {
      return new FossilRenderer.State();
   }

   protected AABB getBoundingBoxForCulling(FossilSkeleton entity, float partial) {
      double grow = entity.species().width * 1.6;
      return super.getBoundingBoxForCulling(entity, partial).inflate(grow, entity.species().height * 0.3, grow);
   }

   public void extractRenderState(FossilSkeleton e, FossilRenderer.State state, float partial) {
      super.extractRenderState(e, state, partial);
      state.species = e.species();
      state.yaw = e.getYRot();
      DinoModels.Entry entry = DinoModels.get(state.species);
      this.in.reset();
      switch (e.stance()) {
         case 1:
            this.in.action = 2;
            this.in.actionT = entry.anim.spec().roarTime * 0.45F;
            break;
         case 2:
            this.in.walk = 1.0F;
            this.in.walkPhase = 0.25F;
            break;
         case 3:
            this.in.action = 1;
            this.in.actionT = entry.anim.spec().biteHit;
      }

      entry.anim.apply(this.in, entry.pose);
      state.bones = entry.solveInto(state.bones);
   }

   public void submit(FossilRenderer.State state, PoseStack ps, SubmitNodeCollector collector, CameraRenderState camera) {
      if (state.bones != null) {
         DinoModels.Entry entry = DinoModels.get(state.species);
         ps.pushPose();
         ps.rotateDegrees(Axis.YP, -state.yaw);
         float[] bones = state.bones;
         int light = state.lightCoords;
         collector.submitCustomGeometry(
            ps,
            RenderTypes.entityCutout(entry.skeleton),
            (pose, buffer) -> DinoModels.draw(entry.model, bones, pose, buffer, -1, OverlayTexture.NO_OVERLAY, light)
         );
         ps.popPose();
      }

      super.submit(state, ps, collector, camera);
   }

   public static class State extends EntityRenderState {
      public Species species = Species.VELOCIRAPTOR;
      public float[] bones;
      public float yaw;
   }
}
