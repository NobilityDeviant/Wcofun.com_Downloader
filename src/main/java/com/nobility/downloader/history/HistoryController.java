package com.nobility.downloader.history;

import com.nobility.downloader.Main;
import com.nobility.downloader.Model;
import com.nobility.downloader.utils.Tools;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;

import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Comparator;
import java.util.Date;
import java.util.ResourceBundle;

public class HistoryController implements Initializable {

    @FXML private TableView<SeriesHistory> table;
    @FXML private TableColumn<SeriesHistory, String> name_column;
    @FXML private TableColumn<SeriesHistory, TextField> link_column;
    @FXML private TableColumn<SeriesHistory, Integer> episodes_column;
    @FXML private TableColumn<SeriesHistory, String> date_column;
    @FXML private TableColumn<SeriesHistory, MenuButton> actions_column;
    private final Model model;
    private final Stage stage;

    public HistoryController(Model model, Stage stage) {
        this.model = model;
        this.stage = stage;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        stage.widthProperty().addListener(((observable, oldValue, newValue) -> table.refresh()));

        //makes columns width the % of the last value of the stage
        name_column.setMaxWidth(1f * Integer.MAX_VALUE * 25);
        link_column.setMaxWidth(1f * Integer.MAX_VALUE * 40);
        episodes_column.setMaxWidth(1f * Integer.MAX_VALUE * 7);
        date_column.setMaxWidth(1f * Integer.MAX_VALUE * 20);
        actions_column.setMaxWidth(1f * Integer.MAX_VALUE * 8);

        date_column.setCellValueFactory(new PropertyValueFactory<>("dateAdded"));
        date_column.setSortType(TableColumn.SortType.DESCENDING);
        table.getSortOrder().add(date_column);
        Comparator<String> dateComparator = (o1, o2) -> {
            SimpleDateFormat sdf = new SimpleDateFormat(Tools.dateFormat);
            try {
                Date date1 = sdf.parse(o1);
                Date date2 = sdf.parse(o2);
                return date1.compareTo(date2);
            } catch (Exception ignored) {}
            return 0;
        };
        date_column.setComparator(dateComparator);
        name_column.setCellValueFactory(new PropertyValueFactory<>("seriesName"));
        episodes_column.setCellValueFactory(new PropertyValueFactory<>("episodes"));
        link_column.setCellValueFactory(row -> {
            SeriesHistory history = row.getValue();
            TextField field = new TextField();
            int height = 22;
            field.setPrefHeight(height);
            field.setMaxHeight(height);
            field.setMinHeight(height);
            int width = (int) link_column.getWidth() - 10;
            field.setPrefWidth(width);
            field.setMaxWidth(width);
            field.setMinWidth(width);
            field.setAlignment(Pos.CENTER_LEFT);
            field.setText(history.getSeriesLink());
            return new SimpleObjectProperty<>(field);
        });
        actions_column.setCellValueFactory(row -> {
            SeriesHistory history = row.getValue();
            MenuButton menu = new MenuButton("");
            menu.getStylesheets().add(String.valueOf(Main.class.getResource("/css/menubutton.css")));
            int height = 22;
            menu.setMaxHeight(height);
            menu.setMinHeight(height);
            menu.setPrefHeight(height);
            int width = (int) actions_column.getWidth() - 10;
            menu.setMaxWidth(width);
            menu.setMinWidth(width);
            menu.setPrefWidth(width);
            menu.setAlignment(Pos.CENTER);

            MenuItem seriesDetails = new MenuItem("Series Details");
            seriesDetails.setOnAction(event -> model.openVideoDetails(history.getSeriesLink()));
            MenuItem openLink = new MenuItem("Open Series Link");
            openLink.setOnAction(event -> model.showLinkPrompt(history.getSeriesLink(), true));
            MenuItem copyLink = new MenuItem("Copy Series Link");
            copyLink.setOnAction(event -> model.showCopyPrompt(history.getSeriesLink(), false));
            MenuItem downloadSeries = new MenuItem("Download Series");
            downloadSeries.setOnAction(event -> {
                if (model.isRunning()) {
                    model.showError("You can't download a series while the checker is running.");
                    return;
                }
                model.getUrlTextField().setText(history.getSeriesLink());
                model.start();
                model.showMessage("Started download", "Launched video downloader for: " + history.getSeriesLink());
            });
            MenuItem removeFromList = new MenuItem("Remove From List");
            removeFromList.setOnAction(event -> {
                model.getHistorySave().removeSeries(history);
                table.getItems().remove(history);
                table.sort();
                model.saveSeriesHistory();
            });
            menu.getItems().addAll(seriesDetails, openLink, copyLink, downloadSeries, removeFromList);
            return new SimpleObjectProperty<>(menu);
        });
        table.getItems().addAll(FXCollections.observableArrayList(model.getHistorySave().getSavedSeries()));
        table.sort();
        table.requestFocus();
    }

}
