package com.andrewproject.filechat.repository;

import com.andrewproject.filechat.entity.Author;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AuthorRepository extends Neo4jRepository<Author, Long> {
    Author findByName(String name);

    @Query("MATCH (a:Author {name: $name})-[:WROTE]->(b:Ebook) RETURN b.title AS title")
    List<String> findEbookTitlesByAuthorName(@Param("name") String name);
}
