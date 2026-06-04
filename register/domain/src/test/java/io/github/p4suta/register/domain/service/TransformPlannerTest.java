package io.github.p4suta.register.domain.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.p4suta.register.domain.model.Anchor;
import io.github.p4suta.register.domain.model.Box;
import io.github.p4suta.register.domain.model.Transform;
import org.junit.jupiter.api.Test;

class TransformPlannerTest {

    private final TransformPlanner planner = new TransformPlanner();

    @Test
    void anchorsTheColumnTopRightWithoutScaling() {
        // A full-width column (its width matches the reference) keeps the right edge: the reading
        // origin is pinned to the reference.
        Box column = new Box(100, 50, 200, 1000);
        Box reference = new Box(120, 40, 200, 1010);
        Transform t =
                planner.plan(
                        column, reference, 2000, 3000, 2000, 3000, false, 0.5, Anchor.TOP_RIGHT);

        assertFalse(t.passthrough());
        assertEquals(1.0, t.scale());
        // dx = reference.right() - column.right() = 320 - 300
        assertEquals(20, t.dx());
        // dy = reference.y() - column.y() = 40 - 50
        assertEquals(-10, t.dy());
    }

    @Test
    void fallsBackToTheLeftEdgeWhenTheRightIsTruncated() {
        // A chapter opener: its left edge sits by the reference, but its rightmost columns are
        // blank
        // so its right edge falls far short. Pinning the right edge would shove the page ~145 px
        // sideways; the planner anchors the grid-flush left edge instead.
        Box column = new Box(105, 45, 250, 1000); // right = 355
        Box reference = new Box(100, 40, 400, 1000); // right = 500, tol = 400/64 = 6
        Transform t =
                planner.plan(
                        column, reference, 2000, 3000, 2000, 3000, false, 0.5, Anchor.TOP_RIGHT);

        assertFalse(t.passthrough());
        // |alignLeft|=5 + tol 6 < |alignRight|=145, so the left edge wins: dx = 100 - 105.
        assertEquals(-5, t.dx());
        // dy = reference.y() - column.y() = 40 - 45
        assertEquals(-5, t.dy());
    }

    @Test
    void fallsBackToTheBottomEdgeWhenTheHeadIsDropped() {
        // A section opener: its body is dropped well below the head margin (a title sits above) but
        // still runs down to the foot margin. The top edge is far from the reference, the bottom
        // edge sits on it, so the planner anchors the foot — not the head — keeping the body on the
        // grid instead of yanking it up by the drop.
        Box column = new Box(100, 450, 1000, 640); // top = 450, bottom = 1090
        Box reference =
                new Box(100, 100, 1000, 1000); // top = 100, bottom = 1100, tol = 1000/64 = 15
        Transform t =
                planner.plan(
                        column, reference, 2000, 3000, 2000, 3000, false, 0.5, Anchor.TOP_RIGHT);

        assertFalse(t.passthrough());
        // |alignBottom|=10 + tol 15 < |alignTop|=350, so the bottom wins: dy = 1100 - 1090.
        assertEquals(10, t.dy());
        // Full width, so the right (reading) edge is kept: dx = 1100 - 1100.
        assertEquals(0, t.dx());
    }

    @Test
    void scalesTheReferenceIntoThePageScaledSpaceForTopRight() {
        // A page identical to the reference, shrunk by half to fit the canvas: once the reference
        // is
        // brought into the same scaled space, both top-right edges already coincide, so no
        // translation is needed. (Were the reference compared in unscaled space, dx/dy would jump
        // by
        // the scaled-away half of the page.)
        Box column = new Box(200, 100, 400, 1000);
        Box reference = new Box(200, 100, 400, 1000);
        Transform t =
                planner.plan(
                        column, reference, 4000, 6000, 2000, 3000, true, 0.5, Anchor.TOP_RIGHT);

        assertFalse(t.passthrough());
        assertEquals(0.5, t.scale());
        assertEquals(0, t.dx());
        assertEquals(0, t.dy());
    }

    @Test
    void shrinksAnOversizedPageToFit() {
        // Page is twice the canvas in both axes, so shrink-to-fit halves it; a page is never
        // enlarged, so the placed result can never exceed the canvas.
        Box column = new Box(200, 100, 400, 1000);
        Box reference = new Box(0, 0, 400, 1000); // col area == ref area -> not an outlier
        Transform t =
                planner.plan(column, reference, 4000, 6000, 2000, 3000, true, 0.5, Anchor.CENTER);

        assertFalse(t.passthrough());
        assertEquals(0.5, t.scale());
        // scaled column center = (100 + 200/2, 50 + 500/2) = (200, 300); canvas center (1000, 1500)
        assertEquals(800, t.dx());
        assertEquals(1200, t.dy());
    }

    @Test
    void centersAnOutlierColumn() {
        Box column = new Box(0, 0, 10, 10);
        Box reference = new Box(0, 0, 1000, 1000);
        Transform t =
                planner.plan(column, reference, 100, 100, 200, 200, true, 0.5, Anchor.TOP_RIGHT);

        assertTrue(t.passthrough());
        assertEquals(50, t.dx());
        assertEquals(50, t.dy());
    }

    @Test
    void keepsThePreferredEdgeRightAtTheToleranceBoundary() {
        // tol = ref.w()/64 = 640/64 = 10. The right (preferred) edge is off by 20, the left edge by
        // 10. The flip needs |left| + tol < |right|, i.e. 10 + 10 < 20, which is false at the
        // boundary — so the reading-origin right edge is kept. dx = ref.right() - col.right().
        Box column = new Box(90, 50, 630, 1000); // right = 720, bottom = 1050
        Box reference = new Box(100, 40, 640, 1010); // right = 740, bottom = 1050
        Transform t =
                planner.plan(
                        column, reference, 2000, 3000, 2000, 3000, false, 0.5, Anchor.TOP_RIGHT);

        assertFalse(t.passthrough());
        assertEquals(20, t.dx());
        // y: tol = 1010/64 = 16. Bottom aligns exactly (0) but the top is off by only 10, within
        // tolerance, so the head edge is kept: dy = ref.y() - col.y() = 40 - 50.
        assertEquals(-10, t.dy());
    }

    @Test
    void flipsToTheOtherEdgeJustBeyondTheTolerance() {
        // Same setup but the right edge is off by 21: now |left|(10) + tol(10) = 20 < 21, so the
        // left edge wins. dx = ref.x() - col.x() = 100 - 90.
        Box column = new Box(90, 50, 629, 1000); // right = 719
        Box reference = new Box(100, 40, 640, 1010); // right = 740, tol = 10
        Transform t =
                planner.plan(
                        column, reference, 2000, 3000, 2000, 3000, false, 0.5, Anchor.TOP_RIGHT);

        assertFalse(t.passthrough());
        assertEquals(10, t.dx());
    }

    @Test
    void centersTheColumnOnTheCanvas() {
        // Column center is at (275, 300); the canvas center is (1000, 1500).
        Box column = new Box(100, 50, 350, 500);
        Box reference = new Box(100, 40, 300, 1000);
        Transform t =
                planner.plan(column, reference, 2000, 3000, 2000, 3000, false, 0.5, Anchor.CENTER);

        assertFalse(t.passthrough());
        assertEquals(1.0, t.scale());
        assertEquals(725, t.dx());
        assertEquals(1200, t.dy());
    }
}
