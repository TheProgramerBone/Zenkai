#version 150

// CUERPO de la familia DIVINE del aura compuesta (client/aura/compose, AuraBodyKind.SOFT): SSG, SSB,
// las formas *_god. Misma malla que la llama (esfera unitaria en VBO, AuraBodyMesh) y mismo contrato
// de espacio que aura_flame_shell.vsh (Position unitaria, transformación a mundo en
// ZenkaiShellModelMat), pero OTRA forma:
//
//  - la llama (aura_flame_shell) son muchas púas triangulares que trepan: SSJ, aura_1/aura_3;
//  - aquí son POCOS LÓBULOS REDONDOS (onda coseno, nunca triangular) que se mecen de lado y por los
//    que suben ABULTAMIENTOS lentos, uno por lóbulo y con su propia fase — lenguas blandas de fuego
//    divino (aura_7, SSG) en vez de púas. Arriba cada lóbulo se alarga en una lengua redondeada.
//
// Lo que NO hace: estrechar en aguja ni tener corona de púas. El resultado se lee "controlado":
// silueta limpia, movimiento lento, sin temblor de alta frecuencia.

in vec3 Position;
in vec4 Color;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform float GameTime;
uniform sampler2D Sampler0; // aura_noise.png

uniform mat4 ZenkaiShellModelMat;
uniform vec3 ZenkaiShellCenter;
uniform vec3 ZenkaiShellSize;
uniform float ZenkaiShellLowerHalf;
uniform float ZenkaiShellAmp;       // bloques de abultamiento de un lóbulo
uniform float ZenkaiShellTopBoost;
uniform float ZenkaiShellPeaks;     // nº de LÓBULOS alrededor (entero, 5-9)
uniform float ZenkaiShellFreqY;     // abultamientos por lóbulo en vertical
uniform float ZenkaiShellSpeed;     // velocidad de subida de los abultamientos
uniform float ZenkaiShellDetail;    // peso del ruido
uniform float ZenkaiShellUpDir;
uniform float ZenkaiShellSideUpDir;
uniform float ZenkaiShellGrowth;
uniform float ZenkaiShellTaper;
uniform float ZenkaiShellTip;       // bloques de lengua redondeada sobre cada lóbulo, arriba
uniform vec4 ZenkaiBodyShape;       // x = vaivén lateral, y = respiración, z = afilado de las lenguas (0 = romas)

out vec4 vColor;
out vec3 vNormal;
out vec3 vViewDir;
out float vPeak;
out float vLocalY;
out vec3 vUnit;

const float TAU = 6.28318530718;
const float PI = 3.14159265359;

// Ruido SUAVE: la textura con interpolación bilineal es lineal a tramos, y una malla desplazada
// con ella sale en CARAS PLANAS (Ultra Ego se leía como una gema facetada, vídeo del usuario del
// 2026-09-30). Reponderar la fracción con smoothstep entre texels (el truco de Quilez) da un campo
// con derivada continua sin coste extra: sigue siendo una sola lectura.
vec4 smoothNoise(vec2 uv) {
    vec2 ts = vec2(textureSize(Sampler0, 0));
    vec2 p = uv * ts - 0.5;
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return textureLod(Sampler0, (i + f + 0.5) / ts, 0.0);
}

void main() {
    vec3 p = Position;
    float localY = p.y;
    float t = GameTime * 1200.0; // segundos de juego, ver aura_flame_shell.vsh

    float angle = atan(p.z, p.x == 0.0 ? 0.00001 : p.x);
    float lobes = max(ZenkaiShellPeaks, 1.0);
    // Vaivén: los lóbulos se mecen de lado, más arriba que abajo (el fuego se mueve por la punta).
    float sway = ZenkaiBodyShape.x * sin(t * 0.9 + localY * 2.5) * smoothstep(-0.6, 0.8, localY);
    float ph = angle * lobes / TAU + sway;
    float col = 0.5 + 0.5 * cos(ph * TAU);
    // Valles planos y crestas redondas: el lóbulo es una lengua, no una sinusoide pura.
    col = smoothstep(0.15, 1.0, col);
    // Fase propia por lóbulo (mod lobes: al cruzar la costura de atan, ph salta exactamente
    // "lobes" y el índice vuelve al mismo valor, sin costura). El salto de índice cae en los
    // valles, donde col ya es 0.
    float lobeIdx = mod(floor(ph + 0.5), lobes);
    float rise = fract(localY * ZenkaiShellFreqY * 0.5 - t * ZenkaiShellSpeed * 0.45 + lobeIdx * 0.37);
    float bump = sin(rise * PI);
    bump *= bump;

    vec2 noiseUV = vec2(angle / TAU * 2.0, localY * 0.7 - t * ZenkaiShellSpeed * 0.3);
    float n = smoothNoise(noiseUV).r;
    float total = col * (0.35 + 0.65 * bump) * mix(1.0, 0.75 + 0.5 * n, clamp(ZenkaiShellDetail, 0.0, 1.0));

    float falloff = smoothstep(-0.9, -0.2, localY);
    falloff *= 1.0 + ZenkaiShellTopBoost * smoothstep(0.0, 1.0, localY);

    float g = clamp(ZenkaiShellGrowth, 0.0, 1.0);
    float smoothGrowth = g * g * (3.0 - 2.0 * g);

    vec3 pos = p * ZenkaiShellSize;
    if (p.y < 0.0) pos.y = p.y * ZenkaiShellLowerHalf;
    pos.xz *= smoothGrowth;
    // Respiración: el volumen entero late despacio (energía controlada, no temblor).
    pos *= 1.0 + ZenkaiBodyShape.y * sin(t * 1.7);

    vec3 radial = normalize(pos + vec3(0.0, 0.00001, 0.0));
    float upD = mix(ZenkaiShellSideUpDir, ZenkaiShellUpDir, smoothstep(0.0, 0.8, localY));
    vec3 dir = normalize(mix(radial, vec3(0.0, 1.0, 0.0), upD));
    float disp = total * ZenkaiShellAmp * falloff * pow(g, 4.0);
    pos += dir * disp;

    // Lenguas redondeadas arriba: cada lóbulo sube un poco más, con la punta roma (exponente bajo)
    // o algo más afilada (ZenkaiBodyShape.z) — nunca la aguja de la llama.
    float tongue = pow(col, 2.0 + ZenkaiBodyShape.z * 4.0) * smoothstep(0.25, 0.95, localY) * (0.6 + 0.4 * bump);
    pos.y += ZenkaiShellTip * tongue * pow(g, 3.0);

    // Estrechamiento suave arriba: se cierra, pero en cúpula, no en punta.
    float reach = ZenkaiShellSize.y + ZenkaiShellAmp * (1.0 + ZenkaiShellTopBoost) + ZenkaiShellTip;
    float hNorm = clamp(pos.y / max(reach, 0.001), -1.0, 1.0);
    pos.xz *= max(0.1, 1.0 - ZenkaiShellTaper * smoothstep(0.2, 1.0, hNorm));

    vec4 world = ZenkaiShellModelMat * vec4(ZenkaiShellCenter + pos, 1.0);
    vec4 viewPos = ModelViewMat * world;
    gl_Position = ProjMat * viewPos;

    vec3 nLocal = normalize(pos + vec3(0.0, 0.00001, 0.0));
    vNormal  = normalize(mat3(ModelViewMat) * mat3(ZenkaiShellModelMat) * nLocal);
    vViewDir = -viewPos.xyz;
    vColor   = Color;
    vPeak    = clamp(total * falloff, 0.0, 1.0);
    vLocalY  = localY;
    vUnit    = p;
}
