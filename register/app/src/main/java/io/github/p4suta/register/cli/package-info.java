/**
 * The command-line front end (Apache Commons CLI) and the one layer allowed to write to {@code
 * System.out}/{@code System.err}. {@link io.github.p4suta.register.cli.RegisterCommand} is the
 * entry point: it runs the image-directory registration, or dispatches {@code register pipeline} to
 * the PDF -> PDF driver ({@code PipelineCommand}). Parsing common to both commands lives in {@code
 * CliSupport}; everything below the shell logs through SLF4J.
 */
@NullMarked
package io.github.p4suta.register.cli;

import org.jspecify.annotations.NullMarked;
