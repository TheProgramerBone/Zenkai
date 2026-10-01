#version 150

// CUERPO de la familia DARK_DENSE del aura compuesta (AuraBodyKind.DENSE): Ultra Ego, majin
// malvado, auras oscuras. Misma malla y mismo contrato de espacio que aura_flame_shell.vsh.
//
// Lenguaje (aura_5): una MASA, no una llama. Sin púas ni lenguas que trepan: el contorno lo mueve
// ruido de BAJA frecuencia en dos octavas que gira y sube despacio (la masa se retuerce), más pocos
// lóbulos grandes que rotan alrededor del cuerpo. El desplazamiento es casi radial (la masa se
// hincha hacia fuera, no sube) y puede ser NEGATIVO: aparecen abolladuras, no solo bultos. Arriba
// la silueta es una cúpula irregular; los bultos que crecen por encima de la cabeza suben un poco
// (ZenkaiShellTip), como humo pesado.

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
uniform float ZenkaiShellAmp;       // bloques de hinchazón de un bulto
uniform float ZenkaiShellPeaks;     // nº de lóbulos grandes (entero, 3-6)
uniform float ZenkaiShellSpeed;     // velocidad del giro/subida del ruido (retorcerse)
uniform float ZenkaiShellDetail;    // peso del ruido frente a los lóbulos
uniform float ZenkaiShellUpDir;
uniform float ZenkaiShellGrowth;
uniform float ZenkaiShellTaper;
uniform float ZenkaiShellTip;
uniform vec4 ZenkaiBodyShape;       // y = respiración

out vec4 vColor;
out vec3 vNormal;
out vec3 vViewDir;
out float vPeak;
out float vLocalY;
out vec3 vUnit;

const float TAU = 6.28318530718;

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
    float t = GameTime * 1200.0;
    float angle = atan(p.z, p.x == 0.0 ? 0.00001 : p.x);

    // Dos octavas de ruido de baja frecuencia. U con multiplicador ENTERO (sin costura en atan);
    // el desfase por tiempo en U hace GIRAR la masa, el de V la hace subir.
    vec2 uv1 = vec2(angle / TAU * 2.0 + t * 0.02 * ZenkaiShellSpeed, localY * 0.45 - t * ZenkaiShellSpeed * 0.12);
    vec2 uv2 = vec2(angle / TAU * 4.0 - t * 0.035 * ZenkaiShellSpeed, localY * 0.9 - t * ZenkaiShellSpeed * 0.2);
    float n = (smoothNoise(uv1).r - 0.5) * 1.3 + (smoothNoise(uv2).g - 0.5) * 0.6;
    float lobes = max(ZenkaiShellPeaks, 1.0);
    float lob = 0.5 + 0.5 * cos((angle * lobes / TAU + t * 0.05 * ZenkaiShellSpeed) * TAU);
    float total = mix(lob * 0.8, n * 1.2 + 0.2, clamp(ZenkaiShellDetail, 0.0, 1.0));

    float falloff = smoothstep(-0.95, -0.4, localY);

    float g = clamp(ZenkaiShellGrowth, 0.0, 1.0);
    float smoothGrowth = g * g * (3.0 - 2.0 * g);

    vec3 pos = p * ZenkaiShellSize;
    if (p.y < 0.0) pos.y = p.y * ZenkaiShellLowerHalf;
    pos.xz *= smoothGrowth;
    // Respiración pesada: dos senos desfasados, nunca un latido limpio.
    pos *= 1.0 + ZenkaiBodyShape.y * (sin(t * 1.3) + 0.5 * sin(t * 2.9 + 1.7));

    vec3 radial = normalize(pos + vec3(0.0, 0.00001, 0.0));
    vec3 dir = normalize(mix(radial, vec3(0.0, 1.0, 0.0), ZenkaiShellUpDir * smoothstep(0.2, 1.0, localY)));
    float disp = total * ZenkaiShellAmp * falloff * pow(g, 4.0);
    // Abolladuras permitidas, pero acotadas: la masa no se hunde hasta el cuerpo.
    pos += dir * max(disp, -0.3 * ZenkaiShellAmp);
    // Humo pesado: los bultos de arriba suben un poco.
    pos.y += ZenkaiShellTip * max(n, 0.0) * smoothstep(0.4, 1.0, localY) * pow(g, 3.0);

    float reach = ZenkaiShellSize.y + ZenkaiShellAmp + ZenkaiShellTip;
    float hNorm = clamp(pos.y / max(reach, 0.001), -1.0, 1.0);
    pos.xz *= max(0.2, 1.0 - ZenkaiShellTaper * smoothstep(0.3, 1.0, hNorm));

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
