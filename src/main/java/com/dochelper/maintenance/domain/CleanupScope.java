package com.dochelper.maintenance.domain;

import java.time.LocalDateTime;
import java.util.List;

/** 清理仅包含已经结束的确定结果；待核验任务和关联执行始终保留。 */
public record CleanupScope(LocalDateTime before, List<Long> tasks, List<Long> reports, List<Long> executions) { }
