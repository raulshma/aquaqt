import type { GestureResponderEvent } from "react-native";
import { Pressable, StyleSheet, View } from "react-native";
import { Chip, IconButton, Text, useTheme } from "react-native-paper";
import { StreamingMarkdown } from "./streaming-markdown";
import { AssistantTelemetry } from "./assistant-telemetry";
import { InlineActionCard } from "./inline-action-card";
import type { AssistantChatMessage, AssistantDetectedAction } from "@/types/assistant";

interface ChatMessageProps {
  message: AssistantChatMessage;
  isStreaming: boolean;
  linkedActions: AssistantDetectedAction[];
  activeDetectedActions: AssistantDetectedAction[];
  onToggleApproval: (actionId: string, approved: boolean) => void;
  onReviewActions: () => void;
  onCopyMessage: () => void;
  onReuseMessage?: () => void;
  onRetryFailed?: () => void;
  onRegenerate?: () => void;
  onRememberMessage?: () => void;
  onForgetMemory?: () => void;
  onOpenMessageMenu?: (event: GestureResponderEvent) => void;
  isRemembered?: boolean;
  isMemoryBusy?: boolean;
  isAsking?: boolean;
}

export function ChatMessage({
  message,
  isStreaming,
  linkedActions,
  activeDetectedActions,
  onToggleApproval,
  onReviewActions,
  onCopyMessage,
  onReuseMessage,
  onRetryFailed,
  onRegenerate,
  onRememberMessage,
  onForgetMemory,
  onOpenMessageMenu,
  isRemembered,
  isMemoryBusy,
  isAsking,
}: ChatMessageProps) {
  const theme = useTheme();
  const isUser = message.role === "user";
  const isSystem = message.role === "system";
  const isAssistant = message.role === "assistant";
  const isRetryableFailedUserMessage = isUser && message.requestFailed === true;

  return (
    <View
      style={[
        styles.messageRow,
        isUser && styles.messageRowUser,
        isSystem && styles.messageRowSystem,
      ]}
    >
      {!isUser && !isSystem ? (
        <View
          style={[
            styles.avatar,
            { backgroundColor: theme.colors.primaryContainer },
          ]}
        >
          <Text
            style={{
              fontSize: 14,
              color: theme.colors.onPrimaryContainer,
            }}
          >
            🐠
          </Text>
        </View>
      ) : null}

      <Pressable
        delayLongPress={250}
        onLongPress={isSystem || !onOpenMessageMenu ? undefined : onOpenMessageMenu}
        style={[
          styles.bubble,
          isUser && {
            backgroundColor: theme.colors.primaryContainer,
            borderBottomRightRadius: 4,
          },
          isSystem && {
            backgroundColor: theme.colors.secondaryContainer,
            alignSelf: "center",
          },
          !isUser &&
            !isSystem && {
              backgroundColor: theme.colors.surface,
              borderBottomLeftRadius: 4,
              flex: 1,
            },
        ]}
      >
        {isSystem ? (
          <Text
            variant="labelSmall"
            style={{ opacity: 0.65, marginBottom: 2 }}
          >
            SYSTEM
          </Text>
        ) : null}
        {isAssistant ? (
          <StreamingMarkdown
            content={message.content}
            isStreaming={isStreaming}
          />
        ) : (
          <Text
            variant="bodyMedium"
            style={[
              isUser && { color: theme.colors.onPrimaryContainer },
            ]}
          >
            {message.content}
          </Text>
        )}

        {isUser && message.content.trim() ? (
          <View style={styles.userMessageActionsRow}>
            <IconButton
              icon="content-copy"
              size={18}
              style={styles.inlineActionIconButton}
              onPress={onCopyMessage}
              accessibilityLabel="Copy user message"
            />
            {onReuseMessage && (
              <IconButton
                icon="message-reply-text-outline"
                size={18}
                style={styles.inlineActionIconButton}
                onPress={onReuseMessage}
                accessibilityLabel="Reuse message in composer"
              />
            )}
          </View>
        ) : null}

        {isStreaming ? (
          <View style={styles.streamingActionsRow}>
            <Text variant="labelSmall" style={styles.streamingLabel}>
              Streaming…
            </Text>
          </View>
        ) : null}

        {isRetryableFailedUserMessage ? (
          <View style={styles.failedMessageActionsRow}>
            <Chip compact icon="alert-circle-outline">
              Failed to send
            </Chip>
            {onRetryFailed && (
              <IconButton
                icon="refresh"
                size={18}
                style={styles.inlineActionIconButton}
                disabled={isAsking}
                onPress={onRetryFailed}
                accessibilityLabel="Retry message"
              />
            )}
          </View>
        ) : null}

        {isRetryableFailedUserMessage && message.requestError ? (
          <Text
            variant="labelSmall"
            style={styles.failedMessageError}
          >
            {message.requestError}
          </Text>
        ) : null}

        {isAssistant && message.responseTelemetry ? (
          <AssistantTelemetry telemetry={message.responseTelemetry} />
        ) : null}

        {isAssistant && !isStreaming && message.content.trim() ? (
          <View style={styles.memoryActionsRow}>
            <IconButton
              icon="content-copy"
              size={18}
              style={styles.inlineActionIconButton}
              onPress={onCopyMessage}
              accessibilityLabel="Copy assistant reply"
            />
            {onRegenerate && (
              <IconButton
                icon="refresh"
                size={18}
                style={styles.inlineActionIconButton}
                disabled={isAsking}
                onPress={onRegenerate}
                accessibilityLabel="Regenerate reply"
              />
            )}
            {isRemembered ? (
              <>
                <Chip compact>Saved to memory</Chip>
                {onForgetMemory && (
                  <IconButton
                    icon="bookmark-remove-outline"
                    size={18}
                    style={styles.inlineActionIconButton}
                    loading={isMemoryBusy}
                    disabled={isMemoryBusy}
                    onPress={onForgetMemory}
                    accessibilityLabel="Forget saved memory"
                  />
                )}
              </>
            ) : onRememberMessage ? (
              <IconButton
                icon="bookmark-outline"
                size={18}
                style={styles.inlineActionIconButton}
                loading={isMemoryBusy}
                disabled={isMemoryBusy}
                onPress={onRememberMessage}
                accessibilityLabel="Remember this reply"
              />
            ) : null}
          </View>
        ) : null}

        {linkedActions.length > 0 ? (
          <View style={styles.inlineActions}>
            <Text
              variant="labelSmall"
              style={{
                opacity: 0.6,
                marginBottom: 2,
                marginTop: 4,
              }}
            >
              Detected {linkedActions.length} action
              {linkedActions.length > 1 ? "s" : ""}:
            </Text>
            {linkedActions.map((action) => (
              <InlineActionCard
                key={action.id}
                action={action}
                onToggleApproval={() =>
                  onToggleApproval(action.id, !action.approved)
                }
                onReview={onReviewActions}
              />
            ))}
          </View>
        ) : null}
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  messageRow: {
    flexDirection: "row",
    alignItems: "flex-start",
    gap: 8,
  },
  messageRowUser: {
    justifyContent: "flex-end",
  },
  messageRowSystem: {
    justifyContent: "center",
  },
  avatar: {
    width: 30,
    height: 30,
    borderRadius: 15,
    alignItems: "center",
    justifyContent: "center",
    marginTop: 2,
  },
  bubble: {
    borderRadius: 18,
    paddingVertical: 10,
    paddingHorizontal: 14,
    maxWidth: "82%",
  },
  inlineActions: {
    marginTop: 4,
    gap: 2,
  },
  streamingLabel: {
    marginTop: 6,
    opacity: 0.6,
  },
  streamingActionsRow: {
    marginTop: 2,
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    gap: 8,
  },
  memoryActionsRow: {
    marginTop: 6,
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
    flexWrap: "wrap",
  },
  userMessageActionsRow: {
    marginTop: 2,
    flexDirection: "row",
    alignItems: "center",
    gap: 6,
    flexWrap: "wrap",
    justifyContent: "flex-end",
  },
  failedMessageActionsRow: {
    marginTop: 6,
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
    flexWrap: "wrap",
  },
  inlineActionIconButton: {
    margin: 0,
    width: 32,
    height: 32,
  },
  failedMessageError: {
    marginTop: 4,
    opacity: 0.75,
  },
});
