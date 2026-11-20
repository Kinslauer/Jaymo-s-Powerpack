package com.peterwolf.forestfire.client.hose;

import com.peterwolf.forestfire.network.HoseSyncPayload;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.phys.Vec3;

public final class ClientHoseState {
   private static final Map<UUID, ClientHoseState.HoseRenderData> HOSES = new ConcurrentHashMap<>();

   private ClientHoseState() {
   }

   public static void apply(HoseSyncPayload payload) {
      UUID id = payload.id();
      if (payload.removed()) {
         HOSES.remove(id);
      } else {
         HOSES.put(
            id,
            new ClientHoseState.HoseRenderData(
               id, payload.hoseType(), payload.pressurized(), payload.deployedLength(), payload.pathLength(), payload.pointsAsVec3()
            )
         );
      }
   }

   public static void clear() {
      HOSES.clear();
   }

   public static Collection<ClientHoseState.HoseRenderData> all() {
      return HOSES.values();
   }

   public record HoseRenderData(UUID id, String type, boolean pressurized, int deployedLength, float pathLength, List<Vec3> points) {
   }
}
