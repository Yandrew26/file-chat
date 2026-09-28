package com.andrewproject.filechat.controller;

import com.andrewproject.filechat.entity.Author;
import com.andrewproject.filechat.entity.Ebook;
import com.andrewproject.filechat.repository.AuthorRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/test")
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
        List<String> ebooks = authorRepository.findEbookTitlesByAuthorName(name);
        if (ebooks == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(ebooks);
    }
}
