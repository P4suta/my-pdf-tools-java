package io.github.p4suta.tateyokopdf.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.p4suta.tateyokopdf.domain.exception.ErrorKind;
import io.github.p4suta.tateyokopdf.domain.exception.SpreadException;
import io.github.p4suta.tateyokopdf.domain.model.FirstPageMode;
import io.github.p4suta.tateyokopdf.domain.model.PageDimension;
import io.github.p4suta.tateyokopdf.domain.model.ReadingDirection;
import io.github.p4suta.tateyokopdf.domain.service.SpreadLayoutCalculator;
import io.github.p4suta.tateyokopdf.port.DocumentFactory;
import io.github.p4suta.tateyokopdf.port.PageContent;
import io.github.p4suta.tateyokopdf.port.PdfPostProcessor;
import io.github.p4suta.tateyokopdf.port.SourceDocument;
import io.github.p4suta.tateyokopdf.port.SpreadDocument;
import io.github.p4suta.tateyokopdf.testfixtures.CapturingProgressListener;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
final class SpreadServiceTest {

    @Mock DocumentFactory factory;
    @Mock SourceDocument source;
    @Mock SpreadDocument output;
    @Mock PageContent content0;
    @Mock PageContent content1;
    @Mock PageContent content2;
    @Mock PageContent content3;
    @Mock PdfPostProcessor postProcessor;

    private final SpreadLayoutCalculator calc = new SpreadLayoutCalculator();

    @Test
    void rejectsMissingSourceFile(@TempDir Path tmp) {
        var listener = new CapturingProgressListener();
        var service = new SpreadService(factory, calc, PdfPostProcessor.noOp(), listener);
        var opt =
                new SpreadOptions(
                        tmp.resolve("missing.pdf"),
                        tmp.resolve("out.pdf"),
                        ReadingDirection.RTL,
                        FirstPageMode.STANDARD,
                        false);
        assertThatThrownBy(() -> service.execute(opt))
                .isInstanceOfSatisfying(
                        SpreadException.class,
                        ex -> assertThat(ex.kind()).isEqualTo(ErrorKind.PDF_NOT_FOUND));
    }

    @Test
    void callsListenerInOrder(@TempDir Path tmp) throws Exception {
        Path inputFile = Files.createFile(tmp.resolve("in.pdf"));
        when(factory.openSource(inputFile)).thenReturn(source);
        when(factory.createOutput()).thenReturn(output);
        when(source.pageCount()).thenReturn(4);
        when(source.pageDimension(anyInt())).thenReturn(new PageDimension(100f, 200f));
        when(source.pageContent(0)).thenReturn(content0);
        when(source.pageContent(1)).thenReturn(content1);
        when(source.pageContent(2)).thenReturn(content2);
        when(source.pageContent(3)).thenReturn(content3);

        var listener = new CapturingProgressListener();
        var service = new SpreadService(factory, calc, PdfPostProcessor.noOp(), listener);
        service.execute(
                new SpreadOptions(
                        inputFile,
                        tmp.resolve("out.pdf"),
                        ReadingDirection.RTL,
                        FirstPageMode.STANDARD,
                        false));

        var events = listener.events();
        assertThat(events).hasSize(4); // Start + 2 SpreadComplete + Complete
        assertThat(events.get(0))
                .isInstanceOf(CapturingProgressListener.Event.Start.class)
                .isEqualTo(new CapturingProgressListener.Event.Start(2));
        assertThat(events.get(1))
                .isEqualTo(new CapturingProgressListener.Event.SpreadComplete(1, 2));
        assertThat(events.get(2))
                .isEqualTo(new CapturingProgressListener.Event.SpreadComplete(2, 2));
        assertThat(events.get(3)).isInstanceOf(CapturingProgressListener.Event.Complete.class);
    }

    @Test
    void emitsBothPairAndTrailingSingleForOddPageCount(@TempDir Path tmp) throws Exception {
        Path inputFile = Files.createFile(tmp.resolve("in.pdf"));
        when(factory.openSource(inputFile)).thenReturn(source);
        when(factory.createOutput()).thenReturn(output);
        // 3 pages, STANDARD -> [Pair(0,1), Single(2)]: exercises both processSpread arms.
        when(source.pageCount()).thenReturn(3);
        when(source.pageDimension(anyInt())).thenReturn(new PageDimension(100f, 200f));
        when(source.pageContent(0)).thenReturn(content0);
        when(source.pageContent(1)).thenReturn(content1);
        when(source.pageContent(2)).thenReturn(content2);

        var listener = new CapturingProgressListener();
        var service = new SpreadService(factory, calc, PdfPostProcessor.noOp(), listener);
        service.execute(
                new SpreadOptions(
                        inputFile,
                        tmp.resolve("out.pdf"),
                        ReadingDirection.RTL,
                        FirstPageMode.STANDARD,
                        false));

        // One spread for the pair, one for the trailing single.
        verify(output, times(2)).addSpread(any(), any());
        var events = listener.events();
        assertThat(events.get(0)).isEqualTo(new CapturingProgressListener.Event.Start(2));
        assertThat(events.get(2))
                .isEqualTo(new CapturingProgressListener.Event.SpreadComplete(2, 2));
    }

    @Test
    void closesSourceAndOutputEvenWhenAddSpreadThrows(@TempDir Path tmp) throws Exception {
        Path inputFile = Files.createFile(tmp.resolve("in.pdf"));
        when(factory.openSource(inputFile)).thenReturn(source);
        when(factory.createOutput()).thenReturn(output);
        when(source.pageCount()).thenReturn(2);
        when(source.pageDimension(anyInt())).thenReturn(new PageDimension(100f, 200f));
        when(source.pageContent(0)).thenReturn(content0);
        when(source.pageContent(1)).thenReturn(content1);
        doThrow(SpreadException.of(ErrorKind.PDF_WRITE_FAILED))
                .when(output)
                .addSpread(any(), any());

        var listener = new CapturingProgressListener();
        var service = new SpreadService(factory, calc, PdfPostProcessor.noOp(), listener);

        assertThatThrownBy(
                        () ->
                                service.execute(
                                        new SpreadOptions(
                                                inputFile,
                                                tmp.resolve("out.pdf"),
                                                ReadingDirection.RTL,
                                                FirstPageMode.STANDARD,
                                                false)))
                .isInstanceOf(SpreadException.class);

        verify(source).close();
        verify(output).close();
    }

    @Test
    void savesOutputAfterAllSpreads(@TempDir Path tmp) throws Exception {
        Path inputFile = Files.createFile(tmp.resolve("in.pdf"));
        Path outputFile = tmp.resolve("out.pdf");
        when(factory.openSource(inputFile)).thenReturn(source);
        when(factory.createOutput()).thenReturn(output);
        when(source.pageCount()).thenReturn(2);
        when(source.pageDimension(anyInt())).thenReturn(new PageDimension(100f, 200f));
        when(source.pageContent(0)).thenReturn(content0);
        when(source.pageContent(1)).thenReturn(content1);

        var listener = new CapturingProgressListener();
        var service = new SpreadService(factory, calc, PdfPostProcessor.noOp(), listener);
        service.execute(
                new SpreadOptions(
                        inputFile,
                        outputFile,
                        ReadingDirection.RTL,
                        FirstPageMode.STANDARD,
                        false));

        InOrder ord = inOrder(output);
        ord.verify(output).addSpread(any(), any());
        ord.verify(output).save(eq(outputFile));
    }

    @Test
    void postProcessorRunsAfterSaveAndBeforeComplete(@TempDir Path tmp) throws Exception {
        Path inputFile = Files.createFile(tmp.resolve("in.pdf"));
        Path outputFile = tmp.resolve("out.pdf");
        when(factory.openSource(inputFile)).thenReturn(source);
        when(factory.createOutput()).thenReturn(output);
        when(source.pageCount()).thenReturn(2);
        when(source.pageDimension(anyInt())).thenReturn(new PageDimension(100f, 200f));
        when(source.pageContent(0)).thenReturn(content0);
        when(source.pageContent(1)).thenReturn(content1);

        var listener = new CapturingProgressListener();
        var service = new SpreadService(factory, calc, postProcessor, listener);
        service.execute(
                new SpreadOptions(
                        inputFile,
                        outputFile,
                        ReadingDirection.RTL,
                        FirstPageMode.STANDARD,
                        false));

        // Order matters: the SpreadDocument must close (file handle release) and
        // save() must finish before qpdf opens the file; onComplete is the terminal
        // signal so the listener should observe it only after post-processing succeeds.
        InOrder ord = inOrder(output, postProcessor);
        ord.verify(output).save(eq(outputFile));
        ord.verify(output).close();
        ord.verify(postProcessor).process(eq(outputFile));
        assertThat(listener.events())
                .last()
                .isInstanceOf(CapturingProgressListener.Event.Complete.class);
    }

    @Test
    void postProcessorIsSkippedWhenSpreadFails(@TempDir Path tmp) throws Exception {
        Path inputFile = Files.createFile(tmp.resolve("in.pdf"));
        when(factory.openSource(inputFile)).thenReturn(source);
        when(factory.createOutput()).thenReturn(output);
        when(source.pageCount()).thenReturn(2);
        when(source.pageDimension(anyInt())).thenReturn(new PageDimension(100f, 200f));
        when(source.pageContent(0)).thenReturn(content0);
        when(source.pageContent(1)).thenReturn(content1);
        doThrow(SpreadException.of(ErrorKind.PDF_WRITE_FAILED))
                .when(output)
                .addSpread(any(), any());

        var listener = new CapturingProgressListener();
        var service = new SpreadService(factory, calc, postProcessor, listener);

        assertThatThrownBy(
                        () ->
                                service.execute(
                                        new SpreadOptions(
                                                inputFile,
                                                tmp.resolve("out.pdf"),
                                                ReadingDirection.RTL,
                                                FirstPageMode.STANDARD,
                                                false)))
                .isInstanceOf(SpreadException.class);

        verify(postProcessor, never()).process(any());
    }

    @Test
    void finalizesPdfAAfterMetadataAndBeforeSaveWhenRequested(@TempDir Path tmp) throws Exception {
        Path inputFile = Files.createFile(tmp.resolve("in.pdf"));
        Path outputFile = tmp.resolve("out.pdf");
        when(factory.openSource(inputFile)).thenReturn(source);
        when(factory.createOutput()).thenReturn(output);
        when(source.pageCount()).thenReturn(2);
        when(source.pageDimension(anyInt())).thenReturn(new PageDimension(100f, 200f));
        when(source.pageContent(0)).thenReturn(content0);
        when(source.pageContent(1)).thenReturn(content1);

        var service =
                new SpreadService(
                        factory, calc, PdfPostProcessor.noOp(), new CapturingProgressListener());
        service.execute(
                new SpreadOptions(
                        inputFile, outputFile, ReadingDirection.RTL, FirstPageMode.STANDARD, true));

        // The XMP packet mirrors the info dictionary, so finalizePdfA must run after
        // applyMetadata; and it mutates the document, so it must run before save.
        InOrder ord = inOrder(output);
        ord.verify(output).applyMetadata(any(), any(), any());
        ord.verify(output).finalizePdfA();
        ord.verify(output).save(eq(outputFile));
    }

    @Test
    void skipsPdfAFinalizationByDefault(@TempDir Path tmp) throws Exception {
        Path inputFile = Files.createFile(tmp.resolve("in.pdf"));
        when(factory.openSource(inputFile)).thenReturn(source);
        when(factory.createOutput()).thenReturn(output);
        when(source.pageCount()).thenReturn(2);
        when(source.pageDimension(anyInt())).thenReturn(new PageDimension(100f, 200f));
        when(source.pageContent(0)).thenReturn(content0);
        when(source.pageContent(1)).thenReturn(content1);

        var service =
                new SpreadService(
                        factory, calc, PdfPostProcessor.noOp(), new CapturingProgressListener());
        service.execute(
                new SpreadOptions(
                        inputFile,
                        tmp.resolve("out.pdf"),
                        ReadingDirection.RTL,
                        FirstPageMode.STANDARD,
                        false));

        verify(output, never()).finalizePdfA();
    }
}
