package no.s11.wpsld.ui;

import java.io.FileWriter;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;
import jakarta.json.JsonWriter;
import jakarta.json.stream.JsonGenerator;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class CreativeWorkEditor extends Application {

    private Map<String, Map<String, Control>> tabFields = new HashMap<>();

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("CreativeWork Editor");

        TabPane tabPane = new TabPane();

        try {
            JsonArray schemaGraph = loadSchemaHierarchy();
            createTabsFromSchema(tabPane, schemaGraph);
        } catch (Exception e) {
            e.printStackTrace();
        }

        Button saveButton = new Button("Save");
        saveButton.setOnAction(e -> saveToJsonLd());

        VBox vbox = new VBox(tabPane, saveButton);
        Scene scene = new Scene(vbox, 600, 400);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private JsonArray loadSchemaHierarchy() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("https://schema.org/version/latest/schemaorg-current-https.jsonld"))
            .build();

        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

        try (JsonReader reader = Json.createReader(response.body())) {
            JsonObject root = reader.readObject();
            return root.getJsonArray("@graph");
        }
    }

    private void createTabsFromSchema(TabPane tabPane, JsonArray schemaGraph) {
        for (JsonValue value : schemaGraph) {
            JsonObject obj = value.asJsonObject();
            if ("rdfs:Class".equals(obj.getString("@type", ""))) {
                String className = obj.getString("rdfs:label", obj.getString("@id", "Unnamed"));
                GridPane gridPane = new GridPane();
                gridPane.setVgap(10);
                gridPane.setHgap(10);

                Map<String, Control> fields = new HashMap<>();
                int row = 0;

                // Simulate some properties for demonstration
                List<String> properties = List.of("name", "about", "license");
                for (String prop : properties) {
                    Label label = new Label(prop + ":");
                    if ("license".equals(prop)) {
                        ComboBox<String> comboBox = new ComboBox<>();
                        comboBox.getItems().addAll("License A", "License B", "License C");
                        Button addButton = new Button("+");
                        addButton.setOnAction(e -> createNewEntity(prop));
                        gridPane.add(label, 0, row);
                        gridPane.add(comboBox, 1, row);
                        gridPane.add(addButton, 2, row);
                        fields.put(prop, comboBox);
                    } else {
                        TextField textField = new TextField();
                        gridPane.add(label, 0, row);
                        gridPane.add(textField, 1, row);
                        fields.put(prop, textField);
                    }
                    row++;
                }

                Tab tab = new Tab(className);
                tab.setContent(gridPane);
                tabFields.put(className, fields);
                tabPane.getTabs().add(tab);
            }
        }
    }

    private void createNewEntity(String propertyName) {
        System.out.println("Creating new entity for: " + propertyName);
    }

    private void saveToJsonLd() {
        JsonObjectBuilder rootBuilder = Json.createObjectBuilder();
        rootBuilder.add("@context", "https://schema.org");

        for (Map.Entry<String, Map<String, Control>> entry : tabFields.entrySet()) {
            String className = entry.getKey();
            JsonObjectBuilder classBuilder = Json.createObjectBuilder();

            for (Map.Entry<String, Control> fieldEntry : entry.getValue().entrySet()) {
                String prop = fieldEntry.getKey();
                Control control = fieldEntry.getValue();
                if (control instanceof TextField textField) {
                    classBuilder.add(prop, textField.getText());
                } else if (control instanceof ComboBox<?> comboBox) {
                    Object value = comboBox.getValue();
                    if (value != null) {
                        classBuilder.add(prop, value.toString());
                    }
                }
            }

            rootBuilder.add(className, classBuilder);
        }

        JsonObject jsonLd = rootBuilder.build();

        try (FileWriter writer = new FileWriter("creativework.jsonld");
             JsonWriter jsonWriter = Json.createWriterFactory(
                 Map.of(JsonGenerator.PRETTY_PRINTING, true)
             ).createWriter(writer)) {
            jsonWriter.writeObject(jsonLd);
            System.out.println("Saved JSON-LD to creativework.jsonld");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}