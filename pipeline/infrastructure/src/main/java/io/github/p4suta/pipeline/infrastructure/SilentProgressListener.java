package io.github.p4suta.pipeline.infrastructure;

import io.github.p4suta.tateyokopdf.application.ProgressListener;

/**
 * A no-op {@link ProgressListener} for the spread sink: the pipeline reports stage-level progress
 * through {@code PipelineRunner}'s logging, so the spread step stays quiet.
 */
final class SilentProgressListener implements ProgressListener {

    @Override
    public void onStart(int totalSpreads) {
        // no-op
    }

    @Override
    public void onSpreadComplete(int currentSpread, int totalSpreads) {
        // no-op
    }

    @Override
    public void onComplete(long elapsedMillis) {
        // no-op
    }
}
