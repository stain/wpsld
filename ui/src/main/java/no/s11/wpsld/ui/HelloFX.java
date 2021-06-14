package no.s11.wpsld.ui;

import java.io.IOException;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.AnchorPane;
//import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public class HelloFX extends Application {

    @Override
    public void start(Stage primaryStage) {
        String javaVersion = System.getProperty("java.version");
        String javafxVersion = System.getProperty("javafx.version");
        Label l = new Label("Hello, JavaFX " + javafxVersion + ", running on Java " + javaVersion + ".");

        FXMLLoader loader = new FXMLLoader(
            HelloFX.class.getResource("/SearchController.fxml"));

        AnchorPane page;
        try {
            page = (AnchorPane) loader.load();
        } catch (IOException e) {
            System.err.println(e.getLocalizedMessage());
            System.exit(1);
            return;
        }
        Scene scene = new Scene(page);

        //Scene scene = new Scene(new StackPane(l), 640, 480);
        primaryStage.setScene(scene);
        primaryStage.setTitle("Wooo");
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch();
    }

}