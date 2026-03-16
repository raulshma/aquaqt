import { ReactNode } from "react";
import { ScrollView, StyleSheet, View } from "react-native";
import { Modal, Portal, Surface, Text } from "react-native-paper";

interface BottomSheetProps {
  visible: boolean;
  onDismiss: () => void;
  title: string;
  children: ReactNode;
  actions?: ReactNode;
}

export function BottomSheet({
  visible,
  onDismiss,
  title,
  children,
  actions,
}: BottomSheetProps) {
  return (
    <Portal>
      <Modal
        visible={visible}
        onDismiss={onDismiss}
        contentContainerStyle={styles.modalContainer}
      >
        <Surface style={styles.sheet} elevation={2}>
          <View style={styles.handle} />
          <Text variant="titleMedium" style={styles.title}>
            {title}
          </Text>
          <ScrollView style={styles.content} showsVerticalScrollIndicator={false}>
            {children}
          </ScrollView>
          {actions ? <View style={styles.actions}>{actions}</View> : null}
        </Surface>
      </Modal>
    </Portal>
  );
}

const styles = StyleSheet.create({
  modalContainer: {
    flex: 1,
    justifyContent: "flex-end",
  },
  sheet: {
    borderTopLeftRadius: 20,
    borderTopRightRadius: 20,
    paddingHorizontal: 16,
    paddingTop: 12,
    paddingBottom: 16,
    maxHeight: "95%",
    flex: 1,
  },
  handle: {
    width: 48,
    height: 4,
    borderRadius: 999,
    backgroundColor: "rgba(120,120,120,0.5)",
    alignSelf: "center",
    marginBottom: 12,
  },
  title: {
    marginBottom: 8,
  },
  content: {
    gap: 10,
    flexGrow: 1,
  },
  actions: {
    marginTop: 14,
    flexDirection: "row",
    justifyContent: "flex-end",
    gap: 8,
  },
});
