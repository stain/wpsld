package no.s11.wpsld.ui;


import java.io.FileWriter;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

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

    private final Map<String, Map<String, Control>> classFields = new HashMap<>();
    private final Map<String, List<String>> classProperties = new HashMap<>();
    private final Map<String, String> propertyRanges = new HashMap<>();
    private final Map<String, List<String>> subclassMap = new HashMap<>();
    private final Set<String> targetClasses = new HashSet<>();

    @Override
    public void start(Stage primaryStage) throws Exception {
        String rootClass = "schema:MediaObject";

        JsonArray graph = loadSchemaGraph();
        buildSubclassMap(graph);
        findAllSubclasses(rootClass);
        mapPropertiesToClasses(graph);

        TabPane tabPane = new TabPane();
        for (String className : targetClasses) {
            String label = className.replace("schema:", "");
            Tab tab = new Tab(label);
            GridPane grid = createFormForClass(label);
            tab.setContent(grid);
            tabPane.getTabs().add(tab);
        }

        Button saveButton = new Button("Save");
        saveButton.setOnAction(e -> saveToJsonLd());

        VBox root = new VBox(tabPane, saveButton);
        Scene scene = new Scene(root, 600, 400);
        primaryStage.setScene(scene);
        primaryStage.setTitle("MediaObject Editor");
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

    private void buildSubclassMap(JsonArray graph) {
        for (JsonValue val : graph) {
            JsonObject obj = val.asJsonObject();
            if ("rdfs:Class".equals(obj.getString("@type", ""))) {
                String id = obj.getString("@id", "");
                JsonValue sub = obj.get("rdfs:subClassOf");
                if (sub != null) {
                    if (sub.getValueType() == JsonValue.ValueType.OBJECT) {
                        String parent = sub.asJsonObject().getString("@id", "");
                        subclassMap.computeIfAbsent(parent, k -> new ArrayList<>()).add(id);
                    } else if (sub.getValueType() == JsonValue.ValueType.ARRAY) {
                        for (JsonValue v : sub.asJsonArray()) {
                            String parent = v.asJsonObject().getString("@id", "");
                            subclassMap.computeIfAbsent(parent, k -> new ArrayList<>()).add(id);
                        }
                    }
                }
            }
        }
    }

    private void findAllSubclasses(String root) {
        Queue<String> queue = new LinkedList<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            if (targetClasses.add(current)) {
                List<String> children = subclassMap.getOrDefault(current, Collections.emptyList());
                queue.addAll(children);
            }
        }
    }

    private void mapPropertiesToClasses(JsonArray graph) {
        for (JsonValue val : graph) {
            JsonObject obj = val.asJsonObject();
            if ("rdf:Property".equals(obj.getString("@type", ""))) {
                String propId = obj.getString("@id", "");
                JsonValue domain = obj.get("http://schema.org/domainIncludes");
                if (domain != null) {
                    List<String> domains = new ArrayList<>();
                    if (domain.getValueType() == JsonValue.ValueType.ARRAY) {
                        for (JsonValue d : domain.asJsonArray()) {
                            domains.add(d.asJsonObject().getString("@id", ""));
                        }
                    } else {
                        domains.add(domain.asJsonObject().getString("@id", ""));
                    }
                    for (String cls : domains) {
                        if (targetClasses.contains(cls)) {
                            classProperties.computeIfAbsent(cls, k -> new ArrayList<>()).add(propId);
                        }
                    }
                }
                JsonValue range = obj.get("http://schema.org/rangeIncludes");
                if (range != null) {
                    if (range.getValueType() == JsonValue.ValueType.ARRAY) {
                        JsonValue first = range.asJsonArray().get(0);
                        propertyRanges.put(propId, first.asJsonObject().getString("@id", ""));
                    } else {
                        propertyRanges.put(propId, range.asJsonObject().getString("@id", ""));
                    }
                }
            }
        }
    }

    private GridPane createFormForClass(String className) {
        GridPane grid = new GridPane();
        grid.setVgap(10);
        grid.setHgap(10);
        String fullClass = "schema:" + className;
        List<String> props = classProperties.getOrDefault(fullClass, Collections.emptyList());
        Map<String, Control> fields = new HashMap<>();
        int row = 0;
        for (String prop : props) {
            String label = prop.replace("schema:", "");
            Label lbl = new Label(label + ":");
            String range = propertyRanges.getOrDefault(prop, "schema:Text");
            if ("schema:Text".equals(range)) {
                TextField tf = new TextField();
                grid.add(lbl, 0, row);
                grid.add(tf, 1, row);
                fields.put(label, tf);
            } else {
                ComboBox<String> cb = new ComboBox<>();
                cb.getItems().addAll("Entity1", "Entity2");
                Button plus = new Button("+");
                grid.add(lbl, 0, row);
                grid.add(cb, 1, row);
                grid.add(plus, 2, row);
                fields.put(label, cb);
            }
            row++;
        }
        classFields.put(className, fields);
        return grid;
    }

    private void saveToJsonLd() {
        JsonObjectBuilder root = Json.createObjectBuilder();
        root.add("@context", "https://schema.org");
        for (Map.Entry<String, Map<String, Control>> entry : classFields.entrySet()) {
            String className = entry.getKey();
            JsonObjectBuilder obj = Json.createObjectBuilder();
            for (Map.Entry<String, Control> field : entry.getValue().entrySet()) {
                String key = field.getKey();
                Control ctrl = field.getValue();
                if (ctrl instanceof TextField tf) {
                    obj.add(key, tf.getText());
                } else if (ctrl instanceof ComboBox<?> cb && cb.getValue() != null) {
                    obj.add(key, cb.getValue().toString());
                }
            }
            root.add(className, obj);
        }
        try (FileWriter fw = new FileWriter("creativework.jsonld");
             JsonWriter writer = Json.createWriterFactory(Map.of(JsonGenerator.PRETTY_PRINTING, true)).createWriter(fw)) {
            writer.writeObject(root.build());
            System.out.println("Saved to creativework.jsonld");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}