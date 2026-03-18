import * as Notifications from "expo-notifications";
import { Platform } from "react-native";

Notifications.setNotificationHandler({
  handleNotification: async () => ({
    shouldShowBanner: true,
    shouldShowList: true,
    shouldPlaySound: false,
    shouldSetBadge: false,
  }),
});

export async function ensureReminderPermissions() {
  const existing = await Notifications.getPermissionsAsync();
  if (
    existing.granted ||
    existing.ios?.status === Notifications.IosAuthorizationStatus.PROVISIONAL
  ) {
    return true;
  }

  const requested = await Notifications.requestPermissionsAsync();
  return (
    requested.granted ||
    requested.ios?.status === Notifications.IosAuthorizationStatus.PROVISIONAL
  );
}

export async function configureReminderChannel() {
  if (Platform.OS !== "android") {
    return;
  }

  await Notifications.setNotificationChannelAsync("reminders", {
    name: "Reminders",
    importance: Notifications.AndroidImportance.DEFAULT,
    vibrationPattern: [0, 250, 250, 250],
    lightColor: "#3B82F6",
  });
}

export async function clearDailyReminderSchedule() {
  const scheduled = await Notifications.getAllScheduledNotificationsAsync();

  const reminderIds = scheduled
    .filter(
      (entry) =>
        entry.content.data?.kind === "daily-reminder" &&
        entry.content.data?.source === "aquapt",
    )
    .map((entry) => entry.identifier);

  if (reminderIds.length === 0) {
    return;
  }

  await Promise.all(
    reminderIds.map((id) => Notifications.cancelScheduledNotificationAsync(id)),
  );
}

export async function scheduleDailyReminder(
  hour: number,
  dueTaskCount?: number,
) {
  await configureReminderChannel();
  await clearDailyReminderSchedule();

  const normalizedHour = Math.min(23, Math.max(0, hour));

  await Notifications.scheduleNotificationAsync({
    content: {
      title: "Aquapt daily check",
      body:
        typeof dueTaskCount === "number"
          ? `${dueTaskCount} tasks currently due. Review maintenance and tank alerts.`
          : "Review due tasks and tank alerts for today.",
      data: {
        kind: "daily-reminder",
        source: "aquapt",
        route: "/(tabs)/tasks",
      },
      sound: false,
    },
    trigger: {
      type: Notifications.SchedulableTriggerInputTypes.DAILY,
      hour: normalizedHour,
      minute: 0,
      channelId: Platform.OS === "android" ? "reminders" : undefined,
    },
  });
}

export function registerNotificationResponseHandler(
  onRoute: (route: string) => void,
) {
  const subscription = Notifications.addNotificationResponseReceivedListener(
    (response) => {
      const route = response.notification.request.content.data?.route;
      if (typeof route === "string" && route.length > 0) {
        onRoute(route);
      }
    },
  );

  return () => {
    subscription.remove();
  };
}

export async function routeFromLastNotification(
  onRoute: (route: string) => void,
) {
  const response = await Notifications.getLastNotificationResponseAsync();
  const route = response?.notification?.request?.content?.data?.route;

  if (typeof route === "string" && route.length > 0) {
    onRoute(route);
    await Notifications.clearLastNotificationResponseAsync();
  }
}
