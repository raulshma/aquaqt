import { Streamdown } from "streamdown";
import "streamdown/styles.css";

interface StreamingMarkdownProps {
  content: string;
  isStreaming?: boolean;
}

export function StreamingMarkdown({
  content,
  isStreaming = false,
}: StreamingMarkdownProps) {
  return (
    <Streamdown animated isAnimating={isStreaming}>
      {content}
    </Streamdown>
  );
}
