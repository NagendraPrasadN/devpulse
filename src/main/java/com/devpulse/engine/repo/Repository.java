package com.devpulse.engine.repo;

import java.util.List;
import java.util.Optional;

/**
 * A generic "box of things findable by id".
 *
 * T  = the type of thing stored (Event, Incident, Project)
 * ID = the type of its identifier (UUID, String)
 *
 * Today the only implementation is in-memory. In Phase 3 we write a database-backed one
 * and the rest of the code does not change, because it only depends on this interface.
 *
 * findById returns Optional<T>: "maybe nothing" is part of the signature, so callers
 * cannot forget to handle it the way they can forget a null check.
 */
public interface Repository<T, ID> {

    T save(T entity);

    Optional<T> findById(ID id);

    List<T> findAll();

    /** Accepts a Rule for T or any supertype of T (see Rule for the wildcard explanation). */
    List<T> findWhere(Rule<? super T> rule);

    boolean deleteById(ID id);

    long count();
}
