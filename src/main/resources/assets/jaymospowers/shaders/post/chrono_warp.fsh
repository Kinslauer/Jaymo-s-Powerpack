#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D InSampler;

layout(location = 0) in vec2 texCoord;

layout(std140) uniform WarpConfig {
    float Strength;
    float Time;
};

layout(location = 0) out vec4 fragColor;

// A Chrono's time skip, on the runner's own screen: the picture is pulled in toward the middle and streaked outward from
// it (a radial blur), with rings running out across it and the colours splitting at the edges. Strength 0 to 1; Time runs
// the rings.
void main() {
    vec2 d = texCoord - 0.5;
    float r = length(d);
    vec2 dir = d / max(r, 0.0001);
    float ring = sin(r * 30.0 - Time * 0.6) * 0.010 * Strength * smoothstep(0.05, 0.6, r);
    vec3 color = vec3(0.0);
    for (int i = 0; i < 7; i++) {
        float k = float(i) / 6.0;
        vec2 uv = texCoord - d * (0.16 * Strength * k) + dir * ring;
        color += texture(InSampler, clamp(uv, vec2(0.001), vec2(0.999))).rgb;
    }
    color /= 7.0;
    float split = 0.012 * Strength * r;
    color.r = mix(color.r, texture(InSampler, clamp(texCoord - dir * split, vec2(0.001), vec2(0.999))).r, 0.6);
    color.b = mix(color.b, texture(InSampler, clamp(texCoord + dir * split, vec2(0.001), vec2(0.999))).b, 0.6);
    // the edges brighten with the speed
    color += vec3(0.55, 0.75, 1.0) * smoothstep(0.35, 0.8, r) * 0.22 * Strength;
    fragColor = vec4(color, 1.0);
}
