package com.andrewproject.filechat.service.impl;

import com.andrewproject.filechat.service.UploadService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.ParagraphPdfDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UploadServiceImpl implements UploadService {

    private static final Logger logger = LoggerFactory.getLogger(UploadServiceImpl.class);

    @Override
    public List<Document> pdfUpload(Resource resource) {
        logger.info("start read pdf file");
        PagePdfDocumentReader pagePdfDocumentReader = new PagePdfDocumentReader(resource); // 只可以传pdf格式文件
        List<Document> pdfDocument = pagePdfDocumentReader.read();
        logger.info("start token text splitter");
        TokenTextSplitter tokenTextSplitter = TokenTextSplitter.builder()
                // 每个文本块的目标token数量
                .withChunkSize(800)
                // 每个文本块的最小字符数
                .withMinChunkSizeChars(350)
                // 丢弃小于此长度的文本块
                .withMinChunkLengthToEmbed(5)
                // 文本中生成的最大块数
                .withMaxNumChunks(10000)
                // 是否保留分隔符
                .withKeepSeparator(true)
                .build();
        return tokenTextSplitter.split(pdfDocument);
    }
}
