import { Image } from "expo-image";
import { LinearGradient } from "expo-linear-gradient";
import { Stack, useLocalSearchParams, useRouter } from "expo-router";
import { type ReactNode, useMemo } from "react";
import { StyleSheet, View } from "react-native";
import { Card, Chip, Text, useTheme } from "react-native-paper";

import { getCardTextColorForBackground } from "@/components/ui/card-tone";
import {
  DashboardHero,
  DashboardScrollView,
  DashboardSection,
} from "@/components/ui/dashboard-shell";
import { useAquapt } from "@/context/aquapt-context";
import {
  AquaptEntityStore,
  createEntityRef,
  getAquariumCollections,
  getEntityHref,
  getLivestockFeedingTasks,
  getLivestockOffspring,
  getRelatedTimelineEvents,
  getTaskExecutionHistory,
  getTimelineEventTarget,
  parseEntityKind,
  resolveEntityRef,
} from "@/services/entity-links";
import { formatCurrencyAmount } from "@/services/localization";
import { isTaskDue } from "@/services/scheduling";
import { evaluateParameterAlerts } from "@/services/water-alerts";
import {
  Asset,
  Consumable,
  DosingLog,
  EntityRef,
  Issue,
  Livestock,
  Memo,
  TaskTemplate,
  WaterParameterLog,
  WaterParameters,
} from "@/types/aquapt";

const PARAMETER_LABELS: Record<keyof WaterParameters, string> = {
  ammonia: "NH3",
  nitrite: "NO2",
  nitrate: "NO3",
  ph: "pH",
  temperatureC: "Temp",
  gh: "GH",
  kh: "KH",
  salinity: "Salinity",
  calcium: "Calcium",
  alkalinity: "Alk",
};

function useAquaptStore(): AquaptEntityStore {
  const {
    aquariums,
    taskTemplates,
    taskExecutions,
    livestock,
    assets,
    consumables,
    dosingLogs,
    parameterLogs,
    issues,
    memos,
    timeline,
  } = useAquapt();

  return {
    aquariums,
    taskTemplates,
    taskExecutions,
    livestock,
    assets,
    consumables,
    dosingLogs,
    parameterLogs,
    issues,
    memos,
    timeline,
  };
}

function LinkChip({
  label,
  entityRef,
  icon,
}: {
  label: string;
  entityRef: EntityRef;
  icon?: string;
}) {
  const router = useRouter();

  return (
    <Chip
      compact
      icon={icon}
      onPress={() => router.push(getEntityHref(entityRef) as never)}
    >
      {label}
    </Chip>
  );
}

function MiniCard({
  title,
  subtitle,
  body,
  backgroundColor,
  onPress,
  children,
}: {
  title: string;
  subtitle?: string;
  body?: string;
  backgroundColor: string;
  onPress?: () => void;
  children?: ReactNode;
}) {
  const theme = useTheme();
  const textColor = getCardTextColorForBackground(theme, backgroundColor);

  return (
    <Card
      style={[styles.card, { backgroundColor }]}
      mode="contained"
      onPress={onPress}
    >
      <Card.Content style={styles.cardContent}>
        <Text variant="titleSmall" style={{ color: textColor }}>
          {title}
        </Text>
        {subtitle ? (
          <Text
            variant="bodySmall"
            style={[styles.subtitle, { color: textColor }]}
          >
            {subtitle}
          </Text>
        ) : null}
        {body ? <Text style={{ color: textColor }}>{body}</Text> : null}
        {children}
      </Card.Content>
    </Card>
  );
}

function ParameterChips({ values }: { values: WaterParameters }) {
  return (
    <View style={styles.chipRow}>
      {(Object.entries(values) as [keyof WaterParameters, number | undefined][])
        .filter(([, value]) => value !== undefined)
        .map(([key, value]) => (
          <Chip key={key} compact>
            {PARAMETER_LABELS[key]} {value}
          </Chip>
        ))}
    </View>
  );
}

export default function EntityDetailScreen() {
  const params = useLocalSearchParams<{ kind?: string; id?: string }>();
  const theme = useTheme();
  const router = useRouter();
  const store = useAquaptStore();
  const { settings } = useAquapt();
  const userCurrencyCode = settings.defaultCurrency ?? "USD";
  const userLocale = settings.defaultLocale;

  const kind = parseEntityKind(params.kind);
  const id = typeof params.id === "string" ? params.id : "";
  const ref = kind && id ? createEntityRef(kind, id) : null;
  const resolved = ref ? resolveEntityRef(store, ref) : null;

  const aquariumCollections =
    resolved?.ref.kind === "aquarium"
      ? getAquariumCollections(store, resolved.ref.id)
      : null;

  const relatedTimeline = useMemo(() => {
    if (!resolved || resolved.ref.kind === "aquarium") {
      return [];
    }

    return getRelatedTimelineEvents(store, resolved.ref).sort(
      (left, right) => +new Date(right.createdAt) - +new Date(left.createdAt),
    );
  }, [resolved, store]);

  const openEntity = (entityRef: EntityRef) =>
    router.push(getEntityHref(entityRef) as never);

  const taskItem =
    resolved?.ref.kind === "task"
      ? (resolved.item as TaskTemplate | undefined)
      : undefined;
  const livestockItem =
    resolved?.ref.kind === "livestock"
      ? (resolved.item as Livestock | undefined)
      : undefined;
  const assetItem =
    resolved?.ref.kind === "asset"
      ? (resolved.item as Asset | undefined)
      : undefined;
  const consumableItem =
    resolved?.ref.kind === "consumable"
      ? (resolved.item as Consumable | undefined)
      : undefined;
  const issueItem =
    resolved?.ref.kind === "issue"
      ? (resolved.item as Issue | undefined)
      : undefined;
  const memoItem =
    resolved?.ref.kind === "memo"
      ? (resolved.item as Memo | undefined)
      : undefined;
  const dosingItem =
    resolved?.ref.kind === "dosing"
      ? (resolved.item as DosingLog | undefined)
      : undefined;
  const parameterLogItem =
    resolved?.ref.kind === "parameter-log"
      ? (resolved.item as WaterParameterLog | undefined)
      : undefined;

  if (!resolved) {
    return (
      <DashboardScrollView>
        <Stack.Screen options={{ title: "Details" }} />
        <DashboardHero
          title="Record not found"
          subtitle="The link no longer resolves to a saved entity."
          tone="error"
        />
      </DashboardScrollView>
    );
  }

  return (
    <DashboardScrollView>
      <Stack.Screen options={{ title: resolved.title }} />
      {resolved.ref.kind === "aquarium" && aquariumCollections ? (
        <>
          <Card mode="elevated" style={styles.aquariumHeroCard}>
            <View style={styles.aquariumHeroInner}>
              {aquariumCollections.aquarium.photoUri ? (
                <>
                  <Image
                    source={{ uri: aquariumCollections.aquarium.photoUri }}
                    style={styles.aquariumHeroPhoto}
                    contentFit="cover"
                    transition={120}
                  />
                  <LinearGradient
                    colors={["transparent", theme.colors.surface]}
                    locations={[0.3, 1]}
                    style={styles.aquariumHeroGradient}
                    start={{ x: 0, y: 0 }}
                    end={{ x: 0, y: 1 }}
                  />
                </>
              ) : null}
              <View style={styles.aquariumHeroContent}>
                <Text variant="headlineMedium" style={styles.aquariumHeroTitle}>
                  {aquariumCollections.aquarium.name}
                </Text>
                <Text variant="bodyMedium" style={styles.aquariumHeroSubtitle}>
                  {aquariumCollections.aquarium.volumeLiters}L •{" "}
                  {aquariumCollections.aquarium.waterType} • Setup{" "}
                  {aquariumCollections.aquarium.setupDate}
                </Text>
                <View style={styles.chipRow}>
                  <Chip compact icon="calendar-clock" mode="flat">
                    {aquariumCollections.dueTaskTemplates.length} due
                  </Chip>
                  <Chip compact icon="fish" mode="flat">
                    {aquariumCollections.livestock.length} livestock
                  </Chip>
                  <Chip compact icon="alert-circle" mode="flat">
                    {
                      aquariumCollections.issues.filter(
                        (issue) => issue.status !== "resolved",
                      ).length
                    }{" "}
                    open issues
                  </Chip>
                </View>
                <View style={styles.aquariumDetailsRow}>
                  <Text style={styles.aquariumDetailsText} variant="bodySmall">
                    {aquariumCollections.aquarium.dimensions}
                  </Text>
                  <Text style={styles.aquariumDetailsText} variant="bodySmall">
                    {aquariumCollections.aquarium.investmentCost !== undefined
                      ? `Cost: ${formatCurrencyAmount(
                          aquariumCollections.aquarium.investmentCost,
                          userCurrencyCode,
                          userLocale,
                        )}`
                      : "Cost: N/A"}
                  </Text>
                </View>
              </View>
            </View>
          </Card>

          <DashboardSection
            title="Current focus"
            description="Due tasks and active issues for this aquarium."
          >
            {aquariumCollections.dueTaskTemplates.map((task) => (
              <MiniCard
                key={task.id}
                title={task.title}
                subtitle={task.frequency}
                body={task.description}
                backgroundColor={theme.colors.primaryContainer}
                onPress={() =>
                  openEntity(
                    createEntityRef(
                      "task",
                      task.id,
                      aquariumCollections.aquarium.id,
                    ),
                  )
                }
              />
            ))}
            {aquariumCollections.issues
              .filter((issue) => issue.status !== "resolved")
              .map((issue) => (
                <MiniCard
                  key={issue.id}
                  title={issue.title}
                  subtitle={issue.status}
                  body={issue.resolutionNote}
                  backgroundColor={theme.colors.errorContainer}
                  onPress={() =>
                    openEntity(
                      createEntityRef(
                        "issue",
                        issue.id,
                        aquariumCollections.aquarium.id,
                      ),
                    )
                  }
                />
              ))}
          </DashboardSection>

          <DashboardSection
            title="Connected records"
            description="Livestock, assets, consumables, and observations linked to this aquarium."
          >
            {aquariumCollections.livestock.map((item) => (
              <MiniCard
                key={item.id}
                title={item.name}
                subtitle={`${item.species} • ${item.quantity}`}
                body={item.dietaryNotes}
                backgroundColor={theme.colors.tertiaryContainer}
                onPress={() =>
                  openEntity(
                    createEntityRef(
                      "livestock",
                      item.id,
                      aquariumCollections.aquarium.id,
                    ),
                  )
                }
              />
            ))}
            {aquariumCollections.assets.map((asset) => (
              <MiniCard
                key={asset.id}
                title={asset.brandModel}
                subtitle={asset.category}
                body={
                  asset.purchasedAt
                    ? `Purchased ${asset.purchasedAt}${asset.price !== undefined ? ` • ${formatCurrencyAmount(asset.price, userCurrencyCode, userLocale)}` : ""}`
                    : undefined
                }
                backgroundColor={theme.colors.surfaceVariant}
                onPress={() =>
                  openEntity(
                    createEntityRef(
                      "asset",
                      asset.id,
                      aquariumCollections.aquarium.id,
                    ),
                  )
                }
              >
                <View style={styles.chipRow}>
                  {(asset.maintenanceTaskTemplateIds ?? []).map((taskId) => {
                    const task = aquariumCollections.taskTemplates.find(
                      (candidate) => candidate.id === taskId,
                    );
                    return task ? (
                      <LinkChip
                        key={task.id}
                        label={task.title}
                        icon="wrench"
                        entityRef={createEntityRef(
                          "task",
                          task.id,
                          aquariumCollections.aquarium.id,
                        )}
                      />
                    ) : null;
                  })}
                </View>
              </MiniCard>
            ))}
            {aquariumCollections.consumables.map((consumable) => (
              <MiniCard
                key={consumable.id}
                title={consumable.name}
                subtitle={`${consumable.remaining}${consumable.unit} remaining`}
                body={
                  consumable.reorderAt !== undefined
                    ? `Reorder at ${consumable.reorderAt}${consumable.unit}`
                    : undefined
                }
                backgroundColor={theme.colors.secondaryContainer}
                onPress={() =>
                  openEntity(
                    createEntityRef(
                      "consumable",
                      consumable.id,
                      aquariumCollections.aquarium.id,
                    ),
                  )
                }
              />
            ))}
            {aquariumCollections.parameterLogs
              .sort(
                (left, right) =>
                  +new Date(right.createdAt) - +new Date(left.createdAt),
              )
              .slice(0, 2)
              .map((entry) => (
                <MiniCard
                  key={entry.id}
                  title="Water parameters"
                  subtitle={new Date(entry.createdAt).toLocaleString()}
                  backgroundColor={theme.colors.secondaryContainer}
                  onPress={() =>
                    openEntity(
                      createEntityRef(
                        "parameter-log",
                        entry.id,
                        aquariumCollections.aquarium.id,
                      ),
                    )
                  }
                >
                  <ParameterChips values={entry.values} />
                </MiniCard>
              ))}
            {aquariumCollections.dosingLogs
              .sort(
                (left, right) =>
                  +new Date(right.createdAt) - +new Date(left.createdAt),
              )
              .slice(0, 2)
              .map((entry) => (
                <MiniCard
                  key={entry.id}
                  title={entry.product}
                  subtitle={new Date(entry.createdAt).toLocaleString()}
                  body={`${entry.amountMl}ml${entry.note ? ` • ${entry.note}` : ""}`}
                  backgroundColor={theme.colors.tertiaryContainer}
                  onPress={() =>
                    openEntity(
                      createEntityRef(
                        "dosing",
                        entry.id,
                        aquariumCollections.aquarium.id,
                      ),
                    )
                  }
                />
              ))}
            {aquariumCollections.memos
              .sort(
                (left, right) =>
                  +new Date(right.createdAt) - +new Date(left.createdAt),
              )
              .slice(0, 2)
              .map((memo) => (
                <MiniCard
                  key={memo.id}
                  title="Memo"
                  subtitle={new Date(memo.createdAt).toLocaleString()}
                  body={memo.content}
                  backgroundColor={theme.colors.surfaceVariant}
                  onPress={() =>
                    openEntity(
                      createEntityRef(
                        "memo",
                        memo.id,
                        aquariumCollections.aquarium.id,
                      ),
                    )
                  }
                />
              ))}
          </DashboardSection>

          <DashboardSection
            title="Recent timeline"
            description="Latest events for this aquarium."
          >
            {aquariumCollections.timeline
              .sort(
                (left, right) =>
                  +new Date(right.createdAt) - +new Date(left.createdAt),
              )
              .slice(0, 8)
              .map((event) => (
                <MiniCard
                  key={event.id}
                  title={event.title}
                  subtitle={new Date(event.createdAt).toLocaleString()}
                  body={event.description}
                  backgroundColor={theme.colors.surfaceVariant}
                  onPress={() => openEntity(getTimelineEventTarget(event))}
                />
              ))}
          </DashboardSection>
        </>
      ) : null}
      {taskItem ? (
        <>
          <DashboardHero
            title={taskItem.title}
            subtitle={taskItem.description ?? "Recurring maintenance task"}
            tone="secondary"
            chips={
              <>
                <Chip compact icon="calendar-sync">
                  {taskItem.frequency}
                </Chip>
                <Chip compact icon="history">
                  {getTaskExecutionHistory(store, taskItem.id).length}{" "}
                  executions
                </Chip>
              </>
            }
          />
          <DashboardSection
            title="Linked aquariums"
            description="Aquarium-specific state for this task."
          >
            {taskItem.aquariumIds.map((aquariumId) => {
              const aquarium = store.aquariums.find(
                (entry) => entry.id === aquariumId,
              );
              const latestExecution = getTaskExecutionHistory(
                store,
                taskItem.id,
              ).find((entry) => entry.aquariumId === aquariumId);

              return (
                <MiniCard
                  key={aquariumId}
                  title={aquarium?.name ?? "Unknown aquarium"}
                  subtitle={
                    latestExecution
                      ? `Last done ${new Date(latestExecution.completedAt).toLocaleString()}`
                      : "Never completed"
                  }
                  body={
                    isTaskDue(
                      taskItem,
                      aquariumId,
                      store.taskExecutions,
                      new Date(),
                    )
                      ? "Due now"
                      : "On schedule"
                  }
                  backgroundColor={theme.colors.primaryContainer}
                  onPress={() =>
                    openEntity(
                      createEntityRef("aquarium", aquariumId, aquariumId),
                    )
                  }
                />
              );
            })}
            <View style={styles.chipRow}>
              {taskItem.livestockId ? (
                <LinkChip
                  label={
                    store.livestock.find(
                      (entry) => entry.id === taskItem.livestockId,
                    )?.name ?? "Target livestock"
                  }
                  icon="fish"
                  entityRef={createEntityRef(
                    "livestock",
                    taskItem.livestockId,
                    taskItem.aquariumIds[0],
                  )}
                />
              ) : null}
            </View>
          </DashboardSection>
          <DashboardSection
            title="Execution history"
            description="Recent completions for this task across aquariums."
          >
            {getTaskExecutionHistory(store, taskItem.id).map((execution) => (
              <MiniCard
                key={execution.id}
                title={
                  store.aquariums.find(
                    (entry) => entry.id === execution.aquariumId,
                  )?.name ?? "Unknown aquarium"
                }
                subtitle={new Date(execution.completedAt).toLocaleString()}
                body={execution.note}
                backgroundColor={theme.colors.surfaceVariant}
                onPress={() =>
                  openEntity(
                    createEntityRef(
                      "aquarium",
                      execution.aquariumId,
                      execution.aquariumId,
                    ),
                  )
                }
              />
            ))}
          </DashboardSection>
        </>
      ) : null}
      {livestockItem ? (
        <>
          <DashboardHero
            title={livestockItem.name}
            subtitle={`${livestockItem.species} • ${livestockItem.quantity} • ${livestockItem.status ?? "active"}`}
            tone="tertiary"
            chips={
              <>
                <LinkChip
                  label={resolved.aquarium?.name ?? "Aquarium"}
                  icon="fishbowl"
                  entityRef={createEntityRef(
                    "aquarium",
                    livestockItem.aquariumId,
                    livestockItem.aquariumId,
                  )}
                />
              </>
            }
          />
          {livestockItem.photoUri ? (
            <Card mode="contained" style={styles.imageCard}>
              <Image
                source={{ uri: livestockItem.photoUri }}
                style={styles.heroImage}
              />
            </Card>
          ) : null}
          {livestockItem.photoUri ? (
            <DashboardSection
              title="Photo"
              description="Saved image for this livestock record."
            >
              <Card mode="contained" style={styles.imageCard}>
                <Image
                  source={{ uri: livestockItem.photoUri }}
                  style={styles.heroImage}
                />
              </Card>
            </DashboardSection>
          ) : null}
          <DashboardSection
            title="Related links"
            description="Family and feeding connections for this livestock."
          >
            <View style={styles.chipRow}>
              {livestockItem.parentId ? (
                <LinkChip
                  label={
                    store.livestock.find(
                      (entry) => entry.id === livestockItem.parentId,
                    )?.name ?? "Parent"
                  }
                  icon="family-tree"
                  entityRef={createEntityRef(
                    "livestock",
                    livestockItem.parentId,
                    livestockItem.aquariumId,
                  )}
                />
              ) : null}
              {getLivestockOffspring(store, livestockItem.id).map(
                (offspring) => (
                  <LinkChip
                    key={offspring.id}
                    label={offspring.name}
                    icon="baby-face-outline"
                    entityRef={createEntityRef(
                      "livestock",
                      offspring.id,
                      offspring.aquariumId,
                    )}
                  />
                ),
              )}
              {getLivestockFeedingTasks(store, livestockItem.id).map((task) => (
                <LinkChip
                  key={task.id}
                  label={task.title}
                  icon="wrench"
                  entityRef={createEntityRef(
                    "task",
                    task.id,
                    livestockItem.aquariumId,
                  )}
                />
              ))}
            </View>
          </DashboardSection>
        </>
      ) : null}
      {assetItem ? (
        <>
          <DashboardHero
            title={assetItem.brandModel}
            subtitle={`${assetItem.category} • ${resolved.aquarium?.name ?? "Unknown tank"}`}
            tone="surfaceVariant"
            chips={
              <>
                <LinkChip
                  label={resolved.aquarium?.name ?? "Aquarium"}
                  icon="fishbowl"
                  entityRef={createEntityRef(
                    "aquarium",
                    assetItem.aquariumId,
                    assetItem.aquariumId,
                  )}
                />
              </>
            }
          />
          <DashboardSection
            title="Asset details"
            description="Purchase data and linked maintenance tasks."
          >
            <MiniCard
              title="Purchase"
              subtitle={assetItem.purchasedAt ?? "Purchase date not set"}
              body={
                assetItem.price !== undefined
                  ? formatCurrencyAmount(
                      assetItem.price,
                      userCurrencyCode,
                      userLocale,
                    )
                  : "No price saved"
              }
              backgroundColor={theme.colors.surfaceVariant}
            />
            <View style={styles.chipRow}>
              {(assetItem.maintenanceTaskTemplateIds ?? [])
                .map((taskId) =>
                  store.taskTemplates.find((entry) => entry.id === taskId),
                )
                .filter((task): task is TaskTemplate => !!task)
                .map((task) => (
                  <LinkChip
                    key={task.id}
                    label={task.title}
                    icon="wrench"
                    entityRef={createEntityRef(
                      "task",
                      task.id,
                      assetItem.aquariumId,
                    )}
                  />
                ))}
            </View>
          </DashboardSection>
        </>
      ) : null}
      {consumableItem ? (
        <>
          <DashboardHero
            title={consumableItem.name}
            subtitle={`${consumableItem.remaining}${consumableItem.unit} remaining`}
            tone="secondary"
            chips={
              <>
                <LinkChip
                  label={resolved.aquarium?.name ?? "Aquarium"}
                  icon="fishbowl"
                  entityRef={createEntityRef(
                    "aquarium",
                    consumableItem.aquariumId,
                    consumableItem.aquariumId,
                  )}
                />
              </>
            }
          />
          <DashboardSection
            title="Stock state"
            description="Current amount and reorder threshold."
          >
            <MiniCard
              title="Inventory"
              subtitle={`Updated ${new Date(consumableItem.updatedAt).toLocaleString()}`}
              body={
                consumableItem.reorderAt !== undefined
                  ? `Reorder at ${consumableItem.reorderAt}${consumableItem.unit}`
                  : "No reorder threshold set"
              }
              backgroundColor={theme.colors.secondaryContainer}
            />
          </DashboardSection>
        </>
      ) : null}
      {issueItem ? (
        <DashboardHero
          title={issueItem.title}
          subtitle={`${issueItem.status} • ${new Date(issueItem.createdAt).toLocaleString()}`}
          tone="error"
          chips={
            <>
              <LinkChip
                label={resolved.aquarium?.name ?? "Aquarium"}
                icon="fishbowl"
                entityRef={createEntityRef(
                  "aquarium",
                  issueItem.aquariumId,
                  issueItem.aquariumId,
                )}
              />
            </>
          }
        />
      ) : null}
      {memoItem ? (
        <DashboardHero
          title="Memo"
          subtitle={new Date(memoItem.createdAt).toLocaleString()}
          tone="surfaceVariant"
          chips={
            <>
              <LinkChip
                label={resolved.aquarium?.name ?? "Aquarium"}
                icon="fishbowl"
                entityRef={createEntityRef(
                  "aquarium",
                  memoItem.aquariumId,
                  memoItem.aquariumId,
                )}
              />
            </>
          }
        />
      ) : null}
      {dosingItem ? (
        <DashboardHero
          title={dosingItem.product}
          subtitle={`${dosingItem.amountMl}ml • ${new Date(dosingItem.createdAt).toLocaleString()}`}
          tone="tertiary"
          chips={
            <>
              <LinkChip
                label={resolved.aquarium?.name ?? "Aquarium"}
                icon="fishbowl"
                entityRef={createEntityRef(
                  "aquarium",
                  dosingItem.aquariumId,
                  dosingItem.aquariumId,
                )}
              />
            </>
          }
        />
      ) : null}
      {parameterLogItem ? (
        <>
          <DashboardHero
            title="Water parameters"
            subtitle={new Date(parameterLogItem.createdAt).toLocaleString()}
            tone="secondary"
            chips={
              <>
                <LinkChip
                  label={resolved.aquarium?.name ?? "Aquarium"}
                  icon="fishbowl"
                  entityRef={createEntityRef(
                    "aquarium",
                    parameterLogItem.aquariumId,
                    parameterLogItem.aquariumId,
                  )}
                />
                <Chip compact icon="shield-alert">
                  {resolved.aquarium
                    ? evaluateParameterAlerts(
                        resolved.aquarium,
                        parameterLogItem.values,
                      ).length
                    : 0}{" "}
                  alerts
                </Chip>
              </>
            }
          />
          <DashboardSection
            title="Recorded values"
            description="Full values saved in this entry."
          >
            <MiniCard
              title="Parameters"
              backgroundColor={theme.colors.secondaryContainer}
            >
              <ParameterChips values={parameterLogItem.values} />
            </MiniCard>
          </DashboardSection>
        </>
      ) : null}

      {resolved.ref.kind !== "aquarium" ? (
        <DashboardSection
          title="Related activity"
          description="Timeline entries connected to this record."
        >
          {resolved.item &&
          "photoUri" in resolved.item &&
          resolved.item.photoUri ? (
            <Card mode="contained" style={styles.imageCard}>
              <Image
                source={{ uri: resolved.item.photoUri }}
                style={styles.heroImage}
              />
            </Card>
          ) : null}
          {resolved.item && "content" in resolved.item ? (
            <MiniCard
              title="Content"
              body={resolved.item.content}
              backgroundColor={theme.colors.surfaceVariant}
            />
          ) : null}
          {resolved.item && "note" in resolved.item && resolved.item.note ? (
            <MiniCard
              title="Note"
              body={resolved.item.note}
              backgroundColor={theme.colors.surfaceVariant}
            />
          ) : null}
          {resolved.item &&
          "resolutionNote" in resolved.item &&
          resolved.item.resolutionNote ? (
            <MiniCard
              title="Resolution"
              body={resolved.item.resolutionNote}
              backgroundColor={theme.colors.surfaceVariant}
            />
          ) : null}
          {relatedTimeline.map((event) => (
            <MiniCard
              key={event.id}
              title={event.title}
              subtitle={new Date(event.createdAt).toLocaleString()}
              body={event.description}
              backgroundColor={theme.colors.surfaceVariant}
              onPress={() => openEntity(getTimelineEventTarget(event))}
            />
          ))}
        </DashboardSection>
      ) : null}
    </DashboardScrollView>
  );
}

const styles = StyleSheet.create({
  card: {
    borderRadius: 24,
    marginTop: 0,
  },
  cardContent: {
    gap: 8,
  },
  subtitle: {
    opacity: 0.75,
  },
  chipRow: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 8,
  },
  imageCard: {
    borderRadius: 24,
    overflow: "hidden",
  },
  heroImage: {
    width: "100%",
    height: 220,
  },
  aquariumHeroCard: {
    borderRadius: 24,
    marginTop: 0,
    overflow: "hidden",
  },
  aquariumHeroInner: {
    position: "relative",
    minHeight: 220,
    overflow: "hidden",
    borderTopLeftRadius: 24,
    borderTopRightRadius: 24,
  },
  aquariumHeroPhoto: {
    position: "absolute",
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    width: "100%",
    height: "100%",
  },
  aquariumHeroGradient: {
    position: "absolute",
    left: 0,
    right: 0,
    bottom: 0,
    height: "70%",
  },
  aquariumHeroContent: {
    position: "relative",
    padding: 19,
    paddingTop: 24,
    gap: 9,
    zIndex: 1,
  },
  aquariumHeroTitle: {
    color: "#ffffff",
    fontWeight: "700",
  },
  aquariumHeroSubtitle: {
    color: "rgba(255, 255, 255, 0.96)",
  },
  aquariumDetailsRow: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 8,
  },
  aquariumDetailsText: {
    color: "rgba(255, 255, 255, 0.9)",
    backgroundColor: "rgba(0, 0, 0, 0.22)",
    paddingHorizontal: 7,
    paddingVertical: 3,
    borderRadius: 7,
  },
  classificationErrorText: {
    color: "#dc2626",
  },
});
