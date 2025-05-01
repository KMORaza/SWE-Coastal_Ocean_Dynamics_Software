#### Desktop-App zur Modellierung der Küstenozeandynamik und geschrieben in JavaFX (Desktop app which models coastal ocean dynamics and written in JavaFX)

* The appplication models coastal ocean dynamics such as tsunamis, tides, and storm surges utilizing Shallow Water Equations (SWE) and is written in JavaFX.
* Utilizes continuity equation and momentum equations and discretizes these equations using a semi-explicit finite difference method and includes like nonlinear advection, wetting and drying, and variable wind stress.
* Triggers a Gaussian-shaped wave pulse to model tsunami propagation for visualizing tsunami.
* Simulates tides with single or combined tidal constituents (M2 and S2) for tidal forcing.
* Models wind-driven surges with uniform or spatially varying (cyclone) wind stress.
* Models coastal inundation by setting dry cells where water height is below the bathymetry.
* Toggles Coriolis force and bottom friction for realistic dynamics are for Coriolis effect and friction.
* Displays water height, velocity magnitude, or bathymetry using customizable colormaps (Blue-Red, Viridis, Grayscale).
* Shows tracer particles to track fluid motion.
* Overlays velocity vectors to visualize flow direction.
* Draws contour lines for water height at user-specified levels.
* Includes a line chart to track water height at the grid center over time.
* Saves water height, velocity magnitude, and bathymetry data to a CSV file.
* Uses a semi-explicit finite difference scheme for solving the SWE.
* Implements upwind advection for nonlinear terms when enabled.
* Applies reflective boundary conditions to simulate closed boundaries.
* Supports variable grid sizes (50 to 150) and adjustable time steps (0.01 to 0.2 seconds).
* Updates the SWE fields using finite differences, handles tidal forcing, wind stress, and wetting/drying.
* Advances tracer particle positions based on velocity fields.
* Renders the simulation state on the canvas, including colormaps, vectors, contours, and particles.
