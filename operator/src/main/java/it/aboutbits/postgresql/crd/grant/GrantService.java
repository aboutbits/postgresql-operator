package it.aboutbits.postgresql.crd.grant;

import it.aboutbits.postgresql.core.SQLUtil;
import it.aboutbits.postgresql.core.infrastructure.persistence.Routines;
import jakarta.inject.Singleton;
import org.jooq.DSLContext;
import org.jooq.DataType;
import org.jooq.impl.SQLDataType;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static it.aboutbits.postgresql.core.infrastructure.persistence.Tables.ACLEXPLODE;
import static it.aboutbits.postgresql.core.infrastructure.persistence.Tables.PG_CLASS;
import static it.aboutbits.postgresql.core.infrastructure.persistence.Tables.PG_DATABASE;
import static it.aboutbits.postgresql.core.infrastructure.persistence.Tables.PG_NAMESPACE;
import static it.aboutbits.postgresql.crd.grant.GrantObjectType.SEQUENCE;
import static it.aboutbits.postgresql.crd.grant.GrantObjectType.TABLE;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.noCondition;
import static org.jooq.impl.DSL.query;
import static org.jooq.impl.DSL.quotedName;
import static org.jooq.impl.DSL.role;
import static org.jooq.impl.DSL.val;

@NullMarked
@Singleton
public class GrantService {
    private static final DataType<Long> OID_DATA_TYPE = SQLDataType.BIGINT;

    // language=SQL
    private static final String ROLE_OID_SQL = "{0}::regrole";
    // language=SQL
    private static final String NAMESPACE_OID_SQL = "{0}::regnamespace";

    /// Determines all existing privileges for the specified `role`, when applicable `schema`, and the given `objectTyoe`.
    ///
    /// @param tx       The DSLContext for database operations.
    /// @param resource The Grant resource containing the specification details.
    /// @return A map with object names as keys and lists of GrantPrivilege as values.
    @SuppressWarnings("checkstyle:MethodLength")
    public Map<String, Set<GrantPrivilege>> determineCurrentObjectPrivileges(
            DSLContext tx,
            Grant resource
    ) {
        var spec = resource.getSpec();

        var database = spec.getDatabase();
        var role = spec.getRole();
        var schema = spec.getSchema();

        var objectType = spec.getObjectType();

        var currentObjectPrivileges = switch (objectType) {
            /*
             * select
             *   d.datname,
             *   a.privilege_type
             * from pg_catalog.pg_database d
             * cross join aclexplode(d.datacl) a
             * where
             *   d.datname = '<database_name>'
             *   and a.grantee = '<role_name>'::regrole
             */
            case DATABASE -> tx
                    .select(
                            PG_DATABASE.DATNAME,
                            ACLEXPLODE.PRIVILEGE_TYPE
                    )
                    .from(PG_DATABASE)
                    .crossJoin(Routines.aclexplode(PG_DATABASE.DATACL))
                    .where(
                            PG_DATABASE.DATNAME.eq(database),
                            ACLEXPLODE.GRANTEE.eq(field(
                                    ROLE_OID_SQL,
                                    OID_DATA_TYPE,
                                    val(role)
                            ))
                    )
                    .fetchGroups(
                            PG_DATABASE.DATNAME,
                            r -> r.get(ACLEXPLODE.PRIVILEGE_TYPE, GrantPrivilege.class)
                    );
            /*
             * select
             *   s.nspname,
             *   a.privilege_type
             * from pg_catalog.pg_namespace s
             * cross join aclexplode(s.nspacl) a
             * where
             *   s.nspname = '<schema_name>'
             *   and a.grantee = '<role_name>'::regrole
             */
            case SCHEMA -> tx
                    .select(
                            PG_NAMESPACE.NSPNAME,
                            ACLEXPLODE.PRIVILEGE_TYPE
                    )
                    .from(PG_NAMESPACE)
                    .crossJoin(Routines.aclexplode(PG_NAMESPACE.NSPACL))
                    .where(
                            PG_NAMESPACE.NSPNAME.eq(schema),
                            ACLEXPLODE.GRANTEE.eq(field(
                                    ROLE_OID_SQL,
                                    OID_DATA_TYPE,
                                    val(role)
                            ))
                    )
                    .fetchGroups(
                            PG_NAMESPACE.NSPNAME,
                            r -> r.get(PG_NAMESPACE.NSPNAME, GrantPrivilege.class)
                    );

            /*
             * select
             *   c.relname,
             *   a.privilege_type
             * from pg_catalog.pg_class c
             * cross join aclexplode(c.relacl) a
             * where
             *   c.relnamespace = '<schema_name>'::regnamespace
             *   and c.relkind in ('r', 'p', 'v', 'm', 'f')
             *   and a.grantee = '<role_name>'::regrole
             */
            case TABLE -> tx
                    .select(
                            PG_CLASS.RELNAME,
                            ACLEXPLODE.PRIVILEGE_TYPE
                    )
                    .from(PG_CLASS)
                    .crossJoin(Routines.aclexplode(PG_CLASS.RELACL))
                    .where(
                            PG_CLASS.RELNAMESPACE.eq(field(
                                    NAMESPACE_OID_SQL,
                                    OID_DATA_TYPE,
                                    val(schema)
                            )),
                            // See https://www.postgresql.org/docs/current/catalog-pg-class.html#CATALOG-PG-CLASS
                            PG_CLASS.RELKIND.in(
                                    "r", // Ordinary Table
                                    "p", // Partitioned Table
                                    "v", // View
                                    "m", // Materialized View
                                    "f" // Foreign Table
                            ),
                            ACLEXPLODE.GRANTEE.eq(field(
                                    ROLE_OID_SQL,
                                    OID_DATA_TYPE,
                                    val(role)
                            ))
                    )
                    .fetchGroups(
                            PG_CLASS.RELNAME,
                            r -> r.get(ACLEXPLODE.PRIVILEGE_TYPE, GrantPrivilege.class)
                    );
            /*
             * select
             *   c.relname,
             *   a.privilege_type
             * from pg_catalog.pg_class c
             * cross join aclexplode(c.relacl) a
             * where
             *   c.relnamespace = '<schema_name>'::regnamespace
             *   and c.relkind = 'S'
             *   and a.grantee = '<role_name>'::regrole
             */
            case SEQUENCE -> tx
                    .select(
                            PG_CLASS.RELNAME,
                            ACLEXPLODE.PRIVILEGE_TYPE
                    )
                    .from(PG_CLASS)
                    .crossJoin(Routines.aclexplode(PG_CLASS.RELACL))
                    .where(
                            PG_CLASS.RELNAMESPACE.eq(field(
                                    NAMESPACE_OID_SQL,
                                    OID_DATA_TYPE,
                                    val(schema)
                            )),
                            // See https://www.postgresql.org/docs/current/catalog-pg-class.html#CATALOG-PG-CLASS
                            PG_CLASS.RELKIND.eq(
                                    "S" // Sequence
                            ),
                            ACLEXPLODE.GRANTEE.eq(field(
                                    ROLE_OID_SQL,
                                    OID_DATA_TYPE,
                                    val(role)
                            ))
                    )
                    .fetchGroups(
                            PG_CLASS.RELNAME,
                            r -> r.get(ACLEXPLODE.PRIVILEGE_TYPE, GrantPrivilege.class)
                    );
        };

        return currentObjectPrivileges.entrySet()
                .stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> new HashSet<>(entry.getValue())
                ));
    }

    /// Determines the database `objects` existence, and if it exists, the ownership
    /// based on the given resource specification and transaction context.
    ///
    /// If the `objects` List is empty, no condition is applied for object filtering,
    /// and thus all objects from this `namespace`/`schema` are returned.
    ///
    /// @param tx       the DSLContext used to execute database operations
    /// @param resource the Grant object containing specifications about the target database objects and privileges
    /// @return a map where the keys represent object names and the values indicate ownership status,
    /// or `null` if the object does not exist
    @SuppressWarnings({"checkstyle:MethodLength", "java:S3776"})
    public Map<String, @Nullable Boolean> determineObjectExistenceAndOwnership(
            DSLContext tx,
            Grant resource
    ) {
        var spec = resource.getSpec();

        var database = spec.getDatabase();
        var role = spec.getRole();
        var schema = spec.getSchema();

        var objectType = spec.getObjectType();
        var objects = spec.getObjects();

        var objectExistenceAndOwnershipMap = HashMap.<String, @Nullable Boolean>newHashMap(
                (objects.isEmpty() ? 1 : objects.size()) * spec.getPrivileges().size()
        );

        var isAllMode = objects.isEmpty();

        switch (objectType) {
            case DATABASE -> {
                /*
                 * select (d.datdba = '<role_name>'::regrole) is_owner
                 * from pg_catalog.pg_database d
                 * where
                 *   d.datname = '<database_name>'
                 */
                var isOwner = tx
                        .select(
                                field(PG_DATABASE.DATDBA.eq(field(
                                        ROLE_OID_SQL,
                                        OID_DATA_TYPE,
                                        val(role)
                                )))
                        )
                        .from(PG_DATABASE)
                        .where(PG_DATABASE.DATNAME.eq(database))
                        .fetchOneInto(Boolean.class);

                objectExistenceAndOwnershipMap.put(database, isOwner);
            }
            case SCHEMA -> {
                /*
                 * select (s.nspowner = '<role_name>'::regrole) is_owner
                 * from pg_catalog.pg_namespace s
                 * where
                 *   s.nspname = '<schema_name>'
                 */
                var isOwner = tx
                        .select(
                                field(PG_NAMESPACE.NSPOWNER.eq(field(
                                        ROLE_OID_SQL,
                                        OID_DATA_TYPE,
                                        val(role)
                                )))
                        )
                        .from(PG_NAMESPACE)
                        .where(PG_NAMESPACE.NSPNAME.eq(schema))
                        .fetchOneInto(Boolean.class);

                objectExistenceAndOwnershipMap.put(schema, isOwner);
            }
            case TABLE -> {
                /*
                 * Get all tables and if the role is the owner or not:
                 *
                 * select
                 *   c.relname,
                 *   (c.relowner = '<role_name>'::regrole) is_owner
                 * from pg_catalog.pg_class c
                 * where
                 *   c.relnamespace = '<schema_name>'::regnamespace
                 *   and c.relname in (<table_1>, <table_2>, ..., <table_n>) // only if we specified objects in the CRD
                 *   and c.relkind in ('r', 'p', 'v', 'm', 'f')
                 */
                var isOwnerCondition = PG_CLASS.RELOWNER.eq(field(
                                ROLE_OID_SQL,
                                OID_DATA_TYPE,
                                val(role)
                        )
                );

                var existingObjectsOwner = tx
                        .select(
                                PG_CLASS.RELNAME,
                                isOwnerCondition
                        )
                        .from(PG_CLASS)
                        .where(
                                PG_CLASS.RELNAMESPACE.eq(field(
                                        NAMESPACE_OID_SQL,
                                        OID_DATA_TYPE,
                                        val(schema)
                                )),
                                objects.isEmpty() ? noCondition() : PG_CLASS.RELNAME.in(objects),
                                // See https://www.postgresql.org/docs/current/catalog-pg-class.html#CATALOG-PG-CLASS
                                PG_CLASS.RELKIND.in(
                                        "r", // Ordinary Table
                                        "p", // Partitioned Table
                                        "v", // View
                                        "m", // Materialized View
                                        "f" // Foreign Table
                                )
                        )
                        .fetchMap(PG_CLASS.RELNAME, isOwnerCondition);

                if (objects.isEmpty()) {
                    objectExistenceAndOwnershipMap.putAll(existingObjectsOwner);
                } else {
                    for (var object : objects) {
                        objectExistenceAndOwnershipMap.put(
                                object,
                                existingObjectsOwner.get(object)
                        );
                    }
                }
            }
            case SEQUENCE -> {
                /*
                 * Get all sequences and if the role is the owner or not:
                 *
                 * select
                 *   c.relname,
                 *   (c.relowner = '<role_name>'::regrole) is_owner
                 * from pg_catalog.pg_class c
                 * where
                 *   c.relnamespace = '<schema_name>'::regnamespace
                 *   and c.relname in (<sequence_1>, <sequence_2>, ..., <sequence_n>) // only if we specified objects in the CRD
                 *   and c.relkind = 'S'
                 */
                var isOwnerCondition = PG_CLASS.RELOWNER.eq(field(
                                ROLE_OID_SQL,
                                OID_DATA_TYPE,
                                val(role)
                        )
                );

                var existingObjectsOwner = tx
                        .select(
                                PG_CLASS.RELNAME,
                                isOwnerCondition
                        )
                        .from(PG_CLASS)
                        .where(
                                PG_CLASS.RELNAMESPACE.eq(field(
                                        NAMESPACE_OID_SQL,
                                        OID_DATA_TYPE,
                                        val(schema)
                                )),
                                isAllMode ? noCondition() : PG_CLASS.RELNAME.in(objects),
                                // See https://www.postgresql.org/docs/current/catalog-pg-class.html#CATALOG-PG-CLASS
                                PG_CLASS.RELKIND.eq(
                                        "S" // Sequence
                                )
                        )
                        .fetchMap(PG_CLASS.RELNAME, isOwnerCondition);

                if (isAllMode) {
                    objectExistenceAndOwnershipMap.putAll(existingObjectsOwner);
                } else {
                    for (var object : objects) {
                        objectExistenceAndOwnershipMap.put(
                                object,
                                existingObjectsOwner.get(object)
                        );
                    }
                }
            }

            default -> throw new UnsupportedOperationException(
                    "The GrantObjectType is not implemented yet [objectType=%s]".formatted(
                            objectType
                    )
            );
        }

        return objectExistenceAndOwnershipMap;
    }

    public void grant(
            DSLContext tx,
            Grant resource,
            String object,
            Set<GrantPrivilege> privilegesToGrant
    ) {
        var spec = resource.getSpec();

        var schema = spec.getSchema();
        var role = role(spec.getRole());
        var objectType = spec.getObjectType();

        var qualifiedObject = switch (objectType) {
            case TABLE, SEQUENCE -> quotedName(schema, object);
            default -> quotedName(object);
        };

        var privileges = privilegesToGrant.stream()
                .map(GrantPrivilege::privilege)
                .toList();

        var statement = query(
                "grant {0} on {1} {2} to {3}",
                SQLUtil.concatenateQueryPartsWithComma(privileges),
                objectType.objectType(),
                qualifiedObject,
                role
        );

        tx.execute(statement);
    }

    public void grantOnAll(
            DSLContext tx,
            Grant resource,
            Set<GrantPrivilege> privilegesToGrant
    ) {
        var spec = resource.getSpec();

        var schema = quotedName(spec.getSchema());
        var role = role(spec.getRole());
        var objectType = spec.getObjectType();

        // grant <privileges> on all <objectType>s in schema <schema> to <role>;
        // is only supported by TABLE, SEQUENCE, FUNCTION, PROCEDURE and ROUTINE
        if (objectType != TABLE && objectType != SEQUENCE) {
            return;
        }

        var privileges = privilegesToGrant.stream()
                .map(GrantPrivilege::privilege)
                .toList();

        var statement = query(
                "grant {0} on all {1}s in schema {2} to {3}",
                SQLUtil.concatenateQueryPartsWithComma(privileges),
                objectType.objectType(),
                schema,
                role
        );

        tx.execute(statement);
    }

    public void revoke(
            DSLContext tx,
            Grant resource,
            String object,
            Set<GrantPrivilege> privilegesToRevoke
    ) {
        var spec = resource.getSpec();

        var schema = spec.getSchema();
        var role = role(spec.getRole());
        var objectType = spec.getObjectType();

        var qualifiedObject = switch (objectType) {
            case TABLE, SEQUENCE -> quotedName(schema, object);
            default -> quotedName(object);
        };

        var privileges = privilegesToRevoke.stream()
                .map(GrantPrivilege::privilege)
                .toList();

        var statement = query(
                "revoke {0} on {1} {2} from {3}",
                SQLUtil.concatenateQueryPartsWithComma(privileges),
                objectType.objectType(),
                qualifiedObject,
                role
        );

        tx.execute(statement);
    }
}
