package org.handshakes;

import javafx.scene.control.TextArea;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class HandshakeFinder {
    private static final Logger logger = LoggerFactory.getLogger(HandshakeFinder.class);
    private final Graph graph;
    private final DatasetManager datasetManager;
    private List<String> lastPath;
    private List<String> lastFilms;

    public HandshakeFinder(Graph graph, DatasetManager datasetManager) {
        this.graph = graph;
        this.datasetManager = datasetManager;
    }

    public void findHandshakes(String actor1, String actor2, TextArea output, Runnable onPathFound) {
        if (graph == null || graph.getActorsFilms().isEmpty()) {
            output.appendText("Датасет пуст. Сначала спарсите фильмы.\n");
            return;
        }

        Map<String, ActorNode> nodes = new HashMap<>();
        Queue<ActorNode> queue = new LinkedList<>();
        Set<String> visited = new HashSet<>();

        ActorNode start = new ActorNode(actor1, null, null);
        nodes.put(actor1, start);
        queue.add(start);
        visited.add(actor1);

        ActorNode found = null;

        while (!queue.isEmpty() && found == null) {
            ActorNode current = queue.poll();
            Set<String> films = graph.getActorsFilms().getOrDefault(current.name, new HashSet<>());

            for (String film : films) {
                String[] cast = new String[0];
                try {
                    cast = datasetManager.readDataset().stream()
                            .filter(m -> m.filmName().equals(film))
                            .findFirst()
                            .map(m -> m.cast().split(", "))
                            .orElse(new String[0]);
                } catch (Exception e) {
                    logger.error("Ошибка чтения датасета при поиске фильма '{}': {}", film, e.getMessage());
                    continue;
                }

                for (String neighbor : cast) {
                    if (!visited.contains(neighbor)) {
                        visited.add(neighbor);
                        ActorNode child = new ActorNode(neighbor, current, film);
                        nodes.put(neighbor, child);
                        queue.add(child);
                        if (neighbor.equals(actor2)) {
                            found = child;
                            break;
                        }
                    }
                }
                if (found != null) break;
            }
        }

        if (found == null) {
            output.appendText("Цепочка между " + actor1 + " и " + actor2 + " не найдена.\n");
            logger.info("Цепочка не найдена: {} → {}", actor1, actor2);
            return;
        }
        List<String> path = new ArrayList<>();
        List<String> filmsInPath = new ArrayList<>();
        ActorNode current = found;
        while (current != null && current.parent != null) {
            path.add(0, current.name);
            if (current.film != null) {
                filmsInPath.add(0, current.film);
            }
            current = current.parent;
        }
        path.add(0, actor1);
        this.lastPath = path;
        this.lastFilms = filmsInPath;

        output.appendText("Кратчайшая цепочка от " + actor1 + " до " + actor2 + ":\n");
        for (int i = 0; i < path.size() - 1; i++) {
            output.appendText(path.get(i) + " → " + path.get(i + 1));
            if (i < filmsInPath.size()) {
                output.appendText(" (Фильм: " + filmsInPath.get(i) + ")");
            }
            output.appendText("\n");
        }
        output.appendText("Длина цепочки = " + (path.size() - 1) + "\n");

        logger.info("Цепочка найдена: длина {}", path.size() - 1);

        if (onPathFound != null) {
            onPathFound.run();
        }
    }

    public List<String> getLastPath() {
        return lastPath != null ? new ArrayList<>(lastPath) : null;
    }

    public List<String> getFilmsInPath() {
        return lastFilms != null ? new ArrayList<>(lastFilms) : null;
    }

    private static class ActorNode {
        String name;
        ActorNode parent;
        String film;

        ActorNode(String name, ActorNode parent, String film) {
            this.name = name;
            this.parent = parent;
            this.film = film;
        }
    }
}