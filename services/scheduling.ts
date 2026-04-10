import { ReminderGroup, TaskExecution, TaskFrequency, TaskTemplate } from "@/types/aquapt";

const frequencyDays: Record<TaskFrequency, number> = {
  daily: 1,
  weekly: 7,
  "bi-weekly": 14,
  monthly: 30,
};

function getDayStart(date: Date): Date {
  const d = new Date(date);
  d.setHours(0, 0, 0, 0);
  return d;
}

export function toIsoDate(date: Date): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

export function getExecutionsForTask(
  taskExecutions: TaskExecution[],
  taskTemplateId: string,
  aquariumId: string,
): TaskExecution[] {
  return taskExecutions.filter(
    (entry) =>
      entry.taskTemplateId === taskTemplateId && entry.aquariumId === aquariumId,
  );
}

export function getExecutionsForDay(
  executions: TaskExecution[],
  date: Date,
): TaskExecution[] {
  const dayStart = getDayStart(date);
  const dayEnd = new Date(dayStart);
  dayEnd.setDate(dayEnd.getDate() + 1);

  return executions.filter((e) => {
    const ts = new Date(e.completedAt).getTime();
    return ts >= dayStart.getTime() && ts < dayEnd.getTime();
  });
}

export function getLatestExecutionIso(
  taskExecutions: TaskExecution[],
  taskTemplateId: string,
  aquariumId: string,
) {
  const executions = getExecutionsForTask(
    taskExecutions,
    taskTemplateId,
    aquariumId,
  );

  if (executions.length === 0) {
    return undefined;
  }

  let latestIso: string | undefined;
  let latestTs = -1;

  for (const entry of executions) {
    const ts = new Date(entry.completedAt).getTime();
    if (Number.isNaN(ts) || ts <= latestTs) {
      continue;
    }
    latestTs = ts;
    latestIso = entry.completedAt;
  }

  return latestIso;
}

export function isTaskDue(
  task: TaskTemplate,
  aquariumId: string,
  taskExecutions: TaskExecution[],
  now = new Date(),
) {
  if (task.startDate) {
    const startDate = new Date(task.startDate);
    if (getDayStart(now) < getDayStart(startDate)) {
      return false;
    }
  }

  const executions = getExecutionsForTask(
    taskExecutions,
    task.id,
    aquariumId,
  );
  const timesPerDay = task.timesPerDay ?? 1;

  if (task.frequency === "daily" && timesPerDay > 1) {
    const todayExecutions = getExecutionsForDay(executions, now);
    return todayExecutions.length < timesPerDay;
  }

  const lastDoneIso = getLatestExecutionIso(
    taskExecutions,
    task.id,
    aquariumId,
  );

  if (!lastDoneIso) {
    return true;
  }

  const days = frequencyDays[task.frequency];
  const elapsedMs = now.getTime() - new Date(lastDoneIso).getTime();

  return elapsedMs >= days * 24 * 60 * 60 * 1000;
}

export function getCompletionsToday(
  task: TaskTemplate,
  aquariumId: string,
  taskExecutions: TaskExecution[],
  now = new Date(),
): number {
  const executions = getExecutionsForTask(
    taskExecutions,
    task.id,
    aquariumId,
  );
  const todayExecutions = getExecutionsForDay(executions, now);
  return todayExecutions.length;
}

export function countDueTasks(
  taskTemplates: TaskTemplate[],
  taskExecutions: TaskExecution[],
  now = new Date(),
) {
  return taskTemplates.reduce((count, task) => {
    const dueForTask = task.aquariumIds.filter((aquariumId) =>
      isTaskDue(task, aquariumId, taskExecutions, now),
    ).length;

    return count + dueForTask;
  }, 0);
}

export function resolveEffectiveReminderHours(
  task: TaskTemplate,
  reminderGroups: ReminderGroup[],
  globalHours: number[],
): number[] {
  if (task.reminderHours && task.reminderHours.length > 0) {
    return task.reminderHours;
  }

  if (task.reminderGroupId) {
    const group = reminderGroups.find((g) => g.id === task.reminderGroupId);
    if (group && group.hours.length > 0) {
      return group.hours;
    }
  }

  return globalHours;
}

export function collectDueTasksByHour(
  taskTemplates: TaskTemplate[],
  taskExecutions: TaskExecution[],
  reminderGroups: ReminderGroup[],
  globalHours: number[],
  now = new Date(),
): Map<number, TaskTemplate[]> {
  const byHour = new Map<number, TaskTemplate[]>();

  for (const task of taskTemplates) {
    const isDue = task.aquariumIds.some((aquariumId) =>
      isTaskDue(task, aquariumId, taskExecutions, now),
    );
    if (!isDue) continue;

    const hours = resolveEffectiveReminderHours(task, reminderGroups, globalHours);
    for (const hour of hours) {
      const existing = byHour.get(hour);
      if (existing) {
        existing.push(task);
      } else {
        byHour.set(hour, [task]);
      }
    }
  }

  return byHour;
}
