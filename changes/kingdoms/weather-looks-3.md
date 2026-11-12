# Weather and looks, part 3: pixel shadows and water caustics with shaders off

- **Pixel shadows** (Andy: "you can build those"). With no shader pack on, walls, roofs, cliffs and tree trunks now throw hard, blocky shadows on the ground near you. They swing round and stretch as the Sun crosses the sky, leaves throw a dappled shade, and slabs, stairs and fences cast their own shapes. They fade as the Sun nears the horizon and in rain, and there are none at night. Only open ground under the sky gets them (not floors indoors, and not the sides of blocks), within 14 blocks of you.
- **Water caustics**. With no shader pack on, a rippling web of light plays over the bed of open water up to 10 blocks deep, brightest in the shallows, by day only.
- Both go the moment a shader pack is on (Complementary casts its own), and both are our own: ImmersiveVFX's ideas, none of its code or art. The caustics frames are drawn by `tools/caustics_art.py`.
