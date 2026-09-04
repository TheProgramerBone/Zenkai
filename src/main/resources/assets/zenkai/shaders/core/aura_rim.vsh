#version 150

// Picos angulares del rim de aura (AuraRimRenderer), inspirado en el vertex shader de aura de
// dbrebirth-0.3 (desplaza la silueta del propio jugador con una onda angular + ruido). Portado
// con una diferencia de fondo: en Zenkai los vértices llegan YA transformados por el PoseStack
// (Minecraft hornea posición de mundo + yaw de cuerpo en CPU antes de emitir el vértice — mismo
// principio que documenta ki_fresnel.vsh), así que `Position` NO está en espacio local del
// modelo como en dbrebirth. Sin corregir eso, un `atan(Position.z, Position.x)` daría un ángulo
// que gira CON el jugador en vez de fijo a su silueta: un pico que hoy sale por el hombro
// derecho saldría por la espalda en cuanto el jugador se diera la vuelta.
//
// InvBodyRotMat deshace SOLO el yaw de cuerpo (lo sube AuraRimRenderer cada frame desde
// player.yBodyRot/yBodyRotO interpolado) para obtener un ángulo estable en espacio de modelo
// antes de medir los picos — la traslación al mundo y la cámara siguen aplicadas normalmente
// vía ModelViewMat/ProjMat después, sin tocar.

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform mat4 InvBodyRotMat;
uniform float GameTime;
uniform float ZenkaiSpikeAmount;    // AuraProfile.spike(), 0..1 — amplitud de los picos
uniform float ZenkaiSpikeCount;     // nº de picos alrededor del eje Y
uniform vec2  ZenkaiSpikeFalloffY;  // x = altura donde empiezan a aparecer, y = altura de fuerza plena

out vec4 vColor;
out vec3 vNormal;
out vec3 vViewDir;
out vec2 vUv;

const float TAU = 6.28318530718;

// Onda triangular (0..1..0..1...): más barata que un seno y con un filo neto en vez de una
// ondulación suave — es lo que hace que se lean como PICOS y no como bultos redondeados.
float triangleWave(float x) {
    return abs(fract(x) * 2.0 - 1.0);
}

void main() {
    // Deshacer solo la rotación (mat3, sin traslación) para medir el ángulo en espacio de
    // modelo del jugador, estable frente a su yaw.
    vec3 localPos = mat3(InvBodyRotMat) * Position;
    vec3 localN   = normalize(mat3(InvBodyRotMat) * Normal);

    float angle = atan(localPos.z, localPos.x);                    // -PI..PI, estable con el yaw
    float wave  = triangleWave(angle * ZenkaiSpikeCount / TAU);
    // Componente vertical: sin ella los picos serían anillos horizontales perfectos a cada
    // altura; con ella se rompen en un patrón que sube/baja, más orgánico que un peine regular.
    float verticalWobble = sin(localPos.y * 9.0 + GameTime * 0.35) * 0.15;

    // Banda en Y: sube de 0 a 1 entre FalloffY.x/.y y vuelve a bajar 0.6 unidades por encima de
    // FalloffY.y — evita deformar pies (por debajo de .x) y cabeza (muy por encima de .y). El
    // ancho de la caída de vuelta (0.6) es el primer número a tocar si los picos siguen
    // asomando por la cabeza/botas al calibrar en juego.
    float yFactor = smoothstep(ZenkaiSpikeFalloffY.x, ZenkaiSpikeFalloffY.y, localPos.y);
    yFactor *= 1.0 - smoothstep(ZenkaiSpikeFalloffY.y, ZenkaiSpikeFalloffY.y + 0.6, localPos.y);

    // pow(amplitud, 6.0): mismo exponente que dbrebirth — el efecto aparece de golpe cerca del
    // máximo de spike, no linealmente, así que un spike bajo (mayoría de auras) no se nota nada
    // y solo las firmas con spike alto muestran picos marcados.
    float amp = pow(clamp(ZenkaiSpikeAmount, 0.0, 1.0), 6.0);
    // 0.12 = escala del desplazamiento en bloques: ajustar a ojo en juego junto a FalloffY.
    float displacement = (wave + verticalWobble) * amp * yFactor * 0.12;

    vec3 displaced = Position + Normal * displacement;

    vec4 viewPos = ModelViewMat * vec4(displaced, 1.0);
    gl_Position = ProjMat * viewPos;

    vNormal  = normalize(mat3(ModelViewMat) * Normal);
    vViewDir = normalize(-viewPos.xyz);
    vColor   = Color;
    vUv      = UV0;
}
