package com.dochelper.agent.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.dochelper.openapi.domain.ApiEndpoint;
import org.springframework.stereotype.Component;

/**
 * 根据用户目标筛选并排序 OpenAPI 候选接口。
 *
 * <p>评分同时考虑业务名词、操作意图、列表或详情形态以及用户显式给出的路径，
 * 避免所有接口零分后仅按路径字典序截断。</p>
 */
@Component
public class OpenApiCandidateSelector {

    private static final Pattern WORD_PATTERN = Pattern.compile("[\\p{IsHan}]+|[a-zA-Z0-9]+");
    private static final Set<String> GENERIC_TERMS = Set.of(
            "查询", "最新", "列表", "验证", "接口", "返回", "包含", "数据",
            "状态", "状态码", "获取", "分页", "请求", "响应", "调用", "检查",
            "api", "http", "json", "get", "post", "put", "patch", "delete"
    );
    private static final Map<String, Set<String>> BUSINESS_CONCEPTS = Map.ofEntries(
            Map.entry("health", Set.of("健康", "存活", "health", "ready", "status")),
            Map.entry("version", Set.of("版本", "version", "build")),
            Map.entry("login", Set.of("登录", "认证", "鉴权", "login", "signin", "auth")),
            Map.entry("user", Set.of("用户", "账号", "成员", "user", "account", "member")),
            Map.entry("role", Set.of("角色", "权限组", "role")),
            Map.entry("audit", Set.of("审计", "操作记录", "audit")),
            Map.entry("product", Set.of("商品", "产品", "货品", "product", "goods", "item")),
            Map.entry("category", Set.of("商品分类", "分类", "类目", "category", "categories")),
            Map.entry("cart", Set.of("购物车", "cart", "basket")),
            Map.entry("order", Set.of("订单", "下单", "order")),
            Map.entry("payment", Set.of("支付", "付款", "payment", "pay")),
            Map.entry("shipment", Set.of("物流", "配送", "运单", "shipment", "shipping", "delivery")),
            Map.entry("coupon", Set.of("优惠券", "代金券", "coupon", "voucher")),
            Map.entry("inventory", Set.of("库存", "存量", "inventory", "stock")),
            Map.entry("post", Set.of("帖子", "文章", "post", "article")),
            Map.entry("comment", Set.of("评论", "回复", "comment", "reply")),
            Map.entry("bookmark", Set.of("收藏", "书签", "bookmark", "favorite"))
    );
    private static final Map<String, Set<String>> ACTION_CONCEPTS = Map.ofEntries(
            Map.entry("read", Set.of("查询", "查看", "获取", "读取", "检查", "验证", "get", "find", "read", "search")),
            Map.entry("list", Set.of("列表", "列出", "浏览", "分页", "全部", "list", "page", "browse")),
            Map.entry("detail", Set.of("详情", "指定", "单个", "detail", "byid")),
            Map.entry("create", Set.of("创建", "新增", "发布", "加入", "发起", "create", "add")),
            Map.entry("update", Set.of("修改", "更新", "调整", "变更", "update", "patch", "replace")),
            Map.entry("delete", Set.of("删除", "移除", "delete", "remove")),
            Map.entry("cancel", Set.of("取消", "撤销", "cancel")),
            Map.entry("pay", Set.of("支付", "付款", "pay")),
            Map.entry("login", Set.of("登录", "认证", "login", "signin"))
    );

    /**
     * 选择与目标最相关的接口；有正向匹配时不再用无关接口补足数量。
     *
     * @param goal 用户目标
     * @param endpoints 当前生效的接口目录
     * @param limit 最大候选数
     * @return 已按相关度排序的候选接口
     */
    public List<RankedEndpoint> select(String goal, List<ApiEndpoint> endpoints, int limit) {
        String normalizedGoal = normalize(goal);
        Set<String> goalTerms = meaningfulTerms(goal);
        List<RankedEndpoint> ranked = endpoints.stream()
                .map(endpoint -> rank(endpoint, normalizedGoal, goalTerms))
                .sorted(Comparator.comparingInt(RankedEndpoint::score).reversed()
                        .thenComparing(candidate -> candidate.endpoint().path())
                        .thenComparing(candidate -> candidate.endpoint().httpMethod()))
                .toList();
        int safeLimit = Math.max(1, limit);
        boolean hasPositiveMatch = ranked.stream().anyMatch(candidate -> candidate.score() > 0);
        return ranked.stream()
                .filter(candidate -> !hasPositiveMatch || candidate.score() > 0)
                .limit(safeLimit)
                .toList();
    }

    private RankedEndpoint rank(ApiEndpoint endpoint, String goal, Set<String> goalTerms) {
        int score = 0;
        List<String> reasons = new ArrayList<>();
        Set<String> matchedTerms = new LinkedHashSet<>();
        String path = normalize(endpoint.path());
        String operationId = normalize(endpoint.operationId());
        String summary = normalize(endpoint.summary());
        String searchable = String.join(" ", path, operationId, summary,
                normalize(endpoint.description()), normalize(endpoint.tagsJson()));
        boolean directMatch = false;

        if (!path.isBlank() && goal.contains(path)) {
            score += 100;
            reasons.add("目标显式包含接口路径");
            directMatch = true;
        }
        if (!summary.isBlank() && goal.contains(summary)) {
            score += 50;
            reasons.add("目标完整包含接口摘要");
            directMatch = true;
        }
        if (!operationId.isBlank() && goal.contains(operationId)) {
            score += 40;
            reasons.add("目标包含 operationId");
            directMatch = true;
        }

        Set<String> summaryTerms = meaningfulTerms(summary);
        Set<String> pathTerms = meaningfulTerms(path + " " + operationId);
        for (String term : goalTerms) {
            if (summaryTerms.contains(term)) {
                score += 12;
                matchedTerms.add(term);
            } else if (pathTerms.contains(term)) {
                score += 9;
                matchedTerms.add(term);
            } else if (searchable.contains(term)) {
                score += 4;
                matchedTerms.add(term);
            }
        }
        if (!matchedTerms.isEmpty()) {
            reasons.add("业务词匹配：" + String.join("、", matchedTerms));
        }

        Set<String> goalConcepts = concepts(goal, BUSINESS_CONCEPTS);
        Set<String> endpointConcepts = concepts(searchable, BUSINESS_CONCEPTS);
        Set<String> matchedConcepts = intersection(goalConcepts, endpointConcepts);
        if (!matchedConcepts.isEmpty()) {
            score += matchedConcepts.stream()
                    .mapToInt(concept -> 18 + compoundAliasBonus(goal, BUSINESS_CONCEPTS.get(concept)))
                    .sum();
            reasons.add("中英文业务概念匹配：" + String.join("、", matchedConcepts));
        }

        Set<String> goalActions = concepts(goal, ACTION_CONCEPTS);
        Set<String> endpointActions = concepts(operationId + " " + summary, ACTION_CONCEPTS);
        Set<String> matchedActions = intersection(goalActions, endpointActions);
        boolean businessMatch = directMatch || !matchedTerms.isEmpty() || !matchedConcepts.isEmpty();
        if ((businessMatch || goalConcepts.isEmpty()) && !matchedActions.isEmpty()) {
            score += matchedActions.size() * 14;
            reasons.add("操作意图匹配：" + String.join("、", matchedActions));
        }

        boolean semanticMatch = businessMatch;
        if (semanticMatch || goalTerms.isEmpty()) {
            String method = endpoint.httpMethod().toUpperCase(Locale.ROOT);
            if (readIntent(goal) && "GET".equals(method)) {
                score += 15;
                reasons.add("读取意图匹配 GET");
            }
            if (writeIntent(goal) && Set.of("POST", "PUT", "PATCH", "DELETE").contains(method)) {
                score += 15;
                reasons.add("写入意图匹配 " + method);
            }

            boolean pathRequiresIdentifier = path.contains("{");
            if (listIntent(goal)) {
                if (!pathRequiresIdentifier) {
                    score += 8;
                    reasons.add("列表目标匹配无路径变量接口");
                } else {
                    score -= 8;
                }
                if (containsAny(summary, "列表", "分页", "全部")) {
                    score += 10;
                    reasons.add("接口摘要具有列表语义");
                }
            }
            if (detailIntent(goal)) {
                if (pathRequiresIdentifier) {
                    score += 8;
                    reasons.add("详情目标匹配路径变量接口");
                }
                if (containsAny(summary, "详情", "指定", "单个")) {
                    score += 10;
                    reasons.add("接口摘要具有详情语义");
                }
            }
        }
        return new RankedEndpoint(endpoint, score, List.copyOf(matchedTerms), List.copyOf(reasons));
    }

    private Set<String> meaningfulTerms(String value) {
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        Matcher matcher = WORD_PATTERN.matcher(normalize(value));
        while (matcher.find()) {
            String token = matcher.group();
            if (token.codePoints().allMatch(this::isHan)) {
                if (token.length() <= 2) {
                    addTerm(terms, token);
                } else {
                    for (int index = 0; index < token.length() - 1; index++) {
                        addTerm(terms, token.substring(index, index + 2));
                    }
                }
            } else {
                addTerm(terms, token);
            }
        }
        return terms;
    }

    private void addTerm(Set<String> terms, String term) {
        if (term.length() >= 2 && !GENERIC_TERMS.contains(term)) {
            terms.add(term);
        }
    }

    private Set<String> concepts(String value, Map<String, Set<String>> dictionary) {
        String normalized = normalize(value);
        LinkedHashSet<String> concepts = new LinkedHashSet<>();
        dictionary.forEach((concept, aliases) -> {
            if (aliases.stream().anyMatch(normalized::contains)) {
                concepts.add(concept);
            }
        });
        return concepts;
    }

    private Set<String> intersection(Set<String> left, Set<String> right) {
        LinkedHashSet<String> result = new LinkedHashSet<>(left);
        result.retainAll(right);
        return result;
    }

    private int compoundAliasBonus(String value, Set<String> aliases) {
        return aliases.stream()
                .filter(alias -> alias.codePoints().allMatch(this::isHan))
                .filter(alias -> value.contains(alias))
                .mapToInt(alias -> Math.max(0, alias.length() - 2) * 4)
                .max()
                .orElse(0);
    }

    private boolean isHan(int codePoint) {
        return Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN;
    }

    private boolean readIntent(String goal) {
        return containsAny(goal, "查询", "获取", "列表", "详情", "读取", "检查", "验证");
    }

    private boolean writeIntent(String goal) {
        return containsAny(goal, "创建", "新增", "发布", "修改", "更新", "删除", "提交");
    }

    private boolean listIntent(String goal) {
        return containsAny(goal, "列表", "分页", "全部", "最新", "搜索");
    }

    private boolean detailIntent(String goal) {
        return containsAny(goal, "详情", "指定", "单个", "根据id", "根据 id");
    }

    private boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
    }

    /**
     * 候选接口及其可解释评分。
     */
    public record RankedEndpoint(
            ApiEndpoint endpoint,
            int score,
            List<String> matchedTerms,
            List<String> reasons
    ) {
    }
}
