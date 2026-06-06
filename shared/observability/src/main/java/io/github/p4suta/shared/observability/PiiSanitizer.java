package io.github.p4suta.shared.observability;

import java.nio.file.Path;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/** Strips personally-identifying file-system paths out of text bound for logs. */
public final class PiiSanitizer {

    private static final Pattern ABSOLUTE_PATH =
            Pattern.compile("(?:[A-Za-z]:)?[/\\\\][\\w\\-./\\\\]{3,}");

    private PiiSanitizer() {}

    /** {@return just the file name of {@code path}, dropping any directory component} */
    public static String basenameOnly(Path path) {
        Path name = path.getFileName();
        return name == null ? path.toString() : name.toString();
    }

    /**
     * {@return {@code message} with absolute paths replaced by {@code <path>}, or {@code ""} if it
     * is null or empty}
     */
    public static String maskAbsolutePaths(@Nullable String message) {
        if (message == null || message.isEmpty()) {
            return "";
        }
        return ABSOLUTE_PATH.matcher(message).replaceAll("<path>");
    }
}
