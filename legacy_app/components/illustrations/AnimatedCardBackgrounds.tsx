import React, { useEffect } from "react";
import { StyleSheet, View } from "react-native";
import Animated, {
  Easing,
  useAnimatedProps,
  useSharedValue,
  withRepeat,
  withTiming,
} from "react-native-reanimated";
import Svg, {
  Circle,
  G,
  Path,
  type CircleProps,
  type GProps,
} from "react-native-svg";

// Create animated versions of SVG primitives
const AnimatedCircle = Animated.createAnimatedComponent(Circle);
const AnimatedG = Animated.createAnimatedComponent(G);

/* ---------- shared wrapper ---------- */
function BackgroundWrap({ children }: { children: React.ReactNode }) {
  return (
    <View style={StyleSheet.absoluteFill} pointerEvents="none">
      <Svg
        width="100%"
        height="100%"
        viewBox="0 0 200 200"
        preserveAspectRatio="xMidYMid slice"
      >
        {children}
      </Svg>
    </View>
  );
}

/* ============================================================
   1. AQUARIUM  –  rising bubbles
   ============================================================ */
function RisingBubble({
  cx,
  r,
  dur,
  fill,
}: {
  cx: number;
  r: number;
  dur: number;
  fill: string;
}) {
  const progress = useSharedValue(0);

  useEffect(() => {
    progress.value = withRepeat(
      withTiming(1, { duration: dur, easing: Easing.linear }),
      -1,
      false,
    );
  }, [dur, progress]);

  const animatedProps = useAnimatedProps<CircleProps>(() => {
    const t = progress.value;
    // Move from bottom (y=220) to top (y=-20), fade out near the end
    const translateY = 220 - 240 * t;
    const opacity = t < 0.85 ? 1 : 1 - (t - 0.85) / 0.15;
    return {
      cy: translateY,
      opacity,
    };
  });

  return <AnimatedCircle cx={cx} r={r} fill={fill} animatedProps={animatedProps} />;
}

export function AquariumBackground({ tint }: { tint: string }) {
  return (
    <BackgroundWrap>
      <RisingBubble cx={155} r={18} dur={4000} fill={tint} />
      <RisingBubble cx={95} r={12} dur={4500} fill={tint} />
      <RisingBubble cx={40} r={24} dur={3500} fill={tint} />
      <RisingBubble cx={180} r={8} dur={5200} fill={tint} />
    </BackgroundWrap>
  );
}

/* ============================================================
   2. ASSET  –  rotating gears
   ============================================================ */
const GEAR_PATH =
  "M12 2C12 1.45 11.55 1 11 1H9C8.45 1 8 1.45 8 2V4.1C7.35 4.25 6.75 4.5 6.15 4.85L4.7 3.4C4.3 3 3.65 3 3.25 3.4L1.85 4.8C1.45 5.2 1.45 5.85 1.85 6.25L3.3 7.7C2.95 8.3 2.7 8.9 2.55 9.55H0.5C-0.05 9.55 -0.5 10 -0.5 10.55V12.55C-0.5 13.1 -0.05 13.55 0.5 13.55H2.55C2.7 14.2 2.95 14.8 3.3 15.35L1.85 16.8C1.45 17.2 1.45 17.85 1.85 18.25L3.25 19.65C3.65 20.05 4.3 20.05 4.7 19.65L6.15 18.2C6.75 18.55 7.35 18.8 8 18.95V21.05C8 21.6 8.45 22.05 9 22.05H11C11.55 22.05 12 21.6 12 21.05V18.95C12.65 18.8 13.25 18.55 13.85 18.2L15.3 19.65C15.7 20.05 16.35 20.05 16.75 19.65L18.15 18.25C18.55 17.85 18.55 17.2 18.15 16.8L16.7 15.35C17.05 14.75 17.3 14.15 17.45 13.55H19.5C20.05 13.55 20.5 13.1 20.5 12.55V10.55C20.5 10 20.05 9.55 19.5 9.55H17.45C17.3 8.9 17.05 8.3 16.7 7.7L18.15 6.25C18.55 5.85 18.55 5.2 18.15 4.8L16.75 3.4C16.35 3 15.7 3 15.3 3.4L13.85 4.85C13.25 4.5 12.65 4.25 12 4.1V2ZM10 16.05C6.65 16.05 4 13.4 4 10.05C4 6.7 6.65 4.05 10 4.05C13.35 4.05 16 6.7 16 10.05C16 13.4 13.35 16.05 10 16.05Z";

function RotatingGear({
  cx,
  cy,
  scale,
  dur,
  clockwise,
  fill,
}: {
  cx: number;
  cy: number;
  scale: number;
  dur: number;
  clockwise: boolean;
  fill: string;
}) {
  const rotation = useSharedValue(0);

  useEffect(() => {
    rotation.value = withRepeat(
      withTiming(clockwise ? 360 : -360, {
        duration: dur,
        easing: Easing.linear,
      }),
      -1,
      false,
    );
  }, [clockwise, dur, rotation]);

  const animatedProps = useAnimatedProps<GProps>(() => {
    // Rotate around the gear center (roughly 10, 11.5 in local coords, translated to cx, cy)
    return {
      rotation: rotation.value,
      originX: cx + 10 * scale,
      originY: cy + 11.5 * scale,
    };
  });

  return (
    <AnimatedG animatedProps={animatedProps}>
      <G transform={`translate(${cx}, ${cy}) scale(${scale})`}>
        <Path d={GEAR_PATH} fill={fill} />
      </G>
    </AnimatedG>
  );
}

export function AssetBackground({ tint }: { tint: string }) {
  return (
    <BackgroundWrap>
      <RotatingGear cx={115} cy={20} scale={2.8} dur={15000} clockwise fill={tint} />
      <RotatingGear cx={20} cy={110} scale={2.0} dur={15000} clockwise={false} fill={tint} />
    </BackgroundWrap>
  );
}

/* ============================================================
   3. LIVESTOCK  –  swimming fish
   ============================================================ */
const FISH_PATH =
  "M21.98 12.02C21.43 13.56 18.23 18.42 12.43 18.42C8.83 18.42 5.03 16.33 1.93 18.42C2.18 16.48 2.03 13.84 0.98 12.02C2.08 10.22 2.18 7.55 1.93 5.61C5.03 7.7 8.83 5.61 12.43 5.61C18.23 5.61 21.43 10.47 21.98 12.02ZM16.48 10.52C16.48 9.69 15.81 9.02 14.98 9.02C14.15 9.02 13.48 9.69 13.48 10.52C13.48 11.35 14.15 12.02 14.98 12.02C15.81 12.02 16.48 11.35 16.48 10.52Z";

function SwimmingFish({
  y,
  scale,
  flipX,
  dur,
  fill,
}: {
  y: number;
  scale: number;
  flipX: boolean;
  dur: number;
  fill: string;
}) {
  const progress = useSharedValue(0);

  useEffect(() => {
    progress.value = withRepeat(
      withTiming(1, { duration: dur, easing: Easing.linear }),
      -1,
      false,
    );
  }, [dur, progress]);

  const animatedProps = useAnimatedProps<GProps>(() => {
    const t = progress.value;
    // Swim from one side to the other
    const startX = flipX ? 220 : -60;
    const endX = flipX ? -60 : 220;
    const translateX = startX + (endX - startX) * t;
    return {
      translateX,
      translateY: y,
    };
  });

  return (
    <AnimatedG animatedProps={animatedProps}>
      <G transform={`scale(${flipX ? -scale : scale}, ${scale})`}>
        <Path d={FISH_PATH} fill={fill} />
      </G>
    </AnimatedG>
  );
}

export function LivestockBackground({ tint }: { tint: string }) {
  return (
    <BackgroundWrap>
      <SwimmingFish y={35} scale={2.8} flipX={false} dur={14000} fill={tint} />
      <SwimmingFish y={130} scale={2.0} flipX dur={18000} fill={tint} />
      <SwimmingFish y={80} scale={1.5} flipX={false} dur={22000} fill={tint} />
    </BackgroundWrap>
  );
}

/* ============================================================
   4. CONSUMABLE  –  flask with rising droplets
   ============================================================ */
const FLASK_PATH =
  "M21 21.05H3C1.5 21.05 0.5 19.45 1.3 18.25L8 8V3H7C6.45 3 6 2.55 6 2C6 1.45 6.45 1 7 1H17C17.55 1 18 1.45 18 2C18 2.55 17.55 3 17 3H16V8L22.7 18.25C23.5 19.45 22.5 21.05 21 21.05ZM10 9L4.4 17.55H19.6L14 9V3H10V9Z";

function PulsingFlask({ fill }: { fill: string }) {
  const opacity = useSharedValue(0.95);

  useEffect(() => {
    opacity.value = withRepeat(
      withTiming(0.6, { duration: 2000, easing: Easing.inOut(Easing.sin) }),
      -1,
      true,
    );
  }, [opacity]);

  const animatedProps = useAnimatedProps<GProps>(() => ({
    opacity: opacity.value,
  }));

  return (
    <AnimatedG animatedProps={animatedProps}>
      <G transform="translate(88, 75) scale(3.2) translate(-12, -12)">
        <Path d={FLASK_PATH} fill={fill} />
      </G>
    </AnimatedG>
  );
}

function Droplet({
  cx,
  startY,
  endY,
  r,
  dur,
  fill,
}: {
  cx: number;
  startY: number;
  endY: number;
  r: number;
  dur: number;
  fill: string;
}) {
  const progress = useSharedValue(0);

  useEffect(() => {
    progress.value = withRepeat(
      withTiming(1, { duration: dur, easing: Easing.linear }),
      -1,
      false,
    );
  }, [dur, progress]);

  const animatedProps = useAnimatedProps<CircleProps>(() => {
    const t = progress.value;
    const cy = startY + (endY - startY) * t;
    const opacity = t < 0.8 ? 1 : 1 - (t - 0.8) / 0.2;
    return { cy, opacity };
  });

  return <AnimatedCircle cx={cx} r={r} fill={fill} animatedProps={animatedProps} />;
}

export function ConsumableBackground({ tint }: { tint: string }) {
  return (
    <BackgroundWrap>
      <PulsingFlask fill={tint} />
      <Droplet cx={145} r={7} startY={130} endY={30} dur={3000} fill={tint} />
      <Droplet cx={170} r={4} startY={150} endY={40} dur={3800} fill={tint} />
      <Droplet cx={125} r={5} startY={140} endY={20} dur={4200} fill={tint} />
    </BackgroundWrap>
  );
}
