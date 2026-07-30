package com.dochelper.knowledge.application;

import java.util.ArrayList;
import java.util.List;

import com.dochelper.knowledge.config.KnowledgeProperties;
import com.dochelper.knowledge.domain.ChunkDraft;
import org.springframework.stereotype.Component;

/**
 * 按 Markdown 标题和段落切分文本，并在长段落间保留重叠上下文。
 */
@Component
public class SectionAwareChunker {

    private final KnowledgeProperties properties;

    public SectionAwareChunker(KnowledgeProperties properties) {
        this.properties = properties;
    }

    public List<ChunkDraft> chunk(String title, String text) {
        List<Section> sections = splitSections(title, text);
        List<ChunkDraft> chunks = new ArrayList<>();
        int index = 0;
        for (Section section : sections) {
            String content = section.content().trim();
            int start = 0;
            while (start < content.length()) {
                int desiredEnd = Math.min(content.length(), start + properties.chunkSize());
                int end = findBoundary(content, start, desiredEnd);
                String part = content.substring(start, end).trim();
                if (!part.isBlank()) {
                    chunks.add(new ChunkDraft(index++, section.title(), part));
                }
                if (end >= content.length()) {
                    break;
                }
                start = Math.max(start + 1, end - properties.chunkOverlap());
            }
        }
        return List.copyOf(chunks);
    }

    private List<Section> splitSections(String documentTitle, String text) {
        List<Section> sections = new ArrayList<>();
        String currentTitle = documentTitle;
        StringBuilder currentContent = new StringBuilder();
        for (String line : text.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.matches("^#{1,6}\\s+.+")) {
                appendSection(sections, currentTitle, currentContent);
                currentTitle = trimmed.replaceFirst("^#{1,6}\\s+", "").trim();
                currentContent = new StringBuilder();
            } else {
                if (!currentContent.isEmpty()) {
                    currentContent.append('\n');
                }
                currentContent.append(line);
            }
        }
        appendSection(sections, currentTitle, currentContent);
        return sections.isEmpty() ? List.of(new Section(documentTitle, text)) : sections;
    }

    private void appendSection(List<Section> sections, String title, StringBuilder content) {
        if (!content.toString().isBlank()) {
            sections.add(new Section(title, content.toString()));
        }
    }

    private int findBoundary(String content, int start, int desiredEnd) {
        if (desiredEnd >= content.length()) {
            return content.length();
        }
        int minimum = start + properties.chunkSize() / 2;
        int paragraph = content.lastIndexOf("\n\n", desiredEnd);
        if (paragraph >= minimum) {
            return paragraph;
        }
        int sentence = Math.max(
                content.lastIndexOf('。', desiredEnd),
                content.lastIndexOf('\n', desiredEnd)
        );
        return sentence >= minimum ? sentence + 1 : desiredEnd;
    }

    private record Section(String title, String content) {
    }
}
