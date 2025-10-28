package org.handshakes;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ApiClient implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(org.handshakes.ApiClient.class);
    private final String token;
    private final HttpClient client;
    private final Gson gson;
    private final ExecutorService executor;

    public ApiClient(String token) {
        this.token = token;
        this.client = HttpClient.newHttpClient();
        this.gson = new Gson();
        this.executor = Executors.newFixedThreadPool(4); // Пул на 4 потока
    }

    public ExecutorService getExecutor() {
        return executor;
    }

    public Movie getRandomFilm() throws IOException, InterruptedException {
        String url = "https://api.kinopoisk.dev/v1/movie/random";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("accept", "application/json")
                .header("X-API-KEY", token)
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
            JsonObject data = gson.fromJson(response.body(), JsonObject.class);
            String filmName = data.get("name").getAsString();
            List<String> cast = new ArrayList<>();
            if (data.get("persons") != null && !data.get("persons").isJsonNull()) {
                JsonArray persons = data.get("persons").getAsJsonArray();
                for (int i = 0; i < persons.size(); i++) {
                    JsonObject person = persons.get(i).getAsJsonObject();
                    if (person.get("name") != null && !person.get("name").isJsonNull() &&
                            person.get("profession").getAsString().equals("актеры")) {
                        cast.add(person.get("name").getAsString());
                    }
                }
            }
            return new Movie(filmName, String.join(", ", cast));
        } else {
            JsonObject data = gson.fromJson(response.body(), JsonObject.class);
            String errorMessage = data.get("message").getAsString();
            logger.error("Ошибка API. Код: {}, Сообщение: {}", response.statusCode(), errorMessage);
            throw new IOException("Ошибка API. Код: " + response.statusCode() + ", Сообщение: " + errorMessage);
        }
    }

    public Movie getSpecificFilm(String filmName) throws IOException, InterruptedException {
        String encodedName = URLEncoder.encode(filmName, StandardCharsets.UTF_8);
        String url = "https://api.kinopoisk.dev/v1.3/movie?page=1&limit=10&name=" + encodedName;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("accept", "application/json")
                .header("X-API-KEY", token)
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
            JsonObject tempData = gson.fromJson(response.body(), JsonObject.class);
            JsonArray docs = tempData.getAsJsonArray("docs");
            if (docs.size() == 0) {
                logger.warn("Фильм '{}' не найден", filmName);
                throw new IOException("Фильм не найден");
            }
            int filmId = docs.get(0).getAsJsonObject().get("id").getAsInt();
            String secondUrl = "https://api.kinopoisk.dev/v1.3/movie/" + filmId;
            HttpRequest secondRequest = HttpRequest.newBuilder()
                    .uri(URI.create(secondUrl))
                    .header("accept", "application/json")
                    .header("X-API-KEY", token)
                    .GET()
                    .build();

            HttpResponse<String> secondResponse = client.send(secondRequest, HttpResponse.BodyHandlers.ofString());
            if (secondResponse.statusCode() == 200) {
                JsonObject data = gson.fromJson(secondResponse.body(), JsonObject.class);
                String name = data.get("name").getAsString();
                List<String> cast = new ArrayList<>();
                if (data.get("persons") != null && !data.get("persons").isJsonNull()) {
                    JsonArray persons = data.get("persons").getAsJsonArray();
                    for (int i = 0; i < persons.size(); i++) {
                        JsonObject person = persons.get(i).getAsJsonObject();
                        if (person.get("name") != null && !person.get("name").isJsonNull() &&
                                person.get("profession").getAsString().equals("актеры")) {
                            cast.add(person.get("name").getAsString());
                        }
                    }
                }
                return new Movie(name, String.join(", ", cast));
            } else {
                JsonObject data = gson.fromJson(secondResponse.body(), JsonObject.class);
                String errorMessage = data.get("message").getAsString();
                logger.error("Ошибка API. Код: {}, Сообщение: {}", secondResponse.statusCode(), errorMessage);
                throw new IOException("Ошибка API. Код: " + secondResponse.statusCode() + ", Сообщение: " + errorMessage);
            }
        } else {
            JsonObject data = gson.fromJson(response.body(), JsonObject.class);
            String errorMessage = data.get("message").getAsString();
            logger.error("Ошибка API. Код: {}, Сообщение: {}", response.statusCode(), errorMessage);
            throw new IOException("Ошибка API. Код: " + response.statusCode() + ", Сообщение: " + errorMessage);
        }
    }

    @Override
    public void close() {
        executor.shutdown();
        logger.info("Закрытие пула потоков KinopoiskApiClient");
    }
}