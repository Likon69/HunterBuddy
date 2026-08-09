#version 330 core

// Horizontal half of a separable dilation over the silhouette buffer.
//
// This used to be half of a gaussian, and the composite finished the blur to
// produce the glow. That model was wrong: the alpha of a blur depends on how
// much surface the target covers, so a firework rocket glowed far weaker than a
// player's torso at the same distance from the edge. The glow is now a distance
// field computed in the composite instead.
//
// The pass survives as a pure rejection mask. A true distance search costs a few
// hundred texture fetches per pixel, and most of the screen is nowhere near a
// silhouette. Dilation is a max, so it is exact and separable: after the
// horizontal half here and the vertical half in the composite, a zero means no
// silhouette texel lies within the radius, and the expensive search is skipped
// with no risk of a false negative.
//
// Only alpha carries information; the colour is resolved from the silhouette
// itself once the nearest texel is known.

in vec2 v_TexCoord;
in vec2 v_OneTexel;

uniform sampler2D u_Texture;

layout (std140) uniform MaskData {
    float u_Radius;
};

out vec4 color;

void main() {
    int reach = int(ceil(max(u_Radius, 0.0)));
    float coverage = 0.0;

    for (int i = -reach; i <= reach; i++) {
        coverage = max(coverage, texture(u_Texture, v_TexCoord + vec2(float(i), 0.0) * v_OneTexel).a);
        if (coverage >= 1.0) break;
    }

    color = vec4(0.0, 0.0, 0.0, coverage);
}
