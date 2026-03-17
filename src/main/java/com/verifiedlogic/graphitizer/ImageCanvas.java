/*
 * Graphitizer
 * Copyright (C) 2026 Willem Langeveld
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.verifiedlogic.graphitizer;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

public class ImageCanvas extends JPanel {

    private BufferedImage image;

    public enum State {
        IDLE, ZOOMED_IN, EDIT_ZOOMED_IN,
        PICK_X1, ZOOMED_X1,
        PICK_X2, ZOOMED_X2,
        PICK_Y1, ZOOMED_Y1,
        PICK_Y2, ZOOMED_Y2,
        PICK_TL, ZOOMED_TL,
        PICK_TR, ZOOMED_TR,
        PICK_BR, ZOOMED_BR,
        PICK_BL, ZOOMED_BL,
        DRAG_ROI, DRAG_ROI_HANDLE, DRAG_ROI_MOVE
    }

    public enum Handle {
        TL, T, TR, R, BR, B, BL, L, NONE
    }

    private Handle draggedHandle = Handle.NONE;
    private static final int HANDLE_SIZE = 8;
    private java.awt.geom.Point2D.Double dragOffsetTL = null;
    private java.awt.geom.Point2D.Double dragOffsetBR = null;

    private State state = State.IDLE;

    public interface PointSelectedListener {
        void onPointSelected(State state, double x, double y);

        void onPointAdded(double x, double y);

        void onPointEdited(int index, double x, double y);

        void onPointDeleted(int index);

        boolean isReadyToPlot();

        void onRoiUpdated();
    }

    private PointSelectedListener listener;

    /**
     * Optional callback invoked immediately BEFORE any point is added, deleted,
     * or moved. Used by GraphitizerApp to push an undo snapshot onto the history
     * stack without requiring a hard dependency on the app class.
     */
    private Runnable undoCallback;

    /** Registers the undo snapshot callback (set once after construction). */
    public void setUndoCallback(Runnable callback) {
        this.undoCallback = callback;
    }

    /** Fires the undo callback if one has been registered. */
    private void fireUndoSnapshot() {
        if (undoCallback != null) undoCallback.run();
    }

    private List<Dataset> datasets = new ArrayList<>();
    private Dataset activeDataset = null;
    private int editingIndex = -1;

    // View Transformation
    private double viewScale = 1.0;

    // Keystone Boundary overlay
    private java.awt.geom.Point2D.Double keyTL, keyTR, keyBR, keyBL;

    // ROI Boundary overlay
    private java.awt.geom.Point2D.Double roiTL, roiBR;

    public void setKeystonePoints(java.awt.geom.Point2D.Double tl, java.awt.geom.Point2D.Double tr,
            java.awt.geom.Point2D.Double br, java.awt.geom.Point2D.Double bl) {
        this.keyTL = tl;
        this.keyTR = tr;
        this.keyBR = br;
        this.keyBL = bl;
        repaint();
    }

    public void setRoiPoints(java.awt.geom.Point2D.Double tl, java.awt.geom.Point2D.Double br) {
        this.roiTL = tl;
        this.roiBR = br;
        repaint();
    }

    public java.awt.geom.Point2D.Double getRoiTL() {
        return roiTL;
    }

    public java.awt.geom.Point2D.Double getRoiBR() {
        return roiBR;
    }

    public void setDatasets(List<Dataset> datasets, Dataset activeDataset) {
        this.datasets = datasets;
        this.activeDataset = activeDataset;
        repaint();
    }

    private Point lockedScreenCenter = new Point();
    private double originX = 0, originY = 0;
    private Point mousePos = new Point();

    public ImageCanvas(PointSelectedListener listener) {
        this.listener = listener;
        setBackground(Color.DARK_GRAY);
        setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        setFocusable(true);

        MouseAdapter ma = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                if (image == null)
                    return;

                if (state == State.DRAG_ROI) {
                    double[] coords = getOriginalImageCoordinates(e.getX(), e.getY());
                    roiTL = new java.awt.geom.Point2D.Double(coords[0], coords[1]);
                    roiBR = new java.awt.geom.Point2D.Double(coords[0], coords[1]);
                    repaint();
                    return;
                }

                if (state == State.IDLE) {
                    Handle h = getRoiHandleHit(e.getPoint());
                    if (h != Handle.NONE) {
                        state = State.DRAG_ROI_HANDLE;
                        draggedHandle = h;
                        return;
                    }
                }

                if (state == State.IDLE && isRoiStrokeHit(e.getPoint())) {
                    state = State.DRAG_ROI_MOVE;
                    double[] coords = getOriginalImageCoordinates(e.getX(), e.getY());
                    dragOffsetTL = new java.awt.geom.Point2D.Double(roiTL.x - coords[0], roiTL.y - coords[1]);
                    dragOffsetBR = new java.awt.geom.Point2D.Double(roiBR.x - coords[0], roiBR.y - coords[1]);
                    return;
                }

                if (isIdle()) {
                    if (e.getButton() == MouseEvent.BUTTON3 && state == State.IDLE) {
                        int hit = getHitIndex(e.getPoint());
                        if (hit >= 0 && activeDataset != null) {
                            // Snapshot before deleting so the user can undo
                            fireUndoSnapshot();
                            activeDataset.getPoints().remove(hit);
                            if (ImageCanvas.this.listener != null)
                                ImageCanvas.this.listener.onPointDeleted(hit);
                            repaint();
                        }
                        return;
                    }

                    if (e.getButton() == MouseEvent.BUTTON1 && state == State.IDLE) {
                        int hit = getHitIndex(e.getPoint());
                        if (hit >= 0 && activeDataset != null) {
                            editingIndex = hit;
                            state = State.EDIT_ZOOMED_IN;

                            java.awt.geom.Point2D.Double p = activeDataset.getPoints().get(hit);
                            double[] screenCoords = getScreenCoordinates(p.x, p.y);
                            lockedScreenCenter = new Point((int) Math.round(screenCoords[0]),
                                    (int) Math.round(screenCoords[1]));
                            mousePos = e.getPoint();
                            originX = p.x;
                            originY = p.y;
                            repaint();
                            return;
                        }
                    }

                    if (e.getButton() == MouseEvent.BUTTON1 && state != State.ZOOMED_IN && state != State.EDIT_ZOOMED_IN
                            && state != State.ZOOMED_X1 && state != State.ZOOMED_X2 && state != State.ZOOMED_Y1
                            && state != State.ZOOMED_Y2) {
                        lockedScreenCenter = e.getPoint();
                        mousePos = e.getPoint();
                        double[] imgCoords = getOriginalImageCoordinates(lockedScreenCenter.x, lockedScreenCenter.y);
                        originX = imgCoords[0];
                        originY = imgCoords[1];

                        switch (state) {
                            case IDLE:
                                if (ImageCanvas.this.listener != null && !ImageCanvas.this.listener.isReadyToPlot()) {
                                    return;
                                }
                                state = State.ZOOMED_IN;
                                break;
                            case PICK_X1:
                                state = State.ZOOMED_X1;
                                break;
                            case PICK_X2:
                                state = State.ZOOMED_X2;
                                break;
                            case PICK_Y1:
                                state = State.ZOOMED_Y1;
                                break;
                            case PICK_Y2:
                                state = State.ZOOMED_Y2;
                                break;
                            case PICK_TL:
                                state = State.ZOOMED_TL;
                                break;
                            case PICK_TR:
                                state = State.ZOOMED_TR;
                                break;
                            case PICK_BR:
                                state = State.ZOOMED_BR;
                                break;
                            case PICK_BL:
                                state = State.ZOOMED_BL;
                                break;
                            default:
                                break;
                        }
                        repaint();
                    }
                } else if (isZoomed() && e.getButton() == MouseEvent.BUTTON3) {
                    state = State.IDLE;
                    repaint();
                    return;
                } else if (isZoomed() && e.getButton() == MouseEvent.BUTTON1) {
                    double finalX = originX + (e.getPoint().x - lockedScreenCenter.x) / 10.0;
                    double finalY = originY + (e.getPoint().y - lockedScreenCenter.y) / 10.0;
                    State oldState = state;
                    state = State.IDLE;

                    if (oldState == State.ZOOMED_IN) {
                        if (activeDataset != null) {
                            // Snapshot before adding a new point so the user can undo
                            fireUndoSnapshot();
                            activeDataset.getPoints().add(new java.awt.geom.Point2D.Double(finalX, finalY));
                        }
                        if (ImageCanvas.this.listener != null)
                            ImageCanvas.this.listener.onPointAdded(finalX, finalY);
                    } else if (oldState == State.EDIT_ZOOMED_IN) {
                        if (activeDataset != null) {
                            // Snapshot before moving a point so the user can undo
                            fireUndoSnapshot();
                            activeDataset.getPoints().get(editingIndex).setLocation(finalX, finalY);
                        }
                        if (ImageCanvas.this.listener != null)
                            ImageCanvas.this.listener.onPointEdited(editingIndex, finalX, finalY);
                    } else {
                        if (ImageCanvas.this.listener != null)
                            ImageCanvas.this.listener.onPointSelected(oldState, finalX, finalY);
                    }
                    repaint();
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                mousePos = e.getPoint();
                if (state == State.IDLE && roiTL != null && roiBR != null) {
                    if (getRoiHandleHit(mousePos) != Handle.NONE) {
                        setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
                    } else if (isRoiStrokeHit(mousePos)) {
                        setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                    } else {
                        setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
                    }
                } else if (!isZoomed()) {
                    setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
                }

                if (isZoomed()) {
                    repaint();
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (state == State.DRAG_ROI_MOVE && roiTL != null && roiBR != null) {
                    double[] coords = getOriginalImageCoordinates(e.getX(), e.getY());
                    double newTLx = coords[0] + dragOffsetTL.x;
                    double newTLy = coords[1] + dragOffsetTL.y;
                    double newBRx = coords[0] + dragOffsetBR.x;
                    double newBRy = coords[1] + dragOffsetBR.y;

                    double w = Math.abs(newBRx - newTLx);
                    double h = Math.abs(newBRy - newTLy);

                    if (newTLx < 0) {
                        newTLx = 0;
                        newBRx = w;
                    }
                    if (newTLy < 0) {
                        newTLy = 0;
                        newBRy = h;
                    }
                    if (newBRx > image.getWidth()) {
                        newBRx = image.getWidth();
                        newTLx = image.getWidth() - w;
                    }
                    if (newBRy > image.getHeight()) {
                        newBRy = image.getHeight();
                        newTLy = image.getHeight() - h;
                    }

                    roiTL.setLocation(newTLx, newTLy);
                    roiBR.setLocation(newBRx, newBRy);
                    repaint();
                    return;
                }

                if (state == State.DRAG_ROI_HANDLE && roiTL != null && roiBR != null) {
                    double[] imgCoords = getOriginalImageCoordinates(e.getX(), e.getY());
                    double ix = imgCoords[0];
                    double iy = imgCoords[1];
                    switch (draggedHandle) {
                        case TL:
                            roiTL.x = ix;
                            roiTL.y = iy;
                            break;
                        case T:
                            roiTL.y = iy;
                            break;
                        case TR:
                            roiBR.x = ix;
                            roiTL.y = iy;
                            break;
                        case R:
                            roiBR.x = ix;
                            break;
                        case BR:
                            roiBR.x = ix;
                            roiBR.y = iy;
                            break;
                        case B:
                            roiBR.y = iy;
                            break;
                        case BL:
                            roiTL.x = ix;
                            roiBR.y = iy;
                            break;
                        case L:
                            roiTL.x = ix;
                            break;
                        default:
                            break;
                    }

                    // Clamp dragged handles to Image borders
                    roiTL.x = Math.max(0, Math.min(roiTL.x, image.getWidth()));
                    roiTL.y = Math.max(0, Math.min(roiTL.y, image.getHeight()));
                    roiBR.x = Math.max(0, Math.min(roiBR.x, image.getWidth()));
                    roiBR.y = Math.max(0, Math.min(roiBR.y, image.getHeight()));

                    repaint();
                    return;
                }

                if (state == State.DRAG_ROI && roiTL != null) {
                    double[] coords = getOriginalImageCoordinates(e.getX(), e.getY());
                    roiBR = new java.awt.geom.Point2D.Double(
                            Math.max(0, Math.min(coords[0], image.getWidth())),
                            Math.max(0, Math.min(coords[1], image.getHeight())));
                    repaint();
                    return;
                }

                if (isZoomed()) {
                    mousePos = e.getPoint();
                    repaint();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (state == State.DRAG_ROI || state == State.DRAG_ROI_HANDLE || state == State.DRAG_ROI_MOVE) {
                    if (roiTL != null && roiBR != null) {
                        double minX = Math.max(0, Math.min(roiTL.x, roiBR.x));
                        double minY = Math.max(0, Math.min(roiTL.y, roiBR.y));
                        double maxX = Math.min(image.getWidth(), Math.max(roiTL.x, roiBR.x));
                        double maxY = Math.min(image.getHeight(), Math.max(roiTL.y, roiBR.y));
                        roiTL.setLocation(minX, minY);
                        roiBR.setLocation(maxX, maxY);
                    }
                    state = State.IDLE;
                    draggedHandle = Handle.NONE;
                    dragOffsetTL = null;
                    dragOffsetBR = null;
                    repaint();
                    if (ImageCanvas.this.listener != null) {
                        ImageCanvas.this.listener.onRoiUpdated();
                    }
                }
            }
        };

        addMouseListener(ma);
        addMouseMotionListener(ma);

        addMouseWheelListener(e -> {
            if (e.isControlDown() && image != null) {
                double zoomFactor = 1.1;
                double oldScale = viewScale;

                if (e.getWheelRotation() < 0) {
                    viewScale *= zoomFactor;
                } else {
                    viewScale /= zoomFactor;
                }

                // Get pre-zoom parameters
                Point mousePoint = e.getPoint();

                // Keep references to JScrollPane and Viewport
                final JViewport viewport;
                if (getParent() instanceof JViewport) {
                    viewport = (JViewport) getParent();
                } else {
                    viewport = null;
                }

                // Actually perform the native resize
                recalculatePreferredSize();
                revalidate();

                // Adjust scrollbars to maintain the mouse point's position
                if (viewport != null) {
                    double scaleRatio = viewScale / oldScale;
                    Point viewPos = viewport.getViewPosition();

                    int newViewX = (int) (mousePoint.x * scaleRatio - (mousePoint.x - viewPos.x));
                    int newViewY = (int) (mousePoint.y * scaleRatio - (mousePoint.y - viewPos.y));

                    // Constrain the new positions so they don't go out of bounds
                    newViewX = Math.max(0, newViewX);
                    newViewY = Math.max(0, newViewY);

                    final Point newViewPos = new Point(newViewX, newViewY);
                    SwingUtilities.invokeLater(() -> viewport.setViewPosition(newViewPos));
                }

                repaint();
            }
        });

        addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyPressed(java.awt.event.KeyEvent e) {
                if (e.getKeyCode() == java.awt.event.KeyEvent.VK_ESCAPE) {
                    if (isZoomed()) {
                        state = State.IDLE;
                        repaint();
                    }
                } else if (e.getKeyCode() == java.awt.event.KeyEvent.VK_DELETE
                        || e.getKeyCode() == java.awt.event.KeyEvent.VK_BACK_SPACE) {
                    if (state == State.EDIT_ZOOMED_IN && activeDataset != null) {
                        // Snapshot before delete-in-edit-mode so the user can undo
                        fireUndoSnapshot();
                        activeDataset.getPoints().remove(editingIndex);
                        if (ImageCanvas.this.listener != null)
                            ImageCanvas.this.listener.onPointDeleted(editingIndex);
                        state = State.IDLE;
                        repaint();
                    } else if (state == State.IDLE && activeDataset != null) {
                        int hit = getHitIndex(mousePos);
                        if (hit >= 0) {
                            // Snapshot before idle-mode key-delete so the user can undo
                            fireUndoSnapshot();
                            activeDataset.getPoints().remove(hit);
                            if (ImageCanvas.this.listener != null)
                                ImageCanvas.this.listener.onPointDeleted(hit);
                            repaint();
                        }
                    }
                }
            }
        });
    }

    private void recalculatePreferredSize() {
        if (image == null)
            return;
        int iw = image.getWidth();
        int ih = image.getHeight();

        Container parent = getParent();
        if (parent == null) {
            setPreferredSize(new Dimension(iw, ih));
            return;
        }

        int w = parent.getWidth();
        int h = parent.getHeight();
        if (w == 0 || h == 0) {
            setPreferredSize(new Dimension(iw, ih));
            return;
        }

        double baseScale = Math.min((double) w / iw, (double) h / ih);
        int scaledW = (int) (iw * baseScale * viewScale);
        int scaledH = (int) (ih * baseScale * viewScale);
        setPreferredSize(new Dimension(Math.max(w, scaledW), Math.max(h, scaledH)));
    }

    private boolean isRoiStrokeHit(Point p) {
        if (roiTL == null || roiBR == null)
            return false;
        double[] stl = getScreenCoordinates(roiTL.x, roiTL.y);
        double[] sbr = getScreenCoordinates(roiBR.x, roiBR.y);
        int rx = (int) Math.min(stl[0], sbr[0]);
        int ry = (int) Math.min(stl[1], sbr[1]);
        int rw = (int) Math.abs(stl[0] - sbr[0]);
        int rh = (int) Math.abs(stl[1] - sbr[1]);

        // Check if point is near the boundary but NOT inside the interior margin
        Rectangle outer = new Rectangle(rx - 4, ry - 4, rw + 8, rh + 8);
        Rectangle inner = new Rectangle(rx + 4, ry + 4, rw - 8, rh - 8);

        return outer.contains(p) && !inner.contains(p);
    }

    private Handle getRoiHandleHit(Point p) {
        if (roiTL == null || roiBR == null)
            return Handle.NONE;
        double[] stl = getScreenCoordinates(roiTL.x, roiTL.y);
        double[] sbr = getScreenCoordinates(roiBR.x, roiBR.y);
        int rx = (int) Math.min(stl[0], sbr[0]);
        int ry = (int) Math.min(stl[1], sbr[1]);
        int rw = (int) Math.abs(stl[0] - sbr[0]);
        int rh = (int) Math.abs(stl[1] - sbr[1]);

        int hSize = HANDLE_SIZE;
        Rectangle[] rects = new Rectangle[8];
        rects[0] = new Rectangle(rx - hSize / 2, ry - hSize / 2, hSize, hSize); // TL
        rects[1] = new Rectangle(rx + rw / 2 - hSize / 2, ry - hSize / 2, hSize, hSize); // T
        rects[2] = new Rectangle(rx + rw - hSize / 2, ry - hSize / 2, hSize, hSize); // TR
        rects[3] = new Rectangle(rx + rw - hSize / 2, ry + rh / 2 - hSize / 2, hSize, hSize); // R
        rects[4] = new Rectangle(rx + rw - hSize / 2, ry + rh - hSize / 2, hSize, hSize); // BR
        rects[5] = new Rectangle(rx + rw / 2 - hSize / 2, ry + rh - hSize / 2, hSize, hSize); // B
        rects[6] = new Rectangle(rx - hSize / 2, ry + rh - hSize / 2, hSize, hSize); // BL
        rects[7] = new Rectangle(rx - hSize / 2, ry + rh / 2 - hSize / 2, hSize, hSize); // L

        Handle[] handles = { Handle.TL, Handle.T, Handle.TR, Handle.R, Handle.BR, Handle.B, Handle.BL, Handle.L };
        for (int i = 0; i < 8; i++) {
            if (rects[i].contains(p))
                return handles[i];
        }
        return Handle.NONE;
    }

    private boolean isIdle() {
        return state == State.IDLE || state == State.PICK_X1 || state == State.PICK_X2 || state == State.PICK_Y1
                || state == State.PICK_Y2 || state == State.PICK_TL || state == State.PICK_TR
                || state == State.PICK_BR || state == State.PICK_BL || state == State.DRAG_ROI;
    }

    private boolean isZoomed() {
        return state == State.ZOOMED_IN || state == State.EDIT_ZOOMED_IN || state == State.ZOOMED_X1
                || state == State.ZOOMED_X2 || state == State.ZOOMED_Y1 || state == State.ZOOMED_Y2
                || state == State.ZOOMED_TL || state == State.ZOOMED_TR || state == State.ZOOMED_BR
                || state == State.ZOOMED_BL;
    }

    public void setState(State state) {
        this.state = state;
        repaint();
    }

    public State getState() {
        return this.state;
    }

    private double[] getScreenCoordinates(double ix, double iy) {
        if (image == null)
            return new double[] { 0, 0 };
        int iw = image.getWidth();
        int ih = image.getHeight();

        Container parent = getParent();
        int w = (parent != null && parent.getWidth() > 0) ? parent.getWidth() : getWidth();
        int h = (parent != null && parent.getHeight() > 0) ? parent.getHeight() : getHeight();

        double baseScale = Math.min((double) w / iw, (double) h / ih);
        double s = baseScale * viewScale;

        int currentW = getWidth();
        int currentH = getHeight();

        double dx = (currentW - iw * s) / 2.0;
        double dy = (currentH - ih * s) / 2.0;

        double sx = ix * s + dx;
        double sy = iy * s + dy;
        return new double[] { sx, sy };
    }

    private int getHitIndex(Point p) {
        if (activeDataset == null)
            return -1;
        List<java.awt.geom.Point2D.Double> pts = activeDataset.getPoints();
        for (int i = 0; i < pts.size(); i++) {
            java.awt.geom.Point2D.Double rp = pts.get(i);
            double[] screenCoords = getScreenCoordinates(rp.x, rp.y);
            double dist = Math.hypot(p.x - screenCoords[0], p.y - screenCoords[1]);
            if (dist <= 5.0) {
                return i;
            }
        }
        return -1;
    }

    private double[] getOriginalImageCoordinates(int screenX, int screenY) {
        if (image == null)
            return new double[] { 0, 0 };
        int iw = image.getWidth();
        int ih = image.getHeight();

        Container parent = getParent();
        int w = (parent != null && parent.getWidth() > 0) ? parent.getWidth() : getWidth();
        int h = (parent != null && parent.getHeight() > 0) ? parent.getHeight() : getHeight();

        double baseScale = Math.min((double) w / iw, (double) h / ih);
        double s = baseScale * viewScale;

        int currentW = getWidth();
        int currentH = getHeight();

        double dx = (currentW - iw * s) / 2.0;
        double dy = (currentH - ih * s) / 2.0;

        double ix = (screenX - dx) / s;
        double iy = (screenY - dy) / s;
        return new double[] { ix, iy };
    }

    public void setImage(BufferedImage img) {
        this.image = img;
        this.state = State.IDLE;
        this.viewScale = 1.0;
        recalculatePreferredSize();
        revalidate();
        repaint();
    }

    private void drawInstructions(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("SansSerif", Font.PLAIN, 16));

        String[] instructions = {
                "Welcome to Graphitizer!",
                "",
                "1. Load Image: Use 'Open Image' or 'Paste from Clipboard' to load a graph.",
                "2. Prep Image (Optional): Use 'Keystone Correction' to flatten skewed photos, or",
                "   'Rectangular ROI' to constrain the tracing area.",
                "3. Calibration: Set your Real Coordinates on the left, then use the 'Set' buttons.",
                "   - Click near a point (magnifier appears), then click exactly to set it.",
                "4. Plotting Data: Click the canvas to drop points on the active curve.",
                "   - Drag an existing point to move it. Press ESC or Right-click to abort.",
                "   - Hover and press DEL/Backspace, or Right-click a point to delete it.",
                "   - Use Ctrl + Mouse Wheel to zoom the canvas view.",
                "5. Tracing: Use 'Find Similar Points' or 'Trace This Line' for fast plotting.",
                "6. Multiple Curves: Click the '+' button to add curves (and inherit calibrations).",
                "7. Export: Your data is recorded on the right. Copy or Save as CSV when done.",
                "   - Use the 'Sort Data' dropdown to automatically order points by X or Y.",
                "",
                "Authors: Gemini 3.1 and Willy Langeveld"
        };

        FontMetrics fm = g2.getFontMetrics();
        int lineHeight = fm.getHeight();
        int totalHeight = instructions.length * lineHeight;

        int startY = (getHeight() - totalHeight) / 2;
        if (startY < 50)
            startY = 50;

        for (int i = 0; i < instructions.length; i++) {
            String text = instructions[i];
            int textWidth = fm.stringWidth(text);
            int startX = (getWidth() - textWidth) / 2;
            if (startX < 20)
                startX = 20;
            g2.drawString(text, startX, startY + (i * lineHeight));
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (image == null) {
            drawInstructions(g);
            return;
        }

        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        int iw = image.getWidth();
        int ih = image.getHeight();

        Container parent = getParent();
        int w = (parent != null && parent.getWidth() > 0) ? parent.getWidth() : getWidth();
        int h = (parent != null && parent.getHeight() > 0) ? parent.getHeight() : getHeight();

        double baseScale = Math.min((double) w / iw, (double) h / ih);
        double s = baseScale * viewScale;

        int currentW = getWidth();
        int currentH = getHeight();

        double dx = (currentW - iw * s) / 2.0;
        double dy = (currentH - ih * s) / 2.0;

        // Draw main scaled image
        g2.drawImage(image, (int) dx, (int) dy, (int) (iw * s), (int) (ih * s), null);

        // Draw points
        for (Dataset ds : datasets) {
            g2.setColor(ds.getColor());
            List<java.awt.geom.Point2D.Double> pts = ds.getPoints();
            int prevCx = -1, prevCy = -1;
            for (int i = 0; i < pts.size(); i++) {
                java.awt.geom.Point2D.Double rp;
                if (ds == activeDataset && state == State.EDIT_ZOOMED_IN && i == editingIndex) {
                    double drawX = originX + (mousePos.x - lockedScreenCenter.x) / 10.0;
                    double drawY = originY + (mousePos.y - lockedScreenCenter.y) / 10.0;
                    rp = new java.awt.geom.Point2D.Double(drawX, drawY);
                } else {
                    rp = pts.get(i);
                }

                double[] sc = getScreenCoordinates(rp.x, rp.y);
                int cx = (int) Math.round(sc[0]);
                int cy = (int) Math.round(sc[1]);
                g2.drawLine(cx - 5, cy, cx + 5, cy);
                g2.drawLine(cx, cy - 5, cx, cy + 5);
                g2.drawOval(cx - 3, cy - 3, 6, 6);

                if (prevCx != -1) {
                    g2.drawLine(prevCx, prevCy, cx, cy);
                }
                prevCx = cx;
                prevCy = cy;
            }
        }

        // Draw Keystone Quadrilateral Boundary
        boolean hasAnyKey = keyTL != null || keyTR != null || keyBR != null || keyBL != null;
        if (hasAnyKey) {
            g2.setColor(Color.CYAN);
            Stroke oldStroke = g2.getStroke();
            Stroke dashedStroke = new BasicStroke(2.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f,
                    new float[] { 9.0f }, 0.0f);
            Stroke solidStroke = new BasicStroke(2.0f);

            double[] stl = keyTL != null ? getScreenCoordinates(keyTL.x, keyTL.y) : null;
            double[] str = keyTR != null ? getScreenCoordinates(keyTR.x, keyTR.y) : null;
            double[] sbr = keyBR != null ? getScreenCoordinates(keyBR.x, keyBR.y) : null;
            double[] sbl = keyBL != null ? getScreenCoordinates(keyBL.x, keyBL.y) : null;

            g2.setStroke(solidStroke);
            if (stl != null)
                g2.drawOval((int) stl[0] - 5, (int) stl[1] - 5, 10, 10);
            if (str != null)
                g2.drawOval((int) str[0] - 5, (int) str[1] - 5, 10, 10);
            if (sbr != null)
                g2.drawOval((int) sbr[0] - 5, (int) sbr[1] - 5, 10, 10);
            if (sbl != null)
                g2.drawOval((int) sbl[0] - 5, (int) sbl[1] - 5, 10, 10);

            g2.setStroke(dashedStroke);
            if (stl != null && str != null)
                g2.drawLine((int) stl[0], (int) stl[1], (int) str[0], (int) str[1]);
            if (str != null && sbr != null)
                g2.drawLine((int) str[0], (int) str[1], (int) sbr[0], (int) sbr[1]);
            if (sbr != null && sbl != null)
                g2.drawLine((int) sbr[0], (int) sbr[1], (int) sbl[0], (int) sbl[1]);
            if (sbl != null && stl != null)
                g2.drawLine((int) sbl[0], (int) sbl[1], (int) stl[0], (int) stl[1]);

            g2.setStroke(oldStroke);
        }

        // Draw ROI Rectangular Boundary
        if (roiTL != null && roiBR != null) {
            Stroke oldStroke = g2.getStroke();
            g2.setStroke(new BasicStroke(2.0f));

            double[] stl = getScreenCoordinates(roiTL.x, roiTL.y);
            double[] sbr = getScreenCoordinates(roiBR.x, roiBR.y);

            int rectX = (int) Math.min(stl[0], sbr[0]);
            int rectY = (int) Math.min(stl[1], sbr[1]);
            int rectW = (int) Math.abs(stl[0] - sbr[0]);
            int rectH = (int) Math.abs(stl[1] - sbr[1]);

            // Draw the solid red border
            g2.setColor(Color.RED);
            g2.drawRect(rectX, rectY, rectW, rectH);

            // Draw 8 white handles with black outlines
            int hSize = HANDLE_SIZE;
            Rectangle[] rects = new Rectangle[8];
            rects[0] = new Rectangle(rectX - hSize / 2, rectY - hSize / 2, hSize, hSize); // TL
            rects[1] = new Rectangle(rectX + rectW / 2 - hSize / 2, rectY - hSize / 2, hSize, hSize); // T
            rects[2] = new Rectangle(rectX + rectW - hSize / 2, rectY - hSize / 2, hSize, hSize); // TR
            rects[3] = new Rectangle(rectX + rectW - hSize / 2, rectY + rectH / 2 - hSize / 2, hSize, hSize); // R
            rects[4] = new Rectangle(rectX + rectW - hSize / 2, rectY + rectH - hSize / 2, hSize, hSize); // BR
            rects[5] = new Rectangle(rectX + rectW / 2 - hSize / 2, rectY + rectH - hSize / 2, hSize, hSize); // B
            rects[6] = new Rectangle(rectX - hSize / 2, rectY + rectH - hSize / 2, hSize, hSize); // BL
            rects[7] = new Rectangle(rectX - hSize / 2, rectY + rectH / 2 - hSize / 2, hSize, hSize); // L

            g2.setColor(Color.WHITE);
            for (Rectangle r : rects) {
                g2.fill(r);
            }
            g2.setColor(Color.BLACK);
            g2.setStroke(new BasicStroke(1.0f));
            for (Rectangle r : rects) {
                g2.draw(r);
            }

            g2.setStroke(oldStroke);
        }

        // Draw magnifier
        if (isZoomed()) {
            Graphics2D gMag = (Graphics2D) g2.create();

            Ellipse2D.Double clip = new Ellipse2D.Double(lockedScreenCenter.x - 100, lockedScreenCenter.y - 100, 200,
                    200);

            // Draw background for magnifier in case image has alpha
            gMag.setClip(clip);
            gMag.setColor(Color.LIGHT_GRAY);
            gMag.fill(clip);

            // Calculate scaled image position
            double zoom = 10.0;
            double drawX = lockedScreenCenter.x - originX * zoom;
            double drawY = lockedScreenCenter.y - originY * zoom;

            // Draw 10x magnified image (nearest neighbor for crisp pixels)
            gMag.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            gMag.drawImage(image, (int) Math.round(drawX), (int) Math.round(drawY), (int) Math.round(iw * zoom),
                    (int) Math.round(ih * zoom), null);

            for (Dataset ds : datasets) {
                gMag.setColor(ds.getColor());
                List<java.awt.geom.Point2D.Double> pts = ds.getPoints();
                int prevMagPx = -1, prevMagPy = -1;
                for (int i = 0; i < pts.size(); i++) {
                    java.awt.geom.Point2D.Double rp;
                    if (ds == activeDataset && state == State.EDIT_ZOOMED_IN && i == editingIndex) {
                        double tempDrawX = originX + (mousePos.x - lockedScreenCenter.x) / 10.0;
                        double tempDrawY = originY + (mousePos.y - lockedScreenCenter.y) / 10.0;
                        rp = new java.awt.geom.Point2D.Double(tempDrawX, tempDrawY);
                    } else {
                        rp = pts.get(i);
                    }

                    int magPx = (int) Math.round(lockedScreenCenter.x + (rp.x - originX) * zoom);
                    int magPy = (int) Math.round(lockedScreenCenter.y + (rp.y - originY) * zoom);
                    gMag.drawLine(magPx - 5, magPy, magPx + 5, magPy);
                    gMag.drawLine(magPx, magPy - 5, magPx, magPy + 5);
                    gMag.drawOval(magPx - 3, magPy - 3, 6, 6);

                    if (prevMagPx != -1) {
                        gMag.drawLine(prevMagPx, prevMagPy, magPx, magPy);
                    }
                    prevMagPx = magPx;
                    prevMagPy = magPy;
                }
            }

            gMag.dispose();

            // Draw border
            g2.setColor(Color.WHITE);
            g2.draw(clip);
            g2.setColor(Color.BLACK);
            g2.draw(new Ellipse2D.Double(lockedScreenCenter.x - 101, lockedScreenCenter.y - 101, 202, 202));

            // Draw crosshair bounded by circle
            double dist = Math.hypot(mousePos.x - lockedScreenCenter.x, mousePos.y - lockedScreenCenter.y);
            int crossX = mousePos.x;
            int crossY = mousePos.y;

            if (dist > 100) {
                crossX = (int) (lockedScreenCenter.x + (mousePos.x - lockedScreenCenter.x) * 100 / dist);
                crossY = (int) (lockedScreenCenter.y + (mousePos.y - lockedScreenCenter.y) * 100 / dist);
            }

            g2.setColor(Color.RED);
            g2.drawLine(crossX - 10, crossY, crossX + 10, crossY);
            g2.drawLine(crossX, crossY - 10, crossX, crossY + 10);
        }
    }
}
