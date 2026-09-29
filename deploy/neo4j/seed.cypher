// Demo knowledge graph: (:Author)-[:WROTE]->(:Ebook)
// Neo4jSearchNode looks up every author extracted from an uploaded file
// and recommends their other books. All names and titles are fictional.
MERGE (ada:Author {name: 'Ada Park'})
MERGE (leo:Author {name: 'Leo Marsh'})
MERGE (b1:Ebook {title: 'Retrieval Pipelines in Practice'})
MERGE (b2:Ebook {title: 'Vectors for Humans'})
MERGE (b3:Ebook {title: 'Graphs, Memory and Agents'})
MERGE (ada)-[:WROTE]->(b1)
MERGE (ada)-[:WROTE]->(b2)
MERGE (leo)-[:WROTE]->(b3);
