package com.peterwolf.forestfire.firefighting.hose;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class HoseConnection {
   public static final Codec<HoseConnection> CODEC = RecordCodecBuilder.create(
      instance -> instance.group(
            UUIDUtil.STRING_CODEC.fieldOf("id").forGetter(c -> c.connectionId),
            UUIDUtil.STRING_CODEC.optionalFieldOf("networkId").forGetter(c -> Optional.ofNullable(c.networkId)),
            Codec.STRING.fieldOf("type").forGetter(c -> c.type.name()),
            BlockPos.CODEC.fieldOf("source").forGetter(c -> c.sourcePos),
            BlockPos.CODEC.fieldOf("target").forGetter(c -> c.targetPos),
            UUIDUtil.STRING_CODEC.optionalFieldOf("targetEntity").forGetter(c -> Optional.ofNullable(c.targetEntityId)),
            UUIDUtil.STRING_CODEC.optionalFieldOf("endpointId").forGetter(c -> Optional.ofNullable(c.nozzleEndpointId)),
            Codec.INT.fieldOf("deployedLength").forGetter(c -> c.deployedLength),
            HoseControlPoint.CODEC.listOf().fieldOf("points").forGetter(c -> c.path.points()),
            Codec.BOOL.fieldOf("connected").forGetter(c -> c.connected),
            Codec.BOOL.optionalFieldOf("pressurized", false).forGetter(c -> c.pressurized),
            Codec.FLOAT.optionalFieldOf("intakeEfficiency", 1.0F).forGetter(c -> c.intakeEfficiency),
            Codec.BOOL.optionalFieldOf("dirty", false).forGetter(c -> c.routeDirty)
         )
         .apply(instance, HoseConnection::fromCodec)
   );
   public final UUID connectionId;
   @Nullable
   public UUID networkId;
   public HoseConnectionType type;
   public BlockPos sourcePos;
   public BlockPos targetPos;
   @Nullable
   public UUID targetEntityId;
   @Nullable
   public UUID nozzleEndpointId;
   public int deployedLength;
   public HosePath path;
   public boolean connected;
   public boolean pressurized;
   public float intakeEfficiency;
   public boolean routeDirty;
   public double currentPathLength;
   public HoseTension tension = HoseTension.SLACK;
   public double remainingSlack;

   public HoseConnection(UUID id, HoseConnectionType type, BlockPos source, BlockPos target) {
      this.connectionId = id;
      this.networkId = null;
      this.type = type;
      this.sourcePos = source.immutable();
      this.targetPos = target.immutable();
      this.targetEntityId = null;
      this.nozzleEndpointId = null;
      this.deployedLength = 0;
      this.path = HosePath.empty();
      this.connected = true;
      this.pressurized = false;
      this.intakeEfficiency = 1.0F;
      this.routeDirty = false;
      this.currentPathLength = 0.0;
      this.remainingSlack = 0.0;
   }

   private static HoseConnection fromCodec(
      UUID id,
      Optional<UUID> networkId,
      String type,
      BlockPos source,
      BlockPos target,
      Optional<UUID> targetEntity,
      Optional<UUID> endpointId,
      int deployedLength,
      List<HoseControlPoint> points,
      boolean connected,
      boolean pressurized,
      float intakeEfficiency,
      boolean dirty
   ) {
      HoseConnection c = new HoseConnection(id, HoseConnectionType.byName(type), source, target);
      c.networkId = networkId.orElse(null);
      c.targetEntityId = targetEntity.orElse(null);
      c.nozzleEndpointId = endpointId.orElse(null);
      c.deployedLength = deployedLength;
      c.path = HosePath.of(new ArrayList<>(points));
      c.connected = connected;
      c.pressurized = pressurized;
      c.intakeEfficiency = intakeEfficiency;
      c.routeDirty = dirty;
      c.currentPathLength = c.path.pathLength();
      c.refreshTension();
      return c;
   }

   public void setPath(HosePath newPath) {
      this.path = newPath;
      this.currentPathLength = newPath.pathLength();
      this.refreshTension();
   }

   public void updateDynamicEndpoint(Vec3 endpoint, HosePointType type) {
      if (!this.path.isEmpty()) {
         this.setPath(this.path.withDynamicEndpoint(endpoint, type));
      }
   }

   public void refreshTension() {
      this.remainingSlack = this.deployedLength - this.currentPathLength;
      this.tension = HoseTensionCalculator.fromSlack(this.remainingSlack, this.deployedLength);
   }

   public int requiredBlocks() {
      return this.path.blockLength();
   }
}
