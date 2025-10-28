package org.handshakes;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import com.opencsv.exceptions.CsvValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class DatasetManager {
    private static final Logger logger = LoggerFactory.getLogger(DatasetManager.class);
    private final String csvFilePath;
    private final List<Movie> cachedDataset;
    private Graph cachedGraph;

    public DatasetManager(String csvFilePath) {
        this.csvFilePath = csvFilePath;
        this.cachedDataset = new ArrayList<>();
        try {
            this.cachedDataset.addAll(readDatasetFromFile());
            this.cachedGraph = new Graph(cachedDataset);
        } catch (Exception e) {
            logger.error("Ошибка инициализации датасета: {}", e.getMessage());
        }
    }

    public List<Movie> parseRandomFilms(ApiClient apiClient, int numFilms) throws Exception {
        List<CompletableFuture<Movie>> futures = new ArrayList<>();
        for (int i = 0; i < numFilms; i++) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                try {
                    return apiClient.getRandomFilm();
                } catch (Exception e) {
                    logger.error("Ошибка получения фильма: {}", e.getMessage());
                    return null;
                }
            }, apiClient.getExecutor()));
        }

        List<Movie> newMovies = futures.stream()
                .map(CompletableFuture::join)
                .filter(movie -> movie != null && !movie.cast().isEmpty())
                .peek(movie -> logger.info("Загружен фильм: {}", movie.filmName()))
                .collect(Collectors.toList());

        if (!newMovies.isEmpty()) {
            writeToCsv(newMovies);
            cachedDataset.addAll(newMovies);
            updateCachedGraph(newMovies);
        } else {
            logger.warn("Не удалось загрузить фильмы");
            throw new IOException("Не удалось загрузить ни одного фильма");
        }
        return newMovies;
    }

    public void parseSpecificFilm(ApiClient apiClient, String filmName) throws Exception {
        Movie filmData = apiClient.getSpecificFilm(filmName);
        if (!filmData.cast().isEmpty()) {
            logger.info("Загружен фильм: {}", filmData.filmName());
            List<Movie> dataset = new ArrayList<>();
            dataset.add(filmData);
            writeToCsv(dataset);
            cachedDataset.add(filmData);
            updateCachedGraph(dataset);
        } else {
            logger.warn("У фильма '{}' нет актеров", filmName);
        }
    }

    private void writeToCsv(List<Movie> dataset) throws IOException {
        File file = new File(csvFilePath);
        boolean fileExists = file.exists() && file.length() > 0;

        try (CSVWriter writer = new CSVWriter(
                new OutputStreamWriter(new FileOutputStream(csvFilePath, true), StandardCharsets.UTF_8)
        )) {
            if (!fileExists) {
                writer.writeNext(new String[]{"Film", "Cast"});
            }
            for (Movie movie : dataset) {
                writer.writeNext(new String[]{movie.filmName(), movie.cast()});
            }
        } catch (IOException e) {
            logger.error("Ошибка записи в CSV: {}", e.getMessage());
            throw e;
        }
    }

    private List<Movie> readDatasetFromFile() throws IOException, CsvValidationException {
        List<Movie> dataset = new ArrayList<>();
        File file = new File(csvFilePath);
        if (!file.exists() || file.length() == 0) {
            return dataset;
        }
        try (CSVReader reader = new CSVReader(new InputStreamReader(new FileInputStream(csvFilePath), StandardCharsets.UTF_8))) {
            reader.readNext(); // Пропуск заголовка
            String[] line;
            while ((line = reader.readNext()) != null) {
                if (line.length >= 2) {
                    dataset.add(new Movie(line[0], line[1]));
                }
            }
        } catch (IOException | CsvValidationException e) {
            logger.error("Ошибка чтения CSV: {}", e.getMessage());
            throw e;
        }
        return dataset;
    }

    public String[] autoInput() throws Exception {
        if (cachedDataset.isEmpty()) {
            logger.warn("Датасет пуст. Сначала спарсите фильмы.");
            throw new IOException("Датасет пуст. Пожалуйста, спарсите фильмы.");
        }

        Random random = new Random();
        String actor1 = null;
        String actor2 = null;
        Movie film1 = null;
        Movie film2 = null;

        while (actor1 == null || actor2 == null || actor1.equals(actor2)) {
            film1 = cachedDataset.get(random.nextInt(cachedDataset.size()));
            film2 = cachedDataset.get(random.nextInt(cachedDataset.size()));
            String[] cast1 = film1.cast().split(", ");
            String[] cast2 = film2.cast().split(", ");
            if (cast1.length == 0 || cast2.length == 0) {
                continue;
            }
            actor1 = cast1[random.nextInt(cast1.length)];
            actor2 = cast2[random.nextInt(cast2.length)];
        }

        logger.info("Random actor #1: {}; From movie: {}", actor1, film1.filmName());
        logger.info("Random actor #2: {}; From movie: {}", actor2, film2.filmName());
        return new String[]{actor1, actor2};
    }

    public String showDatasetStatistics() throws Exception {
        if (cachedDataset.isEmpty()) {
            logger.warn("Датасет пуст. Нечего анализировать.");
            return "Датасет пуст. Пожалуйста, спарсите фильмы.";
        }

        int totalFilms = cachedDataset.size();
        Set<String> uniqueActors = new HashSet<>();
        Map<String, Integer> actorFrequency = new HashMap<>();
        Map<String, Integer> actorConnections = new HashMap<>(); // Кол-во уникальных коллабораций
        long totalActors = 0;

        for (Movie movie : cachedDataset) {
            String[] cast = movie.cast().split(", ");
            totalActors += cast.length;
            for (String actor : cast) {
                uniqueActors.add(actor);
                actorFrequency.merge(actor, 1, Integer::sum);
            }
        }

        // Подсчёт связей
        Graph graph = getGraph();
        if (graph != null) {
            for (String actor : uniqueActors) {
                Set<String> films = graph.getActorsFilms().getOrDefault(actor, new HashSet<>());
                int connections = 0;
                for (String film : films) {
                    String[] cast = cachedDataset.stream()
                            .filter(m -> m.filmName().equals(film))
                            .findFirst()
                            .map(m -> m.cast().split(", "))
                            .orElse(new String[0]);
                    connections += Arrays.stream(cast)
                            .filter(a -> !a.equals(actor))
                            .collect(Collectors.toSet()).size();
                }
                actorConnections.put(actor, connections);
            }
        }

        double avgActorsPerFilm = totalFilms > 0 ? (double) totalActors / totalFilms : 0;
        String topActorByFilms = actorFrequency.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(entry -> entry.getKey() + " (" + entry.getValue() + " фильмов)")
                .orElse("Н/Д");

        String mostConnectedActor = actorConnections.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(entry -> entry.getKey() + " (" + entry.getValue() + " связей)")
                .orElse("Н/Д");

        StringBuilder stats = new StringBuilder();
        stats.append("Статистика датасета:\n");
        stats.append("Количество фильмов: ").append(totalFilms).append("\n");
        stats.append("Количество уникальных актеров: ").append(uniqueActors.size()).append("\n");
        stats.append("Среднее число актеров на фильм: ").append(String.format("%.2f", avgActorsPerFilm)).append("\n");
        stats.append("Чаще всех снимался: ").append(topActorByFilms).append("\n");
        stats.append("Самый связанный актер: ").append(mostConnectedActor).append("\n");
        logger.info("Статистика выведена: {} фильмов, {} актеров", totalFilms, uniqueActors.size());
        return stats.toString();
    }

    public List<String> getUniqueActors() {
        Set<String> uniqueActors = new HashSet<>();
        for (Movie movie : cachedDataset) {
            String[] cast = movie.cast().split(", ");
            uniqueActors.addAll(Arrays.asList(cast));
        }
        return new ArrayList<>(uniqueActors);
    }

    public void exportToJson(String filePath) throws IOException {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try (FileWriter writer = new FileWriter(filePath)) {
            gson.toJson(cachedDataset, writer);
        }
        logger.info("Датасет экспортирован в JSON: {}", filePath);
    }

    private void updateCachedGraph(List<Movie> newMovies) {
        if (cachedGraph == null) {
            cachedGraph = new Graph(newMovies);
        } else {
            cachedGraph.addMovies(newMovies);
        }
    }

    public Graph getGraph() {
        return cachedGraph;
    }

    public List<Movie> readDataset() throws Exception {
        return new ArrayList<>(cachedDataset);
    }
}