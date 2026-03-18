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
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.dnd.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import javax.swing.JSlider;

public class GraphitizerApp extends JFrame {

    private ImageCanvas imageCanvas;
    private DefaultTableModel tableModel;
    private JTable table;

    private JTextField txtRealX1, txtRealX2, txtRealY1, txtRealY2;
    private JPanel dataPhasePanel;
    private JTextField txtPixX1, txtPixX2, txtPixY1, txtPixY2;
    private java.awt.geom.Point2D.Double keyTL, keyTR, keyBR, keyBL;
    private java.util.List<java.awt.geom.Point2D.Double> tempKeystonePoints = new java.util.ArrayList<>();
    private JLabel statusLabel;
    private JCheckBox chkLogX, chkLogY;

    private JButton btnFindSimilar;
    private BufferedImage loadedImage;
    private JSlider accuracySlider;
    private JLabel lblAccuracy;

    private JButton btnTraceLine;
    private JSlider pointsSlider;
    private JLabel lblNumPoints;
    /** Live readout of the current X-Step slider value (shown after the slider). */
    private JLabel lblPointsValue;
    /** Live readout of the current Tolerance slider value (shown after the slider). */
    private JLabel lblAccuracyValue;
    private JLabel lblRoiInstruction;

    private List<Dataset> datasets = new java.util.ArrayList<>();
    private Dataset activeDataset;
    private JComboBox<Dataset> curveCombo;
    private JComboBox<String> modeCombo;
    private JComboBox<String> sortCombo;
    private JComboBox<String> plotAreaCombo;

    // Keystone Buttons
    private JButton btnTL, btnTR, btnBR, btnBL, btnApplyKeystone;
    private JButton btnX1, btnX2, btnY1, btnY2;
    private JPanel keystoneGrid;
    private static final Color[] CURVE_COLORS = { Color.RED, Color.BLUE, Color.GREEN, new Color(255, 128, 0),
            Color.MAGENTA, Color.CYAN };

    // --- Undo System ---
    /** Maximum number of undo steps retained in memory. */
    private static final int MAX_UNDO = 50;
    // Undo Stack — Stores closures (Runnables) that, when executed, revert the state
    private static class UndoItem {
        final Runnable restorePast;
        final java.util.function.Supplier<Runnable> capturePresent;
        UndoItem(Runnable restorePast, java.util.function.Supplier<Runnable> capturePresent) {
            this.restorePast = restorePast;
            this.capturePresent = capturePresent;
        }
    }

    private java.util.Deque<UndoItem> undoStack = new java.util.ArrayDeque<>();
    private java.util.Deque<UndoItem> redoStack = new java.util.ArrayDeque<>();
    private JButton btnUndo;

    public GraphitizerApp() {
        setTitle("Graphitizer v1.1");
        try {
            java.awt.Image appIcon = javax.imageio.ImageIO.read(getClass().getResource("/icon.png"));
            setIconImage(appIcon);
        } catch (Exception e) {
            System.err.println("Could not load application icon.");
        }

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1000, 700);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // TOP Toolbar
        JToolBar toolBar = new JToolBar();
        JButton openBtn = new JButton("Open Image");
        JButton pasteBtn = new JButton("Paste from Clipboard");
        JButton copyBtn = new JButton("Copy Data to Clipboard");
        JButton saveAsBtn = new JButton("Save Data As...");

        styleButton(openBtn);
        styleButton(pasteBtn);
        styleButton(copyBtn);
        styleButton(saveAsBtn);

        btnFindSimilar = new JButton("Find Similar Points");
        styleButton(btnFindSimilar);
        btnFindSimilar.setVisible(false);

        lblAccuracy = new JLabel(" Tolerance: ");
        lblAccuracy.setVisible(false);
        accuracySlider = new JSlider(5, 100, 30);
        accuracySlider.setVisible(false);
        accuracySlider.setPreferredSize(new java.awt.Dimension(120, 25));
        accuracySlider.setFocusable(false); // Suppress the dotted focus border
        accuracySlider.setToolTipText("Adjust template matching tolerance (higher = looser match)");
        // Live readout: shows current tolerance in ΔE (Euclidean color-distance) units
        lblAccuracyValue = new JLabel("  " + accuracySlider.getValue() + " ΔE  ");
        lblAccuracyValue.setFont(lblAccuracyValue.getFont().deriveFont(java.awt.Font.ITALIC));
        lblAccuracyValue.setForeground(new Color(80, 80, 80));
        lblAccuracyValue.setVisible(false);
        accuracySlider.addChangeListener(e -> lblAccuracyValue.setText("  " + accuracySlider.getValue() + " ΔE  "));

        btnTraceLine = new JButton("Trace This Line");
        styleButton(btnTraceLine);
        btnTraceLine.setVisible(false);
        btnTraceLine.addActionListener(e -> runLineTracer());

        lblNumPoints = new JLabel(" X-Step (px): ");
        lblNumPoints.setVisible(false);
        pointsSlider = new JSlider(1, 100, 10); // Default to 10px spacing
        pointsSlider.setVisible(false);
        pointsSlider.setPreferredSize(new java.awt.Dimension(120, 25));
        pointsSlider.setFocusable(false); // Suppress the dotted focus border
        pointsSlider.setToolTipText("Distance in pixels between generated points");
        // Live readout: shows current step size in pixels
        lblPointsValue = new JLabel("  " + pointsSlider.getValue() + " px  ");
        lblPointsValue.setFont(lblPointsValue.getFont().deriveFont(java.awt.Font.ITALIC));
        lblPointsValue.setForeground(new Color(80, 80, 80));
        lblPointsValue.setVisible(false);
        pointsSlider.addChangeListener(e -> lblPointsValue.setText("  " + pointsSlider.getValue() + " px  "));

        activeDataset = new Dataset("Curve 1", CURVE_COLORS[0]);
        datasets.add(activeDataset);
        curveCombo = new JComboBox<>();
        curveCombo.addItem(activeDataset);

        JButton newCurveBtn = new JButton("+");
        newCurveBtn.setFocusPainted(false);
        newCurveBtn.setMargin(new Insets(0, 5, 0, 5));
        newCurveBtn.setFont(new Font("SansSerif", Font.BOLD, 18));
        newCurveBtn.setForeground(new Color(50, 150, 250)); // Modern blue
        newCurveBtn.setToolTipText("Add new curve");

        JButton clearBtn = new JButton("Clear");
        styleButton(clearBtn);
        clearBtn.setForeground(new Color(220, 50, 50)); // Modern red

        toolBar.add(openBtn);
        toolBar.add(Box.createHorizontalStrut(5));
        toolBar.add(pasteBtn);
        toolBar.add(Box.createHorizontalStrut(5));
        toolBar.add(copyBtn);
        toolBar.add(Box.createHorizontalStrut(5));
        toolBar.add(saveAsBtn);
        toolBar.add(Box.createHorizontalStrut(5));
        toolBar.add(btnFindSimilar);
        toolBar.add(Box.createHorizontalStrut(5));
        toolBar.add(lblAccuracy);
        toolBar.add(accuracySlider);
        toolBar.add(lblAccuracyValue);
        toolBar.add(Box.createHorizontalStrut(5));
        toolBar.add(btnTraceLine);
        toolBar.add(Box.createHorizontalStrut(5));
        toolBar.add(lblNumPoints);
        toolBar.add(pointsSlider);
        toolBar.add(lblPointsValue);
        add(toolBar, BorderLayout.NORTH);

        // WEST Calibration Panel -> Now moved to rightPanel NORTH
        JPanel calibPanel = new JPanel();
        calibPanel.setLayout(new BoxLayout(calibPanel, BoxLayout.Y_AXIS));
        calibPanel.setBorder(BorderFactory.createTitledBorder("Axis Calibration"));

        txtRealX1 = new JTextField("0", 5);
        txtRealX2 = new JTextField("10", 5);
        txtRealY1 = new JTextField("0", 5);
        txtRealY2 = new JTextField("10", 5);

        javax.swing.event.DocumentListener realCoordListener = new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                onRealCoordinateChanged();
            }

            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                onRealCoordinateChanged();
            }

            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                onRealCoordinateChanged();
            }
        };
        txtRealX1.getDocument().addDocumentListener(realCoordListener);
        txtRealX2.getDocument().addDocumentListener(realCoordListener);
        txtRealY1.getDocument().addDocumentListener(realCoordListener);
        txtRealY2.getDocument().addDocumentListener(realCoordListener);

        txtPixX1 = new JTextField("Not Set", 7);
        txtPixX1.setEditable(false);
        txtPixX2 = new JTextField("Not Set", 7);
        txtPixX2.setEditable(false);
        txtPixY1 = new JTextField("Not Set", 7);
        txtPixY1.setEditable(false);
        txtPixY2 = new JTextField("Not Set", 7);
        txtPixY2.setEditable(false);

        chkLogX = new JCheckBox("Logarithmic X");
        chkLogY = new JCheckBox("Logarithmic Y");

        btnX1 = new JButton("Set X1");
        btnX2 = new JButton("Set X2");
        btnY1 = new JButton("Set Y1");
        btnY2 = new JButton("Set Y2");

        // Action listeners
        btnX1.addActionListener(e -> imageCanvas.setState(ImageCanvas.State.PICK_X1));
        btnX2.addActionListener(e -> imageCanvas.setState(ImageCanvas.State.PICK_X2));
        btnY1.addActionListener(e -> imageCanvas.setState(ImageCanvas.State.PICK_Y1));
        btnY2.addActionListener(e -> imageCanvas.setState(ImageCanvas.State.PICK_Y2));

        JPanel pX1 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        pX1.add(new JLabel("Real:"));
        pX1.add(txtRealX1);
        pX1.add(new JLabel("Pix:"));
        pX1.add(txtPixX1);
        pX1.add(btnX1);
        JPanel pX2 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        pX2.add(new JLabel("Real:"));
        pX2.add(txtRealX2);
        pX2.add(new JLabel("Pix:"));
        pX2.add(txtPixX2);
        pX2.add(btnX2);
        JPanel pY1 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        pY1.add(new JLabel("Real:"));
        pY1.add(txtRealY1);
        pY1.add(new JLabel("Pix:"));
        pY1.add(txtPixY1);
        pY1.add(btnY1);
        JPanel pY2 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        pY2.add(new JLabel("Real:"));
        pY2.add(txtRealY2);
        pY2.add(new JLabel("Pix:"));
        pY2.add(txtPixY2);
        pY2.add(btnY2);

        calibPanel.add(pX1);
        calibPanel.add(pX2);
        calibPanel.add(pY1);
        calibPanel.add(pY2);

        JPanel pLog = new JPanel(new FlowLayout(FlowLayout.CENTER));
        pLog.add(chkLogX);
        pLog.add(chkLogY);
        calibPanel.add(pLog);

        calibPanel.add(Box.createVerticalStrut(10));

        statusLabel = new JLabel("Status: Uncalibrated");
        statusLabel.setForeground(Color.RED);
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD));
        calibPanel.add(statusLabel);

        // NEW: Plot Area Panel
        JPanel plotAreaPanel = new JPanel();
        plotAreaPanel.setLayout(new BoxLayout(plotAreaPanel, BoxLayout.Y_AXIS));
        plotAreaPanel.setBorder(BorderFactory.createTitledBorder("Plot Area Setup"));

        JPanel comboPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        plotAreaCombo = new JComboBox<>(new String[] { "None (Flat Image)", "Rectangular ROI", "Keystone Correction", "Raw Pixel Coordinates" });
        comboPanel.add(new JLabel("Mode:"));
        comboPanel.add(plotAreaCombo);
        plotAreaPanel.add(comboPanel);

        // Keystone Corner Controls (Hidden by default)
        keystoneGrid = new JPanel(new GridLayout(2, 2, 5, 5));
        keystoneGrid.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        btnTL = new JButton("Set Top Left");
        btnTR = new JButton("Set Top Right");
        btnBL = new JButton("Set Bottom Left");
        btnBR = new JButton("Set Bottom Right");

        java.awt.Dimension keyDim = new java.awt.Dimension(140, 30);
        btnTL.setPreferredSize(keyDim);
        btnTR.setPreferredSize(keyDim);
        btnBL.setPreferredSize(keyDim);
        btnBR.setPreferredSize(keyDim);

        keystoneGrid.add(btnTL);
        keystoneGrid.add(btnTR);
        keystoneGrid.add(btnBL);
        keystoneGrid.add(btnBR);

        btnApplyKeystone = new JButton("Apply Image Warp!");
        btnApplyKeystone.setForeground(new Color(220, 50, 50));
        JPanel applyWarpPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        applyWarpPanel.add(btnApplyKeystone);

        plotAreaPanel.add(keystoneGrid);
        plotAreaPanel.add(applyWarpPanel);

        // NEW: ROI Controls (Instruction only, interaction is drag-based)
        JPanel roiPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        lblRoiInstruction = new JLabel(
                "<html><center><b>Left-Click and Drag on the image<br>to draw a bounding region.</b></center></html>");
        lblRoiInstruction.setForeground(Color.BLACK);
        roiPanel.add(lblRoiInstruction);

        plotAreaPanel.add(roiPanel);

        keystoneGrid.setVisible(false);
        applyWarpPanel.setVisible(false);
        roiPanel.setVisible(false);

        plotAreaCombo.addActionListener(e -> {
            boolean isKeystone = "Keystone Correction".equals(plotAreaCombo.getSelectedItem());
            boolean isRoi = "Rectangular ROI".equals(plotAreaCombo.getSelectedItem());
            boolean isRawPixels = "Raw Pixel Coordinates".equals(plotAreaCombo.getSelectedItem());

            keystoneGrid.setVisible(isKeystone);
            applyWarpPanel.setVisible(isKeystone);

            if (isKeystone) {
                tempKeystonePoints.clear();
                keyTL = keyTR = keyBR = keyBL = null;
                btnTL.setText("Set Top Left");
                btnTR.setText("Set Top Right");
                btnBL.setText("Set Bottom Left");
                btnBR.setText("Set Bottom Right");
                imageCanvas.setKeystonePoints(null, null, null, null);
                imageCanvas.setState(ImageCanvas.State.PICK_KEYSTONE_MULTI);
            }

            roiPanel.setVisible(isRoi);

            if (isRawPixels && loadedImage != null) {
                // Auto-calibrate
                txtRealX1.setText("0");
                txtRealX2.setText(String.valueOf(loadedImage.getWidth()));
                txtRealY1.setText(String.valueOf(loadedImage.getHeight()));
                txtRealY2.setText("0");

                activeDataset.setPixX1(0.0);
                activeDataset.setPixX2((double) loadedImage.getWidth());
                activeDataset.setPixY1((double) loadedImage.getHeight());
                activeDataset.setPixY2(0.0);
                
                txtPixX1.setText("0");
                txtPixX2.setText(String.valueOf(loadedImage.getWidth()));
                txtPixY1.setText(String.valueOf(loadedImage.getHeight()));
                txtPixY2.setText("0");
                
                btnX1.setText("Set X1 ✓");
                btnX2.setText("Set X2 ✓");
                btnY1.setText("Set Y1 ✓");
                btnY2.setText("Set Y2 ✓");
                
                checkCalibration();
            }

            if (!isRoi && !isKeystone) {
                imageCanvas.setRoiPoints(null, null);
                imageCanvas.setState(ImageCanvas.State.IDLE);
                if (lblRoiInstruction != null)
                    lblRoiInstruction.setVisible(false);
            } else if (isRoi) {
                imageCanvas.setState(ImageCanvas.State.DRAG_ROI);
                if (lblRoiInstruction != null)
                    lblRoiInstruction.setVisible(imageCanvas.getRoiTL() == null);
            }

            plotAreaPanel.revalidate();
            plotAreaPanel.repaint();
        });

        // Right Side Panel
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setPreferredSize(new Dimension(300, 0));

        JPanel setupPanelsWrapper = new JPanel();
        setupPanelsWrapper.setLayout(new BoxLayout(setupPanelsWrapper, BoxLayout.Y_AXIS));
        setupPanelsWrapper.add(plotAreaPanel);
        setupPanelsWrapper.add(Box.createVerticalStrut(10));
        setupPanelsWrapper.add(calibPanel);

        rightPanel.add(setupPanelsWrapper, BorderLayout.NORTH);

        // EAST Table
        tableModel = new DefaultTableModel(new String[] { "X", "Y" }, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }

            @Override
            public Class<?> getColumnClass(int columnIndex) {
                return Double.class;
            }
        };
        table = new JTable(tableModel);
        JScrollPane scrollPane = new JScrollPane(table);

        clearBtn.addActionListener(e -> clearSelectedOrAll());
        
        table.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyPressed(java.awt.event.KeyEvent e) {
                if (e.getKeyCode() == java.awt.event.KeyEvent.VK_DELETE || 
                    e.getKeyCode() == java.awt.event.KeyEvent.VK_BACK_SPACE) {
                    clearSelectedOrAll();
                    e.consume();
                } else if (e.getKeyCode() == java.awt.event.KeyEvent.VK_A && e.isControlDown()) {
                    e.consume(); // Hijack default select-all
                    if (table.getSelectedRowCount() == table.getRowCount() && table.getRowCount() > 0) {
                        table.clearSelection();
                    } else {
                        table.selectAll();
                    }
                }
            }
        });

        sortCombo = new JComboBox<>(new String[] { "Manual Sort", "Auto-Sort X", "Auto-Sort Y" });
        sortCombo.setSelectedItem("Manual Sort");
        sortCombo.addActionListener(e -> {
            autoSortData();
            refreshTable();
            if (imageCanvas != null)
                imageCanvas.repaint();
        });

        modeCombo = new JComboBox<>(new String[] { "Point Mode", "Line Mode" });
        modeCombo.addActionListener(e -> {
            updateToolbarButtonVisibility();
            checkWizardState();
            refreshTable();
        });
        JPanel modePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        modePanel.add(new JLabel("Mode:"));
        modePanel.add(modeCombo);
        modePanel.add(sortCombo);

        btnUndo = new JButton("Undo");
        styleButton(btnUndo);
        btnUndo.setForeground(new Color(200, 120, 0)); // Warm amber — "reverse action"
        btnUndo.setEnabled(false);
        btnUndo.setToolTipText("Undo the last point add, delete, move, or autotrace (Ctrl+Z). Redo (Ctrl+Y)");
        btnUndo.addActionListener(e -> undo());

        JPanel curvePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        curvePanel.add(new JLabel("Active Curve:"));
        curvePanel.add(curveCombo);
        curvePanel.add(newCurveBtn);
        curvePanel.add(clearBtn);
        curvePanel.add(btnUndo);

        JPanel topOptionsPanel = new JPanel(new BorderLayout());
        topOptionsPanel.add(modePanel, BorderLayout.NORTH);
        topOptionsPanel.add(curvePanel, BorderLayout.CENTER);

        dataPhasePanel = new JPanel(new BorderLayout());
        dataPhasePanel.add(topOptionsPanel, BorderLayout.NORTH);
        dataPhasePanel.add(scrollPane, BorderLayout.CENTER);
        // Initially hide the Data Phase until calibration is complete
        dataPhasePanel.setVisible(false);

        rightPanel.add(dataPhasePanel, BorderLayout.CENTER);
        add(rightPanel, BorderLayout.EAST);

        // CENTER Canvas
        imageCanvas = new ImageCanvas(new ImageCanvas.PointSelectedListener() {
            @Override
            public void onPointSelected(ImageCanvas.State state, double x, double y) {
                if (activeDataset != null) {
                    if (state == ImageCanvas.State.ZOOMED_X1) {
                        activeDataset.setPixX1(x);
                        txtPixX1.setText(String.format("%.2f", x));
                        btnX1.setText("Set X1 ✓");
                        checkCalibration();
                    } else if (state == ImageCanvas.State.ZOOMED_X2) {
                        activeDataset.setPixX2(x);
                        txtPixX2.setText(String.format("%.2f", x));
                        btnX2.setText("Set X2 ✓");
                        checkCalibration();
                    } else if (state == ImageCanvas.State.ZOOMED_Y1) {
                        activeDataset.setPixY1(y);
                        txtPixY1.setText(String.format("%.2f", y));
                        btnY1.setText("Set Y1 ✓");
                        checkCalibration();
                    } else if (state == ImageCanvas.State.ZOOMED_Y2) {
                        activeDataset.setPixY2(y);
                        txtPixY2.setText(String.format("%.2f", y));
                        btnY2.setText("Set Y2 ✓");
                        checkCalibration();
                    } else if (state == ImageCanvas.State.ZOOMED_TL) {
                        final java.awt.geom.Point2D.Double oldPt = keyTL;
                        Runnable undoKeystone = () -> {
                            keyTL = oldPt;
                            btnTL.setText(oldPt != null ? String.format("%.1f, %.1f", oldPt.x, oldPt.y) : "Set Top Left");
                            imageCanvas.setKeystonePoints(keyTL, keyTR, keyBR, keyBL);
                        };
                        pushUndoAction(new UndoItem(undoKeystone, () -> captureKeystoneState()));
                        
                        keyTL = new java.awt.geom.Point2D.Double(x, y);
                        btnTL.setText(String.format("%.1f, %.1f", x, y));
                        imageCanvas.setKeystonePoints(keyTL, keyTR, keyBR, keyBL);
                    } else if (state == ImageCanvas.State.ZOOMED_TR) {
                        final java.awt.geom.Point2D.Double oldPt = keyTR;
                        Runnable undoKeystone = () -> {
                            keyTR = oldPt;
                            btnTR.setText(oldPt != null ? String.format("%.1f, %.1f", oldPt.x, oldPt.y) : "Set Top Right");
                            imageCanvas.setKeystonePoints(keyTL, keyTR, keyBR, keyBL);
                        };
                        pushUndoAction(new UndoItem(undoKeystone, () -> captureKeystoneState()));

                        keyTR = new java.awt.geom.Point2D.Double(x, y);
                        btnTR.setText(String.format("%.1f, %.1f", x, y));
                        imageCanvas.setKeystonePoints(keyTL, keyTR, keyBR, keyBL);
                    } else if (state == ImageCanvas.State.ZOOMED_BR) {
                        final java.awt.geom.Point2D.Double oldPt = keyBR;
                        Runnable undoKeystone = () -> {
                            keyBR = oldPt;
                            btnBR.setText(oldPt != null ? String.format("%.1f, %.1f", oldPt.x, oldPt.y) : "Set Bottom Right");
                            imageCanvas.setKeystonePoints(keyTL, keyTR, keyBR, keyBL);
                        };
                        pushUndoAction(new UndoItem(undoKeystone, () -> captureKeystoneState()));

                        keyBR = new java.awt.geom.Point2D.Double(x, y);
                        btnBR.setText(String.format("%.1f, %.1f", x, y));
                        imageCanvas.setKeystonePoints(keyTL, keyTR, keyBR, keyBL);
                    } else if (state == ImageCanvas.State.ZOOMED_BL) {
                        final java.awt.geom.Point2D.Double oldPt = keyBL;
                        Runnable undoKeystone = () -> {
                            keyBL = oldPt;
                            btnBL.setText(oldPt != null ? String.format("%.1f, %.1f", oldPt.x, oldPt.y) : "Set Bottom Left");
                            imageCanvas.setKeystonePoints(keyTL, keyTR, keyBR, keyBL);
                        };
                        pushUndoAction(new UndoItem(undoKeystone, () -> captureKeystoneState()));

                        keyBL = new java.awt.geom.Point2D.Double(x, y);
                        btnBL.setText(String.format("%.1f, %.1f", x, y));
                        imageCanvas.setKeystonePoints(keyTL, keyTR, keyBR, keyBL);
                    } else if (state == ImageCanvas.State.ZOOMED_KEYSTONE_MULTI) {
                        java.awt.geom.Point2D.Double newPt = new java.awt.geom.Point2D.Double(x, y);
                        int indexAdded = tempKeystonePoints.size();
                        
                        Runnable undoKeystone = () -> {
                            tempKeystonePoints.remove(indexAdded);
                            updateKeystonePointsUI();
                            imageCanvas.setState(ImageCanvas.State.PICK_KEYSTONE_MULTI);
                        };
                        pushUndoAction(new UndoItem(undoKeystone, () -> captureKeystoneState()));
                        
                        tempKeystonePoints.add(newPt);
                        updateKeystonePointsUI();
                        
                        if (tempKeystonePoints.size() == 4) {
                            sortAndAssignKeystonePoints();
                            imageCanvas.setState(ImageCanvas.State.IDLE);
                        } else {
                            imageCanvas.setState(ImageCanvas.State.PICK_KEYSTONE_MULTI);
                        }
                    }
                }
            }

            @Override
            public void onPointAdded(double x, double y) {
                if (activeDataset != null) {
                    for (java.awt.geom.Point2D.Double p : activeDataset.getPoints()) {
                        if (Math.abs(p.x - x) < 0.001 && Math.abs(p.y - y) < 0.001) {
                            activeDataset.markManual(p);
                        }
                    }
                }
                recordDataPoint(x, y, -1);
            }

            @Override
            public void onPointEdited(int index, double x, double y) {
                recordDataPoint(x, y, index);
            }

            @Override
            public void onPointDeleted(int index) {
                tableModel.removeRow(index);
                updateToolbarButtonVisibility();
            }

            @Override
            public boolean isReadyToPlot() {
                if (activeDataset == null || activeDataset.getPixX1() == null || activeDataset.getPixX2() == null ||
                        activeDataset.getPixY1() == null || activeDataset.getPixY2() == null) {
                    JOptionPane.showMessageDialog(GraphitizerApp.this,
                            "Please calibrate all 4 axes points before adding data! Click the 'Set X1', 'Set X2', ... buttons on the left first.");
                    return false;
                }

                try {
                    Double.parseDouble(txtRealX1.getText());
                    Double.parseDouble(txtRealX2.getText());
                    Double.parseDouble(txtRealY1.getText());
                    Double.parseDouble(txtRealY2.getText());
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(GraphitizerApp.this,
                            "One or more Real coordinate values are invalid. Please fix the entries highlighted in red before plotting data.");
                    return false;
                }

                return true;
            }

            @Override
            public void onRoiUpdated() {
                if (lblRoiInstruction != null && "Rectangular ROI".equals(plotAreaCombo.getSelectedItem())) {
                    lblRoiInstruction.setVisible(imageCanvas.getRoiTL() == null);
                }
            }
        });
        imageCanvas.setDatasets(datasets, activeDataset);
        JScrollPane canvasScrollPane = new JScrollPane(imageCanvas);
        add(canvasScrollPane, BorderLayout.CENTER);

        // Curve Listeners
        curveCombo.addActionListener(e -> {
            if (curveCombo.getSelectedItem() != null) {
                activeDataset = (Dataset) curveCombo.getSelectedItem();
                imageCanvas.setDatasets(datasets, activeDataset);

                txtPixX1.setText(
                        activeDataset.getPixX1() != null ? String.format("%.1f", activeDataset.getPixX1()) : "Not Set");
                txtPixX2.setText(
                        activeDataset.getPixX2() != null ? String.format("%.1f", activeDataset.getPixX2()) : "Not Set");
                txtPixY1.setText(
                        activeDataset.getPixY1() != null ? String.format("%.1f", activeDataset.getPixY1()) : "Not Set");
                txtPixY2.setText(
                        activeDataset.getPixY2() != null ? String.format("%.1f", activeDataset.getPixY2()) : "Not Set");

                btnX1.setText(activeDataset.getPixX1() != null ? "Set X1 ✓" : "Set X1");
                btnX2.setText(activeDataset.getPixX2() != null ? "Set X2 ✓" : "Set X2");
                btnY1.setText(activeDataset.getPixY1() != null ? "Set Y1 ✓" : "Set Y1");
                btnY2.setText(activeDataset.getPixY2() != null ? "Set Y2 ✓" : "Set Y2");

                if (activeDataset.getRealX1() != null)
                    txtRealX1.setText(String.valueOf(activeDataset.getRealX1()));
                if (activeDataset.getRealX2() != null)
                    txtRealX2.setText(String.valueOf(activeDataset.getRealX2()));
                if (activeDataset.getRealY1() != null)
                    txtRealY1.setText(String.valueOf(activeDataset.getRealY1()));
                if (activeDataset.getRealY2() != null)
                    txtRealY2.setText(String.valueOf(activeDataset.getRealY2()));

                refreshTable();
                checkCalibration();
            }
        });

        newCurveBtn.addActionListener(e -> {
            int newIdx = datasets.size();
            Color c = CURVE_COLORS[newIdx % CURVE_COLORS.length];
            Dataset newDs = new Dataset("Curve " + (newIdx + 1), c);

            // Inherit Calibration Prompt
            if (activeDataset != null &&
                    activeDataset.getPixX1() != null && activeDataset.getPixX2() != null &&
                    activeDataset.getPixY1() != null && activeDataset.getPixY2() != null) {

                int inherit = JOptionPane.showConfirmDialog(GraphitizerApp.this,
                        "Would you like to inherit the Axis Calibration from '" + activeDataset.getName()
                                + "' for this new curve?",
                        "Inherit Calibration?",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.QUESTION_MESSAGE);

                if (inherit == JOptionPane.YES_OPTION) {
                    newDs.setPixX1(activeDataset.getPixX1());
                    newDs.setPixX2(activeDataset.getPixX2());
                    newDs.setPixY1(activeDataset.getPixY1());
                    newDs.setPixY2(activeDataset.getPixY2());

                    newDs.setRealX1(activeDataset.getRealX1());
                    newDs.setRealX2(activeDataset.getRealX2());
                    newDs.setRealY1(activeDataset.getRealY1());
                    newDs.setRealY2(activeDataset.getRealY2());
                }
            }

            datasets.add(newDs);
            curveCombo.addItem(newDs);
            curveCombo.setSelectedItem(newDs);
        });

        // Action Listeners
        openBtn.addActionListener(this::openImage);
        pasteBtn.addActionListener(this::pasteImage);
        copyBtn.addActionListener(this::copyData);
        saveAsBtn.addActionListener(this::saveDataAs);

        // Drag and Drop
        new DropTarget(this, new DropTargetAdapter() {
            @Override
            public void drop(DropTargetDropEvent dtde) {
                try {
                    dtde.acceptDrop(DnDConstants.ACTION_COPY);
                    Transferable t = dtde.getTransferable();
                    if (t.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                        List<?> list = (List<?>) t.getTransferData(DataFlavor.javaFileListFlavor);
                        if (!list.isEmpty()) {
                            File file = (File) list.get(0);
                            loadImage(file);
                        }
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });

        btnFindSimilar.addActionListener(e -> runFindSimilar());

        btnApplyKeystone.addActionListener(e -> {
            if (loadedImage == null)
                return;
            if (keyTL == null || keyTR == null || keyBR == null || keyBL == null) {
                JOptionPane.showMessageDialog(this,
                        "Please select all 4 corner points (TL, TR, BR, BL) before applying Keystone Correction.");
                return;
            }

            // Warning prompt if they have unsaved data
            boolean hasData = datasets.stream().anyMatch(ds -> !ds.getPoints().isEmpty());
            if (hasData) {
                int options = JOptionPane.showConfirmDialog(this,
                        "Applying a Perspective Warp will fundamentally change the image geometry.\nThis will clear all currently traced Data Points and Axis Calibrations!\n\nAre you sure you want to proceed?",
                        "Confirm Image Warp",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE);
                if (options != JOptionPane.YES_OPTION)
                    return;
            }

            java.awt.geom.Point2D.Double[] pts = new java.awt.geom.Point2D.Double[] { keyTL, keyTR, keyBR, keyBL };
            BufferedImage warped = ImageAnalyzer.applyKeystoneWarp(loadedImage, pts);

            if (warped != null) {
                loadedImage = warped;
                imageCanvas.setImage(loadedImage);

                // Reset Wizard State & Data
                keyTL = keyTR = keyBR = keyBL = null;
                btnTL.setText("Set TL");
                btnTR.setText("Set TR");
                btnBR.setText("Set BR");
                btnBL.setText("Set BL");
                imageCanvas.setKeystonePoints(null, null, null, null);

                for (Dataset ds : datasets) {
                    ds.getPoints().clear();
                    ds.setPixX1(null);
                    ds.setPixX2(null);
                    ds.setPixY1(null);
                    ds.setPixY2(null);
                }
                txtPixX1.setText("Not Set");
                txtPixX2.setText("Not Set");
                txtPixY1.setText("Not Set");
                txtPixY2.setText("Not Set");

                refreshTable();
                checkWizardState();
                plotAreaCombo.setSelectedIndex(0);

                JOptionPane.showMessageDialog(this,
                        "Perspective Correction applied successfully. Please calibrate your Plot Area axes!");
            } else {
                JOptionPane.showMessageDialog(this, "Failed to apply Keystone Correction. The points may be invalid.");
            }
        });
        // Initial setup binding
        btnTL.addActionListener(e -> imageCanvas.setState(ImageCanvas.State.PICK_TL));
        btnTR.addActionListener(e -> imageCanvas.setState(ImageCanvas.State.PICK_TR));
        btnBR.addActionListener(e -> imageCanvas.setState(ImageCanvas.State.PICK_BR));
        btnBL.addActionListener(e -> imageCanvas.setState(ImageCanvas.State.PICK_BL));

        // Register Ctrl-Z globally and give ImageCanvas a callback so its own
        // point mutations (add, delete, move via mouse/keyboard) can push undo entries.
        registerUndoKeyBinding();
        imageCanvas.setUndoCallback(() -> pushUndo(activeDataset));
    }

    // -----------------------------------------------------------------------
    // Keystone helpers
    // -----------------------------------------------------------------------

    private void updateKeystonePointsUI() {
        if (tempKeystonePoints.size() > 0) {
            keyTL = tempKeystonePoints.get(0);
            btnTL.setText(String.format("%.1f, %.1f", keyTL.x, keyTL.y));
        } else {
            keyTL = null;
            btnTL.setText("Set Top Left");
        }
        if (tempKeystonePoints.size() > 1) {
            keyTR = tempKeystonePoints.get(1);
            btnTR.setText(String.format("%.1f, %.1f", keyTR.x, keyTR.y));
        } else {
            keyTR = null;
            btnTR.setText("Set Top Right");
        }
        if (tempKeystonePoints.size() > 2) {
            keyBR = tempKeystonePoints.get(2);
            btnBR.setText(String.format("%.1f, %.1f", keyBR.x, keyBR.y));
        } else {
            keyBR = null;
            btnBR.setText("Set Bottom Right");
        }
        if (tempKeystonePoints.size() > 3) {
            keyBL = tempKeystonePoints.get(3);
            btnBL.setText(String.format("%.1f, %.1f", keyBL.x, keyBL.y));
        } else {
            keyBL = null;
            btnBL.setText("Set Bottom Left");
        }
        imageCanvas.setKeystonePoints(keyTL, keyTR, keyBR, keyBL);
    }

    private void sortAndAssignKeystonePoints() {
        if (tempKeystonePoints.size() < 4) return;
        java.util.List<java.awt.geom.Point2D.Double> pts = new java.util.ArrayList<>(tempKeystonePoints);
        
        // Sort by X first to separate left side vs right side
        pts.sort((p1, p2) -> Double.compare(p1.x, p2.x));
        java.util.List<java.awt.geom.Point2D.Double> leftPts = pts.subList(0, 2);
        java.util.List<java.awt.geom.Point2D.Double> rightPts = pts.subList(2, 4);
        
        // Sort left side by Y to get Top Left vs Bottom Left
        leftPts.sort((p1, p2) -> Double.compare(p1.y, p2.y));
        keyTL = leftPts.get(0);
        keyBL = leftPts.get(1);
        
        // Sort right side by Y to get Top Right vs Bottom Right
        rightPts.sort((p1, p2) -> Double.compare(p1.y, p2.y));
        keyTR = rightPts.get(0);
        keyBR = rightPts.get(1);
        
        // Match the UI's temporary list ordering to the logical TR, TL, BR, BL order
        tempKeystonePoints.clear();
        tempKeystonePoints.add(keyTL);
        tempKeystonePoints.add(keyTR);
        tempKeystonePoints.add(keyBR);
        tempKeystonePoints.add(keyBL);
        
        updateKeystonePointsUI();
    }

    // -----------------------------------------------------------------------
    // Undo helpers
    // -----------------------------------------------------------------------

    private Runnable captureKeystoneState() {
        final java.awt.geom.Point2D.Double curTL = keyTL;
        final java.awt.geom.Point2D.Double curTR = keyTR;
        final java.awt.geom.Point2D.Double curBL = keyBL;
        final java.awt.geom.Point2D.Double curBR = keyBR;
        final java.util.List<java.awt.geom.Point2D.Double> curTemp = new java.util.ArrayList<>(tempKeystonePoints);
        final ImageCanvas.State curState = imageCanvas.getState();
        
        return () -> {
            keyTL = curTL;
            btnTL.setText(curTL != null ? String.format("%.1f, %.1f", curTL.x, curTL.y) : "Set Top Left");
            keyTR = curTR;
            btnTR.setText(curTR != null ? String.format("%.1f, %.1f", curTR.x, curTR.y) : "Set Top Right");
            keyBR = curBR;
            btnBR.setText(curBR != null ? String.format("%.1f, %.1f", curBR.x, curBR.y) : "Set Bottom Right");
            keyBL = curBL;
            btnBL.setText(curBL != null ? String.format("%.1f, %.1f", curBL.x, curBL.y) : "Set Bottom Left");

            tempKeystonePoints.clear();
            tempKeystonePoints.addAll(curTemp);
            
            imageCanvas.setKeystonePoints(keyTL, keyTR, keyBR, keyBL);
            imageCanvas.setState(curState);
        };
    }

    /**
     * Snapshot the active dataset's current point list BEFORE mutating it.
     * Must be called immediately before any add / delete / move / autotrace
     * operation so that undo() can roll it back.
     */
    void pushUndo(Dataset ds) {
        if (ds == null) return;
        final Dataset.DatasetSnapshot snap = ds.takeSnapshot();
        
        Runnable restoreAction = () -> {
            ds.restoreSnapshot(snap);
            refreshTable();
            if (imageCanvas != null) imageCanvas.repaint();
        };
        
        java.util.function.Supplier<Runnable> capturePresent = () -> {
            final Dataset.DatasetSnapshot currentSnap = ds.takeSnapshot();
            return () -> {
                ds.restoreSnapshot(currentSnap);
                refreshTable();
                if (imageCanvas != null) imageCanvas.repaint();
            };
        };
        
        pushUndoAction(new UndoItem(restoreAction, capturePresent), true);
    }
    
    /**
     * Pushes a generic undo action onto the stack.
     */
    void pushUndoAction(UndoItem ui) {
        pushUndoAction(ui, true);
    }
    
    void pushUndoAction(UndoItem ui, boolean clearRedo) {
        // Trim to keep stack within MAX_UNDO entries
        while (undoStack.size() >= MAX_UNDO)
            ((java.util.ArrayDeque<?>) undoStack).pollLast();
        undoStack.push(ui);
        btnUndo.setEnabled(true);
        if (clearRedo) {
            redoStack.clear();
        }
    }

    /**
     * Pops the top entry from the undo stack and executes its restore Runnable.
     */
    private void undo() {
        if (undoStack.isEmpty()) return;
        UndoItem item = undoStack.pop();
        
        Runnable redoAction = item.capturePresent.get();
        redoStack.push(new UndoItem(redoAction, item.capturePresent));
        
        item.restorePast.run();
        btnUndo.setEnabled(!undoStack.isEmpty());
    }

    private void redo() {
        if (redoStack.isEmpty()) return;
        UndoItem item = redoStack.pop();
        
        Runnable undoAction = item.capturePresent.get();
        undoStack.push(new UndoItem(undoAction, item.capturePresent));
        
        item.restorePast.run();
        btnUndo.setEnabled(!undoStack.isEmpty());
    }

    /**
     * Registers the global Ctrl-Z and Ctrl-Y key bindings.
     */
    private void registerUndoKeyBinding() {
        javax.swing.InputMap im = getRootPane().getInputMap(javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW);
        javax.swing.ActionMap am = getRootPane().getActionMap();
        im.put(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_Z,
                java.awt.event.InputEvent.CTRL_DOWN_MASK), "globalUndo");
        am.put("globalUndo", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                undo();
            }
        });
        
        im.put(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_Y,
                java.awt.event.InputEvent.CTRL_DOWN_MASK), "globalRedo");
        am.put("globalRedo", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                redo();
            }
        });
    }

    private void checkCalibration() {
        if (activeDataset != null && activeDataset.getPixX1() != null && activeDataset.getPixX2() != null &&
                activeDataset.getPixY1() != null && activeDataset.getPixY2() != null) {
            statusLabel.setText("Status: Calibrated");
            statusLabel.setForeground(new Color(0, 150, 0));
            refreshTable();
            checkWizardState();
        } else {
            statusLabel.setText("Status: Uncalibrated");
            statusLabel.setForeground(Color.RED);
            checkWizardState();
        }
    }

    private void checkWizardState() {
        if (dataPhasePanel == null)
            return;
            
        boolean isCalibrated = activeDataset != null && activeDataset.getPixX1() != null
                && activeDataset.getPixX2() != null &&
                activeDataset.getPixY1() != null && activeDataset.getPixY2() != null;

        // Reveal the data entry table if calibrated
        dataPhasePanel.setVisible(isCalibrated);
    }

    private void autoSortData() {
        if (activeDataset == null || sortCombo == null)
            return;
        String mode = (String) sortCombo.getSelectedItem();
        if ("Auto-Sort X".equals(mode)) {
            boolean invertX = false;
            try {
                if (activeDataset.getPixX1() != null && activeDataset.getPixX2() != null) {
                    double realX1 = activeDataset.getRealX1() != null ? activeDataset.getRealX1()
                            : Double.parseDouble(txtRealX1.getText());
                    double realX2 = activeDataset.getRealX2() != null ? activeDataset.getRealX2()
                            : Double.parseDouble(txtRealX2.getText());
                    if ((realX2 - realX1) / (activeDataset.getPixX2() - activeDataset.getPixX1()) < 0) {
                        invertX = true;
                    }
                }
            } catch (Exception e) {
            }
            final boolean finalInvertX = invertX;
            activeDataset.getPoints().sort((p1, p2) -> {
                int c = finalInvertX ? Double.compare(p2.x, p1.x) : Double.compare(p1.x, p2.x);
                return c == 0 ? Double.compare(p1.y, p2.y) : c;
            });
        } else if ("Auto-Sort Y".equals(mode)) {
            boolean invertY = true; // Default to true because lower pixel Y usually means higher real Y
            try {
                if (activeDataset.getPixY1() != null && activeDataset.getPixY2() != null) {
                    double realY1 = activeDataset.getRealY1() != null ? activeDataset.getRealY1()
                            : Double.parseDouble(txtRealY1.getText());
                    double realY2 = activeDataset.getRealY2() != null ? activeDataset.getRealY2()
                            : Double.parseDouble(txtRealY2.getText());
                    if ((realY2 - realY1) / (activeDataset.getPixY2() - activeDataset.getPixY1()) > 0) {
                        invertY = false;
                    }
                }
            } catch (Exception e) {
            }
            final boolean finalInvertY = invertY;
            activeDataset.getPoints().sort((p1, p2) -> {
                int c = finalInvertY ? Double.compare(p2.y, p1.y) : Double.compare(p1.y, p2.y);
                return c == 0 ? Double.compare(p1.x, p2.x) : c;
            });
        }
    }

    private void refreshTable() {
        tableModel.setRowCount(0);
        
        boolean isRawPixels = plotAreaCombo != null && "Raw Pixel Coordinates".equals(plotAreaCombo.getSelectedItem());
        
        if (activeDataset != null) {
            if (activeDataset.getPixX1() != null && activeDataset.getPixX2() != null &&
                    activeDataset.getPixY1() != null && activeDataset.getPixY2() != null) {
                try {
                    double realX1 = Double.parseDouble(txtRealX1.getText());
                    double realX2 = Double.parseDouble(txtRealX2.getText());
                    double realY1 = Double.parseDouble(txtRealY1.getText());
                    double realY2 = Double.parseDouble(txtRealY2.getText());
                    for (java.awt.geom.Point2D.Double p : activeDataset.getPoints()) {
                        double plotX = calculateCoordinate(realX1, realX2, activeDataset.getPixX1(),
                                activeDataset.getPixX2(), p.x, chkLogX.isSelected(), "X");
                        double plotY = calculateCoordinate(realY1, realY2, activeDataset.getPixY1(),
                                activeDataset.getPixY2(), p.y, chkLogY.isSelected(), "Y");
                        
                        if (isRawPixels) {
                            plotX = Math.round(plotX);
                            plotY = Math.round(plotY);
                        }
                        
                        tableModel.addRow(new Object[] { plotX, plotY });
                    }
                } catch (Exception ex) {
                    // Ignore parse errors on refresh
                }
            }
        }

        // Update Find Similar Points button visibility
        updateToolbarButtonVisibility();
    }

    private void onRealCoordinateChanged() {
        if (activeDataset == null)
            return;

        boolean allValid = true;
        double realX1 = 0, realX2 = 0, realY1 = 0, realY2 = 0;

        try {
            realX1 = Double.parseDouble(txtRealX1.getText());
            txtRealX1.setForeground(Color.BLACK);
        } catch (NumberFormatException e) {
            txtRealX1.setForeground(Color.RED);
            allValid = false;
        }

        try {
            realX2 = Double.parseDouble(txtRealX2.getText());
            txtRealX2.setForeground(Color.BLACK);
        } catch (NumberFormatException e) {
            txtRealX2.setForeground(Color.RED);
            allValid = false;
        }

        try {
            realY1 = Double.parseDouble(txtRealY1.getText());
            txtRealY1.setForeground(Color.BLACK);
        } catch (NumberFormatException e) {
            txtRealY1.setForeground(Color.RED);
            allValid = false;
        }

        try {
            realY2 = Double.parseDouble(txtRealY2.getText());
            txtRealY2.setForeground(Color.BLACK);
        } catch (NumberFormatException e) {
            txtRealY2.setForeground(Color.RED);
            allValid = false;
        }

        if (!allValid)
            return;

        if (txtPixX1.getText().isEmpty() || txtPixX2.getText().isEmpty() ||
                txtPixY1.getText().isEmpty() || txtPixY2.getText().isEmpty() ||
                "Not Set".equals(txtPixX1.getText()) || "Not Set".equals(txtPixX2.getText()) ||
                "Not Set".equals(txtPixY1.getText()) || "Not Set".equals(txtPixY2.getText())) {
            return; // Valid numbers typed, but pixels aren't mapped yet so don't recalculate data.
        }

        activeDataset.setRealX1(realX1);
        activeDataset.setRealX2(realX2);
        activeDataset.setRealY1(realY1);
        activeDataset.setRealY2(realY2);

        if (activeDataset.getPixX2() - activeDataset.getPixX1() == 0
                || activeDataset.getPixY2() - activeDataset.getPixY1() == 0) {
            return; // Silently ignore invalid pixels
        }

        if (!"Manual Sort".equals(sortCombo.getSelectedItem())) {
            autoSortData();
        }
        refreshTable();
        checkCalibration();
        updateToolbarButtonVisibility();
        if (imageCanvas != null)
            imageCanvas.repaint();
    }

    private void updateToolbarButtonVisibility() {
        boolean hasPoints = activeDataset != null && !activeDataset.getPoints().isEmpty();
        boolean isFullyCalibrated = activeDataset != null && activeDataset.getPixX1() != null
                && activeDataset.getPixX2() != null &&
                activeDataset.getPixY1() != null && activeDataset.getPixY2() != null;
        
        boolean shouldShow = isFullyCalibrated && hasPoints && loadedImage != null;

        boolean isLineMode = "Line Mode".equals(modeCombo.getSelectedItem());

        if (btnFindSimilar != null && btnTraceLine != null) {
            btnFindSimilar.setVisible(shouldShow && !isLineMode);
            lblAccuracy.setVisible(shouldShow && !isLineMode);
            accuracySlider.setVisible(shouldShow && !isLineMode);
            lblAccuracyValue.setVisible(shouldShow && !isLineMode);

            btnTraceLine.setVisible(shouldShow && isLineMode);
            lblNumPoints.setVisible(shouldShow && isLineMode);
            pointsSlider.setVisible(shouldShow && isLineMode);
            lblPointsValue.setVisible(shouldShow && isLineMode);
        }
    }

    private void recordDataPoint(double pixX, double pixY, int editIndex) {
        if (activeDataset == null) return;
        
        boolean isRawPixels = plotAreaCombo != null && "Raw Pixel Coordinates".equals(plotAreaCombo.getSelectedItem());

        if (activeDataset.getPixX1() != null && activeDataset.getPixX2() != null &&
                activeDataset.getPixY1() != null && activeDataset.getPixY2() != null) {
            try {
                double realX1 = Double.parseDouble(txtRealX1.getText());
                double realX2 = Double.parseDouble(txtRealX2.getText());
                double realY1 = Double.parseDouble(txtRealY1.getText());
                double realY2 = Double.parseDouble(txtRealY2.getText());

                if (activeDataset.getPixX2() - activeDataset.getPixX1() == 0
                        || activeDataset.getPixY2() - activeDataset.getPixY1() == 0) {
                    JOptionPane.showMessageDialog(this,
                            "Calibration error: X1 and X2 (or Y1 and Y2) pixels are identical.");
                    return;
                }

                double plotX = calculateCoordinate(realX1, realX2, activeDataset.getPixX1(), activeDataset.getPixX2(),
                        pixX, chkLogX.isSelected(), "X");
                double plotY = calculateCoordinate(realY1, realY2, activeDataset.getPixY1(), activeDataset.getPixY2(),
                        pixY, chkLogY.isSelected(), "Y");

                if (isRawPixels) {
                    plotX = Math.round(plotX);
                    plotY = Math.round(plotY);
                }

                if (!"Manual Sort".equals(sortCombo.getSelectedItem())) {
                    autoSortData();
                    refreshTable();
                } else {
                    if (editIndex >= 0) {
                        tableModel.setValueAt(plotX, editIndex, 0);
                        tableModel.setValueAt(plotY, editIndex, 1);
                        table.scrollRectToVisible(table.getCellRect(editIndex, 0, true));
                    } else {
                        tableModel.addRow(new Object[] { plotX, plotY });
                        int newRow = tableModel.getRowCount() - 1;
                        table.scrollRectToVisible(table.getCellRect(newRow, 0, true));
                    }
                }
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, "Please enter valid numeric values for all Real coords.");
            }
        } else {
            JOptionPane.showMessageDialog(this, "Please calibrate all 4 axes points first!");
        }
        updateToolbarButtonVisibility();
    }

    private double calculateCoordinate(double real1, double real2, double pix1, double pix2, double curPix,
            boolean isLog, String axis) {
        double ratio = (curPix - pix1) / (pix2 - pix1);
        if (isLog) {
            if (real1 <= 0 || real2 <= 0) {
                JOptionPane.showMessageDialog(this,
                        "Logarithmic scale requires values > 0. Falling back to linear for " + axis);
                return real1 + ratio * (real2 - real1);
            }
            double log1 = Math.log10(real1);
            double log2 = Math.log10(real2);
            double interpLog = log1 + ratio * (log2 - log1);
            return Math.pow(10, interpLog);
        } else {
            return real1 + ratio * (real2 - real1);
        }
    }

    private void openImage(ActionEvent e) {
        if (!checkUnsavedData())
            return;

        JFileChooser chooser = new JFileChooser();
        File settingsFile = getSettingsFile();
        String defaultDir = loadSetting(settingsFile, "DefaultDir");
        if (defaultDir != null && !defaultDir.isEmpty()) {
            File dirFile = new File(defaultDir);
            if (dirFile.exists() && dirFile.isDirectory()) {
                chooser.setCurrentDirectory(dirFile);
            }
        }

        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File selectedFile = chooser.getSelectedFile();
            saveSetting(settingsFile, "DefaultDir", selectedFile.getParent());
            loadImage(selectedFile);
        }
    }

    private File getSettingsFile() {
        String appData = System.getenv("LOCALAPPDATA");
        if (appData == null) {
            appData = System.getProperty("user.home");
        }
        File dir = new File(appData, "Graphitizer");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return new File(dir, "settings.txt");
    }

    private String loadSetting(File file, String key) {
        if (!file.exists())
            return null;
        try (java.io.FileInputStream fis = new java.io.FileInputStream(file)) {
            java.util.Properties props = new java.util.Properties();
            props.load(fis);
            return props.getProperty(key);
        } catch (Exception ex) {
            // Ignore if we can't read settings
        }
        return null;
    }

    private void saveSetting(File file, String key, String value) {
        if (value == null)
            return;
        java.util.Properties props = new java.util.Properties();
        if (file.exists()) {
            try (java.io.FileInputStream fis = new java.io.FileInputStream(file)) {
                props.load(fis);
            } catch (Exception ex) {
            }
        }
        props.setProperty(key, value);
        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(file)) {
            props.store(fos, "Graphitizer Settings");
        } catch (Exception ex) {
        }
    }

    private void loadImage(File file) {
        try {
            BufferedImage img = ImageIO.read(file);
            if (img != null) {
                resetApplication(); // Reset before setting new image
                loadedImage = img;
                imageCanvas.setImage(loadedImage);
                // If already in Raw Pixel mode, auto-calibrate to the new image dimensions
                applyRawPixelCalibrationIfActive();
            } else {
                JOptionPane.showMessageDialog(this, "Could not read image from file: " + file.getName());
            }
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Error loading image: " + ex.getMessage());
        }
    }

    /**
     * If the Plot Area combo is set to "Raw Pixel Coordinates" and an image is
     * loaded, automatically populates the axis calibration fields with the
     * image's pixel boundaries. This mirrors exactly what happens when the user
     * switches the combo to that mode while an image is already open.
     */
    private void applyRawPixelCalibrationIfActive() {
        if (loadedImage == null || !"Raw Pixel Coordinates".equals(plotAreaCombo.getSelectedItem()))
            return;

        txtRealX1.setText("0");
        txtRealX2.setText(String.valueOf(loadedImage.getWidth()));
        txtRealY1.setText(String.valueOf(loadedImage.getHeight()));
        txtRealY2.setText("0");

        activeDataset.setPixX1(0.0);
        activeDataset.setPixX2((double) loadedImage.getWidth());
        activeDataset.setPixY1((double) loadedImage.getHeight());
        activeDataset.setPixY2(0.0);

        txtPixX1.setText("0");
        txtPixX2.setText(String.valueOf(loadedImage.getWidth()));
        txtPixY1.setText(String.valueOf(loadedImage.getHeight()));
        txtPixY2.setText("0");

        btnX1.setText("Set X1 ✓");
        btnX2.setText("Set X2 ✓");
        btnY1.setText("Set Y1 ✓");
        btnY2.setText("Set Y2 ✓");

        btnX1.setForeground(Color.BLACK);
        btnX2.setForeground(Color.BLACK);
        btnY1.setForeground(Color.BLACK);
        btnY2.setForeground(Color.BLACK);

        checkCalibration();
    }

    private void pasteImage(ActionEvent e) {
        if (!checkUnsavedData())
            return;

        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        if (clipboard.isDataFlavorAvailable(DataFlavor.imageFlavor)) {
            try {
                Image img = (Image) clipboard.getData(DataFlavor.imageFlavor);
                resetApplication();
                loadedImage = toBufferedImage(img);
                imageCanvas.setImage(loadedImage);
                // If already in Raw Pixel mode, auto-calibrate to the new image dimensions
                applyRawPixelCalibrationIfActive();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error pasting image: " + ex.getMessage());
            }
        } else {
            JOptionPane.showMessageDialog(this, "No image found on clipboard.");
        }
    }

    private boolean checkUnsavedData() {
        boolean hasData = false;
        for (Dataset ds : datasets) {
            if (!ds.getPoints().isEmpty()) {
                hasData = true;
                break;
            }
        }
        boolean hasCalibration = (activeDataset != null
                && (activeDataset.getPixX1() != null || activeDataset.getPixX2() != null
                        || activeDataset.getPixY1() != null || activeDataset.getPixY2() != null));

        if (hasData || hasCalibration) {
            int options = JOptionPane.showConfirmDialog(this,
                    "Loading a new image will discard your current calibration and all entered data.\n\nAre you sure you want to continue?",
                    "Discard Current Data?",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            return options == JOptionPane.YES_OPTION;
        }
        return true;
    }

    private void resetApplication() {
        // Reset calibration points
        if (activeDataset != null) {
            activeDataset.setPixX1(null);
            activeDataset.setPixX2(null);
            activeDataset.setPixY1(null);
            activeDataset.setPixY2(null);
        }
        txtPixX1.setText("");
        txtPixX2.setText("");
        txtPixY1.setText("");
        txtPixY2.setText("");

        // Reset Real Coordinates defaults
        txtRealX1.setText("0");
        txtRealX2.setText("10");
        txtRealY1.setText("0");
        txtRealY2.setText("10");

        // Uncheck Log scale
        chkLogX.setSelected(false);
        chkLogY.setSelected(false);

        // Reset state label
        checkCalibration();

        // Regenerate datasets natively from scratch
        datasets.clear();
        curveCombo.removeAllItems();
        activeDataset = new Dataset("Curve 1", CURVE_COLORS[0]);
        datasets.add(activeDataset);
        curveCombo.addItem(activeDataset);
        curveCombo.setSelectedItem(activeDataset);

        // Notify Canvas to update
        imageCanvas.setDatasets(datasets, activeDataset);

        // Empty out the unified array table
        refreshTable();
    }

    private java.awt.image.BufferedImage toBufferedImage(Image img) {
        if (img instanceof java.awt.image.BufferedImage) {
            return (java.awt.image.BufferedImage) img;
        }
        java.awt.image.BufferedImage bimage = new java.awt.image.BufferedImage(
                img.getWidth(null), img.getHeight(null), java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D bGr = bimage.createGraphics();
        bGr.drawImage(img, 0, 0, null);
        bGr.dispose();
        return bimage;
    }

    private void clearSelectedOrAll() {
        if (activeDataset == null) return;
        
        int[] selectedRows = table.getSelectedRows();
        boolean hasSelection = selectedRows.length > 0;
        
        if (!hasSelection && activeDataset.getPoints().isEmpty()) {
            return;
        }
        
        // Push full state to undo stack before deleting points
        pushUndo(activeDataset);
        
        if (hasSelection) {
            // Remove backwards by index so we don't shift elements down mid-loop
            for (int i = selectedRows.length - 1; i >= 0; i--) {
                int rowIndex = selectedRows[i];
                if (rowIndex >= 0 && rowIndex < activeDataset.getPoints().size()) {
                    activeDataset.getPoints().remove(rowIndex);
                }
            }
        } else {
            // No selection -> standard clear all
            activeDataset.getPoints().clear();
        }
        
        refreshTable();
        if (imageCanvas != null) imageCanvas.repaint();
    }

    private String buildCsvData(boolean onlySelected) {
        StringBuilder sb = new StringBuilder();
        sb.append("Curve,X,Y\n");
        boolean anyData = false;
        
        boolean isRawPixels = plotAreaCombo != null && "Raw Pixel Coordinates".equals(plotAreaCombo.getSelectedItem());

        if (onlySelected && activeDataset != null) {
            // Export ONLY the selected rows from the ACTIVE dataset
            if (activeDataset.getPixX1() != null && activeDataset.getPixX2() != null && activeDataset.getPixY1() != null && activeDataset.getPixY2() != null) {
                try {
                    double realX1 = activeDataset.getRealX1() != null ? activeDataset.getRealX1() : Double.parseDouble(txtRealX1.getText());
                    double realX2 = activeDataset.getRealX2() != null ? activeDataset.getRealX2() : Double.parseDouble(txtRealX2.getText());
                    double realY1 = activeDataset.getRealY1() != null ? activeDataset.getRealY1() : Double.parseDouble(txtRealY1.getText());
                    double realY2 = activeDataset.getRealY2() != null ? activeDataset.getRealY2() : Double.parseDouble(txtRealY2.getText());

                    int[] selectedRows = table.getSelectedRows();
                    java.util.List<java.awt.geom.Point2D.Double> points = activeDataset.getPoints();
                    
                    for (int rowIndex : selectedRows) {
                        if (rowIndex >= 0 && rowIndex < points.size()) {
                            java.awt.geom.Point2D.Double p = points.get(rowIndex);
                            double plotX = calculateCoordinate(realX1, realX2, activeDataset.getPixX1(), activeDataset.getPixX2(), p.x, chkLogX.isSelected(), "X");
                            double plotY = calculateCoordinate(realY1, realY2, activeDataset.getPixY1(), activeDataset.getPixY2(), p.y, chkLogY.isSelected(), "Y");
                            
                            if (isRawPixels) {
                                plotX = Math.round(plotX);
                                plotY = Math.round(plotY);
                            }
                            
                            sb.append(activeDataset.getName()).append(",");
                            if (isRawPixels) {
                                sb.append((long)plotX).append(",");
                                sb.append((long)plotY).append("\n");
                            } else {
                                sb.append(plotX).append(",");
                                sb.append(plotY).append("\n");
                            }
                            anyData = true;
                        }
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Calibration bounds error exporting selected data from curve: " + activeDataset.getName());
                }
            }
        } else {
            // Export ALL datasets normally
            for (Dataset ds : datasets) {
            if (ds.getPixX1() != null && ds.getPixX2() != null && ds.getPixY1() != null && ds.getPixY2() != null) {
                try {
                    // Extract curve-specific real max/min if they exist, else fallback
                    double realX1 = ds.getRealX1() != null ? ds.getRealX1() : Double.parseDouble(txtRealX1.getText());
                    double realX2 = ds.getRealX2() != null ? ds.getRealX2() : Double.parseDouble(txtRealX2.getText());
                    double realY1 = ds.getRealY1() != null ? ds.getRealY1() : Double.parseDouble(txtRealY1.getText());
                    double realY2 = ds.getRealY2() != null ? ds.getRealY2() : Double.parseDouble(txtRealY2.getText());

                    for (java.awt.geom.Point2D.Double p : ds.getPoints()) {
                        double plotX = calculateCoordinate(realX1, realX2, ds.getPixX1(), ds.getPixX2(), p.x,
                                chkLogX.isSelected(), "X");
                        double plotY = calculateCoordinate(realY1, realY2, ds.getPixY1(), ds.getPixY2(), p.y,
                                chkLogY.isSelected(), "Y");
                        
                        if (isRawPixels) {
                            plotX = Math.round(plotX);
                            plotY = Math.round(plotY);
                        }
                        
                        sb.append(ds.getName()).append(",");
                        if (isRawPixels) {
                            sb.append((long)plotX).append(",");
                            sb.append((long)plotY).append("\n");
                        } else {
                            sb.append(plotX).append(",");
                            sb.append(plotY).append("\n");
                        }
                        anyData = true;
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Calibration bounds error exporting curve: " + ds.getName());
                }
            }
        }
        }

        if (!anyData) {
            JOptionPane.showMessageDialog(this, "No valid data to export.");
            return null;
        }

        return sb.toString();
    }

    private void copyData(ActionEvent e) {
        boolean onlySelected = table != null && table.getSelectedRowCount() > 0;
        String csvData = buildCsvData(onlySelected);
        if (csvData != null) {
            StringSelection selection = new StringSelection(csvData);
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(selection, null);
            JOptionPane.showMessageDialog(this, "Data exported successfully to clipboard!");
        }
    }

    private void saveDataAs(ActionEvent e) {
        boolean onlySelected = table != null && table.getSelectedRowCount() > 0;
        String csvData = buildCsvData(onlySelected);
        if (csvData == null)
            return;

        JFileChooser chooser = new JFileChooser();
        File settingsFile = getSettingsFile();
        String defaultSaveDir = loadSetting(settingsFile, "DefaultSaveDir");
        if (defaultSaveDir != null && !defaultSaveDir.isEmpty()) {
            File dirFile = new File(defaultSaveDir);
            if (dirFile.exists() && dirFile.isDirectory()) {
                chooser.setCurrentDirectory(dirFile);
            }
        }

        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File selectedFile = chooser.getSelectedFile();
            if (!selectedFile.getName().toLowerCase().endsWith(".csv")) {
                selectedFile = new File(selectedFile.getParentFile(), selectedFile.getName() + ".csv");
            }
            saveSetting(settingsFile, "DefaultSaveDir", selectedFile.getParent());
            try (java.io.PrintWriter writer = new java.io.PrintWriter(selectedFile)) {
                writer.print(csvData);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error saving file: " + ex.getMessage());
            }
        }
    }

    private void runFindSimilar() {
        if (activeDataset == null || activeDataset.getPoints().isEmpty() || loadedImage == null) {
            return;
        }

        if (activeDataset.getPixX1() == null || activeDataset.getPixX2() == null ||
                activeDataset.getPixY1() == null || activeDataset.getPixY2() == null) {
            return;
        }

        java.awt.geom.Point2D.Double lastPoint = activeDataset.getPoints().get(activeDataset.getPoints().size() - 1);
        Point refPixel = new Point((int) Math.round(lastPoint.x), (int) Math.round(lastPoint.y));

        // Default to the full image bounds so curves extending outside the calibrated axis grid aren't arbitrarily truncated.
        Rectangle bounds = new Rectangle(0, 0, loadedImage.getWidth(), loadedImage.getHeight());

        // Use custom user ROI if explicitly enabled to mask off noise
        if ("Rectangular ROI".equals(plotAreaCombo.getSelectedItem()) && imageCanvas.getRoiTL() != null
                && imageCanvas.getRoiBR() != null) {
            int roiX = (int) Math.min(imageCanvas.getRoiTL().x, imageCanvas.getRoiBR().x);
            int roiY = (int) Math.min(imageCanvas.getRoiTL().y, imageCanvas.getRoiBR().y);
            int roiW = (int) Math.abs(imageCanvas.getRoiTL().x - imageCanvas.getRoiBR().x);
            int roiH = (int) Math.abs(imageCanvas.getRoiTL().y - imageCanvas.getRoiBR().y);
            bounds = new Rectangle(roiX, roiY, roiW, roiH);
        }

        // Ensure bounds are within the image
        bounds = bounds.intersection(new Rectangle(0, 0, loadedImage.getWidth(), loadedImage.getHeight()));
        if (bounds.isEmpty() || bounds.width <= 0 || bounds.height <= 0) {
            JOptionPane.showMessageDialog(this,
                    "The search area bounds are invalid. Please check your Calibration or drawn ROI.");
            return;
        }

        btnFindSimilar.setEnabled(false);
        accuracySlider.setEnabled(false);
        final Rectangle searchArea = bounds;
        final double matchThreshold = (double) accuracySlider.getValue();

        javax.swing.SwingWorker<List<java.awt.geom.Point2D.Double>, Void> worker = new javax.swing.SwingWorker<>() {
            @Override
            protected List<java.awt.geom.Point2D.Double> doInBackground() throws Exception {
                return ImageAnalyzer.findSimilarPoints(loadedImage, refPixel, searchArea, matchThreshold);
            }

            @Override
            protected void done() {
                try {
                    List<java.awt.geom.Point2D.Double> results = get();
                    if (results != null && !results.isEmpty()) {
                        // Snapshot before bulk-adding auto-found points so Undo works
                        pushUndo(activeDataset);
                        for (java.awt.geom.Point2D.Double p : results) {
                            // Don't re-add the exact same template source center
                            if (p.distanceSq(refPixel) > 25.0) {
                                activeDataset.getPoints().add(new java.awt.geom.Point2D.Double(p.x, p.y));
                                recordDataPoint(p.x, p.y, -1);
                            }
                        }
                        autoSortData();
                        refreshTable();
                        imageCanvas.repaint();
                        JOptionPane.showMessageDialog(GraphitizerApp.this,
                                "Found " + results.size() + " similar points!");
                    } else {
                        JOptionPane.showMessageDialog(GraphitizerApp.this,
                                "No similar points found matching the last plotted point.");
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(GraphitizerApp.this, "Error scanning for points: " + ex.getMessage());
                } finally {
                    btnFindSimilar.setEnabled(true);
                    accuracySlider.setEnabled(true);
                }
            }
        };
        worker.execute();
    }

    private void runLineTracer() {
        if (activeDataset == null || activeDataset.getPoints().isEmpty() || loadedImage == null) {
            return;
        }

        if (activeDataset.getPixX1() == null || activeDataset.getPixX2() == null ||
                activeDataset.getPixY1() == null || activeDataset.getPixY2() == null) {
            return;
        }

        java.awt.geom.Point2D.Double lastPoint = activeDataset.getPoints().get(activeDataset.getPoints().size() - 1);
        double startX = lastPoint.x;
        double startY = lastPoint.y;
        final Point refPixel = new Point((int) Math.round(startX), (int) Math.round(startY));

        // Default to the full image bounds so curves extending outside the calibrated axis grid aren't artificially blocked.
        Rectangle bounds = new Rectangle(0, 0, loadedImage.getWidth(), loadedImage.getHeight());

        // Use custom user ROI if explicitly enabled to mask off noise
        if ("Rectangular ROI".equals(plotAreaCombo.getSelectedItem()) && imageCanvas.getRoiTL() != null
                && imageCanvas.getRoiBR() != null) {
            int roiX = (int) Math.min(imageCanvas.getRoiTL().x, imageCanvas.getRoiBR().x);
            int roiY = (int) Math.min(imageCanvas.getRoiTL().y, imageCanvas.getRoiBR().y);
            int roiW = (int) Math.abs(imageCanvas.getRoiTL().x - imageCanvas.getRoiBR().x);
            int roiH = (int) Math.abs(imageCanvas.getRoiTL().y - imageCanvas.getRoiBR().y);
            bounds = new Rectangle(roiX, roiY, roiW, roiH);
        }

        // Ensure bounds are strictly within the image dimensions
        bounds = bounds.intersection(new Rectangle(0, 0, loadedImage.getWidth(), loadedImage.getHeight()));
        if (bounds.isEmpty() || bounds.width <= 0 || bounds.height <= 0) {
            JOptionPane.showMessageDialog(this,
                    "The search area bounds are invalid. Please check your Calibration or drawn ROI.");
            return;
        }

        final Rectangle searchArea = bounds;
        final int targetDistance = pointsSlider.getValue();
        // Base color tolerance can be a fixed conservative value, or we could add
        // another slider
        // Let's use 30.0 for Line Tracing since Accuracy slider is hidden.
        final double colorTolerance = 30.0;

        btnTraceLine.setEnabled(false);
        pointsSlider.setEnabled(false);

        javax.swing.SwingWorker<List<java.awt.geom.Point2D.Double>, Void> worker = new javax.swing.SwingWorker<>() {
            @Override
            protected List<java.awt.geom.Point2D.Double> doInBackground() throws Exception {
                List<java.awt.geom.Point2D.Double> rawTrace = ImageAnalyzer.traceLine(loadedImage, refPixel, searchArea,
                        colorTolerance);
                return ImageAnalyzer.resampleTraceByXDistance(rawTrace, targetDistance);
            }

            @Override
            protected void done() {
                try {
                    List<java.awt.geom.Point2D.Double> results = get();
                    if (results != null && !results.isEmpty()) {
                        System.out.println("--- NEW LINE TRACE ARRAY OUTPUT ---");
                        List<java.awt.geom.Point2D.Double> newPoints = new ArrayList<>();
                        for (java.awt.geom.Point2D.Double p : results) {
                            System.out.printf("Raw Trace Output: X: %.3f, Y: %.3f\n", p.x, p.y);
                            // The seed point was already added by ImageCanvas mouse click, avoid duplicate
                            if (Math.abs(p.x - startX) > 1.0 || Math.abs(p.y - startY) > 1.0) {
                                newPoints.add(new java.awt.geom.Point2D.Double(p.x, p.y));
                            }
                        }

                        List<java.awt.geom.Point2D.Double> existingPoints = activeDataset.getPoints();
                        boolean hasConflict = false;
                        for (java.awt.geom.Point2D.Double np : newPoints) {
                            for (java.awt.geom.Point2D.Double ep : existingPoints) {
                                if (Math.abs(np.x - ep.x) < 0.01 && !activeDataset.isManual(ep)) {
                                    hasConflict = true;
                                    break;
                                }
                            }
                            if (hasConflict)
                                break;
                        }

                        if (hasConflict) {
                            String[] options = {
                                    "Keep old points",
                                    "Keep new points",
                                    "Keep only the new points that do not conflict"
                            };
                            int choice = JOptionPane.showOptionDialog(GraphitizerApp.this,
                                    "Conflict detected: The autotrace found points with the same X coordinates as existing autotraced points.\nWhat would you like to do?",
                                    "Autotrace Conflict",
                                    JOptionPane.DEFAULT_OPTION,
                                    JOptionPane.QUESTION_MESSAGE,
                                    null,
                                    options,
                                    options[2]);

                            if (choice == 0) {
                                // "Keep old points" -> Discard the entire new trace (Cancel)
                                newPoints.clear();
                            } else if (choice == 1) {
                                // "Keep new points" -> Remove existing autotraced points that conflict with new
                                // trace
                                List<java.awt.geom.Point2D.Double> toRemove = new ArrayList<>();
                                for (java.awt.geom.Point2D.Double np : newPoints) {
                                    for (java.awt.geom.Point2D.Double ep : existingPoints) {
                                        if (Math.abs(np.x - ep.x) < 0.01 && !activeDataset.isManual(ep)) {
                                            toRemove.add(ep);
                                        }
                                    }
                                }
                                existingPoints.removeAll(toRemove);
                            } else if (choice == 2) {
                                // "Keep only the new points that do not conflict"
                                List<java.awt.geom.Point2D.Double> filteredNew = new ArrayList<>();
                                for (java.awt.geom.Point2D.Double np : newPoints) {
                                    boolean conflicts = false;
                                    for (java.awt.geom.Point2D.Double ep : existingPoints) {
                                        if (Math.abs(np.x - ep.x) < 0.01 && !activeDataset.isManual(ep)) {
                                            conflicts = true;
                                            break;
                                        }
                                    }
                                    if (!conflicts) {
                                        filteredNew.add(np);
                                    }
                                }
                                newPoints = filteredNew;
                            } else {
                                // Dialog closed -> Cancel
                                newPoints.clear();
                            }
                        }

                        if (!newPoints.isEmpty()) {
                            // Snapshot before bulk-adding autotrace points so Undo works
                            pushUndo(activeDataset);
                            activeDataset.getPoints().addAll(newPoints);
                        }

                        // Sort points by X coordinate
                        activeDataset.getPoints().sort((p1, p2) -> {
                            int c = Double.compare(p1.x, p2.x);
                            return c == 0 ? Double.compare(p1.y, p2.y) : c;
                        });

                        refreshTable();
                        imageCanvas.repaint();
                        JOptionPane.showMessageDialog(GraphitizerApp.this,
                                "Traced " + results.size() + " points along the line!");
                    } else {
                        JOptionPane.showMessageDialog(GraphitizerApp.this, "Could not trace a line from this point.");
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(GraphitizerApp.this, "Error tracing line: " + ex.getMessage());
                } finally {
                    btnTraceLine.setEnabled(true);
                    pointsSlider.setEnabled(true);
                }
            }
        };
        worker.execute();
    }

    private void styleButton(JButton btn) {
        btn.setFocusPainted(false);
        btn.setMargin(new java.awt.Insets(4, 10, 4, 10));
        btn.setFont(new java.awt.Font("SansSerif", java.awt.Font.BOLD, 12));
        btn.setBackground(new Color(210, 230, 255));
        btn.setOpaque(true);
        btn.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }
        SwingUtilities.invokeLater(() -> {
            new GraphitizerApp().setVisible(true);
        });
    }
}
