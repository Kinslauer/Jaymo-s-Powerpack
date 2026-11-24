package com.jaymospowers.client.dino;

import com.jaymospowers.dino.entity.Dinosaur;
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
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

public class DinoRenderer extends EntityRenderer<Dinosaur, DinoRenderer.State> {
   private final DinoAnimIn in = new DinoAnimIn();

   public DinoRenderer(Context ctx) {
      super(ctx);
   }

   public DinoRenderer.State createRenderState() {
      return new DinoRenderer.State();
   }

   protected AABB getBoundingBoxForCulling(Dinosaur entity, float partial) {
      AABB box = super.getBoundingBoxForCulling(entity, partial);
      double grow = entity.species().width * 1.6 * entity.getAgeScale();
      return box.inflate(grow, entity.species().height * 0.3, grow);
   }

   protected float getShadowRadius(DinoRenderer.State state) {
      return state.species.width * 0.55F * state.scale;
   }

   public void extractRenderState(Dinosaur e, DinoRenderer.State state, float partial) {
      super.extractRenderState(e, state, partial);
      Species s = e.species();
      state.species = s;
      state.scale = e.getAgeScale();
      state.bodyYaw = Mth.rotLerp(partial, e.yBodyRotO, e.yBodyRot);
      state.overlay = OverlayTexture.pack(0.0F, e.hurtTime > 0);
      DinoModels.Entry entry = DinoModels.get(s);
      this.fill(e, partial, state.bodyYaw);
      entry.anim.apply(this.in, entry.pose);
      state.bones = entry.solveInto(state.bones);
   }

   private void fill(Dinosaur e, float partial, float bodyYaw) {
      this.in.reset();
      this.in.time = (e.tickCount + partial) / 20.0F;
      this.in.walkPhase = Mth.lerp(partial, e.walkPhaseO, e.walkPhase);
      this.in.walk = Mth.lerp(partial, e.walkAmtO, e.walkAmt);
      this.in.run = Mth.lerp(partial, e.runAmtO, e.runAmt);
      this.in.turn = Mth.lerp(partial, e.turnAmtO, e.turnAmt);
      float headYaw = Mth.rotLerp(partial, e.yHeadRotO, e.yHeadRot);
      this.in.lookYaw = Mth.clamp(Mth.wrapDegrees(bodyYaw - headYaw), -70.0F, 70.0F) * (float) (Math.PI / 180.0);
      this.in.lookPitch = Mth.clamp(-e.getXRot(partial), -40.0F, 40.0F) * (float) (Math.PI / 180.0);
      int action = e.getAction();
      if (action != 0) {
         this.in.action = action;
         this.in.actionT = Math.max(0.0F, e.tickCount - e.actionStartTick + partial) / 20.0F;
      }

      this.in.sleep = Mth.lerp(partial, e.sleepAmtO, e.sleepAmt);
      this.in.sit = Mth.lerp(partial, e.sitAmtO, e.sitAmt);
      this.in.display = Mth.lerp(partial, e.displayAmtO, e.displayAmt);
      this.in.charge = Mth.lerp(partial, e.chargeAmtO, e.chargeAmt);
      this.in.air = Mth.lerp(partial, e.airAmtO, e.airAmt);
      this.in.hurt = Mth.lerp(partial, e.hurtAmtO, e.hurtAmt);
      this.in.death = e.deathTime > 0 ? (e.deathTime - 1 + partial) / 20.0F : -1.0F;
      this.in.saddled = e.isSaddled();
      this.in.baby = e.isBaby();
      this.in.seed = e.getId() % 97 / 97.0F;
   }

   public void submit(DinoRenderer.State state, PoseStack ps, SubmitNodeCollector collector, CameraRenderState camera) {
      if (state.bones != null && !state.isInvisible) {
         DinoModels.Entry entry = DinoModels.get(state.species);
         ps.pushPose();
         ps.rotateDegrees(Axis.YP, -state.bodyYaw);
         ps.scale(state.scale, state.scale, state.scale);
         float[] bones = state.bones;
         int light = state.lightCoords;
         int overlay = state.overlay;
         collector.submitCustomGeometry(
            ps, RenderTypes.entityCutout(entry.skin), (pose, buffer) -> DinoModels.draw(entry.model, bones, pose, buffer, -1, overlay, light)
         );
         ps.popPose();
      }

      super.submit(state, ps, collector, camera);
   }

   public static class State extends EntityRenderState {
      public Species species = Species.VELOCIRAPTOR;
      public float[] bones;
      public float bodyYaw;
      public float scale = 1.0F;
      public int overlay = OverlayTexture.NO_OVERLAY;
   }
}
