package org.handshakes;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.*;

public class Main extends Application {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    private static final String TOKEN;
    private ApiClient apiClient;
    private DatasetManager datasetManager;
    private HandshakeFinder handshakeFinder;
    private TabPane tabPane;
    private List<String> currentPath;
    private List<String> currentFilms;

    static {
        Properties props = new Properties();
        try (InputStream is = Main.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (is == null) {
                throw new RuntimeException("application.properties not found");
            }
            props.load(is);
            TOKEN = props.getProperty("kinopoisk.api.token");
            if (TOKEN == null || TOKEN.isEmpty()) {
                throw new RuntimeException("Kinopoisk API token not set in application.properties");
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load configuration", e);
        }
    }

    @Override
    public void start(Stage primaryStage) {
        try {
            apiClient = new ApiClient(TOKEN);
            datasetManager = new DatasetManager("film_dataset.csv");
            handshakeFinder = new HandshakeFinder(datasetManager.getGraph(), datasetManager);
        } catch (Exception e) {
            logger.error("Ошибка инициализации: {}", e.getMessage());
            showAlert("Ошибка", "Не удалось инициализировать приложение: " + e.getMessage());
            return;
        }

        tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        Tab parseTab = new Tab("Парсинг фильмов");
        parseTab.setContent(createParseTab());
        Tab handshakeTab = new Tab("Поиск рукопожатий");
        handshakeTab.setContent(createHandshakeTab());
        Tab statsTab = new Tab("Статистика");
        statsTab.setContent(createStatsTab());
        Tab graphTab = new Tab("Граф цепочки");
        graphTab.setContent(createGraphTab());

        tabPane.getTabs().addAll(parseTab, handshakeTab, statsTab, graphTab);

        Scene scene = new Scene(tabPane, 900, 700);
        primaryStage.setTitle("Цепочка рукопожатий");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private VBox createParseTab() {
        VBox parseLayout = new VBox(10);
        parseLayout.setPadding(new Insets(10));

        ToggleGroup parseModeGroup = new ToggleGroup();
        RadioButton randomRadio = new RadioButton("Случайные фильмы");
        randomRadio.setToggleGroup(parseModeGroup);
        randomRadio.setSelected(true);
        RadioButton specificRadio = new RadioButton("Конкретный фильм");
        specificRadio.setToggleGroup(parseModeGroup);

        TextField numFilmsField = new TextField();
        numFilmsField.setPromptText("Количество фильмов (1-200)");

        TextField filmNameField = new TextField();
        filmNameField.setPromptText("Введите название фильма");
        filmNameField.setDisable(true);

        randomRadio.setOnAction(e -> {
            numFilmsField.setDisable(false);
            filmNameField.setDisable(true);
        });
        specificRadio.setOnAction(e -> {
            numFilmsField.setDisable(true);
            filmNameField.setDisable(false);
        });

        Button parseButton = new Button("Спарсить");
        Button exportButton = new Button("Экспорт в JSON");
        HBox buttonBox = new HBox(10, parseButton, exportButton);

        TextArea resultArea = new TextArea();
        resultArea.setEditable(false);
        resultArea.setPrefHeight(300);

        parseButton.setOnAction(e -> {
            resultArea.clear();
            if (randomRadio.isSelected()) {
                try {
                    int numFilms = Integer.parseInt(numFilmsField.getText());
                    if (numFilms < 1 || numFilms > 200) {
                        showAlert("Ошибка", "Введите число от 1 до 200");
                        return;
                    }
                    List<Movie> addedMovies = datasetManager.parseRandomFilms(apiClient, numFilms);
                    String movieNames = addedMovies.stream()
                            .map(Movie::filmName)
                            .collect(java.util.stream.Collectors.joining("\n"));
                    resultArea.setText("Успешно спарсено " + addedMovies.size() + " фильмов:\n\n" + movieNames);
                } catch (NumberFormatException ex) {
                    showAlert("Ошибка", "Введите корректное число!");
                } catch (Exception ex) {
                    showAlert("Ошибка", "Ошибка при парсинге: " + ex.getMessage());
                }
            } else {
                String filmName = filmNameField.getText().trim();
                if (filmName.isEmpty()) {
                    showAlert("Ошибка", "Название фильма не может быть пустым");
                    return;
                }
                try {
                    datasetManager.parseSpecificFilm(apiClient, filmName);
                    resultArea.setText("Успешно спарсен фильм: " + filmName);
                } catch (Exception ex) {
                    showAlert("Ошибка", "Ошибка при парсинге: " + ex.getMessage());
                }
            }
        });

        exportButton.setOnAction(e -> {
            try {
                datasetManager.exportToJson("film_dataset.json");
                resultArea.setText("Датасет экспортирован в film_dataset.json");
            } catch (Exception ex) {
                showAlert("Ошибка", "Не удалось экспортировать: " + ex.getMessage());
            }
        });

        parseLayout.getChildren().addAll(
                new Label("Режим парсинга:"), randomRadio, specificRadio,
                new Label("Количество фильмов:"), numFilmsField,
                new Label("Название фильма:"), filmNameField,
                buttonBox, resultArea
        );
        return parseLayout;
    }

    private VBox createHandshakeTab() {
        VBox handshakeLayout = new VBox(10);
        handshakeLayout.setPadding(new Insets(10));

        ToggleGroup handshakeModeGroup = new ToggleGroup();
        RadioButton autoRadio = new RadioButton("Автоматический выбор актеров");
        autoRadio.setToggleGroup(handshakeModeGroup);
        autoRadio.setSelected(true);
        RadioButton manualRadio = new RadioButton("Ручной ввод актеров");
        manualRadio.setToggleGroup(handshakeModeGroup);

        ComboBox<String> actor1Combo = new ComboBox<>();
        actor1Combo.setEditable(true);
        actor1Combo.setPromptText("Выберите или введите актера");
        actor1Combo.setDisable(true);

        ComboBox<String> actor2Combo = new ComboBox<>();
        actor2Combo.setEditable(true);
        actor2Combo.setPromptText("Выберите или введите актера");
        actor2Combo.setDisable(true);

        try {
            List<String> actors = datasetManager.getUniqueActors();
            actor1Combo.getItems().addAll(actors);
            actor2Combo.getItems().addAll(actors);
        } catch (Exception e) {
            logger.warn("Не удалось загрузить актеров для автодополнения");
        }

        autoRadio.setOnAction(e -> {
            actor1Combo.setDisable(true);
            actor2Combo.setDisable(true);
        });
        manualRadio.setOnAction(e -> {
            actor1Combo.setDisable(false);
            actor2Combo.setDisable(false);
        });

        Button findButton = new Button("Найти рукопожатия");
        TextArea resultArea = new TextArea();
        resultArea.setEditable(false);
        resultArea.setPrefHeight(300);

        findButton.setOnAction(e -> {
            resultArea.clear();
            currentPath = null;
            currentFilms = null;

            String actor1, actor2;
            if (autoRadio.isSelected()) {
                try {
                    String[] actors = datasetManager.autoInput();
                    actor1 = actors[0];
                    actor2 = actors[1];
                    resultArea.appendText("Случайный актер #1: " + actor1 + "\n");
                    resultArea.appendText("Случайный актер #2: " + actor2 + "\n");
                } catch (Exception ex) {
                    showAlert("Ошибка", "Ошибка при выборе: " + ex.getMessage());
                    return;
                }
            } else {
                actor1 = actor1Combo.getEditor().getText().trim();
                actor2 = actor2Combo.getEditor().getText().trim();
                if (actor1.isEmpty() || actor2.isEmpty()) {
                    showAlert("Ошибка", "Введите имена актеров");
                    return;
                }
            }

            handshakeFinder.findHandshakes(actor1, actor2, resultArea, () -> {
                currentPath = handshakeFinder.getLastPath();
                currentFilms = handshakeFinder.getFilmsInPath();
                updateGraphTab();
            });
        });

        handshakeLayout.getChildren().addAll(
                new Label("Режим поиска:"), autoRadio, manualRadio,
                new Label("Первый актер:"), actor1Combo,
                new Label("Второй актер:"), actor2Combo,
                findButton, resultArea
        );
        return handshakeLayout;
    }

    private VBox createStatsTab() {
        VBox statsLayout = new VBox(10);
        statsLayout.setPadding(new Insets(10));

        Button statsButton = new Button("Показать статистику");
        TextArea statsArea = new TextArea();
        statsArea.setEditable(false);
        statsArea.setPrefHeight(300);

        statsButton.setOnAction(e -> {
            try {
                statsArea.setText(datasetManager.showDatasetStatistics());
            } catch (Exception ex) {
                showAlert("Ошибка", "Ошибка при получении статистики: " + ex.getMessage());
            }
        });

        statsLayout.getChildren().addAll(statsButton, statsArea);
        return statsLayout;
    }

    private VBox createGraphTab() {
        VBox graphLayout = new VBox(10);
        graphLayout.setPadding(new Insets(10));

        Canvas canvas = new Canvas(800, 500);
        GraphicsContext gc = canvas.getGraphicsContext2D();

        Button clearButton = new Button("Очистить граф");

        clearButton.setOnAction(e -> {
            gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
            currentPath = null;
            currentFilms = null;
        });

        graphLayout.getChildren().addAll(
                new Label("Граф найденной цепочки рукопожатий"),
                clearButton,
                canvas
        );

        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
        return graphLayout;
    }

    private void updateGraphTab() {
        Tab graphTab = tabPane.getTabs().get(3);
        if (graphTab.getContent() instanceof VBox) {
            VBox box = (VBox) graphTab.getContent();
            Canvas canvas = (Canvas) box.getChildren().get(2);
            drawPathGraph(canvas.getGraphicsContext2D(), canvas.getWidth(), canvas.getHeight());
        }
    }

    private void drawPathGraph(GraphicsContext gc, double width, double height) {
        gc.clearRect(0, 0, width, height);
        gc.setFill(Color.LIGHTGRAY);
        gc.fillRect(0, 0, width, height);

        if (currentPath == null || currentPath.size() < 2) {
            gc.setFill(Color.BLACK);
            gc.fillText("Сначала найдите цепочку рукопожатий.", 20, 30);
            return;
        }

        double centerX = width / 2;
        double centerY = height / 2;
        double radius = Math.min(width, height) * 0.3;
        int n = currentPath.size();

        Map<String, Point> positions = new HashMap<>();
        for (int i = 0; i < n; i++) {
            double angle = Math.PI / 2 - 2 * Math.PI * i / n;
            double x = centerX + radius * Math.cos(angle);
            double y = centerY + radius * Math.sin(angle);
            positions.put(currentPath.get(i), new Point(x, y));
        }

        gc.setStroke(Color.web("#2196F3"));
        gc.setLineWidth(3);
        for (int i = 0; i < n - 1; i++) {
            Point p1 = positions.get(currentPath.get(i));
            Point p2 = positions.get(currentPath.get(i + 1));
            gc.strokeLine(p1.x, p1.y, p2.x, p2.y);

            if (currentFilms != null && i < currentFilms.size()) {
                String film = currentFilms.get(i);
                double midX = (p1.x + p2.x) / 2;
                double midY = (p1.y + p2.y) / 2 - 10;
                gc.setFill(Color.BLACK);
                gc.setFont(javafx.scene.text.Font.font(10));
                gc.fillText(truncate(film, 25), midX - 40, midY);
            }
        }

        for (int i = 0; i < n; i++) {
            String actor = currentPath.get(i);
            Point p = positions.get(actor);
            gc.setFill(i == 0 ? Color.web("#4CAF50") : i == n-1 ? Color.web("#F44336") : Color.web("#FF9800"));
            gc.fillOval(p.x - 12, p.y - 12, 24, 24);
            gc.setFill(Color.WHITE);
            gc.setFont(javafx.scene.text.Font.font("Bold", 11));
            gc.fillText(truncate(actor, 18), p.x - 35, p.y + 4);
        }
    }
    private String truncate(String text, int max) {
        return text.length() > max ? text.substring(0, max - 3) + "..." : text;
    }
    private static class Point {
        double x, y;
        Point(double x, double y) { this.x = x; this.y = y; }
    }
    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    @Override
    public void stop() throws Exception {
        if (apiClient != null) {
            apiClient.close();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}