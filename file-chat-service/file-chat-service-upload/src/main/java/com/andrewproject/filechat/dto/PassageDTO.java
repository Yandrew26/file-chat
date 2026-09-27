package com.andrewproject.filechat.dto;

import org.springframework.ai.document.Document;

import java.util.Map;

public record PassageDTO(String id, String text, String fileName, Integer pageNumber, Double score) {

    public static PassageDTO from(Document document) {
        Map<String, Object> metadata = document.getMetadata();
        Object fileName = metadata.get("file_name");
        Object pageNumber = metadata.get("page_number");
        return new PassageDTO(document.getId(),
                document.getText(),
                fileName == null ? null : fileName.toString(),
                pageNumber instanceof Number number ? number.intValue() : null,
                document.getScore());
    }
}
