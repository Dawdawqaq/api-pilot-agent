package com.dochelper.retrieval.application;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * 提取接口路径、错误码、英文词和中文二元词，供 MySQL 精确召回。
 */
@Component
public class KeywordTokenizer {

    private static final Pattern TOKEN_PATTERN = Pattern.compile(
            "[/A-Za-z0-9_.:-]{2,}|[\\p{IsHan}]{2,}"
    );
    private static final int MAX_TERMS = 12;

    public List<String> tokenize(String query) {
        Set<String> terms = new LinkedHashSet<>();
        Matcher matcher = TOKEN_PATTERN.matcher(query);
        while (matcher.find() && terms.size() < MAX_TERMS) {
            String token = matcher.group();
            if (token.codePoints().allMatch(this::isHan)) {
                if (token.length() <= 4) {
                    terms.add(token);
                }
                for (int index = 0; index < token.length() - 1 && terms.size() < MAX_TERMS; index++) {
                    terms.add(token.substring(index, index + 2));
                }
            } else {
                terms.add(token);
            }
        }
        return List.copyOf(terms);
    }

    private boolean isHan(int codePoint) {
        return Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN;
    }
}
