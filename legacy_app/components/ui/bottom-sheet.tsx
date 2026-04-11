import { ReactNode, useCallback, useEffect, useMemo, useRef } from "react";
import {
    ScrollView,
    StyleSheet,
    useWindowDimensions,
    View,
} from "react-native";
import { Gesture, GestureDetector } from "react-native-gesture-handler";
import { Modal, Portal, Surface, Text, useTheme } from "react-native-paper";
import Animated, {
    interpolate,
    runOnJS,
    useAnimatedStyle,
    useSharedValue,
    withTiming,
} from "react-native-reanimated";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { withAlpha } from "@/constants/theme";

interface BottomSheetProps {
  visible: boolean;
  onDismiss: () => void;
  title: string;
  children: ReactNode;
  actions?: ReactNode;
  layer?: number;
}

const SWIPE_THRESHOLD = 50;
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
  layer = 9999,
}: BottomSheetProps) {
  const { height: screenHeight } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const theme = useTheme();
  const translateY = useSharedValue(screenHeight);
  const isClosing = useSharedValue(false);
  const maxSheetHeight = Math.max(320, screenHeight - insets.top - 12);
  const sheetPaddingBottom = 20 + insets.bottom;
  const animatedSheetStyle = useAnimatedStyle(() => ({
    transform: [{ translateY: translateY.value }],
    opacity: interpolate(
      translateY.value,
      [0, screenHeight * 0.35, screenHeight],
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
      screenHeight,
      { duration: CLOSE_DURATION },
      (finished) => {
        if (finished) {
          runOnJS(finishDismiss)();
        }
      },
    );
  }, [finishDismiss, isClosing, screenHeight, translateY]);

  useEffect(() => {
    if (!visible) {
      translateY.value = screenHeight;
      isClosing.value = false;
      return;
    }

    isClosing.value = false;
    translateY.value = screenHeight;
    translateY.value = withTiming(0, { duration: OPEN_DURATION });
  }, [isClosing, screenHeight, translateY, visible]);

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
                screenHeight,
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
    [finishDismiss, isClosing, screenHeight, translateY],
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
        style={[
          styles.modalRoot,
          {
            zIndex: layer,
            elevation: layer,
          },
        ]}
      >
        <Animated.View style={[styles.sheetWrapper, animatedSheetStyle]}>
          <Surface
            style={[
              styles.sheet,
              {
                maxHeight: maxSheetHeight,
                paddingBottom: sheetPaddingBottom,
              },
            ]}
            elevation={2}
          >
            <GestureDetector gesture={panGesture}>
              <View style={styles.handleArea}>
                <View style={[styles.handle, { backgroundColor: withAlpha(theme.colors.onSurfaceVariant, 0.35) }]} />
              </View>
            </GestureDetector>
            <Text variant="titleMedium" style={styles.title}>
              {title}
            </Text>
            <ScrollView
              style={styles.content}
              showsVerticalScrollIndicator={false}
              contentContainerStyle={styles.contentContainer}
              keyboardShouldPersistTaps="handled"
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
    justifyContent: "flex-end",
  },
  sheet: {
    flexShrink: 1,
    borderTopLeftRadius: 20,
    borderTopRightRadius: 20,
    paddingHorizontal: 16,
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
  },
  title: {
    marginBottom: 8,
  },
  content: {
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
