package com.simon.MindCrew.service;

import com.simon.MindCrew.entity.McpAuditLog;
import com.simon.MindCrew.entity.McpClient;
import com.simon.MindCrew.entity.McpToolPolicy;
import com.simon.MindCrew.entity.McpToolRegistry;
import com.simon.MindCrew.mapper.McpAuditLogMapper;
import com.simon.MindCrew.mapper.McpClientMapper;
import com.simon.MindCrew.mapper.McpToolPolicyMapper;
import com.simon.MindCrew.mapper.McpToolRegistryMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class McpGovernanceServiceTest {

    @Test
    void disabledToolPolicyBlocksAndWritesAuditLog() {
        Fixture fixture = fixture();
        McpToolPolicy policy = policy("public-mcp", "store_memory", 0, 10, null);
        when(fixture.policyMapper.selectOne(any())).thenReturn(policy);

        McpGovernanceService.Decision decision = fixture.service.checkAndStart(
                "public-mcp", "user-1", "store_memory", Map.of("userId", "user-1"));

        assertFalse(decision.allowed());
        assertEquals("policy_disabled", decision.reason());
        assertAudit(fixture.auditLogMapper, "BLOCK", "policy_disabled");
    }

    @Test
    void rateLimitBlocksCallsOverPolicyLimit() {
        Fixture fixture = fixture();
        McpToolPolicy policy = policy("public-mcp", "web_search", 1, 1, null);
        when(fixture.policyMapper.selectOne(any())).thenReturn(policy);

        McpGovernanceService.Decision first = fixture.service.checkAndStart(
                "public-mcp", "user-1", "web_search", Map.of("query", "MindCrew"));
        McpGovernanceService.Decision second = fixture.service.checkAndStart(
                "public-mcp", "user-1", "web_search", Map.of("query", "MindCrew"));

        assertTrue(first.allowed());
        assertFalse(second.allowed());
        assertTrue(second.reason().startsWith("rate_limited_1"));
    }

    @Test
    void kbScopeBlocksOutOfScopeKnowledgeBaseAccess() {
        Fixture fixture = fixture();
        McpToolPolicy policy = policy("public-mcp", "doc_search", 1, 30, "[1,2]");
        when(fixture.policyMapper.selectOne(any())).thenReturn(policy);

        McpGovernanceService.Decision decision = fixture.service.checkAndStart(
                "public-mcp", "user-1", "doc_search", Map.of("kbIds", List.of(3L)));

        assertFalse(decision.allowed());
        assertEquals("kb_scope_denied", decision.reason());
        assertAudit(fixture.auditLogMapper, "BLOCK", "kb_scope_denied");
    }

    private Fixture fixture() {
        McpClientMapper clientMapper = mock(McpClientMapper.class);
        McpToolPolicyMapper policyMapper = mock(McpToolPolicyMapper.class);
        McpAuditLogMapper auditLogMapper = mock(McpAuditLogMapper.class);
        McpToolRegistryMapper registryMapper = mock(McpToolRegistryMapper.class);

        when(clientMapper.selectOne(any())).thenReturn(client("public-mcp", "active", 60));
        when(registryMapper.selectOne(any())).thenReturn(tool("active"));

        return new Fixture(new McpGovernanceService(clientMapper, policyMapper, auditLogMapper, registryMapper),
                policyMapper, auditLogMapper);
    }

    private McpClient client(String clientId, String status, int rateLimit) {
        McpClient client = new McpClient();
        client.setClientId(clientId);
        client.setStatus(status);
        client.setDefaultRateLimitPerMinute(rateLimit);
        return client;
    }

    private McpToolPolicy policy(String clientId, String toolName, int enabled, int rateLimit, String kbScopeJson) {
        McpToolPolicy policy = new McpToolPolicy();
        policy.setClientId(clientId);
        policy.setToolName(toolName);
        policy.setEnabled(enabled);
        policy.setRateLimitPerMinute(rateLimit);
        policy.setKbScopeJson(kbScopeJson);
        return policy;
    }

    private McpToolRegistry tool(String status) {
        McpToolRegistry tool = new McpToolRegistry();
        tool.setName("doc_search");
        tool.setStatus(status);
        return tool;
    }

    private void assertAudit(McpAuditLogMapper auditLogMapper, String status, String reason) {
        ArgumentCaptor<McpAuditLog> captor = ArgumentCaptor.forClass(McpAuditLog.class);
        verify(auditLogMapper).insert(captor.capture());
        assertEquals(status, captor.getValue().getStatus());
        assertEquals(reason, captor.getValue().getReason());
    }

    private record Fixture(McpGovernanceService service,
                           McpToolPolicyMapper policyMapper,
                           McpAuditLogMapper auditLogMapper) {
    }
}
