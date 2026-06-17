package com.kaer.service;

import com.kaer.model.request.CreateKnowledgeBaseRequest;
import com.kaer.model.request.UpdateKnowledgeBaseRequest;
import com.kaer.model.response.CreateKnowledgeBaseResponse;
import com.kaer.model.response.GetKnowledgeBasesResponse;

public interface KnowledgeBaseFacadeService {
    CreateKnowledgeBaseResponse createKnowledgeBase(CreateKnowledgeBaseRequest request);

    GetKnowledgeBasesResponse getKnowledgeBases();

    void deleteKnowledgeBase(String knowledgeBaseId);

    void updateKnowledgeBase(String knowledgeBaseId, UpdateKnowledgeBaseRequest request);
}
