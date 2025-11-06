package com.andrewproject.filechat.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.andrewproject.filechat.service.UploadService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.ParagraphPdfDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.elasticsearch.autoconfigure.ElasticsearchVectorStoreProperties;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UploadServiceImpl implements UploadService {

    private static final Logger logger = LoggerFactory.getLogger(UploadServiceImpl.class);

    private final VectorStore vectorStore;

    public UploadServiceImpl(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Override
    public List<Document> pdfUpload(Resource resource) {
        logger.info("start read pdf file");
        PagePdfDocumentReader pagePdfDocumentReader = new PagePdfDocumentReader(resource); // 只可以传pdf格式文件
        List<Document> pdfDocument = pagePdfDocumentReader.read();
        logger.info("start token text splitter");
        TokenTextSplitter tokenTextSplitter = TokenTextSplitter.builder()
                .withChunkSize(1024)
                .withMinChunkSizeChars(100)
                .withMinChunkLengthToEmbed(10)
                .withMaxNumChunks(5000)
                .withKeepSeparator(false)
                .build();
        return tokenTextSplitter.split(pdfDocument);
    }

    @Override
    public List<String> search(String query) {
        logger.info("search begin");
        List<Document> search = vectorStore.similaritySearch(SearchRequest
                .builder()
                .query(query)
                .topK(5)
                .build());
        return search.stream().map(Document::getText).toList();
    }
}
