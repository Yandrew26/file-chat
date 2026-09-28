package com.andrewproject.filechat.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.neo4j.core.schema.*;

import java.util.List;

@Node("Ebook")
@Data
@NoArgsConstructor
public class Ebook {

    @Id
    @GeneratedValue
    private Long id;

    @Property("title")
    private String title;

    @Relationship(type = "WROTE", direction = Relationship.Direction.INCOMING)
    private List<Author> author;
}
