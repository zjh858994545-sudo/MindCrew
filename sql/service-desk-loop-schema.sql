CREATE TABLE IF NOT EXISTS `service_ticket` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `ticket_no` varchar(40) NOT NULL COMMENT 'Business ticket number',
  `title` varchar(200) NOT NULL COMMENT 'Ticket title',
  `requester` varchar(80) DEFAULT NULL COMMENT 'Requester name',
  `requester_role` varchar(80) DEFAULT NULL COMMENT 'Requester role',
  `department` varchar(80) DEFAULT NULL COMMENT 'Department',
  `priority` varchar(20) NOT NULL DEFAULT 'P2' COMMENT 'P0/P1/P2/P3',
  `channel` varchar(40) NOT NULL DEFAULT 'web' COMMENT 'web/im/email/api',
  `status` varchar(32) NOT NULL DEFAULT 'new' COMMENT 'new/ai_drafted/needs_review/accepted/rejected',
  `category` varchar(64) NOT NULL COMMENT 'HR/IT/FINANCE/SECURITY/SALES/GENERAL',
  `question` text NOT NULL COMMENT 'Original business question',
  `expected_outcome` varchar(500) DEFAULT NULL COMMENT 'Expected outcome from requester',
  `kb_scope` varchar(255) DEFAULT NULL COMMENT 'Simulated knowledge scope',
  `confidence` decimal(5,2) DEFAULT NULL COMMENT 'AI answer confidence',
  `answer_draft` text COMMENT 'AI generated answer draft',
  `final_answer` text COMMENT 'Human accepted/revised final answer',
  `source_summary` text COMMENT 'Source citations or retrieval summary',
  `ai_trace_id` varchar(80) DEFAULT NULL COMMENT 'Trace id for agent/retrieval chain',
  `accepted` tinyint NOT NULL DEFAULT 0 COMMENT 'Whether human accepted this answer',
  `feedback_status` varchar(40) NOT NULL DEFAULT 'none' COMMENT 'none/pending_human_review/golden_pair_candidate/kb_gap',
  `golden_pair_id` bigint DEFAULT NULL COMMENT 'Synced qa_golden_pair.id',
  `resolution_owner` varchar(80) DEFAULT NULL COMMENT 'Human owner',
  `resolved_at` datetime DEFAULT NULL COMMENT 'Resolved time',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT 'Logical delete flag',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_service_ticket_no` (`ticket_no`),
  KEY `idx_service_ticket_status` (`status`),
  KEY `idx_service_ticket_category` (`category`),
  KEY `idx_service_ticket_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Enterprise service desk demo tickets';

CREATE TABLE IF NOT EXISTS `service_ticket_event` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `ticket_id` bigint NOT NULL COMMENT 'service_ticket.id',
  `event_type` varchar(48) NOT NULL COMMENT 'CREATED/AI_DRAFTED/ACCEPTED/REJECTED/NEEDS_REVIEW',
  `actor` varchar(80) NOT NULL DEFAULT 'system' COMMENT 'Operator',
  `detail` text COMMENT 'Event detail',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
  PRIMARY KEY (`id`),
  KEY `idx_service_ticket_event_ticket_id` (`ticket_id`),
  KEY `idx_service_ticket_event_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Enterprise service desk ticket timeline';

CREATE TABLE IF NOT EXISTS `service_knowledge_gap` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `ticket_id` bigint NOT NULL COMMENT 'service_ticket.id',
  `ticket_no` varchar(40) NOT NULL COMMENT 'Business ticket number',
  `title` varchar(200) NOT NULL COMMENT 'Ticket title',
  `category` varchar(64) NOT NULL COMMENT 'HR/IT/FINANCE/SECURITY/SALES/GENERAL',
  `priority` varchar(20) NOT NULL DEFAULT 'P2' COMMENT 'P0/P1/P2/P3',
  `reason` text COMMENT 'Why the answer was rejected or which knowledge is missing',
  `source_summary` text COMMENT 'Original retrieval summary for diagnosis',
  `status` varchar(32) NOT NULL DEFAULT 'open' COMMENT 'open/resolved/ignored',
  `owner` varchar(80) DEFAULT NULL COMMENT 'Knowledge ops owner',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT 'Logical delete flag',
  PRIMARY KEY (`id`),
  KEY `idx_service_knowledge_gap_ticket_id` (`ticket_id`),
  KEY `idx_service_knowledge_gap_status` (`status`),
  KEY `idx_service_knowledge_gap_category` (`category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Knowledge gaps raised by rejected service desk answers';

INSERT INTO `service_ticket` (
  `ticket_no`, `title`, `requester`, `requester_role`, `department`, `priority`, `channel`,
  `status`, `category`, `question`, `expected_outcome`, `kb_scope`, `feedback_status`
)
SELECT
  'MC-SD-2026-0001',
  '试用期员工如何申请年假',
  '林嘉',
  '新员工',
  '研发平台部',
  'P2',
  'web',
  'new',
  'HR',
  '我刚入职 2 个月，7 月想请 2 天年假。试用期能不能申请？流程需要谁审批？',
  '给出可执行流程、审批人和注意事项',
  'HR-Handbook, Leave-SOP',
  'none'
WHERE NOT EXISTS (SELECT 1 FROM `service_ticket` WHERE `ticket_no` = 'MC-SD-2026-0001');

INSERT INTO `service_ticket` (
  `ticket_no`, `title`, `requester`, `requester_role`, `department`, `priority`, `channel`,
  `status`, `category`, `question`, `expected_outcome`, `kb_scope`, `feedback_status`
)
SELECT
  'MC-SD-2026-0002',
  '外包同学需要开通生产只读权限',
  '周宁',
  '项目经理',
  '交易中台',
  'P1',
  'im',
  'new',
  'SECURITY',
  '供应商外包同学需要排查线上订单问题，能不能给他开生产库只读权限？需要走什么审批？',
  '判断是否允许，给出安全审批链路和替代方案',
  'Security-Access-Policy, Database-Access-SOP',
  'none'
WHERE NOT EXISTS (SELECT 1 FROM `service_ticket` WHERE `ticket_no` = 'MC-SD-2026-0002');

INSERT INTO `service_ticket` (
  `ticket_no`, `title`, `requester`, `requester_role`, `department`, `priority`, `channel`,
  `status`, `category`, `question`, `expected_outcome`, `kb_scope`, `feedback_status`
)
SELECT
  'MC-SD-2026-0003',
  '差旅发票抬头和报销时限',
  '陈若',
  '销售运营',
  '华东销售部',
  'P2',
  'email',
  'new',
  'FINANCE',
  '上周去杭州拜访客户，高铁票和酒店发票已经拿到。公司抬头怎么填？最晚什么时候提交报销？',
  '给出发票要求、报销时限和材料清单',
  'Finance-Reimbursement-SOP, Travel-Policy',
  'none'
WHERE NOT EXISTS (SELECT 1 FROM `service_ticket` WHERE `ticket_no` = 'MC-SD-2026-0003');

INSERT INTO `service_ticket` (
  `ticket_no`, `title`, `requester`, `requester_role`, `department`, `priority`, `channel`,
  `status`, `category`, `question`, `expected_outcome`, `kb_scope`, `feedback_status`
)
SELECT
  'MC-SD-2026-0004',
  'VPN 登录失败无法访问知识库后台',
  '赵宇',
  '客服质检',
  '客户成功部',
  'P1',
  'web',
  'new',
  'IT',
  '我今天换了新电脑，VPN 一直提示 MFA 校验失败，导致进不了知识库后台审核反馈。',
  '给出排查步骤和升级路径',
  'IT-Account-SOP, VPN-MFA-Runbook',
  'none'
WHERE NOT EXISTS (SELECT 1 FROM `service_ticket` WHERE `ticket_no` = 'MC-SD-2026-0004');

INSERT INTO `service_ticket` (
  `ticket_no`, `title`, `requester`, `requester_role`, `department`, `priority`, `channel`,
  `status`, `category`, `question`, `expected_outcome`, `kb_scope`, `feedback_status`
)
SELECT
  'MC-SD-2026-0005',
  '客户要求导出全量原始日志',
  '高晨',
  '解决方案顾问',
  '政企行业部',
  'P0',
  'im',
  'new',
  'SECURITY',
  '客户要求我们导出最近 30 天全量原始访问日志用于审计，里面可能有手机号和用户 ID，可以直接发邮件给吗？',
  '给出合规判断、脱敏要求和审批路径',
  'Security-Data-Export-Policy, PII-Desensitization-SOP',
  'none'
WHERE NOT EXISTS (SELECT 1 FROM `service_ticket` WHERE `ticket_no` = 'MC-SD-2026-0005');

INSERT INTO `service_ticket` (
  `ticket_no`, `title`, `requester`, `requester_role`, `department`, `priority`, `channel`,
  `status`, `category`, `question`, `expected_outcome`, `kb_scope`, `feedback_status`
)
SELECT
  'MC-SD-2026-0006',
  '新产品试点报价口径确认',
  '许然',
  '商务经理',
  '战略客户部',
  'P3',
  'web',
  'new',
  'SALES',
  '客户想买 200 个账号做 3 个月试点，后续再转年框。试点阶段可以打几折？有没有最低合同金额？',
  '给出报价口径、审批条件和风险提醒',
  'Sales-Pricing-Playbook, Pilot-Contract-SOP',
  'none'
WHERE NOT EXISTS (SELECT 1 FROM `service_ticket` WHERE `ticket_no` = 'MC-SD-2026-0006');

INSERT INTO `service_ticket_event` (`ticket_id`, `event_type`, `actor`, `detail`)
SELECT t.`id`, 'CREATED', 'demo-seed', CONCAT('Demo ticket seeded: ', t.`ticket_no`)
FROM `service_ticket` t
WHERE t.`ticket_no` IN (
  'MC-SD-2026-0001',
  'MC-SD-2026-0002',
  'MC-SD-2026-0003',
  'MC-SD-2026-0004',
  'MC-SD-2026-0005',
  'MC-SD-2026-0006'
)
AND NOT EXISTS (
  SELECT 1 FROM `service_ticket_event` e
  WHERE e.`ticket_id` = t.`id` AND e.`event_type` = 'CREATED'
);

INSERT INTO `kb_knowledge_base` (
  `name`, `category`, `tags`, `summary`, `category_user_set`, `description`,
  `file_url`, `file_type`, `file_size`, `chunk_count`, `status`, `user_id`, `visibility`
)
SELECT
  'MindCrew 企业服务台模拟知识',
  'service_desk',
  JSON_ARRAY('服务台', 'HR', 'IT', '财务', '安全合规', '销售商务', 'SOP'),
  '用于 MindCrew 企业知识服务台演示的合成制度知识，覆盖休假、VPN/MFA、报销、生产权限、数据导出和试点报价。',
  1,
  'Synthetic enterprise service desk knowledge for Agentic RAG demo.',
  'docs/demo-enterprise-service-desk-knowledge.md',
  'md',
  12000,
  6,
  'ready',
  1,
  'public'
WHERE NOT EXISTS (
  SELECT 1 FROM `kb_knowledge_base`
  WHERE `name` = 'MindCrew 企业服务台模拟知识' AND `deleted` = 0
);

SET @mindcrew_service_desk_kb_id := (
  SELECT `id` FROM `kb_knowledge_base`
  WHERE `name` = 'MindCrew 企业服务台模拟知识' AND `deleted` = 0
  ORDER BY `id` ASC
  LIMIT 1
);

INSERT INTO `kb_chunk` (`kb_id`, `content`, `chunk_index`, `metadata`, `vector_id`)
SELECT @mindcrew_service_desk_kb_id,
       'HR-Handbook / Leave-SOP：年假额度按入职日期折算，系统额度以 OA 展示为准。试用期员工可以申请已折算产生的年假。连续 2 天以内年假审批链路为直属上级、部门负责人；超过 2 天或涉及关键发布窗口，需要 HRBP 补充确认。请假前需要同步交接事项、项目影响和紧急联系人。',
       0,
       JSON_OBJECT('chapter', 'HR-Handbook / Leave-SOP', 'pageNumber', 1, 'contentType', 'policy'),
       CONCAT('mindcrew-sd-', @mindcrew_service_desk_kb_id, '-0')
WHERE @mindcrew_service_desk_kb_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM `kb_chunk`
    WHERE `kb_id` = @mindcrew_service_desk_kb_id AND `chunk_index` = 0
  );

INSERT INTO `kb_chunk` (`kb_id`, `content`, `chunk_index`, `metadata`, `vector_id`)
SELECT @mindcrew_service_desk_kb_id,
       'IT-Account-SOP / VPN-MFA-Runbook：新设备登录失败时，优先检查本机时间同步和 MFA 绑定状态。MFA 失败可以在账号中心自助重绑；自助重绑失败时提交 IT 工单，附员工工号、设备编号、报错截图。管理后台访问属于内部权限，需要直属主管确认。临时权限默认 7 天过期，到期自动回收。',
       1,
       JSON_OBJECT('chapter', 'IT-Account-SOP / VPN-MFA-Runbook', 'pageNumber', 2, 'contentType', 'runbook'),
       CONCAT('mindcrew-sd-', @mindcrew_service_desk_kb_id, '-1')
WHERE @mindcrew_service_desk_kb_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM `kb_chunk`
    WHERE `kb_id` = @mindcrew_service_desk_kb_id AND `chunk_index` = 1
  );

INSERT INTO `kb_chunk` (`kb_id`, `content`, `chunk_index`, `metadata`, `vector_id`)
SELECT @mindcrew_service_desk_kb_id,
       'Finance-Reimbursement-SOP / Travel-Policy：差旅报销要求发票抬头使用公司工商全称，税号以财务系统内当前法人主体为准。高铁票、酒店发票、拜访记录、出差审批单需要一起上传。差旅结束后 30 个自然日内提交报销，跨月费用尽量在当月结账日前提交。发票主体或税号错误时需要联系商家重开。',
       2,
       JSON_OBJECT('chapter', 'Finance-Reimbursement-SOP / Travel-Policy', 'pageNumber', 3, 'contentType', 'policy'),
       CONCAT('mindcrew-sd-', @mindcrew_service_desk_kb_id, '-2')
WHERE @mindcrew_service_desk_kb_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM `kb_chunk`
    WHERE `kb_id` = @mindcrew_service_desk_kb_id AND `chunk_index` = 2
  );

INSERT INTO `kb_chunk` (`kb_id`, `content`, `chunk_index`, `metadata`, `vector_id`)
SELECT @mindcrew_service_desk_kb_id,
       'Security-Access-Policy / Database-Access-SOP：外包人员默认不能直接访问生产数据库。优先使用内部员工代查、日志平台、脱敏查询平台。如确需访问，需要限定工单编号、库表范围、时间窗口、只读权限；审批链路为项目负责人、安全负责人、系统 Owner。所有生产访问必须记录审计日志，到期自动回收。',
       3,
       JSON_OBJECT('chapter', 'Security-Access-Policy / Database-Access-SOP', 'pageNumber', 4, 'contentType', 'security'),
       CONCAT('mindcrew-sd-', @mindcrew_service_desk_kb_id, '-3')
WHERE @mindcrew_service_desk_kb_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM `kb_chunk`
    WHERE `kb_id` = @mindcrew_service_desk_kb_id AND `chunk_index` = 3
  );

INSERT INTO `kb_chunk` (`kb_id`, `content`, `chunk_index`, `metadata`, `vector_id`)
SELECT @mindcrew_service_desk_kb_id,
       'Security-Data-Export-Policy / PII-Desensitization-SOP：不能通过普通邮件发送全量原始日志。包含手机号、用户 ID、IP、设备号等字段时，需要做脱敏或哈希化。数据导出遵循最小必要原则，只提供审计目标需要的字段。审批链路为业务负责人、安全负责人、法务或隐私合规。交付方式应使用受控下载链接，设置有效期、水印和访问审计。',
       4,
       JSON_OBJECT('chapter', 'Security-Data-Export-Policy / PII-Desensitization-SOP', 'pageNumber', 5, 'contentType', 'security'),
       CONCAT('mindcrew-sd-', @mindcrew_service_desk_kb_id, '-4')
WHERE @mindcrew_service_desk_kb_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM `kb_chunk`
    WHERE `kb_id` = @mindcrew_service_desk_kb_id AND `chunk_index` = 4
  );

INSERT INTO `kb_chunk` (`kb_id`, `content`, `chunk_index`, `metadata`, `vector_id`)
SELECT @mindcrew_service_desk_kb_id,
       'Sales-Pricing-Playbook / Pilot-Contract-SOP：试点报价需要同时看账号数、试点周期、转年框承诺和 API 调用量。低于标准折扣线需要区域负责人审批。试点转正式年框时，需要在合同中写清转正条件和抵扣规则。不建议口头承诺最低价，必须输出正式报价单并走商务审批。',
       5,
       JSON_OBJECT('chapter', 'Sales-Pricing-Playbook / Pilot-Contract-SOP', 'pageNumber', 6, 'contentType', 'sales'),
       CONCAT('mindcrew-sd-', @mindcrew_service_desk_kb_id, '-5')
WHERE @mindcrew_service_desk_kb_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM `kb_chunk`
    WHERE `kb_id` = @mindcrew_service_desk_kb_id AND `chunk_index` = 5
  );

UPDATE `kb_knowledge_base`
SET `chunk_count` = (
      SELECT COUNT(*) FROM `kb_chunk`
      WHERE `kb_id` = @mindcrew_service_desk_kb_id
    ),
    `status` = 'ready'
WHERE `id` = @mindcrew_service_desk_kb_id;

SET @service_ticket_golden_pair_col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'service_ticket'
    AND COLUMN_NAME = 'golden_pair_id'
);

SET @service_ticket_golden_pair_ddl := IF(
  @service_ticket_golden_pair_col_exists = 0,
  'ALTER TABLE `service_ticket` ADD COLUMN `golden_pair_id` bigint NULL COMMENT ''Synced qa_golden_pair.id'' AFTER `feedback_status`',
  'SELECT 1'
);

PREPARE service_ticket_golden_pair_stmt FROM @service_ticket_golden_pair_ddl;
EXECUTE service_ticket_golden_pair_stmt;
DEALLOCATE PREPARE service_ticket_golden_pair_stmt;
