### SETUP

init:
	$(MAKE) install

install:
	./gradlew --console=colored :operator:quarkusBuild

### EXECUTION

run:
	./gradlew --console=colored :operator:quarkusDev

generate-jooq:
	./gradlew --console=colored :generated:jooqCodegen

# Latest PostgreSQL Version configured in application.yml
test:
	./gradlew --console=colored :operator:clean :operator:test --rerun-tasks

test-pg18:
	./gradlew --console=colored :operator:clean :operator:test --fail-fast --rerun-tasks -Dquarkus.test.profile=test-pg18

test-pg17:
	./gradlew --console=colored :operator:clean :operator:test --rerun-tasks -Dquarkus.test.profile=test-pg17

test-pg16:
	./gradlew --console=colored :operator:clean :operator:test --rerun-tasks -Dquarkus.test.profile=test-pg16

test-pg15:
	./gradlew --console=colored :operator:clean :operator:test --rerun-tasks -Dquarkus.test.profile=test-pg15

# Flag targets as phony, to tell `make` that these are no file targets
.PHONY: init install run generate-jooq test test-pg18 test-pg17 test-pg16 test-pg15
