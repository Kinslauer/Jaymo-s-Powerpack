package com.peterwolf.forestfire.firefighting.hose;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.phys.Vec3;

public record HoseControlPoint(Vec3 position, HosePointType type) {
   public static final Codec<HoseControlPoint> CODEC = RecordCodecBuilder.create(
      instance -> instance.group(
            Codec.DOUBLE.fieldOf("x").forGetter(p -> p.position.x),
            Codec.DOUBLE.fieldOf("y").forGetter(p -> p.position.y),
            Codec.DOUBLE.fieldOf("z").forGetter(p -> p.position.z),
            Codec.STRING.fieldOf("type").forGetter(p -> p.type.name())
         )
         .apply(instance, (x, y, z, type) -> new HoseControlPoint(new Vec3(x, y, z), HosePointType.byName(type)))
   );

   public HoseControlPoint withPosition(Vec3 pos) {
      return new HoseControlPoint(pos, this.type);
   }

   public HoseControlPoint withType(HosePointType newType) {
      return new HoseControlPoint(this.position, newType);
   }
}
