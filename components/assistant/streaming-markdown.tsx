import { Text } from "react-native-paper";

interface StreamingMarkdownProps {
  content: string;
  isStreaming?: boolean;
}

/**
 * Native fallback renderer.
 * Streamdown is web-first, so we keep native behavior lightweight.
 */
export function StreamingMarkdown({ content }: StreamingMarkdownProps) {
  return <Text variant="bodyMedium">{content}</Text>;
}
