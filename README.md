# Graphitizer

Graphitizer is a powerful, lightweight Java desktop utility designed to effortlessly extract numerical data from images of printed graphs.

Whether you're digitizing historical data from a scanned paper, scraping figures from a PDF, or reverse-engineering an undigitized chart, Graphitizer dramatically speeds up manual data extraction with an intuitive suite of built-in tools.

## Features

* **Homography Keystone Correction**: If your graph picture was taken at an angle (skewed or warped), Graphitizer can instantly compute a perspective warp and flatten the plot area back into a perfect square, completely eliminating distortion before you trace!
* **Automated Line-Tracing Algorithm**: Simply click a point on a clean data curve, and Graphitizer's flood-fill edge-tracking algorithm will trace the entire curve and extract all the points automatically. Perfect for rapidly digitizing long, solid continuous graphs!
* **Similar Color Snapping**: Extracting data points from a scatter plot? Click one dot, and hit "Find Similar". Graphitizer will scan the image for any pixels sharing that color and automatically register them as points.
* **Smart Axis Calibration**: Define the physical coordinates of your X and Y axes simply by clicking their limits on the image. You only need to calibrate the axes once!
* **Multiple Concurrent Datasets**: Tracing a graph with several different curves? Create multiple Data Sets, each with bespoke styling (Red, Blue, Green dots). Newly spawned Data Sets can inherently "Inherit Calibration", copying your axis math directly from the primary curve.
* **Instant Exporting**: When you log a physical coordinate from the image, it is automatically cataloged in the built-in Data Table. Auto-sort your captured points by X or Y coordinate, and immediately copy the entire table into your clipboard, or save directly out to `.csv` for Excel!

## Installation & Execution

Graphitizer provides a standalone, native Windows executable that requires **zero installation** or third-party dependencies!

If you are on Windows, simply download the latest `Graphitizer_v1.exe` from the Releases page and double-click to run! The application includes its own integrated Java 25 runtime environment, so you do not even need Java installed on your computer.

### Compiling from Source
If you are running macOS or Linux, or wish to contribute to the engine, you can compile Graphitizer from source:
1. Ensure `JAVA_HOME` is bound to your JDK 25 directory, and that Apache Maven is exposed on your system path.
2. Clone the repository and navigate to the project root.
3. Build and execute using this command:

```bash
mvn clean compile exec:java
```

## System Requirements
- Wait-Free Execution: **Windows 10/11**
- Source Execution: **Windows, macOS, or Linux** (Requires Java 25 JDK & Maven)

## Authors & Licensing

**Graphitizer** was designed and engineered by **Willy Langeveld** in collaboration with Google DeepMind's **Gemini 3.1** (Antigravity).

Graphitizer is Free Software distributed under the **GNU General Public License (GPLv3)**. See the `LICENSE` file for more details. 

Copyright (C) 2026 Willem Langeveld.
