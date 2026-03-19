import { Pressable, StyleSheet, View } from "react-native";
import { IconButton, Text, useTheme } from "react-native-paper";
import type { AssistantDetectedAction } from "@/types/assistant";
import { ACTION_ICONS, HUMANIZED_TYPES } from "./conversation-drawer";
import { getActionSummary } from "@/utils/assistant-constants";

interface InlineActionCardProps {
  action: AssistantDetectedAction;
  onToggleApproval: () => void;
  onReview: () => void;
}

export function InlineActionCard({
  action,
  onToggleApproval,
  onReview,
}: InlineActionCardProps) {
  const theme = useTheme();
  const label = HUMANIZED_TYPES[action.type] ?? action.type;
  const icon = ACTION_ICONS[action.type] ?? "lightning-bolt";
  const summary = getActionSummary(action);
  const hasErrors = action.validationErrors.length > 0;

  return (
    <Pressable
      onPress={onReview}
      style={({ pressed }) => [
        styles.card,
        {
          backgroundColor: action.approved
            ? `${theme.colors.primary}18`
            : theme.colors.surfaceVariant,
          borderColor: action.approved
            ? theme.colors.primary
            : theme.colors.outlineVariant,
        },
        pressed && { opacity: 0.7 },
      ]}
    >
      <View style={styles.topRow}>
        <IconButton
          icon={icon}
          size={16}
          style={styles.icon}
          iconColor={theme.colors.primary}
        />
        <Text variant="labelMedium" style={styles.typeLabel}>
          {label}
        </Text>
        <View
          style={[
            styles.confBadge,
            {
              backgroundColor: theme.colors.surfaceVariant,
              borderRadius: 9,
              paddingHorizontal: 5,
              paddingVertical: 1,
            },
          ]}
        >
          <Text variant="labelSmall">{`${(action.confidence * 100).toFixed(0)}%`}</Text>
        </View>
        <Pressable
          onPress={onToggleApproval}
          hitSlop={8}
        >
          <IconButton
            icon={
              action.approved ? "check-circle" : "checkbox-blank-circle-outline"
            }
            size={18}
            iconColor={
              action.approved ? theme.colors.primary : theme.colors.outline
            }
            style={styles.approveIcon}
          />
        </Pressable>
      </View>
      <Text variant="bodySmall" numberOfLines={2} style={{ opacity: 0.85 }}>
        {summary}
      </Text>
      {hasErrors ? (
        <Text
          variant="labelSmall"
          style={{ color: theme.colors.error, marginTop: 2 }}
        >
          {action.validationErrors[0]}
          {action.validationErrors.length > 1
            ? ` (+${action.validationErrors.length - 1} more)`
            : ""}
        </Text>
      ) : null}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  card: {
    borderWidth: 1,
    borderRadius: 12,
    padding: 10,
    marginTop: 6,
    gap: 4,
  },
  topRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 4,
  },
  icon: { margin: 0, width: 22, height: 22 },
  typeLabel: { flex: 1, fontWeight: "600" },
  confBadge: { marginRight: 4 },
  approveIcon: { margin: 0, width: 24, height: 24 },
});
