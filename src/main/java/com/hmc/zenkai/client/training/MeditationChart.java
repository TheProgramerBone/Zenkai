package com.hmc.zenkai.client.training;

import java.util.List;

/**
 * Chart de un disco vanilla para Meditation modo Canción — leído de
 * assets/zenkai/meditation_charts/&lt;discId&gt;.json (ver MeditationChartLoader), generado
 * (borrador) por tools/gen_meditation_chart.py y afinado a mano jugándolo.
 *
 * `notes` va en orden de tiempo — MeditationScreen los consume secuencialmente con un cursor,
 * igual de barato que el generador aleatorio de Práctica libre.
 */
public record MeditationChart(String discId, int durationMs, int lanes,
                               String difficultyHint, List<MeditationNote> notes) {

    public record MeditationNote(int timeMs, int lane) {}
}
