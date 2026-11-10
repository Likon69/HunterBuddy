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

vec3 starTint(float temperature) {
    vec3 hot = vec3(0.62, 0.76, 1.0);
    vec3 mid = vec3(1.0, 0.96, 0.9);
    vec3 cool = vec3(1.0, 0.68, 0.4);
    return mix(mix(hot, mid, smoothstep(0.0, 0.5, temperature)), cool, smoothstep(0.55, 1.0, temperature));
}

vec3 starLayer(vec3 dir, float scale, float chance, float radius, float gain, float t) {
    vec3 p = dir * scale;
    vec3 cell = floor(p);
    if (hash31(cell) >= chance) return vec3(0.0);

    vec3 h = hash33(cell);
    float dist = length(p - cell - (0.3 + 0.4 * h));
    float core = 1.0 - smoothstep(0.0, radius * (0.6 + 0.8 * h.x), dist);
    float temperature = hash31(cell + 41.7);
    float twinkle = 0.9 + 0.1 * sin(t * (0.6 + 1.2 * h.y) + h.z * TAU);
    return starTint(temperature) * core * core * twinkle * gain * (0.35 + 0.65 * h.y * h.y);
}

vec3 starField(vec3 dir, float density, float t) {
    vec3 col = starLayer(dir, 55.0, 0.02 * density, 0.30, 2.6, t);
    col += starLayer(dir, 120.0, 0.06 * density, 0.26, 1.5, t);
    col += starLayer(dir, 230.0, 0.13 * density, 0.22, 0.95, t);
    col += starLayer(dir, 400.0, 0.2 * density, 0.2, 0.6, t);
    return col;
}

vec3 spaceBackground(vec3 dir, float t) {
    vec3 col = mix(vec3(0.018, 0.024, 0.039), vec3(0.003, 0.006, 0.014), smoothstep(0.0, 0.75, dir.y));
    return col + stars(dir, Misc.z, t * 0.035);
}

vec3 diskColor(vec3 point, vec3 ray, vec3 diskNormal, float t) {
    float radius = length(point);
    float heat = clamp(pow(2.6 / radius, 1.5), 0.0, 1.0);
    vec3 axisU = normalize(cross(diskNormal, vec3(1.0, 0.0, 0.0)));
    vec3 axisV = cross(diskNormal, axisU);
    vec2 planar = normalize(vec2(dot(point, axisU), dot(point, axisV)));
    float spin = t * 0.2 / (radius * sqrt(radius));
    vec2 turned = vec2(planar.x * cos(spin) - planar.y * sin(spin), planar.y * cos(spin) + planar.x * sin(spin));
    float streaks = 0.3 + 1.2 * fbm3(vec3(turned * 5.0, radius * 20.0));
    streaks *= 0.7 + 0.6 * valueNoise3(vec3(turned * 14.0, radius * 55.0));

    vec3 orbit = cross(diskNormal, point) / radius;
    float beta = 0.5 * inversesqrt(radius / 2.6);
    float approach = dot(orbit, -ray);
    float doppler = clamp(pow(1.0 / (1.0 - beta * approach), 3.0), 0.25, 3.5);

    vec3 tint = mix(Color0.rgb, Color1.rgb, smoothstep(0.1, 0.65, heat));
    tint = mix(tint, vec3(1.0, 0.97, 0.9), smoothstep(0.7, 1.0, heat));
    return tint * (0.12 + 1.1 * pow(heat, 1.3)) * streaks * doppler;
}

vec3 blackHole(vec3 dir, float t) {
    float night = nightFactor();
    if (night <= 0.0) return daySky(dir);

    float elevation = Color0.w;
    float tilt = Color1.w;
    float size = Color2.w;
    vec3 axis = vec3(0.4226 * cos(elevation), sin(elevation), -0.9063 * cos(elevation));
    float alignment = dot(dir, axis);
    if (alignment <= 0.0) return mix(daySky(dir), spaceBackground(dir, t), night);
    vec3 right = normalize(cross(axis, vec3(0.0, 1.0, 0.0)));
    vec3 up = cross(right, axis);
    vec3 ray = normalize(vec3(vec2(dot(dir, right), dot(dir, up)) / size, -alignment));
    vec3 position = vec3(0.0, 0.0, 6.5);

    vec3 momentum = cross(position, ray);
    float impactSquared = dot(momentum, momentum);
    float impact = sqrt(impactSquared);
    float photonWidth = max(fwidth(impact) * 1.25, 0.01);
    float photons = exp(-pow((impact - 2.598) / photonWidth, 2.0));
    float halo = impact > 2.598 ? exp(-(impact - 2.598) * 0.75) : 0.0;

    vec3 diskNormal = vec3(0.0, cos(tilt), sin(tilt));
    vec3 emission = vec3(0.0);
    vec3 haze = vec3(0.0);
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
        float ringMask = smoothstep(2.4, 2.9, radius) * (1.0 - smoothstep(4.4, 5.6, radius));
        haze += transmission * exp(-height * height / 0.6) * ringMask * stride * 0.05 * diskColor(position, ray, diskNormal, t);
        if (height * nextHeight < 0.0) {
            vec3 crossing = mix(position, nextPosition, height / (height - nextHeight));
            float diskRadius = length(crossing);
            float opacity = smoothstep(2.55, 2.8, diskRadius) * (1.0 - smoothstep(4.6, 5.2, diskRadius)) * 0.92;
            emission += transmission * opacity * diskColor(crossing, ray, diskNormal, t);
            transmission *= 1.0 - opacity;
        }
        position = nextPosition;
    }

    vec3 escaped = normalize(right * ray.x + up * ray.y - axis * ray.z);
    vec3 col = emission + haze + transmission * spaceBackground(escaped, t);
    col += Color0.rgb * halo * 0.08 * transmission;
    col += Color2.rgb * photons * 0.9 * transmission;
    col = 1.0 - exp(-col * 1.3);
    return mix(daySky(dir), col, night);
}

vec3 milkyWay(vec3 dir, float t) {
    float night = nightFactor();
    if (night <= 0.0) return daySky(dir);

    vec3 col = mix(vec3(0.012, 0.017, 0.032), vec3(0.003, 0.005, 0.013), smoothstep(0.0, 0.8, dir.y));
    col += vec3(0.05, 0.09, 0.07) * exp(-abs(dir.y) * 7.0) * 0.45;

    vec3 normal = normalize(vec3(-0.38, 0.64, 0.67));
    vec3 core = normalize(vec3(0.78, 0.57, -0.10));
    vec3 across = normalize(cross(normal, core));
    float longitude = atan(dot(dir, across), dot(dir, core));
    float towardCore = 0.5 + 0.5 * cos(longitude);
    float coreGlow = pow(towardCore, 6.0);
    float latitude = dot(dir, normal) / (1.0 + 0.7 * coreGlow);
    float wide = exp(-latitude * latitude * 26.0);
    float narrow = exp(-latitude * latitude * 130.0);
    float band = wide * 0.4 + narrow * 0.6;
    float extinction = 0.0;

    if (abs(latitude) < 0.6) {
        float structure = fbm3(dir * 7.0 + 3.0);
        float warp = fbm3(dir * 4.0 + 9.0);
        float laneOffset = latitude - (warp - 0.5) * 0.22;
        float rift = fbm3(dir * 11.0 + 21.0);
        float lane = exp(-laneOffset * laneOffset * 900.0) * smoothstep(0.3, 0.62, rift);
        float clumps = smoothstep(0.55, 0.8, fbm3(dir * 22.0 + 5.0));
        extinction = clamp(lane * 1.3 + wide * clumps * 0.4, 0.0, 0.92);

        vec3 starlight = mix(Color0.rgb, Color1.rgb, coreGlow);
        float glow = (wide * 0.07 + narrow * 0.15) * (0.5 + 0.9 * structure) * (0.55 + 0.6 * towardCore);
        glow += coreGlow * exp(-latitude * latitude * 55.0) * 0.2;
        col += starlight * glow * (1.0 - extinction);
        col += Color2.rgb * band * extinction * 0.12;

        float alongBand = exp(-latitude * latitude * 90.0);
        float emissionPatch = pow(smoothstep(0.58, 0.85, fbm3(dir * 3.4 + 31.0)), 2.0);
        float reflectionPatch = pow(smoothstep(0.58, 0.85, fbm3(dir * 3.0 + 57.0)), 2.0);
        col += vec3(1.0, 0.25, 0.4) * emissionPatch * alongBand * 0.11 * (1.0 - extinction);
        col += vec3(0.3, 0.5, 1.0) * reflectionPatch * alongBand * 0.09 * (1.0 - extinction);
    }

    float density = Misc.z * 2.0 * (1.0 + 2.5 * band * (1.0 - extinction));
    col += starField(dir, density, t * 0.5) * (1.0 - extinction * 0.85);
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
    else if (style == 5) col = blackHole(dir, t);
    else col = milkyWay(dir, t);

    col *= Params.z;
    col *= 1.0 - 0.55 * Misc.y;

    float fogMix = Fog.w * (1.0 - smoothstep(-0.02, 0.25, dir.y));
    col = mix(col, Fog.rgb, fogMix);

    float dither = fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715))));
    col += (dither - 0.5) / 255.0;

    fragColor = vec4(col, 1.0);
}
