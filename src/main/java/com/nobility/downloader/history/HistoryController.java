package com.nobility.downloader.history;

import com.nobility.downloader.Model;
import com.nobility.downloader.settings.Defaults;
import com.nobility.downloader.utils.Tools;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
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
        table.setRowFactory(callback -> {
            TableRow<SeriesHistory> row = new TableRow<>();
            ContextMenu menu = new ContextMenu();
            row.emptyProperty().addListener((observable, oldValue, isEmpty) -> {
                if (!isEmpty) {
                    SeriesHistory history = row.getItem();
                    MenuItem seriesDetails = new MenuItem("Series Details");
                    seriesDetails.setOnAction(event -> model.openVideoDetails(history.getSeriesLink()));
                    MenuItem openLink = new MenuItem("Open Series Link");
                    openLink.setOnAction(event -> model.showLinkPrompt(history.getSeriesLink(), true));
                    MenuItem copyLink = new MenuItem("Copy Series Link");
                    copyLink.setOnAction(event -> model.showCopyPrompt(history.getSeriesLink(), false));
                    MenuItem downloadSeries = new MenuItem("Download Series");
                    downloadSeries.setOnAction(event -> {
                        if (model.isRunning()) {
                            model.showError("You can't download a series while the downloader is running.");
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
                    row.setContextMenu(menu);
                    row.setOnMouseClicked(event -> {
                        if (model.settings().getBoolean(Defaults.SHOWCONTEXTONCLICK)) {
                            menu.show(stage, event.getScreenX(), event.getScreenY());
                        }
                    });
                }
            });
            return row;
        });
        name_column.setCellValueFactory(new PropertyValueFactory<>("seriesName"));
        episodes_column.setCellValueFactory(new PropertyValueFactory<>("episodes"));
        link_column.setCellValueFactory(new PropertyValueFactory<>("seriesLink"));
        table.getItems().addAll(FXCollections.observableArrayList(model.getHistorySave().getSavedSeries()));
        table.sort();
        table.requestFocus();
    }

}
