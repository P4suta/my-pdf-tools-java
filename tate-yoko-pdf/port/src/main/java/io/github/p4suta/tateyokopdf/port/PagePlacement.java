package io.github.p4suta.tateyokopdf.port;

import io.github.p4suta.tateyokopdf.domain.model.LayoutPosition;

/**
 * One page's content paired with where it sits in the spread: {@code content} drawn with its
 * lower-left corner translated to {@code position}.
 *
 * @param content the source page handle to draw (non-null)
 * @param position the offset within the spread frame, in points (non-null)
 */
public record PagePlacement(PageContent content, LayoutPosition position) {
    public PagePlacement {
        if (content == null || position == null) {
            throw new IllegalArgumentException("content and position must not be null");
        }
    }
}
