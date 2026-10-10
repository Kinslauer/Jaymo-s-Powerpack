package com.jaymoskitchen.client.horse;

import com.jaymoskitchen.JaymosKitchen;
import com.jaymoskitchen.horse.NiftyCarts;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.util.Mth;

/**
 * The supply cart, animal cart and plow models, ported from NiftyCarts (Copyright (c) 2019 MennoMax, (c) 2023
 * jmb19905; MIT licensed, see LICENSE_niftycarts). Each has a body (pitched as a whole) and a pair of eight-sided
 * spoked wheels; their textures are put together from the cart's wood (see {@link CartTextures}).
 */
final class NiftyCartModels {
	static final ModelLayerLocation SUPPLY_CART = new ModelLayerLocation(JaymosKitchen.id("supply_cart"), "main");
	static final ModelLayerLocation ANIMAL_CART = new ModelLayerLocation(JaymosKitchen.id("animal_cart"), "main");
	static final ModelLayerLocation PLOW = new ModelLayerLocation(JaymosKitchen.id("plow"), "main");
	static final ModelLayerLocation WAGON = new ModelLayerLocation(JaymosKitchen.id("wagon"), "main");

	private NiftyCartModels() {
	}

	/** The two wheels on their axle: rimLength is a side of the octagon, axleLength the gap between the wheels. */
	private static MeshDefinition wheels(float rimLength, float axleLength) {
		MeshDefinition mesh = new MeshDefinition();
		float xOffset = axleLength / 2.0F + 2.0F;
		wheel("leftWheel_0", xOffset, rimLength, -2.0F).build(mesh.getRoot());
		wheel("rightWheel_0", -xOffset, rimLength, 0.0F).build(mesh.getRoot());
		return mesh;
	}

	private static NiftyMesh wheel(String name, float xOffset, float rimLength, float boxOffset) {
		float radius = (rimLength / 2.0F) / (Mth.sin(Mth.PI / 8.0F) / Mth.cos(Mth.PI / 8.0F));
		NiftyMesh wheel = new NiftyMesh(name, 46, 60);
		wheel.setPivot(xOffset, -radius, 1.0F);
		wheel.addBox(boxOffset, -1.0F, -1.0F, 2, 2, 2);
		for (int i = 0; i < 8; i++) {
			NiftyMesh rim = new NiftyMesh(name + "_rim_" + i, 58, 64 - ((int) rimLength + 1));
			float epsilon = 0.001F * i % 2;
			rim.addBox(boxOffset + epsilon, -rimLength / 2.0F, radius - 1.0F, 2, rimLength, 1);
			rim.xRot = i * Mth.PI / 4.0F;
			wheel.addChild(rim);
			NiftyMesh spoke = new NiftyMesh(name + "_spoke_" + i, 54, 64 - Mth.ceil(radius - 1.0F));
			spoke.addBox(0.5F + boxOffset, 1.0F, -0.5F, 1, radius - 2.0F, 1);
			spoke.xRot = i * Mth.PI / 4.0F;
			wheel.addChild(spoke);
		}
		return wheel;
	}

	private static NiftyMesh body() {
		NiftyMesh body = new NiftyMesh("body");
		body.setPivot(0.0F, -NiftyCarts.WHEEL_RADIUS, 1.0F);
		return body;
	}

	static LayerDefinition supplyCart() {
		MeshDefinition mesh = wheels(9, 25);
		NiftyMesh boardBottom = new NiftyMesh("boardBottom", 0, 0);
		boardBottom.addBox(-15.5F, -11.0F, -2.0F, 29, 22, 1);
		boardBottom.yRot = -Mth.PI / 2.0F;
		boardBottom.xRot = -Mth.PI / 2.0F;
		NiftyMesh axis = new NiftyMesh("axis", 0, 23);
		axis.addBox(-12.5F, -1.0F, -1.0F, 25, 2, 2);
		NiftyMesh shaft = new NiftyMesh("shaft", 0, 28);
		shaft.setPivot(0.0F, -5.0F, -15.0F);
		shaft.yRot = Mth.PI / 2.0F;
		shaft.addBox(0.0F, -2.5F, -8.0F, 20, 2, 1);
		shaft.addBox(0.0F, -2.5F, 7.0F, 20, 2, 1);
		NiftyMesh boardFront = new NiftyMesh("boardFront", 0, 31);
		boardFront.addBox(-12.0F, -12.0F, -15.5F, 24, 10, 1);
		NiftyMesh[] sides = new NiftyMesh[4];
		for (int i = 0; i < 4; i++) {
			sides[i] = new NiftyMesh("boards_side_" + i, 0, 27);
			boolean left = i % 2 == 0;
			sides[i].addBox(left ? -13.5F : -14.5F, i < 2 ? -7.0F : -2.0F, 0.0F, 28, 3, 1);
			sides[i].setPivot(left ? -11.0F : 11.0F, -5.0F, -1.0F);
			sides[i].yRot = left ? -Mth.PI / 2.0F : Mth.PI / 2.0F;
		}
		NiftyMesh rear0 = new NiftyMesh("boards_rear_0", 42, 0);
		rear0.addBox(10.0F, -12.0F, 13.5F, 2, 11, 1);
		NiftyMesh rear1 = new NiftyMesh("boards_rear_1", 42, 0);
		rear1.addBox(-12.0F, -12.0F, 13.5F, 2, 11, 1);
		NiftyMesh body = body();
		body.addChild(axis);
		body.addChild(shaft);
		body.addChild(boardBottom);
		body.addChild(boardFront);
		body.addChild(rear0);
		body.addChild(rear1);
		for (NiftyMesh side : sides) {
			body.addChild(side);
		}
		body.build(mesh.getRoot());
		return LayerDefinition.create(mesh, 64, 64);
	}

	/**
	 * The wagon: the supply cart's frame (its bed's top is 2 px above the axle), with a plank bench up front for a
	 * passenger. Its chest is vanilla's, drawn by CartRenderer at the back.
	 */
	static LayerDefinition wagon() {
		MeshDefinition mesh = wheels(9, 25);
		NiftyMesh boardBottom = new NiftyMesh("boardBottom", 0, 0);
		boardBottom.addBox(-15.5F, -11.0F, -2.0F, 29, 22, 1);
		boardBottom.yRot = -Mth.PI / 2.0F;
		boardBottom.xRot = -Mth.PI / 2.0F;
		NiftyMesh axis = new NiftyMesh("axis", 0, 23);
		axis.addBox(-12.5F, -1.0F, -1.0F, 25, 2, 2);
		NiftyMesh shaft = new NiftyMesh("shaft", 0, 28);
		shaft.setPivot(0.0F, -5.0F, -15.0F);
		shaft.yRot = Mth.PI / 2.0F;
		shaft.addBox(0.0F, -2.5F, -8.0F, 20, 2, 1);
		shaft.addBox(0.0F, -2.5F, 7.0F, 20, 2, 1);
		NiftyMesh boardFront = new NiftyMesh("boardFront", 0, 31);
		boardFront.addBox(-12.0F, -12.0F, -15.5F, 24, 10, 1);
		NiftyMesh[] sides = new NiftyMesh[2];
		for (int i = 0; i < 2; i++) {
			boolean left = i == 0;
			sides[i] = new NiftyMesh("boards_side_" + i, 0, 27);
			sides[i].addBox(left ? -13.5F : -14.5F, -7.0F, 0.0F, 28, 3, 1);
			sides[i].setPivot(left ? -11.0F : 11.0F, -5.0F, -1.0F);
			sides[i].yRot = left ? -Mth.PI / 2.0F : Mth.PI / 2.0F;
		}
		NiftyMesh rear0 = new NiftyMesh("boards_rear_0", 42, 0);
		rear0.addBox(10.0F, -12.0F, 13.5F, 2, 11, 1);
		NiftyMesh rear1 = new NiftyMesh("boards_rear_1", 42, 0);
		rear1.addBox(-12.0F, -12.0F, 13.5F, 2, 11, 1);
		// the bench: a plank seat on two legs across the front, with a backrest
		NiftyMesh bench = new NiftyMesh("bench", 0, 0);
		bench.addBox(-10.0F, -8.0F, -14.0F, 20, 2, 5);
		bench.addBox(-9.0F, -6.0F, -13.0F, 2, 4, 2);
		bench.addBox(7.0F, -6.0F, -13.0F, 2, 4, 2);
		bench.addBox(-10.0F, -14.0F, -9.5F, 20, 6, 1);
		NiftyMesh body = body();
		body.addChild(axis);
		body.addChild(shaft);
		body.addChild(boardBottom);
		body.addChild(boardFront);
		body.addChild(rear0);
		body.addChild(rear1);
		body.addChild(sides[0]);
		body.addChild(sides[1]);
		body.addChild(bench);
		body.build(mesh.getRoot());
		return LayerDefinition.create(mesh, 64, 64);
	}

	static LayerDefinition animalCart() {
		MeshDefinition mesh = wheels(9, 25);
		NiftyMesh axis = new NiftyMesh("axis", 0, 21);
		axis.addBox(-12.5F, -1.0F, -1.0F, 25, 2, 2);
		NiftyMesh cartBase = new NiftyMesh("cartBase", 0, 0);
		cartBase.addBox(-15.5F, -10.0F, -2.0F, 29, 20, 1);
		cartBase.xRot = -Mth.PI / 2.0F;
		cartBase.yRot = -Mth.PI / 2.0F;
		NiftyMesh shaft = new NiftyMesh("shaft", 0, 25);
		shaft.setPivot(0.0F, -5.0F, -15.0F);
		shaft.yRot = Mth.PI / 2.0F;
		shaft.addBox(0.0F, -0.5F, -8.0F, 20, 2, 1);
		shaft.addBox(0.0F, -0.5F, 7.0F, 20, 2, 1);
		NiftyMesh boardLeft = new NiftyMesh("boardLeft", 0, 28);
		boardLeft.addBox(-10.0F, -14.5F, 9.0F, 8, 31, 2);
		boardLeft.xRot = -Mth.PI / 2.0F;
		boardLeft.zRot = Mth.PI / 2.0F;
		NiftyMesh boardRight = new NiftyMesh("boardRight", 0, 28);
		boardRight.addBox(-10.0F, -14.5F, -11.0F, 8, 31, 2);
		boardRight.xRot = -Mth.PI / 2.0F;
		boardRight.zRot = Mth.PI / 2.0F;
		NiftyMesh boardBack = new NiftyMesh("boardBack", 20, 28);
		boardBack.addBox(-9.0F, -10.0F, 12.5F, 18, 8, 2);
		NiftyMesh boardFront = new NiftyMesh("boardFront", 20, 28);
		boardFront.addBox(-9.0F, -10.0F, -16.5F, 18, 8, 2);
		NiftyMesh body = body();
		body.addChild(axis);
		body.addChild(cartBase);
		body.addChild(shaft);
		body.addChild(boardLeft);
		body.addChild(boardRight);
		body.addChild(boardBack);
		body.addChild(boardFront);
		body.build(mesh.getRoot());
		return LayerDefinition.create(mesh, 64, 64);
	}

	static LayerDefinition plow() {
		MeshDefinition mesh = wheels(9, 25);
		NiftyMesh axis = new NiftyMesh("axis", 0, 0);
		axis.addBox(-12.5F, -1.0F, -1.0F, 25, 2, 2);
		NiftyMesh triangle0 = new NiftyMesh("triangle_0", 0, 4);
		triangle0.addBox(-7.5F, -9.0F, 0.0F, 15, 2, 2);
		NiftyMesh triangle1 = new NiftyMesh("triangle_1", 0, 11);
		triangle1.addBox(-5.0F, -9.0F, 0.5F, 2, 14, 2);
		triangle1.zRot = -0.175F;
		NiftyMesh triangle2 = new NiftyMesh("triangle_2", 0, 11);
		triangle2.mirror(true);
		triangle2.addBox(3.0F, -9.0F, 0.5F, 2, 14, 2);
		triangle2.zRot = 0.175F;
		NiftyMesh shaft = new NiftyMesh("shaft", 0, 8);
		shaft.zRot = -0.07F;
		shaft.addBox(0.0F, 0.0F, -8.0F, 20, 2, 1);
		shaft.addBox(0.0F, 0.0F, 7.0F, 20, 2, 1);
		NiftyMesh shaftConnector = new NiftyMesh("shaftConnector", 0, 27);
		shaftConnector.zRot = -0.26F;
		shaftConnector.addBox(-16.0F, 0.0F, -8.0F, 16, 2, 1);
		shaftConnector.addBox(-16.0F, 0.0F, 7.0F, 16, 2, 1);
		NiftyMesh shafts = new NiftyMesh("shafts");
		shafts.setPivot(0.0F, 0.0F, -14.0F);
		shafts.yRot = Mth.PI / 2.0F;
		shafts.addChild(shaft);
		shafts.addChild(shaftConnector);
		NiftyMesh[] upper = new NiftyMesh[3];
		for (int i = 0; i < upper.length; i++) {
			upper[i] = new NiftyMesh("plow_shaft_upper_" + i, 56, 0);
			upper[i].addBox(-1.0F, -2.0F, -2.0F, 2, 30, 2);
			upper[i].setPivot(-3.0F + 3 * i, -7.0F, 0.0F);
			upper[i].yRot = -0.523599F + Mth.PI / 6.0F * i;
			NiftyMesh lower = new NiftyMesh("plow_shaft_lower_" + i, 42, 4);
			lower.addBox(-1.0F, -0.7F, -0.7F, 2, 10, 2);
			lower.setPivot(0.0F, 28.0F, -1.0F);
			lower.xRot = Mth.PI / 4.0F;
			upper[i].addChild(lower);
		}
		NiftyMesh handle = new NiftyMesh("plow_handle", 50, 4);
		handle.addBox(-0.5F, 0.0F, -0.5F, 1, 18, 1);
		handle.setPivot(0.0F, 33.0F, 5.0F);
		handle.xRot = Mth.PI / 2.0F;
		upper[1].addChild(handle);
		NiftyMesh grip = new NiftyMesh("plow_handle_grip", 50, 23);
		grip.addBox(-0.5F, 0.0F, -1.0F, 1, 5, 1);
		grip.setPivot(0.0F, 32.8F, 21.0F);
		grip.xRot = Mth.PI / 4.0F;
		upper[1].addChild(grip);
		NiftyMesh parts = new NiftyMesh("parts");
		parts.setPivot(0.0F, -5.0F, -1.0F);
		parts.addChild(shafts);
		parts.addChild(triangle0);
		parts.addChild(triangle1);
		parts.addChild(triangle2);
		for (NiftyMesh arm : upper) {
			parts.addChild(arm);
		}
		NiftyMesh body = body();
		body.addChild(axis);
		body.addChild(parts);
		body.build(mesh.getRoot());
		return LayerDefinition.create(mesh, 64, 64);
	}
}
