# Schema

The `Schema` Custom Resource Definition (CRD) is responsible for managing PostgreSQL schemas.

## Spec

| Field           | Type               | Description                                                                                        | Required | Immutable |
|-----------------|--------------------|----------------------------------------------------------------------------------------------------|----------|-----------|
| `clusterRef`    | `ClusterReference` | Reference to the `ClusterConnection` to use.                                                       | Yes      | No        |
| `name`          | `string`           | The name of the schema to create.                                                                  | Yes      | Yes       |
| `owner`         | `string`           | The owner of the schema.                                                                           | No       | No        |
| `reclaimPolicy` | `string`           | The policy for reclaiming the schema when the CR is deleted. Values: `Retain` (Default), `Delete`. | No       | No        |

### ClusterReference

| Field       | Type     | Description                                                                      | Required |
|-------------|----------|----------------------------------------------------------------------------------|----------|
| `name`      | `string` | Name of the `ClusterConnection`.                                                 | Yes      |
| `namespace` | `string` | Namespace of the `ClusterConnection`. If not specified, uses the CR's namespace. | No       |

### Reclaim Policy

The `reclaimPolicy` controls what happens to the PostgreSQL schema when the Custom Resource is deleted from Kubernetes.

- `Retain` (Default): The schema remains in the PostgreSQL database. Only the Kubernetes Custom Resource is deleted. This prevents accidental data loss.
- `Delete`: The schema is dropped from the PostgreSQL database. **Warning:** This will permanently delete the schema and all objects (tables, views, etc.) within it.

## Example

```yaml
apiVersion: postgresql.aboutbits.it/v1
kind: Schema
metadata:
  name: my-schema
spec:
  clusterRef:
    name: my-postgres-connection
  name: my_schema
  owner: my_role
  reclaimPolicy: Retain
```

## Official Documentation

- [CREATE SCHEMA](https://www.postgresql.org/docs/current/sql-createschema.html)
- [ALTER SCHEMA](https://www.postgresql.org/docs/current/sql-alterschema.html)
