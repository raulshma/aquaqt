import { ScrollView, StyleSheet, View } from "react-native";
import { SegmentedButtons } from "react-native-paper";

type ScrollableSegmentedButtonsProps = React.ComponentProps<
  typeof SegmentedButtons
>;

export function ScrollableSegmentedButtons(
  props: ScrollableSegmentedButtonsProps,
) {
  return (
    <ScrollView
      horizontal
      nestedScrollEnabled
      showsHorizontalScrollIndicator={false}
      contentContainerStyle={styles.container}
    >
      <View style={styles.inner}>
        <SegmentedButtons {...props} />
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: {
    paddingVertical: 1,
    paddingRight: 1,
  },
  inner: {
    minWidth: "100%",
  },
});
