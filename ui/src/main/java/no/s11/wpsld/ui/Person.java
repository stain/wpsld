package no.s11.wpsld.ui;

import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;

public class Person {
    private SimpleIntegerProperty id;
    private SimpleStringProperty name;
    private SimpleBooleanProperty isEmployed;

    public SimpleIntegerProperty getId() {
        return id;
    }
    public SimpleBooleanProperty getIsEmployed() {
        return isEmployed;
    }
    public void setIsEmployed(SimpleBooleanProperty isEmployed) {
        this.isEmployed = isEmployed;
    }
    public SimpleStringProperty getName() {
        return name;
    }
    public void setName(SimpleStringProperty name) {
        this.name = name;
    }
    public void setId(SimpleIntegerProperty id) {
        this.id = id;
    }

}
