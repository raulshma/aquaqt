import { useCallback, useMemo } from "react";
import { Linking } from "react-native";
import { EnrichedMarkdownText } from "react-native-enriched-markdown";
import { useTheme } from "react-native-paper";

interface StreamingMarkdownProps {
  content: string;
  isStreaming?: boolean;
}

/**
 * Native markdown streaming renderer for assistant replies.
 * Uses `react-native-enriched-markdown` with streaming tail animation.
 */
export function StreamingMarkdown({
  content,
  isStreaming = false,
}: StreamingMarkdownProps) {
  const theme = useTheme();

  const markdownStyle = useMemo(
    () => ({
      paragraph: {
        color: theme.colors.onSurface,
        fontSize: 16,
        lineHeight: 22,
        marginBottom: 8,
      },
      h1: {
        color: theme.colors.onSurface,
        fontSize: 24,
        fontWeight: "700",
        marginBottom: 10,
      },
      h2: {
        color: theme.colors.onSurface,
        fontSize: 20,
        fontWeight: "700",
        marginBottom: 8,
      },
      h3: {
        color: theme.colors.onSurface,
        fontSize: 18,
        fontWeight: "700",
        marginBottom: 6,
      },
      blockquote: {
        backgroundColor: theme.colors.surfaceVariant,
        borderColor: theme.colors.outline,
        borderWidth: 1,
        gapWidth: 12,
        marginBottom: 10,
        marginTop: 2,
      },
      list: {
        color: theme.colors.onSurface,
        gapWidth: 6,
        marginBottom: 8,
        marginLeft: 12,
      },
      codeBlock: {
        backgroundColor: theme.colors.surfaceVariant,
        borderColor: theme.colors.outline,
        borderRadius: 8,
        borderWidth: 1,
        color: theme.colors.onSurface,
        fontFamily: "monospace",
        padding: 12,
        marginBottom: 8,
        marginTop: 2,
      },
      code: {
        backgroundColor: theme.colors.surfaceVariant,
        borderColor: theme.colors.outline,
        color: theme.colors.onSurface,
        fontFamily: "monospace",
      },
      link: {
        color: theme.colors.primary,
        underline: true,
      },
      strong: {
        color: theme.colors.onSurface,
      },
      em: {
        color: theme.colors.onSurface,
      },
      taskList: {
        borderColor: theme.colors.outline,
        checkedColor: theme.colors.primary,
        checkedTextColor: theme.colors.onSurface,
        checkedStrikethrough: true,
        checkboxBorderRadius: 4,
        checkboxSize: 18,
        checkmarkColor: theme.colors.onPrimary,
      },
      table: {
        marginBottom: 8,
      },
    }),
    [theme],
  );

  const handleLinkPress = useCallback(({ url }: { url: string }) => {
    void Linking.openURL(url);
  }, []);

  return (
    <EnrichedMarkdownText
      markdown={content}
      flavor="commonmark"
      streamingAnimation={isStreaming}
      md4cFlags={{ latexMath: false }}
      markdownStyle={markdownStyle}
      onLinkPress={handleLinkPress}
    />
  );
}
