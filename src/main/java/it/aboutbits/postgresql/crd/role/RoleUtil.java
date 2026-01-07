package it.aboutbits.postgresql.crd.role;

import it.aboutbits.postgresql.core.infrastructure.persistence.Routines;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Query;
import org.jooq.QueryPart;
import org.jooq.Record1;
import org.jooq.impl.DSL;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import static it.aboutbits.postgresql.core.infrastructure.persistence.Tables.PG_AUTHID;
import static it.aboutbits.postgresql.core.infrastructure.persistence.Tables.PG_AUTH_MEMBERS;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.keyword;
import static org.jooq.impl.DSL.multiset;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.query;
import static org.jooq.impl.DSL.role;
import static org.jooq.impl.DSL.select;
import static org.jooq.impl.DSL.selectOne;
import static org.jooq.impl.DSL.sql;
import static org.jooq.impl.DSL.val;

@NullMarked
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class RoleUtil {
    public static boolean roleExists(
            DSLContext tx,
            RoleSpec spec
    ) {
        return tx.fetchExists(selectOne()
                .from(PG_AUTHID)
                .where(PG_AUTHID.ROLNAME.eq(spec.getName()))
        );
    }

    public static void createRole(
            DSLContext tx,
            RoleSpec spec,
            @Nullable String password
    ) {
        var roleName = spec.getName();
        var flags = spec.getFlags();
        var comment = spec.getComment();

        tx.execute(
                buildCreateRole(
                        roleName,
                        flags,
                        password
                )
        );

        // Optional comment
        if (!comment.isBlank()) {
            tx.execute(
                    buildCommentOnRole(roleName, comment)
            );
        }
    }

    public static void alterRole(
            DSLContext tx,
            RoleSpec spec,
            boolean changePassword,
            @Nullable String password
    ) {
        var roleName = spec.getName();
        var flags = spec.getFlags();
        var expectedComment = normalizeComment(spec.getComment());

        tx.execute(
                buildAlterRole(
                        roleName,
                        flags,
                        changePassword,
                        password
                )
        );

        var currentComment = normalizeComment(
                fetchCurrentRoleComment(tx, roleName)
        );

        if (!Objects.equals(currentComment, expectedComment)) {
            tx.execute(
                    buildCommentOnRole(roleName, expectedComment)
            );
        }
    }

    public static boolean roleLoginMatches(
            DSLContext tx,
            RoleSpec spec
    ) {
        var loginExpected = spec.getPasswordSecretRef() != null;

        var canLogin = tx.fetchExists(selectOne()
                .from(PG_AUTHID)
                .where(PG_AUTHID.ROLNAME.eq(spec.getName()))
                .and(PG_AUTHID.ROLCANLOGIN.isTrue())
        );

        return loginExpected == canLogin;
    }

    public static RoleSpec.Flags fetchCurrentFlags(
            DSLContext tx,
            RoleSpec spec
    ) {
        var member = PG_AUTHID.as("member");
        var parent = PG_AUTHID.as("parent");

        return tx
                .select(
                        PG_AUTHID.ROLSUPER.as("superuser"),
                        PG_AUTHID.ROLCREATEDB.as("createdb"),
                        PG_AUTHID.ROLCREATEROLE.as("createrole"),
                        PG_AUTHID.ROLINHERIT.as("inherit"),
                        PG_AUTHID.ROLREPLICATION.as("replication"),
                        PG_AUTHID.ROLBYPASSRLS.as("bypassrls"),
                        PG_AUTHID.ROLCONNLIMIT.as("connectionLimit"),
                        field("nullif({0}, 'infinity')", PG_AUTHID.ROLVALIDUNTIL.getDataType(), PG_AUTHID.ROLVALIDUNTIL).as("validUntil"),
                        multiset(
                                select(parent.ROLNAME)
                                        .from(PG_AUTH_MEMBERS)
                                        .join(member).on(member.OID.eq(PG_AUTH_MEMBERS.MEMBER))
                                        .join(parent).on(parent.OID.eq(PG_AUTH_MEMBERS.ROLEID))
                                        .where(member.OID.eq(PG_AUTHID.OID))
                                        .orderBy(parent.ROLNAME)
                        ).as("inRole").convertFrom(result -> result.map(Record1::value1)),
                        multiset(
                                select(member.ROLNAME)
                                        .from(PG_AUTH_MEMBERS)
                                        .join(parent).on(parent.OID.eq(PG_AUTH_MEMBERS.ROLEID))
                                        .join(member).on(member.OID.eq(PG_AUTH_MEMBERS.MEMBER))
                                        .where(parent.OID.eq(PG_AUTHID.OID))
                                        .orderBy(member.ROLNAME)
                        ).as("role").convertFrom(result -> result.map(Record1::value1))
                )
                .from(PG_AUTHID)
                .where(PG_AUTHID.ROLNAME.eq(spec.getName()))
                .fetchSingleInto(RoleSpec.Flags.class);
    }

    /**
     * Build: CREATE ROLE <name> [ [ WITH ] option [ ... ] ]
     * See <a href="https://www.postgresql.org/docs/current/sql-createrole.html">
     * PostgreSQL: Documentation: CREATE ROLE
     * </a>
     */
    private static Query buildCreateRole(
            String roleName,
            RoleSpec.Flags flags,
            @Nullable String password
    ) {
        var options = new ArrayList<QueryPart>();

        // Only allow the user to log in if a password is specified.
        if (password != null) {
            options.add(keyword(RoleFlags.LOGIN.flag()));
            options.add(keyword(RoleFlags.PASSWORD.flag()));
            options.add(val(password));
        }

        if (flags.isSuperuser()) {
            options.add(keyword(RoleFlags.SUPERUSER.flag()));
        }
        if (flags.isCreatedb()) {
            options.add(keyword(RoleFlags.CREATEDB.flag()));
        }
        if (flags.isCreaterole()) {
            options.add(keyword(RoleFlags.CREATEROLE.flag()));
        }
        if (flags.isInherit()) {
            options.add(keyword(RoleFlags.INHERIT.flag()));
        }
        if (flags.isReplication()) {
            options.add(keyword(RoleFlags.REPLICATION.flag()));
        }
        if (flags.isBypassrls()) {
            options.add(keyword(RoleFlags.BYPASSRLS.flag()));
        }
        if (flags.getConnectionLimit() >= 0) {
            options.add(keyword(RoleFlags.CONNECTION_LIMIT.flag()));
            options.add(val(flags.getConnectionLimit()));
        }
        if (flags.getValidUntil() != null) {
            options.add(keyword(RoleFlags.VALID_UNTIL.flag()));
            options.add(val(flags.getValidUntil().toString()));
        }
        if (!flags.getInRole().isEmpty()) {
            options.add(keyword(RoleFlags.IN_ROLE.flag()));
            options.add(joinWithComma(
                    flags.getInRole()
                            .stream()
                            .map(DSL::role)
                            .toList()
            ));
        }
        if (!flags.getRole().isEmpty()) {
            options.add(keyword(RoleFlags.ROLE.flag()));
            options.add(joinWithComma(
                    flags.getRole()
                            .stream()
                            .map(DSL::role)
                            .toList()
            ));
        }

        var optionsSql = options.isEmpty()
                ? sql("") // nothing
                : sql(" with {0}", joinWithSpaces(options));

        return query(
                "create role {0}{1}",
                role(roleName),
                optionsSql
        );
    }

    private static Query buildAlterRole(
            String roleName,
            RoleSpec.Flags flags,
            boolean changePassword,
            @Nullable String password
    ) {
        var options = new ArrayList<QueryPart>();
        var loginExpected = password != null;

        // LOGIN / NOLOGIN
        options.add(keyword(loginExpected
                ? RoleFlags.LOGIN.flag()
                : RoleFlags.NO_LOGIN.flag()
        ));

        // Password handling
        // - if NOLOGIN, remove the password
        // - if LOGIN and passwordChanged, set the new password
        if (!loginExpected) {
            options.add(keyword(RoleFlags.PASSWORD.flag()));
            options.add(keyword("NULL"));
        } else if (changePassword) {
            options.add(keyword(RoleFlags.PASSWORD.flag()));
            options.add(val(password));
        }

        // Explicitly set the expected state to make the statement idempotent
        options.add(keyword(flags.isSuperuser()
                ? RoleFlags.SUPERUSER.flag()
                : RoleFlags.NO_SUPERUSER.flag()
        ));
        options.add(keyword(flags.isCreatedb()
                ? RoleFlags.CREATEDB.flag()
                : RoleFlags.NO_CREATEDB.flag()
        ));
        options.add(keyword(flags.isCreaterole()
                ? RoleFlags.CREATEROLE.flag()
                : RoleFlags.NO_CREATEROLE.flag()
        ));
        options.add(keyword(flags.isInherit()
                ? RoleFlags.INHERIT.flag()
                : RoleFlags.NO_INHERIT.flag()
        ));
        options.add(keyword(flags.isReplication()
                ? RoleFlags.REPLICATION.flag()
                : RoleFlags.NO_REPLICATION.flag()
        ));
        options.add(keyword(flags.isBypassrls()
                ? RoleFlags.BYPASSRLS.flag()
                : RoleFlags.NO_BYPASSRLS.flag()
        ));

        options.add(keyword(RoleFlags.CONNECTION_LIMIT.flag()));
        options.add(val(flags.getConnectionLimit()));

        options.add(keyword(RoleFlags.VALID_UNTIL.flag()));
        if (flags.getValidUntil() != null) {
            options.add(val(flags.getValidUntil().toString()));
        } else {
            options.add(val("infinity"));
        }

        return query(
                "alter role {0} with {1}",
                role(roleName),
                joinWithSpaces(options)
        );
    }

    public static void reconcileRoleMembership(
            DSLContext tx,
            RoleSpec spec,
            RoleSpec.Flags expectedFlags,
            RoleSpec.Flags currentFlags
    ) {
        var roleName = spec.getName();

        // ROLE IN
        var expectedInRole = new HashSet<>(expectedFlags.getInRole());
        var currentInRole = new HashSet<>(currentFlags.getInRole());

        var queries = new ArrayList<Query>();

        var inRoleToGrant = new HashSet<>(expectedInRole);
        inRoleToGrant.removeAll(currentInRole);

        var inRoleToRevoke = new HashSet<>(currentInRole);
        inRoleToRevoke.removeAll(expectedInRole);

        for (var parentRole : inRoleToGrant) {
            // GRANT parentRole TO roleName
            queries.add(buildGrantRoleToMember(parentRole, roleName));
        }
        for (var parentRole : inRoleToRevoke) {
            // REVOKE parentRole FROM roleName
            queries.add(buildRevokeRoleFromMember(parentRole, roleName));
        }

        // ROLE
        var expectedRoleMembers = new HashSet<>(expectedFlags.getRole());
        var currentRoleMembers = new HashSet<>(currentFlags.getRole());

        var roleMembersToGrant = new HashSet<>(expectedRoleMembers);
        roleMembersToGrant.removeAll(currentRoleMembers);

        var roleMembersToRevoke = new HashSet<>(currentRoleMembers);
        roleMembersToRevoke.removeAll(expectedRoleMembers);

        for (var member : roleMembersToGrant) {
            // GRANT roleName TO member
            queries.add(buildGrantRoleToMember(roleName, member));
        }
        for (var member : roleMembersToRevoke) {
            // REVOKE roleName FROM member
            queries.add(buildRevokeRoleFromMember(roleName, member));
        }

        if (!queries.isEmpty()) {
            tx.batch(queries).execute();
        }
    }

    public static Query buildGrantRoleToMember(
            String role,
            String member
    ) {
        return query("grant {0} to {1}", role(role), role(member));
    }

    public static Query buildRevokeRoleFromMember(
            String role,
            String member
    ) {
        return query("revoke {0} from {1}", role(role), role(member));
    }

    /**
     * Build: COMMENT ON ROLE <name> IS <comment>
     */
    private static Query buildCommentOnRole(
            String roleName,
            @Nullable String comment
    ) {
        return query(
                "comment on role {0} is {1}",
                name(roleName),
                val(comment)
        );
    }

    private static @Nullable String fetchCurrentRoleComment(
            DSLContext tx,
            String roleName
    ) {

        return tx
                .select(Routines.shobjDescription(
                        PG_AUTHID.OID,
                        val(PG_AUTHID.getUnqualifiedName().last())
                ))
                .from(PG_AUTHID)
                .where(PG_AUTHID.ROLNAME.eq(roleName))
                .fetchSingleInto(String.class);
    }

    private static @Nullable String normalizeComment(@Nullable String comment) {
        if (comment == null || comment.isBlank()) {
            return null;
        }

        return comment;
    }

    /**
     * Join QueryParts with the requested separator
     */
    private static QueryPart join(
            List<? extends QueryPart> items,
            String separator
    ) {
        int size = items.size();

        if (items.isEmpty()) {
            return sql("");
        } else if (size == 1) {
            return items.getFirst();
        }

        var template = new StringBuilder();

        // Add the first item without a separator
        template.append('{').append(0).append('}');

        // Add the rest of the items with the leading separator
        for (int i = 1; i < size; i++) {
            template.append(separator).append('{').append(i).append('}');
        }

        return sql(
                template.toString(),
                items.toArray(QueryPart[]::new)
        );
    }

    private static QueryPart joinWithSpaces(List<? extends QueryPart> parts) {
        return join(parts, " ");
    }

    private static QueryPart joinWithComma(List<? extends QueryPart> parts) {
        return join(parts, ", ");
    }
}
