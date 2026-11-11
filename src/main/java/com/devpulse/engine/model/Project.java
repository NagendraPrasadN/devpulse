package com.devpulse.engine.model;

import java.util.Objects;

/**
 * A registered project that sends events to DevPulse.
 *
 * WHY A RECORD?
 *  A Project is pure data: id, name, owner. A record gives us for free: final fields,
 *  a constructor, accessors (id(), name(), owner()), and equals/hashCode/toString
 *  based on ALL components. Compare with Event, which is a normal class because we
 *  want equality based on the id only.
 *
 * The compact constructor (no parameter list) runs before fields are assigned,
 * so it is the right place for validation.
 */
public record Project(String id, String name, String owner) {

    public Project {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name,"name");
        Objects.requireNonNull(owner, "owner");
        if (id.isBlank()) {
            throw new IllegalArgumentException("Project id must not be blank");
        }
    }
}
