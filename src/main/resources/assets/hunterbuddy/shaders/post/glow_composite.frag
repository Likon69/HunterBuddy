#version 330 core

// Second half of the separable gaussian (vertical), composited straight onto
// the main framebuffer:
//   - inside the silhouette  -> optional semi-transparent fill
//   - outside the silhouette -> the glow halo
// The crisp 1px vanilla outline is drawn by the vanilla pipeline underneath, so
// this pass only ever adds to it.

in vec2 v_TexCoord;
in vec2 v_OneTexel;

uniform sampler2D u_Texture; // horizontally blurred silhouette
uniform sampler2D u_Origin;  // raw silhouette

layout (std140) uniform GlowData {
    vec2 u_Direction;
    float u_Radius;
    int u_Samples;
    float u_Intensity;
    float u_FillOpacity;
    int u_Flags; // bit 0 = glow, bit 1 = fill
};

out vec4 color;

void main() {
    vec4 origin = texture(u_Origin, v_TexCoord);

    if (origin.a > 0.0) {
        if ((u_Flags & 2) == 0) discard;

        color = vec4(origin.rgb, origin.a * u_FillOpacity);
        return;
    }

    if ((u_Flags & 1) == 0) discard;

    float radius = max(u_Radius, 0.0);
    if (radius <= 0.0) discard;

    int taps = max(u_Samples, 1);
    float sigma = max(radius * 0.5, 0.0001);
    float twoSigmaSq = 2.0 * sigma * sigma;
    float stepSize = radius / float(taps);

    vec3 rgbSum = vec3(0.0);
    float alphaSum = 0.0;
    float weightSum = 0.0;

    for (int i = -taps; i <= taps; i++) {
        float offset = float(i) * stepSize;
        float weight = exp(-(offset * offset) / twoSigmaSq);
        vec4 texel = texture(u_Texture, v_TexCoord + u_Direction * v_OneTexel * offset);

        rgbSum += texel.rgb * texel.a * weight;
        alphaSum += texel.a * weight;
        weightSum += weight;
    }

    if (alphaSum <= 0.0) discard;

    vec3 glowRgb = rgbSum / alphaSum;
    float glowAlpha = min((alphaSum / weightSum) * u_Intensity * 8.0, 1.0);

    if (glowAlpha <= 0.004) discard;

    color = vec4(glowRgb, glowAlpha);
}
