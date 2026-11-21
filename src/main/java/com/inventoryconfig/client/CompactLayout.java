package com.inventoryconfig.client;

import java.util.ArrayList;
import java.util.List;

/**
 * Como queda una pantalla cuando se le quitan tramos horizontales (los renglones de slots apagados).
 * Todas las coordenadas son las ORIGINALES de la pantalla, relativas a su esquina superior izquierda.
 */
public final class CompactLayout {

    /** Tramo que se quita: desde la fila {@code top} (inclusive) hasta {@code bottom} (exclusive). */
    public record Band(int top, int bottom) {
        public int height() {
            return bottom - top;
        }
    }

    /** Pedazo de la ventana original que se conserva, y cuanto sube respecto de su lugar original. */
    public record Segment(int start, int end, int shift) {
    }

    public static final CompactLayout NONE = new CompactLayout(0, List.of());

    private final int fullHeight;
    private final List<Band> bands;
    private final int totalHeight;

    public CompactLayout(int fullHeight, List<Band> bands) {
        this.fullHeight = fullHeight;
        this.bands = List.copyOf(bands);
        int total = 0;
        for (Band b : bands) total += b.height();
        this.totalHeight = total;
    }

    public boolean isEmpty() {
        return bands.isEmpty();
    }

    /** Alto de la ventana original. */
    public int fullHeight() {
        return fullHeight;
    }

    /** Pixeles que se quitan en total. */
    public int totalHeight() {
        return totalHeight;
    }

    /** Alto de la ventana ya achicada. */
    public int collapsedHeight() {
        return fullHeight - totalHeight;
    }

    /** Donde empieza el primer tramo quitado (lo de arriba de esa fila no se mueve). */
    public int firstTop() {
        return bands.get(0).top();
    }

    /** Cuanto sube lo que esta en la fila original {@code y}. */
    public int shiftAt(int y) {
        int shift = 0;
        for (Band b : bands) {
            if (b.bottom() <= y) shift += b.height();
        }
        return shift;
    }

    /** Si la fila original {@code y} cae dentro de un tramo quitado. */
    public boolean inBand(int y) {
        for (Band b : bands) {
            if (y >= b.top() && y < b.bottom()) return true;
        }
        return false;
    }

    /** Los pedazos que se conservan, de arriba hacia abajo. */
    public List<Segment> segments() {
        List<Segment> out = new ArrayList<>();
        int start = 0;
        int shift = 0;
        for (Band b : bands) {
            if (b.top() > start) out.add(new Segment(start, b.top(), shift));
            shift += b.height();
            start = b.bottom();
        }
        if (fullHeight > start) out.add(new Segment(start, fullHeight, shift));
        return out;
    }
}
