#version 330 core

layout (std140) uniform SkyData {
    mat4 InvViewProj;
    vec4 Params;
    vec4 Color0;
    vec4 Color1;
    vec4 Color2;
    vec4 Misc;
    vec4 Fog;
};

in vec2 v_Ndc;

out vec4 fragColor;

const float TAU = 6.2831853;
const float STAR_SCALE = 110.0;
const float STAR_CHANCE = 0.05;
const float LIGHTNING_PERIOD = 7.0;

float hash11(float p) {
    p = fract(p * 0.1031);
    p *= p + 33.33;
    p *= p + p;
    return fract(p);
}

float hash21(vec2 p) {
    vec3 q = fract(vec3(p.xyx) * 0.1031);
    q += dot(q, q.yzx + 33.33);
    return fract((q.x + q.y) * q.z);
}

float hash31(vec3 p) {
    vec3 q = fract(p * 0.1031);
    q += dot(q, q.zyx + 31.32);
    return fract((q.x + q.y) * q.z);
}

vec3 hash33(vec3 p) {
    vec3 q = fract(p * vec3(0.1031, 0.1030, 0.0973));
    q += dot(q, q.yxz + 33.33);
    return fract((q.xxy + q.yxx) * q.zyx);
}

float valueNoise2(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    float a = hash21(i);
    float b = hash21(i + vec2(1.0, 0.0));
    float c = hash21(i + vec2(0.0, 1.0));
    float e = hash21(i + vec2(1.0, 1.0));
    return mix(mix(a, b, u.x), mix(c, e, u.x), u.y);
}

float valueNoise3(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    vec3 u = f * f * (3.0 - 2.0 * f);
    float a = hash31(i);
    float b = hash31(i + vec3(1.0, 0.0, 0.0));
    float c = hash31(i + vec3(0.0, 1.0, 0.0));
    float e = hash31(i + vec3(1.0, 1.0, 0.0));
    float g = hash31(i + vec3(0.0, 0.0, 1.0));
    float h = hash31(i + vec3(1.0, 0.0, 1.0));
    float k = hash31(i + vec3(0.0, 1.0, 1.0));
    float m = hash31(i + vec3(1.0, 1.0, 1.0));
    return mix(mix(mix(a, b, u.x), mix(c, e, u.x), u.y),
               mix(mix(g, h, u.x), mix(k, m, u.x), u.y), u.z);
}

float fbm2(vec2 p) {
    float sum = 0.0;
    float amplitude = 0.5;
    for (int i = 0; i < 4; i++) {
        sum += amplitude * valueNoise2(p);
        p = p * 2.02 + vec2(17.1, 9.3);
        amplitude *= 0.5;
    }
    return sum / 0.9375;
}

float fbm3(vec3 p) {
    float sum = 0.0;
    float amplitude = 0.5;
    for (int i = 0; i < 4; i++) {
        sum += amplitude * valueNoise3(p);
        p = p * 2.02 + vec3(17.1, 9.3, 5.7);
        amplitude *= 0.5;
    }
    return sum / 0.9375;
}

vec3 stars(vec3 dir, float density, float t) {
    vec3 p = dir * STAR_SCALE;
    vec3 cell = floor(p);
    if (hash31(cell) >= density * STAR_CHANCE) return vec3(0.0);

    vec3 h = hash33(cell);
    float dist = length(p - cell - (0.3 + 0.4 * h));
    float size = 0.05 + 0.09 * h.x;
    float core = 1.0 - smoothstep(0.0, size, dist);
    float twinkle = 0.65 + 0.35 * sin(t * (1.5 + 3.0 * h.y) + h.z * TAU);
    vec3 tint = mix(vec3(1.0), mix(vec3(0.7, 0.8, 1.0), vec3(1.0, 0.85, 0.7), h.y), 0.6);
    return tint * core * core * twinkle * (0.5 + 0.5 * h.x) * 1.6;
}

float nightFactor() {
    if (Misc.x < 0.0) return 1.0;
    float sunHeight = cos((Misc.x - 0.25) * TAU);
    return 1.0 - smoothstep(-0.15, 0.1, sunHeight);
}

vec3 sunDirection() {
    if (Misc.x < 0.0) return normalize(vec3(-0.8, 0.08, -0.6));
    float angle = (Misc.x - 0.25) * TAU;
    return normalize(vec3(-sin(angle), cos(angle), 0.25));
}

vec3 daySky(vec3 dir) {
    float up = clamp(dir.y, 0.0, 1.0);
    return mix(vec3(0.72, 0.84, 0.97), vec3(0.28, 0.5, 0.88), pow(up, 0.6));
}

vec3 aurora(vec3 dir, float t) {
    vec3 col = mix(vec3(0.03, 0.05, 0.12), vec3(0.005, 0.01, 0.03), smoothstep(0.0, 0.8, dir.y));
    vec2 plane = dir.xz / (max(dir.y, 0.0) + 0.25);
    vec3 curtains = vec3(0.0);

    for (int i = 0; i < 3; i++) {
        float layer = float(i);
        vec2 p = plane * (1.0 + 0.35 * layer);
        float centre = 1.2 * sin(p.x * 0.45 + t * 0.12 + layer * 2.1)
            + 1.6 * valueNoise2(vec2(p.x * 0.3 + layer * 7.0, t * 0.05))
            - 0.8 * layer;
        float height = p.y - centre;
        float rays = 0.55 + 0.45 * valueNoise2(vec2(p.x * 5.0 + t * 0.25, layer * 3.7));
        float sheet = smoothstep(-0.2, 0.05, height) * exp(-max(height, 0.0) * (1.4 + 0.5 * layer));
        float shimmer = 0.7 + 0.3 * sin(p.x * 2.5 + t * 1.3 + layer);
        vec3 tint = mix(Color0.rgb, Color1.rgb, smoothstep(0.0, 1.8, height));
        curtains += tint * sheet * rays * shimmer / (1.0 + 0.6 * layer);
    }

    float cover = clamp(dot(curtains, vec3(0.33)) * 1.2, 0.0, 0.8);
    col += curtains * smoothstep(0.02, 0.3, dir.y) * 1.3;
    col += stars(dir, Misc.z, t) * (1.0 - cover);
    return mix(daySky(dir), col, nightFactor());
}

vec3 nebula(vec3 dir, float t) {
    vec3 p = dir * 1.8;
    vec3 drift = vec3(t * 0.02, -t * 0.015, t * 0.01);
    float warp = fbm3(p + drift);
    float gas = fbm3(p * 1.3 + vec3(warp * 2.2) - drift * 1.5 + 5.0);

    vec3 col = vec3(0.01, 0.01, 0.03);
    col += Color0.rgb * smoothstep(0.35, 0.85, gas) * 0.9;
    col += Color1.rgb * smoothstep(0.3, 0.9, warp) * 0.6;
    col += Color2.rgb * pow(smoothstep(0.55, 1.0, gas * warp * 2.0), 3.0) * 0.8;

    vec3 bandNormal = normalize(vec3(0.3, 0.8, 0.5));
    float bandDistance = dot(dir, bandNormal) * 4.0;
    float band = exp(-bandDistance * bandDistance);
    float dust = band * (0.4 + 0.8 * fbm3(dir * 5.0 + drift));
    col += vec3(0.9, 0.85, 1.0) * dust * 0.25;

    col += stars(dir, Misc.z * (1.0 + band), t);
    return col;
}

vec3 twilight(vec3 dir, float t) {
    vec3 sun = sunDirection();
    float daylight = Misc.x < 0.0 ? 1.0 : smoothstep(-0.25, 0.1, sun.y);
    float sunDot = max(dot(dir, sun), 0.0);

    vec3 col = mix(Color0.rgb, Color1.rgb, pow(clamp(dir.y, 0.0, 1.0), 0.5));
    col += Color0.rgb * pow(sunDot, 8.0) * 0.6;
    col += mix(Color0.rgb, vec3(1.0, 0.92, 0.75), 0.6) * pow(sunDot, 40.0) * 0.8;
    col += vec3(1.0, 0.95, 0.85) * pow(sunDot, 600.0) * 2.0;

    vec2 plane = dir.xz / (max(dir.y, 0.0) + 0.2);
    vec2 streak = vec2(plane.x * 0.5 + t * 0.03, plane.y * 1.6);
    float density = fbm2(streak);
    float shadowed = fbm2(streak + sun.xz * 0.15);
    float mask = smoothstep(0.5, 0.8, density) * smoothstep(0.02, 0.25, dir.y);
    float lit = clamp(0.35 + 0.65 * pow(sunDot, 2.0) + (density - shadowed) * 2.0, 0.0, 1.0);
    vec3 cloudColor = mix(Color1.rgb * 0.6, Color2.rgb, lit);
    col = mix(col, cloudColor, mask * 0.85);

    col *= mix(0.12, 1.0, daylight);
    col += stars(dir, Misc.z, t) * (1.0 - daylight) * (1.0 - mask);
    return col;
}

float lightning(float t) {
    float cycle = floor(t / LIGHTNING_PERIOD);
    float local = fract(t / LIGHTNING_PERIOD) * LIGHTNING_PERIOD;
    float strikes = step(0.45, hash11(cycle));
    float since = local - hash11(cycle + 17.0) * 4.0;
    float first = exp(-max(since, 0.0) * 8.0) * step(0.0, since);
    float second = 0.6 * exp(-max(since - 0.18, 0.0) * 10.0) * step(0.18, since);
    return strikes * (first + second);
}

vec3 stormNight(vec3 dir, float t) {
    vec2 plane = dir.xz / (max(dir.y, 0.0) + 0.35) * 1.2;
    vec2 flow = vec2(t * 0.08, t * 0.03);
    float warp = fbm2(plane * 0.5 - flow * 0.6);
    float density = fbm2(plane * 0.9 + flow + warp * 1.5);
    float cloud = smoothstep(0.35, 0.8, density);

    vec3 col = mix(vec3(0.02, 0.025, 0.05), vec3(0.005, 0.008, 0.02), smoothstep(0.0, 0.9, dir.y));
    col = mix(col, Color0.rgb * (0.15 + 0.35 * density), cloud);

    float cycle = floor(t / LIGHTNING_PERIOD);
    vec3 boltDir = normalize(vec3(hash11(cycle + 3.0) - 0.5, 0.5, hash11(cycle + 9.0) - 0.5));
    float flash = lightning(t);
    float reach = 0.35 + 0.65 * pow(max(dot(dir, boltDir), 0.0), 3.0);
    col += Color1.rgb * flash * reach * (0.2 + cloud * 1.1);

    return mix(daySky(dir) * 0.6, col, nightFactor());
}

vec3 deepVoid(vec3 dir, float t) {
    vec3 col = mix(Color1.rgb, Color0.rgb, smoothstep(-0.2, 1.0, dir.y));
    col *= 0.9 + 0.1 * sin(t * 0.3);
    col += (valueNoise3(dir * 3.0 + vec3(t * 0.05)) - 0.5) * 0.08 * (Color0.rgb + Color1.rgb + 0.1);
    col += stars(dir, Misc.z * 0.5, t);
    return col;
}

void main() {
    vec4 farPoint = InvViewProj * vec4(v_Ndc, 0.5, 1.0);
    vec3 dir = normalize(farPoint.xyz / farPoint.w);
    float t = Params.x * Params.y;
    int style = int(Params.w + 0.5);

    vec3 col;
    if (style == 0) col = aurora(dir, t);
    else if (style == 1) col = nebula(dir, t);
    else if (style == 2) col = twilight(dir, t);
    else if (style == 3) col = stormNight(dir, t);
    else col = deepVoid(dir, t);

    col *= Params.z;
    col *= 1.0 - 0.55 * Misc.y;

    float fogMix = Fog.w * (1.0 - smoothstep(-0.02, 0.25, dir.y));
    col = mix(col, Fog.rgb, fogMix);

    float dither = fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715))));
    col += (dither - 0.5) / 255.0;

    fragColor = vec4(col, 1.0);
}
