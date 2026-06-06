package io.github.p4suta.shared.cli;

import org.jspecify.annotations.Nullable;

/**
 * Renders the {@code --version} line for a CLI tool from its jar manifest.
 *
 * <p>The version is read from the {@code Implementation-Version} of {@code anchorClass}'s package,
 * which the build stamps into each app jar's manifest. When the class is loaded from an exploded
 * build (no manifest), the version is absent and {@code (dev)} is shown instead.
 */
public final class CliVersion {

    private CliVersion() {}

    /**
     * {@return the version line {@code "<tool> <version>"}, or {@code "<tool> (dev)"} when no
     * manifest version is present}
     *
     * @param tool the program name (e.g. {@code "register"})
     * @param anchorClass a class in the app's package whose manifest carries the version
     */
    public static String line(String tool, Class<?> anchorClass) {
        return tool + " " + value(anchorClass);
    }

    /**
     * {@return the bare version string from {@code anchorClass}'s manifest, or {@code "(dev)"} when
     * absent}
     *
     * @param anchorClass a class in the app's package whose manifest carries the version
     */
    public static String value(Class<?> anchorClass) {
        @Nullable String version = anchorClass.getPackage().getImplementationVersion();
        return version == null ? "(dev)" : version;
    }
}
