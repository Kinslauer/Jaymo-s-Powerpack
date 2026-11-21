package com.peterwolf.forestfire.firefighting.hose;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.peterwolf.forestfire.firefighting.nozzle.NozzleLocationState;
import com.peterwolf.forestfire.firefighting.nozzle.NozzleMode;
import com.peterwolf.forestfire.firefighting.nozzle.NozzleValveState;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import org.jspecify.annotations.Nullable;

public final class HoseEndpoint {
   public static final Codec<HoseEndpoint> CODEC = RecordCodecBuilder.create(
      instance -> instance.group(
            UUIDUtil.STRING_CODEC.fieldOf("id").forGetter(e -> e.id),
            BlockPos.CODEC.fieldOf("anchor").forGetter(e -> e.anchorPos),
            BlockPos.CODEC.fieldOf("endpoint").forGetter(e -> e.endpointPos),
            BlockPos.CODEC.optionalFieldOf("pump").forGetter(e -> Optional.ofNullable(e.pumpPos)),
            Codec.STRING.fieldOf("location").forGetter(e -> e.location.name()),
            Codec.STRING.fieldOf("mode").forGetter(e -> e.mode.name()),
            Codec.STRING.fieldOf("valve").forGetter(e -> e.valve.name()),
            UUIDUtil.STRING_CODEC.optionalFieldOf("operator").forGetter(e -> Optional.ofNullable(e.operatorId)),
            Codec.INT.optionalFieldOf("hoseLength", 0).forGetter(e -> e.hoseLengthFromPump),
            Codec.INT.optionalFieldOf("maxReach", 16).forGetter(e -> e.maxReach),
            Codec.FLOAT.optionalFieldOf("yaw", 0.0F).forGetter(e -> e.yaw),
            Codec.FLOAT.optionalFieldOf("pitch", 0.0F).forGetter(e -> e.pitch),
            Codec.BOOL.optionalFieldOf("connected", true).forGetter(e -> e.connected)
         )
         .apply(instance, HoseEndpoint::fromCodec)
   );
   public final UUID id;
   public BlockPos anchorPos;
   @Nullable
   public BlockPos pumpPos;
   public BlockPos endpointPos;
   public NozzleLocationState location;
   public NozzleMode mode;
   public NozzleValveState valve;
   @Nullable
   public UUID operatorId;
   public int hoseLengthFromPump;
   public int maxReach;
   public float yaw;
   public float pitch;
   public boolean connected;
   public float pressure01;
   public HoseTension tension = HoseTension.SLACK;
   public int remainingDistance;
   public int missingHoldTicks;

   public HoseEndpoint(UUID id, BlockPos anchor, BlockPos endpoint) {
      this.id = id;
      this.anchorPos = anchor.immutable();
      this.pumpPos = anchor.immutable();
      this.endpointPos = endpoint.immutable();
      this.location = NozzleLocationState.GROUND;
      this.mode = NozzleMode.STRAIGHT_STREAM;
      this.valve = NozzleValveState.CLOSED;
      this.operatorId = null;
      this.hoseLengthFromPump = 0;
      this.maxReach = 16;
      this.yaw = 0.0F;
      this.pitch = 0.0F;
      this.connected = true;
      this.pressure01 = 0.0F;
      this.remainingDistance = this.maxReach;
   }

   private static HoseEndpoint fromCodec(
      UUID id,
      BlockPos anchor,
      BlockPos endpoint,
      Optional<BlockPos> pump,
      String location,
      String mode,
      String valve,
      Optional<UUID> operator,
      int hoseLength,
      int maxReach,
      float yaw,
      float pitch,
      boolean connected
   ) {
      HoseEndpoint ep = new HoseEndpoint(id, anchor, endpoint);
      ep.pumpPos = pump.orElse(anchor).immutable();
      ep.location = NozzleLocationState.byName(location);
      ep.mode = NozzleMode.byName(mode);
      ep.valve = NozzleValveState.byName(valve);
      ep.operatorId = operator.orElse(null);
      ep.hoseLengthFromPump = hoseLength;
      ep.maxReach = maxReach;
      ep.yaw = yaw;
      ep.pitch = pitch;
      ep.connected = connected;
      ep.remainingDistance = maxReach;
      return ep;
   }

   public void bindPump(BlockPos pump) {
      this.pumpPos = pump.immutable();
      this.anchorPos = pump.immutable();
   }

   public void closeValve() {
      this.valve = NozzleValveState.CLOSED;
   }

   public boolean isHeldBy(UUID playerId) {
      return this.location == NozzleLocationState.HELD && playerId.equals(this.operatorId);
   }

   public boolean isAssignedToPump(BlockPos pump) {
      return this.pumpPos != null && this.pumpPos.equals(pump);
   }
}
