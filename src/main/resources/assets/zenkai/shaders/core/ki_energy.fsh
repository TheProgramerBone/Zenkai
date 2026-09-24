#version 150

// Fresnel de TRES BANDAS. Es lo que convierte una malla teñida en energía de Dragon Ball: núcleo
// blanco, cuerpo del color de la técnica y contorno más profundo, con los límites temblando y el
// borde exterior disolviéndose en el aire.
//
// LOS TRES COLORES SALEN DEL TINTE DEL VÉRTICE, no de uniforms. El color de una técnica lo elige
// el jugador y ya viaja por vértice; derivar aquí el núcleo (tinte lavado a blanco) y el contorno
// (tinte oscurecido) evita tener el mismo color en tres sitios. Los uniforms solo llevan la FORMA
// de la rampa (dirección de arte por tipo de técnica, ver KiVfxProfile).
//
// La banda se decide con `g`, un escalar 0..1 que significa "cuánto núcleo hay aquí". Cómo se
// calcula depende de ZenkaiShape, porque una esfera, un disco plano y una burbuja no comparten
// noción de "centro":
//   0 SURFACE — superficies cerradas (esfera, haz, hélice). Para una esfera vista desde fuera, `g`
//               es EXACTAMENTE 1 menos el radio en pantalla: los niveles de banda se leen como
//               porcentajes del radio.
//   1 RADIAL  — superficies planas (disco): núcleo en el centro geométrico, vía UV.
//   2 RIM     — burbuja (barrera/Death Ball): fresnel INVERTIDO. Brilla en el filo y desaparece
//               de frente — lo que la hace leer como cristal/plasma, no como una bola sólida.
//
// EL BORDE NO SE CORTA. `edgeFade` (ZenkaiTone.z) es la anchura del desvanecido final: con un
// valor pequeño la silueta queda nítida y el proyectil se ve como un objeto sobrepuesto en la
// escena por muy bien que estén las bandas de dentro — el parámetro que separa "energía" de
// "calcomanía".
//
// CAPA DE DETALLE (ZenkaiDetail). La textura NUNCA aporta su propio color (sería un color fijo
// peleando con el tinte del vértice, que es lo que permite que un solo shader sirva para
// cualquier color de técnica) — es blanca con el patrón en alfa, igual que halo/estela, y solo
// modula BRILLO. ZenkaiDetail 0 = capa desactivada (la mayoría de técnicas).
//
// BORDE DENTADO. El hervor del borde es una suma de `ridge()` (picos angulosos sobre valles
// suaves), no de senos — ver esa función. El núcleo (`flameField`) se queda liso a propósito: el
// centro blanco es limpio, es SOLO el borde el que se ve roto/tipo llama.
//
// ── BUG DE CÁMARA (root-caused con dos vídeos + capturas del usuario; STEP 9 de la auditoría) ──
// SÍNTOMA: un haz fino y largo (Kamehameha, Death Beam, Lazer...) se convertía en una CUÑA BLANCA
// SÓLIDA enorme en cuanto la vista quedaba casi alineada con el EJE del haz — no solo de cerca
// (ver ZenkaiProximity, mitigación insuficiente por sí sola).
// DESCARTADO PRIMERO, verificado explícitamente: NO es mezcla de espacios (normal y view-dir
// viven los dos en espacio de VISTA, ver ki_energy.vsh) y NO es billboard (la geometría es 3D
// real, generada en espacio local por KiVfxGeometry, sin ningún vértice derivado de cámara).
// CAUSA REAL: SURFACE decide el núcleo con `dot(V,N)` puro sobre TODA la superficie visible. En
// una ESFERA eso da un punto brillante de tamaño angular CONSTANTE (cada punto de la superficie
// tiene una normal distinta en todas direcciones). En un TUBO/CONO LARGO mirado casi a lo largo
// de su propio eje, la normal radial de CUALQUIER punto de la longitud visible queda
// simultáneamente casi perpendicular a la vista de la misma manera: no hay un único "punto que
// encara a cámara", TODA la longitud lo hace a la vez, y el núcleo deja de ser un punto para
// cubrir el haz entero.
// ARREGLO DE RAÍZ, dos capas independientes:
//  1) `ZenkaiAxial` (formas alargadas) hace que el fresnel ignore la componente de la vista A LO
//     LARGO del eje de vuelo (`vAxis`) antes de compararla con la normal — lo que queda es la
//     vista "de perfil" pura del tubo, que no depende de en qué punto de la LONGITUD estés ni de
//     cuán paralela sea la vista al eje. `vAxis` no es un uniform por proyectil: es el vector
//     local (0,0,1) que KiVfxGeometry ya usa siempre como eje, transformado por la MISMA matriz
//     que ya transforma la normal (ver ki_energy.vsh) — no hay estado que un lote de proyectiles
//     distintos pueda desincronizar entre sí.
//  2) GARANTÍA DURA (defensa en profundidad, no un ajuste de umbral): la cáscara de una forma
//     alargada YA NO es responsable del centro blanco en absoluto — eso lo dibuja una malla
//     APARTE (ver KiVfxCompositeRenderer/KiVfxGeometry.core), inmune por construcción porque su
//     ancho en pantalla lo decide su propio radio, nunca un ángulo de vista. Acotar aquí el
//     blanqueado de la cáscara a un máximo bajo, SIEMPRE, hace que la cuña blanca sea
//     MATEMÁTICAMENTE IMPOSIBLE sea cual sea `g` — no solo "menos probable".
// `ZenkaiProximity` queda como red de seguridad SECUNDARIA para el caso — mucho más raro — de
// cámara pegada a la superficie de CUALQUIER forma, incluida una esfera, donde ningún arreglo de
// eje aplica.
//
// MODOS DE DEPURACIÓN (ZenkaiDebugMode, ver KiVfxDebugMode/STEP 11 de la auditoría). 0 = normal.
// 1 = pintar por NORMAL (espacio de vista, RGB = N*0.5+0.5): si el color cambia SUAVE al rotar la
// cámara, la normal/transform son correctos y cualquier artefacto que persista viene de más
// adelante en este mismo shader (las bandas) — si cambia a SALTOS, el bug está en la geometría o
// en el vertex shader, no aquí.

in vec4 vColor;
in vec3 vNormal;
in vec3 vViewDir;
in vec3 vAxis;
in vec2 vUv;
in vec3 vField;

uniform sampler2D Sampler0; // ki_detail.png — SOLO variación de brillo, ver ZenkaiDetail
uniform vec4 ColorModulator;
uniform float GameTime;
uniform float ZenkaiShape;
uniform vec3 ZenkaiBands;   // x = nivel del núcleo, y = del cuerpo, z = del contorno
uniform vec3 ZenkaiTone;    // x = blancura del núcleo, y = oscurecido del contorno, z = edgeFade
uniform float ZenkaiWobble; // amplitud del hervor (0 = bandas perfectamente quietas)
uniform float ZenkaiDetail; // 0 = sin capa de detalle (la mayoría de técnicas hoy)
uniform float ZenkaiFrozenTime; // -1 = usar GameTime real; >=0 = fase FIJA
uniform float ZenkaiProximity;  // 1 = cámara lejos; 0 = pegada a la superficie
uniform float ZenkaiAxial;      // 1 = forma alargada con eje de vuelo (haz/hélice)
uniform float ZenkaiDebugMode;  // 0 normal, 1 = pintar por normal (ver cabecera)
uniform float ZenkaiBloomMode;  // 1 = pasada de bloom de KiVfxBloomPipeline (ver main)
uniform vec2 ZenkaiEmit;        // emisión: x = cuerpo, y = banda de núcleo (ver SALIDA en main)
uniform float ZenkaiBloomBody;  // peso del cuerpo (no núcleo) en la pasada de bloom

out vec4 fragColor;

const float WOBBLE_CORE    = 0.15;
// 0.13 -> 0.42 (2026-09-24, ver KiVfxProfile "MULTIPLICADORES GLOBALES DE SILUETA"). Con 0.13 el
// hervor del borde apenas movía `edge` respecto al ancho del smoothstep de edgeFade — se leía
// como un tinte que tiembla, no como una silueta rota. A esta amplitud el perturbado de `edge`
// cruza el umbral de alfa repetidamente alrededor del contorno: el borde deja de ser una
// circunferencia con textura y pasa a tener picos/muescas reales, el "diente de llama"/cristal
// de las referencias (kamehameha_1-3, finalflash_1/3, deathball_1-4) en vez de un círculo suave.
const float WOBBLE_OUTLINE = 0.42;

/** Campo de llama SUAVE: cuatro senos con frecuencias primas entre sí. Reservado para el
 *  parpadeo del NÚCLEO — en todas las referencias (Kamehameha, Final Flash...) el centro blanco
 *  es liso, nunca dentado. No es ruido real y no hace falta que lo sea: solo tiene que no
 *  repetirse a la vista en los pocos segundos que vive un proyectil. */
float flameField(vec3 p, float t, float freq, float speed) {
    float v  = sin(p.x *  9.0 * freq + t * 4.0 * speed)               * 0.50;
    v       += sin(p.y * 11.0 * freq - t * 5.5 * speed + p.x * 3.0)   * 0.32;
    v       += sin(p.z * 13.0 * freq + t * 6.5 * speed + p.y * 4.0)   * 0.24;
    v       += sin((p.x + p.y + p.z) * 19.0 * freq - t * 8.0 * speed) * 0.16;
    return v;
}

/** Cresta: MISMO seno de siempre, pasado por `1 - 2·|sin|` en vez de usarlo tal cual. `|sin(x)|`
 *  tiene un pico ANGULOSO en cada cruce por cero y un valle REDONDEADO en cada cresta — invertido
 *  y reescalado a [-1,1] eso da picos afilados sobre valles suaves: el "diente de llama" de las
 *  referencias en vez del bulto romo de una onda. */
float ridge(float x) {
    return 1.0 - 2.0 * abs(sin(x));
}

/** Campo de llama DENTADO: misma estructura que flameField (mismas frecuencias/pesos), pero con
 *  `ridge()` en vez de `sin()`, más una quinta octava de frecuencia alta y peso bajo para que los
 *  picos no salgan todos del mismo tamaño. Reservada para el BORDE. */
float ridgedField(vec3 p, float t, float freq, float speed) {
    float v  = ridge(p.x *  9.0 * freq + t * 4.0 * speed)               * 0.50;
    v       += ridge(p.y * 11.0 * freq - t * 5.5 * speed + p.x * 3.0)   * 0.32;
    v       += ridge(p.z * 13.0 * freq + t * 6.5 * speed + p.y * 4.0)   * 0.24;
    v       += ridge((p.x + p.y + p.z) * 19.0 * freq - t * 8.0 * speed) * 0.16;
    v       += ridge((p.x * 2.0 - p.y * 1.5 + p.z) * 31.0 * freq + t * 9.0 * speed) * 0.18;
    return v;
}

void main() {
    vec3 N = normalize(vNormal);
    vec3 V = normalize(vViewDir);

    if (ZenkaiDebugMode > 0.5) {
        // NORMALS: sin bandas, sin hervor, sin alfa variable — solo la normal como color, para
        // aislar el vertex/transform del resto del pipeline (ver cabecera).
        fragColor = vec4((N * 0.5 + 0.5) * vColor.a, vColor.a) * ColorModulator;
        return;
    }

    float t = (ZenkaiFrozenTime >= 0.0) ? ZenkaiFrozenTime : GameTime * 24000.0 * 0.05;

    float g;
    if (ZenkaiShape > 1.5) {                     // RIM
        float f = abs(dot(V, N));
        g = 1.0 - f;
        g = g * g;                               // aprieta el filo: si no, la burbuja se llena
    } else if (ZenkaiShape > 0.5) {              // RADIAL
        g = 1.0 - clamp(vUv.x, 0.0, 1.0);
    } else {                                     // SURFACE
        vec3 Vs = V;
        if (ZenkaiAxial > 0.5) {
            // Quita de V su componente A LO LARGO DEL EJE (vAxis) antes de compararla con la
            // normal — ver "BUG DE CÁMARA" en la cabecera. Lo que queda es la vista "de perfil"
            // pura del tubo: ya no depende de en qué punto de la LONGITUD estés ni de cuán
            // paralela sea la vista al eje, solo del ángulo alrededor del tubo.
            vec3 axis = normalize(vAxis);
            vec3 radial = V - axis * dot(V, axis);
            float rl = length(radial);
            // rl≈0: vista EXACTAMENTE por el eje (de frente al bulbo de la punta, o justo por
            // detrás). No hay "de perfil" que extraer — cae al V completo, correcto en ese punto
            // exacto de todos modos (la cabeza esférica).
            Vs = rl > 1.0e-4 ? radial / rl : V;
        }
        float f = abs(dot(Vs, N));
        g = 1.0 - sqrt(max(0.0, 1.0 - f * f));
    }

    // ZenkaiProximity: red de seguridad SECUNDARIA (ver "BUG DE CÁMARA"). Sube el umbral de
    // núcleo/cuerpo cuando la cámara está cerca respecto al propio radio de la técnica, para que
    // la franja que la perspectiva abre en abanico sobre un tubo largo vuelva a leerse del tamaño
    // de "punto" que tiene de lejos. A ZenkaiProximity 1.0 (cámara lejos) `bands` queda
    // exactamente en ZenkaiBands.
    float proximityRaise = (1.0 - ZenkaiProximity) * 0.42;
    vec3 bands = clamp(ZenkaiBands + vec3(proximityRaise, proximityRaise * 0.65, 0.0), 0.0, 0.97);

    float flameCore    = flameField(vField, t, 1.35, 1.30);
    float flameOutline = ridgedField(vField, t, 0.65, 0.65);

    vec3 tint    = vColor.rgb;
    vec3 core    = mix(tint, vec3(1.0), ZenkaiTone.x);
    vec3 outline = tint * ZenkaiTone.y;

    vec3 col = outline;
    col = mix(col, tint, smoothstep(bands.z, bands.y,
                                    g + flameOutline * WOBBLE_OUTLINE * ZenkaiWobble));
    float toCore = smoothstep(bands.y, bands.x,
                              g + flameCore * WOBBLE_CORE * ZenkaiWobble);

    // GARANTÍA DURA (ver "BUG DE CÁMARA", arreglo 2 de 2): la cáscara de una forma alargada ya NO
    // es responsable del centro blanco — eso lo dibuja la malla de núcleo aparte, inmune por
    // construcción. Acotar aquí el blanqueado de la cáscara a un máximo bajo, SIEMPRE, hace la
    // cuña blanca MATEMÁTICAMENTE IMPOSIBLE sea cual sea `g`, no solo menos probable.
    if (ZenkaiAxial > 0.5) toCore = min(toCore, 0.18);

    col = mix(col, core, toCore);

    // Parpadeo de intensidad, más fuerte en el núcleo — impide que la técnica se vea como
    // plástico pintado cuando el proyectil está quieto respecto a la cámara.
    col *= 1.0 + flameCore * mix(0.08, 0.20, toCore) * ZenkaiWobble;

    // Detalle de superficie: variación de brillo tileable, deslizándose sobre la malla en el
    // tiempo. `fract()` a mano en vez de fiarse del wrap de la textura: no todos los ejes UV de
    // todas las formas dan la vuelta completa, así que envolver aquí es lo único que garantiza
    // tileado sin costura en cualquier forma. Puramente multiplicativo, nunca mezcla color.
    if (ZenkaiDetail > 0.0) {
        vec2 detailUv = fract(vUv * 3.0 + vec2(t * 0.006, -t * 0.010));
        float d = texture(Sampler0, detailUv).a;
        col *= mix(1.0, 0.65 + 0.70 * d, ZenkaiDetail);
    }

    float edge = g + flameOutline * WOBBLE_OUTLINE * ZenkaiWobble;
    float alpha = vColor.a * smoothstep(0.0, max(1.0e-3, ZenkaiTone.z), edge);

    // PASADA DE BLOOM (KiVfxFrameQueue.bloomPass): la MISMA geometría, con el mismo depth test y
    // culling, redibujada sobre negro en el target de bloom. Solo cambia CUÁNTO aporta cada
    // píxel: el núcleo pesa entero y el cuerpo/contorno poco, para que el resplandor nazca del
    // centro caliente y no lave la técnica entera (misma idea que el bloomMode de dragonminez).
    if (ZenkaiBloomMode > 0.5) {
        alpha *= mix(ZenkaiBloomBody, 1.0, toCore);
    }

    // SALIDA EN ALFA PREMULTIPLICADO (blend ONE / ONE_MINUS_SRC_ALPHA, ver KiVfxRenderTypes). El
    // color va ya multiplicado por su alfa; el alfa de salida es solo la parte que TAPA. La
    // emisión crece del cuerpo al núcleo: el centro suma luz (brilla incluso sobre un cielo
    // claro) y el cuerpo sigue tapando lo bastante para leerse como materia, no como un
    // resplandor sin forma. Ajustable en vivo: /zkvfx set emit.body|emit.core.
    vec4 c = vec4(col, alpha) * ColorModulator;
    if (c.a < 0.004) discard;
    float emit = clamp(mix(ZenkaiEmit.x, ZenkaiEmit.y, toCore), 0.0, 1.0);
    fragColor = vec4(c.rgb * c.a, c.a * (1.0 - emit));
}
