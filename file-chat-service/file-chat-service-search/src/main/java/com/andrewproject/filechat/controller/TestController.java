package com.andrewproject.filechat.controller;

import com.andrewproject.filechat.entity.Author;
import com.andrewproject.filechat.repository.AuthorRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Endpoints for inspecting the Neo4j catalog during development. Off by default because the gateway would expose
 * them without authentication; enable with FILECHAT_DEBUG_ENDPOINTS=true.
 */
@RestController
@RequestMapping("/test")
@ConditionalOnProperty(name = "filechat.debug-endpoints", havingValue = "true")
public class TestController {

    private final AuthorRepository authorRepository;

    public TestController(AuthorRepository authorRepository) {
        this.authorRepository = authorRepository;
    }

    @GetMapping("/getAuthor")
    public ResponseEntity<Author> getAuthorByName(@RequestParam("name") String name) {
        Author author = authorRepository.findByName(name);
        if (author == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(author);
    }

    @GetMapping("/books/byAuthor")
    public ResponseEntity<List<String>> getBooksByAuthorName(@RequestParam("name") String name) {
        return ResponseEntity.ok(authorRepository.findEbookTitlesByAuthorName(name));
    }
}
