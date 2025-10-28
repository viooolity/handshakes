package org.handshakes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class Graph {
    private static final Logger logger = LoggerFactory.getLogger(Graph.class);
    private final Map<String, Set<String>> adjacencyList;
    private final Map<String, Set<String>> actorsFilms;

    public Graph(List<Movie> dataset) {
        this.adjacencyList = new HashMap<>();
        this.actorsFilms = new HashMap<>();
        buildGraph(dataset);
    }

    private void buildGraph(List<Movie> dataset) {
        for (Movie film : dataset) {
            String[] cast = film.cast().split(", ");
            for (int i = 0; i < cast.length; i++) {
                String actor = cast[i];
                adjacencyList.computeIfAbsent(actor, k -> new HashSet<>());
                actorsFilms.computeIfAbsent(actor, k -> new HashSet<>()).add(film.filmName());
                for (int j = i + 1; j < cast.length; j++) {
                    String coActor = cast[j];
                    adjacencyList.computeIfAbsent(coActor, k -> new HashSet<>());
                    adjacencyList.get(actor).add(coActor);
                    adjacencyList.get(coActor).add(actor);
                }
            }
        }
        logger.debug("Граф построен с {} фильмами", dataset.size());
    }

    public void addMovies(List<Movie> newMovies) {
        for (Movie film : newMovies) {
            String[] cast = film.cast().split(", ");
            for (int i = 0; i < cast.length; i++) {
                String actor = cast[i];
                adjacencyList.computeIfAbsent(actor, k -> new HashSet<>());
                actorsFilms.computeIfAbsent(actor, k -> new HashSet<>()).add(film.filmName());
                for (int j = i + 1; j < cast.length; j++) {
                    String coActor = cast[j];
                    adjacencyList.computeIfAbsent(coActor, k -> new HashSet<>());
                    adjacencyList.get(actor).add(coActor);
                    adjacencyList.get(coActor).add(actor);
                }
            }
        }
        logger.debug("Граф обновлен с добавлением {} фильмов", newMovies.size());
    }

    public List<String> findShortestPath(String startActor, String endActor) {
        if (!adjacencyList.containsKey(startActor) || !adjacencyList.containsKey(endActor)) {
            logger.warn("Один из актеров ({} или {}) отсутствует в графе", startActor, endActor);
            return null;
        }

        Set<String> visited = new HashSet<>();
        Queue<ActorNode> queue = new LinkedList<>();
        queue.add(new ActorNode(startActor, new ArrayList<>(List.of(startActor))));

        while (!queue.isEmpty()) {
            ActorNode node = queue.poll();
            String actor = node.actor();
            List<String> path = node.path();

            if (actor.equals(endActor)) {
                logger.debug("Найден кратчайший путь: {}", path);
                return path;
            }

            visited.add(actor);
            for (String neighbor : adjacencyList.getOrDefault(actor, new HashSet<>())) {
                if (!visited.contains(neighbor)) {
                    List<String> newPath = new ArrayList<>(path);
                    newPath.add(neighbor);
                    queue.add(new ActorNode(neighbor, newPath));
                }
            }
        }
        logger.warn("Путь между {} и {} не найден", startActor, endActor);
        return null;
    }

    public Map<String, Set<String>> getActorsFilms() {
        return actorsFilms;
    }
}