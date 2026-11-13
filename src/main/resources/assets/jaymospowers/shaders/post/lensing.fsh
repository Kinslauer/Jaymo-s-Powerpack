#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D InSampler;

layout(location = 0) in vec2 texCoord;

layout(std140) uniform LensConfig {
    vec2 Centre;
    float Radius;
    float Aspect;
    float Strength;
};

layout(location = 0) out vec4 fragColor;

// Light bending round a black hole. Centre is where the hole is on the screen (0 to 1 both ways), Radius how far the
// bending reaches as a share of the screen's height, Aspect the screen's width over its height, Strength 0 to 1.
// What is seen at a distance r from the hole is what lies at r - ring*ring/r behind it (a point mass lens), so the
// world stretches into an arc round the dark middle. The bending fades to nothing at Radius, so there is no edge.
void main() {
    vec2 d = texCoord - Centre;
    d.x *= Aspect;
    float r = length(d);
    if (Strength <= 0.0 || Radius <= 0.0 || r >= Radius) {
        fragColor = vec4(texture(InSampler, texCoord).rgb, 1.0);
        return;
    }
    float ring = Radius * 0.3;
    float fade = 1.0 - smoothstep(0.55, 1.0, r / Radius);
    float bend = Strength * fade * ring * ring / max(r, 0.0001);
    vec2 from = d - (d / max(r, 0.0001)) * bend;
    from.x /= Aspect;
    vec2 uv = clamp(Centre + from, vec2(0.001), vec2(0.999));
    vec3 color = texture(InSampler, uv).rgb;

    // the dark middle, and the thin bright ring where the light goes round
    float hole = ring * 0.62;
    float dark = (1.0 - smoothstep(hole * 0.85, hole, r)) * Strength;
    float glow = exp(-pow((r - hole * 1.12) / (hole * 0.10), 2.0)) * 0.55 * Strength;
    color = mix(color, vec3(0.0), dark) + vec3(1.0, 0.86, 0.62) * glow;
    fragColor = vec4(color, 1.0);
}
