import { gcm } from "@noble/ciphers/aes.js";
import { hmac } from "@noble/hashes/hmac.js";
import { pbkdf2 } from "@noble/hashes/pbkdf2.js";
import { sha256 } from "@noble/hashes/sha2.js";
import { bytesToHex, utf8ToBytes } from "@noble/hashes/utils.js";
import { fromByteArray, toByteArray } from "base64-js";

import { PersistedAppState } from "@/services/persistence";

const BACKUP_SCHEMA_VERSION = 1;
const ENCRYPTION_VERSION = 1;
const KDF_ITERATIONS = 210_000;
const KDF_SALT_BYTES = 16;
const GCM_NONCE_BYTES = 12;
const DERIVED_KEY_BYTES = 32;

export interface BackupEnvelope {
  schemaVersion: number;
  exportedAt: string;
  appState: PersistedAppState;
}

export interface EncryptedBackupPayload {
  version: number;
  algorithm: "AES-256-GCM";
  kdf: {
    name: "PBKDF2-HMAC-SHA256";
    iterations: number;
    saltB64: string;
  };
  nonceB64: string;
  ciphertextB64: string;
}

export interface S3SyncConfig {
  endpoint: string;
  region: string;
  bucket: string;
  objectKey: string;
  accessKeyId: string;
  secretAccessKey: string;
  forcePathStyle?: boolean;
}

export interface BackupSyncOutcome {
  uploadedAt: string;
  objectUrl: string;
  payloadBytes: number;
}

const textEncoder = new TextEncoder();
const textDecoder = new TextDecoder();

function isoNow() {
  return new Date().toISOString();
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}

function normalizeKeyPrefix(key: string) {
  return key.trim().replace(/^\/+/, "");
}

function encodePathSegment(segment: string) {
  return encodeURIComponent(segment).replace(/[!*'()]/g, (token) => {
    const hex = token.charCodeAt(0).toString(16).toUpperCase();
    return `%${hex}`;
  });
}

function encodeObjectPath(path: string) {
  return path
    .split("/")
    .filter(Boolean)
    .map((segment) => encodePathSegment(segment))
    .join("/");
}

function formatAmzDate(date: Date) {
  return date.toISOString().replace(/[:-]|\.\d{3}/g, "");
}

function formatDateStamp(date: Date) {
  return formatAmzDate(date).slice(0, 8);
}

function sha256Hex(input: Uint8Array | string) {
  const bytes = typeof input === "string" ? textEncoder.encode(input) : input;
  return bytesToHex(sha256(bytes));
}

function hmacSha256(key: Uint8Array | string, message: Uint8Array | string) {
  const normalizedKey = typeof key === "string" ? textEncoder.encode(key) : key;
  const normalizedMessage =
    typeof message === "string" ? textEncoder.encode(message) : message;

  return hmac(sha256, normalizedKey, normalizedMessage);
}

function deriveSigV4SigningKey(
  secretAccessKey: string,
  dateStamp: string,
  region: string,
) {
  const kDate = hmacSha256(`AWS4${secretAccessKey}`, dateStamp);
  const kRegion = hmacSha256(kDate, region);
  const kService = hmacSha256(kRegion, "s3");
  const kSigning = hmacSha256(kService, "aws4_request");

  return kSigning;
}

function buildSignedRequestParams(
  method: "GET" | "PUT" | "DELETE",
  endpoint: URL,
  canonicalUri: string,
  canonicalQuery: string,
  payload: Uint8Array,
  credentials: {
    accessKeyId: string;
    secretAccessKey: string;
    region: string;
  },
) {
  const timestamp = new Date();
  const amzDate = formatAmzDate(timestamp);
  const dateStamp = formatDateStamp(timestamp);
  const payloadHash = sha256Hex(payload);

  const host = endpoint.host;
  const canonicalHeaders =
    `host:${host}\n` +
    `x-amz-content-sha256:${payloadHash}\n` +
    `x-amz-date:${amzDate}\n`;
  const signedHeaders = "host;x-amz-content-sha256;x-amz-date";
  const canonicalRequest = [
    method,
    canonicalUri,
    canonicalQuery,
    canonicalHeaders,
    signedHeaders,
    payloadHash,
  ].join("\n");

  const credentialScope = `${dateStamp}/${credentials.region}/s3/aws4_request`;
  const stringToSign = [
    "AWS4-HMAC-SHA256",
    amzDate,
    credentialScope,
    sha256Hex(canonicalRequest),
  ].join("\n");

  const signingKey = deriveSigV4SigningKey(
    credentials.secretAccessKey,
    dateStamp,
    credentials.region,
  );
  const signature = bytesToHex(hmacSha256(signingKey, stringToSign));

  const authorization =
    `AWS4-HMAC-SHA256 Credential=${credentials.accessKeyId}/${credentialScope}, ` +
    `SignedHeaders=${signedHeaders}, Signature=${signature}`;

  return {
    headers: {
      Authorization: authorization,
      "x-amz-date": amzDate,
      "x-amz-content-sha256": payloadHash,
      host,
    },
  };
}

function deriveEncryptionKey(masterKey: string, salt: Uint8Array) {
  const normalized = masterKey.trim();
  if (normalized.length < 12) {
    throw new Error("Master key must be at least 12 characters.");
  }

  return pbkdf2(sha256, utf8ToBytes(normalized), salt, {
    c: KDF_ITERATIONS,
    dkLen: DERIVED_KEY_BYTES,
  });
}

function randomBytes(length: number) {
  const output = new Uint8Array(length);
  crypto.getRandomValues(output);
  return output;
}

export function createBackupEnvelope(state: PersistedAppState): BackupEnvelope {
  return {
    schemaVersion: BACKUP_SCHEMA_VERSION,
    exportedAt: isoNow(),
    appState: state,
  };
}

export function encryptBackupEnvelope(
  envelope: BackupEnvelope,
  masterKey: string,
): string {
  const salt = randomBytes(KDF_SALT_BYTES);
  const nonce = randomBytes(GCM_NONCE_BYTES);
  const key = deriveEncryptionKey(masterKey, salt);

  const plaintext = textEncoder.encode(JSON.stringify(envelope));
  const encrypted = gcm(key, nonce).encrypt(plaintext);

  const payload: EncryptedBackupPayload = {
    version: ENCRYPTION_VERSION,
    algorithm: "AES-256-GCM",
    kdf: {
      name: "PBKDF2-HMAC-SHA256",
      iterations: KDF_ITERATIONS,
      saltB64: fromByteArray(salt),
    },
    nonceB64: fromByteArray(nonce),
    ciphertextB64: fromByteArray(encrypted),
  };

  return JSON.stringify(payload);
}

export function decryptBackupEnvelope(
  encryptedPayloadJson: string,
  masterKey: string,
): BackupEnvelope {
  const payload = JSON.parse(encryptedPayloadJson) as EncryptedBackupPayload;

  if (
    !isRecord(payload) ||
    payload.algorithm !== "AES-256-GCM" ||
    !isRecord(payload.kdf) ||
    payload.kdf.name !== "PBKDF2-HMAC-SHA256"
  ) {
    throw new Error("Unsupported or malformed encrypted backup payload.");
  }

  const salt = toByteArray(payload.kdf.saltB64);
  const nonce = toByteArray(payload.nonceB64);
  const ciphertext = toByteArray(payload.ciphertextB64);

  const key = deriveEncryptionKey(masterKey, salt);
  const plaintext = gcm(key, nonce).decrypt(ciphertext);
  const decoded = textDecoder.decode(plaintext);
  const envelope = JSON.parse(decoded) as BackupEnvelope;

  if (!isRecord(envelope) || !isRecord(envelope.appState)) {
    throw new Error("Decrypted payload is invalid.");
  }

  return envelope;
}

function resolveS3ObjectUrl(config: S3SyncConfig) {
  const endpoint = new URL(config.endpoint.trim());
  const normalizedKey = normalizeKeyPrefix(config.objectKey);
  if (!normalizedKey) {
    throw new Error("Object key is required.");
  }

  const encodedKeyPath = encodeObjectPath(normalizedKey);
  const bucket = config.bucket.trim();
  if (!bucket) {
    throw new Error("Bucket is required.");
  }

  const pathStyle = config.forcePathStyle ?? true;

  if (pathStyle) {
    const path = `/${encodePathSegment(bucket)}/${encodedKeyPath}`;
    return {
      endpoint,
      canonicalUri: path,
      requestUrl: `${endpoint.origin}${path}`,
    };
  }

  const virtualHost = `${bucket}.${endpoint.host}`;
  const path = `/${encodedKeyPath}`;
  return {
    endpoint: new URL(`${endpoint.protocol}//${virtualHost}`),
    canonicalUri: path,
    requestUrl: `${endpoint.protocol}//${virtualHost}${path}`,
  };
}

function resolveS3BucketRoot(config: S3SyncConfig) {
  const endpoint = new URL(config.endpoint.trim());
  const bucket = config.bucket.trim();
  const pathStyle = config.forcePathStyle ?? true;

  if (pathStyle) {
    const path = `/${encodePathSegment(bucket)}`;
    return {
      endpoint,
      canonicalUri: path,
      requestUrl: `${endpoint.origin}${path}`,
    };
  }

  const virtualHost = `${bucket}.${endpoint.host}`;
  return {
    endpoint: new URL(`${endpoint.protocol}//${virtualHost}`),
    canonicalUri: "/",
    requestUrl: `${endpoint.protocol}//${virtualHost}`,
  };
}

function encodeQueryValue(value: string) {
  return encodeURIComponent(value)
    .replace(/\+/g, "%20")
    .replace(/\*/g, "%2A")
    .replace(/%7E/g, "~");
}

function canonicalizeQuery(queryParams: Record<string, string>) {
  return Object.entries(queryParams)
    .filter(([, value]) => value !== "")
    .sort(([a], [b]) => a.localeCompare(b))
    .map(
      ([key, value]) => `${encodeQueryValue(key)}=${encodeQueryValue(value)}`,
    )
    .join("&");
}

function getHistoryPrefixFromObjectKey(objectKey: string) {
  const normalized = normalizeKeyPrefix(objectKey);
  const slashIndex = normalized.lastIndexOf("/");
  const directory = slashIndex >= 0 ? normalized.slice(0, slashIndex) : "";
  return `${directory ? `${directory}/` : ""}history/`;
}

function decodeXmlEntities(value: string) {
  return value
    .replace(/&amp;/g, "&")
    .replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">")
    .replace(/&quot;/g, '"')
    .replace(/&#39;/g, "'");
}

function parseS3ListResponse(xml: string) {
  const result: { key: string; lastModified?: string }[] = [];
  const contentBlocks = xml.match(/<Contents>[\s\S]*?<\/Contents>/g) ?? [];

  contentBlocks.forEach((block) => {
    const keyMatch = block.match(/<Key>([\s\S]*?)<\/Key>/);
    if (!keyMatch) {
      return;
    }

    const lastModifiedMatch = block.match(
      /<LastModified>([\s\S]*?)<\/LastModified>/,
    );

    result.push({
      key: decodeXmlEntities(keyMatch[1]),
      lastModified: lastModifiedMatch?.[1],
    });
  });

  return result;
}

export interface S3HistoryCleanupOutcome {
  deletedKeys: string[];
  keptKeys: string[];
}

export function buildVersionedBackupObjectKey(
  latestObjectKey: string,
  isoTimestamp: string,
) {
  const day = getBackupDateStamp(isoTimestamp);
  if (!day) {
    throw new Error("Invalid timestamp for versioned backup key.");
  }

  const historyPrefix = getHistoryPrefixFromObjectKey(latestObjectKey);
  return `${historyPrefix}${day}.enc.json`;
}

export async function listS3ObjectsWithPrefix(
  configInput: S3SyncConfig,
  prefix: string,
  maxKeys = 500,
) {
  const config = parseS3Config(configInput);
  const resolved = resolveS3BucketRoot(config);
  const payload = new Uint8Array(0);
  const canonicalQuery = canonicalizeQuery({
    "list-type": "2",
    "max-keys": String(Math.max(1, Math.min(1000, maxKeys))),
    prefix,
  });

  const signed = buildSignedRequestParams(
    "GET",
    resolved.endpoint,
    resolved.canonicalUri,
    canonicalQuery,
    payload,
    {
      accessKeyId: config.accessKeyId,
      secretAccessKey: config.secretAccessKey,
      region: config.region,
    },
  );

  const response = await fetch(`${resolved.requestUrl}?${canonicalQuery}`, {
    method: "GET",
    headers: signed.headers,
  });

  if (!response.ok) {
    const body = await response.text();
    throw new Error(
      `S3 list failed (${response.status} ${response.statusText}): ${body.slice(0, 220)}`,
    );
  }

  const xml = await response.text();
  return parseS3ListResponse(xml);
}

export async function deleteS3Object(
  configInput: S3SyncConfig,
  objectKey: string,
) {
  const config = parseS3Config({ ...configInput, objectKey });
  const payload = new Uint8Array(0);
  const resolved = resolveS3ObjectUrl(config);

  const signed = buildSignedRequestParams(
    "DELETE",
    resolved.endpoint,
    resolved.canonicalUri,
    "",
    payload,
    {
      accessKeyId: config.accessKeyId,
      secretAccessKey: config.secretAccessKey,
      region: config.region,
    },
  );

  const response = await fetch(resolved.requestUrl, {
    method: "DELETE",
    headers: signed.headers,
  });

  if (!response.ok && response.status !== 404) {
    const body = await response.text();
    throw new Error(
      `S3 delete failed (${response.status} ${response.statusText}): ${body.slice(0, 220)}`,
    );
  }
}

export async function cleanupVersionedBackups(
  configInput: S3SyncConfig,
  latestObjectKey: string,
  retentionDays: number,
): Promise<S3HistoryCleanupOutcome> {
  const config = parseS3Config(configInput);
  const historyPrefix = getHistoryPrefixFromObjectKey(latestObjectKey);
  const objects = await listS3ObjectsWithPrefix(config, historyPrefix, 1000);

  const normalizedRetention = Math.max(1, Math.min(3650, retentionDays));
  const cutoffMillis = Date.now() - normalizedRetention * 24 * 60 * 60 * 1000;

  const deletable = objects
    .filter((entry) => entry.key.endsWith(".enc.json"))
    .filter((entry) => {
      const millis = entry.lastModified ? Date.parse(entry.lastModified) : NaN;
      return Number.isFinite(millis) && millis < cutoffMillis;
    })
    .sort((a, b) => (a.lastModified ?? "").localeCompare(b.lastModified ?? ""));

  for (const entry of deletable) {
    await deleteS3Object(config, entry.key);
  }

  const deletedKeys = deletable.map((entry) => entry.key);
  const keptKeys = objects
    .map((entry) => entry.key)
    .filter((key) => !deletedKeys.includes(key));

  return {
    deletedKeys,
    keptKeys,
  };
}

function parseS3Config(config: S3SyncConfig): S3SyncConfig {
  const normalized: S3SyncConfig = {
    endpoint: config.endpoint.trim(),
    region: config.region.trim(),
    bucket: config.bucket.trim(),
    objectKey: normalizeKeyPrefix(config.objectKey),
    accessKeyId: config.accessKeyId.trim(),
    secretAccessKey: config.secretAccessKey.trim(),
    forcePathStyle: config.forcePathStyle ?? true,
  };

  if (!normalized.endpoint) {
    throw new Error("S3 endpoint is required.");
  }
  if (!normalized.region) {
    throw new Error("S3 region is required.");
  }
  if (!normalized.bucket) {
    throw new Error("S3 bucket is required.");
  }
  if (!normalized.objectKey) {
    throw new Error("S3 object key is required.");
  }
  if (!normalized.accessKeyId || !normalized.secretAccessKey) {
    throw new Error("S3 credentials are required.");
  }

  return normalized;
}

export async function uploadEncryptedBackupToS3(
  configInput: S3SyncConfig,
  encryptedPayload: string,
): Promise<BackupSyncOutcome> {
  const config = parseS3Config(configInput);
  const bytes = textEncoder.encode(encryptedPayload);

  const resolved = resolveS3ObjectUrl(config);
  const signed = buildSignedRequestParams(
    "PUT",
    resolved.endpoint,
    resolved.canonicalUri,
    "",
    bytes,
    {
      accessKeyId: config.accessKeyId,
      secretAccessKey: config.secretAccessKey,
      region: config.region,
    },
  );

  const response = await fetch(resolved.requestUrl, {
    method: "PUT",
    headers: {
      ...signed.headers,
      "content-type": "application/json",
      "content-length": String(bytes.byteLength),
    },
    body: encryptedPayload,
  });

  if (!response.ok) {
    const body = await response.text();
    throw new Error(
      `S3 upload failed (${response.status} ${response.statusText}): ${body.slice(0, 220)}`,
    );
  }

  return {
    uploadedAt: isoNow(),
    objectUrl: resolved.requestUrl,
    payloadBytes: bytes.byteLength,
  };
}

export async function downloadEncryptedBackupFromS3(
  configInput: S3SyncConfig,
): Promise<string | null> {
  const config = parseS3Config(configInput);
  const emptyPayload = new Uint8Array(0);
  const resolved = resolveS3ObjectUrl(config);

  const signed = buildSignedRequestParams(
    "GET",
    resolved.endpoint,
    resolved.canonicalUri,
    "",
    emptyPayload,
    {
      accessKeyId: config.accessKeyId,
      secretAccessKey: config.secretAccessKey,
      region: config.region,
    },
  );

  const response = await fetch(resolved.requestUrl, {
    method: "GET",
    headers: signed.headers,
  });

  if (response.status === 404) {
    return null;
  }

  if (!response.ok) {
    const body = await response.text();
    throw new Error(
      `S3 download failed (${response.status} ${response.statusText}): ${body.slice(0, 220)}`,
    );
  }

  return await response.text();
}

export function getBackupDateStamp(isoTimestamp: string) {
  const date = new Date(isoTimestamp);
  if (Number.isNaN(date.getTime())) {
    return "";
  }

  return date.toISOString().slice(0, 10);
}

export function compareIsoTimestamps(a?: string, b?: string) {
  const aMillis = a ? Date.parse(a) : Number.NaN;
  const bMillis = b ? Date.parse(b) : Number.NaN;

  if (!Number.isFinite(aMillis) && !Number.isFinite(bMillis)) {
    return 0;
  }
  if (!Number.isFinite(aMillis)) {
    return -1;
  }
  if (!Number.isFinite(bMillis)) {
    return 1;
  }

  if (aMillis === bMillis) {
    return 0;
  }

  return aMillis > bMillis ? 1 : -1;
}
