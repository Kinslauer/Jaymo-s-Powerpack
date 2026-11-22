package com.peterwolf.forestfire.network;

import com.peterwolf.forestfire.ForestFireMod;
import com.peterwolf.forestfire.firefighting.hose.HoseConnection;
import com.peterwolf.forestfire.firefighting.hose.HoseControlPoint;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.world.phys.Vec3;

public record HoseSyncPayload(
   String connectionId, String hoseType, boolean removed, boolean pressurized, int deployedLength, float pathLength, List<Float> coords
) implements CustomPacketPayload {
   public static final Type<HoseSyncPayload> TYPE = new Type(ForestFireMod.id("hose_sync"));
   public static final StreamCodec<RegistryFriendlyByteBuf, HoseSyncPayload> CODEC = StreamCodec.composite(
      ByteBufCodecs.STRING_UTF8,
      HoseSyncPayload::connectionId,
      ByteBufCodecs.STRING_UTF8,
      HoseSyncPayload::hoseType,
      ByteBufCodecs.BOOL,
      HoseSyncPayload::removed,
      ByteBufCodecs.BOOL,
      HoseSyncPayload::pressurized,
      ByteBufCodecs.VAR_INT,
      HoseSyncPayload::deployedLength,
      ByteBufCodecs.FLOAT,
      HoseSyncPayload::pathLength,
      ByteBufCodecs.FLOAT.apply(ByteBufCodecs.list()),
      HoseSyncPayload::coords,
      HoseSyncPayload::new
   );

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public static HoseSyncPayload fromConnection(HoseConnection c, boolean removed) {
      List<Float> coords = new ArrayList<>();
      if (!removed && c.path != null) {
         List<HoseControlPoint> pts = c.path.points();
         int step = pts.size() > 80 ? 2 : 1;

         for (int i = 0; i < pts.size(); i += step) {
            Vec3 p = pts.get(i).position();
            coords.add((float)p.x);
            coords.add((float)p.y);
            coords.add((float)p.z);
         }

         if (pts.size() > 1 && (pts.size() - 1) % step != 0) {
            Vec3 p = pts.getLast().position();
            coords.add((float)p.x);
            coords.add((float)p.y);
            coords.add((float)p.z);
         }
      }

      return new HoseSyncPayload(c.connectionId.toString(), c.type.name(), removed, c.pressurized, c.deployedLength, (float)c.currentPathLength, coords);
   }

   public List<Vec3> pointsAsVec3() {
      List<Vec3> out = new ArrayList<>();

      for (int i = 0; i + 2 < this.coords.size(); i += 3) {
         out.add(new Vec3(this.coords.get(i).floatValue(), this.coords.get(i + 1).floatValue(), this.coords.get(i + 2).floatValue()));
      }

      return out;
   }

   public UUID id() {
      try {
         return UUID.fromString(this.connectionId);
      } catch (IllegalArgumentException var2) {
         return new UUID(0L, 0L);
      }
   }
}
