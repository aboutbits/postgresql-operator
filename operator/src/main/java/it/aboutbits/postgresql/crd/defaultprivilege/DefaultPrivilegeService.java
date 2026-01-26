package it.aboutbits.postgresql.crd.defaultprivilege;

import it.aboutbits.postgresql.core.Privilege;
import it.aboutbits.postgresql.core.SQLUtil;
import it.aboutbits.postgresql.core.infrastructure.persistence.Routines;
import jakarta.inject.Singleton;
import org.jooq.DSLContext;
import org.jooq.DataType;
import org.jooq.impl.SQLDataType;
import org.jspecify.annotations.NullMarked;

import java.util.Set;

import static it.aboutbits.postgresql.core.infrastructure.persistence.Tables.ACLEXPLODE;
import static it.aboutbits.postgresql.core.infrastructure.persistence.Tables.PG_DEFAULT_ACL;
import static it.aboutbits.postgresql.crd.defaultprivilege.DefaultPrivilegeObjectType.SCHEMA;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.noCondition;
import static org.jooq.impl.DSL.query;
import static org.jooq.impl.DSL.quotedName;
import static org.jooq.impl.DSL.role;
import static org.jooq.impl.DSL.sql;
import static org.jooq.impl.DSL.val;

@NullMarked
@Singleton
public class DefaultPrivilegeService {
    private static final DataType<Long> OID_DATA_TYPE = SQLDataType.BIGINT;

    // language=SQL
    private static final String ROLE_OID_SQL = "{0}::regrole";
    // language=SQL
    private static final String NAMESPACE_OID_SQL = "{0}::regnamespace";

    /// Determines all existing default privileges for the specified `role`, `schema`, and the given `objectType`.
    ///
    /// @param tx   The DSLContext for database operations.
    /// @param spec The DefaultPrivilegeSpec containing the specification details.
    /// @return A set of Privilege as values.
    public Set<Privilege> determineCurrentDefaultPrivileges(
            DSLContext tx,
            DefaultPrivilegeSpec spec
    ) {
        var owner = spec.getOwner();
        var role = spec.getRole();
        var schema = spec.getSchema();

        var objectType = spec.getObjectType();

        /*
         * select
         *   --d.defaclnamespace::regnamespace, -- Only for debugging
         *   a.privilege_type
         * from pg_catalog.pg_default_acl d
         * cross join aclexplode(d.defaclacl) a
         * where
         *   -- One of the following conditions
         *   --d.defaclobjtype = 'n' -- For schema
         *   --d.defaclobjtype = 'r' -- For table/view
         *   --d.defaclobjtype = 'S' -- For sequence
         *   --and d.defaclnamespace = '<schema_name>'::regnamespace -- Only if d.defaclobjtype is not 'n' (schema)
         *   and a.grantee = '<role_name>'::regrole
         */
        var currentObjectPrivileges = tx
                .select(ACLEXPLODE.PRIVILEGE_TYPE)
                .from(PG_DEFAULT_ACL)
                .crossJoin(Routines.aclexplode(PG_DEFAULT_ACL.DEFACLACL))
                .where(
                        PG_DEFAULT_ACL.DEFACLROLE.eq(field(
                                ROLE_OID_SQL,
                                OID_DATA_TYPE,
                                val(owner)
                        )),
                        PG_DEFAULT_ACL.DEFACLOBJTYPE.eq(objectType.objectTypeChar()),
                        objectType == SCHEMA
                                ? noCondition()
                                : PG_DEFAULT_ACL.DEFACLNAMESPACE.eq(field(
                                        NAMESPACE_OID_SQL,
                                        OID_DATA_TYPE,
                                        val(schema)
                                )),
                        ACLEXPLODE.GRANTEE.eq(field(
                                ROLE_OID_SQL,
                                OID_DATA_TYPE,
                                val(role)
                        ))
                )
                .fetch(r -> r.get(ACLEXPLODE.PRIVILEGE_TYPE, Privilege.class));

        return Set.copyOf(currentObjectPrivileges);
    }

    public void grant(
            DSLContext tx,
            DefaultPrivilegeSpec spec,
            Set<Privilege> privilegesToGrant
    ) {
        var owner = role(spec.getOwner());
        var role = role(spec.getRole());
        var objectType = spec.getObjectType();
        var schema = quotedName(spec.getSchema());

        var privileges = privilegesToGrant.stream()
                .map(Privilege::privilege)
                .toList();

        var statement = query(
                "alter default privileges for role {0}{1} grant {2} on {3}s to {4}",
                owner,
                sql(objectType == SCHEMA ? "" : " in schema {0}", schema),
                SQLUtil.concatenateQueryPartsWithComma(privileges),
                objectType.objectType(),
                role
        );

        tx.execute(statement);
    }

    public void revoke(
            DSLContext tx,
            DefaultPrivilegeSpec spec,
            Set<Privilege> privilegesToRevoke
    ) {
        var owner = role(spec.getOwner());
        var role = role(spec.getRole());
        var objectType = spec.getObjectType();
        var schema = quotedName(spec.getSchema());

        var privileges = privilegesToRevoke.stream()
                .map(Privilege::privilege)
                .toList();

        var statement = query(
                "alter default privileges for role {0}{1} revoke {2} on {3}s from {4}",
                owner,
                sql(objectType == SCHEMA ? "" : " in schema {0}", schema),
                SQLUtil.concatenateQueryPartsWithComma(privileges),
                objectType.objectType(),
                role
        );

        tx.execute(statement);
    }
}
