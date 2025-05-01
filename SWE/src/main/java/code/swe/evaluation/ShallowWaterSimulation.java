package code.swe.evaluation;
import javafx.application.Application;
import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.scene.control.*;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.animation.AnimationTimer;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import java.util.ArrayList;
import java.io.FileWriter;
import java.io.IOException;
public class ShallowWaterSimulation extends Application {
    // Simulation parameters
    private int GRID_SIZE = 100;
    private double DX = 1000.0; // meters
    private double DT = 0.05; // seconds
    private final double G = 9.81; // gravity
    private double F = 1e-4; // Coriolis parameter
    private double K = 0.01; // Friction coefficient
    private double WIND = 0.0; // Wind stress amplitude (N/m²)
    private final int CANVAS_SIZE = 600;
    private boolean tideActive = false;
    private long tideStartTime = 0;

    // Fields
    private double[][] h; // water height
    private double[][] u; // x-velocity
    private double[][] v; // y-velocity
    private double[][] b; // bathymetry
    private double[][] windStress; // New: Variable wind stress
    private boolean running = false;
    private final ArrayList<double[]> particles = new ArrayList<>();
    private ArrayList<double[][][]> history = new ArrayList<>();
    private boolean replayMode = false;
    private int replayFrame = 0;

    // Components of User Interface
    private Canvas canvas;
    private Slider coriolisSlider, frictionSlider, windSlider, gridSlider, dtSlider;
    private CheckBox coriolisCheck, frictionCheck, windCheck, particleCheck;
    private Button startButton, resetButton, tsunamiButton, tideButton;
    private ComboBox<String> visualizationMode, colormapMode;
    private Text legendText;
    private LineChart<Number, Number> heightChart;
    private XYChart.Series<Number, Number> heightSeries;
    private TabPane tabPane;
    private BorderPane root;
    private CheckBox velocityVectorCheck;
    private Slider contourSlider;
    private Slider replaySlider;
    private Button replayButton;
    private CheckBox nonlinearAdvectionCheck; // Toggle nonlinear advection
    private CheckBox wettingDryingCheck; // Toggle wetting and drying
    private CheckBox variableWindCheck; // Toggle variable wind
    private Slider cycloneSlider; // Control cyclone intensity
    private ComboBox<String> tideMode; // Select tidal constituents

    @Override
    public void start(Stage primaryStage) {
        initializeFields();
        root = new BorderPane();
        canvas = new Canvas(CANVAS_SIZE, CANVAS_SIZE);
        tabPane = createControls();
        root.setCenter(canvas);
        root.setRight(tabPane);

        // Mouse interaction for bathymetry editing
        canvas.setOnMouseDragged(event -> {
            if (tabPane.getSelectionModel().getSelectedItem().getText().equals("Bathymetry")) {
                int i = (int) (event.getX() * GRID_SIZE / CANVAS_SIZE);
                int j = (int) (event.getY() * GRID_SIZE / CANVAS_SIZE);
                if (i >= 0 && i < GRID_SIZE && j >= 0 && j < GRID_SIZE) {
                    editBathymetry(i, j);
                }
            }
        });

        // Scene and stage
        Scene scene = new Scene(root, 1200, 900);
        primaryStage.setTitle("Coastal Ocean Dynamics Simulator");
        primaryStage.setScene(scene);
        primaryStage.show();

        // Animation loop
        new AnimationTimer() {
            private long lastUpdate = 0;
            @Override
            public void handle(long now) {
                if (running && !replayMode && (now - lastUpdate) >= 16_000_000) { // ~60 FPS
                    updateSimulation();
                    updateParticles();
                    drawSimulation();
                    updateChart();
                    lastUpdate = now;
                }
            }
        }.start();
    }

    private void initializeFields() {
        h = new double[GRID_SIZE][GRID_SIZE];
        u = new double[GRID_SIZE][GRID_SIZE];
        v = new double[GRID_SIZE][GRID_SIZE];
        b = new double[GRID_SIZE][GRID_SIZE];
        windStress = new double[GRID_SIZE][GRID_SIZE];
        // Initialize with coastal bathymetry
        for (int i = 0; i < GRID_SIZE; i++) {
            for (int j = 0; j < GRID_SIZE; j++) {
                h[i][j] = 10.0;
                u[i][j] = 0.0;
                v[i][j] = 0.0;
                b[i][j] = 8.0 * (1.0 - (double)i / GRID_SIZE);
                windStress[i][j] = 0.0;
            }
        }
        particles.clear();
        addParticles();
        history.clear();
    }

    private void addParticles() {
        for (int i = 0; i < 100; i++) {
            double x = Math.random() * GRID_SIZE;
            double y = Math.random() * GRID_SIZE;
            particles.add(new double[]{x, y});
        }
    }

    private void editBathymetry(int i, int j) {
        for (int di = -2; di <= 2; di++) {
            for (int dj = -2; dj <= 2; dj++) {
                int ni = i + di;
                int nj = j + dj;
                if (ni >= 0 && ni < GRID_SIZE && nj >= 0 && nj < GRID_SIZE) {
                    b[ni][nj] = Math.max(0, b[ni][nj] + 0.5);
                }
            }
        }
        drawSimulation();
    }

    private TabPane createControls() {
        TabPane tabPane = new TabPane();

        // Simulation Tab
        VBox simControls = new VBox(10);
        simControls.setPadding(new Insets(10));

        startButton = new Button("Start");
        startButton.setOnAction(e -> {
            running = !running;
            startButton.setText(running ? "Stop" : "Start");
            replayMode = false;
            replayButton.setText("Start Replay");
        });

        resetButton = new Button("Reset");
        resetButton.setOnAction(e -> {
            running = false;
            tideActive = false;
            startButton.setText("Start");
            replayMode = false;
            replayButton.setText("Start Replay");
            initializeFields();
            heightSeries.getData().clear();
            drawSimulation();
        });

        tsunamiButton = new Button("Simulate Tsunami");
        tsunamiButton.setOnAction(e -> {
            triggerTsunami();
            running = true;
            startButton.setText("Stop");
            replayMode = false;
            replayButton.setText("Start Replay");
            drawSimulation();
            System.out.println("Tsunami triggered");
        });

        tideButton = new Button("Simulate Tide");
        tideButton.setOnAction(e -> {
            tideActive = true;
            tideStartTime = System.nanoTime();
            running = true;
            startButton.setText("Stop");
            replayMode = false;
            replayButton.setText("Start Replay");
            drawSimulation();
            System.out.println("Tide simulation started");
        });

        replayButton = new Button("Start Replay");
        replayButton.setOnAction(e -> {
            replayMode = !replayMode;
            replayButton.setText(replayMode ? "Stop Replay" : "Start Replay");
            running = !replayMode;
            startButton.setText(running ? "Stop" : "Start");
            if (replayMode) {
                replayFrame = 0;
                replaySlider.setValue(0);
            }
            drawSimulation();
        });

        replaySlider = new Slider(0, 1000, 0);
        replaySlider.setShowTickLabels(true);
        replaySlider.setMajorTickUnit(100);
        replaySlider.valueProperty().addListener((obs, old, val) -> {
            if (replayMode) {
                replayFrame = val.intValue();
                if (replayFrame < history.size()) {
                    drawSimulation();
                }
            }
        });

        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER_LEFT);
        buttonBox.getChildren().addAll(startButton, resetButton, tsunamiButton, tideButton, replayButton);

        simControls.getChildren().addAll(
                new Label("Simulation Controls"),
                new Text("Model tsunamis, tides, and storm surges"),
                buttonBox,
                new Label("Replay Frame:"),
                replaySlider
        );

        // Parameters Tab
        VBox paramControls = new VBox(10);
        paramControls.setPadding(new Insets(10));

        // Coriolis
        coriolisCheck = new CheckBox("Enable Coriolis Force");
        coriolisSlider = new Slider(0, 2e-4, 1e-4);
        coriolisSlider.setShowTickLabels(true);
        Label coriolisLabel = new Label("Coriolis: 1e-4");
        coriolisSlider.valueProperty().addListener((obs, old, val) -> {
            F = val.doubleValue();
            coriolisLabel.setText(String.format("Coriolis: %.2e", F));
        });

        // Friction
        frictionCheck = new CheckBox("Enable Bottom Friction");
        frictionSlider = new Slider(0, 0.1, 0.01);
        frictionSlider.setShowTickLabels(true);
        Label frictionLabel = new Label("Friction: 0.01");
        frictionSlider.valueProperty().addListener((obs, old, val) -> {
            K = val.doubleValue();
            frictionLabel.setText(String.format("Friction: %.3f", K));
        });

        // Wind
        windCheck = new CheckBox("Enable Uniform Wind Stress");
        windSlider = new Slider(0, 1.0, 0.0);
        windSlider.setShowTickLabels(true);
        Label windLabel = new Label("Wind Stress: 0.0 N/m²");
        windSlider.valueProperty().addListener((obs, old, val) -> {
            WIND = val.doubleValue();
            windLabel.setText(String.format("Wind Stress: %.2f N/m²", WIND));
        });

        // Variable wind (cyclone)
        variableWindCheck = new CheckBox("Enable Cyclone Wind");
        cycloneSlider = new Slider(0, 2.0, 0.0);
        cycloneSlider.setShowTickLabels(true);
        Label cycloneLabel = new Label("Cyclone Intensity: 0.0 N/m²");
        cycloneSlider.valueProperty().addListener((obs, old, val) -> {
            cycloneLabel.setText(String.format("Cyclone Intensity: %.2f N/m²", val.doubleValue()));
        });

        // Grid and Time Step
        gridSlider = new Slider(50, 150, 100);
        gridSlider.setShowTickLabels(true);
        gridSlider.setSnapToTicks(true);
        Label gridLabel = new Label("Grid Size: 100");
        gridSlider.valueProperty().addListener((obs, old, val) -> {
            GRID_SIZE = val.intValue();
            DX = 100000.0 / GRID_SIZE;
            gridLabel.setText("Grid Size: " + GRID_SIZE);
            initializeFields();
            drawSimulation();
        });

        dtSlider = new Slider(0.01, 0.2, 0.05);
        dtSlider.setShowTickLabels(true);
        Label dtLabel = new Label("Time Step: 0.05s");
        dtSlider.valueProperty().addListener((obs, old, val) -> {
            DT = val.doubleValue();
            dtLabel.setText(String.format("Time Step: %.2fs", DT));
        });

        // Nonlinear advection
        nonlinearAdvectionCheck = new CheckBox("Enable Nonlinear Advection");

        // Wetting and drying
        wettingDryingCheck = new CheckBox("Enable Wetting and Drying");

        // Tidal constituents
        tideMode = new ComboBox<>();
        tideMode.getItems().addAll("Single Tide", "M2 + S2 Tides");
        tideMode.setValue("Single Tide");

        paramControls.getChildren().addAll(
                new Label("Physical Parameters"),
                coriolisCheck, coriolisSlider, coriolisLabel,
                frictionCheck, frictionSlider, frictionLabel,
                windCheck, windSlider, windLabel,
                variableWindCheck, cycloneSlider, cycloneLabel,
                gridSlider, gridLabel, dtSlider, dtLabel,
                nonlinearAdvectionCheck, wettingDryingCheck, tideMode
        );

        // Visualization Tab
        VBox visControls = new VBox(10);
        visControls.setPadding(new Insets(10));

        visualizationMode = new ComboBox<>();
        visualizationMode.getItems().addAll("Water Height", "Velocity Magnitude", "Bathymetry");
        visualizationMode.setValue("Water Height");
        visualizationMode.setOnAction(e -> drawSimulation());

        colormapMode = new ComboBox<>();
        colormapMode.getItems().addAll("Blue-Red", "Viridis", "Grayscale");
        colormapMode.setValue("Blue-Red");
        colormapMode.setOnAction(e -> drawSimulation());

        particleCheck = new CheckBox("Show Tracer Particles");

        velocityVectorCheck = new CheckBox("Show Velocity Vectors");
        velocityVectorCheck.setOnAction(e -> drawSimulation());

        contourSlider = new Slider(5.0, 15.0, 10.0);
        contourSlider.setShowTickLabels(true);
        contourSlider.setMajorTickUnit(2.5);
        Label contourLabel = new Label("Contour Level: 10.0");
        contourSlider.valueProperty().addListener((obs, old, val) -> {
            contourLabel.setText(String.format("Contour Level: %.1f", val.doubleValue()));
            drawSimulation();
        });

        legendText = new Text("Legend: Blue (low) to Red (high)");

        Button exportButton = new Button("Export Data");
        exportButton.setOnAction(e -> exportData());

        visControls.getChildren().addAll(
                new Label("Visualization"),
                new Text("Click/drag on canvas to edit bathymetry"),
                visualizationMode, colormapMode, particleCheck,
                velocityVectorCheck, contourSlider, contourLabel, legendText, exportButton
        );

        // Help Tab
        VBox helpControls = new VBox(10);
        helpControls.setPadding(new Insets(10));
        helpControls.getChildren().addAll(
                new Label("Help"),
                new Text("This simulator models coastal ocean dynamics using the Shallow Water Equations (SWE). Use it to study:\n" +
                        "- Tsunamis: Click 'Simulate Tsunami' for a wave pulse.\n" +
                        "- Tides: Click 'Simulate Tide' for periodic forcing (select M2+S2 for realistic tides).\n" +
                        "- Storm Surges: Enable uniform or cyclone wind stress.\n" +
                        "Adjust parameters, edit bathymetry, and visualize results.\n" +
                        "- Nonlinear Advection: Toggle for accurate wave steepening.\n" +
                        "- Wetting and Drying: Toggle for coastal inundation.\n" +
                        "- Cyclone Wind: Enable for spatially varying wind.\n" +
                        "- Velocity Vectors: Toggle to see flow directions.\n" +
                        "- Contour Lines: Adjust slider to highlight water height levels.\n" +
                        "- Replay: Use slider to scrub through simulation history.")
        );

        // Create tabs
        tabPane.getTabs().addAll(
                new Tab("Simulation", simControls),
                new Tab("Parameters", paramControls),
                new Tab("Visualization", visControls),
                new Tab("Help", helpControls)
        );
        tabPane.getTabs().forEach(tab -> tab.setClosable(false));

        // Chart setup
        NumberAxis xAxis = new NumberAxis(0, 100, 10);
        NumberAxis yAxis = new NumberAxis();
        xAxis.setLabel("Time (s)");
        yAxis.setLabel("Water Height (m)");
        heightChart = new LineChart<>(xAxis, yAxis);
        heightChart.setPrefHeight(200);
        heightSeries = new XYChart.Series<>();
        heightChart.getData().add(heightSeries);
        root.setBottom(heightChart);

        return tabPane;
    }

    private void triggerTsunami() {
        for (int i = 0; i < GRID_SIZE; i++) {
            for (int j = 0; j < 20; j++) {
                h[i][j] += 10.0 * Math.exp(-Math.pow(j - 10, 2) / 16.0);
            }
        }
    }

    private void updateSimulation() {
        double[][] hNew = new double[GRID_SIZE][GRID_SIZE];
        double[][] uNew = new double[GRID_SIZE][GRID_SIZE];
        double[][] vNew = new double[GRID_SIZE][GRID_SIZE];

        double f = coriolisCheck.isSelected() ? F : 0;
        double k = frictionCheck.isSelected() ? K : 0;
        double wind = windCheck.isSelected() ? WIND : 0;

        // Update variable wind stress (cyclone)
        double cycloneIntensity = variableWindCheck.isSelected() ? cycloneSlider.getValue() : 0;
        if (variableWindCheck.isSelected()) {
            double time = System.nanoTime() / 1e9;
            for (int i = 0; i < GRID_SIZE; i++) {
                for (int j = 0; j < GRID_SIZE; j++) {
                    double x = (i - GRID_SIZE / 2.0) * DX / 1000; // km
                    double y = (j - GRID_SIZE / 2.0) * DX / 1000;
                    double r = Math.sqrt(x * x + y * y);
                    double theta = Math.atan2(y, x);
                    double windSpeed = cycloneIntensity * Math.exp(-r / 20) * Math.cos(time / 3600);
                    windStress[i][j] = windSpeed * Math.cos(theta + Math.PI / 2); // Tangential wind
                }
            }
        } else {
            for (int i = 0; i < GRID_SIZE; i++) {
                for (int j = 0; j < GRID_SIZE; j++) {
                    windStress[i][j] = 0.0;
                }
            }
        }

        // Apply tidal forcing with constituents
        if (tideActive) {
            double time = (System.nanoTime() - tideStartTime) / 1e9;
            double tideHeight = 0.0;
            if (tideMode.getValue().equals("Single Tide")) {
                tideHeight = 5.0 * Math.sin(2 * Math.PI * time / 3600);
            } else { // M2 + S2
                double M2 = 5.0 * Math.sin(2 * Math.PI * time / 44712); // M2: 12.42h
                double S2 = 2.0 * Math.sin(2 * Math.PI * time / 43200); // S2: 12h
                tideHeight = M2 + S2;
            }
            for (int i = 0; i < GRID_SIZE; i++) {
                h[i][0] = 10.0 + tideHeight;
            }
        }

        // Semi-explicit finite difference
        for (int i = 1; i < GRID_SIZE - 1; i++) {
            for (int j = 1; j < GRID_SIZE - 1; j++) {
                // Continuity equation
                double hu_x = ((h[i+1][j] + h[i][j]) * u[i+1][j] - (h[i-1][j] + h[i][j]) * u[i-1][j]) / (4 * DX);
                double hv_y = ((h[i][j+1] + h[i][j]) * v[i][j+1] - (h[i][j-1] + h[i][j]) * v[i][j-1]) / (4 * DX);
                hNew[i][j] = h[i][j] - DT * (hu_x + hv_y);

                // Momentum equation (x)
                double u_adv_x, u_adv_y;
                if (nonlinearAdvectionCheck.isSelected()) {
                    // Upwind advection for u
                    u_adv_x = u[i][j] >= 0 ? u[i][j] * (u[i][j] - u[i-1][j]) / DX : u[i][j] * (u[i+1][j] - u[i][j]) / DX;
                    u_adv_y = v[i][j] >= 0 ? v[i][j] * (u[i][j] - u[i][j-1]) / DX : v[i][j] * (u[i][j+1] - u[i][j]) / DX;
                } else {
                    u_adv_x = u[i][j] * (u[i+1][j] - u[i-1][j]) / (2 * DX);
                    u_adv_y = v[i][j] * (u[i][j+1] - u[i][j-1]) / (2 * DX);
                }
                double h_grad_x = G * (h[i+1][j] - h[i-1][j]) / (2 * DX);
                double b_grad_x = G * (b[i+1][j] - b[i-1][j]) / (2 * DX);
                double windForce = wind / (h[i][j] + 1e-6) + windStress[i][j] / (h[i][j] + 1e-6);
                uNew[i][j] = u[i][j] - DT * (u_adv_x + u_adv_y + h_grad_x + b_grad_x - windForce) +
                        DT * f * v[i][j] - DT * k * u[i][j];

                // Momentum equation (y)
                double v_adv_x, v_adv_y;
                if (nonlinearAdvectionCheck.isSelected()) {
                    // Upwind advection for v
                    v_adv_x = u[i][j] >= 0 ? u[i][j] * (v[i][j] - v[i-1][j]) / DX : u[i][j] * (v[i+1][j] - v[i][j]) / DX;
                    v_adv_y = v[i][j] >= 0 ? v[i][j] * (v[i][j] - v[i][j-1]) / DX : v[i][j] * (v[i][j+1] - v[i][j]) / DX;
                } else {
                    v_adv_x = u[i][j] * (v[i+1][j] - v[i-1][j]) / (2 * DX);
                    v_adv_y = v[i][j] * (v[i][j+1] - v[i][j-1]) / (2 * DX);
                }
                double h_grad_y = G * (h[i][j+1] - h[i][j-1]) / (2 * DX);
                double b_grad_y = G * (b[i][j+1] - b[i-1][j]) / (2 * DX);
                vNew[i][j] = v[i][j] - DT * (v_adv_x + v_adv_y + h_grad_y + b_grad_y) -
                        DT * f * u[i][j] - DT * k * v[i][j];
            }
        }

        // New: Wetting and drying
        if (wettingDryingCheck.isSelected()) {
            for (int i = 1; i < GRID_SIZE - 1; i++) {
                for (int j = 1; j < GRID_SIZE - 1; j++) {
                    if (hNew[i][j] + b[i][j] < 0) { // Dry cell
                        hNew[i][j] = -b[i][j];
                        uNew[i][j] = 0;
                        vNew[i][j] = 0;
                    }
                }
            }
        }

        // Update fields
        h = hNew;
        u = uNew;
        v = vNew;

        // Reflective boundaries
        for (int i = 0; i < GRID_SIZE; i++) {
            h[i][0] = h[i][1];
            h[i][GRID_SIZE-1] = h[i][GRID_SIZE-2];
            u[i][0] = -u[i][1];
            u[i][GRID_SIZE-1] = -u[i][GRID_SIZE-2];
            v[i][0] = v[i][1];
            v[i][GRID_SIZE-1] = v[i][GRID_SIZE-2];
        }
        for (int j = 0; j < GRID_SIZE; j++) {
            h[0][j] = h[1][j];
            h[GRID_SIZE-1][j] = h[GRID_SIZE-2][j];
            u[0][j] = u[1][j];
            u[GRID_SIZE-1][j] = u[GRID_SIZE-2][j];
            v[0][j] = -v[1][j];
            v[GRID_SIZE-1][j] = -v[GRID_SIZE-2][j];
        }

        // Store simulation state for replay
        if (!replayMode && history.size() < 1000) {
            double[][] hCopy = new double[GRID_SIZE][GRID_SIZE];
            double[][] uCopy = new double[GRID_SIZE][GRID_SIZE];
            double[][] vCopy = new double[GRID_SIZE][GRID_SIZE];
            for (int i = 0; i < GRID_SIZE; i++) {
                for (int j = 0; j < GRID_SIZE; j++) {
                    hCopy[i][j] = h[i][j];
                    uCopy[i][j] = u[i][j];
                    vCopy[i][j] = v[i][j];
                }
            }
            history.add(new double[][][]{hCopy, uCopy, vCopy});
            replaySlider.setMax(Math.max(0, history.size() - 1));
        }
    }

    private void updateParticles() {
        if (!replayMode) {
            for (double[] p : particles) {
                int i = (int) p[0];
                int j = (int) p[1];
                if (i >= 0 && i < GRID_SIZE - 1 && j >= 0 && j < GRID_SIZE - 1) {
                    p[0] += DT * u[i][j] * GRID_SIZE / DX;
                    p[1] += DT * v[i][j] * GRID_SIZE / DX;
                    if (p[0] < 0 || p[0] >= GRID_SIZE || p[1] < 0 || p[1] >= GRID_SIZE) {
                        p[0] = Math.random() * GRID_SIZE;
                        p[1] = Math.random() * GRID_SIZE;
                    }
                }
            }
        }
    }

    private void updateChart() {
        if (!replayMode) {
            double time = heightSeries.getData().size() * DT;
            double height = h[GRID_SIZE / 2][GRID_SIZE / 2];
            heightSeries.getData().add(new XYChart.Data<>(time, height));
            if (heightSeries.getData().size() > 1000) {
                heightSeries.getData().remove(0);
            }
        }
    }

    private void drawSimulation() {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        String mode = visualizationMode.getValue();
        String colormap = colormapMode.getValue();

        // Use history data in replay mode
        double[][] hDraw = h;
        double[][] uDraw = u;
        double[][] vDraw = v;
        if (replayMode && replayFrame < history.size()) {
            hDraw = history.get(replayFrame)[0];
            uDraw = history.get(replayFrame)[1];
            vDraw = history.get(replayFrame)[2];
        }

        // Compute min/max
        double minVal = Double.MAX_VALUE;
        double maxVal = -Double.MAX_VALUE;
        for (int i = 0; i < GRID_SIZE; i++) {
            for (int j = 0; j < GRID_SIZE; j++) {
                double value = mode.equals("Velocity Magnitude") ?
                        Math.sqrt(uDraw[i][j] * uDraw[i][j] + vDraw[i][j] * vDraw[i][j]) :
                        mode.equals("Bathymetry") ? b[i][j] : hDraw[i][j];
                minVal = Math.min(minVal, value);
                maxVal = Math.max(maxVal, value);
            }
        }
        minVal = Math.min(minVal, 5.0);
        maxVal = Math.max(maxVal, 15.0);

        // Draw grid
        for (int i = 0; i < GRID_SIZE; i++) {
            for (int j = 0; j < GRID_SIZE; j++) {
                double value = mode.equals("Velocity Magnitude") ?
                        Math.sqrt(uDraw[i][j] * uDraw[i][j] + vDraw[i][j] * vDraw[i][j]) :
                        mode.equals("Bathymetry") ? b[i][j] : hDraw[i][j];
                double normalized = (maxVal == minVal) ? 0.5 : (value - minVal) / (maxVal - minVal);
                normalized = Math.max(0, Math.min(1, normalized));
                switch (colormap) {
                    case "Viridis":
                        gc.setFill(Color.rgb(
                                (int) (255 * (0.267 * (1 - normalized) + 0.959 * normalized)),
                                (int) (255 * (0.329 * (1 - normalized) + 0.617 * normalized)),
                                (int) (255 * (0.676 * (1 - normalized) + 0.280 * normalized))
                        ));
                        break;
                    case "Grayscale":
                        gc.setFill(Color.gray(normalized));
                        break;
                    case "Blue-Red":
                    default:
                        gc.setFill(Color.hsb(240 * (1 - normalized), 1.0, 1.0));
                        break;
                }
                gc.fillRect(i * CANVAS_SIZE / GRID_SIZE, j * CANVAS_SIZE / GRID_SIZE,
                        CANVAS_SIZE / GRID_SIZE + 1, CANVAS_SIZE / GRID_SIZE + 1);
            }
        }

        // Draw velocity vectors
        if (velocityVectorCheck.isSelected()) {
            gc.setStroke(Color.BLACK);
            gc.setLineWidth(1.0);
            for (int i = 0; i < GRID_SIZE; i += 5) {
                for (int j = 0; j < GRID_SIZE; j += 5) {
                    double px = i * CANVAS_SIZE / GRID_SIZE;
                    double py = j * CANVAS_SIZE / GRID_SIZE;
                    double vx = uDraw[i][j] * 50;
                    double vy = vDraw[i][j] * 50;
                    double mag = Math.sqrt(vx * vx + vy * vy);
                    if (mag > 0) {
                        vx = vx / mag * 10;
                        vy = vy / mag * 10;
                        gc.strokeLine(px, py, px + vx, py + vy);
                        double angle = Math.atan2(vy, vx);
                        gc.strokeLine(px + vx, py + vy, px + vx - 3 * Math.cos(angle + Math.PI / 6), py + vy - 3 * Math.sin(angle + Math.PI / 6));
                        gc.strokeLine(px + vx, py + vy, px + vx - 3 * Math.cos(angle - Math.PI / 6), py + vy - 3 * Math.sin(angle - Math.PI / 6));
                    }
                }
            }
        }

        // Draw contour lines
        double contourLevel = contourSlider.getValue();
        if (mode.equals("Water Height")) {
            gc.setStroke(Color.BLACK);
            gc.setLineWidth(0.5);
            for (int i = 0; i < GRID_SIZE - 1; i++) {
                for (int j = 0; j < GRID_SIZE - 1; j++) {
                    double h1 = hDraw[i][j], h2 = hDraw[i+1][j], h3 = hDraw[i][j+1], h4 = hDraw[i+1][j+1];
                    if ((h1 <= contourLevel && h2 > contourLevel) || (h1 > contourLevel && h2 <= contourLevel)) {
                        gc.strokeLine((i + 0.5) * CANVAS_SIZE / GRID_SIZE, j * CANVAS_SIZE / GRID_SIZE,
                                (i + 0.5) * CANVAS_SIZE / GRID_SIZE, (j + 1) * CANVAS_SIZE / GRID_SIZE);
                    }
                    if ((h1 <= contourLevel && h3 > contourLevel) || (h1 > contourLevel && h3 <= contourLevel)) {
                        gc.strokeLine(i * CANVAS_SIZE / GRID_SIZE, (j + 0.5) * CANVAS_SIZE / GRID_SIZE,
                                (i + 1) * CANVAS_SIZE / GRID_SIZE, (j + 0.5) * CANVAS_SIZE / GRID_SIZE);
                    }
                }
            }
        }

        // Draw particles
        if (particleCheck.isSelected() && !replayMode) {
            gc.setFill(Color.WHITE);
            for (double[] p : particles) {
                gc.fillOval(p[0] * CANVAS_SIZE / GRID_SIZE - 2, p[1] * CANVAS_SIZE / GRID_SIZE - 2, 4, 4);
            }
        }

        // Update legend
        legendText.setText(String.format("Legend: %.2f to %.2f", minVal, maxVal));
    }

    private void exportData() {
        try (FileWriter writer = new FileWriter("swe_data.csv")) {
            writer.write("x,y,water_height,velocity_magnitude,bathymetry\n");
            for (int i = 0; i < GRID_SIZE; i++) {
                for (int j = 0; j < GRID_SIZE; j++) {
                    double vel = Math.sqrt(u[i][j] * u[i][j] + v[i][j] * v[i][j]);
                    writer.write(String.format("%d,%d,%.3f,%.3f,%.3f\n", i, j, h[i][j], vel, b[i][j]));
                }
            }
        } catch (IOException e) {
            System.err.println("Error exporting data: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}