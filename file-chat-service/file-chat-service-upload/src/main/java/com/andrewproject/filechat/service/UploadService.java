package com.andrewproject.filechat.service;


import org.springframework.ai.document.Document;
import org.springframework.core.io.Resource;

import java.util.List;

public interface UploadService {
    List<Document> pdfUpload(Resource resource);
    List<String> search(String query);
}
