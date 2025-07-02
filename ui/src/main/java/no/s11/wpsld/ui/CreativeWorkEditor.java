package no.s11.wpsld.ui;


import java.io.FileWriter;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;
import jakarta.json.JsonWriter;
import jakarta.json.stream.JsonGenerator;

// CreativeWorkEditor.java
// JavaFX application that downloads schema.org JSON-LD, parses it using jakarta.json,
// maps properties to classes using domainIncludes, and builds a UI with tabs for each class.

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

    private final Map<String, Map<String, Control>> classFields = new HashMap<>();
    private final Map<String, List<String>> classProperties = new HashMap<>();
    private final Map<String, String> propertyRanges = new HashMap<>();

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Schema.org Editor");

        TabPane tabPane = new TabPane();

        try {
            JsonArray graph = loadSchemaGraph();
            extractClassPropertyMappings(graph);
            for (String className : classProperties.keySet()) {
                Tab tab = new Tab(className);
                GridPane grid = createClassForm(className);
                tab.setContent(grid);
                tabPane.getTabs().add(tab);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        Button saveButton = new Button("Save");
        saveButton.setOnAction(e -> saveToJsonLd());

        VBox root = new VBox(tabPane, saveButton);
        Scene scene = new Scene(root, 600, 400);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private JsonArray loadSchemaGraph() throws Exception {
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

    private void extractClassPropertyMappings(JsonArray graph) {
        Set<String> classNames = new HashSet<>();

        for (JsonValue item : graph) {
            JsonObject obj = item.asJsonObject();
            String id = obj.getString("@id", "");
            if (obj.containsKey("@type") && obj.get("@type").toString().contains("rdfs:Class")) {
                String className = id.replace("schema:", "");
                classNames.add(className);
                classProperties.putIfAbsent(className, new ArrayList<>());
            }
        }

        for (JsonValue item : graph) {
            JsonObject obj = item.asJsonObject();
            String id = obj.getString("@id", "");
            if (obj.containsKey("@type") && obj.get("@type").toString().contains("rdf:Property")) {
                String propName = id.replace("schema:", "");
                JsonArray domains = obj.getJsonArray("http://schema.org/domainIncludes");
                JsonArray ranges = obj.getJsonArray("http://schema.org/rangeIncludes");
                String rangeType = "Text";
                if (ranges != null && !ranges.isEmpty()) {
                    JsonObject rangeObj = ranges.get(0).asJsonObject();
                    rangeType = rangeObj.getString("@id", "Text").replace("schema:", "");
                }
                propertyRanges.put(propName, rangeType);
                if (domains != null) {
                    for (JsonValue domain : domains) {
                        JsonObject domainObj = domain.asJsonObject();
                        String className = domainObj.getString("@id", "").replace("schema:", "");
                        if (classNames.contains(className)) {
                            classProperties.computeIfAbsent(className, k -> new ArrayList<>()).add(propName);
                        }
                    }
                }
            }
        }
    }

    private GridPane createClassForm(String className) {
        GridPane grid = new GridPane();
        grid.setVgap(8);
        grid.setHgap(10);
        Map<String, Control> fields = new HashMap<>();
        List<String> props = classProperties.getOrDefault(className, new ArrayList<>());
        int row = 0;
        for (String prop : props) {
            Label label = new Label(prop + ":");
            String range = propertyRanges.getOrDefault(prop, "Text");
            if (range.equals("Text")) {
                TextField tf = new TextField();
                grid.add(label, 0, row);
                grid.add(tf, 1, row);
                fields.put(prop, tf);
            } else {
                ComboBox<String> cb = new ComboBox<>();
                cb.getItems().addAll("Entity1", "Entity2", "Entity3");
                Button addBtn = new Button("+");
                addBtn.setOnAction(e -> System.out.println("Add new " + range));
                grid.add(label, 0, row);
                grid.add(cb, 1, row);
                grid.add(addBtn, 2, row);
                fields.put(prop, cb);
            }
            row++;
        }
        classFields.put(className, fields);
        return grid;
    }

    private void saveToJsonLd() {
        JsonObjectBuilder rootBuilder = Json.createObjectBuilder();
        rootBuilder.add("@context", "https://schema.org");
        for (String className : classFields.keySet()) {
            JsonObjectBuilder classBuilder = Json.createObjectBuilder();
            Map<String, Control> fields = classFields.get(className);
            for (String prop : fields.keySet()) {
                Control ctrl = fields.get(prop);
                if (ctrl instanceof TextField tf) {
                    classBuilder.add(prop, tf.getText());
                } else if (ctrl instanceof ComboBox<?> cb) {
                    Object val = cb.getValue();
                    if (val != null) {
                        classBuilder.add(prop, val.toString());
                    }
                }
            }
            rootBuilder.add(className, classBuilder);
        }
        JsonObject jsonLd = rootBuilder.build();
        try (FileWriter fw = new FileWriter("creativework.jsonld");
             JsonWriter writer = Json.createWriterFactory(Map.of(JsonGenerator.PRETTY_PRINTING, true)).createWriter(fw)) {
            writer.writeObject(jsonLd);
            System.out.println("Saved to creativework.jsonld");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}