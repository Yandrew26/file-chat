package com.andrewproject.filechat.dto;

import java.util.List;

/** Title and authors of an uploaded document, extracted by the model from its first pages. */
public record DocumentMetadata(String title, List<String> authors) {
}
