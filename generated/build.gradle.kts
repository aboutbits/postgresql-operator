plugins {
    `java-library`
    alias(libs.plugins.jooqPlugin)
}

dependencies {
    /**
     * jOOQ
     */
    api(libs.jooq)
    compileOnly(libs.jooqMeta)
    compileOnly(libs.jspecify)
    // PostgreSQL JDBC Driver for jOOQ generation
    jooqCodegen(libs.postgresql)
}

jooq {
    configuration {
        jdbc {
            driver = "org.postgresql.Driver"
            url = "jdbc:postgresql://localhost:5432/postgres"
            user = "root"
            password = "password"
        }
        generator {
            database {
                name = "org.jooq.meta.postgres.PostgresDatabase"
                schemata {
                    schema {
                        inputSchema = "pg_catalog"
                    }
                }
                includes = """
                  aclexplode   
                | pg_auth_members
                | pg_authid
                | pg_class
                | pg_database
                | pg_db_role_setting
                | pg_default_acl
                | pg_get_userbyid
                | pg_namespace
                | shobj_description
                """.trimIndent()
                excludes = """
                """.trimIndent()
            }
            generate {
                deprecated = false
                fluentSetters = true
                generatedAnnotation = true
                pojos = false
                nonnullAnnotation = true
                nullableAnnotation = true
                // We use JSpecify annotations already even though jOOQ does not officially support JSpecify's TYPE_USE positioning yet.
                // See https://github.com/jOOQ/jOOQ/issues/10759
                // This only works as our generated code is not using any generics, collections, maps, arrays
                // or forced types with inner classes, and therefore the positioning of the annotations is accidentally correct.
                nonnullAnnotationType = "org.jspecify.annotations.NonNull"
                nullableAnnotationType = "org.jspecify.annotations.Nullable"
            }
            target {
                packageName = "it.aboutbits.postgresql.core.infrastructure.persistence"
                directory = "src/main/java"
            }
        }
    }
}
