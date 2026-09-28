package com.andrewproject.filechat.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.data.neo4j.core.schema.*;

import java.util.List;

@Node("Ebook")
@Data
@NoArgsConstructor
public class Ebook {

    // Neo4j element id (internal Long ids are deprecated since Neo4j 5)
    @Id
    @GeneratedValue
    private String id;

    @Property("title")
    private String title;

    @JsonIgnoreProperties("ebook")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @Relationship(type = "WROTE", direction = Relationship.Direction.INCOMING)
    private List<Author> author;
}
