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
    float cloud = smoothstep(0.35, 0.8, density) * smoothstep(-0.05, 0.15, dir.y);

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

vec3 spaceBackground(vec3 dir, float t) {
    vec3 col = mix(vec3(0.018, 0.024, 0.039), vec3(0.003, 0.006, 0.014), smoothstep(0.0, 0.75, dir.y));
    return col + stars(dir, Misc.z, t * 0.035);
}

float sphereHit(vec3 origin, vec3 ray, vec3 centre, float radius) {
    vec3 offset = origin - centre;
    float b = dot(offset, ray);
    float c = dot(offset, offset) - radius * radius;
    float discriminant = b * b - c;
    if (discriminant < 0.0) return -1.0;
    float distance = -b - sqrt(discriminant);
    return distance > 0.0 ? distance : -1.0;
}

vec3 ringedPlanet(vec3 dir, float t) {
    float night = nightFactor();
    if (night <= 0.0) return daySky(dir);
    vec3 col = spaceBackground(dir, t);

    vec3 centre = normalize(vec3(0.65, 0.29, -0.70)) * 7.0;
    float alignment = dot(dir, normalize(centre));
    if (alignment < 0.91) return mix(daySky(dir), col, night);

    vec3 light = sunDirection();
    vec3 normal = normalize(vec3(-0.22, 0.86, -0.46));
    float surface = sphereHit(vec3(0.0), dir, centre, 1.45);
    float miss = length(cross(centre, dir));
    float rim = exp(-abs(miss - 1.45) * 35.0);
    col += Color1.rgb * rim * 0.24;

    if (surface > 0.0) {
        vec3 point = dir * surface - centre;
        vec3 outward = point / 1.45;
        float latitude = dot(outward, normal);
        float bands = sin(latitude * 42.0 + valueNoise3(outward * 5.0 + vec3(t * 0.003)) * 2.5);
        float diffuse = max(dot(outward, light), 0.0);
        col = Color0.rgb * (0.74 + 0.16 * bands) * (0.055 + 0.78 * diffuse);
        float limb = pow(1.0 - max(dot(outward, -dir), 0.0), 3.0);
        col += Color1.rgb * limb * (0.04 + 0.22 * diffuse);
    }

    float denominator = dot(dir, normal);
    if (abs(denominator) > 0.0001) {
        float distance = dot(centre, normal) / denominator;
        if (distance > 0.0 && (surface < 0.0 || distance < surface)) {
            vec3 point = dir * distance - centre;
            float radius = length(point);
            float edge = max(fwidth(radius), 0.006);
            float ring = smoothstep(1.72, 1.72 + edge, radius) * (1.0 - smoothstep(2.74 - edge, 2.74, radius));
            float gap = smoothstep(0.025, 0.025 + edge, abs(radius - 2.27));
            float bands = 0.66 + 0.17 * sin(radius * 105.0) + 0.09 * sin(radius * 43.0);
            float shadow = sphereHit(point + light * 0.002, light, vec3(0.0), 1.45) > 0.0 ? 0.075 : 1.0;
            vec3 rings = Color2.rgb * bands * shadow * (0.34 + 0.4 * abs(dot(normal, light)));
            col = mix(col, rings, ring * gap * 0.9);
        }
    }

    return mix(daySky(dir), col, night);
}

vec3 accretionDisk(vec3 point, float t) {
    float radius = length(point);
    float heat = 1.0 - smoothstep(2.6, 4.6, radius);
    float angle = atan(point.z, point.x);
    float lanes = 0.82 + 0.12 * sin(radius * 31.0 + sin(angle * 3.0 - t * 0.018));
    float beaming = clamp(1.0 + point.x / max(radius, 0.001) * 0.35, 0.65, 1.35);
    return mix(Color0.rgb, Color1.rgb, heat) * (0.22 + heat * 0.72) * lanes * beaming;
}

vec3 blackHole(vec3 dir, float t) {
    float night = nightFactor();
    if (night <= 0.0) return daySky(dir);
    vec3 col = spaceBackground(dir, t);

    vec3 axis = normalize(vec3(0.35, 0.46, -0.82));
    float alignment = dot(dir, axis);
    if (alignment < 0.866) return mix(daySky(dir), col, night);

    vec3 right = normalize(cross(axis, vec3(0.0, 1.0, 0.0)));
    vec3 up = cross(right, axis);
    vec3 ray = vec3(dot(dir, right), dot(dir, up), -alignment);
    vec3 position = vec3(0.0, 0.0, 12.0);
    float entry = sphereHit(position, ray, vec3(0.0), 6.0);
    if (entry < 0.0) return mix(daySky(dir), col, night);

    vec3 momentum = cross(position, ray);
    float impactSquared = dot(momentum, momentum);
    float impact = sqrt(impactSquared);
    float photonWidth = max(fwidth(impact) * 1.25, 0.012);
    float photons = exp(-pow((impact - 2.598) / photonWidth, 2.0));
    position += ray * entry;
    vec3 diskNormal = normalize(vec3(0.12, 0.95, 0.29));
    vec3 emission = vec3(0.0);
    float transmission = 1.0;
    int steps = clamp(int(Misc.w + 0.5), 16, 64);

    for (int i = 0; i < 64; i++) {
        if (i >= steps) break;
        float radius = length(position);
        if (radius < 1.0) {
            transmission = 0.0;
            break;
        }
        if (radius > 6.01 && dot(position, ray) > 0.0) break;

        float stride = clamp(radius * 0.3, 0.18, 1.35) * 32.0 / float(steps);
        vec3 acceleration = -1.5 * impactSquared * position / pow(radius, 5.0);
        vec3 nextPosition = position + ray * stride + acceleration * (0.5 * stride * stride);
        float nextRadius = length(nextPosition);
        if (nextRadius < 1.0) {
            transmission = 0.0;
            break;
        }
        vec3 nextAcceleration = -1.5 * impactSquared * nextPosition / pow(nextRadius, 5.0);
        ray += (acceleration + nextAcceleration) * (0.5 * stride);

        float height = dot(position, diskNormal);
        float nextHeight = dot(nextPosition, diskNormal);
        if (height * nextHeight < 0.0) {
            vec3 crossing = mix(position, nextPosition, height / (height - nextHeight));
            float diskRadius = length(crossing);
            float opacity = smoothstep(2.55, 2.75, diskRadius) * (1.0 - smoothstep(4.2, 4.6, diskRadius)) * 0.92;
            emission += transmission * opacity * accretionDisk(crossing, t);
            transmission *= 1.0 - opacity;
        }
        position = nextPosition;
    }

    vec3 escaped = normalize(right * ray.x + up * ray.y - axis * ray.z);
    col = emission + transmission * spaceBackground(escaped, t);
    col += Color2.rgb * photons * 0.28;
    return mix(daySky(dir), col, night);
}

vec3 milkyWay(vec3 dir, float t) {
    float night = nightFactor();
    if (night <= 0.0) return daySky(dir);
    vec3 col = spaceBackground(dir, t);

    vec3 normal = normalize(vec3(-0.38, 0.64, 0.67));
    vec3 core = normalize(vec3(0.78, 0.57, -0.10));
    float latitude = dot(dir, normal);
    float band = exp(-latitude * latitude * 95.0);
    float bulge = pow(max(dot(dir, core), 0.0), 9.0);
    if (abs(latitude) > 0.45) return mix(daySky(dir), col, night);
    vec2 plane = vec2(dot(dir, normalize(cross(normal, core))), dot(dir, core));
    float clouds = valueNoise2(plane * 14.0) * 0.65 + valueNoise2(plane * 33.0 + 7.0) * 0.35;
    float detail = valueNoise2(plane * 79.0);
    float dust = smoothstep(0.40, 0.67, clouds + detail * 0.12);
    float lane = exp(-pow((latitude + (clouds - 0.5) * 0.11) * 42.0, 2.0));
    float extinction = clamp(lane * (0.3 + dust * 0.65), 0.0, 0.93);
    vec3 starlight = mix(Color0.rgb, Color1.rgb, bulge);
    float glow = band * (0.055 + clouds * 0.1) + bulge * exp(-latitude * latitude * 26.0) * 0.075;
    col += starlight * glow * (1.0 - extinction);
    col += Color2.rgb * band * extinction * 0.035;
    float density = Misc.z * band * (1.0 - extinction) * 3.5;
    col += stars(dir * 1.73, density, t * 0.025) * starlight * 0.55;
    float unresolved = smoothstep(0.965, 1.0, hash31(floor(dir * 850.0)));
    col += starlight * unresolved * band * (1.0 - extinction) * Misc.z * 0.11;
    return mix(daySky(dir), col, night);
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
    else if (style == 4) col = deepVoid(dir, t);
    else if (style == 5) col = ringedPlanet(dir, t);
    else if (style == 6) col = blackHole(dir, t);
    else col = milkyWay(dir, t);

    col *= Params.z;
    col *= 1.0 - 0.55 * Misc.y;

    float fogMix = Fog.w * (1.0 - smoothstep(-0.02, 0.25, dir.y));
    col = mix(col, Fog.rgb, fogMix);

    float dither = fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715))));
    col += (dither - 0.5) / 255.0;

    fragColor = vec4(col, 1.0);
}
