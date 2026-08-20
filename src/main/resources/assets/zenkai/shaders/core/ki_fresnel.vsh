#version 150

// Vértice del pipeline de ki. No hace nada de arte: solo prepara los tres datos que el
// fragment necesita para decidir la banda — normal, dirección de vista y un dominio estable
// para el hervor.
//
// Los vértices llegan YA transformados por el PoseStack (Minecraft hornea la matriz de
// modelo en CPU), así que ModelViewMat es prácticamente la identidad durante el render del
// mundo y `Position` es espacio de vista. El cálculo funciona igual en ambos casos porque
// se aplica ModelViewMat de todos modos.

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec4 vColor;
out vec3 vNormal;
out vec3 vViewDir;
out vec2 vUv;
out vec3 vField;

void main() {
    vec4 viewPos = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * viewPos;

    vNormal  = normalize(mat3(ModelViewMat) * Normal);
    vViewDir = normalize(-viewPos.xyz);
    vColor   = Color;
    vUv      = UV0;

    // DOMINIO DEL HERVOR: normal de malla + UV, no la posición.
    // Con la posición, el patrón se queda anclado al mundo y el proyectil lo atraviesa: se ve
    // como volar a través de una nube fija, no como energía inestable. Con la normal, el
    // patrón viaja pegado a la superficie. La UV entra porque el disco tiene la normal
    // constante y sin ella no herviría en absoluto.
    vField = Normal + vec3(UV0 * 2.0 - 1.0, 0.0);
}
