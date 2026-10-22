package com.jaymospowers.client.blood;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleGroup;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.ParticleGroupRenderState;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;

/**
 * (0.15.91) How {@link BloodPuddle}s are drawn (Iron's Simple Blood's {@code BloodDecalRenderState}, ported to 26.3):
 * their own particle group ({@link #TYPE}, registered through Fabric's {@code ParticleGroupRegistry}), whose render
 * state is a {@link QuadParticleRenderState} that hands vanilla's quad particle renderer ready-made vertices instead of
 * camera-facing squares, so a puddle can be cut to the block tops it lies on.
 */
final class BloodDecals {
	static final ParticleRenderType TYPE = new ParticleRenderType("jaymospowers:blood_decal", "BD");

	private BloodDecals() {
	}

	/** The puddles; each frame lays the ones in view into its {@link State}. */
	static final class Group extends ParticleGroup<BloodPuddle> {
		private final State state = new State();

		Group(ParticleEngine engine) {
			super(engine);
		}

		@Override
		public ParticleGroupRenderState extractRenderState(Frustum frustum, Camera camera, float partialTick) {
			for (BloodPuddle puddle : this.particles) {
				if (puddle.visible(frustum)) {
					puddle.emit(this.state, camera, partialTick);
				}
			}
			return this.state;
		}
	}

	/** This frame's puddle vertices (camera-relative), by layer, four to a quad. Cleared by vanilla each frame. */
	static final class State extends QuadParticleRenderState {
		private final Map<SingleQuadParticle.Layer, Vertices> layers = new IdentityHashMap<>();
		private int count;

		void add(SingleQuadParticle.Layer layer, float x, float y, float z, float u, float v, int color, int light) {
			this.layers.computeIfAbsent(layer, l -> new Vertices()).add(x, y, z, u, v, color, light);
			this.count++;
		}

		@Override
		public void clear() {
			this.layers.values().forEach(Vertices::clear);
			this.count = 0;
		}

		@Override
		public boolean isEmpty() {
			return this.count == 0;
		}

		@Override
		public Set<SingleQuadParticle.Layer> layers() {
			Set<SingleQuadParticle.Layer> used = new HashSet<>();
			this.layers.forEach((layer, vertices) -> {
				if (vertices.size > 0) {
					used.add(layer);
				}
			});
			return used;
		}

		@Override
		public void buildLayer(SingleQuadParticle.Layer layer, VertexConsumer consumer) {
			Vertices vertices = this.layers.get(layer);
			if (vertices != null) {
				vertices.build(consumer);
			}
		}

		@Override
		public void submit(SubmitNodeCollector collector, CameraRenderState camera) {
			if (this.count > 0) {
				collector.submitQuadParticleGroup(this);
			}
		}
	}

	/** A growable list of vertices: position and UV as floats, colour and light as ints. */
	private static final class Vertices {
		private float[] floats = new float[1024 * 5];
		private int[] ints = new int[1024 * 2];
		private int size;

		void add(float x, float y, float z, float u, float v, int color, int light) {
			if (this.size * 2 >= this.ints.length) {
				this.floats = Arrays.copyOf(this.floats, this.floats.length * 2);
				this.ints = Arrays.copyOf(this.ints, this.ints.length * 2);
			}
			int f = this.size * 5, i = this.size * 2;
			this.floats[f] = x;
			this.floats[f + 1] = y;
			this.floats[f + 2] = z;
			this.floats[f + 3] = u;
			this.floats[f + 4] = v;
			this.ints[i] = color;
			this.ints[i + 1] = light;
			this.size++;
		}

		void build(VertexConsumer consumer) {
			for (int n = 0; n < this.size; n++) {
				int f = n * 5, i = n * 2;
				consumer.addVertex(this.floats[f], this.floats[f + 1], this.floats[f + 2]).setUv(this.floats[f + 3], this.floats[f + 4])
					.setColor(this.ints[i]).setLight(this.ints[i + 1]);
			}
		}

		void clear() {
			this.size = 0;
		}
	}
}
