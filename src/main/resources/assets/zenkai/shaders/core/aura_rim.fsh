#version 150

// Fragment mínimo: el aro ya lo produce el cull FRONT + depth-test de
// ModAuraRenderType.energyRimSpiked (ver su javadoc) — aquí solo hace falta igualar lo que el
// shader vainilla emisivo (RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER) hacía antes de que
// este shader lo sustituyera: textura de piel del jugador tintada por vértice.

in vec4 vColor;
in vec3 vNormal;
in vec3 vViewDir;
in vec2 vUv;

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;

out vec4 fragColor;

void main() {
    vec4 tex = texture(Sampler0, vUv);
    fragColor = tex * vColor * ColorModulator;
    if (fragColor.a < 0.004) discard;
}
