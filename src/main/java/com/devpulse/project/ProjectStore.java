package com.devpulse.project;

import com.devpulse.engine.model.Project;
import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.Optional;

/**
 * Persistence for projects, using plain SQL through Spring's JdbcClient (an H2 in-memory
 * database for now; PostgreSQL + Hibernate arrive in Phase 3).
 *
 * @Repository marks the data-access layer (and is a @Component, so it becomes a bean).
 * Only SQL lives here. Business rules and transactions belong to ProjectService.
 *
 * insert() on an existing id throws Spring's DuplicateKeyException: Spring translates each
 * database's own error code into one common exception family, so our code does not depend
 * on which database we use.
 */
@Repository
public class ProjectStore {

    private static final RowMapper<Project> MAPPER = (rs, rowNum) ->
            new Project(rs.getString("id"), rs.getString("project_name"), rs.getString("owner_name"));

    private final JdbcClient jdbc;
    private final Clock clock;

    public ProjectStore(DataSource dataSource, Clock clock) {
        this.jdbc = JdbcClient.create(dataSource);
        this.clock = clock;
    }

    @PostConstruct
    void createTable() {
        jdbc.sql("""
                create table if not exists projects (
                    id varchar(40) primary key,
                    project_name varchar(100) not null,
                    owner_name varchar(100) not null,
                    created_at timestamp not null
                )""").update();
    }

    public void insert(Project project) {
        jdbc.sql("insert into projects (id, project_name, owner_name, created_at) values (?, ?, ?, ?)")
                .params(project.id(), project.name(), project.owner(), Timestamp.from(clock.instant()))
                .update();
    }

    public Optional<Project> findById(String id) {
        return jdbc.sql("select id, project_name, owner_name from projects where id = ?")
                .param(id)
                .query(MAPPER)
                .optional();
    }

    public List<Project> findAll() {
        return jdbc.sql("select id, project_name, owner_name from projects order by id")
                .query(MAPPER)
                .list();
    }

    public boolean exists(String id) {
        Long count = jdbc.sql("select count(*) from projects where id = ?")
                .param(id)
                .query(Long.class)
                .single();
        return count > 0;
    }
}
