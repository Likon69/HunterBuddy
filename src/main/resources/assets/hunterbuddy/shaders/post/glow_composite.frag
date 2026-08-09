#version 330 core

// Outline and glow, both derived from the distance to the nearest silhouette
// texel. Three zones:
//
//   inside the silhouette        -> optional semi-transparent fill
//   within the line thickness    -> crisp outline, antialiased on its outer edge
//   beyond, out to the radius    -> glow, quadratic falloff from the line
//
// Distance, not blur. A gaussian's alpha follows how much surface the target
// covers, so a thin target glowed far weaker than a wide one at the same
// distance from its edge, concave corners glowed harder than convex ones, and
// two nearby targets grew a bridge of light between them. A distance field has
// none of those: the ribbon only depends on how far the pixel is from an edge,
// which is what Future's shader does and what makes it read the same on a
// firework rocket and on a player.
//
// Vanilla's entity_outline sobel is suppressed by WorldRendererOutlineMixin
// while this runs, so the line here is the only one on screen and its width is
// ours to choose.

in vec2 v_TexCoord;
in vec2 v_OneTexel;

uniform sampler2D u_Origin; // raw silhouettes
uniform sampler2D u_Mask;   // horizontally dilated coverage

layout (std140) uniform GlowData {
    float u_Thickness;
    float u_Radius;
    float u_Intensity;
    float u_FillOpacity;
    int u_Quality;
    int u_Flags; // bit 0 = glow, bit 1 = fill, bit 2 = outline
};

out vec4 color;

void main() {
    vec4 origin = texture(u_Origin, v_TexCoord);

    // ── Inside the silhouette ────────────────────────────────────────────────
    if (origin.a > 0.0) {
        if ((u_Flags & 2) == 0) discard;

        color = vec4(origin.rgb, origin.a * u_FillOpacity);
        return;
    }

    bool wantOutline = (u_Flags & 4) != 0 && u_Thickness > 0.0;
    bool wantGlow = (u_Flags & 1) != 0 && u_Radius > 0.0 && u_Intensity > 0.0;
    if (!wantOutline && !wantGlow) discard;

    // A sub-pixel line still has to probe at least one texel out, or the nearest
    // possible neighbour — which sits at exactly distance 1 — gets rejected and
    // nothing is drawn at all. The requested thickness survives as the opacity.
    float thickness = wantOutline ? max(u_Thickness, 1.0) : 0.0;
    float lineAlpha = wantOutline ? clamp(u_Thickness, 0.0, 1.0) : 0.0;
    float radius = wantGlow ? u_Radius : 0.0;

    // One texel of slack so the outer edge of the line can be faded.
    float maxDist = thickness + radius + 1.0;
    int reach = int(ceil(maxDist));

    // ── Rejection ────────────────────────────────────────────────────────────
    // Vertical half of the separable dilation. Zero means nothing within reach.
    float coverage = 0.0;
    for (int i = -reach; i <= reach; i++) {
        coverage = max(coverage, texture(u_Mask, v_TexCoord + vec2(0.0, float(i)) * v_OneTexel).a);
        if (coverage >= 1.0) break;
    }
    if (coverage <= 0.0) discard;

    // ── Distance to the nearest silhouette texel ─────────────────────────────
    float nearest = maxDist + 1.0;
    int bestX = 0;
    int bestY = 0;

    // Coarse sweep, then a refine pass around the winner. At the default radius
    // the stride is 1 and the refine is skipped; it only earns its keep at the
    // top of the slider, where a full search is thousands of fetches.
    int stride = max(1, reach / max(u_Quality * 6, 1));

    for (int y = -reach; y <= reach; y += stride) {
        for (int x = -reach; x <= reach; x += stride) {
            if (x == 0 && y == 0) continue;

            float dist = length(vec2(float(x), float(y)));
            if (dist > maxDist || dist >= nearest) continue;

            if (texture(u_Origin, v_TexCoord + vec2(float(x), float(y)) * v_OneTexel).a > 0.0) {
                nearest = dist;
                bestX = x;
                bestY = y;
            }
        }
    }

    if (nearest > maxDist) discard;

    if (stride > 1) {
        for (int y = bestY - stride; y <= bestY + stride; y++) {
            for (int x = bestX - stride; x <= bestX + stride; x++) {
                if (x == 0 && y == 0) continue;

                float dist = length(vec2(float(x), float(y)));
                if (dist > maxDist || dist >= nearest) continue;

                if (texture(u_Origin, v_TexCoord + vec2(float(x), float(y)) * v_OneTexel).a > 0.0) {
                    nearest = dist;
                    bestX = x;
                    bestY = y;
                }
            }
        }
    }

    vec3 rgb = texture(u_Origin, v_TexCoord + vec2(float(bestX), float(bestY)) * v_OneTexel).rgb;

    // ── The two zones, blended by whichever is stronger ──────────────────────
    // Taking the max rather than branching keeps the line and the glow
    // continuous where they meet, so the antialiased edge has no seam.
    float outlineA = wantOutline ? lineAlpha * clamp(thickness + 1.0 - nearest, 0.0, 1.0) : 0.0;

    float glowA = 0.0;
    if (wantGlow) {
        float d = max(nearest - thickness, 0.0);
        if (d <= radius) {
            float falloff = 1.0 - d / radius;
            glowA = falloff * falloff * u_Intensity;
        }
    }

    float alpha = max(outlineA, glowA);
    if (alpha <= 0.004) discard;

    color = vec4(rgb, alpha);
}
