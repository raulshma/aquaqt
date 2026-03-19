import { Modal, Pressable, StyleSheet, View } from "react-native";
import { Button, Text, useTheme } from "react-native-paper";
import { useSafeAreaInsets } from "react-native-safe-area-context";

export type PhotoSource = "camera" | "library";

interface PhotoSourceDialogProps {
  visible: boolean;
  title: string;
  description?: string;
  hasCurrentPhoto?: boolean;
  loading?: boolean;
  onDismiss: () => void;
  onPickSource: (source: PhotoSource) => void;
  onRemovePhoto?: () => void;
}

export function PhotoSourceDialog({
  visible,
  title,
  description = "Choose photo source",
  hasCurrentPhoto = false,
  loading = false,
  onDismiss,
  onPickSource,
  onRemovePhoto,
}: PhotoSourceDialogProps) {
  const theme = useTheme();
  const insets = useSafeAreaInsets();
  const backdropColor = theme.dark
    ? "rgba(0, 0, 0, 0.72)"
    : "rgba(15, 23, 42, 0.45)";
  const sheetBackgroundColor =
    theme.colors.elevation?.level2 ?? theme.colors.surface;
  const handleColor = theme.colors.onSurfaceVariant
    ? `${theme.colors.onSurfaceVariant}80`
    : "rgba(120,120,120,0.5)";

  return (
    <Modal
      visible={visible}
      transparent
      animationType="fade"
      onRequestClose={onDismiss}
    >
      <View style={styles.modalRoot}>
        <Pressable
          style={[styles.backdrop, { backgroundColor: backdropColor }]}
          onPress={onDismiss}
        />
        <View
          style={[
            styles.sheetWrapper,
            {
              paddingBottom: 12 + insets.bottom,
              backgroundColor: sheetBackgroundColor,
            },
          ]}
        >
          <View style={styles.handleArea}>
            <View style={[styles.handle, { backgroundColor: handleColor }]} />
          </View>
          <Text variant="titleMedium" style={styles.title}>
            {title}
          </Text>
          <Text variant="bodyMedium" style={styles.description}>
            {description}
          </Text>

          <View style={styles.optionList}>
            <Button
              mode="contained-tonal"
              icon="camera"
              loading={loading}
              disabled={loading}
              onPress={() => onPickSource("camera")}
              contentStyle={styles.optionContent}
            >
              Take photo
            </Button>

            <Button
              mode="contained-tonal"
              icon="image"
              loading={loading}
              disabled={loading}
              onPress={() => onPickSource("library")}
              contentStyle={styles.optionContent}
            >
              Choose from library
            </Button>

            {hasCurrentPhoto && onRemovePhoto ? (
              <Button
                mode="outlined"
                icon="delete-outline"
                textColor={theme.colors.error}
                style={[
                  styles.removeButton,
                  { borderColor: theme.colors.error },
                ]}
                disabled={loading}
                onPress={onRemovePhoto}
                contentStyle={styles.optionContent}
              >
                Remove photo
              </Button>
            ) : null}

            <Button onPress={onDismiss}>Cancel</Button>
          </View>
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  modalRoot: {
    flex: 1,
    justifyContent: "flex-end",
  },
  backdrop: {
    ...StyleSheet.absoluteFillObject,
  },
  sheetWrapper: {
    marginHorizontal: 0,
    paddingHorizontal: 16,
    paddingTop: 12,
    borderTopLeftRadius: 20,
    borderTopRightRadius: 20,
  },
  handleArea: {
    paddingBottom: 4,
    alignItems: "center",
  },
  handle: {
    width: 48,
    height: 4,
    borderRadius: 999,
  },
  title: {
    marginBottom: 8,
  },
  description: {
    opacity: 0.72,
    marginBottom: 4,
  },
  optionList: {
    gap: 10,
    paddingBottom: 4,
  },
  optionContent: {
    minHeight: 46,
  },
  removeButton: {
    marginTop: 4,
  },
});
