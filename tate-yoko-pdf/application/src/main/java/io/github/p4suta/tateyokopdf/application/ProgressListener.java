package io.github.p4suta.tateyokopdf.application;

/**
 * Receives progress callbacks during a single conversion.
 *
 * <p>The application calls {@link #onStart} once, then {@link #onSpreadComplete} once per finished
 * spread, then {@link #onComplete} once. All callbacks run on the conversion thread, in that order.
 */
public interface ProgressListener {

    /**
     * Signals the start of a conversion, before any spread is written.
     *
     * @param totalSpreads the number of spreads that will be produced
     */
    void onStart(int totalSpreads);

    /**
     * Signals that one more spread has been written.
     *
     * @param currentSpread the 1-based index of the spread just finished
     * @param totalSpreads the total announced at {@link #onStart}
     */
    void onSpreadComplete(int currentSpread, int totalSpreads);

    /**
     * Signals successful completion, after the output has been saved and post-processed.
     *
     * @param elapsedMillis wall-clock duration of the conversion, in milliseconds
     */
    void onComplete(long elapsedMillis);
}
