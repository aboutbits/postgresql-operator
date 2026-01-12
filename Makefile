### SETUP

init:
	$(MAKE) install

install:
	./gradlew --console=colored :operator:quarkusBuild

### EXECUTION

run:
	./gradlew --console=colored :operator:quarkusDev

generate-jooq:
	./gradlew --console=colored :generate:jooqCodegen

test:
	./gradlew --console=colored :operator:clean :operator:test

# Flag targets as phony, to tell `make` that these are no file targets
.PHONY: init install run generate-jooq test
