package com.jaymoskingdoms.client.illagers;

import com.jaymoskingdoms.illagers.Stunnable;
import net.minecraft.client.renderer.entity.state.IllagerRenderState;
import net.minecraft.world.entity.LivingEntity;

public class StunnableIllagerRenderState extends IllagerRenderState {
   public boolean isStunned;
   public boolean shieldInMainHand;
   public boolean shieldInOffHand;

   public <T extends LivingEntity & Stunnable> void extractRenderState(T entity) {
      this.isStunned = entity.isStunned();
      this.shieldInMainHand = entity.getMainHandItem().has(net.minecraft.core.component.DataComponents.BLOCKS_ATTACKS);
      this.shieldInOffHand = entity.getOffhandItem().has(net.minecraft.core.component.DataComponents.BLOCKS_ATTACKS);
   }
}
