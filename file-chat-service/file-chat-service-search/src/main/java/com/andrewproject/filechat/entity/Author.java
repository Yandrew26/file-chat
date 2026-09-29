package com.andrewproject.filechat.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.data.neo4j.core.schema.*;

import java.util.List;

@Node("Author")
@Data
@NoArgsConstructor
public class Author {

    // Neo4j element id (internal Long ids are deprecated since Neo4j 5)
    @Id
    @GeneratedValue
    private String id;

    @Property("name")
    private String name;

    // Author and Ebook reference each other: keep the back-reference out of JSON, toString and hashCode
    @JsonIgnoreProperties("author")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @Relationship(type = "WROTE", direction = Relationship.Direction.OUTGOING)
    @JsonIgnoreProperties("author") // Ebook.author points back here; without this the JSON never terminates
    private List<Ebook> ebook;
}
