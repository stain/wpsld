package no.s11.wpsld.ui;

import java.util.Locale;
import java.util.stream.Collectors;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

public class SearchController {
    
    @FXML
    private TextField searchField;
    @FXML
    private Button searchButton;
    @FXML
    private VBox dataContainer;
    @FXML
    private TableView tableView;
    
    @FXML
    private void initialize() {
        // search panel
        searchButton.setText("Search");
        searchButton.setOnAction(event -> loadData());
        searchButton.setStyle("-fx-background-color: #457ecd; -fx-text-fill: #ffffff;");

        initTable();
    }

    private void initTable() {        
        tableView = new TableView<>();
        TableColumn id = new TableColumn("ID");
        TableColumn name = new TableColumn("NAME");
        TableColumn employed = new TableColumn("EMPLOYED");
        tableView.getColumns().addAll(id, name, employed);
        dataContainer.getChildren().add(tableView);
    }

    ObservableList<Person> masterData = FXCollections.observableArrayList();
    ObservableList<Person> results = FXCollections.observableList(masterData);

    private void loadData() {
        String searchText = searchField.getText();
        Task<ObservableList<Person>> task = new Task<ObservableList<Person>>() {
            @Override
            protected ObservableList<Person> call() throws Exception {
                updateMessage("Loading data");
                return FXCollections.observableArrayList(masterData
                        .stream()
                        .filter(value -> value.getName().get().toLowerCase().contains(searchText))
                        .collect(Collectors.toList()));
            }
        };
        task.setOnSucceeded(event -> {
            results = task.getValue();
            tableView.setItems(FXCollections.observableList(results));
        });
        Thread th = new Thread(task);
        th.setDaemon(true);
        th.start();
    }    
}
