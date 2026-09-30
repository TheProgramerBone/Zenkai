#version 150

// LLAMA EXTERIOR del sistema de aura nuevo (estilo dbrebirth-0.3). Port de la fórmula de
// .claude/mods de referencia/dbrebirth-0.3/assets/dbrebirth/shaders/core/aura.vsh sobre una malla
// PROPIA (AuraFlameShellMesh: esfera unitaria), no sobre el modelo del jugador — ver el javadoc de
// esa clase para el porqué (cubos del PlayerModel = caras separadas, confirmado en juego).
//
// ESPACIO: Position llega en coordenadas UNITARIAS de la esfera, sin pasar por el PoseStack en
// CPU. Toda la forma se calcula aquí en espacio LOCAL del jugador (pies en el origen, Y arriba,
// ya rotado con el cuerpo) y solo al final se lleva a mundo con ZenkaiShellModelMat
// (pose del evento * traslación al jugador * giro de cuerpo * escala de cuerpo). Así no hace falta
// ninguna corrección de origen como la de aura_flame.vsh: el ángulo y la altura ya son locales.
//
// FORMA:
//  - elipsoide base de semiejes ZenkaiShellSize centrada en ZenkaiShellCenter;
//  - onda angular triangular (ZenkaiShellPeaks picos alrededor) * onda vertical triangular que
//    SUBE con el tiempo (lenguas que trepan) + ruido (temblor del contorno);
//  - desplazamiento en una mezcla entre radial y vertical (ZenkaiShellUpDir): radial puro da una
//    bola erizada, vertical da lenguas que suben — la mezcla da la lágrima de las referencias;
//  - falloff abajo para que los pies no se deformen (las lenguas NACEN en los pies).
// RUIDO (Fase 3): canal R de aura_noise.png (tools/gen_aura_noise.py, tileable) en Sampler0 —
// el NoiseTex de dbrebirth. U = ángulo alrededor (x2: ENTERO a propósito, así el salto de atan de
// +PI a -PI cae en un múltiplo exacto de la textura y no deja costura en la espalda), V = altura
// con scroll hacia arriba. textureLod explícito: en el vertex shader no hay derivadas para elegir
// mip.

in vec3 Position;
in vec4 Color;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform float GameTime;
uniform sampler2D Sampler0; // aura_noise.png

uniform mat4 ZenkaiShellModelMat;
uniform vec3 ZenkaiShellCenter;   // centro local de la elipsoide (bloques, sobre los pies)
uniform vec3 ZenkaiShellSize;     // semiejes de la elipsoide (bloques)
uniform float ZenkaiShellAmp;     // bloques de desplazamiento de un pico típico a media altura
uniform float ZenkaiShellTopBoost;// refuerzo del desplazamiento hacia arriba (0 = uniforme)
uniform float ZenkaiShellPeaks;   // nº de picos alrededor (entero, ver AuraFlameParams)
uniform float ZenkaiShellSharp;   // 0 = lóbulos redondos, 1 = picos triangulares (AuraProfile.spike)
uniform float ZenkaiShellFreqY;   // pisos de lenguas en vertical
uniform float ZenkaiShellSpeed;   // ciclos por segundo de la onda vertical
uniform float ZenkaiShellDetail;  // peso del ruido
uniform float ZenkaiShellUpDir;   // 0 radial .. 1 vertical
uniform float ZenkaiShellGrowth;  // 0..1, encendido/apagado del aura

out vec4 vColor;
out vec3 vNormal;
out vec3 vViewDir;
out float vPeak;   // 0 = valle, 1 = punta de pico (para teñir las puntas hacia el color exterior)
out float vLocalY; // Y de la esfera unitaria, -1 (pies) .. 1 (punta)
out vec3 vUnit;    // punto de la esfera unitaria: el fragment calcula su propia UV de ruido
                   // (interpolar una UV angular entre vértices la mancharía en la costura de atan)

const float TAU = 6.28318530718;

float triangleWave(float x) {
    return abs(fract(x) * 2.0 - 1.0);
}

void main() {
    vec3 p = Position;
    float localY = p.y;
    // GameTime es la fracción del día (0..1 cada 24000 ticks): *1200 = segundos de juego, mismo
    // criterio que ki_energy.fsh. (El aura_flame.vsh viejo usaba GameTime*0.6, casi inmóvil.)
    float t = GameTime * 1200.0;

    float angle = atan(p.z, p.x == 0.0 ? 0.00001 : p.x);
    // Fase 6: la forma del pico sale de AuraProfile.spike — onda triangular (filo, SSJ) o coseno
    // con el mismo periodo, rango 0..1 y FASE (las dos valen 1 en x entero y 0 en x+0.5, así la
    // mezcla no desplaza los picos de sitio) — lóbulo, auras blandas tipo aura_5.
    float wavePhase = angle * ZenkaiShellPeaks / TAU;
    float angularWave = mix(0.5 + 0.5 * cos(wavePhase * TAU), triangleWave(wavePhase),
                            clamp(ZenkaiShellSharp, 0.0, 1.0));
    float layer = triangleWave(localY * ZenkaiShellFreqY - t * ZenkaiShellSpeed);

    // Mismo rango que dbrebirth (tex*1.5-0.5 -> -0.5..1). El scroll va algo más lento que la onda
    // de lenguas: si fueran a la par el temblor se leería pegado a los picos, no como vida propia.
    vec2 noiseUV = vec2(angle / TAU * 2.0, localY * 1.1 - t * ZenkaiShellSpeed * 0.8);
    float noiseValue = textureLod(Sampler0, noiseUV, 0.0).r * 1.5 - 0.5;

    float total = layer * angularWave + noiseValue * ZenkaiShellDetail * 1.08;
    // Los pies (y=-1) no se deforman; desde la cintura hacia arriba, fuerza plena.
    float falloff = smoothstep(-0.95, -0.2, localY);
    // Fase 2: las lenguas más largas salen de la parte alta (lágrima), no por igual en cualquier
    // altura (bola erizada) — sin esto la llama apenas pasaba de la cabeza en la primera prueba.
    falloff *= 1.0 + ZenkaiShellTopBoost * smoothstep(0.0, 1.0, localY);

    float g = clamp(ZenkaiShellGrowth, 0.0, 1.0);
    float smoothGrowth = g * g * (3.0 - 2.0 * g);

    vec3 pos = p * ZenkaiShellSize;
    pos.xz *= smoothGrowth;
    vec3 radial = normalize(pos + vec3(0.0, 0.00001, 0.0));
    vec3 dir = normalize(mix(radial, vec3(0.0, 1.0, 0.0), ZenkaiShellUpDir));
    float disp = total * ZenkaiShellAmp * falloff * pow(g, 6.0);
    pos += dir * disp;

    vec4 world = ZenkaiShellModelMat * vec4(ZenkaiShellCenter + pos, 1.0);
    vec4 viewPos = ModelViewMat * world;
    gl_Position = ProjMat * viewPos;

    // Normal "radial" de la superficie desplazada, igual que dbrebirth (normalize(pos)): da un
    // fresnel de elipsoide suave; las puntas se leen por su silueta, no por la normal.
    vec3 nLocal = normalize(pos + vec3(0.0, 0.00001, 0.0));
    vNormal  = normalize(mat3(ModelViewMat) * mat3(ZenkaiShellModelMat) * nLocal);
    vViewDir = -viewPos.xyz;
    vColor   = Color;
    vPeak    = clamp(total * falloff, 0.0, 1.0);
    vLocalY  = localY;
    vUnit    = p;
}
