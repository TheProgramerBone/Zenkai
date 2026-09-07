#version 150

// FASE 1 del sistema de aura NUEVO (estilo dbrebirth-0.3, ver
// .claude/pendiente/aura-dbrebirth-sistema-propuesta.md). AISLADO de aura_rim.vsh a propósito:
// vive en su propio par vertex/fragment porque el propósito es distinto (una llama que ENVUELVE
// al jugador varias veces su tamaño, no un aro delgado pegado a la silueta) aunque reutiliza la
// misma plomería de espacio de coordenadas que aura_rim.vsh ya resolvió — leer su comentario de
// cabecera para el porqué completo de InvBodyRotMat: los vértices llegan YA transformados por el
// PoseStack (posición de mundo + yaw de cuerpo horneados en CPU), así que un `atan` directo sobre
// `Position` giraría CON el jugador en vez de fijo a su silueta.
//
// DIFERENCIA DE ESCALA: aura_rim desplaza como mucho 0.12 bloques (un aro delgado). Aquí
// ZenkaiFlameScale es del orden de 1-3 bloques — un pico que sube muy por encima de la cabeza.
// Valores fijados A MANO para esta fase (Fase 1: confirmar que la malla se deforma bien con la
// silueta correcta y que los picos se leen como picos — NO es calibración fina, eso es la Fase 2
// del documento).
//
// SIN CAÍDA DE VUELTA EN Y (a diferencia de ZenkaiSpikeFalloffY de aura_rim, que baja la fuerza
// de vuelta a 0 por encima de su umbral para no deformar la cabeza): aquí el efecto se mantiene a
// fuerza plena desde ZenkaiFlameFalloffY.y hacia arriba, a propósito — la cabeza es precisamente
// de donde deben salir los picos más altos.
//
// CORRECCIÓN DE ORIGEN (encontrada en la primera prueba en juego de esta fase, ver
// .claude/pendiente/aura-pendientes.md — el "artefacto sin diagnosticar" de aura_rim era
// probablemente este mismo fallo, invisible a su escala de 0.12 bloques). InvBodyRotMat deshace
// SOLO la rotación de cuerpo — pero `Position` sigue cargando la traslación cámara-relativa que
// el PoseStack ya horneó (Minecraft traduce el PoseStack a `posEntidad - posCámara` antes de
// dibujar), un vector grande y casi constante en TODO el modelo comparado con el propio tamaño
// del jugador. Sin cancelarlo, tanto el ángulo (`atan(z,x)`) como la altura (`.y`) quedan
// dominados por ese offset en vez de la geometría real del cuerpo: a escala de aura_flame (1-3
// bloques) esto infla el modelo ENTERO por igual en vez de picos localizados — confirmado en
// juego como una "cortina" que envuelve toda la pantalla, no picos sobre la cabeza.
//
// ZenkaiFlameOrigin es ese mismo vector (`player.getPosition(partialTick) - camera.getPosition()`,
// calculado en Java exactamente como el motor) — restarlo de Position ANTES de aplicar
// InvBodyRotMat recupera coordenadas de verdad locales al jugador (pequeñas, ~[-2, 2] en X/Z,
// ~[0, 1.8] en Y), independientes de dónde esté la cámara.

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform mat4 InvBodyRotMat;
uniform vec3 ZenkaiFlameOrigin;      // posEntidad - posCámara (partialTick), ver el comentario de cabecera
uniform float GameTime;
uniform float ZenkaiFlameAmount;    // 0..1, interruptor de intensidad general (1.0 fijo en esta fase)
uniform float ZenkaiFlameSpikeCount;// nº de picos alrededor del eje Y
uniform float ZenkaiFlameScale;     // bloques de desplazamiento en el pico más alto (1-3 en esta fase)
uniform vec2  ZenkaiFlameFalloffY;  // x = altura donde empieza el efecto, y = altura de fuerza plena

out vec4 vColor;
out vec3 vNormal;
out vec3 vViewDir;
out vec2 vUv;
out float vBlend; // 0 = núcleo (cerca del cuerpo), 1 = envolvente (en el pico) — ver AuraFlameRenderer

const float TAU = 6.28318530718;

// Onda triangular (0..1..0..1...): filo neto en vez de una ondulación suave — lo que hace que
// se lean como PICOS y no como bultos redondeados. Misma función que aura_rim.vsh.
float triangleWave(float x) {
    return abs(fract(x) * 2.0 - 1.0);
}

void main() {
    // Restar el origen ANTES de derotar: recupera coordenadas locales de verdad (ver el
    // comentario de cabecera), luego deshacer solo la rotación (mat3, sin traslación) para medir
    // el ángulo en espacio de modelo del jugador, estable frente a su yaw.
    vec3 localWorld = Position - ZenkaiFlameOrigin;
    vec3 localPos = mat3(InvBodyRotMat) * localWorld;

    float angle = atan(localPos.z, localPos.x);
    float wave  = triangleWave(angle * ZenkaiFlameSpikeCount / TAU);
    // Ondulación vertical: sin ella los picos serían un peine perfectamente regular a cada
    // altura; con ella el patrón sube/baja de forma más orgánica. Placeholder analítico —
    // sustituir por NoiseTex real en la Fase 3 del documento.
    float verticalWobble = sin(localPos.y * 5.0 + GameTime * 0.6) * 0.25;

    float yFactor = smoothstep(ZenkaiFlameFalloffY.x, ZenkaiFlameFalloffY.y, localPos.y);

    float amp = clamp(ZenkaiFlameAmount, 0.0, 1.0);
    float peak = clamp(wave + verticalWobble, 0.0, 1.0);
    float displacement = peak * yFactor * amp * ZenkaiFlameScale;

    vec3 displaced = Position + Normal * displacement;

    vec4 viewPos = ModelViewMat * vec4(displaced, 1.0);
    gl_Position = ProjMat * viewPos;

    vNormal  = normalize(mat3(ModelViewMat) * Normal);
    vViewDir = normalize(-viewPos.xyz);
    vColor   = Color;
    vUv      = UV0;
    vBlend   = peak * yFactor;
}
