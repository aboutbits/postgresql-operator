# Database

The `Database` Custom Resource Definition (CRD) is responsible for managing PostgreSQL databases.

## Spec

| Field           | Type               | Description                                                                                          | Required | Immutable |
|-----------------|--------------------|------------------------------------------------------------------------------------------------------|----------|-----------|
| `clusterRef`    | `ClusterReference` | Reference to the `ClusterConnection` to use.                                                         | Yes      | No        |
| `name`          | `string`           | The name of the database to create.                                                                  | Yes      | Yes       |
| `owner`         | `string`           | The owner of the database.                                                                           | No       | No        |
| `reclaimPolicy` | `string`           | The policy for reclaiming the database when the CR is deleted. Values: `Retain` (Default), `Delete`. | No       | No        |

### ClusterReference

| Field       | Type     | Description                                                                      | Required |
|-------------|----------|----------------------------------------------------------------------------------|----------|
| `name`      | `string` | Name of the `ClusterConnection`.                                                 | Yes      |
| `namespace` | `string` | Namespace of the `ClusterConnection`. If not specified, uses the CR's namespace. | No       |

### Reclaim Policy

The `reclaimPolicy` controls what happens to the PostgreSQL database when the Custom Resource is deleted from Kubernetes.

- `Retain` (Default): The database remains in the PostgreSQL cluster. Only the Kubernetes Custom Resource is deleted. This prevents accidental data loss.
- `Delete`: The database is dropped from the PostgreSQL cluster. **Warning:** This will permanently delete the database and all its data.

## Example

```yaml
apiVersion: postgresql.aboutbits.it/v1
kind: Database
metadata:
  name: my-database
spec:
  clusterRef:
    name: my-postgres-connection
  name: my_database
  owner: my_role
  reclaimPolicy: Retain
```

## Official Documentation

- [CREATE DATABASE](https://www.postgresql.org/docs/current/sql-createdatabase.html)
- [ALTER DATABASE](https://www.postgresql.org/docs/current/sql-alterdatabase.html)
