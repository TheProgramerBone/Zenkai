#version 150

// Composición final: KiVfxBloomPipeline dibuja este quad de pantalla completa con blending
// ADITIVO (ONE, ONE) — sumar sobre lo que ya hay en pantalla es trabajo del blend state de GL.
//
// Sampler0 llega en coma flotante (RGBA16F, ver KiVfxBloomPipeline.makeHdr) con la energía de
// bloom SIN RECORTAR. Aquí se acota con un codo suave aplicado al canal MÁXIMO y reescalando los
// tres canales por igual: por debajo de Knee pasa intacto (una fuente sola se ve igual que antes),
// por encima se aproxima asintóticamente a Limit. Al escalar los tres canales con el mismo factor
// el TONO se conserva — un recorte por canal (lo que hacía el RGBA8 de antes) satura primero el
// canal dominante y deja crecer los demás, que es justo lo que lava un turquesa a blanco.

uniform sampler2D Sampler0;
uniform float Intensity;
uniform float Knee;
uniform float Limit;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec3 c = texture(Sampler0, texCoord).rgb * Intensity;
    float peak = max(max(c.r, c.g), c.b);
    if (peak > Knee) {
        float room = Limit - Knee;
        float limited = Knee + room * (1.0 - exp(-(peak - Knee) / room));
        c *= limited / peak;
    }
    fragColor = vec4(c, 0.0);
}
