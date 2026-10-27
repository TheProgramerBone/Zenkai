#version 150

// CAMPO del contorno de silueta (SilhouetteFlameRenderer): fracción de la máscara de ESTA ranura en
// una ventana de 7x7 muestras que cubre el grosor del contorno. Fuera del cuerpo vale 0 lejos y sube
// al acercarse al borde; dentro, 1 salvo cerca del borde. Media resolución: el filtrado lineal al
// leerlo después ya lo suaviza.
// G = suma de las distancias (máscara G) de las muestras cubiertas / 49: G/R es la distancia media del
// cuerpo cercano, y sigue siéndolo tras el filtrado lineal (cociente de dos medias con el mismo peso).

uniform sampler2D Sampler0; // máscara: R = ranura, A = cubierto
uniform vec2 MaskTexel;
uniform float Footprint;     // píxeles del principal
uniform float Id;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    float stride = Footprint / 3.0;
    float sum = 0.0;
    float dist = 0.0;
    for (int y = -3; y <= 3; y++) {
        for (int x = -3; x <= 3; x++) {
            vec4 m = texture(Sampler0, texCoord + vec2(float(x), float(y)) * stride * MaskTexel);
            if (m.a > 0.5 && abs(m.r - Id) < 0.03) {
                sum += 1.0;
                dist += m.g;
            }
        }
    }
    fragColor = vec4(sum / 49.0, dist / 49.0, 0.0, 1.0);
}
