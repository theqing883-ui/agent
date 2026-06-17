package com.kaer.service;

import com.kaer.model.request.CreateDocumentRequest;
import com.kaer.model.request.UpdateDocumentRequest;
import com.kaer.model.response.CreateDocumentResponse;
import com.kaer.model.response.GetDocumentsResponse;
import org.springframework.web.multipart.MultipartFile;

public interface DocumentFacadeService {
    CreateDocumentResponse uploadDocument(String kbId, MultipartFile file);

    GetDocumentsResponse getDocuments(String kbId);

    GetDocumentsResponse getDocumentsByKbId(String kbId);

    void deleteDocument(String documentId);

    void updateDocument(String documentId, UpdateDocumentRequest request);

    CreateDocumentResponse createDocument(CreateDocumentRequest request);
}
