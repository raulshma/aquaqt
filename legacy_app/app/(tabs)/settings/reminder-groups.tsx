import { useState } from "react";
import { View } from "react-native";
import {
  Button,
  Chip,
  HelperText,
  IconButton,
  List,
  Text,
  TextInput,
  useTheme,
} from "react-native-paper";

import { BottomSheet } from "@/components/ui/bottom-sheet";
import {
  DashboardHero,
  DashboardScrollView,
  DashboardSection,
} from "@/components/ui/dashboard-shell";
import { useAquapt } from "@/context/aquapt-context";
import { getFrequencyLabel } from "@/types/aquapt";

const ALL_HOURS = Array.from({ length: 24 }, (_, i) => i);

export default function ReminderGroupsScreen() {
  const theme = useTheme();
  const {
    reminderGroups,
    taskTemplates,
    addReminderGroup,
    editReminderGroup,
    deleteReminderGroup,
    editTaskTemplate,
  } = useAquapt();

  const [sheetOpen, setSheetOpen] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [groupName, setGroupName] = useState("");
  const [selectedHours, setSelectedHours] = useState<number[]>([]);
  const [assignSheetGroupId, setAssignSheetGroupId] = useState<string | null>(
    null,
  );
  const [errorText, setErrorText] = useState("");

  const openCreate = () => {
    setEditingId(null);
    setGroupName("");
    setSelectedHours([]);
    setErrorText("");
    setSheetOpen(true);
  };

  const openEdit = (id: string) => {
    const group = reminderGroups.find((g) => g.id === id);
    if (!group) return;
    setEditingId(id);
    setGroupName(group.name);
    setSelectedHours([...group.hours]);
    setErrorText("");
    setSheetOpen(true);
  };

  const saveGroup = () => {
    if (!groupName.trim()) {
      setErrorText("Group name is required.");
      return;
    }
    if (selectedHours.length === 0) {
      setErrorText("Select at least one hour.");
      return;
    }

    if (editingId) {
      editReminderGroup(editingId, {
        name: groupName.trim(),
        hours: [...selectedHours].sort((a, b) => a - b),
      });
    } else {
      addReminderGroup({
        name: groupName.trim(),
        hours: [...selectedHours].sort((a, b) => a - b),
      });
    }

    setSheetOpen(false);
  };

  const toggleHour = (hour: number) => {
    setSelectedHours((prev) =>
      prev.includes(hour)
        ? prev.filter((h) => h !== hour)
        : [...prev, hour].sort((a, b) => a - b),
    );
  };

  const toggleTaskAssignment = (taskTemplateId: string, groupId: string) => {
    const task = taskTemplates.find((t) => t.id === taskTemplateId);
    if (!task) return;

    if (task.reminderGroupId === groupId) {
      editTaskTemplate(taskTemplateId, { reminderGroupId: undefined });
    } else {
      editTaskTemplate(taskTemplateId, { reminderGroupId: groupId });
    }
  };

  const assignSheetGroup = reminderGroups.find(
    (g) => g.id === assignSheetGroupId,
  );

  return (
    <DashboardScrollView topPadding={4}>
      <DashboardHero
        title="Reminder groups"
        subtitle="Create named schedules and assign tasks to them for per-group reminders."
        tone="primary"
        chips={
          <>
            <Chip compact icon="account-group">
              {reminderGroups.length} group{reminderGroups.length !== 1 ? "s" : ""}
            </Chip>
          </>
        }
      />

      <DashboardSection
        title="Groups"
        description="Each group defines a set of hours. Tasks assigned to a group inherit its schedule."
        action={
          <Button mode="contained-tonal" icon="plus" onPress={openCreate}>
            Add group
          </Button>
        }
      >
        {reminderGroups.length === 0 ? (
          <Text
            variant="bodyMedium"
            style={{ opacity: 0.6, marginTop: 8 }}
          >
            No groups yet. Create one to batch-assign reminder times to tasks.
          </Text>
        ) : (
          reminderGroups.map((group) => {
            const assignedCount = taskTemplates.filter(
              (t) => t.reminderGroupId === group.id,
            ).length;
            return (
              <List.Item
                key={group.id}
                title={group.name}
                description={`${group.hours.map((h) => `${String(h).padStart(2, "0")}:00`).join(", ")} · ${assignedCount} task${assignedCount !== 1 ? "s" : ""}`}
                left={(props) => <List.Icon {...props} icon="clock-outline" />}
                right={() => (
                  <View style={{ flexDirection: "row", alignItems: "center" }}>
                    <IconButton
                      icon="playlist-plus"
                      size={20}
                      onPress={() => setAssignSheetGroupId(group.id)}
                    />
                    <IconButton
                      icon="pencil"
                      size={20}
                      onPress={() => openEdit(group.id)}
                    />
                    <IconButton
                      icon="delete"
                      size={20}
                      iconColor={theme.colors.error}
                      onPress={() => deleteReminderGroup(group.id)}
                    />
                  </View>
                )}
              />
            );
          })
        )}
      </DashboardSection>

      <BottomSheet
        visible={sheetOpen}
        onDismiss={() => setSheetOpen(false)}
        title={editingId ? "Edit group" : "Create group"}
        actions={
          <>
            <Button onPress={() => setSheetOpen(false)}>Cancel</Button>
            <Button onPress={saveGroup}>Save</Button>
          </>
        }
      >
        <TextInput
          mode="outlined"
          label="Group name"
          value={groupName}
          onChangeText={setGroupName}
        />
        <Text variant="labelLarge">Reminder hours</Text>
        <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 6 }}>
          {ALL_HOURS.map((hour) => (
            <Chip
              key={hour}
              selected={selectedHours.includes(hour)}
              onPress={() => toggleHour(hour)}
              compact
            >
              {`${String(hour).padStart(2, "0")}:00`}
            </Chip>
          ))}
        </View>
        <HelperText type="error" visible={!!errorText}>
          {errorText}
        </HelperText>
      </BottomSheet>

      <BottomSheet
        visible={assignSheetGroupId !== null}
        onDismiss={() => setAssignSheetGroupId(null)}
        title={`Assign tasks to "${assignSheetGroup?.name ?? ""}"`}
        actions={<Button onPress={() => setAssignSheetGroupId(null)}>Done</Button>}
      >
        {taskTemplates.length === 0 ? (
          <Text variant="bodyMedium" style={{ opacity: 0.6 }}>
            No tasks available.
          </Text>
        ) : (
          taskTemplates.map((task) => (
            <List.Item
              key={task.id}
              title={task.title}
              description={`${getFrequencyLabel(task.frequency)} · ${task.aquariumIds.length} tank(s)`}
              right={() => (
                <Chip
                  selected={task.reminderGroupId === assignSheetGroupId}
                  onPress={() =>
                    toggleTaskAssignment(task.id, assignSheetGroupId!)
                  }
                  compact
                >
                  {task.reminderGroupId === assignSheetGroupId
                    ? "Assigned"
                    : "Assign"}
                </Chip>
              )}
            />
          ))
        )}
      </BottomSheet>
    </DashboardScrollView>
  );
}
