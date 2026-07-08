# Cloud Memory Sync Design

## Goal

Keep local memory as the source of truth for Phase 1/2 while allowing a future cloud agent to read and reconcile memory safely.

## Principles

- Local-first: Room remains authoritative for user-visible memory until a user enables cloud sync.
- No raw image upload by default: vision memories sync text summaries and metadata only; `imageBase64` stays local unless a later explicit media consent exists.
- Source-aware reconciliation: every synced item carries `source`, `sourceMessageIds`, `confidence`, `importance`, `updatedAt`, and `sensitivity`.
- Read before write: the first remote integration should support read-only memory context export before accepting cloud writes.
- User deletion wins: local archive/delete tombstones must suppress older cloud copies.

## Minimal Payload

```json
{
  "deviceId": "local-device",
  "schemaVersion": 1,
  "cursor": "last-server-token",
  "memories": [
    {
      "id": "memory-id",
      "type": "FACT",
      "content": "User likes jasmine tea",
      "source": "tool:update_state",
      "sourceMessageIds": ["message-id"],
      "importance": 0.7,
      "confidence": 0.8,
      "sensitivity": "normal",
      "updatedAt": 1710000000000,
      "archived": false
    }
  ]
}
```

## Conflict Rules

- Same `id`: latest `updatedAt` wins unless either side is archived; archived wins.
- Similar content with different ids: keep both on device, let the local merge path handle future writes.
- Cloud write with lower confidence than local memory: do not overwrite content; merge source metadata only.
- Sensitive memory: sync only when the future privacy setting allows that sensitivity tier.

## Rollout

1. Export read-only memory context to remote agent requests.
2. Add a sync cursor table and tombstone table.
3. Add cloud pull with local preview/audit logging.
4. Add opt-in push for non-sensitive memories.
5. Add UI controls for cloud memory visibility, deletion, and per-source audit.
