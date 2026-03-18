import { useCallback, useMemo, useState } from "react";
import { Pressable, ScrollView, StyleSheet, View } from "react-native";
import {
    Badge,
    Button,
    Divider,
    IconButton,
    Surface,
    Text,
    TextInput,
    useTheme,
} from "react-native-paper";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import {
    AssistantConversation,
    AssistantDetectedAction,
} from "@/types/assistant";

const HUMANIZED_TYPES: Record<string, string> = {
  create_task_template: "Create Task",
  complete_task: "Complete Task",
  log_dosing: "Log Dosing",
  log_parameters: "Log Parameters",
  add_issue: "Add Issue",
  add_memo: "Add Memo",
  save_reminder_settings: "Reminder Settings",
  add_aquarium: "Add Aquarium",
  edit_aquarium: "Edit Aquarium",
  add_livestock: "Add Livestock",
  transfer_livestock: "Transfer Livestock",
  set_livestock_status: "Livestock Status",
  add_asset: "Add Asset",
  add_consumable: "Add Consumable",
  consume_consumable: "Use Consumable",
  set_issue_status: "Issue Status",
};

const ACTION_ICONS: Record<string, string> = {
  create_task_template: "clipboard-check-outline",
  complete_task: "check-circle-outline",
  log_dosing: "eyedropper",
  log_parameters: "test-tube",
  add_issue: "alert-circle-outline",
  add_memo: "note-text-outline",
  save_reminder_settings: "bell-outline",
  add_aquarium: "fishbowl-outline",
  edit_aquarium: "pencil-outline",
  add_livestock: "fish",
  transfer_livestock: "swap-horizontal",
  set_livestock_status: "heart-pulse",
  add_asset: "package-variant",
  add_consumable: "flask-outline",
  consume_consumable: "minus-circle-outline",
  set_issue_status: "flag-outline",
};

export { ACTION_ICONS, HUMANIZED_TYPES };

interface ConversationDrawerProps {
  conversations: AssistantConversation[];
  activeConversationId: string;
  onSelect: (id: string) => void;
  onNew: () => void;
  onTogglePin: (id: string) => void;
  onRename: (id: string, title: string) => void;
  onDelete: (id: string) => void;
  onClose: () => void;
}

interface DateGroup {
  label: string;
  items: AssistantConversation[];
}

function groupByDate(conversations: AssistantConversation[]): DateGroup[] {
  const now = new Date();
  const todayStr = now.toDateString();
  const yest = new Date(now.getTime() - 86_400_000);
  const yesterdayStr = yest.toDateString();
  const weekAgo = new Date(now.getTime() - 7 * 86_400_000);

  const today: AssistantConversation[] = [];
  const yesterday: AssistantConversation[] = [];
  const week: AssistantConversation[] = [];
  const older: AssistantConversation[] = [];

  for (const c of conversations) {
    const d = new Date(c.createdAt);
    const ds = d.toDateString();
    if (ds === todayStr) today.push(c);
    else if (ds === yesterdayStr) yesterday.push(c);
    else if (d > weekAgo) week.push(c);
    else older.push(c);
  }

  const groups: DateGroup[] = [];
  if (today.length > 0) groups.push({ label: "Today", items: today });
  if (yesterday.length > 0)
    groups.push({ label: "Yesterday", items: yesterday });
  if (week.length > 0) groups.push({ label: "Previous 7 days", items: week });
  if (older.length > 0) groups.push({ label: "Older", items: older });
  return groups;
}

function buildSearchText(conversation: AssistantConversation) {
  const messageText = conversation.messages
    .slice(-10)
    .map((message) => message.content)
    .join(" ");
  return `${conversation.title} ${messageText}`.toLowerCase();
}

function ActionSubItem({ action }: { action: AssistantDetectedAction }) {
  const theme = useTheme();
  const label = HUMANIZED_TYPES[action.type] ?? action.type;
  const icon = ACTION_ICONS[action.type] ?? "lightning-bolt";
  const detail =
    action.title ||
    action.taskTitle ||
    action.product ||
    action.issueTitle ||
    action.consumableName ||
    "";

  return (
    <View style={subStyles.row}>
      <IconButton icon={icon} size={14} style={subStyles.icon} />
      <View style={subStyles.textCol}>
        <Text
          variant="labelSmall"
          numberOfLines={1}
          style={{ color: theme.colors.onSurfaceVariant }}
        >
          {label}
        </Text>
        {detail ? (
          <Text
            variant="labelSmall"
            numberOfLines={1}
            style={{ color: theme.colors.onSurfaceVariant, opacity: 0.7 }}
          >
            {detail}
          </Text>
        ) : null}
      </View>
      {action.approved ? (
        <IconButton
          icon="check-circle"
          size={12}
          iconColor={theme.colors.primary}
          style={subStyles.statusIcon}
        />
      ) : null}
    </View>
  );
}

const subStyles = StyleSheet.create({
  row: {
    flexDirection: "row",
    alignItems: "center",
    paddingLeft: 24,
    paddingRight: 8,
    paddingVertical: 2,
    gap: 2,
  },
  icon: { margin: 0, width: 20, height: 20 },
  textCol: { flex: 1, gap: 1 },
  statusIcon: { margin: 0, width: 16, height: 16 },
});

export function ConversationDrawer({
  conversations,
  activeConversationId,
  onSelect,
  onNew,
  onTogglePin,
  onRename,
  onDelete,
  onClose,
}: ConversationDrawerProps) {
  const theme = useTheme();
  const insets = useSafeAreaInsets();
  const [searchText, setSearchText] = useState("");
  const [renamingConversationId, setRenamingConversationId] = useState<
    string | null
  >(null);
  const [renameValue, setRenameValue] = useState("");

  const normalizedSearch = searchText.trim().toLowerCase();

  const sorted = useMemo(
    () =>
      [...conversations].sort((a, b) => {
        if (Boolean(a.pinned) !== Boolean(b.pinned)) {
          return a.pinned ? -1 : 1;
        }

        return (
          new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime()
        );
      }),
    [conversations],
  );

  const filtered = useMemo(() => {
    if (!normalizedSearch) {
      return sorted;
    }

    return sorted.filter((conversation) =>
      buildSearchText(conversation).includes(normalizedSearch),
    );
  }, [normalizedSearch, sorted]);

  const groups = useMemo(() => groupByDate(filtered), [filtered]);

  const handleSelect = useCallback(
    (id: string) => {
      onSelect(id);
      onClose();
    },
    [onSelect, onClose],
  );

  const startRename = useCallback((conversation: AssistantConversation) => {
    setRenamingConversationId(conversation.id);
    setRenameValue(conversation.title);
  }, []);

  const cancelRename = useCallback(() => {
    setRenamingConversationId(null);
    setRenameValue("");
  }, []);

  const commitRename = useCallback(() => {
    const conversationId = renamingConversationId;
    if (!conversationId) {
      return;
    }

    const nextTitle = renameValue.trim();
    if (!nextTitle) {
      return;
    }

    onRename(conversationId, nextTitle);
    cancelRename();
  }, [cancelRename, onRename, renameValue, renamingConversationId]);

  return (
    <Surface
      style={[
        styles.drawerSurface,
        { paddingTop: insets.top + 8, paddingBottom: insets.bottom + 8 },
      ]}
      elevation={3}
    >
      {/* Header */}
      <View style={styles.header}>
        <Text variant="titleMedium" style={{ color: theme.colors.primary }}>
          AquaPT
        </Text>
        <IconButton icon="close" size={20} onPress={onClose} />
      </View>

      {/* New Chat button */}
      <Button
        mode="contained-tonal"
        icon="plus"
        onPress={() => {
          onNew();
          onClose();
        }}
        style={styles.newChatButton}
        contentStyle={styles.newChatContent}
        labelStyle={styles.newChatLabel}
      >
        New Chat
      </Button>

      <TextInput
        mode="outlined"
        placeholder="Search conversations"
        value={searchText}
        onChangeText={setSearchText}
        style={styles.searchInput}
        dense
        left={<TextInput.Icon icon="magnify" />}
        right={
          searchText ? (
            <TextInput.Icon icon="close" onPress={() => setSearchText("")} />
          ) : undefined
        }
      />

      <Divider style={styles.divider} />

      {/* Conversation list */}
      <ScrollView
        style={styles.list}
        showsVerticalScrollIndicator={false}
        contentContainerStyle={styles.listContent}
      >
        {groups.length === 0 ? (
          <View style={styles.emptyStateWrap}>
            <Text variant="bodySmall" style={{ opacity: 0.7 }}>
              No conversations found.
            </Text>
          </View>
        ) : null}

        {groups.map((group) => (
          <View key={group.label}>
            <Text
              variant="labelSmall"
              style={[
                styles.groupLabel,
                { color: theme.colors.onSurfaceVariant },
              ]}
            >
              {group.label}
            </Text>

            {group.items.map((conv) => {
              const isActive = conv.id === activeConversationId;
              const actionCount = conv.detectedActions.length;

              return (
                <View key={conv.id}>
                  {renamingConversationId === conv.id ? (
                    <View style={styles.renameRow}>
                      <TextInput
                        mode="outlined"
                        value={renameValue}
                        onChangeText={setRenameValue}
                        style={styles.renameInput}
                        dense
                        autoFocus
                        onSubmitEditing={commitRename}
                      />
                      <IconButton
                        icon="check"
                        size={18}
                        onPress={commitRename}
                      />
                      <IconButton
                        icon="close"
                        size={18}
                        onPress={cancelRename}
                      />
                    </View>
                  ) : null}

                  <Pressable
                    onPress={() => handleSelect(conv.id)}
                    style={({ pressed }) => [
                      styles.convItem,
                      isActive && {
                        backgroundColor: theme.colors.primaryContainer,
                        borderRadius: 12,
                      },
                      pressed && { opacity: 0.7 },
                    ]}
                  >
                    <View style={styles.convTitleRow}>
                      <Text
                        variant="bodyMedium"
                        numberOfLines={1}
                        style={[
                          styles.convTitle,
                          isActive && {
                            color: theme.colors.onPrimaryContainer,
                            fontWeight: "600",
                          },
                        ]}
                      >
                        {conv.title}
                      </Text>
                      <View style={styles.convBadges}>
                        <IconButton
                          icon={conv.pinned ? "pin" : "pin-outline"}
                          size={16}
                          onPress={() => onTogglePin(conv.id)}
                          style={styles.actionBtn}
                        />
                        <IconButton
                          icon="pencil-outline"
                          size={16}
                          onPress={() => startRename(conv)}
                          style={styles.actionBtn}
                        />
                        {actionCount > 0 ? (
                          <Badge
                            size={18}
                            style={{
                              backgroundColor: theme.colors.primary,
                            }}
                          >
                            {actionCount}
                          </Badge>
                        ) : null}
                        {conversations.length > 1 ? (
                          <IconButton
                            icon="delete-outline"
                            size={16}
                            onPress={() => onDelete(conv.id)}
                            style={styles.actionBtn}
                          />
                        ) : null}
                      </View>
                    </View>
                  </Pressable>

                  {/* Show actions under conversation */}
                  {isActive && conv.detectedActions.length > 0 ? (
                    <View style={styles.actionsList}>
                      {conv.detectedActions.map((action) => (
                        <ActionSubItem key={action.id} action={action} />
                      ))}
                    </View>
                  ) : null}
                </View>
              );
            })}
          </View>
        ))}
      </ScrollView>
    </Surface>
  );
}

const styles = StyleSheet.create({
  drawerSurface: {
    flex: 1,
    paddingHorizontal: 12,
  },
  header: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    paddingHorizontal: 4,
    marginBottom: 4,
  },
  newChatButton: {
    borderRadius: 12,
    marginHorizontal: 4,
  },
  newChatContent: {
    height: 40,
  },
  newChatLabel: {
    fontSize: 14,
  },
  searchInput: {
    marginTop: 8,
    marginHorizontal: 4,
  },
  divider: {
    marginVertical: 8,
  },
  list: {
    flex: 1,
  },
  listContent: {
    paddingBottom: 8,
    gap: 4,
  },
  groupLabel: {
    textTransform: "uppercase",
    letterSpacing: 0.8,
    fontSize: 11,
    paddingHorizontal: 8,
    paddingTop: 12,
    paddingBottom: 4,
    opacity: 0.7,
  },
  convItem: {
    paddingVertical: 10,
    paddingHorizontal: 10,
    borderRadius: 8,
  },
  convTitleRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 6,
  },
  convTitle: {
    flex: 1,
  },
  convBadges: {
    flexDirection: "row",
    alignItems: "center",
    gap: 2,
  },
  actionBtn: {
    margin: 0,
    width: 24,
    height: 24,
  },
  actionsList: {
    paddingBottom: 4,
    gap: 2,
  },
  renameRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 2,
    paddingHorizontal: 6,
    paddingTop: 6,
  },
  renameInput: {
    flex: 1,
  },
  emptyStateWrap: {
    alignItems: "center",
    justifyContent: "center",
    paddingVertical: 24,
  },
});
