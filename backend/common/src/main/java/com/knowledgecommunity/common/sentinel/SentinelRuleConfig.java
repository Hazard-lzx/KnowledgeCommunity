package com.knowledgecommunity.common.sentinel;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Feign 熔断规则（代码配置，无 Dashboard 依赖）：
 * 全部跨服务内部接口资源——异常比例 > 50%（含超时异常），统计窗口 10s，
 * 最小请求数 5，熔断 10s 后半开探测。上游 service 层已有空值降级逻辑兜底。
 * 资源名与 Sentinel Feign 集成的默认格式一致：方法:http://服务名/路径模板
 */
@Configuration
public class SentinelRuleConfig {

    private static final Logger log = LoggerFactory.getLogger(SentinelRuleConfig.class);

    private static final List<String> FEIGN_RESOURCES = List.of(
            // feed-service → article / auth
            "GET:http://article-service/api/internal/articles/page",
            // search-service → article / auth
            "GET:http://article-service/api/internal/articles/batch",
            "GET:http://auth-service/api/internal/users/batch",
            "GET:http://auth-service/api/internal/users/{id}/following-ids",
            // article-service → auth
            "GET:http://auth-service/api/internal/users/follow/check",
            // auth-service → article
            "GET:http://article-service/api/internal/articles/stats/{userId}"
    );

    @PostConstruct
    public void initDegradeRules() {
        List<DegradeRule> rules = new ArrayList<>();
        for (String resource : FEIGN_RESOURCES) {
            DegradeRule rule = new DegradeRule(resource)
                    .setGrade(RuleConstant.DEGRADE_GRADE_EXCEPTION_RATIO)
                    .setCount(0.5)
                    .setTimeWindow(10)
                    .setMinRequestAmount(5)
                    .setStatIntervalMs(10_000);
            rules.add(rule);
        }
        DegradeRuleManager.loadRules(rules);
        log.info("Sentinel 熔断规则已加载: {} 条（异常比例>50% 熔断 10s）", rules.size());
    }
}
