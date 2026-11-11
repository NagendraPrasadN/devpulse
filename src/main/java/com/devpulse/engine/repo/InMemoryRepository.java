package com.devpulse.engine.repo;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Thread-safe in-memory Repository backed by a ConcurrentHashMap.
 *
 * It does not know how to read an id from a T, so the creator passes a Function:
 *     new InMemoryRepository<>(Event::id)      // method reference
 *
 * "Function<? super T, ? extends ID>": accepts a function that can take a T (or a parent)
 * and returns an ID (or a child of it). Same PECS idea, now with both directions.
 */
public class InMemoryRepository<T, ID> implements Repository<T, ID> {

    private final ConcurrentHashMap<ID, T> data = new ConcurrentHashMap<>();
    private final Function<? super T, ? extends ID> idExtractor;

    public InMemoryRepository(Function<? super T, ? extends ID> idExtractor) {
        this.idExtractor = idExtractor;
    }

    @Override
    public T save(T entity) {
        data.put(idExtractor.apply(entity), entity); // same id again = overwrite (upsert)
        return entity;
    }

    @Override
    public Optional<T> findById(ID id) {
        return Optional.ofNullable(data.get(id));
    }

    @Override
    public List<T> findAll() {
        return List.copyOf(data.values());
    }

    @Override
    public List<T> findWhere(Rule<? super T> rule) {
        return data.values().stream()
                .filter(rule::test)
                .toList();
    }

    @Override
    public boolean deleteById(ID id) {
        return data.remove(id) != null;
    }

    @Override
    public long count() {
        return data.size();
    }
}
