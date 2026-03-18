# Graphitizer 1.1

## Changes in Version 1.1
*   **Sequential Multi-Point Keystone:** Easily pick tracking bounds by sequentially clicking the four canvas corners before warping (fully Undo-integrated).
*   **Undo button:** Allows undoing the previous addition of points.
*   **Temporal Redo Feature:** `Ctrl-Y` seamlessly reverses the effects of `Undo`, dynamically swapping snapshots of the current view stack on the fly.
*   **Selection-Aware UX:** Have a subset of plot points selected? Operations like `Clear`, `Copy Data`, and `Save Data As` now act entirely localized on just that subset.
*   **Global Eraser Bindings:** Hitting `Backspace` or `Delete` directly snips the row from the data table natively. `Ctrl-A` efficiently toggles selecting or unselecting all rows.
---


# Graphitizer 1.0 - Initial Release

Welcome to the very first stable release of **Graphitizer**!

Graphitizer is a powerful, lightweight Java standalone desktop utility designed specifically to effortlessly extract numerical data from images of printed graphs.

## Major Features in Version 1.0

*   **Homography Keystone Correction:** Graph picture taken at an angle? Graphitizer will compute an 8x8 Linear System perspective warp to flatten the plot area perfectly square before you trace.
*   **Automated Line-Tracing:** Click a single clean curve, and the flood-fill sub-pixel edge-tracking algorithm automatically sweeps the entire line to extract data mathematically.
*   **Similar Color Snapping:** Hunting down scatter plots? Click a single data dot, type in your hue tolerance, and click "Find Similar" to automatically snap up all matching plot points across the image instantly.
*   **Smart Multi-Curve Calibration:** Manage multiple datasets with distinct stylings and unique domain scales seamlessly layered over the exact same source image.
*   **Instant Exporting:** Native Data Table integration allows you to instantly sort plot coordinates and export them straight to `.csv` for Excel!

## Installation & Execution Notes

This release contains the **Standalone Windows Application**. It does **not** require Java, Maven, or any other third-party dependencies to be installed on your computer.

1.  Download the attached `Graphitizer_v1.0_Windows.zip` file under **Assets** below.
2.  Extract the ZIP folder to your preferred location (e.g. your Desktop or Documents).
3.  Open the newly extracted folder and double-click `Graphitizer 1.0.exe` to instantly launch the application!

*Note: As this is an un-signed open-source utility, Windows SmartScreen may present a "Windows protected your PC" popup on the first run. If so, simply click "**More info**" and then "**Run anyway**".*

Enjoy digitizing!
