package com.waxball.asmr.gl

object Shaders {

    /**
     * 조각마다 변환이 다르므로 정점이 자기 조각 번호를 들고 다닌다.
     * 그 번호로 텍스처에서 3x4 변환 행렬과 (수축량, 투명도)를 읽는다.
     *
     * 수축량은 금이 갈수록 커진다. 조각이 자기 중심 쪽으로 조금씩 줄어들면서
     * 조각 사이에 틈이 벌어지는데, 그게 눈에는 금으로 보인다.
     */
    const val SHELL_VERTEX = """#version 300 es
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNormal;
layout(location = 2) in vec3 aShrink;
layout(location = 3) in vec2 aShardFace;

uniform mat4 uViewProj;
uniform sampler2D uXform;
uniform float uScale;

/**
 * 손으로 쥔 만큼 볼 전체가 눌린다. 껍질·속살·풍선·갇힌 조각이 같은 변환을 공유해야
 * "풍선과 안의 것이 한 몸"으로 보인다. 볼 중심([uCenter]) 기준이라 자리는 안 움직인다.
 */
uniform vec3 uSquash;
uniform vec3 uCenter;

/** 납작해지는 축(화면 평면, 정규화). 손바닥이 접히는 방향이다. */
uniform vec2 uSquashDir;

/**
 * 쥐는 동안 표면이 울그락불그락 일렁이는 정도 0~1.
 * 균일하게 납작해지기만 하면 고무 덩어리가 아니라 눌린 그림처럼 보인다.
 */
uniform float uWobble;
uniform float uVertTime;

out vec3 vNormal;
out vec3 vWorld;
out float vFace;
out float vAlpha;

/** 물체 좌표에서의 방향. 조각이 날아가도 제 자리의 사진 조각을 그대로 들고 다닌다. */
out vec3 vObjDir;

void main() {
    int id = int(aShardFace.x + 0.5);
    vec4 r0 = texelFetch(uXform, ivec2(id, 0), 0);
    vec4 r1 = texelFetch(uXform, ivec2(id, 1), 0);
    vec4 r2 = texelFetch(uXform, ivec2(id, 2), 0);
    vec4 st = texelFetch(uXform, ivec2(id, 3), 0);

    vec4 p = vec4(aPos - aShrink * st.x, 1.0);
    vec3 world = vec3(dot(r0, p), dot(r1, p), dot(r2, p)) * uScale;
    // 카메라 쪽으로 납작해지는 게 아니라 손바닥이 접히는 축으로 찌그러진다.
    // 그 직각과 깊이 방향으로는 부푼다 — 손아귀에서 삐져나오는 그 모양이다.
    vec3 rel = world - uCenter;
    float along = dot(rel.xy, uSquashDir);
    vec2 perp = rel.xy - uSquashDir * along;
    world = uCenter + vec3(uSquashDir * along * uSquash.z + perp * uSquash.x, rel.z * uSquash.x);
    vec3 n = normalize(vec3(dot(r0.xyz, aNormal), dot(r1.xyz, aNormal), dot(r2.xyz, aNormal)));

    // 쥐는 동안 자리마다 다르게 부풀었다 꺼진다. 손아귀 안의 말랑한 것이 그렇다.
    float wob = sin(dot(aPos, vec3(5.1, 4.3, 4.7)) + uVertTime * 6.0)
              * sin(aPos.y * 3.7 - uVertTime * 4.4);
    world += n * wob * uScale * 0.05 * uWobble;

    vNormal = n;
    vWorld = world;
    vFace = aShardFace.y;
    vAlpha = st.y;
    vObjDir = aPos;
    gl_Position = uViewProj * vec4(world, 1.0);
}
"""

    const val SHELL_FRAGMENT = """#version 300 es
precision mediump float;

in vec3 vNormal;
in vec3 vWorld;
in float vFace;
in float vAlpha;
in vec3 vObjDir;

uniform vec3 uShellColor;
uniform vec3 uFleshColor;
uniform vec3 uLightDir;
uniform vec3 uCamPos;

/** 표면 무늬 종류. 0=민무늬 1=가로띠 2=소용돌이 3=분화구 4=얼음균열 5=용암 6=항성 7=점박이 8=바다·대륙 */
uniform int uSurface;
/** 무늬에 쓰는 두 번째 색. 띠 사이나 균열 안쪽에 들어간다. */
uniform vec3 uAccentColor;
/** 흐르는 시간(초). 용암 틈과 항성 표면이 아주 천천히 움직인다. */
uniform float uTime;

/**
 * 실제 탐사선이 찍은 표면 지도. [uUseTex]가 1이면 절차적 무늬 대신 이걸 입힌다.
 * 잡음 함수로는 실사가 안 나온다 — 실존 천체는 진짜 지도를 쓴다.
 */
uniform sampler2D uPlanetTex;
uniform int uUseTex;

out vec4 fragColor;

/** 해시 기반 값잡음. 텍스처를 넣으면 용량이 커져서 셰이더로 만든다. */
float hash(vec3 p) {
    return fract(sin(dot(p, vec3(127.1, 311.7, 74.7))) * 43758.5453);
}

float noise(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float n000 = hash(i);
    float n100 = hash(i + vec3(1.0, 0.0, 0.0));
    float n010 = hash(i + vec3(0.0, 1.0, 0.0));
    float n110 = hash(i + vec3(1.0, 1.0, 0.0));
    float n001 = hash(i + vec3(0.0, 0.0, 1.0));
    float n101 = hash(i + vec3(1.0, 0.0, 1.0));
    float n011 = hash(i + vec3(0.0, 1.0, 1.0));
    float n111 = hash(i + vec3(1.0, 1.0, 1.0));
    return mix(mix(mix(n000, n100, f.x), mix(n010, n110, f.x), f.y),
               mix(mix(n001, n101, f.x), mix(n011, n111, f.x), f.y), f.z);
}

float fbm(vec3 p) {
    return 0.5 * noise(p) + 0.25 * noise(p * 2.03) + 0.125 * noise(p * 4.01)
         + 0.0625 * noise(p * 8.07);
}

/**
 * 행성 표면색. 단순히 두 색을 섞는 게 아니라 종류마다 실제 지형처럼 만든다.
 *
 * @param emissive 스스로 내는 빛. 용암 틈과 항성만 쓴다
 * @param specMask 반짝임 배율. 바다·얼음은 반짝이고 흙·구름은 안 반짝인다
 */
vec3 planet(vec3 n, vec3 l, vec3 v, out vec3 emissive, out float specMask) {
    emissive = vec3(0.0);
    specMask = 1.0;
    vec3 dark = uShellColor * 0.55;

    if (uSurface == 1) {
        // 가스 행성의 가로 띠. 위도 줄무늬를 난류로 흐트러뜨려야 목성처럼 보인다.
        // 곧은 줄무늬는 비치볼이 된다.
        vec3 q = n * 3.0;
        float warp = fbm(q + vec3(0.0, fbm(q * 1.6) * 1.4, 0.0)) - 0.5;
        float t = sin(n.y * 9.5 + warp * 2.8) * 0.5 + 0.5;
        vec3 col = mix(dark, uShellColor, smoothstep(0.12, 0.5, t));
        col = mix(col, uAccentColor, smoothstep(0.58, 0.94, t));
        // 대적점 같은 폭풍 하나. 이게 있어야 "돌고 있는 행성"으로 읽힌다.
        float storm = smoothstep(0.34, 0.08,
            length(vec2(atan(n.z, n.x) - 1.2, (n.y + 0.3) * 2.6)));
        col = mix(col, mix(uAccentColor, vec3(0.72, 0.28, 0.16), 0.5), storm * 0.75);
        specMask = 0.25;
        return col;
    }
    if (uSurface == 2) {
        // 두꺼운 구름의 소용돌이. 잡음으로 잡음을 비틀면(도메인 워핑) 구름이 흐르는
        // 결이 나온다. 금성·해왕성이 이 얼굴이다.
        vec3 q = n * 2.5;
        float f1 = fbm(q + vec3(1.7, 9.2, 4.1));
        float f2 = fbm(q + vec3(8.3, 2.8, 5.9));
        float m = fbm(q + 2.1 * vec3(f1, f2, f1));
        vec3 col = mix(uShellColor, uAccentColor, smoothstep(0.3, 0.72, m));
        specMask = 0.3;
        return col * (0.82 + 0.36 * f2);
    }
    if (uSurface == 3) {
        // 분화구. 잡음 얼룩을 색으로 칠하면 곰팡이 핀 돌이 된다 — 실제로 그렇게 나왔다.
        // 진짜 분화구는 둥근 구덩이다. 가장 가까운 크레이터 중심까지의 거리(워리 잡음)로
        // 구덩이를 파고, 테두리에 능선을 세우고, 빛을 향한 안쪽 벽만 밝힌다.
        vec3 p = n * 3.6;
        vec3 ip = floor(p);
        vec3 fp = fract(p);
        float d = 8.0;
        vec3 toCentre = vec3(0.0);
        for (int xo = -1; xo <= 1; xo++)
        for (int yo = -1; yo <= 1; yo++)
        for (int zo = -1; zo <= 1; zo++) {
            vec3 g = vec3(float(xo), float(yo), float(zo));
            vec3 c = g + vec3(hash(ip + g), hash(ip + g + 17.1), hash(ip + g + 31.7)) - fp;
            float len = length(c);
            if (len < d) { d = len; toCentre = c; }
        }
        float bowl = 1.0 - smoothstep(0.12, 0.42, d);
        float rimRing = smoothstep(0.56, 0.42, d) - smoothstep(0.42, 0.30, d);
        // 구덩이 안에서 빛을 마주 보는 벽이 밝고 그늘진 벽이 어둡다.
        float lit = 0.5 + 0.5 * dot(normalize(toCentre + vec3(1e-4)), l);
        vec3 col = mix(uShellColor, uAccentColor, bowl * 0.6);
        col *= mix(1.0, mix(0.58, 1.12, lit), bowl);
        col *= 1.0 + rimRing * 0.22;
        col *= 0.93 + 0.14 * noise(n * 15.0);
        specMask = 0.12;
        return col;
    }
    if (uSurface == 4) {
        // 얼음 껍질의 균열. 유로파처럼 매끈한 얼음판에 실금이 가로지른다.
        float c = abs(fbm(n * 4.5) - 0.5);
        float crack = smoothstep(0.06, 0.0, c);
        vec3 col = uShellColor * (0.9 + 0.25 * fbm(n * 2.2));
        specMask = 1.8;   // 얼음은 젖은 것처럼 반짝인다
        return mix(col, uAccentColor, crack * 0.85);
    }
    if (uSurface == 5) {
        // 식은 껍질 사이로 용암이 비친다. 틈은 조명과 무관하게 스스로 빛나야
        // 뜨거워 보인다. 아주 천천히 흐른다.
        float crust = fbm(n * 3.2 + vec3(uTime * 0.015));
        float glow = smoothstep(0.56, 0.40, crust);
        emissive = uAccentColor * glow * 1.5;
        specMask = 0.0;
        return mix(dark, uShellColor, smoothstep(0.35, 0.75, crust));
    }
    if (uSurface == 6) {
        // 항성. 표면이 끓고(쌀알무늬) 스스로 빛난다. 실제 항성은 가장자리가
        // 어두워지므로(주연감광) 테두리를 밝히면 전구처럼 보인다.
        float g = fbm(n * 6.0 + vec3(0.0, uTime * 0.05, 0.0))
                + 0.5 * fbm(n * 13.0 - vec3(uTime * 0.03));
        vec3 col = mix(uShellColor, uAccentColor, smoothstep(0.4, 1.1, g));
        float limb = 0.45 + 0.55 * max(dot(n, v), 0.0);
        emissive = col * (0.6 + 0.55 * g) * limb;
        specMask = 0.0;
        return col * 0.2;
    }
    if (uSurface == 7) {
        // 점박이. 굵은 얼룩 위에 자잘한 알갱이가 박힌 암석 표면.
        float g = fbm(n * 8.0);
        vec3 col = mix(uShellColor, uAccentColor, smoothstep(0.52, 0.75, g));
        col = mix(col, uAccentColor, smoothstep(0.74, 0.9, noise(n * 21.0)) * 0.5);
        return col * (0.88 + 0.24 * g);
    }
    // uSurface == 8: 바다·대륙·구름·빙관. 지구다.
    float cont = fbm(n * 2.6 + vec3(4.2, 1.3, 7.8));
    float land = smoothstep(0.5, 0.535, cont);
    vec3 landCol = mix(uAccentColor, vec3(0.72, 0.62, 0.42), smoothstep(0.6, 0.78, cont));
    vec3 col = mix(uShellColor, landCol, land);
    float cap = smoothstep(0.7, 0.84, abs(n.y) + (fbm(n * 5.0) - 0.5) * 0.14);
    col = mix(col, vec3(0.92, 0.95, 1.0), cap);
    float clouds = smoothstep(0.55, 0.78, fbm(n * 3.8 + vec3(uTime * 0.008, 0.0, 0.0)));
    // 바다에만 해가 비쳐 반짝인다. 육지와 구름은 반짝이지 않는다.
    specMask = (1.0 - land) * (1.0 - clouds) * 1.6;
    return mix(col, vec3(0.98), clouds * 0.8);
}

void main() {
    if (vAlpha < 0.02) discard;

    vec3 n = normalize(vNormal);
    vec3 l = normalize(uLightDir);
    vec3 v = normalize(uCamPos - vWorld);

    // 무늬는 껍질 바깥면에만 그린다. 깨진 단면과 안쪽면은 왁스 속살이라 민무늬다.
    vec3 emissive = vec3(0.0);
    float specMask = 1.0;
    vec3 shell = uShellColor;
    if (uUseTex == 1 && vFace > 0.5) {
        // 정거방형도법 지도를 구면 방향으로 편다. 정점 UV로 하면 경도 이음새에서
        // 지도가 통째로 감기므로 프래그먼트에서 방향으로 계산한다.
        vec3 d = normalize(vObjDir);
        vec2 uv = vec2(atan(d.z, d.x) * 0.15915494 + 0.5,
                       0.5 - asin(clamp(d.y, -1.0, 1.0)) * 0.31830989);
        shell = texture(uPlanetTex, uv).rgb;
        specMask = 0.35;
        // 항성 사진은 스스로 빛나야 한다. 조명을 태우면 반쪽이 꺼진 전구가 된다.
        if (uSurface == 6) {
            float limb = 0.45 + 0.55 * max(dot(n, v), 0.0);
            emissive = shell * limb * 1.1;
            shell *= 0.2;
            specMask = 0.0;
        }
    } else if (uSurface > 0 && vFace > 0.5) {
        shell = planet(n, l, v, emissive, specMask);
    }

    vec3 base;
    if (vFace > 0.5) {
        base = shell;
    } else if (vFace < -0.5) {
        base = uFleshColor * 0.78;          // 껍질 안쪽면. 왁스 속살이 훤히 보인다
    } else {
        base = mix(uShellColor, uFleshColor, 0.4) * 0.92;   // 깨진 단면은 밝아야 "갓 부러진" 맛이 난다
    }

    vec3 h = normalize(l + v);

    float wrap = dot(n, l) * 0.5 + 0.5;     // 왁스는 빛을 머금어 경계가 부드럽다
    float rim = pow(1.0 - max(dot(n, v), 0.0), 3.0);
    float spec = pow(max(dot(n, h), 0.0), 48.0);

    // 대기 테두리는 그 행성의 색을 띤다. 지구는 푸르게, 화성은 불그스름하게.
    vec3 atmo = mix(uShellColor, vec3(1.0), 0.35);
    vec3 col = base * (0.24 + 0.88 * wrap * wrap)
             + atmo * rim * 0.3
             + vec3(spec * 0.3) * specMask
             + emissive;
    fragColor = vec4(col, vAlpha);
}
"""

    /** 말랑이 코어. 누르면 그 지점이 실제로 눌린다. */
    const val CORE_VERTEX = """#version 300 es
layout(location = 0) in vec3 aPos;

uniform mat4 uViewProj;
uniform mat3 uRot;
uniform float uRadius;
uniform vec3 uPressPoint;
uniform float uPressAmount;
uniform vec3 uOffset;

/** 쥔 만큼 눌린다. 껍질과 같은 값을 받아야 풍선·속살이 함께 찌그러진다. */
uniform vec3 uSquash;
uniform vec2 uSquashDir;

/** 쥐는 동안 울그락불그락 일렁이는 정도. 껍질과 같은 값을 받는다. */
uniform float uWobble;
uniform float uVertTime;

out vec3 vNormal;
out vec3 vWorld;
out vec3 vObj;

void main() {
    vec3 p = aPos;
    float d = max(0.0, 1.0 - length(p - uPressPoint) / 0.85);
    p -= normalize(uPressPoint + vec3(0.0001)) * (d * d) * uPressAmount;

    // 여러 개를 흩어 놓을 때 껍질과 같은 자리로 옮겨야 한다.
    // 눌림 축은 껍질과 같다 — 손바닥이 접히는 방향.
    vec3 w0 = (uRot * p) * uRadius;
    float along = dot(w0.xy, uSquashDir);
    vec2 perp = w0.xy - uSquashDir * along;
    vec3 world = vec3(uSquashDir * along * uSquash.z + perp * uSquash.x, w0.z * uSquash.x) + uOffset;
    vec3 nrm = normalize(uRot * aPos);

    float wob = sin(dot(aPos, vec3(5.1, 4.3, 4.7)) + uVertTime * 6.0)
              * sin(aPos.y * 3.7 - uVertTime * 4.4);
    world += nrm * wob * uRadius * 0.055 * uWobble;

    vNormal = nrm;
    vWorld = world;
    vObj = aPos;
    gl_Position = uViewProj * vec4(world, 1.0);
}
"""

    const val CORE_FRAGMENT = """#version 300 es
precision mediump float;

in vec3 vNormal;
in vec3 vWorld;
in vec3 vObj;

uniform vec3 uColor;
uniform vec3 uLightDir;
uniform vec3 uCamPos;

/**
 * 1이면 속살(불투명), 1보다 작으면 고무풍선 껍질이다.
 * 같은 구 메시를 반지름과 이 값만 바꿔 두 번 그린다.
 */
uniform float uAlpha;

/**
 * 속 반죽의 재료색. 실제 왁뿌볼 속은 단색이 아니라 2~4색 점토가 마블로
 * 뭉쳐 있고, 주무를수록 그 덩어리들이 서로 섞여 한 색이 되어 간다.
 * uClayMix 0 = 덩어리 뚜렷, 1 = 전부 섞인 평균색.
 */
uniform vec3 uClayColors[4];
uniform int uClayCount;
uniform float uClayMix;
uniform float uClaySeed;

/** 지금 쥐는 세기(0~1)와 시간. 주무르는 동안 무늬가 휘저어지는 데 쓴다. */
uniform float uClayStir;
uniform float uClayTime;

out vec4 fragColor;

float claynoise(vec3 p) {
    return sin(dot(p, vec3(4.1, 5.3, 3.7)) + uClaySeed)
         * sin(dot(p, vec3(2.3, 3.1, 4.9)) * 1.7 - uClaySeed * 0.7);
}

/** 색 덩어리 소프트맥스. 뾰족할수록(k↑) 경계가 뚜렷하고, k=0이면 전부 평균이 된다. */
vec3 clay(vec3 dir) {
    // 경계만 흐려지면 "섞인다"가 아니라 "바랜다"로 보인다. 진짜 반죽처럼
    // 치댈수록 무늬 자체가 높이에 따라 비틀려 감기고(스미어), 쥐는 동안에는
    // 살짝 휘저어져야 손이 반죽을 젓고 있다는 느낌이 든다.
    float ang = uClayMix * 5.0 * dir.y
              + uClayStir * sin(uClayTime * 2.4 + dir.y * 4.0) * 0.35;
    float c = cos(ang);
    float s = sin(ang);
    dir = vec3(c * dir.x + s * dir.z, dir.y, -s * dir.x + c * dir.z);

    vec3 axes[4];
    axes[0] = vec3(0.577, 0.577, 0.577);
    axes[1] = vec3(0.577, -0.577, -0.577);
    axes[2] = vec3(-0.577, 0.577, -0.577);
    axes[3] = vec3(-0.577, -0.577, 0.577);

    float k = mix(6.0, 0.0, clamp(uClayMix, 0.0, 1.0));
    float fs[4];
    float fmax = -10.0;
    for (int i = 0; i < uClayCount; i++) {
        fs[i] = dot(dir, axes[i])
              + claynoise(dir * (1.0 + float(i) * 0.37) + vec3(float(i) * 9.3)) * 0.55;
        fmax = max(fmax, fs[i]);
    }
    vec3 acc = vec3(0.0);
    float wsum = 0.0;
    for (int i = 0; i < uClayCount; i++) {
        // 최대값을 빼서 exp가 mediump 범위를 넘지 않게 한다.
        float w = exp(k * (fs[i] - fmax));
        acc += uClayColors[i] * w;
        wsum += w;
    }
    return acc / max(wsum, 1e-4);
}

void main() {
    vec3 n = normalize(vNormal);
    vec3 l = normalize(uLightDir);
    vec3 v = normalize(uCamPos - vWorld);
    float wrap = dot(n, l) * 0.5 + 0.5;
    float rim = pow(1.0 - max(dot(n, v), 0.0), 2.0);
    // 풍선(반투명)은 마블 없이 제 색 그대로. 반죽은 속살에만 있다.
    vec3 base = (uClayCount >= 2 && uAlpha > 0.999) ? clay(normalize(vObj)) : uColor;
    // 말랑한 것은 빛이 속으로 스며 가장자리가 밝다
    vec3 col = base * (0.3 + 0.8 * wrap) + base * rim * 0.5;

    if (uAlpha < 0.999) {
        // 고무풍선은 가운데가 비치고 가장자리에서 두꺼워 보인다. 알파를 프레넬로 준다.
        // 균일하게 주면 비닐랩을 씌운 것처럼 보이고 안이 훤히 들여다보인다.
        float edge = pow(1.0 - max(dot(n, v), 0.0), 1.6);
        float a = uAlpha * (0.35 + 1.9 * edge);
        // 젖은 고무의 반짝임. 세게 주면 뿌연 막이 되어 행성을 가린다.
        vec3 h = normalize(l + v);
        float spec = pow(max(dot(n, h), 0.0), 64.0);
        fragColor = vec4(col * 0.6 + vec3(spec) * 0.35, clamp(a, 0.0, 0.75));
        return;
    }
    fragColor = vec4(col, 1.0);
}
"""
}
