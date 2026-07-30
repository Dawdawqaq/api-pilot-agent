package com.dochelper.compatibility;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * 阶段 0 使用的工具集合。
 */
public final class CalculatorTools {

    /**
     * 计算两个整数之和。
     *
     * @param left 左操作数
     * @param right 右操作数
     * @return 计算结果
     */
    @Tool(description = "计算两个整数之和")
    public int add(
            @ToolParam(description = "左操作数") int left,
            @ToolParam(description = "右操作数") int right
    ) {
        return left + right;
    }
}
