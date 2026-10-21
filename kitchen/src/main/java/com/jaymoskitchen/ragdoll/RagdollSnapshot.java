package com.jaymoskitchen.ragdoll;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * (0.5.1) A ragdoll as it is this tick, small enough to send every tick: which rig (shape and standing size), where its
 * middle is, and every point as a short offset from there (1/256 of a block). Asleep means it won't move again until
 * something wakes it, so the receiver can stop expecting more.
 */
public record RagdollSnapshot(int shape, float width, float height, double x, double y, double z, short[] points,
	boolean asleep) {
	/** Offsets are kept to this fraction of a block. */
	private static final float UNIT = 256.0F;

	public static final StreamCodec<ByteBuf, RagdollSnapshot> CODEC = StreamCodec.of(
		(buf, s) -> {
			buf.writeByte(s.shape);
			buf.writeFloat(s.width);
			buf.writeFloat(s.height);
			buf.writeDouble(s.x);
			buf.writeDouble(s.y);
			buf.writeDouble(s.z);
			buf.writeByte(s.points.length);
			for (short p : s.points) {
				buf.writeShort(p);
			}
			buf.writeBoolean(s.asleep);
		},
		buf -> {
			int shape = buf.readByte();
			float width = buf.readFloat(), height = buf.readFloat();
			double x = buf.readDouble(), y = buf.readDouble(), z = buf.readDouble();
			int n = buf.readUnsignedByte();
			short[] points = new short[n];
			for (int i = 0; i < n; i++) {
				points[i] = buf.readShort();
			}
			return new RagdollSnapshot(shape, width, height, x, y, z, points, buf.readBoolean());
		});

	public static RagdollSnapshot of(RagdollBody body) {
		double[] m = body.middle();
		int n = body.rig.count;
		short[] points = new short[n * 3];
		for (int i = 0; i < n; i++) {
			points[i * 3] = quantize(body.x[i] - m[0]);
			points[i * 3 + 1] = quantize(body.y[i] - m[1]);
			points[i * 3 + 2] = quantize(body.z[i] - m[2]);
		}
		RagdollRig rig = body.rig;
		return new RagdollSnapshot(rig.shape.ordinal(), rig.width, rig.height, m[0], m[1], m[2], points, body.asleep());
	}

	private static short quantize(double offset) {
		return (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(offset * UNIT)));
	}

	public RagdollRig rig() {
		return RagdollRig.of(RagdollRig.Shape.byId(shape), width, height);
	}

	/** True if this snapshot is for a body of {@code rig}'s shape and size. */
	public boolean fits(RagdollRig rig) {
		return rig.shape.ordinal() == shape && rig.width == width && rig.height == height && rig.count * 3 == points.length;
	}

	public int count() {
		return points.length / 3;
	}

	public double pointX(int i) {
		return x + points[i * 3] / UNIT;
	}

	public double pointY(int i) {
		return y + points[i * 3 + 1] / UNIT;
	}

	public double pointZ(int i) {
		return z + points[i * 3 + 2] / UNIT;
	}

	/** A body standing in this snapshot's points, at rest (to carry a ragdoll from one owner to another). */
	public RagdollBody body() {
		RagdollRig rig = rig();
		if (count() != rig.count) {
			return null;
		}
		RagdollBody body = new RagdollBody(rig);
		float[] rel = new float[points.length];
		for (int i = 0; i < points.length; i++) {
			rel[i] = points[i] / UNIT;
		}
		return body.restore(rel, x, y, z) ? body : null;
	}
}
