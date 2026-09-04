# Terraform

The Custom Resources of this Operator can be managed with the
[`kubernetes_manifest`](https://registry.terraform.io/providers/hashicorp/kubernetes/latest/docs/resources/manifest)
resource of the `hashicorp/kubernetes` provider.

## Never set optional fields to `null`

**Omit** optional spec fields from the manifest instead of setting them to `null`. A field that is present but `null` produces a permanent in-place update on every single plan:

```hcl
# module.postgresql_role.kubernetes_manifest.postgresql_role_cr will be updated in-place
~ resource "kubernetes_manifest" "postgresql_role_cr" {
    ~ object = {
        ~ spec = {
            + comment = (known after apply)
            ~ flags = {
                + validUntil = (known after apply)
              }
          }
      }
  }
```

### Why this happens

While planning, the provider fills every field with the CRD schema that the configuration does not set with an unknown value,  
and then takes the value from the prior state again - unless the field was present in the previous configuration.  
In that case it keeps the value unknown, to give the API server a chance to default it.

A `null` counts as "present" here. As this Operator does not default these fields, the applied object never contains them, the refreshed state holds `null` again,
and the next plan repeats the same `(known after apply)`. The plan never converges.

A field that is **absent** from the configuration produces no diff at all.

This is a known limitation of the provider, see [hashicorp/terraform-provider-kubernetes#2669](https://github.com/hashicorp/terraform-provider-kubernetes/issues/2669).

### How to avoid it

Build the `spec` with [`merge`](https://developer.hashicorp.com/terraform/language/functions/merge)
and only add optional attributes when they actually have a value:

```hcl
variable "comment" {
  type = string
  default = null
  nullable = true
}

variable "valid_until" {
  type = string
  default = null
  nullable = true
}

resource "kubernetes_manifest" "postgresql_role_cr" {
  manifest = {
    apiVersion = "postgresql.aboutbits.it/v1"
    kind = "Role"

    metadata = {
      namespace = var.namespace
      name = var.name
    }

    spec = merge(
      {
        clusterRef = {
          namespace = var.cluster_ref_namespace
          name = var.cluster_ref_name
        }
        name = var.role
        flags = merge(
          {
            createdb = var.flag_createdb
          },
          var.valid_until == null ? {} : {
            validUntil = var.valid_until
          },
        )
      },
      var.comment == null ? {} : {
        comment = var.comment
      },
    )
  }
}
```

For variables that have a non-`null` default, declaring them as `nullable = false` additionally makes Terraform fall back to the default whenever a caller passes `null` explicitly.

### Affected fields

Every optional field of every Custom Resource is affected, in particular:

| Custom Resource     | Optional fields                                                                                |
|---------------------|------------------------------------------------------------------------------------------------|
| `ClusterConnection` | `parameters`, `adminSecretRef`, `adminSecretRef.namespace`, `adminSecretFileRef`               |
| `Database`          | `owner`, `reclaimPolicy`, `clusterRef.namespace`                                               |
| `Schema`            | `owner`, `reclaimPolicy`, `clusterRef.namespace`                                               |
| `Role`              | `comment`, `passwordSecretRef`, `flags` (including `flags.validUntil`), `clusterRef.namespace` |
| `Grant`             | `schema`, `objects`, `clusterRef.namespace`                                                    |
| `DefaultPrivilege`  | `schema`, `clusterRef.namespace`                                                               |
