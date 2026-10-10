package com.jaymoskitchen.client.horse;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * A small builder for model parts with child parts, ported from NiftyCarts' EasyMeshBuilder (Copyright (c) 2019
 * MennoMax, (c) 2023 jmb19905; MIT licensed, see LICENSE_niftycarts). Used to build their cart models.
 */
final class NiftyMesh {
	private final String name;
	float x, y, z;
	float xRot, yRot, zRot;
	private final CubeListBuilder cubes = CubeListBuilder.create();
	private final List<NiftyMesh> children = new ArrayList<>();

	NiftyMesh(String name) {
		this.name = name;
	}

	NiftyMesh(String name, int u, int v) {
		this(name);
		texOffs(u, v);
	}

	NiftyMesh texOffs(int u, int v) {
		cubes.texOffs(u, v);
		return this;
	}

	NiftyMesh mirror(boolean mirror) {
		cubes.mirror(mirror);
		return this;
	}

	NiftyMesh addBox(float x, float y, float z, float width, float height, float depth) {
		cubes.addBox(x, y, z, width, height, depth);
		return this;
	}

	void addChild(NiftyMesh child) {
		children.add(child);
	}

	void setPivot(float x, float y, float z) {
		this.x = x;
		this.y = y;
		this.z = z;
	}

	void build(PartDefinition parent) {
		PartDefinition part = parent.addOrReplaceChild(name, cubes, PartPose.offsetAndRotation(x, y, z, xRot, yRot, zRot));
		for (NiftyMesh child : children) {
			child.build(part);
		}
	}
}
