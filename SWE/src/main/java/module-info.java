module code.swe.evaluation.shallowwaterequations {
    requires javafx.controls;
    requires javafx.fxml;


    opens code.swe.evaluation to javafx.fxml;
    exports code.swe.evaluation;
}