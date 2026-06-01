package com.kaer.service;


import com.kaer.model.request.CreateAgentRequest;
import com.kaer.model.request.UpdateAgentRequest;
import com.kaer.model.response.CreateAgentResponse;
import com.kaer.model.response.GetAgentsResponse;

public interface AgentFacadeService {
    GetAgentsResponse getAgents();

    CreateAgentResponse createAgent(CreateAgentRequest request);

    void deleteAgent(String agentId);

    void updateAgent(String agentId, UpdateAgentRequest request);
}
