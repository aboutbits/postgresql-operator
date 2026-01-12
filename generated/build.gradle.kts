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
    // Custom Database generator
    jooqCodegen(project(":jooq-generator"))
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
                name = "it.aboutbits.postgresql.CustomPostgresDatabase"
                schemata {
                    schema {
                        inputSchema = "pg_catalog"
                    }
                }
                includes = """
                  has_column_privilege
                | has_database_privilege
                | has_foreign_data_wrapper_privilege
                | has_function_privilege
                | has_language_privilege
                | has_parameter_privilege
                | has_schema_privilege
                | has_sequence_privilege
                | has_server_privilege
                | has_table_privilege
                | has_tablespace_privilege
                | has_type_privilege
                | pg_auth_members
                | pg_authid
                | pg_db_role_setting
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
            }
            target {
                packageName = "it.aboutbits.postgresql.core.infrastructure.persistence"
                directory = "src/main/java"
            }
        }
    }
}
