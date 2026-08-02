#version 330 core

// First half of a separable gaussian blur over the entity silhouette buffer.
// The vertical half lives in glow_composite.frag so the second pass can also do
// the fill/compositing without an extra fullscreen draw.

in vec2 v_TexCoord;
in vec2 v_OneTexel;

uniform sampler2D u_Texture;

layout (std140) uniform BlurData {
    vec2 u_Direction;
    float u_Radius;
    int u_Samples;
};

out vec4 color;

void main() {
    float radius = max(u_Radius, 0.0);

    if (radius <= 0.0) {
        color = texture(u_Texture, v_TexCoord);
        return;
    }

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

        // Premultiplied accumulation, otherwise fully transparent texels drag
        // the silhouette colour towards black.
        rgbSum += texel.rgb * texel.a * weight;
        alphaSum += texel.a * weight;
        weightSum += weight;
    }

    if (alphaSum <= 0.0) {
        color = vec4(0.0);
        return;
    }

    color = vec4(rgbSum / alphaSum, alphaSum / weightSum);
}
