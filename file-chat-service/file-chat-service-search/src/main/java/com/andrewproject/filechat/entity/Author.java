package com.andrewproject.filechat.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.neo4j.core.schema.*;

import java.util.List;

@Node("Author")
@Data
@NoArgsConstructor
public class Author {

    @Id
    @GeneratedValue
    private Long id;

    @Property("name")
    private String name;

    @Relationship(type = "WROTE", direction = Relationship.Direction.OUTGOING)
    private List<Ebook> ebook;
}
