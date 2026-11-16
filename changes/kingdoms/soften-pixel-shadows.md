# Soften pixel shadows' edges

Pixel shadows from the sun and moon now fade smoothly at their edges instead of cutting hard from dark to light. The shade itself stays as dark as before. Shaders on still disable them.

- com.jaymoskingdoms.client.disasters.SunShade: each shadow quad's corners shade by the average of the cells around that corner, so edges fade into a ramp instead of a hard cut.
- Built; not played in game.