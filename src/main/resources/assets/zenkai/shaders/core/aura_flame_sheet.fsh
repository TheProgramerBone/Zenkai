#version 150

// LÁMINAS de llama — fragment (ver aura_flame_sheet.vsh). Lo que reproduce, medido en los fotogramas
// de SSG (ReferenciaAuraGokuGod2, VideoSinRecortarGokuGOD2 388-432 s):
//  - lenguas VERTICALES que suben (ruido estirado en vertical, desplazado hacia arriba con el tiempo);
//  - degradado continuo carmesí (núcleo) → naranja → amarillo (bordes y puntas), mezcla aditiva
//    (2026-10-27; antes cuatro bandas cel con núcleo blanco, que se leían como picos sólidos);
//  - las puntas se rompen en lenguas sueltas arriba (el umbral sube con la altura).
// Salida premultiplicada como el resto del aura (vec4(c*a, a*(1-emit))); la llama usa emit = 1, es decir,
// aditiva pura. Contrato del vértice en el vsh: Normal = tono del NÚCLEO (exterior de la forma).

in vec4 vColor;
in vec2 vUV;
in vec3 vShadow;
in float vDist;

uniform sampler2D Sampler0; // aura_noise.png
uniform vec4 ColorModulator;
uniform float GameTime;

out vec4 fragColor;

// Mismo ruido suave que los cuerpos (smoothstep entre texels): sin él las lenguas salen en tramos rectos.
vec4 smoothNoise(vec2 uv) {
    vec2 ts = vec2(textureSize(Sampler0, 0));
    vec2 p = uv * ts - 0.5;
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return textureLod(Sampler0, (i + f + 0.5) / ts, 0.0);
}

void main() {
    float t = GameTime * 1200.0; // segundos de juego
    float seed = floor(vUV.x);
    bool light = vUV.y >= 1.5;
    float y = light ? vUV.y - 2.0 : vUV.y;
    // Ondulado lento de lado, más arriba que abajo: fuego LÍQUIDO y calmado (descripción del usuario
    // de la llama de SSG, 2026-10-26), no un parpadeo.
    float x = fract(vUV.x) + sin(y * 3.2 - t * 0.6 + seed * 1.7) * 0.04 * y * (light ? 0.3 : 1.0); // el grueso del ondulado ya lo da la malla (FlameSheetComponent)
    float cx = abs(x - 0.5) * 2.0; // 0 en el centro, 1 en los cantos
    vec3 body = vColor.rgb;

    // aura_noise.png (128 px) ya tiene vetas verticales de 10-20 texels. En los fotogramas de SSG
    // (ReferenciaAuraGokuGod2 2,5 s) las lenguas son MUCHAS vetas largas y estrechas casi paralelas: 4-5
    // por lámina y muy estiradas en vertical (con 0.22/0.30 salían 2-3 lenguas anchas y redondas).
    // Subida LENTA (calma divina, "fuego lento, plasma": el usuario la vio demasiado rápida, 2026-10-27).
    float n1 = smoothNoise(vec2(x * 0.40 + seed * 0.173, y * 0.17 - t * 0.11 + seed * 0.31)).r;
    float n2 = smoothNoise(vec2(x * 0.85 + seed * 0.291, y * 0.36 - t * 0.18)).g;
    float n = n1 * 0.62 + n2 * 0.38;

    vec3 color;
    float alpha;
    float emit;
    if (light) {
        // Columna de luz (Ultra Instinct): recta, núcleo blanco y borde del tinte, ondula poco.
        float wob = (n - 0.5) * 0.25;
        float d = clamp(abs(x - 0.5 + wob * 0.3) * 2.0, 0.0, 1.0);
        float i = (1.0 - d * d) * smoothstep(0.0, 0.15, y) * (1.0 - smoothstep(0.75, 1.0, y));
        float coreB = step(0.62, i);
        float bodyB = step(0.25, i);
        color = mix(body, vec3(1.0), coreB * 0.85);
        alpha = mix(0.0, 0.55, bodyB) + 0.35 * coreB;
        emit = 0.35;
    } else {
        // Envolvente: llena abajo y en el centro, se cierra hacia arriba y hacia los cantos.
        // Con pow 2.2 la envolvente seguía alta casi hasta el canto y la lámina acababa en un borde
        // vertical recto (v10, 2026-10-27); más cerrada, y el canto se funde abajo.
        float env = (1.0 - pow(cx, 1.4)) * (1.0 - 0.55 * y);
        // Las lenguas: el ruido manda más cuanto más arriba (abajo es masa, arriba se rompe).
        float i = env * mix(0.95, 0.35 + 1.2 * n, smoothstep(0.0, 0.7, y)) - smoothstep(0.55, 1.0, y) * 0.45;
        i += (n2 - 0.5) * 0.18;
        // Corona IRREGULAR: cada veta acaba a su altura (en la referencia unas lenguas suben el doble
        // que sus vecinas); con un solo corte las puntas formaban una línea casi recta (v9, 2026-10-27).
        // El tope nunca pasa de 0.97: la punta queda SIEMPRE cerrada (sin esto el borde de arriba del
        // quad salía como un corte recto visto desde arriba).
        float topY = 0.45 + 0.5 * smoothNoise(vec2(x * 0.30 + seed * 0.53, 0.37 - t * 0.05)).b;
        i *= 1.0 - smoothstep(topY, min(topY + 0.3, 0.97), y);
        // Colores de la referencia del usuario (GokuGod.jpg, 2026-10-27):
        //  - cada lengua es NARANJA con un PERFIL amarillo brillante y nítido (también dentro, donde una
        //    lengua pisa a otra: los valles del ruido);
        //  - el carmesí-magenta NO va en cada lengua (v17 lo ponía en el centro de todas y el aura se
        //    leía roja): es un resplandor suave y central, detrás del pecho y la cabeza.
        // Sale de los tintes de la forma (exterior carmesí, tinte ámbar en SSG), no de colores fijos: las
        // *_god de otras razas conservan los suyos.
        vec3 crimson = clamp(vShadow, 0.0, 1.0);
        vec3 orange = mix(crimson, body, 0.7);
        vec3 yellow = body + (vec3(1.0) - body) * 0.6;
        float rimB = smoothstep(0.10, 0.13, i);
        float bodyB = smoothstep(0.25, 0.29, i);
        color = mix(yellow, orange, bodyB);
        // A la altura del pecho y la cabeza (en la v18, pegado a la base, el aura salía roja por abajo).
        float glow = (1.0 - smoothstep(0.15, 0.55, cx)) * smoothstep(0.1, 0.25, y) * (1.0 - smoothstep(0.45, 0.7, y)) * bodyB;
        color = mix(color, crimson, glow * 0.85);
        alpha = rimB * mix(0.95, 0.88, bodyB) * mix(1.0, 0.75, glow);
        // Mezcla HÍBRIDA, 30 % aditiva: las capas superpuestas suman algo de luz (brillo interno sobre el
        // jugador) sin lavarse. Aditiva pura (emit 1, equivale a GL_SRC_ALPHA, GL_ONE) se probó en la v15
        // (2026-10-27): de día el carmesí sumado al cielo daba ROSA y el amarillo BLANCO, y la llama
        // perdía la saturación de la referencia.
        emit = 0.3;
        // Base fundida y REDONDEADA (más alta en los cantos): con una base recta, al tumbarse el aura
        // en vuelo las bases de varias láminas se alineaban en un borde diagonal recto, "cuadrado"
        // (capturas volando de lado, 2026-10-27). En el alfa: recortando la lengua (v20-v21) la base de
        // cada lámina sacaba perfil amarillo y el conjunto se leía como hojas redondas.
        alpha *= smoothstep(0.0, 0.25 + 0.35 * cx * cx, y + (n - 0.5) * 0.12);
        // Cantos con ruido, fundidos en el ALFA y cortos: recortando la lengua también aquí (v20), cada
        // lámina sacaba su propio perfil amarillo y el aura se leía como hojas redondas superpuestas; con
        // el fundido largo de la v19 (0.78-1.0) los flancos salían blandos.
        alpha *= 1.0 - smoothstep(0.84, 0.99, cx + (n2 - 0.5) * 0.12);
        // Cierre GARANTIZADO en los cuatro bordes del quad, sin ruido: el fundido de arriba depende del
        // ruido y en el canto (cx = 1) llegaba a dejar la mitad del alfa. Volando hacia la cámara, con la
        // lámina enorme en pantalla, eso se veía como un plano rosa de borde recto (v12, 2026-10-27).
        alpha *= (1.0 - smoothstep(0.80, 0.97, cx)) * smoothstep(0.0, 0.06, y) * (1.0 - smoothstep(0.92, 0.99, y));
    }

    alpha *= vColor.a;
    // Fundido cerca de la cámara: una lámina pegada al objetivo cruzaba la pantalla entera como un plano
    // pálido. Más amplio que el de los cuerpos (0,6-1,6): volando hacia la cámara, las puntas
    // tumbadas llegan a medio bloque del objetivo (2026-10-27).
    // Vuelto a 0,6-1,6 (el de los cuerpos) el 2026-10-27: con 1,0-2,6 y la lágrima arrancando bajo los
    // pies, desde abajo (cámara a -90°) la llama quedaba a ~2 bloques y salía casi transparente. Los
    // planos que obligaron a ampliarlo eran la lágrima de fondo, hoy apagada.
    alpha *= smoothstep(0.6, 1.6, vDist);
    alpha = clamp(alpha, 0.0, 1.0) * ColorModulator.a;
    if (alpha < 0.004) discard;
    fragColor = vec4(color * ColorModulator.rgb * alpha, alpha * (1.0 - emit));
}
