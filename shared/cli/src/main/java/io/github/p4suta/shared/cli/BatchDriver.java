package io.github.p4suta.shared.cli;

import io.github.p4suta.shared.observability.ExceptionMapper;
import io.github.p4suta.shared.observability.ExitCodes;
import java.io.PrintStream;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs a list of items continue-on-error: each item is processed independently, an item's failure
 * is classified through the shared {@link ExceptionMapper} and reported (one {@code Error[KIND]
 * <item>: <message>} line, paths already masked), and the loop carries on. The run as a whole then
 * returns the exit code carried by the FIRST failure's {@link
 * io.github.p4suta.shared.kernel.error.ErrorCategory} — a real sysexits code, since the flat {@code
 * GENERIC} (1) bucket is retired across these CLIs — or {@link ExitCodes#OK} when every item
 * succeeded.
 *
 * <p>Generalizes tate-yoko-pdf's {@code SpreadCommand} batch loop. The item type {@code T} is the
 * app's unit of work (a {@link java.nio.file.Path}, a parsed job record, …); the caller supplies
 * how to label and how to process one item.
 *
 * @param <T> the per-item unit of work
 */
public final class BatchDriver<T> {

    /** Processes a single item; may throw, which the driver classifies and continues past. */
    @FunctionalInterface
    public interface ItemProcessor<T> {

        /**
         * Processes {@code item}.
         *
         * @param item the unit of work
         * @throws Exception if processing fails (classified by the driver, never rethrown)
         */
        void process(T item) throws Exception;
    }

    /** Renders an item to the label shown on its progress / failure line. */
    @FunctionalInterface
    public interface ItemLabeler<T> {

        /**
         * {@return the display label for {@code item} at one-based position {@code index} of {@code
         * total}}
         *
         * @param item the unit of work
         * @param index the one-based position of the item
         * @param total the number of items in the batch
         */
        String label(T item, int index, int total);
    }

    private final Logger log;
    private final PrintStream err;

    /** A driver logging to {@code BatchDriver}'s own logger and reporting to {@code System.err}. */
    public BatchDriver() {
        this(LoggerFactory.getLogger(BatchDriver.class), System.err);
    }

    BatchDriver(Logger log, PrintStream err) {
        this.log = log;
        this.err = err;
    }

    /**
     * Runs every item, continuing past failures, and returns the aggregate exit code.
     *
     * @param items the units of work, in order
     * @param labeler renders the per-item label
     * @param processor processes one item
     * @return {@link ExitCodes#OK} if all items succeeded, otherwise the first failure's exit code
     */
    public int run(List<T> items, ItemLabeler<T> labeler, ItemProcessor<T> processor) {
        Objects.requireNonNull(items, "items");
        int total = items.size();
        int failures = 0;
        int firstFailureExit = ExitCodes.OK;
        for (int i = 0; i < total; i++) {
            T item = items.get(i);
            String label = labeler.label(item, i + 1, total);
            try {
                processor.process(item);
            } catch (Exception e) {
                failures++;
                ExceptionMapper.Mapping mapping = ExceptionMapper.map(e);
                if (failures == 1) {
                    firstFailureExit = mapping.exitCode();
                }
                err.println(
                        "Error["
                                + mapping.kind().name()
                                + "] "
                                + label
                                + ": "
                                + mapping.safeUserMessage());
                log.atLevel(mapping.level()).log("{} failed: {}", label, mapping.safeUserMessage());
            }
        }
        if (failures > 0) {
            err.printf("%d of %d items failed.%n", failures, total);
            return firstFailureExit;
        }
        return ExitCodes.OK;
    }
}
