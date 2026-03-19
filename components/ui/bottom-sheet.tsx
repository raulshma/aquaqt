import { ReactNode, useCallback, useEffect, useMemo, useRef } from "react";
import { Dimensions, ScrollView, StyleSheet, View } from "react-native";
import { Gesture, GestureDetector } from "react-native-gesture-handler";
import Animated, {
  interpolate,
  runOnJS,
  useAnimatedStyle,
  useSharedValue,
  withTiming,
} from "react-native-reanimated";
import { Modal, Portal, Surface, Text } from "react-native-paper";

interface BottomSheetProps {
  visible: boolean;
  onDismiss: () => void;
  title: string;
  children: ReactNode;
  actions?: ReactNode;
}

const SWIPE_THRESHOLD = 50;
const SCREEN_HEIGHT = Dimensions.get("window").height;
const DISMISS_VELOCITY = 900;
const OPEN_DURATION = 180;
const RESET_DURATION = 140;
const CLOSE_DURATION = 180;

export function BottomSheet({
  visible,
  onDismiss,
  title,
  children,
  actions,
}: BottomSheetProps) {
  const translateY = useSharedValue(SCREEN_HEIGHT);
  const isClosing = useSharedValue(false);
  const animatedSheetStyle = useAnimatedStyle(() => ({
    transform: [{ translateY: translateY.value }],
    opacity: interpolate(
      translateY.value,
      [0, SCREEN_HEIGHT * 0.35, SCREEN_HEIGHT],
      [1, 0.82, 0],
    ),
  }));
  const onDismissRef = useRef(onDismiss);
  onDismissRef.current = onDismiss;

  const finishDismiss = useCallback(() => {
    onDismissRef.current();
  }, []);

  const closeSheet = useCallback(() => {
    if (isClosing.value) {
      return;
    }

    isClosing.value = true;
    translateY.value = withTiming(
      SCREEN_HEIGHT,
      { duration: CLOSE_DURATION },
      (finished) => {
        if (finished) {
          runOnJS(finishDismiss)();
        }
      },
    );
  }, [finishDismiss, translateY]);

  useEffect(() => {
    if (!visible) {
      translateY.value = SCREEN_HEIGHT;
      isClosing.value = false;
      return;
    }

    isClosing.value = false;
    translateY.value = SCREEN_HEIGHT;
    translateY.value = withTiming(0, { duration: OPEN_DURATION });
  }, [isClosing, translateY, visible]);

  const panGesture = useMemo(
    () =>
      Gesture.Pan()
        .activeOffsetY(8)
        .failOffsetX([-24, 24])
        .onUpdate((event) => {
          translateY.value = Math.max(0, event.translationY);
        })
        .onEnd((event) => {
          const shouldDismiss =
            event.translationY > SWIPE_THRESHOLD ||
            event.velocityY > DISMISS_VELOCITY;

          if (shouldDismiss) {
            if (!isClosing.value) {
              isClosing.value = true;
              translateY.value = withTiming(
                SCREEN_HEIGHT,
                { duration: CLOSE_DURATION },
                (finished) => {
                  if (finished) {
                    runOnJS(finishDismiss)();
                  }
                },
              );
            }
            return;
          }

          translateY.value = withTiming(0, { duration: RESET_DURATION });
        }),
    [finishDismiss, isClosing, translateY],
  );

  const handleModalDismiss = useCallback(() => {
    closeSheet();
  }, [closeSheet]);

  return (
    <Portal>
      <Modal
        visible={visible}
        onDismiss={handleModalDismiss}
        contentContainerStyle={styles.modalContainer}
        style={styles.modalRoot}
      >
        <Animated.View style={[styles.sheetWrapper, animatedSheetStyle]}>
          <Surface style={styles.sheet} elevation={2}>
            <GestureDetector gesture={panGesture}>
              <View style={styles.handleArea}>
                <View style={styles.handle} />
              </View>
            </GestureDetector>
            <Text variant="titleMedium" style={styles.title}>
              {title}
            </Text>
            <ScrollView
              style={styles.content}
              showsVerticalScrollIndicator={false}
              contentContainerStyle={styles.contentContainer}
            >
              {children}
            </ScrollView>
            {actions ? <View style={styles.actions}>{actions}</View> : null}
          </Surface>
        </Animated.View>
      </Modal>
    </Portal>
  );
}

const styles = StyleSheet.create({
  modalRoot: {
    zIndex: 9999,
    elevation: 9999,
  },
  modalContainer: {
    flex: 1,
    justifyContent: "flex-end",
  },
  sheetWrapper: {
    maxHeight: "90%",
  },
  sheet: {
    borderTopLeftRadius: 20,
    borderTopRightRadius: 20,
    paddingHorizontal: 16,
    paddingBottom: 24,
    overflow: "hidden",
  },
  handleArea: {
    paddingTop: 12,
    paddingBottom: 4,
    alignItems: "center",
  },
  handle: {
    width: 48,
    height: 4,
    borderRadius: 999,
    backgroundColor: "rgba(120,120,120,0.5)",
  },
  title: {
    marginBottom: 8,
  },
  content: {
    flexGrow: 0,
    flexShrink: 1,
  },
  contentContainer: {
    gap: 10,
    paddingBottom: 4,
  },
  actions: {
    marginTop: 14,
    flexDirection: "row",
    justifyContent: "flex-end",
    gap: 8,
  },
});
