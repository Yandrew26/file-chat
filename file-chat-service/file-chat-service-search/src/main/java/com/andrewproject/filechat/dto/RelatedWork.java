package com.andrewproject.filechat.dto;

import java.util.List;

/** Another work by one or more authors of the documents in a conversation. */
public record RelatedWork(String title, List<String> authors) {
}
