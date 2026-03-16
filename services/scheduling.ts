import { TaskExecution, TaskFrequency, TaskTemplate } from "@/types/aquapt";

const frequencyDays: Record<TaskFrequency, number> = {
  daily: 1,
  weekly: 7,
  "bi-weekly": 14,
  monthly: 30,
};

export function getLatestExecutionIso(
  taskExecutions: TaskExecution[],
  taskTemplateId: string,
  aquariumId: string,
) {
  return taskExecutions
    .filter(
      (entry) =>
        entry.taskTemplateId === taskTemplateId &&
        entry.aquariumId === aquariumId,
    )
    .sort((a, b) => +new Date(b.completedAt) - +new Date(a.completedAt))[0]
    ?.completedAt;
}

export function isTaskDue(
  task: TaskTemplate,
  aquariumId: string,
  taskExecutions: TaskExecution[],
  now = new Date(),
) {
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
